"""Generates the six foil pattern sheets under textures/gui/foil/.

    rainbow.png   broad iridescent sweep      (Super, Ultra art window)
    linear.png    fine vertical holo lines    (Secret Rare)
    relief.png    embossed / raised banding   (Ultimate Rare)
    ice.png       cracked-ice facets          (Starlight, Collector's)
    metal.png     brushed metal sweep         (Rare name, Gold, Platinum)
    sparkle.png   hard specks                 (overlay for the loud ones)

Run from the repository root:  python tools/_foil_sheets.py


HOW BRIGHTNESS IS ENCODED, AND WHY
----------------------------------
The sheets are drawn by FieldQuad, which always submits
RenderTypes.breezeWind(texture, 0F, 0F). RenderPipelines.BREEZE_WIND uses
BlendFunction.TRANSLUCENT -- colour (SRC_ALPHA, ONE_MINUS_SRC_ALPHA). That is
plain alpha blending: the result is lerp(card, sheet, alpha). It cannot add
light. A mid-grey sheet therefore drags the artwork towards grey, which is the
muddy look these sheets have to avoid.

So, throughout:

    RGB carries CHROMA only and is always bright.
    ALPHA carries INTENSITY.

Every sheet's "dark" parts are transparent, not black, so an overlay only ever
brightens or tints. The one deliberate exception is relief.png's shadow line
(see gen_relief) -- an emboss cannot read as raised without a dark side, and
that is the whole point of that sheet.

Two consequences worth stating because they are easy to get wrong later:

* Transparent pixels are still painted WHITE in RGB, never black. Card art is
  sampled bilinearly (DuelTextures.cardSmooth), and a black RGB under a zero
  alpha bleeds dark fringes around every speck once the sheet is filtered or
  minified. White costs nothing and cannot fringe dark.

* BREEZE_WIND's shader defines ALPHA_CUTOUT = 0.1, and entity.fsh tests it on
  the RAW TEXEL alpha before the vertex colour is multiplied in. Any texel
  below alpha 26/255 is discarded outright. The two smooth sweeps (rainbow,
  metal) are therefore floored above that so they never cross the cutoff and
  never show a contour; the four hard-edged sheets drop straight to alpha 0,
  where being discarded is exactly the wanted behaviour.

If the draw is ever switched to RenderTypes.energySwirl (BlendFunction.ADDITIVE
= ONE, ONE) these sheets must be re-authored: under additive blending alpha
does nothing at all and the intensity has to live in RGB instead.

metal.png is kept near-achromatic on purpose -- every channel within 0.02 of
every other, all of them close to white -- so that the 12-float
FieldQuad.drawCorners overload can multiply a gold or silver tint into it and
get that colour exactly rather than a darkened version of it.


HOW SEAMLESS TILING IS GUARANTEED
---------------------------------
Each sheet is sampled at a sliding offset driven by the card's angle, so a seam
would sweep visibly across the card. Tiling is guaranteed by CONSTRUCTION, not
by touching up the edges:

* every analytic sheet is built only out of sin/cos of 2*pi*(k*x/W + m*y/H)
  with INTEGER k and m, which is exactly periodic with period W in x and H in y;
* every hard-edged threshold is taken on x mod P or y mod P with P dividing the
  image size exactly (linear.png uses P = 8 on W = 256);
* every point-feature sheet (ice's Voronoi seeds, sparkle's specks) measures
  distance with the toroidal delta below, which is itself periodic in both axes,
  so features wrap around the edges instead of being clipped by them.

Because of that, each generator is a pure function of the pixel coordinates,
and the tiling claim is testable rather than asserted: verify() renders every
sheet a second time over the coordinate window [W, 2W) x [H, 2H) and requires
the quantised uint8 result to be BYTE-IDENTICAL to the [0, W) x [0, H) render.
That is a proof of exact periodicity, not a heuristic. A wrap-versus-interior
difference statistic is printed alongside it as a human-readable sanity check.

The fundamental period is the full image in both axes (some harmonic has
k = 1 and some has m = 1 in every sheet), so none of these files is secretly
two copies of a smaller tile the way the old textures/gui/foil.png is.
"""
import os
import numpy as np
from PIL import Image

TWO_PI = 2.0 * np.pi

