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
    surface = Color(0xFF23232A),
    surfaceVariant = Color(0xFF2E2E38),
    onBackground = Color(0xFFF1F2F6),
    onSurface = Color(0xFFF1F2F6)
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
fun KastamonuStudioScreen(vm: EditorViewModel = viewModel()) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current

    var showLayers by remember { mutableStateOf(false) }
    var showBrushSheet by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }
    var pendingExportFormat by remember { mutableStateOf("png") }
    var canvasViewRef by remember { mutableStateOf<VulkanCanvasView?>(null) }

    val openDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            vm.openDocumentFromUri(context.contentResolver, uri)
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
        // Full-screen zero-copy Vulkan SurfaceView canvas
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                VulkanCanvasView(
                    context = ctx,
                    getActiveTool = { vm.uiState.value.activeTool },
                    getDocSize = {
                        val d = vm.uiState.value.docInfo
                        (d?.width?.toInt() ?: 2048) to (d?.height?.toInt() ?: 2048)
                    },
                    onStrokeComplete = { pts -> vm.commitStroke(pts) }
                ).also { canvasViewRef = it }
            }
        )

        // Floating Top Studio Bar
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            tonalElevation = 6.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "KastamonuShop",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                IconButton(onClick = { openDocLauncher.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Default.FolderOpen, contentDescription = "Aç (PSD/RAW/PNG)")
                }

                Box {
                    IconButton(onClick = { showExportMenu = true }) {
                        Icon(Icons.Default.Save, contentDescription = "Dışa Aktar")
                    }
                    DropdownMenu(
                        expanded = showExportMenu,
                        onDismissRequest = { showExportMenu = false }
                    ) {
                        listOf(
                            "psd" to "Photoshop (.psd)",
                            "pcraft" to "PhotoCraft (.pcraft)",
                            "png" to "PNG (.png)",
                            "jpg" to "JPEG (.jpg)",
                            "webp" to "WebP (.webp)",
                            "tiff" to "TIFF (.tiff)"
                        ).forEach { (ext, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    showExportMenu = false
                                    pendingExportFormat = ext
                                    exportDocLauncher.launch("kastamonushop_export.$ext")
                                }
                            )
                        }
                    }
                }

                IconButton(
                    onClick = { vm.undo() },
                    enabled = state.docInfo?.canUndo == true
                ) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Geri Al")
                }

                IconButton(
                    onClick = { vm.redo() },
                    enabled = state.docInfo?.canRedo == true
                ) {
                    Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "İleri Al")
                }

                IconButton(onClick = { canvasViewRef?.resetCamera() }) {
                    Icon(Icons.Default.CenterFocusStrong, contentDescription = "Tuvali Ortala")
                }

                IconButton(onClick = { showLayers = !showLayers }) {
                    Icon(
                        Icons.Default.Layers,
                        contentDescription = "Katmanlar",
                        tint = if (showLayers) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Left Pro Studio Tool Rail
        Surface(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 12.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(onClick = { vm.setTool(ActiveTool.BRUSH) }) {
                    Icon(
                        Icons.Default.Brush,
                        contentDescription = "Fırça",
                        tint = if (state.activeTool == ActiveTool.BRUSH) MaterialTheme.colorScheme.primary else Color.LightGray
                    )
                }
                IconButton(onClick = { vm.setTool(ActiveTool.ERASER) }) {
                    Icon(
                        Icons.Default.AutoFixNormal,
                        contentDescription = "Silgi",
                        tint = if (state.activeTool == ActiveTool.ERASER) MaterialTheme.colorScheme.primary else Color.LightGray
                    )
                }
                IconButton(onClick = { vm.setTool(ActiveTool.PAN_ZOOM) }) {
                    Icon(
                        Icons.Default.PanTool,
                        contentDescription = "Kaydır / Yakınlaştır",
                        tint = if (state.activeTool == ActiveTool.PAN_ZOOM) MaterialTheme.colorScheme.primary else Color.LightGray
                    )
                }

                // Active Color Swatch -> Opens Brush & Color Studio
                val parsedColor = remember(state.colorHex) {
                    runCatching { Color(android.graphics.Color.parseColor(state.colorHex)) }
                        .getOrDefault(Color.Red)
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(parsedColor)
                        .border(2.dp, Color.White, CircleShape)
                        .clickable { showBrushSheet = !showBrushSheet }
                )

                Text(
                    text = "${state.brushSize.toInt()}px",
                    fontSize = 10.sp,
                    color = Color.LightGray
                )
            }
        }

        // Right Floating Layers Drawer
        if (showLayers) {
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp, top = 76.dp, bottom = 76.dp)
                    .width(260.dp)
                    .fillMaxHeight(0.72f),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                tonalElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Katmanlar", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Row {
                            IconButton(onClick = { vm.addLayer() }) {
                                Icon(Icons.Default.Add, contentDescription = "Yeni Katman")
                            }
                            IconButton(onClick = { vm.deleteActiveLayer() }) {
                                Icon(Icons.Default.Delete, contentDescription = "Katmanı Sil")
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(state.layers, key = { it.id }) { layer ->
                            val bg = if (layer.isActive) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
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
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f).padding(start = 6.dp)) {
                                    Text(layer.name, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    Text(
                                        "${layer.blendMode} · ${(layer.opacity * 100).toInt()}%",
                                        fontSize = 11.sp,
                                        color = Color.Gray
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
                        Text("Fırça & Renk", fontWeight = FontWeight.SemiBold)
                        IconButton(onClick = { showBrushSheet = false }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Kapat")
                        }
                    }

                    // Quick Pro Palette
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
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .border(
                                        width = if (state.colorHex == hex) 2.5.dp else 1.dp,
                                        color = if (state.colorHex == hex) MaterialTheme.colorScheme.primary else Color.DarkGray,
                                        shape = CircleShape
                                    )
                                    .clickable { vm.updateBrush(colorHex = hex) }
                            )
                        }
                    }

                    Text("Boyut: ${state.brushSize.toInt()} px", fontSize = 12.sp)
                    Slider(
                        value = state.brushSize,
                        onValueChange = { vm.updateBrush(size = it) },
                        valueRange = 2f..250f
                    )

                    Text("Opaklık: ${(state.brushOpacity * 100).toInt()}%", fontSize = 12.sp)
                    Slider(
                        value = state.brushOpacity,
                        onValueChange = { vm.updateBrush(opacity = it) },
                        valueRange = 0.05f..1.0f
                    )

                    Text("Sertlik: ${(state.brushHardness * 100).toInt()}%", fontSize = 12.sp)
                    Slider(
                        value = state.brushHardness,
                        onValueChange = { vm.updateBrush(hardness = it) },
                        valueRange = 0.0f..1.0f
                    )
                }
            }
        }

        // Status / Busy indicator
        state.statusMessage?.let { msg ->
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(16.dp)
                    .clickable { vm.clearStatus() },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.94f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (state.isBusy) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    }
                    Text(msg, fontSize = 12.sp)
                }
            }
        }
    }
}
