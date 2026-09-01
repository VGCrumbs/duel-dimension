"""Draws the NEW badge that marks a card the collection did not already have.

Art, not text, by CLAUDE.md's rule -- and the word never changes, so there is
nothing for the font to do here.

Authored at 1:1 GUI units like the rest of the hub's furniture, and drawn at
that size: GUI textures sample nearest, so a badge blitted at its own size is
pixel-exact at every gui scale rather than resampled at some of them.

Shares its letterforms with `make_deck_part_labels.py` -- the two are the same
alphabet at the same size, and two hand-drawn N's that differ by a pixel would
be a thing to notice.
"""
import os

from PIL import Image

from make_deck_part_labels import GLYPHS, LETTER_H, LETTER_W

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
OUT = os.path.join(REPO, "shared", "resources", "assets", "dueldimension",
                   "textures", "gui", "common")

# Warm and loud, against a hub that is slate and gold everywhere else: the badge
# has one job, which is to be the thing you notice on a screen full of cards.
FILL_TOP = (232, 86, 62, 255)
FILL_BOTTOM = (178, 44, 34, 255)
RIM = (255, 226, 168, 255)
RIM_SHADE = (146, 106, 44, 255)
SHADOW = (0, 0, 0, 130)
INK = (255, 249, 232, 255)

PAD_X = 4
PAD_Y = 2
TRACK = 1
WORD = "NEW"


def badge():
    word_w = len(WORD) * LETTER_W + (len(WORD) - 1) * TRACK
    w = word_w + PAD_X * 2 + 2          # +2 for the rim
    h = LETTER_H + PAD_Y * 2 + 2
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = im.load()

    for y in range(h):
        t = y / (h - 1)
        colour = tuple(round(FILL_TOP[i] + (FILL_BOTTOM[i] - FILL_TOP[i]) * t)
                       for i in range(4))
        for x in range(w):
            px[x, y] = colour

    # The rim, gold on top and shaded underneath, so the badge reads as a raised
    # tag rather than a coloured rectangle.
    for x in range(w):
        px[x, 0] = RIM
        px[x, h - 1] = RIM_SHADE
    for y in range(h):
        px[0, y] = RIM if y < h // 2 else RIM_SHADE
        px[w - 1, y] = RIM if y < h // 2 else RIM_SHADE
    # Corners cut, which is all it takes to stop it looking like a rectangle.
    for cx, cy in ((0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)):
        px[cx, cy] = (0, 0, 0, 0)

    x0 = 1 + PAD_X
    top = 1 + PAD_Y
    for letter in WORD:
        rows = GLYPHS[letter]
        for row, bits in enumerate(rows):
            for column, bit in enumerate(bits):
                if bit == "X" and top + row + 1 < h - 1:
                    px[x0 + column, top + row + 1] = SHADOW
        for row, bits in enumerate(rows):
            for column, bit in enumerate(bits):
                if bit == "X":
                    px[x0 + column, top + row] = INK
        x0 += LETTER_W + TRACK
    return im


def main():
    os.makedirs(OUT, exist_ok=True)
    im = badge()
    path = os.path.join(OUT, "new_badge.png")
    im.save(path)
    print("new_badge.png  %dx%d" % (im.width, im.height))


if __name__ == "__main__":
    main()
