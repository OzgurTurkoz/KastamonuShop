//! Zero-copy Vulkan (GLES fallback) GPU Renderer binding `photocraft_gpu::Compositor`
//! directly to an Android `SurfaceView` (`ANativeWindow`) with incremental dirty-rect updates,
//! mobile-tuned 128 MB GPU memory budget, non-blocking cached-texture present, and 60 FPS GPU previews.

use bytemuck::{Pod, Zeroable};
use photocraft_doc::Document;
use photocraft_geom::Rect;
use photocraft_gpu::Compositor;

#[derive(Clone, Copy, Debug)]
pub struct ViewportCamera {
    pub pan_x: f32,
    pub pan_y: f32,
    pub zoom: f32,
    pub rotation_rad: f32,
}

impl Default for ViewportCamera {
    fn default() -> Self {
        Self {
            pan_x: 0.0,
            pan_y: 0.0,
            zoom: 0.5,
            rotation_rad: 0.0,
        }
    }
}

#[derive(Clone, Copy, Debug)]
pub struct GpuPreviewState {
    pub mirror_mode: i32,
    pub mirror_axis: f32,
    pub flip_x: bool,
    pub flip_y: bool,
    pub brightness: f32,
    pub contrast: f32,
    pub saturation: f32,
    pub hue: f32,
    pub blur_radius: f32,
    pub sharpen_amount: f32,
}

impl Default for GpuPreviewState {
    fn default() -> Self {
        Self {
            mirror_mode: 0,
            mirror_axis: 0.5,
            flip_x: false,
            flip_y: false,
            brightness: 0.0,
            contrast: 0.0,
            saturation: 0.0,
            hue: 0.0,
            blur_radius: 0.0,
            sharpen_amount: 0.0,
        }
    }
}

#[repr(C)]
#[derive(Clone, Copy, Pod, Zeroable)]
struct ViewportUniforms {
    dims: [f32; 4],
    camera: [f32; 4],
    style: [f32; 4],
    preview: [f32; 4],
    adjust: [f32; 4],
    filter_p: [f32; 4],
}

pub struct VulkanRenderer {
    _instance: wgpu::Instance,
    surface: wgpu::Surface<'static>,
    device: wgpu::Device,
    queue: wgpu::Queue,
    config: wgpu::SurfaceConfiguration,
    compositor: Compositor,
    blit_pipeline: wgpu::RenderPipeline,
    blit_bind_group_layout: wgpu::BindGroupLayout,
    sampler: wgpu::Sampler,
    uniform_buf: wgpu::Buffer,
    doc_texture: Option<wgpu::Texture>,
    cached_blit_bg: Option<wgpu::BindGroup>,
    doc_tex_size: (u32, u32),
    last_rendered_gen: u64,
}

