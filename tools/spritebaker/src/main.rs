//! Sprite Baker -- turn a monster's `.glb` into the sheet the mod draws when
//! the model is not being used.
//!
//! It exists because the orientation is a judgement call and judgement calls
//! belong to whoever is looking at the result. Rendering these from a script
//! meant guessing an axis, rendering, and asking -- three round trips to find
//! out a dragon was on its side. Here the controls are live and the preview is
//! the same pass that gets saved.
//!
//! SAVING WRITES WHERE THE GAME READS. The sheet goes to
//! `config/dueldimension/sheets/<type>/<name>.png` in the same profile the
//! model came from, and the entry is upserted into that profile's
//! `monster_sprites.json` -- both are read at startup, so a bake needs a game
//! restart and nothing else. No rebuild, no jar.

mod model;
mod render;

use eframe::egui;
use model::Model;
use std::path::{Path, PathBuf};

fn main() -> eframe::Result<()> {
    let options = eframe::NativeOptions {
        viewport: egui::ViewportBuilder::default()
            .with_inner_size([1180.0, 780.0])
            .with_title("Duel Dimension - Sprite Baker"),
        renderer: eframe::Renderer::Wgpu,
        ..Default::default()
    };
    eframe::run_native(
        "spritebaker",
        options,
        Box::new(|cc| Ok(Box::new(App::new(cc)))),
    )
}

#[derive(Clone, Copy, PartialEq)]
enum LoopMode {
    Loop,
    PingPong,
    Once,
}

impl LoopMode {
    fn json(self) -> &'static str {
        match self {
            LoopMode::Loop => "LOOP",
            LoopMode::PingPong => "PING_PONG",
            LoopMode::Once => "ONCE",
        }
    }
}

/// A `.glb` found on disk, and where it came from.
struct Entry {
    path: PathBuf,
    name: String,
    /// The profile root, so a bake can be written back beside it.
    config: PathBuf,
}

struct Loaded {
    model: Model,
    renderer: render::Renderer,
}

struct App {
    found: Vec<Entry>,
    filter: String,
    selected: Option<usize>,
    loaded: Option<Loaded>,

    anim: usize,
    time: f32,
    /// Runs 0..period; for PING_PONG the period is twice the clip and the
    /// second half is folded back, so the preview plays what the setting says.
    phase: f32,
    playing: bool,

    distance: f32,
    yaw: f32,
    pitch: f32,
    roll: f32,
    /// Where the subject sits inside the frame, in units of the model's
    /// radius: x right, y up. The sheet wants feet ON the bottom edge, and
    /// auto-fit centres the whole creature instead -- which for anything with
    /// a tail, wings or a hovering pose leaves a gap under it that reads in
    /// game as the monster floating above its card.
    off_x: f32,
    off_y: f32,

    /// The frame at 1x. What actually renders is this times `scale`.
    frame_w: u32,
    frame_h: u32,
    /// Supersample/output multiplier: a 128x256 frame at 2x bakes 256x512.
    scale: u32,
    /// How many samples per output pixel, per axis: 2 means the model is
    /// drawn at twice the width and height and averaged down.
    ///
    /// Not the same thing as `scale`, which makes a BIGGER sprite. This makes
    /// the same sprite out of more information, which is what a still frame
    /// needs -- motion hides a staircase and a stopped preview does not.
    ssaa: u32,
    /// Index into ASPECTS, or the last entry for a free-form size.
    aspect: usize,
    /// The LONG side, in pixels, when a preset is driving the shape.
    base: u32,
    frames: u32,
    ticks: u32,
    /// When set, `ticks` is computed from the clip so the sprite plays at the
    /// model's own speed rather than at a number picked by hand.
    match_timing: bool,
    /// Frames per second for the GIF. Zero means "the clip's own rate".
    gif_fps: u32,
    loop_mode: LoopMode,
    /// Steps in the bob cycle; 0 or 1 means no bob at all.
    bob: u32,

    card: String,
    kind: String,
    status: String,

    // Lighting, applied in the shader so preview and bake are the same picture.
    light_az: f32,
    light_el: f32,
    light_power: f32,
    ambient: f32,
    gamma: f32,
    brightness: f32,
    contrast: f32,
    /// Gaussian sigma in output pixels. Zero leaves the frame untouched.
    blur: f32,
    /// PS2-style bilinear, quantised to a sixteenth of a texel.
    ps2_filter: bool,
    /// Signed lift on the dark end and the bright end respectively.
    shadows: f32,
    highlights: f32,
    /// Fill the background instead of leaving it transparent.
    bg_on: bool,
    bg: egui::Color32,

    /// Framing held still across a clip -- see `framing()`.
    /// `(clip, centre, radius, min, max)`; the bounds are kept because
    /// "feet to floor" needs where the subject ENDS, not just how big it is.
    framing: Option<(usize, glam::Vec3, f32, glam::Vec3, glam::Vec3)>,
    /// The live frame, as the exact bytes an export would write.
    frame_tex: Option<egui::TextureHandle>,
    /// The assembled sheet, baked but not yet written.
    sheet: Option<egui::TextureHandle>,
    show_sheet: bool,
    export_as: Format,

    mode: Mode,
    /// How many views of the monster a SAVED SHEET holds, one per row.
    ///
    /// 1 is an ordinary sheet. 8 is the Doom idea in the shape the mod
    /// reads -- see `bake_rotations` -- and is written into the game's
    /// settings as `directions`, which is what makes the billboard pick a
    /// row by the angle the viewer is standing at.
    rotations: u32,
    /// Doom's four-character sprite name, e.g. TROO.
    doom_name: String,
    /// The frame letter the first animation frame gets: A, then B, and so on.
    doom_letter: char,
    /// Doom numbers its rotations anticlockwise; a model built the other way
    /// round needs the sequence flipped, and which it is cannot be guessed.
    doom_reverse: bool,
    /// Write a grAb chunk placing the origin at the sprite's feet.
    doom_grab: bool,

    preset_name: String,
    presets: Vec<String>,

    undo: Vec<Settings>,
    redo: Vec<Settings>,
    /// The last state considered settled, which is what an undo steps back to.
    last: Option<Settings>,
}

/// Everything a bake depends on, in one comparable lump.
///
/// Deliberately NOT the selected model, the clip or the playhead: undo should
/// step back through choices about how a monster looks, not swap which monster
/// is loaded underneath you. Reloading a model on Ctrl+Z would be a surprise
/// and a stutter.
#[derive(Clone, PartialEq)]
struct Settings {
    distance: f32,
    yaw: f32,
    pitch: f32,
    roll: f32,
    off_x: f32,
    off_y: f32,
    light_az: f32,
    light_el: f32,
    light_power: f32,
    ambient: f32,
    gamma: f32,
    brightness: f32,
    contrast: f32,
    blur: f32,
    ps2_filter: bool,
    shadows: f32,
    highlights: f32,
    bg_on: bool,
    bg: egui::Color32,
    frame_w: u32,
    frame_h: u32,
    scale: u32,
    ssaa: u32,
    aspect: usize,
    base: u32,
    frames: u32,
    rotations: u32,
    ticks: u32,
    match_timing: bool,
    /// Frames per second for the GIF. Zero means "the clip's own rate".
    gif_fps: u32,
    loop_mode: LoopMode,
    bob: u32,
    kind: String,
}

/// What the tool is producing.
///
/// The two share everything up to the point of writing -- same model, same
/// camera, same light, same bake -- and differ only in how many angles are
/// rendered and what the files are called.
#[derive(Clone, Copy, PartialEq)]
enum Mode {
    Sheet,
    Doom,
}

#[derive(Clone, Copy, PartialEq)]
enum Format {
    Png,
    Gif,
}

impl Format {
    fn label(self) -> &'static str {
        match self {
            Format::Png => "PNG sheet",
            Format::Gif => "Animated GIF",
        }
    }

    fn ext(self) -> &'static str {
        match self {
            Format::Png => "png",
            Format::Gif => "gif",
        }
    }
}

/// Label, width, height. The last entry means "leave the numbers alone".
///
/// Tall and wide both matter and for different creatures: a humanoid is a
/// column and wastes most of a wide frame, while a winged monster is a
/// horizontal thing whose wingtips are the first casualty of a tall one. The
/// mod's own rips are 1:2, which is right for the warriors and spellcasters
/// that make up most of them and wrong for a dragon with its wings out.
const ASPECTS: &[(&str, u32, u32)] = &[
    ("1:2  tall - humanoid", 1, 2),
    ("2:3  tall", 2, 3),
    ("3:4  tall", 3, 4),
    ("1:1  square", 1, 1),
    ("4:3  wide", 4, 3),
    ("3:2  wide", 3, 2),
    ("16:9 wide", 16, 9),
    ("2:1  wide - winged", 2, 1),
    ("3:1  very wide", 3, 1),
    ("custom", 0, 0),
];

/// Dark blues, greens and reds, plus a neutral, for showing a sprite against.
const BACKDROPS: &[(u8, u8, u8)] = &[
    (16, 24, 40),
    (22, 33, 62),
    (11, 26, 46),
    (14, 31, 20),
    (18, 48, 28),
    (10, 35, 24),
    (42, 14, 18),
    (58, 18, 24),
    (36, 10, 14),
    (18, 18, 20),
];

const KINDS: &[&str] = &[
    "beast", "dragon", "fiend", "rock", "spellcaster", "warrior", "zombie",
    "aqua", "machine", "insect", "plant", "fairy", "dinosaur", "thunder",
    "winged_beast", "pyro", "sea_serpent", "reptile", "psychic", "divine",
];

impl App {
    fn new(_cc: &eframe::CreationContext<'_>) -> App {
        let mut app = App {
            found: Vec::new(),
            filter: String::new(),
            selected: None,
            loaded: None,
            anim: 0,
            time: 0.0,
            phase: 0.0,
            playing: true,
            distance: 2.6,
            off_x: 0.0,
            off_y: 0.0,
            yaw: 35.0,
            pitch: 12.0,
            roll: 0.0,
            // 1:1 at 256. A square frame is the neutral starting point: it
            // crops neither a column nor a wingspan, so the shape of the
            // monster decides which preset to move to rather than the default
            // deciding it in advance.
            frame_w: 256,
            frame_h: 256,
            scale: 1,
            aspect: 3,
            base: 256,
            frames: 4,
            ticks: 5,
            match_timing: true,
            gif_fps: 0,
            loop_mode: LoopMode::Loop,
            bob: 0,
            card: String::new(),
            kind: "dragon".into(),
            status: String::new(),
            light_az: 35.0,
            light_el: 45.0,
            light_power: 0.55,
            ambient: 0.45,
            gamma: 1.0,
            brightness: 0.0,
            contrast: 1.0,
            blur: 0.0,
            ps2_filter: false,
            shadows: 0.0,
            highlights: 0.0,
            bg_on: false,
            bg: egui::Color32::from_rgb(16, 24, 40),
            framing: None,
            frame_tex: None,
            sheet: None,
            show_sheet: false,
            export_as: Format::Png,
            mode: Mode::Sheet,
            rotations: 1,
            ssaa: 3,
            doom_name: "MNST".into(),
            doom_letter: 'A',
            doom_reverse: false,
            doom_grab: true,
            preset_name: String::new(),
            presets: Vec::new(),
            undo: Vec::new(),
            redo: Vec::new(),
            last: None,
        };
        app.rescan_presets();
        app.rescan();
        app
    }

    /// Every `.glb` under any profile's `config/dueldimension/models`.
    fn rescan(&mut self) {
        self.found.clear();
        for root in candidate_roots() {
            let models = root.join("models");
            let Ok(dir) = std::fs::read_dir(&models) else { continue };
            for item in dir.flatten() {
                let path = item.path();
                if path.extension().and_then(|e| e.to_str()) != Some("glb") {
                    continue;
                }
                let name = path
                    .file_stem()
                    .and_then(|s| s.to_str())
                    .unwrap_or("?")
                    .to_string();
                self.found.push(Entry { path, name, config: root.clone() });
            }
        }
        self.found.sort_by(|a, b| a.name.cmp(&b.name));
        self.found.dedup_by(|a, b| a.name == b.name);
        self.status = format!("{} models found", self.found.len());
    }

    fn select(&mut self, index: usize, ctx: &egui::Context, frame: &mut eframe::Frame) {
        let Some(entry) = self.found.get(index) else { return };
        let path = entry.path.clone();
        let config = entry.config.clone();
        let name = entry.name.clone();
        match Model::load(&path) {
            Ok(m) => {
                // Prefill card and type from whatever this profile already says
                // about this model, so an existing monster keeps its identity
                // and only the sheet changes.
                if let Some((card, kind)) = lookup(&config, &name) {
                    self.card = card;
                    self.kind = kind;
                }
                self.status = format!(
                    "{name}: {} tris, {} joints, {} animations",
                    m.indices.len() / 3,
                    m.joints.len(),
                    m.animations.len()
                );
                self.anim = 0;
                self.time = 0.0;
                self.framing = None;
                self.build(m, ctx, frame);
                self.selected = Some(index);
            }
            Err(e) => self.status = format!("{name}: {e}"),
        }
    }

    fn build(&mut self, m: Model, _ctx: &egui::Context, frame: &mut eframe::Frame) {
        let Some(rs) = frame.wgpu_render_state() else { return };
        let renderer =
            render::Renderer::new(&rs.device, &rs.queue, &m, (self.out_w(), self.out_h()));
        self.loaded = Some(Loaded { model: m, renderer });
    }

    /// Centre and radius for the WHOLE clip, computed once and held.
    ///
    /// Framing per frame from that frame's own bounds is what made the preview
    /// bob: a flier moves, so its bounds move, so a camera fitted to them chases
    /// it and the subject appears to swim while the background stays put. The
    /// union across the clip gives one fixed camera, which is also what the
    /// baked sheet needs -- frames that each re-framed would not line up.
    fn framing(&mut self) -> (glam::Vec3, f32) {
        let (c, r, _, _) = self.framing_full();
        (c, r)
    }

