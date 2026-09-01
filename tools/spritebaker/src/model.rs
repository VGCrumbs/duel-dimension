//! Reading a `.glb` far enough to pose and draw it.
//!
//! Deliberately the same subset the mod's own loader accepts (see
//! `GlbModel.java`): indexed triangles, POSITION / NORMAL / TEXCOORD_0 /
//! JOINTS_0 / WEIGHTS_0, one skin with inverse binds, node TRS, LINEAR
//! samplers, an embedded PNG. A model this refuses is a model the mod would
//! refuse, which makes this a check on the file as well as a viewer.

use glam::{Mat4, Quat, Vec3};
use std::path::Path;

#[repr(C)]
#[derive(Copy, Clone, bytemuck::Pod, bytemuck::Zeroable)]
pub struct Vertex {
    pub pos: [f32; 3],
    pub nrm: [f32; 3],
    pub uv: [f32; 2],
    pub joints: [u32; 4],
    pub weights: [f32; 4],
}

#[derive(Clone)]
pub struct Node {
    pub translation: Vec3,
    pub rotation: Quat,
    pub scale: Vec3,
    pub children: Vec<usize>,
    pub parent: Option<usize>,
}

#[derive(Clone, Copy, PartialEq)]
pub enum Path_ {
    Translation,
    Rotation,
    Scale,
}

pub struct Channel {
    pub node: usize,
    pub path: Path_,
    pub times: Vec<f32>,
    /// 3 floats per key for translation/scale, 4 for rotation.
    pub values: Vec<f32>,
}

pub struct Animation {
    pub name: String,
    pub duration: f32,
    pub channels: Vec<Channel>,
}

/// A run of indices sharing one material.
///
/// A monster is not one texture. Dark Magician is a body, a hat and a staff
/// with separate images, and taking the first material for the whole mesh --
/// which this did -- paints every triangle with whichever one happened to come
/// first. The mod's own loader keeps primitives apart for the same reason.
pub struct Part {
    pub start: u32,
    pub count: u32,
    /// Index into `images`, or none where the material carries no texture.
    pub image: Option<usize>,
}

pub struct Model {
    pub vertices: Vec<Vertex>,
    pub indices: Vec<u32>,
    pub parts: Vec<Part>,
    /// Every image in the file, RGBA8, indexed as glTF indexes them.
    pub images: Vec<(u32, u32, Vec<u8>)>,
    pub nodes: Vec<Node>,
    pub joints: Vec<usize>,
    pub inverse_binds: Vec<Mat4>,
    pub animations: Vec<Animation>,
    pub rest_min: Vec3,
    pub rest_max: Vec3,
}

