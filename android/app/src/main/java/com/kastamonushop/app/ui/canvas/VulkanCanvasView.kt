package com.kastamonushop.app.ui.canvas

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.kastamonushop.app.bridge.NativeSurfaceBridge
import uniffi.kastamonushop_ffi.StrokeSample
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * Hardware-accelerated Vulkan/GLES canvas with distance-decimated stroke sampling
 * so even giant 250px brushes paint at silky-smooth 60fps.
 */
class VulkanCanvasView(
    context: Context,
    private val getDocSize: () -> Pair<Int, Int>,
    private val getBrushSize: () -> Float,
    private val onStrokeBegin: (StrokeSample) -> Unit,
    private val onStrokeMove: (List<StrokeSample>) -> Unit,
    private val onStrokeEnd: () -> Unit,
    private val onStrokeCancel: () -> Unit
) : SurfaceView(context), SurfaceHolder.Callback {

    private var panX = 0f
    private var panY = 0f
    private var zoom = 0.45f
    private var rotationRad = 0f

    private var isMultiTouch = false
    private var strokeActive = false
    private var prevMidX = 0f
    private var prevMidY = 0f
    private var prevDist = 1f
    private var prevAngle = 0f

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
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        NativeSurfaceBridge.nativeDetachSurface()
    }

    fun fitToDocument() {
        val (docW, docH) = getDocSize()
        val viewW = width.coerceAtLeast(1).toFloat()
        val viewH = height.coerceAtLeast(1).toFloat()
        val fitZoom = min(viewW / docW.coerceAtLeast(1), viewH / docH.coerceAtLeast(1)) * 0.88f
        panX = 0f
        panY = 0f
        zoom = fitZoom.coerceIn(0.05f, 16f)
        rotationRad = 0f
        NativeSurfaceBridge.nativeSetCamera(panX, panY, zoom, rotationRad)
        NativeSurfaceBridge.nativeRenderFrame()
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
            MotionEvent.ACTION_MOVE -> {
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
                if (strokeActive) {
                    strokeActive = false
                    onStrokeEnd()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                if (strokeActive) {
                    strokeActive = false
                    onStrokeCancel()
                }
            }
        }
        return true
    }
}
