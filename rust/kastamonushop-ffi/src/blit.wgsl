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
};

@group(0) @binding(0)
var<uniform> u: ViewportUniforms;

@group(0) @binding(1)
var doc_tex: texture_2d<f32>;

@group(0) @binding(2)
var doc_sampler: sampler;

struct VertexOut {
    @builtin(position) clip_pos: vec4<f32>,
    @location(0) screen_px: vec2<f32>,
};

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

    let rgb = mix(checker, col, sampled.a);
    return vec4<f32>(rgb, 1.0);
}