    /// As `framing()`, and also the clip's world-space bounds.
    fn framing_full(&mut self) -> (glam::Vec3, f32, glam::Vec3, glam::Vec3) {
        let anim_key = self.anim;
        if let Some((key, c, r, lo, hi)) = self.framing {
            if key == anim_key {
                return (c, r, lo, hi);
            }
        }
        let Some(l) = self.loaded.as_ref() else {
            return (glam::Vec3::ZERO, 1.0, glam::Vec3::ZERO, glam::Vec3::ZERO);
        };
        let has = !l.model.animations.is_empty();
        let duration = l
            .model
            .animations
            .get(self.anim)
            .map(|a| a.duration)
            .unwrap_or(0.0);
        let mut min = glam::Vec3::splat(f32::MAX);
        let mut max = glam::Vec3::splat(f32::MIN);
        let steps = if duration > 0.0 { 16 } else { 1 };
        for i in 0..steps {
            let t = duration * (i as f32) / (steps as f32);
            let (_, lo, hi) = l.model.pose(if has { Some(self.anim) } else { None }, t);
            min = min.min(lo);
            max = max.max(hi);
        }
        let centre = (min + max) * 0.5;
        let radius = ((max - min).length() * 0.5).max(0.001);
        self.framing = Some((anim_key, centre, radius, min, max));
        (centre, radius, min, max)
    }

    /// The `off_y` that lands the subject's lowest point on the bottom edge.
    ///
    /// Measured, not guessed. The bottom edge sits `d * tan(fovy/2)` below the
    /// target in world units, and the subject's lowest extent is the smallest
    /// projection of its bounding box onto the camera's up vector -- which
    /// depends on yaw, pitch and roll, so it has to be recomputed whenever the
    /// view moves rather than stored.
    ///
    /// It is an estimate in one respect: the half-height is taken at the
    /// target's depth, while perspective makes nearer parts of the model project
    /// larger. For a subject roughly centred in depth the error is small, and
    /// this is a starting point to nudge from rather than a final answer.
    fn feet_to_floor(&mut self) -> f32 {
        let (centre, radius, lo, hi) = self.framing_full();
        let d = radius * self.distance;
        let (sy, cy) = self.yaw.to_radians().sin_cos();
        let (sp, cp) = self.pitch.to_radians().sin_cos();
        let eye = centre + glam::Vec3::new(cp * sy, sp, cp * cy) * d;
        let forward = (centre - eye).normalize_or_zero();
        let right = forward.cross(glam::Vec3::Y).normalize_or_zero();
        let up_base = right.cross(forward).normalize_or_zero();
        let (sr, cr) = self.roll.to_radians().sin_cos();
        let up = (up_base * cr + right * sr).normalize_or_zero();

        // Every corner, because which one is lowest on screen changes with the
        // view -- for a rolled camera it can be a corner no axis would name.
        let mut lowest = f32::MAX;
        for i in 0..8 {
            let corner = glam::Vec3::new(
                if i & 1 == 0 { lo.x } else { hi.x },
                if i & 2 == 0 { lo.y } else { hi.y },
                if i & 4 == 0 { lo.z } else { hi.z },
            );
            lowest = lowest.min((corner - centre).dot(up));
        }
        let half_height = d * (35f32.to_radians() * 0.5).tan();
        (-half_height - lowest) / radius
    }

    /// Applies the chosen ratio, sizing from the LONG side.
    ///
    /// Anchoring on height instead -- which this did -- is fine while every
    /// preset is taller than it is wide, and balloons the moment one is not:
    /// 16:9 off a 256px height is a 455px frame, four times the area of the
    /// 1:2 beside it. The long side keeps a wide frame and a tall frame the
    /// same size on screen, which is what makes them comparable.
    fn apply_aspect(&mut self) {
        let (_, aw, ah) = ASPECTS[self.aspect.min(ASPECTS.len() - 1)];
        if aw == 0 || ah == 0 {
            return;
        }
        let base = self.base.clamp(8, 1024);
        if aw >= ah {
            self.frame_w = base;
            self.frame_h = ((base * ah) / aw).clamp(8, 1024);
        } else {
            self.frame_h = base;
            self.frame_w = ((base * aw) / ah).clamp(8, 1024);
        }
    }

    /// The size actually rendered and written, after the scale multiplier.
    fn out_w(&self) -> u32 {
        (self.frame_w * self.scale).max(1)
    }

    fn out_h(&self) -> u32 {
        (self.frame_h * self.scale).max(1)
    }

    /// The clip's length, or zero when there is no animation.
    fn duration(&self) -> f32 {
        self.loaded
            .as_ref()
            .and_then(|l| l.model.animations.get(self.anim))
            .map(|a| a.duration)
            .unwrap_or(0.0)
    }

    /// Game ticks each frame is held for.
    ///
    /// A tick is 50ms, so matching the model means asking how long one baked
    /// frame stands for -- clip length over frame count -- and rounding that to
    /// ticks. The rounding is real and is reported in the UI: at 20 ticks per
    /// second a sprite cannot hold a frame for 30ms, so a fast clip lands
    /// slightly slow or slightly fast and there is no third option.
    fn effective_ticks(&self) -> u32 {
        if !self.match_timing {
            return self.ticks.max(1);
        }
        let duration = self.duration();
        let count = self.frames.max(1) as f32;
        if duration <= 0.0 {
            return self.ticks.max(1);
        }
        let seconds_per_frame = duration / count;
        ((seconds_per_frame / 0.05).round() as i64).clamp(1, 200) as u32
    }

    /// Milliseconds each GIF frame is held for.
    ///
    /// NOT derived from ticks. A tick is Minecraft's 50ms floor, which caps the
    /// in-game sprite at 20fps -- a real limit of the game and no business of a
    /// GIF. Taking the clip's own interval instead lets an export run as fast
    /// as the clip actually does.
    ///
    /// GIF measures delays in CENTISECONDS, so the achievable rates are 100/n:
    /// 50, 33.3, 25, 20 and so on. 60fps is not among them and lands on 50.
    /// `gif_interval_ms` returns the honest request; the encoder rounds it.
    fn gif_interval_ms(&self) -> f32 {
        if self.gif_fps > 0 {
            return 1000.0 / self.gif_fps as f32;
        }
        let duration = self.duration();
        if duration <= 0.0 {
            return 1000.0 / 12.0;
        }
        // The gap between the frames that were actually SAMPLED, which depends
        // on the loop mode for the same reason the sampling does: LOOP stops a
        // step short of the end so the wrap is even, the others reach it.
        (duration / self.span(self.frames.max(1)) as f32) * 1000.0
    }

    /// How many steps the clip is divided into for `count` frames.
    fn span(&self, count: u32) -> u32 {
        if self.loop_mode == LoopMode::Loop {
            count.max(1)
        } else {
            (count - 1).max(1)
        }
    }

    /// How many frames the GIF needs.
    ///
    /// A RATE IS NOT A DELAY. Asking for 50fps and leaving the frame count at
    /// eight used to hold each of those eight for 2cs -- so a three second clip
    /// played in 160ms, nineteen times too fast and bearing no relation to the
    /// animation. A rate says how finely to sample the clip, and the count has
    /// to follow it or the export is simply a different animation.
    fn gif_frame_count(&self) -> u32 {
        if self.gif_fps == 0 {
            return self.frames.max(1);
        }
        let duration = self.duration();
        if duration <= 0.0 {
            return self.frames.max(1);
        }
        ((duration * self.gif_fps as f32).round() as i64).clamp(1, 600) as u32
    }

    /// How long the finished GIF runs, in seconds.
    fn gif_length(&self) -> f32 {
        let mut count = self.gif_frame_count();
        if self.loop_mode == LoopMode::PingPong && count > 2 {
            count += count - 2;
        }
        count as f32 * self.gif_delay_cs() as f32 / 100.0
    }

    /// The delay, in whole centiseconds, that will be written.
    ///
    /// GIF stores delay in centiseconds, so this rounds rather than letting the
    /// encoder truncate milliseconds into one. That truncation is what made a
    /// 60fps export crawl: 1000/60 is 16.7ms, which becomes 1cs -- and a delay
    /// of 0 or 1 is the legacy "unspecified" value that browsers and most
    /// viewers substitute with 10cs. The file said 100fps and played at 10.
    ///
    /// Hence the floor of 2. It is not caution: 1cs is a value that does not
    /// mean what it says, and there is nothing between 2cs and it.
    fn gif_delay_cs(&self) -> u32 {
        ((self.gif_interval_ms() / 10.0).round() as i64).clamp(2, 655) as u32
    }

    /// What the GIF will actually run at, which is 100 over the delay.
    fn gif_actual_fps(&self) -> f32 {
        100.0 / self.gif_delay_cs() as f32
    }

    /// The clear colour, converted out of sRGB.
    ///
    /// wgpu takes a clear value in LINEAR space and the target is sRGB, so it
    /// is encoded on write. Handing it the picker's bytes directly makes every
    /// background come out markedly lighter than the swatch that was clicked --
    /// the conversion is not optional, it is the difference between the colour
    /// asked for and a washed-out one.
    fn clear_colour(&self) -> eframe::wgpu::Color {
        if !self.bg_on {
            return eframe::wgpu::Color::TRANSPARENT;
        }
        let to_linear = |v: u8| -> f64 {
            let c = v as f64 / 255.0;
            if c <= 0.04045 { c / 12.92 } else { ((c + 0.055) / 1.055).powf(2.4) }
        };
        eframe::wgpu::Color {
            r: to_linear(self.bg.r()),
            g: to_linear(self.bg.g()),
            b: to_linear(self.bg.b()),
            a: 1.0,
        }
    }

    fn light(&self) -> [f32; 4] {
        let az = self.light_az.to_radians();
        let el = self.light_el.to_radians();
        [
            el.cos() * az.sin(),
            el.sin(),
            el.cos() * az.cos(),
            self.light_power,
        ]
    }

    fn grade(&self) -> [f32; 4] {
        [self.ambient, self.gamma, self.brightness, self.contrast]
    }

    /// Redraws the preview at the current pose, and returns its size.
    fn redraw(&mut self, frame: &mut eframe::Frame) {
        let (centre, radius) = self.framing();
        let light = self.light();
        let grade = self.grade();
        let (fw, fh) = (self.out_w(), self.out_h());
        // Drawn larger than it is kept, and averaged back down in
        // read_frame. Sampling the model above the output grid is what
        // stops an edge having to choose one pixel or the other.
        let (rw, rh) = (fw * self.ssaa.max(1), fh * self.ssaa.max(1));
        let (distance, yaw, pitch, roll) =
            (self.distance, self.yaw, self.pitch, self.roll);
        let off = (self.off_x, self.off_y);
        let ps2 = self.ps2_filter;
        let tone = [self.shadows, self.highlights, 0.0, 0.0];
        let clear = self.clear_colour();
        let anim_index = self.anim;
        let time = self.time;
        let Some(rs) = frame.wgpu_render_state().cloned() else { return };
        let Some(l) = self.loaded.as_mut() else { return };

        if l.renderer.size != (rw, rh) {
            l.renderer.resize(&rs.device, (rw, rh));
        }

        let anim = if l.model.animations.is_empty() { None } else { Some(anim_index) };
        let (joints, _, _) = l.model.pose(anim, time);
        let vp = render::view_proj(
            centre,
            radius,
            distance,
            yaw,
            pitch,
            roll,
            off,
            rw as f32 / rh as f32,
        );
        l.renderer.draw(
            &rs.device, &rs.queue, &joints, vp, light, grade, ps2, tone, clear,
        );
    }

    /// Reads the drawn frame back and applies the softening pass.
    ///
    /// Every consumer goes through here -- the live preview, the sheet and the
    /// GIF -- so the blur cannot end up on one and not the others, which is the
    /// shape of bug this tool has already had once with the materials.
    fn read_frame(&self, rs: &eframe::egui_wgpu::RenderState) -> Option<(u32, u32, Vec<u8>)> {
        let l = self.loaded.as_ref()?;
        let (mut w, mut h) = l.renderer.size;
        let mut pixels = l.renderer.read_back(&rs.device, &rs.queue);
        // BEFORE the blur, and before anything else sees it. The
        // supersample is an implementation detail of DRAWING; every
        // consumer -- preview, sheet, GIF, Doom sprites -- is handed the
        // frame at the size it asked for and never learns this happened.
        let ss = self.ssaa.max(1);
        if ss > 1 {
            pixels = downsample_rgba(&pixels, w, h, ss);
            w /= ss;
            h /= ss;
        }
        if self.blur > 0.01 {
            blur_rgba(&mut pixels, w, h, self.blur);
        }
        Some((w, h, pixels))
    }

    /// Pulls the drawn frame back and hands it to egui as an image.
    ///
    /// A round trip per displayed frame, which at 128x256 is 128 KB and not
    /// worth optimising: it is what makes the preview and the export the same
    /// pixels rather than two renderings that ought to agree.
    fn refresh_preview(&mut self, ctx: &egui::Context, frame: &mut eframe::Frame) {
        let Some(rs) = frame.wgpu_render_state().cloned() else { return };
        let Some((w, h, pixels)) = self.read_frame(&rs) else { return };
        let image =
            egui::ColorImage::from_rgba_unmultiplied([w as usize, h as usize], &pixels);
        match self.frame_tex.as_mut() {
            Some(handle) => handle.set(image, egui::TextureOptions::NEAREST),
            None => {
                self.frame_tex =
                    Some(ctx.load_texture("frame", image, egui::TextureOptions::NEAREST))
            }
        }
    }

