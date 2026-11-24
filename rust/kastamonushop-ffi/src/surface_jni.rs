//! Panic-safe JNI bridge connecting Android's `SurfaceView` (`android.view.Surface`) directly to
//! `VulkanRenderer` via `ANativeWindow` and `raw-window-handle`.

use std::panic::{AssertUnwindSafe, catch_unwind};
use std::ptr::NonNull;
use jni::JNIEnv;
use jni::objects::{JClass, JObject};
use jni::sys::{jboolean, jfloat, jint, JNI_FALSE, JNI_TRUE};
use ndk::native_window::NativeWindow;
use raw_window_handle::{
    AndroidDisplayHandle, AndroidNdkWindowHandle, RawDisplayHandle, RawWindowHandle,
};

use crate::renderer::{ViewportCamera, VulkanRenderer};
use crate::{SendNativeWindow, runtime, set_gpu_error};

fn panic_payload_to_string(payload: Box<dyn std::any::Any + Send>) -> String {
    if let Some(s) = payload.downcast_ref::<&str>() {
        (*s).to_string()
    } else if let Some(s) = payload.downcast_ref::<String>() {
        s.clone()
    } else {
        "Unknown Rust panic".to_string()
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_kastamonushop_app_bridge_NativeSurfaceBridge_nativeAttachSurface(
    env: JNIEnv,
    _class: JClass,
    surface: JObject,
    width: jint,
    height: jint,
) -> jboolean {
    let res = catch_unwind(AssertUnwindSafe(|| {
        let rt = runtime();
        *rt.renderer.lock() = None;
        *rt.native_window.lock() = None;

        let raw_env = env.get_native_interface();
        let Some(native_window) = (unsafe { NativeWindow::from_surface(raw_env, surface.as_raw()) }) else {
            set_gpu_error("ANativeWindow_fromSurface returned null");
            return JNI_FALSE;
        };

        let Some(a_native_window) = NonNull::new(native_window.ptr().as_ptr().cast()) else {
            set_gpu_error("ANativeWindow pointer was null");
            return JNI_FALSE;
        };

        let raw_window = RawWindowHandle::AndroidNdk(AndroidNdkWindowHandle::new(a_native_window));
        let raw_display = RawDisplayHandle::Android(AndroidDisplayHandle::new());

        let target = wgpu::SurfaceTargetUnsafe::RawHandle {
            raw_display_handle: Some(raw_display),
            raw_window_handle: raw_window,
        };

        match VulkanRenderer::new(target, width.max(1) as u32, height.max(1) as u32) {
            Ok(renderer) => {
                *rt.native_window.lock() = Some(SendNativeWindow(native_window));
                *rt.renderer.lock() = Some(renderer);
                *rt.last_gpu_error.lock() = None;
                rt.engine.lock().mark_full_dirty();
                log::info!("StonebridgeCraft: Vulkan Surface attached ({}x{})", width, height);
                JNI_TRUE
            }
            Err(e) => {
                set_gpu_error(format!("VulkanRenderer init failed: {e}"));
                JNI_FALSE
            }
        }
    }));

    match res {
        Ok(v) => v,
        Err(p) => {
            set_gpu_error(format!("Panic in nativeAttachSurface: {}", panic_payload_to_string(p)));
            JNI_FALSE
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_kastamonushop_app_bridge_NativeSurfaceBridge_nativeResizeSurface(
    _env: JNIEnv,
    _class: JClass,
    width: jint,
    height: jint,
) {
    let _ = catch_unwind(AssertUnwindSafe(|| {
        if let Some(renderer) = runtime().renderer.lock().as_mut() {
            renderer.resize(width.max(1) as u32, height.max(1) as u32);
        }
    }));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_kastamonushop_app_bridge_NativeSurfaceBridge_nativeDetachSurface(
    _env: JNIEnv,
    _class: JClass,
) {
    let _ = catch_unwind(AssertUnwindSafe(|| {
        let rt = runtime();
        *rt.renderer.lock() = None;
        *rt.native_window.lock() = None;
        log::info!("StonebridgeCraft: Vulkan Surface detached cleanly");
    }));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_kastamonushop_app_bridge_NativeSurfaceBridge_nativeSetCamera(
    _env: JNIEnv,
    _class: JClass,
    pan_x: jfloat,
    pan_y: jfloat,
    zoom: jfloat,
    rotation_rad: jfloat,
) {
    let _ = catch_unwind(AssertUnwindSafe(|| {
        *runtime().camera.lock() = ViewportCamera {
            pan_x,
            pan_y,
            zoom: zoom.clamp(0.05, 64.0),
            rotation_rad,
        };
    }));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_kastamonushop_app_bridge_NativeSurfaceBridge_nativeSetPreview(
    _env: JNIEnv,
    _class: JClass,
    mirror_mode: jint,
    mirror_axis: jfloat,
    flip_x: jboolean,
    flip_y: jboolean,
) {
    let _ = catch_unwind(AssertUnwindSafe(|| {
        let mut p = runtime().preview.lock();
        p.mirror_mode = mirror_mode;
        p.mirror_axis = mirror_axis.clamp(0.05, 0.95);
        p.flip_x = flip_x != JNI_FALSE;
        p.flip_y = flip_y != JNI_FALSE;
    }));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_kastamonushop_app_bridge_NativeSurfaceBridge_nativeSetAdjustPreview(
    _env: JNIEnv,
    _class: JClass,
    brightness: jfloat,
    contrast: jfloat,
    saturation: jfloat,
    hue: jfloat,
    blur_radius: jfloat,
    sharpen_amount: jfloat,
) {
    let _ = catch_unwind(AssertUnwindSafe(|| {
        let mut p = runtime().preview.lock();
        p.brightness = brightness;
        p.contrast = contrast;
        p.saturation = saturation;
        p.hue = hue;
        p.blur_radius = blur_radius.max(0.0);
        p.sharpen_amount = sharpen_amount.max(0.0);
    }));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_kastamonushop_app_bridge_NativeSurfaceBridge_nativeSetTonePreview(
    _env: JNIEnv,
    _class: JClass,
    levels_black: jfloat,
    levels_gamma: jfloat,
    levels_white: jfloat,
    cb_cr: jfloat,
    cb_mg: jfloat,
    cb_yb: jfloat,
) {
    let _ = catch_unwind(AssertUnwindSafe(|| {
        let mut p = runtime().preview.lock();
        p.levels_black = levels_black.clamp(0.0, 0.95);
        p.levels_gamma = levels_gamma.clamp(0.1, 4.0);
        p.levels_white = levels_white.clamp(0.05, 1.0);
        p.cb_cr = cb_cr.clamp(-1.0, 1.0);
        p.cb_mg = cb_mg.clamp(-1.0, 1.0);
        p.cb_yb = cb_yb.clamp(-1.0, 1.0);
    }));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_kastamonushop_app_bridge_NativeSurfaceBridge_nativeSetCurvePreview(
    _env: JNIEnv,
    _class: JClass,
    black_y: jfloat,
    shadow_y: jfloat,
    mid_y: jfloat,
    high_y: jfloat,
    white_y: jfloat,
    enabled: jboolean,
) {
    let _ = catch_unwind(AssertUnwindSafe(|| {
        let mut p = runtime().preview.lock();
        p.curve_black = black_y.clamp(0.0, 1.0);
        p.curve_shadow = shadow_y.clamp(0.0, 1.0);
        p.curve_mid = mid_y.clamp(0.0, 1.0);
        p.curve_high = high_y.clamp(0.0, 1.0);
        p.curve_white = white_y.clamp(0.0, 1.0);
        p.curve_enabled = enabled != JNI_FALSE;
    }));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_kastamonushop_app_bridge_NativeSurfaceBridge_nativeRenderFrame(
    _env: JNIEnv,
    _class: JClass,
) -> jboolean {
    let res = catch_unwind(AssertUnwindSafe(|| {
        let rt = runtime();
        let Some(mut r_guard) = rt.renderer.try_lock() else {
            return JNI_TRUE;
        };
        let Some(renderer) = r_guard.as_mut() else {
            return JNI_FALSE;
        };
        let camera = *rt.camera.lock();
        let preview = *rt.preview.lock();

        // Non-blocking try_lock on engine: if a background CPU job is running, still blit the cached GPU texture at 60 FPS!
        let doc_state = rt.engine.try_lock().and_then(|mut guard| {
            let (doc, disp_size) = guard.active_document_and_display_size()?;
            let (full, rect) = guard.take_dirty_state();
            Some((doc, disp_size, guard.render_generation, guard.selection_generation, full, rect))
        });

        let result = if let Some((doc, disp_size, render_gen, sel_gen, force_full, dirty_rect)) = doc_state {
            renderer.render_frame(&doc, disp_size, render_gen, sel_gen, force_full, dirty_rect, camera, preview)
        } else {
            renderer.present_cached_only(camera, preview)
        };

        match result {
            Ok(()) => JNI_TRUE,
            Err(e) => {
                set_gpu_error(format!("render_frame error: {e}"));
                JNI_FALSE
            }
        }
    }));

    match res {
        Ok(v) => v,
        Err(p) => {
            set_gpu_error(format!("Panic in nativeRenderFrame: {}", panic_payload_to_string(p)));
            JNI_FALSE
        }
    }
}
