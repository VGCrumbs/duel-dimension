"""Converts a legacy 64x32 Minecraft skin to the modern 64x64 layout.

The 1.19 player model reads the left arm and left leg from their own regions,
which a 64x32 skin does not have -- the old format drew both sides from one
mirrored half. Minecraft's own converter mirrors the right limb into the left
slot, and this does the same, so a skin drawn in the old format renders on the
new model instead of sampling empty texture.

    python tools/convert_skin.py in.png out.png
"""
import sys
from PIL import Image


def mirror(image, source, destination):
    """Copies a limb box, flipping it the way the old format implied."""
    # A limb is six faces in a row: right, front, left, back on the middle band,
    # with top and bottom above. Mirroring the whole box and swapping the two
    # side faces is what turns a right limb into a left one.
    sx, sy, w, h = source
    dx, dy = destination
    box = image.crop((sx, sy, sx + w, sy + h)).transpose(Image.FLIP_LEFT_RIGHT)
    image.paste(box, (dx, dy))


def convert(source, destination):
    old = Image.open(source).convert('RGBA')
    if old.size == (64, 64):
        old.save(destination)
        return 'already 64x64'
    if old.size != (64, 32):
        raise SystemExit('expected a 64x32 or 64x64 skin, got %dx%d' % old.size)

    new = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    new.paste(old, (0, 0))

    # Right leg (0,16) -> left leg (16,48); right arm (40,16) -> left arm (32,48).
    mirror(new, (0, 16, 16, 16), (16, 48))
    mirror(new, (40, 16, 16, 16), (32, 48))
    new.save(destination)
    return 'converted 64x32 -> 64x64'


if __name__ == '__main__':
    print(convert(sys.argv[1], sys.argv[2]))
