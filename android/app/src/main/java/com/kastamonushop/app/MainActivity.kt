package com.kastamonushop.app

import android.app.Activity
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.layout.onSizeChanged
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kastamonushop.app.bridge.NativeSurfaceBridge
import com.kastamonushop.app.data.SavedPhotoItem
import com.kastamonushop.app.ui.canvas.RulerLinePreview
import com.kastamonushop.app.ui.canvas.VulkanCanvasView
import com.kastamonushop.app.viewmodel.ActiveTool
import com.kastamonushop.app.viewmodel.EditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

private val StudioDarkScheme = darkColorScheme(
    primary = Color(0xFF4F9CFF),
    secondary = Color(0xFF2ED573),
    background = Color(0xFF19191E),
    surface = Color(0xFF202128),
    surfaceVariant = Color(0xFF2C2E38),
    onBackground = Color(0xFFFFFFFF),
    onSurface = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFFD8DCE8)
)

private enum class BottomPanel {
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
    CAMERA_LIGHT_LIVE,
    GRADIENT_EDIT,
    SHAPE_EDIT,
    LIQUIFY_EDIT,
    AUTO_ENHANCE_MENU,
    CINEMA_LOOK_EDIT,
    MOTION_BLUR_EDIT,
    RADIAL_BLUR_EDIT
}

private data class BlendModeOption(
    val engineName: String,
    val labelTr: String,
    val hintTr: String
)

private data class CropRatioPreset(
    val label: String,
    val ratio: Float?
)

private data class MirrorModeOption(
    val id: String,
    val gpuId: Int,
    val label: String,
    val isVerticalAxis: Boolean
)

private val MIRROR_MODES = listOf(
    MirrorModeOption("enine_auto", 1, "Enine (Sol→Sağ)", true),
    MirrorModeOption("enine_flip", 2, "Enine (Sağ→Sol)", true),
    MirrorModeOption("boyuna_auto", 3, "Boyuna (Üst→Alt)", false),
    MirrorModeOption("boyuna_flip", 4, "Boyuna (Alt→Üst)", false)
)

private val CROP_PRESETS = listOf(
    CropRatioPreset("1:1", 1f),
    CropRatioPreset("4:5", 4f / 5f),
    CropRatioPreset("3:4", 3f / 4f),
    CropRatioPreset("16:9", 16f / 9f),
    CropRatioPreset("9:16", 9f / 16f),
    CropRatioPreset("Orijinal", null)
)

private val BEGINNER_BLEND_MODES = listOf(
    BlendModeOption("Normal", "Normal", "Standart görünüm, üstteki alttakini örter"),
    BlendModeOption("Multiply", "Çoğalt (Multiply)", "Koyu tonları korur, gölge için ideal"),
    BlendModeOption("Screen", "Ekran (Screen)", "Parlak tonları korur, ışık ve parlama için ideal"),
    BlendModeOption("Overlay", "Kaplama (Overlay)", "Kontrastı artırır, renkleri canlandırır"),
    BlendModeOption("SoftLight", "Yumuşak Işık", "Fotoğrafa doğal ve yumuşak renk tonu verir")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = StudioDarkScheme) {
                StonebridgeAppRoot()
            }
        }
    }
}

private fun hexToHsv(hex: String): FloatArray {
    val hsv = FloatArray(3)
    val c = runCatching { AndroidColor.parseColor(hex) }.getOrDefault(AndroidColor.RED)
    AndroidColor.colorToHSV(c, hsv)
    return hsv
}

private fun hsvToHex(h: Float, s: Float, v: Float): String {
    val c = AndroidColor.HSVToColor(floatArrayOf(h.coerceIn(0f, 360f), s.coerceIn(0f, 1f), v.coerceIn(0f, 1f)))
    return String.format("#%06X", 0xFFFFFF and c)
}

private fun computeCropRect(docW: Int, docH: Int, ratio: Float?, scale: Float): IntArray {
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
fun StonebridgeAppRoot(vm: EditorViewModel = viewModel()) {
    val context = LocalContext.current
    val state by vm.uiState.collectAsState()
    var isHomeScreen by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        vm.loadSavedGallery(context)
    }

    val homePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { res ->
        val uri = if (res.resultCode == Activity.RESULT_OK) res.data?.data else null
        if (uri != null) {
            vm.openDocumentFromUri(context.contentResolver, uri, asLayer = false)
            isHomeScreen = false
        }
    }

    if (isHomeScreen) {
        StonebridgeHomeScreen(
            savedPhotos = state.savedPhotos,
            onPickPhoto = {
                val pick = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "image/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                homePhotoLauncher.launch(Intent.createChooser(pick, "Fotoğraf Seç"))
            },
            onNewBlankCanvas = {
                vm.newBlankCanvas()
                isHomeScreen = false
            },
            onOpenSavedPhoto = { item ->
                vm.openSavedPhoto(item)
                isHomeScreen = false
            },
            onDeleteSavedPhoto = { item ->
                vm.deleteSavedPhoto(context, item.id)
            }
        )
    } else {
        StonebridgeStudioScreen(
            vm = vm,
            onGoHome = { isHomeScreen = true }
        )
    }
}

