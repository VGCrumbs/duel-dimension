"""Draws the duel-music button's two states.

A 256x256 atlas because that is what this mod's widget textures are and what
DdBlitUtil/TextureButton divide by; the two 16x16 cells sit in the top-left and
everything else is transparent. Authored at final size rather than drawn large
and shrunk, so the pixels land where they were put.

Cell 0 (0,0)  playing: speaker, two sound bars, in the panels' light grey.
Cell 1 (16,0) muted:   the same speaker greyed out, with a red cross.
"""
from PIL import Image

LIGHT = (230, 234, 242, 255)   # 0xE6EAF2, the mod's bright caption colour
GREY = (138, 147, 163, 255)    # 0x8A93A3, its "inactive" colour
RED = (255, 107, 107, 255)     # 0xFF6B6B, its "no"/warning colour

img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
px = img.load()


def plot(x, y, colour, ox=0):
    px[ox + x, y] = colour


def speaker(ox, colour):
    """The cone, as a column of spans -- a trapezoid opening to the right."""
    for x, (y0, y1) in {
        3: (6, 9),
        4: (6, 9),
        5: (5, 10),
        6: (4, 11),
        7: (3, 12),
        8: (3, 12),
    }.items():
        for y in range(y0, y1 + 1):
            plot(x, y, colour, ox)


# ---- cell 0: playing ----
speaker(0, LIGHT)
for y in range(6, 10):
    plot(10, y, LIGHT)
for y in range(4, 12):
    plot(12, y, LIGHT)

# ---- cell 1: muted ----
speaker(16, GREY)
for i in range(4):
    plot(10 + i, 5 + i, RED, 16)
    plot(13 - i, 5 + i, RED, 16)

img.save("src/main/resources/assets/dueldimension/textures/gui/duel/music.png")
print("wrote textures/gui/duel/music.png (256x256, cells at 0,0 and 16,0)")
