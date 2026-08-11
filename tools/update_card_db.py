"""Bring run/ydm_db up to the present day from the source it already came from.

The database stopped at Burst of Destiny, 2021-11-05. Every card file in it
already points at images.ygoprodeck.com and every set at ygoprodeck.com/pics_sets,
so YGOPRODeck is not a substitute source -- it is the original one, and this
regenerates from it rather than approximating it.

WHAT IS DERIVED RATHER THAN ASSUMED

  Grouping. NOT by series number. A first pass tried to continue the DB's own
  Series 9/10 cadence of twelve core sets and it misfired badly -- on card count
  alone it read Legendary Arc-V Decks and Speed Duel GX as core boosters, and it
  ordered Dawn of Majesty after Burst of Destiny. Nothing in the source names a
  series, so any number here would be a claim the data cannot support, and a
  confidently wrong "Series 12" is worse than no series at all. Unclassified
  boosters are grouped by release year, which is in the data and is never wrong.

  Distributions. Picked by looking at the rarities a set actually contains, not
  by its name. A set whose chase is a Quarter Century Secret Rare gets a
  distribution built for that; one with a Starlight gets the Starlight one.

  Card shape. Fields follow DdUtil.buildProperties exactly -- it reads with
  j.get(...) and no has() guard, so a missing field is not a default, it is a
  crash. Monsters need def unless Link; level and is_tuner unless Xyz or Link;
  rank if Xyz; link_rating and link_arrows if Link; the three pendulum fields if
  pendulum.

NOTHING EXISTING IS OVERWRITTEN. Only files that do not yet exist are written, so
a rerun is safe and the curation already in the tree is left alone.
"""
import datetime
import io
import json
import os
import re
import sys
from collections import Counter, defaultdict

DB = "run/ydm_db"
SRC = "build/dbupdate"
# Deliberately ahead of the calendar: announced sets are imported as an early
# taster of the physical TCG, on the condition that the source already carries
# their full card list and artwork. Verified before switching this on -- MAMO
# has 54 printings and MAMS 66, all with images. Pre-release listings can still
# change, so re-running the importer nearer a release date is how they get
# corrected; it only ever writes files that do not already exist.
TODAY = datetime.date(2027, 12, 31)

# DdUtil.toSimpleString, verbatim: s.replaceAll("[^a-zA-Z0-9]", "_").toLowerCase()
def slug(s):
    return re.sub(r"[^a-zA-Z0-9]", "_", s).lower()


# ---------------------------------------------------------------- rarities
# The API's rarity strings are user-maintained and carry typos and junk. Anything
# that is not a real rarity becomes Common, which is what an unlabelled printing
# effectively is -- the alternative is inventing a rarity the card never had.
RARITY_FIX = {
    "platinum secret rare": "Platinum Secret Rare",
    "quarter century secret rare": "Quarter Century Secret Rare",
    "extra secret": "Extra Secret Rare",
    "ultra secret rare": "Ultra Secret Rare",
    "ultra rare (pharaoh's rare)": "Ultra Rare",
    "duel terminal normal rare parallel rare": "Duel Terminal Normal Parallel Rare",
}
RARITY_JUNK = {"", "1", "2", "3", "new", "cr", "force-smw", "new artwork", "n/a", "none"}


def clean_rarity(r):
    r = (r or "").strip()
    low = r.lower()
    if low in RARITY_JUNK:
        return "Common"
    return RARITY_FIX.get(low, r)


# ---------------------------------------------------------------- card shape
FRAME_TO_MONSTER_TYPE = {
    "fusion": "Fusion", "synchro": "Synchro", "xyz": "Xyz", "link": "Link",
    "ritual": "Ritual",
    "fusion_pendulum": "Fusion", "synchro_pendulum": "Synchro",
    "xyz_pendulum": "Xyz", "ritual_pendulum": "Ritual",
}
ABILITIES = ("Flip", "Gemini", "Spirit", "Toon", "Union")
SPELL_TYPES = {"Normal", "Field", "Equip", "Continuous", "Quick-Play", "Ritual"}
TRAP_TYPES = {"Normal", "Continuous", "Counter"}


