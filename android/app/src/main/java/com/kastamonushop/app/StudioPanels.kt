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

@Composable
internal fun CameraLightPanel(
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
internal fun GradientEditPanel(
    style: String, onStyle: (String) -> Unit,
    startHex: String, onStartHex: (String) -> Unit,
    endHex: String, onEndHex: (String) -> Unit,
    opacity: Float, onOpacity: (Float) -> Unit,
    onPreview: () -> Unit,
    onClose: () -> Unit,
    onApply: () -> Unit
) {
    val swatches = listOf("#4F9CFF", "#FF4D4F", "#FAAD14", "#52C41A", "#A855F7", "#FFFFFF", "#000000")
    var gradWheelTarget by remember { mutableIntStateOf(0) } // 1 = start, 2 = end
    if (gradWheelTarget != 0) {
        ProColorWheelDialog(
            initialHex = if (gradWheelTarget == 1) startHex else endHex,
            showOpacity = false,
            onDismiss = { gradWheelTarget = 0 },
            onConfirm = { picked, _ ->
                if (gradWheelTarget == 1) onStartHex(picked) else onEndHex(picked)
                gradWheelTarget = 0
                onPreview()
            }
        )
    }
    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BottomEditorHeader("Degrade Katmanı", "Tuvalde sürükleyerek yönü ayarla", "Uygula", onClose, onApply)
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("linear" to "Doğrusal", "radial" to "Dairesel", "reflected" to "Yansımalı", "diamond" to "Elmas").forEach { (st, lbl) ->
                FilterChip(selected = style == st, onClick = { onStyle(st); onPreview() }, label = { Text(lbl, fontSize = 11.sp) })
            }
        }
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Başlangıç", fontSize = 11.sp, color = Color(0xFFDCE0EC), modifier = Modifier.width(58.dp))
            swatches.forEach { hex ->
                val c = remember(hex) { Color(AndroidColor.parseColor(hex)) }
                Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(c).border(if (startHex.equals(hex, true)) 2.dp else 1.dp, if (startHex.equals(hex, true)) Color.White else Color(0xFF444858), CircleShape).clickable { onStartHex(hex); onPreview() })
            }
            Box(modifier = Modifier.size(26.dp).clip(CircleShape).background(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red))).border(1.5.dp, Color.White, CircleShape).clickable { gradWheelTarget = 1 })
        }
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Bitiş", fontSize = 11.sp, color = Color(0xFFDCE0EC), modifier = Modifier.width(58.dp))
            swatches.forEach { hex ->
                val c = remember(hex) { Color(AndroidColor.parseColor(hex)) }
                Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(c).border(if (endHex.equals(hex, true)) 2.dp else 1.dp, if (endHex.equals(hex, true)) Color.White else Color(0xFF444858), CircleShape).clickable { onEndHex(hex); onPreview() })
            }
            Box(modifier = Modifier.size(26.dp).clip(CircleShape).background(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red))).border(1.5.dp, Color.White, CircleShape).clickable { gradWheelTarget = 2 })
        }
        CompactSliderRow("Opaklık", "%${opacity.roundToInt()}", opacity, 10f..100f, 85f) { onOpacity(it); onPreview() }
    }
}

