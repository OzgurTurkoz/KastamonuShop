// KastamonuShop — Hardware-accelerated Canvas Blit & Viewport Shader (WGSL)
// Renders PhotoCraft's GPU-composited RGBA texture onto Android's Vulkan/GLES SurfaceView
// with smooth pan, zoom, rotation, and transparency checkerboard.

struct ViewportUniforms {
    // [canvas_width, canvas_height, doc_width, doc_height]
    dims: vec4<f32>,
    // [pan_x, pan_y, zoom, rotation_rad]
    camera: vec4<f32>,
    // [checker_size_px, bg_r, bg_g, bg_b]
    style: vec4<f32>,
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

@vertex
fn vs_main(@builtin(vertex_index) idx: u32) -> VertexOut {
    // Full-screen triangle (3 vertices, zero vertex buffer overhead)
    var pos = array<vec2<f32>, 3>(
        vec2<f32>(-1.0, -3.0),
        vec2<f32>(-1.0,  1.0),
        vec2<f32>( 3.0,  1.0),
    );
    let ndc = pos[idx];
    var out: VertexOut;
    out.clip_pos = vec4<f32>(ndc, 0.0, 1.0);
    // Convert NDC to screen pixel coordinates (top-left origin)
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

    // Screen pixel -> centered offset -> undo pan -> undo rotation -> undo zoom -> doc space
    let p = in.screen_px - canvas_center - pan;
    let c = cos(-rot);
    let s = sin(-rot);
    let rotated = vec2<f32>(p.x * c - p.y * s, p.x * s + p.y * c);
    let doc_px = rotated / zoom + doc_size * 0.5;

    // Outside document bounds: draw studio workspace background + subtle drop shadow
    if (doc_px.x < 0.0 || doc_px.y < 0.0 || doc_px.x > doc_size.x || doc_px.y > doc_size.y) {
        return vec4<f32>(u.style.y, u.style.z, u.style.w, 1.0);
    }

    // Transparency checkerboard in screen space so it stays crisp at every zoom level
    let cell = floor(in.screen_px / max(u.style.x, 4.0));
    let is_light = ((i32(cell.x) + i32(cell.y)) & 1) == 0;
    let checker = select(vec3<f32>(0.76, 0.76, 0.76), vec3<f32>(0.90, 0.90, 0.90), is_light);

    let doc_uv = doc_px / doc_size;
    let sampled = textureSample(doc_tex, doc_sampler, doc_uv);

    // Straight-alpha composite over checkerboard
    let rgb = mix(checker, sampled.rgb, sampled.a);
    return vec4<f32>(rgb, 1.0);
}
