"""Draws the trade screen's own PNGs, in the hub kit's idiom.

The mod's rule is that every UI element is a PNG and only text uses the font, so
the trade screen cannot draw its own boxes in code. Most of what it needs
already exists -- `common/panel.png`, `panel_inset.png` and `button.png` are the
same nine-slice tiles every hub screen is built from -- and those are reused
rather than redrawn. What is here is the handful of things only a trade has.

## Everything is drawn large and reduced

Each icon is drawn at four times its final size and resampled down with
Lanczos. A 32-pixel circle drawn directly by `ImageDraw.ellipse` is a staircase
-- there is no antialiasing in PIL's drawing primitives -- and the first version
of the DP coin was exactly that: a hard yellow ring. Drawing at 128 and reducing
gives clean edges for free, which is what makes a 16-pixel icon read as a coin
rather than as a circle of pixels.

## The kit these have to match

`common/panel.png` is a 24x24 nine-slice: an 8 pixel border and an 8 pixel
middle, so `NineSlice` can stretch it to any size. Anything new here keeps that
shape so it can be drawn by the same code. The palette is read off the existing
files rather than invented, because a new panel in a slightly different blue is
worse than no new panel.

## What is drawn

  trade/slot_empty     a card-shaped well, for the nine offer slots
  trade/slot_ready     the same well once its side has agreed, in green
  trade/ready.png      the tick that marks an agreed side
  trade/waiting.png    the hollow ring that marks one that has not
  trade/dp.png         the coin beside the points field
  trade/arrows.png     the exchange arrows between the two halves
"""
import os

from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
OUT = os.path.join(REPO, "shared", "resources", "assets", "dueldimension",
                   "textures", "gui", "trade")

# Sampled from common/panel.png and panel_inset.png so the new pieces belong to
# the same set rather than merely being near it.
EDGE = (86, 96, 116, 255)
EDGE_DARK = (43, 48, 58, 255)
WELL = (22, 25, 32, 255)
WELL_LIP = (14, 16, 21, 255)
GREEN = (108, 190, 122, 255)
GREEN_DIM = (52, 96, 62, 255)
GREEN_GLOW = (74, 132, 88, 255)
GOLD = (246, 206, 122, 255)
GOLD_DEEP = (188, 142, 58, 255)
GOLD_RIM = (120, 88, 34, 255)
TEXT = (194, 201, 214, 255)

TILE = 24
SS = 4  # supersample factor


def reduce(im, size):
    """Down to the final size with a real filter, which is the whole trick."""
    return im.resize((size, size), Image.LANCZOS)


