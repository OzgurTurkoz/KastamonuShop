//! PhotoCraft Session wrapper exposed to Kotlin via UniFFI.
//! - Real-time `LiveStroke` with dirty-rect tracking for 60fps painting even on 12MP+ photos
//! - Verified PhotoCraft command IDs (`layer.new.layer`, `layer.setProps`, `layer.delete`, `tools.setBrush`)
//! - Auto-creates a transparent "Çizim Katmanı" above opened photos so erasing never destroys the photo

use std::sync::Arc;
use parking_lot::Mutex;
use photocraft_doc::{Document, LayerId};
use photocraft_engine::brush_cmds::LiveStroke;
use photocraft_engine::Session;
use photocraft_geom::Rect;
use photocraft_paint::StrokePoint;
use serde_json::{Value, json};

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum EngineError {
    #[error("No active document")]
    NoActiveDocument,
    #[error("Invalid JSON parameters: {detail}")]
    InvalidParams { detail: String },
    #[error("PhotoCraft engine error: {detail}")]
    CommandFailed { detail: String },
    #[error("Import/Export I/O error: {detail}")]
    IoFailed { detail: String },
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
    pub pressure: f32,
    pub tilt_x: f32,
    pub tilt_y: f32,
}

struct ActiveLiveStroke {
    live: LiveStroke,
    points_json: Vec<Value>,
    color_hex: String,
    is_eraser: bool,
}

pub(crate) struct EngineCore {
    pub session: Session,
    pub render_generation: u64,
    /// Pending dirty region for fast incremental GPU compositing (`None` = full document redraw).
    pub pending_dirty: Option<Rect>,
    pub force_full_redraw: bool,
    live_stroke: Option<ActiveLiveStroke>,
}

impl EngineCore {
    pub fn new() -> Self {
        let mut session = Session::new();
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
        // Add a transparent drawing layer on top of white Background so erasing reveals white cleanly
        let _ = session.execute("layer.new.layer", json!({ "name": "Çizim Katmanı" }));
        Self {
            session,
            render_generation: 1,
            pending_dirty: None,
            force_full_redraw: true,
            live_stroke: None,
        }
    }

    pub fn active_document(&self) -> Option<Arc<Document>> {
        if let Some(active_live) = &self.live_stroke {
            return Some(Arc::clone(&active_live.live.doc));
        }
        self.session.active().map(|ds| Arc::clone(ds.doc()))
    }

    pub fn mark_full_dirty(&mut self) {
        self.force_full_redraw = true;
        self.pending_dirty = None;
        self.render_generation = self.render_generation.wrapping_add(1);
    }

    pub fn mark_region_dirty(&mut self, rect: Rect) {
        if !rect.is_empty() && !self.force_full_redraw {
            self.pending_dirty = Some(match self.pending_dirty {
                Some(prev) => prev.union(&rect),
                None => rect,
            });
        }
        self.render_generation = self.render_generation.wrapping_add(1);
    }

    pub fn take_dirty_state(&mut self) -> (bool, Option<Rect>) {
        let full = self.force_full_redraw;
        let rect = self.pending_dirty.take();
        self.force_full_redraw = false;
        (full, rect)
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

fn sample_to_json(pt: &StrokeSample) -> Value {
    json!({
        "x": pt.x,
        "y": pt.y,
        "pressure": pt.pressure.clamp(0.01, 1.0),
        "tiltX": pt.tilt_x,
        "tiltY": pt.tilt_y
    })
}

fn sample_to_stroke_point(pt: &StrokeSample) -> StrokePoint {
    StrokePoint {
        x: pt.x as f64,
        y: pt.y as f64,
        pressure: pt.pressure.clamp(0.01, 1.0),
        tilt_x: pt.tilt_x,
        tilt_y: pt.tilt_y,
        ..Default::default()
    }
}

#[uniffi::export]
impl KastamonuEngine {
    pub fn new_document(&self, name: String, width: u32, height: u32) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
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
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        let _ = guard.session.execute("layer.new.layer", json!({ "name": "Çizim Katmanı" }));
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    /// Directly imports any supported image/document format and auto-adds a transparent
    /// drawing layer on top of single-layer photos so erasing never destroys the original photo.
    pub fn open_document_bytes(&self, file_name: String, bytes: Vec<u8>) -> Result<DocumentInfo, EngineError> {
        let imported = photocraft_io::import(&file_name, &bytes)
            .map_err(|e| EngineError::IoFailed { detail: e.to_string() })?;
        let mut doc = imported.document;
        if doc.name.is_empty() {
            doc.name = file_name;
        }
        let is_single_layer = doc.layers.len() == 1;
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        let idx = guard.session.add_document(doc, None);
        guard.session.set_active(idx);
        if is_single_layer {
            let _ = guard.session.execute("layer.new.layer", json!({ "name": "Çizim Katmanı" }));
        }
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    /// Places an image from Android SAF bytes as a new layer on top of the active document.
    pub fn place_image_bytes(&self, file_name: String, bytes: Vec<u8>) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        photocraft_engine::file_cmds::place_bytes(&mut guard.session, &file_name, bytes, None, &json!({}))
            .map_err(|e| EngineError::IoFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    pub fn export_document_bytes(&self, format_ext: String) -> Result<Vec<u8>, EngineError> {
        let doc = {
            let guard = self.core.lock();
            guard.active_document().ok_or(EngineError::NoActiveDocument)?
        };
        let export_name = format!("export.{}", format_ext.trim_start_matches('.'));
        let result = photocraft_io::export(&doc, &export_name, &photocraft_io::ExportOptions::default())
            .map_err(|e| EngineError::IoFailed { detail: e.to_string() })?;
        Ok(result.bytes)
    }

    pub fn execute_command(&self, command_id: String, params_json: String) -> Result<String, EngineError> {
        let params: Value = if params_json.trim().is_empty() {
            json!({})
        } else {
            serde_json::from_str(&params_json)
                .map_err(|e| EngineError::InvalidParams { detail: e.to_string() })?
        };
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        let out = guard
            .session
            .execute(&command_id, params)
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        Ok(out.to_string())
    }

    pub fn undo(&self) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard
            .session
            .execute("edit.undo", json!({}))
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    pub fn redo(&self) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard
            .session
            .execute("edit.redo", json!({}))
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    pub fn get_document_info(&self) -> Result<DocumentInfo, EngineError> {
        let guard = self.core.lock();
        self.info_from_locked(&guard)
    }

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
                blend_mode: format!("{:?}", layer.blend),
                is_group: matches!(layer.content, photocraft_doc::LayerContent::Group(_)),
                is_active: active_id == Some(layer.id),
            });
        }
        Ok(layers)
    }

