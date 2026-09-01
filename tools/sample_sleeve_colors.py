"""Assigns each sleeve a representative colour, so the shop can sort by it.

Writes `sleeve_colors.json`, which `import_sleeves.py` bakes into
`CardSleevesType` as a constructor argument. The game never samples a texture --
it reads one int per sleeve.

<h2>Prominent, not average</h2>
Three methods were built and compared on a contact sheet of the whole catalogue
in each one's own sort order. `--contact-sheet` still renders the last two, so
the choice can be re-checked rather than believed.

  * **Plain mean of every pixel.** Useless. These are busy, mostly dark
    illustrations: a red dragon on black, a blue dragon on black and a green
    dragon on black all average to the same grey-brown, differing by a couple of
    units. Sorting by that sorts by nothing.
  * **Saturation-weighted mean** (`representative`). Sorts, but muddily. It
    blends, so art built from two colours reports the colour between them -- a
    red-and-blue sleeve comes out purple, a colour that appears nowhere on it,
    and it lands between the reds and the blues belonging with neither. The
    whole strip skews dark and desaturated.
  * **Dominant hue** (`dominant`), which is what ships. A saturation-weighted
    histogram over hue, peak wins, and the colour returned is the mean of the
    pixels IN that peak -- so it is a colour the sleeve actually has. Yellows
    come out yellow and cyans cyan where the mean gave olive and slate.

Both surviving methods weight by saturation times value, because by raw pixel
count the dominant colour of nearly every one of these is the dark background:
true, and useless.

Averaging happens in **linear** RGB. sRGB values are gamma encoded, and adding
them directly overweights the dark end -- the classic reason averaged images
come out muddier than either input.
"""
import argparse
import colorsys
import io
import json
import math
import os
import sys

from PIL import Image

try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    pass

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
NAMES = os.path.join(HERE, "sleeve_names.json")
OUT = os.path.join(HERE, "sleeve_colors.json")
# The tier is a sampling decision, not a quality one: 256 is plenty of pixels
# for a mean and reads 260 files in a couple of seconds.
TIER = 256
TEX = os.path.join(REPO, "shared", "resources", "assets", "dueldimension",
                   "textures", "item", str(TIER))


def to_linear(c):
    c = c / 255.0
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def to_srgb(c):
    c = max(0.0, min(1.0, c))
    v = c * 12.92 if c <= 0.0031308 else 1.055 * (c ** (1 / 2.4)) - 0.055
    return int(round(v * 255))


LINEAR = [to_linear(i) for i in range(256)]


def representative(path):
    """One colour for one sleeve. See the module docstring for the weighting."""
    im = Image.open(path).convert("RGBA")
    # Only the art. The canvas is half transparent margin, and averaging that in
    # would drag every sleeve toward whatever alpha compositing decided.
    box = im.getchannel("A").getbbox()
    if box:
        im = im.crop(box)
    im = im.resize((64, 92), Image.LANCZOS)

    sin = cos = sat_w = weight = 0.0
    lin_r = lin_g = lin_b = 0.0
    n = 0
    for r, g, b, a in im.getdata():
        if a < 128:
            continue
        n += 1
        lin_r += LINEAR[r]
        lin_g += LINEAR[g]
        lin_b += LINEAR[b]
        h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
        w = s * v          # vivid AND bright pixels decide the hue
        weight += w
        sat_w += s * w
        angle = h * 2 * math.pi
        sin += math.sin(angle) * w
        cos += math.cos(angle) * w
    if not n:
        return 0x000000

    # Value from the plain mean, so a dark sleeve reads dark.
    value = colorsys.rgb_to_hsv(to_srgb(lin_r / n) / 255,
                                to_srgb(lin_g / n) / 255,
                                to_srgb(lin_b / n) / 255)[2]
    if weight <= 1e-6:
        # Genuinely achromatic: no pixel had any colour to vote with.
        grey = to_srgb(lin_r / n), to_srgb(lin_g / n), to_srgb(lin_b / n)
        return (grey[0] << 16) | (grey[1] << 8) | grey[2]

    hue = (math.atan2(sin, cos) / (2 * math.pi)) % 1.0
    sat = sat_w / weight
    r, g, b = colorsys.hsv_to_rgb(hue, sat, max(value, 0.06))
    return (int(round(r * 255)) << 16) | (int(round(g * 255)) << 8) | int(round(b * 255))


HUE_BINS = 36          # 10 degrees each, wide enough that noise does not split a peak
# Mean saturation-times-value per pixel, below which the art has no colour
# worth naming. Chosen from the contact sheet: the sleeves it catches are
# the monochrome ones and nothing else.
COLOURFUL_ENOUGH = 0.02


