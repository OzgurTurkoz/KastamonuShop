// StonebridgeCraft — Hardware-accelerated Canvas Blit & 60 FPS GPU Preview Shader (WGSL)
// Zero-lag 60 FPS GPU previews for Mirror, Flip, Brightness/Contrast, Hue/Saturation, Blur, and Sharpen.

struct ViewportUniforms {
    // [canvas_width, canvas_height, doc_width, doc_height]
    dims: vec4<f32>,
    // [pan_x, pan_y, zoom, rotation_rad]
    camera: vec4<f32>,
    // [checker_size_px, bg_r, bg_g, bg_b]
    style: vec4<f32>,
    // [mirror_mode (0..4), mirror_axis, flip_x, flip_y]
    preview: vec4<f32>,
    // [brightness (-100..100), contrast (-50..100), saturation (-100..100), hue_deg (-180..180)]
    adjust: vec4<f32>,
    // [blur_radius_px (0..60), sharpen_amount (0..250), 0.0, 0.0]
    filter_p: vec4<f32>,
    // [in_black (0..1), gamma (0.1..4.0), in_white (0..1), enabled (0/1)]
    levels: vec4<f32>,
    // [cyan_red (-1..1), magenta_green (-1..1), yellow_blue (-1..1), enabled (0/1)]
    color_bal: vec4<f32>,
    // [has_selection (0/1), sel_tex_w, sel_tex_h, 0.0]
    sel_info: vec4<f32>,
    // [shadow_y (0..1), mid_y (0..1), high_y (0..1), enabled (0/1)]
    curve_pts: vec4<f32>,
    // [black_y (0..1), white_y (0..1), 0.0, 0.0]
    curve_ends: vec4<f32>,
};

@group(0) @binding(0)
var<uniform> u: ViewportUniforms;

@group(0) @binding(1)
var doc_tex: texture_2d<f32>;

@group(0) @binding(2)
var doc_sampler: sampler;

@group(0) @binding(3)
var sel_tex: texture_2d<f32>;

struct VertexOut {
    @builtin(position) clip_pos: vec4<f32>,
    @location(0) screen_px: vec2<f32>,
};

fn eval_curve_1d(x: f32, y0: f32, y1: f32, y2: f32, y3: f32, y4: f32) -> f32 {
    let xc = clamp(x, 0.0, 1.0);
    // Piecewise smooth Hermite interpolation over 5 knots: (0,y0), (0.25,y1), (0.5,y2), (0.75,y3), (1,y4)
    let m0 = (y1 - y0) * 4.0;
    let m1 = (y2 - y0) * 2.0;
    let m2 = (y3 - y1) * 2.0;
    let m3 = (y4 - y2) * 2.0;
    let m4 = (y4 - y3) * 4.0;
    var p0 = y0; var p1 = y1; var t0 = m0; var t1 = m1; var t = xc * 4.0;
    if (xc >= 0.75) {
        p0 = y3; p1 = y4; t0 = m3; t1 = m4; t = (xc - 0.75) * 4.0;
    } else if (xc >= 0.50) {
        p0 = y2; p1 = y3; t0 = m2; t1 = m3; t = (xc - 0.50) * 4.0;
    } else if (xc >= 0.25) {
        p0 = y1; p1 = y2; t0 = m1; t1 = m2; t = (xc - 0.25) * 4.0;
    }
    let t2 = t * t;
    let t3 = t2 * t;
    let h00 = 2.0 * t3 - 3.0 * t2 + 1.0;
    let h10 = t3 - 2.0 * t2 + t;
    let h01 = -2.0 * t3 + 3.0 * t2;
    let h11 = t3 - t2;
    return clamp(h00 * p0 + h10 * (t0 * 0.25) + h01 * p1 + h11 * (t1 * 0.25), 0.0, 1.0);
}

fn ping_pong_01(v: f32) -> f32 {
    let period = 2.0;
    let m = v - period * floor(v / period);
    return select(period - m, m, m <= 1.0);
}

