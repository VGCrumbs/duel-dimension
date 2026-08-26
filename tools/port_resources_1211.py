"""Copy mc262's resources into mc1211, and say what it refused to copy.

The sibling of port_1211.py, for everything that is not Java.

WHY MOST OF THIS IS A COPY. A resource pack is version-agnostic far more often
than source is. A PNG is a PNG; a block model's `elements` and `display` blocks
have not changed shape since well before 1.19; the mod's own formats -- the card
database under ydm_extras, the screen layouts, monster_sprites.json -- are read
by the mod's own code, which is now the same code on both sides. So the default
here is "copy it, byte for byte", and the interesting output is the SKIP list.

WHAT IT REFUSES, and why each one is a decision rather than a rename:

  assets/dueldimension/items/**
      1.21.4 introduced a second layer of item model definition: `items/foo.json`
      picks a model TYPE, and `models/item/foo.json` is only one kind of thing it
      can pick. 1.21.1 has no such layer at all -- an item is bound to
      `models/item/<id>.json` by name and that is the whole mechanism. Sixty of
      the seventy-three files here say nothing more than "use the model with my
      own name", which is 1.21.1's default and so becomes no file. The other
      thirteen -- the card, the two sets, and ten duel disks -- name a mod
      renderer, and are hand-written for 1.21.1 as builtin/entity models. See
      mc1211/README.md.

  assets/dueldimension/shaders/**
      Two fragment shaders that grey an unowned card. They are written against
      26.2's uniform blocks (`layout(std140) uniform DynamicTransforms`), which
      1.21.1's core shaders do not have, and 1.21.1 also needs a .json shader
      definition beside each one that 26.2 does not use. Copying them would
      produce a shader that fails to link at load.

      Not ported because there is nothing to port them INTO: mc1211's
      UnownedPipelines answers `available() == false` permanently, so every
      caller takes the `dimmed(tint)` fallback the mod already had. Greying an
      unowned card is a tint there rather than a shader.

  fabric.mod.json, dueldimension.mixins.json, dueldimension.accesswidener
      Per-platform by definition -- different entrypoints, a different mixin
      list, and a widener whose whole content is 26.2 problems. Hand-written.

Run from anywhere:  python tools/port_resources_1211.py [--dry-run]
"""

import filecmp
import json
import os
import shutil
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, 'mc262', 'src', 'main', 'resources')
DST = os.path.join(ROOT, 'mc1211', 'src', 'main', 'resources')

# Path prefixes, relative to the resources root, that are never copied. Each one
# is explained at the top of this file; the reason is repeated here in one line
# so that a person reading the skip list in the output knows why without going
# looking.
SKIP = [
    ('assets/dueldimension/items', 'no item-model layer on 1.21.1; 13 hand-written, 60 are the default'),
    ('assets/dueldimension/shaders', 'written against 26.2 uniform blocks; UnownedPipelines has no shader path here'),
    ('fabric.mod.json', 'per-platform'),
    ('dueldimension.mixins.json', 'per-platform: a different mixin list'),
    ('dueldimension.accesswidener', 'per-platform: 26.2 problems only'),
]


# The items whose model is drawn by mod code rather than baked from JSON. Each
# needs `"parent": "minecraft:builtin/entity"` on 1.21.1 -- that is the whole of
# what makes BakedModel.isCustomRenderer() true and sends vanilla to
# BuiltinItemRendererRegistry instead of drawing quads.
SELF_DRAWN = ['card', 'set', 'opened_set']

# The duel disks, in DuelDisks.ALL's order. See disk_models() for what happens
# to each; the list is duplicated from Java deliberately, because a disk added
# there and not here draws with no cards and says nothing, and a name that only
# exists here fails loudly on the next run.
DISKS = [
    'duel_disk', 'chaos_disk', 'academia_disk', 'academia_disk_red',
    'academia_disk_blue', 'academia_disk_yellow', 'rock_spirit_disk',
    'trueman_disk', 'jewel_disk', 'kaibaman_disk',
]


def read(rel):
    with open(os.path.join(SRC, rel.replace('/', os.sep)), encoding='utf-8') as f:
        return json.load(f)


