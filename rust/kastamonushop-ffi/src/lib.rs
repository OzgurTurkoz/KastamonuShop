//! StonebridgeCraft — Native Android FFI & Vulkan GPU Bridge for PhotoCraft.

#![allow(unsafe_code)]

pub mod engine;
pub mod renderer;
#[cfg(target_os = "android")]
pub mod surface_jni;

use std::sync::{Arc, OnceLock};
use parking_lot::Mutex;

pub use engine::{
    DocumentInfo, EngineError, KastamonuEngine, LayerSummary, StrokeSample,
};
pub use renderer::{ViewportCamera, VulkanRenderer};

uniffi::setup_scaffolding!();

#[cfg(target_os = "android")]
pub(crate) struct SendNativeWindow(pub ndk::native_window::NativeWindow);

#[cfg(target_os = "android")]
unsafe impl Send for SendNativeWindow {}

pub(crate) struct SharedRuntime {
    pub engine: Arc<Mutex<engine::EngineCore>>,
    pub renderer: Mutex<Option<VulkanRenderer>>,
    #[cfg(target_os = "android")]
    pub native_window: Mutex<Option<SendNativeWindow>>,
    pub camera: Mutex<ViewportCamera>,
    pub last_gpu_error: Mutex<Option<String>>,
}

static RUNTIME: OnceLock<SharedRuntime> = OnceLock::new();

pub(crate) fn runtime() -> &'static SharedRuntime {
    RUNTIME.get_or_init(|| {
        #[cfg(target_os = "android")]
        android_logger::init_once(
            android_logger::Config::default()
                .with_max_level(log::LevelFilter::Info)
                .with_tag("StonebridgeCraft"),
        );

        SharedRuntime {
            engine: Arc::new(Mutex::new(engine::EngineCore::new())),
            renderer: Mutex::new(None),
            #[cfg(target_os = "android")]
            native_window: Mutex::new(None),
            camera: Mutex::new(ViewportCamera::default()),
            last_gpu_error: Mutex::new(None),
        }
    })
}

pub(crate) fn set_gpu_error(msg: impl Into<String>) {
    let s = msg.into();
    log::error!("StonebridgeCraft GPU Error: {s}");
    *runtime().last_gpu_error.lock() = Some(s);
}

#[uniffi::export]
pub fn create_kastamonu_engine() -> Arc<KastamonuEngine> {
    let rt = runtime();
    Arc::new(KastamonuEngine::from_shared(Arc::clone(&rt.engine)))
}

#[uniffi::export]
pub fn engine_version_info() -> String {
    format!(
        "StonebridgeCraft 0.5.1 (PhotoCraft Core {} + wgpu 30 Vulkan/GLES)",
        env!("CARGO_PKG_VERSION")
    )
}

#[uniffi::export]
pub fn get_last_gpu_error() -> Option<String> {
    runtime().last_gpu_error.lock().clone()
}
