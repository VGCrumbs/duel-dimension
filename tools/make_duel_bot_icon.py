"""Builds the Duel Bot's inventory icon from the Duel Bot's own skin.

The icon that was here before was a 16x16 crop of the entity skin saved with NO
ALPHA CHANNEL -- every one of its 256 pixels opaque -- so the skin's beige
background came with it and the item drew as a solid tan square with a face in
it. Every other item texture in the mod is palette-mode with transparency.

Rather than hand-paint a replacement, this reads the colours out of
`textures/entity/duelist/duel_bot.png` and lays them out as an item: a head with
a visor, an antenna, and a transparent margin around it so it reads as an object
on the hotbar instead of a tile.

Run from the repo root:  python tools/make_duel_bot_icon.py
"""
import os

from PIL import Image

SKIN = 'shared/resources/assets/dueldimension/textures/entity/duelist/duel_bot.png'
OUT = 'shared/resources/assets/dueldimension/textures/item/duel_bot.png'

# The head's front face on a 64x64 skin, which is where the bot's own casing
# and visor colours are read from rather than guessed at.
FACE = (8, 8, 16, 16)


def palette(skin):
    """The casing, its shading and the visor, taken off the head itself."""
    face = skin.crop(FACE).convert('RGBA')
    counts = {}
    for pixel in face.getdata():
        if pixel[3]:
            counts[pixel[:3]] = counts.get(pixel[:3], 0) + 1
    ranked = sorted(counts.items(), key=lambda kv: -kv[1])
    # The visor is the darkest thing on the face and the casing the commonest
    # light one, which holds for this skin and is asserted below.
    visor = min(counts, key=lambda c: sum(c))
    casing = max((c for c in counts if sum(c) > 200), key=lambda c: counts[c])
    eye = max(counts, key=lambda c: sum(c))
    assert sum(visor) < 90, 'visor is not dark: %s' % (visor,)
    assert sum(eye) > 600, 'eye is not bright: %s' % (eye,)
    print('  casing %s   visor %s   eye %s   (%d colours on the face)'
          % (casing, visor, eye, len(ranked)))
    return casing, visor, eye


def shade(colour, factor):
    return tuple(max(0, min(255, round(c * factor))) for c in colour)


def build(casing, visor, eye):
    """A 16x16 icon: antenna, casing, visor, two eyes, transparent margin."""
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = im.load()

    def box(x0, y0, x1, y1, colour):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                px[x, y] = colour + (255,)

    dark = shade(casing, 0.72)
    lit = shade(casing, 1.12)

    # Antenna, off-centre so the head does not read as symmetrical furniture.
    box(7, 0, 7, 2, dark)
    box(6, 0, 6, 0, eye)

    # The casing, one pixel in from each side so the icon has a margin.
    box(2, 3, 13, 13, casing)
    # A lit top edge and a shaded bottom-right, which is the same raised
    # bevel the hub's panels use.
    box(2, 3, 13, 3, lit)
    box(13, 4, 13, 13, dark)
    box(3, 13, 13, 13, dark)

    # The visor, inset, with the two eyes the entity has.
    box(4, 5, 11, 10, visor)
    box(5, 6, 6, 8, eye)
    box(9, 6, 10, 8, eye)

    # Corners cut, so the head is a rounded object rather than a rectangle.
    for x, y in ((2, 3), (13, 3), (2, 13), (13, 13)):
        px[x, y] = (0, 0, 0, 0)
    return im


def main():
    if not os.path.isfile(SKIN):
        raise SystemExit('no skin at %s' % SKIN)
    skin = Image.open(SKIN).convert('RGBA')
    icon = build(*palette(skin))
    transparent = sum(1 for p in icon.getdata() if p[3] == 0)
    assert transparent > 0, 'the icon has no transparency, which was the bug'
    icon.save(OUT)
    print('  wrote %s  %s  %d transparent of 256' % (OUT, icon.size, transparent))


if __name__ == '__main__':
    main()
