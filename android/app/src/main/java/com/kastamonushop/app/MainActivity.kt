package com.kastamonushop.app

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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
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
    ADJUST_MENU,
    LIGHT_LIVE,
    COLOR_LIVE,
    CROP_MENU,
    CROP_RATIO_EDIT,
    FILTER_MENU,
    BLUR_EDIT,
    SHARPEN_EDIT,
    EFFECT_MENU,
    MIRROR_EDIT
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
    val label: String,
    val isVerticalAxis: Boolean
)

private val MIRROR_MODES = listOf(
    MirrorModeOption("left_to_right", "Sol→Sağ", true),
    MirrorModeOption("right_to_left", "Sağ→Sol", true),
    MirrorModeOption("top_to_bottom", "Üst→Alt", false),
    MirrorModeOption("bottom_to_top", "Alt→Üst", false)
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
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            vm.openDocumentFromUri(context.contentResolver, uri, asLayer = false)
            isHomeScreen = false
        }
    }

    if (isHomeScreen) {
        StonebridgeHomeScreen(
            savedPhotos = state.savedPhotos,
            onPickPhoto = {
                homePhotoLauncher.launch(arrayOf("image/*", "application/octet-stream", "*/*"))
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
                    text = "v0.5.1",
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
    // Decode thumbnail asynchronously on Dispatchers.IO for smooth 60fps gallery scrolling
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
            .width(64.dp)
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
        Text(text = label, color = textTint, fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1)
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
            fontSize = 12.sp,
            modifier = Modifier.width(66.dp)
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
    val context = LocalContext.current

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
    var cameraTick by remember { mutableIntStateOf(0) }

    var brightnessVal by remember { mutableFloatStateOf(0f) }
    var contrastVal by remember { mutableFloatStateOf(0f) }
    var hueVal by remember { mutableFloatStateOf(0f) }
    var saturationVal by remember { mutableFloatStateOf(0f) }
    var blurRadiusVal by remember { mutableFloatStateOf(0f) }
    var sharpenAmountVal by remember { mutableFloatStateOf(0f) }

    // Live Photo Mirror Effect state
    var selectedMirrorMode by remember { mutableStateOf(MIRROR_MODES.first()) }
    var mirrorAxisRatio by remember { mutableFloatStateOf(0.50f) }

    // Crop overlay state
    var selectedCropPreset by remember { mutableStateOf(CROP_PRESETS.first()) }
    var cropScale by remember { mutableFloatStateOf(0.90f) }

    // HSV color picker state synced with state.colorHex
    val initHsv = remember(state.colorHex) { hexToHsv(state.colorHex) }
    var pickerH by remember(state.colorHex) { mutableFloatStateOf(initHsv[0]) }
    var pickerS by remember(state.colorHex) { mutableFloatStateOf(initHsv[1]) }
    var pickerV by remember(state.colorHex) { mutableFloatStateOf(initHsv[2]) }

    fun dismissLivePanelIfActive() {
        when (activePanel) {
            BottomPanel.LIGHT_LIVE, BottomPanel.COLOR_LIVE -> {
                vm.cancelLiveAdjustment()
                activePanel = BottomPanel.DOCK
            }
            BottomPanel.BLUR_EDIT, BottomPanel.SHARPEN_EDIT -> {
                vm.cancelLiveFilter()
                activePanel = BottomPanel.DOCK
            }
            BottomPanel.MIRROR_EDIT -> {
                vm.cancelLiveMirror()
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
                BottomPanel.LIGHT_LIVE, BottomPanel.COLOR_LIVE -> {
                    vm.cancelLiveAdjustment()
                    activePanel = BottomPanel.ADJUST_MENU
                }
                BottomPanel.BLUR_EDIT, BottomPanel.SHARPEN_EDIT -> {
                    vm.cancelLiveFilter()
                    activePanel = BottomPanel.FILTER_MENU
                }
                BottomPanel.MIRROR_EDIT -> {
                    vm.cancelLiveMirror()
                    activePanel = BottomPanel.EFFECT_MENU
                }
                BottomPanel.CROP_RATIO_EDIT -> activePanel = BottomPanel.CROP_MENU
                else -> activePanel = BottomPanel.DOCK
            }
        } else {
            onGoHome()
        }
    }

    // Bulletproof camera auto-fit when opening/rotating/cropping or attaching canvas
    LaunchedEffect(state.fitCameraSignal, canvasViewRef) {
        if (state.fitCameraSignal > 0 && canvasViewRef != null) {
            canvasViewRef?.fitToDocument()
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

    val exportDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        if (uri != null) {
            vm.exportDocumentToUri(context, uri, pendingExportFormat)
        }
    }

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
                        val t = vm.uiState.value.activeTool
                        t == ActiveTool.BRUSH || t == ActiveTool.ERASER
                    },
                    isEyedropperActive = { vm.uiState.value.activeTool == ActiveTool.EYEDROPPER },
                    isRulerEnabled = { vm.uiState.value.rulerEnabled },
                    onStrokeBegin = { sample -> vm.beginLiveStroke(sample) },
                    onStrokeMove = { samples -> vm.pushLiveStroke(samples) },
                    onStrokeEnd = { vm.finishLiveStroke() },
                    onStrokeCancel = { vm.cancelLiveStroke() },
                    onRulerPreview = { preview -> rulerPreview = preview },
                    onRulerStrokeCommit = { samples -> vm.commitRulerStroke(samples) },
                    onEyedropperSample = { dx, dy -> vm.sampleColorAt(dx, dy) },
                    onCameraChanged = { cameraTick++ }
                ).also { canvasViewRef = it }
            }
        )

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

        // Live Dashed Mirror Axis Overlay when in MIRROR_EDIT
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
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val dash = PathEffect.dashPathEffect(floatArrayOf(18f, 12f))
                    // Subtle dark shadow behind white dashed line so it's readable on both light and dark photos
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
                }
            }
        }

        // Live Crop Frame Overlay when in CROP_RATIO_EDIT
        if (activePanel == BottomPanel.CROP_RATIO_EDIT) {
            val docW = state.docInfo?.width?.toInt() ?: 2048
            val docH = state.docInfo?.height?.toInt() ?: 2048
            val rect = remember(docW, docH, selectedCropPreset, cropScale) {
                computeCropRect(docW, docH, selectedCropPreset.ratio, cropScale)
            }
            val viewRef = canvasViewRef
            val tick = cameraTick
            if (viewRef != null && tick >= 0) {
                val (sx0, sy0) = viewRef.docToScreen(rect[0].toFloat(), rect[1].toFloat())
                val (sx1, sy1) = viewRef.docToScreen((rect[0] + rect[2]).toFloat(), (rect[1] + rect[3]).toFloat())
                val left = min(sx0, sx1)
                val top = min(sy0, sy1)
                val boxW = abs(sx1 - sx0)
                val boxH = abs(sy1 - sy0)
                Canvas(modifier = Modifier.fillMaxSize()) {
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
                    drawRect(
                        color = Color.White,
                        topLeft = Offset(left, top),
                        size = Size(boxW, boxH),
                        style = Stroke(width = 3.5f)
                    )
                }
            }
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
                        onGoHome()
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
                            text = { Text("Fotoğraf / PSD Aç (Yeni Proje)") },
                            onClick = {
                                showOpenMenu = false
                                openAsLayer = false
                                openDocLauncher.launch(arrayOf("image/*", "application/octet-stream", "*/*"))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Fotoğrafı Katman Olarak Ekle") },
                            onClick = {
                                showOpenMenu = false
                                openAsLayer = true
                                openDocLauncher.launch(arrayOf("image/*"))
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
                        listOf(
                            "png" to "PNG Resim (.png)",
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
                    enabled = state.docInfo?.canUndo == true,
                    onClick = {
                        dismissLivePanelIfActive()
                        vm.undo()
                    }
                )

                TopBarButton(
                    icon = Icons.AutoMirrored.Filled.Redo,
                    label = "İleri",
                    enabled = state.docInfo?.canRedo == true,
                    onClick = {
                        dismissLivePanelIfActive()
                        vm.redo()
                    }
                )

                TopBarButton(
                    icon = Icons.Default.CenterFocusStrong,
                    label = "Ortala",
                    onClick = { canvasViewRef?.fitToDocument() }
                )

                TopBarButton(
                    icon = Icons.Default.Layers,
                    label = "Katman",
                    selected = showLayers,
                    onClick = { showLayers = !showLayers }
                )
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
                            IconButton(onClick = { vm.addLayer() }) {
                                Icon(Icons.Default.Add, contentDescription = "Yeni Katman", tint = Color.White)
                            }
                            IconButton(onClick = { vm.deleteActiveLayer() }) {
                                Icon(Icons.Default.Delete, contentDescription = "Katmanı Sil", tint = Color(0xFFFF6B6B))
                            }
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
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            if (layer.locked) Icons.Default.Lock else Icons.Default.LockOpen,
                                            contentDescription = if (layer.locked) "Fotoğraf Kilitli" else "Fotoğraf Kilidi Açık",
                                            tint = if (layer.locked) Color(0xFFFFB74D) else Color(0xFF8E93A6),
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
                    if (state.isBusy) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                    }
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
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularDockButton(
                            icon = Icons.Default.Brush,
                            label = "Çizim",
                            selected = state.activeTool != ActiveTool.NONE,
                            accentDot = if (state.activeTool == ActiveTool.BRUSH || state.activeTool == ActiveTool.EYEDROPPER) activeColor else null,
                            onClick = { activePanel = BottomPanel.DRAW_MENU }
                        )
                        CircularDockButton(
                            icon = Icons.Default.Tune,
                            label = "Ayarla",
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
                                ActiveTool.NONE -> "Çizmek için Fırça veya Silgi seç"
                            },
                            onClose = { activePanel = BottomPanel.DOCK }
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
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
                                CompactSliderRow("Boyut", "${state.brushSize.toInt()}px", state.brushSize, 2f..250f, defaultValue = 28f) {
                                    vm.updateBrush(size = it)
                                }
                                CompactSliderRow("Opaklık", "${(state.brushOpacity * 100).toInt()}%", state.brushOpacity, 0.05f..1f, defaultValue = 1f) {
                                    vm.updateBrush(opacity = it)
                                }
                                CompactSliderRow("Sertlik", "${(state.brushHardness * 100).toInt()}%", state.brushHardness, 0f..1f, defaultValue = 0.85f) {
                                    vm.updateBrush(hardness = it)
                                }
                            }
                        } else if (state.activeTool == ActiveTool.ERASER) {
                            CompactSliderRow("Boyut", "${state.brushSize.toInt()}px", state.brushSize, 4f..250f, defaultValue = 28f) {
                                vm.updateBrush(size = it)
                            }
                            CompactSliderRow("Sertlik", "${(state.brushHardness * 100).toInt()}%", state.brushHardness, 0f..1f, defaultValue = 0.85f) {
                                vm.updateBrush(hardness = it)
                            }
                        }
                    }
                }

                BottomPanel.ADJUST_MENU -> {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BottomEditorHeader(
                            title = "Ayarla",
                            subtitle = "Canlı önizlemeli ışık ve renk ayarları",
                            onClose = { activePanel = BottomPanel.DOCK }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            CircularDockButton(
                                icon = Icons.Default.Brightness6,
                                label = "Işık",
                                onClick = {
                                    brightnessVal = 0f
                                    contrastVal = 0f
                                    vm.beginLiveAdjustment("brightnessContrast", """{"brightness":0,"contrast":0}""")
                                    activePanel = BottomPanel.LIGHT_LIVE
                                }
                            )
                            CircularDockButton(
                                icon = Icons.Default.Palette,
                                label = "Renk",
                                onClick = {
                                    hueVal = 0f
                                    saturationVal = 0f
                                    vm.beginLiveAdjustment("hueSaturation", """{"hue":0,"saturation":0,"lightness":0}""")
                                    activePanel = BottomPanel.COLOR_LIVE
                                }
                            )
                        }
                    }
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
                                vm.cancelLiveAdjustment()
                                activePanel = BottomPanel.ADJUST_MENU
                            },
                            onApply = {
                                vm.commitLiveAdjustment("Işık & Kontrast")
                                activePanel = BottomPanel.DOCK
                            }
                        )
                        CompactSliderRow("Parlaklık", "${brightnessVal.roundToInt()}", brightnessVal, -100f..100f, defaultValue = 0f) {
                            brightnessVal = it
                            vm.updateLiveAdjustment("""{"brightness":${brightnessVal.roundToInt()},"contrast":${contrastVal.roundToInt()}}""")
                        }
                        CompactSliderRow("Kontrast", "${contrastVal.roundToInt()}", contrastVal, -50f..100f, defaultValue = 0f) {
                            contrastVal = it
                            vm.updateLiveAdjustment("""{"brightness":${brightnessVal.roundToInt()},"contrast":${contrastVal.roundToInt()}}""")
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
                                vm.cancelLiveAdjustment()
                                activePanel = BottomPanel.ADJUST_MENU
                            },
                            onApply = {
                                vm.commitLiveAdjustment("Renk & Canlılık")
                                activePanel = BottomPanel.DOCK
                            }
                        )
                        CompactSliderRow("Canlılık", "${saturationVal.roundToInt()}", saturationVal, -100f..100f, defaultValue = 0f) {
                            saturationVal = it
                            vm.updateLiveAdjustment("""{"hue":${hueVal.roundToInt()},"saturation":${saturationVal.roundToInt()},"lightness":0}""")
                        }
                        CompactSliderRow("Renk Tonu", "${hueVal.roundToInt()}°", hueVal, -180f..180f, defaultValue = 0f) {
                            hueVal = it
                            vm.updateLiveAdjustment("""{"hue":${hueVal.roundToInt()},"saturation":${saturationVal.roundToInt()},"lightness":0}""")
                        }
                    }
                }

                BottomPanel.CROP_MENU -> {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BottomEditorHeader(
                            title = "Kırp & Çevir",
                            subtitle = "Fotoğrafı kırp, 90° döndür veya aynala",
                            onClose = { activePanel = BottomPanel.DOCK }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            CircularDockButton(
                                icon = Icons.Default.Crop,
                                label = "Oran Kırp",
                                onClick = {
                                    canvasViewRef?.fitToDocument()
                                    cropScale = 0.90f
                                    activePanel = BottomPanel.CROP_RATIO_EDIT
                                }
                            )
                            CircularDockButton(
                                icon = Icons.AutoMirrored.Filled.RotateLeft,
                                label = "90° Sol",
                                onClick = { vm.transformCanvas("90ccw", "90° sola döndürüldü") }
                            )
                            CircularDockButton(
                                icon = Icons.AutoMirrored.Filled.RotateRight,
                                label = "90° Sağ",
                                onClick = { vm.transformCanvas("90cw", "90° sağa döndürüldü") }
                            )
                            CircularDockButton(
                                icon = Icons.Default.Flip,
                                label = "Yatay",
                                onClick = { vm.transformCanvas("flip_h", "Yatay çevrildi") }
                            )
                            CircularDockButton(
                                icon = Icons.Default.SwapVert,
                                label = "Dikey",
                                onClick = { vm.transformCanvas("flip_v", "Dikey çevrildi") }
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
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CROP_PRESETS.forEach { preset ->
                                val isSel = preset == selectedCropPreset
                                FilterChip(
                                    selected = isSel,
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
                                    vm.beginLiveFilter()
                                    activePanel = BottomPanel.BLUR_EDIT
                                }
                            )
                            CircularDockButton(
                                icon = Icons.Default.Details,
                                label = "Keskin",
                                onClick = {
                                    sharpenAmountVal = 0f
                                    vm.beginLiveFilter()
                                    activePanel = BottomPanel.SHARPEN_EDIT
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
                                vm.cancelLiveFilter()
                                activePanel = BottomPanel.FILTER_MENU
                            },
                            onApply = {
                                vm.commitLiveFilter("Bulanıklaştırma")
                                activePanel = BottomPanel.DOCK
                            }
                        )
                        CompactSliderRow("Yarıçap", "${blurRadiusVal.roundToInt()}px", blurRadiusVal, 0f..60f, defaultValue = 0f) {
                            blurRadiusVal = it
                            if (it < 0.5f) {
                                vm.updateLiveFilter("", "{}")
                            } else {
                                vm.updateLiveFilter("filter.blur.gaussianBlur", """{"radius":${it}}""")
                            }
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
                                vm.cancelLiveFilter()
                                activePanel = BottomPanel.FILTER_MENU
                            },
                            onApply = {
                                vm.commitLiveFilter("Keskinleştirme")
                                activePanel = BottomPanel.DOCK
                            }
                        )
                        CompactSliderRow("Güç", "%${sharpenAmountVal.roundToInt()}", sharpenAmountVal, 0f..250f, defaultValue = 0f) {
                            sharpenAmountVal = it
                            if (it < 1f) {
                                vm.updateLiveFilter("", "{}")
                            } else {
                                vm.updateLiveFilter("filter.sharpen.unsharpMask", """{"amount":${it},"radius":1.2,"threshold":0.0}""")
                            }
                        }
                    }
                }

                // Category 5: Efekt -> [Siyah-Beyaz] [Negatif] [Ayna (Fotoğraf Yansıtma)]
                BottomPanel.EFFECT_MENU -> {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BottomEditorHeader(
                            title = "Hızlı Efektler",
                            subtitle = "Siyah-Beyaz, Negatif veya Ayna Yansıtma",
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
                                    canvasViewRef?.fitToDocument()
                                    vm.beginLiveMirror(selectedMirrorMode.id, mirrorAxisRatio)
                                    activePanel = BottomPanel.MIRROR_EDIT
                                }
                            )
                        }
                    }
                }

                // Live Preview: Photo Mirror Effect (Fotoğraf Ayna Efekti)
                BottomPanel.MIRROR_EDIT -> {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BottomEditorHeader(
                            title = "Ayna Efekti",
                            subtitle = "Yön seç ve kesikli çizgiyi kaydır, beğenirsen Uygula de",
                            applyLabel = "Uygula",
                            onClose = {
                                vm.cancelLiveMirror()
                                activePanel = BottomPanel.EFFECT_MENU
                            },
                            onApply = {
                                vm.commitLiveMirror()
                                activePanel = BottomPanel.DOCK
                            }
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            MIRROR_MODES.forEach { mode ->
                                FilterChip(
                                    selected = mode == selectedMirrorMode,
                                    onClick = {
                                        selectedMirrorMode = mode
                                        vm.updateLiveMirror(mode.id, mirrorAxisRatio)
                                    },
                                    label = { Text(mode.label, fontSize = 11.sp) }
                                )
                            }
                        }
                        CompactSliderRow(
                            label = "Çizgi",
                            valueText = "%${(mirrorAxisRatio * 100).roundToInt()}",
                            value = mirrorAxisRatio,
                            range = 0.15f..0.85f,
                            defaultValue = 0.50f
                        ) {
                            mirrorAxisRatio = it
                            vm.updateLiveMirror(selectedMirrorMode.id, it)
                        }
                    }
                }
            }
        }
    }
}