@Composable
private fun StonebridgeHomeScreen(
    savedPhotos: List<SavedPhotoItem>,
    onPickPhoto: () -> Unit,
    onNewBlankCanvas: () -> Unit,
    onOpenSavedPhoto: (SavedPhotoItem) -> Unit,
    onDeleteSavedPhoto: (SavedPhotoItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "StonebridgeCraft",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = "v0.9.9",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(124.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.75f), RoundedCornerShape(24.dp))
                    .clickable(onClick = onPickPhoto),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Fotoğraf Düzenle",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(46.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Fotoğraf Düzenle",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(onClick = onNewBlankCanvas) {
                Icon(
                    imageVector = Icons.Default.NoteAdd,
                    contentDescription = null,
                    tint = Color(0xFFB8BDD0),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Boş Tuval ile Başla",
                    color = Color(0xFFB8BDD0),
                    fontSize = 12.sp
                )
            }
        }

        HorizontalDivider(color = Color(0xFF323544), thickness = 1.dp)

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Kaydedilen Fotoğraflar",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (savedPhotos.isNotEmpty()) {
                Text(
                    text = "${savedPhotos.size} kayıt",
                    color = Color(0xFF9EA4B8),
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (savedPhotos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        tint = Color(0xFF4C5062),
                        modifier = Modifier.size(42.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Kaydettiğin fotoğraflar burada görünecek",
                        color = Color(0xFF8C91A6),
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(savedPhotos, key = { it.id }) { item ->
                    SavedPhotoCard(
                        item = item,
                        onClick = { onOpenSavedPhoto(item) },
                        onDelete = { onDeleteSavedPhoto(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SavedPhotoCard(
    item: SavedPhotoItem,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val thumbBitmap by produceState<ImageBitmap?>(initialValue = null, key1 = item.thumbPath) {
        value = withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(item.thumbPath)?.asImageBitmap() }.getOrNull()
        }
    }
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, Color(0xFF3A3D4D), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        val bmp = thumbBitmap
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = item.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = null,
                tint = Color(0xFF8C91A6),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(28.dp)
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(5.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(Color(0xB3111217))
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Sil",
                tint = Color.White,
                modifier = Modifier.size(13.dp)
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0xAA111217))
                .padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            Text(
                text = item.displayName,
                color = Color.White,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TopBarButton(
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
private fun CircularDockButton(
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
private fun CompactSliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    defaultValue: Float,
    onChange: (Float) -> Unit
) {
    val isModified = abs(value - defaultValue) > ((range.endInclusive - range.start) * 0.004f)
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
                .clickable(enabled = isModified) { onChange(defaultValue) },
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
private fun BrushSizeSliderRow(
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
private fun BottomEditorHeader(
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
fun StonebridgeStudioScreen(
    vm: EditorViewModel = viewModel(),
    onGoHome: () -> Unit = {}
) {
    val state by vm.uiState.collectAsState()
    val layerThumbs by vm.layerThumbnails.collectAsState()
    val activeLayerPrev by vm.activeLayerPreview.collectAsState()
    val context = LocalContext.current
    val density = LocalDensity.current

    var showLayers by remember { mutableStateOf(false) }
    var activePanel by remember { mutableStateOf(BottomPanel.DOCK) }
    var showHsvPicker by remember { mutableStateOf(false) }
    var showOpenMenu by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }
    var showBlendMenu by remember { mutableStateOf(false) }
    var pendingExportFormat by remember { mutableStateOf("png") }
    var openAsLayer by remember { mutableStateOf(false) }
    var canvasViewRef by remember { mutableStateOf<VulkanCanvasView?>(null) }

    var rulerPreview by remember { mutableStateOf<RulerLinePreview?>(null) }
    var retouchCursor by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var selectPreviewRect by remember { mutableStateOf<FloatArray?>(null) }
    var levelsBlackVal by remember { mutableFloatStateOf(0f) }
    var levelsGammaVal by remember { mutableFloatStateOf(1f) }
    var levelsWhiteVal by remember { mutableFloatStateOf(255f) }
    var cbCyanRedVal by remember { mutableFloatStateOf(0f) }
    var cbMagentaGreenVal by remember { mutableFloatStateOf(0f) }
    var cbYellowBlueVal by remember { mutableFloatStateOf(0f) }
    var curveBlackVal by remember { mutableFloatStateOf(0.0f) }
    var curveShadowVal by remember { mutableFloatStateOf(0.25f) }
    var curveMidVal by remember { mutableFloatStateOf(0.50f) }
    var curveHighVal by remember { mutableFloatStateOf(0.75f) }
    var curveWhiteVal by remember { mutableFloatStateOf(1.0f) }
    var curveOverlayExpanded by remember { mutableStateOf(false) }
    var draggingCurveNode by remember { mutableIntStateOf(-1) }

    fun evalHermiteCurve(x: Float, y0: Float, y1: Float, y2: Float, y3: Float, y4: Float): Float {
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

    fun pushCurveGpu(y0: Float, y1: Float, y2: Float, y3: Float, y4: Float) {
        val by = (y0 * 255f).roundToInt().coerceIn(0, 255)
        val sy = (y1 * 255f).roundToInt().coerceIn(0, 255)
        val my = (y2 * 255f).roundToInt().coerceIn(0, 255)
        val hy = (y3 * 255f).roundToInt().coerceIn(0, 255)
        val wy = (y4 * 255f).roundToInt().coerceIn(0, 255)
        vm.updateProxyAdjustment("curves", """{"points":[[0,$by],[64,$sy],[128,$my],[192,$hy],[255,$wy]]}""")
    }

    // 4 normalized corners (TL, TR, BR, BL) in document space (0..1) for 60 FPS Perspective Crop
    var pTl by remember { mutableStateOf(Offset(0.12f, 0.12f)) }
    var pTr by remember { mutableStateOf(Offset(0.88f, 0.12f)) }
    var pBr by remember { mutableStateOf(Offset(0.88f, 0.88f)) }
    var pBl by remember { mutableStateOf(Offset(0.12f, 0.88f)) }
    var draggingCornerIdx by remember { mutableIntStateOf(-1) }

    // Independent active layer Move / Scale / Rotate state
    var layerDx by remember { mutableFloatStateOf(0f) }
    var layerDy by remember { mutableFloatStateOf(0f) }
    var layerScalePct by remember { mutableFloatStateOf(100f) }
    var layerRotDeg by remember { mutableFloatStateOf(0f) }

    // Temel + İnce Ayar & Live Brush Size Preview state
    var showBrushFineTune by remember { mutableStateOf(false) }
    var brushSizePreviewTick by remember { mutableIntStateOf(0) }
    var showBrushSizeCircle by remember { mutableStateOf(false) }

    // Layer Effects (fx) state (Temel + İnce Ayar)
    var fxKind by remember { mutableStateOf("dropShadow") }
    var fxColorHex by remember { mutableStateOf("#000000") }
    var fxSizeVal by remember { mutableFloatStateOf(16f) }
    var fxOpacityVal by remember { mutableFloatStateOf(75f) }
    var fxDistanceVal by remember { mutableFloatStateOf(12f) }
    var showFxFineTune by remember { mutableStateOf(false) }

    LaunchedEffect(brushSizePreviewTick) {
        if (brushSizePreviewTick > 0) {
            showBrushSizeCircle = true
            kotlinx.coroutines.delay(550L)
            showBrushSizeCircle = false
        }
    }
    var showExitSaveDialog by remember { mutableStateOf(false) }

    // Live draggable Text layer editor state
    val dockScrollState = rememberScrollState()
    val drawScrollState = rememberScrollState()
    val selectScrollState = rememberScrollState()
    val adjustScrollState = rememberScrollState()
    val cropScrollState = rememberScrollState()
    val effectScrollState = rememberScrollState()
    var textBoxSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    // v0.9.5 Camera Light (PhotoCraft native filter.cameraRaw) state
    var camExposureVal by remember { mutableFloatStateOf(0f) }
    var camShadowsVal by remember { mutableFloatStateOf(0f) }
    var camHighlightsVal by remember { mutableFloatStateOf(0f) }
    var camVibranceVal by remember { mutableFloatStateOf(0f) }
    var camTempVal by remember { mutableFloatStateOf(0f) }
    var camTintVal by remember { mutableFloatStateOf(0f) }
    var camClarityVal by remember { mutableFloatStateOf(0f) }
    var camDehazeVal by remember { mutableFloatStateOf(0f) }
    var showCamFineTune by remember { mutableStateOf(false) }

    // v0.9.5 Live Gradient Fill (gradient.fill.create) state
    var gradFromNormX by remember { mutableFloatStateOf(0.20f) }
    var gradFromNormY by remember { mutableFloatStateOf(0.20f) }
    var gradToNormX by remember { mutableFloatStateOf(0.80f) }
    var gradToNormY by remember { mutableFloatStateOf(0.80f) }
    var gradStyle by remember { mutableStateOf("linear") }
    var gradStartHex by remember { mutableStateOf("#4F9CFF") }
    var gradEndHex by remember { mutableStateOf("#FF4D4F") }
    var gradOpacity by remember { mutableFloatStateOf(85f) }

    // v0.9.5 Native Vector Shape (shape.create) state
    var shapeKind by remember { mutableStateOf("roundedRect") }
    var shapeX0Norm by remember { mutableFloatStateOf(0.25f) }
    var shapeY0Norm by remember { mutableFloatStateOf(0.25f) }
    var shapeX1Norm by remember { mutableFloatStateOf(0.75f) }
    var shapeY1Norm by remember { mutableFloatStateOf(0.75f) }
    var shapeFillHex by remember { mutableStateOf("#4F9CFF") }
    var shapeStrokeHex by remember { mutableStateOf("#FFFFFF") }
    var shapeStrokeWidth by remember { mutableFloatStateOf(6f) }
    var shapeCornerRadius by remember { mutableFloatStateOf(24f) }
    var shapeSides by remember { mutableIntStateOf(6) }
    var showShapeFineTune by remember { mutableStateOf(false) }
    // v0.9.8 60 FPS Liquify (photocraft_algo::liquify) state
    var liquifyTool by remember { mutableStateOf("forwardWarp") }
    var liquifySize by remember { mutableFloatStateOf(120f) }
    var liquifyPressure by remember { mutableFloatStateOf(75f) }
    var liquifyCursorScreen by remember { mutableStateOf<Offset?>(null) }
    // v0.9.9 Cinematic Looks + Vignette/Grain & Motion/Radial Blur state
    var cinemaPreset by remember { mutableStateOf("golden") }
    var cinemaIntensity by remember { mutableFloatStateOf(80f) }
    var cinemaVignette by remember { mutableFloatStateOf(-25f) }
    var cinemaGrain by remember { mutableFloatStateOf(12f) }
    var motionBlurAngle by remember { mutableFloatStateOf(0f) }
    var motionBlurDistance by remember { mutableFloatStateOf(24f) }
    var radialBlurAmount by remember { mutableFloatStateOf(14f) }
    var radialBlurMethod by remember { mutableStateOf("zoom") }
    var textDraft by remember { mutableStateOf("StonebridgeCraft") }
    var textNormX by remember { mutableFloatStateOf(0.50f) }
    var textNormY by remember { mutableFloatStateOf(0.50f) }
    var textSizePx by remember { mutableFloatStateOf(140f) }
    var textColorHex by remember { mutableStateOf("#FFFFFF") }
    var textAlign by remember { mutableStateOf("center") }
    var cameraTick by remember { mutableIntStateOf(0) }

    var brightnessVal by remember { mutableFloatStateOf(0f) }
    var contrastVal by remember { mutableFloatStateOf(0f) }
    var hueVal by remember { mutableFloatStateOf(0f) }
    var saturationVal by remember { mutableFloatStateOf(0f) }
    var blurRadiusVal by remember { mutableFloatStateOf(0f) }
    var sharpenAmountVal by remember { mutableFloatStateOf(0f) }

    // 60 FPS GPU Mirror Effect state (zero CPU lag!)
    var selectedMirrorMode by remember { mutableStateOf(MIRROR_MODES.first()) }
    var mirrorAxisRatio by remember { mutableFloatStateOf(0.50f) }

    // 60 FPS GPU Rotate/Flip preview state inside Kırp & Çevir
    var pendingQuarterTurns by remember { mutableIntStateOf(0) }
    var pendingFlipH by remember { mutableStateOf(false) }
    var pendingFlipV by remember { mutableStateOf(false) }

    // Crop overlay state
    var selectedCropPreset by remember { mutableStateOf(CROP_PRESETS.first()) }
    var cropScale by remember { mutableFloatStateOf(0.90f) }

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
        val dpVal = when (panel) {
            BottomPanel.DOCK -> 112f
            BottomPanel.CURVES_LIVE -> 340f
            BottomPanel.LIGHT_LIVE, BottomPanel.COLOR_LIVE, BottomPanel.LEVELS_LIVE, BottomPanel.COLOR_BAL_LIVE -> 250f
            BottomPanel.BLUR_EDIT, BottomPanel.SHARPEN_EDIT, BottomPanel.MIRROR_EDIT, BottomPanel.LAYER_TRANSFORM, BottomPanel.LAYER_FX_EDIT -> 250f
            BottomPanel.PERSPECTIVE_CROP_EDIT, BottomPanel.CROP_RATIO_EDIT, BottomPanel.TEXT_EDIT -> 220f
            else -> 190f
        }
        return with(density) { dpVal.dp.toPx() }
    }

    fun fitToSafeArea(turns: Int = pendingQuarterTurns, panel: BottomPanel = activePanel) {
        val topPx = with(density) { 96.dp.toPx() }
        val botPx = bottomInsetPxForPanel(panel)
        canvasViewRef?.fitToDocument(turns, topPx, botPx)
    }

    fun clearGpuPreview() {
        pendingQuarterTurns = 0
        pendingFlipH = false
        pendingFlipV = false
        NativeSurfaceBridge.nativeSetPreview(0, 0.5f, false, false)
        NativeSurfaceBridge.nativeSetAdjustPreview(0f, 0f, 0f, 0f, 0f, 0f)
        NativeSurfaceBridge.nativeSetTonePreview(0f, 1f, 1f, 0f, 0f, 0f)
        curveOverlayExpanded = false
        NativeSurfaceBridge.nativeSetCurvePreview(0.0f, 0.25f, 0.50f, 0.75f, 1.0f, false)
        vm.endProxyPreview()
        fitToSafeArea(0, BottomPanel.DOCK)
    }

    fun clearAdjustGpuPreviewOnly() {
        NativeSurfaceBridge.nativeSetAdjustPreview(0f, 0f, 0f, 0f, 0f, 0f)
        NativeSurfaceBridge.nativeSetTonePreview(0f, 1f, 1f, 0f, 0f, 0f)
        curveOverlayExpanded = false
        NativeSurfaceBridge.nativeSetCurvePreview(0.0f, 0.25f, 0.50f, 0.75f, 1.0f, false)
        vm.endProxyPreview()
    }

    fun pushGpuTransformPreview(turns: Int, flipH: Boolean, flipV: Boolean) {
        NativeSurfaceBridge.nativeSetPreview(0, 0.5f, flipH, flipV)
        canvasViewRef?.fitToDocument(turns)
    }

    fun dismissLivePanelIfActive() {
        when (activePanel) {
            BottomPanel.LIGHT_LIVE, BottomPanel.COLOR_LIVE, BottomPanel.LEVELS_LIVE, BottomPanel.COLOR_BAL_LIVE, BottomPanel.CURVES_LIVE -> {
                clearAdjustGpuPreviewOnly()
                activePanel = BottomPanel.DOCK
            }
            BottomPanel.BLUR_EDIT, BottomPanel.SHARPEN_EDIT -> {
                clearAdjustGpuPreviewOnly()
                activePanel = BottomPanel.DOCK
            }
            BottomPanel.MIRROR_EDIT -> {
                clearGpuPreview()
                activePanel = BottomPanel.DOCK
            }
            BottomPanel.CROP_MENU -> {
                if (pendingQuarterTurns != 0 || pendingFlipH || pendingFlipV) {
                    clearGpuPreview()
                }
                activePanel = BottomPanel.DOCK
            }
            else -> Unit
        }
    }

    BackHandler(enabled = true) {
        if (showLayers) {
            showLayers = false
        } else if (activePanel != BottomPanel.DOCK) {
            when (activePanel) {
                BottomPanel.LIGHT_LIVE, BottomPanel.COLOR_LIVE, BottomPanel.LEVELS_LIVE, BottomPanel.COLOR_BAL_LIVE, BottomPanel.CURVES_LIVE -> {
                    clearAdjustGpuPreviewOnly()
                    activePanel = BottomPanel.ADJUST_MENU
                }
                BottomPanel.BLUR_EDIT, BottomPanel.SHARPEN_EDIT -> {
                    clearAdjustGpuPreviewOnly()
                    activePanel = BottomPanel.FILTER_MENU
                }
                BottomPanel.MIRROR_EDIT -> {
                    clearGpuPreview()
                    activePanel = BottomPanel.EFFECT_MENU
                }
                BottomPanel.CROP_MENU -> {
                    if (pendingQuarterTurns != 0 || pendingFlipH || pendingFlipV) {
                        clearGpuPreview()
                    }
                    activePanel = BottomPanel.DOCK
                }
                BottomPanel.CROP_RATIO_EDIT, BottomPanel.PERSPECTIVE_CROP_EDIT -> activePanel = BottomPanel.CROP_MENU
                BottomPanel.LAYER_TRANSFORM -> { vm.clearActiveLayerPreview(); activePanel = BottomPanel.DOCK }
                BottomPanel.LAYER_FX_EDIT, BottomPanel.GRADIENT_EDIT, BottomPanel.SHAPE_EDIT -> { vm.endProxyPreview(); activePanel = BottomPanel.DOCK }
                BottomPanel.CAMERA_LIGHT_LIVE -> CameraLightPanel(
                    exposure = camExposureVal, onExposure = { camExposureVal = it },
                    shadows = camShadowsVal, onShadows = { camShadowsVal = it },
                    highlights = camHighlightsVal, onHighlights = { camHighlightsVal = it },
                    vibrance = camVibranceVal, onVibrance = { camVibranceVal = it },
                    temp = camTempVal, onTemp = { camTempVal = it },
                    tint = camTintVal, onTint = { camTintVal = it },
                    clarity = camClarityVal, onClarity = { camClarityVal = it },
                    dehaze = camDehazeVal, onDehaze = { camDehazeVal = it },
                    showFine = showCamFineTune, onToggleFine = { showCamFineTune = !showCamFineTune },
                    onPreview = { vm.updateProxyCameraLight(camExposureVal, camShadowsVal, camHighlightsVal, camVibranceVal, camTempVal, camTintVal, camClarityVal, camDehazeVal) },
                    onClose = { clearAdjustGpuPreviewOnly(); activePanel = BottomPanel.ADJUST_MENU },
                    onApply = {
                        val isNeutral = abs(camExposureVal) < 0.02f &&
                            camShadowsVal.roundToInt() == 0 && camHighlightsVal.roundToInt() == 0 &&
                            camVibranceVal.roundToInt() == 0 && camTempVal.roundToInt() == 0 &&
                            camTintVal.roundToInt() == 0 && camClarityVal.roundToInt() == 0 &&
                            camDehazeVal.roundToInt() == 0
                        if (isNeutral) clearAdjustGpuPreviewOnly()
                        else vm.applyCameraLight(camExposureVal, camShadowsVal, camHighlightsVal, camVibranceVal, camTempVal, camTintVal, camClarityVal, camDehazeVal)
                        activePanel = BottomPanel.DOCK
                    }
                )
                BottomPanel.GRADIENT_EDIT -> {
                    val docW = (state.docInfo?.width?.toInt() ?: 2048).toFloat()
                    val docH = (state.docInfo?.height?.toInt() ?: 2048).toFloat()
                    GradientEditPanel(
                        style = gradStyle, onStyle = { gradStyle = it },
                        startHex = gradStartHex, onStartHex = { gradStartHex = it },
                        endHex = gradEndHex, onEndHex = { gradEndHex = it },
                        opacity = gradOpacity, onOpacity = { gradOpacity = it },
                        onPreview = {
                            vm.updateProxyGradient(
                                docW * gradFromNormX, docH * gradFromNormY,
                                docW * gradToNormX, docH * gradToNormY,
                                gradStyle, gradStartHex, gradEndHex, gradOpacity
                            )
                        },
                        onClose = { vm.endProxyPreview(); activePanel = BottomPanel.DOCK },
                        onApply = {
                            vm.applyGradientFill(
                                docW * gradFromNormX, docH * gradFromNormY,
                                docW * gradToNormX, docH * gradToNormY,
                                gradStyle, gradStartHex, gradEndHex, gradOpacity
                            )
                            activePanel = BottomPanel.DOCK
                        }
                    )
                }
                BottomPanel.SHAPE_EDIT -> {
                    val docW = (state.docInfo?.width?.toInt() ?: 2048).toFloat()
                    val docH = (state.docInfo?.height?.toInt() ?: 2048).toFloat()
                    ShapeEditPanel(
                        kind = shapeKind, onKind = { shapeKind = it },
                        fillHex = shapeFillHex, onFillHex = { shapeFillHex = it },
                        strokeWidth = shapeStrokeWidth, onStrokeWidth = { shapeStrokeWidth = it },
                        cornerRadius = shapeCornerRadius, onCornerRadius = { shapeCornerRadius = it },
                        sides = shapeSides, onSides = { shapeSides = it },
                        showFine = showShapeFineTune, onToggleFine = { showShapeFineTune = !showShapeFineTune },
                        onPreview = {
                            vm.updateProxyShape(
                                shapeKind,
                                docW * shapeX0Norm, docH * shapeY0Norm,
                                docW * shapeX1Norm, docH * shapeY1Norm,
                                shapeFillHex, shapeStrokeHex, shapeStrokeWidth, shapeCornerRadius, shapeSides
                            )
                        },
                        onClose = { vm.endProxyPreview(); activePanel = BottomPanel.DOCK },
                        onApply = {
                            vm.createShapeLayer(
                                shapeKind,
                                docW * shapeX0Norm, docH * shapeY0Norm,
                                docW * shapeX1Norm, docH * shapeY1Norm,
                                shapeFillHex, shapeStrokeHex, shapeStrokeWidth, shapeCornerRadius, shapeSides
                            )
                            activePanel = BottomPanel.DOCK
                        }
                    )
                }
                BottomPanel.LIGHT_LIVE -> {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BottomEditorHeader(
                            title = "Işık & Kontrast",
                            subtitle = "Kaydırdıkça anlık gör, beğenirsen Uygula de",
                            applyLabel = "Uygula",
                            onClose = {
                                clearAdjustGpuPreviewOnly()
                                activePanel = BottomPanel.ADJUST_MENU
                            },
                            onApply = {
                                if (brightnessVal.roundToInt() == 0 && contrastVal.roundToInt() == 0) {
                                    clearAdjustGpuPreviewOnly()
                                } else {
                                    vm.applyAdjustmentOnce(
                                        "brightnessContrast",
                                        """{"brightness":${brightnessVal.roundToInt()},"contrast":${contrastVal.roundToInt()}}""",
                                        "Işık & Kontrast"
                                    )
                                }
                                activePanel = BottomPanel.DOCK
                            }
                        )
                        CompactSliderRow("Parlaklık", "${brightnessVal.roundToInt()}", brightnessVal, -100f..100f, defaultValue = 0f) {
                            brightnessVal = it
                            vm.updateProxyAdjustment("brightnessContrast", """{"brightness":${brightnessVal.roundToInt()},"contrast":${contrastVal.roundToInt()}}""")
                        }
                        CompactSliderRow("Kontrast", "${contrastVal.roundToInt()}", contrastVal, -50f..100f, defaultValue = 0f) {
                            contrastVal = it
                            vm.updateProxyAdjustment("brightnessContrast", """{"brightness":${brightnessVal.roundToInt()},"contrast":${contrastVal.roundToInt()}}""")
                        }
                    }
                }

                BottomPanel.COLOR_LIVE -> {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BottomEditorHeader(
                            title = "Renk & Canlılık",
                            subtitle = "Kaydırdıkça anlık gör, beğenirsen Uygula de",
                            applyLabel = "Uygula",
                            onClose = {
                                clearAdjustGpuPreviewOnly()
                                activePanel = BottomPanel.ADJUST_MENU
                            },
                            onApply = {
                                if (saturationVal.roundToInt() == 0 && hueVal.roundToInt() == 0) {
                                    clearAdjustGpuPreviewOnly()
                                } else {
                                    vm.applyAdjustmentOnce(
                                        "hueSaturation",
                                        """{"hue":${hueVal.roundToInt()},"saturation":${saturationVal.roundToInt()},"lightness":0}""",
                                        "Renk & Canlılık"
                                    )
                                }
                                activePanel = BottomPanel.DOCK
                            }
                        )
                        CompactSliderRow("Canlılık", "${saturationVal.roundToInt()}", saturationVal, -100f..100f, defaultValue = 0f) {
                            saturationVal = it
                            vm.updateProxyAdjustment("hueSaturation", """{"hue":${hueVal.roundToInt()},"saturation":${saturationVal.roundToInt()},"lightness":0}""")
                        }
                        CompactSliderRow("Renk Tonu", "${hueVal.roundToInt()}°", hueVal, -180f..180f, defaultValue = 0f) {
                            hueVal = it
                            vm.updateProxyAdjustment("hueSaturation", """{"hue":${hueVal.roundToInt()},"saturation":${saturationVal.roundToInt()},"lightness":0}""")
                        }
                    }
                }

                BottomPanel.LEVELS_LIVE -> {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BottomEditorHeader(
                            title = "Düzeyler (Levels)",
                            subtitle = "60 FPS canlı önizleme — Uygula ile katman oluştur",
                            applyLabel = "Uygula",
                            onClose = {
                                clearAdjustGpuPreviewOnly()
                                activePanel = BottomPanel.ADJUST_MENU
                            },
                            onApply = {
                                val ib = levelsBlackVal.roundToInt().coerceIn(0, 240)
                                val iw = levelsWhiteVal.roundToInt().coerceIn(ib + 10, 255)
                                if (ib == 0 && iw == 255 && kotlin.math.abs(levelsGammaVal - 1f) < 0.02f) {
                                    clearAdjustGpuPreviewOnly()
                                } else {
                                    val gStr = String.format(java.util.Locale.US, "%.2f", levelsGammaVal)
                                    vm.applyAdjustmentOnce(
                                        "levels",
                                        """{"inBlack":$ib,"gamma":$gStr,"inWhite":$iw}""",
                                        "Düzeyler"
                                    )
                                }
                                activePanel = BottomPanel.DOCK
                            }
                        )
                        CompactSliderRow("Siyahlar", "${levelsBlackVal.roundToInt()}", levelsBlackVal, 0f..180f, defaultValue = 0f) {
                            levelsBlackVal = it.coerceAtMost(levelsWhiteVal - 15f)
                            val gStr = String.format(java.util.Locale.US, "%.2f", levelsGammaVal); vm.updateProxyAdjustment("levels", """{"inBlack":${levelsBlackVal.roundToInt()},"gamma":$gStr,"inWhite":${levelsWhiteVal.roundToInt()}}""")
                        }
                        CompactSliderRow("Orta Ton", String.format(java.util.Locale.US, "%.2f", levelsGammaVal), levelsGammaVal, 0.25f..2.5f, defaultValue = 1f) {
                            levelsGammaVal = it
                            val gStr = String.format(java.util.Locale.US, "%.2f", levelsGammaVal); vm.updateProxyAdjustment("levels", """{"inBlack":${levelsBlackVal.roundToInt()},"gamma":$gStr,"inWhite":${levelsWhiteVal.roundToInt()}}""")
                        }
                        CompactSliderRow("Beyazlar", "${levelsWhiteVal.roundToInt()}", levelsWhiteVal, 75f..255f, defaultValue = 255f) {
                            levelsWhiteVal = it.coerceAtLeast(levelsBlackVal + 15f)
                            val gStr = String.format(java.util.Locale.US, "%.2f", levelsGammaVal); vm.updateProxyAdjustment("levels", """{"inBlack":${levelsBlackVal.roundToInt()},"gamma":$gStr,"inWhite":${levelsWhiteVal.roundToInt()}}""")
                        }
                    }
                }

                BottomPanel.COLOR_BAL_LIVE -> {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BottomEditorHeader(
                            title = "Renk Dengesi",
                            subtitle = "60 FPS canlı önizleme — Uygula ile katman oluştur",
                            applyLabel = "Uygula",
                            onClose = {
                                clearAdjustGpuPreviewOnly()
                                activePanel = BottomPanel.ADJUST_MENU
                            },
                            onApply = {
                                val cr = cbCyanRedVal.roundToInt()
                                val mg = cbMagentaGreenVal.roundToInt()
                                val yb = cbYellowBlueVal.roundToInt()
                                if (cr == 0 && mg == 0 && yb == 0) {
                                    clearAdjustGpuPreviewOnly()
                                } else {
                                    vm.applyAdjustmentOnce(
                                        "colorBalance",
                                        """{"midtones":[$cr,$mg,$yb],"preserveLuminosity":true}""",
                                        "Renk Dengesi"
                                    )
                                }
                                activePanel = BottomPanel.DOCK
                            }
                        )
                        CompactSliderRow("Camgöbeği\nKırmızı", "${cbCyanRedVal.roundToInt()}", cbCyanRedVal, -100f..100f, defaultValue = 0f) {
                            cbCyanRedVal = it
                            vm.updateProxyAdjustment("colorBalance", """{"midtones":[${cbCyanRedVal.roundToInt()},${cbMagentaGreenVal.roundToInt()},${cbYellowBlueVal.roundToInt()}],"preserveLuminosity":true}""")
                        }
                        CompactSliderRow("Macenta\nYeşil", "${cbMagentaGreenVal.roundToInt()}", cbMagentaGreenVal, -100f..100f, defaultValue = 0f) {
                            cbMagentaGreenVal = it
                            vm.updateProxyAdjustment("colorBalance", """{"midtones":[${cbCyanRedVal.roundToInt()},${cbMagentaGreenVal.roundToInt()},${cbYellowBlueVal.roundToInt()}],"preserveLuminosity":true}""")
                        }
                        CompactSliderRow("Sarı / Mavi", "${cbYellowBlueVal.roundToInt()}", cbYellowBlueVal, -100f..100f, defaultValue = 0f) {
                            cbYellowBlueVal = it
                            vm.updateProxyAdjustment("colorBalance", """{"midtones":[${cbCyanRedVal.roundToInt()},${cbMagentaGreenVal.roundToInt()},${cbYellowBlueVal.roundToInt()}],"preserveLuminosity":true}""")
                        }
                    }
                }

                BottomPanel.CURVES_LIVE -> {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BottomEditorHeader(
                            title = "Eğriler (Curves)",
                            subtitle = if (curveOverlayExpanded) "Fotoğraf üzerindeki 5 noktayı parmağınla sürükle" else "Elle kontrol için sol kutucuğa dokun",
                            applyLabel = "Uygula",
                            onClose = {
                                curveOverlayExpanded = false
                                clearAdjustGpuPreviewOnly()
                                activePanel = BottomPanel.ADJUST_MENU
                            },
                            onApply = {
                                curveOverlayExpanded = false
                                val isDefault = abs(curveBlackVal - 0.0f) < 0.01f &&
                                    abs(curveShadowVal - 0.25f) < 0.01f &&
                                    abs(curveMidVal - 0.50f) < 0.01f &&
                                    abs(curveHighVal - 0.75f) < 0.01f &&
                                    abs(curveWhiteVal - 1.0f) < 0.01f
                                if (isDefault) {
                                    clearAdjustGpuPreviewOnly()
                                } else {
                                    val by = (curveBlackVal * 255f).roundToInt().coerceIn(0, 255)
                                    val sy = (curveShadowVal * 255f).roundToInt().coerceIn(0, 255)
                                    val my = (curveMidVal * 255f).roundToInt().coerceIn(0, 255)
                                    val hy = (curveHighVal * 255f).roundToInt().coerceIn(0, 255)
                                    val wy = (curveWhiteVal * 255f).roundToInt().coerceIn(0, 255)
                                    vm.applyAdjustmentOnce(
                                        "curves",
                                        """{"points":[[0,$by],[64,$sy],[128,$my],[192,$hy],[255,$wy]]}""",
                                        "Eğriler"
                                    )
                                }
                                activePanel = BottomPanel.DOCK
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
                                    .background(if (curveOverlayExpanded) Color(0xFF1D2B45) else Color(0xFF141720))
                                    .border(
                                        width = if (curveOverlayExpanded) 2.dp else 1.dp,
                                        color = if (curveOverlayExpanded) MaterialTheme.colorScheme.primary else Color(0xFF454D66),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        curveOverlayExpanded = !curveOverlayExpanded
                                        fitToSafeArea(0, BottomPanel.CURVES_LIVE)
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
                                        val y = evalHermiteCurve(x, curveBlackVal, curveShadowVal, curveMidVal, curveHighVal, curveWhiteVal)
                                        val sx = x * w
                                        val sy = h * (1f - y)
                                        if (i == 0) path.moveTo(sx, sy) else path.lineTo(sx, sy)
                                    }
                                    drawPath(path, Color(0xFF4F9CFF), style = Stroke(width = 2.5f))
                                    val ys = floatArrayOf(curveBlackVal, curveShadowVal, curveMidVal, curveHighVal, curveWhiteVal)
                                    val xs = floatArrayOf(0f, 0.25f, 0.50f, 0.75f, 1.0f)
                                    for (i in 0..4) {
                                        drawCircle(Color.White, radius = 3f, center = Offset(w * xs[i], h * (1f - ys[i])))
                                    }
                                }
                                Icon(
                                    imageVector = if (curveOverlayExpanded) Icons.Default.CloseFullscreen else Icons.Default.OpenInFull,
                                    contentDescription = "Manuel Eğri",
                                    tint = if (curveOverlayExpanded) MaterialTheme.colorScheme.primary else Color(0xCCFFFFFF),
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
                                            curveBlackVal = 0.0f; curveShadowVal = 0.17f; curveMidVal = 0.50f; curveHighVal = 0.84f; curveWhiteVal = 1.0f
                                            pushCurveGpu(curveBlackVal, curveShadowVal, curveMidVal, curveHighVal, curveWhiteVal)
                                        },
                                        label = { Text("S-Kontrast", fontSize = 10.sp) }
                                    )
                                    FilterChip(
                                        selected = false,
                                        onClick = {
                                            curveBlackVal = 0.08f; curveShadowVal = 0.34f; curveMidVal = 0.52f; curveHighVal = 0.72f; curveWhiteVal = 0.94f
                                            pushCurveGpu(curveBlackVal, curveShadowVal, curveMidVal, curveHighVal, curveWhiteVal)
                                        },
                                        label = { Text("Mat Film", fontSize = 10.sp) }
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilterChip(
                                        selected = false,
                                        onClick = {
                                            curveBlackVal = 0.0f; curveShadowVal = 0.33f; curveMidVal = 0.62f; curveHighVal = 0.85f; curveWhiteVal = 1.0f
                                            pushCurveGpu(curveBlackVal, curveShadowVal, curveMidVal, curveHighVal, curveWhiteVal)
                                        },
                                        label = { Text("Aydınlık", fontSize = 10.sp) }
                                    )
                                    FilterChip(
                                        selected = false,
                                        onClick = {
                                            curveBlackVal = 0.0f; curveShadowVal = 0.25f; curveMidVal = 0.50f; curveHighVal = 0.75f; curveWhiteVal = 1.0f
                                            pushCurveGpu(curveBlackVal, curveShadowVal, curveMidVal, curveHighVal, curveWhiteVal)
                                        },
                                        label = { Text("Sıfırla", fontSize = 10.sp) }
                                    )
                                }
                            }
                        }
                        CompactSliderRow("Gölgeler", "${(curveShadowVal * 255f).roundToInt()}", curveShadowVal, 0.0f..1.0f, defaultValue = 0.25f) {
                            curveShadowVal = it
                            pushCurveGpu(curveBlackVal, curveShadowVal, curveMidVal, curveHighVal, curveWhiteVal)
                        }
                        CompactSliderRow("Orta Ton", "${(curveMidVal * 255f).roundToInt()}", curveMidVal, 0.0f..1.0f, defaultValue = 0.50f) {
                            curveMidVal = it
                            pushCurveGpu(curveBlackVal, curveShadowVal, curveMidVal, curveHighVal, curveWhiteVal)
                        }
                        CompactSliderRow("Parlaklar", "${(curveHighVal * 255f).roundToInt()}", curveHighVal, 0.0f..1.0f, defaultValue = 0.75f) {
                            curveHighVal = it
                            pushCurveGpu(curveBlackVal, curveShadowVal, curveMidVal, curveHighVal, curveWhiteVal)
                        }
                    }
                }

                // Category 3: Kırp & Çevir — 0 ms Instant GPU Preview for 90° Rotate & Flip!
                BottomPanel.CROP_MENU -> {
                    val hasPendingTransform = pendingQuarterTurns != 0 || pendingFlipH || pendingFlipV
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BottomEditorHeader(
                            title = "Kırp & Çevir",
                            subtitle = if (hasPendingTransform) "Anlık önizleme · kaydetmek için Uygula de" else "Fotoğrafı kırp, anlık döndür veya çevir",
                            applyLabel = if (hasPendingTransform) "Uygula" else null,
                            onClose = {
                                if (hasPendingTransform) clearGpuPreview()
                                activePanel = BottomPanel.DOCK
                            },
                            onApply = if (hasPendingTransform) {
                                {
                                    val t = pendingQuarterTurns
                                    val fh = pendingFlipH
                                    val fv = pendingFlipV
                                    pendingQuarterTurns = 0
                                    pendingFlipH = false
                                    pendingFlipV = false
                                    vm.applyTransformBatch(t, fh, fv)
                                    activePanel = BottomPanel.DOCK
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
                                    if (hasPendingTransform) clearGpuPreview()
                                    canvasViewRef?.fitToDocument(0)
                                    cropScale = 0.90f
                                    activePanel = BottomPanel.CROP_RATIO_EDIT
                                }
                            )
                            CircularDockButton(
                                icon = Icons.Default.Transform,
                                label = "Perspektif",
                                onClick = {
                                    if (hasPendingTransform) clearGpuPreview()
                                    canvasViewRef?.fitToDocument(0)
                                    pTl = Offset(0.12f, 0.12f)
                                    pTr = Offset(0.88f, 0.12f)
                                    pBr = Offset(0.88f, 0.88f)
                                    pBl = Offset(0.12f, 0.88f)
                                    activePanel = BottomPanel.PERSPECTIVE_CROP_EDIT
                                }
                            )
                            CircularDockButton(
                                icon = Icons.AutoMirrored.Filled.RotateLeft,
                                label = "90° Sol",
                                onClick = {
                                    pendingQuarterTurns = (pendingQuarterTurns + 3) % 4
                                    pushGpuTransformPreview(pendingQuarterTurns, pendingFlipH, pendingFlipV)
                                }
                            )
                            CircularDockButton(
                                icon = Icons.AutoMirrored.Filled.RotateRight,
                                label = "90° Sağ",
                                onClick = {
                                    pendingQuarterTurns = (pendingQuarterTurns + 1) % 4
                                    pushGpuTransformPreview(pendingQuarterTurns, pendingFlipH, pendingFlipV)
                                }
                            )
                            CircularDockButton(
                                icon = Icons.Default.Flip,
                                label = "Yatay",
                                selected = pendingFlipH,
                                onClick = {
                                    pendingFlipH = !pendingFlipH
                                    pushGpuTransformPreview(pendingQuarterTurns, pendingFlipH, pendingFlipV)
                                }
                            )
                            CircularDockButton(
                                icon = Icons.Default.SwapVert,
                                label = "Dikey",
                                selected = pendingFlipV,
                                onClick = {
                                    pendingFlipV = !pendingFlipV
                                    pushGpuTransformPreview(pendingQuarterTurns, pendingFlipH, pendingFlipV)
                                }
                            )
                        }
                    }
                }

                BottomPanel.CROP_RATIO_EDIT -> {
                    val docW = state.docInfo?.width?.toInt() ?: 2048
                    val docH = state.docInfo?.height?.toInt() ?: 2048
                    val rect = computeCropRect(docW, docH, selectedCropPreset.ratio, cropScale)
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BottomEditorHeader(
                            title = "Kırp (${rect[2]}×${rect[3]})",
                            subtitle = "Oran seçip çerçeveyi ayarla, sonra Uygula de",
                            applyLabel = "Uygula",
                            onClose = { activePanel = BottomPanel.CROP_MENU },
                            onApply = {
                                vm.cropCanvas(rect[0], rect[1], rect[2], rect[3])
                                activePanel = BottomPanel.DOCK
                            }
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(effectScrollState),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CROP_PRESETS.forEach { preset ->
                                FilterChip(
                                    selected = preset == selectedCropPreset,
                                    onClick = { selectedCropPreset = preset },
                                    label = { Text(preset.label, fontSize = 11.sp) }
                                )
                            }
                        }
                        CompactSliderRow("Alan", "%${(cropScale * 100).roundToInt()}", cropScale, 0.40f..1.0f, defaultValue = 1.0f) {
                            cropScale = it
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
                            onClose = { activePanel = BottomPanel.CROP_MENU },
                            onApply = {
                                vm.perspectiveCrop(
                                    pTl.x * docW, pTl.y * docH,
                                    pTr.x * docW, pTr.y * docH,
                                    pBr.x * docW, pBr.y * docH,
                                    pBl.x * docW, pBl.y * docH
                                )
                                activePanel = BottomPanel.DOCK
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
                                pTl = Offset(0.12f, 0.12f)
                                pTr = Offset(0.88f, 0.12f)
                                pBr = Offset(0.88f, 0.88f)
                                pBl = Offset(0.12f, 0.88f)
                            }) {
                                Text("Sıfırla", fontSize = 11.sp)
                            }
                        }
                    }
                }

                BottomPanel.FILTER_MENU -> {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BottomEditorHeader(
                            title = "Filtreler",
                            subtitle = "Kaydırdıkça anlık önizleme ile uygulanır",
                            onClose = { activePanel = BottomPanel.DOCK }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            CircularDockButton(
                                icon = Icons.Default.BlurOn,
                                label = "Bulanık",
                                onClick = {
                                    blurRadiusVal = 0f
                                    vm.beginProxyPreview()
                                    activePanel = BottomPanel.BLUR_EDIT
                                }
                            )
                            CircularDockButton(
                                icon = Icons.Default.Details,
                                label = "Keskin",
                                onClick = {
                                    sharpenAmountVal = 0f
                                    vm.beginProxyPreview()
                                    activePanel = BottomPanel.SHARPEN_EDIT
                                }
                            )
                            CircularDockButton(
                                icon = Icons.Default.FastForward,
                                label = "Hareket",
                                onClick = {
                                    motionBlurAngle = 0f
                                    motionBlurDistance = 24f
                                    vm.beginProxyPreview()
                                    vm.updateProxyMotionBlur(motionBlurAngle, motionBlurDistance)
                                    activePanel = BottomPanel.MOTION_BLUR_EDIT
                                }
                            )
                            CircularDockButton(
                                icon = Icons.Default.BlurCircular,
                                label = "Radyal",
                                onClick = {
                                    radialBlurAmount = 14f
                                    radialBlurMethod = "zoom"
                                    vm.beginProxyPreview()
                                    vm.updateProxyRadialBlur(radialBlurAmount, radialBlurMethod)
                                    activePanel = BottomPanel.RADIAL_BLUR_EDIT
                                }
                            )
                        }
                    }
                }

                BottomPanel.BLUR_EDIT -> {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BottomEditorHeader(
                            title = "Bulanıklaştır",
                            subtitle = "Kaydırdıkça anlık gör, beğenirsen Uygula de",
                            applyLabel = "Uygula",
                            onClose = {
                                clearAdjustGpuPreviewOnly()
                                activePanel = BottomPanel.FILTER_MENU
                            },
                            onApply = {
                                vm.applyBlurFast(blurRadiusVal)
                                activePanel = BottomPanel.DOCK
                            }
                        )
                        CompactSliderRow("Yarıçap", "${blurRadiusVal.roundToInt()}px", blurRadiusVal, 0f..60f, defaultValue = 0f) {
                            blurRadiusVal = it
                            vm.updateProxyBlur(blurRadiusVal)
                        }
                    }
                }

                BottomPanel.SHARPEN_EDIT -> {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BottomEditorHeader(
                            title = "Keskinleştir",
                            subtitle = "Kaydırdıkça anlık gör, beğenirsen Uygula de",
                            applyLabel = "Uygula",
                            onClose = {
                                clearAdjustGpuPreviewOnly()
                                activePanel = BottomPanel.FILTER_MENU
                            },
                            onApply = {
                                vm.applySharpenFast(sharpenAmountVal)
                                activePanel = BottomPanel.DOCK
                            }
                        )
                        CompactSliderRow("Güç", "%${sharpenAmountVal.roundToInt()}", sharpenAmountVal, 0f..250f, defaultValue = 0f) {
                            sharpenAmountVal = it
                            vm.updateProxySharpen(sharpenAmountVal)
                        }
                    }
                }

                // Category 5: Efekt -> [Siyah-Beyaz] [Negatif] [Ayna]
                BottomPanel.AUTO_ENHANCE_MENU -> AutoEnhancePanel(
                    onClose = { activePanel = BottomPanel.ADJUST_MENU },
                    onPick = { mode, lbl -> vm.applyAutoEnhance(mode, lbl); activePanel = BottomPanel.DOCK }
                )
                BottomPanel.CINEMA_LOOK_EDIT -> CinemaLookPanel(
                    preset = cinemaPreset, onPreset = { cinemaPreset = it },
                    intensity = cinemaIntensity, onIntensity = { cinemaIntensity = it },
                    vignette = cinemaVignette, onVignette = { cinemaVignette = it },
                    grain = cinemaGrain, onGrain = { cinemaGrain = it },
                    onPreview = { vm.updateProxyCinemaLook(cinemaPreset, cinemaIntensity, cinemaVignette, cinemaGrain) },
                    onClose = { vm.endProxyPreview(); activePanel = BottomPanel.EFFECT_MENU },
                    onApply = { vm.applyCinemaLook(cinemaPreset, cinemaIntensity, cinemaVignette, cinemaGrain); activePanel = BottomPanel.DOCK }
                )
                BottomPanel.MOTION_BLUR_EDIT -> MotionBlurPanel(
                    angle = motionBlurAngle, onAngle = { motionBlurAngle = it },
                    distance = motionBlurDistance, onDistance = { motionBlurDistance = it },
                    onPreview = { vm.updateProxyMotionBlur(motionBlurAngle, motionBlurDistance) },
                    onClose = { vm.endProxyPreview(); activePanel = BottomPanel.FILTER_MENU },
                    onApply = {
                        if (motionBlurDistance < 1f) vm.endProxyPreview()
                        else vm.applyMotionBlur(motionBlurAngle, motionBlurDistance)
                        activePanel = BottomPanel.DOCK
                    }
                )
                BottomPanel.RADIAL_BLUR_EDIT -> RadialBlurPanel(
                    amount = radialBlurAmount, onAmount = { radialBlurAmount = it },
                    method = radialBlurMethod, onMethod = { radialBlurMethod = it },
                    onPreview = { vm.updateProxyRadialBlur(radialBlurAmount, radialBlurMethod) },
                    onClose = { vm.endProxyPreview(); activePanel = BottomPanel.FILTER_MENU },
                    onApply = {
                        if (radialBlurAmount < 1f) vm.endProxyPreview()
                        else vm.applyRadialBlur(radialBlurAmount, radialBlurMethod)
                        activePanel = BottomPanel.DOCK
                    }
                )
                BottomPanel.LIQUIFY_EDIT -> LiquifyEditPanel(
                    tool = liquifyTool, onTool = { liquifyTool = it },
                    size = liquifySize, onSize = { liquifySize = it },
                    pressure = liquifyPressure, onPressure = { liquifyPressure = it },
                    onReset = { vm.resetLiquify() },
                    onClose = { vm.cancelLiquify(); liquifyCursorScreen = null; activePanel = BottomPanel.EFFECT_MENU },
                    onApply = { liquifyCursorScreen = null; vm.commitLiquify(); activePanel = BottomPanel.DOCK }
                )
                BottomPanel.EFFECT_MENU -> {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BottomEditorHeader(
                            title = "Hızlı Efektler",
                            subtitle = "Siyah-Beyaz, Negatif veya 60 FPS Ayna",
                            onClose = { activePanel = BottomPanel.DOCK }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
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
                                    selectedMirrorMode = MIRROR_MODES.first()
                                    mirrorAxisRatio = 0.50f
                                    canvasViewRef?.fitToDocument(0)
                                    pushGpuMirrorPreview(selectedMirrorMode, mirrorAxisRatio)
                                    activePanel = BottomPanel.MIRROR_EDIT
                                }
                            )
                            CircularDockButton(
                                icon = Icons.Default.Waves,
                                label = "Sıvılaştır",
                                onClick = {
                                    liquifyTool = "forwardWarp"
                                    val docMax = maxOf(state.docInfo?.width?.toInt() ?: 1024, state.docInfo?.height?.toInt() ?: 1024).toFloat()
                                    liquifySize = (docMax * 0.14f).coerceIn(48f, 220f)
                                    liquifyPressure = 75f
                                    vm.beginLiquify()
                                    activePanel = BottomPanel.LIQUIFY_EDIT
                                }
                            )
                            CircularDockButton(
                                icon = Icons.Default.MovieFilter,
                                label = "Sinema",
                                onClick = {
                                    cinemaPreset = "golden"
                                    cinemaIntensity = 80f
                                    cinemaVignette = -25f
                                    cinemaGrain = 12f
                                    vm.beginProxyPreview()
                                    vm.updateProxyCinemaLook(cinemaPreset, cinemaIntensity, cinemaVignette, cinemaGrain)
                                    activePanel = BottomPanel.CINEMA_LOOK_EDIT
                                }
                            )
                        }
                    }
                }

                // 60 FPS GPU Mirror Effect — drag right on the photo OR use the slider!
                BottomPanel.MIRROR_EDIT -> {
                    val isEnine = selectedMirrorMode.isVerticalAxis
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BottomEditorHeader(
                            title = "Ayna Efekti",
                            subtitle = "Fotoğraf üzerinde parmağınla çizgiyi sürükle",
                            applyLabel = "Uygula",
                            onClose = {
                                clearGpuPreview()
                                activePanel = BottomPanel.EFFECT_MENU
                            },
                            onApply = {
                                val modeId = selectedMirrorMode.id
                                val ratio = mirrorAxisRatio
                                vm.applyPhotoMirror(modeId, ratio)
                                activePanel = BottomPanel.DOCK
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
                                    selectedMirrorMode = MIRROR_MODES[0]
                                    pushGpuMirrorPreview(selectedMirrorMode, mirrorAxisRatio)
                                },
                                label = { Text("Ayna (Enine)", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = !isEnine,
                                onClick = {
                                    selectedMirrorMode = MIRROR_MODES[2]
                                    pushGpuMirrorPreview(selectedMirrorMode, mirrorAxisRatio)
                                },
                                label = { Text("Ayna (Boyuna)", fontSize = 11.sp) }
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            // Flip mirrored side (Sol->Sağ <-> Sağ->Sol, Üst->Alt <-> Alt->Üst)
                            OutlinedButton(
                                onClick = {
                                    selectedMirrorMode = when (selectedMirrorMode.id) {
                                        "enine_auto" -> MIRROR_MODES[1]
                                        "enine_flip" -> MIRROR_MODES[0]
                                        "boyuna_auto" -> MIRROR_MODES[3]
                                        else -> MIRROR_MODES[2]
                                    }
                                    pushGpuMirrorPreview(selectedMirrorMode, mirrorAxisRatio)
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
                            valueText = "%${(mirrorAxisRatio * 100).roundToInt()}",
                            value = mirrorAxisRatio,
                            range = 0.05f..0.95f,
                            defaultValue = 0.50f
                        ) {
                            mirrorAxisRatio = it
                            pushGpuMirrorPreview(selectedMirrorMode, it)
                        }
                    }
                }

                BottomPanel.LAYER_TRANSFORM -> {
                    val activeName = state.layers.firstOrNull { it.isActive }?.name ?: "Aktif Katman"
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BottomEditorHeader(
                            title = "Katmanı Taşı: $activeName",
                            subtitle = "1 parmak taşı · 2 parmak büyüt/küçült ve döndür",
                            applyLabel = "Uygula",
                            onClose = {
                                vm.clearActiveLayerPreview()
                                activePanel = BottomPanel.DOCK
                            },
                            onApply = {
                                vm.transformActiveLayer(layerDx, layerDy, layerScalePct, layerRotDeg)
                                activePanel = BottomPanel.DOCK
                            }
                        )
                        CompactSliderRow("Boyut", "%${layerScalePct.roundToInt()}", layerScalePct, 15f..250f, defaultValue = 100f) {
                            layerScalePct = it
                        }
                        CompactSliderRow("Döndür", "${layerRotDeg.roundToInt()}°", layerRotDeg, -180f..180f, defaultValue = 0f) {
                            layerRotDeg = it
                        }
                    }
                }

                BottomPanel.LAYER_FX_EDIT -> {
                    val activeName = state.layers.firstOrNull { it.isActive }?.name ?: "Katman"
                    fun pushFxLive() {
                        vm.updateProxyLayerEffect(fxKind, fxColorHex, fxSizeVal, fxOpacityVal, fxDistanceVal)
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
                                activePanel = BottomPanel.DOCK
                            },
                            onApply = {
                                vm.applyLayerEffect(fxKind, fxColorHex, fxSizeVal, fxOpacityVal, fxDistanceVal)
                                activePanel = BottomPanel.DOCK
                            }
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "dropShadow" to "Gölge",
                                "stroke" to "Kontur",
                                "outerGlow" to "Işıma",
                                "colorOverlay" to "Renk"
                            ).forEach { (id, lbl) ->
                                FilterChip(
                                    selected = fxKind == id,
                                    onClick = {
                                        fxKind = id
                                        if (id == "dropShadow") fxColorHex = "#000000"
                                        else if (id == "stroke") fxColorHex = "#FFFFFF"
                                        else if (id == "outerGlow") fxColorHex = "#4F9CFF"
                                        pushFxLive()
                                    },
                                    label = { Text(lbl, fontSize = 11.sp) }
                                )
                            }
                            FilterChip(
                                selected = showFxFineTune,
                                onClick = { showFxFineTune = !showFxFineTune },
                                label = { Text(if (showFxFineTune) "İnce ▲" else "İnce ▼", fontSize = 11.sp) }
                            )
                            OutlinedButton(
                                onClick = {
                                    vm.endProxyPreview()
                                    vm.clearLayerEffects()
                                    activePanel = BottomPanel.DOCK
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
                                            width = if (fxColorHex.equals(hex, true)) 2.5.dp else 1.dp,
                                            color = if (fxColorHex.equals(hex, true)) MaterialTheme.colorScheme.primary else Color.Gray,
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            fxColorHex = hex
                                            pushFxLive()
                                        }
                                )
                            }
                        }
                        if (fxKind != "colorOverlay") {
                            CompactSliderRow("Boyut", "${fxSizeVal.roundToInt()}px", fxSizeVal, 1f..80f, defaultValue = 16f) {
                                fxSizeVal = it
                                pushFxLive()
                            }
                        }
                        if (showFxFineTune) {
                            CompactSliderRow("Opaklık", "%${fxOpacityVal.roundToInt()}", fxOpacityVal, 5f..100f, defaultValue = 75f) {
                                fxOpacityVal = it
                                pushFxLive()
                            }
                            if (fxKind == "dropShadow") {
                                CompactSliderRow("Uzaklık", "${fxDistanceVal.roundToInt()}px", fxDistanceVal, 0f..80f, defaultValue = 12f) {
                                    fxDistanceVal = it
                                    pushFxLive()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraLightPanel(
    exposure: Float, onExposure: (Float) -> Unit,
    shadows: Float, onShadows: (Float) -> Unit,
    highlights: Float, onHighlights: (Float) -> Unit,
    vibrance: Float, onVibrance: (Float) -> Unit,
    temp: Float, onTemp: (Float) -> Unit,
    tint: Float, onTint: (Float) -> Unit,
    clarity: Float, onClarity: (Float) -> Unit,
    dehaze: Float, onDehaze: (Float) -> Unit,
    showFine: Boolean, onToggleFine: () -> Unit,
    onPreview: () -> Unit,
    onClose: () -> Unit,
    onApply: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        BottomEditorHeader("Kamera Işık (RAW)", "60 FPS canlı önizleme — Uygula ile kalıcı işle", "Uygula", onClose, onApply)
        CompactSliderRow("Pozlama", String.format(java.util.Locale.US, "%+.2f", exposure), exposure, -2.5f..2.5f, 0f) { onExposure(it); onPreview() }
        CompactSliderRow("Gölgeler", "${shadows.roundToInt()}", shadows, -100f..100f, 0f) { onShadows(it); onPreview() }
        CompactSliderRow("Parlaklar", "${highlights.roundToInt()}", highlights, -100f..100f, 0f) { onHighlights(it); onPreview() }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                CompactSliderRow("Canlılık", "${vibrance.roundToInt()}", vibrance, -100f..100f, 0f) { onVibrance(it); onPreview() }
            }
            FilterChip(selected = showFine, onClick = onToggleFine, label = { Text(if (showFine) "İnce ▲" else "İnce ▼", fontSize = 10.sp, maxLines = 1, softWrap = false) })
        }
        if (showFine) {
            CompactSliderRow("Sıcaklık", "${temp.roundToInt()}", temp, -100f..100f, 0f) { onTemp(it); onPreview() }
            CompactSliderRow("Netlik", "${clarity.roundToInt()}", clarity, -100f..100f, 0f) { onClarity(it); onPreview() }
            CompactSliderRow("Sis Gider", "${dehaze.roundToInt()}", dehaze, -100f..100f, 0f) { onDehaze(it); onPreview() }
            CompactSliderRow("Renk Tonu", "${tint.roundToInt()}", tint, -100f..100f, 0f) { onTint(it); onPreview() }
        }
    }
}

