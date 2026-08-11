"""Check run/ydm_db the way the mod checks it, before the mod gets a chance to crash.

WHY THIS EXISTS. DdUtil.buildProperties reads every field with j.get(...) and no
has() guard, so a card file missing one key is not a card with a default -- it is
a NullPointerException at database load. The same goes the other way: CardSet
entries name a card id, and an id with no file in cards/ is a dangling reference.
Neither shows up until the game boots, so both are checked here instead.

WHAT IS CHECKED
  1. Card schema, per DdUtil.buildProperties and the Properties subclasses:
     which keys are required depends on the card's own type, monster_type and
     is_pendulum, exactly as MonsterProperties.getHasDef / getHasLevel decide it.
  2. Referential integrity: every id referenced by a set has a file in cards/.
  3. Every rarity named by a set has an overlay file in rarities/ (reported,
     not fatal -- a missing one renders unfoiled, and Common has none by design).
  4. Every distribution named by a set exists, and -- the check
     fix_distributions.py does NOT make -- whether a set's own rarities are all
     actually pullable and whether any pull comes up short of its advertised
     card count.

Exit code is non-zero only for 1, 2 and a missing distribution -- the things that
actually stop the database loading. 3 and 4 are reported but not fatal: both are
long-standing conditions across much of the tree, not new regressions.
"""
import io
import json
import os
import sys

DB = "run/ydm_db"

BASE = ["name", "id", "is_illegal", "is_custom", "text", "type", "images"]
MONSTER = ["attribute", "atk", "species", "monster_type", "is_pendulum",
           "ability", "has_effect"]
PENDULUM = ["pendulum_text", "pendulum_scale_left_blue", "pendulum_scale_right_red"]


def required_keys(c):
    """The exact keys buildProperties will j.get() for this card."""
    need = list(BASE)
    t = c.get("type")

    if t == "Spell":
        return need + ["spell_type"]
    if t == "Trap":
        return need + ["trap_type"]
    if t != "Monster":
        return need                      # Properties only; unknown types fall through

    need += MONSTER
    if c.get("is_pendulum"):
        need += PENDULUM

    mt = c.get("monster_type") or ""     # "" is Java's null MonsterType
    has_def = mt in ("", "Fusion", "Ritual", "Synchro", "Xyz")
    has_level = mt in ("", "Fusion", "Ritual", "Synchro")

    if has_def:
        need.append("def")
        if has_level:
            need += ["level", "is_tuner"]
        elif mt == "Xyz":
            need.append("rank")
    elif mt == "Link":
        need += ["link_rating", "link_arrows"]

    return need


def main():
    bad_schema, unparsable = [], []
    cards = {}

    for p in sorted(os.listdir(f"{DB}/cards")):
        if not p.endswith(".json"):
            continue
        path = f"{DB}/cards/{p}"
        try:
            c = json.load(io.open(path, encoding="utf-8"))
        except Exception as e:
            unparsable.append((p, str(e)))
            continue
        missing = [k for k in required_keys(c) if k not in c]
        if missing:
            bad_schema.append((p, missing))
        if not c.get("images"):
            bad_schema.append((p, ["images (empty)"]))
        cards[c["id"]] = c

    # RARITIES_LIST is keyed by the "rarity" string INSIDE the file, not by the
    # filename, and DdDatabase.getRarity returns null for anything absent. A
    # missing entry therefore means "no foil overlay", not a crash -- Common is
    # the most-used rarity in the tree and deliberately has no file. Reported,
    # never fatal.
    have_rarity = set()
    for f in os.listdir(f"{DB}/rarities"):
        if f.endswith(".json"):
            have_rarity.add(json.load(io.open(f"{DB}/rarities/{f}", encoding="utf-8"))["rarity"])
    dists = {}
    for p in os.listdir(f"{DB}/distributions"):
        d = json.load(io.open(f"{DB}/distributions/{p}", encoding="utf-8"))
        dists[d["name"]] = d

    dangling, missing_rarity, missing_dist = [], set(), []
    unpullable, short_packs = [], []
    n_sets = n_entries = 0

    for p in sorted(os.listdir(f"{DB}/sets")):
        s = json.load(io.open(f"{DB}/sets/{p}", encoding="utf-8"))
        n_sets += 1
        entries = s.get("cards") or []
        n_entries += len(entries)

        for c in entries:
            if c["id"] not in cards:
                dangling.append((s.get("code"), c["id"], c.get("code")))
            if c["rarity"] not in have_rarity:
                missing_rarity.add(c["rarity"])

        if s.get("pull_type") != "distribution" or not entries:
            continue

        d = dists.get(s.get("distribution"))
        if d is None:
            missing_dist.append((s.get("code"), s.get("distribution")))
            continue

        have = set(c["rarity"] for c in entries)
        pullable = set(r for pu in d["pulls"] for e in pu["entries"] for r in e["rarities"])
        never = have - pullable
        if never:
            unpullable.append((s.get("code"), sorted(never)))

        total = sum(pu["weight"] for pu in d["pulls"])
        short_w = sum(pu["weight"] for pu in d["pulls"]
                      if any(not (set(e["rarities"]) & have) for e in pu["entries"]))
        if short_w:
            short_packs.append((short_w / total, s.get("code"), s.get("distribution")))

    print(f"cards/ files            : {len(cards)}")
    print(f"  unparsable            : {len(unparsable)}")
    print(f"  failing the schema    : {len(bad_schema)}")
    for f, m in bad_schema[:20]:
        print(f"      {f}: missing {m}")
    print(f"sets/ files             : {n_sets}   printings: {n_entries}")
    print(f"  ids with no card file : {len(dangling)} {dangling[:10]}")
    print(f"  distributions missing : {len(missing_dist)} {missing_dist[:10]}")
    print()
    print("non-fatal, pre-existing across the tree:")
    print(f"  rarities with no overlay file (render only) : {len(missing_rarity)} {sorted(missing_rarity)}")
    print(f"  sets with a rarity that can NEVER be pulled : {len(unpullable)}")
    print(f"  sets whose packs can come up short          : {len(short_packs)}")
    always = [x for x in short_packs if x[0] == 1.0]
    print(f"    of those, short EVERY time                : {len(always)}")

    for code in ("MAMO",):
        row = [x for x in short_packs if x[1] == code]
        bad = [x for x in unpullable if x[0] == code]
        print(f"  {code}: unpullable={bad or 'none'}  short={row or 'never'}")

    fatal = len(unparsable) + len(bad_schema) + len(dangling) + len(missing_dist)
    print()
    print("FATAL PROBLEMS:", fatal)
    return 1 if fatal else 0


if __name__ == "__main__":
    sys.exit(main())
