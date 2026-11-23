package com.kastamonushop.app.viewmodel

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import java.nio.ByteBuffer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kastamonushop.app.bridge.NativeSurfaceBridge
import com.kastamonushop.app.data.SavedGalleryStore
import com.kastamonushop.app.data.SavedPhotoItem
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
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

enum class ActiveTool {
    NONE,
    BRUSH,
    ERASER,
    EYEDROPPER,
    SPOT_HEAL,
    SMUDGE,
    DODGE,
    BURN
}

data class EditorUiState(
    val engineVersion: String = "",
    val docInfo: DocumentInfo? = null,
    val layers: List<LayerSummary> = emptyList(),
    val savedPhotos: List<SavedPhotoItem> = emptyList(),
    val activeTool: ActiveTool = ActiveTool.NONE,
    val rulerEnabled: Boolean = false,
    val symmetryMode: String = "off",
    val brushSize: Float = 28f,
    val brushHardness: Float = 0.85f,
    val brushOpacity: Float = 1.0f,
    val brushFlow: Float = 1.0f,
    val retouchStrength: Float = 55f,
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

    // Coalesced live pixel filter preview state
    private val pendingFilterUpdate = AtomicReference<Pair<String, String>?>(null)
    private val filterDrainScheduled = AtomicBoolean(false)

    // Coalesced layer opacity slider state
    private val pendingOpacityUpdate = AtomicReference<Pair<ULong, Float>?>(null)
    private val opacityDrainScheduled = AtomicBoolean(false)

    // Coalesced eyedropper sample state
    private val pendingSamplePt = AtomicReference<Pair<Float, Float>?>(null)
    private val sampleDrainScheduled = AtomicBoolean(false)

    // Prevents rapid Spot Healing swipes from piling up on paintExecutor
    private val spotHealInFlight = AtomicBoolean(false)

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

    fun loadSavedGallery(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val items = SavedGalleryStore.list(context)
            _uiState.update { it.copy(savedPhotos = items) }
        }
    }

    fun deleteSavedPhoto(context: Context, id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val remaining = SavedGalleryStore.delete(context, id)
            _uiState.update { it.copy(savedPhotos = remaining) }
        }
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

    fun toggleRuler() {
        val next = !_uiState.value.rulerEnabled
        _uiState.update { it.copy(rulerEnabled = next) }
        showStatus(
            if (next) "Cetvel açık — düz çizgi çek (0°/45°/90° otomatik hizalanır)"
            else "Cetvel kapatıldı — serbest çizim",
            2200L
        )
    }

    fun cycleSymmetry() {
        val order = listOf("off", "vertical", "horizontal", "dual")
        val curIdx = order.indexOf(_uiState.value.symmetryMode).coerceAtLeast(0)
        val nextMode = order[(curIdx + 1) % order.size]
        setSymmetry(nextMode)
    }

    fun setSymmetry(mode: String) {
        _uiState.update { it.copy(symmetryMode = mode) }
        paintExecutor.execute {
            runCatching {
                engine.setSymmetry(mode)
                val label = when (mode) {
                    "vertical" -> "Ayna: Dikey simetri açık"
                    "horizontal" -> "Ayna: Yatay simetri açık"
                    "dual" -> "Ayna: Dörtlü (Mandala) simetri açık"
                    else -> "Ayna simetrisi kapatıldı"
                }
                showStatus(label, 2000L)
            }.onFailure { err ->
                showStatus("Simetri hatası: ${err.message}", 3000L)
            }
        }
    }

    fun sampleColorAt(docX: Float, docY: Float) {
        pendingSamplePt.set(docX to docY)
        if (sampleDrainScheduled.compareAndSet(false, true)) {
            paintExecutor.execute { drainPendingColorSamples() }
        }
    }

    private fun drainPendingColorSamples() {
        while (true) {
            val pt = pendingSamplePt.getAndSet(null)
            if (pt == null) {
                sampleDrainScheduled.set(false)
                if (pendingSamplePt.get() == null) return
                if (!sampleDrainScheduled.compareAndSet(false, true)) return
                continue
            }
            runCatching {
                val hex = engine.sampleColorHex(pt.first, pt.second)
                _uiState.update { it.copy(colorHex = hex) }
                showStatus("Renk seçildi: $hex", 1400L)
            }
        }
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

    private fun isLiveDabTool(tool: ActiveTool): Boolean =
        tool == ActiveTool.SMUDGE || tool == ActiveTool.DODGE || tool == ActiveTool.BURN

    private fun dabToolKey(tool: ActiveTool): String = when (tool) {
        ActiveTool.SMUDGE -> "smudge"
        ActiveTool.DODGE -> "dodge"
        ActiveTool.BURN -> "burn"
        else -> ""
    }

    fun beginLiveStroke(sample: StrokeSample) {
        val s = _uiState.value
        val isBrushOrEraser = s.activeTool == ActiveTool.BRUSH || s.activeTool == ActiveTool.ERASER
        val isDab = isLiveDabTool(s.activeTool)
        if (!isBrushOrEraser && !isDab) return

        if (isBrushOrEraser) {
            val activeLayer = s.layers.firstOrNull { it.isActive }
            if (activeLayer?.locked == true) {
                showStatus("Orijinal fotoğraf kilitli — düzenlemek için Katman panelinden kilidi açabilirsin", 3200L)
                return
            }
        }

        synchronized(pendingLock) { pendingSamples.clear() }
        paintExecutor.execute {
            runCatching {
                if (isDab) {
                    engine.beginLiveDab(dabToolKey(s.activeTool), sample, s.brushSize, s.brushHardness, s.retouchStrength)
                } else {
                    engine.beginLiveStroke(sample, s.colorHex, s.activeTool == ActiveTool.ERASER)
                }
                NativeSurfaceBridge.nativeRenderFrame()
            }.onFailure { err ->
                showStatus("Araç hatası: ${err.message}")
            }
        }
    }

    fun pushLiveStroke(samples: List<StrokeSample>) {
        val tool = _uiState.value.activeTool
        val ok = tool == ActiveTool.BRUSH || tool == ActiveTool.ERASER || isLiveDabTool(tool)
        if (!ok || samples.isEmpty()) return
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
            val isDab = isLiveDabTool(_uiState.value.activeTool)
            runCatching {
                if (isDab) {
                    engine.pushLiveDab(batch)
                } else {
                    engine.pushLiveStroke(batch)
                }
                NativeSurfaceBridge.nativeRenderFrame()
            }
        }
    }

    fun finishLiveStroke() {
        val tool = _uiState.value.activeTool
        val isDab = isLiveDabTool(tool)
        if (tool != ActiveTool.BRUSH && tool != ActiveTool.ERASER && !isDab) return
        paintExecutor.execute {
            drainPendingSamples()
            runCatching {
                val info = if (isDab) engine.finishLiveDab() else engine.finishLiveStroke()
                _uiState.update { it.copy(docInfo = info) }
                NativeSurfaceBridge.nativeRenderFrame()
            }.onFailure { err ->
                showStatus("Kayıt hatası: ${err.message}")
            }
        }
    }

    fun commitRulerStroke(samples: List<StrokeSample>) {
        val s = _uiState.value
        if ((s.activeTool != ActiveTool.BRUSH && s.activeTool != ActiveTool.ERASER) || samples.isEmpty()) return
        val activeLayer = s.layers.firstOrNull { it.isActive }
        if (activeLayer?.locked == true) {
            showStatus("Orijinal fotoğraf kilitli — düzenlemek için Katman panelinden kilidi açabilirsin", 3200L)
            return
        }
        val isEraser = s.activeTool == ActiveTool.ERASER
        paintExecutor.execute {
            runCatching {
                engine.beginLiveStroke(samples.first(), s.colorHex, isEraser)
                if (samples.size > 1) {
                    engine.pushLiveStroke(samples.drop(1))
                }
                val info = engine.finishLiveStroke()
                _uiState.update { it.copy(docInfo = info) }
                NativeSurfaceBridge.nativeRenderFrame()
            }.onFailure { err ->
                showStatus("Cetvel çizim hatası: ${err.message}")
            }
        }
    }

    fun cancelLiveStroke() {
        synchronized(pendingLock) { pendingSamples.clear() }
        paintExecutor.execute {
            runCatching {
                engine.cancelLiveStroke()
                engine.cancelLiveDab()
                NativeSurfaceBridge.nativeRenderFrame()
            }
        }
    }

    // One-shot CPU bake after 60 FPS GPU Mirror preview
    fun updateRetouchStrength(strength: Float) {
        _uiState.update { it.copy(retouchStrength = strength.coerceIn(5f, 100f)) }
    }

    fun addTextLayer(text: String, x: Float, y: Float, sizePx: Float, colorHex: String, align: String) {
        if (text.trim().isEmpty()) {
            showStatus("Lütfen eklenecek metni yaz", 2000L)
            return
        }
        _uiState.update { it.copy(isBusy = true) }
        paintExecutor.execute {
            runCatching {
                val info = engine.addTextLayer(text, x, y, sizePx, colorHex, align)
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers, isBusy = false) }
                NativeSurfaceBridge.nativeRenderFrame()
                showStatus("Metin katmanı eklendi", 2000L)
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false) }
                showStatus("Metin hatası: ${err.message}", 4000L)
            }
        }
    }

    fun applyRetouchStroke(samples: List<StrokeSample>) {
        val s = _uiState.value
        val toolKey = when (s.activeTool) {
            ActiveTool.SPOT_HEAL -> "spot_heal"
            ActiveTool.SMUDGE -> "smudge"
            ActiveTool.DODGE -> "dodge"
            ActiveTool.BURN -> "burn"
            else -> return
        }
        if (samples.isEmpty()) return
        if (toolKey == "spot_heal" && !spotHealInFlight.compareAndSet(false, true)) {
            return
        }
        paintExecutor.execute {
            try {
                runCatching {
                    val info = engine.applyRetouchStroke(toolKey, samples, s.brushSize, s.brushHardness, s.retouchStrength)
                    val layers = engine.getLayers()
                    _uiState.update { it.copy(docInfo = info, layers = layers) }
                    NativeSurfaceBridge.nativeRenderFrame()
                }.onFailure { err ->
                    showStatus("Rötuş hatası: ${err.message}", 3500L)
                }
            } finally {
                if (toolKey == "spot_heal") {
                    spotHealInFlight.set(false)
                }
            }
        }
    }

    /** Quick-saves the current document straight into the Home screen's Saved Photos gallery. */
    fun quickSaveToGallery(context: Context, onSaved: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isBusy = true) }
            runCatching {
                val bytes = engine.exportDocumentBytes("png")
                val doc = _uiState.value.docInfo
                val updated = SavedGalleryStore.recordSavedPhoto(
                    context = context,
                    displayName = doc?.name ?: "StonebridgeCraft",
                    formatExt = "png",
                    exportedBytes = bytes,
                    previewPngBytes = null,
                    width = doc?.width?.toInt() ?: 0,
                    height = doc?.height?.toInt() ?: 0
                )
                _uiState.update { it.copy(isBusy = false, savedPhotos = updated) }
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false) }
                showStatus("Kaydetme hatası: ${err.message}", 3500L)
            }
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                onSaved()
            }
        }
    }

    fun applyPhotoMirror(mode: String, axisRatio: Float) {
        _uiState.update { it.copy(isBusy = true) }
        paintExecutor.execute {
            runCatching {
                val info = engine.applyPhotoMirror(mode, axisRatio)
                val layers = engine.getLayers()
                NativeSurfaceBridge.nativeSetPreview(0, 0.5f, false, false)
                _uiState.update { it.copy(docInfo = info, layers = layers, isBusy = false) }
                NativeSurfaceBridge.nativeRenderFrame()
                showStatus("Ayna efekti uygulandı", 2000L)
            }.onFailure { err ->
                NativeSurfaceBridge.nativeSetPreview(0, 0.5f, false, false)
                _uiState.update { it.copy(isBusy = false) }
                showStatus("Ayna hatası: ${err.message}", 3500L)
            }
        }
    }

    // One-shot CPU bake after 60 FPS GPU Rotate/Flip preview
    fun applyTransformBatch(quarterTurnsCw: Int, flipH: Boolean, flipV: Boolean) {
        val turns = ((quarterTurnsCw % 4) + 4) % 4
        if (turns == 0 && !flipH && !flipV) {
            NativeSurfaceBridge.nativeSetPreview(0, 0.5f, false, false)
            _uiState.update { it.copy(fitCameraSignal = it.fitCameraSignal + 1) }
            return
        }
        _uiState.update { it.copy(isBusy = true) }
        paintExecutor.execute {
            runCatching {
                val info = engine.applyCanvasTransformBatch(turns, flipH, flipV)
                val layers = engine.getLayers()
                NativeSurfaceBridge.nativeSetPreview(0, 0.5f, false, false)
                _uiState.update {
                    it.copy(
                        docInfo = info,
                        layers = layers,
                        isBusy = false,
                        fitCameraSignal = it.fitCameraSignal + 1
                    )
                }
                showStatus("Dönüştürme uygulandı", 1800L)
            }.onFailure { err ->
                NativeSurfaceBridge.nativeSetPreview(0, 0.5f, false, false)
                _uiState.update { it.copy(isBusy = false) }
                showStatus("Döndürme hatası: ${err.message}", 3500L)
            }
        }
    }

    fun cropCanvas(x: Int, y: Int, width: Int, height: Int) {
        _uiState.update { it.copy(isBusy = true) }
        paintExecutor.execute {
            runCatching {
                val info = engine.cropCanvas(x, y, width, height)
                val layers = engine.getLayers()
                _uiState.update {
                    it.copy(
                        docInfo = info,
                        layers = layers,
                        isBusy = false,
                        fitCameraSignal = it.fitCameraSignal + 1
                    )
                }
                showStatus("Kırpıldı (${info.width}×${info.height})", 2200L)
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false) }
                showStatus("Kırpma hatası: ${err.message}", 3500L)
            }
        }
    }

    // One-shot native adjustment bake after 60 FPS GPU preview
    fun applyAdjustmentOnce(kind: String, paramsJson: String, labelTr: String) {
        _uiState.update { it.copy(isBusy = true) }
        paintExecutor.execute {
            runCatching {
                val info = engine.applyAdjustmentOnce(kind, paramsJson)
                val layers = engine.getLayers()
                NativeSurfaceBridge.nativeSetAdjustPreview(0f, 0f, 0f, 0f, 0f, 0f)
                _uiState.update { it.copy(docInfo = info, layers = layers, isBusy = false) }
                NativeSurfaceBridge.nativeRenderFrame()
                showStatus("$labelTr uygulandı", 2000L)
            }.onFailure { err ->
                NativeSurfaceBridge.nativeSetAdjustPreview(0f, 0f, 0f, 0f, 0f, 0f)
                _uiState.update { it.copy(isBusy = false) }
                showStatus("$labelTr hatası: ${err.message}", 3500L)
            }
        }
    }

    // One-shot low-memory Blur bake after 60 FPS GPU preview
    fun applyBlurFast(radius: Float) {
        if (radius < 0.5f) {
            NativeSurfaceBridge.nativeSetAdjustPreview(0f, 0f, 0f, 0f, 0f, 0f)
            NativeSurfaceBridge.nativeRenderFrame()
            return
        }
        _uiState.update { it.copy(isBusy = true) }
        paintExecutor.execute {
            runCatching {
                val info = engine.applyBlurFast(radius)
                val layers = engine.getLayers()
                NativeSurfaceBridge.nativeSetAdjustPreview(0f, 0f, 0f, 0f, 0f, 0f)
                _uiState.update { it.copy(docInfo = info, layers = layers, isBusy = false) }
                NativeSurfaceBridge.nativeRenderFrame()
                showStatus("Bulanıklaştırma uygulandı", 2000L)
            }.onFailure { err ->
                NativeSurfaceBridge.nativeSetAdjustPreview(0f, 0f, 0f, 0f, 0f, 0f)
                _uiState.update { it.copy(isBusy = false) }
                showStatus("Bulanıklaştırma hatası: ${err.message}", 3500L)
            }
        }
    }

    // One-shot low-memory Sharpen bake after 60 FPS GPU preview
    fun applySharpenFast(amount: Float) {
        if (amount < 1f) {
            NativeSurfaceBridge.nativeSetAdjustPreview(0f, 0f, 0f, 0f, 0f, 0f)
            NativeSurfaceBridge.nativeRenderFrame()
            return
        }
        _uiState.update { it.copy(isBusy = true) }
        paintExecutor.execute {
            runCatching {
                val info = engine.applySharpenFast(amount)
                val layers = engine.getLayers()
                NativeSurfaceBridge.nativeSetAdjustPreview(0f, 0f, 0f, 0f, 0f, 0f)
                _uiState.update { it.copy(docInfo = info, layers = layers, isBusy = false) }
                NativeSurfaceBridge.nativeRenderFrame()
                showStatus("Keskinleştirme uygulandı", 2000L)
            }.onFailure { err ->
                NativeSurfaceBridge.nativeSetAdjustPreview(0f, 0f, 0f, 0f, 0f, 0f)
                _uiState.update { it.copy(isBusy = false) }
                showStatus("Keskinleştirme hatası: ${err.message}", 3500L)
            }
        }
    }

    // Coalesced, undo-clean real-time live adjustment preview
    fun beginLiveAdjustment(kind: String, initialParamsJson: String) {
        pendingAdjParams.set(null)
        paintExecutor.execute {
            runCatching {
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
                engine.commitLiveAdjustment()
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
            liveAdjustmentLayerId = null
            runCatching {
                engine.cancelLiveAdjustment()
                val info = engine.getDocumentInfo()
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers) }
                NativeSurfaceBridge.nativeRenderFrame()
            }
        }
    }

    // Coalesced real-time live pixel filter preview
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
        pendingOpacityUpdate.set(layerId to opacity)
        if (opacityDrainScheduled.compareAndSet(false, true)) {
            paintExecutor.execute { drainPendingOpacityUpdates() }
        }
    }

    private fun drainPendingOpacityUpdates() {
        while (true) {
            val next = pendingOpacityUpdate.getAndSet(null)
            if (next == null) {
                opacityDrainScheduled.set(false)
                if (pendingOpacityUpdate.get() == null) return
                if (!opacityDrainScheduled.compareAndSet(false, true)) return
                continue
            }
            runCatching {
                engine.setLayerOpacity(next.first, next.second)
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

    fun newBlankCanvas() {
        paintExecutor.execute {
            runCatching {
                val info = engine.newDocument("StonebridgeCraft Canvas", 2048u, 2048u)
                val layers = engine.getLayers()
                _uiState.update {
                    it.copy(
                        docInfo = info,
                        layers = layers,
                        bwLayerId = null,
                        invertLayerId = null,
                        symmetryMode = "off",
                        rulerEnabled = false,
                        activeTool = ActiveTool.NONE,
                        fitCameraSignal = it.fitCameraSignal + 1
                    )
                }
            }
        }
    }

    fun openSavedPhoto(item: SavedPhotoItem) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isBusy = true) }
            runCatching {
                val f = File(item.filePath)
                val bytes = f.readBytes()
                val info = engine.openDocumentBytes(f.name, bytes)
                val layers = engine.getLayers()
                _uiState.update {
                    it.copy(
                        docInfo = info,
                        layers = layers,
                        isBusy = false,
                        bwLayerId = null,
                        invertLayerId = null,
                        symmetryMode = "off",
                        rulerEnabled = false,
                        activeTool = ActiveTool.NONE,
                        fitCameraSignal = it.fitCameraSignal + 1
                    )
                }
                showStatus("Açıldı: ${item.displayName}", 2000L)
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false) }
                showStatus("Açma hatası: ${err.message}", 4500L)
            }
        }
    }

    private fun decodeHardwareSafeRgba(
        resolver: ContentResolver,
        uri: Uri,
        maxLongEdge: Int = 4096
    ): Triple<Int, Int, Pair<ByteArray, Boolean>>? {
        // 1. Read bounds only (0 pixel RAM allocated, ~2 ms)
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, boundsOpts) }
        val origW = boundsOpts.outWidth
        val origH = boundsOpts.outHeight
        if (origW <= 0 || origH <= 0) return null

        // 2. Read EXIF orientation so portrait camera photos stay upright
        val exifOrientation = runCatching {
            resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        // 3. Safe OOM guard: subsample ONLY if photo exceeds 4096px (e.g. 48MP/64MP/108MP camera shots)
        var sample = 1
        var curMax = maxOf(origW, origH)
        while (curMax / sample > maxLongEdge) {
            sample *= 2
        }
        val wasDownscaled = sample > 1

        // 4. Hardware NEON SIMD decode (~90 ms for 12MP!)
        val decOpts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inPremultiplied = false
        }
        var bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decOpts) }
            ?: return null

        // If still slightly above maxLongEdge (e.g. 4200px), scale cleanly
        val decodedMax = maxOf(bmp.width, bmp.height)
        if (decodedMax > maxLongEdge) {
            val scale = maxLongEdge.toFloat() / decodedMax.toFloat()
            val nw = (bmp.width * scale).toInt().coerceAtLeast(1)
            val nh = (bmp.height * scale).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(bmp, nw, nh, true)
            if (scaled != bmp) {
                bmp.recycle()
                bmp = scaled
            }
        }

        // Apply EXIF rotation/flip if needed
        val matrix = Matrix()
        when (exifOrientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
        }
        if (!matrix.isIdentity) {
            val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
            if (rotated != bmp) {
                bmp.recycle()
                bmp = rotated
            }
        }

        val finalW = bmp.width
        val finalH = bmp.height
        val buf = ByteBuffer.allocate(finalW * finalH * 4)
        bmp.copyPixelsToBuffer(buf)
        bmp.recycle()
        return Triple(finalW, finalH, buf.array() to (wasDownscaled || decodedMax > maxLongEdge))
    }

    fun openDocumentFromUri(resolver: ContentResolver, uri: Uri, asLayer: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isBusy = true) }
            runCatching {
                val fileName = resolveDisplayName(resolver, uri) ?: "image.png"
                val lower = fileName.lowercase()
                val isComplexFormat = lower.endsWith(".psd") || lower.endsWith(".psb") ||
                    lower.endsWith(".pcraft") || lower.endsWith(".svg") ||
                    lower.endsWith(".pdf") || lower.endsWith(".exr") ||
                    lower.endsWith(".tif") || lower.endsWith(".tiff")

                var downscaledNote = false
                val info = if (!asLayer && !isComplexFormat) {
                    val fastDecoded = decodeHardwareSafeRgba(resolver, uri, maxLongEdge = 4096)
                    if (fastDecoded != null) {
                        val (w, h, payload) = fastDecoded
                        downscaledNote = payload.second
                        engine.openRgbaBytes(fileName, w.toUInt(), h.toUInt(), payload.first)
                    } else {
                        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: error("Dosya okunamadı")
                        engine.openDocumentBytes(fileName, bytes)
                    }
                } else {
                    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("Dosya okunamadı")
                    if (asLayer) engine.placeImageBytes(fileName, bytes)
                    else engine.openDocumentBytes(fileName, bytes)
                }

                val layers = engine.getLayers()
                _uiState.update {
                    it.copy(
                        docInfo = info,
                        layers = layers,
                        isBusy = false,
                        bwLayerId = if (asLayer) it.bwLayerId else null,
                        invertLayerId = if (asLayer) it.invertLayerId else null,
                        symmetryMode = if (asLayer) it.symmetryMode else "off",
                        rulerEnabled = if (asLayer) it.rulerEnabled else false,
                        activeTool = if (asLayer) it.activeTool else ActiveTool.NONE,
                        fitCameraSignal = if (asLayer) it.fitCameraSignal else it.fitCameraSignal + 1
                    )
                }
                if (asLayer) {
                    NativeSurfaceBridge.nativeRenderFrame()
                }
                val suffix = if (downscaledNote) " · bellek koruması" else ""
                showStatus("Açıldı: ${info.name} (${info.width}×${info.height}$suffix)", 2400L)
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false) }
                showStatus("Açma hatası: ${err.message}", 5000L)
            }
        }
    }

    fun exportDocumentToUri(context: Context, uri: Uri, formatExt: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isBusy = true) }
            showStatus("Kaydediliyor ($formatExt)...", 0L)
            runCatching {
                val bytes = engine.exportDocumentBytes(formatExt)
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: error("Hedef dosyaya yazılamadı")
                val doc = _uiState.value.docInfo
                val previewBytes = if (formatExt.equals("png", true) || formatExt.equals("jpg", true) || formatExt.equals("webp", true)) {
                    null
                } else {
                    runCatching { engine.exportDocumentBytes("jpg") }.getOrNull()
                }
                val updatedGallery = SavedGalleryStore.recordSavedPhoto(
                    context = context,
                    displayName = doc?.name ?: "StonebridgeCraft",
                    formatExt = formatExt,
                    exportedBytes = bytes,
                    previewPngBytes = previewBytes,
                    width = doc?.width?.toInt() ?: 0,
                    height = doc?.height?.toInt() ?: 0
                )
                _uiState.update { it.copy(isBusy = false, savedPhotos = updatedGallery) }
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
