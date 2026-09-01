//! An offscreen wgpu pass that draws one posed frame with a transparent
//! background, for both the live preview and the baked sheet.
//!
//! The two share a pipeline on purpose. A preview drawn by different code from
//! the thing it is previewing is a preview that can lie, and the whole point of
//! this tool is that what you line up is what gets saved.

use crate::model::{Model, Vertex};
use eframe::wgpu;
use glam::{Mat4, Vec3};

#[repr(C)]
#[derive(Copy, Clone, bytemuck::Pod, bytemuck::Zeroable)]
struct Globals {
    view_proj: [[f32; 4]; 4],
    /// xyz = direction toward the light, w = diffuse strength.
    light: [f32; 4],
    /// x = ambient, y = gamma, z = brightness, w = contrast.
    grade: [f32; 4],
    /// x = filtering mode: 0 crisp, 1 PS2. The rest is spare.
    opts: [f32; 4],
    /// x = shadows, y = highlights. Both signed: lift or crush.
    tone: [f32; 4],
}

pub struct Renderer {
    pipeline: wgpu::RenderPipeline,
    layout: wgpu::BindGroupLayout,
    vbuf: wgpu::Buffer,
    ibuf: wgpu::Buffer,
    index_count: u32,
    globals: wgpu::Buffer,
    joints: wgpu::Buffer,
    joint_capacity: usize,
    /// Shared: globals and the joint matrices.
    bind: wgpu::BindGroup,
    /// One per image, plus a white fallback at the end for untextured parts.
    materials: Vec<wgpu::BindGroup>,
    fallback: usize,
    parts: Vec<(u32, u32, usize)>,
    /// The colour target, recreated when the frame size changes.
    pub target: wgpu::Texture,
    pub view: wgpu::TextureView,
    depth: wgpu::TextureView,
    pub size: (u32, u32),
}

pub const FORMAT: wgpu::TextureFormat = wgpu::TextureFormat::Rgba8UnormSrgb;

