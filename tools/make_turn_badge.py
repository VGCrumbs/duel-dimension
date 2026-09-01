"""The plate the turn number stands on, at the middle of the duel board.

White art, because the board TINTS it: green while the turn is yours and red
while it is not, which is the same pair the duel screen's badge uses. Anything
coloured here would multiply against those and come out muddy.

A plate rather than a ring or a bare number: the mat underneath is whatever
playmat the player chose, so a number with nothing behind it is a number over an
unknown background. The plate is what makes it readable on all of them.

Run from the repo root:  python tools/make_turn_badge.py
"""
import io
import os

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
OUT = os.path.join(REPO, "shared", "resources", "assets", "dueldimension",
                   "textures", "duel", "turn_badge.png")

# Drawn large and reduced, because the corners and the rim are the whole shape
# and a rounded rectangle rasterised straight to 48px stair-steps.
#
# HALF THE WIDTH IT STARTED AT. A 96 by 40 plate was a banner lying across the
# middle of the mat; the number it carries is at most three digits and needs
# nothing like that much room. The height is unchanged, so the digits -- which
# are sized off the height -- are exactly the size they were.
SCALE = 8
W, H = 48, 40
RADIUS = 13


def main():
    big = Image.new("RGBA", (W * SCALE, H * SCALE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(big)
    edge = 2 * SCALE

    # The body: mostly transparent, so the mat reads through it and the plate
    # sits ON the board rather than hiding a piece of it.
    draw.rounded_rectangle([0, 0, W * SCALE - 1, H * SCALE - 1], radius=RADIUS * SCALE,
                           fill=(255, 255, 255, 150))
    # A solid rim, which is what carries the colour at a distance: the fill is
    # too faint to tell green from red across a table, and the rim is not.
    draw.rounded_rectangle([0, 0, W * SCALE - 1, H * SCALE - 1], radius=RADIUS * SCALE,
                           outline=(255, 255, 255, 255), width=edge)
    # And a darker inner well, so a white digit standing on it has something to
    # be white against whatever the playmat is doing underneath.
    inset = edge + SCALE
    draw.rounded_rectangle([inset, inset, W * SCALE - 1 - inset, H * SCALE - 1 - inset],
                           radius=(RADIUS - 2) * SCALE, fill=(60, 60, 60, 190))

    badge = big.resize((W, H), Image.LANCZOS)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    badge.save(OUT)
    print("  wrote %s %s" % (os.path.relpath(OUT, REPO), badge.size))

    # Drawn small on the board, so it is always being reduced: bilinear, or the
    # rounded corners come back as stair steps.
    meta = OUT + ".mcmeta"
    io.open(meta, "w", encoding="utf-8", newline="\n").write(
        '{\n  "texture": {\n    "blur": true,\n    "clamp": true\n  }\n}\n')
    print("  wrote %s" % os.path.relpath(meta, REPO))


if __name__ == "__main__":
    main()
