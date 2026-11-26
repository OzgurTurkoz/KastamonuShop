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

internal enum class BottomPanel {
    DOCK,
    DRAW_MENU,
    SELECT_MENU,
    TEXT_EDIT,
    ADJUST_MENU,
    LIGHT_LIVE,
    COLOR_LIVE,
    LEVELS_LIVE,
    COLOR_BAL_LIVE,
    CURVES_LIVE,
    CROP_MENU,
    CROP_RATIO_EDIT,
    PERSPECTIVE_CROP_EDIT,
    FILTER_MENU,
    BLUR_EDIT,
    SHARPEN_EDIT,
    EFFECT_MENU,
    MIRROR_EDIT,
    LAYER_TRANSFORM,
    LAYER_FX_EDIT,
    LAYER_MASK_EDIT,
    BLEND_IF_EDIT,
    LENS_CORRECTION_EDIT,
    LUT_IMPORT_EDIT,
    CAMERA_LIGHT_LIVE,
    GRADIENT_EDIT,
    SHAPE_EDIT,
    LIQUIFY_EDIT,
    AUTO_ENHANCE_MENU,
    CINEMA_LOOK_EDIT,
    MOTION_BLUR_EDIT,
    RADIAL_BLUR_EDIT,
    QUICK_ACTIONS_MENU,
    HSL_MIXER_LIVE,
    POSTER_THRESH_LIVE,
    FILTER_GALLERY_EDIT,
    LIGHT_DISTORT_EDIT,
    PUPPET_WARP_EDIT,
    IMAGE_RESIZE_EDIT
}

internal data class BlendModeOption(
    val engineName: String,
    val labelTr: String,
    val hintTr: String
)

internal data class CropRatioPreset(
    val label: String,
    val ratio: Float?
)

internal data class MirrorModeOption(
    val id: String,
    val gpuId: Int,
    val label: String,
    val isVerticalAxis: Boolean
)

internal val MIRROR_MODES = listOf(
    MirrorModeOption("enine_auto", 1, "Enine (Sol→Sağ)", true),
    MirrorModeOption("enine_flip", 2, "Enine (Sağ→Sol)", true),
    MirrorModeOption("boyuna_auto", 3, "Boyuna (Üst→Alt)", false),
    MirrorModeOption("boyuna_flip", 4, "Boyuna (Alt→Üst)", false)
)

internal val CROP_PRESETS = listOf(
    CropRatioPreset("1:1", 1f),
    CropRatioPreset("4:5", 4f / 5f),
    CropRatioPreset("3:4", 3f / 4f),
    CropRatioPreset("16:9", 16f / 9f),
    CropRatioPreset("9:16", 9f / 16f),
    CropRatioPreset("Orijinal", null)
)

internal val BEGINNER_BLEND_MODES = listOf(
    BlendModeOption("Normal", "Normal", "Standart görünüm, üstteki alttakini örter"),
    BlendModeOption("Multiply", "Çoğalt (Multiply)", "Koyu tonları korur, gölge için ideal"),
    BlendModeOption("Screen", "Ekran (Screen)", "Parlak tonları korur, ışık ve parlama için ideal"),
    BlendModeOption("Overlay", "Kaplama (Overlay)", "Kontrastı artırır, renkleri canlandırır"),
    BlendModeOption("SoftLight", "Yumuşak Işık", "Fotoğrafa doğal ve yumuşak renk tonu verir")
)

internal fun hexToHsv(hex: String): FloatArray {
    val hsv = FloatArray(3)
    val c = runCatching { AndroidColor.parseColor(hex) }.getOrDefault(AndroidColor.RED)
    AndroidColor.colorToHSV(c, hsv)
    return hsv
}

internal fun hsvToHex(h: Float, s: Float, v: Float): String {
    val c = AndroidColor.HSVToColor(floatArrayOf(h.coerceIn(0f, 360f), s.coerceIn(0f, 1f), v.coerceIn(0f, 1f)))
    return String.format("#%06X", 0xFFFFFF and c)
}

