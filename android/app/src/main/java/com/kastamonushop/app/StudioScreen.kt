package com.kastamonushop.app

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kastamonushop.app.bridge.NativeSurfaceBridge
import com.kastamonushop.app.data.SavedPhotoItem
import com.kastamonushop.app.ui.canvas.RulerLinePreview
import com.kastamonushop.app.ui.canvas.VulkanCanvasView
import com.kastamonushop.app.viewmodel.ActiveTool
import com.kastamonushop.app.viewmodel.EditorUiState
import com.kastamonushop.app.viewmodel.EditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uniffi.kastamonushop_ffi.PuppetPinPoint
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun StonebridgeStudioScreen(
    vm: EditorViewModel = viewModel(),
    onGoHome: () -> Unit = {}
) {
    val state by vm.uiState.collectAsState()
    val layerThumbs by vm.layerThumbnails.collectAsState()
    val loupeBitmap by vm.loupeBitmap.collectAsState()
    val cinemaThumbs by vm.cinemaThumbs.collectAsState()
    val galleryThumbs by vm.galleryThumbs.collectAsState()
    val lightDistortThumbs by vm.lightDistortThumbs.collectAsState()
    val s = remember { StudioSessionState() }
    val activeLayerPrev by vm.activeLayerPreview.collectAsState()
    val context = LocalContext.current
    val density = LocalDensity.current

    val currentHandPanState by rememberUpdatedState(s.singleFingerPanMode)



    fun pushCurveGpu(y0: Float, y1: Float, y2: Float, y3: Float, y4: Float) {
        val by = (y0 * 255f).roundToInt().coerceIn(0, 255)
        val sy = (y1 * 255f).roundToInt().coerceIn(0, 255)
        val my = (y2 * 255f).roundToInt().coerceIn(0, 255)
        val hy = (y3 * 255f).roundToInt().coerceIn(0, 255)
        val wy = (y4 * 255f).roundToInt().coerceIn(0, 255)
        vm.updateProxyAdjustment("curves", """{"points":[[0,$by],[64,$sy],[128,$my],[192,$hy],[255,$wy]]}""")
    }

    // 4 normalized corners (TL, TR, BR, BL) in document space (0..1) for 60 FPS Perspective Crop

    // Independent active layer Move / Scale / Rotate state

    // Temel + İnce Ayar & Live Brush Size Preview state

    // Layer Effects (fx) state (Temel + İnce Ayar)

    LaunchedEffect(s.brushSizePreviewTick) {
        if (s.brushSizePreviewTick > 0) {
            s.showBrushSizeCircle = true
            kotlinx.coroutines.delay(550L)
            s.showBrushSizeCircle = false
        }
    }

    // Live draggable Text layer editor state
    val dockScrollState = rememberScrollState()
    val drawScrollState = rememberScrollState()
    val selectScrollState = rememberScrollState()
    val adjustScrollState = rememberScrollState()
    val cropScrollState = rememberScrollState()
    val effectScrollState = rememberScrollState()
    // v0.9.5 Camera Light (PhotoCraft native filter.cameraRaw) state

    // v0.9.5 Live Gradient Fill (gradient.fill.create) state

    // v0.9.5 Native Vector Shape (shape.create) state
    // v0.9.8 60 FPS Liquify (photocraft_algo::liquify) state
    // v0.9.9 Cinematic Looks + Vignette/Grain & Motion/Radial Blur state
    // v1.1.0 HSL 8-Channel Mixer, Paint Bucket & Posterize/Threshold state
    // v1.2.0 Filter Gallery (Artistic & Pixelate) state — starts cleanly on 'none' (Orijinal)
    val studioFonts by vm.studioFonts.collectAsState()
    val importFontLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            vm.importCustomFont(context, uri) { newFam ->
                s.textFontFamily = newFam
            }
        }
    }
    val importXmpLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            vm.importXmpPreset(context, uri) { p ->
                s.camExposureVal = p.exposure; s.camContrastVal = p.contrast
                s.camHighlightsVal = p.highlights; s.camShadowsVal = p.shadows
                s.camWhitesVal = p.whites; s.camBlacksVal = p.blacks
                s.camTempVal = p.temperature; s.camTintVal = p.tint
                s.camVibranceVal = p.vibrance; s.camSatVal = p.saturation
                s.camTextureVal = p.texture; s.camClarityVal = p.clarity
                s.camDehazeVal = p.dehaze; s.camSharpenVal = p.sharpenAmount
                s.camNoiseLumVal = p.noiseLuminance; s.camNoiseColVal = p.noiseColor
                s.camGradeShadowHue = p.gradeShadowHue; s.camGradeShadowSat = p.gradeShadowSat
                s.camGradeMidHue = p.gradeMidHue; s.camGradeMidSat = p.gradeMidSat
                s.camGradeHighHue = p.gradeHighHue; s.camGradeHighSat = p.gradeHighSat
                s.camCalRedHue = p.calRedHue; s.camCalRedSat = p.calRedSat
                s.camCalGreenHue = p.calGreenHue; s.camCalGreenSat = p.calGreenSat
                s.camCalBlueHue = p.calBlueHue; s.camCalBlueSat = p.calBlueSat
                s.camVignetteVal = p.vignetteAmount; s.camGrainVal = p.grainAmount
                vm.updateProxyCameraRawJson(buildCameraRawProJson(s))
            }
        }
    }
    val importLutLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            vm.importCubeLutFile(context, uri) { name, text ->
                s.lutFileName = name
                s.lutText = text
                vm.updateProxyColorLookup(name, text, s.lutOpacity)
            }
        }
    }
    val importAbrLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            vm.importAbrFromUri(context.contentResolver, uri)
        }
    }


    // 60 FPS GPU Mirror Effect state (zero CPU lag!)

    // 60 FPS GPU Rotate/Flip preview state inside Kırp & Çevir

    // Crop overlay state

    // HSV color picker state synced with state.colorHex
    val initHsv = remember(state.colorHex) { hexToHsv(state.colorHex) }
    var pickerH by remember(state.colorHex) { mutableFloatStateOf(initHsv[0]) }
    var pickerS by remember(state.colorHex) { mutableFloatStateOf(initHsv[1]) }
    var pickerV by remember(state.colorHex) { mutableFloatStateOf(initHsv[2]) }

    fun pushGpuMirrorPreview(mode: MirrorModeOption, ratio: Float) {
        NativeSurfaceBridge.nativeSetPreview(mode.gpuId, ratio, false, false)
        NativeSurfaceBridge.nativeRenderFrame()
    }

    fun bottomInsetPxForPanel(panel: BottomPanel): Float {
        val fallbackDp = when (panel) {
            BottomPanel.DOCK -> 112f
            BottomPanel.CURVES_LIVE -> 150f
            BottomPanel.DRAW_MENU -> 280f
            BottomPanel.CAMERA_LIGHT_LIVE, BottomPanel.LENS_CORRECTION_EDIT -> 300f
            BottomPanel.LIGHT_LIVE, BottomPanel.COLOR_LIVE, BottomPanel.LEVELS_LIVE, BottomPanel.COLOR_BAL_LIVE -> 250f
            BottomPanel.BLUR_EDIT, BottomPanel.SHARPEN_EDIT, BottomPanel.MIRROR_EDIT, BottomPanel.LAYER_TRANSFORM, BottomPanel.LAYER_FX_EDIT -> 250f
            BottomPanel.PERSPECTIVE_CROP_EDIT, BottomPanel.CROP_RATIO_EDIT, BottomPanel.TEXT_EDIT -> 220f
            else -> 190f
        }
        val fallbackPx = with(density) { fallbackDp.dp.toPx() }
        val extraPadPx = with(density) { 26.dp.toPx() }
        return if (s.measuredBottomBarPx > 10f) {
            s.measuredBottomBarPx + extraPadPx
        } else {
            fallbackPx
        }
    }

    fun fitToSafeArea(turns: Int = s.pendingQuarterTurns, panel: BottomPanel = s.activePanel) {
        val topPx = with(density) { 96.dp.toPx() }
        val botPx = bottomInsetPxForPanel(panel)
        s.canvasViewRef?.fitToDocument(turns, topPx, botPx)
        s.cameraTick++
    }

    fun clearGpuPreview() {
        s.pendingQuarterTurns = 0
        s.pendingFlipH = false
        s.pendingFlipV = false
        NativeSurfaceBridge.nativeSetPreview(0, 0.5f, false, false)
        NativeSurfaceBridge.nativeSetAdjustPreview(0f, 0f, 0f, 0f, 0f, 0f)
        NativeSurfaceBridge.nativeSetTonePreview(0f, 1f, 1f, 0f, 0f, 0f)
        s.curveOverlayExpanded = false
        NativeSurfaceBridge.nativeSetCurvePreview(0.0f, 0.25f, 0.50f, 0.75f, 1.0f, false)
        vm.endProxyPreview()
        fitToSafeArea(0, BottomPanel.DOCK)
    }

    fun clearAdjustGpuPreviewOnly() {
        NativeSurfaceBridge.nativeSetAdjustPreview(0f, 0f, 0f, 0f, 0f, 0f)
        NativeSurfaceBridge.nativeSetTonePreview(0f, 1f, 1f, 0f, 0f, 0f)
        s.curveOverlayExpanded = false
        NativeSurfaceBridge.nativeSetCurvePreview(0.0f, 0.25f, 0.50f, 0.75f, 1.0f, false)
        vm.endProxyPreview()
    }

    fun pushGpuTransformPreview(turns: Int, flipH: Boolean, flipV: Boolean) {
        NativeSurfaceBridge.nativeSetPreview(0, 0.5f, flipH, flipV)
        s.canvasViewRef?.fitToDocument(turns)
    }

    fun dismissLivePanelIfActive() {
        when (s.activePanel) {
            BottomPanel.LIGHT_LIVE, BottomPanel.COLOR_LIVE, BottomPanel.LEVELS_LIVE, BottomPanel.COLOR_BAL_LIVE, BottomPanel.CURVES_LIVE,
            BottomPanel.CAMERA_LIGHT_LIVE, BottomPanel.HSL_MIXER_LIVE, BottomPanel.POSTER_THRESH_LIVE,
            BottomPanel.CINEMA_LOOK_EDIT, BottomPanel.MOTION_BLUR_EDIT, BottomPanel.RADIAL_BLUR_EDIT,
            BottomPanel.FILTER_GALLERY_EDIT, BottomPanel.LIGHT_DISTORT_EDIT, BottomPanel.PUPPET_WARP_EDIT, BottomPanel.GRADIENT_EDIT, BottomPanel.SHAPE_EDIT -> {
                clearAdjustGpuPreviewOnly()
                s.activePanel = BottomPanel.DOCK
            }
            BottomPanel.BLUR_EDIT, BottomPanel.SHARPEN_EDIT -> {
                clearAdjustGpuPreviewOnly()
                s.activePanel = BottomPanel.DOCK
            }
            BottomPanel.MIRROR_EDIT -> {
                clearGpuPreview()
                s.activePanel = BottomPanel.DOCK
            }
            BottomPanel.LAYER_TRANSFORM -> {
                vm.clearActiveLayerPreview()
                s.activePanel = BottomPanel.DOCK
            }
            BottomPanel.CROP_MENU -> {
                if (s.pendingQuarterTurns != 0 || s.pendingFlipH || s.pendingFlipV) {
                    clearGpuPreview()
                }
                s.activePanel = BottomPanel.DOCK
            }
            else -> Unit
        }
    }

    BackHandler(enabled = true) {
        if (s.showSearchPaletteV2) {
            s.showSearchPaletteV2 = false
        } else if (s.activeWorkspaceV2 != null) {
            s.activeWorkspaceV2 = null
        } else if (s.showLayers) {
            s.showLayers = false
        } else if (s.activePanel != BottomPanel.DOCK) {
            when (s.activePanel) {
                BottomPanel.LIGHT_LIVE, BottomPanel.COLOR_LIVE, BottomPanel.LEVELS_LIVE, BottomPanel.COLOR_BAL_LIVE, BottomPanel.CURVES_LIVE -> {
                    s.curveOverlayExpanded = false; clearAdjustGpuPreviewOnly(); vm.endProxyPreview()
                    s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2
                }
                BottomPanel.BLUR_EDIT, BottomPanel.SHARPEN_EDIT -> {
                    s.curveOverlayExpanded = false; clearAdjustGpuPreviewOnly(); vm.endProxyPreview()
                    s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2
                }
                BottomPanel.MIRROR_EDIT -> {
                    clearGpuPreview()
                    s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2
                }
                BottomPanel.CROP_MENU -> {
                    if (s.pendingQuarterTurns != 0 || s.pendingFlipH || s.pendingFlipV) {
                        clearGpuPreview()
                    }
                    s.activePanel = BottomPanel.DOCK
                }
                BottomPanel.CROP_RATIO_EDIT, BottomPanel.PERSPECTIVE_CROP_EDIT -> { s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 }
                BottomPanel.IMAGE_RESIZE_EDIT -> { vm.endProxyPreview(); s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 }
                BottomPanel.PUPPET_WARP_EDIT -> { vm.endProxyPreview(); s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 }
                BottomPanel.LAYER_TRANSFORM -> { vm.clearActiveLayerPreview(); s.activePanel = BottomPanel.DOCK }
                BottomPanel.LAYER_FX_EDIT, BottomPanel.BLEND_IF_EDIT, BottomPanel.GRADIENT_EDIT, BottomPanel.SHAPE_EDIT -> { vm.endProxyPreview(); s.activePanel = BottomPanel.DOCK }
                BottomPanel.CAMERA_LIGHT_LIVE, BottomPanel.LENS_CORRECTION_EDIT, BottomPanel.LUT_IMPORT_EDIT -> { s.curveOverlayExpanded = false; clearAdjustGpuPreviewOnly(); vm.endProxyPreview(); s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 }
                BottomPanel.LIQUIFY_EDIT -> { vm.cancelLiquify(); s.liquifyCursorScreen = null; s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 }
                BottomPanel.AUTO_ENHANCE_MENU -> { s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 }
                BottomPanel.CINEMA_LOOK_EDIT -> { vm.endProxyPreview(); s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 }
                BottomPanel.MOTION_BLUR_EDIT, BottomPanel.RADIAL_BLUR_EDIT, BottomPanel.FILTER_GALLERY_EDIT, BottomPanel.LIGHT_DISTORT_EDIT -> { vm.endProxyPreview(); s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 }
                BottomPanel.QUICK_ACTIONS_MENU -> s.activePanel = BottomPanel.DOCK
                BottomPanel.HSL_MIXER_LIVE, BottomPanel.POSTER_THRESH_LIVE -> { vm.endProxyPreview(); s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 }
                BottomPanel.CONTENT_AWARE_FILL_EDIT -> { vm.endProxyPreview(); s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 }
                BottomPanel.CONTENT_AWARE_MOVE_EDIT -> { vm.endProxyPreview(); s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 }
                BottomPanel.CONTENT_AWARE_SCALE_EDIT -> { vm.endProxyPreview(); s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 }
                BottomPanel.SELECT_COLOR_RANGE_EDIT, BottomPanel.SELECT_SKY_EDIT, BottomPanel.SELECT_FOCUS_AREA_EDIT -> {
                    vm.endProxyPreview(); s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2
                }
                BottomPanel.SELECT_MODIFY_EDIT -> { vm.endProxyPreview(); s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 }
                BottomPanel.TEXT_EDIT -> { vm.endProxyPreview(); s.activePanel = BottomPanel.DOCK }
                BottomPanel.SELECT_MENU, BottomPanel.DRAW_MENU, BottomPanel.RETOUCH_MENU, BottomPanel.LAYER_MASK_EDIT -> {
                    vm.setTool(ActiveTool.NONE); s.activePanel = BottomPanel.DOCK
                }
                else -> { vm.setTool(ActiveTool.NONE); s.activePanel = BottomPanel.DOCK }
            }
        } else {
            s.showExitSaveDialog = true
        }
    }


    LaunchedEffect(
        s.activePanel, s.textEditLayerId, s.textDraft, s.textNormX, s.textNormY, s.textSizePx,
        s.textColorHex, s.textAlign, s.textFontFamily, s.textBold, s.textItalic,
        s.textUnderline, s.textStrike, s.textCaps, s.textHScale, s.textVScale,
        s.textTracking, s.textLineHeight, s.textVertical,
        s.textWarpStyle, s.textWarpBend, s.textWarpHDist, s.textWarpVDist,
        s.textRotationDeg, s.textOpacity, s.textBlendMode,
        s.textStrokeWidth, s.textStrokeColor,
        s.textShadowEnabled, s.textShadowDist, s.textShadowBlur, s.textShadowAngle, s.textShadowColor,
        s.textGlowSize, s.textGlowColor,
        s.textGradientEnabled, s.textGradientEndColor, s.textGradientAngle,
        s.textBgEnabled, s.textBgColor, s.textBgOpacity, s.textBgRadius, s.textBgPadding,
        s.textBaselineShift, s.textBoxWidthNorm
    ) {
        // v2.2.2 Pure Rust WYSIWYG Text Drag Overlay (no fake Compose box, no detached blue dots!)
        if (s.activePanel == BottomPanel.TEXT_EDIT) {
            val docW = (state.docInfo?.width?.toInt() ?: 2048).toFloat()
            val docH = (state.docInfo?.height?.toInt() ?: 2048).toFloat()
            val viewRef = s.canvasViewRef
            val tick = s.cameraTick
            if (viewRef != null && tick >= 0) {
                val currentZoom = viewRef.getCurrentZoom().coerceAtLeast(0.05f)
                val hapticText = androidx.compose.ui.platform.LocalHapticFeedback.current
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (s.singleFingerPanMode) Modifier else Modifier.pointerInput(docW, docH, currentZoom) {
                                detectDragGestures(
                                    onDragEnd = { s.textSnapX = false; s.textSnapY = false },
                                    onDragCancel = { s.textSnapX = false; s.textSnapY = false }
                                ) { change, dragAmount ->
                                    change.consume()
                                    val dxDoc = dragAmount.x / currentZoom
                                    val dyDoc = dragAmount.y / currentZoom
                                    var nx = (s.textNormX + dxDoc / docW).coerceIn(0.02f, 0.98f)
                                    var ny = (s.textNormY + dyDoc / docH).coerceIn(0.02f, 0.98f)
                                    val snapXNow = kotlin.math.abs(nx - 0.5f) < 0.016f
                                    val snapYNow = kotlin.math.abs(ny - 0.5f) < 0.016f
                                    if (snapXNow && !s.textSnapX) hapticText.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    if (snapYNow && !s.textSnapY) hapticText.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    if (snapXNow) nx = 0.5f
                                    if (snapYNow) ny = 0.5f
                                    s.textSnapX = snapXNow
                                    s.textSnapY = snapYNow
                                    s.textNormX = nx
                                    s.textNormY = ny
                                }
                            }
                        )
                ) {
                    if (s.textSnapX || s.textSnapY) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val (cx, cy) = viewRef.docToScreen(docW * 0.5f, docH * 0.5f)
                            if (s.textSnapX) {
                                drawLine(Color(0xCCF59E0B), Offset(cx, 0f), Offset(cx, size.height), strokeWidth = 2f)
                            }
                            if (s.textSnapY) {
                                drawLine(Color(0xCCF59E0B), Offset(0f, cy), Offset(size.width, cy), strokeWidth = 2f)
                            }
                        }
                    }
                }
            }
        }
        if (s.activePanel == BottomPanel.PUPPET_WARP_EDIT) {
            PuppetWarpCanvasOverlay(
                canvasViewRef = s.canvasViewRef,
                cameraTick = s.cameraTick,
                docW = (state.docInfo?.width?.toInt() ?: 2048).toFloat(),
                docH = (state.docInfo?.height?.toInt() ?: 2048).toFloat(),
                pins = s.puppetPins,
                selectedIdx = s.puppetSelectedIdx,
                showMesh = s.puppetShowMesh,
                meshSegments = s.puppetMeshSegments,
                handPanEnabled = s.singleFingerPanMode,
                onSelectPin = { s.puppetSelectedIdx = it },
                onPushUndo = { snapshot ->
                    s.puppetUndoStack = (s.puppetUndoStack + listOf(snapshot)).takeLast(40)
                    s.puppetRedoStack = emptyList()
                },
                onAddPin = { dx, dy ->
                    s.puppetUndoStack = (s.puppetUndoStack + listOf(s.puppetPins)).takeLast(40)
                    s.puppetRedoStack = emptyList()
                    val next = s.puppetPins + PuppetPinPoint(dx, dy, dx, dy, null, 0)
                    s.puppetPins = next
                    s.puppetSelectedIdx = next.lastIndex
                },
                onDeletePin = { idx ->
                    if (idx in s.puppetPins.indices) {
                        s.puppetUndoStack = (s.puppetUndoStack + listOf(s.puppetPins)).takeLast(40)
                        s.puppetRedoStack = emptyList()
                        val next = s.puppetPins.toMutableList().apply { removeAt(idx) }
                        s.puppetPins = next
                        s.puppetSelectedIdx = if (next.isEmpty()) -1 else (idx.coerceAtMost(next.lastIndex))
                        if (next.isEmpty()) vm.resetProxyPreview() else vm.updateProxyPuppetWarp(next, s.puppetMode, s.puppetDensity, s.puppetExpansion)
                    }
                },
                onMovePin = { idx, nx, ny ->
                    if (idx in s.puppetPins.indices) {
                        val updated = s.puppetPins.toMutableList()
                        val old = updated[idx]
                        updated[idx] = PuppetPinPoint(old.srcX, old.srcY, nx, ny, old.rotateDeg, old.depth)
                        s.puppetPins = updated
                        vm.updateProxyPuppetWarp(updated, s.puppetMode, s.puppetDensity, s.puppetExpansion)
                        if (s.puppetShowMesh) vm.fetchPuppetMeshSegments(updated, s.puppetMode, s.puppetDensity, s.puppetExpansion) { s.puppetMeshSegments = it }
                    }
                },
                onRestorePins = { prev ->
                    s.puppetPins = prev
                    if (prev.isEmpty()) vm.resetProxyPreview() else vm.updateProxyPuppetWarp(prev, s.puppetMode, s.puppetDensity, s.puppetExpansion)
                },
                onPinchPan = { dPanX, dPanY, zoomScale, cx, cy ->
                    s.canvasViewRef?.applyExternalPinchPan(zoomScale, dPanX, dPanY)
                    s.cameraTick++
                }
            )
        }

        // v0.9.5 Interactive On-Canvas Gradient Start/End Drag Overlay (60 FPS Live Proxy)
        if (s.activePanel == BottomPanel.GRADIENT_EDIT) {
            val docW = (state.docInfo?.width?.toInt() ?: 2048).toFloat()
            val docH = (state.docInfo?.height?.toInt() ?: 2048).toFloat()
            val viewRef = s.canvasViewRef
            val tick = s.cameraTick
            if (viewRef != null && tick >= 0) {
                val (ax, ay) = viewRef.docToScreen(docW * s.gradFromNormX, docH * s.gradFromNormY)
                val (bx, by) = viewRef.docToScreen(docW * s.gradToNormX, docH * s.gradToNormY)
                val startCol = remember(s.gradStartHex) { runCatching { Color(AndroidColor.parseColor(s.gradStartHex)) }.getOrDefault(Color(0xFF4F9CFF)) }
                val endCol = remember(s.gradEndHex) { runCatching { Color(AndroidColor.parseColor(s.gradEndHex)) }.getOrDefault(Color(0xFFFF4D4F)) }
                androidx.compose.foundation.Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (s.singleFingerPanMode) Modifier else Modifier.pointerInput(docW, docH, s.gradStyle, s.gradStartHex, s.gradEndHex, s.gradOpacity) {
                            detectDragGestures(
                                onDragStart = { pos ->
                                    val dA = kotlin.math.hypot(pos.x - ax, pos.y - ay)
                                    val dB = kotlin.math.hypot(pos.x - bx, pos.y - by)
                                    val (dx, dy) = viewRef.screenToDoc(pos.x, pos.y)
                                    if (dA > 70f && dB > 70f) {
                                        s.gradFromNormX = (dx / docW).coerceIn(0f, 1f)
                                        s.gradFromNormY = (dy / docH).coerceIn(0f, 1f)
                                        s.gradToNormX = s.gradFromNormX
                                        s.gradToNormY = s.gradFromNormY
                                    }
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val p = change.position
                                    val (dx, dy) = viewRef.screenToDoc(p.x, p.y)
                                    val nx = (dx / docW).coerceIn(0f, 1f)
                                    val ny = (dy / docH).coerceIn(0f, 1f)
                                    val (curAx, curAy) = viewRef.docToScreen(docW * s.gradFromNormX, docH * s.gradFromNormY)
                                    val (curBx, curBy) = viewRef.docToScreen(docW * s.gradToNormX, docH * s.gradToNormY)
                                    if (kotlin.math.hypot(p.x - curAx, p.y - curAy) < kotlin.math.hypot(p.x - curBx, p.y - curBy)) {
                                        s.gradFromNormX = nx
                                        s.gradFromNormY = ny
                                    } else {
                                        s.gradToNormX = nx
                                        s.gradToNormY = ny
                                    }
                                    vm.updateProxyGradient(
                                        docW * s.gradFromNormX, docH * s.gradFromNormY,
                                        docW * s.gradToNormX, docH * s.gradToNormY,
                                        s.gradStyle, s.gradStartHex, s.gradEndHex, s.gradOpacity
                                    )
                                }
                            )
                        })
                ) {
                    drawLine(Color.White.copy(alpha = 0.85f), Offset(ax, ay), Offset(bx, by), strokeWidth = 3f)
                    drawCircle(Color.White, radius = 16f, center = Offset(ax, ay))
                    drawCircle(startCol, radius = 12f, center = Offset(ax, ay))
                    drawCircle(Color.White, radius = 16f, center = Offset(bx, by))
                    drawCircle(endCol, radius = 12f, center = Offset(bx, by))
                }
            }
        }

        // v0.9.5 Interactive On-Canvas Vector Shape Draw/Resize Overlay (60 FPS Live Proxy)
        if (s.activePanel == BottomPanel.SHAPE_EDIT) {
            val docW = (state.docInfo?.width?.toInt() ?: 2048).toFloat()
            val docH = (state.docInfo?.height?.toInt() ?: 2048).toFloat()
            val viewRef = s.canvasViewRef
            val handleHitPx = with(density) { 26.dp.toPx() }
            if (viewRef != null) {
                androidx.compose.foundation.Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (s.singleFingerPanMode) Modifier else Modifier.pointerInput(docW, docH, s.shapeKind, s.shapeFillHex, s.shapeStrokeHex, s.shapeStrokeWidth, s.shapeCornerRadius, s.shapeSides) {
                            var dragMode = 5 // 1=TL, 2=TR, 3=BR, 4=BL, 5=rigid move
                            var lastDocX = 0f
                            var lastDocY = 0f
                            detectDragGestures(
                                onDragStart = { pos ->
                                    val (c0x, c0y) = viewRef.docToScreen(docW * s.shapeX0Norm, docH * s.shapeY0Norm)
                                    val (c1x, c1y) = viewRef.docToScreen(docW * s.shapeX1Norm, docH * s.shapeY1Norm)
                                    val (dx, dy) = viewRef.screenToDoc(pos.x, pos.y)
                                    lastDocX = dx
                                    lastDocY = dy
                                    if (s.shapeKind == "line") {
                                        val d0 = kotlin.math.hypot(pos.x - c0x, pos.y - c0y)
                                        val d1 = kotlin.math.hypot(pos.x - c1x, pos.y - c1y)
                                        dragMode = when {
                                            d0 <= handleHitPx -> 1
                                            d1 <= handleHitPx -> 3
                                            else -> 5
                                        }
                                    } else {
                                        // Normalize so x0<=x1 and y0<=y1 for clean 4-corner handles
                                        val nx0 = min(s.shapeX0Norm, s.shapeX1Norm)
                                        val nx1 = kotlin.math.max(s.shapeX0Norm, s.shapeX1Norm)
                                        val ny0 = min(s.shapeY0Norm, s.shapeY1Norm)
                                        val ny1 = kotlin.math.max(s.shapeY0Norm, s.shapeY1Norm)
                                        s.shapeX0Norm = nx0; s.shapeX1Norm = nx1; s.shapeY0Norm = ny0; s.shapeY1Norm = ny1
                                        val (lx, ty) = viewRef.docToScreen(docW * nx0, docH * ny0)
                                        val (rx, by) = viewRef.docToScreen(docW * nx1, docH * ny1)
                                        val pts = listOf(Offset(lx, ty), Offset(rx, ty), Offset(rx, by), Offset(lx, by))
                                        var best = 5
                                        var bestD = handleHitPx
                                        pts.forEachIndexed { idx, pt ->
                                            val d = kotlin.math.hypot(pos.x - pt.x, pos.y - pt.y)
                                            if (d < bestD) { bestD = d; best = idx + 1 }
                                        }
                                        dragMode = best
                                    }
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val (dx, dy) = viewRef.screenToDoc(change.position.x, change.position.y)
                                    val nx = (dx / docW).coerceIn(0f, 1f)
                                    val ny = (dy / docH).coerceIn(0f, 1f)
                                    when (dragMode) {
                                        1 -> { s.shapeX0Norm = nx; s.shapeY0Norm = ny } // TL
                                        2 -> { s.shapeX1Norm = nx; s.shapeY0Norm = ny } // TR
                                        3 -> { s.shapeX1Norm = nx; s.shapeY1Norm = ny } // BR
                                        4 -> { s.shapeX0Norm = nx; s.shapeY1Norm = ny } // BL
                                        else -> {
                                            val dnx = (dx - lastDocX) / docW
                                            val dny = (dy - lastDocY) / docH
                                            lastDocX = dx
                                            lastDocY = dy
                                            val bx0 = min(s.shapeX0Norm, s.shapeX1Norm)
                                            val bx1 = kotlin.math.max(s.shapeX0Norm, s.shapeX1Norm)
                                            val by0 = min(s.shapeY0Norm, s.shapeY1Norm)
                                            val by1 = kotlin.math.max(s.shapeY0Norm, s.shapeY1Norm)
                                            val cdx = dnx.coerceIn(-0.2f - bx0, 1.2f - bx1)
                                            val cdy = dny.coerceIn(-0.2f - by0, 1.2f - by1)
                                            s.shapeX0Norm += cdx
                                            s.shapeX1Norm += cdx
                                            s.shapeY0Norm += cdy
                                            s.shapeY1Norm += cdy
                                        }
                                    }
                                    vm.updateProxyShape(
                                        s.shapeKind,
                                        docW * s.shapeX0Norm, docH * s.shapeY0Norm,
                                        docW * s.shapeX1Norm, docH * s.shapeY1Norm,
                                        s.shapeFillHex, s.shapeStrokeHex, s.shapeStrokeWidth, s.shapeCornerRadius, s.shapeSides
                                    )
                                }
                            )
                        })
                ) {
                    val _camT = s.cameraTick
                    if (_camT < 0) return@Canvas
                    val (sx0, sy0) = viewRef.docToScreen(docW * s.shapeX0Norm, docH * s.shapeY0Norm)
                    val (sx1, sy1) = viewRef.docToScreen(docW * s.shapeX1Norm, docH * s.shapeY1Norm)
                    val lx = min(sx0, sx1)
                    val rx = kotlin.math.max(sx0, sx1)
                    val ty = min(sy0, sy1)
                    val by = kotlin.math.max(sy0, sy1)
                    val rw = abs(sx1 - sx0).coerceAtLeast(4f)
                    val rh = abs(sy1 - sy0).coerceAtLeast(4f)
                    if (s.shapeKind == "line") {
                        drawLine(Color(0xCC4F9CFF), Offset(sx0, sy0), Offset(sx1, sy1), strokeWidth = 2.5f)
                        listOf(Offset(sx0, sy0), Offset(sx1, sy1)).forEach { pt ->
                            drawCircle(Color.White, radius = 10f, center = pt)
                            drawCircle(Color(0xFF4F9CFF), radius = 6f, center = pt)
                        }
                    } else {
                        drawRect(
                            color = Color(0xCC4F9CFF),
                            topLeft = Offset(lx, ty),
                            size = androidx.compose.ui.geometry.Size(rw, rh),
                            style = Stroke(width = 2f)
                        )
                        listOf(Offset(lx, ty), Offset(rx, ty), Offset(rx, by), Offset(lx, by)).forEach { pt ->
                            drawCircle(Color.White, radius = 10f, center = pt)
                            drawCircle(Color(0xFF4F9CFF), radius = 6f, center = pt)
                        }
                    }
                }
            }
        }

        // v0.9.8 Interactive 60 FPS On-Canvas Liquify Drag Overlay
        if (s.activePanel == BottomPanel.LIQUIFY_EDIT) {
            val viewRef = s.canvasViewRef
            if (viewRef != null) {
                androidx.compose.foundation.Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (s.singleFingerPanMode) Modifier else Modifier.pointerInput(s.liquifyTool, s.liquifySize, s.liquifyPressure) {
                            awaitPointerEventScope {
                                while (true) {
                                    val firstDown = awaitFirstDown(requireUnconsumed = false)
                                var singleStrokeActive = false
                                var pinchActive = false
                                var prevDist = 0f
                                var prevCentroid = Offset.Zero
                                val downPos = firstDown.position

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val pressed = event.changes.filter { it.pressed }
                                    if (pressed.isEmpty()) {
                                        if (singleStrokeActive) {
                                            vm.onLiquifyEnd()
                                        }
                                        s.liquifyCursorScreen = null
                                        break
                                    }
                                    if (pressed.size >= 2) {
                                        if (singleStrokeActive) {
                                            vm.onLiquifyEnd()
                                            singleStrokeActive = false
                                        }
                                        s.liquifyCursorScreen = null
                                        val p0 = pressed[0].position
                                        val p1 = pressed[1].position
                                        val dist = kotlin.math.hypot((p1.x - p0.x).toDouble(), (p1.y - p0.y).toDouble()).toFloat().coerceAtLeast(1f)
                                        val centroid = Offset((p0.x + p1.x) * 0.5f, (p0.y + p1.y) * 0.5f)
                                        if (pinchActive && prevDist > 1f) {
                                            val zf = (dist / prevDist).coerceIn(0.85f, 1.18f)
                                            viewRef.applyExternalPinchPan(zf, centroid.x - prevCentroid.x, centroid.y - prevCentroid.y)
                                        }
                                        prevDist = dist
                                        prevCentroid = centroid
                                        pinchActive = true
                                        pressed.forEach { it.consume() }
                                    } else if (!pinchActive) {
                                        val ch = pressed[0]
                                        val pos = ch.position
                                        ch.consume()
                                        if (!singleStrokeActive) {
                                            val moved = kotlin.math.hypot((pos.x - downPos.x).toDouble(), (pos.y - downPos.y).toDouble()).toFloat()
                                            if (moved > 3f || s.liquifyTool == "bloat" || s.liquifyTool == "pucker" || s.liquifyTool == "twirlCw") {
                                                singleStrokeActive = true
                                                s.liquifyCursorScreen = pos
                                                val (dx0, dy0) = viewRef.screenToDoc(downPos.x, downPos.y)
                                                vm.onLiquifyStart(s.liquifyTool, s.liquifySize, s.liquifyPressure, 55f, dx0, dy0)
                                                val (dx, dy) = viewRef.screenToDoc(pos.x, pos.y)
                                                vm.onLiquifyMove(dx, dy)
                                            }
                                        } else {
                                            s.liquifyCursorScreen = pos
                                            val (dx, dy) = viewRef.screenToDoc(pos.x, pos.y)
                                            vm.onLiquifyMove(dx, dy)
                                        }
                                    }
                                }
                                }
                            }
                        }
                        )
                ) {
                    val cur = s.liquifyCursorScreen
                    if (cur != null) {
                        val rPx = (s.liquifySize * 0.5f * viewRef.getCurrentZoom()).coerceIn(10f, 360f)
                        drawCircle(Color.White.copy(alpha = 0.85f), radius = rPx, center = cur, style = Stroke(width = 2.2f))
                        drawCircle(Color(0x884F9CFF), radius = (rPx - 2f).coerceAtLeast(4f), center = cur, style = Stroke(width = 1.2f))
                    }
                }
            }
        }

        // v0.9.9.5 Floating Touch-Point Magnifier / Loupe (Fine Brush, Eyedropper, Clone Stamp, Perspective Crop)
        val lPos = s.loupeScreenPos
        val lBmp = loupeBitmap
        if (lPos != null && lBmp != null) {
            FloatingLoupeOverlay(
                fingerScreenPos = lPos,
                loupeBitmap = lBmp,
                isEyedropper = state.activeTool == ActiveTool.EYEDROPPER,
                eyedropperColorHex = state.colorHex
            )
        }

        // Exit Save Confirmation Dialog ("Bu fotoğrafı kaydetmek istiyor musun?")
        if (s.showExitSaveDialog) {
            AlertDialog(
                onDismissRequest = { s.showExitSaveDialog = false },
                containerColor = Color(0xFF232530),
                titleContentColor = Color.White,
                textContentColor = Color(0xFFD8DCE8),
                title = { Text("Fotoğrafı Kaydet", fontWeight = FontWeight.SemiBold) },
                text = { Text("Bu fotoğrafı kaydetmek istiyor musun? Evet dersen Kaydedilen Fotoğraflar bölümüne eklenir.") },
                confirmButton = {
                    Button(
                        onClick = {
                            s.showExitSaveDialog = false
                            clearGpuPreview()
                            vm.quickSaveToGallery(context) {
                                onGoHome()
                            }
                        }
                    ) {
                        Text("Evet")
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = {
                            s.showExitSaveDialog = false
                            clearGpuPreview()
                            onGoHome()
                        }
                    ) {
                        Text("Hayır", color = Color.White)
                    }
                }
            )
        }

        // Studio v2.0 Minimal Floating OLED Top Bar (with Tek El + Ortala built-in)
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = StudioV2Colors.GlassBar,
            border = androidx.compose.foundation.BorderStroke(1.dp, StudioV2Colors.GlassBorder),
            tonalElevation = 6.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TopBarButton(
                    icon = Icons.Default.Home,
                    label = "Ana",
                    onClick = {
                        dismissLivePanelIfActive()
                        s.showExitSaveDialog = true
                    }
                )

                TopBarButton(
                    icon = Icons.AutoMirrored.Filled.Undo,
                    label = "Geri",
                    enabled = when (s.activePanel) {
                        BottomPanel.LIQUIFY_EDIT -> state.liquifyCanUndo
                        BottomPanel.PUPPET_WARP_EDIT -> s.puppetUndoStack.isNotEmpty()
                        BottomPanel.CURVES_LIVE -> s.curveUndoStack.isNotEmpty()
                        else -> state.docInfo?.canUndo == true
                    },
                    onClick = {
                        when (s.activePanel) {
                            BottomPanel.LIQUIFY_EDIT -> vm.liquifyUndo()
                            BottomPanel.CURVES_LIVE -> {
                                if (s.curveUndoStack.isNotEmpty()) {
                                    val prev = s.curveUndoStack.last()
                                    s.curveRedoStack = s.curveRedoStack + s.snapshotCurve()
                                    s.curveUndoStack = s.curveUndoStack.dropLast(1)
                                    s.restoreCurve(prev)
                                    fun fmtPts(pts: List<Float>): String {
                                        val xs = listOf(0, 64, 128, 192, 255)
                                        return xs.indices.joinToString(",", "[", "]") { i -> "[${xs[i]},${(pts[i] * 255f).roundToInt().coerceIn(0, 255)}]" }
                                    }
                                    val mPts = listOf(s.curveBlackVal, s.curveShadowVal, s.curveMidVal, s.curveHighVal, s.curveWhiteVal)
                                    val json = """{"points":${fmtPts(mPts)},"red":${fmtPts(s.curveRedPts)},"green":${fmtPts(s.curveGreenPts)},"blue":${fmtPts(s.curveBluePts)}}"""
                                    vm.updateProxyAdjustment("curves", json)
                                }
                            }
                            BottomPanel.PUPPET_WARP_EDIT -> {
                                if (s.puppetUndoStack.isNotEmpty()) {
                                    val prev = s.puppetUndoStack.last()
                                    s.puppetRedoStack = s.puppetRedoStack + listOf(s.puppetPins)
                                    s.puppetUndoStack = s.puppetUndoStack.dropLast(1)
                                    s.puppetPins = prev
                                    s.puppetSelectedIdx = s.puppetSelectedIdx.coerceIn(-1, prev.lastIndex)
                                    if (prev.isEmpty()) vm.resetProxyPreview() else vm.updateProxyPuppetWarp(prev, s.puppetMode, s.puppetDensity, s.puppetExpansion)
                                }
                            }
                            else -> {
                                if (s.activePanel == BottomPanel.MIRROR_EDIT || s.activePanel == BottomPanel.CROP_MENU) {
                                    dismissLivePanelIfActive()
                                }
                                vm.undo()
                            }
                        }
                    }
                )

                TopBarButton(
                    icon = Icons.AutoMirrored.Filled.Redo,
                    label = "İleri",
                    enabled = when (s.activePanel) {
                        BottomPanel.LIQUIFY_EDIT -> state.liquifyCanRedo
                        BottomPanel.PUPPET_WARP_EDIT -> s.puppetRedoStack.isNotEmpty()
                        BottomPanel.CURVES_LIVE -> s.curveRedoStack.isNotEmpty()
                        else -> state.docInfo?.canRedo == true
                    },
                    onClick = {
                        when (s.activePanel) {
                            BottomPanel.LIQUIFY_EDIT -> vm.liquifyRedo()
                            BottomPanel.CURVES_LIVE -> {
                                if (s.curveRedoStack.isNotEmpty()) {
                                    val next = s.curveRedoStack.last()
                                    s.curveUndoStack = s.curveUndoStack + s.snapshotCurve()
                                    s.curveRedoStack = s.curveRedoStack.dropLast(1)
                                    s.restoreCurve(next)
                                    fun fmtPts(pts: List<Float>): String {
                                        val xs = listOf(0, 64, 128, 192, 255)
                                        return xs.indices.joinToString(",", "[", "]") { i -> "[${xs[i]},${(pts[i] * 255f).roundToInt().coerceIn(0, 255)}]" }
                                    }
                                    val mPts = listOf(s.curveBlackVal, s.curveShadowVal, s.curveMidVal, s.curveHighVal, s.curveWhiteVal)
                                    val json = """{"points":${fmtPts(mPts)},"red":${fmtPts(s.curveRedPts)},"green":${fmtPts(s.curveGreenPts)},"blue":${fmtPts(s.curveBluePts)}}"""
                                    vm.updateProxyAdjustment("curves", json)
                                }
                            }
                            BottomPanel.PUPPET_WARP_EDIT -> {
                                if (s.puppetRedoStack.isNotEmpty()) {
                                    val next = s.puppetRedoStack.last()
                                    s.puppetUndoStack = s.puppetUndoStack + listOf(s.puppetPins)
                                    s.puppetRedoStack = s.puppetRedoStack.dropLast(1)
                                    s.puppetPins = next
                                    s.puppetSelectedIdx = s.puppetSelectedIdx.coerceIn(-1, next.lastIndex)
                                    if (next.isEmpty()) vm.resetProxyPreview() else vm.updateProxyPuppetWarp(next, s.puppetMode, s.puppetDensity, s.puppetExpansion)
                                }
                            }
                            else -> {
                                if (s.activePanel == BottomPanel.MIRROR_EDIT || s.activePanel == BottomPanel.CROP_MENU) {
                                    dismissLivePanelIfActive()
                                }
                                vm.redo()
                            }
                        }
                    }
                )

                TopBarButton(
                    icon = Icons.Default.PanTool,
                    label = "Tek El",
                    selected = s.singleFingerPanMode,
                    onClick = { s.singleFingerPanMode = !s.singleFingerPanMode }
                )

                TopBarButton(
                    icon = Icons.Default.CenterFocusStrong,
                    label = "Ortala",
                    onClick = { fitToSafeArea(s.pendingQuarterTurns, s.activePanel) }
                )

                TopBarButton(
                    icon = Icons.Default.Compare,
                    label = "Önce",
                    selected = state.compareOriginal,
                    onClick = { vm.toggleCompareOriginal() }
                )

                TopBarButton(
                    icon = Icons.Default.Layers,
                    label = "Katman",
                    selected = s.showLayers,
                    onClick = { s.showLayers = !s.showLayers }
                )

                Box {
                    TopBarButton(
                        icon = Icons.Default.Save,
                        label = "Kaydet",
                        onClick = { s.showExportMenu = true }
                    )
                    DropdownMenu(
                        expanded = s.showExportMenu,
                        onDismissRequest = { s.showExportMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Cihaz Galerisine Kaydet (PNG)", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary) },
                            onClick = {
                                s.showExportMenu = false
                                vm.saveToDeviceGallery(context)
                            }
                        )
                        HorizontalDivider()
                        listOf(
                            "png" to "PNG Dosya Olarak Kaydet (.png)",
                            "jpg" to "JPEG Resim (.jpg)",
                            "psd" to "Photoshop Katmanlı (.psd)",
                            "pcraft" to "StonebridgeCraft Proje (.pcraft)",
                            "webp" to "WebP (.webp)"
                        ).forEach { (ext, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    s.showExportMenu = false
                                    s.pendingExportFormat = ext
                                    exportDocLauncher.launch("stonebridgecraft.$ext")
                                }
                            )
                        }
                    }
                }

                Box {
                    TopBarButton(
                        icon = Icons.Default.MoreVert,
                        label = "Diğer",
                        selected = s.singleFingerPanMode,
                        onClick = { s.showOpenMenu = true }
                    )
                    DropdownMenu(
                        expanded = s.showOpenMenu,
                        onDismissRequest = { s.showOpenMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Fotoğraf Aç (Galeri)") },
                            onClick = {
                                s.showOpenMenu = false
                                s.openAsLayer = false
                                val pick = Intent(Intent.ACTION_GET_CONTENT).apply {
                                    type = "image/*"
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                }
                                imageChooserLauncher.launch(Intent.createChooser(pick, "Fotoğraf Seç"))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Fotoğrafı Katman Olarak Ekle") },
                            onClick = {
                                s.showOpenMenu = false
                                s.openAsLayer = true
                                val pick = Intent(Intent.ACTION_GET_CONTENT).apply {
                                    type = "image/*"
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                }
                                imageChooserLauncher.launch(Intent.createChooser(pick, "Katman İçin Fotoğraf Seç"))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Proje Aç (.psd / .pcraft)") },
                            onClick = {
                                s.showOpenMenu = false
                                s.openAsLayer = false
                                openDocLauncher.launch(arrayOf("*/*"))
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(if (s.singleFingerPanMode) "Tek Parmak Kaydır: AÇIK ✓" else "Tek Parmak Kaydır Modu") },
                            onClick = {
                                s.showOpenMenu = false
                                s.singleFingerPanMode = !s.singleFingerPanMode
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Fotoğrafı Ekrana Ortala") },
                            onClick = {
                                s.showOpenMenu = false
                                fitToSafeArea(s.pendingQuarterTurns, s.activePanel)
                            }
                        )
                    }
                }
            }
        }

        LaunchedEffect(s.showLayers) {
            vm.setLayersDrawerOpen(s.showLayers)
        }
        LaunchedEffect(s.showLayers, state.layers, state.docInfo) {
            if (s.showLayers) {
                vm.refreshLayerThumbnails(force = true)
            }
        }

        // Floating Quick Mask Mode control bar (v1.8.2)
        if (state.quickMaskMode) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 74.dp),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xEE2B1620),
                tonalElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Hızlı Maske", color = Color(0xFFFF6B81), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    ProPill(
                        selected = state.colorHex.equals("#000000", ignoreCase = true),
                        onClick = { vm.updateBrush(colorHex = "#000000"); vm.setTool(ActiveTool.BRUSH) },
                        label = { Text("Çıkar", fontSize = 11.sp) }
                    )
                    ProPill(
                        selected = state.colorHex.equals("#FFFFFF", ignoreCase = true),
                        onClick = { vm.updateBrush(colorHex = "#FFFFFF"); vm.setTool(ActiveTool.BRUSH) },
                        label = { Text("Ekle", fontSize = 11.sp) }
                    )
                    TextButton(onClick = { vm.toggleQuickMaskMode(false) }) {
                        Text("Seçime Dön", color = Color(0xFFFFD600), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Floating Mask Paint Mode control bar (Siyah: Gizle / Beyaz: Göster / Bitir)
        if (state.maskPaintMode) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 74.dp),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xEE1E2636),
                tonalElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Maske", color = Color(0xFF4F9CFF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    ProPill(
                        selected = state.colorHex.equals("#000000", ignoreCase = true),
                        onClick = { vm.updateBrush(colorHex = "#000000"); vm.setTool(ActiveTool.BRUSH) },
                        label = { Text("Gizle", fontSize = 11.sp) }
                    )
                    ProPill(
                        selected = state.colorHex.equals("#FFFFFF", ignoreCase = true),
                        onClick = { vm.updateBrush(colorHex = "#FFFFFF"); vm.setTool(ActiveTool.BRUSH) },
                        label = { Text("Göster", fontSize = 11.sp) }
                    )
                    TextButton(onClick = { vm.exitMaskPaintMode() }) {
                        Text("Bitir", color = Color(0xFFFFD600), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Safety pill if every layer is currently hidden
        if (state.layers.isNotEmpty() && state.layers.none { it.visible }) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 74.dp)
                    .clickable {
                        val target = state.layers.lastOrNull { it.isBackground } ?: state.layers.last()
                        vm.toggleLayerVisibility(target.id, true)
                    },
                shape = RoundedCornerShape(18.dp),
                color = Color(0xEE3A2418),
                tonalElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = Color(0xFFFFD600), modifier = Modifier.size(16.dp))
                    Text("Tüm katmanlar gizli — Göster", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Interactive On-Canvas Drag Overlay for Content-Aware Move / Extend (v1.8.0)
        if (s.activePanel == BottomPanel.CONTENT_AWARE_MOVE_EDIT) {
            val view = s.canvasViewRef
            val bounds = state.selectionBounds
            if (view != null && bounds != null) {
                val _tick = s.cameraTick
                val z = view.getCurrentZoom().coerceAtLeast(0.05f)
                val (ox0, oy0) = view.docToScreen(bounds.x0.toFloat(), bounds.y0.toFloat())
                val (ox1, oy1) = view.docToScreen(bounds.x1.toFloat(), bounds.y1.toFloat())
                val cxDoc = (bounds.x0 + bounds.x1) * 0.5f + s.camMoveDx
                val cyDoc = (bounds.y0 + bounds.y1) * 0.5f + s.camMoveDy
                val wDoc = (bounds.x1 - bounds.x0).coerceAtLeast(8) * (s.camMoveScale / 100f)
                val hDoc = (bounds.y1 - bounds.y0).coerceAtLeast(8) * (s.camMoveScale / 100f)
                val (tCx, tCy) = view.docToScreen(cxDoc, cyDoc)
                val hw = wDoc * z * 0.5f
                val hh = hDoc * z * 0.5f
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (s.singleFingerPanMode) Modifier else Modifier.pointerInput(bounds, z) {
                                detectDragGestures(
                                    onDragEnd = {
                                        vm.updateProxyContentAwareMove(
                                            s.camMoveDx, s.camMoveDy, s.camMoveMode,
                                            s.camMoveStructure.roundToInt(), s.camMoveColor.roundToInt(),
                                            s.camMoveScale, s.camMoveRotate, s.camMoveSampleAll
                                        )
                                    }
                                ) { change, dragAmount ->
                                    change.consume()
                                    s.camMoveDx += dragAmount.x / z
                                    s.camMoveDy += dragAmount.y / z
                                }
                            }
                        )
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // Original selection dashed box
                        drawRect(
                            color = Color(0x88FFFFFF),
                            topLeft = Offset(ox0, oy0),
                            size = Size((ox1 - ox0).coerceAtLeast(2f), (oy1 - oy0).coerceAtLeast(2f)),
                            style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
                        )
                        // Destination box (rotated & scaled around destination center)
                        val rad = Math.toRadians(s.camMoveRotate.toDouble())
                        val cosR = kotlin.math.cos(rad).toFloat()
                        val sinR = kotlin.math.sin(rad).toFloat()
                        fun rotPt(lx: Float, ly: Float) = Offset(tCx + lx * cosR - ly * sinR, tCy + lx * sinR + ly * cosR)
                        val p0 = rotPt(-hw, -hh); val p1 = rotPt(hw, -hh)
                        val p2 = rotPt(hw, hh); val p3 = rotPt(-hw, hh)
                        val quad = Path().apply { moveTo(p0.x, p0.y); lineTo(p1.x, p1.y); lineTo(p2.x, p2.y); lineTo(p3.x, p3.y); close() }
                        val accent = if (s.camMoveMode == "extend") Color(0xFF2ED573) else Color(0xFF4F9CFF)
                        drawPath(quad, color = accent.copy(alpha = 0.16f))
                        drawPath(quad, color = accent, style = Stroke(width = 3f))
                        drawLine(accent.copy(alpha = 0.7f), Offset((ox0 + ox1) * 0.5f, (oy0 + oy1) * 0.5f), Offset(tCx, tCy), strokeWidth = 2f)
                        drawCircle(accent, radius = 7f, center = Offset(tCx, tCy))
                    }
                }
            }
        }

        // Right Floating Layers Drawer
        if (s.showLayers) {
            val activeLayer = state.layers.firstOrNull { it.isActive }
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(end = 10.dp, top = 74.dp, bottom = 130.dp)
                    .width(268.dp)
                    .fillMaxHeight(0.62f),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                tonalElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Katmanlar", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White)
                        Row {
                            IconButton(onClick = { vm.addLayer() }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Add, contentDescription = "Yeni Katman", tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = { vm.deleteActiveLayer() }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Katmanı Sil", tint = Color(0xFFFF6B6B), modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    // Synergistic Layer Quick-Actions: Taşı, fx, Yukarı, Aşağı, Çoğalt, Birleştir
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                s.showLayers = false
                                vm.setTool(ActiveTool.NONE)
                                s.layerDx = 0f; s.layerDy = 0f; s.layerScalePct = 100f; s.layerScaleXPct = 100f; s.layerScaleYPct = 100f; s.layerRotDeg = 0f
                                vm.loadActiveLayerPreview()
                                s.activePanel = BottomPanel.LAYER_TRANSFORM
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Default.OpenWith, contentDescription = "Taşı", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = {
                                s.showLayers = false
                                vm.setTool(ActiveTool.NONE)
                                s.showFxFineTune = false
                                vm.beginLayerProxyPreview()
                                s.activePanel = BottomPanel.LAYER_FX_EDIT
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = "Efekt (fx)", tint = Color(0xFFFFD54F), modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { vm.moveLayerUp() }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = "Yukarı", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { vm.moveLayerDown() }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = "Aşağı", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { vm.duplicateActiveLayer() }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Çoğalt", tint = Color.White, modifier = Modifier.size(17.dp))
                        }
                        IconButton(onClick = { vm.mergeLayerDown() }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.MergeType, contentDescription = "Birleştir", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }

                    // v1.5.0 Non-Destructive Layer Pro row: Maske, Kırpma (↳), Grup/Dağıt, Blend If
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(top = 4.dp, bottom = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isActiveGroup = activeLayer?.isGroup == true
                        val isActiveClipped = activeLayer?.clipped == true
                        val hasActiveMask = activeLayer?.hasMask == true
                        ProPill(
                            selected = hasActiveMask || state.maskPaintMode,
                            onClick = {
                                s.showLayers = false
                                s.activePanel = BottomPanel.LAYER_MASK_EDIT
                            },
                            label = { Text(if (hasActiveMask) "Maske ✓" else "Maske", fontSize = 10.sp) }
                        )
                        ProPill(
                            selected = isActiveClipped,
                            onClick = { vm.toggleClippingMask() },
                            label = { Text(if (isActiveClipped) "↳ Kırp" else "Kırpma", fontSize = 10.sp) }
                        )
                        ProPill(
                            selected = isActiveGroup,
                            onClick = { if (isActiveGroup) vm.ungroupActiveLayer() else vm.groupActiveLayer() },
                            label = { Text(if (isActiveGroup) "Dağıt" else "Grupla", fontSize = 10.sp) }
                        )
                        ProPill(
                            selected = s.activePanel == BottomPanel.BLEND_IF_EDIT,
                            onClick = {
                                s.showLayers = false
                                vm.setTool(ActiveTool.NONE)
                                s.blendIfThisBlack = 0f; s.blendIfThisWhite = 255f
                                s.blendIfUnderBlack = 0f; s.blendIfUnderWhite = 255f; s.blendIfFeather = 20f
                                vm.beginLayerProxyPreview()
                                s.activePanel = BottomPanel.BLEND_IF_EDIT
                            },
                            label = { Text("Blend If", fontSize = 10.sp) }
                        )
                        ProPill(
                            selected = false,
                            onClick = { vm.copyActiveLayerStyle() },
                            label = { Text("Stil Kopyala", fontSize = 10.sp) }
                        )
                        if (state.hasCopiedStyle) {
                            ProPill(
                                selected = true,
                                onClick = { vm.pasteLayerStyleToActive() },
                                label = { Text("Stil Yapıştır", fontSize = 10.sp) }
                            )
                        }
                        if (activeLayer?.isText == true) {
                            ProPill(
                                selected = false,
                                onClick = { vm.rasterizeActiveTextLayer() },
                                label = { Text("Rasterleştir", fontSize = 10.sp) }
                            )
                        }
                        if (state.selectionBounds != null) {
                            ProPill(
                                selected = false,
                                onClick = { vm.layerViaCopy() },
                                label = { Text("Seçimden Katman", fontSize = 10.sp) }
                            )
                        }
                    }
                    if (activeLayer != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        CompactSliderRow(
                            label = "Opaklık",
                            valueText = "${(activeLayer.opacity * 100).toInt()}%",
                            value = activeLayer.opacity,
                            range = 0f..1f,
                            defaultValue = 1f
                        ) {
                            vm.setLayerOpacity(activeLayer.id, it)
                        }
                        Spacer(modifier = Modifier.height(4.dp))

                        Box(modifier = Modifier.fillMaxWidth()) {
                            val currentOpt = BEGINNER_BLEND_MODES.firstOrNull {
                                it.engineName.equals(activeLayer.blendMode, ignoreCase = true)
                            }
                            OutlinedButton(
                                onClick = { s.showBlendMenu = true },
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Karışım: ${currentOpt?.labelTr ?: activeLayer.blendMode}",
                                    fontSize = 11.sp,
                                    color = Color.White
                                )
                            }
                            DropdownMenu(
                                expanded = s.showBlendMenu,
                                onDismissRequest = { s.showBlendMenu = false }
                            ) {
                                BEGINNER_BLEND_MODES.forEach { opt ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(opt.labelTr, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                                Text(opt.hintTr, fontSize = 10.sp, color = Color.Gray)
                                            }
                                        },
                                        onClick = {
                                            s.showBlendMenu = false
                                            vm.setLayerBlendMode(activeLayer.id, opt.engineName)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(state.layers, key = { it.id.toLong() }) { layer ->
                            val bg = if (layer.isActive) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(bg)
                                    .clickable { vm.selectLayer(layer.id) }
                                    .padding(horizontal = 6.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { vm.toggleLayerVisibility(layer.id, !layer.visible) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        if (layer.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = "Görünürlük",
                                        tint = if (layer.visible) Color.White else Color.Gray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f).padding(start = (4 + layer.depth.toInt().coerceAtMost(3) * 12).dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        if (layer.clipped) {
                                            Text("↳", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4F9CFF))
                                        }
                                        if (layer.isGroup) {
                                            Icon(Icons.Default.Folder, contentDescription = null, tint = Color(0xFFFFD54F), modifier = Modifier.size(14.dp))
                                        }
                                        Text(layer.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (layer.fxBadges.isNotEmpty()) {
                                            Text(
                                                text = "fx: " + layer.fxBadges.joinToString(" · "),
                                                fontSize = 9.sp,
                                                color = Color(0xFFFFD54F),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Text(
                                            "${layer.blendMode} · ${(layer.opacity * 100).toInt()}%",
                                            fontSize = 10.sp,
                                            color = Color(0xFFB8BCC8)
                                        )
                                        if (layer.hasMask) {
                                            Text(
                                                text = if (layer.maskEnabled) "Maske" else "Maske (Kapalı)",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (layer.maskEnabled) Color(0xFF2ED573) else Color(0xFFFF8A80)
                                            )
                                        }
                                    }
                                }
                                if (layer.isBackground) {
                                    IconButton(
                                        onClick = { vm.toggleLayerLocked(layer.id, !layer.locked) },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(
                                            if (layer.locked) Icons.Default.Lock else Icons.Default.LockOpen,
                                            contentDescription = if (layer.locked) "Fotoğraf Kilitli" else "Fotoğraf Kilidi Açık",
                                            tint = if (layer.locked) Color(0xFFFFB74D) else Color(0xFF8E93A6),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                // Mini layer preview box (checkerboard + layer content thumbnail)
                                val thumb = layerThumbs[layer.id]
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(7.dp))
                                        .border(1.dp, Color(0xFF454A5C), RoundedCornerShape(7.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val cell = 6.dp.toPx()
                                        val cols = (size.width / cell).toInt() + 1
                                        val rows = (size.height / cell).toInt() + 1
                                        for (ry in 0 until rows) {
                                            for (rx in 0 until cols) {
                                                val light = ((rx + ry) and 1) == 0
                                                drawRect(
                                                    color = if (light) Color(0xFFD6D8E0) else Color(0xFFA8ACB8),
                                                    topLeft = Offset(rx * cell, ry * cell),
                                                    size = Size(cell, cell)
                                                )
                                            }
                                        }
                                    }
                                    if (thumb != null) {
                                        Image(
                                            bitmap = thumb,
                                            contentDescription = "${layer.name} önizleme",
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Tune,
                                            contentDescription = null,
                                            tint = Color(0xFF2B2F3D),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Status pill above bottom dock
        state.statusMessage?.let { msg ->
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = if (s.measuredBottomBarPx > 10f) with(density) { s.measuredBottomBarPx.toDp() + 18.dp } else if (s.activePanel == BottomPanel.DOCK) 114.dp else 210.dp)
                    .clickable { vm.clearStatus() },
                shape = RoundedCornerShape(20.dp),
                color = Color(0xEE111217),
                tonalElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(msg, fontSize = 12.sp, color = Color.White)
                }
            }
        }

        // v1.7.2 PROCREATE-STYLE LEFT EDGE VERTICAL SIZE & OPACITY SIDEBAR
        if (s.activePanel == BottomPanel.DRAW_MENU || s.activePanel == BottomPanel.RETOUCH_MENU || s.activePanel == BottomPanel.SELECT_MENU) {
            LeftBrushSidebar(
                vm = vm,
                state = state,
                bucketTolerance = s.bucketTolerance,
                onBucketTolerance = { s.bucketTolerance = it },
                onBrushSizePreview = { s.brushSizePreviewTick++ },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 6.dp)
            )
        }

        // v1.7.2 FLOATING BRUSH / TOOL SETTINGS POPOVER (AUTO-CLOSES ON CANVAS TOUCH)
        if (s.showToolPopover && (s.activePanel == BottomPanel.DRAW_MENU || s.activePanel == BottomPanel.RETOUCH_MENU)) {
            ToolSettingsPopoverCard(
                vm = vm,
                state = state,
                bucketTolerance = s.bucketTolerance,
                onBucketTolerance = { s.bucketTolerance = it },
                bucketContiguous = s.bucketContiguous,
                onBucketContiguous = { s.bucketContiguous = it },
                onImportAbr = { importAbrLauncher.launch(arrayOf("*/*")) },
                onDismiss = { s.showToolPopover = false },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(
                        bottom = if (s.measuredBottomBarPx > 10f)
                            with(density) { s.measuredBottomBarPx.toDp() + 14.dp }
                        else 80.dp
                    )
            )
        }

        // v1.7.2 DIRECT 1-TAP PRO HSV COLOR WHEEL DIALOG
        if (s.showQuickColorWheel) {
            ProColorWheelDialog(
                initialHex = state.colorHex,
                initialOpacity = state.brushOpacity,
                showOpacity = true,
                onPickEyedropper = { vm.setTool(ActiveTool.EYEDROPPER) },
                onDismiss = { s.showQuickColorWheel = false },
                onConfirm = { pickedHex, pickedOpacity ->
                    vm.updateBrush(colorHex = pickedHex, opacity = pickedOpacity)
                    s.showQuickColorWheel = false
                }
            )
        }

        // Studio v2.0 Frosted-Glass Workspace Sheet (opens cleanly above fixed 4+1 dock)
        val openWs = s.activeWorkspaceV2
        if (openWs != null && s.activePanel == BottomPanel.DOCK) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x55000000))
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { s.activeWorkspaceV2 = null }
            )
            StudioWorkspaceSheetV2(
                workspace = openWs,
                pinnedIds = s.pinnedToolIdsV2,
                onTogglePin = { id ->
                    s.pinnedToolIdsV2 = if (id in s.pinnedToolIdsV2) s.pinnedToolIdsV2 - id else s.pinnedToolIdsV2 + id
                },
                onSelectTool = { spec ->
                    s.lastWorkspaceV2 = spec.workspace
                    s.activeWorkspaceV2 = null
                    val ctx = StudioLaunchContext(
                        vm = vm,
                        state = state,
                        s = s,
                        context = context,
                        onPushGpuMirrorPreview = { mode, ratio -> pushGpuMirrorPreview(mode, ratio) },
                        onClearGpuPreview = { clearGpuPreview() }
                    )
                    spec.onLaunch(ctx)
                },
                onDismiss = { s.activeWorkspaceV2 = null },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 84.dp, start = 8.dp, end = 8.dp)
            )
        }

        // Studio v2.0 Fixed 4+1 Bottom Dock & Focused Tool Host
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .onGloballyPositioned { coords ->
                    val h = coords.size.height.toFloat()
                    if (kotlin.math.abs(h - s.measuredBottomBarPx) > 4f) {
                        s.measuredBottomBarPx = h
                    }
                },
            shape = RoundedCornerShape(22.dp),
            color = StudioV2Colors.GlassBar,
            border = androidx.compose.foundation.BorderStroke(1.dp, StudioV2Colors.GlassBorder),
            tonalElevation = 10.dp
        ) {
            StudioBottomPanelHost(
                vm = vm,
                state = state,
                s = s,
                context = context,
                studioFonts = studioFonts,
                cinemaThumbs = cinemaThumbs,
                galleryThumbs = galleryThumbs,
                lightDistortThumbs = lightDistortThumbs,
                dockScrollState = dockScrollState,
                drawScrollState = drawScrollState,
                selectScrollState = selectScrollState,
                adjustScrollState = adjustScrollState,
                cropScrollState = cropScrollState,
                effectScrollState = effectScrollState,
                onImportFont = { importFontLauncher.launch(arrayOf("*/*")) },
                onImportXmp = { importXmpLauncher.launch(arrayOf("*/*")) },
                onImportLut = { importLutLauncher.launch(arrayOf("*/*")) },
                onImportAbr = { importAbrLauncher.launch(arrayOf("*/*")) },
                onPushCurveGpu = { y0, y1, y2, y3, y4 -> pushCurveGpu(y0, y1, y2, y3, y4) },
                onPushGpuMirrorPreview = { mode, ratio -> pushGpuMirrorPreview(mode, ratio) },
                onFitToSafeArea = { turns, panel -> fitToSafeArea(turns, panel) },
                onClearGpuPreview = { clearGpuPreview() },
                onClearAdjustGpuPreviewOnly = { clearAdjustGpuPreviewOnly() },
                onPushGpuTransformPreview = { turns, fh, fv -> pushGpuTransformPreview(turns, fh, fv) }
            )
        }

        // Studio v2.0 Instant Command Palette (Fuzzy Search across all registered tools)
        if (s.showSearchPaletteV2) {
            StudioCommandPaletteV2(
                pinnedIds = s.pinnedToolIdsV2,
                onTogglePin = { id ->
                    s.pinnedToolIdsV2 = if (id in s.pinnedToolIdsV2) s.pinnedToolIdsV2 - id else s.pinnedToolIdsV2 + id
                },
                onSelectTool = { spec ->
                    s.lastWorkspaceV2 = spec.workspace
                    s.showSearchPaletteV2 = false
                    s.activeWorkspaceV2 = null
                    val ctx = StudioLaunchContext(
                        vm = vm,
                        state = state,
                        s = s,
                        context = context,
                        onPushGpuMirrorPreview = { mode, ratio -> pushGpuMirrorPreview(mode, ratio) },
                        onClearGpuPreview = { clearGpuPreview() }
                    )
                    spec.onLaunch(ctx)
                },
                onDismiss = { s.showSearchPaletteV2 = false }
            )
        }
    }
}


@Composable
private fun RetouchTrailOverlay(
    trailPoints: List<Offset>,
    activeTool: ActiveTool,
    brushSize: Float,
    zoomProvider: () -> Float
) {
    val trailColor = when (activeTool) {
        ActiveTool.REMOVE_TOOL -> Color(0x99FF2EA6)
        ActiveTool.SPOT_HEAL -> Color(0x88FF5A5F)
        ActiveTool.SELECT_BRUSH_SUB -> Color(0x88FF4D4F)
        ActiveTool.SELECT_QUICK -> Color(0x882ED573)
        ActiveTool.SELECT_LASSO -> Color(0xDD00E5FF)
        ActiveTool.SELECT_MAGNETIC_LASSO -> Color(0xDDFFD600)
        ActiveTool.ADJUST_BRUSH -> Color(0x88FFA502)
        ActiveTool.HEALING_BRUSH -> Color(0x882ED573)
        ActiveTool.HISTORY_BRUSH -> Color(0x8870A1FF)
        ActiveTool.ART_HISTORY_BRUSH -> Color(0x88C56CF0)
        else -> Color(0x884F9CFF)
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val n = trailPoints.size
        if (n == 0) return@Canvas
        val strokeW = if (activeTool == ActiveTool.SELECT_LASSO || activeTool == ActiveTool.SELECT_MAGNETIC_LASSO) {
            6f
        } else {
            (brushSize * zoomProvider()).coerceIn(4f, 360f)
        }
        if (n == 1) {
            drawCircle(trailColor, radius = strokeW * 0.5f, center = trailPoints[0])
        } else {
            val p = Path().apply {
                moveTo(trailPoints[0].x, trailPoints[0].y)
                for (i in 1 until n) {
                    lineTo(trailPoints[i].x, trailPoints[i].y)
                }
            }
            drawPath(
                path = p,
                color = trailColor,
                style = Stroke(
                    width = strokeW,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                )
            )
        }
    }
}


@Composable
private fun SymmetryAxisOverlay(
    mode: String,
    canvasViewRef: VulkanCanvasView?,
    docW: Float,
    docH: Float,
    cameraTick: Int
) {
    if (canvasViewRef == null) return
    Canvas(modifier = Modifier.fillMaxSize()) {
        if (cameraTick < 0) return@Canvas
        val (l, t) = canvasViewRef.docToScreen(0f, 0f)
        val (r, b) = canvasViewRef.docToScreen(docW, docH)
        val (cx, cy) = canvasViewRef.docToScreen(docW * 0.5f, docH * 0.5f)
        val dash = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
        val col = Color(0xBB00E5FF)
        if (mode == "vertical" || mode == "dual") {
            drawLine(col, Offset(cx, t), Offset(cx, b), strokeWidth = 2.2f, pathEffect = dash)
        }
        if (mode == "horizontal" || mode == "dual") {
            drawLine(col, Offset(l, cy), Offset(r, cy), strokeWidth = 2.2f, pathEffect = dash)
        }
        if (mode == "diagonal") {
            drawLine(col, Offset(l, t), Offset(r, b), strokeWidth = 2.2f, pathEffect = dash)
        }
    }
}
