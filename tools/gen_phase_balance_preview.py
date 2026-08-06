"""Half-height phase bar: the lopsided build against a balanced one.

Three things were off centre in the first half-height pass:

  vertical    the shell was ch + pad*2 + 4 tall while the keys sat at pad from
              the top, so the whole row rode high and the case looked
              bottom-heavy;
  horizontal  the keys were inset 2 on the left and 3 on the right;
  the nose    the angled ends stayed a fixed 9 while the padding halved, so the
              left key ran into the slope while the right one stopped short of
              it -- the most visible tilt of the three.

The skew adds a fourth: a parallelogram's mass sits half a skew to the right of
its box, so a row of them needs shifting back by half a skew to look centred.
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


def build_bar(colour, cell_h, pad, skew, label_pt, balanced):
    light, mid, _dark = colour
    cw = 66 * SCALE
    ch = cell_h * SCALE
    pd = pad * SCALE
    sk = skew * SCALE
    inset = 2 * SCALE

    if balanced:
        # The nose scales with the bar, and the padding always clears it, so a
        # key can never sit inside the sloped end.
        nose = round(ch * 0.55)
        pd = nose + 3 * SCALE
        H = ch + pd * 2
        key_top = pd
        # Half a skew back, so the slanted row reads centred in its case.
        shift = -sk // 2
    else:
        nose = 9 * SCALE
        H = ch + pd * 2 + 4 * SCALE
        key_top = pd
        shift = 0

    W = cw * 6 + pd * 2
    im = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    shell = [(nose, 0), (W - nose - 1, 0), (W - 1, H // 2),
             (W - nose - 1, H - 1), (nose, H - 1), (0, H // 2)]
    d.polygon(shell, fill=(34, 37, 43, 255))
    inner = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    idraw = ImageDraw.Draw(inner)
    for y in range(2 * SCALE, H - 2 * SCALE):
        f = (y - 2 * SCALE) / max(1, H - 4 * SCALE)
        c = round(120 * 0.42 + 120 * 0.9 * (1 - abs(f - 0.42) * 2.1)) if f < 0.85 else 42
        idraw.line([3 * SCALE, y, W - 3 * SCALE, y],
                   fill=(max(28, min(252, c)),) * 3 + (255,))
    mask = Image.new('L', (W, H), 0)
    ImageDraw.Draw(mask).polygon(shell, fill=255)
    im.paste(inner, (0, 0), mask)
    d = ImageDraw.Draw(im)
    d.polygon(shell, outline=(196, 204, 214, 220), width=SCALE)

    for i in range(6):
        # Equal inset either side, and the whole row nudged by the skew.
        x0 = pd + i * cw + inset + shift
        x1 = pd + (i + 1) * cw - inset + shift
        y0, y1 = key_top, key_top + ch
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
    gf = font(label_pt * SCALE)
    centre(gd, (pd + LIT * cw + shift, key_top, pd + (LIT + 1) * cw + shift, key_top + ch),
           LABELS[LIT], gf, (255, 206, 44, 240))
    im.alpha_composite(glow.filter(ImageFilter.GaussianBlur(1.6 * SCALE)))

    d = ImageDraw.Draw(im)
    for i, lab in enumerate(LABELS):
        lf = font((label_pt if len(lab) < 3 else label_pt - 2) * SCALE)
        box = (pd + i * cw + shift, key_top, pd + (i + 1) * cw + shift, key_top + ch)
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
before = build_bar(BLUE, 15, 7, 4, 10, balanced=False)
after = build_bar(BLUE, 15, 7, 4, 10, balanced=True)

gap = 30
W = max(before.width, after.width) + 24
sheet = Image.new('RGBA', (W, before.height + after.height + gap * 2 + 40), (26, 28, 32, 255))
sd = ImageDraw.Draw(sheet)
centre(sd, (0, 2, W, 20), 'Half-height bar  -  as generated above, balanced below', font(13),
       (233, 239, 247, 255))
y = 26
centre(sd, (0, y, 150, y + 14), 'as generated', font(11), (150, 158, 170, 255))
sheet.alpha_composite(before, (12, y + 16))
y += 16 + before.height + gap
centre(sd, (0, y, 150, y + 14), 'balanced', font(11), (150, 158, 170, 255))
sheet.alpha_composite(after, (12, y + 16))

# A hairline through the middle of each bar, so any tilt is obvious.
fd = ImageDraw.Draw(sheet)
for top, bar in ((26 + 16, before), (26 + 16 + before.height + gap + 16, after)):
    fd.line([(6, top + bar.height // 2), (W - 6, top + bar.height // 2)],
            fill=(255, 90, 90, 90))

sheet = sheet.resize((round(sheet.width * 1.5), round(sheet.height * 1.5)), Image.LANCZOS)
sheet.save('build/phase-previews/height_balance.png')
print('height_balance.png  before=%dpx after=%dpx (1x: %d vs %d)'
      % (before.height, after.height, before.height // SCALE, after.height // SCALE))
