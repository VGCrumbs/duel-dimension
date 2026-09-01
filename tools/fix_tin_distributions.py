"""Gives every tin the number of cards the real product actually contained.

A tin was opening the wrong size. The 2009 Collectible Tin handed out five
cards, when the thing on the shelf contained one Secret Rare promo; the 2010
tins handed out sixteen, when they contained five variant cards; and every Mega
Pack from 2020 on handed out nine, when Konami went to eighteen that year and
never went back.

The cause is that no tin had a distribution of its own. Each was pointed at
whichever existing one looked closest -- the 2004 through 2009 tins at
`the_dark_side_of_dimensions_movie_pack_secret_edition__pack`, which is a
completely unrelated product -- and that distribution's pack size became the
tin's, whatever it happened to be.

Those distributions CANNOT simply be corrected in place. They are shared:
`world_superstars` is the distribution for thirty-five sets, most of them video
game promos; `booster_pack_without_rare` for fifty-eight, mostly OTS packs.
Changing one to suit a tin would change all of them. So each tin gets its own
here, and the shared ones are left exactly as they are.

## Where the numbers come from

Mostly from the shipped data, which turns out to describe the products
precisely once you know to look. A promo set's card list is the pool of variants
across every tin in that wave, so the per-tin contents fall straight out of the
rarity split:

    2014 Mega-Tins    4 Super + 2 Platinum Secret   over 2 tins -> 2 + 1 = 3
    2016 Mega-Tins    6 Super + 4 Ultra + 2 Secret  over 2 tins -> 3 + 2 + 1 = 6
    2018 Mega-Tins    8 Ultra + 2 Secret            over 2 tins -> 4 + 1 = 5
    2008 Duelist Pack 6 Ultra + 6 Super + 3 Secret  over 3 tins -> 2 + 2 + 1 = 5

Each of those divides evenly and matches Konami's own description of the
product, which is the check that the split is the real one rather than an
arithmetic coincidence. The 2016 tins were advertised on "6 variant cards -- the
highest number of variant cards we've ever included in a tin before", and 6 is
what the split gives.

The rest are documented: 1 Secret Rare for 2002-2009, 5 variants for 2010-2013,
18-card Mega Packs for 2020 onward, 14 foils for both the Premium Collection and
Zexal Collection tins.

## On the chase rarities

A Quarter Century Secret Rare is one per TIN, not one per pack, and a Starlight
Rare is rarer still. Those are weighted pulls rather than fixed slots -- two
pulls in three at MP24 have no Quarter Century in them, which averages to the
one per three-pack tin the real product guaranteed. Every pull of a given
distribution still totals the same number of cards, so the pack size does not
wobble; only what is in it does.
"""
import io
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)

# The bundle is the source of truth -- it is what is packed into the jar. The
# run copies are unpacked from it and would otherwise keep the old numbers
# until a database update wiped and rewrote them.
DATABASES = [
    os.path.join(REPO, "bundle", "ydm_db"),
    os.path.join(REPO, "mc262", "run", "ydm_db"),
    os.path.join(REPO, "mc1211", "run", "ydm_db"),
]


def pull(weight, *entries):
    return {"weight": weight,
            "entries": [{"count": n, "rarities": list(r)} for n, r in entries]}


SECRET = ["Secret Rare"]
SUPER = ["Super Rare"]
ULTRA = ["Ultra Rare"]
ULTIMATE = ["Ultimate Rare"]
PLATINUM = ["Platinum Secret Rare"]
PRISMATIC = ["Prismatic Secret Rare"]
QUARTER = ["Quarter Century Secret Rare"]
STARLIGHT = ["Starlight Rare"]
COMMON = ["Common", "Short Print", "Super Short Print"]
RARE = ["Rare"]
TOP = ["Secret Rare", "Prismatic Secret Rare"]

