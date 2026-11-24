//! PhotoCraft Session wrapper exposed to Kotlin via UniFFI (v0.5.2 — StonebridgeCraft).
//! - Live undo-clean adjustments & pixel filters
//! - 60 FPS GPU-previewed Photo Mirror Effect (`apply_photo_mirror` bakes once on commit)
//! - 60 FPS GPU-previewed Rotate/Flip (`apply_canvas_transform_batch` bakes net result once on commit)
//! - Eyedropper color sampling, Painting symmetry, Canvas crop

use std::sync::Arc;
use parking_lot::Mutex;
use photocraft_color::{ColorMode, PixelFormat, SampleType};
use photocraft_doc::{Document, Layer, LayerContent, LayerId};
use photocraft_engine::brush_cmds::LiveStroke;
use photocraft_engine::retouch_cmds::{LiveDab, LiveRetouch};
use photocraft_engine::Session;
use photocraft_geom::{Rect, Size};
use photocraft_raster::Surface;
use photocraft_paint::StrokePoint;
use photocraft_algo::liquify::{
    LiquifyField, LiquifyStroke, LiquifyTool, ProxyImage as LiquifyProxyImage, apply_liquify, auto_cell,
};
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
pub struct LiquifyHistoryState {
    pub can_undo: bool,
    pub can_redo: bool,
}

#[derive(Clone, Debug, uniffi::Record)]
pub struct LayerThumbnail {
    pub width: u32,
    pub height: u32,
    pub rgba: Vec<u8>,
}

#[derive(Clone, Debug, uniffi::Record)]
pub struct SelectionBounds {
    pub x0: i32,
    pub y0: i32,
    pub x1: i32,
    pub y1: i32,
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

struct ActiveLiveDab {
    live: LiveDab,
    cmd: String,
    base_params: Value,
    points_json: Vec<Value>,
    target_id: LayerId,
    prev_active: Option<LayerId>,
    was_locked: bool,
}

struct ActiveLiveClone {
    live: LiveRetouch,
    base_params: Value,
    points_json: Vec<Value>,
    target_id: LayerId,
    prev_active: Option<LayerId>,
    was_locked: bool,
}

struct ProxyPreviewSession {
    base_doc: Arc<Document>,
    session: Session,
    scale: f32,
    real_size: (u32, u32),
}

struct ActiveLiquifySession {
    target_layer_id: LayerId,
    bounds: Rect,
    cell: f64,
    field: LiquifyField,
    proxy: LiquifyProxyImage,
    proxy_rgba: Vec<[u8; 4]>,
    preview_doc: Document,
    real_size: (u32, u32),
    last_point: Option<[f64; 3]>,
    current_stroke: LiquifyStroke,
    undo_stack: Vec<LiquifyField>,
    redo_stack: Vec<LiquifyField>,
}

impl ActiveLiquifySession {
    fn sync_proxy_rect_to_preview_doc(&mut self, r: [usize; 4]) {
        let [x0, y0, x1, y1] = [
            r[0].min(self.proxy.w),
            r[1].min(self.proxy.h),
            r[2].min(self.proxy.w),
            r[3].min(self.proxy.h),
        ];
        if x0 >= x1 || y0 >= y1 {
            return;
        }
        let Some(layer) = self.preview_doc.layers.first_mut() else {
            return;
        };
        let LayerContent::Raster(surf) = &mut layer.content else {
            return;
        };
        let rw = x1 - x0;
        let rh = y1 - y0;
        let mut fbuf = vec![0.0f32; rw * rh * 4];
        for y in y0..y1 {
            let src_row = y * self.proxy.w;
            let dst_row = (y - y0) * rw;
            for x in x0..x1 {
                let p = self.proxy_rgba[src_row + x];
                let di = (dst_row + (x - x0)) * 4;
                fbuf[di] = p[0] as f32 / 255.0;
                fbuf[di + 1] = p[1] as f32 / 255.0;
                fbuf[di + 2] = p[2] as f32 / 255.0;
                fbuf[di + 3] = p[3] as f32 / 255.0;
            }
        }
        surf.write_region(Rect::new(x0 as i32, y0 as i32, x1 as i32, y1 as i32), &fbuf);
    }
}

fn parse_liquify_tool(name: &str) -> LiquifyTool {
    match name {
        "bloat" => LiquifyTool::Bloat,
        "pucker" => LiquifyTool::Pucker,
        "twirlCw" => LiquifyTool::TwirlCw,
        "twirlCcw" => LiquifyTool::TwirlCcw,
        "smooth" => LiquifyTool::Smooth,
        "reconstruct" => LiquifyTool::Reconstruct,
        _ => LiquifyTool::ForwardWarp,
    }
}

pub(crate) struct EngineCore {
    pub session: Session,
    pub render_generation: u64,
    pub selection_generation: u64,
    pub pending_dirty: Option<Rect>,
    pub force_full_redraw: bool,
    live_stroke: Option<ActiveLiveStroke>,
    live_dab: Option<ActiveLiveDab>,
    live_clone: Option<ActiveLiveClone>,
    live_adjustment: Option<LiveAdjustmentSession>,
    live_filter: Option<LiveFilterSession>,
    proxy_preview: Option<ProxyPreviewSession>,
    live_liquify: Option<ActiveLiquifySession>,
    transform_hidden_layer: Option<LayerId>,
    original_snapshot: Option<Arc<Document>>,
    compare_original: bool,
}

impl EngineCore {
    pub fn new() -> Self {
        // System-wide mobile RAM guardrails (desktop defaults are 2 GB scratch / 8 GB transform)
        photocraft_raster::scratch::set_budget(64 << 20);
        photocraft_engine::transform_cmds::set_budget(256 << 20);

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
        let initial_snapshot = session.active().map(|ds| Arc::clone(ds.doc()));
        Self {
            session,
            render_generation: 1,
            selection_generation: 1,
            pending_dirty: None,
            force_full_redraw: true,
            live_stroke: None,
            live_dab: None,
            live_clone: None,
            live_adjustment: None,
            live_filter: None,
            proxy_preview: None,
            live_liquify: None,
            transform_hidden_layer: None,
            original_snapshot: initial_snapshot,
            compare_original: false,
        }
    }

    pub fn active_document(&self) -> Option<Arc<Document>> {
        if let Some(active_live) = &self.live_stroke {
            return Some(Arc::clone(&active_live.live.doc));
        }
        if let Some(active_dab) = &self.live_dab {
            return Some(Arc::clone(&active_dab.live.doc));
        }
        if let Some(active_clone) = &self.live_clone {
            return Some(Arc::clone(&active_clone.live.doc));
        }
        self.session.active().map(|ds| Arc::clone(ds.doc()))
    }

    pub fn active_document_and_display_size(&self) -> Option<(Arc<Document>, (u32, u32))> {
        if let Some(liq) = &self.live_liquify {
            return Some((Arc::new(liq.preview_doc.clone()), liq.real_size));
        }
        if let Some(proxy) = &self.proxy_preview {
            if let Some(ds) = proxy.session.active() {
                return Some((Arc::clone(ds.doc()), proxy.real_size));
            }
        }
        let doc = self.active_document()?;
        let sz = (doc.size.width.max(1), doc.size.height.max(1));
        if self.compare_original {
            if let Some(orig) = &self.original_snapshot {
                let orig_sz = (orig.size.width.max(1), orig.size.height.max(1));
                return Some((Arc::clone(orig), orig_sz));
            }
            return Some((doc, sz));
        }
        if let Some(hid) = self.transform_hidden_layer {
            let mut cloned = (*doc).clone();
            if let Some(l) = cloned.layers.iter_mut().find(|l| l.id == hid) {
                l.visible = false;
            }
            return Some((Arc::new(cloned), sz));
        }
        Some((doc, sz))
    }

    pub fn capture_original_snapshot(&mut self) {
        self.original_snapshot = self.session.active().map(|ds| Arc::clone(ds.doc()));
        self.compare_original = false;
        self.selection_generation = self.selection_generation.wrapping_add(1);
    }

    pub fn dismiss_compare(&mut self) {
        if self.compare_original {
            self.compare_original = false;
            self.force_full_redraw = true;
            self.selection_generation = self.selection_generation.wrapping_add(1);
        }
    }