def write(rel, obj, dry):
    if dry:
        return
    target = os.path.join(DST, rel.replace('/', os.sep))
    os.makedirs(os.path.dirname(target), exist_ok=True)
    with open(target, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')


def marker(source, note):
    """A builtin/entity model carrying `source`'s transforms and nothing else.

    The display block is copied rather than dropped because it is the only thing
    a builtin/entity model still decides: 1.21.1 applies the transform for the
    display context BEFORE handing the pose to a custom renderer, so a disk with
    no display block is drawn at block scale, in the wrong place, in every hand
    and every inventory slot. The particle texture goes with it so the bakery
    does not log a missing sprite for a model that never shows one.
    """
    out = {'parent': 'minecraft:builtin/entity', '__ported': note}
    textures = source.get('textures', {})
    # 'layer0' is in the list because the card and the two sets are flat
    # item/generated models on 26.2, where the disks are Blockbench boxes.
    particle = (textures.get('particle') or textures.get('layer0')
        or textures.get('1') or textures.get('0'))
    if particle:
        out['textures'] = {'particle': particle}
    if 'display' in source:
        out['display'] = source['display']
    return out


# The element rotations a 1.21.1 model loader will accept. 26.2 dropped the
# restriction; 1.21.1 still throws "Invalid rotation N found, only
# -45/-22.5/0/22.5/45 allowed" and the whole model fails to bake -- which is how
# every duel disk in the game came out invisible on the first run, from one
# element in one file.
LEGAL_ANGLES = [-45.0, -22.5, 0.0, 22.5, 45.0]


def snap_rotations(dry, log):
    """Snap out-of-range element rotations, and say which.

    Walks the DESTINATION, not the source, and that is not a detail: by the time
    this runs, thirteen models have been replaced by builtin/entity markers and
    nine more have been written under new names. Reading the source again would
    snap a file nobody ships and write the pre-marker model back over the marker
    -- which it did, once, and every duel disk went back to being a static plate.
    """
    root = 'assets/dueldimension/models'
    for dirpath, _, filenames in os.walk(os.path.join(DST, root.replace('/', os.sep))):
        for name in filenames:
            if not name.endswith('.json'):
                continue
            rel = os.path.relpath(os.path.join(dirpath, name), DST).replace(os.sep, '/')
            try:
                with open(os.path.join(DST, rel.replace('/', os.sep)), encoding='utf-8') as f:
                    model = json.load(f)
            except (ValueError, UnicodeDecodeError):
                continue
            touched = []
            for element in model.get('elements', []):
                rotation = element.get('rotation')
                if not isinstance(rotation, dict):
                    continue
                angle = rotation.get('angle')
                if not isinstance(angle, (int, float)) or angle in LEGAL_ANGLES:
                    continue
                nearest = min(LEGAL_ANGLES, key=lambda legal: abs(legal - angle))
                rotation['angle'] = nearest
                touched.append(f'{angle} -> {nearest}')
            if touched:
                write(rel, model, dry)
                log.append(f'{name}: rotation ' + ', '.join(touched))


def recipes(dry, log):
    """Rewrite shaped-recipe keys into 1.21.1's ingredient form.

    26.2 lets a pattern key be a bare item id. 1.21.1 wants an ingredient
    object, and a bare string fails with "Not a JSON object" -- the recipe is
    dropped, with the item quietly uncraftable and one line in the log.
    """
    root = 'data/dueldimension/recipe'
    directory = os.path.join(SRC, root.replace('/', os.sep))
    if not os.path.isdir(directory):
        return
    for name in sorted(os.listdir(directory)):
        rel = f'{root}/{name}'
        recipe = read(rel)
        key = recipe.get('key')
        if not isinstance(key, dict):
            continue
        rewritten = {k: ({'item': v} if isinstance(v, str) else v) for k, v in key.items()}
        if rewritten != key:
            recipe['key'] = rewritten
            write(rel, recipe, dry)
            log.append(f'{name}: {len(key)} pattern key(s) -> ingredient objects')


def authored_model(dry, log):
    """Put the authored duel disk where mc1211's tests can still read it.

    `DiskRackTest` checks that the generated slot table really is a split of the
    Blockbench model it claims to come from -- which needs that model. mc262 has
    it at `models/item/duel_disk.json`; here that path is a builtin/entity
    marker, so the authored file has nowhere to live in the asset tree.

    It goes into TEST resources instead, and under a different name, for two
    reasons that are both about not being loaded. 1.21.1's bakery reads every
    file under `models/` whether anything references it or not, so an unused
    copy in the asset tree would be parsed -- and it is the file with the 42.5
    degree rotation, so it would log the same failure the snap exists to
    prevent. And a second file at the SAME classpath path as the marker would be
    a coin toss over which one a test opened.
    """
    source = 'assets/dueldimension/models/item/duel_disk.json'
    target = os.path.join(ROOT, 'mc1211', 'src', 'test', 'resources', 'authored',
        'duel_disk.json')
    if dry:
        return
    os.makedirs(os.path.dirname(target), exist_ok=True)
    shutil.copy2(os.path.join(SRC, source.replace('/', os.sep)), target)
    log.append('duel_disk.json -> src/test/resources/authored/ (for DiskRackTest)')


def specials(dry, log):
    """The thirteen models the bulk copy is not allowed to answer for."""
    for name in SELF_DRAWN:
        rel = f'assets/dueldimension/models/item/{name}.json'
        write(rel, marker(read(rel),
            'drawn by DdCardModels; 26.2 says this in assets/dueldimension/items/'
            + name + '.json'), dry)
        log.append(f'{name}.json -> builtin/entity')

    for disk in DISKS:
        # 26.2's composite names the model to draw the FRAME with. For the stock
        # disk that is a separate file already -- gen_disk_cards.py split the
        # card slots out of duel_disk.json and left duel_disk_frame.json behind.
        # The other nine never had card geometry in them, so their own model IS
        # the frame; it moves to the same _frame name so that DiskCardsItemModel
        # can find all ten by one rule instead of a special case.
        rel = f'assets/dueldimension/models/item/{disk}.json'
        if disk == 'duel_disk':
            frame_rel = 'assets/dueldimension/models/item/duel_disk_frame.json'
            frame = read(frame_rel)
        else:
            frame = read(rel)
            frame_rel = f'assets/dueldimension/models/item/{disk}_frame.json'
            write(frame_rel, frame, dry)
            log.append(f'{disk}.json -> {disk}_frame.json (geometry moved)')
        write(rel, marker(frame,
            'frame quads + live board, both from DiskCardsItemModel; 26.2 does '
            'the same two things with a minecraft:composite'), dry)
        log.append(f'{disk}.json -> builtin/entity')


def skipped(rel):
    for prefix, why in SKIP:
        if rel == prefix or rel.startswith(prefix + '/'):
            return why
    return None


def main():
    dry = '--dry-run' in sys.argv
    copied = same = 0
    skips = {}

    for dirpath, _, filenames in os.walk(SRC):
        for name in filenames:
            full = os.path.join(dirpath, name)
            rel = os.path.relpath(full, SRC).replace(os.sep, '/')
            why = skipped(rel)
            if why is not None:
                skips.setdefault(why, []).append(rel)
                continue
            target = os.path.join(DST, rel.replace('/', os.sep))
            if os.path.exists(target) and filecmp.cmp(full, target, shallow=False):
                same += 1
                continue
            copied += 1
            if not dry:
                os.makedirs(os.path.dirname(target), exist_ok=True)
                shutil.copy2(full, target)

    # After the bulk copy, not before: these overwrite files it just wrote.
    log = []
    specials(dry, log)
    authored_model(dry, log)
    snap_rotations(dry, log)
    recipes(dry, log)

    print(f'{copied} copied, {same} already identical')
    print(f'\n  REWRITTEN ({len(log)}) -- what 1.21.1 will not read as written')
    for line in log:
        print(f'      {line}')
    for why, files in sorted(skips.items()):
        print(f'\n  SKIP ({len(files)}) -- {why}')
        for f in sorted(files)[:6]:
            print(f'      {f}')
        if len(files) > 6:
            print(f'      ... and {len(files) - 6} more')


if __name__ == '__main__':
    main()
