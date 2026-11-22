//! PhotoCraft Session wrapper exposed to Kotlin via UniFFI.
//!
//! Every action routes through `photocraft_engine::Session::execute` or the byte-level
//! `photocraft_io` / `file_cmds::open_bytes_as` APIs so Android Storage Access Framework (SAF)
//! streams work without requiring raw filesystem paths.

use std::sync::Arc;
use parking_lot::Mutex;
use photocraft_doc::{Document, LayerId};
use photocraft_engine::Session;
use serde_json::{Value, json};

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum EngineError {
    #[error("No active document")]
    NoActiveDocument,
    #[error("Invalid JSON parameters: {message}")]
    InvalidParams { message: String },
    #[error("PhotoCraft engine error: {message}")]
    CommandFailed { message: String },
    #[error("Import/Export I/O error: {message}")]
    IoFailed { message: String },
}

#[derive(Clone, Debug, uniffi::Record)]
pub struct DocumentInfo {
    pub name: String,
    pub width: u32,
    pub height: u32,
    pub layer_count: u32,
    pub active_layer_id: Option<u64>,
    pub can_undo: bool,
    pub can_redo: bool,
    pub dirty: bool,
}

#[derive(Clone, Debug, uniffi::Record)]
pub struct LayerSummary {
    pub id: u64,
    pub name: String,
    pub visible: bool,
    pub opacity: f32,
    pub blend_mode: String,
    pub is_group: bool,
    pub is_active: bool,
}

#[derive(Clone, Debug, uniffi::Record)]
pub struct StrokeSample {
    pub x: f32,
    pub y: f32,
    /// Stylus/touch pressure in 0.0..=1.0
    pub pressure: f32,
    /// Stylus tilt X in radians (-pi/2..=pi/2)
    pub tilt_x: f32,
    /// Stylus tilt Y in radians (-pi/2..=pi/2)
    pub tilt_y: f32,
}

pub(crate) struct EngineCore {
    pub session: Session,
    pub render_generation: u64,
}

impl EngineCore {
    pub fn new() -> Self {
        let mut session = Session::new();
        // Start with a ready-to-paint 2048x2048 RGBA8 studio canvas
        let _ = session.execute(
            "file.new",
            json!({
                "name": "KastamonuShop Canvas",
                "width": 2048,
                "height": 2048,
                "resolution": 300.0,
                "background": "white"
            }),
        );
        Self {
            session,
            render_generation: 1,
        }
    }

    pub fn active_document(&self) -> Option<Arc<Document>> {
        self.session.active().map(|ds| Arc::clone(ds.doc()))
    }

    fn bump_generation(&mut self) {
        self.render_generation = self.render_generation.wrapping_add(1);
    }
}

#[derive(uniffi::Object)]
pub struct KastamonuEngine {
    core: Arc<Mutex<EngineCore>>,
}

impl KastamonuEngine {
    pub(crate) fn from_shared(core: Arc<Mutex<EngineCore>>) -> Self {
        Self { core }
    }
}

