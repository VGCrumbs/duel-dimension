"""Compare the current phase bar against a half-height version.

Draws the whole indicator -- shell plus keys, with a realistic mix of states --
at both heights, composited the way the screen composites it, then scales the
sheet up so the difference in legibility is visible.
"""
from PIL import Image, ImageDraw, ImageFont, ImageFilter

LABELS = ['DP', 'SP', 'MP1', 'BP', 'MP2', 'EP']
LIT = 3
IDLE = {2}
SCALE = 2


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


def build_bar(colour, cell_h, pad, skew, label_pt):
    """One complete indicator at the given key height, all in 2x units."""
    light, mid, _dark = colour
    cw = 66 * SCALE
    ch = cell_h * SCALE
    pd = pad * SCALE
    sk = skew * SCALE
    W = cw * 6 + pd * 2
    H = ch + pd * 2 + 4 * SCALE

    im = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    nose = 9 * SCALE
    shell = [(nose, 0), (W - nose - 1, 0), (W - 1, H // 2),
             (W - nose - 1, H - 1), (nose, H - 1), (0, H // 2)]
    d.polygon(shell, fill=(34, 37, 43, 255))
    inner = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    idraw = ImageDraw.Draw(inner)
    for y in range(4 * SCALE, H - 4 * SCALE):
        f = (y - 4 * SCALE) / max(1, H - 8 * SCALE)
        c = round(120 * 0.42 + 120 * 0.9 * (1 - abs(f - 0.42) * 2.1)) if f < 0.85 else 42
        idraw.line([5 * SCALE, y, W - 5 * SCALE, y],
                   fill=(max(28, min(252, c)),) * 3 + (255,))
    mask = Image.new('L', (W, H), 0)
    ImageDraw.Draw(mask).polygon(shell, fill=255)
    im.paste(inner, (0, 0), mask)
    d = ImageDraw.Draw(im)
    d.polygon(shell, outline=(196, 204, 214, 220), width=SCALE)

    for i in range(6):
        x0, x1 = pd + i * cw + 2 * SCALE, pd + (i + 1) * cw - 3 * SCALE
        y0, y1 = pd, pd + ch
        state = 1 if i == LIT else (0 if i in IDLE else 2)
        quad = [(x0 + sk, y0), (x1, y0), (x1 - sk, y1), (x0, y1)]
        half = [(x0 + sk, y0), (x1, y0), (x1 - sk // 2, (y0 + y1) // 2),
                (x0 + sk // 2, (y0 + y1) // 2)]
        if state == 1:
            face, top = mid, tuple(min(255, c + 50) for c in light)
            rim, width = (255, 255, 255, 245), 2 * SCALE
        elif state == 2:
            face, top = shade(grey(mid), 0.42), shade(grey(light), 0.42)
            rim, width = (108, 114, 124, 115), SCALE
        else:
            face, top, rim, width = mid, light, (170, 182, 196, 150), SCALE
        d.polygon(quad, fill=face + (255,))
        d.polygon(half, fill=top + (255,))
        d.polygon(quad, outline=rim, width=width)

    glow = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    f = font(label_pt * SCALE)
    centre(gd, (pd + LIT * cw, pd, pd + (LIT + 1) * cw, pd + ch), LABELS[LIT], f,
           (255, 206, 44, 240))
    im.alpha_composite(glow.filter(ImageFilter.GaussianBlur(1.8 * SCALE)))

    d = ImageDraw.Draw(im)
    for i, lab in enumerate(LABELS):
        lf = font((label_pt if len(lab) < 3 else label_pt - 2) * SCALE)
        box = (pd + i * cw, pd, pd + (i + 1) * cw, pd + ch)
        if i == LIT:
            centre(d, box, lab, lf, (255, 250, 196, 255))
        elif i in IDLE:
            centre(d, (box[0] + SCALE, box[1] + SCALE, box[2] + SCALE, box[3] + SCALE),
                   lab, lf, (0, 0, 0, 150))
            centre(d, box, lab, lf, (240, 246, 253, 255))
        else:
            centre(d, box, lab, lf, (150, 156, 166, 255))
    return im


BLUE = ((150, 205, 255), (18, 78, 190), (9, 40, 112))

# Current: 30-tall keys, 14 padding, 7 skew, 15pt labels.
current = build_bar(BLUE, 30, 14, 7, 15)
# Half: 15-tall keys, padding and skew scaled with it, labels sized to fit.
half = build_bar(BLUE, 15, 7, 4, 10)

gap = 26
W = max(current.width, half.width) + 24
sheet = Image.new('RGBA', (W, current.height + half.height + gap * 2 + 34), (26, 28, 32, 255))
sd = ImageDraw.Draw(sheet)
centre(sd, (0, 2, W, 20), 'Phase bar height  -  current above, half below', font(13),
       (233, 239, 247, 255))
y = 26
centre(sd, (0, y, 130, y + 14), 'current', font(11), (150, 158, 170, 255))
sheet.alpha_composite(current, (12, y + 14))
y += 14 + current.height + gap
centre(sd, (0, y, 130, y + 14), 'half height', font(11), (150, 158, 170, 255))
sheet.alpha_composite(half, (12, y + 14))

sheet.save('build/phase-previews/height_compare.png')
print('height_compare.png  current=%dpx  half=%dpx  (at 1x: %d vs %d)'
      % (current.height, half.height, current.height // SCALE, half.height // SCALE))
