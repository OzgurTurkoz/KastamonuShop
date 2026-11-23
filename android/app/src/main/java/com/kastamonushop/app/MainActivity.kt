package com.kastamonushop.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kastamonushop.app.ui.canvas.VulkanCanvasView
import com.kastamonushop.app.viewmodel.ActiveTool
import com.kastamonushop.app.viewmodel.EditorViewModel
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
    FILTER_MENU,
    BLUR_EDIT,
    SHARPEN_EDIT,
    EFFECT_MENU
}

private data class BlendModeOption(
    val engineName: String,
    val labelTr: String,
    val hintTr: String
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
                StonebridgeStudioScreen()
            }
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
            .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(19.dp))
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = label, color = tint, fontSize = 10.sp, fontWeight = FontWeight.Medium)
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
            .width(72.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(circleBg)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) Color.White else Color(0xFF3E4150),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = iconTint, modifier = Modifier.size(22.dp))
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
        Text(text = label, color = textTint, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun CompactSliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color(0xFFDCE0EC),
            fontSize = 12.sp,
            modifier = Modifier.width(72.dp)
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
            modifier = Modifier.width(42.dp)
        )
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
fun StonebridgeStudioScreen(vm: EditorViewModel = viewModel()) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current

    var showLayers by remember { mutableStateOf(false) }
    var activePanel by remember { mutableStateOf(BottomPanel.DOCK) }
    var showOpenMenu by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }
    var showBlendMenu by remember { mutableStateOf(false) }
    var pendingExportFormat by remember { mutableStateOf("png") }
    var openAsLayer by remember { mutableStateOf(false) }
    var canvasViewRef by remember { mutableStateOf<VulkanCanvasView?>(null) }

    var brightnessVal by remember { mutableFloatStateOf(0f) }
    var contrastVal by remember { mutableFloatStateOf(0f) }
    var hueVal by remember { mutableFloatStateOf(0f) }
    var saturationVal by remember { mutableFloatStateOf(0f) }
    var blurRadiusVal by remember { mutableFloatStateOf(8f) }
    var sharpenAmountVal by remember { mutableFloatStateOf(80f) }

    LaunchedEffect(state.fitCameraSignal) {
        if (state.fitCameraSignal > 0) {
            canvasViewRef?.fitToDocument()
        }
    }

    val openDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            vm.openDocumentFromUri(context.contentResolver, uri, asLayer = openAsLayer)
        }
    }

    val exportDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        if (uri != null) {
            vm.exportDocumentToUri(context.contentResolver, uri, pendingExportFormat)
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
                    onStrokeBegin = { sample -> vm.beginLiveStroke(sample) },
                    onStrokeMove = { samples -> vm.pushLiveStroke(samples) },
                    onStrokeEnd = { vm.finishLiveStroke() },
                    onStrokeCancel = { vm.cancelLiveStroke() }
                ).also { canvasViewRef = it }
            }
        )

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
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
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
                    onClick = { vm.undo() }
                )

                TopBarButton(
                    icon = Icons.AutoMirrored.Filled.Redo,
                    label = "İleri",
                    enabled = state.docInfo?.canRedo == true,
                    onClick = { vm.redo() }
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
                        Text(
                            text = "Seçili: ${activeLayer.name} · Opaklık ${(activeLayer.opacity * 100).toInt()}%",
                            fontSize = 11.sp,
                            color = Color(0xFFD0D4E0)
                        )
                        Slider(
                            value = activeLayer.opacity,
                            onValueChange = { vm.setLayerOpacity(activeLayer.id, it) },
                            valueRange = 0f..1f,
                            modifier = Modifier.height(28.dp)
                        )

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
                    .padding(bottom = if (activePanel == BottomPanel.DOCK) 114.dp else 198.dp)
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

        // GROUPED 4-CATEGORY BOTTOM CIRCULAR DOCK & COMPACT SUB-MENUS
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
                runCatching { Color(android.graphics.Color.parseColor(state.colorHex)) }
                    .getOrDefault(Color.Red)
            }

            when (activePanel) {
                // Main Dock: ONLY 4 clean category buttons!
                BottomPanel.DOCK -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularDockButton(
                            icon = Icons.Default.Brush,
                            label = "Çizim",
                            selected = state.activeTool != ActiveTool.NONE,
                            accentDot = if (state.activeTool == ActiveTool.BRUSH) activeColor else null,
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

                // Category 1: Çizim (Fırça + Silgi + Bırak, all in one compact strip)
                BottomPanel.DRAW_MENU -> {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BottomEditorHeader(
                            title = "Çizim Araçları",
                            subtitle = if (state.activeTool == ActiveTool.NONE) "Çizmek için Fırça veya Silgi seç" else "Aktif araçla tuvale çizebilirsin",
                            onClose = { activePanel = BottomPanel.DOCK }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
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
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                palette.forEach { hex ->
                                    val c = Color(android.graphics.Color.parseColor(hex))
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(c)
                                            .border(
                                                width = if (state.colorHex == hex) 2.5.dp else 1.dp,
                                                color = if (state.colorHex == hex) MaterialTheme.colorScheme.primary else Color.Gray,
                                                shape = CircleShape
                                            )
                                            .clickable { vm.updateBrush(colorHex = hex) }
                                    )
                                }
                            }
                            CompactSliderRow("Boyut", "${state.brushSize.toInt()}px", state.brushSize, 2f..250f) {
                                vm.updateBrush(size = it)
                            }
                            CompactSliderRow("Opaklık", "${(state.brushOpacity * 100).toInt()}%", state.brushOpacity, 0.05f..1f) {
                                vm.updateBrush(opacity = it)
                            }
                            CompactSliderRow("Sertlik", "${(state.brushHardness * 100).toInt()}%", state.brushHardness, 0f..1f) {
                                vm.updateBrush(hardness = it)
                            }
                        } else if (state.activeTool == ActiveTool.ERASER) {
                            CompactSliderRow("Boyut", "${state.brushSize.toInt()}px", state.brushSize, 4f..250f) {
                                vm.updateBrush(size = it)
                            }
                            CompactSliderRow("Sertlik", "${(state.brushHardness * 100).toInt()}%", state.brushHardness, 0f..1f) {
                                vm.updateBrush(hardness = it)
                            }
                        }
                    }
                }

                // Category 2: Ayarla -> Sub-buttons [Işık] [Renk]
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

                // Live Preview: Işık & Kontrast
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
                        CompactSliderRow("Parlaklık", "${brightnessVal.roundToInt()}", brightnessVal, -100f..100f) {
                            brightnessVal = it
                            vm.updateLiveAdjustment("""{"brightness":${brightnessVal.roundToInt()},"contrast":${contrastVal.roundToInt()}}""")
                        }
                        CompactSliderRow("Kontrast", "${contrastVal.roundToInt()}", contrastVal, -50f..100f) {
                            contrastVal = it
                            vm.updateLiveAdjustment("""{"brightness":${brightnessVal.roundToInt()},"contrast":${contrastVal.roundToInt()}}""")
                        }
                    }
                }

                // Live Preview: Renk & Canlılık
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
                        CompactSliderRow("Canlılık", "${saturationVal.roundToInt()}", saturationVal, -100f..100f) {
                            saturationVal = it
                            vm.updateLiveAdjustment("""{"hue":${hueVal.roundToInt()},"saturation":${saturationVal.roundToInt()},"lightness":0}""")
                        }
                        CompactSliderRow("Renk Tonu", "${hueVal.roundToInt()}°", hueVal, -180f..180f) {
                            hueVal = it
                            vm.updateLiveAdjustment("""{"hue":${hueVal.roundToInt()},"saturation":${saturationVal.roundToInt()},"lightness":0}""")
                        }
                    }
                }

                // Category 3: Filtre -> Sub-buttons [Bulanık] [Keskin]
                BottomPanel.FILTER_MENU -> {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BottomEditorHeader(
                            title = "Filtreler",
                            subtitle = "Fotoğraf katmanına doğrudan uygulanır",
                            onClose = { activePanel = BottomPanel.DOCK }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            CircularDockButton(
                                icon = Icons.Default.BlurOn,
                                label = "Bulanık",
                                onClick = { activePanel = BottomPanel.BLUR_EDIT }
                            )
                            CircularDockButton(
                                icon = Icons.Default.Details,
                                label = "Keskin",
                                onClick = { activePanel = BottomPanel.SHARPEN_EDIT }
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
                            subtitle = "Gaussian Blur ile fotoğrafı yumuşatır",
                            applyLabel = "Uygula",
                            onClose = { activePanel = BottomPanel.FILTER_MENU },
                            onApply = {
                                vm.applyPixelFilter("filter.blur.gaussianBlur", """{"radius":${blurRadiusVal}}""", "Bulanıklaştırma")
                                activePanel = BottomPanel.DOCK
                            }
                        )
                        CompactSliderRow("Yarıçap", "${blurRadiusVal.roundToInt()}px", blurRadiusVal, 1f..60f) {
                            blurRadiusVal = it
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
                            subtitle = "Detay ve kenarları belirginleştirir",
                            applyLabel = "Uygula",
                            onClose = { activePanel = BottomPanel.FILTER_MENU },
                            onApply = {
                                vm.applyPixelFilter("filter.sharpen.unsharpMask", """{"amount":${sharpenAmountVal},"radius":1.2,"threshold":0.0}""", "Keskinleştirme")
                                activePanel = BottomPanel.DOCK
                            }
                        )
                        CompactSliderRow("Güç", "%${sharpenAmountVal.roundToInt()}", sharpenAmountVal, 20f..250f) {
                            sharpenAmountVal = it
                        }
                    }
                }

                // Category 4: Efekt -> Sub-buttons [Siyah-Beyaz (Aç/Kapat)] [Negatif (Aç/Kapat)]
                BottomPanel.EFFECT_MENU -> {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BottomEditorHeader(
                            title = "Hızlı Efektler",
                            subtitle = "Tek dokunuşla aç, tekrar basınca geri kapat",
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
                        }
                    }
                }
            }
        }
    }
}
