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

@Stable
internal class StudioSessionState {
    var ldKind by mutableStateOf("none")
    var ldAmount by mutableStateOf(50f)
    var ldDetail by mutableStateOf(45f)
    var ldX by mutableStateOf(35f)
    var ldY by mutableStateOf(30f)
    var ldVariant by mutableStateOf("moviePrime")
    var puppetPins by mutableStateOf<List<PuppetPinPoint>>(emptyList())
    var puppetSelectedIdx by mutableIntStateOf(-1)
    var puppetMode by mutableStateOf("normal")
    var puppetUndoStack by mutableStateOf<List<List<PuppetPinPoint>>>(emptyList())
    var puppetRedoStack by mutableStateOf<List<List<PuppetPinPoint>>>(emptyList())
    var resizeWidthText by mutableStateOf("1080")
    var resizeHeightText by mutableStateOf("1080")
    var resizeLockAspect by mutableStateOf(true)
    var resizeResample by mutableStateOf("lanczos")
    var loupeScreenPos by mutableStateOf<Offset?>(null)
    var showLayers by mutableStateOf(false)
    var singleFingerPanMode by mutableStateOf(false)
    var activePanel by mutableStateOf(BottomPanel.DOCK)
    var showHsvPicker by mutableStateOf(false)
    var showOpenMenu by mutableStateOf(false)
    var showExportMenu by mutableStateOf(false)
    var showBlendMenu by mutableStateOf(false)
    var pendingExportFormat by mutableStateOf("png")
    var openAsLayer by mutableStateOf(false)
    var canvasViewRef by mutableStateOf<VulkanCanvasView?>(null)
    var rulerPreview by mutableStateOf<RulerLinePreview?>(null)
    var retouchCursor by mutableStateOf<Pair<Float, Float>?>(null)
    var retouchTrailPoints by mutableStateOf<List<Offset>>(emptyList())
    var cropNormL by mutableFloatStateOf(0.05f)
    var cropNormT by mutableFloatStateOf(0.05f)
    var cropNormR by mutableFloatStateOf(0.95f)
    var cropNormB by mutableFloatStateOf(0.95f)
    var cropFreeMode by mutableStateOf(false)
    var selectPreviewRect by mutableStateOf<FloatArray?>(null)
    var levelsBlackVal by mutableFloatStateOf(0f)
    var levelsGammaVal by mutableFloatStateOf(1f)
    var levelsWhiteVal by mutableFloatStateOf(255f)
    var cbCyanRedVal by mutableFloatStateOf(0f)
    var cbMagentaGreenVal by mutableFloatStateOf(0f)
    var cbYellowBlueVal by mutableFloatStateOf(0f)
    var curveBlackVal by mutableFloatStateOf(0.0f)
    var curveShadowVal by mutableFloatStateOf(0.25f)
    var curveMidVal by mutableFloatStateOf(0.50f)
    var curveHighVal by mutableFloatStateOf(0.75f)
    var curveWhiteVal by mutableFloatStateOf(1.0f)
    var curveOverlayExpanded by mutableStateOf(false)
    var draggingCurveNode by mutableIntStateOf(-1)
    var pTl by mutableStateOf(Offset(0.12f, 0.12f))
    var pTr by mutableStateOf(Offset(0.88f, 0.12f))
    var pBr by mutableStateOf(Offset(0.88f, 0.88f))
    var pBl by mutableStateOf(Offset(0.12f, 0.88f))
    var draggingCornerIdx by mutableIntStateOf(-1)
    var layerDx by mutableFloatStateOf(0f)
    var layerDy by mutableFloatStateOf(0f)
    var layerScalePct by mutableFloatStateOf(100f)
    var layerScaleXPct by mutableFloatStateOf(100f)
    var layerScaleYPct by mutableFloatStateOf(100f)
    var layerLockAspect by mutableStateOf(false)
    var layerRotDeg by mutableFloatStateOf(0f)
    var showBrushFineTune by mutableStateOf(false)
    var brushSizePreviewTick by mutableIntStateOf(0)
    var showBrushSizeCircle by mutableStateOf(false)
    var fxKind by mutableStateOf("dropShadow")
    var fxColorHex by mutableStateOf("#000000")
    var fxSizeVal by mutableFloatStateOf(16f)
    var fxOpacityVal by mutableFloatStateOf(75f)
    var fxDistanceVal by mutableFloatStateOf(12f)
    var showFxFineTune by mutableStateOf(false)
    var showExitSaveDialog by mutableStateOf(false)
    var textBoxSize by mutableStateOf(androidx.compose.ui.unit.IntSize.Zero)
    var camExposureVal by mutableFloatStateOf(0f)
    var camShadowsVal by mutableFloatStateOf(0f)
    var camHighlightsVal by mutableFloatStateOf(0f)
    var camVibranceVal by mutableFloatStateOf(0f)
    var camTempVal by mutableFloatStateOf(0f)
    var camTintVal by mutableFloatStateOf(0f)
    var camClarityVal by mutableFloatStateOf(0f)
    var camDehazeVal by mutableFloatStateOf(0f)
    var showCamFineTune by mutableStateOf(false)
    var gradFromNormX by mutableFloatStateOf(0.20f)
    var gradFromNormY by mutableFloatStateOf(0.20f)
    var gradToNormX by mutableFloatStateOf(0.80f)
    var gradToNormY by mutableFloatStateOf(0.80f)
    var gradStyle by mutableStateOf("linear")
    var gradStartHex by mutableStateOf("#4F9CFF")
    var gradEndHex by mutableStateOf("#FF4D4F")
    var gradOpacity by mutableFloatStateOf(85f)
    var shapeKind by mutableStateOf("roundedRect")
    var shapeX0Norm by mutableFloatStateOf(0.25f)
    var shapeY0Norm by mutableFloatStateOf(0.25f)
    var shapeX1Norm by mutableFloatStateOf(0.75f)
    var shapeY1Norm by mutableFloatStateOf(0.75f)
    var shapeFillHex by mutableStateOf("#4F9CFF")
    var shapeStrokeHex by mutableStateOf("#FFFFFF")
    var shapeStrokeWidth by mutableFloatStateOf(6f)
    var shapeCornerRadius by mutableFloatStateOf(24f)
    var shapeSides by mutableIntStateOf(6)
    var showShapeFineTune by mutableStateOf(false)
    var liquifyTool by mutableStateOf("forwardWarp")
    var liquifySize by mutableFloatStateOf(120f)
    var liquifyPressure by mutableFloatStateOf(75f)
    var liquifyCursorScreen by mutableStateOf<Offset?>(null)
    var cinemaPreset by mutableStateOf("golden")
    var cinemaIntensity by mutableFloatStateOf(80f)
    var cinemaVignette by mutableFloatStateOf(-25f)
    var cinemaGrain by mutableFloatStateOf(12f)
    var motionBlurAngle by mutableFloatStateOf(0f)
    var motionBlurDistance by mutableFloatStateOf(24f)
    var radialBlurAmount by mutableFloatStateOf(14f)
    var radialBlurMethod by mutableStateOf("zoom")
    var hslBandIdx by mutableIntStateOf(0)
    var hslHue by mutableStateOf(List(8) { 0f })
    var hslSat by mutableStateOf(List(8) { 0f })
    var hslLum by mutableStateOf(List(8) { 0f })
    var bucketTolerance by mutableFloatStateOf(36f)
    var bucketContiguous by mutableStateOf(true)
    var posterMode by mutableStateOf(true)
    var posterLevels by mutableFloatStateOf(6f)
    var threshLevel by mutableFloatStateOf(128f)
    var posterTouched by mutableStateOf(false)
    var galleryKind by mutableStateOf("none")
    var galleryAmount by mutableFloatStateOf(45f)
    var galleryDetail by mutableFloatStateOf(50f)
    var textDraft by mutableStateOf("StonebridgeCraft")
    var textNormX by mutableFloatStateOf(0.50f)
    var textNormY by mutableFloatStateOf(0.50f)
    var textSizePx by mutableFloatStateOf(140f)
    var textColorHex by mutableStateOf("#FFFFFF")
    var textAlign by mutableStateOf("center")
    var textFontFamily by mutableStateOf("Montserrat")
    var textBold by mutableStateOf(false)
    var textItalic by mutableStateOf(false)
    var textTracking by mutableFloatStateOf(0f)
    var textLineHeight by mutableFloatStateOf(1.2f)
    var textVertical by mutableStateOf(false)
    var textEditLayerId by mutableStateOf<ULong?>(null)
    var textUnderline by mutableStateOf(false)
    var textStrike by mutableStateOf(false)
    var textCaps by mutableStateOf("normal")
    var textHScale by mutableFloatStateOf(100f)
    var textVScale by mutableFloatStateOf(100f)
    var textWarpStyle by mutableStateOf("none")
    var textWarpBend by mutableFloatStateOf(50f)
    var textWarpHDist by mutableFloatStateOf(0f)
    var textWarpVDist by mutableFloatStateOf(0f)
    var cameraTick by mutableIntStateOf(0)
    var brightnessVal by mutableFloatStateOf(0f)
    var contrastVal by mutableFloatStateOf(0f)
    var hueVal by mutableFloatStateOf(0f)
    var saturationVal by mutableFloatStateOf(0f)
    var blurRadiusVal by mutableFloatStateOf(0f)
    var sharpenAmountVal by mutableFloatStateOf(0f)
    var selectedMirrorMode by mutableStateOf(MIRROR_MODES.first())
    var mirrorAxisRatio by mutableFloatStateOf(0.50f)
    var pendingQuarterTurns by mutableIntStateOf(0)
    var pendingFlipH by mutableStateOf(false)
    var pendingFlipV by mutableStateOf(false)
    var selectedCropPreset by mutableStateOf(CROP_PRESETS.first())
    var cropScale by mutableFloatStateOf(0.90f)
}