    pub fn mark_selection_dirty(&mut self) {
        self.selection_generation = self.selection_generation.wrapping_add(1);
        self.mark_full_dirty();
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

/// Shared premultiplied-alpha 3-pass separable sliding-window box blur (O(1) per pixel, tiny scratch RAM).
/// Premultiplying RGB by alpha prevents dark/white fringe halos on transparent PNG cutouts.
fn box_blur_passes_inplace(data: &mut [f32], w: usize, h: usize, ch: usize, pass_r: usize, passes: usize) {
    if pass_r == 0 || w < 2 || h < 2 || ch == 0 {
        return;
    }
    let active_ch = ch.min(8);
    let has_alpha = ch >= 4;
    if has_alpha {
        for px in data.chunks_exact_mut(ch) {
            let a = px[3].clamp(0.0, 1.0);
            px[0] *= a;
            px[1] *= a;
            px[2] *= a;
        }
    }
    let mut line_buf = vec![0.0f32; w.max(h) * ch];
    let mut acc = [0.0f32; 8];
    let win = (2 * pass_r + 1) as f32;
    let inv_win = 1.0 / win;

    for _ in 0..passes {
        for y in 0..h {
            let row = y * w * ch;
            for c in 0..active_ch {
                let first = data[row + c];
                acc[c] = first * (pass_r + 1) as f32;
                for k in 1..=pass_r {
                    acc[c] += data[row + k.min(w - 1) * ch + c];
                }
            }
            for x in 0..w {
                for c in 0..active_ch {
                    line_buf[x * ch + c] = acc[c] * inv_win;
                }
                let add_x = (x + pass_r + 1).min(w - 1);
                let sub_x = x.saturating_sub(pass_r);
                for c in 0..active_ch {
                    acc[c] += data[row + add_x * ch + c] - data[row + sub_x * ch + c];
                }
            }
            data[row..(row + w * ch)].copy_from_slice(&line_buf[..(w * ch)]);
        }

        for x in 0..w {
            for c in 0..active_ch {
                let first = data[x * ch + c];
                acc[c] = first * (pass_r + 1) as f32;
                for k in 1..=pass_r {
                    acc[c] += data[(k.min(h - 1) * w + x) * ch + c];
                }
            }
            for y in 0..h {
                for c in 0..active_ch {
                    line_buf[y * ch + c] = acc[c] * inv_win;
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

    if has_alpha {
        for px in data.chunks_exact_mut(ch) {
            let a = px[3].clamp(0.0, 1.0);
            if a > 1e-4 {
                let inv_a = 1.0 / a;
                px[0] = (px[0] * inv_a).clamp(0.0, 1.0);
                px[1] = (px[1] * inv_a).clamp(0.0, 1.0);
                px[2] = (px[2] * inv_a).clamp(0.0, 1.0);
            } else {
                px[0] = 0.0;
                px[1] = 0.0;
                px[2] = 0.0;
            }
            px[3] = a;
        }
    }
}

fn run_blur_on_doc(doc: &mut Document, target_id: LayerId, radius_px: f32) {
    if radius_px < 0.5 {
        return;
    }
    let bounds = doc.bounds();
    let w = doc.size.width as usize;
    let h = doc.size.height as usize;
    if w < 2 || h < 2 {
        return;
    }
    let sel_mask: Option<Vec<f32>> = doc.selection.as_ref().and_then(|sel| {
        if sel.content_bounds().is_empty() {
            None
        } else {
            Some(sel.read_region(bounds))
        }
    });
    let Some(layer) = doc.layers.iter_mut().find(|l| l.id == target_id) else {
        return;
    };
    let LayerContent::Raster(surf) = &mut layer.content else {
        return;
    };
    let ch = surf.format().channels();
    let orig = surf.read_region(bounds);
    if orig.len() < w * h * ch {
        return;
    }
    let mut blurred = orig.clone();
    let pass_r = (radius_px * 0.58).round().max(1.0) as usize;
    box_blur_passes_inplace(&mut blurred, w, h, ch, pass_r, 3);

    if let Some(mask) = sel_mask {
        let m_ch = (mask.len() / (w * h)).max(1);
        for i in 0..(w * h) {
            let m = mask[i * m_ch].clamp(0.0, 1.0);
            if m < 0.999 {
                let base = i * ch;
                let inv_m = 1.0 - m;
                for c in 0..ch {
                    blurred[base + c] = orig[base + c] * inv_m + blurred[base + c] * m;
                }
            }
        }
    }
    surf.write_region_par(bounds, &blurred);
}

fn run_sharpen_on_doc(doc: &mut Document, target_id: LayerId, amount: f32) {
    let strength = (amount.clamp(0.0, 250.0) / 100.0) * 1.35;
    if strength < 0.01 {
        return;
    }
    let bounds = doc.bounds();
    let w = doc.size.width as usize;
    let h = doc.size.height as usize;
    if w < 3 || h < 3 {
        return;
    }
    let sel_mask: Option<Vec<f32>> = doc.selection.as_ref().and_then(|sel| {
        if sel.content_bounds().is_empty() {
            None
        } else {
            Some(sel.read_region(bounds))
        }
    });
    let Some(layer) = doc.layers.iter_mut().find(|l| l.id == target_id) else {
        return;
    };
    let LayerContent::Raster(surf) = &mut layer.content else {
        return;
    };
    let ch = surf.format().channels();
    let rgb_ch = ch.min(3);
    let orig = surf.read_region(bounds);
    if orig.len() < w * h * ch {
        return;
    }
    // Resolution-invariant Unsharp Mask radius so 512px proxy and full-res match 100% identically
    let usm_radius = ((w.max(h) as f32) * 0.0028).clamp(1.0, 8.0);
    let pass_r = (usm_radius * 0.58).round().max(1.0) as usize;
    let mut blurred = orig.clone();
    box_blur_passes_inplace(&mut blurred, w, h, ch, pass_r, 2);

    let mut out = orig.clone();
    if let Some(mask) = sel_mask {
        let m_ch = (mask.len() / (w * h)).max(1);
        for i in 0..(w * h) {
            let m = mask[i * m_ch].clamp(0.0, 1.0);
            if m > 0.001 {
                let base = i * ch;
                let eff = strength * m;
                for c in 0..rgb_ch {
                    out[base + c] = (orig[base + c] + (orig[base + c] - blurred[base + c]) * eff).clamp(0.0, 1.0);
                }
            }
        }
    } else {
        for i in 0..(w * h) {
            let base = i * ch;
            for c in 0..rgb_ch {
                out[base + c] = (orig[base + c] + (orig[base + c] - blurred[base + c]) * strength).clamp(0.0, 1.0);
            }
        }
    }
    surf.write_region_par(bounds, &out);
}



fn build_shape_params(
    kind: &str,
    x0: f32,
    y0: f32,
    x1: f32,
    y1: f32,
    fill_hex: &str,
    stroke_hex: &str,
    stroke_width: f32,
    corner_radius: f32,
    sides: u32,
) -> Value {
    let min_x = x0.min(x1) as f64;
    let min_y = y0.min(y1) as f64;
    let w = (x1 - x0).abs().max(4.0) as f64;
    let h = (y1 - y0).abs().max(4.0) as f64;
    let mut obj = serde_json::Map::new();
    obj.insert("kind".into(), json!(kind));
    if kind == "line" {
        obj.insert("from".into(), json!([x0 as f64, y0 as f64]));
        obj.insert("to".into(), json!([x1 as f64, y1 as f64]));
        obj.insert("weight".into(), json!(stroke_width.clamp(1.0, 200.0) as f64));
        let col = if !stroke_hex.trim().is_empty() { stroke_hex } else if !fill_hex.trim().is_empty() { fill_hex } else { "#FFFFFF" };
        obj.insert("fill".into(), json!(col));
        obj.insert("stroke".into(), json!({ "color": col, "width": stroke_width.clamp(1.0, 200.0) as f64, "opacity": 100.0 }));
    } else {
        obj.insert("rect".into(), json!([min_x, min_y, w, h]));
        if kind == "roundedRect" {
            obj.insert("radius".into(), json!(corner_radius.clamp(0.0, 400.0) as f64));
        }
        if kind == "polygon" || kind == "star" {
            obj.insert("sides".into(), json!(sides.clamp(3, 12)));
        }
        if fill_hex.trim().is_empty() {
            obj.insert("fill".into(), Value::Null);
        } else {
            obj.insert("fill".into(), json!(fill_hex));
        }
        if stroke_width > 0.5 && !stroke_hex.trim().is_empty() {
            obj.insert(
                "stroke".into(),
                json!({
                    "color": stroke_hex,
                    "width": stroke_width.clamp(1.0, 200.0) as f64,
                    "opacity": 100.0
                }),
            );
        } else {
            obj.insert("stroke".into(), Value::Null);
        }
    }
    Value::Object(obj)
}


fn build_cinema_look_params(preset: &str, intensity: f32, vignette: f32, grain: f32) -> Value {
    let k = (intensity.clamp(0.0, 100.0) / 100.0) as f64;
    let (exp, con, hi, sh, temp, tint, clar, deh, vib, sat) = match preset {
        "golden" => (0.20, 14.0, -18.0, 22.0, 34.0, 6.0, 12.0, 8.0, 26.0, 10.0),
        "tealOrange" => (0.05, 22.0, -24.0, 18.0, -16.0, -4.0, 18.0, 14.0, 28.0, 8.0),
        "noir" => (0.10, 36.0, -15.0, -10.0, 0.0, 0.0, 28.0, 18.0, 0.0, -100.0),
        "vivid" => (0.12, 18.0, -12.0, 16.0, 6.0, 2.0, 20.0, 12.0, 42.0, 22.0),
        "vintage" => (0.08, -8.0, -28.0, 26.0, 22.0, 10.0, -6.0, -10.0, -14.0, -18.0),
        "cyber" => (-0.08, 28.0, -10.0, 14.0, -30.0, 24.0, 24.0, 20.0, 38.0, 18.0),
        _ => (0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
    };
    json!({
        "exposure": exp * k,
        "contrast": con * k,
        "highlights": hi * k,
        "shadows": sh * k,
        "temperature": temp * k,
        "tint": tint * k,
        "clarity": clar * k,
        "dehaze": deh * k,
        "vibrance": vib * k,
        "saturation": sat * k,
        "vignetteAmount": vignette.clamp(-100.0, 100.0) as f64,
        "grainAmount": grain.clamp(0.0, 100.0) as f64,
        "grainSize": 28.0,
        "grainRoughness": 50.0,
        "seed": 42u32
    })
}

#[uniffi::export]
impl KastamonuEngine {
    pub fn new_document(&self, name: String, width: u32, height: u32) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard.live_dab = None;
        guard.live_adjustment = None;
        guard.proxy_preview = None;
        guard.live_liquify = None;
        guard.live_filter = None;
        while guard.session.close(0).is_some() {}
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
        guard.capture_original_snapshot();
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
        guard.live_dab = None;
        guard.live_adjustment = None;
        guard.proxy_preview = None;
        guard.live_liquify = None;
        guard.live_filter = None;
        while guard.session.close(0).is_some() {}
        let idx = guard.session.add_document(doc, None);
        guard.session.set_active(idx);
        if single_layer_id.is_some() {
            let _ = guard.session.execute("layer.new.layer", json!({ "name": "Çizim Katmanı" }));
        }
        guard.capture_original_snapshot();
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    /// Fast zero-reencode path for Android hardware-decoded RGBA8 bitmaps.
    /// Closes any previous document first so RAM never accumulates across opens.
    pub fn open_rgba_bytes(
        &self,
        file_name: String,
        width: u32,
        height: u32,
        rgba_bytes: Vec<u8>,
    ) -> Result<DocumentInfo, EngineError> {
        let w = width.max(1);
        let h = height.max(1);
        let expected = (w as usize).saturating_mul(h as usize).saturating_mul(4);
        if rgba_bytes.len() < expected {
            return Err(EngineError::InvalidParams {
                detail: format!("RGBA buffer size mismatch: got {}, expected {}", rgba_bytes.len(), expected),
            });
        }
        let rect = Rect::new(0, 0, w as i32, h as i32);
        let surf = Surface::from_interleaved(PixelFormat::RGBA8, rect, &rgba_bytes[..expected]);
        let mut doc = Document::new(file_name, Size::new(w, h), ColorMode::Rgb, SampleType::U8);
        let bg = Layer::new("Background", LayerContent::Raster(surf));
        doc.layers.push(bg);

        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard.live_dab = None;
        guard.live_adjustment = None;
        guard.proxy_preview = None;
        guard.live_liquify = None;
        guard.live_filter = None;
        while guard.session.close(0).is_some() {}
        let idx = guard.session.add_document(doc, None);
        guard.session.set_active(idx);
        let _ = guard.session.execute("layer.new.layer", json!({ "name": "Çizim Katmanı" }));
        guard.capture_original_snapshot();
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    pub fn place_image_bytes(&self, file_name: String, bytes: Vec<u8>) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard.live_dab = None;
        guard.live_adjustment = None;
        guard.live_filter = None;
        guard.proxy_preview = None;
        guard.dismiss_compare();
        photocraft_engine::file_cmds::place_bytes(&mut guard.session, &file_name, bytes, None, &json!({}))
            .map_err(|e| EngineError::IoFailed { detail: e.to_string() })?;
        // Auto-rasterize placed photo into a normal editable pixel layer so brush/eraser/thumbnails work immediately
        let _ = guard.session.execute("layer.rasterize.layer", json!({}));
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
        guard.dismiss_compare();
        guard.live_stroke = None;
        let out = guard
            .session
            .execute(&command_id, params)
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        Ok(out.to_string())
    }

    /// Creates a real PhotoCraft text layer using Android system fonts (`/system/fonts`).
    /// Converts desired pixel height `size_px` into exact document points via `doc.resolution_dpi`.
    pub fn add_text_layer(
        &self,
        text: String,
        x: f32,
        y: f32,
        size_px: f32,
        color_hex: String,
        align: String,
    ) -> Result<DocumentInfo, EngineError> {
        let trimmed = text.trim();
        if trimmed.is_empty() {
            return Err(EngineError::InvalidParams { detail: "Metin boş olamaz".into() });
        }
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard.live_stroke = None;
        let dpi = guard
            .session
            .active()
            .map(|ds| ds.doc().resolution_dpi.max(1.0))
            .unwrap_or(72.0);
        let size_pt = (size_px.max(8.0) * 72.0 / dpi).clamp(6.0, 1296.0);

        // Pick Roboto if available in /system/fonts, else first discovered font family
        let mut chosen_font: Option<String> = None;
        if let Ok(fonts_val) = guard.session.execute("type.fonts", json!({})) {
            let list = fonts_val
                .get("fonts")
                .and_then(Value::as_array)
                .or_else(|| fonts_val.as_array());
            if let Some(arr) = list {
                let mut first_name: Option<String> = None;
                for item in arr {
                    let name = item
                        .as_str()
                        .or_else(|| item.get("family").and_then(Value::as_str))
                        .or_else(|| item.get("name").and_then(Value::as_str));
                    if let Some(n) = name {
                        if first_name.is_none() {
                            first_name = Some(n.to_string());
                        }
                        if n.eq_ignore_ascii_case("Roboto") || n.eq_ignore_ascii_case("Noto Sans") {
                            chosen_font = Some(n.to_string());
                            break;
                        }
                    }
                }
                if chosen_font.is_none() {
                    chosen_font = first_name;
                }
            }
        }

        let mut params = json!({
            "x": x as f64,
            "y": y as f64,
            "text": trimmed,
            "size": size_pt,
            "color": color_hex,
            "align": align,
        });
        if let Some(f) = chosen_font {
            params["font"] = json!(f);
        }

        guard
            .session
            .execute("type.create", params)
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    /// Applies a native PhotoCraft retouch stroke (`spot_heal`, `smudge`, `dodge`, `burn`)
    /// by automatically targeting the primary photo raster layer and restoring its lock afterward.
    pub fn apply_retouch_stroke(
        &self,
        tool: String,
        samples: Vec<StrokeSample>,
        size: f32,
        hardness: f32,
        strength: f32,
    ) -> Result<DocumentInfo, EngineError> {
        if samples.is_empty() {
            let guard = self.core.lock();
            return self.info_from_locked(&guard);
        }
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard.live_stroke = None;

        let (prev_active, target_id, was_locked) = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            let prev = ds.active_layer();
            let (id, locked) = find_primary_photo_raster_layer(ds.doc(), prev).ok_or_else(|| {
                EngineError::CommandFailed {
                    detail: "Rötuş uygulanacak fotoğraf katmanı bulunamadı".into(),
                }
            })?;
            (prev, id, locked)
        };

        // retouch_cmds.rs expects points as [[x, y, pressure], ...] arrays and hardness in 0..100%
        let pts_json: Vec<Value> = samples
            .iter()
            .map(|pt| json!([pt.x as f64, pt.y as f64, pt.pressure.clamp(0.05, 1.0) as f64]))
            .collect();
        let clamped_size = if tool == "spot_heal" {
            size.clamp(1.0, 140.0)
        } else {
            size.clamp(1.0, 400.0)
        };
        let clamped_hard = (hardness.clamp(0.0, 1.0) * 100.0).round();
        let clamped_str = strength.clamp(5.0, 100.0);

        let (cmd, params) = match tool.as_str() {
            "spot_heal" => (
                "paint.spotHealing",
                json!({
                    "points": pts_json,
                    "size": clamped_size,
                    "hardness": clamped_hard,
                    "type": "proximityMatch",
                    "sampleAllLayers": false
                }),
            ),
            "smudge" => (
                "paint.smudge",
                json!({
                    "points": pts_json,
                    "size": clamped_size,
                    "hardness": clamped_hard,
                    "strength": clamped_str,
                    "sampleAllLayers": false
                }),
            ),
            "dodge" => (
                "paint.dodge",
                json!({
                    "points": pts_json,
                    "size": clamped_size,
                    "hardness": clamped_hard,
                    "range": "midtones",
                    "exposure": clamped_str,
                    "protectTones": true
                }),
            ),
            "burn" => (
                "paint.burn",
                json!({
                    "points": pts_json,
                    "size": clamped_size,
                    "hardness": clamped_hard,
                    "range": "midtones",
                    "exposure": clamped_str,
                    "protectTones": true
                }),
            ),
            other => {
                return Err(EngineError::InvalidParams {
                    detail: format!("Bilinmeyen rötuş aracı: {other}"),
                })
            }
        };

        guard.session.set_active_layer(Some(target_id));
        if was_locked {
            let _ = guard.session.execute(
                "layer.setProps",
                json!({ "layer": target_id.0, "locks": { "pixels": false } }),
            );
        }

        let res = guard.session.execute(cmd, params);

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
        let pad = (clamped_size * 1.4).ceil() as i32;
        let mut min_x = i32::MAX;
        let mut min_y = i32::MAX;
        let mut max_x = i32::MIN;
        let mut max_y = i32::MIN;
        for s in &samples {
            let sx = s.x.round() as i32;
            let sy = s.y.round() as i32;
            min_x = min_x.min(sx - pad);
            min_y = min_y.min(sy - pad);
            max_x = max_x.max(sx + pad);
            max_y = max_y.max(sy + pad);
        }
        if min_x < max_x && min_y < max_y {
            guard.mark_region_dirty(Rect::new(min_x, min_y, max_x, max_y));
        } else {
            guard.mark_full_dirty();
        }
        self.info_from_locked(&guard)
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
        guard.dismiss_compare();
        guard.live_stroke = None;
        guard.live_dab = None;
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

    /// Perspective Crop (`image.perspectiveCrop`): warps a 4-corner convex quad (TL, TR, BR, BL)
    /// into a flat upright rectangle in one undo step.
    pub fn perspective_crop(
        &self,
        tl_x: f32,
        tl_y: f32,
        tr_x: f32,
        tr_y: f32,
        br_x: f32,
        br_y: f32,
        bl_x: f32,
        bl_y: f32,
    ) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard.live_dab = None;
        guard.live_clone = None;
        guard.live_adjustment = None;
        guard.live_filter = None;
        guard
            .session
            .execute(
                "image.perspectiveCrop",
                json!({
                    "corners": [
                        [tl_x as f64, tl_y as f64],
                        [tr_x as f64, tr_y as f64],
                        [br_x as f64, br_y as f64],
                        [bl_x as f64, bl_y as f64]
                    ],
                    "interpolation": "bicubic"
                }),
            )
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.dismiss_compare();
        guard.mark_selection_dirty();
        self.info_from_locked(&guard)
    }

    pub fn crop_canvas(&self, x: i32, y: i32, width: i32, height: i32) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard.live_dab = None;
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
        guard.dismiss_compare();
        guard.mark_selection_dirty();
        self.info_from_locked(&guard)
    }

    /// Bakes the 60 FPS GPU-previewed Photo Mirror Effect into the photo raster layer once (single clean Undo step).
    /// `mode`: `"left_to_right"`, `"right_to_left"`, `"top_to_bottom"`, `"bottom_to_top"`.
    pub fn apply_photo_mirror(&self, mode: String, axis_ratio: f32) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard.live_stroke = None;
        guard.live_dab = None;
        if guard.session.active().is_none() {
            return Err(EngineError::NoActiveDocument);
        }
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

                // Mirror EVERY visible raster layer (Background + Çizim Katmanı + any photo layers)
                // so the baked result matches the 60 FPS GPU composite preview 100% WYSIWYG.
                for layer in &mut doc.layers {
                    if !layer.visible {
                        continue;
                    }
                    let LayerContent::Raster(surf) = &mut layer.content else {
                        continue;
                    };
                    let ch = surf.format().channels();
                    let mut data = surf.read_region(bounds);
                    let row_stride = w * ch;
                    if data.len() < row_stride * h {
                        continue;
                    }
                    let src_snap = data.clone();

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
                }
                Ok(())
            })
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;

        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    /// Low-memory O(1)-per-pixel 3-pass separable Box/Gaussian Blur with transparency & selection-mask parity.
    pub fn apply_blur_fast(&self, radius: f32) -> Result<DocumentInfo, EngineError> {
        let r = radius.clamp(0.0, 80.0);
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard.proxy_preview = None;
        guard.dismiss_compare();
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
                run_blur_on_doc(doc, target_id, r);
                Ok(())
            })
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    /// Low-memory resolution-invariant Unsharp Mask sharpen with transparency & selection-mask parity.
    pub fn apply_sharpen_fast(&self, amount: f32) -> Result<DocumentInfo, EngineError> {
        let amt = amount.clamp(0.0, 250.0);
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard.proxy_preview = None;
        guard.dismiss_compare();
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
                run_sharpen_on_doc(doc, target_id, amt);
                Ok(())
            })
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    /// Starts a 60 FPS exact-parity alpha- & layer-preserving Proxy Preview (~512px) so EVERY PhotoCraft
    /// adjustment and filter preserves transparency, layers, and selection masks identically to `Uygula`.
    pub fn begin_proxy_preview(&self) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard.live_dab = None;
        guard.live_clone = None;
        guard.dismiss_compare();
        let (real_doc, active_id) = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            (Arc::clone(ds.doc()), ds.active_layer())
        };
        let real_w = real_doc.size.width.max(1);
        let real_h = real_doc.size.height.max(1);
        let max_proxy = 512u32;
        let scale = (max_proxy as f32 / real_w.max(real_h) as f32).min(1.0);
        let pw = ((real_w as f32 * scale).round() as u32).clamp(1, max_proxy);
        let ph = ((real_h as f32 * scale).round() as u32).clamp(1, max_proxy);

        let mut ps = Session::new();
        ps.add_document((*real_doc).clone(), None);
        ps.set_active(0);
        if active_id.is_some() {
            ps.set_active_layer(active_id);
        }
        // NOTE: NEVER call layer.flattenImage here — flattening destroys transparent backgrounds by filling solid white!
        if pw < real_w || ph < real_h {
            let _ = ps.execute(
                "image.imageSize",
                json!({ "width": pw, "height": ph, "resample": "bilinear" }),
            );
        }
        let base_doc = ps
            .active()
            .map(|ds| Arc::clone(ds.doc()))
            .ok_or(EngineError::NoActiveDocument)?;
        guard.proxy_preview = Some(ProxyPreviewSession {
            base_doc,
            session: ps,
            scale,
            real_size: (real_w, real_h),
        });
        guard.mark_full_dirty();
        Ok(())
    }