    /// Renders every frame and lays them out side by side.
    ///
    /// The one place a sheet is produced, so what gets previewed and what gets
    /// written are the same bytes rather than two paths that agree today.
    /// Every frame of the clip, rendered separately.
    ///
    /// The sheet and the GIF both want these, so they are produced once. A GIF
    /// needs them apart and a sheet needs them side by side, and building each
    /// from its own render loop is how the two would drift.
    fn bake_frames(
        &mut self,
        frame: &mut eframe::Frame,
        count: u32,
    ) -> Option<(u32, u32, Vec<Vec<u8>>)> {
        // Cloned out of the frame rather than borrowed from it: redraw() below
        // needs `frame` mutably, and a live borrow of the render state would
        // hold it. RenderState is a handful of Arcs, so this is cheap.
        let rs = frame.wgpu_render_state().cloned()?;
        let (fw, fh) = (self.out_w(), self.out_h());
        let count = count.max(1);
        let duration = self
            .loaded
            .as_ref()
            .and_then(|l| l.model.animations.get(self.anim))
            .map(|a| a.duration)
            .unwrap_or(0.0);
        let was = self.time;
        let mut out = Vec::with_capacity(count as usize);

        // EVEN SPACING IN EVERY MODE, but the span depends on the mode, and
        // one rule cannot serve both.
        //
        //   LOOP wraps frame N-1 back to frame 0, so t=duration is the SAME
        //   pose as t=0. Including it plays the first pose twice at the wrap,
        //   which is the stutter that stops a loop being seamless. Dividing by
        //   `count` stops one step short, so the wrap is one even step like
        //   every other.
        //
        //   PING_PONG and ONCE do not wrap. They reverse or stop at the end, so
        //   the extremes are exactly what should be shown, and dividing by
        //   `count - 1` puts frame 0 at t=0 and the last at t=duration.
        //
        // Either way the steps are equal; what changes is whether the endpoint
        // belongs to the sequence.
        let span = self.span(count) as f32;
        for i in 0..count {
            self.time = if duration > 0.0 {
                duration * (i as f32) / span
            } else {
                0.0
            };
            self.redraw(frame);
            let (_, _, pixels) = self.read_frame(&rs)?;
            out.push(pixels);
        }
        self.time = was;
        Some((fw, fh, out))
    }

    /// Those frames laid out in one row, which is the shape the mod reads.
    fn bake(&mut self, frame: &mut eframe::Frame) -> Option<(u32, u32, Vec<u8>)> {
        if self.rotations > 1 {
            return self.bake_rotations(frame);
        }
        let (fw, fh, frames) = self.bake_frames(frame, self.frames.max(1))?;
        let count = frames.len() as u32;
        let mut sheet = vec![0u8; (fw * count * fh * 4) as usize];
        for (i, pixels) in frames.iter().enumerate() {
            for row in 0..fh {
                let src = (row * fw * 4) as usize;
                let dst = ((row * fw * count + i as u32 * fw) * 4) as usize;
                sheet[dst..dst + (fw * 4) as usize]
                    .copy_from_slice(&pixels[src..src + (fw * 4) as usize]);
            }
        }
        Some((fw * count, fh, sheet))
    }

    /// The same clip seen from all the way round, one rotation per ROW.
    ///
    /// This is the Doom idea in the shape the mod can read. `export_doom`
    /// writes the same views as separate lumps because that is what Doom wants;
    /// the mod wants one PNG and a grid, and turning eight files back into a
    /// sheet by hand is exactly the sort of step that ends up done differently
    /// each time. Row 0 is the yaw on screen now, and the rest step round from
    /// it, matching what `MonsterSprites.directionAt` expects of row 0.
    ///
    /// Every rotation of a given frame is posed at the SAME instant, so a
    /// walking monster's eight angles are one moment seen from around it rather
    /// than eight moments -- the same rule `export_doom` states and for the same
    /// reason.
    fn bake_rotations(&mut self, frame: &mut eframe::Frame) -> Option<(u32, u32, Vec<u8>)> {
        let rotations = self.rotations.max(1);
        let columns = self.frames.max(1);
        let start_yaw = self.yaw;
        let step = 360.0 / rotations as f32;

        let mut rows: Vec<(u32, u32, Vec<Vec<u8>>)> = Vec::new();
        for rot in 0..rotations {
            self.yaw = start_yaw + step * rot as f32;
            self.framing = None; // the camera moved
            let baked = self.bake_frames(frame, columns);
            if baked.is_none() {
                self.yaw = start_yaw;
                self.framing = None;
                return None;
            }
            rows.push(baked.unwrap());
        }
        self.yaw = start_yaw;
        self.framing = None;

        let (fw, fh, _) = &rows[0];
        let (fw, fh) = (*fw, *fh);
        let sheet_w = fw * columns;
        let sheet_h = fh * rotations;
        let mut sheet = vec![0u8; (sheet_w * sheet_h * 4) as usize];
        for (r, (_, _, frames)) in rows.iter().enumerate() {
            for (c, pixels) in frames.iter().enumerate() {
                for y in 0..fh {
                    let src = (y * fw * 4) as usize;
                    let dst = (((r as u32 * fh + y) * sheet_w + c as u32 * fw) * 4) as usize;
                    sheet[dst..dst + (fw * 4) as usize]
                        .copy_from_slice(&pixels[src..src + (fw * 4) as usize]);
                }
            }
        }
        Some((sheet_w, sheet_h, sheet))
    }

    fn snapshot(&self) -> Settings {
        Settings {
            distance: self.distance,
            yaw: self.yaw,
            pitch: self.pitch,
            roll: self.roll,
            off_x: self.off_x,
            off_y: self.off_y,
            light_az: self.light_az,
            light_el: self.light_el,
            light_power: self.light_power,
            ambient: self.ambient,
            gamma: self.gamma,
            brightness: self.brightness,
            contrast: self.contrast,
            blur: self.blur,
            ps2_filter: self.ps2_filter,
            shadows: self.shadows,
            highlights: self.highlights,
            bg_on: self.bg_on,
            bg: self.bg,
            frame_w: self.frame_w,
            frame_h: self.frame_h,
            scale: self.scale,
            ssaa: self.ssaa,
            aspect: self.aspect,
            base: self.base,
            frames: self.frames,
            rotations: self.rotations,
            ticks: self.ticks,
            match_timing: self.match_timing,
            gif_fps: self.gif_fps,
            loop_mode: self.loop_mode,
            bob: self.bob,
            kind: self.kind.clone(),
        }
    }

    fn restore(&mut self, s: Settings) {
        self.distance = s.distance;
        self.yaw = s.yaw;
        self.pitch = s.pitch;
        self.roll = s.roll;
        self.off_x = s.off_x;
        self.off_y = s.off_y;
        self.light_az = s.light_az;
        self.light_el = s.light_el;
        self.light_power = s.light_power;
        self.ambient = s.ambient;
        self.gamma = s.gamma;
        self.brightness = s.brightness;
        self.contrast = s.contrast;
        self.blur = s.blur;
        self.ps2_filter = s.ps2_filter;
        self.shadows = s.shadows;
        self.highlights = s.highlights;
        self.bg_on = s.bg_on;
        self.bg = s.bg;
        self.frame_w = s.frame_w;
        self.frame_h = s.frame_h;
        self.scale = s.scale;
        self.ssaa = s.ssaa;
        self.aspect = s.aspect;
        self.base = s.base;
        self.frames = s.frames;
        self.rotations = s.rotations;
        self.ticks = s.ticks;
        self.match_timing = s.match_timing;
        self.gif_fps = s.gif_fps;
        self.loop_mode = s.loop_mode;
        self.bob = s.bob;
        self.kind = s.kind.clone();
        // The camera moved, so the held framing is stale.
        self.framing = None;
        self.last = Some(self.snapshot());
    }

    /// Records a step once an interaction has FINISHED.
    ///
    /// Committing on every change would put one entry per pixel of a slider
    /// drag on the stack, so a single drag would take fifty presses to undo.
    /// Nothing is recorded while a mouse button is down; the whole drag lands
    /// as one step when it is released.
    fn commit(&mut self, ctx: &egui::Context) {
        let now = self.snapshot();
        let Some(last) = self.last.clone() else {
            self.last = Some(now);
            return;
        };
        if now == last {
            return;
        }
        if ctx.input(|i| i.pointer.any_down()) {
            return;
        }
        self.undo.push(last);
        // A new edit is a new branch; whatever was undone is not coming back.
        self.redo.clear();
        if self.undo.len() > 200 {
            self.undo.remove(0);
        }
        self.last = Some(now);
    }

    fn undo(&mut self) {
        if let Some(prev) = self.undo.pop() {
            self.redo.push(self.snapshot());
            self.restore(prev);
            self.status = format!("undo  ({} left)", self.undo.len());
        }
    }

    fn redo(&mut self) {
        if let Some(next) = self.redo.pop() {
            self.undo.push(self.snapshot());
            self.restore(next);
            self.status = format!("redo  ({} left)", self.redo.len());
        }
    }

    fn rescan_presets(&mut self) {
        self.presets.clear();
        if let Ok(dir) = std::fs::read_dir(preset_dir()) {
            for item in dir.flatten() {
                let path = item.path();
                if path.extension().and_then(|e| e.to_str()) == Some("txt") {
                    if let Some(stem) = path.file_stem().and_then(|s| s.to_str()) {
                        self.presets.push(stem.to_string());
                    }
                }
            }
        }
        self.presets.sort();
    }

    /// The look, written as plain `key=value` lines.
    ///
    /// Deliberately not the model, the card or the clip: a preset is how a
    /// monster is LIT and FRAMED, and those three are what makes one monster
    /// different from another. Saving them would mean every preset silently
    /// carried somebody else's identity.
    fn save_preset(&mut self) {
        let name: String = self
            .preset_name
            .trim()
            .chars()
            .filter(|c| c.is_ascii_alphanumeric() || *c == '_' || *c == '-')
            .collect();
        if name.is_empty() {
            self.status = "give the preset a name first".into();
            return;
        }
        let dir = preset_dir();
        if let Err(e) = std::fs::create_dir_all(&dir) {
            self.status = format!("{e}");
            return;
        }
        let body = format!(
            "distance={}\nyaw={}\npitch={}\nroll={}\noff_x={}\noff_y={}\n\
             light_az={}\nlight_el={}\nlight_power={}\nambient={}\n\
             gamma={}\nbrightness={}\ncontrast={}\nblur={}\nps2={}\n\
             shadows={}\nhighlights={}\nbg_on={}\nbg={:02x}{:02x}{:02x}\n\
             frame_w={}\nframe_h={}\nscale={}\nssaa={}\naspect={}\nbase={}\n\
             frames={}\nrotations={}\nticks={}\nmatch_timing={}\ngif_fps={}\nloop={}\nbob={}\nkind={}\n",
            self.distance, self.yaw, self.pitch, self.roll, self.off_x, self.off_y,
            self.light_az, self.light_el, self.light_power, self.ambient,
            self.gamma, self.brightness, self.contrast, self.blur, self.ps2_filter,
            self.shadows, self.highlights, self.bg_on,
            self.bg.r(), self.bg.g(), self.bg.b(),
            self.frame_w, self.frame_h, self.scale, self.ssaa, self.aspect, self.base,
            self.frames, self.rotations, self.ticks, self.match_timing, self.gif_fps,
            self.loop_mode.json(), self.bob, self.kind,
        );
        let file = dir.join(format!("{name}.txt"));
        match std::fs::write(&file, body) {
            Ok(()) => {
                self.status = format!("saved preset {name}");
                self.rescan_presets();
            }
            Err(e) => self.status = format!("{e}"),
        }
    }

    fn load_preset(&mut self, name: &str) {
        let file = preset_dir().join(format!("{name}.txt"));
        let Ok(text) = std::fs::read_to_string(&file) else {
            self.status = format!("could not read {}", file.display());
            return;
        };
        for line in text.lines() {
            let Some((key, value)) = line.split_once('=') else { continue };
            let value = value.trim();
            let f = || value.parse::<f32>().ok();
            let u = || value.parse::<u32>().ok();
            match key.trim() {
                "distance" => self.distance = f().unwrap_or(self.distance),
                "yaw" => self.yaw = f().unwrap_or(self.yaw),
                "pitch" => self.pitch = f().unwrap_or(self.pitch),
                "roll" => self.roll = f().unwrap_or(self.roll),
                // Absent from presets written before the offset existed, and
                // unwrap_or leaves those at whatever is current rather than
                // silently re-centring a frame someone had already aligned.
                "off_x" => self.off_x = f().unwrap_or(self.off_x),
                "off_y" => self.off_y = f().unwrap_or(self.off_y),
                "light_az" => self.light_az = f().unwrap_or(self.light_az),
                "light_el" => self.light_el = f().unwrap_or(self.light_el),
                "light_power" => self.light_power = f().unwrap_or(self.light_power),
                "ambient" => self.ambient = f().unwrap_or(self.ambient),
                "gamma" => self.gamma = f().unwrap_or(self.gamma),
                "brightness" => self.brightness = f().unwrap_or(self.brightness),
                "contrast" => self.contrast = f().unwrap_or(self.contrast),
                "blur" => self.blur = f().unwrap_or(self.blur).clamp(0.0, 4.0),
                "ps2" => self.ps2_filter = value == "true",
                "bg_on" => self.bg_on = value == "true",
                "bg" => {
                    if value.len() == 6 {
                        if let Ok(n) = u32::from_str_radix(value, 16) {
                            self.bg = egui::Color32::from_rgb(
                                (n >> 16) as u8,
                                (n >> 8) as u8,
                                n as u8,
                            );
                        }
                    }
                }
                "shadows" => self.shadows = f().unwrap_or(self.shadows).clamp(-1.0, 1.0),
                "highlights" => {
                    self.highlights = f().unwrap_or(self.highlights).clamp(-1.0, 1.0)
                }
                "frame_w" => self.frame_w = u().unwrap_or(self.frame_w),
                "frame_h" => self.frame_h = u().unwrap_or(self.frame_h),
                "scale" => self.scale = u().unwrap_or(self.scale).clamp(1, 8),
                // Absent from presets written before this existed, which then
                // keep whatever is current rather than snapping back to off.
                "ssaa" => self.ssaa = u().unwrap_or(self.ssaa).clamp(1, 4),
                "aspect" => {
                    self.aspect = u().unwrap_or(0) as usize;
                    if self.aspect >= ASPECTS.len() {
                        self.aspect = ASPECTS.len() - 1;
                    }
                }
                "base" => self.base = u().unwrap_or(self.base).clamp(8, 1024),
                "frames" => self.frames = u().unwrap_or(self.frames).clamp(1, 120),
                "rotations" => self.rotations = u().unwrap_or(self.rotations).max(1),
                "ticks" => self.ticks = u().unwrap_or(self.ticks).clamp(1, 40),
                "bob" => self.bob = u().unwrap_or(self.bob).clamp(0, 32),
                "match_timing" => self.match_timing = value == "true",
                "gif_fps" => self.gif_fps = u().unwrap_or(self.gif_fps).clamp(0, 60),
                "loop" => {
                    self.loop_mode = match value {
                        "LOOP" => LoopMode::Loop,
                        "ONCE" => LoopMode::Once,
                        _ => LoopMode::PingPong,
                    }
                }
                // The type folder travels because it is a property of the SHEET
                // rather than of the monster -- every dragon lands in the same
                // folder, and that is the point of a preset.
                "kind" => self.kind = value.to_string(),
                _ => {}
            }
        }
        // The camera changed, so the held framing is stale.
        self.framing = None;
        self.status = format!("loaded preset {name}");
    }

