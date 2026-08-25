"""Splits the duel disk model into the frame and its card slots.

The disk is authored as ONE file in Blockbench -- that is the whole point, so
the slots can be nudged against the plate they sit on and seen together. But the
game cannot draw it as one file: the ten card slots have to appear and disappear
with the board, and a baked item model draws every element it has, always.

So this reads the authored model and writes two derived files:

  * `duel_disk_frame.json` -- everything that is not a card slot. This is what
    the item's baked model points at.
  * `duel_disk_slots.json` -- the ten slots as exact authored vertices, which
    `DiskCardsRenderer` reads at load and draws itself.

Neither is edited by hand. Edit `duel_disk.json` in Blockbench and run:

    python tools/gen_disk_cards.py

The two card groups identify which elements belong to the dynamic layer. The
row comes from each element's `card_topN` / `card_bottomN` name because the
current Blockbench group labels are inverted relative to their children.
"""
import json
import math
import os
import re

ROOT = 'src/main/resources/assets/dueldimension'
SOURCE = os.path.join(ROOT, 'models/item/duel_disk.json')
FRAME = os.path.join(ROOT, 'models/item/duel_disk_frame.json')
SLOTS = os.path.join(ROOT, 'models/item/duel_disk_slots.json')

# The groups collectively contain all dynamic card placeholders.
CARD_GROUPS = {'cards_top', 'cards_bottom'}


def row_of(element):
    """The board row named by the placeholder element itself."""
    name = element.get('name', '')
    if re.fullmatch(r'card_top\d+', name):
        return 'monsters'
    if re.fullmatch(r'card_bottom\d+', name):
        return 'spells'
    raise SystemExit('card group contains unrecognised element %r' % name)


def order_of(element, fallback):
    """The number the slot was given in Blockbench.

    The slots are named `card_bottom1`..`card_bottom5` and `card_top2`..
    `card_top6` -- somebody numbered them, and that numbering is the one thing
    here that states intent rather than being an accident of how the boxes were
    dragged. Zone 0 is number 1 (or 2, in the row that starts there), so this
    sorts by it and does not try to infer an order from geometry.
    """
    match = re.search(r'(\d+)\s*$', element.get('name', ''))
    return int(match.group(1)) if match else fallback


def slot_of(element):
    """One card slot as the renderer needs it: its exact authored top face.

    Blockbench gives a box; the renderer draws a quad. These boxes are 0.05
    thick, so the card is really the box's TOP face -- which is also the face
    the model textures with `card_front`. The thickness axis is dropped and the
    quad is placed at that top face rather than at the box's middle, so it lands
    exactly where the placeholder's visible surface was.

    The box corners in the file are written BEFORE the element's own rotation.
    Every top-face corner is therefore spun about that rotation origin here and
    stored verbatim.  Runtime does not reconstruct a rectangle from a centre,
    size and angle; it draws these four points, which makes the JSON the single
    source of truth for the card's position.
    """
    x0, y0, z0 = element['from']
    x1, y1, z1 = element['to']
    rotation = element.get('rotation') or {}
    angle = rotation.get('angle', 0)
    axis = rotation.get('axis', 'y')
    origin = rotation.get('origin', [8, 8, 8])
    if axis != 'y':
        # A slot tilted about x or z would need the renderer to carry a second
        # rotation. None of them are, and silently ignoring it would put the
        # card somewhere the placeholder was not.
        raise SystemExit('%s rotates about %s; only y is handled'
                         % (element.get('name'), axis))

    corners = [
        point(x0, y1, z0, origin, angle),
        point(x1, y1, z0, origin, angle),
        point(x1, y1, z1, origin, angle),
        point(x0, y1, z1, origin, angle),
    ]
    cx = sum(corner[0] for corner in corners) / 4.0
    cy = sum(corner[1] for corner in corners) / 4.0
    cz = sum(corner[2] for corner in corners) / 4.0
    return {
        'source': element.get('name', ''),
        'plane': [round(cx, 5), round(cy, 5), round(cz, 5)],
        'width': round(x1 - x0, 5),
        'length': round(z1 - z0, 5),
        'angle': angle,
        'corners': corners,
    }


def point(x, y, z, origin, angle):
    """One model point after the element's authored Y rotation."""
    rx, rz = spin(x, z, origin[0], origin[2], angle)
    return [round(rx, 5), round(y, 5), round(rz, 5)]


def spin(x, z, ox, oz, angle):
    """A point turned about a vertical axis, the way JOML's rotateY turns it.

    Matching the baked model matters because two conventions that differ by a
    sign put the card on the wrong half of the plate, mirrored, which looks like
    a coordinate mistake rather than like a sign one.
    """
    radians = math.radians(angle)
    dx = x - ox
    dz = z - oz
    return (ox + dx * math.cos(radians) + dz * math.sin(radians),
            oz - dx * math.sin(radians) + dz * math.cos(radians))