# name -> (pulls, which sets use it, what the real product was)
DISTRIBUTIONS = {
    # -------------------------------------------------- promo-only tins
    "collectible_tin_promo": (
        [pull(1, (1, SECRET))],
        ["BPT", "CT1", "CT2", "CT03", "CT04", "CT05", "CT06"],
        "2002-2009 tins: one Secret Rare promo",
    ),
    "collectible_tin_promos_2010": (
        [pull(1, (4, SUPER), (1, SECRET))],
        ["CT07", "CT08", "CT09", "CT10"],
        "2010-2013 tins: five variants, one of them Secret",
    ),
    "mega_tin_promos_2014": (
        [pull(1, (2, SUPER), (1, PLATINUM))],
        ["CT11", "CT12"],
        "2014-2015 Mega-Tins: two Super, one Platinum Secret",
    ),
    "mega_tin_promos_2016": (
        [pull(1, (3, SUPER), (2, ULTRA), (1, SECRET))],
        ["CT13"],
        "2016 Mega-Tins: six variants, the most of any tin",
    ),
    "mega_tin_promos_2017": (
        [pull(1, (2, SUPER), (1, ULTRA), (1, SECRET))],
        ["CT14"],
        "2017 Mega-Tins: four variants",
    ),
    "mega_tin_promos_2018": (
        [pull(1, (4, ULTRA), (1, SECRET))],
        ["CT15"],
        "2018 Mega-Tins: four Ultra, one Secret",
    ),
    "mega_tin_promos_2019": (
        [pull(1, (3, PRISMATIC))],
        ["TN19"],
        "2019 Gold Sarcophagus Tin: three Prismatic Secret variants",
    ),
    "mega_tin_promos_25th": (
        [pull(1, (3, QUARTER))],
        ["TN23"],
        "25th Anniversary tin: three Quarter Century Secret variants",
    ),
    "duelist_pack_collection_tin_2008": (
        [pull(1, (2, ULTRA), (2, SUPER), (1, SECRET))],
        ["DPCT"],
        "Duelist Pack Collection Tin 2008: five promos",
    ),
    "duelist_pack_collection_tin_2011": (
        [pull(1, (3, SUPER))],
        ["DPC5"],
        "Duelist Pack Collection Tin 2011: the three-card promo pack",
    ),
    "premium_collection_tin": (
        [pull(1, (6, SECRET), (8, SUPER))],
        ["PRC1"],
        "Premium Collection Tin: fourteen foils",
    ),
    "zexal_collection_tin": (
        [pull(1, (3, ULTIMATE), (4, ULTRA), (7, SUPER))],
        ["ZTIN"],
        "Zexal Collection Tin: fourteen cards, three of them Ultimate",
    ),

    # ------------------------------------------------ the 18-card Mega Packs
    "mega_pack_18": (
        [pull(1, (12, COMMON), (1, RARE), (2, SUPER), (2, ULTRA), (1, TOP))],
        ["MP20", "MP21", "MP22"],
        "2020-2022 Mega Pack: eighteen cards",
    ),
    "mega_pack_18_double_prismatic": (
        [pull(1, (12, COMMON), (1, RARE), (1, SUPER), (2, ULTRA), (2, PRISMATIC))],
        ["MP23"],
        "2023 Mega Pack: eighteen, with the anniversary's doubled Prismatics",
    ),
    "mega_pack_18_quarter_century": (
        # Two pulls in three carry no Quarter Century, which averages to the one
        # per three-pack tin the real product guaranteed.
        [pull(2, (12, COMMON), (4, ULTRA), (2, PRISMATIC)),
         pull(1, (12, COMMON), (4, ULTRA), (1, PRISMATIC), (1, QUARTER))],
        ["MP24"],
        "2024 Mega Pack: eighteen; no Rare or Super exists in this set",
    ),
    "mega_pack_18_starlight": (
        # Starlight stays a chase at the same 1-in-48 the other Starlight
        # distribution uses, rather than becoming a guaranteed slot.
        [pull(47, (12, COMMON), (4, ULTRA), (2, PRISMATIC)),
         pull(1, (12, COMMON), (4, ULTRA), (1, PRISMATIC), (1, STARLIGHT))],
        ["MP25"],
        "2025 Mega Pack: eighteen, Starlight as the chase",
    ),
}


def size(pulls):
    """Every pull of one distribution has to hand out the same many cards."""
    sizes = {sum(e["count"] for e in p["entries"]) for p in pulls}
    if len(sizes) != 1:
        raise SystemExit("uneven pack size: %s" % sorted(sizes))
    return sizes.pop()


def rarities_present(cards):
    found = set()
    for card in cards:
        for rarity in (card.get("rarities") or [card.get("rarity")]):
            if rarity:
                found.add(rarity)
    return found


def main():
    for db in DATABASES:
        if not os.path.isdir(db):
            print("skip (absent)  %s" % db)
            continue
        print(os.path.relpath(db, REPO))

        folder = os.path.join(db, "distributions")
        for name, (pulls, codes, why) in DISTRIBUTIONS.items():
            path = os.path.join(folder, name + ".json")
            with io.open(path, "w", encoding="utf-8", newline="\n") as out:
                json.dump({"name": name, "pulls": pulls}, out, indent=2)
                out.write("\n")

        # Point each set at its own, and check the pool can actually answer it.
        # A distribution asking for a rarity the set has none of yields short
        # packs forever and silently, which is exactly the class of bug being
        # fixed here.
        wanted = {}
        for name, (pulls, codes, why) in DISTRIBUTIONS.items():
            for code in codes:
                wanted[code] = name

        done = []
        for file in sorted(os.listdir(os.path.join(db, "sets"))):
            if not file.endswith(".json"):
                continue
            path = os.path.join(db, "sets", file)
            data = json.load(io.open(path, encoding="utf-8"))
            code = data.get("code")
            if code not in wanted:
                continue
            name = wanted[code]
            pulls = DISTRIBUTIONS[name][0]

            have = rarities_present(data.get("cards", []))
            asked = {r for p in pulls for e in p["entries"] for r in e["rarities"]}
            unmet = [r for p in pulls for e in p["entries"]
                     if not (set(e["rarities"]) & have) for r in [e["rarities"][0]]]
            if unmet:
                raise SystemExit("%s (%s) has no %s" % (code, data.get("name"),
                                                        ", ".join(sorted(set(unmet)))))

            was = data.get("distribution")
            data["distribution"] = name
            with io.open(path, "w", encoding="utf-8", newline="\n") as out:
                json.dump(data, out, indent=2)
                out.write("\n")
            done.append((code, data.get("name"), was, name, size(pulls)))

        for code, label, was, now, count in sorted(done, key=lambda t: t[1] or ""):
            print("   %-6s %-46s %2d cards   %s" % (code, (label or "")[:46], count, now))
        missing = sorted(set(wanted) - {d[0] for d in done})
        if missing:
            print("   not in this database: %s" % ", ".join(missing))
        print()


if __name__ == "__main__":
    main()
