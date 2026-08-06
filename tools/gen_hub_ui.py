"""Generates the PNG furniture for the Duel Hub and the deck editor.

Every UI element is a texture, so nothing here is drawn with a flat rectangle at
runtime. The panels are nine-slice: a fixed corner, a stretchable edge and a
stretchable middle, so one small file dresses a container of any size without
its corners smearing.

Run from the repo root:  python tools/gen_hub_ui.py
"""
import math
import os

from PIL import Image, ImageDraw, ImageFilter

OUT = 'src/main/resources/assets/dueldimension/textures/gui'

# Nine-slice corner size. Every panel and button below is 3x3 cells of this, so
# the Java side can slice any of them with the same constant.
BORDER = 8
CELL = BORDER * 3

# The hub's palette: a cool near-black case with a warm gold accent, so it sits
# beside the duel screen's own chrome rather than fighting it.
INK = (18, 20, 26)
PANEL_TOP = (44, 48, 58)
PANEL_BOT = (28, 31, 39)
INSET_TOP = (20, 22, 28)
INSET_BOT = (26, 29, 36)
EDGE_LIGHT = (120, 132, 150)
EDGE_DARK = (10, 11, 15)
GOLD = (214, 172, 84)
GOLD_DIM = (128, 102, 50)


def ensure(path):
    os.makedirs(os.path.join(OUT, path), exist_ok=True)


def vgrad(draw, box, top, bottom):
    x0, y0, x1, y1 = box
    span = max(1, y1 - y0)
    for y in range(y0, y1):
        f = (y - y0) / span
        draw.line([x0, y, x1 - 1, y],
                  fill=tuple(round(top[i] + (bottom[i] - top[i]) * f) for i in range(3)) + (255,))


def panel(top, bottom, edge, accent=None, inset=False):
    """One nine-slice tile: a bevelled frame around a vertical gradient."""
    im = Image.new('RGBA', (CELL, CELL), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle([0, 0, CELL - 1, CELL - 1], radius=BORDER - 2, fill=INK + (255,))
    vgrad(d, (2, 2, CELL - 2, CELL - 2), top, bottom)
    # Two rims: a light one inside a dark one reads as a raised edge, and the
    # order is swapped for an inset so it reads as a recess instead.
    outer, inner = (EDGE_DARK, edge) if not inset else (edge, EDGE_DARK)
    d.rounded_rectangle([0, 0, CELL - 1, CELL - 1], radius=BORDER - 2, outline=outer + (255,), width=1)
    d.rounded_rectangle([1, 1, CELL - 2, CELL - 2], radius=BORDER - 3,
                        outline=inner + (120,), width=1)
    if accent:
        d.line([(BORDER, 2), (CELL - BORDER, 2)], fill=accent + (200,), width=1)
    return im


def write(im, path, name):
    ensure(path)
    im.save(os.path.join(OUT, path, name))
    print('  %s/%s %s' % (path, name, im.size))


def states(builder):
    """Stacks idle / hovered / disabled as three rows of one atlas."""
    rows = [builder(state) for state in ('idle', 'hover', 'disabled')]
    atlas = Image.new('RGBA', (CELL, CELL * 3), (0, 0, 0, 0))
    for i, row in enumerate(rows):
        atlas.paste(row, (0, i * CELL))
    return atlas


def button_state(state):
    if state == 'idle':
        return panel((62, 67, 80), (38, 42, 52), EDGE_LIGHT)
    if state == 'hover':
        return panel((86, 92, 108), (52, 57, 70), (200, 210, 226), accent=GOLD)
    return panel((34, 36, 42), (26, 28, 34), (70, 74, 84))


def tab_state(state):
    if state == 'idle':
        return panel((40, 44, 54), (28, 31, 39), (90, 98, 112))
    if state == 'hover':
        return panel((58, 63, 76), (38, 42, 52), (150, 160, 176), accent=GOLD_DIM)
    # The selected tab is the bright one; it shares the disabled slot because a
    # tab is never disabled, and a third row keeps every atlas the same shape.
    return panel((74, 80, 96), (46, 50, 62), (214, 224, 240), accent=GOLD)


def slot():
    """A card slot: a recessed square with a faint inner shadow."""
    size = 32
    im = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle([0, 0, size - 1, size - 1], radius=3, fill=(22, 24, 30, 255))
    d.rounded_rectangle([1, 1, size - 2, size - 2], radius=3, outline=(58, 63, 74, 255), width=1)
    shadow = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle([2, 2, size - 3, size - 6], radius=2,
                                             fill=(0, 0, 0, 90))
    im.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(1.5)))
    return im


