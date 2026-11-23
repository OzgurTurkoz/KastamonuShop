package com.kastamonushop.app.viewmodel

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kastamonushop.app.bridge.NativeSurfaceBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uniffi.kastamonushop_ffi.DocumentInfo
import uniffi.kastamonushop_ffi.KastamonuEngine
import uniffi.kastamonushop_ffi.LayerSummary
import uniffi.kastamonushop_ffi.StrokeSample
import uniffi.kastamonushop_ffi.createKastamonuEngine
import uniffi.kastamonushop_ffi.engineVersionInfo
import uniffi.kastamonushop_ffi.getLastGpuError
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

enum class ActiveTool {
    NONE,
    BRUSH,
    ERASER
}

data class EditorUiState(
    val engineVersion: String = "",
    val docInfo: DocumentInfo? = null,
    val layers: List<LayerSummary> = emptyList(),
    val activeTool: ActiveTool = ActiveTool.NONE,
    val brushSize: Float = 28f,
    val brushHardness: Float = 0.85f,
    val brushOpacity: Float = 1.0f,
    val brushFlow: Float = 1.0f,
    val colorHex: String = "#FF4D4F",
    val bwLayerId: ULong? = null,
    val invertLayerId: ULong? = null,
    val statusMessage: String? = null,
    val isBusy: Boolean = false,
    val fitCameraSignal: Int = 0
)

class EditorViewModel : ViewModel() {
    private val engine: KastamonuEngine = createKastamonuEngine()
    private var statusAutoHideJob: Job? = null

    private val paintExecutor = Executors.newSingleThreadExecutor()
    private val pendingLock = Any()
    private val pendingSamples = ArrayList<StrokeSample>(64)
    private val drainScheduled = AtomicBoolean(false)

    // Coalesced live adjustment preview state
    @Volatile
    private var liveAdjustmentLayerId: ULong? = null
    private val pendingAdjParams = AtomicReference<String?>(null)
    private val adjDrainScheduled = AtomicBoolean(false)

    // Coalesced live pixel filter preview state (Gaussian Blur / Sharpen)
    private val pendingFilterUpdate = AtomicReference<Pair<String, String>?>(null)
    private val filterDrainScheduled = AtomicBoolean(false)

    private val _uiState = MutableStateFlow(
        EditorUiState(engineVersion = engineVersionInfo())
    )
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    init {
        refreshState()
        syncBrushToEngine()
    }

    override fun onCleared() {
        super.onCleared()
        paintExecutor.shutdown()
    }

    private fun showStatus(msg: String, autoHideMs: Long = 3000L) {
        statusAutoHideJob?.cancel()
        _uiState.update { it.copy(statusMessage = msg) }
        if (autoHideMs > 0) {
            statusAutoHideJob = viewModelScope.launch {
                delay(autoHideMs)
                _uiState.update { it.copy(statusMessage = null) }
            }
        }
    }

