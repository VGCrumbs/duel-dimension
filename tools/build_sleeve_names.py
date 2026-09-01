"""Reads Master Duel's PROTECTOR item table into `sleeve_names.json`.

`ItemID.json` in the MasterDuelDecomp research tree is the game's own item id
list with the English name of each id in a trailing `//` comment -- the same
table `deck_box_names.json` came from, and the same reason to use it: it is
Konami's naming, not a guess or a wiki scrape.

A PROTECTOR id is 107NNNN, and the art on disk is `ProtectorIcon107NNNN`, so the
id joins the two directly with no mapping table in between.

Run this before `import_sleeves.py`. It is separate because the name table is a
FACT about Master Duel that wants checking in and reviewing, while the import is
a mechanical consequence of it.
"""
import io
import json
import os
import re
import sys

try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    pass

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = (r"C:\Users\Admin\Desktop\YGO\MasterDuelDecomp\research\item-names\ItemID.json")
OUT = os.path.join(HERE, "sleeve_names.json")


def slug(name: str) -> str:
    return re.sub(r"[^a-z0-9]+", "_", name.lower()).strip("_")


def main() -> None:
    text = io.open(SRC, encoding="utf-8").read()
    block = re.search(r'"PROTECTOR":\s*\[(.*?)\n\s*\]', text, re.S)
    if not block:
        raise SystemExit("no PROTECTOR section in " + SRC)

    names = {}
    for ident, name in re.findall(r"(\d+),\s*//(.*)", block.group(1)):
        names[ident] = name.strip()

    # Slug collisions are real: Konami ships "...The Virtuous" and
    # "...The Virtuous2", and a slug that drops the digit would have the second
    # overwrite the first's art and enum constant silently. Report them rather
    # than dedupe behind the caller's back -- which of two near-identical
    # sleeves keeps the plain name is an editorial call.
    seen = {}
    clashes = []
    for ident, name in sorted(names.items()):
        s = slug(name)
        if s in seen:
            clashes.append((seen[s], ident, s))
        seen[s] = ident

    io.open(OUT, "w", encoding="utf-8", newline="\n").write(
        json.dumps({"source": "MasterDuelDecomp/research/item-names/ItemID.json",
                    "section": "PROTECTOR",
                    "names": names}, indent=2, ensure_ascii=False) + "\n")
    print("%d protector names -> %s" % (len(names), os.path.basename(OUT)))
    if clashes:
        print("%d slug collisions:" % len(clashes))
        for first, second, s in clashes:
            print("   %s and %s both slug to %s" % (first, second, s))
            print("      %-40s %s" % (names[first], names[second]))
    else:
        print("no slug collisions")


if __name__ == "__main__":
    main()
