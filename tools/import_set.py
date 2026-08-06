"""Imports a card set into the mod's database format.

The bundled database stops in August 2021, so anything printed since is simply
absent -- not just one card but every set after that point. This converts a set
from the same source the database was originally built from (YGOPRODeck, which
is what every card's image URL already points at) into the exact JSON shapes the
mod reads.

Output goes into the mod's own resources, so the data is tracked in the
repository, packaged into the jar, and installed into the live database by the
mod itself on every boot. Nothing here needs running for a checkout to work --
it is only needed to add a set that is not bundled yet.

    python tools/import_set.py "Burst of Destiny" BODE "Booster Pack (Series 11)" 05-11-2021

Run with no arguments to re-import everything listed in EXTRA_SETS.
"""
import json
import os
import re
import sys
import urllib.parse
import urllib.request

API = "https://db.ygoprodeck.com/api/v7/cardinfo.php"
# Inside the mod's resources, so the imported data is packaged in the jar and
# installed into the live database by DdDatabase.installBundledExtras() on every
# boot. Writing it into one machine's run folder instead -- which is what this
# used to do -- meant a server and its players could disagree about which cards
# exist, and a card the other side cannot find silently becomes a blank.
TRACKED = os.path.join("src", "main", "resources", "ydm_extras")

# Sets imported on top of the bundled database, re-runnable as a batch.
EXTRA_SETS = [
    # name, code, type, release date, distribution
    ("Burst of Destiny", "BODE", "Booster Pack (Series 11)", "05-11-2021",
     "booster_pack_without_rare_with_starlight_rare"),
]


def slug(name):
    """The mod's filename rule: lowercase, every non-alphanumeric becomes _."""
    return re.sub(r"[^a-z0-9]", "_", name.lower())


def fetch(params):
    # A User-Agent is required: the default urllib one is rejected with 403.
    url = API + "?" + urllib.parse.urlencode(params)
    request = urllib.request.Request(url, headers={"User-Agent": "DuelDimension-import/1.0"})
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.load(response)


def card_json(card):
    """One API card in the mod's own schema.

    The schemas differ by variant -- a Link has no def or level, an Xyz has rank
    rather than level, a Pendulum carries its scales -- so each is built
    explicitly rather than by copying a superset and hoping the reader ignores
    the extras.
    """
    api_type = card.get("type", "")
    base = {
        "id": card["id"],
        "name": card["name"],
        "is_illegal": False,
        "is_custom": False,
        "text": card.get("desc", ""),
        "images": ["https://images.ygoprodeck.com/images/cards/%d.jpg" % card["id"]],
    }

    if "Spell" in api_type:
        base["type"] = "Spell"
        base["spell_type"] = card.get("race", "Normal")
        return base
    if "Trap" in api_type:
        base["type"] = "Trap"
        base["trap_type"] = card.get("race", "Normal")
        return base

    # Monsters. monster_type is the extra-deck kind, empty for a main-deck card.
    monster_type = ""
    for kind in ("Fusion", "Synchro", "Xyz", "Link", "Ritual"):
        if kind in api_type:
            monster_type = kind
            break

    ability = ""
    for kind in ("Flip", "Toon", "Spirit", "Union", "Gemini"):
        if kind in api_type:
            ability = kind
            break

    base.update({
        "type": "Monster",
        "attribute": card.get("attribute", ""),
        "atk": card.get("atk") or 0,
        "species": card.get("race", ""),
        "monster_type": monster_type,
        "is_pendulum": "Pendulum" in api_type,
        "ability": ability,
        "has_effect": "Effect" in api_type,
    })

    if monster_type == "Link":
        base["link_rating"] = card.get("linkval") or 0
        base["link_arrows"] = card.get("linkmarkers") or []
        return base

    base["def"] = card.get("def") or 0
    if monster_type == "Xyz":
        base["rank"] = card.get("level") or 0
    else:
        base["level"] = card.get("level") or 0
        base["is_tuner"] = "Tuner" in api_type

    if base["is_pendulum"]:
        base["pendulum_scale_left_blue"] = card.get("scale") or 0
        base["pendulum_scale_right_red"] = card.get("scale") or 0
        # The API gives one description; the pendulum half is the bracketed
        # block when present, so it is split out rather than duplicated.
        text = card.get("desc", "")
        marker = "[ Pendulum Effect ]"
        if marker in text:
            _, rest = text.split(marker, 1)
            pendulum, _, monster = rest.partition("[ Monster Effect ]")
            base["pendulum_text"] = pendulum.strip()
            base["text"] = monster.strip() or text
        else:
            base["pendulum_text"] = ""
    return base


def write(path, payload):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(payload, handle, indent=2, ensure_ascii=False)
        handle.write("\n")


def import_set(name, code, set_type, date, distribution):
    print("== %s (%s) ==" % (name, code))
    data = fetch({"cardset": name}).get("data", [])
    if not data:
        print("   no cards returned; nothing written")
        return

    entries = []
    written = 0
    skipped = 0
    for card in sorted(data, key=lambda c: c["name"]):
        # A card can appear in a set more than once at different rarities; the
        # printing that belongs to THIS set is the one to record.
        printings = [s for s in card.get("card_sets", []) if s.get("set_name") == name]
        if not printings:
            continue
        printing = sorted(printings, key=lambda s: s.get("set_code", ""))[0]
        entries.append({
            "id": card["id"],
            "code": printing.get("set_code", ""),
            "rarity": printing.get("set_rarity", "Common"),
            "image_index": 0,
        })

        payload = card_json(card)
        filename = slug(card["name"]) + ".json"
        target = os.path.join(TRACKED, "cards", filename)
        if os.path.exists(target):
            skipped += 1
        else:
            written += 1
        write(target, payload)

    entries.sort(key=lambda e: e["code"])
    set_payload = {
        "name": name,
        "code": code,
        "type": set_type,
        "date": date,
        "image": "https://ygoprodeck.com/pics_sets/%s.jpg" % code,
        "pull_type": "distribution",
        "distribution": distribution,
        "cards": entries,
    }
    set_file = slug(name) + ".json"
    write(os.path.join(TRACKED, "sets", set_file), set_payload)
    print("   %d cards in set, %d new card files, %d already present"
          % (len(entries), written, skipped))


def write_index():
    """Names every bundled file, because a jar's directories cannot be listed.

    The mod reads this index to know what to install, so it is rewritten after
    every import rather than maintained by hand.
    """
    paths = []
    for folder in ("cards", "sets", "distributions", "rarities"):
        directory = os.path.join(TRACKED, folder)
        if not os.path.isdir(directory):
            continue
        for filename in sorted(os.listdir(directory)):
            if filename.endswith(".json"):
                paths.append(folder + "/" + filename)

    with open(os.path.join(TRACKED, "index.json"), "w", encoding="utf-8") as handle:
        json.dump(paths, handle, indent=1)
        handle.write("\n")
    print("== index: %d files ==" % len(paths))


if __name__ == "__main__":
    if len(sys.argv) >= 5:
        import_set(sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4],
                   sys.argv[5] if len(sys.argv) > 5
                   else "booster_pack_without_rare_with_starlight_rare")
    else:
        for entry in EXTRA_SETS:
            import_set(*entry)
    write_index()
