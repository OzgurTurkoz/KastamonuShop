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
internal fun StudioBottomPanelHost(
    vm: EditorViewModel,
    state: EditorUiState,
    s: StudioSessionState,
    context: Context,
    studioFonts: List<EditorViewModel.StudioFontOption>,
    cinemaThumbs: List<ImageBitmap>,
    galleryThumbs: List<ImageBitmap>,
    lightDistortThumbs: List<ImageBitmap>,
    dockScrollState: ScrollState,
    drawScrollState: ScrollState,
    selectScrollState: ScrollState,
    adjustScrollState: ScrollState,
    cropScrollState: ScrollState,
    effectScrollState: ScrollState,
    onImportFont: () -> Unit,
    onImportXmp: () -> Unit,
    onImportLut: () -> Unit,
    onPushCurveGpu: (Float, Float, Float, Float, Float) -> Unit,
    onPushGpuMirrorPreview: (MirrorModeOption, Float) -> Unit,
    onFitToSafeArea: (Int, BottomPanel) -> Unit,
    onClearGpuPreview: () -> Unit,
    onClearAdjustGpuPreviewOnly: () -> Unit,
    onPushGpuTransformPreview: (Int, Boolean, Boolean) -> Unit
) {
            val activeColor = remember(state.colorHex) {
                runCatching { Color(AndroidColor.parseColor(state.colorHex)) }
                    .getOrDefault(Color.Red)
            }

            when (s.activePanel) {
                BottomPanel.DOCK -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(dockScrollState)
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isSelectTool = state.activeTool == ActiveTool.SELECT_RECT ||
                            state.activeTool == ActiveTool.SELECT_WAND ||
                            state.activeTool == ActiveTool.SELECT_QUICK ||
                            state.activeTool == ActiveTool.SELECT_BRUSH_ADD ||
                            state.activeTool == ActiveTool.SELECT_BRUSH_SUB
                        CircularDockButton(
                            icon = Icons.Default.Bolt,
                            label = "Hızlı",
                            onClick = {
                                vm.setTool(ActiveTool.NONE)
                                s.activePanel = BottomPanel.QUICK_ACTIONS_MENU
                            }
                        )
                        CircularDockButton(
                            icon = Icons.Default.Brush,
                            label = "Çizim",
                            selected = state.activeTool != ActiveTool.NONE && !isSelectTool,
                            accentDot = if (state.activeTool == ActiveTool.BRUSH || state.activeTool == ActiveTool.EYEDROPPER) activeColor else null,
                            onClick = { s.activePanel = BottomPanel.DRAW_MENU }
                        )
                        CircularDockButton(
                            icon = Icons.Default.SelectAll,
                            label = "Seçim",
                            selected = state.selectionBounds != null || isSelectTool,
                            onClick = {
                                if (!isSelectTool) {
                                    vm.setTool(ActiveTool.SELECT_QUICK)
                                }
                                s.activePanel = BottomPanel.SELECT_MENU
                            }
                        )
                        CircularDockButton(
                            icon = Icons.Default.Title,
                            label = "Metin",
                            onClick = {
                                vm.setTool(ActiveTool.NONE)
                                vm.ensureStudioFontsLoaded(context)
                                vm.beginProxyPreview()
                                val docW = (state.docInfo?.width?.toInt() ?: 2048).toFloat()
                                s.textEditLayerId = null
                                s.textSizePx = (docW * 0.055f).coerceIn(24f, 180f)
                                s.textNormX = 0.50f
                                s.textNormY = 0.50f
                                s.textTracking = 0f
                                s.textLineHeight = 1.2f
                                s.textVertical = false
                                s.textBold = false
                                s.textItalic = false
                                s.textUnderline = false
                                s.textStrike = false
                                s.textCaps = "normal"
                                s.textHScale = 100f
                                s.textVScale = 100f
                                s.textWarpStyle = "none"
                                s.textWarpBend = 50f
                                s.textWarpHDist = 0f
                                s.textWarpVDist = 0f
                                vm.inspectActiveTextLayer { existing ->
                                    if (existing != null) {
                                        s.textEditLayerId = existing.layerId
                                        s.textDraft = existing.text
                                        s.textFontFamily = existing.fontFamily.ifBlank { "Montserrat" }
                                        s.textSizePx = existing.sizePx.coerceIn(6f, 600f)
                                        s.textColorHex = existing.colorHex
                                        s.textAlign = existing.align
                                        s.textBold = existing.bold
                                        s.textItalic = existing.italic
                                        s.textUnderline = existing.underline
                                        s.textStrike = existing.strikethrough
                                        s.textCaps = existing.caps
                                        s.textHScale = existing.hScale
                                        s.textVScale = existing.vScale
                                        s.textTracking = existing.tracking
                                        s.textLineHeight = existing.lineHeight
                                        s.textVertical = existing.vertical
                                        s.textWarpStyle = existing.warpStyle
                                        s.textWarpBend = existing.warpBend
                                        s.textWarpHDist = existing.warpHDist
                                        s.textWarpVDist = existing.warpVDist
                                        s.textNormX = existing.normX
                                        s.textNormY = existing.normY
                                    }
                                }
                                s.activePanel = BottomPanel.TEXT_EDIT
                            }
                        )
                        CircularDockButton(
                            icon = Icons.Default.Gradient,
                            label = "Degrade",
                            onClick = {
                                vm.setTool(ActiveTool.NONE)
                                val docW = (state.docInfo?.width?.toInt() ?: 2048).toFloat()
                                val docH = (state.docInfo?.height?.toInt() ?: 2048).toFloat()
                                s.gradFromNormX = 0.20f; s.gradFromNormY = 0.20f
                                s.gradToNormX = 0.80f; s.gradToNormY = 0.80f
                                vm.beginProxyPreview()
                                vm.updateProxyGradient(
                                    docW * s.gradFromNormX, docH * s.gradFromNormY,
                                    docW * s.gradToNormX, docH * s.gradToNormY,
                                    s.gradStyle, s.gradStartHex, s.gradEndHex, s.gradOpacity
                                )
                                s.activePanel = BottomPanel.GRADIENT_EDIT
                            }
                        )
                        CircularDockButton(
                            icon = Icons.Default.Category,
                            label = "Şekil",
                            onClick = {
                                vm.setTool(ActiveTool.NONE)
                                val docW = (state.docInfo?.width?.toInt() ?: 2048).toFloat()
                                val docH = (state.docInfo?.height?.toInt() ?: 2048).toFloat()
                                s.shapeX0Norm = 0.25f; s.shapeY0Norm = 0.25f
                                s.shapeX1Norm = 0.75f; s.shapeY1Norm = 0.75f
                                s.showShapeFineTune = false
                                vm.beginProxyPreview()
                                vm.updateProxyShape(
                                    s.shapeKind,
                                    docW * s.shapeX0Norm, docH * s.shapeY0Norm,
                                    docW * s.shapeX1Norm, docH * s.shapeY1Norm,
                                    s.shapeFillHex, s.shapeStrokeHex, s.shapeStrokeWidth, s.shapeCornerRadius, s.shapeSides
                                )
                                s.activePanel = BottomPanel.SHAPE_EDIT
                            }
                        )
                        CircularDockButton(
                            icon = Icons.Default.Tune,
                            label = "Ayarlar",
                            onClick = {
                                vm.setTool(ActiveTool.NONE)
                                s.activePanel = BottomPanel.ADJUST_MENU
                            }
                        )
                        CircularDockButton(
                            icon = Icons.Default.Crop,
                            label = "Kırp",
                            onClick = {
                                vm.setTool(ActiveTool.NONE)
                                s.pendingQuarterTurns = 0
                                s.pendingFlipH = false
                                s.pendingFlipV = false
                                s.activePanel = BottomPanel.CROP_MENU
                            }
                        )
                        CircularDockButton(
                            icon = Icons.Default.BlurOn,
                            label = "Filtre",
                            onClick = {
                                vm.setTool(ActiveTool.NONE)
                                s.activePanel = BottomPanel.FILTER_MENU
                            }
                        )
                        CircularDockButton(
                            icon = Icons.Default.AutoAwesome,
                            label = "Efekt",
                            selected = state.bwLayerId != null || state.invertLayerId != null,
                            onClick = {
                                vm.setTool(ActiveTool.NONE)
                                s.activePanel = BottomPanel.EFFECT_MENU
                            }
                        )
                    }
                }

                BottomPanel.DRAW_MENU -> DrawMenuPanel(
                    vm = vm,
                    state = state,
                    drawScrollState = drawScrollState,
                    bucketTolerance = s.bucketTolerance,
                    onBucketTolerance = { s.bucketTolerance = it },
                    bucketContiguous = s.bucketContiguous,
                    onBucketContiguous = { s.bucketContiguous = it },
                    onBrushSizePreview = { s.brushSizePreviewTick++ },
                    onClose = { s.activePanel = BottomPanel.DOCK }
                )

                BottomPanel.SELECT_MENU -> SelectMenuPanel(
                    vm = vm,
                    state = state,
                    selectScrollState = selectScrollState,
                    onClose = { s.activePanel = BottomPanel.DOCK }
                )

                BottomPanel.TEXT_EDIT -> {
                    val docW = (state.docInfo?.width?.toInt() ?: 2048).toFloat()
                    val docH = (state.docInfo?.height?.toInt() ?: 2048).toFloat()
                    TextStudioPanel(
                        isEditingExisting = s.textEditLayerId != null,
                        onSwitchToNew = { s.textEditLayerId = null },
                        onImportFont = { onImportFont() },
                        text = s.textDraft, onText = { s.textDraft = it },
                        fonts = studioFonts, selectedFont = s.textFontFamily, onSelectFont = { s.textFontFamily = it },
                        colorHex = s.textColorHex, onColorHex = { s.textColorHex = it },
                        align = s.textAlign, onAlign = { s.textAlign = it },
                        bold = s.textBold, onBold = { s.textBold = it },
                        italic = s.textItalic, onItalic = { s.textItalic = it },
                        underline = s.textUnderline, onUnderline = { s.textUnderline = it },
                        strikethrough = s.textStrike, onStrikethrough = { s.textStrike = it },
                        caps = s.textCaps, onCaps = { s.textCaps = it },
                        hScale = s.textHScale, onHScale = { s.textHScale = it },
                        vScale = s.textVScale, onVScale = { s.textVScale = it },
                        vertical = s.textVertical, onVertical = { s.textVertical = it },
                        sizePx = s.textSizePx, defaultSizePx = (docW * 0.055f).coerceIn(24f, 180f), onSizePx = { s.textSizePx = it },
                        tracking = s.textTracking, onTracking = { s.textTracking = it },
                        lineHeight = s.textLineHeight, onLineHeight = { s.textLineHeight = it },
                        warpStyle = s.textWarpStyle, onWarpStyle = { s.textWarpStyle = it },
                        warpBend = s.textWarpBend, onWarpBend = { s.textWarpBend = it },
                        warpHDist = s.textWarpHDist, onWarpHDist = { s.textWarpHDist = it },
                        warpVDist = s.textWarpVDist, onWarpVDist = { s.textWarpVDist = it },
                        onCreateMask = {
                            vm.createTextSelectionMask(
                                text = s.textDraft, x = docW * s.textNormX, y = docH * s.textNormY,
                                sizePx = s.textSizePx, align = s.textAlign, fontFamily = s.textFontFamily,
                                bold = s.textBold, italic = s.textItalic, caps = s.textCaps,
                                hScale = s.textHScale, vScale = s.textVScale,
                                tracking = s.textTracking, lineHeight = s.textLineHeight, vertical = s.textVertical,
                                warpStyle = s.textWarpStyle, warpBend = s.textWarpBend,
                                warpHDist = s.textWarpHDist, warpVDist = s.textWarpVDist
                            )
                            s.activePanel = BottomPanel.SELECT_MENU
                        },
                        onRasterize = {
                            vm.rasterizeActiveTextLayer()
                            s.activePanel = BottomPanel.DOCK
                        },
                        onClose = { vm.endProxyPreview(); s.activePanel = BottomPanel.DOCK },
                        onApply = {
                            vm.addOrEditTextLayer(
                                editLayerId = s.textEditLayerId,
                                text = s.textDraft, x = docW * s.textNormX, y = docH * s.textNormY,
                                sizePx = s.textSizePx, colorHex = s.textColorHex, align = s.textAlign,
                                fontFamily = s.textFontFamily, bold = s.textBold, italic = s.textItalic,
                                underline = s.textUnderline, strikethrough = s.textStrike, caps = s.textCaps,
                                hScale = s.textHScale, vScale = s.textVScale,
                                tracking = s.textTracking, lineHeight = s.textLineHeight, vertical = s.textVertical,
                                warpStyle = s.textWarpStyle, warpBend = s.textWarpBend,
                                warpHDist = s.textWarpHDist, warpVDist = s.textWarpVDist
                            )
                            s.activePanel = BottomPanel.DOCK
                        }
                    )
                }
                BottomPanel.ADJUST_MENU -> {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BottomEditorHeader(
                            title = "Ayarlar",
                            subtitle = "Canlı önizlemeli ışık ve renk ayarları",
                            onClose = { s.activePanel = BottomPanel.DOCK }
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(adjustScrollState),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CircularDockButton(
                                icon = Icons.Default.AutoFixHigh,
                                label = "Oto",
                                onClick = { s.activePanel = BottomPanel.AUTO_ENHANCE_MENU }
                            )
                            CircularDockButton(
                                icon = Icons.Default.Camera,
                                label = "Lens",
                                onClick = {
                                    s.lensDistortion = 0f; s.lensVignette = 0f
                                    s.lensRedCyan = 0f; s.lensBlueYellow = 0f
                                    s.lensVertical = 0f; s.lensHorizontal = 0f
                                    vm.beginProxyPreview()
                                    s.activePanel = BottomPanel.LENS_CORRECTION_EDIT
                                }
                            )
                            CircularDockButton(
                                icon = Icons.Default.ColorLens,
                                label = "3D LUT",
                                onClick = {
                                    vm.beginProxyPreview()
                                    s.activePanel = BottomPanel.LUT_IMPORT_EDIT
                                }
                            )
                            CircularDockButton(
                                icon = Icons.Default.WbSunny,
                                label = "Kamera",
                                onClick = {
                                    s.camExposureVal = 0f; s.camShadowsVal = 0f; s.camHighlightsVal = 0f; s.camVibranceVal = 0f
                                    s.camTempVal = 0f; s.camTintVal = 0f; s.camClarityVal = 0f; s.camDehazeVal = 0f
                                    s.showCamFineTune = false
                                    vm.beginProxyPreview()
                                    s.activePanel = BottomPanel.CAMERA_LIGHT_LIVE
                                }
                            )
                            CircularDockButton(
                                icon = Icons.Default.Brightness6,
                                label = "Işık",
                                onClick = {
                                    s.brightnessVal = 0f
                                    s.contrastVal = 0f
                                    vm.beginProxyPreview()
                                    s.activePanel = BottomPanel.LIGHT_LIVE
                                }
                            )
                            CircularDockButton(
                                icon = Icons.Default.Palette,
                                label = "Renk",
                                onClick = {
                                    s.hueVal = 0f
                                    s.saturationVal = 0f
                                    vm.beginProxyPreview()
                                    s.activePanel = BottomPanel.COLOR_LIVE
                                }
                            )
                            CircularDockButton(
                                icon = Icons.Default.Equalizer,
                                label = "Düzeyler",
                                onClick = {
                                    s.levelsBlackVal = 0f
                                    s.levelsGammaVal = 1f
                                    s.levelsWhiteVal = 255f
                                    vm.beginProxyPreview()
                                    s.activePanel = BottomPanel.LEVELS_LIVE
                                }
                            )
                            CircularDockButton(
                                icon = Icons.Default.ColorLens,
                                label = "Denge",
                                onClick = {
                                    s.cbCyanRedVal = 0f
                                    s.cbMagentaGreenVal = 0f
                                    s.cbYellowBlueVal = 0f
                                    vm.beginProxyPreview()
                                    s.activePanel = BottomPanel.COLOR_BAL_LIVE
                                }
                            )
                            CircularDockButton(
                                icon = Icons.Default.ShowChart,
                                label = "Eğriler",
                                onClick = {
                                    s.curveBlackVal = 0.0f
                                    s.curveShadowVal = 0.25f
                                    s.curveMidVal = 0.50f
                                    s.curveHighVal = 0.75f
                                    s.curveWhiteVal = 1.0f
                                    s.curveOverlayExpanded = false
                                    vm.beginProxyPreview()
                                    s.activePanel = BottomPanel.CURVES_LIVE
                                }
                            )
                            CircularDockButton(
                                icon = Icons.Default.Gradient,
                                label = "HSL",
                                onClick = {
                                    s.hslBandIdx = 0
                                    s.hslHue = List(8) { 0f }
                                    s.hslSat = List(8) { 0f }
                                    s.hslLum = List(8) { 0f }
                                    vm.beginProxyPreview()
                                    s.activePanel = BottomPanel.HSL_MIXER_LIVE
                                }
                            )
                            CircularDockButton(
                                icon = Icons.Default.FilterFrames,
                                label = "Poster",
                                onClick = {
                                    s.posterMode = true
                                    s.posterLevels = 6f
                                    s.threshLevel = 128f
                                    s.posterTouched = false
                                    vm.beginProxyPreview()
                                    s.activePanel = BottomPanel.POSTER_THRESH_LIVE
                                }
                            )
                        }
                    }
                }

                BottomPanel.CAMERA_LIGHT_LIVE -> CameraRawProPanel(
                    s = s,
                    onPreview = { vm.updateProxyCameraRawJson(buildCameraRawProJson(s)) },
                    onImportXmp = onImportXmp,
                    onReset = {
                        resetCameraRawProState(s)
                        vm.resetProxyPreview()
                    },
                    onClose = { onClearAdjustGpuPreviewOnly(); s.activePanel = BottomPanel.ADJUST_MENU },
                    onApply = {
                        vm.applyCameraRawJson(buildCameraRawProJson(s))
                        s.activePanel = BottomPanel.DOCK
                    }
                )
                BottomPanel.GRADIENT_EDIT -> {
                    val docW = (state.docInfo?.width?.toInt() ?: 2048).toFloat()
                    val docH = (state.docInfo?.height?.toInt() ?: 2048).toFloat()
                    GradientEditPanel(
                        style = s.gradStyle, onStyle = { s.gradStyle = it },
                        startHex = s.gradStartHex, onStartHex = { s.gradStartHex = it },
                        endHex = s.gradEndHex, onEndHex = { s.gradEndHex = it },
                        opacity = s.gradOpacity, onOpacity = { s.gradOpacity = it },
                        onPreview = {
                            vm.updateProxyGradient(
                                docW * s.gradFromNormX, docH * s.gradFromNormY,
                                docW * s.gradToNormX, docH * s.gradToNormY,
                                s.gradStyle, s.gradStartHex, s.gradEndHex, s.gradOpacity
                            )
                        },
                        onClose = { vm.endProxyPreview(); s.activePanel = BottomPanel.DOCK },
                        onApply = {
                            vm.applyGradientFill(
                                docW * s.gradFromNormX, docH * s.gradFromNormY,
                                docW * s.gradToNormX, docH * s.gradToNormY,
                                s.gradStyle, s.gradStartHex, s.gradEndHex, s.gradOpacity
                            )
                            s.activePanel = BottomPanel.DOCK
                        }
                    )
                }
                BottomPanel.SHAPE_EDIT -> {
                    val docW = (state.docInfo?.width?.toInt() ?: 2048).toFloat()
                    val docH = (state.docInfo?.height?.toInt() ?: 2048).toFloat()
                    ShapeEditPanel(
                        kind = s.shapeKind, onKind = { s.shapeKind = it },
                        fillHex = s.shapeFillHex, onFillHex = { s.shapeFillHex = it },
                        strokeWidth = s.shapeStrokeWidth, onStrokeWidth = { s.shapeStrokeWidth = it },
                        cornerRadius = s.shapeCornerRadius, onCornerRadius = { s.shapeCornerRadius = it },
                        sides = s.shapeSides, onSides = { s.shapeSides = it },
                        showFine = s.showShapeFineTune, onToggleFine = { s.showShapeFineTune = !s.showShapeFineTune },
                        onPreview = {
                            vm.updateProxyShape(
                                s.shapeKind,
                                docW * s.shapeX0Norm, docH * s.shapeY0Norm,
                                docW * s.shapeX1Norm, docH * s.shapeY1Norm,
                                s.shapeFillHex, s.shapeStrokeHex, s.shapeStrokeWidth, s.shapeCornerRadius, s.shapeSides
                            )
                        },
                        onClose = { vm.endProxyPreview(); s.activePanel = BottomPanel.DOCK },
                        onApply = {
                            vm.createShapeLayer(
                                s.shapeKind,
                                docW * s.shapeX0Norm, docH * s.shapeY0Norm,
                                docW * s.shapeX1Norm, docH * s.shapeY1Norm,
                                s.shapeFillHex, s.shapeStrokeHex, s.shapeStrokeWidth, s.shapeCornerRadius, s.shapeSides
                            )
                            s.activePanel = BottomPanel.DOCK
                        }
                    )
                }
                BottomPanel.LIGHT_LIVE -> LightLivePanel(
                    brightness = s.brightnessVal,
                    contrast = s.contrastVal,
                    onBrightness = {
                        s.brightnessVal = it
                        vm.updateProxyAdjustment("brightnessContrast", """{"brightness":${s.brightnessVal.roundToInt()},"contrast":${s.contrastVal.roundToInt()}}""")
                    },
                    onContrast = {
                        s.contrastVal = it
                        vm.updateProxyAdjustment("brightnessContrast", """{"brightness":${s.brightnessVal.roundToInt()},"contrast":${s.contrastVal.roundToInt()}}""")
                    },
                    onClose = { onClearAdjustGpuPreviewOnly(); s.activePanel = BottomPanel.ADJUST_MENU },
                    onApply = {
                        if (s.brightnessVal.roundToInt() == 0 && s.contrastVal.roundToInt() == 0) onClearAdjustGpuPreviewOnly()
                        else vm.applyAdjustmentOnce("brightnessContrast", """{"brightness":${s.brightnessVal.roundToInt()},"contrast":${s.contrastVal.roundToInt()}}""", "Işık & Kontrast")
                        s.activePanel = BottomPanel.DOCK
                    }
                )

                BottomPanel.COLOR_LIVE -> ColorLivePanel(
                    saturation = s.saturationVal,
                    hue = s.hueVal,
                    onSaturation = {
                        s.saturationVal = it
                        vm.updateProxyAdjustment("hueSaturation", """{"hue":${s.hueVal.roundToInt()},"saturation":${s.saturationVal.roundToInt()},"lightness":0}""")
                    },
                    onHue = {
                        s.hueVal = it
                        vm.updateProxyAdjustment("hueSaturation", """{"hue":${s.hueVal.roundToInt()},"saturation":${s.saturationVal.roundToInt()},"lightness":0}""")
                    },
                    onClose = { onClearAdjustGpuPreviewOnly(); s.activePanel = BottomPanel.ADJUST_MENU },
                    onApply = {
                        if (s.saturationVal.roundToInt() == 0 && s.hueVal.roundToInt() == 0) onClearAdjustGpuPreviewOnly()
                        else vm.applyAdjustmentOnce("hueSaturation", """{"hue":${s.hueVal.roundToInt()},"saturation":${s.saturationVal.roundToInt()},"lightness":0}""", "Renk & Canlılık")
                        s.activePanel = BottomPanel.DOCK
                    }
                )

                BottomPanel.LEVELS_LIVE -> LevelsLivePanel(
                    inBlack = s.levelsBlackVal,
                    gamma = s.levelsGammaVal,
                    inWhite = s.levelsWhiteVal,
                    onChange = { b, g, w ->
                        s.levelsBlackVal = b
                        s.levelsGammaVal = g
                        s.levelsWhiteVal = w
                        val gStr = String.format(java.util.Locale.US, "%.2f", g)
                        vm.updateProxyAdjustment("levels", """{"inBlack":${b.roundToInt()},"gamma":$gStr,"inWhite":${w.roundToInt()}}""")
                    },
                    onClose = { onClearAdjustGpuPreviewOnly(); s.activePanel = BottomPanel.ADJUST_MENU },
                    onApply = {
                        val ib = s.levelsBlackVal.roundToInt().coerceIn(0, 240)
                        val iw = s.levelsWhiteVal.roundToInt().coerceIn(ib + 10, 255)
                        if (ib == 0 && iw == 255 && kotlin.math.abs(s.levelsGammaVal - 1f) < 0.02f) onClearAdjustGpuPreviewOnly()
                        else {
                            val gStr = String.format(java.util.Locale.US, "%.2f", s.levelsGammaVal)
                            vm.applyAdjustmentOnce("levels", """{"inBlack":$ib,"gamma":$gStr,"inWhite":$iw}""", "Düzeyler")
                        }
                        s.activePanel = BottomPanel.DOCK
                    }
                )

                BottomPanel.COLOR_BAL_LIVE -> {
                    fun buildCbJson(): String =
                        """{"shadows":[${s.cbShadowCR.roundToInt()},${s.cbShadowMG.roundToInt()},${s.cbShadowYB.roundToInt()}],"midtones":[${s.cbCyanRedVal.roundToInt()},${s.cbMagentaGreenVal.roundToInt()},${s.cbYellowBlueVal.roundToInt()}],"highlights":[${s.cbHighCR.roundToInt()},${s.cbHighMG.roundToInt()},${s.cbHighYB.roundToInt()}],"preserveLuminosity":true}"""
                    val curCR = when (s.cbToneIdx) { 0 -> s.cbShadowCR; 2 -> s.cbHighCR; else -> s.cbCyanRedVal }
                    val curMG = when (s.cbToneIdx) { 0 -> s.cbShadowMG; 2 -> s.cbHighMG; else -> s.cbMagentaGreenVal }
                    val curYB = when (s.cbToneIdx) { 0 -> s.cbShadowYB; 2 -> s.cbHighYB; else -> s.cbYellowBlueVal }
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        BottomEditorHeader(
                            title = "Renk Dengesi (3 Ton)",
                            subtitle = "Gölgeler, Orta Tonlar ve Aydınlıklar ayrı ayrı",
                            applyLabel = "Uygula",
                            onClose = { onClearAdjustGpuPreviewOnly(); s.activePanel = BottomPanel.ADJUST_MENU },
                            onApply = {
                                vm.applyAdjustmentOnce("colorBalance", buildCbJson(), "Renk dengesi uygulandı")
                                s.activePanel = BottomPanel.DOCK
                            }
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Gölgeler", "Orta Tonlar", "Aydınlıklar").forEachIndexed { idx, lbl ->
                                FilterChip(
                                    selected = s.cbToneIdx == idx,
                                    onClick = { s.cbToneIdx = idx },
                                    label = { Text(lbl, fontSize = 11.sp) }
                                )
                            }
                        }
                        CompactSliderRow("Cyan / Kırmızı", "${curCR.roundToInt()}", curCR, -100f..100f, defaultValue = 0f) {
                            when (s.cbToneIdx) { 0 -> s.cbShadowCR = it; 2 -> s.cbHighCR = it; else -> s.cbCyanRedVal = it }
                            vm.updateProxyAdjustment("colorBalance", buildCbJson())
                        }
                        CompactSliderRow("Magenta / Yeşil", "${curMG.roundToInt()}", curMG, -100f..100f, defaultValue = 0f) {
                            when (s.cbToneIdx) { 0 -> s.cbShadowMG = it; 2 -> s.cbHighMG = it; else -> s.cbMagentaGreenVal = it }
                            vm.updateProxyAdjustment("colorBalance", buildCbJson())
                        }
                        CompactSliderRow("Sarı / Mavi", "${curYB.roundToInt()}", curYB, -100f..100f, defaultValue = 0f) {
                            when (s.cbToneIdx) { 0 -> s.cbShadowYB = it; 2 -> s.cbHighYB = it; else -> s.cbYellowBlueVal = it }
                            vm.updateProxyAdjustment("colorBalance", buildCbJson())
                        }
                    }
                }
                BottomPanel.CURVES_LIVE -> {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        fun buildRgbCurvesJson(): String {
                            fun fmtPts(pts: List<Float>): String {
                                val xs = listOf(0, 64, 128, 192, 255)
                                return xs.indices.joinToString(",", "[", "]") { i -> "[${xs[i]},${(pts[i] * 255f).roundToInt().coerceIn(0, 255)}]" }
                            }
                            val mPts = listOf(s.curveBlackVal, s.curveShadowVal, s.curveMidVal, s.curveHighVal, s.curveWhiteVal)
                            return """{"points":${fmtPts(mPts)},"red":${fmtPts(s.curveRedPts)},"green":${fmtPts(s.curveGreenPts)},"blue":${fmtPts(s.curveBluePts)}}"""
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("RGB", "Kırmızı", "Yeşil", "Mavi").forEachIndexed { idx, lbl ->
                                FilterChip(
                                    selected = s.curveChannelIdx == idx,
                                    onClick = { s.curveChannelIdx = idx },
                                    label = { Text(lbl, fontSize = 11.sp) }
                                )
                            }
                        }
                        if (s.curveChannelIdx > 0) {
                            val chPts = when (s.curveChannelIdx) { 1 -> s.curveRedPts; 2 -> s.curveGreenPts; else -> s.curveBluePts }
                            fun setCh(i: Int, v: Float) {
                                val next = chPts.toMutableList().apply { this[i] = v }
                                when (s.curveChannelIdx) { 1 -> s.curveRedPts = next; 2 -> s.curveGreenPts = next; else -> s.curveBluePts = next }
                                vm.updateProxyAdjustment("curves", buildRgbCurvesJson())
                            }
                            CompactSliderRow("Gölgeler", "${(chPts[1] * 255).roundToInt()}", chPts[1], 0f..1f, defaultValue = 0.25f) { setCh(1, it) }
                            CompactSliderRow("Orta Ton", "${(chPts[2] * 255).roundToInt()}", chPts[2], 0f..1f, defaultValue = 0.50f) { setCh(2, it) }
                            CompactSliderRow("Aydınlık", "${(chPts[3] * 255).roundToInt()}", chPts[3], 0f..1f, defaultValue = 0.75f) { setCh(3, it) }
                        }
                        BottomEditorHeader(
                            title = "Eğriler (RGB Curves)",
                            subtitle = if (s.curveOverlayExpanded) "Fotoğraf üzerindeki 5 noktayı parmağınla sürükle" else "RGB / R / G / B kanallarını bağımsız ayarla",
                            applyLabel = "Uygula",
                            onClose = {
                                s.curveOverlayExpanded = false
                                onClearAdjustGpuPreviewOnly()
                                s.activePanel = BottomPanel.ADJUST_MENU
                            },
                            onApply = {
                                s.curveOverlayExpanded = false
                                val isDefault = abs(s.curveBlackVal - 0.0f) < 0.01f &&
                                    abs(s.curveShadowVal - 0.25f) < 0.01f &&
                                    abs(s.curveMidVal - 0.50f) < 0.01f &&
                                    abs(s.curveHighVal - 0.75f) < 0.01f &&
                                    abs(s.curveWhiteVal - 1.0f) < 0.01f
                                if (isDefault) {
                                    onClearAdjustGpuPreviewOnly()
                                } else {
                                    val by = (s.curveBlackVal * 255f).roundToInt().coerceIn(0, 255)
                                    val sy = (s.curveShadowVal * 255f).roundToInt().coerceIn(0, 255)
                                    val my = (s.curveMidVal * 255f).roundToInt().coerceIn(0, 255)
                                    val hy = (s.curveHighVal * 255f).roundToInt().coerceIn(0, 255)
                                    val wy = (s.curveWhiteVal * 255f).roundToInt().coerceIn(0, 255)
                                    vm.applyAdjustmentOnce(
                                        "curves",
                                        """{"points":[[0,$by],[64,$sy],[128,$my],[192,$hy],[255,$wy]]}""",
                                        "Eğriler"
                                    )
                                }
                                s.activePanel = BottomPanel.DOCK
                            }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Tappable Mini Tone Curve Graph -> opens/closes full-canvas manual curve overlay!
                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (s.curveOverlayExpanded) Color(0xFF1D2B45) else Color(0xFF141720))
                                    .border(
                                        width = if (s.curveOverlayExpanded) 2.dp else 1.dp,
                                        color = if (s.curveOverlayExpanded) MaterialTheme.colorScheme.primary else Color(0xFF454D66),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        s.curveOverlayExpanded = !s.curveOverlayExpanded
                                        onFitToSafeArea(0, BottomPanel.CURVES_LIVE)
                                    }
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize().padding(6.dp)) {
                                    val w = size.width
                                    val h = size.height
                                    drawLine(Color(0x33FFFFFF), Offset(w * 0.5f, 0f), Offset(w * 0.5f, h), 1f)
                                    drawLine(Color(0x33FFFFFF), Offset(0f, h * 0.5f), Offset(w, h * 0.5f), 1f)
                                    drawLine(Color(0x44FFFFFF), Offset(0f, h), Offset(w, 0f), 1f)
                                    val path = Path()
                                    val steps = 24
                                    for (i in 0..steps) {
                                        val x = i.toFloat() / steps.toFloat()
                                        val y = evalHermiteCurve(x, s.curveBlackVal, s.curveShadowVal, s.curveMidVal, s.curveHighVal, s.curveWhiteVal)
                                        val sx = x * w
                                        val sy = h * (1f - y)
                                        if (i == 0) path.moveTo(sx, sy) else path.lineTo(sx, sy)
                                    }
                                    drawPath(path, Color(0xFF4F9CFF), style = Stroke(width = 2.5f))
                                    val ys = floatArrayOf(s.curveBlackVal, s.curveShadowVal, s.curveMidVal, s.curveHighVal, s.curveWhiteVal)
                                    val xs = floatArrayOf(0f, 0.25f, 0.50f, 0.75f, 1.0f)
                                    for (i in 0..4) {
                                        drawCircle(Color.White, radius = 3f, center = Offset(w * xs[i], h * (1f - ys[i])))
                                    }
                                }
                                Icon(
                                    imageVector = if (s.curveOverlayExpanded) Icons.Default.CloseFullscreen else Icons.Default.OpenInFull,
                                    contentDescription = "Manuel Eğri",
                                    tint = if (s.curveOverlayExpanded) MaterialTheme.colorScheme.primary else Color(0xCCFFFFFF),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(13.dp)
                                )
                            }
                            // 1-tap Pro Presets
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilterChip(
                                        selected = false,
                                        onClick = {
                                            s.curveBlackVal = 0.0f; s.curveShadowVal = 0.17f; s.curveMidVal = 0.50f; s.curveHighVal = 0.84f; s.curveWhiteVal = 1.0f
                                            onPushCurveGpu(s.curveBlackVal, s.curveShadowVal, s.curveMidVal, s.curveHighVal, s.curveWhiteVal)
                                        },
                                        label = { Text("S-Kontrast", fontSize = 10.sp) }
                                    )
                                    FilterChip(
                                        selected = false,
                                        onClick = {
                                            s.curveBlackVal = 0.08f; s.curveShadowVal = 0.34f; s.curveMidVal = 0.52f; s.curveHighVal = 0.72f; s.curveWhiteVal = 0.94f
                                            onPushCurveGpu(s.curveBlackVal, s.curveShadowVal, s.curveMidVal, s.curveHighVal, s.curveWhiteVal)
                                        },
                                        label = { Text("Mat Film", fontSize = 10.sp) }
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilterChip(
                                        selected = false,
                                        onClick = {
                                            s.curveBlackVal = 0.0f; s.curveShadowVal = 0.33f; s.curveMidVal = 0.62f; s.curveHighVal = 0.85f; s.curveWhiteVal = 1.0f
                                            onPushCurveGpu(s.curveBlackVal, s.curveShadowVal, s.curveMidVal, s.curveHighVal, s.curveWhiteVal)
                                        },
                                        label = { Text("Aydınlık", fontSize = 10.sp) }
                                    )
                                    FilterChip(
                                        selected = false,
                                        onClick = {
                                            s.curveBlackVal = 0.0f; s.curveShadowVal = 0.25f; s.curveMidVal = 0.50f; s.curveHighVal = 0.75f; s.curveWhiteVal = 1.0f
                                            onPushCurveGpu(s.curveBlackVal, s.curveShadowVal, s.curveMidVal, s.curveHighVal, s.curveWhiteVal)
                                        },
                                        label = { Text("Sıfırla", fontSize = 10.sp) }
                                    )
                                }
                            }
                        }
                        CompactSliderRow("Gölgeler", "${(s.curveShadowVal * 255f).roundToInt()}", s.curveShadowVal, 0.0f..1.0f, defaultValue = 0.25f) {
                            s.curveShadowVal = it
                            onPushCurveGpu(s.curveBlackVal, s.curveShadowVal, s.curveMidVal, s.curveHighVal, s.curveWhiteVal)
                        }
                        CompactSliderRow("Orta Ton", "${(s.curveMidVal * 255f).roundToInt()}", s.curveMidVal, 0.0f..1.0f, defaultValue = 0.50f) {
                            s.curveMidVal = it
                            onPushCurveGpu(s.curveBlackVal, s.curveShadowVal, s.curveMidVal, s.curveHighVal, s.curveWhiteVal)
                        }
                        CompactSliderRow("Parlaklar", "${(s.curveHighVal * 255f).roundToInt()}", s.curveHighVal, 0.0f..1.0f, defaultValue = 0.75f) {
                            s.curveHighVal = it
                            onPushCurveGpu(s.curveBlackVal, s.curveShadowVal, s.curveMidVal, s.curveHighVal, s.curveWhiteVal)
                        }
                    }
                }

                // Category 3: Kırp & Çevir — 0 ms Instant GPU Preview for 90° Rotate & Flip!
                else -> StudioBottomPanelHostPart2(
                    vm = vm,
                    state = state,
                    s = s,
                    context = context,
                    studioFonts = studioFonts,
                    cinemaThumbs = cinemaThumbs,
                    galleryThumbs = galleryThumbs,
                    lightDistortThumbs = lightDistortThumbs,
                    cropScrollState = cropScrollState,
                    effectScrollState = effectScrollState,
                    onImportFont = onImportFont,
                    onImportXmp = onImportXmp,
                    onImportLut = onImportLut,
                    onPushGpuMirrorPreview = onPushGpuMirrorPreview,
                    onFitToSafeArea = onFitToSafeArea,
                    onClearGpuPreview = onClearGpuPreview,
                    onClearAdjustGpuPreviewOnly = onClearAdjustGpuPreviewOnly,
                    onPushGpuTransformPreview = onPushGpuTransformPreview
                )
            }

}

