"""Put a sensible colour UNDER the transparent pixels of a texture.

WHAT IS WRONG. A fully transparent pixel still stores an RGB, and nothing in a
paint program shows you what it is. Five of the duel disks store WHITE there --
7,680 pixels each of rgb(255,255,255) at alpha 0.

That is invisible until something averages pixels together, and the GPU averages
constantly: the block atlas is built with four mipmap levels, and every level
blends a texel with its neighbours. A blend of "opaque dark plate" and "invisible
white" is a lighter pixel, and the alpha it comes with is not low enough to hide
it. The result is white speckle scattered across the disk, worst where the art
meets its transparent margin and worst at distance, which is where the mip levels
bite.

WHAT THIS DOES. Dilation, also called alpha bleed: every fully transparent pixel
takes the colour of the nearest pixel that is not. Repeated until the transparent
region is filled, so there is no white left anywhere for a filter to find.

IT CANNOT CHANGE THE PICTURE. Alpha is never touched -- a pixel that was
invisible stays exactly as invisible. The only thing that changes is a colour
nobody can see, and the only thing that reads it is the filtering that was
producing the artifact.

Run from anywhere:  python tools/bleed_alpha.py [--dry-run]
"""

import os
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEXTURES = os.path.join(ROOT, 'mc262', 'src', 'main', 'resources', 'assets',
    'dueldimension', 'textures', 'item')

#: The four neighbours a colour can come from. Diagonals are deliberately left
#: out: they reach past a one-pixel line and would round off the corner of a
#: shape that the straight neighbours preserve.
NEIGHBOURS = [(-1, 0), (1, 0), (0, -1), (0, 1)]


def dilate(image):
    """Fill transparent pixels from their nearest opaque neighbour.

    Returns how many pixels were given a colour. Alpha is untouched throughout.
    """
    width, height = image.size
    pixels = image.load()

    # Which pixels already have a colour worth spreading. Sampled once rather
    # than re-tested as the fill proceeds, so a pixel filled in round two
    # spreads in round three and not before -- which is what makes this a
    # distance fill rather than a flood from wherever the scan happened to
    # start.
    known = [[pixels[x, y][3] > 0 for y in range(height)] for x in range(width)]
    filled = 0

    while True:
        frontier = []
        for x in range(width):
            for y in range(height):
                if known[x][y]:
                    continue
                total = [0, 0, 0]
                count = 0
                for dx, dy in NEIGHBOURS:
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < width and 0 <= ny < height and known[nx][ny]:
                        r, g, b, _ = pixels[nx, ny]
                        total[0] += r
                        total[1] += g
                        total[2] += b
                        count += 1
                if count:
                    frontier.append((x, y, total[0] // count, total[1] // count,
                        total[2] // count))
        if not frontier:
            return filled
        for x, y, r, g, b in frontier:
            # Alpha kept, which is the whole reason this is safe.
            pixels[x, y] = (r, g, b, pixels[x, y][3])
            known[x][y] = True
            filled += 1


def main():
    dry = '--dry-run' in sys.argv
    total = 0
    for name in sorted(os.listdir(TEXTURES)):
        if not name.endswith('.png') or 'disk' not in name:
            continue
        path = os.path.join(TEXTURES, name)
        image = Image.open(path).convert('RGBA')
        pixels = image.load()
        before = sum(1 for y in range(image.height) for x in range(image.width)
            if pixels[x, y][3] == 0 and pixels[x, y][:3] == (255, 255, 255))
        filled = dilate(image)
        if filled and not dry:
            image.save(path)
        total += filled
        note = f' ({before} of them white)' if before else ''
        print(f'{name:28} {filled} transparent pixel(s) recoloured{note}')
    print(f'\n{total} pixels given a colour under their transparency')


if __name__ == '__main__':
    main()
