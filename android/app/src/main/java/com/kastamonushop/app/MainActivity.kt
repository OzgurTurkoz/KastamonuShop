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

private val StudioDarkScheme = darkColorScheme(
    primary = Color(0xFF4F9CFF),
    secondary = Color(0xFF2ED573),
    background = Color(0xFF19191E),
    surface = Color(0xFF23232C),
    surfaceVariant = Color(0xFF30303D),
    onBackground = Color(0xFFFFFFFF),
    onSurface = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFFE0E2EC)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = StudioDarkScheme) {
                KastamonuStudioScreen()
            }
        }
    }
}

@Composable
private fun LabeledToolButton(
    icon: ImageVector,
    label: String,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color.Transparent
    val tint = when {
        !enabled -> Color(0xFF666A78)
        selected -> MaterialTheme.colorScheme.primary
        else -> Color.White
    }
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = label, color = tint, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun KastamonuStudioScreen(vm: EditorViewModel = viewModel()) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current

    var showLayers by remember { mutableStateOf(false) }
    var showBrushSheet by remember { mutableStateOf(false) }
    var showOpenMenu by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }
    var pendingExportFormat by remember { mutableStateOf("png") }
    var openAsLayer by remember { mutableStateOf(false) }
    var canvasViewRef by remember { mutableStateOf<VulkanCanvasView?>(null) }

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
                    onStrokeBegin = { sample -> vm.beginLiveStroke(sample) },
                    onStrokeMove = { samples -> vm.pushLiveStroke(samples) },
                    onStrokeEnd = { vm.finishLiveStroke() },
                    onStrokeCancel = { vm.cancelLiveStroke() }
                ).also { canvasViewRef = it }
            }
        )

        // Top Bar: Compact, High-Contrast, Labeled for Beginners (never clipped on phones)
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
                    LabeledToolButton(
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
                    LabeledToolButton(
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
                            "pcraft" to "KastamonuShop Proje (.pcraft)",
                            "webp" to "WebP (.webp)"
                        ).forEach { (ext, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    showExportMenu = false
                                    pendingExportFormat = ext
                                    exportDocLauncher.launch("kastamonushop.$ext")
                                }
                            )
                        }
                    }
                }

                LabeledToolButton(
                    icon = Icons.AutoMirrored.Filled.Undo,
                    label = "Geri",
                    enabled = state.docInfo?.canUndo == true,
                    onClick = { vm.undo() }
                )

                LabeledToolButton(
                    icon = Icons.AutoMirrored.Filled.Redo,
                    label = "İleri",
                    enabled = state.docInfo?.canRedo == true,
                    onClick = { vm.redo() }
                )

                LabeledToolButton(
                    icon = Icons.Default.CenterFocusStrong,
                    label = "Ortala",
                    onClick = { canvasViewRef?.fitToDocument() }
                )

                LabeledToolButton(
                    icon = Icons.Default.Layers,
                    label = "Katman",
                    selected = showLayers,
                    onClick = { showLayers = !showLayers }
                )
            }
        }

        // Left Tool Rail: Brush, Eraser & Color/Size (two-finger zoom/pan always works!)
        Surface(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 10.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LabeledToolButton(
                    icon = Icons.Default.Brush,
                    label = "Fırça",
                    selected = state.activeTool == ActiveTool.BRUSH,
                    onClick = { vm.setTool(ActiveTool.BRUSH) }
                )
                LabeledToolButton(
                    icon = Icons.Default.AutoFixNormal,
                    label = "Silgi",
                    selected = state.activeTool == ActiveTool.ERASER,
                    onClick = { vm.setTool(ActiveTool.ERASER) }
                )

                val parsedColor = remember(state.colorHex) {
                    runCatching { Color(android.graphics.Color.parseColor(state.colorHex)) }
                        .getOrDefault(Color.Red)
                }
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showBrushSheet = !showBrushSheet }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(parsedColor)
                            .border(2.dp, Color.White, CircleShape)
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "${state.brushSize.toInt()}px",
                        fontSize = 10.sp,
                        color = Color.White
                    )
                }
            }
        }

        // Right Floating Layers Drawer
        if (showLayers) {
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 10.dp, top = 84.dp, bottom = 76.dp)
                    .width(250.dp)
                    .fillMaxHeight(0.68f),
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
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
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
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
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
                                Column(modifier = Modifier.weight(1f).padding(start = 6.dp)) {
                                    Text(layer.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White)
                                    Text(
                                        "${layer.blendMode} · ${(layer.opacity * 100).toInt()}%",
                                        fontSize = 11.sp,
                                        color = Color(0xFFB8BCC8)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom Brush & Color Studio Card
        if (showBrushSheet) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(12.dp)
                    .widthIn(max = 420.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Fırça & Renk Ayarları", fontWeight = FontWeight.SemiBold, color = Color.White)
                        IconButton(onClick = { showBrushSheet = false }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Kapat", tint = Color.White)
                        }
                    }

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
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .border(
                                        width = if (state.colorHex == hex) 3.dp else 1.dp,
                                        color = if (state.colorHex == hex) MaterialTheme.colorScheme.primary else Color.Gray,
                                        shape = CircleShape
                                    )
                                    .clickable { vm.updateBrush(colorHex = hex) }
                            )
                        }
                    }

                    Text("Boyut: ${state.brushSize.toInt()} px", fontSize = 12.sp, color = Color.White)
                    Slider(
                        value = state.brushSize,
                        onValueChange = { vm.updateBrush(size = it) },
                        valueRange = 2f..250f
                    )

                    Text("Opaklık (Saydamlık): ${(state.brushOpacity * 100).toInt()}%", fontSize = 12.sp, color = Color.White)
                    Slider(
                        value = state.brushOpacity,
                        onValueChange = { vm.updateBrush(opacity = it) },
                        valueRange = 0.05f..1.0f
                    )

                    Text("Sertlik (Kenar Yumuşaklığı): ${(state.brushHardness * 100).toInt()}%", fontSize = 12.sp, color = Color.White)
                    Slider(
                        value = state.brushHardness,
                        onValueChange = { vm.updateBrush(hardness = it) },
                        valueRange = 0.0f..1.0f
                    )
                }
            }
        }

        // High-contrast auto-dismissing status pill
        state.statusMessage?.let { msg ->
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .clickable { vm.clearStatus() },
                shape = RoundedCornerShape(12.dp),
                color = Color(0xEE111217),
                tonalElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (state.isBusy) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(msg, fontSize = 12.sp, color = Color.White)
                }
            }
        }
    }
}