@Composable
private fun StudioBottomPanelHostPart2(
    vm: EditorViewModel,
    state: EditorUiState,
    s: StudioSessionState,
    context: Context,
    studioFonts: List<EditorViewModel.StudioFontOption>,
    cinemaThumbs: List<ImageBitmap>,
    galleryThumbs: List<ImageBitmap>,
    lightDistortThumbs: List<ImageBitmap>,
    cropScrollState: ScrollState,
    effectScrollState: ScrollState,
    onImportFont: () -> Unit,
    onImportXmp: () -> Unit,
    onImportLut: () -> Unit,
    onPushGpuMirrorPreview: (MirrorModeOption, Float) -> Unit,
    onFitToSafeArea: (Int, BottomPanel) -> Unit,
    onClearGpuPreview: () -> Unit,
    onClearAdjustGpuPreviewOnly: () -> Unit,
    onPushGpuTransformPreview: (Int, Boolean, Boolean) -> Unit
) {
            when (s.activePanel) {
                BottomPanel.CROP_MENU -> {
                    val hasPendingTransform = s.pendingQuarterTurns != 0 || s.pendingFlipH || s.pendingFlipV
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BottomEditorHeader(
                            title = "Kırp & Çevir",
                            subtitle = if (hasPendingTransform) "Anlık önizleme · kaydetmek için Uygula de" else "Fotoğrafı kırp, anlık döndür veya çevir",
                            applyLabel = if (hasPendingTransform) "Uygula" else null,
                            onClose = {
                                if (hasPendingTransform) onClearGpuPreview()
                                s.activePanel = BottomPanel.DOCK
                            },
                            onApply = if (hasPendingTransform) {
                                {
                                    val t = s.pendingQuarterTurns
                                    val fh = s.pendingFlipH
                                    val fv = s.pendingFlipV
                                    s.pendingQuarterTurns = 0
                                    s.pendingFlipH = false
                                    s.pendingFlipV = false
                                    vm.applyTransformBatch(t, fh, fv)
                                    s.activePanel = BottomPanel.DOCK
                                }
                            } else null
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(cropScrollState),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CircularDockButton(
                                icon = Icons.Default.Crop,
                                label = "Oran Kırp",
                                onClick = {
                                    if (hasPendingTransform) onClearGpuPreview()
                                    s.canvasViewRef?.fitToDocument(0)
                                    s.cropScale = 0.90f
                                    s.cropFreeMode = false
                                    s.cropScale = 0.90f
                                    val dw0 = state.docInfo?.width?.toInt() ?: 2048
                                    val dh0 = state.docInfo?.height?.toInt() ?: 2048
                                    val r0 = computeCropRect(dw0, dh0, s.selectedCropPreset.ratio, 0.90f)
                                    s.cropNormL = (r0[0].toFloat() / dw0).coerceIn(0f, 1f)
                                    s.cropNormT = (r0[1].toFloat() / dh0).coerceIn(0f, 1f)
                                    s.cropNormR = ((r0[0] + r0[2]).toFloat() / dw0).coerceIn(0f, 1f)
                                    s.cropNormB = ((r0[1] + r0[3]).toFloat() / dh0).coerceIn(0f, 1f)
                                    s.activePanel = BottomPanel.CROP_RATIO_EDIT
                                }
                            )
                            CircularDockButton(
                                icon = Icons.Default.Transform,
                                label = "Perspektif",
                                onClick = {
                                    if (hasPendingTransform) onClearGpuPreview()
                                    s.canvasViewRef?.fitToDocument(0)
                                    s.pTl = Offset(0.12f, 0.12f)
                                    s.pTr = Offset(0.88f, 0.12f)
                                    s.pBr = Offset(0.88f, 0.88f)
                                    s.pBl = Offset(0.12f, 0.88f)
                                    s.activePanel = BottomPanel.PERSPECTIVE_CROP_EDIT
                                }
                            )
                            CircularDockButton(
                                icon = Icons.Default.PhotoSizeSelectLarge,
                                label = "Boyut",
                                onClick = {
                                    if (hasPendingTransform) onClearGpuPreview()
                                    val dw0 = state.docInfo?.width?.toInt() ?: 1080
                                    val dh0 = state.docInfo?.height?.toInt() ?: 1080
                                    s.resizeWidthText = dw0.toString()
                                    s.resizeHeightText = dh0.toString()
                                    s.resizeLockAspect = true
                                    s.resizeResample = "lanczos"
                                    s.activePanel = BottomPanel.IMAGE_RESIZE_EDIT
                                }
                            )
                            CircularDockButton(
                                icon = Icons.AutoMirrored.Filled.RotateLeft,
                                label = "90° Sol",
                                onClick = {
                                    s.pendingQuarterTurns = (s.pendingQuarterTurns + 3) % 4
                                    onPushGpuTransformPreview(s.pendingQuarterTurns, s.pendingFlipH, s.pendingFlipV)
                                }
                            )
                            CircularDockButton(
                                icon = Icons.AutoMirrored.Filled.RotateRight,
                                label = "90° Sağ",
                                onClick = {
                                    s.pendingQuarterTurns = (s.pendingQuarterTurns + 1) % 4
                                    onPushGpuTransformPreview(s.pendingQuarterTurns, s.pendingFlipH, s.pendingFlipV)
                                }
                            )
                            CircularDockButton(
                                icon = Icons.Default.Flip,
                                label = "Yatay",
                                selected = s.pendingFlipH,
                                onClick = {
                                    s.pendingFlipH = !s.pendingFlipH
                                    onPushGpuTransformPreview(s.pendingQuarterTurns, s.pendingFlipH, s.pendingFlipV)
                                }
                            )
                            CircularDockButton(
                                icon = Icons.Default.SwapVert,
                                label = "Dikey",
                                selected = s.pendingFlipV,
                                onClick = {
                                    s.pendingFlipV = !s.pendingFlipV
                                    onPushGpuTransformPreview(s.pendingQuarterTurns, s.pendingFlipH, s.pendingFlipV)
                                }
                            )
                        }
                    }
                }

                BottomPanel.CROP_RATIO_EDIT -> {
                    val docW = state.docInfo?.width?.toInt() ?: 2048
                    val docH = state.docInfo?.height?.toInt() ?: 2048
                    val cropX = (s.cropNormL * docW).roundToInt().coerceIn(0, (docW - 4).coerceAtLeast(0))
                    val cropY = (s.cropNormT * docH).roundToInt().coerceIn(0, (docH - 4).coerceAtLeast(0))
                    val cropW = ((s.cropNormR - s.cropNormL) * docW).roundToInt().coerceIn(4, (docW - cropX).coerceAtLeast(4))
                    val cropH = ((s.cropNormB - s.cropNormT) * docH).roundToInt().coerceIn(4, (docH - cropY).coerceAtLeast(4))
                    val syncFromPreset = { ratio: Float?, sc: Float ->
                        val r = computeCropRect(docW, docH, ratio, sc)
                        s.cropNormL = (r[0].toFloat() / docW).coerceIn(0f, 1f)
                        s.cropNormT = (r[1].toFloat() / docH).coerceIn(0f, 1f)
                        s.cropNormR = ((r[0] + r[2]).toFloat() / docW).coerceIn(0f, 1f)
                        s.cropNormB = ((r[1] + r[3]).toFloat() / docH).coerceIn(0f, 1f)
                    }
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BottomEditorHeader(
                            title = "Kırp (${cropW}×${cropH})",
                            subtitle = "4 köşeden boyutlandır veya içinden tutup taşı",
                            applyLabel = "Uygula",
                            onClose = { s.activePanel = BottomPanel.CROP_MENU },
                            onApply = {
                                vm.cropCanvas(cropX, cropY, cropW, cropH)
                                s.activePanel = BottomPanel.DOCK
                            }
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(effectScrollState),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = s.cropFreeMode,
                                onClick = { s.cropFreeMode = true },
                                label = { Text("Serbest", fontSize = 11.sp) }
                            )
                            CROP_PRESETS.forEach { preset ->
                                FilterChip(
                                    selected = !s.cropFreeMode && preset == s.selectedCropPreset,
                                    onClick = {
                                        s.cropFreeMode = false
                                        s.selectedCropPreset = preset
                                        syncFromPreset(preset.ratio, s.cropScale)
                                    },
                                    label = { Text(preset.label, fontSize = 11.sp) }
                                )
                            }
                        }
                        CompactSliderRow("Alan", "%${(s.cropScale * 100).roundToInt()}", s.cropScale, 0.40f..1.0f, defaultValue = 1.0f) {
                            s.cropScale = it
                            syncFromPreset(if (s.cropFreeMode) null else s.selectedCropPreset.ratio, it)
                        }
                    }
                }

                BottomPanel.PERSPECTIVE_CROP_EDIT -> {
                    val docW = (state.docInfo?.width?.toInt() ?: 2048).toFloat()
                    val docH = (state.docInfo?.height?.toInt() ?: 2048).toFloat()
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BottomEditorHeader(
                            title = "Perspektif Kırpma",
                            subtitle = "4 mavi köşeyi belge/nesne köşelerine sürükle, Uygula de",
                            applyLabel = "Uygula",
                            onClose = { s.activePanel = BottomPanel.CROP_MENU },
                            onApply = {
                                vm.perspectiveCrop(
                                    s.pTl.x * docW, s.pTl.y * docH,
                                    s.pTr.x * docW, s.pTr.y * docH,
                                    s.pBr.x * docW, s.pBr.y * docH,
                                    s.pBl.x * docW, s.pBl.y * docH
                                )
                                s.activePanel = BottomPanel.DOCK
                            }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Eğik belge, tablo veya bina cephesini düzleştirir.",
                                color = Color(0xFFB4B9CB),
                                fontSize = 11.sp
                            )
                            TextButton(onClick = {
                                s.pTl = Offset(0.12f, 0.12f)
                                s.pTr = Offset(0.88f, 0.12f)
                                s.pBr = Offset(0.88f, 0.88f)
                                s.pBl = Offset(0.12f, 0.88f)
                            }) {
                                Text("Sıfırla", fontSize = 11.sp)
                            }
                        }
                    }
                }

                BottomPanel.FILTER_MENU -> FilterMenuPanel(
                    onClose = { s.activePanel = BottomPanel.DOCK },
                    onBlur = { s.blurRadiusVal = 0f; vm.beginProxyPreview(); s.activePanel = BottomPanel.BLUR_EDIT },
                    onSharpen = { s.sharpenAmountVal = 0f; vm.beginProxyPreview(); s.activePanel = BottomPanel.SHARPEN_EDIT },
                    onMotion = {
                        s.motionBlurAngle = 0f; s.motionBlurDistance = 24f
                        vm.beginProxyPreview(); vm.updateProxyMotionBlur(s.motionBlurAngle, s.motionBlurDistance)
                        s.activePanel = BottomPanel.MOTION_BLUR_EDIT
                    },
                    onRadial = {
                        s.radialBlurAmount = 14f; s.radialBlurMethod = "zoom"
                        vm.beginProxyPreview(); vm.updateProxyRadialBlur(s.radialBlurAmount, s.radialBlurMethod)
                        s.activePanel = BottomPanel.RADIAL_BLUR_EDIT
                    },
                    onGallery = {
                        s.galleryKind = "none"; s.galleryAmount = 45f; s.galleryDetail = 50f
                        vm.loadGalleryThumbs(); vm.beginProxyPreview()
                        s.activePanel = BottomPanel.FILTER_GALLERY_EDIT
                    },
                    onLightDistort = {
                        s.ldKind = "none"; s.ldAmount = 50f; s.ldDetail = 45f; s.ldX = 35f; s.ldY = 30f; s.ldVariant = "moviePrime"
                        vm.loadLightDistortThumbs(); vm.beginProxyPreview()
                        s.activePanel = BottomPanel.LIGHT_DISTORT_EDIT
                    }
                )

                BottomPanel.BLUR_EDIT -> BlurEditPanel(
                    radius = s.blurRadiusVal,
                    onRadius = { s.blurRadiusVal = it; vm.updateProxyBlur(it) },
                    onClose = { onClearAdjustGpuPreviewOnly(); s.activePanel = BottomPanel.FILTER_MENU },
                    onApply = { vm.applyBlurFast(s.blurRadiusVal); s.activePanel = BottomPanel.DOCK }
                )

                BottomPanel.SHARPEN_EDIT -> SharpenEditPanel(
                    amount = s.sharpenAmountVal,
                    onAmount = { s.sharpenAmountVal = it; vm.updateProxySharpen(it) },
                    onClose = { onClearAdjustGpuPreviewOnly(); s.activePanel = BottomPanel.FILTER_MENU },
                    onApply = { vm.applySharpenFast(s.sharpenAmountVal); s.activePanel = BottomPanel.DOCK }
                )
                BottomPanel.QUICK_ACTIONS_MENU -> QuickActionsPanel(
                    onClose = { s.activePanel = BottomPanel.DOCK },
                    onRemoveBg = { vm.smartRemoveBackground(); s.activePanel = BottomPanel.DOCK },
                    onPortraitBlur = { vm.smartPortraitBlur(18f); s.activePanel = BottomPanel.DOCK },
                    onObjectRemove = {
                        vm.setTool(ActiveTool.SELECT_QUICK)
                        s.activePanel = BottomPanel.SELECT_MENU
                        vm.showStatus("Silmek istediğin nesnenin üstünden geçip 'Kaldır'a dokun", 3200L)
                    }
                )
                BottomPanel.HSL_MIXER_LIVE -> HslMixerPanel(
                    bandIdx = s.hslBandIdx, onBand = { s.hslBandIdx = it },
                    hues = s.hslHue, sats = s.hslSat, lums = s.hslLum,
                    onUpdate = { h, sat, l ->
                        s.hslHue = h; s.hslSat = sat; s.hslLum = l
                        vm.updateProxyHslMixer(h, sat, l)
                    },
                    onClose = { vm.endProxyPreview(); s.activePanel = BottomPanel.ADJUST_MENU },
                    onApply = { vm.applyHslMixer(s.hslHue, s.hslSat, s.hslLum); s.activePanel = BottomPanel.DOCK }
                )
                BottomPanel.POSTER_THRESH_LIVE -> PosterThreshPanel(
                    isPoster = s.posterMode,
                    isTouched = s.posterTouched,
                    onMode = { isP ->
                        s.posterMode = isP
                        if (s.posterTouched) {
                            if (isP) vm.updateProxyAdjustment("posterize", """{"levels":${s.posterLevels.roundToInt()}}""")
                            else vm.updateProxyAdjustment("threshold", """{"level":${s.threshLevel.roundToInt()}}""")
                        }
                    },
                    posterLevels = s.posterLevels,
                    onPosterLevels = {
                        val prevInt = s.posterLevels.roundToInt()
                        val nextInt = it.roundToInt()
                        val wasTouched = s.posterTouched
                        s.posterTouched = true
                        s.posterLevels = it
                        if (!wasTouched || nextInt != prevInt) {
                            vm.updateProxyAdjustment("posterize", """{"levels":$nextInt}""")
                        }
                    },
                    threshLevel = s.threshLevel,
                    onThreshLevel = {
                        val prevInt = s.threshLevel.roundToInt()
                        val nextInt = it.roundToInt()
                        val wasTouched = s.posterTouched
                        s.posterTouched = true
                        s.threshLevel = it
                        if (!wasTouched || nextInt != prevInt) {
                            vm.updateProxyAdjustment("threshold", """{"level":$nextInt}""")
                        }
                    },
                    onReset = {
                        s.posterTouched = false
                        s.posterLevels = 8f
                        s.threshLevel = 128f
                        vm.resetProxyPreview()
                    },
                    onClose = { vm.endProxyPreview(); s.activePanel = BottomPanel.ADJUST_MENU },
                    onApply = {
                        if (!s.posterTouched) {
                            vm.endProxyPreview()
                        } else if (s.posterMode) {
                            vm.applyAdjustmentOnce("posterize", """{"levels":${s.posterLevels.roundToInt()}}""", "Posterize")
                        } else {
                            vm.applyAdjustmentOnce("threshold", """{"level":${s.threshLevel.roundToInt()}}""", "Eşik")
                        }
                        s.activePanel = BottomPanel.DOCK
                    }
                )
                BottomPanel.AUTO_ENHANCE_MENU -> AutoEnhancePanel(
                    onClose = { s.activePanel = BottomPanel.ADJUST_MENU },
                    onPick = { mode, lbl -> vm.applyAutoEnhance(mode, lbl); s.activePanel = BottomPanel.DOCK }
                )
                BottomPanel.CINEMA_LOOK_EDIT -> CinemaLookPanel(
                    thumbs = cinemaThumbs,
                    preset = s.cinemaPreset, onPreset = { s.cinemaPreset = it },
                    intensity = s.cinemaIntensity, onIntensity = { s.cinemaIntensity = it },
                    vignette = s.cinemaVignette, onVignette = { s.cinemaVignette = it },
                    grain = s.cinemaGrain, onGrain = { s.cinemaGrain = it },
                    onPreview = { vm.updateProxyCinemaLook(s.cinemaPreset, s.cinemaIntensity, s.cinemaVignette, s.cinemaGrain) },
                    onClose = { vm.endProxyPreview(); s.activePanel = BottomPanel.EFFECT_MENU },
                    onApply = { vm.applyCinemaLook(s.cinemaPreset, s.cinemaIntensity, s.cinemaVignette, s.cinemaGrain); s.activePanel = BottomPanel.DOCK }
                )
                BottomPanel.FILTER_GALLERY_EDIT -> FilterGalleryPanel(
                    thumbs = galleryThumbs,
                    kind = s.galleryKind,
                    onKind = { k ->
                        s.galleryKind = k
                        vm.updateProxyGalleryFilter(k, s.galleryAmount, s.galleryDetail)
                    },
                    amount = s.galleryAmount,
                    onAmount = { a ->
                        s.galleryAmount = a
                        if (s.galleryKind != "none") vm.updateProxyGalleryFilter(s.galleryKind, a, s.galleryDetail)
                    },
                    detail = s.galleryDetail,
                    onDetail = { d ->
                        s.galleryDetail = d
                        if (s.galleryKind != "none") vm.updateProxyGalleryFilter(s.galleryKind, s.galleryAmount, d)
                    },
                    onClose = { vm.endProxyPreview(); s.activePanel = BottomPanel.FILTER_MENU },
                    onApply = { lbl ->
                        if (s.galleryKind == "none") vm.endProxyPreview()
                        else vm.applyGalleryFilter(s.galleryKind, s.galleryAmount, s.galleryDetail, lbl)
                        s.activePanel = BottomPanel.DOCK
                    }
                )
                BottomPanel.LIGHT_DISTORT_EDIT -> LightDistortPanel(
                    thumbs = lightDistortThumbs,
                    kind = s.ldKind,
                    onKind = { k ->
                        s.ldKind = k
                        when (k) {
                            "lensFlare" -> { s.ldAmount = 55f; s.ldX = 35f; s.ldY = 30f; s.ldVariant = "moviePrime" }
                            "lensBlur" -> { s.ldAmount = 40f; s.ldDetail = 40f; s.ldVariant = "hexagon" }
                            "wave" -> { s.ldAmount = 40f; s.ldDetail = 55f; s.ldVariant = "sine" }
                            "spherize" -> { s.ldAmount = 55f; s.ldVariant = "normal" }
                            "twirl" -> { s.ldAmount = 90f; s.ldVariant = "" }
                        }
                        if (k == "none") vm.resetProxyPreview()
                        else vm.updateProxyLightDistort(k, s.ldAmount, s.ldDetail, s.ldX, s.ldY, s.ldVariant)
                    },
                    amount = s.ldAmount,
                    onAmount = { v ->
                        s.ldAmount = v
                        if (s.ldKind != "none") vm.updateProxyLightDistort(s.ldKind, v, s.ldDetail, s.ldX, s.ldY, s.ldVariant)
                    },
                    detail = s.ldDetail,
                    onDetail = { v ->
                        s.ldDetail = v
                        if (s.ldKind != "none") vm.updateProxyLightDistort(s.ldKind, s.ldAmount, v, s.ldX, s.ldY, s.ldVariant)
                    },
                    posX = s.ldX,
                    onPosX = { v ->
                        s.ldX = v
                        if (s.ldKind == "lensFlare") vm.updateProxyLightDistort(s.ldKind, s.ldAmount, s.ldDetail, v, s.ldY, s.ldVariant)
                    },
                    posY = s.ldY,
                    onPosY = { v ->
                        s.ldY = v
                        if (s.ldKind == "lensFlare") vm.updateProxyLightDistort(s.ldKind, s.ldAmount, s.ldDetail, s.ldX, v, s.ldVariant)
                    },
                    variant = s.ldVariant,
                    onVariant = { v ->
                        s.ldVariant = v
                        if (s.ldKind != "none") vm.updateProxyLightDistort(s.ldKind, s.ldAmount, s.ldDetail, s.ldX, s.ldY, v)
                    },
                    onClose = { vm.endProxyPreview(); s.activePanel = BottomPanel.FILTER_MENU },
                    onApply = { lbl ->
                        if (s.ldKind == "none") vm.endProxyPreview()
                        else vm.applyLightDistort(s.ldKind, s.ldAmount, s.ldDetail, s.ldX, s.ldY, s.ldVariant, lbl)
                        s.activePanel = BottomPanel.DOCK
                    }
                )
                BottomPanel.PUPPET_WARP_EDIT -> PuppetWarpPanel(
                    pinCount = s.puppetPins.size,
                    hasSelectedPin = s.puppetSelectedIdx in s.puppetPins.indices,
                    mode = s.puppetMode,
                    onMode = { m ->
                        s.puppetMode = m
                        if (s.puppetPins.isNotEmpty()) vm.updateProxyPuppetWarp(s.puppetPins, m)
                    },
                    onDeleteSelected = {
                        if (s.puppetSelectedIdx in s.puppetPins.indices) {
                            s.puppetUndoStack = (s.puppetUndoStack + listOf(s.puppetPins)).takeLast(40)
                            s.puppetRedoStack = emptyList()
                            val next = s.puppetPins.toMutableList().apply { removeAt(s.puppetSelectedIdx) }
                            s.puppetPins = next
                            s.puppetSelectedIdx = if (next.isEmpty()) -1 else (s.puppetSelectedIdx.coerceAtMost(next.lastIndex))
                            if (next.isEmpty()) vm.resetProxyPreview() else vm.updateProxyPuppetWarp(next, s.puppetMode)
                        }
                    },
                    onReset = {
                        if (s.puppetPins.isNotEmpty()) {
                            s.puppetUndoStack = (s.puppetUndoStack + listOf(s.puppetPins)).takeLast(40)
                            s.puppetRedoStack = emptyList()
                        }
                        s.puppetPins = emptyList()
                        s.puppetSelectedIdx = -1
                        vm.resetProxyPreview()
                    },
                    onClose = { vm.endProxyPreview(); s.activePanel = BottomPanel.EFFECT_MENU },
                    onApply = {
                        vm.applyPuppetWarp(s.puppetPins, s.puppetMode)
                        s.activePanel = BottomPanel.DOCK
                    }
                )

                BottomPanel.IMAGE_RESIZE_EDIT -> ImageResizePanel(
                    origW = state.docInfo?.width?.toInt() ?: 1080,
                    origH = state.docInfo?.height?.toInt() ?: 1080,
                    widthText = s.resizeWidthText,
                    heightText = s.resizeHeightText,
                    lockAspect = s.resizeLockAspect,
                    resample = s.resizeResample,
                    onWidthText = { txt ->
                        val clean = txt.filter { it.isDigit() }.take(4)
                        s.resizeWidthText = clean
                        if (s.resizeLockAspect) {
                            val ow = (state.docInfo?.width?.toInt() ?: 1080).coerceAtLeast(1)
                            val oh = (state.docInfo?.height?.toInt() ?: 1080).coerceAtLeast(1)
                            val nw = clean.toIntOrNull()
                            if (nw != null && nw > 0) {
                                s.resizeHeightText = ((nw.toFloat() * oh / ow).roundToInt().coerceIn(16, 8192)).toString()
                            }
                        }
                    },
                    onHeightText = { txt ->
                        val clean = txt.filter { it.isDigit() }.take(4)
                        s.resizeHeightText = clean
                        if (s.resizeLockAspect) {
                            val ow = (state.docInfo?.width?.toInt() ?: 1080).coerceAtLeast(1)
                            val oh = (state.docInfo?.height?.toInt() ?: 1080).coerceAtLeast(1)
                            val nh = clean.toIntOrNull()
                            if (nh != null && nh > 0) {
                                s.resizeWidthText = ((nh.toFloat() * ow / oh).roundToInt().coerceIn(16, 8192)).toString()
                            }
                        }
                    },
                    onToggleLock = { s.resizeLockAspect = !s.resizeLockAspect },
                    onPreset = { pw, ph ->
                        s.resizeWidthText = pw.coerceIn(16, 8192).toString()
                        s.resizeHeightText = ph.coerceIn(16, 8192).toString()
                    },
                    onResample = { s.resizeResample = it },
                    onClose = { s.activePanel = BottomPanel.CROP_MENU },
                    onApply = {
                        val tw = s.resizeWidthText.toIntOrNull()?.coerceIn(16, 8192) ?: (state.docInfo?.width?.toInt() ?: 1080)
                        val th = s.resizeHeightText.toIntOrNull()?.coerceIn(16, 8192) ?: (state.docInfo?.height?.toInt() ?: 1080)
                        vm.resizeImage(tw, th, s.resizeResample)
                        s.activePanel = BottomPanel.DOCK
                    }
                )
                BottomPanel.MOTION_BLUR_EDIT -> MotionBlurPanel(
                    angle = s.motionBlurAngle, onAngle = { s.motionBlurAngle = it },
                    distance = s.motionBlurDistance, onDistance = { s.motionBlurDistance = it },
                    onPreview = { vm.updateProxyMotionBlur(s.motionBlurAngle, s.motionBlurDistance) },
                    onClose = { vm.endProxyPreview(); s.activePanel = BottomPanel.FILTER_MENU },
                    onApply = {
                        if (s.motionBlurDistance < 1f) vm.endProxyPreview()
                        else vm.applyMotionBlur(s.motionBlurAngle, s.motionBlurDistance)
                        s.activePanel = BottomPanel.DOCK
                    }
                )
                BottomPanel.RADIAL_BLUR_EDIT -> RadialBlurPanel(
                    amount = s.radialBlurAmount, onAmount = { s.radialBlurAmount = it },
                    method = s.radialBlurMethod, onMethod = { s.radialBlurMethod = it },
                    onPreview = { vm.updateProxyRadialBlur(s.radialBlurAmount, s.radialBlurMethod) },
                    onClose = { vm.endProxyPreview(); s.activePanel = BottomPanel.FILTER_MENU },
                    onApply = {
                        if (s.radialBlurAmount < 1f) vm.endProxyPreview()
                        else vm.applyRadialBlur(s.radialBlurAmount, s.radialBlurMethod)
                        s.activePanel = BottomPanel.DOCK
                    }
                )
                BottomPanel.LIQUIFY_EDIT -> LiquifyEditPanel(
                    tool = s.liquifyTool, onTool = { s.liquifyTool = it },
                    size = s.liquifySize, onSize = { s.liquifySize = it },
                    pressure = s.liquifyPressure, onPressure = { s.liquifyPressure = it },
                    onReset = { vm.resetLiquify() },
                    onClose = { vm.cancelLiquify(); s.liquifyCursorScreen = null; s.activePanel = BottomPanel.EFFECT_MENU },
                    onApply = { s.liquifyCursorScreen = null; vm.commitLiquify(); s.activePanel = BottomPanel.DOCK }
                )
                BottomPanel.EFFECT_MENU -> {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BottomEditorHeader(
                            title = "Hızlı Efektler",
                            subtitle = "Siyah-Beyaz, Negatif veya 60 FPS Ayna",
                            onClose = { s.activePanel = BottomPanel.DOCK }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularDockButton(
                                icon = Icons.Default.FilterBAndW,
                                label = "Siyah-Beyaz",
                                selected = state.bwLayerId != null,
                                onClick = { vm.toggleBlackAndWhite() }
                            )
                            CircularDockButton(
                                icon = Icons.Default.InvertColors,
                                label = "Negatif",
                                selected = state.invertLayerId != null,
                                onClick = { vm.toggleInvert() }
                            )
                            CircularDockButton(
                                icon = Icons.Default.Compare,
                                label = "Ayna",
                                onClick = {
                                    s.selectedMirrorMode = MIRROR_MODES.first()
                                    s.mirrorAxisRatio = 0.50f
                                    s.canvasViewRef?.fitToDocument(0)
                                    onPushGpuMirrorPreview(s.selectedMirrorMode, s.mirrorAxisRatio)
                                    s.activePanel = BottomPanel.MIRROR_EDIT
                                }
                            )
                            CircularDockButton(
                                icon = Icons.Default.Waves,
                                label = "Sıvılaştır",
                                onClick = {
                                    s.liquifyTool = "forwardWarp"
                                    val docMax = maxOf(state.docInfo?.width?.toInt() ?: 1024, state.docInfo?.height?.toInt() ?: 1024).toFloat()
                                    s.liquifySize = (docMax * 0.14f).coerceIn(48f, 220f)
                                    s.liquifyPressure = 75f
                                    vm.beginLiquify()
                                    s.activePanel = BottomPanel.LIQUIFY_EDIT
                                }
                            )
                            CircularDockButton(
                                icon = Icons.Default.PushPin,
                                label = "Kukla",
                                onClick = {
                                    s.puppetPins = emptyList()
                                    s.puppetSelectedIdx = -1
                                    s.puppetMode = "normal"
                                    s.puppetUndoStack = emptyList()
                                    s.puppetRedoStack = emptyList()
                                    vm.beginProxyPreview()
                                    s.activePanel = BottomPanel.PUPPET_WARP_EDIT
                                }
                            )
                            CircularDockButton(
                                icon = Icons.Default.MovieFilter,
                                label = "Sinema",
                                onClick = {
                                    s.cinemaPreset = "golden"
                                    s.cinemaIntensity = 80f
                                    s.cinemaVignette = -25f
                                    s.cinemaGrain = 12f
                                    vm.loadCinemaThumbs()
                                    vm.beginProxyPreview()
                                    vm.updateProxyCinemaLook(s.cinemaPreset, s.cinemaIntensity, s.cinemaVignette, s.cinemaGrain)
                                    s.activePanel = BottomPanel.CINEMA_LOOK_EDIT
                                }
                            )
                        }
                    }
                }

                // 60 FPS GPU Mirror Effect — drag right on the photo OR use the slider!
                BottomPanel.MIRROR_EDIT -> {
                    val isEnine = s.selectedMirrorMode.isVerticalAxis
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BottomEditorHeader(
                            title = "Ayna Efekti",
                            subtitle = "Fotoğraf üzerinde parmağınla çizgiyi sürükle",
                            applyLabel = "Uygula",
                            onClose = {
                                onClearGpuPreview()
                                s.activePanel = BottomPanel.EFFECT_MENU
                            },
                            onApply = {
                                val modeId = s.selectedMirrorMode.id
                                val ratio = s.mirrorAxisRatio
                                vm.applyPhotoMirror(modeId, ratio)
                                s.activePanel = BottomPanel.DOCK
                            }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = isEnine,
                                onClick = {
                                    s.selectedMirrorMode = MIRROR_MODES[0]
                                    onPushGpuMirrorPreview(s.selectedMirrorMode, s.mirrorAxisRatio)
                                },
                                label = { Text("Ayna (Enine)", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = !isEnine,
                                onClick = {
                                    s.selectedMirrorMode = MIRROR_MODES[2]
                                    onPushGpuMirrorPreview(s.selectedMirrorMode, s.mirrorAxisRatio)
                                },
                                label = { Text("Ayna (Boyuna)", fontSize = 11.sp) }
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            // Flip mirrored side (Sol->Sağ <-> Sağ->Sol, Üst->Alt <-> Alt->Üst)
                            OutlinedButton(
                                onClick = {
                                    s.selectedMirrorMode = when (s.selectedMirrorMode.id) {
                                        "enine_auto" -> MIRROR_MODES[1]
                                        "enine_flip" -> MIRROR_MODES[0]
                                        "boyuna_auto" -> MIRROR_MODES[3]
                                        else -> MIRROR_MODES[2]
                                    }
                                    onPushGpuMirrorPreview(s.selectedMirrorMode, s.mirrorAxisRatio)
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.SwapHoriz, contentDescription = "Yön Çevir", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Ters", fontSize = 11.sp)
                            }
                        }
                        CompactSliderRow(
                            label = "Pozisyon",
                            valueText = "%${(s.mirrorAxisRatio * 100).roundToInt()}",
                            value = s.mirrorAxisRatio,
                            range = 0.05f..0.95f,
                            defaultValue = 0.50f
                        ) {
                            s.mirrorAxisRatio = it
                            onPushGpuMirrorPreview(s.selectedMirrorMode, it)
                        }
                    }
                }

                BottomPanel.LAYER_TRANSFORM -> {
                    val activeName = state.layers.firstOrNull { it.isActive }?.name ?: "Aktif Katman"
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        BottomEditorHeader(
                            title = "Katmanı Taşı: $activeName",
                            subtitle = "İçinden taşı · Kenar/köşeden serbest uzat · 2 parmak döndür",
                            applyLabel = "Uygula",
                            onClose = {
                                vm.clearActiveLayerPreview()
                                s.activePanel = BottomPanel.DOCK
                            },
                            onApply = {
                                vm.transformActiveLayerXY(s.layerDx, s.layerDy, s.layerScaleXPct, s.layerScaleYPct, s.layerRotDeg)
                                s.activePanel = BottomPanel.DOCK
                            }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = !s.layerLockAspect,
                                onClick = { s.layerLockAspect = false },
                                label = { Text("Serbest Uzat", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = s.layerLockAspect,
                                onClick = {
                                    s.layerLockAspect = true
                                    val avg = (s.layerScaleXPct + s.layerScaleYPct) * 0.5f
                                    s.layerScaleXPct = avg; s.layerScaleYPct = avg; s.layerScalePct = avg
                                },
                                label = { Text("Oranlı", fontSize = 11.sp) }
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                s.layerDx = 0f; s.layerDy = 0f; s.layerScaleXPct = 100f; s.layerScaleYPct = 100f; s.layerScalePct = 100f; s.layerRotDeg = 0f
                            }) {
                                Text("Sıfırla", fontSize = 11.sp, color = Color(0xFFB8BDD0))
                            }
                        }
                        if (s.layerLockAspect) {
                            CompactSliderRow("Boyut", "%${s.layerScaleXPct.roundToInt()}", s.layerScaleXPct, 10f..350f, defaultValue = 100f) {
                                s.layerScaleXPct = it; s.layerScaleYPct = it; s.layerScalePct = it
                            }
                        } else {
                            CompactSliderRow("Genişlik", "%${s.layerScaleXPct.roundToInt()}", s.layerScaleXPct, 10f..350f, defaultValue = 100f) {
                                s.layerScaleXPct = it
                            }
                            CompactSliderRow("Yükseklik", "%${s.layerScaleYPct.roundToInt()}", s.layerScaleYPct, 10f..350f, defaultValue = 100f) {
                                s.layerScaleYPct = it
                            }
                        }
                        CompactSliderRow("Döndür", "${s.layerRotDeg.roundToInt()}°", s.layerRotDeg, -180f..180f, defaultValue = 0f) {
                            s.layerRotDeg = it
                        }
                    }
                }

                BottomPanel.LAYER_FX_EDIT -> {
                    val activeName = state.layers.firstOrNull { it.isActive }?.name ?: "Katman"
                    fun pushFxLive() {
                        vm.updateProxyLayerEffect(s.fxKind, s.fxColorHex, s.fxSizeVal, s.fxOpacityVal, s.fxDistanceVal)
                    }
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BottomEditorHeader(
                            title = "Katman Efektleri (fx): $activeName",
                            subtitle = "60 FPS canlı önizleme — beğenirsen Uygula de",
                            applyLabel = "Uygula",
                            onClose = {
                                vm.endProxyPreview()
                                s.activePanel = BottomPanel.DOCK
                            },
                            onApply = {
                                vm.applyLayerEffect(s.fxKind, s.fxColorHex, s.fxSizeVal, s.fxOpacityVal, s.fxDistanceVal)
                                s.activePanel = BottomPanel.DOCK
                            }
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "dropShadow" to "Dış Gölge",
                                "innerShadow" to "İç Gölge",
                                "stroke" to "Kontur",
                                "outerGlow" to "Dış Işıma",
                                "innerGlow" to "İç Işıma",
                                "colorOverlay" to "Renk",
                                "gradientOverlay" to "Degrade"
                            ).forEach { (id, lbl) ->
                                FilterChip(
                                    selected = s.fxKind == id,
                                    onClick = {
                                        s.fxKind = id
                                        when (id) {
                                            "dropShadow", "innerShadow" -> s.fxColorHex = "#000000"
                                            "stroke" -> s.fxColorHex = "#FFFFFF"
                                            "outerGlow", "innerGlow" -> s.fxColorHex = "#4F9CFF"
                                            "gradientOverlay" -> { s.fxColorHex = "#4F9CFF"; s.fxDistanceVal = 90f }
                                        }
                                        pushFxLive()
                                    },
                                    label = { Text(lbl, fontSize = 11.sp) }
                                )
                            }
                            FilterChip(
                                selected = s.showFxFineTune,
                                onClick = { s.showFxFineTune = !s.showFxFineTune },
                                label = { Text(if (s.showFxFineTune) "İnce ▲" else "İnce ▼", fontSize = 11.sp) }
                            )
                            OutlinedButton(
                                onClick = {
                                    vm.endProxyPreview()
                                    vm.clearLayerEffects()
                                    s.activePanel = BottomPanel.DOCK
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Temizle", fontSize = 10.sp)
                            }
                        }
                        // Temel: Renk + Boyut (60 FPS live preview!)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf("#000000", "#FFFFFF", "#FF4D4F", "#FFEC3D", "#2ED573", "#4F9CFF", "#B37FEB").forEach { hex ->
                                val c = Color(AndroidColor.parseColor(hex))
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(c)
                                        .border(
                                            width = if (s.fxColorHex.equals(hex, true)) 2.5.dp else 1.dp,
                                            color = if (s.fxColorHex.equals(hex, true)) MaterialTheme.colorScheme.primary else Color.Gray,
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            s.fxColorHex = hex
                                            pushFxLive()
                                        }
                                )
                            }
                        }
                        if (s.fxKind != "colorOverlay") {
                            CompactSliderRow("Boyut", "${s.fxSizeVal.roundToInt()}px", s.fxSizeVal, 1f..80f, defaultValue = 16f) {
                                s.fxSizeVal = it
                                pushFxLive()
                            }
                        }
                        if (s.showFxFineTune) {
                            CompactSliderRow("Opaklık", "%${s.fxOpacityVal.roundToInt()}", s.fxOpacityVal, 5f..100f, defaultValue = 75f) {
                                s.fxOpacityVal = it
                                pushFxLive()
                            }
                            if (s.fxKind == "gradientOverlay") {
                                CompactSliderRow("Açı", "${s.fxDistanceVal.roundToInt()}°", s.fxDistanceVal, 0f..360f, defaultValue = 90f) {
                                    s.fxDistanceVal = it
                                    pushFxLive()
                                }
                            } else if (s.fxKind == "dropShadow" || s.fxKind == "innerShadow") {
                                CompactSliderRow("Uzaklık", "${s.fxDistanceVal.roundToInt()}px", s.fxDistanceVal, 0f..80f, defaultValue = 12f) {
                                    s.fxDistanceVal = it
                                    pushFxLive()
                                }
                            }
                        }
                    }
                }
                BottomPanel.LAYER_MASK_EDIT -> {
                    val activeL = state.layers.firstOrNull { it.isActive }
                    LayerMaskPanel(
                        layerName = activeL?.name ?: "Katman",
                        hasMask = activeL?.hasMask == true,
                        maskEnabled = activeL?.maskEnabled == true,
                        hasSelection = state.selectionBounds != null,
                        maskPaintMode = state.maskPaintMode,
                        onAddRevealAll = { vm.addLayerMask("revealAll") },
                        onAddHideAll = { vm.addLayerMask("hideAll") },
                        onAddFromSelection = { vm.addLayerMask("revealSelection") },
                        onPaintHide = {
                            vm.enterMaskPaintMode(hideWithBlack = true)
                            s.activePanel = BottomPanel.DOCK
                        },
                        onPaintReveal = {
                            vm.enterMaskPaintMode(hideWithBlack = false)
                            s.activePanel = BottomPanel.DOCK
                        },
                        onToggleEnabled = {
                            if (activeL != null) vm.toggleLayerMaskEnabled(activeL.id, !activeL.maskEnabled)
                        },
                        onInvert = { vm.invertActiveLayerMask() },
                        onApplyMask = { vm.applyActiveLayerMask(); s.activePanel = BottomPanel.DOCK },
                        onDeleteMask = { vm.deleteActiveLayerMask() },
                        onClose = { s.activePanel = BottomPanel.DOCK }
                    )
                }
                BottomPanel.BLEND_IF_EDIT -> {
                    val activeName = state.layers.firstOrNull { it.isActive }?.name ?: "Katman"
                    fun pushBi() {
                        vm.updateProxyBlendIf(
                            s.blendIfThisBlack.roundToInt(),
                            s.blendIfThisWhite.roundToInt(),
                            s.blendIfUnderBlack.roundToInt(),
                            s.blendIfUnderWhite.roundToInt(),
                            s.blendIfFeather.roundToInt()
                        )
                    }
                    BlendIfPanel(
                        layerName = activeName,
                        thisBlack = s.blendIfThisBlack, onThisBlack = { s.blendIfThisBlack = it; pushBi() },
                        thisWhite = s.blendIfThisWhite, onThisWhite = { s.blendIfThisWhite = it; pushBi() },
                        underBlack = s.blendIfUnderBlack, onUnderBlack = { s.blendIfUnderBlack = it; pushBi() },
                        underWhite = s.blendIfUnderWhite, onUnderWhite = { s.blendIfUnderWhite = it; pushBi() },
                        feather = s.blendIfFeather, onFeather = { s.blendIfFeather = it; pushBi() },
                        onReset = {
                            s.blendIfThisBlack = 0f; s.blendIfThisWhite = 255f
                            s.blendIfUnderBlack = 0f; s.blendIfUnderWhite = 255f; s.blendIfFeather = 20f
                            vm.resetBlendIf()
                        },
                        onClose = { vm.endProxyPreview(); s.activePanel = BottomPanel.DOCK },
                        onApply = {
                            vm.applyBlendIf(
                                s.blendIfThisBlack.roundToInt(),
                                s.blendIfThisWhite.roundToInt(),
                                s.blendIfUnderBlack.roundToInt(),
                                s.blendIfUnderWhite.roundToInt(),
                                s.blendIfFeather.roundToInt()
                            )
                            s.activePanel = BottomPanel.DOCK
                        }
                    )
                }
                BottomPanel.LENS_CORRECTION_EDIT -> LensCorrectionPanel(
                    s = s,
                    onPreview = { vm.updateProxyLensCorrection(s.lensDistortion, s.lensVignette, s.lensRedCyan, s.lensBlueYellow, s.lensVertical, s.lensHorizontal) },
                    onReset = {
                        s.lensDistortion = 0f; s.lensVignette = 0f; s.lensRedCyan = 0f
                        s.lensBlueYellow = 0f; s.lensVertical = 0f; s.lensHorizontal = 0f
                        vm.resetProxyPreview()
                    },
                    onClose = { vm.endProxyPreview(); s.activePanel = BottomPanel.ADJUST_MENU },
                    onApply = {
                        vm.applyLensCorrection(s.lensDistortion, s.lensVignette, s.lensRedCyan, s.lensBlueYellow, s.lensVertical, s.lensHorizontal)
                        s.activePanel = BottomPanel.DOCK
                    }
                )
                BottomPanel.LUT_IMPORT_EDIT -> CubeLutPanel(
                    fileName = s.lutFileName,
                    opacityPct = s.lutOpacity,
                    onPickFile = onImportLut,
                    onOpacity = {
                        s.lutOpacity = it
                        if (s.lutText.isNotBlank()) vm.updateProxyColorLookup(s.lutFileName, s.lutText, it)
                    },
                    onClose = { vm.endProxyPreview(); s.activePanel = BottomPanel.ADJUST_MENU },
                    onApply = {
                        if (s.lutText.isNotBlank()) {
                            vm.applyColorLookup(s.lutFileName, s.lutText, s.lutOpacity)
                        } else {
                            vm.endProxyPreview()
                        }
                        s.activePanel = BottomPanel.DOCK
                    }
                )
                else -> Unit
            }

}
