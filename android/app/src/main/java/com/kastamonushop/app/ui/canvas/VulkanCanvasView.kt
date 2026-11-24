package com.kastamonushop.app.ui.canvas

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.kastamonushop.app.bridge.NativeSurfaceBridge
import uniffi.kastamonushop_ffi.StrokeSample
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

data class RulerLinePreview(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val angleDeg: Int,
    val isSnapped: Boolean
)

/**
 * Hardware-accelerated Vulkan/GLES canvas:
 * - Canvas movement (pan, zoom, rotate) is STRICTLY two-finger only.
 * - 1-finger handles: Mirror axis drag (60 FPS), Text placement drag (60 FPS),
 *   Retouch strokes (Spot Heal / Smudge / Dodge / Burn), Eyedropper, Ruler, and Freehand Brush/Eraser.
 */
class VulkanCanvasView(
    context: Context,
    private val getDocSize: () -> Pair<Int, Int>,
    private val getBrushSize: () -> Float,
    private val isDrawingActive: () -> Boolean,
    private val isRetouchActive: () -> Boolean,
    private val isEyedropperActive: () -> Boolean,
    private val isRulerEnabled: () -> Boolean,
    private val isMirrorDragActive: () -> Boolean,
    private val isMirrorVerticalAxis: () -> Boolean,
    private val isTextDragActive: () -> Boolean,
    private val isSelectRectActive: () -> Boolean = { false },
    private val isSelectWandActive: () -> Boolean = { false },
    private val isCloneSourcePickActive: () -> Boolean = { false },
    private val onStrokeBegin: (StrokeSample) -> Unit,
    private val onStrokeMove: (List<StrokeSample>) -> Unit,
    private val onStrokeEnd: () -> Unit,
    private val onStrokeCancel: () -> Unit,
    private val onRetouchPreview: (Pair<Float, Float>?) -> Unit,
    private val onRetouchCommit: (List<StrokeSample>) -> Unit,
    private val onRulerPreview: (RulerLinePreview?) -> Unit,
    private val onRulerStrokeCommit: (List<StrokeSample>) -> Unit,
    private val onEyedropperSample: (Float, Float) -> Unit,
    private val onMirrorAxisDrag: (Float) -> Unit,
    private val onTextPositionDrag: (Float, Float) -> Unit,
    private val onSelectRectPreview: (FloatArray?) -> Unit = {},
    private val onSelectRectCommit: (Int, Int, Int, Int) -> Unit = { _, _, _, _ -> },
    private val onSelectWandTap: (Float, Float) -> Unit = { _, _ -> },
    private val onCloneSourcePick: (Float, Float) -> Unit = { _, _ -> },
    private val onCameraChanged: () -> Unit = {}
) : SurfaceView(context), SurfaceHolder.Callback {

    private var panX = 0f
    private var panY = 0f
    private var zoom = 0.45f
    private var rotationRad = 0f

    private var isMultiTouch = false
    private var strokeActive = false
    private var retouchActive = false
    private var rulerActive = false
    private var eyedropperActive = false
    private var mirrorDragActive = false
    private var textDragActive = false
    private var selectDragActive = false
    private var selectStartScreenX = 0f
    private var selectStartScreenY = 0f
    private var selectEndScreenX = 0f
    private var selectEndScreenY = 0f

    private val retouchBuffer = ArrayList<StrokeSample>(64)

    private var prevMidX = 0f
    private var prevMidY = 0f
    private var prevDist = 1f
    private var prevAngle = 0f

    private var rulerStartScreenX = 0f
    private var rulerStartScreenY = 0f
    private var rulerEndScreenX = 0f
    private var rulerEndScreenY = 0f

    private var lastSampleDocX = 0f
    private var lastSampleDocY = 0f

    init {
        holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        if (NativeSurfaceBridge.nativeAttachSurface(holder.surface, w, h)) {
            fitToDocument()
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        NativeSurfaceBridge.nativeResizeSurface(width.coerceAtLeast(1), height.coerceAtLeast(1))
        NativeSurfaceBridge.nativeSetCamera(panX, panY, zoom, rotationRad)
        NativeSurfaceBridge.nativeRenderFrame()
        onCameraChanged()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        NativeSurfaceBridge.nativeDetachSurface()
    }

    fun getCurrentZoom(): Float = zoom

    fun fitToDocument(
        previewQuarterTurnsCw: Int = 0,
        topInsetPx: Float = 0f,
        bottomInsetPx: Float = 0f
    ) {
        val (rawW, rawH) = getDocSize()
        val turns = ((previewQuarterTurnsCw % 4) + 4) % 4
        val effW = if (turns % 2 == 1) rawH else rawW
        val effH = if (turns % 2 == 1) rawW else rawH
        val viewW = width.coerceAtLeast(1).toFloat()
        val viewH = height.coerceAtLeast(1).toFloat()
        val availH = (viewH - topInsetPx - bottomInsetPx).coerceAtLeast(120f)
        val padFactor = if (bottomInsetPx > 10f) 0.90f else 0.86f
        val fitZoom = min(viewW / effW.coerceAtLeast(1), availH / effH.coerceAtLeast(1)) * padFactor
        panX = 0f
        panY = (topInsetPx - bottomInsetPx) * 0.5f
        zoom = fitZoom.coerceIn(0.05f, 16f)
        rotationRad = (turns * (PI / 2.0)).toFloat()
        NativeSurfaceBridge.nativeSetCamera(panX, panY, zoom, rotationRad)
        NativeSurfaceBridge.nativeRenderFrame()
        onCameraChanged()
    }

    fun docToScreen(dx: Float, dy: Float): Pair<Float, Float> {
        val (docW, docH) = getDocSize()
        val cx = width * 0.5f
        val cy = height * 0.5f
        val rx = (dx - docW * 0.5f) * zoom
        val ry = (dy - docH * 0.5f) * zoom
        val c = cos(rotationRad)
        val s = sin(rotationRad)
        val px = rx * c - ry * s
        val py = rx * s + ry * c
        return (px + cx + panX) to (py + cy + panY)
    }

    private fun screenToDoc(sx: Float, sy: Float): Pair<Float, Float> {
        val (docW, docH) = getDocSize()
        val cx = width * 0.5f
        val cy = height * 0.5f
        val px = sx - cx - panX
        val py = sy - cy - panY
        val c = cos(-rotationRad)
        val s = sin(-rotationRad)
        val rx = px * c - py * s
        val ry = px * s + py * c
        val dx = rx / zoom + docW * 0.5f
        val dy = ry / zoom + docH * 0.5f
        return dx to dy
    }

    private fun updateMirrorRatioFromScreen(sx: Float, sy: Float) {
        val (docW, docH) = getDocSize()
        val (dx, dy) = screenToDoc(sx, sy)
        val ratio = if (isMirrorVerticalAxis()) {
            (dx / docW.coerceAtLeast(1).toFloat()).coerceIn(0.05f, 0.95f)
        } else {
            (dy / docH.coerceAtLeast(1).toFloat()).coerceIn(0.05f, 0.95f)
        }
        onMirrorAxisDrag(ratio)
    }

    private fun updateTextPosFromScreen(sx: Float, sy: Float) {
        val (docW, docH) = getDocSize()
        val (dx, dy) = screenToDoc(sx, sy)
        val nx = (dx / docW.coerceAtLeast(1).toFloat()).coerceIn(0.04f, 0.96f)
        val ny = (dy / docH.coerceAtLeast(1).toFloat()).coerceIn(0.06f, 0.94f)
        onTextPositionDrag(nx, ny)
    }

    private fun makeSample(sx: Float, sy: Float, pressure: Float, tilt: Float, orientation: Float): StrokeSample {
        val (dx, dy) = screenToDoc(sx, sy)
        val tiltX = (sin(tilt.toDouble()) * cos(orientation.toDouble())).toFloat()
        val tiltY = (sin(tilt.toDouble()) * sin(orientation.toDouble())).toFloat()
        return StrokeSample(
            x = dx,
            y = dy,
            pressure = pressure.coerceIn(0.05f, 1.0f),
            tiltX = tiltX,
            tiltY = tiltY
        )
    }

    private fun acceptSampleIfMoved(sample: StrokeSample): Boolean {
        val minStep = (getBrushSize() * 0.12f).coerceIn(2.0f, 28.0f)
        val dist = hypot((sample.x - lastSampleDocX).toDouble(), (sample.y - lastSampleDocY).toDouble()).toFloat()
        if (dist >= minStep) {
            lastSampleDocX = sample.x
            lastSampleDocY = sample.y
            return true
        }
        return false
    }

    private fun computeSnappedRulerEnd(sx: Float, sy: Float): Triple<Float, Float, Pair<Int, Boolean>> {
        val dx = sx - rulerStartScreenX
        val dy = sy - rulerStartScreenY
        val len = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (len < 4f) return Triple(sx, sy, 0 to false)

        val rawDeg = (atan2(dy.toDouble(), dx.toDouble()) * 180.0 / PI).toFloat()
        val nearest45 = (rawDeg / 45f).roundToInt() * 45f
        val snapped = abs(rawDeg - nearest45) <= 4.5f
        val finalDeg = if (snapped) nearest45 else rawDeg
        val rad = finalDeg * PI / 180.0
        val ex = rulerStartScreenX + (cos(rad) * len).toFloat()
        val ey = rulerStartScreenY + (sin(rad) * len).toFloat()
        val normDeg = ((finalDeg.roundToInt() % 360) + 360) % 360
        return Triple(ex, ey, normDeg to snapped)
    }

    private fun buildRulerStrokeSamples(): List<StrokeSample> {
        val (x0, y0) = screenToDoc(rulerStartScreenX, rulerStartScreenY)
        val (x1, y1) = screenToDoc(rulerEndScreenX, rulerEndScreenY)
        val dist = hypot((x1 - x0).toDouble(), (y1 - y0).toDouble()).toFloat()
        val step = max(2f, getBrushSize() * 0.18f)
        val count = max(2, (dist / step).roundToInt() + 1).coerceAtMost(400)
        val out = ArrayList<StrokeSample>(count)
        for (i in 0 until count) {
            val t = if (count <= 1) 0f else i.toFloat() / (count - 1).toFloat()
            out.add(
                StrokeSample(
                    x = x0 + (x1 - x0) * t,
                    y = y0 + (y1 - y0) * t,
                    pressure = 1.0f,
                    tiltX = 0f,
                    tiltY = 0f
                )
            )
        }
        return out
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.pointerCount >= 2) {
            val x0 = event.getX(0)
            val y0 = event.getY(0)
            val x1 = event.getX(1)
            val y1 = event.getY(1)
            val midX = (x0 + x1) * 0.5f
            val midY = (y0 + y1) * 0.5f
            val dist = hypot((x1 - x0).toDouble(), (y1 - y0).toDouble()).toFloat().coerceAtLeast(1f)
            val angle = atan2((y1 - y0).toDouble(), (x1 - x0).toDouble()).toFloat()

            when (event.actionMasked) {
                MotionEvent.ACTION_POINTER_DOWN -> {
                    isMultiTouch = true
                    eyedropperActive = false
                    mirrorDragActive = false
                    textDragActive = false
                    if (selectDragActive) {
                        selectDragActive = false
                        onSelectRectPreview(null)
                    }
                    if (selectDragActive) {
                    selectDragActive = false
                    onSelectRectPreview(null)
                    val (d0x, d0y) = screenToDoc(selectStartScreenX, selectStartScreenY)
                    val (d1x, d1y) = screenToDoc(selectEndScreenX, selectEndScreenY)
                    val rx = min(d0x, d1x).roundToInt()
                    val ry = min(d0y, d1y).roundToInt()
                    val rw = abs(d1x - d0x).roundToInt()
                    val rh = abs(d1y - d0y).roundToInt()
                    if (rw >= 4 && rh >= 4) {
                        onSelectRectCommit(rx, ry, rw, rh)
                    }
                }
                if (retouchActive) {
                        retouchActive = false
                        retouchBuffer.clear()
                        onRetouchPreview(null)
                    }
                    if (rulerActive) {
                        rulerActive = false
                        onRulerPreview(null)
                    }
                    if (strokeActive) {
                        strokeActive = false
                        onStrokeCancel()
                    }
                    prevMidX = midX
                    prevMidY = midY
                    prevDist = dist
                    prevAngle = angle
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isMultiTouch) {
                        panX += (midX - prevMidX)
                        panY += (midY - prevMidY)
                        zoom = (zoom * (dist / prevDist)).coerceIn(0.05f, 32f)
                        rotationRad += (angle - prevAngle)

                        prevMidX = midX
                        prevMidY = midY
                        prevDist = dist
                        prevAngle = angle

                        NativeSurfaceBridge.nativeSetCamera(panX, panY, zoom, rotationRad)
                        NativeSurfaceBridge.nativeRenderFrame()
                        onCameraChanged()
                    }
                }
            }
            return true
        }

        if (isMultiTouch) {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                isMultiTouch = false
            }
            return true
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (isMirrorDragActive()) {
                    mirrorDragActive = true
                    updateMirrorRatioFromScreen(event.x, event.y)
                } else if (isTextDragActive()) {
                    textDragActive = true
                    updateTextPosFromScreen(event.x, event.y)
                } else if (isCloneSourcePickActive()) {
                    val (dx, dy) = screenToDoc(event.x, event.y)
                    onCloneSourcePick(dx, dy)
                } else if (isSelectWandActive()) {
                    val (dx, dy) = screenToDoc(event.x, event.y)
                    onSelectWandTap(dx, dy)
                } else if (isSelectRectActive()) {
                    selectDragActive = true
                    selectStartScreenX = event.x
                    selectStartScreenY = event.y
                    selectEndScreenX = event.x
                    selectEndScreenY = event.y
                    onSelectRectPreview(floatArrayOf(event.x, event.y, event.x, event.y))
                } else if (isEyedropperActive()) {
                    eyedropperActive = true
                    val (dx, dy) = screenToDoc(event.x, event.y)
                    onEyedropperSample(dx, dy)
                } else if (isRetouchActive()) {
                    retouchActive = true
                    retouchBuffer.clear()
                    val sample = makeSample(event.x, event.y, event.pressure, 0f, 0f)
                    lastSampleDocX = sample.x
                    lastSampleDocY = sample.y
                    retouchBuffer.add(sample)
                    onRetouchPreview(event.x to event.y)
                } else if (isDrawingActive() && isRulerEnabled()) {
                    rulerActive = true
                    rulerStartScreenX = event.x
                    rulerStartScreenY = event.y
                    rulerEndScreenX = event.x
                    rulerEndScreenY = event.y
                    onRulerPreview(RulerLinePreview(event.x, event.y, event.x, event.y, 0, false))
                } else if (isDrawingActive()) {
                    strokeActive = true
                    val sample = makeSample(
                        event.x,
                        event.y,
                        event.pressure,
                        event.getAxisValue(MotionEvent.AXIS_TILT),
                        event.orientation
                    )
                    lastSampleDocX = sample.x
                    lastSampleDocY = sample.y
                    onStrokeBegin(sample)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (mirrorDragActive) {
                    updateMirrorRatioFromScreen(event.x, event.y)
                    return true
                }
                if (textDragActive) {
                    updateTextPosFromScreen(event.x, event.y)
                    return true
                }
                if (selectDragActive) {
                    selectEndScreenX = event.x
                    selectEndScreenY = event.y
                    onSelectRectPreview(floatArrayOf(selectStartScreenX, selectStartScreenY, event.x, event.y))
                    return true
                }
                if (eyedropperActive) {
                    val (dx, dy) = screenToDoc(event.x, event.y)
                    onEyedropperSample(dx, dy)
                    return true
                }
                if (retouchActive) {
                    val sample = makeSample(event.x, event.y, event.pressure, 0f, 0f)
                    if (acceptSampleIfMoved(sample)) {
                        retouchBuffer.add(sample)
                    }
                    onRetouchPreview(event.x to event.y)
                    return true
                }
                if (rulerActive) {
                    val (ex, ey, degInfo) = computeSnappedRulerEnd(event.x, event.y)
                    rulerEndScreenX = ex
                    rulerEndScreenY = ey
                    onRulerPreview(
                        RulerLinePreview(
                            startX = rulerStartScreenX,
                            startY = rulerStartScreenY,
                            endX = ex,
                            endY = ey,
                            angleDeg = degInfo.first,
                            isSnapped = degInfo.second
                        )
                    )
                    return true
                }
                if (!strokeActive) return true
                val batch = ArrayList<StrokeSample>(4)
                for (h in 0 until event.historySize) {
                    val s = makeSample(
                        event.getHistoricalX(h),
                        event.getHistoricalY(h),
                        event.getHistoricalPressure(h),
                        event.getHistoricalAxisValue(MotionEvent.AXIS_TILT, h),
                        event.getHistoricalOrientation(h)
                    )
                    if (acceptSampleIfMoved(s)) batch.add(s)
                }
                val cur = makeSample(
                    event.x,
                    event.y,
                    event.pressure,
                    event.getAxisValue(MotionEvent.AXIS_TILT),
                    event.orientation
                )
                if (acceptSampleIfMoved(cur)) batch.add(cur)
                if (batch.isNotEmpty()) {
                    onStrokeMove(batch)
                }
            }
            MotionEvent.ACTION_UP -> {
                mirrorDragActive = false
                textDragActive = false
                if (selectDragActive) {
                    selectDragActive = false
                    onSelectRectPreview(null)
                }
                eyedropperActive = false
                if (retouchActive) {
                    retouchActive = false
                    onRetouchPreview(null)
                    if (retouchBuffer.isNotEmpty()) {
                        onRetouchCommit(ArrayList(retouchBuffer))
                    }
                    retouchBuffer.clear()
                }
                if (rulerActive) {
                    rulerActive = false
                    onRulerPreview(null)
                    val samples = buildRulerStrokeSamples()
                    if (samples.size >= 2) {
                        onRulerStrokeCommit(samples)
                    }
                }
                if (strokeActive) {
                    strokeActive = false
                    onStrokeEnd()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                mirrorDragActive = false
                textDragActive = false
                eyedropperActive = false
                if (retouchActive) {
                    retouchActive = false
                    retouchBuffer.clear()
                    onRetouchPreview(null)
                }
                if (rulerActive) {
                    rulerActive = false
                    onRulerPreview(null)
                }
                if (strokeActive) {
                    strokeActive = false
                    onStrokeCancel()
                }
            }
        }
        return true
    }
}