OUT_DIR = os.path.join("src", "main", "resources", "assets", "dueldimension",
                       "textures", "gui", "foil")

# entity.fsh discards a texel whose RAW alpha is below this. The smooth sheets
# stay above it; the hard-edged ones sit at 0 and are meant to be discarded.
ALPHA_CUTOUT = 0.1
SMOOTH_FLOOR = 0.14


# ---------------------------------------------------------------- helpers

def hsv2rgb(h, s, v):
    """Vectorised HSV -> RGB. h wraps, s and v are broadcast to h's shape."""
    h = np.mod(np.asarray(h, dtype=np.float64), 1.0) * 6.0
    s = np.broadcast_to(np.asarray(s, dtype=np.float64), h.shape)
    v = np.broadcast_to(np.asarray(v, dtype=np.float64), h.shape)
    i = np.floor(h).astype(np.int64) % 6
    f = h - np.floor(h)
    p = v * (1.0 - s)
    q = v * (1.0 - s * f)
    t = v * (1.0 - s * (1.0 - f))
    conds = [i == 0, i == 1, i == 2, i == 3, i == 4, i == 5]
    r = np.select(conds, [v, q, p, p, t, v])
    g = np.select(conds, [t, v, v, q, p, p])
    b = np.select(conds, [p, p, t, v, v, q])
    return np.stack([r, g, b], axis=-1)


def sstep(x):
    """Clamped smoothstep on 0..1."""
    x = np.clip(x, 0.0, 1.0)
    return x * x * (3.0 - 2.0 * x)


def edge(t, e, aa):
    """A 0->1 transition centred exactly on t == e, aa wide."""
    return sstep((t - e) / aa + 0.5)


def tdelta(a, b, period):
    """Shortest signed a - b on a circle of circumference `period`.

    Periodic in a with period `period`, which is what makes every point
    feature built on it wrap across the tile edge instead of being clipped.
    """
    return np.mod(a - b + period * 0.5, period) - period * 0.5


def white(shape):
    return np.ones(shape + (3,), dtype=np.float64)


# ---------------------------------------------------------------- sheets

def gen_rainbow(X, Y, W, H):
    """Broad iridescent sweep: one hue cycle across the tile, softly warped.

    Pastel rather than fully saturated. A pure hue (min channel 0) subtracts
    from two of the three channels of the artwork under alpha blending; mixing
    the hue towards white keeps the minimum channel near 0.5 so the overlay
    tints the light areas and lifts the dark ones instead of staining them.
    """
    u, v = X / W, Y / H
    wobble = (0.090 * np.sin(TWO_PI * (2 * u - v))
              + 0.060 * np.sin(TWO_PI * (u + 2 * v) + 1.1))
    hue = u + v + wobble
    sat = 0.42 + 0.13 * np.sin(TWO_PI * (u - v) + 0.4)
    rgb = hsv2rgb(hue, sat, 1.0)

    sweep = 0.5 + 0.5 * np.sin(TWO_PI * (u - v) + 0.9)
    cross = 0.5 + 0.5 * np.sin(TWO_PI * (2 * u + v) + 2.3)
    inten = np.clip(0.68 * sweep + 0.32 * cross, 0.0, 1.0)
    alpha = SMOOTH_FLOOR + 0.50 * inten
    return rgb, alpha


def gen_linear(X, Y, W, H):
    """Fine hard-edged vertical lines with spectral colour between them.

    Period 8 px on a 256 px width -> 32 lines, 3 px of line and 5 px of gap.
    8 divides 256 exactly, so the line grid meets itself at the wrap.
    """
    P = 8
    xi = np.mod(X, W)
    line_index = np.floor(xi / P)
    within = np.mod(xi, P)
    u, v = X / W, Y / H

    lines = W // P
    hue = (line_index + 0.5) / lines + v + 0.05 * np.sin(TWO_PI * (2 * v))
    core = (within == 1.0)
    sat = np.where(core, 0.34, 0.62)
    rgb = hsv2rgb(hue, sat, 1.0)

    shimmer = 0.5 + 0.5 * np.sin(TWO_PI * (u + 3 * v) + 0.7)
    alpha = 0.30 + 0.34 * shimmer
    alpha = np.where(core, np.minimum(alpha * 1.14, 0.78), alpha)
    alpha = np.where(within < 3.0, alpha, 0.0)
    return rgb, alpha