@Composable
private fun GradientEditPanel(
    style: String, onStyle: (String) -> Unit,
    startHex: String, onStartHex: (String) -> Unit,
    endHex: String, onEndHex: (String) -> Unit,
    opacity: Float, onOpacity: (Float) -> Unit,
    onPreview: () -> Unit,
    onClose: () -> Unit,
    onApply: () -> Unit
) {
    val swatches = listOf("#4F9CFF", "#FF4D4F", "#FAAD14", "#52C41A", "#A855F7", "#FFFFFF", "#000000")
    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BottomEditorHeader("Degrade Katmanı", "Tuvalde sürükleyerek yönü ayarla — 60 FPS canlı önizleme", "Uygula", onClose, onApply)
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("linear" to "Doğrusal", "radial" to "Dairesel", "reflected" to "Yansımalı", "diamond" to "Elmas").forEach { (st, lbl) ->
                FilterChip(selected = style == st, onClick = { onStyle(st); onPreview() }, label = { Text(lbl, fontSize = 11.sp) })
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Başlangıç", fontSize = 11.sp, color = Color(0xFFDCE0EC), modifier = Modifier.width(62.dp))
            swatches.forEach { hex ->
                val c = remember(hex) { Color(AndroidColor.parseColor(hex)) }
                Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(c).border(if (startHex == hex) 2.dp else 1.dp, if (startHex == hex) Color.White else Color(0xFF444858), CircleShape).clickable { onStartHex(hex); onPreview() })
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Bitiş", fontSize = 11.sp, color = Color(0xFFDCE0EC), modifier = Modifier.width(62.dp))
            swatches.forEach { hex ->
                val c = remember(hex) { Color(AndroidColor.parseColor(hex)) }
                Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(c).border(if (endHex == hex) 2.dp else 1.dp, if (endHex == hex) Color.White else Color(0xFF444858), CircleShape).clickable { onEndHex(hex); onPreview() })
            }
        }
        CompactSliderRow("Opaklık", "%${opacity.roundToInt()}", opacity, 10f..100f, 85f) { onOpacity(it); onPreview() }
    }
}

