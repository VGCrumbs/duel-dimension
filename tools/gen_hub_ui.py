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
        return panel((86, 92, 108), (52, 57, 70), (200, 210, 226))
    return panel((34, 36, 42), (26, 28, 34), (70, 74, 84))


def tab_state(state):
    """A tab, told apart by its surface rather than by a line drawn over it.

    The accent was a one-pixel gold rule across the top of the hovered and
    selected rows, and it read as a stray line lying on the button rather than
    as part of it -- most obviously on the sub-tabs, where three of them in a
    row put three unexplained dashes across the panel.

    Nothing is lost by dropping it. A selected tab is already the bright one --
    (74, 80, 96) against the idle (40, 44, 54), with a near-white rim and gold
    lettering -- so it is the most distinguished thing on the strip without it.
    """
    if state == 'idle':
        return panel((40, 44, 54), (28, 31, 39), (90, 98, 112))
    if state == 'hover':
        return panel((58, 63, 76), (38, 42, 52), (150, 160, 176))
    # The selected tab is the bright one; it shares the disabled slot because a
    # tab is never disabled, and a third row keeps every atlas the same shape.
    return panel((74, 80, 96), (46, 50, 62), (214, 224, 240))


def slot():
    """A card slot, as a NINE-SLICE tile rather than a fixed square.

    It was a 32x32 square, which meant drawing it into a card-shaped 30x44 rect
    stretched its rounded corners into ovals and thickened the bottom border.
    As a nine-slice its corners keep their size at any slot dimensions.
    """
    return panel((26, 28, 35), (18, 20, 25), (64, 70, 82), inset=True)


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