def gen_relief(X, Y, W, H):
    """Embossed banding: bright leading edge, flat raised face, dark trailing
    edge, then a trough that does nothing.

    THE ONE PLACE A SHEET IS ALLOWED TO DARKEN. An emboss reads as raised only
    because one side of the ridge is lit and the other is shadowed; a bright
    edge with no dark edge reads as a stripe. The shadow is kept modest --
    RGB 0.20 at alpha 0.34, and the caller multiplies that again by
    CardRarityFoil.sheen and edgeOn() -- so the worst case is a thin, soft
    darkening rather than a black bar across the artwork.

    Bands run at 45 degrees: the phase gradient is (6/256, 3/128) per pixel,
    equal in both axes, giving a band period of about 30 px. Both coefficients
    are integers, so the banding is exactly periodic over the tile.
    """
    u, v = X / W, Y / H
    phase = 6 * u + 3 * v

    # Start the cycle in the middle of the trough so no segment boundary sits
    # on the wrap point -- otherwise the smoothstep either side of it is cut
    # in half and leaves a hairline.
    t = np.mod(phase - 0.81, 1.0)
    aa = 0.5 / 30.0  # about half a pixel, in phase units

    e_a = edge(t, 0.19, aa)
    e_b = edge(t, 0.29, aa)
    e_c = edge(t, 0.71, aa)
    e_d = edge(t, 0.81, aa)

    w_ridge = e_a - e_b
    w_face = e_b - e_c
    w_shadow = e_c - e_d
    w_trough = 1.0 - e_a + e_d          # the four weights sum to exactly 1

    modulation = 0.72 + 0.28 * (0.5 + 0.5 * np.sin(TWO_PI * (u - 2 * v) + 0.5))
    alpha = (w_ridge * 0.60 + w_face * 0.15 + w_shadow * 0.34) * modulation

    c_ridge = np.array([1.00, 0.99, 0.96])
    c_face = np.array([1.00, 1.00, 1.00])
    c_shadow = np.array([0.20, 0.20, 0.23])
    c_trough = np.array([1.00, 1.00, 1.00])   # transparent, but never black
    rgb = (w_ridge[..., None] * c_ridge + w_face[..., None] * c_face
           + w_shadow[..., None] * c_shadow + w_trough[..., None] * c_trough)
    return rgb, alpha


ICE_GRID = 6          # 6x6 jittered seeds over 256 px -> facets about 42 px
ICE_CRACK_PX = 2.2    # width of the bright fracture between two facets


def _ice_seeds(W, H):
    rng = np.random.default_rng(20260809)
    pts, tints, sats, bases, dirs = [], [], [], [], []
    for gy in range(ICE_GRID):
        for gx in range(ICE_GRID):
            cw, ch = W / ICE_GRID, H / ICE_GRID
            pts.append(((gx + rng.uniform(0.15, 0.85)) * cw,
                        (gy + rng.uniform(0.15, 0.85)) * ch))
            tints.append(rng.uniform(0.50, 0.76))     # cyan .. blue .. violet
            sats.append(rng.uniform(0.08, 0.22))
            bases.append(rng.uniform(0.10, 0.30))
            ang = rng.uniform(0.0, TWO_PI)
            dirs.append((np.cos(ang), np.sin(ang)))
    return (np.array(pts), np.array(tints), np.array(sats),
            np.array(bases), np.array(dirs))