impl Model {
    pub fn load(path: &Path) -> Result<Model, String> {
        let (doc, buffers, images) =
            gltf::import(path).map_err(|e| format!("{e}"))?;

        let mut nodes: Vec<Node> = doc
            .nodes()
            .map(|n| {
                let (t, r, s) = n.transform().decomposed();
                Node {
                    translation: Vec3::from(t),
                    rotation: Quat::from_array(r),
                    scale: Vec3::from(s),
                    children: n.children().map(|c| c.index()).collect(),
                    parent: None,
                }
            })
            .collect();
        for i in 0..nodes.len() {
            for c in nodes[i].children.clone() {
                nodes[c].parent = Some(i);
            }
        }

        let mut vertices = Vec::new();
        let mut indices = Vec::new();
        let mut parts: Vec<Part> = Vec::new();
        let mut rest_min = Vec3::splat(f32::MAX);
        let mut rest_max = Vec3::splat(f32::MIN);
        // Converted once, up front, and indexed the way glTF indexes them, so a
        // primitive can name its image without a second lookup table.
        let converted: Vec<(u32, u32, Vec<u8>)> = images.iter().map(to_rgba).collect();

        for mesh in doc.meshes() {
            for prim in mesh.primitives() {
                if prim.mode() != gltf::mesh::Mode::Triangles {
                    continue;
                }
                let reader = prim.reader(|b| Some(&buffers[b.index()]));
                let pos: Vec<[f32; 3]> = match reader.read_positions() {
                    Some(p) => p.collect(),
                    None => continue,
                };
                let nrm: Vec<[f32; 3]> = reader
                    .read_normals()
                    .map(|n| n.collect())
                    .unwrap_or_else(|| vec![[0.0, 1.0, 0.0]; pos.len()]);
                let uv: Vec<[f32; 2]> = reader
                    .read_tex_coords(0)
                    .map(|t| t.into_f32().collect())
                    .unwrap_or_else(|| vec![[0.0, 0.0]; pos.len()]);
                let jt: Vec<[u16; 4]> = reader
                    .read_joints(0)
                    .map(|j| j.into_u16().collect())
                    .unwrap_or_else(|| vec![[0, 0, 0, 0]; pos.len()]);
                let wt: Vec<[f32; 4]> = reader
                    .read_weights(0)
                    .map(|w| w.into_f32().collect())
                    .unwrap_or_else(|| vec![[1.0, 0.0, 0.0, 0.0]; pos.len()]);

                let base = vertices.len() as u32;
                for i in 0..pos.len() {
                    let p = Vec3::from(pos[i]);
                    rest_min = rest_min.min(p);
                    rest_max = rest_max.max(p);
                    vertices.push(Vertex {
                        pos: pos[i],
                        nrm: nrm[i],
                        uv: uv[i],
                        joints: [
                            jt[i][0] as u32,
                            jt[i][1] as u32,
                            jt[i][2] as u32,
                            jt[i][3] as u32,
                        ],
                        weights: wt[i],
                    });
                }
                let start = indices.len() as u32;
                if let Some(read) = reader.read_indices() {
                    indices.extend(read.into_u32().map(|i| i + base));
                } else {
                    // Unindexed primitives still have to draw, so they get the
                    // trivial index list rather than being skipped silently.
                    indices.extend(base..base + pos.len() as u32);
                }
                let image = prim
                    .material()
                    .pbr_metallic_roughness()
                    .base_color_texture()
                    .map(|info| info.texture().source().index())
                    .filter(|i| *i < converted.len());
                parts.push(Part {
                    start,
                    count: indices.len() as u32 - start,
                    image,
                });
            }
        }

        let mut joints = Vec::new();
        let mut inverse_binds = Vec::new();
        if let Some(skin) = doc.skins().next() {
            joints = skin.joints().map(|j| j.index()).collect();
            let reader = skin.reader(|b| Some(&buffers[b.index()]));
            if let Some(ibm) = reader.read_inverse_bind_matrices() {
                inverse_binds = ibm.map(|m| Mat4::from_cols_array_2d(&m)).collect();
            }
        }
        if inverse_binds.len() < joints.len() {
            inverse_binds.resize(joints.len(), Mat4::IDENTITY);
        }

        let mut animations = Vec::new();
        for (i, anim) in doc.animations().enumerate() {
            let mut channels = Vec::new();
            let mut duration: f32 = 0.0;
            for ch in anim.channels() {
                let reader = ch.reader(|b| Some(&buffers[b.index()]));
                let times: Vec<f32> = match reader.read_inputs() {
                    Some(t) => t.collect(),
                    None => continue,
                };
                if let Some(last) = times.last() {
                    duration = duration.max(*last);
                }
                let (path, values) = match reader.read_outputs() {
                    Some(gltf::animation::util::ReadOutputs::Translations(t)) => {
                        (Path_::Translation, t.flat_map(|v| v).collect())
                    }
                    Some(gltf::animation::util::ReadOutputs::Rotations(r)) => {
                        (Path_::Rotation, r.into_f32().flat_map(|v| v).collect())
                    }
                    Some(gltf::animation::util::ReadOutputs::Scales(s)) => {
                        (Path_::Scale, s.flat_map(|v| v).collect())
                    }
                    _ => continue,
                };
                channels.push(Channel {
                    node: ch.target().node().index(),
                    path,
                    times,
                    values,
                });
            }
            animations.push(Animation {
                name: anim.name().map(str::to_string).unwrap_or(format!("anim{i}")),
                duration,
                channels,
            });
        }

        if vertices.is_empty() {
            return Err("no triangles in this file".into());
        }
        Ok(Model {
            vertices,
            indices,
            parts,
            images: converted,
            nodes,
            joints,
            inverse_binds,
            animations,
            rest_min,
            rest_max,
        })
    }

    /// Node transforms at `time` in `anim`, or the rest pose when there is none.
    fn posed_nodes(&self, anim: Option<usize>, time: f32) -> Vec<Node> {
        let mut posed = self.nodes.clone();
        let Some(index) = anim else { return posed };
        let Some(animation) = self.animations.get(index) else {
            return posed;
        };
        for ch in &animation.channels {
            if ch.times.is_empty() || ch.node >= posed.len() {
                continue;
            }
            let (a, b, f) = span(&ch.times, time);
            match ch.path {
                Path_::Translation | Path_::Scale => {
                    let va = Vec3::new(
                        ch.values[a * 3],
                        ch.values[a * 3 + 1],
                        ch.values[a * 3 + 2],
                    );
                    let vb = Vec3::new(
                        ch.values[b * 3],
                        ch.values[b * 3 + 1],
                        ch.values[b * 3 + 2],
                    );
                    let v = va.lerp(vb, f);
                    if ch.path == Path_::Translation {
                        posed[ch.node].translation = v;
                    } else {
                        posed[ch.node].scale = v;
                    }
                }
                Path_::Rotation => {
                    let qa = Quat::from_xyzw(
                        ch.values[a * 4],
                        ch.values[a * 4 + 1],
                        ch.values[a * 4 + 2],
                        ch.values[a * 4 + 3],
                    );
                    let qb = Quat::from_xyzw(
                        ch.values[b * 4],
                        ch.values[b * 4 + 1],
                        ch.values[b * 4 + 2],
                        ch.values[b * 4 + 3],
                    );
                    posed[ch.node].rotation = qa.slerp(qb, f);
                }
            }
        }
        posed
    }

