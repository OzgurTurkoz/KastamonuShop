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
                    text = "v0.7.1",
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
    var retouchCursor by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var selectPreviewRect by remember { mutableStateOf<FloatArray?>(null) }
    var levelsBlackVal by remember { mutableFloatStateOf(0f) }
    var levelsGammaVal by remember { mutableFloatStateOf(1f) }
    var levelsWhiteVal by remember { mutableFloatStateOf(255f) }
    var cbCyanRedVal by remember { mutableFloatStateOf(0f) }
    var cbMagentaGreenVal by remember { mutableFloatStateOf(0f) }
    var cbYellowBlueVal by remember { mutableFloatStateOf(0f) }
    var showExitSaveDialog by remember { mutableStateOf(false) }

    // Live draggable Text layer editor state
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

    fun clearGpuPreview() {
        pendingQuarterTurns = 0
        pendingFlipH = false
        pendingFlipV = false
        NativeSurfaceBridge.nativeSetPreview(0, 0.5f, false, false)
        NativeSurfaceBridge.nativeSetAdjustPreview(0f, 0f, 0f, 0f, 0f, 0f)
        NativeSurfaceBridge.nativeSetTonePreview(0f, 1f, 1f, 0f, 0f, 0f)
        canvasViewRef?.fitToDocument(0)
    }

    fun clearAdjustGpuPreviewOnly() {
        NativeSurfaceBridge.nativeSetAdjustPreview(0f, 0f, 0f, 0f, 0f, 0f)
        NativeSurfaceBridge.nativeSetTonePreview(0f, 1f, 1f, 0f, 0f, 0f)
        NativeSurfaceBridge.nativeRenderFrame()
    }

    fun pushGpuTransformPreview(turns: Int, flipH: Boolean, flipV: Boolean) {
        NativeSurfaceBridge.nativeSetPreview(0, 0.5f, flipH, flipV)
        canvasViewRef?.fitToDocument(turns)
    }

    fun dismissLivePanelIfActive() {
        when (activePanel) {
            BottomPanel.LIGHT_LIVE, BottomPanel.COLOR_LIVE, BottomPanel.LEVELS_LIVE, BottomPanel.COLOR_BAL_LIVE -> {
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
                BottomPanel.LIGHT_LIVE, BottomPanel.COLOR_LIVE, BottomPanel.LEVELS_LIVE, BottomPanel.COLOR_BAL_LIVE -> {
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
                BottomPanel.CROP_RATIO_EDIT -> activePanel = BottomPanel.CROP_MENU
                else -> activePanel = BottomPanel.DOCK
            }
        } else {
            showExitSaveDialog = true
        }
    }

    LaunchedEffect(state.fitCameraSignal, canvasViewRef) {
        if (state.fitCameraSignal > 0 && canvasViewRef != null) {
            canvasViewRef?.fitToDocument(0)
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
                        vm.uiState.value.activeTool == ActiveTool.SPOT_HEAL
                    },
                    isEyedropperActive = { vm.uiState.value.activeTool == ActiveTool.EYEDROPPER },
                    isRulerEnabled = { vm.uiState.value.rulerEnabled },
                    isMirrorDragActive = { currentPanelState == BottomPanel.MIRROR_EDIT },
                    isMirrorVerticalAxis = { currentMirrorModeState.isVerticalAxis },
                    isTextDragActive = { currentPanelState == BottomPanel.TEXT_EDIT },
                    isSelectRectActive = { vm.uiState.value.activeTool == ActiveTool.SELECT_RECT },
                    isSelectWandActive = { vm.uiState.value.activeTool == ActiveTool.SELECT_WAND },
                    isCloneSourcePickActive = {
                        val st = vm.uiState.value
                        st.activeTool == ActiveTool.CLONE_STAMP && st.pickingCloneSource
                    },
                    onStrokeBegin = { sample -> vm.beginLiveStroke(sample) },
                    onStrokeMove = { samples -> vm.pushLiveStroke(samples) },
                    onStrokeEnd = { vm.finishLiveStroke() },
                    onStrokeCancel = { vm.cancelLiveStroke() },
                    onRetouchPreview = { pos -> retouchCursor = pos },
                    onRetouchCommit = { samples -> vm.applyRetouchStroke(samples) },
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
                    onCloneSourcePick = { dx, dy -> vm.setCloneSource(dx, dy) },
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
                        .offset {
                            androidx.compose.ui.unit.IntOffset(
                                (sx - 120f).roundToInt(),
                                (sy - 40f).roundToInt()
                            )
                        }
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
                    onClick = { canvasViewRef?.fitToDocument(pendingQuarterTurns) }
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
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularDockButton(
                            icon = Icons.Default.Brush,
                            label = "Çizim",
                            selected = state.activeTool != ActiveTool.NONE && state.activeTool != ActiveTool.SELECT_RECT && state.activeTool != ActiveTool.SELECT_WAND,
                            accentDot = if (state.activeTool == ActiveTool.BRUSH || state.activeTool == ActiveTool.EYEDROPPER) activeColor else null,
                            onClick = { activePanel = BottomPanel.DRAW_MENU }
                        )
                        CircularDockButton(
                            icon = Icons.Default.SelectAll,
                            label = "Seçim",
                            selected = state.selectionBounds != null || state.activeTool == ActiveTool.SELECT_RECT || state.activeTool == ActiveTool.SELECT_WAND,
                            onClick = {
                                if (state.activeTool != ActiveTool.SELECT_RECT && state.activeTool != ActiveTool.SELECT_WAND) {
                                    vm.setTool(ActiveTool.SELECT_RECT)
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
                                textSizePx = (docW * 0.08f).coerceIn(36f, 260f)
                                textNormX = 0.50f
                                textNormY = 0.50f
                                activePanel = BottomPanel.TEXT_EDIT
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
                                ActiveTool.SPOT_HEAL -> "Leke Giderme · kusurun üzerinden parmağınla geç"
                                ActiveTool.SMUDGE -> "Dağıtma · dokuyu parmağınla yumuşakça yay"
                                ActiveTool.DODGE -> "Aydınlat · dokunduğun bölgeyi açar"
                                ActiveTool.BURN -> "Koyulaştır · dokunduğun bölgeyi gölgeler"
                                ActiveTool.CLONE_STAMP -> if (state.pickingCloneSource) "Klon kaynağı seç: fotoğrafa dokun" else "Klonla · boyamak istediğin yere sürükle"
                                ActiveTool.SELECT_RECT -> "Dikdörtgen/Elips alan seç"
                                ActiveTool.SELECT_WAND -> "Sihirli Değnek · renge dokunarak seç"
                                ActiveTool.NONE -> "Çizim veya Rötuş için bir araç seç"
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
                        } else if (
                            state.activeTool == ActiveTool.SPOT_HEAL ||
                            state.activeTool == ActiveTool.SMUDGE ||
                            state.activeTool == ActiveTool.DODGE ||
                            state.activeTool == ActiveTool.BURN
                        ) {
                            CompactSliderRow("Boyut", "${state.brushSize.toInt()}px", state.brushSize, 8f..250f, defaultValue = 48f) {
                                vm.updateBrush(size = it)
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
                            CompactSliderRow("Boyut", "${state.brushSize.toInt()}px", state.brushSize, 8f..250f, defaultValue = 48f) {
                                vm.updateBrush(size = it)
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
                                ActiveTool.SELECT_WAND -> "Seçmek istediğin renge dokun (Tolerans: ${state.wandTolerance.roundToInt()})"
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
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CircularDockButton(
                                icon = Icons.Default.CropFree,
                                label = if (state.selectEllipse) "Elips" else "Dikdörtgen",
                                selected = state.activeTool == ActiveTool.SELECT_RECT,
                                onClick = {
                                    if (state.activeTool == ActiveTool.SELECT_RECT) {
                                        vm.toggleSelectEllipse()
                                    } else {
                                        vm.setTool(ActiveTool.SELECT_RECT)
                                    }
                                }
                            )
                            CircularDockButton(
                                icon = Icons.Default.AutoFixHigh,
                                label = "Değnek",
                                selected = state.activeTool == ActiveTool.SELECT_WAND,
                                onClick = { vm.setTool(ActiveTool.SELECT_WAND) }
                            )
                            if (state.selectionBounds != null) {
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = state.selectAddMode,
                                onClick = { vm.toggleSelectAddMode() },
                                label = { Text(if (state.selectAddMode) "Ekle (+) Açık" else "Ekle (+)", fontSize = 11.sp) }
                            )
                            if (state.activeTool == ActiveTool.SELECT_WAND) {
                                FilterChip(
                                    selected = state.wandContiguous,
                                    onClick = { vm.toggleWandContiguous() },
                                    label = { Text(if (state.wandContiguous) "Bitişik ✓" else "Tüm Renk", fontSize = 11.sp) }
                                )
                            }
                        }
                        if (state.activeTool == ActiveTool.SELECT_WAND) {
                            CompactSliderRow("Tolerans", "${state.wandTolerance.roundToInt()}", state.wandTolerance, 0f..120f, defaultValue = 32f) {
                                vm.updateWandTolerance(it)
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
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            CircularDockButton(
                                icon = Icons.Default.Brightness6,
                                label = "Işık",
                                onClick = {
                                    brightnessVal = 0f
                                    contrastVal = 0f
                                    NativeSurfaceBridge.nativeSetAdjustPreview(0f, 0f, 0f, 0f, 0f, 0f)
                                    activePanel = BottomPanel.LIGHT_LIVE
                                }
                            )
                            CircularDockButton(
                                icon = Icons.Default.Palette,
                                label = "Renk",
                                onClick = {
                                    hueVal = 0f
                                    saturationVal = 0f
                                    NativeSurfaceBridge.nativeSetAdjustPreview(0f, 0f, 0f, 0f, 0f, 0f)
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
                                    NativeSurfaceBridge.nativeSetTonePreview(0f, 1f, 1f, 0f, 0f, 0f)
                                    activePanel = BottomPanel.LEVELS_LIVE
                                }
                            )
                            CircularDockButton(
                                icon = Icons.Default.ColorLens,
                                label = "Renk Dengesi",
                                onClick = {
                                    cbCyanRedVal = 0f
                                    cbMagentaGreenVal = 0f
                                    cbYellowBlueVal = 0f
                                    NativeSurfaceBridge.nativeSetTonePreview(0f, 1f, 1f, 0f, 0f, 0f)
                                    activePanel = BottomPanel.COLOR_BAL_LIVE
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
                            NativeSurfaceBridge.nativeSetAdjustPreview(brightnessVal, contrastVal, 0f, 0f, 0f, 0f)
                            NativeSurfaceBridge.nativeRenderFrame()
                        }
                        CompactSliderRow("Kontrast", "${contrastVal.roundToInt()}", contrastVal, -50f..100f, defaultValue = 0f) {
                            contrastVal = it
                            NativeSurfaceBridge.nativeSetAdjustPreview(brightnessVal, contrastVal, 0f, 0f, 0f, 0f)
                            NativeSurfaceBridge.nativeRenderFrame()
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
                            NativeSurfaceBridge.nativeSetAdjustPreview(0f, 0f, saturationVal, hueVal, 0f, 0f)
                            NativeSurfaceBridge.nativeRenderFrame()
                        }
                        CompactSliderRow("Renk Tonu", "${hueVal.roundToInt()}°", hueVal, -180f..180f, defaultValue = 0f) {
                            hueVal = it
                            NativeSurfaceBridge.nativeSetAdjustPreview(0f, 0f, saturationVal, hueVal, 0f, 0f)
                            NativeSurfaceBridge.nativeRenderFrame()
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
                            NativeSurfaceBridge.nativeSetTonePreview(levelsBlackVal / 255f, levelsGammaVal, levelsWhiteVal / 255f, 0f, 0f, 0f)
                            NativeSurfaceBridge.nativeRenderFrame()
                        }
                        CompactSliderRow("Orta Ton", String.format(java.util.Locale.US, "%.2f", levelsGammaVal), levelsGammaVal, 0.25f..2.5f, defaultValue = 1f) {
                            levelsGammaVal = it
                            NativeSurfaceBridge.nativeSetTonePreview(levelsBlackVal / 255f, levelsGammaVal, levelsWhiteVal / 255f, 0f, 0f, 0f)
                            NativeSurfaceBridge.nativeRenderFrame()
                        }
                        CompactSliderRow("Beyazlar", "${levelsWhiteVal.roundToInt()}", levelsWhiteVal, 75f..255f, defaultValue = 255f) {
                            levelsWhiteVal = it.coerceAtLeast(levelsBlackVal + 15f)
                            NativeSurfaceBridge.nativeSetTonePreview(levelsBlackVal / 255f, levelsGammaVal, levelsWhiteVal / 255f, 0f, 0f, 0f)
                            NativeSurfaceBridge.nativeRenderFrame()
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
                        CompactSliderRow("Camgöbeği / Kırmızı", "${cbCyanRedVal.roundToInt()}", cbCyanRedVal, -100f..100f, defaultValue = 0f) {
                            cbCyanRedVal = it
                            NativeSurfaceBridge.nativeSetTonePreview(0f, 1f, 1f, cbCyanRedVal / 100f, cbMagentaGreenVal / 100f, cbYellowBlueVal / 100f)
                            NativeSurfaceBridge.nativeRenderFrame()
                        }
                        CompactSliderRow("Macenta / Yeşil", "${cbMagentaGreenVal.roundToInt()}", cbMagentaGreenVal, -100f..100f, defaultValue = 0f) {
                            cbMagentaGreenVal = it
                            NativeSurfaceBridge.nativeSetTonePreview(0f, 1f, 1f, cbCyanRedVal / 100f, cbMagentaGreenVal / 100f, cbYellowBlueVal / 100f)
                            NativeSurfaceBridge.nativeRenderFrame()
                        }
                        CompactSliderRow("Sarı / Mavi", "${cbYellowBlueVal.roundToInt()}", cbYellowBlueVal, -100f..100f, defaultValue = 0f) {
                            cbYellowBlueVal = it
                            NativeSurfaceBridge.nativeSetTonePreview(0f, 1f, 1f, cbCyanRedVal / 100f, cbMagentaGreenVal / 100f, cbYellowBlueVal / 100f)
                            NativeSurfaceBridge.nativeRenderFrame()
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
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
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
                                .horizontalScroll(rememberScrollState()),
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
                                    NativeSurfaceBridge.nativeSetAdjustPreview(0f, 0f, 0f, 0f, 0f, 0f)
                                    activePanel = BottomPanel.BLUR_EDIT
                                }
                            )
                            CircularDockButton(
                                icon = Icons.Default.Details,
                                label = "Keskin",
                                onClick = {
                                    sharpenAmountVal = 0f
                                    NativeSurfaceBridge.nativeSetAdjustPreview(0f, 0f, 0f, 0f, 0f, 0f)
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
                            NativeSurfaceBridge.nativeSetAdjustPreview(0f, 0f, 0f, 0f, blurRadiusVal, 0f)
                            NativeSurfaceBridge.nativeRenderFrame()
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
                            NativeSurfaceBridge.nativeSetAdjustPreview(0f, 0f, 0f, 0f, 0f, sharpenAmountVal)
                            NativeSurfaceBridge.nativeRenderFrame()
                        }
                    }
                }

                // Category 5: Efekt -> [Siyah-Beyaz] [Negatif] [Ayna]
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
            }
        }
    }
}