    /// Eight rotations of every frame, named the way Doom expects.
    ///
    /// `NAMEFR.png` -- four characters of sprite name, one frame letter, one
    /// rotation digit 1..8. Rotation 1 is the front view, which is whatever the
    /// yaw slider is currently showing, and the rest step round in 45 degrees.
    ///
    /// Each is written separately rather than using Doom's mirrored-pair naming
    /// (`TROOA2A8`): mirroring is only correct for a symmetrical monster, and
    /// deciding which of these are symmetrical is not something this can know.
    fn export_doom(&mut self, frame: &mut eframe::Frame) {
        let Some(dir) = rfd::FileDialog::new().pick_folder() else { return };
        let name: String = self
            .doom_name
            .chars()
            .filter(|c| c.is_ascii_alphanumeric())
            .take(4)
            .collect::<String>()
            .to_ascii_uppercase();
        if name.len() != 4 {
            self.status = "a Doom sprite name is exactly four characters".into();
            return;
        }

        let start_yaw = self.yaw;
        let frames = self.frames.max(1);
        let mut written = 0usize;
        let mut failed: Option<String> = None;

        'outer: for f in 0..frames {
            let letter = (self.doom_letter as u8 + f as u8) as char;
            if !letter.is_ascii_uppercase() {
                failed = Some("ran past Z; start earlier or use fewer frames".into());
                break;
            }
            for rot in 0..8u32 {
                let step = if self.doom_reverse { -45.0 } else { 45.0 };
                self.yaw = start_yaw + step * rot as f32;
                self.framing = None; // the camera moved

                // One frame of the clip per letter, all eight rotations of it
                // at the same instant -- so a walking monster's eight angles
                // are the same moment seen from around it, not eight moments.
                let duration = self.duration();
                let span = if self.loop_mode == LoopMode::Loop {
                    frames as f32
                } else {
                    (frames as f32 - 1.0).max(1.0)
                };
                self.time = if duration > 0.0 { duration * f as f32 / span } else { 0.0 };
                self.redraw(frame);

                let Some(rs) = frame.wgpu_render_state().cloned() else { break 'outer };
                let Some((w, h, pixels)) = self.read_frame(&rs) else { break 'outer };
                let Some(buffer) = image::RgbaImage::from_raw(w, h, pixels) else {
                    failed = Some("a frame did not fit".into());
                    break 'outer;
                };

                let file = dir.join(format!("{name}{letter}{}.png", rot + 1));
                let mut encoded: Vec<u8> = Vec::new();
                if let Err(e) = buffer.write_to(
                    &mut std::io::Cursor::new(&mut encoded),
                    image::ImageFormat::Png,
                ) {
                    failed = Some(format!("{e}"));
                    break 'outer;
                }
                if self.doom_grab {
                    // Doom hangs a sprite from an origin, not a corner: x at the
                    // middle and y at the feet, or the monster floats and drifts
                    // sideways as its frames change width.
                    encoded = with_grab(&encoded, (w / 2) as i32, h as i32);
                }
                if let Err(e) = std::fs::write(&file, &encoded) {
                    failed = Some(format!("{e}"));
                    break 'outer;
                }
                written += 1;
            }
        }

        self.yaw = start_yaw;
        self.framing = None;
        self.status = match failed {
            Some(e) => format!("wrote {written} then stopped: {e}"),
            None => format!("wrote {written} sprites to {}", dir.display()),
        };
    }

    /// Writes wherever you point it, in whichever format is chosen.
    fn export_as(&mut self, frame: &mut eframe::Frame) {
        let name = self
            .selected
            .and_then(|i| self.found.get(i))
            .map(|e| e.name.clone())
            .unwrap_or_else(|| "monster".into());
        let format = self.export_as;
        let Some(path) = rfd::FileDialog::new()
            .set_file_name(format!("{name}.{}", format.ext()))
            .add_filter(format.label(), &[format.ext()])
            .save_file()
        else {
            return;
        };

        match format {
            Format::Png => {
                let Some((w, h, pixels)) = self.bake(frame) else {
                    self.status = "nothing to bake".into();
                    return;
                };
                match image::RgbaImage::from_raw(w, h, pixels) {
                    Some(buffer) => match buffer.save(&path) {
                        Ok(()) => self.status = format!("wrote {}", path.display()),
                        Err(e) => self.status = format!("{e}"),
                    },
                    None => self.status = "the baked pixels did not fit the sheet".into(),
                }
            }
            Format::Gif => match self.write_gif(frame, &path) {
                Ok(n) => {
                    // Size is the OTHER reason a GIF plays badly: every frame
                    // carries its own palette, so a long one at a large size is
                    // tens of megabytes and stutters on decode alone, whatever
                    // the delay says.
                    let mb = std::fs::metadata(&path)
                        .map(|m| m.len() as f64 / 1_048_576.0)
                        .unwrap_or(0.0);
                    self.status = format!(
                        "wrote {} -- {n} frames at {:.1} fps, {mb:.1} MB",
                        path.display(),
                        self.gif_actual_fps()
                    );
                }
                Err(e) => self.status = e,
            },
        }
    }

    /// An animated GIF at the same pace the game will play the sprite.
    fn write_gif(&mut self, frame: &mut eframe::Frame, path: &Path) -> Result<usize, String> {
        // At the GIF's own count, which the rate decides.
        let wanted = self.gif_frame_count();
        let Some((fw, fh, mut frames)) = self.bake_frames(frame, wanted) else {
            return Err("nothing to bake".into());
        };
        if frames.is_empty() {
            return Err("no frames".into());
        }

        // PING_PONG really does play back down the list, so the GIF does too --
        // otherwise the preview a duellist sees here is a different animation
        // from the one the game will run. Endpoints are not repeated, or they
        // hold for two frames at each turn.
        if self.loop_mode == LoopMode::PingPong && frames.len() > 2 {
            let back: Vec<Vec<u8>> = frames[1..frames.len() - 1].iter().rev().cloned().collect();
            frames.extend(back);
        }

        let file = std::fs::File::create(path).map_err(|e| format!("{e}"))?;
        let mut encoder = image::codecs::gif::GifEncoder::new(file);
        encoder
            .set_repeat(match self.loop_mode {
                LoopMode::Once => image::codecs::gif::Repeat::Finite(1),
                _ => image::codecs::gif::Repeat::Infinite,
            })
            .map_err(|e| format!("{e}"))?;

        // An EXACT multiple of 10ms, so nothing downstream has to round and
        // nothing lands on the 1cs value viewers reinterpret. See gif_delay_cs.
        let ms = self.gif_delay_cs() * 10;
        let count = frames.len();
        for pixels in frames {
            let buffer = image::RgbaImage::from_raw(fw, fh, pixels)
                .ok_or_else(|| "a frame did not fit".to_string())?;
            let delay = image::Delay::from_numer_denom_ms(ms, 1);
            encoder
                .encode_frame(image::Frame::from_parts(buffer, 0, 0, delay))
                .map_err(|e| format!("{e}"))?;
        }
        Ok(count)
    }

    /// The union of every frame's opaque pixels, in output pixels.
    ///
    /// Every frame of the clip, and every rotation when there are several,
    /// because the point is the range the WHOLE animation needs: a crop fitted
    /// to the idle pose clips the wings off the frame where they spread, and a
    /// crop fitted to one rotation clips the profile of a monster that is
    /// wider than it is deep.
    ///
    /// Returns `None` when nothing is drawn at all -- an empty clip, or a model
    /// that has ended up entirely outside the frame -- because "the bounds of
    /// nothing" has no sensible answer and the caller must not act on one.
    fn content_bounds(&mut self, frame: &mut eframe::Frame) -> Option<(u32, u32, u32, u32)> {
        let rotations = self.rotations.max(1);
        let columns = self.frames.max(1);
        let start_yaw = self.yaw;
        let step = 360.0 / rotations as f32;

        let (mut x0, mut y0) = (u32::MAX, u32::MAX);
        let (mut x1, mut y1) = (0u32, 0u32);
        let mut any = false;

        for rot in 0..rotations {
            if rotations > 1 {
                self.yaw = start_yaw + step * rot as f32;
                self.framing = None;
            }
            let Some((w, h, frames)) = self.bake_frames(frame, columns) else {
                self.yaw = start_yaw;
                self.framing = None;
                return None;
            };
            for pixels in &frames {
                if let Some((fx0, fy0, fx1, fy1)) = opaque_bounds(pixels, w, h) {
                    any = true;
                    x0 = x0.min(fx0);
                    y0 = y0.min(fy0);
                    x1 = x1.max(fx1);
                    y1 = y1.max(fy1);
                }
            }
        }
        self.yaw = start_yaw;
        self.framing = None;
        any.then_some((x0, y0, x1, y1))
    }

    /// Zooms and pans until the animation fills the frame.
    ///
    /// <b>Measured, then corrected, then measured again.</b> The pan is in units
    /// of the model's radius and the zoom is a multiple of it, so moving the
    /// camera changes what a unit of pan is worth -- which means one analytic
    /// pass lands close but not on it. Three passes of measure-and-correct
    /// converge without anybody having to be right about the algebra, and each
    /// pass is a bake the tool already knows how to do.
    ///
    /// The aspect is left alone. Fitting the frame to the content would change
    /// the sprite's SHAPE, and the shape is a decision about how the monster
    /// stands on its card -- this is a decision about where the camera is.
    ///
    /// A small margin is kept because the blur and the antialiasing both spread
    /// a silhouette outward by a pixel or so after this has measured it, and a
    /// crop that fits exactly is a crop that clips once those are applied.
    fn auto_crop(&mut self, frame: &mut eframe::Frame) {
        const MARGIN: f32 = 0.02;
        let before = (self.distance, self.off_x, self.off_y);
        let fov_half = (35f32.to_radians() * 0.5).tan();

        for _ in 0..3 {
            let (fw, fh) = (self.out_w() as f32, self.out_h() as f32);
            let Some((x0, y0, x1, y1)) = self.content_bounds(frame) else {
                self.status = "nothing visible to crop to".into();
                self.distance = before.0;
                self.off_x = before.1;
                self.off_y = before.2;
                return;
            };
            // +1 because the bounds are inclusive: a single lit pixel is one
            // pixel wide, not zero.
            let (bw, bh) = ((x1 - x0 + 1) as f32, (y1 - y0 + 1) as f32);

            // How much of the frame the content uses on its tighter axis. The
            // LARGER fraction is the one that decides the zoom -- fitting to
            // the other would push this one off the edge.
            let fill = (bw / fw).max(bh / fh);
            if fill <= 0.0 {
                break;
            }

            // The content's centre against the frame's, as a fraction of the
            // half-frame, which is what a pan is measured in.
            let cx = ((x0 as f32 + x1 as f32 + 1.0) * 0.5 - fw * 0.5) / (fw * 0.5);
            let cy = ((y0 as f32 + y1 as f32 + 1.0) * 0.5 - fh * 0.5) / (fh * 0.5);

            // Pan first, at the CURRENT zoom, because the conversion below is
            // in terms of it. Screen y runs down and the pan runs up.
            let per_unit = self.distance * fov_half;
            self.off_x += cx * per_unit * (fw / fh);
            self.off_y -= cy * per_unit;
            // Then close the gap to the frame, leaving the margin.
            self.distance = (self.distance * fill / (1.0 - MARGIN)).clamp(0.05, 20.0);
            self.framing = None;
        }
        // Nothing to record: `commit` compares snapshots every frame and lands
        // the whole button press as one step, the same as a slider drag.
        self.status = format!(
            "cropped to the clip: dist {:.2}, shift {:.2}, {:.2}",
            self.distance, self.off_x, self.off_y
        );
    }

    /// Bakes and shows it, without touching the disk.
    fn preview_sheet(&mut self, ctx: &egui::Context, frame: &mut eframe::Frame) {
        let Some((w, h, pixels)) = self.bake(frame) else {
            self.status = "nothing to bake".into();
            return;
        };
        let image =
            egui::ColorImage::from_rgba_unmultiplied([w as usize, h as usize], &pixels);
        self.sheet =
            Some(ctx.load_texture("sheet", image, egui::TextureOptions::NEAREST));
        self.show_sheet = true;
        self.status = format!("previewing {w}x{h} -- not saved yet");
    }

    /// Bakes every frame into one sheet and writes it where the game looks.
    fn save(&mut self, frame: &mut eframe::Frame) {
        let Some(index) = self.selected else {
            self.status = "pick a model first".into();
            return;
        };
        let (config, name) = {
            let e = &self.found[index];
            (e.config.clone(), e.name.clone())
        };
        let Some((sheet_w, fh, sheet)) = self.bake(frame) else {
            self.status = "nothing to bake".into();
            return;
        };

        let dir = config.join("sheets").join(&self.kind);
        if let Err(e) = std::fs::create_dir_all(&dir) {
            self.status = format!("could not make {}: {e}", dir.display());
            return;
        }
        let png = dir.join(format!("{name}.png"));
        let buffer =
            match image::RgbaImage::from_raw(sheet_w, fh, sheet) {
                Some(b) => b,
                None => {
                    self.status = "the baked pixels did not fit the sheet".into();
                    return;
                }
            };
        if let Err(e) = buffer.save(&png) {
            self.status = format!("could not write {}: {e}", png.display());
            return;
        }

        match self.write_entry(&config, &name) {
            Ok(card) => {
                self.status =
                    format!("saved {} and listed card {card}", png.display());
            }
            Err(e) => {
                self.status = format!("sheet saved to {}, but {e}", png.display());
            }
        }
    }

    /// Upserts this monster into the profile's `monster_sprites.json`.
    fn write_entry(&self, config: &Path, name: &str) -> Result<String, String> {
        let card: i64 = self
            .card
            .trim()
            .parse()
            .map_err(|_| "no passcode given, so nothing was listed".to_string())?;
        let file = config.join("monster_sprites.json");
        let text = std::fs::read_to_string(&file).unwrap_or_else(|_| "[]".into());
        // Hoisted, because both the fresh block and the patch below need it.
        let anim = self
            .loaded
            .as_ref()
            .and_then(|l| l.model.animations.get(self.anim))
            .map(|a| a.name.clone())
            .unwrap_or_default();

        // Written by hand rather than with a JSON crate: the file is a flat
        // array of flat objects, and the mod's reader accepts exactly the shape
        // below. Rewriting the whole file through a serialiser risks reordering
        // or dropping keys this tool does not know about.
        let entry = format!(
            "  {{\n    \"card\": {card},\n    \"body\": {{\n      \
             \"sheet\": \"{kind}/{name}\",\n      \"x\": 0,\n      \"y\": 0,\n      \
             \"w\": 0,\n      \"h\": 0,\n      \"columns\": {cols},\n      \
             \"rows\": {rows},\n      \"directions\": {dirs},\n      \"first\": 0,\n      \"frames\": {frames},\n      \
             \"ticks\": {ticks},\n      \"loop\": \"{mode}\",\n      \
             \"bob\": {bob},\n      \
             \"trimX\": 0,\n      \"trimY\": 0\n    }},\n    \"scale\": 1.0,\n    \
             \"model\": \"{name}\",\n    \"animation\": \"{anim}\"\n  }}",
            kind = self.kind,
            cols = self.frames.max(1),
            rows = self.rotations.max(1),
            dirs = self.rotations.max(1),
            frames = self.frames.max(1),
            ticks = self.effective_ticks(),
            mode = self.loop_mode.json(),
            bob = if self.bob < 2 { 0 } else { self.bob },
            anim = anim,
        );

        // AN EXISTING ENTRY IS PATCHED, NOT REPLACED.
        //
        // Size and position belong to the game. They are set by looking at the
        // monster standing on its card in the world, which is the only place
        // they can be judged, and this tool has no idea what they should be --
        // it renders into a frame, not onto a pedestal. Rebuilding the block
        // from scratch, which is what this did, wrote `"scale": 1.0` and no
        // placement at all over whatever had been tuned. Re-baking a sheet to
        // fix one frame therefore silently reset the monster's height and put
        // it back in the middle of its card.
        //
        // So a re-bake now overwrites only what the BAKE decides -- the sheet
        // it just wrote, the grid it wrote it in, the frame count, the timing,
        // and which clip was posed -- and leaves every other key exactly as it
        // found it, including ones this does not know the meaning of.
        let mut kept: Vec<String> = Vec::new();
        let mut patched = false;
        for block in split_objects(&text) {
            if !is_card(&block, card) {
                kept.push(block);
                continue;
            }
            kept.push(patch_entry(&block, &[
                ("sheet", format!("\"{}/{name}\"", self.kind)),
                ("columns", format!("{}", self.frames.max(1))),
                ("rows", format!("{}", self.rotations.max(1))),
                ("first", "0".to_string()),
                ("frames", format!("{}", self.frames.max(1))),
                ("ticks", format!("{}", self.effective_ticks())),
                ("loop", format!("\"{}\"", self.loop_mode.json())),
                ("bob", format!("{}", if self.bob < 2 { 0 } else { self.bob })),
                ("model", format!("\"{name}\"")),
                ("animation", format!("\"{anim}\"")),
            ]));
            // Added rather than merely replaced -- see ensure_scalar. An
            // entry written before rotations existed has no such key, and
            // that is exactly the entry someone is now re-baking with eight.
            let last = kept.len() - 1;
            kept[last] = ensure_scalar(
                &kept[last],
                "directions",
                &format!("{}", self.rotations.max(1)),
                "rows",
            );
            patched = true;
        }
        if !patched {
            // Nothing to preserve, so the freshly built block above stands. It
            // carries no placement keys at all, which leaves the game on its
            // own defaults rather than asserting a position this cannot know.
            kept.push(entry);
        }
        let out = format!("[\n{}\n]\n", kept.join(",\n"));
        std::fs::write(&file, out).map_err(|e| format!("{e}"))?;
        Ok(card.to_string())
    }
}

impl eframe::App for App {
    // egui 0.36 hands the app a Ui rather than a Context, and panels are shown
    // inside it. `logic` is where non-drawing work goes; the animation clock
    // lives here because it only matters when something is being drawn.
    fn ui(&mut self, ui: &mut egui::Ui, frame: &mut eframe::Frame) {
        let ctx = ui.ctx().clone();
        if self.playing {
            let dt = ctx.input(|i| i.stable_dt).min(0.05);
            let duration = self
                .loaded
                .as_ref()
                .and_then(|l| l.model.animations.get(self.anim))
                .map(|a| a.duration)
                .unwrap_or(0.0);
            if duration > 0.0 {
                // The preview plays what the loop setting says, so PING_PONG
                // and LOOP look different HERE rather than only once exported.
                // They were both a plain wrap before, which is why a clip set
                // to LOOP still appeared to bounce.
                let ping = self.loop_mode == LoopMode::PingPong;
                let period = if ping { duration * 2.0 } else { duration };
                self.phase = (self.phase + dt) % period;
                self.time = if ping && self.phase > duration {
                    period - self.phase
                } else {
                    self.phase
                };
            }
            ctx.request_repaint();
        }

        // Ctrl+Z / Ctrl+Shift+Z. Consumed so a text field cannot swallow them,
        // and read before the panels so a press acts on this frame's draw.
        let (undo_pressed, redo_pressed) = ctx.input_mut(|i| {
            let undo = i.consume_key(egui::Modifiers::COMMAND, egui::Key::Z);
            let redo = i.consume_key(
                egui::Modifiers::COMMAND | egui::Modifiers::SHIFT,
                egui::Key::Z,
            ) || i.consume_key(egui::Modifiers::COMMAND, egui::Key::Y);
            (undo, redo)
        });
        if redo_pressed {
            self.redo();
        } else if undo_pressed {
            self.undo();
        }

        let mut pick: Option<usize> = None;

        egui::Panel::left("controls").exact_size(292.0).show(ui, |ui| {
            // Tighter than the default. The panel carries about forty controls
            // and the default spacing put a third of them below the fold.
            ui.spacing_mut().item_spacing.y = 3.0;
            ui.spacing_mut().slider_width = 118.0;
            ui.spacing_mut().interact_size.y = 18.0;
            egui::ScrollArea::vertical().show(ui, |ui| {
            ui.strong("Models");
            ui.horizontal(|ui| {
                ui.add(
                    egui::TextEdit::singleline(&mut self.filter)
                        .hint_text("search")
                        .desired_width(178.0),
                );
                if ui.small_button("x").on_hover_text("clear").clicked() {
                    self.filter.clear();
                }
                if ui.small_button("rescan").clicked() {
                    self.rescan();
                }
            });

            let needle = self.filter.to_lowercase();
            let matches: Vec<usize> = self
                .found
                .iter()
                .enumerate()
                .filter(|(_, e)| needle.is_empty() || e.name.to_lowercase().contains(&needle))
                .map(|(i, _)| i)
                .collect();
            ui.label(
                egui::RichText::new(format!("{} of {}", matches.len(), self.found.len()))
                    .small()
                    .weak(),
            );
            egui::ScrollArea::vertical()
                .id_salt("models")
                .max_height(132.0)
                .show(ui, |ui| {
                    for i in matches {
                        let selected = self.selected == Some(i);
                        if ui.selectable_label(selected, &self.found[i].name).clicked() {
                            pick = Some(i);
                        }
                    }
                });

            ui.separator();
            ui.strong("Animation");
            let names: Vec<String> = self
                .loaded
                .as_ref()
                .map(|l| l.model.animations.iter().map(|a| a.name.clone()).collect())
                .unwrap_or_default();
            if names.is_empty() {
                ui.label("none in this model");
            } else {
                let current = names
                    .get(self.anim)
                    .cloned()
                    .unwrap_or_else(|| "-".into());
                egui::ComboBox::from_id_salt("clip")
                    .selected_text(current)
                    .width(150.0)
                    .show_ui(ui, |ui| {
                        for (i, n) in names.iter().enumerate() {
                            if ui.selectable_label(self.anim == i, n).clicked() {
                                self.anim = i;
                                self.time = 0.0;
                                self.framing = None;
                            }
                        }
                    });
                ui.horizontal(|ui| {
                    if ui.button(if self.playing { "Pause" } else { "Play" }).clicked() {
                        self.playing = !self.playing;
                    }
                    let duration = self
                        .loaded
                        .as_ref()
                        .and_then(|l| l.model.animations.get(self.anim))
                        .map(|a| a.duration)
                        .unwrap_or(1.0);
                    ui.add(
                        egui::Slider::new(&mut self.time, 0.0..=duration.max(0.001))
                            .text("t"),
                    );
                });
            }

            ui.separator();
            egui::CollapsingHeader::new("View")
                .default_open(true)
                .show(ui, |ui| {
                    ui.add(egui::Slider::new(&mut self.distance, 1.2..=6.0).text("dist"));
                    ui.add(egui::Slider::new(&mut self.yaw, -180.0..=180.0).text("yaw"));
                    ui.add(egui::Slider::new(&mut self.pitch, -89.0..=89.0).text("pitch"));
                    ui.add(egui::Slider::new(&mut self.roll, -180.0..=180.0).text("roll"));
                    ui.add(egui::Slider::new(&mut self.off_x, -1.5..=1.5).text("shift x"))
                        .on_hover_text("moves the model left/right in the frame");
                    ui.add(egui::Slider::new(&mut self.off_y, -1.5..=1.5).text("shift y"))
                        .on_hover_text(
                            "moves the model up/down in the frame. The sheet \
                             wants FEET ON THE BOTTOM EDGE -- a gap under them \
                             makes the monster hover over its card in game, and \
                             nothing in the mod can check for it.",
                        );
                    ui.horizontal(|ui| {
                        if ui
                            .small_button("centre")
                            .on_hover_text("shift back to 0, 0")
                            .clicked()
                        {
                            self.off_x = 0.0;
                            self.off_y = 0.0;
                        }
                        // The whole point of the control, one click away.
                        // Measured from the clip's bounds and the current view
                        // -- see feet_to_floor() -- so it follows yaw, pitch,
                        // roll and dist instead of being a fixed nudge.
                        // Bakes the whole clip to measure it, so it is a wait
                        // rather than a nudge -- which is why it is a button
                        // beside the manual controls rather than something that
                        // happens on its own when a model loads.
                        if ui
                            .small_button("auto crop")
                            .on_hover_text(
                                "zooms and centres so the WHOLE animation just \
                                 fits -- every frame, and every rotation when \
                                 there are eight. Leaves the aspect alone.",
                            )
                            .clicked()
                        {
                            self.auto_crop(frame);
                        }
                        if ui
                            .small_button("feet to floor")
                            .on_hover_text(
                                "drop the subject so its lowest point sits on \
                                 the bottom edge, for the whole clip",
                            )
                            .clicked()
                        {
                            self.off_y = self.feet_to_floor();
                        }
                    });
                    // Lives here rather than under the curves: this is how
                    // the model is RENDERED, not how the picture is graded
                    // afterwards. Filed under grading it was both in the
                    // wrong place and inside a section that starts
                    // collapsed, so it could not be found at all.
                    ui.checkbox(&mut self.ps2_filter, "PS2 texture filtering")
                        .on_hover_text(
                            "bilinear quantised to 1/16 of a texel, as the \
                             console did it; off is crisp nearest",
                        );
                    ui.horizontal(|ui| {
                        ui.checkbox(&mut self.bg_on, "background");
                        if self.bg_on {
                            ui.color_edit_button_srgba(&mut self.bg);
                        }
                    });
                    if self.bg_on {
                        // Dark, and in the three families a sprite is usually
                        // shown against. Light backgrounds are deliberately
                        // absent: these monsters are dark-edged and vanish into
                        // one, which is the reason a transparent sheet was the
                        // default in the first place.
                        ui.horizontal_wrapped(|ui| {
                            for (r, g, b) in BACKDROPS {
                                let colour = egui::Color32::from_rgb(*r, *g, *b);
                                if ui
                                    .add(
                                        egui::Button::new("")
                                            .fill(colour)
                                            .min_size(egui::vec2(20.0, 16.0)),
                                    )
                                    .clicked()
                                {
                                    self.bg = colour;
                                }
                            }
                        });
                    }
                    ui.horizontal(|ui| {
                        if ui
                            .small_button("reset orientation")
                            .on_hover_text("yaw, pitch and roll only")
                            .clicked()
                        {
                            // Angles alone. Distance is a framing decision and
                            // usually the one you have just got right, so it
                            // survives -- which is what separates this from the
                            // reset beside it.
                            self.yaw = 35.0;
                            self.pitch = 12.0;
                            self.roll = 0.0;
                            self.framing = None;
                        }
                    if ui.small_button("reset view").clicked() {
                        self.distance = 2.6;
                        self.yaw = 35.0;
                        self.pitch = 12.0;
                        self.roll = 0.0;
                        // Unlike "reset orientation", this one is the whole
                        // view, and a shift left behind by it would be an
                        // alignment nobody could account for.
                        self.off_x = 0.0;
                        self.off_y = 0.0;
                        self.ps2_filter = false;
                        self.bg_on = false;
                        self.framing = None;
                    }
                    });
                });

            egui::CollapsingHeader::new("Light, curves and blur")
                .default_open(false)
                .show(ui, |ui| {
                    ui.add(egui::Slider::new(&mut self.light_az, -180.0..=180.0).text("azim"));
                    ui.add(egui::Slider::new(&mut self.light_el, -89.0..=89.0).text("elev"));
                    ui.add(egui::Slider::new(&mut self.light_power, 0.0..=1.5).text("key"));
                    ui.add(egui::Slider::new(&mut self.ambient, 0.0..=1.5).text("amb"));
                    ui.add(egui::Slider::new(&mut self.gamma, 0.2..=3.0).text("gamma"));
                    ui.add(egui::Slider::new(&mut self.brightness, -0.5..=0.5).text("bright"));
                    ui.add(egui::Slider::new(&mut self.contrast, 0.2..=2.5).text("contr"));
                    ui.add(
                        egui::Slider::new(&mut self.shadows, -0.5..=0.5).text("shadows"),
                    );
                    ui.add(
                        egui::Slider::new(&mut self.highlights, -0.5..=0.5).text("highs"),
                    );
                    ui.add(
                        egui::Slider::new(&mut self.blur, 0.0..=4.0)
                            .text("blur")
                            .custom_formatter(|v, _| {
                                if v < 0.01 { "off".into() } else { format!("{v:.2}") }
                            }),
                    );
                    if ui.small_button("reset light and curves").clicked() {
                        self.light_az = 35.0;
                        self.light_el = 45.0;
                        self.light_power = 0.55;
                        self.ambient = 0.45;
                        self.gamma = 1.0;
                        self.brightness = 0.0;
                        self.contrast = 1.0;
                        self.blur = 0.0;
                        self.shadows = 0.0;
                        self.highlights = 0.0;
                    }
                });

            ui.separator();
            ui.horizontal(|ui| {
                ui.selectable_value(&mut self.mode, Mode::Sheet, "Sheet");
                ui.selectable_value(&mut self.mode, Mode::Doom, "Doom");
            });
            let custom = ASPECTS[self.aspect.min(ASPECTS.len() - 1)].1 == 0;
            egui::ComboBox::from_label("aspect")
                .selected_text(ASPECTS[self.aspect.min(ASPECTS.len() - 1)].0)
                .width(200.0)
                .show_ui(ui, |ui| {
                    for (i, (label, _, _)) in ASPECTS.iter().enumerate() {
                        if ui.selectable_label(self.aspect == i, *label).clicked() {
                            self.aspect = i;
                            self.apply_aspect();
                        }
                    }
                });
            if custom {
                ui.horizontal(|ui| {
                    ui.add(
                        egui::DragValue::new(&mut self.frame_w).range(8..=1024).prefix("w "),
                    );
                    ui.add(
                        egui::DragValue::new(&mut self.frame_h).range(8..=1024).prefix("h "),
                    );
                });
            } else {
                if ui
                    .add(egui::Slider::new(&mut self.base, 32..=512).text("long side"))
                    .changed()
                {
                    self.apply_aspect();
                }
            }
            ui.add(egui::Slider::new(&mut self.scale, 1..=8).text("res x"));
            // Beside `res x` because the two are constantly mistaken for each
            // other, and the label says which is which: one makes the sprite
            // bigger, the other makes the same sprite better.
            ui.add(
                egui::Slider::new(&mut self.ssaa, 1..=4)
                    .text("smooth x")
                    .custom_formatter(|n, _| match n as u32 {
                        1 => "off".to_string(),
                        s => format!("{s}x{s}"),
                    }),
            )
            .on_hover_text(
                "antialiasing. Draws the model this many times larger and \
                 averages it down, so an edge lands BETWEEN two pixels instead \
                 of having to choose one. Costs the square of it in render \
                 time and nothing in file size.",
            );
            ui.label(
                egui::RichText::new(format!(
                    "frame {}x{}   sheet {}x{}",
                    self.out_w(),
                    self.out_h(),
                    self.out_w() * self.frames.max(1),
                    self.out_h()
                ))
                .small(),
            );
            ui.add(
                egui::Slider::new(&mut self.frames, 1..=120)
                    .text("frames")
                    .logarithmic(true),
            );
            // Eight views or one, with nothing in between offered: the mod will
            // read any count, but 8 is what Doom established and what anybody
            // making these expects, and a monster baked at 5 rotations is one
            // whose sides do not match its front.
            let mut eight = self.rotations > 1;
            if ui
                .checkbox(&mut eight, "8 directions")
                .on_hover_text(
                    "bakes the clip from eight angles, one per ROW, and tells \
                     the game to pick a row by where the viewer is standing. \
                     Eight times the render and eight times the sheet.",
                )
                .changed()
            {
                self.rotations = if eight { 8 } else { 1 };
                self.framing = None;
            }
            ui.checkbox(&mut self.match_timing, "match model speed");
            if self.match_timing {
                let ticks = self.effective_ticks();
                let duration = self.duration();
                let count = self.frames.max(1) as f32;
                let sprite_fps = 20.0 / ticks as f32;
                let model_fps = if duration > 0.0 { count / duration } else { 0.0 };
                ui.label(
                    egui::RichText::new(format!(
                        "{ticks} ticks/frame  -  {sprite_fps:.1} fps  (clip wants {model_fps:.1})"
                    ))
                    .small(),
                );
                if duration > 0.0 && (sprite_fps - model_fps).abs() > 0.05 {
                    ui.label(
                        egui::RichText::new(
                            "rounded to whole ticks; add or remove frames to land closer",
                        )
                        .small()
                        .weak(),
                    );
                }
            } else {
                ui.add(egui::Slider::new(&mut self.ticks, 1..=40).text("ticks/frame"));
            }
            egui::ComboBox::from_id_salt("loop")
                .selected_text(self.loop_mode.json())
                .width(120.0)
                .show_ui(ui, |ui| {
                    for m in [LoopMode::Loop, LoopMode::PingPong, LoopMode::Once] {
                        if ui.selectable_label(self.loop_mode == m, m.json()).clicked() {
                            self.loop_mode = m;
                        }
                    }
                });
            // BOB IS NOT A LOOP MODE. The mod used to carry it as a third value
            // of the loop setting and moved it out deliberately, because a
            // monster can be drawn frame by frame AND drift up and down at the
            // same time -- as a loop value, every bobbing monster had to be a
            // still one. So it sits beside the loop rather than inside it.
            ui.add(
                egui::Slider::new(&mut self.bob, 0..=32)
                    .text("bob steps")
                    .custom_formatter(|v, _| {
                        if v < 2.0 { "off".to_string() } else { format!("{v:.0}") }
                    }),
            );
            ui.label(
                egui::RichText::new("bob is applied in game, not baked here")
                    .small()
                    .weak(),
            );
            ui.horizontal(|ui| {
                ui.add(
                    egui::TextEdit::singleline(&mut self.card)
                        .hint_text("card passcode")
                        .desired_width(104.0),
                );
                egui::ComboBox::from_id_salt("kind")
                    .selected_text(&self.kind)
                    .width(120.0)
                    .show_ui(ui, |ui| {
                        for k in KINDS {
                            if ui.selectable_label(self.kind == *k, *k).clicked() {
                                self.kind = (*k).into();
                            }
                        }
                    });
            });

            ui.add_space(4.0);
            if self.mode == Mode::Sheet {
                ui.horizontal(|ui| {
                    if ui.button("Preview sheet").clicked() {
                        let ctx = ui.ctx().clone();
                        self.preview_sheet(&ctx, frame);
                    }
                    if self.sheet.is_some() {
                        ui.checkbox(&mut self.show_sheet, "show it");
                    }
                });
                if ui
                    .add_sized(
                        [ui.available_width(), 26.0],
                        egui::Button::new("Save into the game"),
                    )
                    .clicked()
                {
                    self.save(frame);
                }
                ui.add_space(2.0);
                if self.export_as == Format::Gif {
                    ui.horizontal(|ui| {
                        ui.add(
                            egui::Slider::new(&mut self.gif_fps, 0..=60)
                                .text("gif fps")
                                .custom_formatter(|v, _| {
                                    if v < 1.0 { "clip".into() } else { format!("{v:.0}") }
                                }),
                        );
                    });
                    let actual = self.gif_actual_fps();
                    let asked = if self.gif_fps > 0 {
                        self.gif_fps as f32
                    } else {
                        1000.0 / self.gif_interval_ms()
                    };
                    let cs = self.gif_delay_cs();
                    let count = self.gif_frame_count();
                    let length = self.gif_length();
                    let clip = self.duration();
                    ui.label(
                        egui::RichText::new(format!(
                            "{count} frames at {actual:.1} fps ({cs} cs) = {length:.2}s"
                        ))
                        .small()
                        .weak(),
                    );
                    if clip > 0.0 {
                        // The number that matters: a GIF whose length does not
                        // match the clip is a different animation, however
                        // right its frame rate looks.
                        let reference = if self.loop_mode == LoopMode::PingPong {
                            clip * 2.0
                        } else {
                            clip
                        };
                        let drift = (length - reference).abs() / reference;
                        let text = format!("clip is {reference:.2}s");
                        ui.label(if drift > 0.05 {
                            egui::RichText::new(format!("{text} -- off by {:.0}%", drift * 100.0))
                                .small()
                                .color(egui::Color32::from_rgb(240, 190, 120))
                        } else {
                            egui::RichText::new(format!("{text} -- in step"))
                                .small()
                                .weak()
                        });
                    }
                    if asked > 50.5 {
                        ui.label(
                            egui::RichText::new(
                                "50 fps is GIF's ceiling -- delays are whole \
                                 centiseconds, so 2cs is as short as one gets",
                            )
                            .small()
                            .color(egui::Color32::from_rgb(240, 190, 120)),
                        );
                    } else if (actual - asked).abs() > 0.5 {
                        ui.label(
                            egui::RichText::new(format!(
                                "asked {asked:.0}, nearest whole centisecond is {actual:.1}"
                            ))
                            .small()
                            .weak(),
                        );
                    }
                }
                ui.horizontal(|ui| {
                    egui::ComboBox::from_id_salt("format")
                        .selected_text(self.export_as.label())
                        .width(130.0)
                        .show_ui(ui, |ui| {
                            for f in [Format::Png, Format::Gif] {
                                if ui
                                    .selectable_label(self.export_as == f, f.label())
                                    .clicked()
                                {
                                    self.export_as = f;
                                }
                            }
                        });
                    if ui.button("Export as...").clicked() {
                        self.export_as(frame);
                    }
                });
            } else {
                ui.horizontal(|ui| {
                    ui.add(
                        egui::TextEdit::singleline(&mut self.doom_name)
                            .hint_text("MNST")
                            .char_limit(4)
                            .desired_width(56.0),
                    );
                    let mut letter = self.doom_letter.to_string();
                    if ui
                        .add(
                            egui::TextEdit::singleline(&mut letter)
                                .char_limit(1)
                                .desired_width(24.0),
                        )
                        .changed()
                    {
                        if let Some(c) = letter.chars().next() {
                            if c.is_ascii_alphabetic() {
                                self.doom_letter = c.to_ascii_uppercase();
                            }
                        }
                    }
                    ui.label(
                        egui::RichText::new(format!(
                            "{}{}1..8",
                            self.doom_name.to_ascii_uppercase(),
                            self.doom_letter
                        ))
                        .small()
                        .weak(),
                    );
                });
                ui.checkbox(&mut self.doom_reverse, "reverse rotation order");
                ui.checkbox(&mut self.doom_grab, "grAb offset at the feet");
                ui.label(
                    egui::RichText::new(format!(
                        "{} frames x 8 rotations = {} files",
                        self.frames.max(1),
                        self.frames.max(1) * 8
                    ))
                    .small()
                    .weak(),
                );
                if ui
                    .add_sized(
                        [ui.available_width(), 26.0],
                        egui::Button::new("Export Doom sprites..."),
                    )
                    .clicked()
                {
                    self.export_doom(frame);
                }
            }

            ui.separator();
            ui.horizontal(|ui| {
                if ui
                    .add_enabled(!self.undo.is_empty(), egui::Button::new("undo"))
                    .on_hover_text("Ctrl+Z")
                    .clicked()
                {
                    self.undo();
                }
                if ui
                    .add_enabled(!self.redo.is_empty(), egui::Button::new("redo"))
                    .on_hover_text("Ctrl+Shift+Z")
                    .clicked()
                {
                    self.redo();
                }
                ui.label(
                    egui::RichText::new(format!(
                        "{} / {}",
                        self.undo.len(),
                        self.redo.len()
                    ))
                    .small()
                    .weak(),
                );
            });

            egui::CollapsingHeader::new("Presets")
                .default_open(false)
                .show(ui, |ui| {
            ui.horizontal(|ui| {
                ui.add(
                    egui::TextEdit::singleline(&mut self.preset_name)
                        .hint_text("preset name")
                        .desired_width(150.0),
                );
                if ui.small_button("save").clicked() {
                    self.save_preset();
                }
            });
            if self.presets.is_empty() {
                ui.label(egui::RichText::new("none saved yet").small());
            } else {
                let mut load: Option<String> = None;
                egui::ComboBox::from_id_salt("presets")
                    .selected_text("load a preset")
                    .width(ui.available_width())
                    .show_ui(ui, |ui| {
                        for name in &self.presets {
                            if ui.selectable_label(false, name).clicked() {
                                load = Some(name.clone());
                            }
                        }
                    });
                if let Some(name) = load {
                    self.preset_name = name.clone();
                    self.load_preset(&name);
                }
            }
                });

            ui.add_space(3.0);
            ui.label(egui::RichText::new(&self.status).small().weak());
            });
        });

        if let Some(i) = pick {
            self.select(i, &ctx, frame);
        }

        self.commit(&ctx);
        self.redraw(frame);
        self.refresh_preview(&ctx, frame);

        egui::CentralPanel::default().show(ui, |ui| {
            if self.loaded.is_none() {
                ui.centered_and_justified(|ui| {
                    ui.label("Pick a model on the left.");
                });
                return;
            }
            let Some(tex) = self.frame_tex.as_ref().map(|h| h.id()) else { return };
            ui.horizontal(|ui| {
                ui.label(
                    egui::RichText::new(format!(
                        "preview  {}x{}  -  sheet {}x{}",
                        self.out_w(),
                        self.out_h(),
                        self.out_w() * self.frames.max(1),
                        self.out_h()
                    ))
                    .small(),
                );
            });
            // The baked sheet, when one exists and has been asked for.
            if self.show_sheet {
                if let Some(handle) = self.sheet.as_ref() {
                    let full = handle.size_vec2();
                    let avail = ui.available_rect_before_wrap();
                    let s = (avail.width() / full.x).min(avail.height() / full.y).min(4.0);
                    let size = full * s;
                    let rect = egui::Rect::from_center_size(avail.center(), size);
                    ui.put(rect, egui::Image::new((handle.id(), size)));
                    let painter = ui.painter();
                    painter.rect_stroke(
                        rect,
                        0.0,
                        egui::Stroke::new(1.0, egui::Color32::from_rgb(255, 96, 96)),
                        egui::StrokeKind::Outside,
                    );
                    // Every frame boundary, so a clipped wing belongs to a
                    // frame rather than to the sheet in general.
                    let count = self.frames.max(1);
                    for i in 1..count {
                        let x = rect.left() + size.x * (i as f32) / (count as f32);
                        painter.line_segment(
                            [egui::pos2(x, rect.top()), egui::pos2(x, rect.bottom())],
                            egui::Stroke::new(
                                1.0,
                                egui::Color32::from_rgba_unmultiplied(255, 255, 255, 60),
                            ),
                        );
                    }
                    return;
                }
            }

            // THE CAMERA RECT, not the panel. `centered_and_justified` stretches
            // its child to fill, so the response rect was the whole window and a
            // border drawn on it said nothing about where the frame cuts.
            let avail = ui.available_rect_before_wrap();
            let fw = self.out_w() as f32;
            let fh = self.out_h() as f32;
            let s = (avail.width() / fw).min(avail.height() / fh).min(4.0).max(0.25);
            let size = egui::vec2(fw * s, fh * s);
            let rect = egui::Rect::from_center_size(avail.center(), size);
            ui.put(rect, egui::Image::new((tex, size)).fit_to_exact_size(size));

            let painter = ui.painter();
            // The frame edge. Anything touching this line is anything that gets
            // CUT in the saved sheet -- on a wide pose, the wings.
            painter.rect_stroke(
                rect,
                0.0,
                egui::Stroke::new(1.0, egui::Color32::from_rgb(255, 96, 96)),
                egui::StrokeKind::Outside,
            );
            // A warning track at 6%, so a near-miss shows before it is a hit.
            painter.rect_stroke(
                rect.shrink(size.x.min(size.y) * 0.06),
                0.0,
                egui::Stroke::new(1.0, egui::Color32::from_rgba_unmultiplied(255, 255, 255, 40)),
                egui::StrokeKind::Inside,
            );
        });
    }
}

/// Splices a `grAb` chunk into an encoded PNG.
///
/// Doom source ports read the sprite's origin from this: without it a sprite is
/// hung from its top-left corner, so a monster floats above the floor and slides
/// sideways whenever a frame changes width. The `image` crate writes no custom
/// chunks, so it goes in by hand -- straight after IHDR, which is where readers
/// expect ancillary chunks to start.
fn with_grab(png: &[u8], x: i32, y: i32) -> Vec<u8> {
    // 8 byte signature, then IHDR: 4 length + 4 type + 13 data + 4 crc.
    const AFTER_IHDR: usize = 8 + 4 + 4 + 13 + 4;
    if png.len() < AFTER_IHDR {
        return png.to_vec();
    }
    let mut body = Vec::with_capacity(8 + 4);
    body.extend_from_slice(b"grAb");
    body.extend_from_slice(&x.to_be_bytes());
    body.extend_from_slice(&y.to_be_bytes());

    let mut chunk = Vec::with_capacity(12 + 8);
    chunk.extend_from_slice(&8u32.to_be_bytes());
    chunk.extend_from_slice(&body);
    chunk.extend_from_slice(&crc32(&body).to_be_bytes());

    let mut out = Vec::with_capacity(png.len() + chunk.len());
    out.extend_from_slice(&png[..AFTER_IHDR]);
    out.extend_from_slice(&chunk);
    out.extend_from_slice(&png[AFTER_IHDR..]);
    out
}

/// PNG's CRC-32, computed directly so the tool needs no extra dependency.
fn crc32(data: &[u8]) -> u32 {
    let mut table = [0u32; 256];
    for (i, entry) in table.iter_mut().enumerate() {
        let mut c = i as u32;
        for _ in 0..8 {
            c = if c & 1 != 0 { 0xEDB8_8320 ^ (c >> 1) } else { c >> 1 };
        }
        *entry = c;
    }
    let mut crc = 0xFFFF_FFFFu32;
    for byte in data {
        crc = table[((crc ^ *byte as u32) & 0xFF) as usize] ^ (crc >> 8);
    }
    crc ^ 0xFFFF_FFFF
}

/// The box around everything that is not FULLY transparent, inclusive on both
/// corners, or `None` for a frame with nothing in it.
///
/// Any alpha at all counts, rather than a majority. A silhouette's outermost
/// pixels are the faint ones antialiasing left behind -- at 3x supersampling an
/// edge pixel can be a ninth covered -- and a threshold that ignored those
/// would crop away the very edge it was called to measure, by an amount that
/// grew with how smooth the sprite was.
fn opaque_bounds(pixels: &[u8], w: u32, h: u32) -> Option<(u32, u32, u32, u32)> {
    let (mut x0, mut y0) = (u32::MAX, u32::MAX);
    let (mut x1, mut y1) = (0u32, 0u32);
    let mut any = false;
    for y in 0..h {
        for x in 0..w {
            if pixels[((y * w + x) * 4 + 3) as usize] > 0 {
                any = true;
                x0 = x0.min(x);
                y0 = y0.min(y);
                x1 = x1.max(x);
                y1 = y1.max(y);
            }
        }
    }
    any.then_some((x0, y0, x1, y1))
}

/// Averages an `ss`-by-`ss` block down to one pixel, PREMULTIPLIED.
///
/// This is the whole of the antialiasing. Drawing at four times the width and
/// height gives sixteen samples of the model per output pixel, and an edge that
/// covers three of them comes out three sixteenths of the way between the
/// creature and the background instead of having to pick one or the other. That
/// choice, made pixel by pixel along a silhouette, is the staircase -- and it is
/// only visible when it holds still, which is why a moving preview looked fine
/// and a stopped one looked chunky.
///
/// Premultiplied for the same reason `blur_rgba` is: a transparent pixel's
/// colour is arbitrary and usually black, so averaging straight RGBA drags a
/// dark halo into every soft edge -- which on a sprite that is mostly edge is
/// the difference between smooth and grubby.
///
/// A box filter rather than anything cleverer, because the samples are a
/// regular grid inside one output pixel and a box is what "how much of this
/// pixel is covered" means. A Gaussian here would blur across pixels, which is
/// the `blur` control's job and is meant to be separate from this.
fn downsample_rgba(pixels: &[u8], w: u32, h: u32, ss: u32) -> Vec<u8> {
    let (dw, dh) = (w / ss, h / ss);
    let mut out = vec![0u8; (dw * dh * 4) as usize];
    let n = (ss * ss) as f32;
    for y in 0..dh {
        for x in 0..dw {
            let (mut r, mut g, mut b, mut a) = (0f32, 0f32, 0f32, 0f32);
            for sy in 0..ss {
                for sx in 0..ss {
                    let i = (((y * ss + sy) * w + x * ss + sx) * 4) as usize;
                    let sa = pixels[i + 3] as f32 / 255.0;
                    r += pixels[i] as f32 * sa;
                    g += pixels[i + 1] as f32 * sa;
                    b += pixels[i + 2] as f32 * sa;
                    a += sa;
                }
            }
            let o = ((y * dw + x) * 4) as usize;
            // Back to straight alpha. Where nothing was covered there is no
            // colour to recover and the pixel stays fully transparent.
            if a > 0.0 {
                out[o] = (r / a).round().clamp(0.0, 255.0) as u8;
                out[o + 1] = (g / a).round().clamp(0.0, 255.0) as u8;
                out[o + 2] = (b / a).round().clamp(0.0, 255.0) as u8;
            }
            out[o + 3] = (a / n * 255.0).round().clamp(0.0, 255.0) as u8;
        }
    }
    out
}

/// A separable Gaussian, done on the PREMULTIPLIED image.
///
/// Blurring straight alpha mixes the colour of fully transparent pixels into
/// its neighbours -- and a transparent pixel's colour is arbitrary, usually
/// black -- so the edges of a sprite pick up a dark halo. Premultiplying first,
/// blurring, then dividing back out is what keeps a soft edge the colour of the
/// thing it belongs to.
fn blur_rgba(pixels: &mut [u8], w: u32, h: u32, sigma: f32) {
    let (w, h) = (w as usize, h as usize);
    if w == 0 || h == 0 || pixels.len() < w * h * 4 {
        return;
    }
    let radius = (sigma * 2.5).ceil().max(1.0) as isize;
    let mut kernel: Vec<f32> = (-radius..=radius)
        .map(|i| {
            let x = i as f32 / sigma;
            (-0.5 * x * x).exp()
        })
        .collect();
    let sum: f32 = kernel.iter().sum();
    for k in &mut kernel {
        *k /= sum;
    }

    // Premultiply into floats.
    let mut buf: Vec<[f32; 4]> = (0..w * h)
        .map(|i| {
            let a = pixels[i * 4 + 3] as f32 / 255.0;
            [
                pixels[i * 4] as f32 / 255.0 * a,
                pixels[i * 4 + 1] as f32 / 255.0 * a,
                pixels[i * 4 + 2] as f32 / 255.0 * a,
                a,
            ]
        })
        .collect();
    let mut tmp = buf.clone();

    // Horizontal, then vertical. Edges clamp rather than wrap, so a sprite
    // touching the frame does not bleed in from the opposite side.
    for y in 0..h {
        for x in 0..w {
            let mut acc = [0f32; 4];
            for (ki, k) in kernel.iter().enumerate() {
                let sx = (x as isize + ki as isize - radius).clamp(0, w as isize - 1) as usize;
                let px = buf[y * w + sx];
                for c in 0..4 {
                    acc[c] += px[c] * k;
                }
            }
            tmp[y * w + x] = acc;
        }
    }
    for y in 0..h {
        for x in 0..w {
            let mut acc = [0f32; 4];
            for (ki, k) in kernel.iter().enumerate() {
                let sy = (y as isize + ki as isize - radius).clamp(0, h as isize - 1) as usize;
                let px = tmp[sy * w + x];
                for c in 0..4 {
                    acc[c] += px[c] * k;
                }
            }
            buf[y * w + x] = acc;
        }
    }

    for i in 0..w * h {
        let a = buf[i][3].clamp(0.0, 1.0);
        let inv = if a > 0.0009 { 1.0 / a } else { 0.0 };
        for c in 0..3 {
            pixels[i * 4 + c] = ((buf[i][c] * inv).clamp(0.0, 1.0) * 255.0).round() as u8;
        }
        pixels[i * 4 + 3] = (a * 255.0).round() as u8;
    }
}

/// Where presets live. Beside the user's other settings rather than next to
/// the executable, which lives in `target/` and is deleted by `cargo clean`.
fn preset_dir() -> PathBuf {
    let base = std::env::var("APPDATA").unwrap_or_else(|_| ".".into());
    PathBuf::from(base)
        .join("dueldimension")
        .join("spritebaker")
        .join("presets")
}

/// Every `config/dueldimension` folder worth looking in.
fn candidate_roots() -> Vec<PathBuf> {
    let mut out = Vec::new();
    if let Ok(appdata) = std::env::var("APPDATA") {
        let profiles = PathBuf::from(&appdata).join("ModrinthApp").join("profiles");
        if let Ok(dir) = std::fs::read_dir(&profiles) {
            for item in dir.flatten() {
                let config = item.path().join("config").join("dueldimension");
                if config.is_dir() {
                    out.push(config);
                }
            }
        }
    }
    // The dev run folder, so a model can be tried before it is anywhere else.
    for guess in [
        "../../mc1211/run/config/dueldimension",
        "../../mc262/run/config/dueldimension",
    ] {
        let p = PathBuf::from(guess);
        if p.is_dir() {
            out.push(p);
        }
    }
    out
}

/// The card and type folder a profile already associates with this model.
fn lookup(config: &Path, model: &str) -> Option<(String, String)> {
    let text = std::fs::read_to_string(config.join("monster_sprites.json")).ok()?;
    for block in split_objects(&text) {
        if !block.contains(&format!("\"model\": \"{model}\""))
            && !block.contains(&format!("\"model\":\"{model}\""))
        {
            continue;
        }
        let card = after(&block, "\"card\":")?
            .trim_matches(|c: char| !c.is_ascii_digit())
            .to_string();
        let kind = after(&block, "\"sheet\":")
            .and_then(|s| {
                let s = s.trim().trim_matches('"');
                s.split('/').next().map(str::to_string)
            })
            .unwrap_or_else(|| "dragon".into());
        if !card.is_empty() {
            return Some((card, kind));
        }
    }
    None
}

fn after(block: &str, key: &str) -> Option<String> {
    let at = block.find(key)? + key.len();
    let rest = &block[at..];
    let end = rest.find([',', '\n'])?;
    Some(rest[..end].trim().to_string())
}

/// Does this block belong to exactly this card?
///
/// `contains("\"card\": 1000")` is true of card **10000** as well, so a matcher
/// written that way rewrites a different monster's entry whenever one passcode
/// is a prefix of another -- and Yu-Gi-Oh passcodes are eight digits, so
/// prefixes are not a curiosity. The digit after has to not be a digit.
fn is_card(block: &str, card: i64) -> bool {
    for prefix in [format!("\"card\": {card}"), format!("\"card\":{card}")] {
        if let Some(at) = block.find(&prefix) {
            let next = block[at + prefix.len()..].chars().next();
            if !next.is_some_and(|c| c.is_ascii_digit()) {
                return true;
            }
        }
    }
    false
}

/// Replaces one `"key": value` scalar in a JSON block, leaving every other byte
/// exactly as it was. `None` when the key is not present.
///
/// A textual patch rather than a parse-and-re-emit, and that is the point: this
/// tool must not be the reason a field it has never heard of changes. Anything
/// the game writes and this does not understand -- a per-cell nudge list, a
/// defence pose, a wing -- survives a re-bake untouched because it is never
/// read in the first place.
///
/// Scalars only. Every key patched here is a number or a quoted string, so the
/// value ends at the next comma or newline; an object or array value would not.
fn replace_scalar(block: &str, key: &str, value: &str) -> Option<String> {
    let quoted = format!("\"{key}\"");
    let at = block.find(&quoted)?;
    let colon = at + block[at..].find(':')? + 1;
    let rest = &block[colon..];
    let end = rest.find([',', '\n'])?;
    Some(format!("{}{}{}", &block[..colon], format_args!(" {value}"), &rest[end..]))
}

/// Applies every update this tool owns to one entry, and nothing else.
///
/// A key that is absent is SKIPPED rather than added: the game writes the keys
/// it needs and an entry without `bob` does not want one. Adding keys here
/// would be this tool having opinions about a file it is a guest in.
///
/// See `ensure_scalar` for the one exception, and why it has to be one.
fn patch_entry(block: &str, updates: &[(&str, String)]) -> String {
    let mut out = block.to_string();
    for (key, value) in updates {
        if let Some(next) = replace_scalar(&out, key, value) {
            out = next;
        }
    }
    out
}

/// Sets a key, ADDING it after `after` when the entry does not have one yet.
///
/// The exception to the rule above, and it exists because "skip what is not
/// there" and "this tool owns this key" cannot both be true of `directions`.
/// Every entry written before rotations existed has no `directions`, so a
/// re-bake with eight of them updated `rows` to 8 and left the game reading a
/// sheet it still believed had one view: the sprite showed row 0 forever and
/// the feature looked broken rather than absent.
///
/// Anchored after a key the baker also owns, so the insertion lands inside the
/// same object and inherits its indentation rather than being placed by
/// counting braces.
fn ensure_scalar(block: &str, key: &str, value: &str, after: &str) -> String {
    if let Some(next) = replace_scalar(block, key, value) {
        return next;
    }
    let anchor = format!("\"{after}\"");
    let Some(at) = block.find(&anchor) else { return block.to_string() };
    let Some(rel) = block[at..].find('\n') else { return block.to_string() };
    let line_end = at + rel;
    // The anchor line's own indentation, so the new line sits with its
    // neighbours in a file people read by hand.
    let indent: String = block[..at]
        .chars()
        .rev()
        .take_while(|c| *c == ' ')
        .collect();
    format!(
        "{}\n{indent}\"{key}\": {value},{}",
        &block[..line_end],
        &block[line_end..]
    )
}

/// Splits the top-level array into its object blocks, brace-counted so nested
/// objects (every entry has a `body`) do not end one early.
fn split_objects(text: &str) -> Vec<String> {
    let mut out = Vec::new();
    let mut depth = 0i32;
    let mut start = 0usize;
    let bytes = text.as_bytes();
    let mut in_string = false;
    let mut escape = false;
    for (i, c) in bytes.iter().enumerate() {
        if in_string {
            if escape {
                escape = false;
            } else if *c == b'\\' {
                escape = true;
            } else if *c == b'"' {
                in_string = false;
            }
            continue;
        }
        match c {
            b'"' => in_string = true,
            b'{' => {
                if depth == 0 {
                    start = i;
                }
                depth += 1;
            }
            b'}' => {
                depth -= 1;
                if depth == 0 {
                    out.push(text[start..=i].to_string());
                }
            }
            _ => {}
        }
    }
    out
}

#[cfg(test)]
mod crop_tests {
    use super::opaque_bounds;