#[uniffi::export]
impl KastamonuEngine {
    /// Creates a brand new blank document.
    pub fn new_document(&self, name: String, width: u32, height: u32) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard
            .session
            .execute(
                "file.new",
                json!({
                    "name": name,
                    "width": width.max(1),
                    "height": height.max(1),
                    "resolution": 300.0,
                    "background": "white"
                }),
            )
            .map_err(|e| EngineError::CommandFailed { message: e.to_string() })?;
        guard.bump_generation();
        self.info_from_locked(&guard)
    }

    /// Opens any supported document format (PSD, PSB, PCRAFT, RAW, HEIF, TIFF, EXR, PNG, JPEG,
    /// WebP, SVG, PDF, Affinity, KRA, XCF) directly from Android SAF byte stream.
    pub fn open_document_bytes(&self, file_name: String, bytes: Vec<u8>) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        photocraft_engine::file_cmds::open_bytes_as(&mut guard.session, &file_name, &bytes, None, None)
            .map_err(|e| EngineError::IoFailed { message: e.to_string() })?;
        guard.bump_generation();
        self.info_from_locked(&guard)
    }

    /// Exports the active document to raw bytes for writing via Android SAF `ContentResolver`.
    /// `format_ext` examples: "psd", "pcraft", "png", "jpg", "webp", "tiff", "exr", "pdf".
    pub fn export_document_bytes(&self, format_ext: String) -> Result<Vec<u8>, EngineError> {
        let doc = {
            let guard = self.core.lock();
            guard.active_document().ok_or(EngineError::NoActiveDocument)?
        };
        let export_name = format!("export.{}", format_ext.trim_start_matches('.'));
        let result = photocraft_io::export(&doc, &export_name, &photocraft_io::ExportOptions::default())
            .map_err(|e| EngineError::IoFailed { message: e.to_string() })?;
        Ok(result.bytes)
    }

    /// Universal PhotoCraft command bus: executes any Photoshop-style command ID with JSON params
    /// (filters, adjustments, transforms, masks, text, vector shapes, etc.).
    pub fn execute_command(&self, command_id: String, params_json: String) -> Result<String, EngineError> {
        let params: Value = if params_json.trim().is_empty() {
            json!({})
        } else {
            serde_json::from_str(&params_json)
                .map_err(|e| EngineError::InvalidParams { message: e.to_string() })?
        };
        let mut guard = self.core.lock();
        let out = guard
            .session
            .execute(&command_id, params)
            .map_err(|e| EngineError::CommandFailed { message: e.to_string() })?;
        guard.bump_generation();
        Ok(out.to_string())
    }

    pub fn undo(&self) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard
            .session
            .execute("edit.undo", json!({}))
            .map_err(|e| EngineError::CommandFailed { message: e.to_string() })?;
        guard.bump_generation();
        self.info_from_locked(&guard)
    }

    pub fn redo(&self) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard
            .session
            .execute("edit.redo", json!({}))
            .map_err(|e| EngineError::CommandFailed { message: e.to_string() })?;
        guard.bump_generation();
        self.info_from_locked(&guard)
    }

    pub fn get_document_info(&self) -> Result<DocumentInfo, EngineError> {
        let guard = self.core.lock();
        self.info_from_locked(&guard)
    }

    /// Returns top-to-bottom layer list for the mobile Layers drawer.
    pub fn get_layers(&self) -> Result<Vec<LayerSummary>, EngineError> {
        let guard = self.core.lock();
        let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
        let active_id = ds.active_layer();
        let doc = ds.doc();

        let mut layers = Vec::new();
        for layer in doc.layers.iter().rev() {
            layers.push(LayerSummary {
                id: layer.id.0,
                name: layer.name.clone(),
                visible: layer.visible,
                opacity: layer.opacity,
                blend_mode: format!("{:?}", layer.blend_mode),
                is_group: matches!(layer.content, photocraft_doc::LayerContent::Group(_)),
                is_active: active_id == Some(layer.id),
            });
        }
        Ok(layers)
    }

    pub fn add_layer(&self, name: String) -> Result<Vec<LayerSummary>, EngineError> {
        let mut guard = self.core.lock();
        guard
            .session
            .execute("layer.new", json!({ "name": name }))
            .map_err(|e| EngineError::CommandFailed { message: e.to_string() })?;
        guard.bump_generation();
        drop(guard);
        self.get_layers()
    }

    pub fn delete_active_layer(&self) -> Result<Vec<LayerSummary>, EngineError> {
        let mut guard = self.core.lock();
        guard
            .session
            .execute("layer.delete", json!({}))
            .map_err(|e| EngineError::CommandFailed { message: e.to_string() })?;
        guard.bump_generation();
        drop(guard);
        self.get_layers()
    }

    pub fn select_layer(&self, layer_id: u64) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        guard.session.set_active_layer(Some(LayerId(layer_id)));
        Ok(())
    }

    pub fn set_layer_visibility(&self, layer_id: u64, visible: bool) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        guard
            .session
            .execute(
                "layer.setVisibility",
                json!({ "layer": layer_id, "visible": visible }),
            )
            .map_err(|e| EngineError::CommandFailed { message: e.to_string() })?;
        guard.bump_generation();
        Ok(())
    }

    pub fn set_layer_opacity(&self, layer_id: u64, opacity: f32) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        let pct = (opacity.clamp(0.0, 1.0) * 100.0).round();
        guard
            .session
            .execute(
                "layer.setOpacity",
                json!({ "layer": layer_id, "opacity": pct }),
            )
            .map_err(|e| EngineError::CommandFailed { message: e.to_string() })?;
        guard.bump_generation();
        Ok(())
    }

    /// Configures the active session brush (size in px, hardness 0..1, opacity 0..1, flow 0..1).
    pub fn set_brush(
        &self,
        size: f32,
        hardness: f32,
        opacity: f32,
        flow: f32,
        color_hex: String,
    ) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        guard
            .session
            .execute(
                "tools.setBrush",
                json!({
                    "brush": {
                        "size": size.max(1.0),
                        "hardness": hardness.clamp(0.0, 1.0),
                        "opacity": opacity.clamp(0.01, 1.0),
                        "flow": flow.clamp(0.01, 1.0)
                    },
                    "color": color_hex
                }),
            )
            .map_err(|e| EngineError::CommandFailed { message: e.to_string() })?;
        Ok(())
    }

    /// Commits a touch/stylus stroke with pressure & tilt directly into `photocraft-paint`.
    pub fn commit_stroke(
        &self,
        points: Vec<StrokeSample>,
        color_hex: String,
        is_eraser: bool,
    ) -> Result<(), EngineError> {
        if points.is_empty() {
            return Ok(());
        }
        let json_pts: Vec<Value> = points
            .iter()
            .map(|pt| {
                json!({
                    "x": pt.x,
                    "y": pt.y,
                    "pressure": pt.pressure.clamp(0.01, 1.0),
                    "tiltX": pt.tilt_x,
                    "tiltY": pt.tilt_y
                })
            })
            .collect();

        let mut params = json!({
            "points": json_pts,
            "color": color_hex
        });
        if is_eraser {
            params["mode"] = json!("clear");
        }

        let mut guard = self.core.lock();
        guard
            .session
            .execute("paint.stroke", params)
            .map_err(|e| EngineError::CommandFailed { message: e.to_string() })?;
        guard.bump_generation();
        Ok(())
    }

    pub fn render_generation(&self) -> u64 {
        self.core.lock().render_generation
    }
}

impl KastamonuEngine {
    fn info_from_locked(&self, guard: &EngineCore) -> Result<DocumentInfo, EngineError> {
        let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
        let doc = ds.doc();
        Ok(DocumentInfo {
            name: doc.name.clone(),
            width: doc.size.width,
            height: doc.size.height,
            layer_count: doc.layers.len() as u32,
            active_layer_id: ds.active_layer().map(|id| id.0),
            can_undo: guard.session.is_enabled("edit.undo"),
            can_redo: guard.session.is_enabled("edit.redo"),
            dirty: ds.is_dirty(),
        })
    }
}