    fun refreshState() {
        paintExecutor.execute {
            runCatching {
                val info = engine.getDocumentInfo()
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers) }
                NativeSurfaceBridge.nativeRenderFrame()
                getLastGpuError()?.let { gpuErr -> showStatus(gpuErr, 6000L) }
            }.onFailure { err ->
                showStatus(err.message ?: "Bilinmeyen hata")
            }
        }
    }

    fun setTool(tool: ActiveTool) {
        _uiState.update { it.copy(activeTool = tool) }
    }

    fun updateBrush(
        size: Float = _uiState.value.brushSize,
        hardness: Float = _uiState.value.brushHardness,
        opacity: Float = _uiState.value.brushOpacity,
        flow: Float = _uiState.value.brushFlow,
        colorHex: String = _uiState.value.colorHex
    ) {
        _uiState.update {
            it.copy(
                brushSize = size.coerceIn(1f, 500f),
                brushHardness = hardness.coerceIn(0f, 1f),
                brushOpacity = opacity.coerceIn(0.01f, 1f),
                brushFlow = flow.coerceIn(0.01f, 1f),
                colorHex = colorHex
            )
        }
        syncBrushToEngine()
    }

    private fun syncBrushToEngine() {
        val s = _uiState.value
        paintExecutor.execute {
            runCatching {
                engine.setBrush(s.brushSize, s.brushHardness, s.brushOpacity, s.brushFlow, s.colorHex)
            }
        }
    }

    fun beginLiveStroke(sample: StrokeSample) {
        val s = _uiState.value
        if (s.activeTool == ActiveTool.NONE) return
        val activeLayer = s.layers.firstOrNull { it.isActive }
        if (activeLayer?.locked == true) {
            showStatus("Orijinal fotoğraf kilitli — düzenlemek için Katman panelinden kilidi açabilirsin", 3200L)
            return
        }
        val isEraser = s.activeTool == ActiveTool.ERASER
        synchronized(pendingLock) { pendingSamples.clear() }
        paintExecutor.execute {
            runCatching {
                engine.beginLiveStroke(sample, s.colorHex, isEraser)
                NativeSurfaceBridge.nativeRenderFrame()
            }.onFailure { err ->
                showStatus("Çizim hatası: ${err.message}")
            }
        }
    }

    fun pushLiveStroke(samples: List<StrokeSample>) {
        if (_uiState.value.activeTool == ActiveTool.NONE || samples.isEmpty()) return
        synchronized(pendingLock) {
            pendingSamples.addAll(samples)
        }
        if (drainScheduled.compareAndSet(false, true)) {
            paintExecutor.execute { drainPendingSamples() }
        }
    }

    private fun drainPendingSamples() {
        while (true) {
            val batch: List<StrokeSample>
            synchronized(pendingLock) {
                if (pendingSamples.isEmpty()) {
                    drainScheduled.set(false)
                    return
                }
                batch = ArrayList(pendingSamples)
                pendingSamples.clear()
            }
            runCatching {
                engine.pushLiveStroke(batch)
                NativeSurfaceBridge.nativeRenderFrame()
            }
        }
    }

    fun finishLiveStroke() {
        if (_uiState.value.activeTool == ActiveTool.NONE) return
        paintExecutor.execute {
            drainPendingSamples()
            runCatching {
                val info = engine.finishLiveStroke()
                _uiState.update { it.copy(docInfo = info) }
                NativeSurfaceBridge.nativeRenderFrame()
            }.onFailure { err ->
                showStatus("Çizim kayıt hatası: ${err.message}")
            }
        }
    }

    fun cancelLiveStroke() {
        synchronized(pendingLock) { pendingSamples.clear() }
        paintExecutor.execute {
            runCatching {
                engine.cancelLiveStroke()
                NativeSurfaceBridge.nativeRenderFrame()
            }
        }
    }

    // Coalesced real-time live adjustment preview while dragging sliders
    fun beginLiveAdjustment(kind: String, initialParamsJson: String) {
        pendingAdjParams.set(null)
        paintExecutor.execute {
            runCatching {
                liveAdjustmentLayerId?.let { oldId -> runCatching { engine.deleteLayerById(oldId) } }
                val id = engine.beginLiveAdjustment(kind, initialParamsJson)
                liveAdjustmentLayerId = id
                NativeSurfaceBridge.nativeRenderFrame()
            }.onFailure { err ->
                showStatus("Önizleme hatası: ${err.message}")
            }
        }
    }

    fun updateLiveAdjustment(paramsJson: String) {
        pendingAdjParams.set(paramsJson)
        if (adjDrainScheduled.compareAndSet(false, true)) {
            paintExecutor.execute { drainPendingAdjustments() }
        }
    }

    private fun drainPendingAdjustments() {
        while (true) {
            val params = pendingAdjParams.getAndSet(null)
            if (params == null) {
                adjDrainScheduled.set(false)
                if (pendingAdjParams.get() == null) return
                if (!adjDrainScheduled.compareAndSet(false, true)) return
                continue
            }
            val id = liveAdjustmentLayerId ?: continue
            runCatching {
                engine.updateLiveAdjustment(id, params)
                NativeSurfaceBridge.nativeRenderFrame()
            }
        }
    }

    fun commitLiveAdjustment(labelTr: String) {
        paintExecutor.execute {
            drainPendingAdjustments()
            liveAdjustmentLayerId = null
            runCatching {
                val info = engine.getDocumentInfo()
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers) }
                NativeSurfaceBridge.nativeRenderFrame()
                showStatus("$labelTr uygulandı", 2000L)
            }
        }
    }

    fun cancelLiveAdjustment() {
        pendingAdjParams.set(null)
        paintExecutor.execute {
            val id = liveAdjustmentLayerId ?: return@execute
            liveAdjustmentLayerId = null
            runCatching {
                engine.deleteLayerById(id)
                val info = engine.getDocumentInfo()
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers) }
                NativeSurfaceBridge.nativeRenderFrame()
            }
        }
    }

    // Coalesced real-time live pixel filter preview (Gaussian Blur / Sharpen)
    fun beginLiveFilter() {
        pendingFilterUpdate.set(null)
        paintExecutor.execute {
            runCatching {
                engine.beginLiveFilter()
            }.onFailure { err ->
                showStatus("Filtre başlatılamadı: ${err.message}", 3500L)
            }
        }
    }

    fun updateLiveFilter(commandId: String, paramsJson: String) {
        pendingFilterUpdate.set(commandId to paramsJson)
        if (filterDrainScheduled.compareAndSet(false, true)) {
            paintExecutor.execute { drainPendingFilterUpdates() }
        }
    }

    private fun drainPendingFilterUpdates() {
        while (true) {
            val next = pendingFilterUpdate.getAndSet(null)
            if (next == null) {
                filterDrainScheduled.set(false)
                if (pendingFilterUpdate.get() == null) return
                if (!filterDrainScheduled.compareAndSet(false, true)) return
                continue
            }
            runCatching {
                engine.updateLiveFilter(next.first, next.second)
                NativeSurfaceBridge.nativeRenderFrame()
            }.onFailure { err ->
                showStatus("Filtre önizleme hatası: ${err.message}", 3000L)
            }
        }
    }

    fun commitLiveFilter(labelTr: String) {
        paintExecutor.execute {
            drainPendingFilterUpdates()
            runCatching {
                engine.commitLiveFilter()
                val info = engine.getDocumentInfo()
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers) }
                NativeSurfaceBridge.nativeRenderFrame()
                showStatus("$labelTr uygulandı", 2000L)
            }.onFailure { err ->
                showStatus("$labelTr hatası: ${err.message}", 3500L)
            }
        }
    }

    fun cancelLiveFilter() {
        pendingFilterUpdate.set(null)
        paintExecutor.execute {
            runCatching {
                engine.cancelLiveFilter()
                val info = engine.getDocumentInfo()
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers) }
                NativeSurfaceBridge.nativeRenderFrame()
            }
        }
    }

    // True ON/OFF toggles for Black & White and Invert
    fun toggleBlackAndWhite() {
        val existingId = _uiState.value.bwLayerId
        paintExecutor.execute {
            runCatching {
                if (existingId != null) {
                    runCatching { engine.deleteLayerById(existingId) }
                    val info = engine.getDocumentInfo()
                    val layers = engine.getLayers()
                    _uiState.update { it.copy(docInfo = info, layers = layers, bwLayerId = null) }
                    NativeSurfaceBridge.nativeRenderFrame()
                    showStatus("Siyah-Beyaz kapatıldı", 1800L)
                } else {
                    val newId = engine.beginLiveAdjustment("blackWhite", "{}")
                    val info = engine.getDocumentInfo()
                    val layers = engine.getLayers()
                    _uiState.update { it.copy(docInfo = info, layers = layers, bwLayerId = newId) }
                    NativeSurfaceBridge.nativeRenderFrame()
                    showStatus("Siyah-Beyaz açıldı", 1800L)
                }
            }.onFailure { err ->
                showStatus("Siyah-Beyaz hatası: ${err.message}")
            }
        }
    }

    fun toggleInvert() {
        val existingId = _uiState.value.invertLayerId
        paintExecutor.execute {
            runCatching {
                if (existingId != null) {
                    runCatching { engine.deleteLayerById(existingId) }
                    val info = engine.getDocumentInfo()
                    val layers = engine.getLayers()
                    _uiState.update { it.copy(docInfo = info, layers = layers, invertLayerId = null) }
                    NativeSurfaceBridge.nativeRenderFrame()
                    showStatus("Negatif kapatıldı", 1800L)
                } else {
                    val newId = engine.beginLiveAdjustment("invert", "{}")
                    val info = engine.getDocumentInfo()
                    val layers = engine.getLayers()
                    _uiState.update { it.copy(docInfo = info, layers = layers, invertLayerId = newId) }
                    NativeSurfaceBridge.nativeRenderFrame()
                    showStatus("Negatif açıldı", 1800L)
                }
            }.onFailure { err ->
                showStatus("Negatif hatası: ${err.message}")
            }
        }
    }

    fun undo() {
        paintExecutor.execute {
            runCatching {
                val info = engine.undo()
                val layers = engine.getLayers()
                val ids = layers.map { it.id }.toSet()
                _uiState.update {
                    it.copy(
                        docInfo = info,
                        layers = layers,
                        bwLayerId = it.bwLayerId?.takeIf { id -> id in ids },
                        invertLayerId = it.invertLayerId?.takeIf { id -> id in ids }
                    )
                }
                NativeSurfaceBridge.nativeRenderFrame()
            }
        }
    }

    fun redo() {
        paintExecutor.execute {
            runCatching {
                val info = engine.redo()
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers) }
                NativeSurfaceBridge.nativeRenderFrame()
            }
        }
    }

    fun addLayer() {
        paintExecutor.execute {
            runCatching {
                val nextIndex = _uiState.value.layers.size + 1
                val layers = engine.addLayer("Katman $nextIndex")
                val info = engine.getDocumentInfo()
                _uiState.update { it.copy(docInfo = info, layers = layers) }
                NativeSurfaceBridge.nativeRenderFrame()
                showStatus("Katman $nextIndex eklendi", 2000L)
            }
        }
    }

    fun deleteActiveLayer() {
        paintExecutor.execute {
            runCatching {
                val layers = engine.deleteActiveLayer()
                val info = engine.getDocumentInfo()
                val ids = layers.map { it.id }.toSet()
                _uiState.update {
                    it.copy(
                        docInfo = info,
                        layers = layers,
                        bwLayerId = it.bwLayerId?.takeIf { id -> id in ids },
                        invertLayerId = it.invertLayerId?.takeIf { id -> id in ids }
                    )
                }
                NativeSurfaceBridge.nativeRenderFrame()
            }.onFailure { err ->
                showStatus("Katman silinemedi: ${err.message}")
            }
        }
    }

    fun selectLayer(layerId: ULong) {
        paintExecutor.execute {
            runCatching {
                engine.selectLayer(layerId)
                val info = engine.getDocumentInfo()
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers) }
            }
        }
    }

    fun toggleLayerVisibility(layerId: ULong, visible: Boolean) {
        paintExecutor.execute {
            runCatching {
                engine.setLayerVisibility(layerId, visible)
                val layers = engine.getLayers()
                _uiState.update { it.copy(layers = layers) }
                NativeSurfaceBridge.nativeRenderFrame()
            }
        }
    }

    fun setLayerOpacity(layerId: ULong, opacity: Float) {
        _uiState.update { st ->
            st.copy(layers = st.layers.map { if (it.id == layerId) it.copy(opacity = opacity) else it })
        }
        paintExecutor.execute {
            runCatching {
                engine.setLayerOpacity(layerId, opacity)
                NativeSurfaceBridge.nativeRenderFrame()
            }
        }
    }

    fun setLayerBlendMode(layerId: ULong, blendMode: String) {
        paintExecutor.execute {
            runCatching {
                engine.setLayerBlendMode(layerId, blendMode)
                val layers = engine.getLayers()
                _uiState.update { it.copy(layers = layers) }
                NativeSurfaceBridge.nativeRenderFrame()
            }.onFailure { err ->
                showStatus("Karışım modu hatası: ${err.message}")
            }
        }
    }

    fun toggleLayerLocked(layerId: ULong, locked: Boolean) {
        paintExecutor.execute {
            runCatching {
                engine.setLayerLocked(layerId, locked)
                val layers = engine.getLayers()
                _uiState.update { it.copy(layers = layers) }
                showStatus(
                    if (locked) "Orijinal fotoğraf kilitlendi (koruma açık)"
                    else "Fotoğraf kilidi açıldı — artık düzenleyebilirsin",
                    2500L
                )
            }
        }
    }

    fun openDocumentFromUri(resolver: ContentResolver, uri: Uri, asLayer: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isBusy = true) }
            showStatus(if (asLayer) "Fotoğraf katman olarak ekleniyor..." else "Dosya açılıyor...", 0L)
            runCatching {
                val fileName = resolveDisplayName(resolver, uri) ?: "image.png"
                val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Dosya okunamadı")
                val info = if (asLayer) {
                    engine.placeImageBytes(fileName, bytes)
                } else {
                    engine.openDocumentBytes(fileName, bytes)
                }
                val layers = engine.getLayers()
                _uiState.update {
                    it.copy(
                        docInfo = info,
                        layers = layers,
                        isBusy = false,
                        bwLayerId = if (asLayer) it.bwLayerId else null,
                        invertLayerId = if (asLayer) it.invertLayerId else null,
                        fitCameraSignal = if (asLayer) it.fitCameraSignal else it.fitCameraSignal + 1
                    )
                }
                NativeSurfaceBridge.nativeRenderFrame()
                showStatus("Açıldı: ${info.name} (${info.width}×${info.height})")
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false) }
                showStatus("Açma hatası: ${err.message}", 5000L)
            }
        }
    }

    fun exportDocumentToUri(resolver: ContentResolver, uri: Uri, formatExt: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isBusy = true) }
            showStatus("Kaydediliyor ($formatExt)...", 0L)
            runCatching {
                val bytes = engine.exportDocumentBytes(formatExt)
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: error("Hedef dosyaya yazılamadı")
                _uiState.update { it.copy(isBusy = false) }
                showStatus("Kaydedildi (${bytes.size / 1024} KB)")
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false) }
                showStatus("Kayıt hatası: ${err.message}", 5000L)
            }
        }
    }

    fun clearStatus() {
        statusAutoHideJob?.cancel()
        _uiState.update { it.copy(statusMessage = null) }
    }

    private fun resolveDisplayName(resolver: ContentResolver, uri: Uri): String? {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return cursor.getString(idx)
            }
        }
        return uri.lastPathSegment
    }
}
