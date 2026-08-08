"""Draws the duel coin: two struck faces and the milled edge between them.

Layout, in fractions of the file, which is what CoinModel samples:
    heads  (0.0 .. 0.5, 0.0 .. 0.667)
    tails  (0.5 .. 1.0, 0.0 .. 0.667)
    rim    (0.0 .. 1.0, 0.667 .. 1.0)   milled strip, u runs around the edge

One alloy throughout, because a coin has the same metal on both sides -- the
faces differ by what is struck into them, not by what they are made of. Heads
and tails are told apart by the letter.

The faces are drawn as a real struck coin is: a raised rim catching the light
from above, a recessed field, and a bevelled letter. The shading is baked into
the texture rather than lit at runtime because the board's pipeline is
deliberately unlit -- see FieldQuad.
"""
from PIL import Image, ImageDraw, ImageFont
import math

W, H = 256, 192
FACE = 128           # each face cell is 128x128
RIM_TOP = 128        # the milled strip starts here

img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
px = img.load()

GOLD = (214, 168, 52)
SILVER = (186, 192, 202)


def shade(base, factor):
    return tuple(max(0, min(255, int(c * factor))) for c in base)


def face(ox, base, letter):
    """One side of the coin, centred in its 128x128 cell."""
    cx = cy = FACE / 2.0 - 0.5
    outer = FACE / 2.0 - 2       # edge of the coin
    rim_in = outer - 11          # inner edge of the raised rim
    field = rim_in - 2           # the recessed middle

    for y in range(FACE):
        for x in range(FACE):
            dx, dy = x - cx, y - cy
            d = math.hypot(dx, dy)
            if d > outer:
                continue
            # Light from above-left, as a coin under a lamp.
            lit = (-dy * 0.85 - dx * 0.45) / max(outer, 1)
            if d > rim_in:
                # The raised rim: a torus, brightest where it faces the light.
                across = (d - rim_in) / (outer - rim_in)      # 0 inner .. 1 outer
                round_ = math.sin(across * math.pi)            # domed across
                f = 0.72 + 0.55 * round_ * (0.35 + 0.9 * lit)
            elif d > field:
                # The step down into the field reads as a dark ring.
                f = 0.52
            else:
                # The field, very slightly domed so it is not dead flat.
                f = 0.80 + 0.16 * lit + 0.06 * (1 - (d / field) ** 2)
            px[ox + x, y] = shade(base, f) + (255,)


def emboss(ox, base, letter):
    """The struck letter: a dark cut with a lit upper-left bevel."""
    cell = Image.new("L", (FACE, FACE), 0)
    draw = ImageDraw.Draw(cell)
    size = 76
    font = None
    for name in ("arialbd.ttf", "arial.ttf", "seguisb.ttf"):
        try:
            font = ImageFont.truetype(name, size)
            break
        except OSError:
            continue
    if font is None:
        font = ImageFont.load_default()
    box = draw.textbbox((0, 0), letter, font=font)
    draw.text(((FACE - (box[2] - box[0])) / 2 - box[0],
               (FACE - (box[3] - box[1])) / 2 - box[1]), letter, 255, font=font)

    mask = cell.load()
    for y in range(FACE):
        for x in range(FACE):
            if not mask[x, y]:
                continue
            base_px = px[ox + x, y]
            if base_px[3] == 0:
                continue
            # Cut into the metal: darker, with the light edge on the
            # upper-left where the bevel would catch it.
            up = mask[x, max(0, y - 2)] and mask[max(0, x - 2), y]
            f = 0.62 if up else 1.28
            px[ox + x, y] = shade(base_px[:3], f) + (255,)


def rim():
    """The milled edge: vertical reeding, domed across its thickness."""
    height = H - RIM_TOP
    teeth = 64
    for y in range(height):
        across = y / max(1, height - 1)
        dome = math.sin(across * math.pi)              # bright in the middle
        for x in range(W):
            groove = 0.5 + 0.5 * math.cos(x / W * teeth * 2 * math.pi)
            f = 0.42 + 0.50 * dome + 0.26 * groove * dome
            px[x, RIM_TOP + y] = shade(GOLD, f) + (255,)


face(0, GOLD, "H")
emboss(0, GOLD, "H")
face(FACE, GOLD, "T")
emboss(FACE, GOLD, "T")
rim()

img.save("src/main/resources/assets/dueldimension/textures/duel/coin_model.png")
print("wrote textures/duel/coin_model.png (%dx%d)" % (W, H))
