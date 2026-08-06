"""Production phase-indicator art: angular gunmetal shell, three key states.

Atlas layout, per turn colour: six columns (DP SP MP1 BP MP2 EP) by three rows
  row 0  idle      - the phase is reachable
  row 1  lit       - the phase the duel is in, letters glowing
  row 2  disabled  - unreachable: greyed and dimmed

Balance rules, each fixing a tilt found in the first half-height pass:
  * horizontal padding clears the angled nose, so neither end key sits inside
    the slope;
  * vertical padding is its own number -- the nose is a horizontal feature and
    should not drive the bar's height;
  * keys are inset equally on both sides;
  * the row is nudged back half a skew, because a parallelogram's visual mass
    sits half a skew right of its box.

Everything is authored at DETAIL times the size the screen uses, so the baked
labels stay sharp once bilinear filtering scales them down.
"""
from PIL import Image, ImageDraw, ImageFont, ImageFilter

LABELS = ['DP', 'SP', 'MP1', 'BP', 'MP2', 'EP']
DETAIL = 4

# Screen units (see EngineDuelScreen: PHASE_CELL_W / PHASE_CELL_H / PHASE_PAD_*).
CELL_W, CELL_H = 50, 10
PAD_X, PAD_Y = 8, 3
NOSE, SKEW, INSET = 6, 3, 1
LABEL_PT = 7

OUT = 'src/main/resources/assets/dueldimension/textures/duel/'

cw, ch = CELL_W * DETAIL, CELL_H * DETAIL
px, py = PAD_X * DETAIL, PAD_Y * DETAIL
nose, skew, inset = NOSE * DETAIL, SKEW * DETAIL, INSET * DETAIL
shift = -skew // 2


def font(sz):
    for n in ('arialbd.ttf', 'seguisb.ttf', 'DejaVuSans-Bold.ttf'):
        try:
            return ImageFont.truetype(n, max(8, sz))
        except OSError:
            pass
    return ImageFont.load_default()


def centre(d, box, text, f, fill):
    x0, y0, x1, y1 = box
    b = d.textbbox((0, 0), text, font=f)
    d.text(((x0 + x1) / 2 - (b[2] - b[0]) / 2 - b[0],
            (y0 + y1) / 2 - (b[3] - b[1]) / 2 - b[1]), text, font=f, fill=fill)


def grey(rgb):
    lum = 0.299 * rgb[0] + 0.587 * rgb[1] + 0.114 * rgb[2]
    return (round(lum),) * 3


def shade(rgb, f):
    return tuple(max(0, min(255, round(c * f))) for c in rgb)


