//! KastamonuShop — Native Android FFI & Vulkan GPU Bridge for PhotoCraft.
//!
//! Architecture:
//! - `uniffi` exports high-level document, layer, brush, filter, and SAF byte-stream APIs to Kotlin.
//! - `surface_jni` binds Android's `SurfaceView` (`ANativeWindow*`) directly to `wgpu` (Vulkan,
//!   with GLES fallback) so `photocraft_gpu::Compositor` renders zero-copy on the GPU.

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

/// Shared global engine state so the JNI SurfaceView render callback and UniFFI Kotlin calls
/// access the exact same `photocraft_engine::Session` without copying documents across FFI.
pub(crate) struct SharedRuntime {
    pub engine: Arc<Mutex<engine::EngineCore>>,
    pub renderer: Mutex<Option<VulkanRenderer>>,
    pub camera: Mutex<ViewportCamera>,
}

static RUNTIME: OnceLock<SharedRuntime> = OnceLock::new();

pub(crate) fn runtime() -> &'static SharedRuntime {
    RUNTIME.get_or_init(|| {
        #[cfg(target_os = "android")]
        android_logger::init_once(
            android_logger::Config::default()
                .with_max_level(log::LevelFilter::Info)
                .with_tag("KastamonuShop"),
        );

        SharedRuntime {
            engine: Arc::new(Mutex::new(engine::EngineCore::new())),
            renderer: Mutex::new(None),
            camera: Mutex::new(ViewportCamera::default()),
        }
    })
}

/// Initializes the global KastamonuShop engine and returns its UniFFI handle.
#[uniffi::export]
pub fn create_kastamonu_engine() -> Arc<KastamonuEngine> {
    let rt = runtime();
    Arc::new(KastamonuEngine::from_shared(Arc::clone(&rt.engine)))
}

/// Returns build & backend metadata for diagnostics.
#[uniffi::export]
pub fn engine_version_info() -> String {
    format!(
        "KastamonuShop {} (PhotoCraft Core + wgpu 30 Vulkan/GLES)",
        env!("CARGO_PKG_VERSION")
    )
}