impl VulkanRenderer {
    pub fn new(
        target: wgpu::SurfaceTargetUnsafe,
        width: u32,
        height: u32,
    ) -> Result<Self, String> {
        let instance = wgpu::Instance::new(wgpu::InstanceDescriptor {
            backends: wgpu::Backends::VULKAN | wgpu::Backends::GL,
            ..wgpu::InstanceDescriptor::new_without_display_handle()
        });

        let surface = unsafe {
            instance
                .create_surface_unsafe(target)
                .map_err(|e| format!("Failed to create wgpu surface: {e}"))?
        };

        let adapter = pollster::block_on(instance.request_adapter(&wgpu::RequestAdapterOptions {
            power_preference: wgpu::PowerPreference::HighPerformance,
            compatible_surface: Some(&surface),
            force_fallback_adapter: false,
            ..Default::default()
        }))
        .map_err(|e| format!("No compatible Vulkan/GLES GPU adapter: {e}"))?;

        log::info!(
            "StonebridgeCraft GPU Adapter: {} ({:?})",
            adapter.get_info().name,
            adapter.get_info().backend
        );

        let (device, queue) = pollster::block_on(adapter.request_device(&wgpu::DeviceDescriptor {
            label: Some("stonebridgecraft_gpu"),
            required_features: wgpu::Features::empty(),
            required_limits: adapter.limits(),
            ..Default::default()
        }))
        .map_err(|e| format!("Failed to request GPU device: {e}"))?;

        let caps = surface.get_capabilities(&adapter);
        let format = caps
            .formats
            .iter()
            .copied()
            .find(|f| !f.is_srgb())
            .or_else(|| caps.formats.first().copied())
            .ok_or_else(|| "Surface reported zero supported formats".to_string())?;

        let present_mode = if caps.present_modes.contains(&wgpu::PresentMode::Mailbox) {
            wgpu::PresentMode::Mailbox
        } else {
            wgpu::PresentMode::Fifo
        };

        let mut config = surface
            .get_default_config(&adapter, width.max(1), height.max(1))
            .ok_or_else(|| "Surface not supported by adapter".to_string())?;
        config.format = format;
        config.present_mode = present_mode;
        config.alpha_mode = caps
            .alpha_modes
            .first()
            .copied()
            .unwrap_or(wgpu::CompositeAlphaMode::Auto);
        config.desired_maximum_frame_latency = 2;
        surface.configure(&device, &config);

        let acc_format = Compositor::preferred_acc_format(&adapter);
        let mut compositor = Compositor::try_new_with_format(&device, acc_format)
            .map_err(|e| format!("Compositor init failed ({acc_format:?}): {e:?}"))?;
        // Mobile-safe GPU cache budget (128 MB instead of desktop 1+ GB) to prevent Android LMK crashes
        compositor.set_memory_budget(128 << 20);

        let shader = device.create_shader_module(wgpu::ShaderModuleDescriptor {
            label: Some("stonebridgecraft_blit_wgsl"),
            source: wgpu::ShaderSource::Wgsl(include_str!("blit.wgsl").into()),
        });

        let blit_bind_group_layout =
            device.create_bind_group_layout(&wgpu::BindGroupLayoutDescriptor {
                label: Some("blit_bgl"),
                entries: &[
                    wgpu::BindGroupLayoutEntry {
                        binding: 0,
                        visibility: wgpu::ShaderStages::VERTEX_FRAGMENT,
                        ty: wgpu::BindingType::Buffer {
                            ty: wgpu::BufferBindingType::Uniform,
                            has_dynamic_offset: false,
                            min_binding_size: None,
                        },
                        count: None,
                    },
                    wgpu::BindGroupLayoutEntry {
                        binding: 1,
                        visibility: wgpu::ShaderStages::FRAGMENT,
                        ty: wgpu::BindingType::Texture {
                            sample_type: wgpu::TextureSampleType::Float { filterable: false },
                            view_dimension: wgpu::TextureViewDimension::D2,
                            multisampled: false,
                        },
                        count: None,
                    },
                    wgpu::BindGroupLayoutEntry {
                        binding: 2,
                        visibility: wgpu::ShaderStages::FRAGMENT,
                        ty: wgpu::BindingType::Sampler(wgpu::SamplerBindingType::NonFiltering),
                        count: None,
                    },
                ],
            });

        let pipeline_layout = device.create_pipeline_layout(&wgpu::PipelineLayoutDescriptor {
            label: Some("blit_pipeline_layout"),
            bind_group_layouts: &[Some(&blit_bind_group_layout)],
            immediate_size: 0,
        });

        let blit_pipeline = device.create_render_pipeline(&wgpu::RenderPipelineDescriptor {
            label: Some("stonebridgecraft_blit_pipeline"),
            layout: Some(&pipeline_layout),
            vertex: wgpu::VertexState {
                module: &shader,
                entry_point: Some("vs_main"),
                buffers: &[],
                compilation_options: Default::default(),
            },
            fragment: Some(wgpu::FragmentState {
                module: &shader,
                entry_point: Some("fs_main"),
                targets: &[Some(wgpu::ColorTargetState {
                    format: config.format,
                    blend: Some(wgpu::BlendState::REPLACE),
                    write_mask: wgpu::ColorWrites::ALL,
                })],
                compilation_options: Default::default(),
            }),
            primitive: wgpu::PrimitiveState::default(),
            depth_stencil: None,
            multisample: wgpu::MultisampleState::default(),
            multiview_mask: None,
            cache: None,
        });

        let sampler = device.create_sampler(&wgpu::SamplerDescriptor {
            label: Some("doc_nearest_sampler"),
            mag_filter: wgpu::FilterMode::Nearest,
            min_filter: wgpu::FilterMode::Nearest,
            ..Default::default()
        });

        let initial_uniforms = ViewportUniforms {
            dims: [config.width as f32, config.height as f32, 1.0, 1.0],
            camera: [0.0, 0.0, 1.0, 0.0],
            style: [16.0, 0.10, 0.10, 0.12],
            preview: [0.0, 0.5, 0.0, 0.0],
            adjust: [0.0, 0.0, 0.0, 0.0],
            filter_p: [0.0, 0.0, 0.0, 0.0],
        };
        let uniform_buf = device.create_buffer(&wgpu::BufferDescriptor {
            label: Some("viewport_uniforms"),
            size: std::mem::size_of::<ViewportUniforms>() as u64,
            usage: wgpu::BufferUsages::UNIFORM | wgpu::BufferUsages::COPY_DST,
            mapped_at_creation: false,
        });
        queue.write_buffer(&uniform_buf, 0, bytemuck::bytes_of(&initial_uniforms));

        Ok(Self {
            _instance: instance,
            surface,
            device,
            queue,
            config,
            compositor,
            blit_pipeline,
            blit_bind_group_layout,
            sampler,
            uniform_buf,
            doc_texture: None,
            cached_blit_bg: None,
            doc_tex_size: (0, 0),
            last_rendered_gen: 0,
        })
    }