@vertex
fn vs_main(@builtin(vertex_index) idx: u32) -> VertexOut {
    var pos = array<vec2<f32>, 3>(
        vec2<f32>(-1.0, -3.0),
        vec2<f32>(-1.0,  1.0),
        vec2<f32>( 3.0,  1.0),
    );
    let ndc = pos[idx];
    var out: VertexOut;
    out.clip_pos = vec4<f32>(ndc, 0.0, 1.0);
    let uv = vec2<f32>((ndc.x + 1.0) * 0.5, (1.0 - ndc.y) * 0.5);
    out.screen_px = uv * u.dims.xy;
    return out;
}

@fragment
fn fs_main(in: VertexOut) -> @location(0) vec4<f32> {
    let canvas_center = u.dims.xy * 0.5;
    let doc_size = u.dims.zw;
    let pan = u.camera.xy;
    let zoom = max(u.camera.z, 0.01);
    let rot = u.camera.w;

    let p = in.screen_px - canvas_center - pan;
    let c = cos(-rot);
    let s = sin(-rot);
    let rotated = vec2<f32>(p.x * c - p.y * s, p.x * s + p.y * c);
    let doc_px = rotated / zoom + doc_size * 0.5;

    if (doc_px.x < 0.0 || doc_px.y < 0.0 || doc_px.x > doc_size.x || doc_px.y > doc_size.y) {
        return vec4<f32>(u.style.y, u.style.z, u.style.w, 1.0);
    }

    let cell = floor(in.screen_px / max(u.style.x, 4.0));
    let is_light = ((i32(cell.x) + i32(cell.y)) & 1) == 0;
    let checker = select(vec3<f32>(0.76, 0.76, 0.76), vec3<f32>(0.90, 0.90, 0.90), is_light);

    var doc_uv = doc_px / doc_size;

    // 60 FPS GPU Flip preview
    if (u.preview.z > 0.5) {
        doc_uv.x = 1.0 - doc_uv.x;
    }
    if (u.preview.w > 0.5) {
        doc_uv.y = 1.0 - doc_uv.y;
    }

    // 60 FPS Smart GPU Mirror preview
    let mirror_mode = i32(round(u.preview.x));
    let axis = clamp(u.preview.y, 0.05, 0.95);
    if (mirror_mode == 1) {
        if (axis >= 0.5 && doc_uv.x > axis) {
            doc_uv.x = ping_pong_01(2.0 * axis - doc_uv.x);
        } else if (axis < 0.5 && doc_uv.x < axis) {
            doc_uv.x = ping_pong_01(2.0 * axis - doc_uv.x);
        }
    } else if (mirror_mode == 2) {
        if (axis >= 0.5 && doc_uv.x < axis) {
            doc_uv.x = ping_pong_01(2.0 * axis - doc_uv.x);
        } else if (axis < 0.5 && doc_uv.x > axis) {
            doc_uv.x = ping_pong_01(2.0 * axis - doc_uv.x);
        }
    } else if (mirror_mode == 3) {
        if (axis >= 0.5 && doc_uv.y > axis) {
            doc_uv.y = ping_pong_01(2.0 * axis - doc_uv.y);
        } else if (axis < 0.5 && doc_uv.y < axis) {
            doc_uv.y = ping_pong_01(2.0 * axis - doc_uv.y);
        }
    } else if (mirror_mode == 4) {
        if (axis >= 0.5 && doc_uv.y < axis) {
            doc_uv.y = ping_pong_01(2.0 * axis - doc_uv.y);
        } else if (axis < 0.5 && doc_uv.y > axis) {
            doc_uv.y = ping_pong_01(2.0 * axis - doc_uv.y);
        }
    }

    var sampled = textureSample(doc_tex, doc_sampler, doc_uv);
    let texel = vec2<f32>(1.0, 1.0) / max(doc_size, vec2<f32>(1.0, 1.0));

    // 60 FPS GPU Blur preview (13-tap 2-ring disk blur)
    let blur_r = u.filter_p.x;
    if (blur_r > 0.25) {
        let r1 = texel * (blur_r * 0.45);
        let r2 = texel * blur_r;
        let d2 = r2 * 0.70710678;
        var acc = sampled * 2.0;
        acc += textureSample(doc_tex, doc_sampler, clamp(doc_uv + vec2<f32>( r1.x, 0.0), vec2<f32>(0.0), vec2<f32>(1.0))) * 1.5;
        acc += textureSample(doc_tex, doc_sampler, clamp(doc_uv + vec2<f32>(-r1.x, 0.0), vec2<f32>(0.0), vec2<f32>(1.0))) * 1.5;
        acc += textureSample(doc_tex, doc_sampler, clamp(doc_uv + vec2<f32>(0.0,  r1.y), vec2<f32>(0.0), vec2<f32>(1.0))) * 1.5;
        acc += textureSample(doc_tex, doc_sampler, clamp(doc_uv + vec2<f32>(0.0, -r1.y), vec2<f32>(0.0), vec2<f32>(1.0))) * 1.5;
        acc += textureSample(doc_tex, doc_sampler, clamp(doc_uv + vec2<f32>( r2.x, 0.0), vec2<f32>(0.0), vec2<f32>(1.0)));
        acc += textureSample(doc_tex, doc_sampler, clamp(doc_uv + vec2<f32>(-r2.x, 0.0), vec2<f32>(0.0), vec2<f32>(1.0)));
        acc += textureSample(doc_tex, doc_sampler, clamp(doc_uv + vec2<f32>(0.0,  r2.y), vec2<f32>(0.0), vec2<f32>(1.0)));
        acc += textureSample(doc_tex, doc_sampler, clamp(doc_uv + vec2<f32>(0.0, -r2.y), vec2<f32>(0.0), vec2<f32>(1.0)));
        acc += textureSample(doc_tex, doc_sampler, clamp(doc_uv + vec2<f32>( d2.x,  d2.y), vec2<f32>(0.0), vec2<f32>(1.0)));
        acc += textureSample(doc_tex, doc_sampler, clamp(doc_uv + vec2<f32>(-d2.x,  d2.y), vec2<f32>(0.0), vec2<f32>(1.0)));
        acc += textureSample(doc_tex, doc_sampler, clamp(doc_uv + vec2<f32>( d2.x, -d2.y), vec2<f32>(0.0), vec2<f32>(1.0)));
        acc += textureSample(doc_tex, doc_sampler, clamp(doc_uv + vec2<f32>(-d2.x, -d2.y), vec2<f32>(0.0), vec2<f32>(1.0)));
        sampled = acc / 16.0;
    }

    // 60 FPS GPU Sharpen preview (5-tap Unsharp Mask)
    let sharp_amt = u.filter_p.y;
    if (sharp_amt > 0.5) {
        let step = texel * 1.5;
        let n1 = textureSample(doc_tex, doc_sampler, clamp(doc_uv + vec2<f32>( step.x, 0.0), vec2<f32>(0.0), vec2<f32>(1.0))).rgb;
        let n2 = textureSample(doc_tex, doc_sampler, clamp(doc_uv + vec2<f32>(-step.x, 0.0), vec2<f32>(0.0), vec2<f32>(1.0))).rgb;
        let n3 = textureSample(doc_tex, doc_sampler, clamp(doc_uv + vec2<f32>(0.0,  step.y), vec2<f32>(0.0), vec2<f32>(1.0))).rgb;
        let n4 = textureSample(doc_tex, doc_sampler, clamp(doc_uv + vec2<f32>(0.0, -step.y), vec2<f32>(0.0), vec2<f32>(1.0))).rgb;
        let blur4 = (n1 + n2 + n3 + n4) * 0.25;
        let k = (sharp_amt / 100.0) * 1.1;
        sampled = vec4<f32>(clamp(sampled.rgb + (sampled.rgb - blur4) * k, vec3<f32>(0.0), vec3<f32>(1.0)), sampled.a);
    }

    var col = sampled.rgb;

    // 60 FPS GPU Brightness & Contrast preview
    if (abs(u.adjust.x) > 0.01 || abs(u.adjust.y) > 0.01) {
        let b = u.adjust.x / 200.0;
        let c_norm = u.adjust.y / 100.0;
        let factor = select(1.0 + c_norm * 0.65, 1.0 + c_norm * 1.25, c_norm > 0.0);
        col = clamp((col + b - 0.5) * factor + 0.5, vec3<f32>(0.0), vec3<f32>(1.0));
    }

    // 60 FPS GPU Saturation & Hue preview
    if (abs(u.adjust.z) > 0.01 || abs(u.adjust.w) > 0.01) {
        let luma = dot(col, vec3<f32>(0.2126, 0.7152, 0.0722));
        let sat_scale = max(0.0, 1.0 + u.adjust.z / 100.0);
        col = mix(vec3<f32>(luma), col, sat_scale);
        if (abs(u.adjust.w) > 0.01) {
            let rad = u.adjust.w * 0.0174532925;
            let k = vec3<f32>(0.57735027);
            let cos_a = cos(rad);
            let sin_a = sin(rad);
            col = col * cos_a + cross(k, col) * sin_a + k * dot(k, col) * (1.0 - cos_a);
        }
        col = clamp(col, vec3<f32>(0.0), vec3<f32>(1.0));
    }

    // 60 FPS GPU Levels preview
    if (u.levels.w > 0.5) {
        let denom = max(u.levels.z - u.levels.x, 0.004);
        let norm = clamp((col - vec3<f32>(u.levels.x)) / denom, vec3<f32>(0.0), vec3<f32>(1.0));
        col = pow(norm, vec3<f32>(1.0 / clamp(u.levels.y, 0.1, 4.0)));
    }

    // 60 FPS GPU Color Balance preview (midtones)
    if (u.color_bal.w > 0.5) {
        let shift = u.color_bal.xyz * 0.35;
        let mid_w = 1.0 - abs(col * 2.0 - vec3<f32>(1.0));
        col = clamp(col + shift * mid_w, vec3<f32>(0.0), vec3<f32>(1.0));
    }

    // 60 FPS GPU Tone Curve (Curves) preview (all 5 knots draggable)
    if (u.curve_pts.w > 0.5) {
        col = vec3<f32>(
            eval_curve_1d(col.r, u.curve_ends.x, u.curve_pts.x, u.curve_pts.y, u.curve_pts.z, u.curve_ends.y),
            eval_curve_1d(col.g, u.curve_ends.x, u.curve_pts.x, u.curve_pts.y, u.curve_pts.z, u.curve_ends.y),
            eval_curve_1d(col.b, u.curve_ends.x, u.curve_pts.x, u.curve_pts.y, u.curve_pts.z, u.curve_ends.y)
        );
    }

    var rgb = mix(checker, col, sampled.a);

    // 60 FPS GPU-native pixel-exact Selection Mask Overlay + Organic Contour
    if (u.sel_info.x > 0.5) {
        let sel_m = textureSample(sel_tex, doc_sampler, doc_uv).r;
        let sel_step = vec2<f32>(1.0, 1.0) / max(u.sel_info.yz, vec2<f32>(1.0, 1.0));
        let m_l = textureSample(sel_tex, doc_sampler, clamp(doc_uv - vec2<f32>(sel_step.x, 0.0), vec2<f32>(0.0), vec2<f32>(1.0))).r;
        let m_r = textureSample(sel_tex, doc_sampler, clamp(doc_uv + vec2<f32>(sel_step.x, 0.0), vec2<f32>(0.0), vec2<f32>(1.0))).r;
        let m_u = textureSample(sel_tex, doc_sampler, clamp(doc_uv - vec2<f32>(0.0, sel_step.y), vec2<f32>(0.0), vec2<f32>(1.0))).r;
        let m_d = textureSample(sel_tex, doc_sampler, clamp(doc_uv + vec2<f32>(0.0, sel_step.y), vec2<f32>(0.0), vec2<f32>(1.0))).r;
        let is_in = sel_m > 0.35;
        let is_edge = is_in && (m_l <= 0.35 || m_r <= 0.35 || m_u <= 0.35 || m_d <= 0.35);
        if (is_edge) {
            let stripe = (i32(floor(in.screen_px.x + in.screen_px.y)) / 5) & 1;
            rgb = select(vec3<f32>(0.05, 0.08, 0.14), vec3<f32>(1.0, 1.0, 1.0), stripe == 0);
        } else if (is_in) {
            rgb = mix(rgb, vec3<f32>(0.22, 0.62, 1.0), 0.30 * sel_m);
        }
    }

    return vec4<f32>(rgb, 1.0);
}