    /// One pixel wide is one pixel wide, not zero -- the bounds are inclusive,
    /// and auto_crop adds the +1 that turns them into a size.
    #[test]
    fn a_single_lit_pixel_is_its_own_box() {
        let mut px = vec![0u8; 4 * 4 * 4];
        px[((2 * 4 + 1) * 4 + 3) as usize] = 255;
        assert_eq!(opaque_bounds(&px, 4, 4), Some((1, 2, 1, 2)));
    }

    #[test]
    fn an_empty_frame_has_no_box_rather_than_a_silly_one() {
        assert_eq!(opaque_bounds(&vec![0u8; 4 * 4 * 4], 4, 4), None);
    }

    /// The case the whole feature exists for: the extremes are found even when
    /// they are four separate pixels on four different sides.
    #[test]
    fn the_extremes_can_come_from_different_pixels() {
        let mut px = vec![0u8; 8 * 8 * 4];
        let mut lit = |x: u32, y: u32| px[((y * 8 + x) * 4 + 3) as usize] = 255;
        lit(3, 0); // topmost
        lit(7, 4); // rightmost
        lit(2, 7); // lowest
        lit(0, 5); // leftmost
        assert_eq!(opaque_bounds(&px, 8, 8), Some((0, 0, 7, 7)));
    }