@Composable
internal fun ShapeEditPanel(
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
            var showShapeWheel by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier.size(26.dp).clip(CircleShape)
                    .background(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)))
                    .border(1.5.dp, Color.White, CircleShape)
                    .clickable { showShapeWheel = true }
            )
            if (showShapeWheel) {
                ProColorWheelDialog(
                    initialHex = fillHex.ifBlank { "#4F9CFF" },
                    showOpacity = false,
                    onDismiss = { showShapeWheel = false },
                    onConfirm = { picked, _ ->
                        onFillHex(picked)
                        showShapeWheel = false
                        onPreview()
                    }
                )
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
internal fun AutoEnhancePanel(onClose: () -> Unit, onPick: (String, String) -> Unit) {
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
internal fun CinemaLookPanel(
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
internal fun MotionBlurPanel(
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
internal fun RadialBlurPanel(
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
internal fun LiquifyEditPanel(
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
internal fun QuickActionsPanel(
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
internal fun HslMixerPanel(
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
internal fun PosterThreshPanel(
    isPoster: Boolean,
    isTouched: Boolean,
    onMode: (Boolean) -> Unit,
    posterLevels: Float, onPosterLevels: (Float) -> Unit,
    threshLevel: Float, onThreshLevel: (Float) -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
    onApply: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BottomEditorHeader(if (isPoster) "Posterize" else "Eşik (S&B)", "Çubuğu kaydırınca uygulanır — Sıfırla orijinale döner", "Uygula", onClose, onApply)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = isPoster, onClick = { onMode(true) }, label = { Text("Posterize", fontSize = 11.sp) })
            FilterChip(selected = !isPoster, onClick = { onMode(false) }, label = { Text("Eşik (S&B)", fontSize = 11.sp) })
        }
        if (isPoster) {
            CompactSliderRow(
                label = "Seviye",
                valueText = if (isTouched) "${posterLevels.roundToInt()}" else "Kapalı",
                value = posterLevels,
                range = 2f..24f,
                defaultValue = 8f,
                forceResetEnabled = isTouched,
                onReset = onReset,
                onChange = onPosterLevels
            )
        } else {
            CompactSliderRow(
                label = "Eşik",
                valueText = if (isTouched) "${threshLevel.roundToInt()}" else "Kapalı",
                value = threshLevel,
                range = 1f..254f,
                defaultValue = 128f,
                forceResetEnabled = isTouched,
                onReset = onReset,
                onChange = onThreshLevel
            )
        }
    }
}

@Composable
internal fun FilterGalleryPanel(
    thumbs: List<ImageBitmap>,
    kind: String, onKind: (String) -> Unit,
    amount: Float, onAmount: (Float) -> Unit,
    detail: Float, onDetail: (Float) -> Unit,
    onClose: () -> Unit,
    onApply: (String) -> Unit
) {
    val items = listOf(
        "none" to "Orijinal",
        "oilPaint" to "Yağlı Boya",
        "watercolor" to "Suluboya",
        "charcoal" to "Karakalem",
        "glowingEdges" to "Neon Kenar",
        "emboss" to "Kabartma",
        "mosaic" to "Mozaik",
        "crystallize" to "Kristal",
        "pointillize" to "Noktalama",
        "colorHalftone" to "Renkli Tram"
    )
    val selectedLabel = items.firstOrNull { it.first == kind }?.second ?: "Filtre"
    val isPixelate = kind == "mosaic" || kind == "crystallize" || kind == "pointillize" || kind == "colorHalftone"
    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BottomEditorHeader(
            title = if (kind == "none") "Filtre Galerisi" else "Filtre: $selectedLabel",
            subtitle = "Sanatsal & Pikselleştir — kendi fotoğrafınla 60 FPS önizleme",
            applyLabel = "Uygula",
            onClose = onClose,
            onApply = { onApply(selectedLabel) }
        )
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEachIndexed { idx, (id, lbl) ->
                val isSel = kind == id
                Column(
                    modifier = Modifier.width(62.dp).clickable { onKind(id) },
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
                            Icon(Icons.Default.AutoAwesome, contentDescription = lbl, tint = Color(0xFF8A8FA3), modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(lbl, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (isSel) MaterialTheme.colorScheme.primary else Color(0xFFDCE0EC), fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
        if (kind != "none") {
            CompactSliderRow(if (isPixelate) "Hücre" else "Şiddet", "%${amount.roundToInt()}", amount, 5f..100f, 45f, onChange = onAmount)
            if (!isPixelate) {
                CompactSliderRow("Detay", "%${detail.roundToInt()}", detail, 5f..100f, 50f, onChange = onDetail)
            }
        }
    }
}


@Composable
internal fun LightLivePanel(
    brightness: Float,
    contrast: Float,
    onBrightness: (Float) -> Unit,
    onContrast: (Float) -> Unit,
    onClose: () -> Unit,
    onApply: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BottomEditorHeader("Işık & Kontrast", "Kaydırdıkça anlık gör, beğenirsen Uygula de", "Uygula", onClose, onApply)
        CompactSliderRow("Parlaklık", "${brightness.roundToInt()}", brightness, -100f..100f, defaultValue = 0f, onChange = onBrightness)
        CompactSliderRow("Kontrast", "${contrast.roundToInt()}", contrast, -50f..100f, defaultValue = 0f, onChange = onContrast)
    }
}

@Composable
internal fun ColorLivePanel(
    saturation: Float,
    hue: Float,
    onSaturation: (Float) -> Unit,
    onHue: (Float) -> Unit,
    onClose: () -> Unit,
    onApply: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BottomEditorHeader("Renk & Canlılık", "Kaydırdıkça anlık gör, beğenirsen Uygula de", "Uygula", onClose, onApply)
        CompactSliderRow("Canlılık", "${saturation.roundToInt()}", saturation, -100f..100f, defaultValue = 0f, onChange = onSaturation)
        CompactSliderRow("Renk Tonu", "${hue.roundToInt()}°", hue, -180f..180f, defaultValue = 0f, onChange = onHue)
    }
}

@Composable
internal fun LevelsLivePanel(
    inBlack: Float,
    gamma: Float,
    inWhite: Float,
    onChange: (Float, Float, Float) -> Unit,
    onClose: () -> Unit,
    onApply: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BottomEditorHeader("Düzeyler (Levels)", "60 FPS canlı önizleme — Uygula ile katman oluştur", "Uygula", onClose, onApply)
        CompactSliderRow("Siyahlar", "${inBlack.roundToInt()}", inBlack, 0f..180f, defaultValue = 0f) {
            onChange(it.coerceAtMost(inWhite - 15f), gamma, inWhite)
        }
        CompactSliderRow("Orta Ton", String.format(java.util.Locale.US, "%.2f", gamma), gamma, 0.25f..2.5f, defaultValue = 1f) {
            onChange(inBlack, it, inWhite)
        }
        CompactSliderRow("Beyazlar", "${inWhite.roundToInt()}", inWhite, 75f..255f, defaultValue = 255f) {
            onChange(inBlack, gamma, it.coerceAtLeast(inBlack + 15f))
        }
    }
}

@Composable
internal fun ColorBalanceLivePanel(
    cr: Float,
    mg: Float,
    yb: Float,
    onChange: (Float, Float, Float) -> Unit,
    onClose: () -> Unit,
    onApply: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BottomEditorHeader("Renk Dengesi", "60 FPS canlı önizleme — Uygula ile katman oluştur", "Uygula", onClose, onApply)
        CompactSliderRow("Camgöbeği\nKırmızı", "${cr.roundToInt()}", cr, -100f..100f, defaultValue = 0f) { onChange(it, mg, yb) }
        CompactSliderRow("Macenta\nYeşil", "${mg.roundToInt()}", mg, -100f..100f, defaultValue = 0f) { onChange(cr, it, yb) }
        CompactSliderRow("Sarı / Mavi", "${yb.roundToInt()}", yb, -100f..100f, defaultValue = 0f) { onChange(cr, mg, it) }
    }
}


@Composable
internal fun LightDistortPanel(
    thumbs: List<ImageBitmap>,
    kind: String, onKind: (String) -> Unit,
    amount: Float, onAmount: (Float) -> Unit,
    detail: Float, onDetail: (Float) -> Unit,
    posX: Float, onPosX: (Float) -> Unit,
    posY: Float, onPosY: (Float) -> Unit,
    variant: String, onVariant: (String) -> Unit,
    onClose: () -> Unit,
    onApply: (String) -> Unit
) {
    val items = listOf(
        "none" to "Orijinal",
        "lensFlare" to "Mercek",
        "lensBlur" to "Bokeh",
        "wave" to "Dalga",
        "spherize" to "Küre",
        "twirl" to "Girdap"
    )
    val selectedLabel = items.firstOrNull { it.first == kind }?.second ?: "Efekt"
    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BottomEditorHeader(
            title = if (kind == "none") "Işık & Bükme" else "Efekt: $selectedLabel",
            subtitle = "Mercek, Bokeh, Dalga, Küre ve Girdap — canlı önizleme",
            applyLabel = "Uygula",
            onClose = onClose,
            onApply = { onApply(selectedLabel) }
        )
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEachIndexed { idx, (id, lbl) ->
                val isSel = kind == id
                Column(
                    modifier = Modifier.width(62.dp).clickable { onKind(id) },
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
                            Image(
                                bitmap = bmp,
                                contentDescription = lbl,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(lbl.take(2), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = lbl,
                        color = if (isSel) MaterialTheme.colorScheme.primary else Color(0xFFD0D4E0),
                        fontSize = 10.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
        when (kind) {
            "lensFlare" -> {
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("moviePrime" to "Sinema", "zoom" to "Zoom", "prime35" to "35mm", "prime105" to "105mm").forEach { (id, lbl) ->
                        FilterChip(selected = variant == id, onClick = { onVariant(id) }, label = { Text(lbl, fontSize = 10.sp) })
                    }
                }
                CompactSliderRow("Parlaklık", "${amount.roundToInt()}%", amount, 0f..100f, 55f, onChange = onAmount)
                CompactSliderRow("Konum X", "${posX.roundToInt()}%", posX, 0f..100f, 35f, onChange = onPosX)
                CompactSliderRow("Konum Y", "${posY.roundToInt()}%", posY, 0f..100f, 30f, onChange = onPosY)
            }
            "lensBlur" -> {
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("hexagon" to "Altıgen", "octagon" to "Sekizgen", "pentagon" to "Beşgen", "square" to "Kare").forEach { (id, lbl) ->
                        FilterChip(selected = variant == id, onClick = { onVariant(id) }, label = { Text(lbl, fontSize = 10.sp) })
                    }
                }
                CompactSliderRow("Bulanıklık", "${amount.roundToInt()}%", amount, 5f..100f, 40f, onChange = onAmount)
                CompactSliderRow("Parıltı", "${detail.roundToInt()}%", detail, 0f..100f, 40f, onChange = onDetail)
            }
            "wave" -> {
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("sine" to "Sinüs", "triangle" to "Üçgen", "square" to "Kare").forEach { (id, lbl) ->
                        FilterChip(selected = variant == id, onClick = { onVariant(id) }, label = { Text(lbl, fontSize = 10.sp) })
                    }
                }
                CompactSliderRow("Genlik", "${amount.roundToInt()}%", amount, 5f..100f, 40f, onChange = onAmount)
                CompactSliderRow("Sıklık", "${detail.roundToInt()}%", detail, 0f..100f, 55f, onChange = onDetail)
            }
            "spherize" -> {
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("normal" to "Normal", "horizontalOnly" to "Yatay", "verticalOnly" to "Dikey").forEach { (id, lbl) ->
                        FilterChip(selected = variant == id, onClick = { onVariant(id) }, label = { Text(lbl, fontSize = 10.sp) })
                    }
                }
                CompactSliderRow("Şiddet", "${amount.roundToInt()}%", amount, -100f..100f, 0f, onChange = onAmount)
            }
            "twirl" -> {
                CompactSliderRow("Girdap Açısı", "${amount.roundToInt()}°", amount, -360f..360f, 0f, onChange = onAmount)
            }
        }
    }
}


@Composable
internal fun TextStudioPanel(
    isEditingExisting: Boolean,
    onSwitchToNew: () -> Unit,
    onImportFont: () -> Unit,
    text: String, onText: (String) -> Unit,
    fonts: List<EditorViewModel.StudioFontOption>,
    selectedFont: String, onSelectFont: (String) -> Unit,
    colorHex: String, onColorHex: (String) -> Unit,
    align: String, onAlign: (String) -> Unit,
    bold: Boolean, onBold: (Boolean) -> Unit,
    italic: Boolean, onItalic: (Boolean) -> Unit,
    underline: Boolean, onUnderline: (Boolean) -> Unit,
    strikethrough: Boolean, onStrikethrough: (Boolean) -> Unit,
    caps: String, onCaps: (String) -> Unit,
    hScale: Float, onHScale: (Float) -> Unit,
    vScale: Float, onVScale: (Float) -> Unit,
    vertical: Boolean, onVertical: (Boolean) -> Unit,
    sizePx: Float, defaultSizePx: Float, onSizePx: (Float) -> Unit,
    tracking: Float, onTracking: (Float) -> Unit,
    lineHeight: Float, onLineHeight: (Float) -> Unit,
    warpStyle: String, onWarpStyle: (String) -> Unit,
    warpBend: Float, onWarpBend: (Float) -> Unit,
    warpHDist: Float, onWarpHDist: (Float) -> Unit,
    warpVDist: Float, onWarpVDist: (Float) -> Unit,
    onCreateMask: () -> Unit,
    onRasterize: () -> Unit,
    onClose: () -> Unit,
    onApply: () -> Unit
) {
    var tab by remember { mutableIntStateOf(0) } // 0 = Yazı & Font, 1 = Boyut & Biçim, 2 = Bükme & Maske
    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BottomEditorHeader(
            title = if (isEditingExisting) "Metni Düzenle" else "Tipografi Stüdyosu",
            subtitle = "4 köşeden büyüt/küçült, kutudan sürükle",
            applyLabel = if (isEditingExisting) "Güncelle" else "Uygula",
            onClose = onClose,
            onApply = onApply
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("Yazı & Font", fontSize = 11.sp) })
            FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("Boyut & Biçim", fontSize = 11.sp) })
            FilterChip(selected = tab == 2, onClick = { tab = 2 }, label = { Text("Bükme & Maske", fontSize = 11.sp) })
            if (isEditingExisting) {
                FilterChip(selected = false, onClick = onSwitchToNew, label = { Text("+ Yeni", fontSize = 10.sp) })
            }
        }
        when (tab) {
            0 -> {
                OutlinedTextField(
                    value = text,
                    onValueChange = onText,
                    singleLine = false,
                    maxLines = 3,
                    placeholder = { Text("Yazını buraya yaz...", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth()
                )
                // Real-Typeface Font Picker + Custom .ttf/.otf Import button
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1F2738))
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                            .clickable(onClick = onImportFont)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("+ Font Ekle", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(".ttf / .otf", color = Color(0xFF8C92A8), fontSize = 9.sp, maxLines = 1)
                    }
                    val listToShow = if (fonts.isNotEmpty()) fonts else listOf(
                        EditorViewModel.StudioFontOption("Montserrat", "Montserrat", "Modern", FontFamily.SansSerif)
                    )
                    listToShow.forEach { opt ->
                        val isSel = selectedFont.equals(opt.family, ignoreCase = true)
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSel) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else Color(0xFF232530))
                                .border(
                                    width = if (isSel) 1.5.dp else 1.dp,
                                    color = if (isSel) MaterialTheme.colorScheme.primary else Color(0xFF3A3D4D),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { onSelectFont(opt.family) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = opt.displayName,
                                fontFamily = opt.composeFont,
                                color = if (isSel) Color.White else Color(0xFFE0E3EE),
                                fontSize = 13.sp,
                                maxLines = 1
                            )
                            Text(
                                text = opt.category,
                                color = if (isSel) MaterialTheme.colorScheme.primary else Color(0xFF8C92A8),
                                fontSize = 9.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
                // Color palette + Bold / Italic / Underline / Strike / Caps
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("#FFFFFF", "#000000", "#FF4D4F", "#FFEC3D", "#2ED573", "#4F9CFF", "#B37FEB", "#FF8C00").forEach { hex ->
                        val c = Color(AndroidColor.parseColor(hex))
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(
                                    width = if (colorHex.equals(hex, true)) 2.5.dp else 1.dp,
                                    color = if (colorHex.equals(hex, true)) MaterialTheme.colorScheme.primary else Color.Gray,
                                    shape = CircleShape
                                )
                                .clickable { onColorHex(hex) }
                        )
                    }
                    var showTextColorWheel by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.sweepGradient(
                                    listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
                                )
                            )
                            .border(1.5.dp, Color.White, CircleShape)
                            .clickable { showTextColorWheel = true }
                    )
                    if (showTextColorWheel) {
                        ProColorWheelDialog(
                            initialHex = colorHex,
                            showOpacity = false,
                            onDismiss = { showTextColorWheel = false },
                            onConfirm = { picked, _ ->
                                onColorHex(picked)
                                showTextColorWheel = false
                            }
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    FilterChip(selected = bold, onClick = { onBold(!bold) }, label = { Text("K", fontWeight = FontWeight.Bold, fontSize = 11.sp) })
                    FilterChip(selected = italic, onClick = { onItalic(!italic) }, label = { Text("İ", fontStyle = FontStyle.Italic, fontSize = 11.sp) })
                    FilterChip(selected = underline, onClick = { onUnderline(!underline) }, label = { Text("A", textDecoration = TextDecoration.Underline, fontSize = 11.sp) })
                    FilterChip(selected = strikethrough, onClick = { onStrikethrough(!strikethrough) }, label = { Text("S", textDecoration = TextDecoration.LineThrough, fontSize = 11.sp) })
                    FilterChip(
                        selected = caps != "normal",
                        onClick = { onCaps(when (caps) { "normal" -> "all"; "all" -> "small"; else -> "normal" }) },
                        label = { Text(if (caps == "small") "Aa" else "AA", fontSize = 10.sp) }
                    )
                }
            }
            1 -> {
                // Quadratic precision slider for 6px..600px (fine control at tiny sizes!)
                BrushSizeSliderRow("Boyut", sizePx, minPx = 6f, maxPx = 600f, defaultPx = defaultSizePx, onChange = onSizePx)
                CompactSliderRow("Harf Arası", "${tracking.roundToInt()}", tracking, -80f..400f, 0f, onChange = onTracking)
                CompactSliderRow(
                    label = "Satır Arası",
                    valueText = if (abs(lineHeight - 1.2f) < 0.03f) "Oto" else String.format(java.util.Locale.US, "%.2fx", lineHeight),
                    value = lineHeight,
                    range = 0.8f..2.4f,
                    defaultValue = 1.2f,
                    onChange = onLineHeight
                )
                CompactSliderRow("Yatay Ölçek", "${hScale.roundToInt()}%", hScale, 50f..200f, 100f, onChange = onHScale)
                CompactSliderRow("Dikey Ölçek", "${vScale.roundToInt()}%", vScale, 50f..200f, 100f, onChange = onVScale)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("left" to "Sol", "center" to "Orta", "right" to "Sağ").forEach { (id, lbl) ->
                        FilterChip(selected = align == id, onClick = { onAlign(id) }, label = { Text(lbl, fontSize = 10.sp) })
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    FilterChip(selected = !vertical, onClick = { onVertical(false) }, label = { Text("Yatay", fontSize = 10.sp) })
                    FilterChip(selected = vertical, onClick = { onVertical(true) }, label = { Text("Dikey", fontSize = 10.sp) })
                }
            }
            else -> {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "none" to "Yok",
                        "arc" to "Yay",
                        "arch" to "Kemer",
                        "flag" to "Bayrak",
                        "wave" to "Dalga",
                        "bulge" to "Kabartma",
                        "fish" to "Balık",
                        "rise" to "Yükselme",
                        "twist" to "Büküm"
                    ).forEach { (id, lbl) ->
                        FilterChip(selected = warpStyle == id, onClick = { onWarpStyle(id) }, label = { Text(lbl, fontSize = 10.sp) })
                    }
                }
                if (warpStyle != "none") {
                    CompactSliderRow("Bükme", "${warpBend.roundToInt()}%", warpBend, -100f..100f, 50f, onChange = onWarpBend)
                    CompactSliderRow("Yatay Eğim", "${warpHDist.roundToInt()}%", warpHDist, -100f..100f, 0f, onChange = onWarpHDist)
                    CompactSliderRow("Dikey Eğim", "${warpVDist.roundToInt()}%", warpVDist, -100f..100f, 0f, onChange = onWarpVDist)
                }
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(onClick = onCreateMask, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                        Text("Metin Maskesi (Seçim)", fontSize = 11.sp)
                    }
                    if (isEditingExisting) {
                        FilledTonalButton(onClick = onRasterize, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                            Text("Piksele Çevir (Raster)", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}


@Composable
internal fun FilterMenuPanel(
    onClose: () -> Unit,
    onBlur: () -> Unit,
    onSharpen: () -> Unit,
    onMotion: () -> Unit,
    onRadial: () -> Unit,
    onGallery: () -> Unit,
    onLightDistort: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Row(
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SlimToolChip(Icons.Default.BlurOn, "Bulanık", false, onBlur)
            SlimToolChip(Icons.Default.Details, "Keskin", false, onSharpen)
            SlimToolChip(Icons.Default.FastForward, "Hareket", false, onMotion)
            SlimToolChip(Icons.Default.BlurCircular, "Radyal", false, onRadial)
            SlimToolChip(Icons.Default.AutoAwesome, "Galeri", false, onGallery)
            SlimToolChip(Icons.Default.WbSunny, "Işık/Bük", false, onLightDistort)
        }
    }
}

@Composable
internal fun BlurEditPanel(
    radius: Float,
    onRadius: (Float) -> Unit,
    onClose: () -> Unit,
    onApply: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BottomEditorHeader("Bulanıklaştır", "Kaydırdıkça anlık gör, beğenirsen Uygula de", "Uygula", onClose, onApply)
        CompactSliderRow("Yarıçap", "${radius.roundToInt()}px", radius, 0f..60f, defaultValue = 0f, onChange = onRadius)
    }
}