def build_card(c):
    """One card in the mod's exact schema, or None if it has no place here."""
    frame = (c.get("frameType") or "").lower()
    if frame in ("token", "skill"):
        return None                       # neither is a playable collectable card

    api_type = c.get("type") or ""
    out = {
        "id": c["id"],
        "name": c["name"],
        "is_illegal": False,
        "is_custom": False,
        "text": c.get("desc") or "",
        "images": [i["image_url"] for i in (c.get("card_images") or [])],
    }
    if not out["images"]:
        return None

    if frame == "spell" or "Spell" in api_type:
        out["type"] = "Spell"
        race = c.get("race") or "Normal"
        out["spell_type"] = race if race in SPELL_TYPES else "Normal"
        return out

    if frame == "trap" or "Trap" in api_type:
        out["type"] = "Trap"
        race = c.get("race") or "Normal"
        out["trap_type"] = race if race in TRAP_TYPES else "Normal"
        return out

    out["type"] = "Monster"
    monster_type = FRAME_TO_MONSTER_TYPE.get(frame, "")
    is_pendulum = "pendulum" in frame
    ability = next((a for a in ABILITIES if a in api_type), "")

    out["attribute"] = c.get("attribute") or ""
    out["atk"] = int(c.get("atk") or 0)
    out["species"] = c.get("race") or ""
    out["monster_type"] = monster_type
    out["is_pendulum"] = is_pendulum
    out["ability"] = ability
    # getIsNormal() is monsterType == null && !hasEffect, so this is the only
    # thing separating a Normal monster from an Effect one.
    out["has_effect"] = frame not in ("normal", "normal_pendulum")

    if is_pendulum:
        out["pendulum_text"] = c.get("pend_desc") or ""
        out["pendulum_scale_left_blue"] = int(c.get("scale") or 0)
        out["pendulum_scale_right_red"] = int(c.get("scale") or 0)

    # Mirrors MonsterProperties.getHasDef / getHasLevel / DdUtil.buildProperties.
    has_def = monster_type in ("", "Fusion", "Ritual", "Synchro", "Xyz")
    has_level = monster_type in ("", "Fusion", "Ritual", "Synchro")

    if has_def:
        out["def"] = int(c.get("def") or 0)
        if has_level:
            out["level"] = int(c.get("level") or 0)
            out["is_tuner"] = "Tuner" in api_type
        elif monster_type == "Xyz":
            out["rank"] = int(c.get("level") or 0)
    elif monster_type == "Link":
        out["link_rating"] = int(c.get("linkval") or 0)
        out["link_arrows"] = list(c.get("linkmarkers") or [])

    return out


# ---------------------------------------------------------------- set shape
def classify(name, code, n_cards):
    """(type, pull_type) -- and the type is what the shop tabs read."""
    low = name.lower()
    if n_cards <= 1:
        # A one-card "set" is a promo printing. As a pack it would be a pack of
        # one, which is why these were excluded before; as a Single they are
        # exactly what that tab is for.
        return "Single", "full"
    if "structure deck" in low:
        return "Structure Deck", "full"
    if "starter deck" in low or "starter set" in low:
        return "Starter Deck", "full"
    if "speed duel starter" in low or "battle city box" in low:
        return "Starter Deck", "full"
    if "token" in low:
        return "Single", "full"
    for key, label in (
        ("duelist pack", "Duelist Pack"),
        ("ots tournament pack", "OTS Tournament Pack"),
        ("tournament pack", "Tournament Pack"),
        ("deck build pack", "Deck Build Pack"),
        ("mega pack", "Mega Pack"),
        ("mega-tin", "Mega Pack"),
        ("tin of", "Mega Pack"),
        ("battles of legend", "Battles of Legend"),
        ("battle pack", "Battle Pack"),
        ("hidden arsenal", "Hidden Arsenal"),
        ("legendary duelists", "Legendary Duelists"),
        ("gold series", "Gold Series"),
        ("premium gold", "Gold Series"),
        ("rarity collection", "Rarity Collection"),
        ("star pack", "Star Pack"),
        ("astral pack", "Astral Pack"),
        ("champion pack", "Champion Pack"),
        ("turbo pack", "Turbo Pack"),
        ("collection", "Collector's Set"),
        ("collector", "Collector's Set"),
    ):
        if key in low:
            return label, "distribution"
    return "Booster Pack (Misc.)", "distribution"


def choose_distribution(rarities, available):
    """By what the set actually contains, not by what it is called."""
    have = set(rarities)
    def pick(*names):
        for n in names:
            if n in available:
                return n
        return None

    if "Quarter Century Secret Rare" in have:
        return pick("booster_pack_without_rare_with_quarter_century_secret_rare",
                    "booster_pack_without_rare_with_starlight_rare", "booster_pack")
    if "Starlight Rare" in have:
        return pick("booster_pack_without_rare_with_starlight_rare",
                    "booster_pack_with_starlight_rare", "booster_pack")
    if "Collector's Rare" in have:
        return pick("60_card_set_with_collector_s_rare", "booster_pack")
    if "Rare" in have:
        return pick("booster_pack", "booster_pack_without_rare")
    return pick("booster_pack_without_rare", "booster_pack")