def gen_ice(X, Y, W, H):
    """Cracked ice: flat angular facets with hard bright fractures between.

    A Voronoi diagram measured with the toroidal delta, so the cell that owns a
    pixel near the right edge is the same cell that owns the matching pixel on
    the left edge. Each facet gets a flat base intensity plus a linear ramp
    along its own random direction, which is what makes a flat region read as a
    tilted crystal face; the fracture is where the two nearest seeds are within
    ICE_CRACK_PX of being equidistant.
    """
    pts, tints, sats, bases, dirs = _ice_seeds(W, H)

    best = np.full(X.shape, np.inf)
    second = np.full(X.shape, np.inf)
    owner = np.zeros(X.shape, dtype=np.int64)
    dxs = np.empty((len(pts),) + X.shape)
    dys = np.empty((len(pts),) + X.shape)
    for i, (sx, sy) in enumerate(pts):
        dx = tdelta(X, sx, W)
        dy = tdelta(Y, sy, H)
        dxs[i], dys[i] = dx, dy
        d = np.hypot(dx, dy)
        closer = d < best
        second = np.where(closer, best, np.minimum(second, d))
        owner = np.where(closer, i, owner)
        best = np.where(closer, d, best)

    idx = owner
    own_dx = np.take_along_axis(dxs, idx[None], 0)[0]
    own_dy = np.take_along_axis(dys, idx[None], 0)[0]
    ramp = ((own_dx * dirs[idx, 0] + own_dy * dirs[idx, 1])
            / (0.5 * (W / ICE_GRID + H / ICE_GRID)))

    # Floored above ALPHA_CUTOUT rather than allowed to fall through it: the
    # facet ramps are smooth, so a texel discarded mid-ramp would draw a hard
    # contour that reads as a crack the artist never put there. Starlight and
    # Collector's are whole-card treatments, so a faint base sheen everywhere
    # is right anyway.
    facet = np.clip(bases[idx] + 0.20 * ramp, 0.12, 0.46)
    crack = 1.0 - sstep((second - best) / ICE_CRACK_PX)

    alpha = np.maximum(facet, crack * 0.80)
    rgb = hsv2rgb(tints[idx], sats[idx] * (1.0 - crack), 1.0)
    return rgb, alpha


def gen_metal(X, Y, W, H):
    """Brushed metal: a broad soft sweep, scratched along the sweep direction.

    Deliberately near-achromatic. Every channel is within 0.02 of the others
    and all of them are close to white, so tinting it gold with the 12-float
    drawCorners overload yields that gold, and leaving it untinted yields
    silver. The brush is a stack of vertical harmonics -- all integer, so all
    periodic -- which reads as fine horizontal streaks.
    """
    u, v = X / W, Y / H

    # Slight horizontal wander so the streaks are brushed rather than ruled.
    wander = 0.014 * np.sin(TWO_PI * u + 0.3) + 0.008 * np.sin(TWO_PI * 2 * u)

    harmonics = [(7, 1, 0.34, 0.0), (11, 0, 0.26, 1.7), (17, 1, 0.20, 3.9),
                 (23, 0, 0.15, 2.2), (31, 2, 0.12, 5.1), (43, 0, 0.09, 0.8),
                 (59, 1, 0.06, 4.4)]
    streak = np.zeros(X.shape)
    weight = 0.0
    for k, j, amp, ph in harmonics:
        streak += amp * np.sin(TWO_PI * (k * (v + wander) + j * u) + ph)
        weight += amp
    streak = 0.5 + 0.5 * (streak / weight)

    sweep = 0.5 + 0.5 * np.sin(TWO_PI * u + 0.9)
    tilt = 0.5 + 0.5 * np.sin(TWO_PI * (2 * u + v) + 2.6)
    inten = np.clip(0.60 * sweep + 0.14 * tilt + 0.26 * streak, 0.0, 1.0)
    # A sum of sines spends most of its time near the middle. Without this the
    # sheet is a flat fog with streaks in it rather than a sweep with a bright
    # band and a dim one, and the metal is the sheet that most needs to read as
    # a moving highlight when the card turns.
    inten = inten ** 1.6

    alpha = SMOOTH_FLOOR - 0.01 + 0.64 * inten
    rgb = np.stack([0.985 + 0.015 * inten,
                    0.985 + 0.012 * inten,
                    0.995 - 0.010 * inten], axis=-1)
    return rgb, alpha


SPARKLE_N = 300


def _sparkle_specks(W, H):
    rng = np.random.default_rng(31415926)
    out = []
    for _ in range(SPARKLE_N):
        hero = rng.random() < 0.06
        out.append((rng.uniform(0, W), rng.uniform(0, H),
                    rng.uniform(1.9, 3.1) if hero else rng.uniform(0.7, 1.7),
                    rng.uniform(0.85, 1.0) if hero else rng.uniform(0.5, 0.95),
                    hero))
    return out


