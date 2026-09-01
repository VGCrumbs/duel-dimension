"""Folds the billboards you tuned in game back into the ones the mod ships.

## The problem this exists for

The billboard editor saves the moment a slider moves -- but it saves to the
PLAYER'S CONFIG, at `<config>/dueldimension/monster_sprites.json`, and it saves
only what DIFFERS from the list inside the jar. That is the right design for a
player: their tuning survives a mod update, and a definition they never touched
keeps following the mod.

It is the wrong default for the person BUILDING the mod, because their config is
not in the repository and does not go into the jar. Work done in the dev client
is invisible to everyone who installs the result.

## What this does

Starts from the shipped list, lays the config's entries over it by card code,
drops anything the config marks `removed`, and writes the result back to the
shipped asset. After it runs the two agree, so the config's diff is empty and
the next save writes an empty list -- which is what "these are shipped now"
looks like from the game's side.

The shipped file is NOT in git, so it is copied to `.backup` first rather than
trusted to be recoverable.

Run from the repo root:  python tools/promote_monster_sprites.py [--dry-run]
"""
import io
import json
import os
import shutil
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
SHIPPED = os.path.join(REPO, "shared", "resources", "assets", "dueldimension",
                       "monster_sprites.json")
# The dev client's own config, which is where runClient puts it.
LIVE = os.path.join(REPO, "mc262", "run", "config", "dueldimension",
                    "monster_sprites.json")


def read(path):
    if not os.path.isfile(path):
        raise SystemExit("no file at %s" % path)
    with io.open(path, encoding="utf-8") as handle:
        entries = json.load(handle)
    if not isinstance(entries, list):
        raise SystemExit("%s is not a list of sprites" % path)
    return entries


def main():
    dry = "--dry-run" in sys.argv
    shipped = read(SHIPPED)
    live = read(LIVE)

    # Keyed by card, in shipped order first so the file stays readable rather
    # than being reshuffled every time this runs.
    merged = {}
    for entry in shipped:
        merged[entry.get("card")] = entry

    added = 0
    changed = 0
    removed = 0
    held = 0
    for entry in live:
        code = entry.get("card")
        if entry.get("removed"):
            if merged.pop(code, None) is not None:
                removed += 1
            continue
        # HELD BACK: an entry that is only a 3D model assignment.
        #
        # Those name a .glb under the player's own config, not an asset the mod
        # carries -- there are 683 of them and 550 MB of them, against an 88 MB
        # jar. Promoting one ships a card pointing at a model nobody else has.
        # ShippedSpritesTest says the same thing from the other direction: a
        # shipped entry must carry a sprite body, and a model-only one has none.
        #
        # Adding the models to the mod is a distribution decision and not this
        # script's to make, so these are counted and left alone.
        if "body" not in entry and "body" not in merged.get(code, {}):
            held += 1
            continue
        if code in merged:
            if merged[code] != entry:
                changed += 1
        else:
            added += 1
        merged[code] = entry

    print("  shipped   %4d" % len(shipped))
    print("  config    %4d  (differences only)" % len(live))
    print("  ---")
    print("  added     %4d" % added)
    print("  changed   %4d" % changed)
    print("  removed   %4d" % removed)
    print("  held back %4d  (model-only; the .glb does not ship)" % held)
    print("  result    %4d" % len(merged))

    if dry:
        print("\n  --dry-run, nothing written")
        return
    if not (added or changed or removed):
        print("\n  already in step; nothing to do")
        return

    backup = SHIPPED + ".backup"
    shutil.copy2(SHIPPED, backup)
    print("\n  backed up to %s" % os.path.relpath(backup, REPO))
    with io.open(SHIPPED, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(list(merged.values()), handle, indent=2, ensure_ascii=False)
        handle.write("\n")
    print("  wrote %s" % os.path.relpath(SHIPPED, REPO))
    print("\n  The config still holds the old diff until the game next saves;"
          "\n  it is harmless either way, because applying it over this list"
          "\n  now produces this list.")


if __name__ == "__main__":
    main()