def main():
    model = json.load(open(SOURCE, encoding='utf-8'))
    elements = model['elements']

    all_groups = list(walk_groups(model.get('groups', [])))
    row_indices = {'monsters': [], 'spells': []}
    carded = set()
    for group in all_groups:
        if group.get('name') not in CARD_GROUPS:
            continue
        indices = [c for c in group.get('children', []) if isinstance(c, int)]
        for index in indices:
            row_indices[row_of(elements[index])].append(index)
        carded.update(indices)
        print('  %-14s %d slots' % (group['name'], len(indices)))

    missing = CARD_GROUPS - {g.get('name') for g in all_groups}
    if missing:
        raise SystemExit('no group named %s in %s -- renaming one silently '
                         'empties a row' % (', '.join(sorted(missing)), SOURCE))

    slots = {}
    for row, indices in row_indices.items():
        # By the number in the slot's own name, so zone 0 is the same authored
        # box every time. Blockbench child order is only creation order.
        indices.sort(key=lambda i: order_of(elements[i], i))
        slots[row] = [slot_of(elements[i]) for i in indices]

    # The frame: every element that is not a slot, with the groups re-indexed
    # because dropping elements shifts everything after them.
    keep = [i for i in range(len(elements)) if i not in carded]
    renumber = {old: new for new, old in enumerate(keep)}
    frame = dict(model)
    frame['elements'] = [dress(elements[i]) for i in keep]
    frame['groups'] = [g for g in regroup(model.get('groups', []), renumber) if g is not None]
    # The card textures go with the slots; leaving them would have the frame
    # asking for two textures nothing on it samples.
    frame['textures'] = {k: v for k, v in model.get('textures', {}).items()
                         if k in ('1',) or not str(v).startswith('card_')}
    frame['textures'].setdefault('particle', model['textures'].get('1'))
    frame['__generated'] = 'tools/gen_disk_cards.py -- edit duel_disk.json instead'

    write(FRAME, frame)
    write(SLOTS, {'__generated': frame['__generated'], **slots})

    if dressed:
        print('\n  %d element(s) had faces with no texture, given %s so the item '
              'can bake:' % (len(dressed), DISK))
        for name, sides in dressed:
            print('    %-16s %s' % (name, ', '.join(sides)))
        print('  Assign them in Blockbench to be rid of this.')


def walk_groups(groups):
    """Every named group in Blockbench's nested group tree.

    Card groups used to be top-level. The authored model now keeps them under
    the deck group, alongside the plate they belong to. Searching only the root
    silently reused the previous generated slot table and ignored every manual
    move made in Blockbench.
    """
    for group in groups:
        if not isinstance(group, dict):
            continue
        yield group
        yield from walk_groups(group.get('children', []))


#: The texture an untextured face is given. Reported, not silent -- see `dress`.
DISK = '#1'

#: Elements whose faces this had to fill in, for the summary at the end.
dressed = []


def dress(element):
    """One frame element, with any untextured face given the disk's texture.

    Blockbench writes `#missing` for a face nobody assigned a texture to. That
    is fatal here rather than cosmetic: `#missing` resolves to a sprite on the
    BLOCK atlas, an item model may only draw from the item atlas, and 26.2
    refuses to bake a model that mixes the two --

        Multiple atlases used in model, expected .../items.png,
        but also got .../blocks.png

    -- which loses the whole disk, not the face. So the face is given the disk's
    own texture, like every other face on the model.

    Filled in rather than deleted deliberately: an unassigned face is usually an
    interior one nobody can see, but "usually" is not "always", and a hole in
    the plate is worse than a face wearing the same texture as its neighbours.
    The right fix is to assign it in Blockbench; this keeps the disk on the arm
    until somebody does, and says which elements need it.
    """
    faces = {}
    filled = []
    for side, face in element.get('faces', {}).items():
        if face.get('texture') == '#missing':
            face = dict(face, texture=DISK)
            filled.append(side)
        faces[side] = face
    if filled:
        dressed.append((element.get('name'), filled))
    return dict(element, faces=faces)


def regroup(groups, renumber):
    """The group tree with dropped elements removed and the rest renumbered."""
    out = []
    for group in groups:
        if isinstance(group, int):
            if group in renumber:
                out.append(renumber[group])
            continue
        children = regroup(group.get('children', []), renumber)
        if not children:
            # A group that held nothing but slots goes with them.
            continue
        copy = dict(group)
        copy['children'] = children
        out.append(copy)
    return out


def write(path, data):
    with open(path, 'w', encoding='utf-8') as handle:
        json.dump(data, handle, indent=2)
        handle.write('\n')
    print('  wrote %s (%d bytes)' % (path, os.path.getsize(path)))


if __name__ == '__main__':
    main()
