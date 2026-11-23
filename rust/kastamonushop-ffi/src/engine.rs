//! PhotoCraft Session wrapper exposed to Kotlin via UniFFI (v0.5.2 — StonebridgeCraft).
//! - Live undo-clean adjustments & pixel filters
//! - 60 FPS GPU-previewed Photo Mirror Effect (`apply_photo_mirror` bakes once on commit)
//! - 60 FPS GPU-previewed Rotate/Flip (`apply_canvas_transform_batch` bakes net result once on commit)
//! - Eyedropper color sampling, Painting symmetry, Canvas crop

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

struct LiveAdjustmentSession {
    layer_id: u64,
    has_updated: bool,
}

struct LiveFilterSession {
    target_id: LayerId,
    prev_active: Option<LayerId>,
    was_locked: bool,
    has_applied_preview: bool,
}

pub(crate) struct EngineCore {
    pub session: Session,
    pub render_generation: u64,
    pub pending_dirty: Option<Rect>,
    pub force_full_redraw: bool,
    live_stroke: Option<ActiveLiveStroke>,
    live_adjustment: Option<LiveAdjustmentSession>,
    live_filter: Option<LiveFilterSession>,
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
            live_adjustment: None,
            live_filter: None,
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

fn find_primary_photo_raster_layer(doc: &Document, active: Option<LayerId>) -> Option<(LayerId, bool)> {
    let active_is_photo = active
        .and_then(|aid| doc.layers.iter().find(|l| l.id == aid))
        .is_some_and(|l| matches!(l.content, LayerContent::Raster(_)) && !l.name.eq_ignore_ascii_case("Çizim Katmanı"));

    let chosen = if active_is_photo {
        active.and_then(|aid| doc.layers.iter().find(|l| l.id == aid))
    } else {
        doc.layers
            .iter()
            .find(|l| matches!(l.content, LayerContent::Raster(_)))
    };

    chosen.map(|l| (l.id, l.locks.pixels))
}

#[uniffi::export]
impl KastamonuEngine {
    pub fn new_document(&self, name: String, width: u32, height: u32) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard.live_adjustment = None;
        guard.live_filter = None;
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
        guard.live_adjustment = None;
        guard.live_filter = None;
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
        guard.live_adjustment = None;
        guard.live_filter = None;
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

    pub fn sample_color_hex(&self, x: f32, y: f32) -> Result<String, EngineError> {
        let mut guard = self.core.lock();
        let out = guard
            .session
            .execute(
                "document.sampleColor",
                json!({ "x": x as f64, "y": y as f64, "size": 3 }),
            )
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        let arr = out
            .as_array()
            .filter(|a| a.len() >= 3)
            .ok_or_else(|| EngineError::CommandFailed { detail: "Geçersiz renk örneği".into() })?;
        let r = (arr[0].as_f64().unwrap_or(0.0).clamp(0.0, 1.0) * 255.0).round() as u8;
        let g = (arr[1].as_f64().unwrap_or(0.0).clamp(0.0, 1.0) * 255.0).round() as u8;
        let b = (arr[2].as_f64().unwrap_or(0.0).clamp(0.0, 1.0) * 255.0).round() as u8;
        Ok(format!("#{r:02X}{g:02X}{b:02X}"))
    }

    pub fn set_symmetry(&self, mode: String) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        let m = mode.trim().to_ascii_lowercase();
        if m.is_empty() || m == "off" {
            let _ = guard.session.execute("paint.symmetryDisable", json!({}));
            return Ok(());
        }
        guard
            .session
            .execute("paint.setSymmetry", json!({ "mode": m }))
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        Ok(())
    }