    /// A barely-there edge pixel is still the edge. Supersampling leaves plenty
    /// of these, and ignoring them crops the sprite by however smooth it is.
    #[test]
    fn one_unit_of_alpha_counts() {
        let mut px = vec![0u8; 4 * 4 * 4];
        px[((1 * 4 + 1) * 4 + 3) as usize] = 255;
        px[((0 * 4 + 0) * 4 + 3) as usize] = 1;
        assert_eq!(opaque_bounds(&px, 4, 4), Some((0, 0, 1, 1)));
    }

    /// Colour is irrelevant: an opaque black pixel is as much part of the
    /// monster as a white one, and plenty of these sprites are mostly dark.
    #[test]
    fn opaque_black_is_not_mistaken_for_background() {
        let mut px = vec![0u8; 4 * 4 * 4];
        px[((3 * 4 + 3) * 4 + 3) as usize] = 255; // black, fully opaque
        assert_eq!(opaque_bounds(&px, 4, 4), Some((3, 3, 3, 3)));
    }
}

#[cfg(test)]
mod downsample_tests {
    use super::downsample_rgba;

    /// `ss` by `ss` opaque pixels of one colour must come back as that colour,
    /// not as something a rounding error away from it.
    #[test]
    fn a_flat_block_keeps_its_colour() {
        let px = vec![200u8, 100, 50, 255].repeat(16);
        let out = downsample_rgba(&px, 4, 4, 4);
        assert_eq!(out, vec![200, 100, 50, 255]);
    }

