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
internal fun PuppetWarpCanvasOverlay(
    canvasViewRef: VulkanCanvasView?,
    cameraTick: Int,
    docW: Float,
    docH: Float,
    pins: List<PuppetPinPoint>,
    selectedIdx: Int,
    showMesh: Boolean = false,
    meshSegments: List<Float> = emptyList(),
    onSelectPin: (Int) -> Unit,
    onPushUndo: (List<PuppetPinPoint>) -> Unit,
    onAddPin: (Float, Float) -> Unit,
    onDeletePin: (Int) -> Unit,
    onMovePin: (Int, Float, Float) -> Unit,
    onRestorePins: (List<PuppetPinPoint>) -> Unit,
    onPinchPan: (Float, Float, Float, Float, Float) -> Unit
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val hitRadiusPx = with(density) { 38.dp.toPx() }
    val handleOffsetYPx = with(density) { 42.dp.toPx() } // Touch-drag handle sits BELOW pin so finger never hides pin!
    val deleteOffsetXPx = with(density) { 26.dp.toPx() }
    val deleteOffsetYPx = with(density) { -24.dp.toPx() }
    val deleteHitPx = with(density) { 22.dp.toPx() }
    val normalRadiusPx = with(density) { 4.5.dp.toPx() }
    val selectedRadiusPx = with(density) { 6.5.dp.toPx() }

    val curPins by rememberUpdatedState(pins)
    val curSelIdx by rememberUpdatedState(selectedIdx)
    val curCanvas by rememberUpdatedState(canvasViewRef)
    val curOnSelect by rememberUpdatedState(onSelectPin)
    val curOnPushUndo by rememberUpdatedState(onPushUndo)
    val curOnAdd by rememberUpdatedState(onAddPin)
    val curOnDelete by rememberUpdatedState(onDeletePin)
    val curOnMove by rememberUpdatedState(onMovePin)
    val curOnRestore by rememberUpdatedState(onRestorePins)
    val curOnPinch by rememberUpdatedState(onPinchPan)

    var activeDragFinger by remember { mutableStateOf<Offset?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(docW, docH) {
                awaitPointerEventScope {
                    while (true) {
                        val firstDown = awaitFirstDown(requireUnconsumed = false)
                        val v = curCanvas ?: continue
                        val startPos = firstDown.position
                        val snapshotBefore = curPins.toList()

                        // 1. Check floating × delete button on currently selected pin first
                        if (curSelIdx in curPins.indices) {
                            val sp = curPins[curSelIdx]
                            val (spx, spy) = v.docToScreen(sp.dstX, sp.dstY)
                            val delX = spx + deleteOffsetXPx
                            val delY = spy + deleteOffsetYPx
                            if (hypot((startPos.x - delX).toDouble(), (startPos.y - delY).toDouble()).toFloat() <= deleteHitPx) {
                                firstDown.consume()
                                // Wait for release so it feels crisp
                                while (true) {
                                    val ev = awaitPointerEvent()
                                    if (ev.changes.none { it.pressed }) break
                                    ev.changes.forEach { it.consume() }
                                }
                                curOnDelete(curSelIdx)
                                continue
                            }
                        }

                        // 2. Check if user grabbed the below-pin offset drag handle of the selected pin
                        var grabbedIdx = -1
                        var grabbedViaOffsetHandle = false
                        if (curSelIdx in curPins.indices) {
                            val sp = curPins[curSelIdx]
                            val (spx, spy) = v.docToScreen(sp.dstX, sp.dstY)
                            val dHandle = hypot((startPos.x - spx).toDouble(), (startPos.y - (spy + handleOffsetYPx)).toDouble()).toFloat()
                            if (dHandle <= hitRadiusPx * 0.85f) {
                                grabbedIdx = curSelIdx
                                grabbedViaOffsetHandle = true
                            }
                        }

                        // 3. Otherwise collect ALL pins within generous 38.dp invisible hit area (overlap-safe!)
                        val nearby = ArrayList<Pair<Int, Float>>()
                        if (grabbedIdx < 0) {
                            curPins.forEachIndexed { i, pin ->
                                val (px, py) = v.docToScreen(pin.dstX, pin.dstY)
                                val d = hypot((startPos.x - px).toDouble(), (startPos.y - py).toDouble()).toFloat()
                                if (d <= hitRadiusPx) {
                                    nearby.add(i to d)
                                }
                            }
                            nearby.sortBy { it.second }
                            if (nearby.isNotEmpty()) {
                                // Prioritize currently selected pin if it's inside the hit zone so dragging it is rock-solid
                                grabbedIdx = if (nearby.any { it.first == curSelIdx }) curSelIdx else nearby.first().first
                            }
                        }

                        val grabbedPinStartScreen = if (grabbedIdx in curPins.indices) {
                            val gp = curPins[grabbedIdx]
                            val (gx, gy) = v.docToScreen(gp.dstX, gp.dstY)
                            Offset(gx, gy)
                        } else null

                        var wasMultiTouch = false
                        var movedFar = false
                        var didDragPin = false
                        var prevMid: Offset? = null
                        var prevDist = 0f

                        while (true) {
                            val ev = awaitPointerEvent()
                            val active = ev.changes.filter { it.pressed }
                            if (active.isEmpty()) break
                            if (active.size >= 2) {
                                if (!wasMultiTouch && didDragPin) {
                                    // Cancel accidental pin tug when 2nd finger joins for pinch-zoom!
                                    curOnRestore(snapshotBefore)
                                    didDragPin = false
                                }
                                wasMultiTouch = true
                                activeDragFinger = null
                                val p0 = active[0].position
                                val p1 = active[1].position
                                val mid = Offset((p0.x + p1.x) * 0.5f, (p0.y + p1.y) * 0.5f)
                                val dist = hypot((p1.x - p0.x).toDouble(), (p1.y - p0.y).toDouble()).toFloat().coerceAtLeast(1f)
                                if (prevMid != null && prevDist > 1f) {
                                    curOnPinch(mid.x - prevMid!!.x, mid.y - prevMid!!.y, dist / prevDist, mid.x, mid.y)
                                }
                                prevMid = mid
                                prevDist = dist
                                active.forEach { it.consume() }
                            } else if (!wasMultiTouch) {
                                val ch = active[0]
                                val deltaX = ch.position.x - startPos.x
                                val deltaY = ch.position.y - startPos.y
                                val delta = hypot(deltaX.toDouble(), deltaY.toDouble()).toFloat()
                                if (delta > 8f) movedFar = true
                                if (grabbedIdx >= 0 && grabbedPinStartScreen != null && movedFar) {
                                    if (!didDragPin) {
                                        curOnSelect(grabbedIdx)
                                        didDragPin = true
                                    }
                                    activeDragFinger = ch.position
                                    // Relative delta + gentle upward lift if grabbed directly on top of pin so thumb never hides it!
                                    val liftY = if (grabbedViaOffsetHandle) 0f else -with(density) { 18.dp.toPx() } * (delta / 40f).coerceIn(0f, 1f)
                                    val targetScreenX = grabbedPinStartScreen.x + deltaX
                                    val targetScreenY = grabbedPinStartScreen.y + deltaY + liftY
                                    val (dx, dy) = v.screenToDoc(targetScreenX, targetScreenY)
                                    curOnMove(grabbedIdx, dx.coerceIn(0f, docW), dy.coerceIn(0f, docH))
                                }
                                ch.consume()
                            }
                        }
                        activeDragFinger = null

                        if (didDragPin) {
                            // Push pre-drag snapshot so Undo restores the pin cleanly!
                            curOnPushUndo(snapshotBefore)
                        } else if (!wasMultiTouch && !movedFar) {
                            if (nearby.isNotEmpty()) {
                                // Overlap cycling: if multiple pins are within hit radius & current is already selected, cycle to next!
                                if (nearby.size > 1 && nearby.any { it.first == curSelIdx }) {
                                    val curPos = nearby.indexOfFirst { it.first == curSelIdx }
                                    val nextPinIdx = nearby[(curPos + 1) % nearby.size].first
                                    curOnSelect(nextPinIdx)
                                } else {
                                    curOnSelect(nearby.first().first)
                                }
                            } else if (!grabbedViaOffsetHandle) {
                                val (dx, dy) = v.screenToDoc(startPos.x, startPos.y)
                                if (dx in 0f..docW && dy in 0f..docH) {
                                    curOnAdd(dx, dy)
                                }
                            }
                        }
                    }
                }
            }
    ) {
        if (canvasViewRef != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val _camTick = cameraTick
                if (_camTick < 0) return@Canvas
                if (showMesh && meshSegments.size >= 4) {
                    var i = 0
                    val limit = meshSegments.size - 3
                    while (i < limit) {
                        val (x0, y0) = canvasViewRef.docToScreen(meshSegments[i], meshSegments[i + 1])
                        val (x1, y1) = canvasViewRef.docToScreen(meshSegments[i + 2], meshSegments[i + 3])
                        drawLine(
                            color = Color(0x664FC3F7),
                            start = Offset(x0, y0),
                            end = Offset(x1, y1),
                            strokeWidth = 1.2f
                        )
                        i += 4
                    }
                }
                pins.forEachIndexed { idx, pin ->
                    val (sx, sy) = canvasViewRef.docToScreen(pin.srcX, pin.srcY)
                    val (dx, dy) = canvasViewRef.docToScreen(pin.dstX, pin.dstY)
                    val isSel = idx == selectedIdx
                    // Subtle anchor-to-current tether line
                    if (hypot((dx - sx).toDouble(), (dy - sy).toDouble()) > 3.0) {
                        drawCircle(color = Color(0x88FFFFFF), radius = 3.5f, center = Offset(sx, sy))
                        drawLine(
                            color = if (isSel) Color(0xDDFFD600) else Color(0xAA4F9CFF),
                            start = Offset(sx, sy),
                            end = Offset(dx, dy),
                            strokeWidth = if (isSel) 3.5f else 2.5f
                        )
                    }
                    if (isSel) {
                        // Soft amber selection halo + sleek below-pin finger handle (so thumb never covers pin!)
                        drawCircle(color = Color(0x44FFD600), radius = selectedRadiusPx * 1.9f, center = Offset(dx, dy))
                        // Thin stem to below-pin drag handle
                        val hy = dy + handleOffsetYPx
                        drawLine(color = Color(0x99FFD600), start = Offset(dx, dy + selectedRadiusPx), end = Offset(dx, hy - 10f), strokeWidth = 2f)
                        drawCircle(color = Color(0x55FFD600), radius = 9f, center = Offset(dx, hy))
                        drawCircle(color = Color(0xEEFFD600), radius = 4f, center = Offset(dx, hy))

                        // Floating red × delete button at top-right of selected pin
                        val delC = Offset(dx + deleteOffsetXPx, dy + deleteOffsetYPx)
                        drawCircle(color = Color(0xFFE53935), radius = 11f, center = delC)
                        drawLine(color = Color.White, start = Offset(delC.x - 5.5f, delC.y - 5.5f), end = Offset(delC.x + 5.5f, delC.y + 5.5f), strokeWidth = 2.6f)
                        drawLine(color = Color.White, start = Offset(delC.x + 5.5f, delC.y - 5.5f), end = Offset(delC.x - 5.5f, delC.y + 5.5f), strokeWidth = 2.6f)
                    }
                    // Compact sleek visual pin (never blocks the photo!)
                    val r = if (isSel) selectedRadiusPx else normalRadiusPx
                    drawCircle(color = if (isSel) Color(0xFFFFD600) else Color(0xFF4F9CFF), radius = r, center = Offset(dx, dy))
                    drawCircle(color = Color.White, radius = r * 0.62f, center = Offset(dx, dy))
                    drawCircle(color = if (isSel) Color(0xFFFF6D00) else Color(0xFF1565C0), radius = r * 0.30f, center = Offset(dx, dy))
                }
                // Subtle fingertip guide while dragging
                activeDragFinger?.let { f ->
                    drawCircle(color = Color(0x44FFFFFF), radius = 18f, center = f)
                }
            }
        }
    }
}