def dominant(path):
    """The most PROMINENT colour: the peak of a saturation-weighted hue histogram.

    The difference from a weighted mean is what happens to art built from two
    colours. A mean blends them -- a red-and-blue sleeve reports purple, which is
    a colour that appears nowhere on it, and it sorts between the reds and the
    blues where it belongs with neither. A histogram picks the larger of the two
    and returns a colour the sleeve actually has.

    Weighted by saturation times value for the same reason the mean was: by raw
    pixel count the dominant colour of almost every one of these is the dark
    background, which is true and useless.

    Neighbouring bins are folded into the peak before averaging, because a hue
    that straddles a bin edge would otherwise lose half its votes to the bin next
    door and could lose the peak to a smaller, better-centred cluster.
    """
    im = Image.open(path).convert("RGBA")
    box = im.getchannel("A").getbbox()
    if box:
        im = im.crop(box)
    im = im.resize((64, 92), Image.LANCZOS)

    bins = [0.0] * HUE_BINS
    acc = [[0.0, 0.0, 0.0] for _ in range(HUE_BINS)]   # weighted r,g,b per bin
    achromatic_w = 0.0
    lin_r = lin_g = lin_b = 0.0
    n = 0
    for r, g, b, a in im.getdata():
        if a < 128:
            continue
        n += 1
        lin_r += LINEAR[r]
        lin_g += LINEAR[g]
        lin_b += LINEAR[b]
        h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
        w = s * v
        if s < 0.15:
            continue
        achromatic_w += w
        i = int(h * HUE_BINS) % HUE_BINS
        bins[i] += w
        acc[i][0] += LINEAR[r] * w
        acc[i][1] += LINEAR[g] * w
        acc[i][2] += LINEAR[b] * w
    if not n:
        return 0x000000

    # Each bin scored with its neighbours, so a peak split across an edge still wins.
    best, best_score = -1, 0.0
    for i in range(HUE_BINS):
        score = bins[i] + 0.5 * (bins[(i - 1) % HUE_BINS] + bins[(i + 1) % HUE_BINS])
        if score > best_score:
            best, best_score = i, score
    # Achromatic only when the art has almost no colour AT ALL, measured against
    # the pixel count rather than against a competing vote. An earlier version
    # let greys vote against the peak and put a fifth of the catalogue in the
    # grey bucket -- including sleeves with an obvious colour on them.
    if best < 0 or achromatic_w / n < COLOURFUL_ENOUGH:
        # Report the plain mean, which lands it in the achromatic group.
        return (to_srgb(lin_r / n) << 16) | (to_srgb(lin_g / n) << 8) | to_srgb(lin_b / n)

    r = g = b = w = 0.0
    for i in ((best - 1) % HUE_BINS, best, (best + 1) % HUE_BINS):
        r += acc[i][0]
        g += acc[i][1]
        b += acc[i][2]
        w += bins[i]
    return (to_srgb(r / w) << 16) | (to_srgb(g / w) << 8) | to_srgb(b / w)


def plain_mean(path):
    """The naive version, kept only so --contact-sheet can show why it is not used."""
    im = Image.open(path).convert("RGBA")
    box = im.getchannel("A").getbbox()
    if box:
        im = im.crop(box)
    im = im.resize((64, 92), Image.LANCZOS)
    r = g = b = 0.0
    n = 0
    for pr, pg, pb, pa in im.getdata():
        if pa < 128:
            continue
        n += 1
        r += LINEAR[pr]
        g += LINEAR[pg]
        b += LINEAR[pb]
    if not n:
        return 0x000000
    return (to_srgb(r / n) << 16) | (to_srgb(g / n) << 8) | to_srgb(b / n)


def sort_key(rgb):
    """The order the game will use. Mirrored in CardSleevesType.colourOrder."""
    r, g, b = (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF
    h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
    # Achromatic first, ordered by lightness: a hue below this saturation is
    # noise, and interleaving greys with colours makes both look unsorted.
    if s < 0.15:
        return (0, v, 0.0)
    return (1, h, v)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--contact-sheet", metavar="PATH",
                        help="render the catalogue in colour order, both methods")
    args = parser.parse_args()

    names = json.load(io.open(NAMES, encoding="utf-8"))["names"]
    rows = {}
    plain = {}
    ids = sorted(names)
    for i, item_id in enumerate(ids):
        # The file is named by slug, which import_sleeves derived from the name.
        import re
        slug = re.sub(r"[^a-z0-9]+", "_", names[item_id].lower()).strip("_")
        path = os.path.join(TEX, "sleeves_%s.png" % slug)
        if not os.path.exists(path):
            continue
        rows[item_id] = dominant(path)
        if args.contact_sheet:
            plain[item_id] = representative(path)
        if (i + 1) % 50 == 0:
            print("   %d/%d" % (i + 1, len(ids)))

    # The plain back is a sleeve too: the shop does not sell it, but the deck
    # editor's picker lists it and sorts alongside everything else.
    back = os.path.join(TEX, "card_back.png")
    if os.path.exists(back):
        rows["card_back"] = dominant(back)

    io.open(OUT, "w", encoding="utf-8", newline="\n").write(json.dumps(
        {"note": "representative colour per protector id; see the module docstring",
         "tier_sampled": TIER,
         "colors": {k: "%06X" % v for k, v in rows.items()}},
        indent=2) + "\n")
    print("%d colours -> %s" % (len(rows), os.path.basename(OUT)))

    if args.contact_sheet:
        sheet(rows, plain, args.contact_sheet)


def sheet(rows, plain, path):
    """Two strips, both in their own colour order, so the methods can be compared."""
    order_w = sorted(rows, key=lambda k: sort_key(rows[k]))
    order_p = sorted(plain, key=lambda k: sort_key(plain[k]))
    cols = 40
    cw, ch = 24, 34
    height = ((len(order_w) + cols - 1) // cols) * ch * 2 + 40
    im = Image.new("RGB", (cols * cw, height), (24, 26, 32))
    from PIL import ImageDraw
    d = ImageDraw.Draw(im)
    y0 = 4
    for label, order, table in (("dominant hue (shipped)", order_w, rows),
                                ("weighted mean (rejected)", order_p, plain)):
        d.text((4, y0), label, fill=(230, 230, 230))
        y0 += 14
        for i, k in enumerate(order):
            x = (i % cols) * cw
            y = y0 + (i // cols) * ch
            d.rectangle([x, y, x + cw - 2, y + ch - 2], fill=tuple(
                ((table[k] >> 16) & 255, (table[k] >> 8) & 255, table[k] & 255)))
        y0 += ((len(order) + cols - 1) // cols) * ch + 10
    im.save(path)
    print("contact sheet -> %s" % path)


if __name__ == "__main__":
    main()
