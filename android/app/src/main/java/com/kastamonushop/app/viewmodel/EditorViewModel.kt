package com.kastamonushop.app.viewmodel

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kastamonushop.app.bridge.NativeSurfaceBridge
import kotlinx.coroutines.Dispatchers
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

enum class ActiveTool {
    BRUSH,
    ERASER,
    PAN_ZOOM
}

data class EditorUiState(
    val engineVersion: String = "",
    val docInfo: DocumentInfo? = null,
    val layers: List<LayerSummary> = emptyList(),
    val activeTool: ActiveTool = ActiveTool.BRUSH,
    val brushSize: Float = 28f,
    val brushHardness: Float = 0.85f,
    val brushOpacity: Float = 1.0f,
    val brushFlow: Float = 1.0f,
    val colorHex: String = "#FF4D4F",
    val statusMessage: String? = null,
    val isBusy: Boolean = false
)

class EditorViewModel : ViewModel() {
    private val engine: KastamonuEngine = createKastamonuEngine()

    private val _uiState = MutableStateFlow(
        EditorUiState(engineVersion = engineVersionInfo())
    )
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    init {
        refreshState()
        syncBrushToEngine()
    }

    fun refreshState() {
        viewModelScope.launch(Dispatchers.Default) {
            runCatching {
                val info = engine.getDocumentInfo()
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers) }
                NativeSurfaceBridge.nativeRenderFrame()
                getLastGpuError()?.let { gpuErr -> _uiState.update { it.copy(statusMessage = gpuErr) } }
            }.onFailure { err ->
                _uiState.update { it.copy(statusMessage = err.message) }
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
        viewModelScope.launch(Dispatchers.Default) {
            runCatching {
                engine.setBrush(s.brushSize, s.brushHardness, s.brushOpacity, s.brushFlow, s.colorHex)
            }
        }
    }

    fun commitStroke(points: List<StrokeSample>) {
        if (points.isEmpty()) return
        val s = _uiState.value
        val isEraser = s.activeTool == ActiveTool.ERASER
        viewModelScope.launch(Dispatchers.Default) {
            runCatching {
                engine.commitStroke(points, s.colorHex, isEraser)
                val info = engine.getDocumentInfo()
                _uiState.update { it.copy(docInfo = info) }
                NativeSurfaceBridge.nativeRenderFrame()
                getLastGpuError()?.let { gpuErr -> _uiState.update { it.copy(statusMessage = gpuErr) } }
            }.onFailure { err ->
                _uiState.update { it.copy(statusMessage = "Stroke error: ${err.message}") }
            }
        }
    }

    fun undo() {
        viewModelScope.launch(Dispatchers.Default) {
            runCatching {
                val info = engine.undo()
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers) }
                NativeSurfaceBridge.nativeRenderFrame()
            }
        }
    }

    fun redo() {
        viewModelScope.launch(Dispatchers.Default) {
            runCatching {
                val info = engine.redo()
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers) }
                NativeSurfaceBridge.nativeRenderFrame()
            }
        }
    }

    fun addLayer() {
        viewModelScope.launch(Dispatchers.Default) {
            runCatching {
                val nextIndex = _uiState.value.layers.size + 1
                val layers = engine.addLayer("Katman $nextIndex")
                val info = engine.getDocumentInfo()
                _uiState.update { it.copy(docInfo = info, layers = layers) }
                NativeSurfaceBridge.nativeRenderFrame()
            }
        }
    }

    fun deleteActiveLayer() {
        viewModelScope.launch(Dispatchers.Default) {
            runCatching {
                val layers = engine.deleteActiveLayer()
                val info = engine.getDocumentInfo()
                _uiState.update { it.copy(docInfo = info, layers = layers) }
                NativeSurfaceBridge.nativeRenderFrame()
            }
        }
    }

    fun selectLayer(layerId: ULong) {
        viewModelScope.launch(Dispatchers.Default) {
            runCatching {
                engine.selectLayer(layerId)
                refreshState()
            }
        }
    }

    fun toggleLayerVisibility(layerId: ULong, visible: Boolean) {
        viewModelScope.launch(Dispatchers.Default) {
            runCatching {
                engine.setLayerVisibility(layerId, visible)
                refreshState()
            }
        }
    }

    fun setLayerOpacity(layerId: ULong, opacity: Float) {
        viewModelScope.launch(Dispatchers.Default) {
            runCatching {
                engine.setLayerOpacity(layerId, opacity)
                refreshState()
            }
        }
    }

    fun applyQuickCommand(commandId: String, paramsJson: String = "{}") {
        viewModelScope.launch(Dispatchers.Default) {
            _uiState.update { it.copy(isBusy = true) }
            runCatching {
                engine.executeCommand(commandId, paramsJson)
                val info = engine.getDocumentInfo()
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers, isBusy = false) }
                NativeSurfaceBridge.nativeRenderFrame()
                getLastGpuError()?.let { gpuErr -> _uiState.update { it.copy(statusMessage = gpuErr) } }
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false, statusMessage = err.message) }
            }
        }
    }

    fun openDocumentFromUri(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isBusy = true, statusMessage = "Dosya açılıyor...") }
            runCatching {
                val fileName = resolveDisplayName(resolver, uri) ?: "document.psd"
                val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Dosya okunamadı")
                val info = engine.openDocumentBytes(fileName, bytes)
                val layers = engine.getLayers()
                _uiState.update {
                    it.copy(
                        docInfo = info,
                        layers = layers,
                        isBusy = false,
                        statusMessage = "Açıldı: ${info.name} (${info.width}×${info.height})"
                    )
                }
                NativeSurfaceBridge.nativeRenderFrame()
                getLastGpuError()?.let { gpuErr -> _uiState.update { it.copy(statusMessage = gpuErr) } }
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false, statusMessage = "Açma hatası: ${err.message}") }
            }
        }
    }

    fun exportDocumentToUri(resolver: ContentResolver, uri: Uri, formatExt: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isBusy = true, statusMessage = "Dışa aktarılıyor ($formatExt)...") }
            runCatching {
                val bytes = engine.exportDocumentBytes(formatExt)
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: error("Hedef dosyaya yazılamadı")
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        statusMessage = "Kaydedildi (${bytes.size / 1024} KB)"
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false, statusMessage = "Kayıt hatası: ${err.message}") }
            }
        }
    }

    fun clearStatus() {
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
