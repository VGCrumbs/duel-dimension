"""Draws textures/block/sleeve_shop.png, the sleeve shop's block face.

There is no shapes-in-code option for a block texture, and there is no art to
port -- the Forge tree has no sleeve shop -- so the art is generated here and
the generator is the record of what it is made of.

EVERY COLOUR IS LIFTED FROM textures/block/card_shop.png, sampled rather than
guessed (run this file's `sample()` to see the source rows). The two blocks are
the same piece of furniture in the same shop: the same near-black outline, the
same vertical body gradient, the same pale display frame around a dark window,
and the same shelf across the bottom. Only what is IN the window differs, which
is exactly the difference between the two blocks -- the card shop stands three
booster packs upright on a rack, the sleeve shop fans a stack of sleeves.

16x16 like its sibling, and one texture for all six faces, because both blocks
are `block/cube_all` (see models/block/card_shop.json).
"""
import io
import os

from PIL import Image

OUT = "src/main/resources/assets/dueldimension/textures/block/sleeve_shop.png"
SOURCE = "src/main/resources/assets/dueldimension/textures/block/card_shop.png"

SIZE = 16

# --- the card shop's palette, exactly ---------------------------------------
OUTLINE = (0x0E, 0x10, 0x14, 255)   # the 1px frame around the whole face
BODY_TOP = (0x39, 0x41, 0x52)       # body gradient, row 1
BODY_BOTTOM = (0x28, 0x2F, 0x3D)    # body gradient, row 14
FRAME = (0x78, 0x84, 0x96, 255)     # the pale display frame
WINDOW = (0x16, 0x1A, 0x22, 255)    # the dark glass behind the goods
SHELF = (0x60, 0x68, 0x78, 255)     # the lower shelf
SHELF_SLOT = (0x14, 0x16, 0x1C, 255)  # the recess in it
GOLD = (0xD6, 0xAC, 0x54, 255)      # the product colour: packs there, a sleeve here


def body_gradient(source):
    """The card shop's body shade for each row, READ OFF IT rather than modelled.

    It walks 394152 down to 282F3D over rows 1..14, but not in even steps, and a
    linear interpolation came out one value off on five of the fourteen rows --
    invisible on its own and plainly wrong the moment the two blocks are placed
    side by side. Column 1 of card_shop.png is body on every one of those rows,
    so the answer is simply there to be read.
    """
    pixels = source.load()
    return [pixels[1, y] for y in range(SIZE)]


def main():
    source = Image.open(SOURCE).convert("RGBA")
    body = body_gradient(source)

    image = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    pixels = image.load()

    # The outline ring and the body behind everything.
    for y in range(SIZE):
        for x in range(SIZE):
            if x == 0 or x == SIZE - 1 or y == 0 or y == SIZE - 1:
                pixels[x, y] = OUTLINE
            else:
                pixels[x, y] = body[y]

    # The display case: a pale frame two pixels in, its glass inside that.
    # Same rows as the card shop's (2..9), so the two blocks line up when they
    # stand side by side, which is how a shop with two counters will be built.
    for x in range(2, 14):
        pixels[x, 2] = FRAME
        pixels[x, 9] = FRAME
    for y in range(3, 9):
        pixels[2, y] = FRAME
        pixels[13, y] = FRAME
        for x in range(3, 13):
            pixels[x, y] = WINDOW

    # The goods. The card shop draws three upright bars -- packs on a rack; a
    # sleeve is a flat thing sold in a pack of fifty, so it is drawn as a fanned
    # STACK: three cards stepped up and to the right, each one pixel proud of
    # the one behind. Back to front, so the front card overwrites the corner of
    # the one behind it and the overlap reads as depth rather than as a stripe.
    #
    # The two behind are the frame's own grey and the shelf's darker grey, which
    # is what makes the front one -- in the same gold the card shop sells its
    # packs in -- read as the product and the others as the pile it came off.
    for step, colour in ((0, SHELF), (1, FRAME), (2, GOLD)):
        left = 4 + step * 2
        top = 6 - step
        for y in range(top, top + 3):
            for x in range(left, left + 4):
                pixels[x, y] = colour

    # The shelf across the bottom, identical to the card shop's.
    for x in range(3, 13):
        pixels[x, 11] = SHELF
        pixels[x, 12] = SHELF_SLOT
        pixels[x, 13] = SHELF
    pixels[3, 12] = SHELF
    pixels[12, 12] = SHELF

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    image.save(OUT)
    print("wrote %s (%dx%d)" % (OUT, SIZE, SIZE))

    # Prove the palette claim rather than asserting it: every colour written
    # here must already appear in the card shop's own face.
    theirs = set(source.load()[x, y] for y in range(source.size[1])
                 for x in range(source.size[0]))
    mine = set(pixels[x, y] for y in range(SIZE) for x in range(SIZE))
    stray = sorted(c for c in mine if c not in theirs)
    if stray:
        print("NOT in card_shop.png: %s" % ", ".join("%02X%02X%02X" % c[:3] for c in stray))
    else:
        print("every colour is one of card_shop.png's own")


if __name__ == "__main__":
    main()