@Composable
internal fun PuppetWarpPanel(
    pinCount: Int,
    selectedPin: PuppetPinPoint?,
    mode: String,
    density: String,
    expansion: Float,
    showMesh: Boolean,
    onMode: (String) -> Unit,
    onDensity: (String) -> Unit,
    onExpansion: (Float) -> Unit,
    onToggleMesh: () -> Unit,
    onRotateSelected: (Float) -> Unit,
    onDepthDelta: (Int) -> Unit,
    onNudgeSelected: (Float, Float) -> Unit,
    onDeleteSelected: () -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
    onApply: () -> Unit
) {
    var showMeshSettings by remember { mutableStateOf(false) }
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BottomEditorHeader(
            title = "Kukla Pro ($pinCount iğne)",
            subtitle = "",
            applyLabel = "Uygula",
            onClose = onClose,
            onApply = onApply
        )

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf("rigid" to "Sert", "normal" to "Normal", "distort" to "Esnek").forEach { (id, lbl) ->
                ProPill(selected = mode == id, onClick = { onMode(id) }, label = { Text(lbl, fontSize = 11.sp) })
            }
            ProPill(
                selected = showMesh,
                onClick = onToggleMesh,
                label = { Text(if (showMesh) "Ağ ✓" else "Ağ", fontSize = 11.sp) }
            )
            ProPill(
                selected = showMeshSettings,
                onClick = { showMeshSettings = !showMeshSettings },
                label = { Text("Yoğunluk", fontSize = 11.sp) }
            )
            if (selectedPin != null) {
                ProPill(
                    selected = false,
                    onClick = onDeleteSelected,
                    label = { Text("İğne Sil", fontSize = 11.sp, color = Color(0xFFFF8A8A)) }
                )
            }
            if (pinCount > 0) {
                ProPill(
                    selected = false,
                    onClick = onReset,
                    label = { Text("Sıfırla", fontSize = 11.sp) }
                )
            }
        }

        if (showMeshSettings) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Ağ:", color = Color(0xFF9EA5BA), fontSize = 11.sp)
                listOf("fewer" to "Az", "normal" to "Normal", "more" to "Çok").forEach { (dId, dLbl) ->
                    ProPill(selected = density == dId, onClick = { onDensity(dId) }, label = { Text(dLbl, fontSize = 11.sp) })
                }
            }
            CompactSliderRow("Genişlet", "${expansion.roundToInt()}px", expansion, -10f..30f, defaultValue = 2f) {
                onExpansion(it)
            }
        }

        if (selectedPin != null) {
            val curRot = selectedPin.rotateDeg ?: 0f
            CompactSliderRow("Döndür", "${curRot.roundToInt()}°", curRot, -180f..180f, defaultValue = 0f) {
                onRotateSelected(it)
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val depthTag = if (selectedPin.depth > 0) "+${selectedPin.depth}" else "${selectedPin.depth}"
                ProPill(selected = false, onClick = { onDepthDelta(1) }, label = { Text("Öne ($depthTag)", fontSize = 10.sp) })
                ProPill(selected = false, onClick = { onDepthDelta(-1) }, label = { Text("Arkaya", fontSize = 10.sp) })
                Spacer(modifier = Modifier.width(4.dp))
                ProPill(selected = false, onClick = { onNudgeSelected(-2f, 0f) }, label = { Text("←", fontSize = 12.sp) })
                ProPill(selected = false, onClick = { onNudgeSelected(0f, -2f) }, label = { Text("↑", fontSize = 12.sp) })
                ProPill(selected = false, onClick = { onNudgeSelected(0f, 2f) }, label = { Text("↓", fontSize = 12.sp) })
                ProPill(selected = false, onClick = { onNudgeSelected(2f, 0f) }, label = { Text("→", fontSize = 12.sp) })
            }
        }
    }
}