def header_bar(width=CELL):
    """A section header: a darker strip with a gold underline."""
    im = Image.new('RGBA', (width, CELL), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle([0, 0, width - 1, CELL - 1], radius=BORDER - 3, fill=(24, 26, 33, 255))
    vgrad(d, (2, 2, width - 2, CELL - 6), (52, 57, 70), (30, 33, 41))
    d.line([(BORDER // 2, CELL - 4), (width - BORDER // 2, CELL - 4)], fill=GOLD + (210,), width=2)
    d.rounded_rectangle([0, 0, width - 1, CELL - 1], radius=BORDER - 3,
                        outline=(74, 80, 92, 255), width=1)
    return im


def star(size=32):
    """The mark on a favourited card: a filled star with a black outline.

    Outlined rather than plain, because it is drawn over card art of any colour
    and a gold star on gold art would disappear.

    The outline is a stroked path, not `polygon(outline=...)`. That draws one
    pixel wide, and one pixel of an image built at eight times the final size
    is an eighth of a pixel once it is reduced -- an outline that was in the
    file and invisible on screen. Stroked at scale it survives the reduction.

    Drawn at 32 rather than 16 for the same reason the card icons were doubled:
    the deck grids draw this at around ten pixels and the card info page at
    seventeen, and a 16-pixel source has no detail to give the larger one.
    """
    scale = 8
    big = Image.new('RGBA', (size * scale, size * scale), (0, 0, 0, 0))
    d = ImageDraw.Draw(big)
    cx = cy = size * scale / 2
    # Room for the stroke: it straddles the path, so half of it lies outside
    # the star and would be clipped by the edge of the canvas otherwise.
    stroke = scale * 3
    outer = size * scale * 0.46 - stroke / 2
    inner = outer * 0.42
    points = []
    for i in range(10):
        angle = math.radians(-90 + i * 36)
        r = outer if i % 2 == 0 else inner
        points.append((cx + r * math.cos(angle), cy + r * math.sin(angle)))
    # Stroke first and fill over it, so the half of the stroke that falls
    # inside the star is covered and the gold keeps its full area.
    d.line(points + [points[0]], fill=(0, 0, 0, 255), width=stroke, joint='curve')
    for point in points:
        # The joins at the ten points, which a stroked polyline leaves notched.
        d.ellipse([point[0] - stroke / 2, point[1] - stroke / 2,
                   point[0] + stroke / 2, point[1] + stroke / 2], fill=(0, 0, 0, 255))
    d.polygon(points, fill=GOLD + (255,))
    # Drawn large and reduced, so the points are smooth rather than stepped.
    return big.resize((size, size), Image.LANCZOS)


def check(size=32):
    """The tick on the button that makes a deck the active one.

    WHITE, and deliberately nothing else. It is drawn with `DdBlitUtil`'s tint
    set to the colour the button's label would have taken, so one file covers
    idle, hovered and disabled -- a coloured tick would fight all three, and
    three files would be three things to keep in step.

    No outline either, for the reason the star HAS one: the star is drawn over
    card art of any colour and has to survive it, while this only ever sits on
    the button's own dark surface, where an outline is a smudge.

    Two strokes with round ends, built at eight times and reduced, so the
    diagonals come out smooth rather than stepped at the ten-odd pixels a deck
    row actually gives it.
    """
    scale = 8
    big = Image.new('RGBA', (size * scale, size * scale), (0, 0, 0, 0))
    d = ImageDraw.Draw(big)
    unit = size * scale
    # Measured off the canvas rather than written as pixels, so the shape is
    # the same whatever size this is regenerated at.
    points = [(unit * 0.22, unit * 0.52), (unit * 0.42, unit * 0.72),
              (unit * 0.79, unit * 0.28)]
    width = int(unit * 0.13)
    d.line(points, fill=(255, 255, 255, 255), width=width, joint='curve')
    for point in points:
        # Round caps as well as the round join. `joint='curve'` rounds the
        # corner but leaves both ENDS cut square, which reads as a broken tick.
        d.ellipse([point[0] - width / 2, point[1] - width / 2,
                   point[0] + width / 2, point[1] + width / 2],
                  fill=(255, 255, 255, 255))
    return big.resize((size, size), Image.LANCZOS)


def sort_arrow(up=True, size=32):
    """The collection's sort direction, as an arrow instead of a word.

    ASC and DESC were four and five characters in a thirty-unit button on a row
    that had none to spare. An arrow says the same thing in a square, and the
    square is the row's own height.

    WHITE, like the tick: `HubWidgets.IconButton` tints it to whatever colour
    the label would have taken, so one file covers idle, hovered and disabled.

    A head AND a stem, not a bare triangle -- a triangle alone reads as a
    dropdown caret, which is a different promise. Built at eight times and
    reduced so the diagonals do not step.
    """
    scale = 8
    unit = size * scale
    big = Image.new('RGBA', (unit, unit), (0, 0, 0, 0))
    d = ImageDraw.Draw(big)
    white = (255, 255, 255, 255)
    # Measured off the canvas so the shape survives being regenerated at any
    # size. The head is the top 45% and the stem hangs from its middle.
    head_h = unit * 0.45
    top = unit * 0.18
    stem_w = unit * 0.18
    d.polygon([(unit / 2, top), (unit * 0.86, top + head_h), (unit * 0.14, top + head_h)],
              fill=white)
    d.rectangle([unit / 2 - stem_w / 2, top + head_h * 0.86,
                 unit / 2 + stem_w / 2, unit * 0.82], fill=white)
    small = big.resize((size, size), Image.LANCZOS)
    # One drawing, flipped, so the two can never disagree about weight or size.
    return small if up else small.transpose(Image.FLIP_TOP_BOTTOM)


def alt_art(size=32):
    """The mark on a card that has more than one artwork: a gold [A] badge.

    A badge rather than a bare letter, for the reason the star is outlined: it
    is drawn over card art of any colour, and an unbacked glyph disappears into
    a light illustration. The gold plate carries the contrast and the letter is
    knocked out of it in the case ink, so the mark reads at the six pixels the
    deck grid gives it.

    The letter is PART OF THE PNG, drawn as filled polygons -- every UI element
    in this mod is a texture and only real text uses the font, so an [A] drawn
    with `font.width` at runtime would be the one exception.

    Built at eight times the final size and reduced, exactly as `star` is: a
    one-pixel outline drawn at 32 survives, but the diagonals of the A would be
    stepped without the supersample. Same 32 as the star, and for the same
    reason -- the grids draw this at around six pixels and it shares the
    corner with the star, so the two must be the same shape of file.
    """
    scale = 8
    span = size * scale
    big = Image.new('RGBA', (span, span), (0, 0, 0, 0))
    d = ImageDraw.Draw(big)

    stroke = scale * 2
    # Inset by half the stroke: it straddles the path, so a badge drawn to the
    # canvas edge would have the outer half of its outline clipped away.
    edge = stroke / 2
    d.rounded_rectangle([edge, edge, span - 1 - edge, span - 1 - edge],
                        radius=span * 0.26, fill=GOLD + (255,),
                        outline=(0, 0, 0, 255), width=stroke)

    def at(fx, fy):
        return (span * fx, span * fy)

    # The A: an outer triangle in the case ink, with its counter and the gap
    # between its legs cut back out in the plate's own gold. Cutting rather
    # than stroking keeps the letter's weight even at this size, where a
    # stroked A closes up into a blob.
    d.polygon([at(0.50, 0.20), at(0.80, 0.80), at(0.20, 0.80)], fill=INK + (255,))
    d.polygon([at(0.50, 0.38), at(0.605, 0.585), at(0.395, 0.585)], fill=GOLD + (255,))
    d.polygon([at(0.405, 0.665), at(0.595, 0.665), at(0.655, 0.80), at(0.345, 0.80)],
              fill=GOLD + (255,))
    return big.resize((size, size), Image.LANCZOS)


def title_ribbon():
    """A dark band under the deck's name, separating it from the grids.

    Darker than the panel it sits on rather than lighter, so it reads as a
    recess the name sits in rather than another raised element competing with
    the sections below it. Gold hairline along the bottom to tie it to the
    section headers, which use the same accent.
    """
    im = Image.new('RGBA', (CELL, CELL), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, CELL - 1, CELL - 1], fill=(15, 16, 21, 255))
    vgrad(d, (0, 1, CELL, CELL - 2), (26, 28, 35), (16, 17, 22))
    d.line([(0, 0), (CELL, 0)], fill=EDGE_DARK + (255,), width=1)
    d.line([(0, CELL - 1), (CELL, CELL - 1)], fill=GOLD_DIM + (200,), width=1)
    return im


def search_field():
    """The ground a search bar's text sits on: black, and nothing else.

    No frame and no gradient. The bevelled rectangle read as a second panel
    inside the panel it already sits in, and boxing a line of text that is
    already the only thing on that row told the player nothing they could not
    see. The caret and the text are drawn by the game.
    """
    im = Image.new('RGBA', (CELL, CELL), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle([0, 0, CELL - 1, CELL - 1], radius=BORDER - 2, fill=(0, 0, 0, 255))
    return im


def chip_state(state):
    """A filter chip: small, and clearly on or off at a glance."""
    if state == 'idle':
        return panel((38, 42, 52), (26, 29, 36), (84, 92, 106))
    if state == 'hover':
        return panel((54, 59, 72), (36, 40, 50), (150, 160, 176))
    return panel((92, 74, 34), (60, 48, 22), (240, 200, 110), accent=GOLD)


def scrollbar():
    """Track above, thumb below, as two tiles in one file."""
    im = Image.new('RGBA', (CELL, CELL * 2), (0, 0, 0, 0))
    track = panel((16, 17, 22), (20, 22, 28), (60, 66, 78), inset=True)
    thumb = panel((72, 78, 94), (46, 50, 62), (150, 160, 176))
    im.paste(track, (0, 0))
    im.paste(thumb, (0, CELL))
    return im


def deck_tile_surface(selected=False, width=260, height=232):
    """Master Duel's clipped deck-cell surface at its recovered source size.

    The web reconstruction established the authored 260x232 proportions.  The
    game scales this texture down as a whole, so the clipped corners stay in
    the same relationship to the deck case at every GUI scale.
    """
    polygon = [(20, 0), (width - 2, 0), (width - 1, 2),
               (width - 1, height - 20), (width - 20, height - 1),
               (2, height - 1), (0, height - 3), (0, 20)]
    mask = Image.new('L', (width, height), 0)
    ImageDraw.Draw(mask).polygon(polygon, fill=255)

    surface = Image.new('RGBA', (width, height), (1, 1, 1, 255))
    if selected:
        rank = Image.open(os.path.join(OUT, 'hub', 'deck_rank_background.png')).convert('RGBA')
        rank = rank.resize((width, 164), Image.LANCZOS)
        surface.alpha_composite(rank, (0, 13))
        # The original fades the rank plate back into the black name band.
        fade = Image.new('RGBA', (width, height), (0, 0, 0, 0))
        fd = ImageDraw.Draw(fade)
        for y in range(154, 199):
            alpha = round(255 * (y - 154) / 45)
            fd.line((0, y, width, y), fill=(0, 0, 0, alpha))
        surface.alpha_composite(fade)
    surface.putalpha(mask)
    return surface


def deck_tile_frames(width=260, height=232):
    """Idle, hovered and active outlines for the fixed-ratio deck tile."""
    atlas = Image.new('RGBA', (width, height * 3), (0, 0, 0, 0))
    points = [(20, 1), (width - 3, 1), (width - 2, 2),
              (width - 2, height - 21), (width - 21, height - 2),
              (2, height - 2), (1, height - 3), (1, 20)]
    colours = [(164, 168, 170, 255), (197, 220, 225, 255), (244, 208, 137, 255)]
    for row, colour in enumerate(colours):
        frame = Image.new('RGBA', (width, height), (0, 0, 0, 0))
        d = ImageDraw.Draw(frame)
        d.line(points + [points[0]], fill=colour, width=2, joint='curve')
        atlas.alpha_composite(frame, (0, row * height))
    return atlas


def standard_badge(width=76, height=38):
    """The regulation ring; STANDARD remains real text in the client."""
    scale = 4
    big = Image.new('RGBA', (width * scale, height * scale), (0, 0, 0, 0))
    d = ImageDraw.Draw(big)
    diameter = 33 * scale
    cx = width * scale // 2
    d.ellipse((cx - diameter // 2, 2 * scale,
               cx + diameter // 2, 2 * scale + diameter),
              outline=(20, 123, 210, 190), width=2 * scale)
    return big.resize((width, height), Image.LANCZOS)


if __name__ == '__main__':
    print('common/')
    write(panel((44, 48, 58), (28, 31, 39), EDGE_LIGHT), 'common', 'panel.png')
    write(panel(INSET_TOP, INSET_BOT, (72, 78, 90), inset=True), 'common', 'panel_inset.png')
    write(states(button_state), 'common', 'button.png')
    write(states(tab_state), 'common', 'tab.png')
    write(slot(), 'common', 'slot.png')
    write(check(), 'common', 'check.png')
    write(sort_arrow(True), 'common', 'sort_up.png')
    write(sort_arrow(False), 'common', 'sort_down.png')


    print('settings/')
    write(colour_wheel(), 'settings', 'colour_wheel.png')
    write(value_slider(), 'settings', 'value_slider.png')
    write(picker_cursor(), 'settings', 'picker_cursor.png')

    print('deckeditor/')
    write(header_bar(), 'deckeditor', 'header.png')
    write(title_ribbon(), 'deckeditor', 'title_ribbon.png')
    write(star(), 'deckeditor', 'star.png')
    write(alt_art(), 'deckeditor', 'alt_art.png')
    write(search_field(), 'deckeditor', 'search_field.png')
    write(states(chip_state), 'deckeditor', 'chip.png')
    write(scrollbar(), 'deckeditor', 'scrollbar.png')

    print('hub/')
    write(deck_tile_surface(False), 'hub', 'deck_tile_surface.png')
    write(deck_tile_surface(True), 'hub', 'deck_tile_selected.png')
    write(deck_tile_frames(), 'hub', 'deck_tile_frame.png')
    write(standard_badge(), 'hub', 'standard_badge.png')

    print('mats/')
    greyscale_mat('src/main/resources/assets/dueldimension/textures/duel/mats/classic.png',
                  'src/main/resources/assets/dueldimension/textures/duel/mats/custom.png')
