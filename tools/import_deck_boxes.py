"""Import Master Duel deck cases into the mod, art and code together.

    python tools/import_deck_boxes.py 0009 0012 0014 0016 2001 2009

For each DeckCase id this copies the 422x512 `_L` rendition out of the
MasterDuelDecomp extraction, drops it in the shared asset tree, and makes the
three code edits that a new case needs: the enum constant, the texture field,
and the switch arm that maps one to the other. It refuses to invent a name --
see below.

WHY 422x512 AND WHY `_L`
    The dump carries several renditions per case: a small plain one at 148x180,
    an open-lid view, a mirrored `_reverse`, and `_L` at both 214x256 and
    422x512. The eight cases already in the mod are all 422x512 `_L`, confirmed
    by matching them pixel-wise against the dump, so that is what this takes.
    Anything else would arrive at a different size from its neighbours.

NAMES ARE NOT GUESSED
    A deck case asset carries an id and nothing readable; the display names live
    in Master Duel's own item table. `deck_box_names.json` is where the mapping
    from id to name is recorded, and an id missing from it is REFUSED rather
    than named after its number or its colour. The eight already imported are
    listed there so the file doubles as the record of where each one came from.

WHAT IT DOES NOT DO
    Pricing is uniform (`ShopStock.DECK_BOX_PRICE`) and needs no edit. The
    shop's catalogue is built from the enum, so a new constant appears for sale
    automatically. Nothing here writes to the game install.
"""

from __future__ import annotations

import argparse
import io
import json
import os
import re
import shutil
import sys

# A deck case name may hold characters a Windows console cannot encode --
# "Live☆Twin" and "Yum☆Yum☆Yummys" both do. print() then raises
# UnicodeEncodeError, and because that happens INSIDE the import loop it aborts
# the run partway: the first attempt at the full catalogue stopped dead at 2016
# and silently imported 35 of 51. Reconfiguring the stream is what makes a
# reporting problem stop being an import problem.
try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    pass

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
NAMES = os.path.join(HERE, "deck_box_names.json")

DUMP = (r"C:\Users\Admin\Desktop\YGO\MasterDuelDecomp"
        r"\extracted\deck-boxes\wiki-ready")
ART = os.path.join(REPO, "shared", "resources", "assets", "dueldimension",
                   "textures", "gui", "hub")
MODULES = ("mc262", "mc1211")
SIZE = (422, 512)


def slug(name: str) -> str:
    """A file-safe stem: "Rage of Deep Blue" -> rage_of_deep_blue."""
    return re.sub(r"[^a-z0-9]+", "_", name.lower()).strip("_")


def literal(name: str) -> str:
    """A name as a Java string literal body.

    `"A Case for K9"` is a real deck case name, quotes included, and pasting it
    raw would close the literal three words early and fail the build.
    """
    return name.replace("\\", "\\\\").replace('"', '\\"')


def constant(name: str) -> str:
    """The enum constant: "Rage of Deep Blue" -> RAGE_OF_DEEP_BLUE.

    Prefixed when the name starts with a digit -- "2024 Card Case" would
    otherwise produce `2024_CARD_CASE`, which is not a Java identifier and fails
    the build rather than the import.
    """
    value = slug(name).upper()
    return "CASE_" + value if value[:1].isdigit() else value


def find_art(deck_case_id: str) -> str | None:
    """The 422x512 `_L` rendition of one case, wherever it sits in the dump."""
    wanted = "DeckCase%s_L.png" % deck_case_id
    for dirpath, _, files in os.walk(DUMP):
        if wanted in files:
            path = os.path.join(dirpath, wanted)
            try:
                from PIL import Image
                if Image.open(path).size == SIZE:
                    return path
            except Exception:
                continue
    return None


def patch(path: str, anchor: str, addition: str, label: str) -> bool:
    s = io.open(path, encoding="utf-8").read()
    if addition.strip() in s:
        print("      %s: already present" % label)
        return True
    if anchor not in s:
        print("      %s: ANCHOR MISSING" % label)
        return False
    io.open(path, "w", encoding="utf-8", newline="\n").write(
        s.replace(anchor, addition + anchor, 1))
    return True


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("ids", nargs="+", help="DeckCase ids, e.g. 0009")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    names = json.load(io.open(NAMES, encoding="utf-8"))["names"]

    missing = [i for i in args.ids if i not in names]
    if missing:
        print("No name recorded for: %s" % ", ".join(missing))
        print("Add them to tools/deck_box_names.json first -- this will not")
        print("invent a name for a Konami design.")
        return 1

    for deck_case_id in args.ids:
        name = names[deck_case_id]
        stem = "deck_box_" + slug(name)
        const = constant(name)
        print("DeckCase%s -> %s (%s)" % (deck_case_id, name, const))

        src = find_art(deck_case_id)
        if src is None:
            print("      no %dx%d _L rendition found; skipped" % SIZE)
            continue
        dst = os.path.join(ART, stem + ".png")
        if not args.dry_run:
            # Palettized on the way in, not as a later pass.
            #
            # The dump's renditions are RGBA and run 250-430 KB each; the tree
            # they join is palettized and runs 25-90 KB. Doing it here rather
            # than afterwards is the difference between a jar that grows by
            # 0.7 MB for forty cases and one that quietly grew by 5 MB for
            # sixteen, which is exactly what happened when the compression was
            # a separate step somebody had to remember.
            #
            # FASTOCTREE is the only quantiser that keeps an alpha channel;
            # ADAPTIVE would flatten the transparent surround the tight crop
            # depends on.
            from PIL import Image
            source = Image.open(src).convert("RGBA")
            source.quantize(colors=255, method=Image.FASTOCTREE).save(dst, optimize=True)
            print("      art: %s (%d KB)"
                  % (os.path.basename(dst), os.path.getsize(dst) // 1024))
        else:
            print("      art: %s" % os.path.basename(dst))

        for mod in MODULES:
            style = os.path.join(REPO, mod, "src/main/java/de/cas_ual_ty/"
                                 "dueldimension/duel/profile/DeckBoxStyle.java")
            hub = os.path.join(REPO, mod, "src/main/java/de/cas_ual_ty/"
                               "dueldimension/clientutil/hub/HubTextures.java")
            if args.dry_run:
                continue
            # The enum: appended before the last constant's terminator.
            patch(style,
                  "    THE_MILLENNIUM_PUZZLE(\"The Millennium Puzzle\", false);",
                  "    %s(\"%s\", false),\n" % (const, name),
                  "%s enum" % mod)
            # The texture handle.
            patch(hub,
                  "    public static final Identifier DECK_BOX_MILLENNIUM_PUZZLE ="
                  if mod == "mc262" else
                  "    public static final ResourceLocation DECK_BOX_MILLENNIUM_PUZZLE =",
                  "    public static final %s DECK_BOX_%s =\n        gui(\"hub/%s.png\");\n"
                  % ("Identifier" if mod == "mc262" else "ResourceLocation",
                     const, stem),
                  "%s texture" % mod)
            # The switch arm, so the handle is reachable from the style.
            patch(hub,
                  "            case THE_MILLENNIUM_PUZZLE -> DECK_BOX_MILLENNIUM_PUZZLE;",
                  "            case %s -> DECK_BOX_%s;\n" % (const, const),
                  "%s switch" % mod)

    print("\nNow run:  ./gradlew25.cmd buildAll")
    return 0


if __name__ == "__main__":
    sys.exit(main())