impl Renderer {
    pub fn new(device: &wgpu::Device, queue: &wgpu::Queue, model: &Model, size: (u32, u32)) -> Renderer {
        use wgpu::util::DeviceExt;

        let vbuf = device.create_buffer_init(&wgpu::util::BufferInitDescriptor {
            label: Some("verts"),
            contents: bytemuck::cast_slice(&model.vertices),
            usage: wgpu::BufferUsages::VERTEX,
        });
        let ibuf = device.create_buffer_init(&wgpu::util::BufferInitDescriptor {
            label: Some("indices"),
            contents: bytemuck::cast_slice(&model.indices),
            usage: wgpu::BufferUsages::INDEX,
        });

        let joint_capacity = model.joints.len().max(1);
        let joints = device.create_buffer(&wgpu::BufferDescriptor {
            label: Some("joints"),
            size: (joint_capacity * 64) as u64,
            usage: wgpu::BufferUsages::STORAGE | wgpu::BufferUsages::COPY_DST,
            mapped_at_creation: false,
        });
        let globals = device.create_buffer(&wgpu::BufferDescriptor {
            label: Some("globals"),
            size: std::mem::size_of::<Globals>() as u64,
            usage: wgpu::BufferUsages::UNIFORM | wgpu::BufferUsages::COPY_DST,
            mapped_at_creation: false,
        });

        // Every image in the file becomes its own view, and a flat white one is
        // appended for parts whose material carries no texture at all.
        let mut views: Vec<wgpu::TextureView> = Vec::new();
        let mut sources: Vec<(u32, u32, Vec<u8>)> = model.images.clone();
        sources.push((1, 1, vec![255, 255, 255, 255]));
        for (tw, th, pixels) in &sources {
            let (tw, th) = ((*tw).max(1), (*th).max(1));
            let tex = device.create_texture(&wgpu::TextureDescriptor {
                label: Some("albedo"),
                size: wgpu::Extent3d { width: tw, height: th, depth_or_array_layers: 1 },
                mip_level_count: 1,
                sample_count: 1,
                dimension: wgpu::TextureDimension::D2,
                format: wgpu::TextureFormat::Rgba8UnormSrgb,
                usage: wgpu::TextureUsages::TEXTURE_BINDING | wgpu::TextureUsages::COPY_DST,
                view_formats: &[],
            });
            queue.write_texture(
                wgpu::TexelCopyTextureInfo {
                    texture: &tex,
                    mip_level: 0,
                    origin: wgpu::Origin3d::ZERO,
                    aspect: wgpu::TextureAspect::All,
                },
                pixels,
                wgpu::TexelCopyBufferLayout {
                    offset: 0,
                    bytes_per_row: Some(tw * 4),
                    rows_per_image: Some(th),
                },
                wgpu::Extent3d { width: tw, height: th, depth_or_array_layers: 1 },
            );
            views.push(tex.create_view(&wgpu::TextureViewDescriptor::default()));
        }
        let fallback = views.len() - 1;
        // LINEAR in both directions, and the shader decides what that means.
        //
        // A crisp result is got by sampling the exact texel CENTRE, which makes
        // a linear sampler behave as a nearest one -- so one sampler serves both
        // modes and switching does not mean rebuilding every bind group.
        let sampler = device.create_sampler(&wgpu::SamplerDescriptor {
            mag_filter: wgpu::FilterMode::Linear,
            min_filter: wgpu::FilterMode::Linear,
            ..Default::default()
        });

        let layout = device.create_bind_group_layout(&wgpu::BindGroupLayoutDescriptor {
            label: Some("shared layout"),
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
                    visibility: wgpu::ShaderStages::VERTEX,
                    ty: wgpu::BindingType::Buffer {
                        ty: wgpu::BufferBindingType::Storage { read_only: true },
                        has_dynamic_offset: false,
                        min_binding_size: None,
                    },
                    count: None,
                },
            ],
        });
        // Group 1 changes per part; group 0 does not. Splitting them is what
        // lets one draw call per material share one set of joint matrices.
        let mat_layout = device.create_bind_group_layout(&wgpu::BindGroupLayoutDescriptor {
            label: Some("material layout"),
            entries: &[
                wgpu::BindGroupLayoutEntry {
                    binding: 0,
                    visibility: wgpu::ShaderStages::FRAGMENT,
                    ty: wgpu::BindingType::Texture {
                        sample_type: wgpu::TextureSampleType::Float { filterable: true },
                        view_dimension: wgpu::TextureViewDimension::D2,
                        multisampled: false,
                    },
                    count: None,
                },
                wgpu::BindGroupLayoutEntry {
                    binding: 1,
                    visibility: wgpu::ShaderStages::FRAGMENT,
                    ty: wgpu::BindingType::Sampler(wgpu::SamplerBindingType::Filtering),
                    count: None,
                },
            ],
        });

        let bind = device.create_bind_group(&wgpu::BindGroupDescriptor {
            label: Some("shared"),
            layout: &layout,
            entries: &[
                wgpu::BindGroupEntry { binding: 0, resource: globals.as_entire_binding() },
                wgpu::BindGroupEntry { binding: 1, resource: joints.as_entire_binding() },
            ],
        });
        let materials: Vec<wgpu::BindGroup> = views
            .iter()
            .map(|view| {
                device.create_bind_group(&wgpu::BindGroupDescriptor {
                    label: Some("material"),
                    layout: &mat_layout,
                    entries: &[
                        wgpu::BindGroupEntry {
                            binding: 0,
                            resource: wgpu::BindingResource::TextureView(view),
                        },
                        wgpu::BindGroupEntry {
                            binding: 1,
                            resource: wgpu::BindingResource::Sampler(&sampler),
                        },
                    ],
                })
            })
            .collect();
        let parts: Vec<(u32, u32, usize)> = model
            .parts
            .iter()
            .map(|p| (p.start, p.count, p.image.unwrap_or(fallback)))
            .collect();

        let shader = device.create_shader_module(wgpu::ShaderModuleDescriptor {
            label: Some("skin"),
            source: wgpu::ShaderSource::Wgsl(SHADER.into()),
        });
        let pipeline_layout = device.create_pipeline_layout(&wgpu::PipelineLayoutDescriptor {
            label: Some("pl"),
            // wgpu 30: the slots are optional, and push constants became
            // `immediate_size`.
            bind_group_layouts: &[Some(&layout), Some(&mat_layout)],
            immediate_size: 0,
        });
        let pipeline = device.create_render_pipeline(&wgpu::RenderPipelineDescriptor {
            label: Some("skin pipeline"),
            layout: Some(&pipeline_layout),
            vertex: wgpu::VertexState {
                module: &shader,
                entry_point: Some("vs"),
                buffers: &[Some(wgpu::VertexBufferLayout {
                    array_stride: std::mem::size_of::<Vertex>() as u64,
                    step_mode: wgpu::VertexStepMode::Vertex,
                    attributes: &wgpu::vertex_attr_array![
                        0 => Float32x3, 1 => Float32x3, 2 => Float32x2,
                        3 => Uint32x4,  4 => Float32x4],
                })],
                compilation_options: Default::default(),
            },
            fragment: Some(wgpu::FragmentState {
                module: &shader,
                entry_point: Some("fs"),
                targets: &[Some(wgpu::ColorTargetState {
                    format: FORMAT,
                    blend: Some(wgpu::BlendState::ALPHA_BLENDING),
                    write_mask: wgpu::ColorWrites::ALL,
                })],
                compilation_options: Default::default(),
            }),
            primitive: wgpu::PrimitiveState {
                // No culling. These rips are wound inconsistently and a sprite
                // with holes in it is worse than one drawn twice.
                cull_mode: None,
                ..Default::default()
            },
            depth_stencil: Some(wgpu::DepthStencilState {
                format: wgpu::TextureFormat::Depth32Float,
                depth_write_enabled: Some(true),
                depth_compare: Some(wgpu::CompareFunction::Less),
                stencil: Default::default(),
                bias: Default::default(),
            }),
            multisample: Default::default(),
            multiview_mask: None,
            cache: None,
        });

        let (target, view, depth) = make_targets(device, size);
        Renderer {
            pipeline,
            layout,
            vbuf,
            ibuf,
            index_count: model.indices.len() as u32,
            globals,
            joints,
            joint_capacity,
            bind,
            materials,
            fallback,
            parts,
            target,
            view,
            depth,
            size,
        }
    }

    pub fn resize(&mut self, device: &wgpu::Device, size: (u32, u32)) {
        if size == self.size || size.0 == 0 || size.1 == 0 {
            return;
        }
        let (t, v, d) = make_targets(device, size);
        self.target = t;
        self.view = v;
        self.depth = d;
        self.size = size;
    }

    /// Draws one frame. `view_proj` already contains the camera and rotation.
    pub fn draw(
        &mut self,
        device: &wgpu::Device,
        queue: &wgpu::Queue,
        joints: &[Mat4],
        view_proj: Mat4,
        light: [f32; 4],
        grade: [f32; 4],
        ps2: bool,
        tone: [f32; 4],
        clear: wgpu::Color,
    ) {
        let mut flat: Vec<[[f32; 4]; 4]> =
            joints.iter().map(|m| m.to_cols_array_2d()).collect();
        flat.resize(self.joint_capacity, Mat4::IDENTITY.to_cols_array_2d());
        queue.write_buffer(&self.joints, 0, bytemuck::cast_slice(&flat));
        queue.write_buffer(
            &self.globals,
            0,
            bytemuck::bytes_of(&Globals {
                view_proj: view_proj.to_cols_array_2d(),
                light,
                grade,
                opts: [if ps2 { 1.0 } else { 0.0 }, 0.0, 0.0, 0.0],
                tone,
            }),
        );

        let mut enc = device.create_command_encoder(&wgpu::CommandEncoderDescriptor {
            label: Some("bake"),
        });
        {
            let mut pass = enc.begin_render_pass(&wgpu::RenderPassDescriptor {
                label: Some("pass"),
                color_attachments: &[Some(wgpu::RenderPassColorAttachment {
                    view: &self.view,
                    resolve_target: None,
                    depth_slice: None,
                    ops: wgpu::Operations {
                        // Transparent by default -- a sprite sheet's background
                        // has to be absent rather than a colour to key out --
                        // but a solid one is offered because some viewers and
                        // sites render a transparent GIF badly or not at all.
                        load: wgpu::LoadOp::Clear(clear),
                        store: wgpu::StoreOp::Store,
                    },
                })],
                depth_stencil_attachment: Some(wgpu::RenderPassDepthStencilAttachment {
                    view: &self.depth,
                    depth_ops: Some(wgpu::Operations {
                        load: wgpu::LoadOp::Clear(1.0),
                        store: wgpu::StoreOp::Store,
                    }),
                    stencil_ops: None,
                }),
                timestamp_writes: None,
                occlusion_query_set: None,
                multiview_mask: None,
            });
            pass.set_pipeline(&self.pipeline);
            pass.set_bind_group(0, &self.bind, &[]);
            pass.set_vertex_buffer(0, self.vbuf.slice(..));
            pass.set_index_buffer(self.ibuf.slice(..), wgpu::IndexFormat::Uint32);
            if self.parts.is_empty() {
                pass.set_bind_group(1, &self.materials[self.fallback], &[]);
                pass.draw_indexed(0..self.index_count, 0, 0..1);
            } else {
                for (start, count, image) in &self.parts {
                    let group = self.materials.get(*image).unwrap_or(
                        &self.materials[self.fallback],
                    );
                    pass.set_bind_group(1, group, &[]);
                    pass.draw_indexed(*start..*start + *count, 0, 0..1);
                }
            }
        }
        queue.submit(Some(enc.finish()));
    }

    /// The last drawn frame as RGBA8 rows, for writing into a sheet.
    pub fn read_back(&self, device: &wgpu::Device, queue: &wgpu::Queue) -> Vec<u8> {
        let (w, h) = self.size;
        let unpadded = w * 4;
        let align = wgpu::COPY_BYTES_PER_ROW_ALIGNMENT;
        let padded = unpadded.div_ceil(align) * align;
        let buffer = device.create_buffer(&wgpu::BufferDescriptor {
            label: Some("readback"),
            size: (padded * h) as u64,
            usage: wgpu::BufferUsages::COPY_DST | wgpu::BufferUsages::MAP_READ,
            mapped_at_creation: false,
        });
        let mut enc = device.create_command_encoder(&Default::default());
        enc.copy_texture_to_buffer(
            wgpu::TexelCopyTextureInfo {
                texture: &self.target,
                mip_level: 0,
                origin: wgpu::Origin3d::ZERO,
                aspect: wgpu::TextureAspect::All,
            },
            wgpu::TexelCopyBufferInfo {
                buffer: &buffer,
                layout: wgpu::TexelCopyBufferLayout {
                    offset: 0,
                    bytes_per_row: Some(padded),
                    rows_per_image: Some(h),
                },
            },
            wgpu::Extent3d { width: w, height: h, depth_or_array_layers: 1 },
        );
        queue.submit(Some(enc.finish()));

        let slice = buffer.slice(..);
        slice.map_async(wgpu::MapMode::Read, |_| {});
        // Blocking is the point: the sheet is written from these pixels, so
        // the copy has to have landed before they are read.
        let _ = device.poll(wgpu::PollType::Wait {
            submission_index: None,
            timeout: None,
        });
        let data = slice.get_mapped_range().expect("readback mapping failed");
        let mut out = Vec::with_capacity((unpadded * h) as usize);
        for row in 0..h {
            let start = (row * padded) as usize;
            out.extend_from_slice(&data[start..start + unpadded as usize]);
        }
        drop(data);
        buffer.unmap();
        out
    }
}