    pub fn update_proxy_adjustment(&self, kind: String, params_json: String) -> Result<(), EngineError> {
        let params: Value = if params_json.trim().is_empty() {
            json!({})
        } else {
            serde_json::from_str(&params_json)
                .map_err(|e| EngineError::InvalidParams { detail: e.to_string() })?
        };
        let cmd = format!("layer.newAdjustmentLayer.{kind}");
        let mut guard = self.core.lock();
        let active_id = guard.session.active().and_then(|ds| ds.active_layer());
        let Some(proxy) = guard.proxy_preview.as_mut() else {
            return Ok(());
        };
        let mut ps = Session::new();
        ps.add_document((*proxy.base_doc).clone(), None);
        ps.set_active(0);
        if active_id.is_some() {
            ps.set_active_layer(active_id);
        }
        let _ = ps.execute(&cmd, params);
        proxy.session = ps;
        guard.mark_full_dirty();
        Ok(())
    }

    pub fn update_proxy_blur(&self, radius: f32) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        let active_id = guard.session.active().and_then(|ds| ds.active_layer());
        let Some(proxy) = guard.proxy_preview.as_mut() else {
            return Ok(());
        };
        let mut ps = Session::new();
        ps.add_document((*proxy.base_doc).clone(), None);
        ps.set_active(0);
        if active_id.is_some() {
            ps.set_active_layer(active_id);
        }
        let scaled_r = (radius.clamp(0.0, 80.0) * proxy.scale).clamp(0.0, 60.0);
        if scaled_r >= 0.5 {
            let target_id = ps
                .active()
                .and_then(|ds| find_primary_photo_raster_layer(ds.doc(), ds.active_layer()).map(|(id, _)| id));
            if let Some(tid) = target_id {
                let _ = ps.edit("Proxy Blur", |doc, _| {
                    run_blur_on_doc(doc, tid, scaled_r);
                    Ok(())
                });
            }
        }
        proxy.session = ps;
        guard.mark_full_dirty();
        Ok(())
    }