    /// Half covered is half alpha -- which is the entire point, and the thing a
    /// hard edge cannot express.
    #[test]
    fn a_half_covered_pixel_comes_out_half_transparent() {
        let mut px = vec![0u8; 2 * 2 * 4];
        for i in 0..2 {
            let o = i * 4;
            px[o] = 255;
            px[o + 3] = 255;
        }
        let out = downsample_rgba(&px, 2, 2, 2);
        assert_eq!(out[3], 128, "two of four samples covered");
        // And the colour is the COVERED samples' colour, undiluted.
        assert_eq!(out[0], 255, "red, not a darker red");
    }

    /// The bug this is premultiplied to avoid: transparent pixels are usually
    /// transparent BLACK, and averaging straight RGBA would drag that black
    /// into every soft edge as a dark rim.
    #[test]
    fn transparent_black_does_not_darken_the_edge() {
        let mut px = vec![0u8; 4 * 4 * 4];
        // One opaque white sample in a block of transparent black.
        px[0] = 255;
        px[1] = 255;
        px[2] = 255;
        px[3] = 255;
        let out = downsample_rgba(&px, 4, 4, 4);
        assert_eq!([out[0], out[1], out[2]], [255, 255, 255], "still white");
        assert_eq!(out[3], 16, "one sample in sixteen");
    }

