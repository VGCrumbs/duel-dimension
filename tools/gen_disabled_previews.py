"""Four treatments for a phase that cannot be reached.

Each sheet shows one treatment on the C shell (angular gunmetal), in both turn
colours, with BP lit, MP1 reachable, and DP/SP/MP2/EP unreachable -- so the
lit / available / disabled contrast can be judged side by side.
"""
from PIL import Image, ImageDraw, ImageFont, ImageFilter

LABELS = ['DP', 'SP', 'MP1', 'BP', 'MP2', 'EP']
CW, CH, PAD, SK = 66, 30, 14, 7
LIT = 3
AVAILABLE = {2, 3}


def font(sz):
    for n in ('arialbd.ttf', 'seguisb.ttf', 'DejaVuSans-Bold.ttf'):
        try:
            return ImageFont.truetype(n, sz)
        except OSError:
            pass
    return ImageFont.load_default()


def centre(d, box, text, f, fill):
    x0, y0, x1, y1 = box
    b = d.textbbox((0, 0), text, font=f)
    d.text(((x0 + x1) / 2 - (b[2] - b[0]) / 2 - b[0],
            (y0 + y1) / 2 - (b[3] - b[1]) / 2 - b[1]), text, font=f, fill=fill)


def brushed(d, box, base=120):
    x0, y0, x1, y1 = box
    h = max(1, y1 - y0)
    for y in range(y0, y1):
        f = (y - y0) / h
        c = round(base * 0.42 + base * 0.9 * (1 - abs(f - 0.42) * 2.1)) if f < 0.85 else round(base * 0.35)
        d.line([x0, y, x1, y], fill=(max(28, min(252, c)),) * 3 + (255,))


def grey(rgb, keep=0.0):
    """Desaturate towards luminance; keep=0 is fully grey."""
    lum = 0.299 * rgb[0] + 0.587 * rgb[1] + 0.114 * rgb[2]
    return tuple(round(lum + (rgb[i] - lum) * keep) for i in range(3))


def shade(rgb, f):
    return tuple(max(0, min(255, round(c * f))) for c in rgb)


def bar(colour, treatment):
    light, mid, _dark = colour
    W, H = CW * 6 + PAD * 2, CH + PAD * 2 + 4
    im = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    shell = [(9, 0), (W - 10, 0), (W - 1, H // 2), (W - 10, H - 1), (9, H - 1), (0, H // 2)]
    d.polygon(shell, fill=(34, 37, 43, 255))
    brushed(d, (5, 4, W - 6, H - 5))
    d.polygon(shell, outline=(196, 204, 214, 220), width=1)

    for i in range(6):
        x0, x1 = PAD + i * CW + 2, PAD + (i + 1) * CW - 3
        y0, y1 = PAD + 2, PAD + CH - 1
        lit = i == LIT
        usable = i in AVAILABLE
        quad = [(x0 + SK, y0), (x1, y0), (x1 - SK, y1), (x0, y1)]
        half = [(x0 + SK, y0), (x1, y0), (x1 - SK // 2, (y0 + y1) // 2), (x0 + SK // 2, (y0 + y1) // 2)]

        if usable:
            face, top = mid, (tuple(min(255, c + 50) for c in light) if lit else light)
            rim = (255, 255, 255, 245) if lit else (170, 182, 196, 150)
        elif treatment == 1:      # desaturate to steel
            face, top = grey(mid), grey(light)
            rim = (150, 158, 168, 130)
        elif treatment == 2:      # keep the hue, drop the light right down
            face, top = shade(mid, 0.32), shade(light, 0.32)
            rim = (120, 128, 140, 120)
        elif treatment == 3:      # empty bay: no key at all, just the recess
            face, top = (24, 26, 30), (34, 37, 43)
            rim = (86, 92, 102, 150)
        else:                     # 4: grey AND dimmed, the strongest reading
            face, top = shade(grey(mid), 0.42), shade(grey(light), 0.42)
            rim = (108, 114, 124, 115)

        d.polygon(quad, fill=face + (255,))
        d.polygon(half, fill=top + (255,))
        d.polygon(quad, outline=rim, width=2 if lit else 1)
        if lit:
            d.line([(x0 + 2, y1 + 2), (x1 - 2, y1 + 2)], fill=(255, 214, 60, 235), width=2)
        if not usable and treatment == 3:
            # A faint slash, so an empty bay still reads as "closed".
            d.line([(x0 + SK + 3, y1 - 3), (x1 - 3, y0 + 3)], fill=(70, 76, 86, 200), width=1)
    return im


TITLES = {
    1: '1  Desaturated  -  colour drains to steel, brightness kept',
    2: '2  Dimmed  -  hue kept, driven right down',
    3: '3  Empty bay  -  the key is gone, only the recess and a slash',
    4: '4  Grey and dimmed  -  both, the strongest separation',
}
BLUE = ((150, 205, 255), (18, 78, 190), (9, 40, 112))
RED = ((255, 172, 202), (205, 26, 86), (120, 8, 48))

for treatment in (1, 2, 3, 4):
    blue, red = bar(BLUE, treatment), bar(RED, treatment)
    W = blue.width + 24
    sheet = Image.new('RGBA', (W, blue.height + red.height + 60), (26, 28, 32, 255))
    sd = ImageDraw.Draw(sheet)
    centre(sd, (0, 3, W, 21), TITLES[treatment], font(13), (233, 239, 247, 255))
    centre(sd, (0, 19, W, 33), 'BP lit    MP1 available    DP / SP / MP2 / EP unreachable',
           font(11), (150, 158, 170, 255))
    sheet.alpha_composite(blue, (12, 32))
    sheet.alpha_composite(red, (12, 32 + blue.height + 4))

    rows = (32, 32 + blue.height + 4)
    glow = Image.new('RGBA', sheet.size, (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    for oy in rows:
        bx0 = 12 + PAD + LIT * CW + 2
        bx1 = 12 + PAD + (LIT + 1) * CW - 3
        centre(gd, (bx0, oy + PAD + 2, bx1, oy + PAD + CH - 1), LABELS[LIT], font(15),
               (255, 206, 44, 240))
    sheet.alpha_composite(glow.filter(ImageFilter.GaussianBlur(2.4)))

    fd = ImageDraw.Draw(sheet)
    for oy in rows:
        for i, lab in enumerate(LABELS):
            f = font(15 if len(lab) < 3 else 13)
            box = (12 + PAD + i * CW + 2, oy + PAD + 2, 12 + PAD + (i + 1) * CW - 3, oy + PAD + CH - 1)
            if i == LIT:
                centre(fd, box, lab, f, (255, 250, 196, 255))
            elif i in AVAILABLE:
                centre(fd, (box[0] + 1, box[1] + 1, box[2] + 1, box[3] + 1), lab, f, (0, 0, 0, 150))
                centre(fd, box, lab, f, (240, 246, 253, 255))
            else:
                # Unreachable phases keep their letters, muted.
                centre(fd, box, lab, f, (150, 156, 166, 255) if treatment != 3 else (104, 110, 120, 255))
    sheet.resize((sheet.width * 2, sheet.height * 2), Image.LANCZOS).save(
        'build/phase-previews/disabled_%d.png' % treatment)
    print('disabled_%d.png' % treatment)