    fn world_matrices(&self, posed: &[Node]) -> Vec<Mat4> {
        let mut world = vec![Mat4::IDENTITY; posed.len()];
        let mut done = vec![false; posed.len()];
        for i in 0..posed.len() {
            resolve(i, posed, &mut world, &mut done);
        }
        world
    }

    /// The matrices the vertex shader skins with, and the posed bounds.
    pub fn pose(&self, anim: Option<usize>, time: f32) -> (Vec<Mat4>, Vec3, Vec3) {
        let posed = self.posed_nodes(anim, time);
        let world = self.world_matrices(&posed);
        let mut out = Vec::with_capacity(self.joints.len().max(1));
        for (i, node) in self.joints.iter().enumerate() {
            out.push(world[*node] * self.inverse_binds[i]);
        }
        if out.is_empty() {
            out.push(Mat4::IDENTITY);
        }

        // Bounds from the SKINNED result, not the rest pose: a model is framed
        // by where its animation puts it, and for a flier those are different
        // places entirely.
        let mut min = Vec3::splat(f32::MAX);
        let mut max = Vec3::splat(f32::MIN);
        for v in &self.vertices {
            let p = Vec3::from(v.pos);
            let mut acc = Vec3::ZERO;
            let mut any = false;
            for k in 0..4 {
                let w = v.weights[k];
                if w == 0.0 {
                    continue;
                }
                let j = v.joints[k] as usize;
                if let Some(m) = out.get(j) {
                    acc += w * m.transform_point3(p);
                    any = true;
                }
            }
            let p = if any { acc } else { p };
            min = min.min(p);
            max = max.max(p);
        }
        (out, min, max)
    }
}

fn resolve(i: usize, posed: &[Node], world: &mut Vec<Mat4>, done: &mut Vec<bool>) {
    if done[i] {
        return;
    }
    let n = &posed[i];
    let local = Mat4::from_scale_rotation_translation(n.scale, n.rotation, n.translation);
    world[i] = match n.parent {
        Some(p) => {
            resolve(p, posed, world, done);
            world[p] * local
        }
        None => local,
    };
    done[i] = true;
}

/// Surrounding keys and the fraction between them, for LINEAR sampling.
fn span(times: &[f32], t: f32) -> (usize, usize, f32) {
    if times.len() == 1 || t <= times[0] {
        return (0, 0, 0.0);
    }
    let last = times.len() - 1;
    if t >= times[last] {
        return (last, last, 0.0);
    }
    let mut lo = 0usize;
    let mut hi = last;
    while lo + 1 < hi {
        let mid = (lo + hi) / 2;
        if times[mid] <= t {
            lo = mid;
        } else {
            hi = mid;
        }
    }
    let d = times[hi] - times[lo];
    (lo, hi, if d > 0.0 { (t - times[lo]) / d } else { 0.0 })
}

fn to_rgba(img: &gltf::image::Data) -> (u32, u32, Vec<u8>) {
    use gltf::image::Format;
    let n = (img.width * img.height) as usize;
    let mut out = vec![255u8; n * 4];
    match img.format {
        Format::R8G8B8A8 => out.copy_from_slice(&img.pixels[..n * 4]),
        Format::R8G8B8 => {
            for i in 0..n {
                out[i * 4] = img.pixels[i * 3];
                out[i * 4 + 1] = img.pixels[i * 3 + 1];
                out[i * 4 + 2] = img.pixels[i * 3 + 2];
            }
        }
        Format::R8 => {
            for i in 0..n {
                let v = img.pixels[i];
                out[i * 4] = v;
                out[i * 4 + 1] = v;
                out[i * 4 + 2] = v;
            }
        }
        Format::R8G8 => {
            for i in 0..n {
                out[i * 4] = img.pixels[i * 2];
                out[i * 4 + 1] = img.pixels[i * 2];
                out[i * 4 + 2] = img.pixels[i * 2];
                out[i * 4 + 3] = img.pixels[i * 2 + 1];
            }
        }
        // Anything else is left flat white rather than half-decoded, which is
        // at least obviously wrong rather than subtly miscoloured.
        _ => {}
    }
    (img.width, img.height, out)
}
