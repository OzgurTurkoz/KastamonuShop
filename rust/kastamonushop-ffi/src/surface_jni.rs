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

use crate::renderer::{GpuPreviewState, ViewportCamera, VulkanRenderer};
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
        *runtime().preview.lock() = GpuPreviewState {
            mirror_mode,
            mirror_axis: mirror_axis.clamp(0.05, 0.95),
            flip_x: flip_x != JNI_FALSE,
            flip_y: flip_y != JNI_FALSE,
        };
    }));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_kastamonushop_app_bridge_NativeSurfaceBridge_nativeRenderFrame(
    _env: JNIEnv,
    _class: JClass,
) -> jboolean {
    let res = catch_unwind(AssertUnwindSafe(|| {
        let rt = runtime();
        let mut r_guard = rt.renderer.lock();
        let Some(renderer) = r_guard.as_mut() else {
            return JNI_FALSE;
        };

        let (doc, render_gen, force_full, dirty_rect) = {
            let mut guard = rt.engine.lock();
            let Some(doc) = guard.active_document() else {
                return JNI_FALSE;
            };
            let (full, rect) = guard.take_dirty_state();
            (doc, guard.render_generation, full, rect)
        };
        let camera = *rt.camera.lock();
        let preview = *rt.preview.lock();

        match renderer.render_frame(&doc, render_gen, force_full, dirty_rect, camera, preview) {
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
