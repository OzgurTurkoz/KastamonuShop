package com.kastamonushop.app.bridge

import android.view.Surface

/**
 * Direct JNI bridge to `rust/kastamonushop-ffi/src/surface_jni.rs`.
 * Binds Android's hardware `SurfaceView` (`ANativeWindow`) straight to PhotoCraft's `wgpu` Vulkan compositor.
 */
object NativeSurfaceBridge {
    external fun nativeAttachSurface(surface: Surface, width: Int, height: Int): Boolean
    external fun nativeResizeSurface(width: Int, height: Int)
    external fun nativeDetachSurface()
    external fun nativeSetCamera(panX: Float, panY: Float, zoom: Float, rotationRad: Float)
    external fun nativeSetPreview(mirrorMode: Int, mirrorAxis: Float, flipX: Boolean, flipY: Boolean)
    external fun nativeSetAdjustPreview(
        brightness: Float,
        contrast: Float,
        saturation: Float,
        hue: Float,
        blurRadius: Float,
        sharpenAmount: Float
    )
    external fun nativeSetTonePreview(
        levelsBlack: Float,
        levelsGamma: Float,
        levelsWhite: Float,
        cbCyanRed: Float,
        cbMagentaGreen: Float,
        cbYellowBlue: Float
    )
    external fun nativeSetCurvePreview(
        shadowY: Float,
        midY: Float,
        highY: Float,
        enabled: Boolean
    )
    external fun nativeRenderFrame(): Boolean
}