fn make_targets(
    device: &wgpu::Device,
    size: (u32, u32),
) -> (wgpu::Texture, wgpu::TextureView, wgpu::TextureView) {
    let extent = wgpu::Extent3d {
        width: size.0.max(1),
        height: size.1.max(1),
        depth_or_array_layers: 1,
    };
    let target = device.create_texture(&wgpu::TextureDescriptor {
        label: Some("frame"),
        size: extent,
        mip_level_count: 1,
        sample_count: 1,
        dimension: wgpu::TextureDimension::D2,
        format: FORMAT,
        usage: wgpu::TextureUsages::RENDER_ATTACHMENT
            | wgpu::TextureUsages::TEXTURE_BINDING
            | wgpu::TextureUsages::COPY_SRC,
        view_formats: &[],
    });
    let view = target.create_view(&Default::default());
    let depth = device
        .create_texture(&wgpu::TextureDescriptor {
            label: Some("depth"),
            size: extent,
            mip_level_count: 1,
            sample_count: 1,
            dimension: wgpu::TextureDimension::D2,
            format: wgpu::TextureFormat::Depth32Float,
            usage: wgpu::TextureUsages::RENDER_ATTACHMENT,
            view_formats: &[],
        })
        .create_view(&Default::default());
    (target, view, depth)
}