internal fun computeCropRect(docW: Int, docH: Int, ratio: Float?, scale: Float): IntArray {
    val w = docW.coerceAtLeast(1).toFloat()
    val h = docH.coerceAtLeast(1).toFloat()
    val targetRatio = ratio ?: (w / h)
    var cw = w
    var ch = cw / targetRatio
    if (ch > h) {
        ch = h
        cw = ch * targetRatio
    }
    cw *= scale.coerceIn(0.35f, 1f)
    ch *= scale.coerceIn(0.35f, 1f)
    val x = ((w - cw) * 0.5f).roundToInt().coerceAtLeast(0)
    val y = ((h - ch) * 0.5f).roundToInt().coerceAtLeast(0)
    val rw = cw.roundToInt().coerceIn(1, docW - x)
    val rh = ch.roundToInt().coerceIn(1, docH - y)
    return intArrayOf(x, y, rw, rh)
}

@Composable
internal fun TopBarButton(
    icon: ImageVector,
    label: String,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else Color.Transparent
    val tint = when {
        !enabled -> Color(0xFF5D6170)
        selected -> MaterialTheme.colorScheme.primary
        else -> Color.White
    }
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = label, color = tint, fontSize = 9.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
internal fun CircularDockButton(
    icon: ImageVector,
    label: String,
    selected: Boolean = false,
    accentDot: Color? = null,
    onClick: () -> Unit
) {
    val circleBg = if (selected) MaterialTheme.colorScheme.primary else Color(0xFF2D2F3A)
    val iconTint = if (selected) Color(0xFF0E1117) else Color.White
    val textTint = if (selected) MaterialTheme.colorScheme.primary else Color(0xFFDCE0EC)

    Column(
        modifier = Modifier
            .width(68.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(circleBg)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) Color.White else Color(0xFF3E4150),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = iconTint, modifier = Modifier.size(21.dp))
            if (accentDot != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(accentDot)
                        .border(1.5.dp, Color.White, CircleShape)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, color = textTint, fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun CompactSliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    defaultValue: Float,
    forceResetEnabled: Boolean = false,
    onReset: (() -> Unit)? = null,
    onChange: (Float) -> Unit
) {
    val isModified = forceResetEnabled || abs(value - defaultValue) > ((range.endInclusive - range.start) * 0.004f)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color(0xFFDCE0EC),
            fontSize = 11.sp,
            lineHeight = 13.sp,
            maxLines = 2,
            modifier = Modifier.width(74.dp)
        )
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier
                .weight(1f)
                .height(28.dp)
        )
        Text(
            text = valueText,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            modifier = Modifier
                .width(40.dp)
                .padding(end = 4.dp)
        )
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(if (isModified) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else Color(0xFF2A2C36))
                .clickable(enabled = isModified) { if (onReset != null) onReset() else onChange(defaultValue) },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.RestartAlt,
                contentDescription = "Sıfırla",
                tint = if (isModified) MaterialTheme.colorScheme.primary else Color(0xFF5A5E70),
                modifier = Modifier.size(15.dp)
            )
        }
    }
}


@Composable
internal fun BrushSizeSliderRow(
    label: String,
    sizePx: Float,
    minPx: Float = 1f,
    maxPx: Float = 250f,
    defaultPx: Float = 28f,
    onChange: (Float) -> Unit
) {
    // Quadratic precision curve: gives 1..16px fine-detail brushes ~25% of slider travel while still reaching 250px
    val span = (maxPx - minPx).coerceAtLeast(1f)
    val clamped = sizePx.coerceIn(minPx, maxPx)
    val norm = kotlin.math.sqrt(((clamped - minPx) / span).coerceIn(0f, 1f))
    val defaultNorm = kotlin.math.sqrt(((defaultPx.coerceIn(minPx, maxPx) - minPx) / span).coerceIn(0f, 1f))
    CompactSliderRow(
        label = label,
        valueText = "${clamped.roundToInt()}px",
        value = norm,
        range = 0f..1f,
        defaultValue = defaultNorm,
        onChange = { t ->
            val mapped = (minPx + span * t * t).roundToInt().toFloat().coerceIn(minPx, maxPx)
            onChange(mapped)
        }
    )
}

