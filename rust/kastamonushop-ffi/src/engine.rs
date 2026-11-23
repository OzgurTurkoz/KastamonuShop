//! PhotoCraft Session wrapper exposed to Kotlin via UniFFI (v0.4.2 — StonebridgeCraft).
//! - Live real-time adjustment preview (`begin_live_adjustment`, `update_live_adjustment`, `cancel_live_adjustment`)
//! - Toggleable quick effects (`toggle_quick_adjustment` for B&W / Invert)
//! - Smart pixel filter targeting (`apply_pixel_filter` auto-targets the photo layer even when an adjustment layer is selected)

use std::sync::Arc;
use parking_lot::Mutex;
use photocraft_doc::{Document, LayerContent, LayerId};
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
    pub locked: bool,
    pub is_background: bool,
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
                "name": "StonebridgeCraft Canvas",
                "width": 2048,
                "height": 2048,
                "resolution": 300.0,
                "background": "white"
            }),
        );
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

    pub fn open_document_bytes(&self, file_name: String, bytes: Vec<u8>) -> Result<DocumentInfo, EngineError> {
        let imported = photocraft_io::import(&file_name, &bytes)
            .map_err(|e| EngineError::IoFailed { detail: e.to_string() })?;
        let mut doc = imported.document;
        if doc.name.is_empty() {
            doc.name = file_name;
        }
        let single_layer_id = if doc.layers.len() == 1 {
            doc.layers.first().map(|l| l.id.0)
        } else {
            None
        };
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        let idx = guard.session.add_document(doc, None);
        guard.session.set_active(idx);
        if let Some(bg_id) = single_layer_id {
            let _ = guard.session.execute(
                "layer.setProps",
                json!({ "layer": bg_id, "locks": { "pixels": true } }),
            );
            let _ = guard.session.execute("layer.new.layer", json!({ "name": "Çizim Katmanı" }));
        }
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

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

    /// Creates a new live adjustment layer (e.g. `brightnessContrast`, `hueSaturation`) and returns its layer ID
    /// so Kotlin sliders can update it in real time via `update_live_adjustment`.
    pub fn begin_live_adjustment(&self, kind: String, params_json: String) -> Result<u64, EngineError> {
        let params: Value = if params_json.trim().is_empty() {
            json!({})
        } else {
            serde_json::from_str(&params_json)
                .map_err(|e| EngineError::InvalidParams { detail: e.to_string() })?
        };
        let cmd = format!("layer.newAdjustmentLayer.{kind}");
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        let out = guard
            .session
            .execute(&cmd, params)
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        let layer_id = out
            .get("layer")
            .and_then(Value::as_u64)
            .ok_or_else(|| EngineError::CommandFailed { detail: "Adjustment layer id missing".into() })?;
        guard.mark_full_dirty();
        Ok(layer_id)
    }

    /// Updates an active live adjustment layer in real time as the user drags a slider.
    pub fn update_live_adjustment(&self, layer_id: u64, params_json: String) -> Result<(), EngineError> {
        let mut params: Value = serde_json::from_str(&params_json)
            .map_err(|e| EngineError::InvalidParams { detail: e.to_string() })?;
        if let Some(obj) = params.as_object_mut() {
            obj.insert("layer".to_string(), json!(layer_id));
        }
        let mut guard = self.core.lock();
        guard
            .session
            .execute("layer.setAdjustment", params)
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        Ok(())
    }

    /// Deletes a specific layer by ID (used for cancelling a live adjustment preview or toggling B&W / Invert off).
    pub fn delete_layer_by_id(&self, layer_id: u64) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard
            .session
            .execute("layer.delete", json!({ "layer": layer_id }))
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        Ok(())
    }

    /// Applies a pixel filter (`filter.blur.gaussianBlur`, `filter.sharpen.unsharpMask`, etc.)
    /// by automatically targeting the photo/background pixel layer even if an adjustment layer is currently selected.
    pub fn apply_pixel_filter(&self, command_id: String, params_json: String) -> Result<(), EngineError> {
        let params: Value = if params_json.trim().is_empty() {
            json!({})
        } else {
            serde_json::from_str(&params_json)
                .map_err(|e| EngineError::InvalidParams { detail: e.to_string() })?
        };
        let mut guard = self.core.lock();
        guard.live_stroke = None;

        // Find the best raster pixel layer (prefer Background / bottom raster photo layer if active isn't raster)
        let (prev_active, target_id, was_locked) = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            let doc = ds.doc();
            let prev = ds.active_layer();

            let active_is_raster = prev
                .and_then(|aid| doc.layers.iter().find(|l| l.id == aid))
                .is_some_and(|l| matches!(l.content, LayerContent::Raster(_)) && !l.name.eq_ignore_ascii_case("Çizim Katmanı"));

            let chosen = if active_is_raster {
                prev.and_then(|aid| doc.layers.iter().find(|l| l.id == aid))
            } else {
                doc.layers
                    .iter()
                    .find(|l| matches!(l.content, LayerContent::Raster(_)))
            };

            let target = chosen.ok_or_else(|| EngineError::CommandFailed {
                detail: "Filtre uygulanacak piksel katmanı bulunamadı".into(),
            })?;
            (prev, target.id, target.locks.pixels)
        };

        guard.session.set_active_layer(Some(target_id));
        if was_locked {
            let _ = guard.session.execute(
                "layer.setProps",
                json!({ "layer": target_id.0, "locks": { "pixels": false } }),
            );
        }

        let res = guard.session.execute(&command_id, params);

        if was_locked {
            let _ = guard.session.execute(
                "layer.setProps",
                json!({ "layer": target_id.0, "locks": { "pixels": true } }),
            );
        }
        if prev_active.is_some() {
            guard.session.set_active_layer(prev_active);
        }

        res.map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        Ok(())
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
        let bottom_id = doc.layers.first().map(|l| l.id);

        let mut layers = Vec::new();
        for layer in doc.layers.iter().rev() {
            let is_bg = bottom_id == Some(layer.id) || layer.name.eq_ignore_ascii_case("Background");
            layers.push(LayerSummary {
                id: layer.id.0,
                name: layer.name.clone(),
                visible: layer.visible,
                opacity: layer.opacity,
                blend_mode: format!("{:?}", layer.blend),
                is_group: matches!(layer.content, LayerContent::Group(_)),
                is_active: active_id == Some(layer.id),
                locked: layer.locks.pixels || layer.locks.all,
                is_background: is_bg,
            });
        }
        Ok(layers)
    }

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

    pub fn set_layer_visibility(&self, layer_id: u64, visible: bool) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        guard
            .session
            .execute("layer.setProps", json!({ "layer": layer_id, "visible": visible }))
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        Ok(())
    }

    pub fn set_layer_opacity(&self, layer_id: u64, opacity: f32) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        guard
            .session
            .execute("layer.setProps", json!({ "layer": layer_id, "opacity": opacity.clamp(0.0, 1.0) }))
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        Ok(())
    }

    pub fn set_layer_blend_mode(&self, layer_id: u64, blend_mode: String) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        guard
            .session
            .execute("layer.setProps", json!({ "layer": layer_id, "blend": blend_mode }))
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        Ok(())
    }

    pub fn set_layer_locked(&self, layer_id: u64, locked: bool) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        guard
            .session
            .execute("layer.setProps", json!({ "layer": layer_id, "locks": { "pixels": locked } }))
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        Ok(())
    }

    pub fn set_brush(
        &self,
        size: f32,
        hardness: f32,
        opacity: f32,
        flow: f32,
        _color_hex: String,
    ) -> Result<(), EngineError> {
        let clamped_size = size.clamp(1.0, 1000.0);
        let adaptive_spacing = if clamped_size > 180.0 {
            0.28
        } else if clamped_size > 100.0 {
            0.22
        } else if clamped_size > 50.0 {
            0.15
        } else {
            0.10
        };
        let mut guard = self.core.lock();
        guard
            .session
            .execute(
                "tools.setBrush",
                json!({
                    "brush": {
                        "size": clamped_size,
                        "hardness": hardness.clamp(0.0, 1.0),
                        "opacity": opacity.clamp(0.01, 1.0),
                        "flow": flow.clamp(0.01, 1.0),
                        "spacing": adaptive_spacing,
                        "spacingEnabled": true
                    }
                }),
            )
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        Ok(())
    }

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