    /// Creates a new transparent layer using the verified `layer.new.layer` command.
    pub fn add_layer(&self, name: String) -> Result<Vec<LayerSummary>, EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard
            .session
            .execute("layer.new.layer", json!({ "name": name }))
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        drop(guard);
        self.get_layers()
    }

    pub fn delete_active_layer(&self) -> Result<Vec<LayerSummary>, EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard
            .session
            .execute("layer.delete", json!({}))
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        drop(guard);
        self.get_layers()
    }

    pub fn select_layer(&self, layer_id: u64) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        guard.session.set_active_layer(Some(LayerId(layer_id)));
        Ok(())
    }

    /// Updates layer visibility using the verified `layer.setProps` command.
    pub fn set_layer_visibility(&self, layer_id: u64, visible: bool) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        guard
            .session
            .execute("layer.setProps", json!({ "layer": layer_id, "visible": visible }))
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        Ok(())
    }

    /// Updates layer opacity (0.0..=1.0) using the verified `layer.setProps` command.
    pub fn set_layer_opacity(&self, layer_id: u64, opacity: f32) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        guard
            .session
            .execute("layer.setProps", json!({ "layer": layer_id, "opacity": opacity.clamp(0.0, 1.0) }))
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        Ok(())
    }

    /// Configures the active session brush (strictly matching `BrushSettings` camelCase schema).
    pub fn set_brush(
        &self,
        size: f32,
        hardness: f32,
        opacity: f32,
        flow: f32,
        _color_hex: String,
    ) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        guard
            .session
            .execute(
                "tools.setBrush",
                json!({
                    "brush": {
                        "size": size.clamp(1.0, 1000.0),
                        "hardness": hardness.clamp(0.0, 1.0),
                        "opacity": opacity.clamp(0.01, 1.0),
                        "flow": flow.clamp(0.01, 1.0)
                    }
                }),
            )
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        Ok(())
    }

    /// Starts a real-time live brush/eraser stroke on ACTION_DOWN.
    pub fn begin_live_stroke(
        &self,
        sample: StrokeSample,
        color_hex: String,
        is_eraser: bool,
    ) -> Result<(), EngineError> {
        let pt_json = sample_to_json(&sample);
        let mut params = json!({
            "points": [pt_json.clone()],
            "color": color_hex
        });
        if is_eraser {
            params["mode"] = json!("clear");
        }
        let mut guard = self.core.lock();
        let live = LiveStroke::begin(&guard.session, &params)
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        let dmg = live.bounds();
        guard.live_stroke = Some(ActiveLiveStroke {
            live,
            points_json: vec![pt_json],
            color_hex,
            is_eraser,
        });
        guard.mark_region_dirty(dmg);
        Ok(())
    }

    /// Streams incremental finger/stylus points on ACTION_MOVE and records ONLY the dirty rect
    /// for ultra-fast incremental GPU compositing (60fps even on 12MP+ photos).
    pub fn push_live_stroke(&self, samples: Vec<StrokeSample>) -> Result<(), EngineError> {
        if samples.is_empty() {
            return Ok(());
        }
        let mut guard = self.core.lock();
        let Some(active) = guard.live_stroke.as_mut() else {
            return Ok(());
        };
        let pts: Vec<StrokePoint> = samples.iter().map(sample_to_stroke_point).collect();
        let dmg = active
            .live
            .push(&pts)
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        for s in &samples {
            active.points_json.push(sample_to_json(s));
        }
        guard.mark_region_dirty(dmg);
        Ok(())
    }

    /// Commits the live stroke into Undo/Redo history on ACTION_UP with zero re-rendering.
    pub fn finish_live_stroke(&self) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        let Some(active) = guard.live_stroke.take() else {
            return self.info_from_locked(&guard);
        };
        let seed = active.live.seed;
        let bounds = active.live.bounds();
        let mut params = json!({
            "points": active.points_json,
            "color": active.color_hex,
            "seed": seed
        });
        if active.is_eraser {
            params["mode"] = json!("clear");
        }
        let _ = guard.session.offer_live_stroke(active.live);
        guard
            .session
            .execute("paint.stroke", params)
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_region_dirty(bounds);
        self.info_from_locked(&guard)
    }

    pub fn cancel_live_stroke(&self) {
        let mut guard = self.core.lock();
        if let Some(active) = guard.live_stroke.take() {
            let bounds = active.live.bounds();
            guard.mark_region_dirty(bounds);
        }
    }

    pub fn commit_stroke(
        &self,
        points: Vec<StrokeSample>,
        color_hex: String,
        is_eraser: bool,
    ) -> Result<(), EngineError> {
        if points.is_empty() {
            return Ok(());
        }
        let json_pts: Vec<Value> = points.iter().map(sample_to_json).collect();
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
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
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