/// Camera looking at `centre` from `distance`, turned by the three angles.
/// `off` shifts the subject within the frame, in units of the model's radius:
/// `off.x` right, `off.y` up. Radius-relative for the same reason `distance`
/// is -- a nudge that works on Slifer should be the same nudge on a 150-unit
/// Obelisk, and a pixel- or world-unit offset would not be.
///
/// It is applied as a PAN: eye and target move together, so the view direction
/// is untouched. Moving the target alone would swing the camera and shear the
/// subject's perspective, which looks like the model deforming as you drag.
/// Translating the model instead would move it out of the bounds `framing()`
/// measured, so the auto-fit would fight the adjustment.
pub fn view_proj(
    centre: Vec3,
    radius: f32,
    distance: f32,
    yaw: f32,
    pitch: f32,
    roll: f32,
    off: (f32, f32),
    aspect: f32,
) -> Mat4 {
    let d = radius * distance;
    let (sy, cy) = yaw.to_radians().sin_cos();
    let (sp, cp) = pitch.to_radians().sin_cos();
    let eye = centre + Vec3::new(cp * sy, sp, cp * cy) * d;
    // Roll turns the picture, so it goes on the camera's up vector rather than
    // on the model -- rotating the model would move it out of its own frame.
    let forward = (centre - eye).normalize_or_zero();
    let right = forward.cross(Vec3::Y).normalize_or_zero();
    let up_base = right.cross(forward).normalize_or_zero();
    let (sr, cr) = roll.to_radians().sin_cos();
    let up = (up_base * cr + right * sr).normalize_or_zero();
    // Negated so the SUBJECT goes where the control says: sliding the camera
    // left puts the model right. `up` rather than `up_base`, so a panned frame
    // stays aligned to the picture after roll rather than to the world.
    let pan = (right * off.0 + up * off.1) * radius;
    let view = Mat4::look_at_rh(eye - pan, centre - pan, up);
    let proj = Mat4::perspective_rh(35f32.to_radians(), aspect.max(0.01), d * 0.01, d * 40.0);
    proj * view
}