@Composable
internal fun ImageResizePanel(
    origW: Int,
    origH: Int,
    widthText: String,
    heightText: String,
    lockAspect: Boolean,
    resample: String,
    onWidthText: (String) -> Unit,
    onHeightText: (String) -> Unit,
    onToggleLock: () -> Unit,
    onPreset: (Int, Int) -> Unit,
    onResample: (String) -> Unit,
    onClose: () -> Unit,
    onApply: () -> Unit
) {
    val curW = widthText.toIntOrNull() ?: origW
    val curH = heightText.toIntOrNull() ?: origH
    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BottomEditorHeader(
            title = "Görsel Boyutlandır",
            subtitle = "Mevcut: ${origW}×${origH} px  →  Yeni: ${curW}×${curH} px",
            applyLabel = "Uygula",
            onClose = onClose,
            onApply = onApply
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = widthText,
                onValueChange = onWidthText,
                singleLine = true,
                label = { Text("Genişlik (px)", fontSize = 10.sp) },
                modifier = Modifier.weight(1f)
            )
            ProPill(
                selected = lockAspect,
                onClick = onToggleLock,
                label = { Text(if (lockAspect) "Oran Kilitli" else "Serbest", fontSize = 10.sp) }
            )
            OutlinedTextField(
                value = heightText,
                onValueChange = onHeightText,
                singleLine = true,
                label = { Text("Yükseklik (px)", fontSize = 10.sp) },
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                "%50" to ((origW * 0.5f).roundToInt() to (origH * 0.5f).roundToInt()),
                "%75" to ((origW * 0.75f).roundToInt() to (origH * 0.75f).roundToInt()),
                "%150" to ((origW * 1.5f).roundToInt() to (origH * 1.5f).roundToInt()),
                "%200" to ((origW * 2.0f).roundToInt() to (origH * 2.0f).roundToInt()),
                "1080p" to (1920 to (1920f * origH / origW.coerceAtLeast(1)).roundToInt()),
                "1080×1080" to (1080 to 1080),
                "4K" to (3840 to (3840f * origH / origW.coerceAtLeast(1)).roundToInt())
            ).forEach { (lbl, dims) ->
                ProPill(
                    selected = curW == dims.first && curH == dims.second,
                    onClick = { onPreset(dims.first, dims.second) },
                    label = { Text(lbl, fontSize = 10.sp) }
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                "lanczos" to "Lanczos (Keskin)",
                "bicubic" to "Bikübik",
                "preserveDetails" to "Detay Koru",
                "bilinear" to "Bilineer",
                "nearest" to "Piksel"
            ).forEach { (id, lbl) ->
                ProPill(selected = resample == id, onClick = { onResample(id) }, label = { Text(lbl, fontSize = 10.sp) })
            }
        }
    }
}
