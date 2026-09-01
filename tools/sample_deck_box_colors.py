"""Assigns each deck box a representative colour, so the shop can sort by it.

The same method the sleeves use, and deliberately the same code: `dominant`
from `sample_sleeve_colors` is imported rather than copied, so the two
catalogues cannot drift into sorting by subtly different definitions of "the
colour of this".

Writes `deck_box_colors.json`, keyed by the Master Duel DeckCase id, which is
what `DeckBoxStyle` carries per constant.
"""
import io
import json
import os
import re
import sys

from sample_sleeve_colors import dominant, sort_key   # noqa: F401

try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    pass

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
NAMES = os.path.join(HERE, "deck_box_names.json")
OUT = os.path.join(HERE, "deck_box_colors.json")
ART = os.path.join(REPO, "shared", "resources", "assets", "dueldimension",
                   "textures", "gui", "hub")


def slug(name):
    return re.sub(r"[^a-z0-9]+", "_", name.lower()).strip("_")


def main():
    names = json.load(io.open(NAMES, encoding="utf-8"))["names"]
    colours = {}
    missing = []
    for deck_case_id in sorted(names):
        path = os.path.join(ART, "deck_box_%s.png" % slug(names[deck_case_id]))
        if not os.path.exists(path):
            missing.append(deck_case_id)
            continue
        colours[deck_case_id] = dominant(path)

    # The two that predate the imported art and have no DeckCase id of their
    # own. They are keyed by constant name instead, which is how the importer
    # will have to look them up.
    for label, filename in (("RED", "deck_box_red.png"),
                            ("PURPLE", "deck_box_purple.png"),
                            ("BLUE", "deck_placeholder.png")):
        path = os.path.join(ART, filename)
        if os.path.exists(path):
            colours[label] = dominant(path)

    io.open(OUT, "w", encoding="utf-8", newline="\n").write(json.dumps(
        {"note": "dominant colour per DeckCase id; see sample_sleeve_colors.py",
         "colors": {k: "%06X" % v for k, v in colours.items()}},
        indent=2) + "\n")
    print("%d colours -> %s" % (len(colours), os.path.basename(OUT)))
    if missing:
        print("%d ids had no art: %s" % (len(missing), ", ".join(missing[:8])))


if __name__ == "__main__":
    main()