@Composable
internal fun BottomEditorHeader(
    title: String,
    subtitle: String,
    applyLabel: String? = null,
    onClose: () -> Unit,
    onApply: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            IconButton(onClick = onClose, modifier = Modifier.size(30.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Column(modifier = Modifier.padding(start = 4.dp)) {
                Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(subtitle, color = Color(0xFF9EA4B8), fontSize = 10.sp, maxLines = 1)
            }
        }
        if (applyLabel != null && onApply != null) {
            Button(
                onClick = onApply,
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Text(applyLabel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}


@Composable
internal fun FloatingLoupeOverlay(
    fingerScreenPos: Offset,
    loupeBitmap: ImageBitmap,
    isEyedropper: Boolean,
    eyedropperColorHex: String
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val screenW = with(density) { config.screenWidthDp.dp.toPx() }
    val loupeSizeDp = 112.dp
    val loupePx = with(density) { loupeSizeDp.toPx() }
    val offsetAbovePx = with(density) { 118.dp.toPx() }
    val topSafePx = with(density) { 86.dp.toPx() }
    val marginPx = with(density) { 12.dp.toPx() }

    val targetX = (fingerScreenPos.x - loupePx * 0.5f).coerceIn(marginPx, (screenW - loupePx - marginPx).coerceAtLeast(marginPx))
    val targetY = if (fingerScreenPos.y - offsetAbovePx - loupePx * 0.5f >= topSafePx) {
        fingerScreenPos.y - offsetAbovePx - loupePx * 0.5f
    } else {
        topSafePx
    }
    val finalX = if (fingerScreenPos.y - offsetAbovePx - loupePx * 0.5f < topSafePx) {
        if (fingerScreenPos.x < screenW * 0.5f) (screenW - loupePx - marginPx) else marginPx
    } else targetX

    val ringColor = remember(isEyedropper, eyedropperColorHex) {
        if (isEyedropper) {
            runCatching { Color(AndroidColor.parseColor(eyedropperColorHex)) }.getOrDefault(Color.White)
        } else {
            Color(0xFF4F9CFF)
        }
    }

    Column(
        modifier = Modifier.offset { androidx.compose.ui.unit.IntOffset(finalX.roundToInt(), targetY.roundToInt()) },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(loupeSizeDp)
                .clip(CircleShape)
                .background(Color(0xFF1A1B22))
                .border(3.dp, ringColor, CircleShape)
                .border(1.dp, Color.White, CircleShape)
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                drawImage(
                    image = loupeBitmap,
                    dstSize = androidx.compose.ui.unit.IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    filterQuality = androidx.compose.ui.graphics.FilterQuality.None
                )
                val cx = size.width * 0.5f
                val cy = size.height * 0.5f
                val arm = 12f
                drawLine(Color.Black.copy(alpha = 0.75f), Offset(cx - arm, cy), Offset(cx + arm, cy), strokeWidth = 3.5f)
                drawLine(Color.Black.copy(alpha = 0.75f), Offset(cx, cy - arm), Offset(cx, cy + arm), strokeWidth = 3.5f)
                drawLine(Color.White, Offset(cx - arm + 1f, cy), Offset(cx + arm - 1f, cy), strokeWidth = 1.6f)
                drawLine(Color.White, Offset(cx, cy - arm + 1f), Offset(cx, cy + arm - 1f), strokeWidth = 1.6f)
            }
        }
        if (isEyedropper) {
            Spacer(modifier = Modifier.height(4.dp))
            Surface(shape = RoundedCornerShape(10.dp), color = Color(0xEE14151B)) {
                Text(
                    text = eyedropperColorHex.uppercase(),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}



internal fun evalHermiteCurve(x: Float, y0: Float, y1: Float, y2: Float, y3: Float, y4: Float): Float {
    val xc = x.coerceIn(0f, 1f)
    val m0 = (y1 - y0) * 4f
    val m1 = (y2 - y0) * 2f
    val m2 = (y3 - y1) * 2f
    val m3 = (y4 - y2) * 2f
    val m4 = (y4 - y3) * 4f
    val p0: Float; val p1: Float; val t0: Float; val t1: Float; val t: Float
    when {
        xc >= 0.75f -> { p0 = y3; p1 = y4; t0 = m3; t1 = m4; t = (xc - 0.75f) * 4f }
        xc >= 0.50f -> { p0 = y2; p1 = y3; t0 = m2; t1 = m3; t = (xc - 0.50f) * 4f }
        xc >= 0.25f -> { p0 = y1; p1 = y2; t0 = m1; t1 = m2; t = (xc - 0.25f) * 4f }
        else -> { p0 = y0; p1 = y1; t0 = m0; t1 = m1; t = xc * 4f }
    }
    val t2 = t * t
    val t3 = t2 * t
    val h00 = 2f * t3 - 3f * t2 + 1f
    val h10 = t3 - 2f * t2 + t
    val h01 = -2f * t3 + 3f * t2
    val h11 = t3 - t2
    return (h00 * p0 + h10 * (t0 * 0.25f) + h01 * p1 + h11 * (t1 * 0.25f)).coerceIn(0f, 1f)
}
