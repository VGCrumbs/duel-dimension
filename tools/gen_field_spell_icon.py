"""The field spell zone's emblem: a four-point compass rose.

Redrawn rather than traced -- long faceted points north, east, south and west,
each split down its axis so one half catches the light, with a crescent set
between every pair. Sits on the same dark rounded square the other zone
squares use, so the field spell slot reads as part of the same set.
"""
import math
from PIL import Image, ImageDraw

S = 256
OUT = 'src/main/resources/assets/dueldimension/textures/duel/field_spell.png'

BRIGHT = (248, 250, 253, 255)
SHADE = (188, 196, 208, 255)
ROSE_R = S * 0.40
INNER_R = S * 0.085
RING_OUTER = S * 0.345
RING_INNER = S * 0.255

im = Image.new('RGBA', (S, S), (0, 0, 0, 0))
d = ImageDraw.Draw(im)
c = S / 2

# No frame: the zone's own themed square is drawn underneath, and this rides
# on top at its true square aspect. Baking a frame in meant the emblem had to
# fill the zone rect, and a 0.8 x 1.2 zone stretched the rose out of round.


def at(angle_deg, radius):
    a = math.radians(angle_deg)
    return (c + math.cos(a) * radius, c + math.sin(a) * radius)


# Crescents between the points: an arc with tapered ends and an inward spike,
# drawn on a mask so the inner curve can be cut cleanly.
ring = Image.new('L', (S, S), 0)
rd = ImageDraw.Draw(ring)
for diag in (45, 135, 225, 315):
    span = 34
    rd.pieslice([c - RING_OUTER, c - RING_OUTER, c + RING_OUTER, c + RING_OUTER],
                diag - span, diag + span, fill=255)
rd.ellipse([c - RING_INNER, c - RING_INNER, c + RING_INNER, c + RING_INNER], fill=0)
for diag in (45, 135, 225, 315):
    # The little spike that points back towards the middle.
    tip = at(diag, RING_INNER * 0.55)
    left = at(diag - 11, RING_INNER + 1)
    right = at(diag + 11, RING_INNER + 1)
    rd.polygon([tip, left, right], fill=255)
im.paste(Image.new('RGBA', (S, S), BRIGHT), (0, 0), ring)

# The four long points, each split along its own axis.
d = ImageDraw.Draw(im)
for axis in (270, 0, 90, 180):
    tip = at(axis, ROSE_R)
    left = at(axis - 90, INNER_R)
    right = at(axis + 90, INNER_R)
    # Facet away from the light, then the lit one, so the seam runs to the tip.
    d.polygon([tip, left, (c, c)], fill=SHADE)
    d.polygon([tip, right, (c, c)], fill=BRIGHT)

# A small hub, so the eight facets meet on something rather than a point.
d.ellipse([c - S * 0.022, c - S * 0.022, c + S * 0.022, c + S * 0.022], fill=BRIGHT)

im.save(OUT)
print('field_spell.png', im.size)