# ---------------------------------------------------------------- main
def main():
    api_cards = json.load(io.open(f"{SRC}/cardinfo.json", encoding="utf-8"))["data"]
    api_sets = json.load(io.open(f"{SRC}/ygo_sets.json", encoding="utf-8"))

    have_sets, have_names = {}, set()
    for p in os.listdir(f"{DB}/sets"):
        d = json.load(io.open(f"{DB}/sets/{p}", encoding="utf-8"))
        if d.get("code"):
            have_sets[d["code"].upper()] = d
        have_names.add(p[:-5])

    have_cards, have_card_files = set(), set()
    for p in os.listdir(f"{DB}/cards"):
        have_cards.add(json.load(io.open(f"{DB}/cards/{p}", encoding="utf-8"))["id"])
        have_card_files.add(p[:-5])

    available_dist = {p[:-5] for p in os.listdir(f"{DB}/distributions")}
    by_id = {c["id"]: c for c in api_cards}

    def pdate(s):
        try:
            return datetime.datetime.strptime(s, "%Y-%m-%d").date()
        except Exception:
            return None

    # Which sets to add: everything released on or before today that we lack.
    # Future-dated announcements are skipped -- a shop cannot sell an unreleased pack.
    wanted, skipped_future = {}, []
    for s in api_sets:
        code = (s.get("set_code") or "").upper()
        if not code:
            continue
        d = pdate(s.get("tcg_date") or "")
        if code in have_sets:
            continue
        if d and d > TODAY:
            skipped_future.append((d, code, s.get("set_name")))
            continue
        prev = wanted.get(code)
        if prev is None or (d and prev[0] and d < prev[0]) or (d and not prev[0]):
            wanted[code] = (d, s)

    # Gather printings per set from the card side.
    printings = defaultdict(list)
    for c in api_cards:
        for st in (c.get("card_sets") or []):
            full = st.get("set_code") or ""
            code = full.split("-")[0].upper()
            if code in wanted:
                printings[code].append((c["id"], full, clean_rarity(st.get("set_rarity"))))

    # --- how unclassified boosters are grouped -------------------------------
    # NOT by series number. The first pass tried to continue the DB's Series 9/10
    # cadence of twelve core sets and it misfired badly: it read Legendary Arc-V
    # Decks and Speed Duel GX as core boosters on card count alone, and ordered
    # Dawn of Majesty after Burst of Destiny. Nothing in the source names a
    # series, so any number here is a claim the data cannot support, and a
    # confidently wrong "Series 12" is worse than no series at all.
    #
    # The release year is in the data, is never wrong, and groups the binder into
    # evenly sized generations. That is what is used instead.

    written_cards = written_sets = 0
    dist_used = Counter()
    type_used = Counter()
    new_rarities = Counter()
    known_rarities = {json.load(io.open(f"{DB}/rarities/{p}", encoding="utf-8"))["rarity"]
                      for p in os.listdir(f"{DB}/rarities")}

    # --- cards ---------------------------------------------------------------
    needed_ids = {cid for code in wanted for cid, _, _ in printings.get(code, [])}
    for cid in sorted(needed_ids - have_cards):
        card = build_card(by_id[cid])
        if card is None:
            continue
        name = slug(card["name"]) or f"card_{cid}"
        if name in have_card_files:
            name = f"{name}_{cid}"
        have_card_files.add(name)
        with io.open(f"{DB}/cards/{name}.json", "w", encoding="utf-8", newline="\n") as f:
            json.dump(card, f, indent=2, ensure_ascii=False)
        written_cards += 1

    # --- sets ----------------------------------------------------------------
    for code, (d, s) in sorted(wanted.items(), key=lambda kv: (kv[1][0] or datetime.date(1, 1, 1))):
        rows = printings.get(code, [])
        if not rows:
            continue
        name = s.get("set_name") or code
        seen, cards = set(), []
        for cid, full, rar in rows:
            if cid not in by_id or build_card(by_id[cid]) is None:
                continue
            key = (cid, full, rar)
            if key in seen:
                continue
            seen.add(key)
            cards.append({"id": cid, "code": full, "rarity": rar, "image_index": 0})
            if rar not in known_rarities and rar != "Common":
                new_rarities[rar] += 1
        if not cards:
            continue
        cards.sort(key=lambda c: c["code"])

        stype, pull = classify(name, code, len(cards))
        if stype == "Booster Pack (Misc.)" and d:
            # Grouped by the year it came out -- see the note above on why this
            # is not a series number.
            stype = f"Booster Pack ({d.year})"

        out = {
            "name": name,
            "code": code,
            "type": stype,
            "date": d.strftime("%d-%m-%Y") if d else "",
            "image": f"https://ygoprodeck.com/pics_sets/{code}.jpg",
            "pull_type": pull,
        }
        if pull == "distribution":
            dist = choose_distribution([c["rarity"] for c in cards], available_dist)
            if dist:
                out["distribution"] = dist
                dist_used[dist] += 1
            else:
                out["pull_type"] = "full"
        out["cards"] = cards
        type_used[out["type"]] += 1

        fname = slug(name)
        if fname in have_names:
            fname = f"{fname}_{slug(code)}"
        have_names.add(fname)
        with io.open(f"{DB}/sets/{fname}.json", "w", encoding="utf-8", newline="\n") as f:
            json.dump(out, f, indent=2, ensure_ascii=False)
        written_sets += 1

    print(f"cards written : {written_cards}")
    print(f"sets written  : {written_sets}")
    print(f"future skipped: {len(skipped_future)} -> {[c for _, c, _ in skipped_future]}")
    print()
    print("set types written:")
    for t, n in type_used.most_common():
        print(f"  {n:5}  {t}")
    print()
    print("distributions assigned:")
    for t, n in dist_used.most_common():
        print(f"  {n:5}  {t}")
    print()
    print("rarities with NO definition file (these draw flat until defined):")
    for r, n in new_rarities.most_common():
        print(f"  {n:6}  {r!r}")


if __name__ == "__main__":
    main()