const SHADER: &str = r#"
struct Globals {
    view_proj: mat4x4<f32>,
    light: vec4<f32>,
    grade: vec4<f32>,
    opts: vec4<f32>,
    tone: vec4<f32>,
};
@group(0) @binding(0) var<uniform> globals: Globals;
@group(0) @binding(1) var<storage, read> joints: array<mat4x4<f32>>;
@group(1) @binding(0) var albedo: texture_2d<f32>;
@group(1) @binding(1) var samp: sampler;

struct VsOut {
    @builtin(position) clip: vec4<f32>,
    @location(0) uv: vec2<f32>,
    @location(1) nrm: vec3<f32>,
};

@vertex
fn vs(
    @location(0) pos: vec3<f32>,
    @location(1) nrm: vec3<f32>,
    @location(2) uv: vec2<f32>,
    @location(3) jt: vec4<u32>,
    @location(4) wt: vec4<f32>,
) -> VsOut {
    var skinned = vec4<f32>(0.0, 0.0, 0.0, 0.0);
    var skinned_n = vec3<f32>(0.0, 0.0, 0.0);
    var total = 0.0;
    for (var i = 0; i < 4; i = i + 1) {
        let w = wt[i];
        if (w > 0.0) {
            let m = joints[jt[i]];
            skinned = skinned + w * (m * vec4<f32>(pos, 1.0));
            skinned_n = skinned_n + w * (mat3x3<f32>(m[0].xyz, m[1].xyz, m[2].xyz) * nrm);
            total = total + w;
        }
    }
    if (total <= 0.0) {
        skinned = vec4<f32>(pos, 1.0);
        skinned_n = nrm;
    }
    var out: VsOut;
    out.clip = globals.view_proj * vec4<f32>(skinned.xyz, 1.0);
    out.uv = uv;
    out.nrm = normalize(skinned_n);
    return out;
}

