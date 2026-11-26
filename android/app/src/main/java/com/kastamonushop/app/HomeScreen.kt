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

internal val StudioDarkScheme = darkColorScheme(
    primary = Color(0xFF4F9CFF),
    secondary = Color(0xFF2ED573),
    background = Color(0xFF19191E),
    surface = Color(0xFF202128),
    surfaceVariant = Color(0xFF2C2E38),
    onBackground = Color(0xFFFFFFFF),
    onSurface = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFFD8DCE8)
)

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
                    text = "v1.7.4",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        val homeCtx = LocalContext.current
        var crashReport by remember {
            mutableStateOf(KastamonuApp.startupError ?: CrashGuard.readLastCrash(homeCtx))
        }
        if (!crashReport.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF3A1F24),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Önceki Çökme Raporu",
                            color = Color(0xFFFF8A80),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                val cm = homeCtx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                cm?.setPrimaryClip(ClipData.newPlainText("crash", crashReport))
                            }) {
                                Text("Kopyala", color = Color(0xFFFFD600), fontSize = 11.sp)
                            }
                            TextButton(onClick = {
                                CrashGuard.clearLastCrash(homeCtx)
                                KastamonuApp.startupError = null
                                crashReport = null
                            }) {
                                Text("Kapat", color = Color.White, fontSize = 11.sp)
                            }
                        }
                    }
                    Text(
                        text = crashReport!!.lines().take(6).joinToString("\n"),
                        color = Color(0xFFFFCDD2),
                        fontSize = 10.sp,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
                    )
                }
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