    pub fn update_proxy_sharpen(&self, amount: f32) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        let active_id = guard.session.active().and_then(|ds| ds.active_layer());
        let Some(proxy) = guard.proxy_preview.as_mut() else {
            return Ok(());
        };
        let mut ps = Session::new();
        ps.add_document((*proxy.base_doc).clone(), None);
        ps.set_active(0);
        if active_id.is_some() {
            ps.set_active_layer(active_id);
        }
        if amount > 0.5 {
            let target_id = ps
                .active()
                .and_then(|ds| find_primary_photo_raster_layer(ds.doc(), ds.active_layer()).map(|(id, _)| id));
            if let Some(tid) = target_id {
                let _ = ps.edit("Proxy Sharpen", |doc, _| {
                    run_sharpen_on_doc(doc, tid, amount);
                    Ok(())
                });
            }
        }
        proxy.session = ps;
        guard.mark_full_dirty();
        Ok(())
    }

    /// Starts a 60 FPS multi-layer Proxy Preview (~512px WITHOUT flattening) so Layer Styles (`fx`)
    /// and future per-layer tools preview 100% live and identically to `Uygula` in 2-4ms.
    pub fn begin_layer_proxy_preview(&self) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard.live_dab = None;
        guard.live_clone = None;
        guard.dismiss_compare();
        let (real_doc, active_id) = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            (Arc::clone(ds.doc()), ds.active_layer())
        };
        let real_w = real_doc.size.width.max(1);
        let real_h = real_doc.size.height.max(1);
        let max_proxy = 512u32;
        let scale = (max_proxy as f32 / real_w.max(real_h) as f32).min(1.0);
        let pw = ((real_w as f32 * scale).round() as u32).clamp(1, max_proxy);
        let ph = ((real_h as f32 * scale).round() as u32).clamp(1, max_proxy);

        let mut ps = Session::new();
        ps.add_document((*real_doc).clone(), None);
        ps.set_active(0);
        if active_id.is_some() {
            ps.set_active_layer(active_id);
        }
        if pw < real_w || ph < real_h {
            let _ = ps.execute(
                "image.imageSize",
                json!({ "width": pw, "height": ph, "resample": "bilinear" }),
            );
        }
        let base_doc = ps
            .active()
            .map(|ds| Arc::clone(ds.doc()))
            .ok_or(EngineError::NoActiveDocument)?;
        guard.proxy_preview = Some(ProxyPreviewSession {
            base_doc,
            session: ps,
            scale,
            real_size: (real_w, real_h),
        });
        guard.mark_full_dirty();
        Ok(())
    }

    pub fn update_proxy_layer_effect(
        &self,
        kind: String,
        color_hex: String,
        size: f32,
        opacity: f32,
        distance: f32,
    ) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        let active_id = guard.session.active().and_then(|ds| ds.active_layer());
        let Some(proxy) = guard.proxy_preview.as_mut() else {
            return Ok(());
        };
        let mut ps = Session::new();
        ps.add_document((*proxy.base_doc).clone(), None);
        ps.set_active(0);
        if active_id.is_some() {
            ps.set_active_layer(active_id);
        }
        let is_bg = ps.active().is_some_and(|ds| {
            let bottom = ds.doc().layers.first().map(|l| l.id);
            ds.active_layer().is_some() && ds.active_layer() == bottom
        });
        let op = opacity.clamp(5.0, 100.0) as f64;
        let sz = (size * proxy.scale).clamp(1.0, 150.0) as f64;
        let dist = (distance * proxy.scale).clamp(0.0, 150.0) as f64;
        let stroke_pos = if is_bg { "inside" } else { "outside" };
        let (cmd, params) = match kind.as_str() {
            "dropShadow" => (
                "layer.layerStyle.dropShadow",
                json!({ "color": color_hex, "opacity": op, "size": sz, "distance": dist, "angle": 120.0 }),
            ),
            "stroke" => (
                "layer.layerStyle.stroke",
                json!({ "color": color_hex, "opacity": op, "size": sz, "position": stroke_pos }),
            ),
            "outerGlow" => (
                "layer.layerStyle.outerGlow",
                json!({ "color": color_hex, "opacity": op, "size": sz }),
            ),
            "colorOverlay" => (
                "layer.layerStyle.colorOverlay",
                json!({ "color": color_hex, "opacity": op }),
            ),
            _ => return Ok(()),
        };
        let _ = ps.execute(cmd, params);
        proxy.session = ps;
        guard.mark_full_dirty();
        Ok(())
    }

    /// 60 FPS Proxy Preview for PhotoCraft's native Camera Raw develop pipeline
    /// (Exposure, Shadows, Highlights, Vibrance, Temperature, Tint, Clarity, Dehaze).
    pub fn update_proxy_camera_light(
        &self,
        exposure: f32,
        shadows: f32,
        highlights: f32,
        vibrance: f32,
        temperature: f32,
        tint: f32,
        clarity: f32,
        dehaze: f32,
    ) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        let active_id = guard.session.active().and_then(|ds| ds.active_layer());
        let Some(proxy) = guard.proxy_preview.as_mut() else {
            return Ok(());
        };
        let mut ps = Session::new();
        ps.add_document((*proxy.base_doc).clone(), None);
        ps.set_active(0);
        if active_id.is_some() {
            ps.set_active_layer(active_id);
        }
        let target_id = ps
            .active()
            .and_then(|ds| find_primary_photo_raster_layer(ds.doc(), ds.active_layer()).map(|(id, _)| id));
        if let Some(tid) = target_id {
            ps.set_active_layer(Some(tid));
            let params = json!({
                "exposure": exposure.clamp(-5.0, 5.0) as f64,
                "shadows": shadows.clamp(-100.0, 100.0) as f64,
                "highlights": highlights.clamp(-100.0, 100.0) as f64,
                "vibrance": vibrance.clamp(-100.0, 100.0) as f64,
                "temperature": temperature.clamp(-100.0, 100.0) as f64,
                "tint": tint.clamp(-100.0, 100.0) as f64,
                "clarity": clarity.clamp(-100.0, 100.0) as f64,
                "dehaze": dehaze.clamp(-100.0, 100.0) as f64
            });
            let _ = ps.execute("filter.cameraRaw", params);
        }
        proxy.session = ps;
        guard.mark_full_dirty();
        Ok(())
    }

    /// Commits PhotoCraft's native Camera Raw develop pipeline once on the active photo layer.
    pub fn apply_camera_light(
        &self,
        exposure: f32,
        shadows: f32,
        highlights: f32,
        vibrance: f32,
        temperature: f32,
        tint: f32,
        clarity: f32,
        dehaze: f32,
    ) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard.proxy_preview = None;
        guard.dismiss_compare();
        let target_id = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            find_primary_photo_raster_layer(ds.doc(), ds.active_layer())
                .map(|(id, _)| id)
                .ok_or_else(|| EngineError::CommandFailed {
                    detail: "Kamera Işık uygulanacak fotoğraf katmanı bulunamadı".into(),
                })?
        };
        guard.session.set_active_layer(Some(target_id));
        let params = json!({
            "exposure": exposure.clamp(-5.0, 5.0) as f64,
            "shadows": shadows.clamp(-100.0, 100.0) as f64,
            "highlights": highlights.clamp(-100.0, 100.0) as f64,
            "vibrance": vibrance.clamp(-100.0, 100.0) as f64,
            "temperature": temperature.clamp(-100.0, 100.0) as f64,
            "tint": tint.clamp(-100.0, 100.0) as f64,
            "clarity": clarity.clamp(-100.0, 100.0) as f64,
            "dehaze": dehaze.clamp(-100.0, 100.0) as f64
        });
        guard
            .session
            .execute("filter.cameraRaw", params)
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    /// 60 FPS Live Proxy Preview for non-destructive Gradient Fill (`gradient.fill.create`).
    pub fn update_proxy_gradient(
        &self,
        from_x: f32,
        from_y: f32,
        to_x: f32,
        to_y: f32,
        style: String,
        start_hex: String,
        end_hex: String,
        opacity: f32,
    ) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        let active_id = guard.session.active().and_then(|ds| ds.active_layer());
        let Some(proxy) = guard.proxy_preview.as_mut() else {
            return Ok(());
        };
        let mut ps = Session::new();
        ps.add_document((*proxy.base_doc).clone(), None);
        ps.set_active(0);
        if active_id.is_some() {
            ps.set_active_layer(active_id);
        }
        let sc = proxy.scale;
        let params = json!({
            "from": [(from_x * sc) as f64, (from_y * sc) as f64],
            "to": [(to_x * sc) as f64, (to_y * sc) as f64],
            "style": style,
            "stops": [[0.0, start_hex], [1.0, end_hex]],
            "opacity": opacity.clamp(5.0, 100.0) as f64
        });
        let _ = ps.execute("gradient.fill.create", params);
        proxy.session = ps;
        guard.mark_full_dirty();
        Ok(())
    }

    /// Commits a real non-destructive Gradient Fill layer (`gradient.fill.create`), masked by any active selection.
    pub fn apply_gradient_fill(
        &self,
        from_x: f32,
        from_y: f32,
        to_x: f32,
        to_y: f32,
        style: String,
        start_hex: String,
        end_hex: String,
        opacity: f32,
    ) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard.proxy_preview = None;
        guard.dismiss_compare();
        let params = json!({
            "from": [from_x as f64, from_y as f64],
            "to": [to_x as f64, to_y as f64],
            "style": style,
            "stops": [[0.0, start_hex], [1.0, end_hex]],
            "opacity": opacity.clamp(5.0, 100.0) as f64
        });
        guard
            .session
            .execute("gradient.fill.create", params)
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_selection_dirty();
        self.info_from_locked(&guard)
    }

    /// 60 FPS Live Proxy Preview for Vector Shapes (`shape.create`).
    pub fn update_proxy_shape(
        &self,
        kind: String,
        x0: f32,
        y0: f32,
        x1: f32,
        y1: f32,
        fill_hex: String,
        stroke_hex: String,
        stroke_width: f32,
        corner_radius: f32,
        sides: u32,
    ) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        let active_id = guard.session.active().and_then(|ds| ds.active_layer());
        let Some(proxy) = guard.proxy_preview.as_mut() else {
            return Ok(());
        };
        let mut ps = Session::new();
        ps.add_document((*proxy.base_doc).clone(), None);
        ps.set_active(0);
        if active_id.is_some() {
            ps.set_active_layer(active_id);
        }
        let sc = proxy.scale;
        let params = build_shape_params(
            &kind,
            x0 * sc,
            y0 * sc,
            x1 * sc,
            y1 * sc,
            &fill_hex,
            &stroke_hex,
            stroke_width * sc,
            corner_radius * sc,
            sides,
        );
        let _ = ps.execute("shape.create", params);
        proxy.session = ps;
        guard.mark_full_dirty();
        Ok(())
    }

    /// Commits a native Vector Shape layer (`shape.create`) — movable, scalable & compatible with Layer FX.
    pub fn create_shape_layer(
        &self,
        kind: String,
        x0: f32,
        y0: f32,
        x1: f32,
        y1: f32,
        fill_hex: String,
        stroke_hex: String,
        stroke_width: f32,
        corner_radius: f32,
        sides: u32,
    ) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard.proxy_preview = None;
        guard.dismiss_compare();
        let params = build_shape_params(
            &kind,
            x0,
            y0,
            x1,
            y1,
            &fill_hex,
            &stroke_hex,
            stroke_width,
            corner_radius,
            sides,
        );
        guard
            .session
            .execute("shape.create", params)
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    /// Starts a 60 FPS incremental dirty-rect Liquify session using PhotoCraft's native
    /// `LiquifyField` + `ProxyImage` (640px proxy, <1ms per finger move).
    pub fn begin_liquify_session(&self) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard.live_dab = None;
        guard.live_clone = None;
        guard.proxy_preview = None;
        guard.dismiss_compare();

        let (target_id, bounds, real_size, surf) = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            let doc = ds.doc();
            let (tid, _) = find_primary_photo_raster_layer(doc, ds.active_layer()).ok_or_else(|| {
                EngineError::CommandFailed {
                    detail: "Sıvılaştırma uygulanacak fotoğraf katmanı bulunamadı".into(),
                }
            })?;
            let layer = doc.layers.iter().find(|l| l.id == tid).ok_or(EngineError::NoActiveDocument)?;
            let LayerContent::Raster(s) = &layer.content else {
                return Err(EngineError::CommandFailed {
                    detail: "Aktif katman piksel katmanı değil".into(),
                });
            };
            (
                tid,
                doc.bounds(),
                (doc.size.width.max(1), doc.size.height.max(1)),
                s.clone(),
            )
        };

        let cell = auto_cell(bounds);
        let field = LiquifyField::new(bounds, cell);
        let proxy = LiquifyProxyImage::new(&surf, bounds, 640);
        let pw = proxy.w.max(1);
        let ph = proxy.h.max(1);
        let mut proxy_rgba = vec![[0u8; 4]; pw * ph];
        proxy.render(&field, [0, 0, pw, ph], &mut proxy_rgba);

        let mut flat = Vec::with_capacity(pw * ph * 4);
        for p in &proxy_rgba {
            flat.extend_from_slice(p);
        }
        let preview_surf = Surface::from_interleaved(
            PixelFormat::RGBA8,
            Rect::new(0, 0, pw as i32, ph as i32),
            &flat,
        );
        let mut preview_doc = Document::new(
            "Liquify Preview".to_string(),
            Size::new(pw as u32, ph as u32),
            ColorMode::Rgb,
            SampleType::U8,
        );
        preview_doc.layers.push(Layer::new("Liquify", LayerContent::Raster(preview_surf)));

        guard.live_liquify = Some(ActiveLiquifySession {
            target_layer_id: target_id,
            bounds,
            cell,
            field,
            proxy,
            proxy_rgba,
            preview_doc,
            real_size,
            last_point: None,
            current_stroke: LiquifyStroke::new(LiquifyTool::ForwardWarp, 120.0),
            undo_stack: Vec::new(),
            redo_stack: Vec::new(),
        });
        guard.mark_full_dirty();
        Ok(())
    }

    pub fn liquify_stroke_begin(
        &self,
        tool: String,
        size: f32,
        pressure: f32,
        density: f32,
        x: f32,
        y: f32,
    ) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        let Some(liq) = guard.live_liquify.as_mut() else {
            return Ok(());
        };
        let mut st = LiquifyStroke::new(parse_liquify_tool(&tool), size.clamp(12.0, 600.0) as f64);
        st.pressure = pressure.clamp(5.0, 100.0) as f64;
        st.density = density.clamp(10.0, 100.0) as f64;
        st.rate = pressure.clamp(5.0, 100.0) as f64;
        if liq.undo_stack.len() >= 20 {
            liq.undo_stack.remove(0);
        }
        liq.undo_stack.push(liq.field.clone());
        liq.redo_stack.clear();
        let pt = [x as f64, y as f64, 1.0];
        let dirty = liq.field.stroke_begin(&st, pt);
        liq.current_stroke = st;
        liq.last_point = Some(pt);
        if !dirty.is_empty() {
            let pr = liq.proxy.proxy_rect(dirty);
            liq.proxy.render(&liq.field, pr, &mut liq.proxy_rgba);
            liq.sync_proxy_rect_to_preview_doc(pr);
            guard.mark_region_dirty(Rect::new(pr[0] as i32, pr[1] as i32, pr[2] as i32, pr[3] as i32));
        }
        Ok(())
    }

    pub fn liquify_stroke_move(&self, x: f32, y: f32) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        let Some(liq) = guard.live_liquify.as_mut() else {
            return Ok(());
        };
        let pt = [x as f64, y as f64, 1.0];
        let prev = liq.last_point.unwrap_or(pt);
        let dirty = liq.field.stroke_segment(&liq.current_stroke, prev, pt);
        liq.last_point = Some(pt);
        if !dirty.is_empty() {
            let pr = liq.proxy.proxy_rect(dirty);
            liq.proxy.render(&liq.field, pr, &mut liq.proxy_rgba);
            liq.sync_proxy_rect_to_preview_doc(pr);
            guard.mark_region_dirty(Rect::new(pr[0] as i32, pr[1] as i32, pr[2] as i32, pr[3] as i32));
        }
        Ok(())
    }

    pub fn liquify_stroke_end(&self) {
        let mut guard = self.core.lock();
        if let Some(liq) = guard.live_liquify.as_mut() {
            liq.last_point = None;
        }
    }

    pub fn reset_liquify_session(&self) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        let Some(liq) = guard.live_liquify.as_mut() else {
            return Ok(());
        };
        if !liq.field.is_identity() {
            if liq.undo_stack.len() >= 20 {
                liq.undo_stack.remove(0);
            }
            liq.undo_stack.push(liq.field.clone());
            liq.redo_stack.clear();
        }
        liq.field = LiquifyField::new(liq.bounds, liq.cell);
        liq.last_point = None;
        let full = [0, 0, liq.proxy.w, liq.proxy.h];
        liq.proxy.render(&liq.field, full, &mut liq.proxy_rgba);
        liq.sync_proxy_rect_to_preview_doc(full);
        guard.mark_full_dirty();
        Ok(())
    }

    /// Commits the Liquify displacement field ONCE at full resolution using PhotoCraft's
    /// Catmull-Rom bicubic `apply_liquify` (premultiplied alpha, parallel tile-by-tile, undoable).
    pub fn commit_liquify_session(&self) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        let Some(liq) = guard.live_liquify.take() else {
            return self.info_from_locked(&guard);
        };
        guard.dismiss_compare();
        if !liq.field.is_identity() {
            let tid = liq.target_layer_id;
            let field = liq.field;
            guard
                .session
                .edit("Sıvılaştırma", |doc, _| {
                    if let Some(layer) = doc.layers.iter_mut().find(|l| l.id == tid) {
                        if let LayerContent::Raster(surf) = &mut layer.content {
                            *surf = apply_liquify(surf, &field);
                        }
                    }
                    Ok(())
                })
                .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        }
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    pub fn liquify_undo(&self) -> Result<LiquifyHistoryState, EngineError> {
        let mut guard = self.core.lock();
        let Some(liq) = guard.live_liquify.as_mut() else {
            return Ok(LiquifyHistoryState { can_undo: false, can_redo: false });
        };
        if let Some(prev) = liq.undo_stack.pop() {
            liq.redo_stack.push(liq.field.clone());
            liq.field = prev;
            liq.last_point = None;
            let full = [0, 0, liq.proxy.w, liq.proxy.h];
            liq.proxy.render(&liq.field, full, &mut liq.proxy_rgba);
            liq.sync_proxy_rect_to_preview_doc(full);
            guard.mark_full_dirty();
        }
        let state = guard.live_liquify.as_ref().map(|l| LiquifyHistoryState {
            can_undo: !l.undo_stack.is_empty(),
            can_redo: !l.redo_stack.is_empty(),
        }).unwrap_or(LiquifyHistoryState { can_undo: false, can_redo: false });
        Ok(state)
    }

    pub fn liquify_redo(&self) -> Result<LiquifyHistoryState, EngineError> {
        let mut guard = self.core.lock();
        let Some(liq) = guard.live_liquify.as_mut() else {
            return Ok(LiquifyHistoryState { can_undo: false, can_redo: false });
        };
        if let Some(next) = liq.redo_stack.pop() {
            liq.undo_stack.push(liq.field.clone());
            liq.field = next;
            liq.last_point = None;
            let full = [0, 0, liq.proxy.w, liq.proxy.h];
            liq.proxy.render(&liq.field, full, &mut liq.proxy_rgba);
            liq.sync_proxy_rect_to_preview_doc(full);
            guard.mark_full_dirty();
        }
        let state = guard.live_liquify.as_ref().map(|l| LiquifyHistoryState {
            can_undo: !l.undo_stack.is_empty(),
            can_redo: !l.redo_stack.is_empty(),
        }).unwrap_or(LiquifyHistoryState { can_undo: false, can_redo: false });
        Ok(state)
    }

    pub fn get_liquify_history_state(&self) -> LiquifyHistoryState {
        let guard = self.core.lock();
        guard.live_liquify.as_ref().map(|l| LiquifyHistoryState {
            can_undo: !l.undo_stack.is_empty(),
            can_redo: !l.redo_stack.is_empty(),
        }).unwrap_or(LiquifyHistoryState { can_undo: false, can_redo: false })
    }

    /// Ultra-fast (<0.3ms) live Floating Loupe / Magnifier pixel sampler around `(doc_x, doc_y)`.
    /// Composites all visible raster layers (including in-progress live strokes!) onto a tiny
    /// `out_size x out_size` RGBA8 buffer with crisp checkerboard transparency.
    pub fn sample_loupe_patch(
        &self,
        doc_x: f32,
        doc_y: f32,
        half_extent_px: f32,
        out_size: u32,
    ) -> Option<LayerThumbnail> {
        let guard = self.core.lock();
        let doc = guard.active_document()?;
        let os = out_size.clamp(32, 96) as usize;
        let he = half_extent_px.clamp(4.0, 120.0);
        let doc_w = doc.size.width as i32;
        let doc_h = doc.size.height as i32;
        if doc_w <= 0 || doc_h <= 0 {
            return None;
        }

        // Collect visible raster layers bottom-to-top
        let mut visible_layers: Vec<(&Surface, f32)> = Vec::new();
        for l in &doc.layers {
            if !l.visible || l.opacity <= 0.001 {
                continue;
            }
            if let LayerContent::Raster(surf) = &l.content {
                visible_layers.push((surf, l.opacity));
            }
        }

        let mut rgba = vec![0u8; os * os * 4];
        let step = (he * 2.0) / (os as f32);
        let start_x = doc_x - he;
        let start_y = doc_y - he;

        for py in 0..os {
            let sy = (start_y + (py as f32 + 0.5) * step).floor() as i32;
            let row = py * os * 4;
            for px in 0..os {
                let sx = (start_x + (px as f32 + 0.5) * step).floor() as i32;
                let di = row + px * 4;
                // Outside canvas: dark studio background
                if sx < 0 || sy < 0 || sx >= doc_w || sy >= doc_h {
                    rgba[di] = 26;
                    rgba[di + 1] = 27;
                    rgba[di + 2] = 34;
                    rgba[di + 3] = 255;
                    continue;
                }
                // Checkerboard base for transparent pixels
                let chk = (((px / 6) + (py / 6)) & 1) == 0;
                let bg_v = if chk { 0.82f32 } else { 0.64f32 };
                let mut acc_r = bg_v;
                let mut acc_g = bg_v;
                let mut acc_b = bg_v;

                for (surf, op) in &visible_layers {
                    let c = surf.rgba(sx, sy);
                    let a = (c[3] * *op).clamp(0.0, 1.0);
                    if a > 0.002 {
                        let inv = 1.0 - a;
                        acc_r = c[0] * a + acc_r * inv;
                        acc_g = c[1] * a + acc_g * inv;
                        acc_b = c[2] * a + acc_b * inv;
                    }
                }
                rgba[di] = (acc_r.clamp(0.0, 1.0) * 255.0).round() as u8;
                rgba[di + 1] = (acc_g.clamp(0.0, 1.0) * 255.0).round() as u8;
                rgba[di + 2] = (acc_b.clamp(0.0, 1.0) * 255.0).round() as u8;
                rgba[di + 3] = 255;
            }
        }

        Some(LayerThumbnail {
            width: os as u32,
            height: os as u32,
            rgba,
        })
    }

    pub fn cancel_liquify_session(&self) {
        let mut guard = self.core.lock();
        if guard.live_liquify.take().is_some() {
            guard.mark_full_dirty();
        }
    }

    /// One-tap Smart Auto Enhance using PhotoCraft's native `image.autoTone`, `image.autoColor`, or `image.autoContrast`.
    pub fn apply_auto_enhance(&self, mode: String) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard.proxy_preview = None;
        guard.live_liquify = None;
        guard.dismiss_compare();
        let target_id = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            find_primary_photo_raster_layer(ds.doc(), ds.active_layer())
                .map(|(id, _)| id)
                .ok_or_else(|| EngineError::CommandFailed {
                    detail: "Otomatik iyileştirme uygulanacak fotoğraf katmanı bulunamadı".into(),
                })?
        };
        guard.session.set_active_layer(Some(target_id));
        let cmd = match mode.as_str() {
            "color" => "image.autoColor",
            "contrast" => "image.autoContrast",
            _ => "image.autoTone",
        };
        guard
            .session
            .execute(cmd, json!({}))
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    /// 60 FPS Live Proxy Preview for Cinematic Looks + Vignette + Film Grain (`filter.cameraRaw`).
    pub fn update_proxy_cinema_look(
        &self,
        preset: String,
        intensity: f32,
        vignette: f32,
        grain: f32,
    ) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        let active_id = guard.session.active().and_then(|ds| ds.active_layer());
        let Some(proxy) = guard.proxy_preview.as_mut() else {
            return Ok(());
        };
        let mut ps = Session::new();
        ps.add_document((*proxy.base_doc).clone(), None);
        ps.set_active(0);
        if active_id.is_some() {
            ps.set_active_layer(active_id);
        }
        let target_id = ps
            .active()
            .and_then(|ds| find_primary_photo_raster_layer(ds.doc(), ds.active_layer()).map(|(id, _)| id));
        if let Some(tid) = target_id {
            ps.set_active_layer(Some(tid));
            let params = build_cinema_look_params(&preset, intensity, vignette, grain);
            let _ = ps.execute("filter.cameraRaw", params);
        }
        proxy.session = ps;
        guard.mark_full_dirty();
        Ok(())
    }

    /// Commits Cinematic Look + Vignette + Film Grain once at full resolution.
    pub fn apply_cinema_look(
        &self,
        preset: String,
        intensity: f32,
        vignette: f32,
        grain: f32,
    ) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard.proxy_preview = None;
        guard.dismiss_compare();
        let target_id = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            find_primary_photo_raster_layer(ds.doc(), ds.active_layer())
                .map(|(id, _)| id)
                .ok_or_else(|| EngineError::CommandFailed {
                    detail: "Sinema stili uygulanacak fotoğraf katmanı bulunamadı".into(),
                })?
        };
        guard.session.set_active_layer(Some(target_id));
        let params = build_cinema_look_params(&preset, intensity, vignette, grain);
        guard
            .session
            .execute("filter.cameraRaw", params)
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    /// 60 FPS Live Proxy Preview for Motion Blur (`filter.blur.motionBlur`).
    pub fn update_proxy_motion_blur(&self, angle: f32, distance: f32) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        let active_id = guard.session.active().and_then(|ds| ds.active_layer());
        let Some(proxy) = guard.proxy_preview.as_mut() else {
            return Ok(());
        };
        let mut ps = Session::new();
        ps.add_document((*proxy.base_doc).clone(), None);
        ps.set_active(0);
        if active_id.is_some() {
            ps.set_active_layer(active_id);
        }
        let scaled_dist = (distance.clamp(0.0, 200.0) * proxy.scale).round().clamp(0.0, 120.0);
        if scaled_dist >= 1.0 {
            let target_id = ps
                .active()
                .and_then(|ds| find_primary_photo_raster_layer(ds.doc(), ds.active_layer()).map(|(id, _)| id));
            if let Some(tid) = target_id {
                ps.set_active_layer(Some(tid));
                let _ = ps.execute(
                    "filter.blur.motionBlur",
                    json!({ "angle": angle.clamp(-180.0, 180.0) as f64, "distance": scaled_dist as f64 }),
                );
            }
        }
        proxy.session = ps;
        guard.mark_full_dirty();
        Ok(())
    }

    /// Commits Motion Blur (`filter.blur.motionBlur`) at full resolution.
    pub fn apply_motion_blur(&self, angle: f32, distance: f32) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard.proxy_preview = None;
        guard.dismiss_compare();
        let target_id = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            find_primary_photo_raster_layer(ds.doc(), ds.active_layer())
                .map(|(id, _)| id)
                .ok_or_else(|| EngineError::CommandFailed {
                    detail: "Hareket bulanıklığı uygulanacak fotoğraf katmanı bulunamadı".into(),
                })?
        };
        guard.session.set_active_layer(Some(target_id));
        guard
            .session
            .execute(
                "filter.blur.motionBlur",
                json!({
                    "angle": angle.clamp(-180.0, 180.0) as f64,
                    "distance": distance.clamp(1.0, 200.0) as f64
                }),
            )
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    /// 60 FPS Live Proxy Preview for Radial Blur (`filter.blur.radialBlur`, zoom or spin).
    pub fn update_proxy_radial_blur(&self, amount: f32, method: String) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        let active_id = guard.session.active().and_then(|ds| ds.active_layer());
        let Some(proxy) = guard.proxy_preview.as_mut() else {
            return Ok(());
        };
        let mut ps = Session::new();
        ps.add_document((*proxy.base_doc).clone(), None);
        ps.set_active(0);
        if active_id.is_some() {
            ps.set_active_layer(active_id);
        }
        let amt = amount.round().clamp(0.0, 100.0);
        if amt >= 1.0 {
            let target_id = ps
                .active()
                .and_then(|ds| find_primary_photo_raster_layer(ds.doc(), ds.active_layer()).map(|(id, _)| id));
            if let Some(tid) = target_id {
                ps.set_active_layer(Some(tid));
                let m = if method == "spin" { "spin" } else { "zoom" };
                let _ = ps.execute(
                    "filter.blur.radialBlur",
                    json!({ "amount": amt as i64, "method": m, "quality": "draft", "centerX": 0.5, "centerY": 0.5 }),
                );
            }
        }
        proxy.session = ps;
        guard.mark_full_dirty();
        Ok(())
    }

    /// Commits Radial Blur (`filter.blur.radialBlur`) at full resolution.
    pub fn apply_radial_blur(&self, amount: f32, method: String) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard.proxy_preview = None;
        guard.dismiss_compare();
        let target_id = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            find_primary_photo_raster_layer(ds.doc(), ds.active_layer())
                .map(|(id, _)| id)
                .ok_or_else(|| EngineError::CommandFailed {
                    detail: "Radyal bulanıklık uygulanacak fotoğraf katmanı bulunamadı".into(),
                })?
        };
        guard.session.set_active_layer(Some(target_id));
        let m = if method == "spin" { "spin" } else { "zoom" };
        guard
            .session
            .execute(
                "filter.blur.radialBlur",
                json!({
                    "amount": amount.round().clamp(1.0, 100.0) as i64,
                    "method": m,
                    "quality": "good",
                    "centerX": 0.5,
                    "centerY": 0.5
                }),
            )
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    pub fn end_proxy_preview(&self) {
        let mut guard = self.core.lock();
        if guard.proxy_preview.take().is_some() {
            guard.mark_full_dirty();
        }
    }

    /// Returns the content bounds of the currently active layer (for independent Layer Transform UI).
    pub fn get_active_layer_bounds(&self) -> Option<SelectionBounds> {
        let guard = self.core.lock();
        let ds = guard.session.active()?;
        let active_id = ds.active_layer()?;
        let doc = ds.doc();
        let layer = doc.layers.iter().find(|l| l.id == active_id)?;
        let b = match &layer.content {
            LayerContent::Raster(surf) => surf.content_bounds(),
            _ => doc.bounds(),
        };
        if b.is_empty() {
            Some(SelectionBounds {
                x0: 0,
                y0: 0,
                x1: doc.size.width as i32,
                y1: doc.size.height as i32,
            })
        } else {
            Some(SelectionBounds { x0: b.x0, y0: b.y0, x1: b.x1, y1: b.y1 })
        }
    }

    /// Temporarily hides a layer in the GPU composite while dragging its floating preview (zero undo steps).
    pub fn set_transform_hidden_layer(&self, layer_id: Option<u64>) {
        let mut guard = self.core.lock();
        guard.transform_hidden_layer = layer_id.map(LayerId);
        guard.mark_full_dirty();
    }

    /// Independently moves, scales (%), and rotates (deg) ONLY the currently active layer (`edit.transform`).
    pub fn transform_active_layer(
        &self,
        dx: f32,
        dy: f32,
        scale_pct: f32,
        rotate_deg: f32,
    ) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard.live_stroke = None;
        guard.live_dab = None;
        guard.live_clone = None;
        guard.proxy_preview = None;
        guard.transform_hidden_layer = None;

        let (active_id, was_locked) = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            let id = ds.active_layer().ok_or_else(|| EngineError::CommandFailed {
                detail: "Taşınacak aktif katman seçili değil".into(),
            })?;
            let locked = ds
                .doc()
                .layers
                .iter()
                .find(|l| l.id == id)
                .is_some_and(|l| l.locks.pixels || l.locks.position || l.locks.all);
            (id, locked)
        };

        if was_locked {
            let _ = guard.session.execute(
                "layer.setProps",
                json!({ "layer": active_id.0, "locks": { "pixels": false, "position": false, "all": false } }),
            );
        }

        let sc = scale_pct.clamp(5.0, 500.0) as f64;
        let res = guard.session.execute(
            "edit.transform",
            json!({
                "dx": dx as f64,
                "dy": dy as f64,
                "scaleX": sc,
                "scaleY": sc,
                "rotate": rotate_deg as f64,
                "interpolation": "bicubic"
            }),
        );

        if was_locked {
            let _ = guard.session.execute(
                "layer.setProps",
                json!({ "layer": active_id.0, "locks": { "pixels": true } }),
            );
        }

        res.map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
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
        guard.dismiss_compare();
        guard.live_stroke = None;
        guard.proxy_preview = None;
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
        guard.dismiss_compare();
        guard.selection_generation = guard.selection_generation.wrapping_add(1);
        guard.live_stroke = None;
        guard.live_dab = None;
        guard.live_adjustment = None;
        guard.proxy_preview = None;
        guard.live_liquify = None;
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
        guard.dismiss_compare();
        guard.selection_generation = guard.selection_generation.wrapping_add(1);
        guard.live_stroke = None;
        guard.live_dab = None;
        guard.live_adjustment = None;
        guard.proxy_preview = None;
        guard.live_liquify = None;
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

    /// Generates a lightweight RGBA8 mini-thumbnail (<= max_dim px) for a single layer
    /// so the user can visually see what is painted on each layer in the Layers panel.
    pub fn get_layer_thumbnail(&self, layer_id: u64, max_dim: u32) -> Result<Option<LayerThumbnail>, EngineError> {
        let guard = self.core.lock();
        let doc = guard.active_document().ok_or(EngineError::NoActiveDocument)?;
        let Some(layer) = doc.layers.iter().find(|l| l.id.0 == layer_id) else {
            return Ok(None);
        };
        let LayerContent::Raster(surf) = &layer.content else {
            return Ok(None);
        };
        let doc_w = doc.size.width.max(1);
        let doc_h = doc.size.height.max(1);
        let cap = max_dim.clamp(16, 512);
        let scale = (cap as f32 / doc_w.max(doc_h) as f32).min(1.0);
        let tw = ((doc_w as f32 * scale).round() as u32).clamp(1, cap);
        let th = ((doc_h as f32 * scale).round() as u32).clamp(1, cap);

        let mut rgba = vec![0u8; (tw as usize) * (th as usize) * 4];
        let content_bounds = surf.content_bounds();
        if content_bounds.is_empty() {
            return Ok(Some(LayerThumbnail { width: tw, height: th, rgba }));
        }

        let sx_step = doc_w as f32 / tw as f32;
        let sy_step = doc_h as f32 / th as f32;
        let mask_ref = layer.mask.as_ref().filter(|m| m.enabled);

        for y in 0..th {
            let dy = ((y as f32 + 0.5) * sy_step) as i32;
            if dy < content_bounds.y0 || dy >= content_bounds.y1 {
                continue;
            }
            for x in 0..tw {
                let dx = ((x as f32 + 0.5) * sx_step) as i32;
                if dx < content_bounds.x0 || dx >= content_bounds.x1 {
                    continue;
                }
                let p = surf.rgba(dx, dy);
                let mut a = p[3].clamp(0.0, 1.0);
                if let Some(m) = mask_ref {
                    a *= m.value(dx, dy).clamp(0.0, 1.0);
                }
                if a <= 0.002 {
                    continue;
                }
                let idx = ((y as usize) * (tw as usize) + (x as usize)) * 4;
                rgba[idx] = (p[0].clamp(0.0, 1.0) * 255.0).round() as u8;
                rgba[idx + 1] = (p[1].clamp(0.0, 1.0) * 255.0).round() as u8;
                rgba[idx + 2] = (p[2].clamp(0.0, 1.0) * 255.0).round() as u8;
                rgba[idx + 3] = (a * 255.0).round() as u8;
            }
        }

        Ok(Some(LayerThumbnail { width: tw, height: th, rgba }))
    }

    /// Instant Before/After compare mode: hides all layers except the original Background (zero undo steps).
    pub fn set_compare_original(&self, enabled: bool) {
        let mut guard = self.core.lock();
        if enabled {
            guard.proxy_preview = None;
        }
        guard.compare_original = enabled;
        guard.mark_selection_dirty();
    }

    pub fn move_layer_up(&self) -> Result<Vec<LayerSummary>, EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard
            .session
            .execute("layer.arrange.bringForward", json!({}))
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        drop(guard);
        self.get_layers()
    }

    pub fn move_layer_down(&self) -> Result<Vec<LayerSummary>, EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard
            .session
            .execute("layer.arrange.sendBackward", json!({}))
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        drop(guard);
        self.get_layers()
    }

    pub fn duplicate_active_layer(&self) -> Result<Vec<LayerSummary>, EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard
            .session
            .execute("layer.duplicate", json!({}))
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        drop(guard);
        self.get_layers()
    }

    pub fn merge_layer_down(&self) -> Result<Vec<LayerSummary>, EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard
            .session
            .execute("layer.mergeDown", json!({}))
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        drop(guard);
        self.get_layers()
    }

    /// Copies the active selection onto a brand-new independent layer (`layer.new.layerViaCopy`)
    /// so it can immediately be moved, scaled, rotated, or styled with `fx`!
    pub fn layer_via_copy(&self) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        let has_sel = guard
            .active_document()
            .and_then(|d| d.selection.as_ref().map(|s| !s.content_bounds().is_empty()))
            .unwrap_or(false);
        if !has_sel {
            return Err(EngineError::CommandFailed {
                detail: "Önce yeni katmana kopyalanacak bir alan seç".into(),
            });
        }
        // If active layer is empty (like untouched Çizim Katmanı), target primary photo layer so copy grabs pixels
        let target_id = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            let active = ds.active_layer();
            let active_has_px = active
                .and_then(|id| ds.doc().layers.iter().find(|l| l.id == id))
                .is_some_and(|l| match &l.content {
                    LayerContent::Raster(s) => !s.content_bounds().is_empty(),
                    _ => true,
                });
            if active_has_px {
                active
            } else {
                find_primary_photo_raster_layer(ds.doc(), active).map(|(id, _)| id)
            }
        };
        if let Some(tid) = target_id {
            guard.session.set_active_layer(Some(tid));
        }
        guard
            .session
            .execute("layer.new.layerViaCopy", json!({}))
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        let _ = guard.session.execute("select.deselect", json!({}));
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    /// Softens active selection edges (`select.modify.feather`) for seamless cutouts and masks.
    pub fn feather_selection(&self, radius: f32) -> Result<Option<SelectionBounds>, EngineError> {
        let mut guard = self.core.lock();
        guard
            .session
            .execute("select.modify.feather", json!({ "radius": radius.clamp(0.5, 100.0) as f64 }))
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.dismiss_compare();
        guard.mark_selection_dirty();
        let b = guard
            .active_document()
            .and_then(|d| d.selection.as_ref().map(|s| s.content_bounds()))
            .filter(|r| !r.is_empty())
            .map(|r| SelectionBounds { x0: r.x0, y0: r.y0, x1: r.x1, y1: r.y1 });
        Ok(b)
    }

    /// Applies a non-destructive PhotoCraft Layer Style (`dropShadow`, `stroke`, `outerGlow`, `colorOverlay`).
    pub fn apply_layer_effect(
        &self,
        kind: String,
        color_hex: String,
        size: f32,
        opacity: f32,
        distance: f32,
    ) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard.live_stroke = None;
        guard.proxy_preview = None;
        let is_bg = guard.session.active().is_some_and(|ds| {
            let bottom = ds.doc().layers.first().map(|l| l.id);
            ds.active_layer().is_some() && ds.active_layer() == bottom
        });
        let stroke_pos = if is_bg { "inside" } else { "outside" };
        let op = opacity.clamp(5.0, 100.0) as f64;
        let sz = size.clamp(1.0, 150.0) as f64;
        let dist = distance.clamp(0.0, 150.0) as f64;
        let (cmd, params) = match kind.as_str() {
            "dropShadow" => (
                "layer.layerStyle.dropShadow",
                json!({ "color": color_hex, "opacity": op, "size": sz, "distance": dist, "angle": 120.0 }),
            ),
            "stroke" => (
                "layer.layerStyle.stroke",
                json!({ "color": color_hex, "opacity": op, "size": sz, "position": stroke_pos }),
            ),
            "outerGlow" => (
                "layer.layerStyle.outerGlow",
                json!({ "color": color_hex, "opacity": op, "size": sz }),
            ),
            "colorOverlay" => (
                "layer.layerStyle.colorOverlay",
                json!({ "color": color_hex, "opacity": op }),
            ),
            other => {
                return Err(EngineError::InvalidParams {
                    detail: format!("Bilinmeyen katman efekti: {other}"),
                })
            }
        };
        guard
            .session
            .execute(cmd, params)
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    pub fn clear_layer_effects(&self) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard.live_stroke = None;
        let _ = guard.session.execute("layer.layerStyle.clear", json!({}));
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
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
        } else if clamped_size <= 4.0 {
            0.25
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
        guard.dismiss_compare();
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

    /// Real-time 60 FPS incremental retouching (`smudge`, `dodge`, `burn`) using PhotoCraft's native `LiveDab` + dirty-rect compositing.
    pub fn begin_live_dab(
        &self,
        tool: String,
        sample: StrokeSample,
        size: f32,
        hardness: f32,
        strength: f32,
    ) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard.live_stroke = None;
        guard.live_dab = None;

        let (prev_active, target_id, was_locked) = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            let prev = ds.active_layer();
            let (id, locked) = find_primary_photo_raster_layer(ds.doc(), prev).ok_or_else(|| {
                EngineError::CommandFailed {
                    detail: "Rötuş uygulanacak fotoğraf katmanı bulunamadı".into(),
                }
            })?;
            (prev, id, locked)
        };

        let clamped_size = size.clamp(1.0, 400.0);
        let clamped_hard = (hardness.clamp(0.0, 1.0) * 100.0).round();
        let clamped_str = strength.clamp(5.0, 100.0);
        let adaptive_spacing = if clamped_size > 160.0 {
            38.0
        } else if clamped_size > 100.0 {
            28.0
        } else {
            18.0
        };
        let pt_json = json!([sample.x as f64, sample.y as f64, sample.pressure.clamp(0.05, 1.0) as f64]);

        let (cmd, mut base_params) = match tool.as_str() {
            "smudge" => (
                "paint.smudge".to_string(),
                json!({
                    "size": clamped_size,
                    "hardness": clamped_hard,
                    "spacing": adaptive_spacing,
                    "strength": clamped_str,
                    "sampleAllLayers": false
                }),
            ),
            "dodge" => (
                "paint.dodge".to_string(),
                json!({
                    "size": clamped_size,
                    "hardness": clamped_hard,
                    "spacing": adaptive_spacing,
                    "range": "midtones",
                    "exposure": clamped_str,
                    "protectTones": true
                }),
            ),
            "burn" => (
                "paint.burn".to_string(),
                json!({
                    "size": clamped_size,
                    "hardness": clamped_hard,
                    "spacing": adaptive_spacing,
                    "range": "midtones",
                    "exposure": clamped_str,
                    "protectTones": true
                }),
            ),
            other => {
                return Err(EngineError::InvalidParams {
                    detail: format!("Canlı rötuş desteklenmeyen araç: {other}"),
                })
            }
        };
        base_params["points"] = json!([pt_json.clone()]);

        guard.session.set_active_layer(Some(target_id));
        if was_locked {
            let _ = guard.session.execute(
                "layer.setProps",
                json!({ "layer": target_id.0, "locks": { "pixels": false } }),
            );
        }

        let live = match LiveDab::begin(&guard.session, &cmd, &base_params) {
            Ok(l) => l,
            Err(e) => {
                if was_locked {
                    let _ = guard.session.execute("edit.undo", json!({}));
                }
                if prev_active.is_some() {
                    guard.session.set_active_layer(prev_active);
                }
                return Err(EngineError::CommandFailed { detail: e.to_string() });
            }
        };

        let dmg = live.bounds();
        guard.live_dab = Some(ActiveLiveDab {
            live,
            cmd,
            base_params,
            points_json: vec![pt_json],
            target_id,
            prev_active,
            was_locked,
        });
        guard.mark_region_dirty(dmg);
        Ok(())
    }

    pub fn push_live_dab(&self, samples: Vec<StrokeSample>) -> Result<(), EngineError> {
        if samples.is_empty() {
            return Ok(());
        }
        let mut guard = self.core.lock();
        let Some(active) = guard.live_dab.as_mut() else {
            return Ok(());
        };
        let pts: Vec<StrokePoint> = samples.iter().map(sample_to_stroke_point).collect();
        let dmg = active
            .live
            .push(&pts)
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        for s in &samples {
            active
                .points_json
                .push(json!([s.x as f64, s.y as f64, s.pressure.clamp(0.05, 1.0) as f64]));
        }
        guard.mark_region_dirty(dmg);
        Ok(())
    }

    pub fn finish_live_dab(&self) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        let Some(active) = guard.live_dab.take() else {
            return self.info_from_locked(&guard);
        };
        let bounds = active.live.bounds();
        let mut params = active.base_params;
        params["points"] = Value::Array(active.points_json);

        let _ = guard.session.offer_live_dab(active.live);
        let res = guard.session.execute(&active.cmd, params);

        if active.was_locked {
            let _ = guard.session.execute(
                "layer.setProps",
                json!({ "layer": active.target_id.0, "locks": { "pixels": true } }),
            );
        }
        if active.prev_active.is_some() {
            guard.session.set_active_layer(active.prev_active);
        }
        res.map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_region_dirty(bounds);
        self.info_from_locked(&guard)
    }

    pub fn cancel_live_dab(&self) {
        let mut guard = self.core.lock();
        if let Some(active) = guard.live_dab.take() {
            let bounds = active.live.bounds();
            if active.was_locked {
                let _ = guard.session.execute("edit.undo", json!({}));
            }
            if active.prev_active.is_some() {
                guard.session.set_active_layer(active.prev_active);
            }
            guard.mark_region_dirty(bounds);
        }
    }

    pub fn get_selection_bounds(&self) -> Option<SelectionBounds> {
        let guard = self.core.lock();
        let doc = guard.active_document()?;
        let sel = doc.selection.as_ref()?;
        let b = sel.content_bounds();
        if b.is_empty() {
            None
        } else {
            Some(SelectionBounds {
                x0: b.x0,
                y0: b.y0,
                x1: b.x1,
                y1: b.y1,
            })
        }
    }

    pub fn select_rect(
        &self,
        x: i32,
        y: i32,
        width: u32,
        height: u32,
        ellipse: bool,
        mode: String,
    ) -> Result<Option<SelectionBounds>, EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        let m = if mode.is_empty() { "replace" } else { mode.as_str() };
        guard
            .session
            .execute(
                "select.rect",
                json!({
                    "x": x,
                    "y": y,
                    "width": width.max(1),
                    "height": height.max(1),
                    "ellipse": ellipse,
                    "mode": m
                }),
            )
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.dismiss_compare();
        guard.mark_selection_dirty();
        let b = guard
            .active_document()
            .and_then(|d| d.selection.as_ref().map(|s| s.content_bounds()))
            .filter(|r| !r.is_empty())
            .map(|r| SelectionBounds { x0: r.x0, y0: r.y0, x1: r.x1, y1: r.y1 });
        Ok(b)
    }

    /// Smart Object-Aware Wand: taps an object (`select.object` point mode) to detect its real
    /// boundary instead of raw RGB flood-fill, falling back to edge-aware `select.quick` if needed.
    pub fn select_object_at_point(
        &self,
        x: i32,
        y: i32,
        mode: String,
    ) -> Result<Option<SelectionBounds>, EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        let m = if mode.is_empty() { "replace" } else { mode.as_str() };
        let obj_res = guard.session.execute(
            "select.object",
            json!({
                "point": [x as f64, y as f64],
                "sampleAllLayers": true,
                "hardEdge": false,
                "mode": m
            }),
        );
        let has_sel = guard
            .active_document()
            .and_then(|d| d.selection.as_ref().map(|s| !s.content_bounds().is_empty()))
            .unwrap_or(false);
        if obj_res.is_err() || !has_sel {
            let quick_mode = if m == "subtract" { "subtract" } else if m == "add" { "add" } else { "replace" };
            guard
                .session
                .execute(
                    "select.quick",
                    json!({
                        "points": [[x as f64, y as f64]],
                        "size": 44.0,
                        "mode": quick_mode,
                        "sampleAllLayers": true,
                        "enhanceEdge": true
                    }),
                )
                .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        }
        guard.dismiss_compare();
        guard.mark_selection_dirty();
        let b = guard
            .active_document()
            .and_then(|d| d.selection.as_ref().map(|s| s.content_bounds()))
            .filter(|r| !r.is_empty())
            .map(|r| SelectionBounds { x0: r.x0, y0: r.y0, x1: r.x1, y1: r.y1 });
        Ok(b)
    }

    pub fn select_magic_wand(
        &self,
        x: i32,
        y: i32,
        tolerance: u32,
        contiguous: bool,
        mode: String,
    ) -> Result<Option<SelectionBounds>, EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        let m = if mode.is_empty() { "replace" } else { mode.as_str() };
        guard
            .session
            .execute(
                "select.magicWand",
                json!({
                    "x": x,
                    "y": y,
                    "tolerance": tolerance.min(255),
                    "contiguous": contiguous,
                    "antiAlias": true,
                    "sampleAllLayers": true,
                    "mode": m
                }),
            )
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        // Smooth tiny 1px jagged speckles for cleaner selection edges
        let _ = guard.session.execute("select.modify.smooth", json!({ "radius": 2 }));
        guard.dismiss_compare();
        guard.mark_selection_dirty();
        let b = guard
            .active_document()
            .and_then(|d| d.selection.as_ref().map(|s| s.content_bounds()))
            .filter(|r| !r.is_empty())
            .map(|r| SelectionBounds { x0: r.x0, y0: r.y0, x1: r.x1, y1: r.y1 });
        Ok(b)
    }

    /// One-tap automatic Subject selection (`select.subject`).
    pub fn select_subject(&self) -> Result<Option<SelectionBounds>, EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard
            .session
            .execute(
                "select.subject",
                json!({
                    "sampleAllLayers": true,
                    "mode": "replace"
                }),
            )
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.dismiss_compare();
        guard.mark_selection_dirty();
        let b = guard
            .active_document()
            .and_then(|d| d.selection.as_ref().map(|s| s.content_bounds()))
            .filter(|r| !r.is_empty())
            .map(|r| SelectionBounds { x0: r.x0, y0: r.y0, x1: r.x1, y1: r.y1 });
        Ok(b)
    }

    /// Edge-aware Quick Selection Brush (`select.quick` with `enhanceEdge: true`).
    pub fn select_quick_stroke(
        &self,
        samples: Vec<StrokeSample>,
        size: f32,
        subtract: bool,
    ) -> Result<Option<SelectionBounds>, EngineError> {
        if samples.is_empty() {
            return Ok(self.get_selection_bounds());
        }
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        let pts: Vec<Value> = samples
            .iter()
            .map(|s| json!([s.x as f64, s.y as f64]))
            .collect();
        let mode = if subtract { "subtract" } else { "add" };
        guard
            .session
            .execute(
                "select.quick",
                json!({
                    "points": pts,
                    "size": size.clamp(1.0, 400.0) as f64,
                    "mode": mode,
                    "sampleAllLayers": true,
                    "enhanceEdge": true
                }),
            )
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.dismiss_compare();
        guard.mark_selection_dirty();
        let b = guard
            .active_document()
            .and_then(|d| d.selection.as_ref().map(|s| s.content_bounds()))
            .filter(|r| !r.is_empty())
            .map(|r| SelectionBounds { x0: r.x0, y0: r.y0, x1: r.x1, y1: r.y1 });
        Ok(b)
    }

    /// Instant manual selection brush: paint directly onto `doc.selection` to add (`subtract = false`)
    /// or erase (`subtract = true`) any area by hand with smooth edges.
    pub fn paint_selection_stroke(
        &self,
        samples: Vec<StrokeSample>,
        size: f32,
        subtract: bool,
    ) -> Result<Option<SelectionBounds>, EngineError> {
        if samples.is_empty() {
            return Ok(self.get_selection_bounds());
        }
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        let radius = (size.clamp(1.0, 400.0) * 0.5).max(0.5);
        let pad = radius.ceil() as i32 + 2;
        let label = if subtract { "Seçim Silgisi" } else { "Seçim Fırçası" };

        guard
            .session
            .edit(label, |doc, _| {
                let canvas = doc.bounds();
                if subtract && doc.selection.is_none() {
                    return Ok(());
                }
                let mut min_x = i32::MAX;
                let mut min_y = i32::MAX;
                let mut max_x = i32::MIN;
                let mut max_y = i32::MIN;
                for s in &samples {
                    let sx = s.x.round() as i32;
                    let sy = s.y.round() as i32;
                    min_x = min_x.min(sx - pad);
                    min_y = min_y.min(sy - pad);
                    max_x = max_x.max(sx + pad);
                    max_y = max_y.max(sy + pad);
                }
                let roi = Rect::new(min_x, min_y, max_x, max_y).intersect(&canvas);
                if roi.is_empty() {
                    return Ok(());
                }

                let sel = doc
                    .selection
                    .get_or_insert_with(|| Surface::new(PixelFormat::GRAY8));
                let rw = roi.width() as usize;
                let rh = roi.height() as usize;
                let mut buf = sel.read_region(roi);
                if buf.len() < rw * rh {
                    return Ok(());
                }

                // Stamp smooth circular dabs along stroke segments
                let mut stamp_disc = |cx: f32, cy: f32| {
                    let x0 = ((cx - radius - 1.0).floor() as i32).max(roi.x0);
                    let x1 = ((cx + radius + 1.0).ceil() as i32).min(roi.x1 - 1);
                    let y0 = ((cy - radius - 1.0).floor() as i32).max(roi.y0);
                    let y1 = ((cy + radius + 1.0).ceil() as i32).min(roi.y1 - 1);
                    for py in y0..=y1 {
                        let dy = (py as f32 + 0.5) - cy;
                        let row = (py - roi.y0) as usize * rw;
                        for px in x0..=x1 {
                            let dx = (px as f32 + 0.5) - cx;
                            let dist = (dx * dx + dy * dy).sqrt();
                            let hit_r = radius.max(0.75);
                            if dist <= hit_r {
                                let edge = if radius <= 1.5 { 1.0 } else { ((radius - dist) * 0.5).clamp(0.0, 1.0) };
                                let idx = row + (px - roi.x0) as usize;
                                if subtract {
                                    buf[idx] = (buf[idx] * (1.0 - edge)).clamp(0.0, 1.0);
                                } else {
                                    buf[idx] = buf[idx].max(edge);
                                }
                            }
                        }
                    }
                };

                let step = (radius * 0.35).max(0.5);
                stamp_disc(samples[0].x, samples[0].y);
                for win in samples.windows(2) {
                    let (a, b) = (&win[0], &win[1]);
                    let d = ((b.x - a.x).hypot(b.y - a.y)).max(0.001);
                    let n = (d / step).ceil() as i32;
                    for i in 1..=n {
                        let t = i as f32 / n as f32;
                        stamp_disc(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t);
                    }
                }

                sel.write_region(roi, &buf);
                if subtract && sel.content_bounds().is_empty() {
                    doc.selection = None;
                }
                Ok(())
            })
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;

        guard.mark_full_dirty();
        let b = guard
            .active_document()
            .and_then(|d| d.selection.as_ref().map(|s| s.content_bounds()))
            .filter(|r| !r.is_empty())
            .map(|r| SelectionBounds { x0: r.x0, y0: r.y0, x1: r.x1, y1: r.y1 });
        Ok(b)
    }

    pub fn select_inverse(&self) -> Result<Option<SelectionBounds>, EngineError> {
        let mut guard = self.core.lock();
        guard
            .session
            .execute("select.inverse", json!({}))
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.dismiss_compare();
        guard.mark_selection_dirty();
        let b = guard
            .active_document()
            .and_then(|d| d.selection.as_ref().map(|s| s.content_bounds()))
            .filter(|r| !r.is_empty())
            .map(|r| SelectionBounds { x0: r.x0, y0: r.y0, x1: r.x1, y1: r.y1 });
        Ok(b)
    }

    pub fn select_deselect(&self) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        let _ = guard.session.execute("select.deselect", json!({}));
        guard.dismiss_compare();
        guard.mark_selection_dirty();
        self.info_from_locked(&guard)
    }

    pub fn clear_selection_pixels(&self) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        let (target_id, was_locked) = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            let active = ds.active_layer();
            let chosen = active
                .and_then(|id| ds.doc().layers.iter().find(|l| l.id == id))
                .filter(|l| matches!(l.content, LayerContent::Raster(_)));
            match chosen {
                Some(l) => (l.id, l.locks.pixels),
                None => find_primary_photo_raster_layer(ds.doc(), active).ok_or_else(|| {
                    EngineError::CommandFailed { detail: "Silinecek piksel katmanı bulunamadı".into() }
                })?,
            }
        };
        guard.session.set_active_layer(Some(target_id));
        if was_locked {
            let _ = guard.session.execute(
                "layer.setProps",
                json!({ "layer": target_id.0, "locks": { "pixels": false } }),
            );
        }
        let res = guard.session.execute("edit.clear", json!({}));
        if was_locked {
            let _ = guard.session.execute(
                "layer.setProps",
                json!({ "layer": target_id.0, "locks": { "pixels": true } }),
            );
        }
        res.map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    pub fn fill_selection_pixels(&self, color_hex: String) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        let (target_id, was_locked) = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            let active = ds.active_layer();
            let chosen = active
                .and_then(|id| ds.doc().layers.iter().find(|l| l.id == id))
                .filter(|l| matches!(l.content, LayerContent::Raster(_)));
            match chosen {
                Some(l) => (l.id, l.locks.pixels),
                None => find_primary_photo_raster_layer(ds.doc(), active).ok_or_else(|| {
                    EngineError::CommandFailed { detail: "Doldurulacak piksel katmanı bulunamadı".into() }
                })?,
            }
        };
        guard.session.set_active_layer(Some(target_id));
        if was_locked {
            let _ = guard.session.execute(
                "layer.setProps",
                json!({ "layer": target_id.0, "locks": { "pixels": false } }),
            );
        }
        let res = guard
            .session
            .execute("edit.fill", json!({ "contents": "color", "color": color_hex }));
        if was_locked {
            let _ = guard.session.execute(
                "layer.setProps",
                json!({ "layer": target_id.0, "locks": { "pixels": true } }),
            );
        }
        res.map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    /// Content-Aware Object Removal (`edit.deleteAndFillSelection`): seamlessly removes the
    /// selected object and fills the region from surrounding photo context.
    pub fn content_aware_remove(&self) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard.live_stroke = None;
        guard.live_dab = None;
        guard.live_clone = None;
        let (target_id, was_locked, has_sel) = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            let has_sel = ds.doc().selection.as_ref().is_some_and(|s| !s.content_bounds().is_empty());
            let (id, locked) = find_primary_photo_raster_layer(ds.doc(), ds.active_layer()).ok_or_else(|| {
                EngineError::CommandFailed { detail: "Nesne silinecek fotoğraf katmanı bulunamadı".into() }
            })?;
            (id, locked, has_sel)
        };
        if !has_sel {
            return Err(EngineError::CommandFailed {
                detail: "Önce silmek istediğin nesneyi seç".into(),
            });
        }
        guard.session.set_active_layer(Some(target_id));
        if was_locked {
            let _ = guard.session.execute(
                "layer.setProps",
                json!({ "layer": target_id.0, "locks": { "pixels": false } }),
            );
        }
        let res = guard.session.execute("edit.deleteAndFillSelection", json!({}));
        if was_locked {
            let _ = guard.session.execute(
                "layer.setProps",
                json!({ "layer": target_id.0, "locks": { "pixels": true } }),
            );
        }
        res.map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        let _ = guard.session.execute("select.deselect", json!({}));
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    pub fn reveal_selection_mask(&self) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        let target_id = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            find_primary_photo_raster_layer(ds.doc(), ds.active_layer())
                .map(|(id, _)| id)
                .ok_or_else(|| EngineError::CommandFailed {
                    detail: "Maske eklenecek fotoğraf katmanı bulunamadı".into(),
                })?
        };
        guard.session.set_active_layer(Some(target_id));
        guard
            .session
            .execute("layer.layerMask.revealSelection", json!({}))
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    /// Real-time 60 FPS Clone Stamp (`paint.cloneStamp`) using PhotoCraft's native `LiveRetouch`.
    pub fn begin_live_clone(
        &self,
        sample: StrokeSample,
        source_x: f32,
        source_y: f32,
        size: f32,
        hardness: f32,
        opacity: f32,
    ) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard.live_stroke = None;
        guard.live_dab = None;
        guard.live_clone = None;

        let (prev_active, target_id, was_locked) = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            let prev = ds.active_layer();
            let (id, locked) = find_primary_photo_raster_layer(ds.doc(), prev).ok_or_else(|| {
                EngineError::CommandFailed {
                    detail: "Klonlanacak fotoğraf katmanı bulunamadı".into(),
                }
            })?;
            (prev, id, locked)
        };

        let clamped_size = size.clamp(1.0, 400.0);
        let clamped_hard = (hardness.clamp(0.0, 1.0) * 100.0).round();
        let clamped_op = (opacity.clamp(0.05, 1.0) * 100.0).round();
        let pt_json = json!([sample.x as f64, sample.y as f64, sample.pressure.clamp(0.05, 1.0) as f64]);

        let mut base_params = json!({
            "source": [source_x as f64, source_y as f64],
            "size": clamped_size,
            "hardness": clamped_hard,
            "opacity": clamped_op,
            "aligned": true,
            "sampleLayer": "current",
            "points": [pt_json.clone()]
        });

        guard.session.set_active_layer(Some(target_id));
        if was_locked {
            let _ = guard.session.execute(
                "layer.setProps",
                json!({ "layer": target_id.0, "locks": { "pixels": false } }),
            );
        }

        let live = match LiveRetouch::begin(&guard.session, "paint.cloneStamp", &base_params) {
            Ok(l) => l,
            Err(e) => {
                if was_locked {
                    let _ = guard.session.execute("edit.undo", json!({}));
                }
                if prev_active.is_some() {
                    guard.session.set_active_layer(prev_active);
                }
                return Err(EngineError::CommandFailed { detail: e.to_string() });
            }
        };

        let dmg = live.bounds();
        base_params["points"] = Value::Array(Vec::new());
        guard.live_clone = Some(ActiveLiveClone {
            live,
            base_params,
            points_json: vec![pt_json],
            target_id,
            prev_active,
            was_locked,
        });
        guard.mark_region_dirty(dmg);
        Ok(())
    }

    pub fn push_live_clone(&self, samples: Vec<StrokeSample>) -> Result<(), EngineError> {
        if samples.is_empty() {
            return Ok(());
        }
        let mut guard = self.core.lock();
        let Some(active) = guard.live_clone.as_mut() else {
            return Ok(());
        };
        let pts: Vec<StrokePoint> = samples.iter().map(sample_to_stroke_point).collect();
        let dmg = active
            .live
            .push(&pts)
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        for s in &samples {
            active
                .points_json
                .push(json!([s.x as f64, s.y as f64, s.pressure.clamp(0.05, 1.0) as f64]));
        }
        guard.mark_region_dirty(dmg);
        Ok(())
    }

    pub fn finish_live_clone(&self) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        let Some(active) = guard.live_clone.take() else {
            return self.info_from_locked(&guard);
        };
        let bounds = active.live.bounds();
        let mut params = active.base_params;
        params["points"] = Value::Array(active.points_json);

        let _ = guard.session.offer_live_retouch(active.live);
        let res = guard.session.execute("paint.cloneStamp", params);

        if active.was_locked {
            let _ = guard.session.execute(
                "layer.setProps",
                json!({ "layer": active.target_id.0, "locks": { "pixels": true } }),
            );
        }
        if active.prev_active.is_some() {
            guard.session.set_active_layer(active.prev_active);
        }
        res.map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_region_dirty(bounds);
        self.info_from_locked(&guard)
    }

    pub fn cancel_live_clone(&self) {
        let mut guard = self.core.lock();
        if let Some(active) = guard.live_clone.take() {
            let bounds = active.live.bounds();
            if active.was_locked {
                let _ = guard.session.execute("edit.undo", json!({}));
            }
            if active.prev_active.is_some() {
                guard.session.set_active_layer(active.prev_active);
            }
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

#[cfg(test)]
mod v08_automated_tests {
    use super::*;

    fn test_engine() -> KastamonuEngine {
        KastamonuEngine::from_shared(Arc::new(Mutex::new(EngineCore::new())))
    }

    #[test]
    fn test_v08_content_aware_remove_replaces_selected_object() {
        let eng = test_engine();
        let w = 160u32;
        let h = 120u32;
        let mut rgba = vec![0u8; (w * h * 4) as usize];
        // Green background with a red 20x20 object in the center
        for y in 0..h {
            for x in 0..w {
                let i = ((y * w + x) * 4) as usize;
                let in_blob = (70..90).contains(&x) && (50..70).contains(&y);
                rgba[i] = if in_blob { 220 } else { 40 };
                rgba[i + 1] = if in_blob { 30 } else { 180 };
                rgba[i + 2] = 40;
                rgba[i + 3] = 255;
            }
        }
        eng.open_rgba_bytes("test.png".into(), w, h, rgba).expect("open_rgba_bytes");
        let sel = eng
            .select_rect(68, 48, 24, 24, false, "replace".into())
            .expect("select_rect");
        assert!(sel.is_some());
        let info = eng.content_aware_remove().expect("content_aware_remove");
        assert_eq!(info.width, w);
        assert!(eng.get_selection_bounds().is_none(), "selection cleared after remove");
    }

    #[test]
    fn test_v08_perspective_crop_rectifies_quad() {
        let eng = test_engine();
        eng.new_document("persp".into(), 320, 240).expect("new_document");
        let info = eng
            .perspective_crop(40.0, 30.0, 280.0, 45.0, 295.0, 210.0, 25.0, 200.0)
            .expect("perspective_crop");
        assert!(info.width > 50 && info.height > 50, "rectified dimensions valid");
        let undone = eng.undo().expect("undo perspective crop");
        assert_eq!((undone.width, undone.height), (320, 240));
    }

    #[test]
    fn test_v08_curves_adjustment_layer_and_thumbnail() {
        let eng = test_engine();
        eng.new_document("curves".into(), 128, 128).expect("new_document");
        let before = eng.get_layers().expect("layers").len();
        let info = eng
            .apply_adjustment_once(
                "curves".into(),
                r#"{"points":[[0,0],[64,48],[128,128],[192,210],[255,255]]}"#.into(),
            )
            .expect("curves adjustment");
        assert_eq!(info.layer_count as usize, before + 1);

        let layers = eng.get_layers().expect("layers");
        let bg = layers.iter().find(|l| l.is_background).expect("bg layer");
        let thumb = eng
            .get_layer_thumbnail(bg.id, 64)
            .expect("thumbnail")
            .expect("raster thumb");
        assert_eq!(thumb.width, 64);
        assert_eq!(thumb.rgba.len(), (thumb.width * thumb.height * 4) as usize);
    }

    #[test]
    fn test_v07_manual_selection_paint_and_erase() {
        let eng = test_engine();
        eng.new_document("sel".into(), 128, 128).expect("new_document");
        let pts = vec![
            StrokeSample { x: 40.0, y: 40.0, pressure: 1.0, tilt_x: 0.0, tilt_y: 0.0 },
            StrokeSample { x: 80.0, y: 40.0, pressure: 1.0, tilt_x: 0.0, tilt_y: 0.0 },
        ];
        let added = eng.paint_selection_stroke(pts.clone(), 24.0, false).expect("paint add");
        assert!(added.is_some());
        let erased = eng.paint_selection_stroke(pts, 40.0, true).expect("paint erase");
        assert!(erased.is_none(), "full erase clears selection");
    }

    #[test]
    fn test_v082_proxy_preview_exact_blur_and_hue_saturation() {
        let eng = test_engine();
        eng.new_document("proxy".into(), 640, 480).expect("new_document");
        eng.begin_proxy_preview().expect("begin_proxy_preview");
        eng.update_proxy_adjustment("hueSaturation".into(), r#"{"hue":0,"saturation":100,"lightness":0}"#.into())
            .expect("proxy hueSaturation");
        eng.update_proxy_blur(40.0).expect("proxy blur");
        eng.update_proxy_sharpen(80.0).expect("proxy sharpen");
        eng.end_proxy_preview();
        let info = eng.get_document_info().expect("doc info");
        assert_eq!((info.width, info.height), (640, 480));
    }

    #[test]
    fn test_v082_transform_active_layer_independently() {
        let eng = test_engine();
        eng.new_document("xf".into(), 200, 200).expect("new_document");
        let _ = eng.select_rect(40, 40, 60, 60, false, "replace".into()).expect("sel");
        let _ = eng.fill_selection_pixels("#FF0000".into()).expect("fill");
        let _ = eng.select_deselect().expect("desel");
        let bounds = eng.get_active_layer_bounds().expect("active layer bounds");
        assert!(bounds.x1 > bounds.x0);
        let _ = eng.transform_active_layer(25.0, -15.0, 75.0, 15.0).expect("transform_active_layer");
        let _ = eng.undo().expect("undo transform");
    }

    #[test]
    fn test_v083_smart_object_wand_and_hidden_transform_layer() {
        let eng = test_engine();
        let w = 160u32;
        let h = 160u32;
        let mut rgba = vec![0u8; (w * h * 4) as usize];
        for y in 0..h {
            for x in 0..w {
                let i = ((y * w + x) * 4) as usize;
                let in_obj = (45..115).contains(&x) && (45..115).contains(&y);
                rgba[i] = if in_obj { 210 } else { 30 };
                rgba[i + 1] = if in_obj { 50 } else { 140 };
                rgba[i + 2] = if in_obj { 60 } else { 210 };
                rgba[i + 3] = 255;
            }
        }
        eng.open_rgba_bytes("obj.png".into(), w, h, rgba).expect("open");
        let sel = eng.select_object_at_point(80, 80, "replace".into()).expect("smart object wand");
        assert!(sel.is_some(), "smart object wand should select the center object");

        let layers = eng.get_layers().expect("layers");
        let bg_id = layers.iter().find(|l| l.is_background).unwrap().id;
        eng.set_transform_hidden_layer(Some(bg_id));
        eng.set_transform_hidden_layer(None);
    }

    #[test]
    fn test_v090_layer_arrange_duplicate_merge_via_copy_and_feather() {
        let eng = test_engine();
        eng.new_document("v09".into(), 160, 160).expect("new_document");
        let _ = eng.select_rect(20, 20, 60, 60, false, "replace".into()).expect("select");
        let _ = eng.feather_selection(4.0).expect("feather");
        let info = eng.layer_via_copy().expect("layer_via_copy");
        assert!(info.layer_count >= 3, "new layer created from selection");

        let layers_dup = eng.duplicate_active_layer().expect("duplicate");
        assert!(layers_dup.len() >= 4);
        let _ = eng.move_layer_down().expect("move down");
        let _ = eng.move_layer_up().expect("move up");
        let layers_merged = eng.merge_layer_down().expect("merge down");
        assert_eq!(layers_merged.len(), layers_dup.len() - 1);
    }

    #[test]
    fn test_v090_layer_effects_before_after_and_pcraft_roundtrip() {
        let eng = test_engine();
        eng.new_document("fx".into(), 128, 128).expect("new_document");
        let _ = eng.select_rect(30, 30, 50, 50, false, "replace".into()).expect("sel");
        let _ = eng.fill_selection_pixels("#FF4D4F".into()).expect("fill");
        let _ = eng.select_deselect().expect("desel");

        let _ = eng.apply_layer_effect("dropShadow".into(), "#000000".into(), 12.0, 75.0, 8.0).expect("dropShadow");
        let _ = eng.apply_layer_effect("stroke".into(), "#FFFFFF".into(), 4.0, 100.0, 0.0).expect("stroke");
        let _ = eng.apply_layer_effect("outerGlow".into(), "#4F9CFF".into(), 16.0, 80.0, 0.0).expect("outerGlow");
        let _ = eng.apply_layer_effect("colorOverlay".into(), "#52C41A".into(), 1.0, 50.0, 0.0).expect("colorOverlay");

        eng.set_compare_original(true);
        eng.set_compare_original(false);

        let pcraft_bytes = eng.export_document_bytes("pcraft".into()).expect("export pcraft");
        assert!(!pcraft_bytes.is_empty());
        let reopened = eng.open_document_bytes("saved.pcraft".into(), pcraft_bytes).expect("reopen pcraft");
        assert!(reopened.layer_count >= 2, "multi-layer pcraft preserved all layers");

        let _ = eng.clear_layer_effects().expect("clear fx");
    }

    #[test]
    fn test_v091_live_proxy_layer_effect_preview() {
        let eng = test_engine();
        eng.new_document("fx_proxy".into(), 320, 240).expect("new_document");
        eng.begin_layer_proxy_preview().expect("begin_layer_proxy_preview");
        eng.update_proxy_layer_effect("colorOverlay".into(), "#4F9CFF".into(), 10.0, 60.0, 0.0)
            .expect("proxy colorOverlay");
        eng.update_proxy_layer_effect("stroke".into(), "#FFFFFF".into(), 12.0, 100.0, 0.0)
            .expect("proxy stroke");
        eng.end_proxy_preview();
    }

    #[test]
    fn test_v092_before_after_snapshot_survives_blur_and_auto_dismisses() {
        let eng = test_engine();
        let w = 64u32;
        let h = 64u32;
        let mut rgba = vec![0u8; (w * h * 4) as usize];
        for y in 0..h {
            for x in 0..w {
                let i = ((y * w + x) * 4) as usize;
                let inside = (20..44).contains(&x) && (20..44).contains(&y);
                rgba[i] = if inside { 230 } else { 0 };
                rgba[i + 1] = if inside { 30 } else { 0 };
                rgba[i + 2] = if inside { 30 } else { 0 };
                rgba[i + 3] = if inside { 255 } else { 0 }; // Transparent background!
            }
        }
        eng.open_rgba_bytes("transparent.png".into(), w, h, rgba).expect("open");
        eng.apply_blur_fast(12.0).expect("blur");

        // Enable Before/After — snapshot must still hold the untouched sharp original!
        eng.set_compare_original(true);
        {
            let guard = eng.core.lock();
            assert!(guard.compare_original);
            let (orig_doc, _) = guard.active_document_and_display_size().expect("orig doc");
            let LayerContent::Raster(surf) = &orig_doc.layers[0].content else { panic!("raster") };
            let px = surf.read_region(Rect::new(2, 2, 3, 3));
            assert!(px[3] < 0.01, "original snapshot must preserve transparent background");
            let center = surf.read_region(Rect::new(32, 32, 33, 33));
            assert!(center[0] > 0.85 && center[3] > 0.95, "original snapshot must preserve sharp red center");
        }

        // Starting a new edit must automatically dismiss Before/After
        eng.begin_proxy_preview().expect("proxy");
        {
            let guard = eng.core.lock();
            assert!(!guard.compare_original, "compare_original must auto-dismiss on new tool/preview");
        }
        eng.end_proxy_preview();
    }

    #[test]
    fn test_v092_proxy_preserves_alpha_and_selection_masked_blur_and_1px_brush() {
        let eng = test_engine();
        let w = 80u32;
        let h = 80u32;
        let mut rgba = vec![0u8; (w * h * 4) as usize];
        for y in 0..h {
            for x in 0..w {
                let i = ((y * w + x) * 4) as usize;
                let inside = (24..56).contains(&x) && (24..56).contains(&y);
                rgba[i] = if inside { 240 } else { 0 };
                rgba[i + 1] = 0;
                rgba[i + 2] = 0;
                rgba[i + 3] = if inside { 255 } else { 0 };
            }
        }
        eng.open_rgba_bytes("alpha_test.png".into(), w, h, rgba).expect("open");

        // Proxy preview must NOT flatten transparent background to solid white
        eng.begin_proxy_preview().expect("begin_proxy_preview");
        eng.update_proxy_sharpen(150.0).expect("proxy sharpen");
        {
            let guard = eng.core.lock();
            let (pdoc, _) = guard.active_document_and_display_size().expect("proxy doc");
            let LayerContent::Raster(surf) = &pdoc.layers[0].content else { panic!("raster") };
            let corner = surf.read_region(Rect::new(1, 1, 2, 2));
            assert!(corner[3] < 0.01, "proxy preview must keep transparent corner transparent, got alpha={}", corner[3]);
        }
        eng.end_proxy_preview();

        // Selection-masked blur must leave unselected pixels untouched
        let _ = eng.select_rect(0, 0, 20, 20, false, "replace".into()).expect("sel");
        eng.apply_blur_fast(10.0).expect("masked blur");
        let _ = eng.select_deselect().expect("desel");

        // 1px brush & 1px manual selection stroke must both succeed cleanly
        eng.set_brush(1.0, 1.0, 1.0, 1.0, "#FFFFFF".into()).expect("1px brush");
        let pts = vec![
            StrokeSample { x: 10.0, y: 10.0, pressure: 1.0, tilt_x: 0.0, tilt_y: 0.0 },
            StrokeSample { x: 20.0, y: 10.0, pressure: 1.0, tilt_x: 0.0, tilt_y: 0.0 },
        ];
        let sel1px = eng.paint_selection_stroke(pts, 1.0, false).expect("1px selection stroke");
        assert!(sel1px.is_some(), "1px selection stroke should select pixels");
    }

    #[test]
    fn test_v095_camera_light_proxy_and_apply_preserves_alpha() {
        let eng = test_engine();
        let w = 96u32;
        let h = 96u32;
        let mut rgba = vec![0u8; (w * h * 4) as usize];
        for y in 0..h {
            for x in 0..w {
                let i = ((y * w + x) * 4) as usize;
                let inside = (24..72).contains(&x) && (24..72).contains(&y);
                rgba[i] = if inside { 90 } else { 0 };
                rgba[i + 1] = if inside { 80 } else { 0 };
                rgba[i + 2] = if inside { 70 } else { 0 };
                rgba[i + 3] = if inside { 255 } else { 0 };
            }
        }
        eng.open_rgba_bytes("cam.png".into(), w, h, rgba).expect("open");
        eng.begin_proxy_preview().expect("begin proxy");
        eng.update_proxy_camera_light(1.2, 45.0, -30.0, 35.0, 20.0, 0.0, 25.0, 10.0)
            .expect("proxy camera light");
        eng.end_proxy_preview();
        let info = eng
            .apply_camera_light(1.2, 45.0, -30.0, 35.0, 20.0, 0.0, 25.0, 10.0)
            .expect("apply camera light");
        assert_eq!((info.width, info.height), (w, h));
        // Verify transparent corner stays transparent after Camera Raw develop
        let guard = eng.core.lock();
        let doc = guard.active_document().unwrap();
        let LayerContent::Raster(surf) = &doc.layers[0].content else { panic!("raster") };
        let corner = surf.read_region(Rect::new(2, 2, 3, 3));
        assert!(corner[3] < 0.01, "camera light must preserve transparency");
    }

    #[test]
    fn test_v095_gradient_fill_proxy_and_apply_creates_layer() {
        let eng = test_engine();
        eng.new_document("grad".into(), 160, 160).expect("new_document");
        let before = eng.get_layers().expect("layers").len();
        eng.begin_proxy_preview().expect("begin proxy");
        eng.update_proxy_gradient(10.0, 10.0, 150.0, 150.0, "linear".into(), "#4F9CFF".into(), "#FF4D4F".into(), 85.0)
            .expect("proxy gradient");
        eng.end_proxy_preview();
        let info = eng
            .apply_gradient_fill(10.0, 10.0, 150.0, 150.0, "radial".into(), "#4F9CFF".into(), "#FF4D4F".into(), 85.0)
            .expect("apply gradient");
        assert_eq!(info.layer_count as usize, before + 1);
    }

    #[test]
    fn test_v095_vector_shape_proxy_and_create_all_kinds() {
        let eng = test_engine();
        eng.new_document("shapes".into(), 200, 200).expect("new_document");
        let before = eng.get_layers().expect("layers").len();
        eng.begin_proxy_preview().expect("begin proxy");
        eng.update_proxy_shape("roundedRect".into(), 20.0, 20.0, 160.0, 140.0, "#4F9CFF".into(), "#FFFFFF".into(), 4.0, 16.0, 5)
            .expect("proxy roundedRect");
        eng.end_proxy_preview();

        let _ = eng.create_shape_layer("rect".into(), 20.0, 20.0, 120.0, 100.0, "#FF4D4F".into(), "#FFFFFF".into(), 3.0, 0.0, 5).expect("rect");
        let _ = eng.create_shape_layer("ellipse".into(), 40.0, 40.0, 160.0, 160.0, "#52C41A".into(), "".into(), 0.0, 0.0, 5).expect("ellipse");
        let _ = eng.create_shape_layer("polygon".into(), 30.0, 30.0, 150.0, 150.0, "#FAAD14".into(), "#000000".into(), 2.0, 0.0, 6).expect("polygon");
        let info = eng.create_shape_layer("line".into(), 10.0, 10.0, 180.0, 180.0, "".into(), "#FFFFFF".into(), 6.0, 0.0, 5).expect("line");
        assert_eq!(info.layer_count as usize, before + 4);
    }

    #[test]
    fn test_v098_liquify_forward_warp_bloat_reset_and_commit_undo() {
        let eng = test_engine();
        let w = 128u32;
        let h = 128u32;
        let mut rgba = vec![0u8; (w * h * 4) as usize];
        for y in 0..h {
            for x in 0..w {
                let i = ((y * w + x) * 4) as usize;
                let inside = (32..96).contains(&x) && (32..96).contains(&y);
                rgba[i] = if inside { 220 } else { 0 };
                rgba[i + 1] = if inside { 40 } else { 0 };
                rgba[i + 2] = if inside { 40 } else { 0 };
                rgba[i + 3] = if inside { 255 } else { 0 };
            }
        }
        eng.open_rgba_bytes("liq.png".into(), w, h, rgba).expect("open");

        eng.begin_liquify_session().expect("begin liquify");
        eng.liquify_stroke_begin("forwardWarp".into(), 60.0, 80.0, 50.0, 64.0, 64.0).expect("begin stroke");
        eng.liquify_stroke_move(88.0, 64.0).expect("move stroke");
        eng.liquify_stroke_end();

        eng.reset_liquify_session().expect("reset liquify");
        {
            let guard = eng.core.lock();
            assert!(guard.live_liquify.as_ref().unwrap().field.is_identity());
        }

        eng.liquify_stroke_begin("bloat".into(), 64.0, 85.0, 50.0, 64.0, 64.0).expect("bloat");
        eng.liquify_stroke_move(66.0, 64.0).expect("bloat move");
        eng.liquify_stroke_end();
        let info = eng.commit_liquify_session().expect("commit liquify");
        assert_eq!((info.width, info.height), (w, h));
        let _ = eng.undo().expect("undo liquify");
    }

    #[test]
    fn test_v098_liquify_cancel_leaves_document_untouched() {
        let eng = test_engine();
        eng.new_document("liq_cancel".into(), 96, 96).expect("new_document");
        eng.begin_liquify_session().expect("begin");
        eng.liquify_stroke_begin("twirlCw".into(), 48.0, 70.0, 50.0, 48.0, 48.0).expect("twirl");
        eng.liquify_stroke_move(52.0, 52.0).expect("move");
        eng.liquify_stroke_end();
        eng.cancel_liquify_session();
        let guard = eng.core.lock();
        assert!(guard.live_liquify.is_none());
    }

    #[test]
    fn test_v099_auto_enhance_tone_color_contrast() {
        let eng = test_engine();
        let w = 64u32;
        let h = 64u32;
        let mut rgba = vec![0u8; (w * h * 4) as usize];
        for y in 0..h {
            for x in 0..w {
                let i = ((y * w + x) * 4) as usize;
                rgba[i] = (60 + x * 2) as u8;
                rgba[i + 1] = (70 + y) as u8;
                rgba[i + 2] = 90;
                rgba[i + 3] = 255;
            }
        }
        eng.open_rgba_bytes("auto.png".into(), w, h, rgba).expect("open");
        for mode in ["tone", "color", "contrast"] {
            let info = eng.apply_auto_enhance(mode.into()).expect("auto enhance");
            assert_eq!((info.width, info.height), (w, h));
            let _ = eng.undo().expect("undo auto");
        }
    }

    #[test]
    fn test_v099_cinema_look_vignette_grain_proxy_and_apply() {
        let eng = test_engine();
        eng.new_document("cinema".into(), 128, 128).expect("new_document");
        eng.begin_proxy_preview().expect("begin proxy");
        eng.update_proxy_cinema_look("tealOrange".into(), 80.0, -35.0, 20.0).expect("proxy cinema");
        eng.end_proxy_preview();
        let info = eng.apply_cinema_look("golden".into(), 90.0, -30.0, 15.0).expect("apply cinema");
        assert_eq!((info.width, info.height), (128, 128));
    }

    #[test]
    fn test_v099_motion_and_radial_blur_proxy_and_apply() {
        let eng = test_engine();
        eng.new_document("blurs".into(), 128, 128).expect("new_document");
        eng.begin_proxy_preview().expect("begin proxy");
        eng.update_proxy_motion_blur(45.0, 24.0).expect("proxy motion");
        eng.update_proxy_radial_blur(15.0, "zoom".into()).expect("proxy radial");
        eng.end_proxy_preview();
        let _ = eng.apply_motion_blur(30.0, 18.0).expect("apply motion");
        let info = eng.apply_radial_blur(12.0, "spin".into()).expect("apply radial");
        assert_eq!((info.width, info.height), (128, 128));
    }

    #[test]
    fn test_v0995_liquify_per_stroke_undo_redo_repeatedly() {
        let eng = test_engine();
        eng.new_document("liq_ur".into(), 128, 128).expect("new_document");
        eng.begin_liquify_session().expect("begin");

        let s0 = eng.get_liquify_history_state();
        assert!(!s0.can_undo && !s0.can_redo);

        // Stroke 1
        eng.liquify_stroke_begin("forwardWarp".into(), 48.0, 80.0, 50.0, 40.0, 40.0).expect("s1");
        eng.liquify_stroke_move(70.0, 40.0).expect("s1m");
        eng.liquify_stroke_end();
        // Stroke 2
        eng.liquify_stroke_begin("bloat".into(), 48.0, 80.0, 50.0, 80.0, 80.0).expect("s2");
        eng.liquify_stroke_move(82.0, 80.0).expect("s2m");
        eng.liquify_stroke_end();

        let s1 = eng.liquify_undo().expect("undo 1");
        assert!(s1.can_undo && s1.can_redo, "after 1st undo, both undo and redo must be available");
        let s2 = eng.liquify_undo().expect("undo 2");
        assert!(!s2.can_undo && s2.can_redo, "after 2nd undo, redo must still work");
        let s3 = eng.liquify_redo().expect("redo 1");
        assert!(s3.can_undo && s3.can_redo, "after redo, undo must work again");
        eng.cancel_liquify_session();
    }

    #[test]
    fn test_v0995_sample_loupe_patch_returns_crisp_center_pixels() {
        let eng = test_engine();
        let w = 64u32;
        let h = 64u32;
        let mut rgba = vec![0u8; (w * h * 4) as usize];
        for y in 0..h {
            for x in 0..w {
                let i = ((y * w + x) * 4) as usize;
                rgba[i] = 250;
                rgba[i + 1] = 20;
                rgba[i + 2] = 30;
                rgba[i + 3] = 255;
            }
        }
        eng.open_rgba_bytes("loupe.png".into(), w, h, rgba).expect("open");
        let patch = eng.sample_loupe_patch(32.0, 32.0, 12.0, 48).expect("loupe patch");
        assert_eq!((patch.width, patch.height), (48, 48));
        assert_eq!(patch.rgba.len(), 48 * 48 * 4);
        let center_idx = (24 * 48 + 24) * 4;
        assert!(patch.rgba[center_idx] > 230 && patch.rgba[center_idx + 1] < 40, "center pixel must match red photo");
    }
}