@Composable
internal fun SharpenEditPanel(
    amount: Float,
    onAmount: (Float) -> Unit,
    onClose: () -> Unit,
    onApply: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BottomEditorHeader("Keskinleştir", "Kaydırdıkça anlık gör, beğenirsen Uygula de", "Uygula", onClose, onApply)
        CompactSliderRow("Miktar", "${amount.roundToInt()}%", amount, 0f..300f, defaultValue = 0f, onChange = onAmount)
    }
}

@Composable
internal fun DrawMenuPanel(
    vm: EditorViewModel,
    state: EditorUiState,
    drawScrollState: androidx.compose.foundation.ScrollState,
    bucketTolerance: Float,
    onBucketTolerance: (Float) -> Unit,
    bucketContiguous: Boolean,
    onBucketContiguous: (Boolean) -> Unit,
    onBrushSizePreview: () -> Unit,
    onImportAbr: () -> Unit,
    onClose: () -> Unit
) {
    val activeColor = remember(state.colorHex) {
        runCatching { Color(AndroidColor.parseColor(state.colorHex)) }.getOrDefault(Color.White)
    }
    var showColorWheel by remember { mutableStateOf(false) }
    var showBrushFineTune by remember { mutableStateOf(false) }
    var brushSubTab by remember { mutableIntStateOf(0) } // 0 = Temel, 1 = Dinamikler, 2 = .abr Fırçalar

    if (showColorWheel) {
        ProColorWheelDialog(
            initialHex = state.colorHex,
            initialOpacity = state.brushOpacity,
            showOpacity = true,
            onPickEyedropper = { vm.setTool(ActiveTool.EYEDROPPER) },
            onDismiss = { showColorWheel = false },
            onConfirm = { pickedHex, pickedOpacity ->
                vm.updateBrush(colorHex = pickedHex, opacity = pickedOpacity)
                showColorWheel = false
            }
        )
    }

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
            title = "Çizim & Rötuş",
            subtitle = when (state.activeTool) {
                ActiveTool.BRUSH -> {
                    val presetTag = state.activeBrushPresetName?.let { " · $it" } ?: ""
                    if (state.rulerEnabled) "Cetvel açık · düz çizgi çek$presetTag"
                    else "Fırça aktif$presetTag · iki parmakla kaydır"
                }
                ActiveTool.ERASER -> if (state.rulerEnabled) "Silgi + Cetvel açık" else "Silgi aktif"
                ActiveTool.MIXER_BRUSH -> "Karıştırıcı Fırça · ıslak boya gibi karıştır"
                ActiveTool.COLOR_REPLACE -> "Renk Değiştirici · dokuyu koruyarak ton değiştir"
                ActiveTool.EYEDROPPER -> "Fotoğrafa dokunarak renk al (${state.colorHex})"
                ActiveTool.BUCKET -> "Boya Kovası · doldurmak istediğin alana dokun"
                ActiveTool.SPOT_HEAL -> "Leke Giderme · kusurun üzerinden parmağınla geç"
                ActiveTool.PATCH -> "Yama · seçili alanı kaynak dokuya sürükle"
                ActiveTool.RED_EYE -> "Kırmızı Göz · kırmızı göz bebeğine dokun"
                ActiveTool.SMUDGE -> "Dağıtma · dokuyu parmağınla yumuşakça yay"
                ActiveTool.DODGE -> "Aydınlat · dokunduğun bölgeyi açar"
                ActiveTool.BURN -> "Koyulaştır · dokunduğun bölgeyi gölgeler"
                ActiveTool.SPONGE_SAT -> "Sünger (+) · bölgesel doygunluğu artırır"
                ActiveTool.SPONGE_DESAT -> "Sünger (-) · bölgesel doygunluğu azaltır"
                ActiveTool.BLUR_BRUSH -> "Bulanık Fırça · boyanan alanı yumuşatır"
                ActiveTool.SHARPEN_BRUSH -> "Keskin Fırça · boyanan detayı keskinleştirir"
                ActiveTool.CLONE_STAMP -> if (state.pickingCloneSource) "Klon kaynağı seç: fotoğrafa dokun" else "Klonla · boyamak istediğin yere sürükle"
                ActiveTool.SELECT_RECT -> "Dikdörtgen/Elips alan seç"
                ActiveTool.SELECT_WAND -> "Sihirli Değnek · renge dokunarak seç"
                ActiveTool.SELECT_QUICK -> "Akıllı Fırça · nesne üzerinden geç"
                ActiveTool.SELECT_BRUSH_ADD -> "Elle Ekle (+) · parmağınla seçime boya"
                ActiveTool.SELECT_BRUSH_SUB -> "Elle Sil (-) · yanlış seçilen yerleri sil"
                ActiveTool.NONE -> "Çizim veya Rötuş için bir araç seç"
            },
            onClose = { onClose() }
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
                onClick = { vm.setTool(if (state.activeTool == ActiveTool.BRUSH) ActiveTool.NONE else ActiveTool.BRUSH) }
            )
            CircularDockButton(
                icon = Icons.Default.AutoFixNormal,
                label = "Silgi",
                selected = state.activeTool == ActiveTool.ERASER,
                onClick = { vm.setTool(if (state.activeTool == ActiveTool.ERASER) ActiveTool.NONE else ActiveTool.ERASER) }
            )
            CircularDockButton(
                icon = Icons.Default.WaterDrop,
                label = "Karıştır",
                selected = state.activeTool == ActiveTool.MIXER_BRUSH,
                accentDot = activeColor,
                onClick = { vm.setTool(if (state.activeTool == ActiveTool.MIXER_BRUSH) ActiveTool.NONE else ActiveTool.MIXER_BRUSH) }
            )
            CircularDockButton(
                icon = Icons.Default.InvertColors,
                label = "Renk Değ.",
                selected = state.activeTool == ActiveTool.COLOR_REPLACE,
                accentDot = activeColor,
                onClick = { vm.setTool(if (state.activeTool == ActiveTool.COLOR_REPLACE) ActiveTool.NONE else ActiveTool.COLOR_REPLACE) }
            )
            CircularDockButton(
                icon = Icons.Default.FormatColorFill,
                label = "Kova",
                selected = state.activeTool == ActiveTool.BUCKET,
                accentDot = activeColor,
                onClick = { vm.setTool(ActiveTool.BUCKET) }
            )
            CircularDockButton(
                icon = Icons.Default.Colorize,
                label = "Damlalık",
                selected = state.activeTool == ActiveTool.EYEDROPPER,
                accentDot = activeColor,
                onClick = { vm.setTool(if (state.activeTool == ActiveTool.EYEDROPPER) ActiveTool.BRUSH else ActiveTool.EYEDROPPER) }
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
                onClick = { vm.setTool(if (state.activeTool == ActiveTool.SPOT_HEAL) ActiveTool.NONE else ActiveTool.SPOT_HEAL) }
            )
            CircularDockButton(
                icon = Icons.Default.Texture,
                label = "Yama",
                selected = state.activeTool == ActiveTool.PATCH,
                onClick = { vm.setTool(if (state.activeTool == ActiveTool.PATCH) ActiveTool.NONE else ActiveTool.PATCH) }
            )
            CircularDockButton(
                icon = Icons.Default.RemoveRedEye,
                label = "Kır. Göz",
                selected = state.activeTool == ActiveTool.RED_EYE,
                onClick = { vm.setTool(if (state.activeTool == ActiveTool.RED_EYE) ActiveTool.NONE else ActiveTool.RED_EYE) }
            )
            CircularDockButton(
                icon = Icons.Default.Gesture,
                label = "Dağıt",
                selected = state.activeTool == ActiveTool.SMUDGE,
                onClick = { vm.setTool(if (state.activeTool == ActiveTool.SMUDGE) ActiveTool.NONE else ActiveTool.SMUDGE) }
            )
            CircularDockButton(
                icon = Icons.Default.WbSunny,
                label = "Aydınlat",
                selected = state.activeTool == ActiveTool.DODGE,
                onClick = { vm.setTool(if (state.activeTool == ActiveTool.DODGE) ActiveTool.NONE else ActiveTool.DODGE) }
            )
            CircularDockButton(
                icon = Icons.Default.NightsStay,
                label = "Koyulaştır",
                selected = state.activeTool == ActiveTool.BURN,
                onClick = { vm.setTool(if (state.activeTool == ActiveTool.BURN) ActiveTool.NONE else ActiveTool.BURN) }
            )
            CircularDockButton(
                icon = Icons.Default.Tonality,
                label = "Sünger+",
                selected = state.activeTool == ActiveTool.SPONGE_SAT,
                onClick = { vm.setTool(if (state.activeTool == ActiveTool.SPONGE_SAT) ActiveTool.NONE else ActiveTool.SPONGE_SAT) }
            )
            CircularDockButton(
                icon = Icons.Default.FilterBAndW,
                label = "Sünger-",
                selected = state.activeTool == ActiveTool.SPONGE_DESAT,
                onClick = { vm.setTool(if (state.activeTool == ActiveTool.SPONGE_DESAT) ActiveTool.NONE else ActiveTool.SPONGE_DESAT) }
            )
            CircularDockButton(
                icon = Icons.Default.BlurOn,
                label = "Bulanık",
                selected = state.activeTool == ActiveTool.BLUR_BRUSH,
                onClick = { vm.setTool(if (state.activeTool == ActiveTool.BLUR_BRUSH) ActiveTool.NONE else ActiveTool.BLUR_BRUSH) }
            )
            CircularDockButton(
                icon = Icons.Default.Details,
                label = "Keskin",
                selected = state.activeTool == ActiveTool.SHARPEN_BRUSH,
                onClick = { vm.setTool(if (state.activeTool == ActiveTool.SHARPEN_BRUSH) ActiveTool.NONE else ActiveTool.SHARPEN_BRUSH) }
            )
            CircularDockButton(
                icon = Icons.Default.ContentCopy,
                label = "Klonla",
                selected = state.activeTool == ActiveTool.CLONE_STAMP,
                onClick = { vm.setTool(if (state.activeTool == ActiveTool.CLONE_STAMP) ActiveTool.NONE else ActiveTool.CLONE_STAMP) }
            )
            CircularDockButton(
                icon = Icons.Default.Block,
                label = "Bırak",
                selected = state.activeTool == ActiveTool.NONE,
                onClick = { vm.setTool(ActiveTool.NONE) }
            )
        }

        val palette = listOf(
            "#FF4D4F", "#FFA940", "#FFEC3D", "#52C41A",
            "#13C2C2", "#1677FF", "#722ED1", "#FFFFFF", "#000000"
        )

        if (state.activeTool == ActiveTool.BRUSH) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("Temel", "Dinamikler", ".abr Fırçalar").forEachIndexed { idx, lbl ->
                    FilterChip(
                        selected = brushSubTab == idx,
                        onClick = {
                            brushSubTab = idx
                            if (idx == 2) vm.loadBrushPresets()
                        },
                        label = { Text(lbl, fontSize = 10.sp) }
                    )
                }
            }

            when (brushSubTab) {
                0 -> {
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
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.sweepGradient(
                                        listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
                                    )
                                )
                                .border(2.dp, Color.White, CircleShape)
                                .clickable { showColorWheel = true }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            BrushSizeSliderRow("Boyut", state.brushSize, minPx = 1f, maxPx = 250f, defaultPx = 28f) {
                                vm.updateBrush(size = it)
                                onBrushSizePreview()
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
                1 -> {
                    CompactSliderRow("Aralık", "%${(state.brushSpacing * 100).roundToInt()}", state.brushSpacing, 0.05f..2.0f, defaultValue = 0.15f) {
                        vm.updateBrushDynamics(spacing = it)
                    }
                    CompactSliderRow("Dağılma", "%${(state.brushScatter * 100).roundToInt()}", state.brushScatter, 0f..3.0f, defaultValue = 0f) {
                        vm.updateBrushDynamics(scatter = it)
                    }
                    CompactSliderRow("Boyut Tit.", "%${(state.brushSizeJitter * 100).roundToInt()}", state.brushSizeJitter, 0f..1f, defaultValue = 0f) {
                        vm.updateBrushDynamics(sizeJitter = it)
                    }
                    CompactSliderRow("Açı Tit.", "%${(state.brushAngleJitter * 100).roundToInt()}", state.brushAngleJitter, 0f..1f, defaultValue = 0f) {
                        vm.updateBrushDynamics(angleJitter = it)
                    }
                    CompactSliderRow("Opak. Tit.", "%${(state.brushOpacityJitter * 100).roundToInt()}", state.brushOpacityJitter, 0f..1f, defaultValue = 0f) {
                        vm.updateBrushDynamics(opacityJitter = it)
                    }
                }
                else -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { onImportAbr() },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(".abr Yükle", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { vm.resetBrushToRound() },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Yuvarlak Fırça", fontSize = 11.sp)
                        }
                    }
                    if (state.brushPresets.isEmpty()) {
                        Text(
                            text = "Photoshop .abr fırça paketi yükleyip buradan seçebilirsin.",
                            color = Color(0xFF9AA0B2),
                            fontSize = 11.sp
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            state.brushPresets.take(40).forEach { preset ->
                                val isSel = state.activeBrushPresetName.equals(preset.name, ignoreCase = true)
                                FilterChip(
                                    selected = isSel,
                                    onClick = { vm.selectBrushPreset(preset.name) },
                                    label = {
                                        Text(
                                            text = if (preset.hasSampledTip) "✦ ${preset.name.take(18)}" else preset.name.take(18),
                                            fontSize = 10.sp
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        } else if (state.activeTool == ActiveTool.MIXER_BRUSH) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                palette.take(7).forEach { hex ->
                    val c = Color(AndroidColor.parseColor(hex))
                    Box(
                        modifier = Modifier.size(22.dp).clip(CircleShape).background(c)
                            .border(if (state.colorHex.equals(hex, true)) 2.dp else 1.dp, if (state.colorHex.equals(hex, true)) Color.White else Color.Gray, CircleShape)
                            .clickable { vm.updateBrush(colorHex = hex) }
                    )
                }
                Box(
                    modifier = Modifier.size(26.dp).clip(CircleShape)
                        .background(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)))
                        .border(1.5.dp, Color.White, CircleShape)
                        .clickable { showColorWheel = true }
                )
                FilterChip(
                    selected = state.mixerSampleAll,
                    onClick = { vm.updateMixerParams(sampleAll = !state.mixerSampleAll) },
                    label = { Text("Tümü", fontSize = 10.sp) }
                )
            }
            BrushSizeSliderRow("Boyut", state.brushSize, minPx = 4f, maxPx = 250f, defaultPx = 48f) {
                vm.updateBrush(size = it)
                onBrushSizePreview()
            }
            CompactSliderRow("Islaklık", "%${state.mixerWet.roundToInt()}", state.mixerWet, 0f..100f, defaultValue = 55f) {
                vm.updateMixerParams(wet = it)
            }
            CompactSliderRow("Yük", "%${state.mixerLoad.roundToInt()}", state.mixerLoad, 0f..100f, defaultValue = 50f) {
                vm.updateMixerParams(load = it)
            }
            CompactSliderRow("Karışım", "%${state.mixerMix.roundToInt()}", state.mixerMix, 0f..100f, defaultValue = 50f) {
                vm.updateMixerParams(mix = it)
            }
        } else if (state.activeTool == ActiveTool.COLOR_REPLACE) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                palette.forEach { hex ->
                    val c = Color(AndroidColor.parseColor(hex))
                    Box(
                        modifier = Modifier.size(22.dp).clip(CircleShape).background(c)
                            .border(if (state.colorHex.equals(hex, true)) 2.dp else 1.dp, if (state.colorHex.equals(hex, true)) Color.White else Color.Gray, CircleShape)
                            .clickable { vm.updateBrush(colorHex = hex) }
                    )
                }
                Box(
                    modifier = Modifier.size(26.dp).clip(CircleShape)
                        .background(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)))
                        .border(1.5.dp, Color.White, CircleShape)
                        .clickable { showColorWheel = true }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("color" to "Renk", "hue" to "Ton", "saturation" to "Doygunluk", "luminosity" to "Parlaklık").forEach { (k, lbl) ->
                    FilterChip(
                        selected = state.colorReplaceMode == k,
                        onClick = { vm.updateColorReplaceParams(mode = k) },
                        label = { Text(lbl, fontSize = 10.sp) }
                    )
                }
            }
            BrushSizeSliderRow("Boyut", state.brushSize, minPx = 4f, maxPx = 250f, defaultPx = 48f) {
                vm.updateBrush(size = it)
                onBrushSizePreview()
            }
            CompactSliderRow("Tolerans", "%${state.colorReplaceTolerance.roundToInt()}", state.colorReplaceTolerance, 1f..100f, defaultValue = 32f) {
                vm.updateColorReplaceParams(tolerance = it)
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
                Box(
                    modifier = Modifier.size(26.dp).clip(CircleShape)
                        .background(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)))
                        .border(1.5.dp, Color.White, CircleShape)
                        .clickable { showColorWheel = true }
                )
                FilterChip(
                    selected = bucketContiguous,
                    onClick = { onBucketContiguous(!bucketContiguous) },
                    label = { Text(if (bucketContiguous) "Bitişik" else "Tümü", fontSize = 10.sp) }
                )
            }
            CompactSliderRow("Tolerans", "${bucketTolerance.roundToInt()}", bucketTolerance, 0f..200f, defaultValue = 36f) {
                onBucketTolerance(it)
            }
        } else if (state.activeTool == ActiveTool.PATCH) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = state.patchMode == "source",
                    onClick = { vm.updatePatchMode("source") },
                    label = { Text("Kaynak Modu", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = state.patchMode == "destination",
                    onClick = { vm.updatePatchMode("destination") },
                    label = { Text("Hedef Modu", fontSize = 11.sp) }
                )
            }
            Text(
                text = if (state.selectionBounds == null)
                    "Önce Seçim aracıyla alanı seç, sonra Yama ile temiz dokuya sürükle."
                else
                    "Seçim hazır · seçili alanı temiz doku üzerine sürükleyip bırak.",
                color = Color(0xFFAEB4C4),
                fontSize = 11.sp
            )
        } else if (state.activeTool == ActiveTool.RED_EYE) {
            CompactSliderRow("Göz Bebeği", "%${state.redEyePupilSize.roundToInt()}", state.redEyePupilSize, 10f..100f, defaultValue = 50f) {
                vm.updateRedEyeParams(pupilSize = it)
            }
            CompactSliderRow("Karartma", "%${state.redEyeDarken.roundToInt()}", state.redEyeDarken, 10f..100f, defaultValue = 55f) {
                vm.updateRedEyeParams(darken = it)
            }
        } else if (state.activeTool == ActiveTool.ERASER) {
            BrushSizeSliderRow("Boyut", state.brushSize, minPx = 1f, maxPx = 250f, defaultPx = 28f) {
                vm.updateBrush(size = it)
                onBrushSizePreview()
            }
            CompactSliderRow("Sertlik", "${(state.brushHardness * 100).toInt()}%", state.brushHardness, 0f..1f, defaultValue = 0.85f) {
                vm.updateBrush(hardness = it)
            }
        } else if (
            state.activeTool == ActiveTool.SPOT_HEAL ||
            state.activeTool == ActiveTool.SMUDGE ||
            state.activeTool == ActiveTool.DODGE ||
            state.activeTool == ActiveTool.BURN ||
            state.activeTool == ActiveTool.SPONGE_SAT ||
            state.activeTool == ActiveTool.SPONGE_DESAT ||
            state.activeTool == ActiveTool.BLUR_BRUSH ||
            state.activeTool == ActiveTool.SHARPEN_BRUSH
        ) {
            BrushSizeSliderRow("Boyut", state.brushSize, minPx = 1f, maxPx = 250f, defaultPx = 48f) {
                vm.updateBrush(size = it)
                onBrushSizePreview()
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
                onBrushSizePreview()
            }
            CompactSliderRow("Opaklık", "${(state.brushOpacity * 100).toInt()}%", state.brushOpacity, 0.05f..1f, defaultValue = 1f) {
                vm.updateBrush(opacity = it)
            }
        }
    }
}

