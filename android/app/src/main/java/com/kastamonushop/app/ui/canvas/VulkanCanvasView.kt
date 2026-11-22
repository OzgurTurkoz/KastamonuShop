package com.kastamonushop.app.ui.canvas

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.kastamonushop.app.bridge.NativeSurfaceBridge
import com.kastamonushop.app.viewmodel.ActiveTool
import uniffi.kastamonushop_ffi.StrokeSample
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Hardware-accelerated Vulkan/GLES canvas view.
 * - Single-finger / Stylus (with historical pressure & tilt): paints in document coordinates.
 * - Two-finger gesture (or PAN_ZOOM tool): smooth pan, pinch-zoom, and rotation at 60/120Hz.
 */
class VulkanCanvasView(
    context: Context,
    private val getActiveTool: () -> ActiveTool,
    private val getDocSize: () -> Pair<Int, Int>,
    private val onStrokeComplete: (List<StrokeSample>) -> Unit
) : SurfaceView(context), SurfaceHolder.Callback {

    private var panX = 0f
    private var panY = 0f
    private var zoom = 0.45f
    private var rotationRad = 0f

    private var isMultiTouch = false
    private var prevMidX = 0f
    private var prevMidY = 0f
    private var prevDist = 1f
    private var prevAngle = 0f

    private val currentStroke = mutableListOf<StrokeSample>()

    init {
        holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        if (NativeSurfaceBridge.nativeAttachSurface(holder.surface, w, h)) {
            NativeSurfaceBridge.nativeSetCamera(panX, panY, zoom, rotationRad)
            NativeSurfaceBridge.nativeRenderFrame()
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

    fun resetCamera() {
        panX = 0f
        panY = 0f
        zoom = 0.45f
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

    private fun appendSample(sx: Float, sy: Float, pressure: Float, tilt: Float, orientation: Float) {
        val (dx, dy) = screenToDoc(sx, sy)
        val tiltX = (sin(tilt.toDouble()) * cos(orientation.toDouble())).toFloat()
        val tiltY = (sin(tilt.toDouble()) * sin(orientation.toDouble())).toFloat()
        currentStroke.add(
            StrokeSample(
                x = dx,
                y = dy,
                pressure = pressure.coerceIn(0.05f, 1.0f),
                tiltX = tiltX,
                tiltY = tiltY
            )
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val tool = getActiveTool()

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
                    currentStroke.clear()
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

        if (tool == ActiveTool.PAN_ZOOM) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    prevMidX = event.x
                    prevMidY = event.y
                }
                MotionEvent.ACTION_MOVE -> {
                    panX += (event.x - prevMidX)
                    panY += (event.y - prevMidY)
                    prevMidX = event.x
                    prevMidY = event.y
                    NativeSurfaceBridge.nativeSetCamera(panX, panY, zoom, rotationRad)
                    NativeSurfaceBridge.nativeRenderFrame()
                }
            }
            return true
        }

        // Brush / Eraser stroke capture (including high-frequency historical stylus samples)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                currentStroke.clear()
                appendSample(
                    event.x,
                    event.y,
                    event.pressure,
                    event.getAxisValue(MotionEvent.AXIS_TILT),
                    event.orientation
                )
            }
            MotionEvent.ACTION_MOVE -> {
                for (h in 0 until event.historySize) {
                    appendSample(
                        event.getHistoricalX(h),
                        event.getHistoricalY(h),
                        event.getHistoricalPressure(h),
                        event.getHistoricalAxisValue(MotionEvent.AXIS_TILT, h),
                        event.getHistoricalOrientation(h)
                    )
                }
                appendSample(
                    event.x,
                    event.y,
                    event.pressure,
                    event.getAxisValue(MotionEvent.AXIS_TILT),
                    event.orientation
                )
            }
            MotionEvent.ACTION_UP -> {
                if (currentStroke.isNotEmpty()) {
                    onStrokeComplete(currentStroke.toList())
                    currentStroke.clear()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                currentStroke.clear()
            }
        }
        return true
    }
}
