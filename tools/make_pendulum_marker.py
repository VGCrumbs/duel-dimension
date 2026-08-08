"""Splits the supplied pendulum artwork into a left and a right zone marker.

The source holds both gems side by side -- blue then red -- with a clear gap
between them, so the split is found rather than hardcoded: the columns that
carry any opaque pixel form two runs, and each run is one gem.

Blue is the LEFT Pendulum Zone and red the RIGHT, which is how the scales are
printed on a Pendulum card and how a playmat marks the two zones. One colour
per zone: the first attempt put both on both, which is what a card shows and a
zone does not.

Each gem is centred in a square canvas rather than cropped tight, because the
board draws the marker into a square rect -- letterboxing here is what keeps
the gem's own proportions instead of stretching it to the zone.
"""
from PIL import Image

SRC = r"C:\Users\Admin\Downloads\d773e1y-c0331539-7ec8-419b-953b-01bbcbc08409.png"
OUT = "src/main/resources/assets/dueldimension/textures/duel/"
SIDE = 128

im = Image.open(SRC).convert("RGBA")
w, h = im.size
px = im.load()

# The occupied column runs. Two of them, one gem each.
runs, inrun = [], False
for x in range(w):
    filled = any(px[x, y][3] > 8 for y in range(h))
    if filled and not inrun:
        start, inrun = x, True
    elif not filled and inrun:
        runs.append((start, x - 1))
        inrun = False
if inrun:
    runs.append((start, w - 1))

assert len(runs) == 2, "expected two gems side by side, found %d runs" % len(runs)

for (x0, x1), name in zip(runs, ("pendulum_zone_left", "pendulum_zone_right")):
    gem = im.crop((x0, 0, x1 + 1, h))
    gem = gem.crop(gem.getbbox())            # trim the blank rows too
    side = max(gem.size)
    square = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    square.paste(gem, ((side - gem.size[0]) // 2, (side - gem.size[1]) // 2))
    square = square.resize((SIDE, SIDE), Image.LANCZOS)
    square.save(OUT + name + ".png")
    print("wrote %s.png  (from columns %d..%d, %dx%d)" % (name, x0, x1, SIDE, SIDE))