    /// Applies the net result of GPU-previewed flips and 90-degree rotations in one clean batch.
    pub fn apply_canvas_transform_batch(
        &self,
        quarter_turns_cw: i32,
        flip_h: bool,
        flip_v: bool,
    ) -> Result<DocumentInfo, EngineError> {
        let turns = ((quarter_turns_cw % 4) + 4) % 4;
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard.live_adjustment = None;
        guard.live_filter = None;

        if flip_h {
            guard
                .session
                .execute("image.imageRotation.flipCanvasHorizontal", json!({}))
                .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        }
        if flip_v {
            guard
                .session
                .execute("image.imageRotation.flipCanvasVertical", json!({}))
                .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        }
        match turns {
            1 => {
                guard
                    .session
                    .execute("image.imageRotation.90cw", json!({}))
                    .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
            }
            2 => {
                guard
                    .session
                    .execute("image.imageRotation.180", json!({}))
                    .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
            }
            3 => {
                guard
                    .session
                    .execute("image.imageRotation.90ccw", json!({}))
                    .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
            }
            _ => {}
        }

        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    pub fn crop_canvas(&self, x: i32, y: i32, width: i32, height: i32) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard.live_adjustment = None;
        guard.live_filter = None;
        guard
            .session
            .execute(
                "image.crop",
                json!({
                    "x": x.max(0),
                    "y": y.max(0),
                    "width": width.max(1),
                    "height": height.max(1)
                }),
            )
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    /// Bakes the 60 FPS GPU-previewed Photo Mirror Effect into the photo raster layer once (single clean Undo step).
    /// `mode`: `"left_to_right"`, `"right_to_left"`, `"top_to_bottom"`, `"bottom_to_top"`.
    pub fn apply_photo_mirror(&self, mode: String, axis_ratio: f32) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        let target_id = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            find_primary_photo_raster_layer(ds.doc(), ds.active_layer())
                .map(|(id, _)| id)
                .ok_or_else(|| EngineError::CommandFailed {
                    detail: "Ayna efekti uygulanacak fotoğraf katmanı bulunamadı".into(),
                })?
        };
        let ratio = axis_ratio.clamp(0.05, 0.95);
        guard
            .session
            .edit("Ayna Efekti", |doc, _| {
                let bounds = doc.bounds();
                let w = doc.size.width as usize;
                let h = doc.size.height as usize;
                if w < 2 || h < 2 {
                    return Ok(());
                }
                let Some(layer) = doc.layers.iter_mut().find(|l| l.id == target_id) else {
                    return Ok(());
                };
                let LayerContent::Raster(surf) = &mut layer.content else {
                    return Ok(());
                };
                let ch = surf.format().channels();
                let mut data = surf.read_region(bounds);
                let row_stride = w * ch;
                if data.len() < row_stride * h {
                    return Ok(());
                }

                let ax = ((w as f32) * ratio).round().clamp(1.0, (w - 1) as f32) as isize;
                let ay = ((h as f32) * ratio).round().clamp(1.0, (h - 1) as f32) as isize;

                let ping_pong = |v: isize, len: isize| -> usize {
                    if len <= 1 {
                        return 0;
                    }
                    let period = 2 * len;
                    let m = v.rem_euclid(period);
                    let idx = if m < len { m } else { period - 1 - m };
                    idx.clamp(0, len - 1) as usize
                };

                let src_snap = data.clone();
                let mirror_x_right = match mode.as_str() {
                    "enine_auto" | "left_to_right" => ratio >= 0.5,
                    "enine_flip" | "right_to_left" => ratio < 0.5,
                    _ => false,
                };
                let mirror_x_left = match mode.as_str() {
                    "enine_auto" | "left_to_right" => ratio < 0.5,
                    "enine_flip" | "right_to_left" => ratio >= 0.5,
                    _ => false,
                };
                let mirror_y_bottom = match mode.as_str() {
                    "boyuna_auto" | "top_to_bottom" => ratio >= 0.5,
                    "boyuna_flip" | "bottom_to_top" => ratio < 0.5,
                    _ => false,
                };
                let mirror_y_top = match mode.as_str() {
                    "boyuna_auto" | "top_to_bottom" => ratio < 0.5,
                    "boyuna_flip" | "bottom_to_top" => ratio >= 0.5,
                    _ => false,
                };

                if mirror_x_right {
                    for y in 0..h {
                        let base = y * row_stride;
                        for x in (ax as usize)..w {
                            let sx = ping_pong(2 * ax - 1 - x as isize, w as isize);
                            let dst = base + x * ch;
                            let src = base + sx * ch;
                            data[dst..(dst + ch)].copy_from_slice(&src_snap[src..(src + ch)]);
                        }
                    }
                } else if mirror_x_left {
                    for y in 0..h {
                        let base = y * row_stride;
                        for x in 0..(ax as usize) {
                            let sx = ping_pong(2 * ax - 1 - x as isize, w as isize);
                            let dst = base + x * ch;
                            let src = base + sx * ch;
                            data[dst..(dst + ch)].copy_from_slice(&src_snap[src..(src + ch)]);
                        }
                    }
                } else if mirror_y_bottom {
                    for y in (ay as usize)..h {
                        let sy = ping_pong(2 * ay - 1 - y as isize, h as isize);
                        let src_start = sy * row_stride;
                        let dst_start = y * row_stride;
                        data[dst_start..(dst_start + row_stride)]
                            .copy_from_slice(&src_snap[src_start..(src_start + row_stride)]);
                    }
                } else if mirror_y_top {
                    for y in 0..(ay as usize) {
                        let sy = ping_pong(2 * ay - 1 - y as isize, h as isize);
                        let src_start = sy * row_stride;
                        let dst_start = y * row_stride;
                        data[dst_start..(dst_start + row_stride)]
                            .copy_from_slice(&src_snap[src_start..(src_start + row_stride)]);
                    }
                }

                surf.write_region_par(bounds, &data);
                Ok(())
            })
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;

        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    /// Low-memory O(1)-per-pixel 3-pass separable Box/Gaussian Blur that NEVER hits Android's working-memory cap.
    pub fn apply_blur_fast(&self, radius: f32) -> Result<DocumentInfo, EngineError> {
        let r = radius.round().clamp(1.0, 80.0) as usize;
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        let target_id = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            find_primary_photo_raster_layer(ds.doc(), ds.active_layer())
                .map(|(id, _)| id)
                .ok_or_else(|| EngineError::CommandFailed {
                    detail: "Bulanıklaştırma uygulanacak fotoğraf katmanı bulunamadı".into(),
                })?
        };
        guard
            .session
            .edit("Bulanıklaştırma", |doc, _| {
                let bounds = doc.bounds();
                let w = doc.size.width as usize;
                let h = doc.size.height as usize;
                if w < 2 || h < 2 {
                    return Ok(());
                }
                let Some(layer) = doc.layers.iter_mut().find(|l| l.id == target_id) else {
                    return Ok(());
                };
                let LayerContent::Raster(surf) = &mut layer.content else {
                    return Ok(());
                };
                let ch = surf.format().channels();
                let mut data = surf.read_region(bounds);
                if data.len() < w * h * ch {
                    return Ok(());
                }
                // 3-pass separable sliding-window box blur approximates Gaussian blur with tiny O(max(w,h)) scratch RAM
                let pass_r = ((r as f32) * 0.58).round().max(1.0) as usize;
                let mut line_buf = vec![0.0f32; w.max(h) * ch];
                let mut acc = [0.0f32; 8];
                let active_ch = ch.min(8);

                for _ in 0..3 {
                    // Horizontal pass
                    let win = (2 * pass_r + 1) as f32;
                    for y in 0..h {
                        let row = y * w * ch;
                        for c in 0..active_ch {
                            let first = data[row + c];
                            acc[c] = first * (pass_r + 1) as f32;
                            for k in 1..=pass_r {
                                let xk = k.min(w - 1);
                                acc[c] += data[row + xk * ch + c];
                            }
                        }
                        for x in 0..w {
                            for c in 0..active_ch {
                                line_buf[x * ch + c] = acc[c] / win;
                            }
                            let add_x = (x + pass_r + 1).min(w - 1);
                            let sub_x = x.saturating_sub(pass_r);
                            for c in 0..active_ch {
                                acc[c] += data[row + add_x * ch + c] - data[row + sub_x * ch + c];
                            }
                        }
                        data[row..(row + w * ch)].copy_from_slice(&line_buf[..(w * ch)]);
                    }

                    // Vertical pass
                    for x in 0..w {
                        for c in 0..active_ch {
                            let first = data[x * ch + c];
                            acc[c] = first * (pass_r + 1) as f32;
                            for k in 1..=pass_r {
                                let yk = k.min(h - 1);
                                acc[c] += data[(yk * w + x) * ch + c];
                            }
                        }
                        for y in 0..h {
                            for c in 0..active_ch {
                                line_buf[y * ch + c] = acc[c] / win;
                            }
                            let add_y = (y + pass_r + 1).min(h - 1);
                            let sub_y = y.saturating_sub(pass_r);
                            for c in 0..active_ch {
                                acc[c] += data[(add_y * w + x) * ch + c] - data[(sub_y * w + x) * ch + c];
                            }
                        }
                        for y in 0..h {
                            let idx = (y * w + x) * ch;
                            for c in 0..active_ch {
                                data[idx + c] = line_buf[y * ch + c];
                            }
                        }
                    }
                }

                surf.write_region_par(bounds, &data);
                Ok(())
            })
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    /// Low-memory Unsharp Mask sharpen that NEVER hits Android's working-memory cap.
    pub fn apply_sharpen_fast(&self, amount: f32) -> Result<DocumentInfo, EngineError> {
        let strength = (amount.clamp(0.0, 250.0) / 100.0) * 1.1;
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        let target_id = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            find_primary_photo_raster_layer(ds.doc(), ds.active_layer())
                .map(|(id, _)| id)
                .ok_or_else(|| EngineError::CommandFailed {
                    detail: "Keskinleştirme uygulanacak fotoğraf katmanı bulunamadı".into(),
                })?
        };
        guard
            .session
            .edit("Keskinleştirme", |doc, _| {
                let bounds = doc.bounds();
                let w = doc.size.width as usize;
                let h = doc.size.height as usize;
                if w < 3 || h < 3 {
                    return Ok(());
                }
                let Some(layer) = doc.layers.iter_mut().find(|l| l.id == target_id) else {
                    return Ok(());
                };
                let LayerContent::Raster(surf) = &mut layer.content else {
                    return Ok(());
                };
                let ch = surf.format().channels();
                let rgb_ch = ch.min(3);
                let orig = surf.read_region(bounds);
                if orig.len() < w * h * ch {
                    return Ok(());
                }
                let mut out = orig.clone();
                let row_stride = w * ch;
                for y in 1..(h - 1) {
                    let row = y * row_stride;
                    for x in 1..(w - 1) {
                        let idx = row + x * ch;
                        let left = idx - ch;
                        let right = idx + ch;
                        let up = idx - row_stride;
                        let down = idx + row_stride;
                        for c in 0..rgb_ch {
                            let blur4 = (orig[left + c] + orig[right + c] + orig[up + c] + orig[down + c]) * 0.25;
                            let v = orig[idx + c] + (orig[idx + c] - blur4) * strength;
                            out[idx + c] = v.clamp(0.0, 1.0);
                        }
                    }
                }
                surf.write_region_par(bounds, &out);
                Ok(())
            })
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    /// Creates a single native PhotoCraft adjustment layer with final slider values (called once on Uygula after 60 FPS GPU preview).
    pub fn apply_adjustment_once(&self, kind: String, params_json: String) -> Result<DocumentInfo, EngineError> {
        let params: Value = if params_json.trim().is_empty() {
            json!({})
        } else {
            serde_json::from_str(&params_json)
                .map_err(|e| EngineError::InvalidParams { detail: e.to_string() })?
        };
        let cmd = format!("layer.newAdjustmentLayer.{kind}");
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard
            .session
            .execute(&cmd, params)
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

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

        if let Some(old_adj) = guard.live_adjustment.take() {
            if old_adj.has_updated {
                let _ = guard.session.execute("edit.undo", json!({}));
            }
            let _ = guard.session.execute("edit.undo", json!({}));
        }

        let out = guard
            .session
            .execute(&cmd, params)
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        let layer_id = out
            .get("layer")
            .and_then(Value::as_u64)
            .ok_or_else(|| EngineError::CommandFailed { detail: "Adjustment layer id missing".into() })?;

        if kind != "blackWhite" && kind != "invert" {
            guard.live_adjustment = Some(LiveAdjustmentSession {
                layer_id,
                has_updated: false,
            });
        }
        guard.mark_full_dirty();
        Ok(layer_id)
    }

