"""Takes the gold line off the hovered button.

It is baked into the texture, not drawn in code: common/button.png is a
three-row nine-slice (idle, hover, disabled) and the hover row carries a gold
run across its top edge. Nine pixels, all on one row.

They are replaced with the hover row's own body colour, which leaves that row
uniform exactly as the idle row already is -- the two states then differ only
in brightness, and the label still goes gold on hover, so a hovered button is
still obviously hovered.

tab.png is deliberately untouched. It has a gold run too, but on its SELECTED
row: that is the marker for which tab you are on, not a hover effect.
"""
from PIL import Image

PATH = "src/main/resources/assets/dueldimension/textures/gui/common/button.png"
BODY = (86, 92, 108, 255)      # the hover row's fill, read off the row itself


def goldish(p):
    r, g, b, a = p
    return a > 0 and r > 150 and 120 < g < 210 and b < 140 and r > b + 60


im = Image.open(PATH).convert("RGBA")
w, h = im.size
row = h // 3                    # idle / hover / disabled
px = im.load()

changed = 0
for y in range(row, row * 2):   # the hover row only
    for x in range(w):
        if goldish(px[x, y]):
            px[x, y] = BODY
            changed += 1

im.save(PATH)
print("cleared %d gold pixels from the hover row of %s" % (changed, PATH.split('/')[-1]))
