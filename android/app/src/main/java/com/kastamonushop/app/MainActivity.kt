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
    RADIAL_BLUR_EDIT,
    QUICK_ACTIONS_MENU,
    HSL_MIXER_LIVE,
    POSTER_THRESH_LIVE
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
                    text = "v1.1.0",
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
    val loupeBitmap by vm.loupeBitmap.collectAsState()
    val cinemaThumbs by vm.cinemaThumbs.collectAsState()
    var loupeScreenPos by remember { mutableStateOf<Offset?>(null) }
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
    var retouchTrailPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var cropNormL by remember { mutableFloatStateOf(0.05f) }
    var cropNormT by remember { mutableFloatStateOf(0.05f) }
    var cropNormR by remember { mutableFloatStateOf(0.95f) }
    var cropNormB by remember { mutableFloatStateOf(0.95f) }
    var cropFreeMode by remember { mutableStateOf(false) }
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
    // v1.1.0 HSL 8-Channel Mixer, Paint Bucket & Posterize/Threshold state
    var hslBandIdx by remember { mutableIntStateOf(0) }
    var hslHue by remember { mutableStateOf(List(8) { 0f }) }
    var hslSat by remember { mutableStateOf(List(8) { 0f }) }
    var hslLum by remember { mutableStateOf(List(8) { 0f }) }
    var bucketTolerance by remember { mutableFloatStateOf(36f) }
    var bucketContiguous by remember { mutableStateOf(true) }
    var posterMode by remember { mutableStateOf(true) }
    var posterLevels by remember { mutableFloatStateOf(6f) }
    var threshLevel by remember { mutableFloatStateOf(128f) }
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
                BottomPanel.CAMERA_LIGHT_LIVE -> { clearAdjustGpuPreviewOnly(); activePanel = BottomPanel.ADJUST_MENU }
                BottomPanel.LIQUIFY_EDIT -> { vm.cancelLiquify(); liquifyCursorScreen = null; activePanel = BottomPanel.EFFECT_MENU }
                BottomPanel.AUTO_ENHANCE_MENU -> activePanel = BottomPanel.ADJUST_MENU
                BottomPanel.CINEMA_LOOK_EDIT -> { vm.endProxyPreview(); activePanel = BottomPanel.EFFECT_MENU }
                BottomPanel.MOTION_BLUR_EDIT, BottomPanel.RADIAL_BLUR_EDIT -> { vm.endProxyPreview(); activePanel = BottomPanel.FILTER_MENU }
                BottomPanel.QUICK_ACTIONS_MENU -> activePanel = BottomPanel.DOCK
                BottomPanel.HSL_MIXER_LIVE, BottomPanel.POSTER_THRESH_LIVE -> { vm.endProxyPreview(); activePanel = BottomPanel.ADJUST_MENU }
                else -> activePanel = BottomPanel.DOCK
            }
        } else {
            showExitSaveDialog = true
        }
    }

    LaunchedEffect(state.fitCameraSignal, canvasViewRef) {
        if (state.fitCameraSignal > 0 && canvasViewRef != null) {
            fitToSafeArea(0, activePanel)
        }
    }

    LaunchedEffect(activePanel) {
        if (activePanel != BottomPanel.DOCK && state.compareOriginal) {
            vm.dismissCompareOriginal()
        }
        val isProxyPanel = activePanel == BottomPanel.LIGHT_LIVE ||
            activePanel == BottomPanel.COLOR_LIVE ||
            activePanel == BottomPanel.LEVELS_LIVE ||
            activePanel == BottomPanel.COLOR_BAL_LIVE ||
            activePanel == BottomPanel.CURVES_LIVE ||
            activePanel == BottomPanel.BLUR_EDIT ||
            activePanel == BottomPanel.SHARPEN_EDIT ||
            activePanel == BottomPanel.LAYER_FX_EDIT ||
            activePanel == BottomPanel.CAMERA_LIGHT_LIVE ||
            activePanel == BottomPanel.GRADIENT_EDIT ||
            activePanel == BottomPanel.SHAPE_EDIT ||
            activePanel == BottomPanel.CINEMA_LOOK_EDIT ||
            activePanel == BottomPanel.MOTION_BLUR_EDIT ||
            activePanel == BottomPanel.RADIAL_BLUR_EDIT ||
            activePanel == BottomPanel.HSL_MIXER_LIVE ||
            activePanel == BottomPanel.POSTER_THRESH_LIVE
        if (!isProxyPanel) {
            vm.endProxyPreview()
        }
        if (activePanel != BottomPanel.LIQUIFY_EDIT) {
            vm.cancelLiquify()
            liquifyCursorScreen = null
        }
    }

    LaunchedEffect(activePanel, curveOverlayExpanded, canvasViewRef) {
        if (canvasViewRef != null) {
            when (activePanel) {
                BottomPanel.CURVES_LIVE,
                BottomPanel.PERSPECTIVE_CROP_EDIT,
                BottomPanel.CROP_RATIO_EDIT,
                BottomPanel.MIRROR_EDIT,
                BottomPanel.LIGHT_LIVE,
                BottomPanel.COLOR_LIVE,
                BottomPanel.LEVELS_LIVE,
                BottomPanel.COLOR_BAL_LIVE,
                BottomPanel.BLUR_EDIT,
                BottomPanel.SHARPEN_EDIT,
                BottomPanel.LAYER_TRANSFORM,
                BottomPanel.LAYER_FX_EDIT,
                BottomPanel.CAMERA_LIGHT_LIVE,
                BottomPanel.GRADIENT_EDIT,
                BottomPanel.SHAPE_EDIT,
                BottomPanel.LIQUIFY_EDIT,
                BottomPanel.CINEMA_LOOK_EDIT,
                BottomPanel.MOTION_BLUR_EDIT,
                BottomPanel.RADIAL_BLUR_EDIT,
                BottomPanel.HSL_MIXER_LIVE,
                BottomPanel.POSTER_THRESH_LIVE,
                BottomPanel.DOCK -> fitToSafeArea(pendingQuarterTurns, activePanel)
                else -> Unit
            }
        }
    }

    val openDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            dismissLivePanelIfActive()
            vm.openDocumentFromUri(context.contentResolver, uri, asLayer = openAsLayer)
        }
    }

    val imageChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { res ->
        val uri = if (res.resultCode == Activity.RESULT_OK) res.data?.data else null
        if (uri != null) {
            dismissLivePanelIfActive()
            vm.openDocumentFromUri(context.contentResolver, uri, asLayer = openAsLayer)
        }
    }

    val exportDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        if (uri != null) {
            vm.exportDocumentToUri(context, uri, pendingExportFormat)
        }
    }

    // Keep latest mirror state accessible to VulkanCanvasView touch callbacks
    val currentPanelState by rememberUpdatedState(activePanel)
    val currentMirrorModeState by rememberUpdatedState(selectedMirrorMode)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                VulkanCanvasView(
                    context = ctx,
                    getDocSize = {
                        val d = vm.uiState.value.docInfo
                        (d?.width?.toInt() ?: 2048) to (d?.height?.toInt() ?: 2048)
                    },
                    getBrushSize = { vm.uiState.value.brushSize },
                    isDrawingActive = {
                        val st = vm.uiState.value
                        val t = st.activeTool
                        t == ActiveTool.BRUSH || t == ActiveTool.ERASER ||
                            t == ActiveTool.SMUDGE || t == ActiveTool.DODGE || t == ActiveTool.BURN ||
                            (t == ActiveTool.CLONE_STAMP && !st.pickingCloneSource)
                    },
                    isRetouchActive = {
                        val t = vm.uiState.value.activeTool
                        t == ActiveTool.SPOT_HEAL ||
                            t == ActiveTool.SELECT_QUICK ||
                            t == ActiveTool.SELECT_BRUSH_ADD ||
                            t == ActiveTool.SELECT_BRUSH_SUB
                    },
                    isEyedropperActive = { vm.uiState.value.activeTool == ActiveTool.EYEDROPPER },
                    isRulerEnabled = { vm.uiState.value.rulerEnabled },
                    isMirrorDragActive = { currentPanelState == BottomPanel.MIRROR_EDIT },
                    isMirrorVerticalAxis = { currentMirrorModeState.isVerticalAxis },
                    isTextDragActive = { currentPanelState == BottomPanel.TEXT_EDIT },
                    isSelectRectActive = { vm.uiState.value.activeTool == ActiveTool.SELECT_RECT },
                    isSelectWandActive = { vm.uiState.value.activeTool == ActiveTool.SELECT_WAND },
                    isBucketActive = { vm.uiState.value.activeTool == ActiveTool.BUCKET },
                    isCloneSourcePickActive = {
                        val st = vm.uiState.value
                        st.activeTool == ActiveTool.CLONE_STAMP && st.pickingCloneSource
                    },
                    onStrokeBegin = { sample -> vm.beginLiveStroke(sample) },
                    onStrokeMove = { samples -> vm.pushLiveStroke(samples) },
                    onStrokeEnd = { vm.finishLiveStroke() },
                    onStrokeCancel = { vm.cancelLiveStroke() },
                    onRetouchPreview = { pos ->
                        retouchCursor = pos
                        retouchTrailPoints = if (pos == null) emptyList() else (retouchTrailPoints + Offset(pos.first, pos.second))
                    },
                    onRetouchCommit = { samples ->
                        val t = vm.uiState.value.activeTool
                        if (t == ActiveTool.SELECT_QUICK || t == ActiveTool.SELECT_BRUSH_ADD || t == ActiveTool.SELECT_BRUSH_SUB) {
                            vm.applySelectionStroke(samples)
                        } else {
                            vm.applyRetouchStroke(samples)
                        }
                    },
                    onRulerPreview = { preview -> rulerPreview = preview },
                    onRulerStrokeCommit = { samples -> vm.commitRulerStroke(samples) },
                    onEyedropperSample = { dx, dy -> vm.sampleColorAt(dx, dy) },
                    onMirrorAxisDrag = { ratio ->
                        mirrorAxisRatio = ratio
                        pushGpuMirrorPreview(currentMirrorModeState, ratio)
                    },
                    onTextPositionDrag = { nx, ny ->
                        textNormX = nx
                        textNormY = ny
                    },
                    onSelectRectPreview = { arr -> selectPreviewRect = arr },
                    onSelectRectCommit = { rx, ry, rw, rh -> vm.selectRect(rx, ry, rw, rh) },
                    onSelectWandTap = { dx, dy -> vm.selectMagicWand(dx, dy) },
                    onBucketTap = { dx, dy -> vm.applyPaintBucket(dx, dy, bucketTolerance.roundToInt(), bucketContiguous) },
                    onCloneSourcePick = { dx, dy -> vm.setCloneSource(dx, dy) },
                    onLoupePointer = { sx, sy, dx, dy, active ->
                        val tool = vm.uiState.value.activeTool
                        val needsLoupe = tool == ActiveTool.EYEDROPPER ||
                            tool == ActiveTool.CLONE_STAMP ||
                            (vm.uiState.value.brushSize <= 24f && (
                                tool == ActiveTool.BRUSH || tool == ActiveTool.ERASER ||
                                tool == ActiveTool.SPOT_HEAL || tool == ActiveTool.SMUDGE ||
                                tool == ActiveTool.DODGE || tool == ActiveTool.BURN ||
                                tool == ActiveTool.SELECT_BRUSH_ADD || tool == ActiveTool.SELECT_BRUSH_SUB
                            ))
                        if (active && needsLoupe) {
                            loupeScreenPos = Offset(sx, sy)
                            val halfExt = if (tool == ActiveTool.EYEDROPPER) 12f else (vm.uiState.value.brushSize * 1.15f).coerceIn(12f, 44f)
                            vm.updateLoupe(dx, dy, halfExt)
                        } else if (loupeScreenPos != null) {
                            loupeScreenPos = null
                            vm.hideLoupe()
                        }
                    },
                    onCameraChanged = { cameraTick++ }
                ).also { canvasViewRef = it }
            }
        )

        // Live Selection Box & Active Selection Indicator
        selectPreviewRect?.let { r ->
            Canvas(modifier = Modifier.fillMaxSize()) {
                val left = min(r[0], r[2])
                val top = min(r[1], r[3])
                val w = kotlin.math.abs(r[2] - r[0])
                val h = kotlin.math.abs(r[3] - r[1])
                if (state.selectEllipse) {
                    drawOval(
                        color = Color(0xFF4F9CFF),
                        topLeft = Offset(left, top),
                        size = Size(w, h),
                        style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)))
                    )
                } else {
                    drawRect(
                        color = Color(0xFF4F9CFF),
                        topLeft = Offset(left, top),
                        size = Size(w, h),
                        style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)))
                    )
                }
            }
        }
        // Active selection pixel mask & organic contour are rendered at 60 FPS directly in blit.wgsl
        // Clone Stamp Source Crosshair
        if (state.activeTool == ActiveTool.CLONE_STAMP) {
            state.cloneSourceDoc?.let { (cx, cy) ->
                val view = canvasViewRef
                if (view != null) {
                    val _tick = cameraTick
                    val (sx, sy) = view.docToScreen(cx, cy)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(color = Color(0xFF2ED573), radius = 14f, center = Offset(sx, sy), style = Stroke(width = 2.5f))
                        drawLine(Color(0xFF2ED573), Offset(sx - 20f, sy), Offset(sx + 20f, sy), strokeWidth = 2.5f)
                        drawLine(Color(0xFF2ED573), Offset(sx, sy - 20f), Offset(sx, sy + 20f), strokeWidth = 2.5f)
                    }
                }
            }
        }

        // Live Brush Size Preview Circle in center of screen when dragging Boyut slider
        if (showBrushSizeCircle) {
            val z = canvasViewRef?.getCurrentZoom() ?: 1f
            val rPx = (state.brushSize * z * 0.5f).coerceIn(4f, 260f)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val c = Offset(size.width * 0.5f, size.height * 0.42f)
                drawCircle(color = Color(0x334F9CFF), radius = rPx, center = c)
                drawCircle(color = Color.White, radius = rPx, center = c, style = Stroke(width = 2.5f))
            }
        }

        // Live Ruler Straight-Line Guide Overlay
        rulerPreview?.let { line ->
            Canvas(modifier = Modifier.fillMaxSize()) {
                val guideColor = if (line.isSnapped) Color(0xFF2ED573) else Color(0xFF4F9CFF)
                drawLine(
                    color = guideColor,
                    start = Offset(line.startX, line.startY),
                    end = Offset(line.endX, line.endY),
                    strokeWidth = 4f,
                    pathEffect = if (line.isSnapped) null else PathEffect.dashPathEffect(floatArrayOf(16f, 10f))
                )
                drawCircle(color = Color.White, radius = 8f, center = Offset(line.startX, line.startY))
                drawCircle(color = guideColor, radius = 8f, center = Offset(line.endX, line.endY))
            }
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 68.dp),
                shape = RoundedCornerShape(14.dp),
                color = if (line.isSnapped) Color(0xDD1E462F) else Color(0xDD1A2333)
            ) {
                Text(
                    text = if (line.isSnapped) "Cetvel · ${line.angleDeg}° (Hizalandı)" else "Cetvel · ${line.angleDeg}°",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
        }

        // 60 FPS Dashed Mirror Axis Overlay with Finger Drag Handle
        if (activePanel == BottomPanel.MIRROR_EDIT) {
            val docW = (state.docInfo?.width?.toInt() ?: 2048).toFloat()
            val docH = (state.docInfo?.height?.toInt() ?: 2048).toFloat()
            val viewRef = canvasViewRef
            val tick = cameraTick
            if (viewRef != null && tick >= 0) {
                val (p0, p1) = if (selectedMirrorMode.isVerticalAxis) {
                    val ax = docW * mirrorAxisRatio
                    viewRef.docToScreen(ax, 0f) to viewRef.docToScreen(ax, docH)
                } else {
                    val ay = docH * mirrorAxisRatio
                    viewRef.docToScreen(0f, ay) to viewRef.docToScreen(docW, ay)
                }
                val midX = (p0.first + p1.first) * 0.5f
                val midY = (p0.second + p1.second) * 0.5f
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val dash = PathEffect.dashPathEffect(floatArrayOf(18f, 12f))
                    drawLine(
                        color = Color(0x99000000),
                        start = Offset(p0.first, p0.second),
                        end = Offset(p1.first, p1.second),
                        strokeWidth = 6f,
                        pathEffect = dash
                    )
                    drawLine(
                        color = Color.White,
                        start = Offset(p0.first, p0.second),
                        end = Offset(p1.first, p1.second),
                        strokeWidth = 3f,
                        pathEffect = dash
                    )
                    // Center drag handle ring so user knows they can drag right on the photo
                    drawCircle(color = Color(0xCC19191E), radius = 16f, center = Offset(midX, midY))
                    drawCircle(color = Color.White, radius = 16f, center = Offset(midX, midY), style = Stroke(width = 3f))
                    drawCircle(color = Color(0xFF4F9CFF), radius = 6f, center = Offset(midX, midY))
                }
            }
        }

        // Live Crop Frame Overlay when in CROP_RATIO_EDIT
        // v1.0.0 60 FPS Live Stroke Ribbon Trail for Leke (Spot Heal) & Selection Brushes
        if (retouchTrailPoints.isNotEmpty()) {
            val viewRef = canvasViewRef
            val trailColor = when (state.activeTool) {
                ActiveTool.SPOT_HEAL -> Color(0x88FF5A5F)
                ActiveTool.SELECT_BRUSH_SUB -> Color(0x88FF4D4F)
                ActiveTool.SELECT_QUICK -> Color(0x882ED573)
                else -> Color(0x884F9CFF)
            }
            val strokeW = ((state.brushSize * (viewRef?.getCurrentZoom() ?: 1f)).coerceIn(4f, 360f))
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (retouchTrailPoints.size == 1) {
                    drawCircle(trailColor, radius = strokeW * 0.5f, center = retouchTrailPoints[0])
                } else {
                    val p = Path().apply {
                        moveTo(retouchTrailPoints[0].x, retouchTrailPoints[0].y)
                        for (i in 1 until retouchTrailPoints.size) {
                            lineTo(retouchTrailPoints[i].x, retouchTrailPoints[i].y)
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

        // v1.0.0 Interactive 4-Corner Draggable & Movable Normal Crop Overlay
        if (activePanel == BottomPanel.CROP_RATIO_EDIT) {
            val docW = (state.docInfo?.width?.toInt() ?: 2048).toFloat()
            val docH = (state.docInfo?.height?.toInt() ?: 2048).toFloat()
            val viewRef = canvasViewRef
            val tick = cameraTick
            if (viewRef != null && tick >= 0) {
                val (sx0, sy0) = viewRef.docToScreen(docW * cropNormL, docH * cropNormT)
                val (sx1, sy1) = viewRef.docToScreen(docW * cropNormR, docH * cropNormB)
                val left = min(sx0, sx1)
                val top = min(sy0, sy1)
                val boxW = abs(sx1 - sx0)
                val boxH = abs(sy1 - sy0)
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(docW, docH, cropFreeMode, selectedCropPreset) {
                            awaitPointerEventScope {
                                while (true) {
                                    val firstDown = awaitFirstDown(requireUnconsumed = false)
                                    val pos0 = firstDown.position
                                    val (curX0, curY0) = viewRef.docToScreen(docW * cropNormL, docH * cropNormT)
                                    val (curX1, curY1) = viewRef.docToScreen(docW * cropNormR, docH * cropNormB)
                                    val cL = min(curX0, curX1)
                                    val cT = min(curY0, curY1)
                                    val cR = kotlin.math.max(curX0, curX1)
                                    val cB = kotlin.math.max(curY0, curY1)
                                    val corners = listOf(Offset(cL, cT), Offset(cR, cT), Offset(cR, cB), Offset(cL, cB))
                                    var bestIdx = -1
                                    var bestDist = 72f
                                    corners.forEachIndexed { i, c ->
                                        val d = kotlin.math.hypot((pos0.x - c.x).toDouble(), (pos0.y - c.y).toDouble()).toFloat()
                                        if (d < bestDist) { bestDist = d; bestIdx = i }
                                    }
                                    val (dx0, dy0) = viewRef.screenToDoc(pos0.x, pos0.y)
                                    var lastDocX = dx0
                                    var lastDocY = dy0
                                    var dragTarget = when {
                                        bestIdx in 0..3 -> bestIdx
                                        pos0.x in cL..cR && pos0.y in cT..cB -> 4
                                        else -> -1
                                    }
                                    var pinchActive = false
                                    var prevDist = 0f
                                    var prevCentroid = Offset.Zero

                                    while (true) {
                                        val ev = awaitPointerEvent()
                                        val pressed = ev.changes.filter { it.pressed }
                                        if (pressed.isEmpty()) {
                                            loupeScreenPos = null
                                            vm.hideLoupe()
                                            break
                                        }
                                        if (pressed.size >= 2) {
                                            loupeScreenPos = null
                                            vm.hideLoupe()
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
                                        } else if (!pinchActive && dragTarget != -1) {
                                            val ch = pressed[0]
                                            ch.consume()
                                            val (dx, dy) = viewRef.screenToDoc(ch.position.x, ch.position.y)
                                            val minFrac = 0.08f
                                            val lockedRatio = if (cropFreeMode) null else (selectedCropPreset.ratio ?: (docW / docH.coerceAtLeast(1f)))
                                            if (dragTarget == 4) {
                                                val dnx = (dx - lastDocX) / docW
                                                val dny = (dy - lastDocY) / docH
                                                lastDocX = dx
                                                lastDocY = dy
                                                val wFrac = cropNormR - cropNormL
                                                val hFrac = cropNormB - cropNormT
                                                val nl = (cropNormL + dnx).coerceIn(0f, (1f - wFrac).coerceAtLeast(0f))
                                                val nt = (cropNormT + dny).coerceIn(0f, (1f - hFrac).coerceAtLeast(0f))
                                                cropNormL = nl; cropNormR = nl + wFrac
                                                cropNormT = nt; cropNormB = nt + hFrac
                                            } else {
                                                val nx = (dx / docW).coerceIn(0f, 1f)
                                                val ny = (dy / docH).coerceIn(0f, 1f)
                                                if (lockedRatio == null) {
                                                    when (dragTarget) {
                                                        0 -> { cropNormL = nx.coerceAtMost(cropNormR - minFrac); cropNormT = ny.coerceAtMost(cropNormB - minFrac) }
                                                        1 -> { cropNormR = nx.coerceAtLeast(cropNormL + minFrac); cropNormT = ny.coerceAtMost(cropNormB - minFrac) }
                                                        2 -> { cropNormR = nx.coerceAtLeast(cropNormL + minFrac); cropNormB = ny.coerceAtLeast(cropNormT + minFrac) }
                                                        3 -> { cropNormL = nx.coerceAtMost(cropNormR - minFrac); cropNormB = ny.coerceAtLeast(cropNormT + minFrac) }
                                                    }
                                                } else {
                                                    val anchorX = if (dragTarget == 0 || dragTarget == 3) cropNormR * docW else cropNormL * docW
                                                    val anchorY = if (dragTarget == 0 || dragTarget == 1) cropNormB * docH else cropNormT * docH
                                                    val rawW = abs(dx - anchorX).coerceAtLeast(docW * minFrac)
                                                    val rawH = (rawW / lockedRatio).coerceAtLeast(docH * minFrac)
                                                    val maxW = if (dragTarget == 0 || dragTarget == 3) anchorX else (docW - anchorX)
                                                    val maxH = if (dragTarget == 0 || dragTarget == 1) anchorY else (docH - anchorY)
                                                    val scaleFit = min(1f, min(maxW / rawW.coerceAtLeast(1f), maxH / rawH.coerceAtLeast(1f)))
                                                    val fw = (rawW * scaleFit) / docW
                                                    val fh = (rawH * scaleFit) / docH
                                                    when (dragTarget) {
                                                        0 -> { cropNormL = (cropNormR - fw).coerceIn(0f, 1f); cropNormT = (cropNormB - fh).coerceIn(0f, 1f) }
                                                        1 -> { cropNormR = (cropNormL + fw).coerceIn(0f, 1f); cropNormT = (cropNormB - fh).coerceIn(0f, 1f) }
                                                        2 -> { cropNormR = (cropNormL + fw).coerceIn(0f, 1f); cropNormB = (cropNormT + fh).coerceIn(0f, 1f) }
                                                        3 -> { cropNormL = (cropNormR - fw).coerceIn(0f, 1f); cropNormB = (cropNormT + fh).coerceIn(0f, 1f) }
                                                    }
                                                }
                                                loupeScreenPos = ch.position
                                                val cxDoc = if (dragTarget == 0 || dragTarget == 3) cropNormL * docW else cropNormR * docW
                                                val cyDoc = if (dragTarget == 0 || dragTarget == 1) cropNormT * docH else cropNormB * docH
                                                vm.updateLoupe(cxDoc, cyDoc, (min(docW, docH) * 0.035f).coerceIn(16f, 64f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                ) {
                    val dim = Color(0x88000000)
                    drawRect(dim, topLeft = Offset(0f, 0f), size = Size(size.width, top.coerceAtLeast(0f)))
                    drawRect(dim, topLeft = Offset(0f, top + boxH), size = Size(size.width, (size.height - top - boxH).coerceAtLeast(0f)))
                    drawRect(dim, topLeft = Offset(0f, top), size = Size(left.coerceAtLeast(0f), boxH))
                    drawRect(dim, topLeft = Offset(left + boxW, top), size = Size((size.width - left - boxW).coerceAtLeast(0f), boxH))
                    val gridColor = Color(0x66FFFFFF)
                    for (i in 1..2) {
                        val gx = left + boxW * (i / 3f)
                        val gy = top + boxH * (i / 3f)
                        drawLine(gridColor, Offset(gx, top), Offset(gx, top + boxH), strokeWidth = 1.5f)
                        drawLine(gridColor, Offset(left, gy), Offset(left + boxW, gy), strokeWidth = 1.5f)
                    }
                    drawRect(color = Color.White, topLeft = Offset(left, top), size = Size(boxW, boxH), style = Stroke(width = 3.5f))
                    // 4 crisp interactive corner handles
                    listOf(
                        Offset(left, top),
                        Offset(left + boxW, top),
                        Offset(left + boxW, top + boxH),
                        Offset(left, top + boxH)
                    ).forEach { corner ->
                        drawCircle(Color.White, radius = 15f, center = corner)
                        drawCircle(Color(0xFF4F9CFF), radius = 10f, center = corner)
                    }
                }
            }
        }

        // Clean loading indicator while decoding/baking heavy 12MP images
        if (state.isBusy) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x66111217)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xEE1E2028),
                    tonalElevation = 10.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text("İşleniyor...", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        // Live Retouch brush ring indicator while dragging
        retouchCursor?.let { (cx, cy) ->
            val zoom = canvasViewRef?.getCurrentZoom() ?: 1f
            val rPx = (state.brushSize * 0.5f * zoom).coerceIn(10f, 180f)
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(color = Color(0x88000000), radius = rPx + 2f, center = Offset(cx, cy), style = Stroke(width = 4f))
                drawCircle(color = Color.White, radius = rPx, center = Offset(cx, cy), style = Stroke(width = 2.5f))
            }
        }

        // 60 FPS Live Draggable Text Preview overlay
        // Interactive 60 FPS Full-Canvas Draggable Tone Curve Overlay (toggled by tapping the mini curve box)
        if (activePanel == BottomPanel.CURVES_LIVE && curveOverlayExpanded) {
            val view = canvasViewRef
            val docW = (state.docInfo?.width?.toInt() ?: 2048).toFloat()
            val docH = (state.docInfo?.height?.toInt() ?: 2048).toFloat()
            if (view != null) {
                val _tick = cameraTick
                val (rawL, rawT) = view.docToScreen(0f, 0f)
                val (rawR, rawB) = view.docToScreen(docW, docH)
                val boxL = min(rawL, rawR)
                val boxR = kotlin.math.max(rawL, rawR)
                val boxT = min(rawT, rawB)
                val boxB = kotlin.math.max(rawT, rawB)
                val boxW = (boxR - boxL).coerceAtLeast(120f)
                val boxH = (boxB - boxT).coerceAtLeast(120f)

                val knotXs = floatArrayOf(0f, 0.25f, 0.50f, 0.75f, 1.0f)
                fun knotScreen(idx: Int, yNorm: Float): Offset =
                    Offset(boxL + knotXs[idx] * boxW, boxB - yNorm * boxH)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(boxL, boxT, boxW, boxH) {
                            detectDragGestures(
                                onDragStart = { pos ->
                                    val ys = floatArrayOf(curveBlackVal, curveShadowVal, curveMidVal, curveHighVal, curveWhiteVal)
                                    var bestIdx = -1
                                    var bestDist = 110f
                                    for (i in 0..4) {
                                        val pt = knotScreen(i, ys[i])
                                        val d = kotlin.math.hypot((pos.x - pt.x).toDouble(), (pos.y - pt.y).toDouble()).toFloat()
                                        if (d < bestDist) {
                                            bestDist = d
                                            bestIdx = i
                                        }
                                    }
                                    // If tapped directly near the curve between nodes, pick nearest X node
                                    if (bestIdx == -1 && pos.x in (boxL - 36f)..(boxR + 36f) && pos.y in (boxT - 36f)..(boxB + 36f)) {
                                        val nx = ((pos.x - boxL) / boxW).coerceIn(0f, 1f)
                                        bestIdx = (nx * 4f).roundToInt().coerceIn(0, 4)
                                    }
                                    draggingCurveNode = bestIdx
                                },
                                onDragEnd = { draggingCurveNode = -1 },
                                onDragCancel = { draggingCurveNode = -1 },
                                onDrag = { change, _ ->
                                    if (draggingCurveNode in 0..4) {
                                        change.consume()
                                        val ny = ((boxB - change.position.y) / boxH).coerceIn(0f, 1f)
                                        when (draggingCurveNode) {
                                            0 -> curveBlackVal = ny
                                            1 -> curveShadowVal = ny
                                            2 -> curveMidVal = ny
                                            3 -> curveHighVal = ny
                                            4 -> curveWhiteVal = ny
                                        }
                                        pushCurveGpu(curveBlackVal, curveShadowVal, curveMidVal, curveHighVal, curveWhiteVal)
                                    }
                                }
                            )
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // Dashed outer frame over the photo
                        drawRect(
                            color = Color(0xBBFFFFFF),
                            topLeft = Offset(boxL, boxT),
                            size = Size(boxW, boxH),
                            style = Stroke(width = 2.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
                        )
                        // Subtle 4x4 grid + diagonal reference
                        for (g in 1..3) {
                            val t = g / 4f
                            drawLine(Color(0x33FFFFFF), Offset(boxL + boxW * t, boxT), Offset(boxL + boxW * t, boxB), 1f)
                            drawLine(Color(0x33FFFFFF), Offset(boxL, boxT + boxH * t), Offset(boxR, boxT + boxH * t), 1f)
                        }
                        drawLine(Color(0x44FFFFFF), Offset(boxL, boxB), Offset(boxR, boxT), 1.5f)

                        // Smooth 64-step cubic Hermite spline curve (matches blit.wgsl 1:1!)
                        val path = Path()
                        val steps = 64
                        for (i in 0..steps) {
                            val x = i.toFloat() / steps.toFloat()
                            val y = evalHermiteCurve(x, curveBlackVal, curveShadowVal, curveMidVal, curveHighVal, curveWhiteVal)
                            val sx = boxL + x * boxW
                            val sy = boxB - y * boxH
                            if (i == 0) path.moveTo(sx, sy) else path.lineTo(sx, sy)
                        }
                        drawPath(path, color = Color(0xCC000000), style = Stroke(width = 6f))
                        drawPath(path, color = Color.White, style = Stroke(width = 3f))

                        // 5 large draggable translucent circular handles
                        val ys = floatArrayOf(curveBlackVal, curveShadowVal, curveMidVal, curveHighVal, curveWhiteVal)
                        for (i in 0..4) {
                            val pt = knotScreen(i, ys[i])
                            val active = draggingCurveNode == i
                            drawCircle(
                                color = if (active) Color(0xAA4F9CFF) else Color(0x66FFFFFF),
                                radius = if (active) 30f else 24f,
                                center = pt
                            )
                            drawCircle(
                                color = if (active) Color(0xFF4F9CFF) else Color.White,
                                radius = if (active) 30f else 24f,
                                center = pt,
                                style = Stroke(width = 2.5f)
                            )
                            drawCircle(color = Color.White, radius = 5f, center = pt)
                        }
                    }
                }
            }
        }

        // Interactive 60 FPS 4-Corner Perspective Crop Overlay
        if (activePanel == BottomPanel.PERSPECTIVE_CROP_EDIT) {
            val view = canvasViewRef
            val docW = (state.docInfo?.width?.toInt() ?: 2048).toFloat()
            val docH = (state.docInfo?.height?.toInt() ?: 2048).toFloat()
            if (view != null) {
                val _tick = cameraTick
                fun toScreen(n: Offset): Offset {
                    val (sx, sy) = view.docToScreen(n.x * docW, n.y * docH)
                    return Offset(sx, sy)
                }
                val sTl = toScreen(pTl)
                val sTr = toScreen(pTr)
                val sBr = toScreen(pBr)
                val sBl = toScreen(pBl)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(docW, docH, _tick) {
                            detectDragGestures(
                                onDragStart = { pos ->
                                    val pts = listOf(toScreen(pTl), toScreen(pTr), toScreen(pBr), toScreen(pBl))
                                    var bestIdx = -1
                                    var bestDist = 96f
                                    pts.forEachIndexed { i, pt ->
                                        val d = kotlin.math.hypot((pos.x - pt.x).toDouble(), (pos.y - pt.y).toDouble()).toFloat()
                                        if (d < bestDist) {
                                            bestDist = d
                                            bestIdx = i
                                        }
                                    }
                                    draggingCornerIdx = bestIdx
                                },
                                onDragEnd = { draggingCornerIdx = -1; loupeScreenPos = null; vm.hideLoupe() },
                                onDragCancel = { draggingCornerIdx = -1; loupeScreenPos = null; vm.hideLoupe() },
                                onDrag = { change, dragAmount ->
                                    if (draggingCornerIdx in 0..3) {
                                        change.consume()
                                        val z = view.getCurrentZoom().coerceAtLeast(0.05f)
                                        val dnX = dragAmount.x / (docW * z)
                                        val dnY = dragAmount.y / (docH * z)
                                        val cornerPt = when (draggingCornerIdx) {
                                            0 -> { pTl = Offset((pTl.x + dnX).coerceIn(0f, 1f), (pTl.y + dnY).coerceIn(0f, 1f)); pTl }
                                            1 -> { pTr = Offset((pTr.x + dnX).coerceIn(0f, 1f), (pTr.y + dnY).coerceIn(0f, 1f)); pTr }
                                            2 -> { pBr = Offset((pBr.x + dnX).coerceIn(0f, 1f), (pBr.y + dnY).coerceIn(0f, 1f)); pBr }
                                            else -> { pBl = Offset((pBl.x + dnX).coerceIn(0f, 1f), (pBl.y + dnY).coerceIn(0f, 1f)); pBl }
                                        }
                                        loupeScreenPos = change.position
                                        vm.updateLoupe(cornerPt.x * docW, cornerPt.y * docH, (min(docW, docH) * 0.035f).coerceIn(16f, 64f))
                                    }
                                }
                            )
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val quad = Path().apply {
                            moveTo(sTl.x, sTl.y)
                            lineTo(sTr.x, sTr.y)
                            lineTo(sBr.x, sBr.y)
                            lineTo(sBl.x, sBl.y)
                            close()
                        }
                        drawPath(quad, color = Color(0xFF4F9CFF), style = Stroke(width = 4f))
                        // 3x3 perspective grid inside the quad
                        for (k in 1..2) {
                            val t = k / 3f
                            val topPt = Offset(sTl.x + (sTr.x - sTl.x) * t, sTl.y + (sTr.y - sTl.y) * t)
                            val botPt = Offset(sBl.x + (sBr.x - sBl.x) * t, sBl.y + (sBr.y - sBl.y) * t)
                            drawLine(Color(0x88FFFFFF), topPt, botPt, strokeWidth = 1.5f)
                            val leftPt = Offset(sTl.x + (sBl.x - sTl.x) * t, sTl.y + (sBl.y - sTl.y) * t)
                            val rightPt = Offset(sTr.x + (sBr.x - sTr.x) * t, sTr.y + (sBr.y - sTr.y) * t)
                            drawLine(Color(0x88FFFFFF), leftPt, rightPt, strokeWidth = 1.5f)
                        }
                        listOf(sTl, sTr, sBr, sBl).forEachIndexed { i, pt ->
                            val active = draggingCornerIdx == i
                            drawCircle(
                                color = if (active) Color(0xFF2ED573) else Color(0xFF4F9CFF),
                                radius = if (active) 24f else 18f,
                                center = pt
                            )
                            drawCircle(color = Color.White, radius = 8f, center = pt)
                        }
                    }
                }
            }
        }

        // Interactive 60 FPS Active Layer Move / Scale / Rotate Overlay
        if (activePanel == BottomPanel.LAYER_TRANSFORM) {
            val view = canvasViewRef
            val prev = activeLayerPrev
            if (view != null && prev != null) {
                val _tick = cameraTick
                val b = prev.first
                val z = view.getCurrentZoom().coerceAtLeast(0.05f)
                val cxDoc = (b.x0 + b.x1) * 0.5f + layerDx
                val cyDoc = (b.y0 + b.y1) * 0.5f + layerDy
                val halfW = (b.x1 - b.x0).coerceAtLeast(10) * 0.5f * (layerScalePct / 100f)
                val halfH = (b.y1 - b.y0).coerceAtLeast(10) * 0.5f * (layerScalePct / 100f)
                val (sCx, sCy) = view.docToScreen(cxDoc, cyDoc)
                val boxW = halfW * 2f * z
                val boxH = halfH * 2f * z

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(z) {
                            detectTransformGestures { _, pan, zoomChange, rotationChange ->
                                layerDx += pan.x / z
                                layerDy += pan.y / z
                                if (zoomChange != 1f) {
                                    layerScalePct = (layerScalePct * zoomChange).coerceIn(15f, 300f)
                                }
                                if (rotationChange != 0f) {
                                    val nextRot = layerRotDeg + rotationChange
                                    layerRotDeg = ((nextRot + 180f) % 360f + 360f) % 360f - 180f
                                }
                            }
                        }
                ) {
                    val boxWDp = with(density) { boxW.toDp() }
                    val boxHDp = with(density) { boxH.toDp() }
                    Box(
                        modifier = Modifier
                            .offset {
                                androidx.compose.ui.unit.IntOffset(
                                    (sCx - boxW * 0.5f).roundToInt(),
                                    (sCy - boxH * 0.5f).roundToInt()
                                )
                            }
                            .size(boxWDp, boxHDp)
                            .graphicsLayer { rotationZ = layerRotDeg }
                            .border(2.dp, Color(0xFF4F9CFF), RoundedCornerShape(4.dp))
                    ) {
                        prev.second?.let { bmp ->
                            Image(
                                bitmap = bmp,
                                contentDescription = "Aktif katman",
                                contentScale = ContentScale.FillBounds,
                                alpha = 1.0f,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }

        if (activePanel == BottomPanel.TEXT_EDIT) {
            val docW = (state.docInfo?.width?.toInt() ?: 2048).toFloat()
            val docH = (state.docInfo?.height?.toInt() ?: 2048).toFloat()
            val viewRef = canvasViewRef
            val tick = cameraTick
            if (viewRef != null && tick >= 0) {
                val (sx, sy) = viewRef.docToScreen(docW * textNormX, docH * textNormY)
                val density = androidx.compose.ui.platform.LocalDensity.current
                val previewSp = ((textSizePx * viewRef.getCurrentZoom()) / density.fontScale / density.density).coerceIn(10f, 72f)
                val previewColor = remember(textColorHex) {
                    runCatching { Color(AndroidColor.parseColor(textColorHex)) }.getOrDefault(Color.White)
                }
                Box(
                    modifier = Modifier
                        .onSizeChanged { textBoxSize = it }
                        .offset {
                            val halfW = if (textBoxSize.width > 0) textBoxSize.width / 2f else 90f
                            val halfH = if (textBoxSize.height > 0) textBoxSize.height / 2f else 24f
                            val maxX = (viewRef.width - textBoxSize.width - 12).coerceAtLeast(12)
                            val clampedX = (sx - halfW).roundToInt().coerceIn(12, maxX)
                            val clampedY = (sy - halfH).roundToInt().coerceAtLeast(12)
                            androidx.compose.ui.unit.IntOffset(clampedX, clampedY)
                        }
                        .widthIn(max = 300.dp)
                        .border(1.5.dp, Color(0xAA4F9CFF), RoundedCornerShape(8.dp))
                        .background(Color(0x44111217), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = textDraft.ifBlank { "Metin..." },
                        color = previewColor,
                        fontSize = previewSp.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 3
                    )
                }
            }
        }

        // v0.9.5 Interactive On-Canvas Gradient Start/End Drag Overlay (60 FPS Live Proxy)
        if (activePanel == BottomPanel.GRADIENT_EDIT) {
            val docW = (state.docInfo?.width?.toInt() ?: 2048).toFloat()
            val docH = (state.docInfo?.height?.toInt() ?: 2048).toFloat()
            val viewRef = canvasViewRef
            val tick = cameraTick
            if (viewRef != null && tick >= 0) {
                val (ax, ay) = viewRef.docToScreen(docW * gradFromNormX, docH * gradFromNormY)
                val (bx, by) = viewRef.docToScreen(docW * gradToNormX, docH * gradToNormY)
                val startCol = remember(gradStartHex) { runCatching { Color(AndroidColor.parseColor(gradStartHex)) }.getOrDefault(Color(0xFF4F9CFF)) }
                val endCol = remember(gradEndHex) { runCatching { Color(AndroidColor.parseColor(gradEndHex)) }.getOrDefault(Color(0xFFFF4D4F)) }
                androidx.compose.foundation.Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(docW, docH, gradStyle, gradStartHex, gradEndHex, gradOpacity) {
                            detectDragGestures(
                                onDragStart = { pos ->
                                    val dA = kotlin.math.hypot(pos.x - ax, pos.y - ay)
                                    val dB = kotlin.math.hypot(pos.x - bx, pos.y - by)
                                    val (dx, dy) = viewRef.screenToDoc(pos.x, pos.y)
                                    if (dA > 70f && dB > 70f) {
                                        gradFromNormX = (dx / docW).coerceIn(0f, 1f)
                                        gradFromNormY = (dy / docH).coerceIn(0f, 1f)
                                        gradToNormX = gradFromNormX
                                        gradToNormY = gradFromNormY
                                    }
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val p = change.position
                                    val (dx, dy) = viewRef.screenToDoc(p.x, p.y)
                                    val nx = (dx / docW).coerceIn(0f, 1f)
                                    val ny = (dy / docH).coerceIn(0f, 1f)
                                    val (curAx, curAy) = viewRef.docToScreen(docW * gradFromNormX, docH * gradFromNormY)
                                    val (curBx, curBy) = viewRef.docToScreen(docW * gradToNormX, docH * gradToNormY)
                                    if (kotlin.math.hypot(p.x - curAx, p.y - curAy) < kotlin.math.hypot(p.x - curBx, p.y - curBy)) {
                                        gradFromNormX = nx
                                        gradFromNormY = ny
                                    } else {
                                        gradToNormX = nx
                                        gradToNormY = ny
                                    }
                                    vm.updateProxyGradient(
                                        docW * gradFromNormX, docH * gradFromNormY,
                                        docW * gradToNormX, docH * gradToNormY,
                                        gradStyle, gradStartHex, gradEndHex, gradOpacity
                                    )
                                }
                            )
                        }
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
        if (activePanel == BottomPanel.SHAPE_EDIT) {
            val docW = (state.docInfo?.width?.toInt() ?: 2048).toFloat()
            val docH = (state.docInfo?.height?.toInt() ?: 2048).toFloat()
            val viewRef = canvasViewRef
            val tick = cameraTick
            if (viewRef != null && tick >= 0) {
                val (sx0, sy0) = viewRef.docToScreen(docW * shapeX0Norm, docH * shapeY0Norm)
                val (sx1, sy1) = viewRef.docToScreen(docW * shapeX1Norm, docH * shapeY1Norm)
                androidx.compose.foundation.Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(docW, docH, shapeKind, shapeFillHex, shapeStrokeHex, shapeStrokeWidth, shapeCornerRadius, shapeSides) {
                            var dragMode = 0 // 0=new, 1=handle0, 2=handle1, 3=move
                            var lastDocX = 0f
                            var lastDocY = 0f
                            detectDragGestures(
                                onDragStart = { pos ->
                                    val (c0x, c0y) = viewRef.docToScreen(docW * shapeX0Norm, docH * shapeY0Norm)
                                    val (c1x, c1y) = viewRef.docToScreen(docW * shapeX1Norm, docH * shapeY1Norm)
                                    val d0 = kotlin.math.hypot(pos.x - c0x, pos.y - c0y)
                                    val d1 = kotlin.math.hypot(pos.x - c1x, pos.y - c1y)
                                    val minX = min(c0x, c1x) - 20f
                                    val maxX = kotlin.math.max(c0x, c1x) + 20f
                                    val minY = min(c0y, c1y) - 20f
                                    val maxY = kotlin.math.max(c0y, c1y) + 20f
                                    val (dx, dy) = viewRef.screenToDoc(pos.x, pos.y)
                                    lastDocX = dx
                                    lastDocY = dy
                                    dragMode = when {
                                        d0 <= 68f -> 1
                                        d1 <= 68f -> 2
                                        pos.x in minX..maxX && pos.y in minY..maxY -> 3
                                        else -> {
                                            shapeX0Norm = (dx / docW).coerceIn(0f, 1f)
                                            shapeY0Norm = (dy / docH).coerceIn(0f, 1f)
                                            shapeX1Norm = (shapeX0Norm + 0.04f).coerceAtMost(1f)
                                            shapeY1Norm = (shapeY0Norm + 0.04f).coerceAtMost(1f)
                                            0
                                        }
                                    }
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val (dx, dy) = viewRef.screenToDoc(change.position.x, change.position.y)
                                    when (dragMode) {
                                        1 -> {
                                            shapeX0Norm = (dx / docW).coerceIn(0f, 1f)
                                            shapeY0Norm = (dy / docH).coerceIn(0f, 1f)
                                        }
                                        2, 0 -> {
                                            shapeX1Norm = (dx / docW).coerceIn(0f, 1f)
                                            shapeY1Norm = (dy / docH).coerceIn(0f, 1f)
                                        }
                                        3 -> {
                                            val dnx = (dx - lastDocX) / docW
                                            val dny = (dy - lastDocY) / docH
                                            lastDocX = dx
                                            lastDocY = dy
                                            shapeX0Norm = (shapeX0Norm + dnx).coerceIn(-0.2f, 1.2f)
                                            shapeY0Norm = (shapeY0Norm + dny).coerceIn(-0.2f, 1.2f)
                                            shapeX1Norm = (shapeX1Norm + dnx).coerceIn(-0.2f, 1.2f)
                                            shapeY1Norm = (shapeY1Norm + dny).coerceIn(-0.2f, 1.2f)
                                        }
                                    }
                                    vm.updateProxyShape(
                                        shapeKind,
                                        docW * shapeX0Norm, docH * shapeY0Norm,
                                        docW * shapeX1Norm, docH * shapeY1Norm,
                                        shapeFillHex, shapeStrokeHex, shapeStrokeWidth, shapeCornerRadius, shapeSides
                                    )
                                }
                            )
                        }
                ) {
                    val lx = min(sx0, sx1)
                    val ty = min(sy0, sy1)
                    val rw = abs(sx1 - sx0).coerceAtLeast(4f)
                    val rh = abs(sy1 - sy0).coerceAtLeast(4f)
                    if (shapeKind == "line") {
                        drawLine(Color(0xCC4F9CFF), Offset(sx0, sy0), Offset(sx1, sy1), strokeWidth = 2.5f)
                    } else {
                        drawRect(
                            color = Color(0xCC4F9CFF),
                            topLeft = Offset(lx, ty),
                            size = androidx.compose.ui.geometry.Size(rw, rh),
                            style = Stroke(width = 2f)
                        )
                    }
                    drawCircle(Color.White, radius = 10f, center = Offset(sx0, sy0))
                    drawCircle(Color(0xFF4F9CFF), radius = 6f, center = Offset(sx0, sy0))
                    drawCircle(Color.White, radius = 10f, center = Offset(sx1, sy1))
                    drawCircle(Color(0xFF4F9CFF), radius = 6f, center = Offset(sx1, sy1))
                }
            }
        }

        // v0.9.8 Interactive 60 FPS On-Canvas Liquify Drag Overlay
        if (activePanel == BottomPanel.LIQUIFY_EDIT) {
            val viewRef = canvasViewRef
            if (viewRef != null) {
                androidx.compose.foundation.Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(liquifyTool, liquifySize, liquifyPressure) {
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
                                        liquifyCursorScreen = null
                                        break
                                    }
                                    if (pressed.size >= 2) {
                                        if (singleStrokeActive) {
                                            vm.onLiquifyEnd()
                                            singleStrokeActive = false
                                        }
                                        liquifyCursorScreen = null
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
                                            if (moved > 3f || liquifyTool == "bloat" || liquifyTool == "pucker" || liquifyTool == "twirlCw") {
                                                singleStrokeActive = true
                                                liquifyCursorScreen = pos
                                                val (dx0, dy0) = viewRef.screenToDoc(downPos.x, downPos.y)
                                                vm.onLiquifyStart(liquifyTool, liquifySize, liquifyPressure, 55f, dx0, dy0)
                                                val (dx, dy) = viewRef.screenToDoc(pos.x, pos.y)
                                                vm.onLiquifyMove(dx, dy)
                                            }
                                        } else {
                                            liquifyCursorScreen = pos
                                            val (dx, dy) = viewRef.screenToDoc(pos.x, pos.y)
                                            vm.onLiquifyMove(dx, dy)
                                        }
                                    }
                                }
                                }
                            }
                        }
                ) {
                    val cur = liquifyCursorScreen
                    if (cur != null) {
                        val rPx = (liquifySize * 0.5f * viewRef.getCurrentZoom()).coerceIn(10f, 360f)
                        drawCircle(Color.White.copy(alpha = 0.85f), radius = rPx, center = cur, style = Stroke(width = 2.2f))
                        drawCircle(Color(0x884F9CFF), radius = (rPx - 2f).coerceAtLeast(4f), center = cur, style = Stroke(width = 1.2f))
                    }
                }
            }
        }

        // v0.9.9.5 Floating Touch-Point Magnifier / Loupe (Fine Brush, Eyedropper, Clone Stamp, Perspective Crop)
        val lPos = loupeScreenPos
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
        if (showExitSaveDialog) {
            AlertDialog(
                onDismissRequest = { showExitSaveDialog = false },
                containerColor = Color(0xFF232530),
                titleContentColor = Color.White,
                textContentColor = Color(0xFFD8DCE8),
                title = { Text("Fotoğrafı Kaydet", fontWeight = FontWeight.SemiBold) },
                text = { Text("Bu fotoğrafı kaydetmek istiyor musun? Evet dersen Kaydedilen Fotoğraflar bölümüne eklenir.") },
                confirmButton = {
                    Button(
                        onClick = {
                            showExitSaveDialog = false
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
                            showExitSaveDialog = false
                            clearGpuPreview()
                            onGoHome()
                        }
                    ) {
                        Text("Hayır", color = Color.White)
                    }
                }
            )
        }

        // Slim Top Bar
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
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
                        showExitSaveDialog = true
                    }
                )

                Box {
                    TopBarButton(
                        icon = Icons.Default.AddPhotoAlternate,
                        label = "Aç / Ekle",
                        onClick = { showOpenMenu = true }
                    )
                    DropdownMenu(
                        expanded = showOpenMenu,
                        onDismissRequest = { showOpenMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Fotoğraf Aç (Galeri / Uygulamalar)") },
                            onClick = {
                                showOpenMenu = false
                                openAsLayer = false
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
                                showOpenMenu = false
                                openAsLayer = true
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
                                showOpenMenu = false
                                openAsLayer = false
                                openDocLauncher.launch(arrayOf("*/*"))
                            }
                        )
                    }
                }

                Box {
                    TopBarButton(
                        icon = Icons.Default.Save,
                        label = "Kaydet",
                        onClick = { showExportMenu = true }
                    )
                    DropdownMenu(
                        expanded = showExportMenu,
                        onDismissRequest = { showExportMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Cihaz Galerisine Kaydet (PNG)", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary) },
                            onClick = {
                                showExportMenu = false
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
                                    showExportMenu = false
                                    pendingExportFormat = ext
                                    exportDocLauncher.launch("stonebridgecraft.$ext")
                                }
                            )
                        }
                    }
                }

                TopBarButton(
                    icon = Icons.AutoMirrored.Filled.Undo,
                    label = "Geri",
                    enabled = if (activePanel == BottomPanel.LIQUIFY_EDIT) state.liquifyCanUndo else (state.docInfo?.canUndo == true),
                    onClick = {
                        if (activePanel == BottomPanel.LIQUIFY_EDIT) {
                            vm.liquifyUndo()
                        } else {
                            dismissLivePanelIfActive()
                            vm.undo()
                        }
                    }
                )

                TopBarButton(
                    icon = Icons.AutoMirrored.Filled.Redo,
                    label = "İleri",
                    enabled = if (activePanel == BottomPanel.LIQUIFY_EDIT) state.liquifyCanRedo else (state.docInfo?.canRedo == true),
                    onClick = {
                        if (activePanel == BottomPanel.LIQUIFY_EDIT) {
                            vm.liquifyRedo()
                        } else {
                            dismissLivePanelIfActive()
                            vm.redo()
                        }
                    }
                )

                TopBarButton(
                    icon = Icons.Default.Compare,
                    label = "Önce",
                    selected = state.compareOriginal,
                    onClick = { vm.toggleCompareOriginal() }
                )

                TopBarButton(
                    icon = Icons.Default.CenterFocusStrong,
                    label = "Ortala",
                    onClick = { fitToSafeArea(pendingQuarterTurns, activePanel) }
                )

                TopBarButton(
                    icon = Icons.Default.Layers,
                    label = "Katman",
                    selected = showLayers,
                    onClick = { showLayers = !showLayers }
                )
            }
        }

        LaunchedEffect(showLayers, state.layers, state.docInfo) {
            if (showLayers) {
                vm.refreshLayerThumbnails()
            }
        }

        // Right Floating Layers Drawer
        if (showLayers) {
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
                                showLayers = false
                                vm.setTool(ActiveTool.NONE)
                                layerDx = 0f; layerDy = 0f; layerScalePct = 100f; layerRotDeg = 0f
                                vm.loadActiveLayerPreview()
                                activePanel = BottomPanel.LAYER_TRANSFORM
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Default.OpenWith, contentDescription = "Taşı", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = {
                                showLayers = false
                                vm.setTool(ActiveTool.NONE)
                                showFxFineTune = false
                                vm.beginLayerProxyPreview()
                                vm.updateProxyLayerEffect(fxKind, fxColorHex, fxSizeVal, fxOpacityVal, fxDistanceVal)
                                activePanel = BottomPanel.LAYER_FX_EDIT
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
                                onClick = { showBlendMenu = true },
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
                                expanded = showBlendMenu,
                                onDismissRequest = { showBlendMenu = false }
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
                                            showBlendMenu = false
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
                                Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                                    Text(layer.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White)
                                    Text(
                                        "${layer.blendMode} · ${(layer.opacity * 100).toInt()}%",
                                        fontSize = 10.sp,
                                        color = Color(0xFFB8BCC8)
                                    )
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
                    .padding(bottom = if (activePanel == BottomPanel.DOCK) 114.dp else 210.dp)
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

        // GROUPED 5-CATEGORY BOTTOM CIRCULAR DOCK & COMPACT SUB-MENUS
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            tonalElevation = 10.dp
        ) {
            val activeColor = remember(state.colorHex) {
                runCatching { Color(AndroidColor.parseColor(state.colorHex)) }
                    .getOrDefault(Color.Red)
            }

            when (activePanel) {
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
                                activePanel = BottomPanel.QUICK_ACTIONS_MENU
                            }
                        )
                        CircularDockButton(
                            icon = Icons.Default.Brush,
                            label = "Çizim",
                            selected = state.activeTool != ActiveTool.NONE && !isSelectTool,
                            accentDot = if (state.activeTool == ActiveTool.BRUSH || state.activeTool == ActiveTool.EYEDROPPER) activeColor else null,
                            onClick = { activePanel = BottomPanel.DRAW_MENU }
                        )
                        CircularDockButton(
                            icon = Icons.Default.SelectAll,
                            label = "Seçim",
                            selected = state.selectionBounds != null || isSelectTool,
                            onClick = {
                                if (!isSelectTool) {
                                    vm.setTool(ActiveTool.SELECT_QUICK)
                                }
                                activePanel = BottomPanel.SELECT_MENU
                            }
                        )
                        CircularDockButton(
                            icon = Icons.Default.Title,
                            label = "Metin",
                            onClick = {
                                vm.setTool(ActiveTool.NONE)
                                val docW = (state.docInfo?.width?.toInt() ?: 2048).toFloat()
                                textSizePx = (docW * 0.055f).coerceIn(24f, 180f)
                                textNormX = 0.50f
                                textNormY = 0.50f
                                activePanel = BottomPanel.TEXT_EDIT
                            }
                        )
                        CircularDockButton(
                            icon = Icons.Default.Gradient,
                            label = "Degrade",
                            onClick = {
                                vm.setTool(ActiveTool.NONE)
                                val docW = (state.docInfo?.width?.toInt() ?: 2048).toFloat()
                                val docH = (state.docInfo?.height?.toInt() ?: 2048).toFloat()
                                gradFromNormX = 0.20f; gradFromNormY = 0.20f
                                gradToNormX = 0.80f; gradToNormY = 0.80f
                                vm.beginProxyPreview()
                                vm.updateProxyGradient(
                                    docW * gradFromNormX, docH * gradFromNormY,
                                    docW * gradToNormX, docH * gradToNormY,
                                    gradStyle, gradStartHex, gradEndHex, gradOpacity
                                )
                                activePanel = BottomPanel.GRADIENT_EDIT
                            }
                        )
                        CircularDockButton(
                            icon = Icons.Default.Category,
                            label = "Şekil",
                            onClick = {
                                vm.setTool(ActiveTool.NONE)
                                val docW = (state.docInfo?.width?.toInt() ?: 2048).toFloat()
                                val docH = (state.docInfo?.height?.toInt() ?: 2048).toFloat()
                                shapeX0Norm = 0.25f; shapeY0Norm = 0.25f
                                shapeX1Norm = 0.75f; shapeY1Norm = 0.75f
                                showShapeFineTune = false
                                vm.beginProxyPreview()
                                vm.updateProxyShape(
                                    shapeKind,
                                    docW * shapeX0Norm, docH * shapeY0Norm,
                                    docW * shapeX1Norm, docH * shapeY1Norm,
                                    shapeFillHex, shapeStrokeHex, shapeStrokeWidth, shapeCornerRadius, shapeSides
                                )
                                activePanel = BottomPanel.SHAPE_EDIT
                            }
                        )
                        CircularDockButton(
                            icon = Icons.Default.Tune,
                            label = "Ayarlar",
                            onClick = {
                                vm.setTool(ActiveTool.NONE)
                                activePanel = BottomPanel.ADJUST_MENU
                            }
                        )
                        CircularDockButton(
                            icon = Icons.Default.Crop,
                            label = "Kırp",
                            onClick = {
                                vm.setTool(ActiveTool.NONE)
                                pendingQuarterTurns = 0
                                pendingFlipH = false
                                pendingFlipV = false
                                activePanel = BottomPanel.CROP_MENU
                            }
                        )
                        CircularDockButton(
                            icon = Icons.Default.BlurOn,
                            label = "Filtre",
                            onClick = {
                                vm.setTool(ActiveTool.NONE)
                                activePanel = BottomPanel.FILTER_MENU
                            }
                        )
                        CircularDockButton(
                            icon = Icons.Default.AutoAwesome,
                            label = "Efekt",
                            selected = state.bwLayerId != null || state.invertLayerId != null,
                            onClick = {
                                vm.setTool(ActiveTool.NONE)
                                activePanel = BottomPanel.EFFECT_MENU
                            }
                        )
                    }
                }

                BottomPanel.DRAW_MENU -> {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val symLabel = when (state.symmetryMode) {
                            "vertical" -> "Ayna: Dikey"
                            "horizontal" -> "Ayna: Yatay"
                            "dual" -> "Ayna: 4'lü"
                            else -> "Ayna"
                        }
                        BottomEditorHeader(
                            title = "Çizim Araçları",
                            subtitle = when (state.activeTool) {
                                ActiveTool.BRUSH -> if (state.rulerEnabled) "Cetvel açık · düz çizgi çek" else "Fırça aktif · iki parmakla kaydır/yakınlaştır"
                                ActiveTool.ERASER -> if (state.rulerEnabled) "Silgi + Cetvel açık" else "Silgi aktif"
                                ActiveTool.EYEDROPPER -> "Fotoğrafa dokunarak renk al (${state.colorHex})"
                                ActiveTool.BUCKET -> "Boya Kovası · doldurmak istediğin alana dokun"
                                ActiveTool.SPOT_HEAL -> "Leke Giderme · kusurun üzerinden parmağınla geç"
                                ActiveTool.SMUDGE -> "Dağıtma · dokuyu parmağınla yumuşakça yay"
                                ActiveTool.DODGE -> "Aydınlat · dokunduğun bölgeyi açar"
                                ActiveTool.BURN -> "Koyulaştır · dokunduğun bölgeyi gölgeler"
                                ActiveTool.CLONE_STAMP -> if (state.pickingCloneSource) "Klon kaynağı seç: fotoğrafa dokun" else "Klonla · boyamak istediğin yere sürükle"
                                ActiveTool.SELECT_RECT -> "Dikdörtgen/Elips alan seç"
                                ActiveTool.SELECT_WAND -> "Sihirli Değnek · renge dokunarak seç"
                                ActiveTool.SELECT_QUICK -> "Akıllı Fırça · nesne üzerinden geç"
                                ActiveTool.SELECT_BRUSH_ADD -> "Elle Ekle (+) · parmağınla seçime boya"
                                ActiveTool.SELECT_BRUSH_SUB -> "Elle Sil (-) · yanlış seçilen yerleri sil"
                                ActiveTool.NONE -> "Çizim veya Rötuş için bir araç seç"
                            },
                            onClose = { activePanel = BottomPanel.DOCK }
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(drawScrollState),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CircularDockButton(
                                icon = Icons.Default.Brush,
                                label = "Fırça",
                                selected = state.activeTool == ActiveTool.BRUSH,
                                accentDot = activeColor,
                                onClick = {
                                    vm.setTool(if (state.activeTool == ActiveTool.BRUSH) ActiveTool.NONE else ActiveTool.BRUSH)
                                }
                            )
                            CircularDockButton(
                                icon = Icons.Default.AutoFixNormal,
                                label = "Silgi",
                                selected = state.activeTool == ActiveTool.ERASER,
                                onClick = {
                                    vm.setTool(if (state.activeTool == ActiveTool.ERASER) ActiveTool.NONE else ActiveTool.ERASER)
                                }
                            )
                            CircularDockButton(
                                icon = Icons.Default.FormatColorFill,
                                label = "Kova",
                                selected = state.activeTool == ActiveTool.BUCKET,
                                accentDot = activeColor,
                                onClick = {
                                    vm.setTool(ActiveTool.BUCKET)
                                    showHsvPicker = false
                                }
                            )
                            CircularDockButton(
                                icon = Icons.Default.Colorize,
                                label = "Damlalık",
                                selected = state.activeTool == ActiveTool.EYEDROPPER,
                                accentDot = activeColor,
                                onClick = {
                                    vm.setTool(if (state.activeTool == ActiveTool.EYEDROPPER) ActiveTool.BRUSH else ActiveTool.EYEDROPPER)
                                }
                            )
                            CircularDockButton(
                                icon = Icons.Default.Straighten,
                                label = if (state.rulerEnabled) "Cetvel ✓" else "Cetvel",
                                selected = state.rulerEnabled,
                                onClick = { vm.toggleRuler() }
                            )
                            CircularDockButton(
                                icon = Icons.Default.Flip,
                                label = symLabel,
                                selected = state.symmetryMode != "off",
                                onClick = { vm.cycleSymmetry() }
                            )
                            CircularDockButton(
                                icon = Icons.Default.Healing,
                                label = "Leke",
                                selected = state.activeTool == ActiveTool.SPOT_HEAL,
                                onClick = {
                                    vm.setTool(if (state.activeTool == ActiveTool.SPOT_HEAL) ActiveTool.NONE else ActiveTool.SPOT_HEAL)
                                }
                            )
                            CircularDockButton(
                                icon = Icons.Default.Gesture,
                                label = "Dağıt",
                                selected = state.activeTool == ActiveTool.SMUDGE,
                                onClick = {
                                    vm.setTool(if (state.activeTool == ActiveTool.SMUDGE) ActiveTool.NONE else ActiveTool.SMUDGE)
                                }
                            )
                            CircularDockButton(
                                icon = Icons.Default.WbSunny,
                                label = "Aydınlat",
                                selected = state.activeTool == ActiveTool.DODGE,
                                onClick = {
                                    vm.setTool(if (state.activeTool == ActiveTool.DODGE) ActiveTool.NONE else ActiveTool.DODGE)
                                }
                            )
                            CircularDockButton(
                                icon = Icons.Default.NightsStay,
                                label = "Koyulaştır",
                                selected = state.activeTool == ActiveTool.BURN,
                                onClick = {
                                    vm.setTool(if (state.activeTool == ActiveTool.BURN) ActiveTool.NONE else ActiveTool.BURN)
                                }
                            )
                            CircularDockButton(
                                icon = Icons.Default.ContentCopy,
                                label = "Klonla",
                                selected = state.activeTool == ActiveTool.CLONE_STAMP,
                                onClick = {
                                    vm.setTool(if (state.activeTool == ActiveTool.CLONE_STAMP) ActiveTool.NONE else ActiveTool.CLONE_STAMP)
                                }
                            )
                            CircularDockButton(
                                icon = Icons.Default.Block,
                                label = "Bırak",
                                selected = state.activeTool == ActiveTool.NONE,
                                onClick = { vm.setTool(ActiveTool.NONE) }
                            )
                        }

                        if (state.activeTool == ActiveTool.BRUSH) {
                            val palette = listOf(
                                "#FF4D4F", "#FFA940", "#FFEC3D", "#52C41A",
                                "#13C2C2", "#1677FF", "#722ED1", "#FFFFFF", "#000000"
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                palette.forEach { hex ->
                                    val c = Color(AndroidColor.parseColor(hex))
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(c)
                                            .border(
                                                width = if (state.colorHex.equals(hex, true)) 2.5.dp else 1.dp,
                                                color = if (state.colorHex.equals(hex, true)) MaterialTheme.colorScheme.primary else Color.Gray,
                                                shape = CircleShape
                                            )
                                            .clickable { vm.updateBrush(colorHex = hex) }
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.sweepGradient(
                                                listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
                                            )
                                        )
                                        .border(
                                            width = if (showHsvPicker) 2.5.dp else 1.dp,
                                            color = Color.White,
                                            shape = CircleShape
                                        )
                                        .clickable { showHsvPicker = !showHsvPicker }
                                )
                            }

                            if (showHsvPicker) {
                                CompactSliderRow("Ton", "${pickerH.roundToInt()}°", pickerH, 0f..360f, defaultValue = 0f) {
                                    pickerH = it
                                    vm.updateBrush(colorHex = hsvToHex(pickerH, pickerS, pickerV))
                                }
                                CompactSliderRow("Doygun", "%${(pickerS * 100).roundToInt()}", pickerS, 0f..1f, defaultValue = 1f) {
                                    pickerS = it
                                    vm.updateBrush(colorHex = hsvToHex(pickerH, pickerS, pickerV))
                                }
                                CompactSliderRow("Parlak", "%${(pickerV * 100).roundToInt()}", pickerV, 0f..1f, defaultValue = 1f) {
                                    pickerV = it
                                    vm.updateBrush(colorHex = hsvToHex(pickerH, pickerS, pickerV))
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        BrushSizeSliderRow("Boyut", state.brushSize, minPx = 1f, maxPx = 250f, defaultPx = 28f) {
                                            vm.updateBrush(size = it)
                                            brushSizePreviewTick++
                                        }
                                    }
                                    FilterChip(
                                        selected = showBrushFineTune,
                                        onClick = { showBrushFineTune = !showBrushFineTune },
                                        label = { Text(if (showBrushFineTune) "İnce ▲" else "İnce ▼", fontSize = 10.sp) }
                                    )
                                }
                                if (showBrushFineTune) {
                                    CompactSliderRow("Opaklık", "${(state.brushOpacity * 100).toInt()}%", state.brushOpacity, 0.05f..1f, defaultValue = 1f) {
                                        vm.updateBrush(opacity = it)
                                    }
                                    CompactSliderRow("Sertlik", "${(state.brushHardness * 100).toInt()}%", state.brushHardness, 0f..1f, defaultValue = 0.85f) {
                                        vm.updateBrush(hardness = it)
                                    }
                                    CompactSliderRow("Akış", "${(state.brushFlow * 100).toInt()}%", state.brushFlow, 0.05f..1f, defaultValue = 1f) {
                                        vm.updateBrush(flow = it)
                                    }
                                }
                            }
                        } else if (state.activeTool == ActiveTool.BUCKET) {
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf("#FF4D4F", "#4F9CFF", "#52C41A", "#FAAD14", "#A855F7", "#FFFFFF", "#000000").forEach { hex ->
                                    val c = remember(hex) { Color(AndroidColor.parseColor(hex)) }
                                    Box(
                                        modifier = Modifier.size(26.dp).clip(CircleShape).background(c)
                                            .border(if (state.colorHex.equals(hex, true)) 2.dp else 1.dp, if (state.colorHex.equals(hex, true)) Color.White else Color(0xFF444858), CircleShape)
                                            .clickable { vm.updateBrush(colorHex = hex) }
                                    )
                                }
                                FilterChip(
                                    selected = bucketContiguous,
                                    onClick = { bucketContiguous = !bucketContiguous },
                                    label = { Text(if (bucketContiguous) "Bitişik" else "Tümü", fontSize = 10.sp) }
                                )
                            }
                            CompactSliderRow("Tolerans", "${bucketTolerance.roundToInt()}", bucketTolerance, 0f..200f, defaultValue = 36f) {
                                bucketTolerance = it
                            }
                        } else if (state.activeTool == ActiveTool.ERASER) {
                            BrushSizeSliderRow("Boyut", state.brushSize, minPx = 1f, maxPx = 250f, defaultPx = 28f) {
                                vm.updateBrush(size = it)
                                brushSizePreviewTick++
                            }
                            CompactSliderRow("Sertlik", "${(state.brushHardness * 100).toInt()}%", state.brushHardness, 0f..1f, defaultValue = 0.85f) {
                                vm.updateBrush(hardness = it)
                            }
                        } else if (
                            state.activeTool == ActiveTool.SPOT_HEAL ||
                            state.activeTool == ActiveTool.SMUDGE ||
                            state.activeTool == ActiveTool.DODGE ||
                            state.activeTool == ActiveTool.BURN
                        ) {
                            BrushSizeSliderRow("Boyut", state.brushSize, minPx = 1f, maxPx = 250f, defaultPx = 48f) {
                                vm.updateBrush(size = it)
                                brushSizePreviewTick++
                            }
                            if (state.activeTool != ActiveTool.SPOT_HEAL) {
                                CompactSliderRow("Güç", "%${state.retouchStrength.roundToInt()}", state.retouchStrength, 10f..100f, defaultValue = 55f) {
                                    vm.updateRetouchStrength(it)
                                }
                            }
                        } else if (state.activeTool == ActiveTool.CLONE_STAMP) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { vm.requestPickCloneSource() },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        if (state.cloneSourceDoc == null) "Kaynak Noktası Seç" else "Kaynağı Değiştir",
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            BrushSizeSliderRow("Boyut", state.brushSize, minPx = 1f, maxPx = 250f, defaultPx = 48f) {
                                vm.updateBrush(size = it)
                                brushSizePreviewTick++
                            }
                            CompactSliderRow("Opaklık", "${(state.brushOpacity * 100).toInt()}%", state.brushOpacity, 0.05f..1f, defaultValue = 1f) {
                                vm.updateBrush(opacity = it)
                            }
                        }
                    }
                }

                BottomPanel.SELECT_MENU -> {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BottomEditorHeader(
                            title = "Seçim Araçları",
                            subtitle = when (state.activeTool) {
                                ActiveTool.SELECT_QUICK -> if (state.selectSubtractMode) "Akıllı Fırça (-) · kenar algılayarak çıkar" else "Akıllı Fırça (+) · nesne üzerinden parmağınla geç"
                                ActiveTool.SELECT_BRUSH_ADD -> "Elle Ekle (+) · parmağınla boyayarak seçime ekle"
                                ActiveTool.SELECT_BRUSH_SUB -> "Elle Sil (-) · yanlış seçilen yerleri parmağınla sil"
                                ActiveTool.SELECT_WAND -> if (state.wandSmartMode) "Akıllı Değnek · seçmek istediğin nesneye dokun" else "Renk Değneği · dokun ve Tolerans ile ayarla (${state.wandTolerance.roundToInt()})"
                                else -> "Parmağınla sürükleyerek alan seç"
                            },
                            onClose = {
                                vm.setTool(ActiveTool.NONE)
                                activePanel = BottomPanel.DOCK
                            }
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(selectScrollState),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CircularDockButton(
                                icon = Icons.Default.AutoAwesome,
                                label = "Konu Seç",
                                onClick = { vm.selectSubject() }
                            )
                            CircularDockButton(
                                icon = Icons.Default.Gesture,
                                label = "Akıllı",
                                selected = state.activeTool == ActiveTool.SELECT_QUICK,
                                onClick = { vm.setTool(ActiveTool.SELECT_QUICK) }
                            )
                            CircularDockButton(
                                icon = Icons.Default.AddCircleOutline,
                                label = "Elle (+)",
                                selected = state.activeTool == ActiveTool.SELECT_BRUSH_ADD,
                                onClick = { vm.setTool(ActiveTool.SELECT_BRUSH_ADD) }
                            )
                            CircularDockButton(
                                icon = Icons.Default.RemoveCircleOutline,
                                label = "Elle (-)",
                                selected = state.activeTool == ActiveTool.SELECT_BRUSH_SUB,
                                onClick = { vm.setTool(ActiveTool.SELECT_BRUSH_SUB) }
                            )
                            CircularDockButton(
                                icon = Icons.Default.AutoFixHigh,
                                label = "Değnek",
                                selected = state.activeTool == ActiveTool.SELECT_WAND,
                                onClick = { vm.setTool(ActiveTool.SELECT_WAND) }
                            )
                            CircularDockButton(
                                icon = Icons.Default.CropFree,
                                label = if (state.selectEllipse) "Elips" else "Kutu",
                                selected = state.activeTool == ActiveTool.SELECT_RECT,
                                onClick = {
                                    if (state.activeTool == ActiveTool.SELECT_RECT) {
                                        vm.toggleSelectEllipse()
                                    } else {
                                        vm.setTool(ActiveTool.SELECT_RECT)
                                    }
                                }
                            )
                            if (state.selectionBounds != null) {
                                CircularDockButton(
                                    icon = Icons.Default.ContentCopy,
                                    label = "Katmana Al",
                                    onClick = { vm.layerViaCopy() }
                                )
                                CircularDockButton(
                                    icon = Icons.Default.BlurOn,
                                    label = "Yumuşat",
                                    onClick = { vm.featherSelection(6f) }
                                )
                                CircularDockButton(
                                    icon = Icons.Default.CleaningServices,
                                    label = "Nesne Sil",
                                    onClick = { vm.contentAwareRemove() }
                                )
                                CircularDockButton(
                                    icon = Icons.Default.SwapHoriz,
                                    label = "Tersine",
                                    onClick = { vm.selectInverse() }
                                )
                                CircularDockButton(
                                    icon = Icons.Default.Layers,
                                    label = "Maskele",
                                    onClick = { vm.revealSelectionMask() }
                                )
                                CircularDockButton(
                                    icon = Icons.Default.FormatColorFill,
                                    label = "Doldur",
                                    onClick = { vm.fillSelection() }
                                )
                                CircularDockButton(
                                    icon = Icons.Default.DeleteOutline,
                                    label = "Sil",
                                    onClick = { vm.clearSelection() }
                                )
                                CircularDockButton(
                                    icon = Icons.Default.Close,
                                    label = "Kaldır",
                                    onClick = { vm.deselect() }
                                )
                            }
                        }
                        if (state.activeTool == ActiveTool.SELECT_WAND || state.activeTool == ActiveTool.SELECT_RECT || state.activeTool == ActiveTool.SELECT_QUICK) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (state.activeTool != ActiveTool.SELECT_QUICK) {
                                    FilterChip(
                                        selected = state.selectAddMode,
                                        onClick = { vm.toggleSelectAddMode() },
                                        label = { Text(if (state.selectAddMode) "Ekle (+) ✓" else "Ekle (+)", fontSize = 11.sp) }
                                    )
                                }
                                FilterChip(
                                    selected = state.selectSubtractMode,
                                    onClick = { vm.toggleSelectSubtractMode() },
                                    label = { Text(if (state.selectSubtractMode) "Çıkar (-) ✓" else "Çıkar (-)", fontSize = 11.sp) }
                                )
                                if (state.activeTool == ActiveTool.SELECT_WAND) {
                                    FilterChip(
                                        selected = state.wandSmartMode,
                                        onClick = { vm.toggleWandSmartMode() },
                                        label = { Text(if (state.wandSmartMode) "Akıllı Nesne ✓" else "Renk Modu", fontSize = 11.sp) }
                                    )
                                    if (!state.wandSmartMode) {
                                        FilterChip(
                                            selected = state.wandContiguous,
                                            onClick = { vm.toggleWandContiguous() },
                                            label = { Text(if (state.wandContiguous) "Bitişik ✓" else "Tüm Renk", fontSize = 11.sp) }
                                        )
                                    }
                                }
                            }
                        }
                        if (state.activeTool == ActiveTool.SELECT_WAND && !state.wandSmartMode) {
                            CompactSliderRow("Tolerans", "${state.wandTolerance.roundToInt()}", state.wandTolerance, 0f..120f, defaultValue = 32f) {
                                vm.updateWandTolerance(it)
                            }
                        } else if (
                            state.activeTool == ActiveTool.SELECT_QUICK ||
                            state.activeTool == ActiveTool.SELECT_BRUSH_ADD ||
                            state.activeTool == ActiveTool.SELECT_BRUSH_SUB
                        ) {
                            BrushSizeSliderRow("Fırça", state.brushSize, minPx = 1f, maxPx = 250f, defaultPx = 48f) {
                                vm.updateBrush(size = it)
                            }
                        }
                    }
                }

                BottomPanel.TEXT_EDIT -> {
                    val docW = (state.docInfo?.width?.toInt() ?: 2048).toFloat()
                    val docH = (state.docInfo?.height?.toInt() ?: 2048).toFloat()
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BottomEditorHeader(
                            title = "Metin Ekle",
                            subtitle = "Fotoğrafa dokunarak yazıyı konumlandır, sonra Uygula de",
                            applyLabel = "Uygula",
                            onClose = { activePanel = BottomPanel.DOCK },
                            onApply = {
                                vm.addTextLayer(
                                    text = textDraft,
                                    x = docW * textNormX,
                                    y = docH * textNormY,
                                    sizePx = textSizePx,
                                    colorHex = textColorHex,
                                    align = textAlign
                                )
                                activePanel = BottomPanel.DOCK
                            }
                        )
                        OutlinedTextField(
                            value = textDraft,
                            onValueChange = { textDraft = it },
                            singleLine = true,
                            placeholder = { Text("Yazını buraya yaz...", fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            listOf("#FFFFFF", "#000000", "#FF4D4F", "#FFEC3D", "#2ED573", "#4F9CFF", "#B37FEB").forEach { hex ->
                                val c = Color(AndroidColor.parseColor(hex))
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(c)
                                        .border(
                                            width = if (textColorHex.equals(hex, true)) 2.5.dp else 1.dp,
                                            color = if (textColorHex.equals(hex, true)) MaterialTheme.colorScheme.primary else Color.Gray,
                                            shape = CircleShape
                                        )
                                        .clickable { textColorHex = hex }
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf("left" to "Sol", "center" to "Orta", "right" to "Sağ").forEach { (id, lbl) ->
                                    FilterChip(
                                        selected = textAlign == id,
                                        onClick = { textAlign = id },
                                        label = { Text(lbl, fontSize = 10.sp) }
                                    )
                                }
                            }
                        }
                        CompactSliderRow(
                            label = "Boyut",
                            valueText = "${textSizePx.roundToInt()}px",
                            value = textSizePx,
                            range = 24f..420f,
                            defaultValue = (docW * 0.08f).coerceIn(36f, 260f)
                        ) {
                            textSizePx = it
                        }
                    }
                }

                BottomPanel.ADJUST_MENU -> {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BottomEditorHeader(
                            title = "Ayarlar",
                            subtitle = "Canlı önizlemeli ışık ve renk ayarları",
                            onClose = { activePanel = BottomPanel.DOCK }
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
                                onClick = { activePanel = BottomPanel.AUTO_ENHANCE_MENU }
                            )
                            CircularDockButton(
                                icon = Icons.Default.WbSunny,
                                label = "Kamera",
                                onClick = {
                                    camExposureVal = 0f; camShadowsVal = 0f; camHighlightsVal = 0f; camVibranceVal = 0f
                                    camTempVal = 0f; camTintVal = 0f; camClarityVal = 0f; camDehazeVal = 0f
                                    showCamFineTune = false
                                    vm.beginProxyPreview()
                                    activePanel = BottomPanel.CAMERA_LIGHT_LIVE
                                }
                            )
                            CircularDockButton(
                                icon = Icons.Default.Brightness6,
                                label = "Işık",
                                onClick = {
                                    brightnessVal = 0f
                                    contrastVal = 0f
                                    vm.beginProxyPreview()
                                    activePanel = BottomPanel.LIGHT_LIVE
                                }
                            )
                            CircularDockButton(
                                icon = Icons.Default.Palette,
                                label = "Renk",
                                onClick = {
                                    hueVal = 0f
                                    saturationVal = 0f
                                    vm.beginProxyPreview()
                                    activePanel = BottomPanel.COLOR_LIVE
                                }
                            )
                            CircularDockButton(
                                icon = Icons.Default.Equalizer,
                                label = "Düzeyler",
                                onClick = {
                                    levelsBlackVal = 0f
                                    levelsGammaVal = 1f
                                    levelsWhiteVal = 255f
                                    vm.beginProxyPreview()
                                    activePanel = BottomPanel.LEVELS_LIVE
                                }
                            )
                            CircularDockButton(
                                icon = Icons.Default.ColorLens,
                                label = "Denge",
                                onClick = {
                                    cbCyanRedVal = 0f
                                    cbMagentaGreenVal = 0f
                                    cbYellowBlueVal = 0f
                                    vm.beginProxyPreview()
                                    activePanel = BottomPanel.COLOR_BAL_LIVE
                                }
                            )
                            CircularDockButton(
                                icon = Icons.Default.ShowChart,
                                label = "Eğriler",
                                onClick = {
                                    curveBlackVal = 0.0f
                                    curveShadowVal = 0.25f
                                    curveMidVal = 0.50f
                                    curveHighVal = 0.75f
                                    curveWhiteVal = 1.0f
                                    curveOverlayExpanded = false
                                    vm.beginProxyPreview()
                                    activePanel = BottomPanel.CURVES_LIVE
                                }
                            )
                            CircularDockButton(
                                icon = Icons.Default.Gradient,
                                label = "HSL",
                                onClick = {
                                    hslBandIdx = 0
                                    hslHue = List(8) { 0f }
                                    hslSat = List(8) { 0f }
                                    hslLum = List(8) { 0f }
                                    vm.beginProxyPreview()
                                    activePanel = BottomPanel.HSL_MIXER_LIVE
                                }
                            )
                            CircularDockButton(
                                icon = Icons.Default.FilterFrames,
                                label = "Poster",
                                onClick = {
                                    posterMode = true
                                    posterLevels = 6f
                                    threshLevel = 128f
                                    vm.beginProxyPreview()
                                    vm.updateProxyAdjustment("posterize", """{"levels":6}""")
                                    activePanel = BottomPanel.POSTER_THRESH_LIVE
                                }
                            )
                        }
                    }
                }

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
                                    cropFreeMode = false
                                    cropScale = 0.90f
                                    val dw0 = state.docInfo?.width?.toInt() ?: 2048
                                    val dh0 = state.docInfo?.height?.toInt() ?: 2048
                                    val r0 = computeCropRect(dw0, dh0, selectedCropPreset.ratio, 0.90f)
                                    cropNormL = (r0[0].toFloat() / dw0).coerceIn(0f, 1f)
                                    cropNormT = (r0[1].toFloat() / dh0).coerceIn(0f, 1f)
                                    cropNormR = ((r0[0] + r0[2]).toFloat() / dw0).coerceIn(0f, 1f)
                                    cropNormB = ((r0[1] + r0[3]).toFloat() / dh0).coerceIn(0f, 1f)
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
                    val cropX = (cropNormL * docW).roundToInt().coerceIn(0, (docW - 4).coerceAtLeast(0))
                    val cropY = (cropNormT * docH).roundToInt().coerceIn(0, (docH - 4).coerceAtLeast(0))
                    val cropW = ((cropNormR - cropNormL) * docW).roundToInt().coerceIn(4, (docW - cropX).coerceAtLeast(4))
                    val cropH = ((cropNormB - cropNormT) * docH).roundToInt().coerceIn(4, (docH - cropY).coerceAtLeast(4))
                    val syncFromPreset = { ratio: Float?, sc: Float ->
                        val r = computeCropRect(docW, docH, ratio, sc)
                        cropNormL = (r[0].toFloat() / docW).coerceIn(0f, 1f)
                        cropNormT = (r[1].toFloat() / docH).coerceIn(0f, 1f)
                        cropNormR = ((r[0] + r[2]).toFloat() / docW).coerceIn(0f, 1f)
                        cropNormB = ((r[1] + r[3]).toFloat() / docH).coerceIn(0f, 1f)
                    }
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BottomEditorHeader(
                            title = "Kırp (${cropW}×${cropH})",
                            subtitle = "4 köşeden boyutlandır veya içinden tutup taşı",
                            applyLabel = "Uygula",
                            onClose = { activePanel = BottomPanel.CROP_MENU },
                            onApply = {
                                vm.cropCanvas(cropX, cropY, cropW, cropH)
                                activePanel = BottomPanel.DOCK
                            }
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(effectScrollState),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = cropFreeMode,
                                onClick = { cropFreeMode = true },
                                label = { Text("Serbest", fontSize = 11.sp) }
                            )
                            CROP_PRESETS.forEach { preset ->
                                FilterChip(
                                    selected = !cropFreeMode && preset == selectedCropPreset,
                                    onClick = {
                                        cropFreeMode = false
                                        selectedCropPreset = preset
                                        syncFromPreset(preset.ratio, cropScale)
                                    },
                                    label = { Text(preset.label, fontSize = 11.sp) }
                                )
                            }
                        }
                        CompactSliderRow("Alan", "%${(cropScale * 100).roundToInt()}", cropScale, 0.40f..1.0f, defaultValue = 1.0f) {
                            cropScale = it
                            syncFromPreset(if (cropFreeMode) null else selectedCropPreset.ratio, it)
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
                BottomPanel.QUICK_ACTIONS_MENU -> QuickActionsPanel(
                    onClose = { activePanel = BottomPanel.DOCK },
                    onRemoveBg = { vm.smartRemoveBackground(); activePanel = BottomPanel.DOCK },
                    onPortraitBlur = { vm.smartPortraitBlur(18f); activePanel = BottomPanel.DOCK },
                    onObjectRemove = {
                        vm.setTool(ActiveTool.SELECT_QUICK)
                        activePanel = BottomPanel.SELECT_MENU
                        vm.showStatus("Silmek istediğin nesnenin üstünden geçip 'Kaldır'a dokun", 3200L)
                    }
                )
                BottomPanel.HSL_MIXER_LIVE -> HslMixerPanel(
                    bandIdx = hslBandIdx, onBand = { hslBandIdx = it },
                    hues = hslHue, sats = hslSat, lums = hslLum,
                    onUpdate = { h, s, l ->
                        hslHue = h; hslSat = s; hslLum = l
                        vm.updateProxyHslMixer(h, s, l)
                    },
                    onClose = { vm.endProxyPreview(); activePanel = BottomPanel.ADJUST_MENU },
                    onApply = { vm.applyHslMixer(hslHue, hslSat, hslLum); activePanel = BottomPanel.DOCK }
                )
                BottomPanel.POSTER_THRESH_LIVE -> PosterThreshPanel(
                    isPoster = posterMode,
                    onMode = { isP ->
                        posterMode = isP
                        if (isP) vm.updateProxyAdjustment("posterize", """{"levels":${posterLevels.roundToInt()}}""")
                        else vm.updateProxyAdjustment("threshold", """{"level":${threshLevel.roundToInt()}}""")
                    },
                    posterLevels = posterLevels,
                    onPosterLevels = {
                        posterLevels = it
                        vm.updateProxyAdjustment("posterize", """{"levels":${it.roundToInt()}}""")
                    },
                    threshLevel = threshLevel,
                    onThreshLevel = {
                        threshLevel = it
                        vm.updateProxyAdjustment("threshold", """{"level":${it.roundToInt()}}""")
                    },
                    onClose = { vm.endProxyPreview(); activePanel = BottomPanel.ADJUST_MENU },
                    onApply = {
                        if (posterMode) vm.applyAdjustmentOnce("posterize", """{"levels":${posterLevels.roundToInt()}}""", "Posterize")
                        else vm.applyAdjustmentOnce("threshold", """{"level":${threshLevel.roundToInt()}}""", "Eşik")
                        activePanel = BottomPanel.DOCK
                    }
                )
                BottomPanel.AUTO_ENHANCE_MENU -> AutoEnhancePanel(
                    onClose = { activePanel = BottomPanel.ADJUST_MENU },
                    onPick = { mode, lbl -> vm.applyAutoEnhance(mode, lbl); activePanel = BottomPanel.DOCK }
                )
                BottomPanel.CINEMA_LOOK_EDIT -> CinemaLookPanel(
                    thumbs = cinemaThumbs,
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
                                    vm.loadCinemaThumbs()
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
    thumbs: List<ImageBitmap>,
    preset: String, onPreset: (String) -> Unit,
    intensity: Float, onIntensity: (Float) -> Unit,
    vignette: Float, onVignette: (Float) -> Unit,
    grain: Float, onGrain: (Float) -> Unit,
    onPreview: () -> Unit,
    onClose: () -> Unit,
    onApply: () -> Unit
) {
    val items = listOf("none" to "Doğal", "golden" to "Altın Saat", "tealOrange" to "Sinema", "noir" to "Film S&B", "vivid" to "Canlı Pop", "vintage" to "Retro", "cyber" to "Gece Neon")
    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BottomEditorHeader("Sinematik Stiller", "Kendi fotoğrafınla canlı önizleme + Vinyet & Gren", "Uygula", onClose, onApply)
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEachIndexed { idx, (id, lbl) ->
                val isSel = preset == id
                Column(
                    modifier = Modifier.width(62.dp).clickable { onPreset(id); onPreview() },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF2A2C36))
                            .border(if (isSel) 2.dp else 1.dp, if (isSel) MaterialTheme.colorScheme.primary else Color(0xFF3E4150), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        val bmp = thumbs.getOrNull(idx)
                        if (bmp != null) {
                            androidx.compose.foundation.Image(
                                bitmap = bmp,
                                contentDescription = lbl,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(Icons.Default.MovieFilter, contentDescription = lbl, tint = Color(0xFF8A8FA3), modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(lbl, fontSize = 10.sp, maxLines = 1, color = if (isSel) MaterialTheme.colorScheme.primary else Color(0xFFDCE0EC), fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal)
                }
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

@Composable
private fun FloatingLoupeOverlay(
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

@Composable
private fun QuickActionsPanel(
    onClose: () -> Unit,
    onRemoveBg: () -> Unit,
    onPortraitBlur: () -> Unit,
    onObjectRemove: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BottomEditorHeader("Akıllı Hızlı İşlemler", "Tek dokunuşla arka plan silme, portre bulanıklaştırma & nesne kaldırma", onClose = onClose)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            CircularDockButton(Icons.Default.ContentCut, "Arka Plan Sil", onClick = onRemoveBg)
            CircularDockButton(Icons.Default.BlurOn, "Portre Bulanık", onClick = onPortraitBlur)
            CircularDockButton(Icons.Default.AutoFixHigh, "Nesne Kaldır", onClick = onObjectRemove)
        }
    }
}

@Composable
private fun HslMixerPanel(
    bandIdx: Int, onBand: (Int) -> Unit,
    hues: List<Float>, sats: List<Float>, lums: List<Float>,
    onUpdate: (List<Float>, List<Float>, List<Float>) -> Unit,
    onClose: () -> Unit,
    onApply: () -> Unit
) {
    val bands = listOf(
        "Kırmızı" to Color(0xFFFF4D4F),
        "Turuncu" to Color(0xFFFF8C00),
        "Sarı" to Color(0xFFFAAD14),
        "Yeşil" to Color(0xFF52C41A),
        "Turkuaz" to Color(0xFF13C2C2),
        "Mavi" to Color(0xFF4F9CFF),
        "Mor" to Color(0xFF9254DE),
        "Macenta" to Color(0xFFEB2F96)
    )
    val idx = bandIdx.coerceIn(0, 7)
    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BottomEditorHeader("HSL Renk Mikseri (${bands[idx].first})", "Sadece seçtiğin renk kanalını 60 FPS canlı ayarla", "Uygula", onClose, onApply)
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            bands.forEachIndexed { i, (name, col) ->
                val sel = i == idx
                Box(
                    modifier = Modifier.size(30.dp).clip(CircleShape).background(col)
                        .border(if (sel) 2.5.dp else 1.dp, if (sel) Color.White else Color(0xFF3E4150), CircleShape)
                        .clickable { onBand(i) }
                )
            }
        }
        CompactSliderRow("Ton", "${hues[idx].roundToInt()}", hues[idx], -100f..100f, 0f) { v ->
            val nh = hues.toMutableList().apply { this[idx] = v }
            onUpdate(nh, sats, lums)
        }
        CompactSliderRow("Doygunluk", "${sats[idx].roundToInt()}", sats[idx], -100f..100f, 0f) { v ->
            val ns = sats.toMutableList().apply { this[idx] = v }
            onUpdate(hues, ns, lums)
        }
        CompactSliderRow("Parlaklık", "${lums[idx].roundToInt()}", lums[idx], -100f..100f, 0f) { v ->
            val nl = lums.toMutableList().apply { this[idx] = v }
            onUpdate(hues, sats, nl)
        }
    }
}

@Composable
private fun PosterThreshPanel(
    isPoster: Boolean, onMode: (Boolean) -> Unit,
    posterLevels: Float, onPosterLevels: (Float) -> Unit,
    threshLevel: Float, onThreshLevel: (Float) -> Unit,
    onClose: () -> Unit,
    onApply: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BottomEditorHeader(if (isPoster) "Posterize" else "Eşik (S&B)", "60 FPS canlı önizleme — Uygula ile katman oluştur", "Uygula", onClose, onApply)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = isPoster, onClick = { onMode(true) }, label = { Text("Posterize", fontSize = 11.sp) })
            FilterChip(selected = !isPoster, onClick = { onMode(false) }, label = { Text("Eşik (S&B)", fontSize = 11.sp) })
        }
        if (isPoster) {
            CompactSliderRow("Seviye", "${posterLevels.roundToInt()}", posterLevels, 2f..24f, 6f, onChange = onPosterLevels)
        } else {
            CompactSliderRow("Eşik", "${threshLevel.roundToInt()}", threshLevel, 1f..254f, 128f, onChange = onThreshLevel)
        }
    }
}