@Composable
private fun ShapeEditPanel(
    kind: String, onKind: (String) -> Unit,
    fillHex: String, onFillHex: (String) -> Unit,
    strokeWidth: Float, onStrokeWidth: (Float) -> Unit,
    cornerRadius: Float, onCornerRadius: (Float) -> Unit,
    sides: Int, onSides: (Int) -> Unit,
    showFine: Boolean, onToggleFine: () -> Unit,
    onPreview: () -> Unit,
    onClose: () -> Unit,
    onApply: () -> Unit
) {
    val swatches = listOf("" to "Yok", "#4F9CFF" to "Mavi", "#FF4D4F" to "Kırmızı", "#52C41A" to "Yeşil", "#FAAD14" to "Sarı", "#FFFFFF" to "Beyaz", "#000000" to "Siyah")
    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BottomEditorHeader("Vektör Şekil", "İçinden sürükleyip taşı, köşeden boyutlandır", "Uygula", onClose, onApply)
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("roundedRect" to "Yuvarlak", "rect" to "Kutu", "ellipse" to "Daire", "triangle" to "Üçgen", "polygon" to "Çokgen", "star" to "Yıldız", "line" to "Çizgi").forEach { (k, lbl) ->
                FilterChip(selected = kind == k, onClick = { onKind(k); onPreview() }, label = { Text(lbl, fontSize = 11.sp) })
            }
        }
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Dolgu", fontSize = 11.sp, color = Color(0xFFDCE0EC))
            swatches.forEach { (hex, _) ->
                val isSel = fillHex == hex
                Box(
                    modifier = Modifier.size(24.dp).clip(CircleShape)
                        .background(if (hex.isEmpty()) Color(0xFF2A2C36) else Color(AndroidColor.parseColor(hex)))
                        .border(if (isSel) 2.dp else 1.dp, if (isSel) Color.White else Color(0xFF444858), CircleShape)
                        .clickable { onFillHex(hex); onPreview() },
                    contentAlignment = Alignment.Center
                ) { if (hex.isEmpty()) Text("×", color = Color(0xFFFF6B6B), fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
            FilterChip(selected = showFine, onClick = onToggleFine, label = { Text(if (showFine) "İnce ▲" else "İnce ▼", fontSize = 10.sp, maxLines = 1, softWrap = false) })
        }
        if (showFine) {
            CompactSliderRow("Kenarlık", "${strokeWidth.roundToInt()}px", strokeWidth, 0f..40f, 6f) { onStrokeWidth(it); onPreview() }
            if (kind == "roundedRect") CompactSliderRow("Köşe", "${cornerRadius.roundToInt()}px", cornerRadius, 0f..120f, 24f) { onCornerRadius(it); onPreview() }
            if (kind == "polygon" || kind == "star") CompactSliderRow("Kenar", "$sides", sides.toFloat(), 3f..12f, 6f) { onSides(it.roundToInt().coerceIn(3, 12)); onPreview() }
        }
    }
}