    pub fn update_live_adjustment(&self, layer_id: u64, params_json: String) -> Result<(), EngineError> {
        let mut params: Value = serde_json::from_str(&params_json)
            .map_err(|e| EngineError::InvalidParams { detail: e.to_string() })?;
        if let Some(obj) = params.as_object_mut() {
            obj.insert("layer".to_string(), json!(layer_id));
        }
        let mut guard = self.core.lock();
        if let Some(mut adj) = guard.live_adjustment.take() {
            if adj.layer_id == layer_id && adj.has_updated {
                let _ = guard.session.execute("edit.undo", json!({}));
                adj.has_updated = false;
            }
            let res = guard.session.execute("layer.setAdjustment", params);
            if res.is_ok() && adj.layer_id == layer_id {
                adj.has_updated = true;
            }
            guard.live_adjustment = Some(adj);
            res.map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        } else {
            guard
                .session
                .execute("layer.setAdjustment", params)
                .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        }
        guard.mark_full_dirty();
        Ok(())
    }

    pub fn commit_live_adjustment(&self) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        guard.live_adjustment = None;
        guard.mark_full_dirty();
        Ok(())
    }

    pub fn cancel_live_adjustment(&self) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        if let Some(adj) = guard.live_adjustment.take() {
            if adj.has_updated {
                let _ = guard.session.execute("edit.undo", json!({}));
            }
            let _ = guard.session.execute("edit.undo", json!({}));
            guard.mark_full_dirty();
        }
        Ok(())
    }

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

    pub fn begin_live_filter(&self) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;

        if let Some(old_lf) = guard.live_filter.take() {
            if old_lf.has_applied_preview {
                let _ = guard.session.execute("edit.undo", json!({}));
            }
            if old_lf.was_locked {
                let _ = guard.session.execute("edit.undo", json!({}));
            }
            if old_lf.prev_active.is_some() {
                guard.session.set_active_layer(old_lf.prev_active);
            }
        }

        let (prev_active, target_id, was_locked) = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            let prev = ds.active_layer();
            let (id, locked) = find_primary_photo_raster_layer(ds.doc(), prev).ok_or_else(|| {
                EngineError::CommandFailed {
                    detail: "Filtre uygulanacak piksel katmanı bulunamadı".into(),
                }
            })?;
            (prev, id, locked)
        };

        guard.session.set_active_layer(Some(target_id));
        if was_locked {
            let _ = guard.session.execute(
                "layer.setProps",
                json!({ "layer": target_id.0, "locks": { "pixels": false } }),
            );
        }

        guard.live_filter = Some(LiveFilterSession {
            target_id,
            prev_active,
            was_locked,
            has_applied_preview: false,
        });
        Ok(())
    }

    pub fn update_live_filter(&self, command_id: String, params_json: String) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        let Some(mut lf) = guard.live_filter.take() else {
            return Ok(());
        };

        if lf.has_applied_preview {
            let _ = guard.session.execute("edit.undo", json!({}));
            lf.has_applied_preview = false;
        }

        let mut err_out: Option<EngineError> = None;
        if !command_id.trim().is_empty() {
            match serde_json::from_str::<Value>(&params_json) {
                Ok(params) => {
                    guard.session.set_active_layer(Some(lf.target_id));
                    match guard.session.execute(&command_id, params) {
                        Ok(_) => lf.has_applied_preview = true,
                        Err(e) => err_out = Some(EngineError::CommandFailed { detail: e.to_string() }),
                    }
                }
                Err(e) => err_out = Some(EngineError::InvalidParams { detail: e.to_string() }),
            }
        }

        guard.live_filter = Some(lf);
        guard.mark_full_dirty();

        if let Some(err) = err_out {
            Err(err)
        } else {
            Ok(())
        }
    }

    pub fn commit_live_filter(&self) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        let Some(lf) = guard.live_filter.take() else {
            return Ok(());
        };
        if lf.was_locked {
            if lf.has_applied_preview {
                let _ = guard.session.execute(
                    "layer.setProps",
                    json!({ "layer": lf.target_id.0, "locks": { "pixels": true } }),
                );
            } else {
                let _ = guard.session.execute("edit.undo", json!({}));
            }
        }
        if lf.prev_active.is_some() {
            guard.session.set_active_layer(lf.prev_active);
        }
        guard.mark_full_dirty();
        Ok(())
    }

    pub fn cancel_live_filter(&self) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        let Some(lf) = guard.live_filter.take() else {
            return Ok(());
        };
        if lf.has_applied_preview {
            let _ = guard.session.execute("edit.undo", json!({}));
        }
        if lf.was_locked {
            let _ = guard.session.execute("edit.undo", json!({}));
        }
        if lf.prev_active.is_some() {
            guard.session.set_active_layer(lf.prev_active);
        }
        guard.mark_full_dirty();
        Ok(())
    }

    pub fn undo(&self) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard.live_adjustment = None;
        guard.live_filter = None;
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
        guard.live_adjustment = None;
        guard.live_filter = None;
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