@fragment
fn fs(in: VsOut) -> @location(0) vec4<f32> {
    // TEXTURE FILTERING, of two kinds.
    //
    // Crisp snaps to the texel centre, which turns the linear sampler into a
    // nearest one: right for reading a rip back exactly as it was drawn.
    //
    // PS2 keeps the bilinear blend but quantises the texture coordinate to a
    // SIXTEENTH of a texel first. That 4-bit subtexel precision is the console's
    // own, and it is what gives its filtering the faintly stepped look that
    // plain bilinear does not have -- smooth, but visibly walking between
    // positions rather than gliding.
    let dims = vec2<f32>(textureDimensions(albedo, 0));
    var uv = in.uv;
    if (globals.opts.x < 0.5) {
        uv = (floor(in.uv * dims) + vec2<f32>(0.5, 0.5)) / dims;
    } else {
        uv = (floor(in.uv * dims * 16.0) / 16.0) / dims;
    }
    let base = textureSample(albedo, samp, uv);
    if (base.a < 0.02) { discard; }
    let l = normalize(globals.light.xyz);
    // Half-lambert: a hard terminator reads as a seam on a 128px sprite.
    let d = clamp(dot(normalize(in.nrm), l) * 0.5 + 0.5, 0.0, 1.0);
    let ambient = globals.grade.x;
    let shade = ambient + globals.light.w * d;
    var rgb = base.rgb * shade;

    // SHADOWS AND HIGHLIGHTS, before the global grade.
    //
    // Each acts only on its end of the range, masked by luminance, which is
    // what separates them from brightness: brightness moves everything and
    // flattens the picture, while these open up a dark side or pull a blown
    // highlight back without touching the midtones between them.
    //
    // The masks overlap slightly around the middle on purpose -- a hard split
    // at 0.5 leaves a visible band where one stops and the other starts.
    let lum = dot(rgb, vec3<f32>(0.2126, 0.7152, 0.0722));
    let shadow_mask = 1.0 - smoothstep(0.0, 0.55, lum);
    let highlight_mask = smoothstep(0.45, 1.0, lum);
    rgb = rgb + vec3<f32>(globals.tone.x) * shadow_mask;
    rgb = rgb + vec3<f32>(globals.tone.y) * highlight_mask;
    rgb = max(rgb, vec3<f32>(0.0));

    // The grade runs HERE rather than over the saved PNG, so the preview and
    // the bake cannot disagree about what the sprite looks like.
    rgb = pow(max(rgb, vec3<f32>(0.0)), vec3<f32>(1.0 / max(globals.grade.y, 0.01)));
    rgb = rgb + vec3<f32>(globals.grade.z);
    rgb = (rgb - vec3<f32>(0.5)) * globals.grade.w + vec3<f32>(0.5);
    return vec4<f32>(clamp(rgb, vec3<f32>(0.0), vec3<f32>(1.0)), base.a);
}
"#;
