"""Draws the deck editor's three section labels as art.

MAIN, EXTRA and SIDE used to be font text -- "Main Deck  0 / 60" in the panel's
body colour, which read as a caption rather than as the head of a section, and
said "Deck" three times inside a deck editor.

The word is now a plate: a slate tab with a gold accent bar down its left edge
and the word engraved in the hub's own gold. The COUNT stays as text, because it
changes; the word never does, and CLAUDE.md's rule is that anything that is not
text is art.

Authored at 1:1 GUI units, like `panel.png` and `button.png`. GUI textures are
sampled nearest, so a 1:1 plate upscales to whole crisp blocks at gui scale 3 --
authoring it larger and drawing it down would be the only way to get a blurry
one.
"""
import io
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
OUT = os.path.join(REPO, "shared", "resources", "assets", "dueldimension",
                   "textures", "gui", "deckeditor")

# The hub palette, taken from panel.png and the gold the screens already use
# for a heading (0xFFF4D089).
GOLD = (244, 208, 137, 255)
GOLD_DIM = (150, 122, 68, 255)
TOP = (58, 65, 79, 255)
BOTTOM = (32, 36, 44, 255)
EDGE_TOP = (120, 132, 150, 170)
EDGE_BOTTOM = (12, 13, 17, 255)
SHADOW = (0, 0, 0, 140)

H = 12          # the header band the editor already reserves
PAD_L = 4       # between the accent bar and the first letter
PAD_R = 5
ACCENT = 2
LETTER_W = 5
LETTER_H = 7
TRACK = 1       # between letters

GLYPHS = {
    "M": ["X...X", "XX.XX", "X.X.X", "X...X", "X...X", "X...X", "X...X"],
    "A": [".XXX.", "X...X", "X...X", "XXXXX", "X...X", "X...X", "X...X"],
    "I": ["XXXXX", "..X..", "..X..", "..X..", "..X..", "..X..", "XXXXX"],
    "N": ["X...X", "XX..X", "X.X.X", "X.X.X", "X..XX", "X...X", "X...X"],
    "E": ["XXXXX", "X....", "X....", "XXXX.", "X....", "X....", "XXXXX"],
    "X": ["X...X", "X...X", ".X.X.", "..X..", ".X.X.", "X...X", "X...X"],
    "T": ["XXXXX", "..X..", "..X..", "..X..", "..X..", "..X..", "..X.."],
    "R": ["XXXX.", "X...X", "X...X", "XXXX.", "X.X..", "X..X.", "X...X"],
    "S": [".XXXX", "X....", "X....", ".XXX.", "....X", "....X", "XXXX."],
    "D": ["XXXX.", "X...X", "X...X", "X...X", "X...X", "X...X", "XXXX."],
    # Not used by a section plate; the NEW badge shares this alphabet.
    "W": ["X...X", "X...X", "X...X", "X.X.X", "X.X.X", "XX.XX", "X...X"],
}


def word_width(word: str) -> int:
    return len(word) * LETTER_W + (len(word) - 1) * TRACK


def plate(word: str) -> Image.Image:
    width = ACCENT + PAD_L + word_width(word) + PAD_R
    im = Image.new("RGBA", (width, H), (0, 0, 0, 0))
    px = im.load()

    # Body: a vertical ramp, lighter at the top, so the plate reads as lit from
    # above like every other piece of hub furniture.
    for y in range(H):
        t = y / (H - 1)
        colour = tuple(round(TOP[i] + (BOTTOM[i] - TOP[i]) * t) for i in range(4))
        for x in range(width):
            px[x, y] = colour
    for x in range(width):
        px[x, 0] = EDGE_TOP
        px[x, H - 1] = EDGE_BOTTOM

    # A corner pixel off each end, which is all it takes to stop a rectangle
    # looking like a rectangle.
    for cx, cy in ((0, 0), (width - 1, 0), (0, H - 1), (width - 1, H - 1)):
        px[cx, cy] = (0, 0, 0, 0)

    # The accent bar: the one saturated thing on the plate, so the eye finds the
    # section heads down the panel without reading them.
    for y in range(1, H - 1):
        px[0, y] = GOLD_DIM
        px[1, y] = GOLD

    # The word, baseline centred in the band.
    top = (H - LETTER_H) // 2
    x0 = ACCENT + PAD_L
    for letter in word:
        rows = GLYPHS[letter]
        for row, bits in enumerate(rows):
            for column, bit in enumerate(bits):
                if bit != "X":
                    continue
                x, y = x0 + column, top + row
                # Shadow first, so a letter's own pixels win where they meet.
                if y + 1 < H:
                    px[x, y + 1] = SHADOW
        for row, bits in enumerate(rows):
            for column, bit in enumerate(bits):
                if bit == "X":
                    px[x0 + column, top + row] = GOLD
        x0 += LETTER_W + TRACK
    return im


def main() -> None:
    os.makedirs(OUT, exist_ok=True)
    for word in ("MAIN", "EXTRA", "SIDE"):
        im = plate(word)
        path = os.path.join(OUT, "label_%s.png" % word.lower())
        im.save(path)
        print("%-24s %dx%d" % (os.path.basename(path), im.width, im.height))


if __name__ == "__main__":
    main()
