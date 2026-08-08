"""Installs a supplied player skin as an NPC duelist's, converting if legacy.

DuelistRenderer resolves a duelist to textures/entity/duelist/<profileId>.png by
convention, so installing the file IS the wiring -- there is no registry to add
to. What there is to get right is the format: the skins already here are 64x64
and the model samples the modern layout, so a 64x32 file would leave the left
arm and leg sampling texture that is not there.

The widening is the same mapping UnderSkin.widen() uses for a player's own
imported skin -- the old format drew both limbs from one mirrored half, so the
right limb is mirrored into the left slot. Using the project's existing answer
rather than a second one keeps an NPC and a player skin converted identically.

    python tools/install_duelist_skin.py <source.png> <profileId>
"""
import sys
from PIL import Image

if len(sys.argv) != 3:
    raise SystemExit(__doc__)

source, profile = sys.argv[1], sys.argv[2]
dest = "src/main/resources/assets/dueldimension/textures/entity/duelist/%s.png" % profile

im = Image.open(source).convert("RGBA")
w, h = im.size
if (w, h) == (64, 64):
    wide = im
    note = "already modern"
elif (w, h) == (64, 32):
    wide = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    wide.paste(im, (0, 0))
    # UnderSkin.widen: right leg (0,16) -> left leg (16,48), right arm
    # (40,16) -> left arm (32,48), each mirrored horizontally.
    for (sx, sy), (dx, dy) in (((0, 16), (16, 48)), ((40, 16), (32, 48))):
        limb = im.crop((sx, sy, sx + 16, sy + 16)).transpose(Image.FLIP_LEFT_RIGHT)
        wide.paste(limb, (dx, dy))
    note = "converted 64x32 -> 64x64"
else:
    raise SystemExit("not a player skin: %dx%d" % (w, h))

wide.save(dest)
print("wrote %s (%s)" % (dest, note))