@Composable
private fun AutoEnhancePanel(onClose: () -> Unit, onPick: (String, String) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BottomEditorHeader("Akıllı İyileştir", "Tek dokunuşla otomatik ton, renk veya kontrast", onClose = onClose)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            CircularDockButton(Icons.Default.AutoFixHigh, "Oto Ton", onClick = { onPick("tone", "Oto Ton") })
            CircularDockButton(Icons.Default.ColorLens, "Oto Renk", onClick = { onPick("color", "Oto Renk") })
            CircularDockButton(Icons.Default.Contrast, "Oto Kontrast", onClick = { onPick("contrast", "Oto Kontrast") })
        }
    }
}

@Composable
private fun CinemaLookPanel(
    preset: String, onPreset: (String) -> Unit,
    intensity: Float, onIntensity: (Float) -> Unit,
    vignette: Float, onVignette: (Float) -> Unit,
    grain: Float, onGrain: (Float) -> Unit,
    onPreview: () -> Unit,
    onClose: () -> Unit,
    onApply: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BottomEditorHeader("Sinematik Stiller", "60 FPS canlı görünüm + Vinyet & Film Greni", "Uygula", onClose, onApply)
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("none" to "Doğal", "golden" to "Altın Saat", "tealOrange" to "Sinema", "noir" to "Film S&B", "vivid" to "Canlı Pop", "vintage" to "Retro", "cyber" to "Gece Neon").forEach { (id, lbl) ->
                FilterChip(selected = preset == id, onClick = { onPreset(id); onPreview() }, label = { Text(lbl, fontSize = 11.sp) })
            }
        }
        if (preset != "none") CompactSliderRow("Şiddet", "%${intensity.roundToInt()}", intensity, 0f..100f, 80f) { onIntensity(it); onPreview() }
        CompactSliderRow("Vinyet", "${vignette.roundToInt()}", vignette, -100f..100f, 0f) { onVignette(it); onPreview() }
        CompactSliderRow("Film Greni", "%${grain.roundToInt()}", grain, 0f..100f, 0f) { onGrain(it); onPreview() }
    }
}