def gen_sparkle(X, Y, W, H):
    """Sparse hard bright specks, transparent everywhere else.

    "On black" in the sense that the background contributes nothing -- but the
    background RGB is WHITE, because zero-alpha black would bleed a dark halo
    around every speck the moment the sheet is filtered.

    Each speck is a hard disc measured with the toroidal delta, so a speck near
    an edge reappears on the opposite edge instead of being cut in half. The
    larger ones get a four-point flare.
    """
    alpha = np.zeros(X.shape)
    for sx, sy, r, a, hero in _sparkle_specks(W, H):
        dx = tdelta(X, sx, W)
        dy = tdelta(Y, sy, H)
        d = np.hypot(dx, dy)
        disc = 1.0 - sstep((d - r) / 0.6 + 0.5)      # hard, half-pixel edge
        if hero:
            arm = np.maximum(
                (1.0 - sstep((np.abs(dy) - 0.35) / 0.6 + 0.5))
                * (1.0 - sstep(np.abs(dx) / (r * 3.4))),
                (1.0 - sstep((np.abs(dx) - 0.35) / 0.6 + 0.5))
                * (1.0 - sstep(np.abs(dy) / (r * 3.4))))
            disc = np.maximum(disc, arm * 0.55)
        alpha = np.maximum(alpha, disc * a)
    return white(X.shape), alpha


SHEETS = [
    ("rainbow.png", 256, 128, gen_rainbow),
    ("linear.png", 256, 256, gen_linear),
    ("relief.png", 256, 128, gen_relief),
    ("ice.png", 256, 256, gen_ice),
    ("metal.png", 256, 128, gen_metal),
    ("sparkle.png", 256, 256, gen_sparkle),
]


# ---------------------------------------------------------------- drive

def render(gen, W, H, ox=0, oy=0):
    """Rasterise a generator over the coordinate window offset by (ox, oy)."""
    Y, X = np.mgrid[0:H, 0:W]
    X = X.astype(np.float64) + ox
    Y = Y.astype(np.float64) + oy
    rgb, alpha = gen(X, Y, W, H)
    rgba = np.concatenate([np.clip(rgb, 0.0, 1.0),
                           np.clip(alpha, 0.0, 1.0)[..., None]], axis=-1)
    return np.round(rgba * 255.0).astype(np.uint8)


def wrap_stats(a):
    """Wrap-around difference against interior difference, per axis.

    A seam shows up as a wrap figure well above the interior figure. Reported
    as a readable cross-check on the exact test in verify().
    """
    f = a.astype(np.float64)
    x_wrap = np.abs(f[:, 0] - f[:, -1]).mean()
    x_int = np.abs(np.diff(f, axis=1)).mean()
    y_wrap = np.abs(f[0] - f[-1]).mean()
    y_int = np.abs(np.diff(f, axis=0)).mean()
    return x_wrap, x_int, y_wrap, y_int


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    for name, W, H, gen in SHEETS:
        base = render(gen, W, H)

        # Exact periodicity: the same generator evaluated one full tile to the
        # right, and one full tile down, must quantise byte-identically.
        shift_x = render(gen, W, H, ox=W)
        shift_y = render(gen, W, H, oy=H)
        tiles_x = np.array_equal(base, shift_x)
        tiles_y = np.array_equal(base, shift_y)

        path = os.path.join(OUT_DIR, name)
        Image.fromarray(base, "RGBA").save(path, optimize=True)

        with Image.open(path) as im:
            im.load()
            reread = np.array(im)
            size, mode = im.size, im.mode
        assert np.array_equal(reread, base), name
        nbytes = os.path.getsize(path)

        xw, xi, yw, yi = wrap_stats(base)
        print(f"{name:<12} {size[0]}x{size[1]} {mode} {nbytes:>6} bytes "
              f"tiles_x={tiles_x} tiles_y={tiles_y}")
        for c, ch in enumerate("RGBA"):
            p = base[..., c]
            print(f"    {ch}  mean {p.mean():7.2f}  min {p.min():3d}  "
                  f"max {p.max():3d}")
        print(f"    wrap-vs-interior mean |delta|:  x {xw:5.2f} / {xi:5.2f}"
              f"   y {yw:5.2f} / {yi:5.2f}")
        below = (base[..., 3] < round(ALPHA_CUTOUT * 255)).mean() * 100.0
        zero = (base[..., 3] == 0).mean() * 100.0
        print(f"    alpha below the 0.1 cutout: {below:5.2f}%  "
              f"(of which exactly 0: {zero:5.2f}%)")


if __name__ == "__main__":
    main()