    #[test]
    fn nothing_covered_stays_fully_transparent() {
        let px = vec![0u8; 4 * 4 * 4];
        let out = downsample_rgba(&px, 4, 4, 4);
        assert_eq!(out, vec![0, 0, 0, 0]);
    }

    #[test]
    fn the_output_is_the_input_divided_by_the_factor() {
        let px = vec![0u8; 8 * 12 * 4];
        assert_eq!(downsample_rgba(&px, 8, 12, 2).len(), (4 * 6 * 4) as usize);
        assert_eq!(downsample_rgba(&px, 8, 12, 4).len(), (2 * 3 * 4) as usize);
    }
}

#[cfg(test)]
mod entry_tests {
    use super::{is_card, patch_entry, replace_scalar};

    /// What the game writes once somebody has stood a monster on its card and
    /// tuned it: a size and a placement this tool cannot know and must not
    /// touch, beside a grid it owns and must update.
    const TUNED: &str = r#"  {
    "card": 10000010,
    "body": {
      "sheet": "divine_beast/the_winged_dragon_of_ra",
      "x": 0,
      "y": 0,
      "w": 0,
      "h": 0,
      "columns": 4,
      "rows": 1,
      "first": 0,
      "frames": 4,
      "ticks": 5,
      "loop": "PING_PONG",
      "bob": 0,
      "trimX": 3,
      "trimY": 7
    },
    "scale": 2.25,
    "elevation": 0.75,
    "turn": 30.0,
    "offsetX": -0.5,
    "offsetZ": 0.25,
    "model": "the_winged_dragon_of_ra",
    "animation": "slot_0"
  }"#;

    fn rebake() -> Vec<(&'static str, String)> {
        vec![
            ("sheet", "\"divine_beast/the_winged_dragon_of_ra\"".to_string()),
            ("columns", "8".to_string()),
            ("frames", "8".to_string()),
            ("ticks", "3".to_string()),
        ]
    }

    #[test]
    fn a_rebake_keeps_the_size_and_placement_the_game_set() {
        let out = patch_entry(TUNED, &rebake());
        // The whole point: these are Minecraft's, and a re-bake is not a reason
        // for any of them to move.
        assert!(out.contains("\"scale\": 2.25"), "{out}");
        assert!(out.contains("\"elevation\": 0.75"), "{out}");
        assert!(out.contains("\"turn\": 30.0"), "{out}");
        assert!(out.contains("\"offsetX\": -0.5"), "{out}");
        assert!(out.contains("\"offsetZ\": 0.25"), "{out}");
        // And the in-game crop, which is judged the same way and for the same
        // reason is not the baker's to reset.
        assert!(out.contains("\"trimX\": 3"), "{out}");
        assert!(out.contains("\"trimY\": 7"), "{out}");
    }

    #[test]
    fn a_rebake_does_update_the_grid_it_just_wrote() {
        let out = patch_entry(TUNED, &rebake());
        assert!(out.contains("\"columns\": 8"), "{out}");
        assert!(out.contains("\"frames\": 8"), "{out}");
        assert!(out.contains("\"ticks\": 3"), "{out}");
        assert!(!out.contains("\"columns\": 4"), "{out}");
    }

    #[test]
    fn a_key_the_entry_does_not_have_is_not_invented() {
        // A key this tool has no business knowing about stays absent.
        let out = patch_entry(TUNED, &[("wingspan", "3".to_string())]);
        assert!(!out.contains("wingspan"), "{out}");
    }

    /// The bug that made eight-direction sheets look broken instead of absent:
    /// every entry written before rotations existed has no `directions`, and
    /// patch_entry deliberately never adds one -- so `rows` went to 8 while the
    /// game carried on believing the sheet had a single view.
    #[test]
    fn directions_is_added_to_an_entry_that_predates_it() {
        assert!(!TUNED.contains("directions"), "the fixture is an old entry");
        let out = super::ensure_scalar(TUNED, "directions", "8", "rows");
        assert!(out.contains("\"directions\": 8,"), "{out}");
        // Beside rows, inside body, not loose at the top of the entry.
        let d = out.find("directions").unwrap();
        let body_end = out.find("\"scale\"").unwrap();
        assert!(d < body_end, "it landed outside body: {out}");
        // And it is still valid-looking JSON around the seam.
        assert!(out.contains("\"rows\": 1,
      \"directions\": 8,"), "{out}");
    }

    #[test]
    fn directions_is_replaced_when_the_entry_already_has_one() {
        let once = super::ensure_scalar(TUNED, "directions", "8", "rows");
        let twice = super::ensure_scalar(&once, "directions", "1", "rows");
        assert_eq!(twice.matches("directions").count(), 1, "not duplicated: {twice}");
        assert!(twice.contains("\"directions\": 1,"), "{twice}");
    }

    #[test]
    fn replacing_one_scalar_leaves_the_rest_byte_identical() {
        let out = replace_scalar(TUNED, "ticks", "9").expect("ticks is there");
        // One digit for one digit, so the file does not even change length --
        // which is the strongest statement available that nothing else moved.
        assert_eq!(out.len(), TUNED.len(), "only the digit changed");
        assert!(out.contains("\"ticks\": 9"));
        assert!(out.contains("\"loop\": \"PING_PONG\""));
    }

    /// Passcodes are eight digits, so one being a prefix of another is ordinary
    /// rather than exotic -- and a substring match rewrites the wrong monster.
    #[test]
    fn a_shorter_passcode_does_not_match_a_longer_one() {
        let block = "  {\n    \"card\": 10000010,\n    \"scale\": 1.0\n  }";
        assert!(is_card(block, 10000010));
        assert!(!is_card(block, 1000001));
        assert!(!is_card(block, 1000));
    }

    #[test]
    fn both_spacings_of_the_card_key_are_recognised() {
        assert!(is_card("{\"card\":10000000,\"scale\":1.0}", 10000000));
        assert!(is_card("{ \"card\": 10000000 }", 10000000));
    }
}