def colour_wheel(size=192):
    """An HSV wheel: hue around, saturation outward. Value is a separate slider.

    Sampled directly by the picker, so the texture IS the model -- clicking a
    pixel gives the colour under the cursor with no maths on the Java side
    beyond reading it back.
    """
    im = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    pixels = im.load()
    centre = (size - 1) / 2.0
    radius = centre
    for y in range(size):
        for x in range(size):
            dx = x - centre
            dy = y - centre
            dist = math.hypot(dx, dy)
            if dist > radius:
                continue
            hue = (math.degrees(math.atan2(dy, dx)) + 360.0) % 360.0
            sat = min(1.0, dist / radius)
            r, g, b = hsv_to_rgb(hue, sat, 1.0)
            # Feather the last pixel so the rim is not stair-stepped.
            alpha = 255 if dist < radius - 1 else round(255 * max(0.0, radius - dist))
            pixels[x, y] = (r, g, b, alpha)
    return im


def hsv_to_rgb(h, s, v):
    c = v * s
    x = c * (1 - abs((h / 60.0) % 2 - 1))
    m = v - c
    if h < 60:
        rgb = (c, x, 0)
    elif h < 120:
        rgb = (x, c, 0)
    elif h < 180:
        rgb = (0, c, x)
    elif h < 240:
        rgb = (0, x, c)
    elif h < 300:
        rgb = (x, 0, c)
    else:
        rgb = (c, 0, x)
    return tuple(round((component + m) * 255) for component in rgb)


def value_slider(width=16, height=192):
    """White to black, for the brightness the wheel does not carry."""
    im = Image.new('RGBA', (width, height), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    for y in range(height):
        level = round(255 * (1 - y / max(1, height - 1)))
        d.line([0, y, width - 1, y], fill=(level, level, level, 255))
    d.rectangle([0, 0, width - 1, height - 1], outline=(10, 11, 15, 255), width=1)
    return im


def picker_cursor(size=11):
    """The ring that marks the chosen point; drawn over the wheel."""
    im = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.ellipse([0, 0, size - 1, size - 1], outline=(12, 13, 17, 255), width=2)
    d.ellipse([1, 1, size - 2, size - 2], outline=(255, 255, 255, 255), width=1)
    return im


def greyscale_mat(source, out_name):
    """A neutral mat, so one texture serves every colour the picker offers.

    Tinting multiplies, so the source has to carry the mat's LIGHTNESS and none
    of its hue: anything left coloured here would drag every chosen colour
    towards it. Luminance-weighted rather than a flat average, so the printed
    zone borders keep the contrast they were drawn with.
    """
    im = Image.open(source).convert('RGBA')
    pixels = im.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = pixels[x, y]
            lum = round(0.2126 * r + 0.7152 * g + 0.0722 * b)
            # Lifted slightly: multiplying a very dark source by a colour gives
            # mud, so the midtones are compressed upward before tinting.
            lum = round(70 + lum * (185.0 / 255.0))
            pixels[x, y] = (lum, lum, lum, a)
    im.save(out_name)
    print('  %s %s' % (out_name, im.size))


if __name__ == '__main__':
    print('common/')
    write(panel((44, 48, 58), (28, 31, 39), EDGE_LIGHT), 'common', 'panel.png')
    write(panel(INSET_TOP, INSET_BOT, (72, 78, 90), inset=True), 'common', 'panel_inset.png')
    write(states(button_state), 'common', 'button.png')
    write(states(tab_state), 'common', 'tab.png')
    write(slot(), 'common', 'slot.png')

    print('settings/')
    write(colour_wheel(), 'settings', 'colour_wheel.png')
    write(value_slider(), 'settings', 'value_slider.png')
    write(picker_cursor(), 'settings', 'picker_cursor.png')

    print('mats/')
    greyscale_mat('src/main/resources/assets/dueldimension/textures/duel/mats/classic.png',
                  'src/main/resources/assets/dueldimension/textures/duel/mats/custom.png')