def well(edge):
    """
    One 24x24 nine-slice tile: a sunken card slot.

    Nine-slice means the 8px corners are kept and the middle is stretched, so
    the lip is drawn only in the border ring -- a gradient across the whole
    tile would smear when a slot is drawn three times taller than it is wide.
    """
    im = Image.new("RGBA", (TILE, TILE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(im)
    draw.rectangle([0, 0, TILE - 1, TILE - 1], fill=WELL, outline=edge)
    # A darker line just inside the frame: the lip of the well, which is what
    # makes an empty slot read as recessed rather than as an outlined box.
    draw.rectangle([1, 1, TILE - 2, TILE - 2], outline=WELL_LIP)
    draw.rectangle([2, 2, TILE - 3, TILE - 3], outline=EDGE_DARK)
    # Corner pips, in the border ring so they survive the stretch.
    for cx, cy in ((3, 3), (TILE - 4, 3), (3, TILE - 4), (TILE - 4, TILE - 4)):
        draw.point((cx, cy), fill=edge)
    return im


def coin(size):
    """A DP coin: gold body, deep rim, and a highlight so it reads as metal."""
    big = size * SS
    im = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    draw = ImageDraw.Draw(im)
    edge = big * 0.04
    draw.ellipse([edge, edge, big - edge, big - edge], fill=GOLD_RIM)
    draw.ellipse([edge * 2, edge * 2, big - edge * 2, big - edge * 2], fill=GOLD_DEEP)
    draw.ellipse([edge * 3.2, edge * 3.2, big - edge * 3.2, big - edge * 3.2], fill=GOLD)
    # The face: a recessed inner disc, so the coin has a rim rather than being
    # a flat disc of one colour.
    draw.ellipse([big * 0.28, big * 0.28, big * 0.72, big * 0.72],
                 outline=GOLD_DEEP, width=max(1, int(big * 0.035)))
    # A specular crescent, top left, blurred so it is a sheen and not a shape.
    gloss = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    ImageDraw.Draw(gloss).ellipse(
        [big * 0.18, big * 0.14, big * 0.62, big * 0.5],
        fill=(255, 246, 214, 150))
    gloss = gloss.filter(ImageFilter.GaussianBlur(big * 0.05))
    im.alpha_composite(gloss)
    return reduce(im, size)


def tick(size):
    big = size * SS
    im = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    draw = ImageDraw.Draw(im)
    w = max(2, int(big * 0.13))
    points = [(big * 0.20, big * 0.54), (big * 0.42, big * 0.76), (big * 0.80, big * 0.24)]
    draw.line(points, fill=GREEN, width=w, joint="curve")
    # Round the ends, which `line` does not do on its own.
    for px, py in (points[0], points[2]):
        draw.ellipse([px - w / 2, py - w / 2, px + w / 2, py + w / 2], fill=GREEN)
    return reduce(im, size)


def ring(size):
    big = size * SS
    im = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    inset = big * 0.18
    ImageDraw.Draw(im).ellipse([inset, inset, big - inset, big - inset],
                               outline=EDGE, width=max(2, int(big * 0.09)))
    return reduce(im, size)


def arrows(w, h):
    """Two arrows passing each other, one each way."""
    big_w, big_h = w * SS, h * SS
    im = Image.new("RGBA", (big_w, big_h), (0, 0, 0, 0))
    draw = ImageDraw.Draw(im)
    bar = max(3, int(big_h * 0.10))
    head = big_w * 0.20
    for y, direction, colour in ((big_h * 0.30, 1, TEXT), (big_h * 0.70, -1, TEXT)):
        x0, x1 = big_w * 0.14, big_w * 0.86
        tip = x1 if direction > 0 else x0
        tail = x0 if direction > 0 else x1
        draw.line([(tail, y), (tip - head * direction * 0.5, y)],
                  fill=colour, width=bar)
        draw.polygon([(tip, y),
                      (tip - head * direction, y - big_h * 0.15),
                      (tip - head * direction, y + big_h * 0.15)], fill=colour)
    return im.resize((w, h), Image.LANCZOS)


def panel(face, edge, glow=None):
    """A side's container: 24x24 nine-slice, optionally lit at the border."""
    im = Image.new("RGBA", (TILE, TILE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(im)
    draw.rectangle([0, 0, TILE - 1, TILE - 1], fill=face, outline=edge)
    draw.rectangle([1, 1, TILE - 2, TILE - 2], outline=edge)
    if glow is not None:
        # A third, softer line inboard: a side that has agreed reads as lit
        # from its own edge rather than as merely outlined in a new colour.
        draw.rectangle([2, 2, TILE - 3, TILE - 3], outline=glow)
    return im


def header(face, rule):
    """The strip a side's name sits in, with a rule along its bottom."""
    im = Image.new("RGBA", (TILE, TILE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(im)
    draw.rectangle([0, 0, TILE - 1, TILE - 1], fill=face)
    draw.line([(0, TILE - 1), (TILE - 1, TILE - 1)], fill=rule)
    draw.line([(0, TILE - 2), (TILE - 1, TILE - 2)], fill=rule)
    return im


def banner(face, edge):
    """The centre status pill."""
    im = Image.new("RGBA", (TILE, TILE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(im)
    draw.rectangle([0, 0, TILE - 1, TILE - 1], fill=face, outline=edge)
    return im


def main():
    os.makedirs(OUT, exist_ok=True)
    written = []

    for name, edge in (("slot_empty", EDGE_DARK), ("slot_ready", GREEN_DIM)):
        well(edge).save(os.path.join(OUT, name + ".png"))
        written.append(name + ".png")

    # A side's container, and the same lit once that side has agreed. The whole
    # panel changing is the readable signal -- a 12 pixel tick beside a name is
    # not something anyone notices while looking at the cards.
    panel((28, 32, 41, 235), EDGE_DARK).save(os.path.join(OUT, "side.png"))
    written.append("side.png")
    panel((30, 40, 34, 235), GREEN_DIM, GREEN_GLOW).save(
        os.path.join(OUT, "side_ready.png"))
    written.append("side_ready.png")

    header((38, 43, 54, 255), EDGE_DARK).save(os.path.join(OUT, "head.png"))
    written.append("head.png")
    header((42, 60, 46, 255), GREEN_DIM).save(os.path.join(OUT, "head_ready.png"))
    written.append("head_ready.png")

    banner((20, 23, 30, 235), EDGE_DARK).save(os.path.join(OUT, "banner.png"))
    written.append("banner.png")

    tick(32).save(os.path.join(OUT, "ready.png"))
    written.append("ready.png")
    ring(32).save(os.path.join(OUT, "waiting.png"))
    written.append("waiting.png")
    coin(32).save(os.path.join(OUT, "dp.png"))
    written.append("dp.png")
    arrows(64, 32).save(os.path.join(OUT, "arrows.png"))
    written.append("arrows.png")

    for name in written:
        print("  %s" % name)
    print("wrote %d into %s" % (len(written), os.path.relpath(OUT, REPO)))


if __name__ == "__main__":
    main()