    pub fn resize(&mut self, width: u32, height: u32) {
        if width == 0 || height == 0 {
            return;
        }
        self.config.width = width;
        self.config.height = height;
        self.surface.configure(&self.device, &self.config);
    }

    /// Blits the already-cached GPU `doc_texture` with updated camera/preview uniforms in <0.2ms,
    /// without touching the CPU engine (prevents UI-thread freezes/ANRs during heavy background work).
    pub fn present_cached_only(
        &mut self,
        camera: ViewportCamera,
        preview: GpuPreviewState,
    ) -> Result<(), String> {
        let Some(bind_group) = &self.cached_blit_bg else {
            return Ok(());
        };
        let frame = match self.surface.get_current_texture() {
            wgpu::CurrentSurfaceTexture::Success(f)
            | wgpu::CurrentSurfaceTexture::Suboptimal(f) => f,
            wgpu::CurrentSurfaceTexture::Lost | wgpu::CurrentSurfaceTexture::Outdated => {
                self.surface.configure(&self.device, &self.config);
                return Ok(());
            }
            wgpu::CurrentSurfaceTexture::Timeout | wgpu::CurrentSurfaceTexture::Occluded => {
                return Ok(());
            }
            other => return Err(format!("Swapchain error: {other:?}")),
        };

        let uniforms = ViewportUniforms {
            dims: [
                self.config.width as f32,
                self.config.height as f32,
                self.doc_tex_size.0 as f32,
                self.doc_tex_size.1 as f32,
            ],
            camera: [camera.pan_x, camera.pan_y, camera.zoom, camera.rotation_rad],
            style: [16.0, 0.10, 0.10, 0.12],
            preview: [
                preview.mirror_mode as f32,
                preview.mirror_axis.clamp(0.05, 0.95),
                if preview.flip_x { 1.0 } else { 0.0 },
                if preview.flip_y { 1.0 } else { 0.0 },
            ],
            adjust: [
                preview.brightness,
                preview.contrast,
                preview.saturation,
                preview.hue,
            ],
            filter_p: [preview.blur_radius, preview.sharpen_amount, 0.0, 0.0],
        };
        self.queue
            .write_buffer(&self.uniform_buf, 0, bytemuck::bytes_of(&uniforms));

        let swap_view = frame
            .texture
            .create_view(&wgpu::TextureViewDescriptor::default());
        let mut encoder = self
            .device
            .create_command_encoder(&wgpu::CommandEncoderDescriptor {
                label: Some("stonebridgecraft_present"),
            });
        {
            let mut rpass = encoder.begin_render_pass(&wgpu::RenderPassDescriptor {
                label: Some("stonebridgecraft_blit_pass"),
                color_attachments: &[Some(wgpu::RenderPassColorAttachment {
                    view: &swap_view,
                    resolve_target: None,
                    depth_slice: None,
                    ops: wgpu::Operations {
                        load: wgpu::LoadOp::Clear(wgpu::Color {
                            r: 0.10,
                            g: 0.10,
                            b: 0.12,
                            a: 1.0,
                        }),
                        store: wgpu::StoreOp::Store,
                    },
                })],
                depth_stencil_attachment: None,
                timestamp_writes: None,
                occlusion_query_set: None,
                multiview_mask: None,
            });
            rpass.set_pipeline(&self.blit_pipeline);
            rpass.set_bind_group(0, bind_group, &[]);
            rpass.draw(0..3, 0..1);
        }

        self.queue.submit([encoder.finish()]);
        self.queue.present(frame);
        Ok(())
    }

