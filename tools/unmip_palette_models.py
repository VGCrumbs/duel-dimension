"""Give palette-textured item models room to survive mipmapping.

WHAT IS WRONG. Six of the ten duel disks are Blockbench "palette" models: a
16x16 PNG where every texel is one flat colour, and every face samples a SINGLE
texel of it (`"uv": [0, 0, 1, 1]` and friends -- 276 faces on the stock disk do
exactly that).

That is fine at mip level 0 and falls apart above it. The block atlas is built
with four mipmap levels; at level 1 each texel is the average of a 2x2 block, at
level 2 a 4x4, and so on. A face that wanted one cell of the palette gets a
blend of its neighbours instead -- which on a disk worn on the arm, small on
screen and therefore several mip levels down, reads as wrong-coloured and white
speckles scattered across it.

The other four disks do not have the problem and did not need this: they are
128x128 with sub-texel UVs, so a mip level or two costs them nothing.

WHAT THIS DOES. Upscales each palette PNG by 8, nearest-neighbour, and declares
`texture_size` so the model's UV numbers keep meaning what they meant. One UV
cell is then 8x8 real texels instead of 1x1, so mip levels 0 through 3 stay
inside the cell they belong to.

NEAREST-NEIGHBOUR, so this is not a change to the art. At mip 0 the result is
the same image, texel for texel, magnified. Nothing is resampled, blurred or
recoloured; the only thing added is the margin the mip chain needs.

Run from anywhere:  python tools/unmip_palette_models.py [--dry-run]
"""

import json
import os
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'mc262', 'src', 'main', 'resources', 'assets', 'dueldimension')

#: How much bigger. Eight puts four clean mip levels inside one UV cell, which
#: is what the block atlas builds; sixteen would buy a fifth level for four
#: times the bytes, and there is no fifth level to buy it for.
SCALE = 8

#: Only models whose faces sample single texels. Named rather than detected so
#: that running this twice is not a 64x upscale -- the second run finds
#: texture_size already set and does nothing.
MODELS = [
    'duel_disk',            # the AUTHORED model; gen_disk_cards.py splits it
    'chaos_disk',
    'academia_disk',
    'academia_disk_red',
    'academia_disk_blue',
    'academia_disk_yellow',
]


def main():
    dry = '--dry-run' in sys.argv
    for name in MODELS:
        model_path = os.path.join(ASSETS, 'models', 'item', name + '.json')
        with open(model_path, encoding='utf-8') as f:
            model = json.load(f)

        if 'texture_size' in model:
            print(f'{name}: already sized, skipped')
            continue

        # Every texture the model names, which for these is one plus a particle
        # alias pointing at the same file.
        files = {v.split('/')[-1] for v in model.get('textures', {}).values()}
        for texture in sorted(files):
            png = os.path.join(ASSETS, 'textures', 'item', texture + '.png')
            if not os.path.isfile(png):
                print(f'{name}: no such texture {texture}.png, skipped')
                continue
            image = Image.open(png)
            if image.width > 16 or image.height > 16:
                print(f'{name}: {texture}.png is already {image.width}x{image.height}')
                continue
            bigger = image.resize((image.width * SCALE, image.height * SCALE),
                Image.NEAREST)
            print(f'{name}: {texture}.png {image.width}x{image.height}'
                f' -> {bigger.width}x{bigger.height}')
            if not dry:
                bigger.save(png)

        # UV numbers are read against texture_size, not against the file, so
        # stating the ORIGINAL size keeps every existing uv rect meaning the
        # same region of the same art.
        model['texture_size'] = [16, 16]
        if not dry:
            with open(model_path, 'w', encoding='utf-8', newline='\n') as f:
                json.dump(model, f, indent=2)
                f.write('\n')
        print(f'{name}: texture_size [16, 16]')


if __name__ == '__main__':
    main()