@Composable
private fun MotionBlurPanel(
    angle: Float, onAngle: (Float) -> Unit,
    distance: Float, onDistance: (Float) -> Unit,
    onPreview: () -> Unit,
    onClose: () -> Unit,
    onApply: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BottomEditorHeader("Hareket Bulanıklığı", "Hız ve aksiyon yönlü 60 FPS canlı önizleme", "Uygula", onClose, onApply)
        CompactSliderRow("Mesafe", "${distance.roundToInt()}px", distance, 0f..160f, 24f) { onDistance(it); onPreview() }
        CompactSliderRow("Açı", "${angle.roundToInt()}°", angle, -180f..180f, 0f) { onAngle(it); onPreview() }
    }
}

@Composable
private fun RadialBlurPanel(
    amount: Float, onAmount: (Float) -> Unit,
    method: String, onMethod: (String) -> Unit,
    onPreview: () -> Unit,
    onClose: () -> Unit,
    onApply: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BottomEditorHeader("Radyal Bulanıklık", "Merkeze odaklanan Zoom veya Dönüş efekti", "Uygula", onClose, onApply)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = method == "zoom", onClick = { onMethod("zoom"); onPreview() }, label = { Text("Yakınlaş (Zoom)", fontSize = 11.sp) })
            FilterChip(selected = method == "spin", onClick = { onMethod("spin"); onPreview() }, label = { Text("Döndür (Spin)", fontSize = 11.sp) })
        }
        CompactSliderRow("Şiddet", "${amount.roundToInt()}", amount, 0f..80f, 14f) { onAmount(it); onPreview() }
    }
}

@Composable
private fun LiquifyEditPanel(
    tool: String, onTool: (String) -> Unit,
    size: Float, onSize: (Float) -> Unit,
    pressure: Float, onPressure: (Float) -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
    onApply: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BottomEditorHeader("Sıvılaştırma (Liquify)", "Fotoğraf üzerinde parmağınla it, şişir veya bük", "Uygula", onClose, onApply)
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("forwardWarp" to "İt", "bloat" to "Şişir", "pucker" to "Büz", "twirlCw" to "Burgu", "smooth" to "Yumuşat", "reconstruct" to "Geri Al").forEach { (id, lbl) ->
                FilterChip(selected = tool == id, onClick = { onTool(id) }, label = { Text(lbl, fontSize = 11.sp) })
            }
            OutlinedButton(onClick = onReset, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp), modifier = Modifier.height(32.dp)) {
                Text("Sıfırla", fontSize = 11.sp)
            }
        }
        BrushSizeSliderRow("Boyut", size, minPx = 20f, maxPx = 400f, defaultPx = 120f, onChange = onSize)
        CompactSliderRow("Güç", "%${pressure.roundToInt()}", pressure, 10f..100f, 75f, onChange = onPressure)
    }
}