@Composable
internal fun SelectMenuPanel(
    vm: EditorViewModel,
    state: EditorUiState,
    selectScrollState: androidx.compose.foundation.ScrollState,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Row(
                modifier = Modifier.weight(1f).horizontalScroll(selectScrollState),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SlimToolChip(Icons.Default.AutoFixHigh, "Akıllı", state.activeTool == ActiveTool.SELECT_QUICK) {
                    vm.setTool(ActiveTool.SELECT_QUICK)
                }
                SlimToolChip(Icons.Default.AddCircleOutline, "Ekle+", state.activeTool == ActiveTool.SELECT_BRUSH_ADD) {
                    vm.setTool(ActiveTool.SELECT_BRUSH_ADD)
                }
                SlimToolChip(Icons.Default.RemoveCircleOutline, "Sil-", state.activeTool == ActiveTool.SELECT_BRUSH_SUB) {
                    vm.setTool(ActiveTool.SELECT_BRUSH_SUB)
                }
                SlimToolChip(Icons.Default.AutoAwesome, "Değnek", state.activeTool == ActiveTool.SELECT_WAND) {
                    vm.setTool(ActiveTool.SELECT_WAND)
                }
                SlimToolChip(Icons.Default.CropSquare, if (state.selectEllipse) "Elips" else "Kutu", state.activeTool == ActiveTool.SELECT_RECT) {
                    if (state.activeTool == ActiveTool.SELECT_RECT) vm.toggleSelectEllipse()
                    else vm.setTool(ActiveTool.SELECT_RECT)
                }
                SlimToolChip(Icons.Default.PersonSearch, "Konuyu Seç", false) {
                    vm.selectSubject()
                }
            }
        }
        if (state.selectionBounds != null) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(selected = false, onClick = { vm.layerViaCopy() }, label = { Text("Katmana Kopyala", fontSize = 10.sp) })
                FilterChip(selected = false, onClick = { vm.selectInverse() }, label = { Text("Ters Çevir", fontSize = 10.sp) })
                FilterChip(selected = false, onClick = { vm.featherSelection(6f) }, label = { Text("Yumuşat", fontSize = 10.sp) })
                FilterChip(selected = false, onClick = { vm.contentAwareRemove() }, label = { Text("Nesneyi Sil", fontSize = 10.sp) })
                FilterChip(selected = false, onClick = { vm.clearSelection() }, label = { Text("Temizle", fontSize = 10.sp) })
                FilterChip(selected = false, onClick = { vm.revealSelectionMask() }, label = { Text("Maskele", fontSize = 10.sp) })
                FilterChip(selected = false, onClick = { vm.deselect() }, label = { Text("Bırak", fontSize = 10.sp) })
            }
        }
    }
}