def build_case():
    W, H = cw * 6 + px * 2, ch + py * 2
    im = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    shell = [(nose, 0), (W - nose - 1, 0), (W - 1, H // 2),
             (W - nose - 1, H - 1), (nose, H - 1), (0, H // 2)]
    d.polygon(shell, fill=(34, 37, 43, 255))
    inner = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    idraw = ImageDraw.Draw(inner)
    edge = max(1, DETAIL // 2)
    for y in range(edge, H - edge):
        f = (y - edge) / max(1, H - edge * 2)
        c = round(120 * 0.42 + 120 * 0.9 * (1 - abs(f - 0.42) * 2.1)) if f < 0.85 else 42
        idraw.line([edge, y, W - edge, y], fill=(max(28, min(252, c)),) * 3 + (255,))
    mask = Image.new('L', (W, H), 0)
    ImageDraw.Draw(mask).polygon(shell, fill=255)
    im.paste(inner, (0, 0), mask)
    ImageDraw.Draw(im).polygon(shell, outline=(196, 204, 214, 220), width=max(1, DETAIL // 2))
    return im


def draw_key(d, x0, y0, x1, y1, light, mid, state):
    quad = [(x0 + skew, y0), (x1, y0), (x1 - skew, y1), (x0, y1)]
    half = [(x0 + skew, y0), (x1, y0), (x1 - skew // 2, (y0 + y1) // 2),
            (x0 + skew // 2, (y0 + y1) // 2)]
    if state == 1:
        face, top = mid, tuple(min(255, c + 50) for c in light)
        rim, width = (255, 255, 255, 245), max(1, DETAIL // 2)
    elif state == 2:
        face, top = shade(grey(mid), 0.42), shade(grey(light), 0.42)
        rim, width = (108, 114, 124, 115), max(1, DETAIL // 4)
    else:
        face, top = mid, light
        rim, width = (170, 182, 196, 150), max(1, DETAIL // 4)
    d.polygon(quad, fill=face + (255,))
    d.polygon(half, fill=top + (255,))
    d.polygon(quad, outline=rim, width=width)


def build_atlas(colour):
    light, mid, _dark = colour
    W, H = cw * 6, ch * 3
    im = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    for row in range(3):
        for i in range(6):
            x0 = i * cw + inset + shift
            x1 = (i + 1) * cw - inset + shift
            draw_key(d, x0, row * ch, x1, row * ch + ch - 1, light, mid, row)

    glow = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    for i, lab in enumerate(LABELS):
        f = font((LABEL_PT if len(lab) < 3 else LABEL_PT - 1) * DETAIL)
        centre(gd, (i * cw + shift, ch, (i + 1) * cw + shift, ch * 2), lab, f,
               (255, 206, 44, 240))
    im.alpha_composite(glow.filter(ImageFilter.GaussianBlur(1.1 * DETAIL)))

    d = ImageDraw.Draw(im)
    for row in range(3):
        for i, lab in enumerate(LABELS):
            f = font((LABEL_PT if len(lab) < 3 else LABEL_PT - 1) * DETAIL)
            box = (i * cw + shift, row * ch, (i + 1) * cw + shift, row * ch + ch)
            if row == 1:
                centre(d, box, lab, f, (255, 250, 196, 255))
            elif row == 2:
                centre(d, box, lab, f, (150, 156, 166, 255))
            else:
                off = max(1, DETAIL // 2)
                centre(d, (box[0] + off, box[1] + off, box[2] + off, box[3] + off), lab, f,
                       (0, 0, 0, 150))
                centre(d, box, lab, f, (240, 246, 253, 255))
    return im


BLUE = ((150, 205, 255), (18, 78, 190), (9, 40, 112))
RED = ((255, 172, 202), (205, 26, 86), (120, 8, 48))

case = build_case()
blue = build_atlas(BLUE)
red = build_atlas(RED)
case.save(OUT + 'phase_case.png')
blue.save(OUT + 'phase_blue.png')
red.save(OUT + 'phase_red.png')
print('case %s   atlas %s' % (case.size, blue.size))
print('screen size: case %dx%d, key %dx%d'
      % (CELL_W * 6 + PAD_X * 2, CELL_H + PAD_Y * 2, CELL_W, CELL_H))

# A composite of exactly what the screen will draw, at 4x so it can be judged.
for tag, atlas in (('blue', blue), ('red', red)):
    bar = Image.new('RGBA', case.size, (0, 0, 0, 0))
    bar.alpha_composite(case)
    states = [2, 2, 0, 1, 2, 2]
    for i, st in enumerate(states):
        cell = atlas.crop((i * cw, st * ch, (i + 1) * cw, (st + 1) * ch))
        bar.alpha_composite(cell, (px + i * cw, py))
    sheet = Image.new('RGBA', (bar.width + 24, bar.height + 34), (26, 28, 32, 255))
    ImageDraw.Draw(sheet).text((12, 8),
                               'final  %s turn   -   BP lit, MP1 available, rest unreachable' % tag,
                               font=font(9 * DETAIL // 2), fill=(233, 239, 247, 255))
    sheet.alpha_composite(bar, (12, 26))
    sheet.save('build/phase-previews/final_%s.png' % tag)
print('final_blue.png, final_red.png')
