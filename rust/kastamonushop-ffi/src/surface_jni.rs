//! JNI bridge connecting Android's `SurfaceView` (`android.view.Surface`) directly to
//! `VulkanRenderer` via `ANativeWindow` and `raw-window-handle`.

use std::ptr::NonNull;
use jni::JNIEnv;
use jni::objects::{JClass, JObject};
use jni::sys::{jboolean, jfloat, jint, JNI_FALSE, JNI_TRUE};
use ndk::native_window::NativeWindow;
use raw_window_handle::{
    AndroidDisplayHandle, AndroidNdkWindowHandle, RawDisplayHandle, RawWindowHandle,
};

use crate::renderer::{ViewportCamera, VulkanRenderer};
use crate::runtime;

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_kastamonushop_app_bridge_NativeSurfaceBridge_nativeAttachSurface(
    env: JNIEnv,
    _class: JClass,
    surface: JObject,
    width: jint,
    height: jint,
) -> jboolean {
    let raw_env = env.get_native_interface();
    let Some(native_window) = (unsafe { NativeWindow::from_surface(raw_env, surface.as_raw()) }) else {
        log::error!("KastamonuShop: ANativeWindow_fromSurface returned null");
        return JNI_FALSE;
    };

    let Some(a_native_window) = NonNull::new(native_window.ptr().as_ptr().cast()) else {
        return JNI_FALSE;
    };

    let raw_window = RawWindowHandle::AndroidNdk(AndroidNdkWindowHandle::new(a_native_window));
    let raw_display = RawDisplayHandle::Android(AndroidDisplayHandle::new());

    let target = wgpu::SurfaceTargetUnsafe::RawHandle {
        raw_display_handle: Some(raw_display),
        raw_window_handle: raw_window,
    };

    // Keep the NativeWindow alive while wgpu::Surface owns it (released on detach)
    let _leaked_ref = std::mem::ManuallyDrop::new(native_window);

    match VulkanRenderer::new(target, width.max(1) as u32, height.max(1) as u32) {
        Ok(renderer) => {
            *runtime().renderer.lock() = Some(renderer);
            log::info!("KastamonuShop: Vulkan Surface attached ({}x{})", width, height);
            JNI_TRUE
        }
        Err(e) => {
            log::error!("KastamonuShop: VulkanRenderer init failed: {e}");
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
    if let Some(renderer) = runtime().renderer.lock().as_mut() {
        renderer.resize(width.max(1) as u32, height.max(1) as u32);
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_kastamonushop_app_bridge_NativeSurfaceBridge_nativeDetachSurface(
    _env: JNIEnv,
    _class: JClass,
) {
    *runtime().renderer.lock() = None;
    log::info!("KastamonuShop: Vulkan Surface detached cleanly");
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
    *runtime().camera.lock() = ViewportCamera {
        pan_x,
        pan_y,
        zoom: zoom.clamp(0.05, 64.0),
        rotation_rad,
    };
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_kastamonushop_app_bridge_NativeSurfaceBridge_nativeRenderFrame(
    _env: JNIEnv,
    _class: JClass,
) -> jboolean {
    let rt = runtime();
    let (doc, gen) = {
        let guard = rt.engine.lock();
        let Some(doc) = guard.active_document() else {
            return JNI_FALSE;
        };
        (doc, guard.render_generation)
    };
    let camera = *rt.camera.lock();
    let mut r_guard = rt.renderer.lock();
    let Some(renderer) = r_guard.as_mut() else {
        return JNI_FALSE;
    };

    match renderer.render_frame(&doc, gen, camera) {
        Ok(()) => JNI_TRUE,
        Err(e) => {
            log::error!("KastamonuShop: render_frame error: {e}");
            JNI_FALSE
        }
    }
}