@Composable
internal fun LayerMaskPanel(
    layerName: String,
    hasMask: Boolean,
    maskEnabled: Boolean,
    hasSelection: Boolean,
    maskPaintMode: Boolean,
    onAddRevealAll: () -> Unit,
    onAddHideAll: () -> Unit,
    onAddFromSelection: () -> Unit,
    onPaintHide: () -> Unit,
    onPaintReveal: () -> Unit,
    onToggleEnabled: () -> Unit,
    onInvert: () -> Unit,
    onApplyMask: () -> Unit,
    onDeleteMask: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BottomEditorHeader(
            title = "Katman Maskesi: $layerName",
            subtitle = if (hasMask) "Siyah ile gizle, Beyaz ile geri getir (tahribatsız)" else "Katmana tahribatsız maske ekle",
            applyLabel = "Tamam",
            onClose = onClose,
            onApply = onClose
        )
        if (!hasMask) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onAddRevealAll, shape = RoundedCornerShape(12.dp)) {
                    Text("Beyaz Maske (Göster)", fontSize = 11.sp)
                }
                OutlinedButton(onClick = onAddHideAll, shape = RoundedCornerShape(12.dp)) {
                    Text("Siyah Maske (Gizle)", fontSize = 11.sp, color = Color.White)
                }
                if (hasSelection) {
                    OutlinedButton(onClick = onAddFromSelection, shape = RoundedCornerShape(12.dp)) {
                        Text("Seçimden Maske", fontSize = 11.sp, color = Color(0xFF4F9CFF))
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = maskPaintMode,
                    onClick = onPaintHide,
                    label = { Text("Siyah Boya (Gizle)", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = false,
                    onClick = onPaintReveal,
                    label = { Text("Beyaz Boya (Göster)", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = !maskEnabled,
                    onClick = onToggleEnabled,
                    label = { Text(if (maskEnabled) "Kapat" else "Aç", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = false,
                    onClick = onInvert,
                    label = { Text("Ters Çevir", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = false,
                    onClick = onApplyMask,
                    label = { Text("Uygula", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = false,
                    onClick = onDeleteMask,
                    label = { Text("Maskeyi Sil", fontSize = 11.sp, color = Color(0xFFFF6B6B)) }
                )
            }
        }
    }
}

@Composable
internal fun BlendIfPanel(
    layerName: String,
    thisBlack: Float, onThisBlack: (Float) -> Unit,
    thisWhite: Float, onThisWhite: (Float) -> Unit,
    underBlack: Float, onUnderBlack: (Float) -> Unit,
    underWhite: Float, onUnderWhite: (Float) -> Unit,
    feather: Float, onFeather: (Float) -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
    onApply: () -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        BottomEditorHeader(
            title = "Ton Karışımı (Blend If): $layerName",
            subtitle = "Karanlık veya aydınlık tonları yumuşakça harmanla",
            applyLabel = "Uygula",
            onClose = onClose,
            onApply = onApply
        )
        CompactSliderRow("Bu Katman Koyu", "${thisBlack.roundToInt()}", thisBlack, 0f..250f, defaultValue = 0f, onChange = onThisBlack)
        CompactSliderRow("Bu Katman Açık", "${thisWhite.roundToInt()}", thisWhite, 5f..255f, defaultValue = 255f, onChange = onThisWhite)
        CompactSliderRow("Alt Katman Koyu", "${underBlack.roundToInt()}", underBlack, 0f..250f, defaultValue = 0f, onChange = onUnderBlack)
        CompactSliderRow("Alt Katman Açık", "${underWhite.roundToInt()}", underWhite, 5f..255f, defaultValue = 255f, onChange = onUnderWhite)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                CompactSliderRow("Yumuşatma", "${feather.roundToInt()}", feather, 0f..80f, defaultValue = 20f, onChange = onFeather)
            }
            TextButton(onClick = onReset) {
                Text("Sıfırla", fontSize = 11.sp, color = Color(0xFFFF8A80))
            }
        }
    }
}
