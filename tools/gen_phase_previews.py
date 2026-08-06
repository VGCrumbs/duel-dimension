import random
from PIL import Image, ImageDraw, ImageFont, ImageFilter

LABELS = ['DP', 'SP', 'MP1', 'BP', 'MP2', 'EP']
CW, CH = 66, 30
PAD = 14


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


def vgrad(d, box, top, bot):
    x0, y0, x1, y1 = box
    span = max(1, y1 - y0)
    for y in range(y0, y1):
        f = (y - y0) / span
        d.line([x0, y, x1, y],
               fill=tuple(round(top[i] + (bot[i] - top[i]) * f) for i in range(3)) + (255,))


def brushed(d, box, base=170, streaks=True):
    x0, y0, x1, y1 = box
    h = max(1, y1 - y0)
    for y in range(y0, y1):
        f = (y - y0) / h
        c = round(base * 0.42 + base * 0.9 * (1 - abs(f - 0.42) * 2.1)) if f < 0.85 else round(base * 0.35)
        c = max(28, min(252, c))
        d.line([x0, y, x1, y], fill=(c, c + 2, c + 6, 255))
    if streaks:
        rnd = random.Random(7)
        for _ in range((x1 - x0) // 2):
            sx = rnd.randint(x0, x1)
            sy = rnd.randint(y0, y1 - 1)
            d.line([sx, sy, sx + rnd.randint(4, 16), sy], fill=(255, 255, 255, rnd.randint(10, 34)))


def geometry(style):
    """Where each bay sits, so the labels can be drawn over any style."""
    if style == 'd':
        return PAD - 2, CH - 3, CH + PAD * 2 - 4
    if style == 'c':
        return PAD + 2, CH - 3, CH + PAD * 2 + 4
    return PAD, CH - 1, CH + PAD * 2


def style_a(colour, lit_index):
    light, mid, dark = colour
    W = CW * 6 + PAD * 2
    H = CH + PAD * 2
    im = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle([0, 0, W - 1, H - 1], radius=9, fill=(40, 43, 48, 255))
    brushed(d, (3, 3, W - 4, H - 4))
    d.rounded_rectangle([2, 2, W - 3, H - 3], radius=8, outline=(250, 252, 255, 190), width=1)
    d.rounded_rectangle([0, 0, W - 1, H - 1], radius=9, outline=(24, 26, 30, 255), width=1)
    for i in range(6):
        x0 = PAD + i * CW + 2
        x1 = PAD + (i + 1) * CW - 3
        y0, y1 = PAD, PAD + CH - 1
        lit = i == lit_index
        d.rounded_rectangle([x0 - 2, y0 - 2, x1 + 2, y1 + 2], radius=7, fill=(26, 28, 32, 255))
        vgrad(d, (x0, y0, x1, y1),
              tuple(min(255, c + 45) for c in light) if lit else light, mid)
        d.rounded_rectangle([x0, y0, x1, y1], radius=5,
                            outline=(255, 255, 255, 235) if lit else (208, 218, 230, 150),
                            width=2 if lit else 1)
        gloss = Image.new('RGBA', im.size, (0, 0, 0, 0))
        ImageDraw.Draw(gloss).rounded_rectangle(
            [x0 + 2, y0 + 2, x1 - 2, y0 + (y1 - y0) // 2], radius=4, fill=(255, 255, 255, 52))
        im.alpha_composite(gloss)
        d = ImageDraw.Draw(im)
    return im, 'A  Brushed rail  -  inset bays in a steel plate'


def style_b(colour, lit_index):
    light, mid, dark = colour
    W = CW * 6 + PAD * 2
    H = CH + PAD * 2
    im = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle([0, 0, W - 1, H - 1], radius=H // 2, fill=(60, 64, 70, 255))
    for y in range(3, H - 3):
        f = (y - 3) / max(1, H - 7)
        if f < 0.46:
            c = round(118 + 130 * (f / 0.46))
        elif f < 0.56:
            c = 248
        else:
            c = round(238 - 140 * ((f - 0.56) / 0.44))
        d.line([4, y, W - 5, y], fill=(c, c + 2, c + 7, 255))
    d.rounded_rectangle([2, 2, W - 3, H - 3], radius=(H - 4) // 2, outline=(255, 255, 255, 215), width=1)
    d.rounded_rectangle([0, 0, W - 1, H - 1], radius=H // 2, outline=(30, 33, 38, 255), width=1)
    for i in range(6):
        x0 = PAD + i * CW + 3
        x1 = PAD + (i + 1) * CW - 4
        y0, y1 = PAD, PAD + CH - 1
        lit = i == lit_index
        r = (y1 - y0) // 2
        d.rounded_rectangle([x0, y0, x1, y1], radius=r, fill=dark + (255,))
        vgrad(d, (x0 + 2, y0 + 2, x1 - 2, y1 - 2),
              tuple(min(255, c + 40) for c in light) if lit else light, mid)
        d.rounded_rectangle([x0, y0, x1, y1], radius=r,
                            outline=(255, 255, 255, 240) if lit else (226, 234, 244, 175),
                            width=2 if lit else 1)
        gloss = Image.new('RGBA', im.size, (0, 0, 0, 0))
        ImageDraw.Draw(gloss).ellipse([x0 + 5, y0 + 3, x1 - 5, y0 + 10],
                                      fill=(255, 255, 255, 78))
        im.alpha_composite(gloss)
        d = ImageDraw.Draw(im)
    return im, 'B  Chrome capsule  -  pill case, glossy lozenges'


def style_c(colour, lit_index):
    light, mid, dark = colour
    W = CW * 6 + PAD * 2
    H = CH + PAD * 2 + 4
    im = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    shell = [(9, 0), (W - 10, 0), (W - 1, H // 2), (W - 10, H - 1), (9, H - 1), (0, H // 2)]
    d.polygon(shell, fill=(34, 37, 43, 255))
    brushed(d, (5, 4, W - 6, H - 5), base=120, streaks=False)
    d.polygon(shell, outline=(196, 204, 214, 220), width=1)
    sk = 7
    for i in range(6):
        x0 = PAD + i * CW + 2
        x1 = PAD + (i + 1) * CW - 3
        y0, y1 = PAD + 2, PAD + CH - 1
        lit = i == lit_index
        quad = [(x0 + sk, y0), (x1, y0), (x1 - sk, y1), (x0, y1)]
        d.polygon(quad, fill=mid + (255,))
        top = tuple(min(255, c + 50) for c in light) if lit else light
        d.polygon([(x0 + sk, y0), (x1, y0), (x1 - sk // 2, (y0 + y1) // 2),
                   (x0 + sk // 2, (y0 + y1) // 2)], fill=top + (255,))
        d.polygon(quad, outline=(255, 255, 255, 245) if lit else (170, 182, 196, 150),
                  width=2 if lit else 1)
        if lit:
            d.line([(x0 + 2, y1 + 2), (x1 - 2, y1 + 2)], fill=(255, 214, 60, 235), width=2)
    return im, 'C  Angular gunmetal  -  skewed keys, lit phase underlined'


def style_d(colour, lit_index):
    light, mid, dark = colour
    W = CW * 6 + PAD * 2
    H = CH + PAD * 2 - 4
    im = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle([0, 0, W - 1, H - 1], radius=5, fill=(16, 18, 22, 255))
    d.rounded_rectangle([1, 1, W - 2, H - 2], radius=4, outline=(150, 158, 170, 200), width=1)
    d.rounded_rectangle([3, 3, W - 4, H - 4], radius=3, outline=(58, 63, 72, 255), width=1)
    for i in range(6):
        x0 = PAD + i * CW + 2
        x1 = PAD + (i + 1) * CW - 3
        y0, y1 = PAD - 2, PAD + CH - 5
        lit = i == lit_index
        d.rectangle([x0, y0, x1, y1], fill=(22, 25, 30, 255))
        if lit:
            vgrad(d, (x0 + 1, y0 + 1, x1 - 1, y1 - 1), tuple(min(255, c + 30) for c in light), mid)
        else:
            d.rectangle([x0 + 1, y0 + 1, x1 - 1, y1 - 1],
                        fill=tuple(round(c * 0.30) for c in mid) + (255,))
        d.rectangle([x0, y0, x1, y1], outline=(238, 246, 255, 230) if lit else (86, 94, 106, 190),
                    width=1)
        if i < 5:
            d.line([(x1 + 2, y0 + 3), (x1 + 2, y1 - 3)], fill=(70, 76, 86, 255))
    return im, 'D  Slim LED bezel  -  dark strip, unlit phases dimmed'


BLUE = ((150, 205, 255), (18, 78, 190), (9, 40, 112))
RED = ((255, 172, 202), (205, 26, 86), (120, 8, 48))
STYLES = (('a', style_a, 2), ('b', style_b, 2), ('c', style_c, 3), ('d', style_d, 3))

for tag, maker, lit in STYLES:
    blue, title = maker(BLUE, lit)
    red, _ = maker(RED, lit)
    bay_y, bay_h, _unused = geometry(tag)
    W = blue.width + 24
    sheet = Image.new('RGBA', (W, blue.height + red.height + 52), (26, 28, 32, 255))
    sd = ImageDraw.Draw(sheet)
    centre(sd, (0, 3, W, 21), title, font(13), (233, 239, 247, 255))
    centre(sd, (0, 19, W, 33), 'blue = your turn      red = opponent turn', font(11), (150, 158, 170, 255))
    sheet.alpha_composite(blue, (12, 32))
    sheet.alpha_composite(red, (12, 32 + blue.height + 4))

    glow = Image.new('RGBA', sheet.size, (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    rows = ((32, blue), (32 + blue.height + 4, red))
    for oy, _img in rows:
        for i, lab in enumerate(LABELS):
            if i != lit:
                continue
            f = font(15 if len(lab) < 3 else 13)
            bx0 = 12 + PAD + i * CW + 2
            bx1 = 12 + PAD + (i + 1) * CW - 3
            centre(gd, (bx0, oy + bay_y, bx1, oy + bay_y + bay_h), lab, f, (255, 206, 44, 240))
    sheet.alpha_composite(glow.filter(ImageFilter.GaussianBlur(2.4)))

    fd = ImageDraw.Draw(sheet)
    for oy, _img in rows:
        for i, lab in enumerate(LABELS):
            f = font(15 if len(lab) < 3 else 13)
            bx0 = 12 + PAD + i * CW + 2
            bx1 = 12 + PAD + (i + 1) * CW - 3
            box = (bx0, oy + bay_y, bx1, oy + bay_y + bay_h)
            if i == lit:
                centre(fd, box, lab, f, (255, 250, 196, 255))
            else:
                centre(fd, (box[0] + 1, box[1] + 1, box[2] + 1, box[3] + 1), lab, f, (0, 0, 0, 150))
                centre(fd, box, lab, f, (240, 246, 253, 255))

    sheet.resize((sheet.width * 2, sheet.height * 2), Image.LANCZOS).save(
        'build/phase-previews/style_%s.png' % tag)
    print('style_%s.png' % tag)
