"""The chain toggle's symbol: two interlocking links.

Written to `chain_toggle.png`, NOT `chain.png`: that name was already taken by
the marker the board puts on a card whose effect is chaining, and this overwrote
it once. Two different pictures, two different names.

White art, because the duel HUD tints it -- lit while chaining is automatic and
dulled while it is manual, the same trick `HubTextures.CHECK` uses. Drawn at 8x
and reduced, so the curves survive being shown at about sixteen units.

## Interlocking, which is the whole job

Two rings drawn one after the other read as a figure of eight. What makes them a
CHAIN is that one passes behind the other, so the back link is drawn first, a
notch is cut out of it where the front link crosses, and the front link is drawn
into the gap. Skip the notch and the icon is a pair of spectacles.

Run from the repo root:  python tools/make_chain_icon.py
"""
import io
import os

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
OUT = os.path.join(REPO, "shared", "resources", "assets", "dueldimension",
                   "textures", "duel", "chain_toggle.png")

SIZE = 64
SCALE = 8
WHITE = (255, 255, 255, 255)
CLEAR = (0, 0, 0, 0)

# The two links, as boxes in icon units, offset on the diagonal so the pair sits
# square in the frame rather than lying flat across it.
# They must OVERLAP, not merely touch: the front link's arc has to cross into
# the back link's opening or the pair reads as two rings side by side. About a
# third of each link is inside the other.
BACK = (5, 9, 41, 37)
FRONT = (23, 27, 59, 55)
THICK = 6


def ring(draw, box, colour, width):
    draw.rounded_rectangle([v * SCALE for v in box],
                           radius=((box[3] - box[1]) * SCALE) // 2,
                           outline=colour, width=width * SCALE)


def main():
    big = Image.new("RGBA", (SIZE * SCALE, SIZE * SCALE), CLEAR)
    draw = ImageDraw.Draw(big)

    # 1. the back link, whole
    ring(draw, BACK, WHITE, THICK)

    # 2. the notch: everything the front link is about to occupy, plus a hair of
    #    clearance either side so the gap reads at the reduced size.
    gap = Image.new("RGBA", big.size, CLEAR)
    ImageDraw.Draw(gap).rounded_rectangle(
        [(FRONT[0] - 2) * SCALE, (FRONT[1] - 2) * SCALE,
         (FRONT[2] + 2) * SCALE, (FRONT[3] + 2) * SCALE],
        radius=((FRONT[3] - FRONT[1]) * SCALE) // 2,
        outline=(255, 255, 255, 255), width=(THICK + 4) * SCALE)
    # Paste transparency THROUGH that shape, which is what cuts the notch.
    big.paste(CLEAR, (0, 0), gap.split()[3])

    # 3. the front link, into the gap it just made
    ring(draw, FRONT, WHITE, THICK)

    icon = big.resize((SIZE, SIZE), Image.LANCZOS)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    icon.save(OUT)
    print("  wrote %s %s" % (os.path.relpath(OUT, REPO), icon.size))

    io.open(OUT + ".mcmeta", "w", encoding="utf-8", newline="\n").write(
        '{\n  "texture": {\n    "blur": true,\n    "clamp": true\n  }\n}\n')
    print("  wrote %s.mcmeta" % os.path.relpath(OUT, REPO))


if __name__ == "__main__":
    main()