    pub fn render_frame(
        &mut self,
        doc: &Document,
        generation: u64,
        force_full: bool,
        dirty_rect: Option<Rect>,
        camera: ViewportCamera,
        preview: GpuPreviewState,
    ) -> Result<(), String> {
        let doc_w = doc.size.width.max(1);
        let doc_h = doc.size.height.max(1);
        let mut need_full = force_full || self.doc_texture.is_none();

        if self.doc_tex_size != (doc_w, doc_h) || self.doc_texture.is_none() {
            let max_dim = self.device.limits().max_texture_dimension_2d.min(4096);
            let tex_w = doc_w.min(max_dim);
            let tex_h = doc_h.min(max_dim);
            let tex = self.device.create_texture(&wgpu::TextureDescriptor {
                label: Some("stonebridgecraft_doc_composite"),
                size: wgpu::Extent3d {
                    width: tex_w,
                    height: tex_h,
                    depth_or_array_layers: 1,
                },
                mip_level_count: 1,
                sample_count: 1,
                dimension: wgpu::TextureDimension::D2,
                format: self.compositor.acc_format(),
                usage: wgpu::TextureUsages::COPY_DST | wgpu::TextureUsages::TEXTURE_BINDING,
                view_formats: &[],
            });
            let doc_view = tex.create_view(&wgpu::TextureViewDescriptor::default());
            let bg = self.device.create_bind_group(&wgpu::BindGroupDescriptor {
                label: Some("cached_blit_bg"),
                layout: &self.blit_bind_group_layout,
                entries: &[
                    wgpu::BindGroupEntry {
                        binding: 0,
                        resource: self.uniform_buf.as_entire_binding(),
                    },
                    wgpu::BindGroupEntry {
                        binding: 1,
                        resource: wgpu::BindingResource::TextureView(&doc_view),
                    },
                    wgpu::BindGroupEntry {
                        binding: 2,
                        resource: wgpu::BindingResource::Sampler(&self.sampler),
                    },
                ],
            });
            self.doc_texture = Some(tex);
            self.cached_blit_bg = Some(bg);
            self.doc_tex_size = (tex_w, tex_h);
            self.last_rendered_gen = 0;
            need_full = true;
        }

        if self.last_rendered_gen != generation || need_full {
            if let Some(target_tex) = &self.doc_texture {
                let bounds = doc.bounds();
                let region = if need_full {
                    bounds
                } else {
                    match dirty_rect {
                        Some(r) if !r.is_empty() => r.intersect(&bounds),
                        _ => bounds,
                    }
                };
                if !region.is_empty() {
                    let (tex_w, tex_h) = self.doc_tex_size;
                    let _ = self.compositor.render(
                        &self.device,
                        &self.queue,
                        doc,
                        region,
                        |encoder, chunk| {
                            let dst_x = chunk.rect.x0.max(0) as u32;
                            let dst_y = chunk.rect.y0.max(0) as u32;
                            let copy_w = (chunk.rect.width() as u32)
                                .min(tex_w.saturating_sub(dst_x));
                            let copy_h = (chunk.rect.height() as u32)
                                .min(tex_h.saturating_sub(dst_y));
                            if copy_w == 0 || copy_h == 0 {
                                return;
                            }
                            encoder.copy_texture_to_texture(
                                wgpu::TexelCopyTextureInfo {
                                    texture: chunk.texture,
                                    mip_level: 0,
                                    origin: wgpu::Origin3d::ZERO,
                                    aspect: wgpu::TextureAspect::All,
                                },
                                wgpu::TexelCopyTextureInfo {
                                    texture: target_tex,
                                    mip_level: 0,
                                    origin: wgpu::Origin3d {
                                        x: dst_x,
                                        y: dst_y,
                                        z: 0,
                                    },
                                    aspect: wgpu::TextureAspect::All,
                                },
                                wgpu::Extent3d {
                                    width: copy_w,
                                    height: copy_h,
                                    depth_or_array_layers: 1,
                                },
                            );
                        },
                    );
                }
                self.last_rendered_gen = generation;
            }
        }

        self.present_cached_only(camera, preview)
    }
}
