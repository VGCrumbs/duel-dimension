"""Rebuilds each tin as the booster packs it actually contained.

A tin was one pull from one pool, so opening a Collectible Tin 2009 handed over
a single Secret Rare and nothing else. The real product was five booster packs
and a promo, and the mod already has all five of those boosters as sets -- it
simply had no way to say that a tin held them.

`CompositionCardPuller` is that way, and it predates this: `MVP1_SE_B` is three
packs of `MVP1_SE_P` plus three fixed cards. Two things stopped it working for a
tin, and both are fixed in the Java rather than here:

  * a sub-set that is a product in its own right was handed over SEALED, which
    is right for a box holding a structure deck and wrong for a tin, where the
    packs are the product -- hence `open_sub_sets`;
  * a pull returned a flat list of cards with no record of which pack each came
    from, so the reveal could not group them.

## The shape

Each tin becomes a composition of its boosters plus one promo sub-set:

    CT06  ->  composition [ANPR, ANPR, CRMS, CRMS, RGBT, CT06_C]
    CT06_C -> the promos that used to BE CT06, as a sub-set

A sub-set is one with no `name` and no `date`: that is what
`CardSet.isIndependentAndItem` tests, and it is why `MVP1_SE_C_1` has neither.
The promo sub-set keeps the cards and the distribution the tin used to carry, so
nothing about what the promos are or how many you get changes -- only where they
live.

## Where the contents come from

Yugipedia's per-tin "Breakdown" section, which states the pack line-up outright.
Where it differs by region the NORTH AMERICAN list is used, because the mod's
card data is TCG-EN.

Two known simplifications, both recorded rather than papered over:

  * the 2008 tins also held one Token Pack, which is not a card set the mod
    models, so it is omitted -- the five boosters and the promo are all there;
  * the 2013 tins held "1 Hidden Arsenal 4, 5, 6 or 7 pack", a choice this
    format cannot express, so HA07 is used as the contemporary one.
"""
import io
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)

DATABASES = [
    os.path.join(REPO, "bundle", "ydm_db"),
    os.path.join(REPO, "mc262", "run", "ydm_db"),
    os.path.join(REPO, "mc1211", "run", "ydm_db"),
]

# tin code -> the booster packs it held, in order
TINS = {
    "CT1":  ["PGD", "MFC", "DCR", "IOC", "AST"],
    "CT2":  ["SOD", "RDS", "FET", "DB1", "DR1"],
    "CT03": ["CRV", "CRV", "EEN", "SOI", "EOJ"],
    "CT04": ["EEN", "POTD", "CDIP", "STON", "FOTB"],
    "CT05": ["PTDN", "PTDN", "LODT", "TDGS", "TDGS"],
    "CT06": ["ANPR", "ANPR", "CRMS", "CRMS", "RGBT"],
    "CT07": ["SOVR", "ABPF", "ABPF", "TSHD", "TSHD"],
    "CT08": ["STOR", "STOR", "HA04", "HA04", "EXVC"],
    "CT09": ["PHSW", "PHSW", "PHSW", "GAOV", "GAOV"],
    "CT10": ["HA07", "ABYR", "ABYR", "LTGY", "LTGY"],
    # The Mega-Tins: three Mega Packs, and the mod already carries each year's
    # Mega Pack as its own set, so these need no research at all.
    "CT11": ["MP14", "MP14", "MP14"],
    "CT12": ["MP15", "MP15", "MP15"],
    "CT13": ["MP16", "MP16", "MP16"],
    "CT14": ["MP17", "MP17", "MP17"],
    "CT15": ["MP18", "MP18", "MP18"],
    "TN19": ["MP19", "MP19", "MP19"],
}

# The 2020-on tins ARE the Mega Pack set in this database -- there is no
# separate promo set for them -- so each becomes three packs of itself, split
# out as a sub-set so the composition has something to repeat.
MEGA_ONLY = ["MP20", "MP21", "MP22", "MP23", "MP24", "MP25"]


def load(folder):
    out = {}
    for file in os.listdir(folder):
        if not file.endswith(".json"):
            continue
        path = os.path.join(folder, file)
        data = json.load(io.open(path, encoding="utf-8"))
        if data.get("code"):
            out[data["code"]] = (path, data)
    return out


def write(path, data):
    with io.open(path, "w", encoding="utf-8", newline="\n") as out:
        json.dump(data, out, indent=2)
        out.write("\n")


def main():
    for db in DATABASES:
        folder = os.path.join(db, "sets")
        if not os.path.isdir(folder):
            print("skip (absent)  %s" % db)
            continue
        print(os.path.relpath(db, REPO))
        sets = load(folder)

        missing = sorted({code for packs in TINS.values() for code in packs}
                         - set(sets))
        if missing:
            raise SystemExit("boosters not in this database: %s" % ", ".join(missing))

        for tin, packs in sorted(TINS.items()):
            if tin not in sets:
                print("   %-6s not in this database" % tin)
                continue
            path, data = sets[tin]
            if data.get("pull_type") == "composition":
                print("   %-6s already a composition" % tin)
                continue

            # The promos move out into a sub-set of their own, keeping the
            # cards and the distribution the tin carried. No name and no date,
            # which is what makes it inline rather than arrive sealed.
            promo = tin + "_C"
            write(os.path.join(folder, promo.lower() + ".json"), {
                "code": promo,
                "type": "Sub-Set",
                "pull_type": "distribution",
                "distribution": data["distribution"],
                "cards": data.get("cards", []),
            })

            for key in ("distribution", "cards"):
                data.pop(key, None)
            data["pull_type"] = "composition"
            data["open_sub_sets"] = True
            data["sub_sets"] = packs + [promo]
            write(path, data)
            print("   %-6s %-42s %d packs + promos"
                  % (tin, (data.get("name") or "")[:42], len(packs)))

        for code in MEGA_ONLY:
            if code not in sets:
                continue
            path, data = sets[code]
            if data.get("pull_type") == "composition":
                print("   %-6s already a composition" % code)
                continue
            pack = code + "_P"
            write(os.path.join(folder, pack.lower() + ".json"), {
                "code": pack,
                "type": "Sub-Set",
                "pull_type": "distribution",
                "distribution": data["distribution"],
                "cards": data.get("cards", []),
            })
            for key in ("distribution", "cards"):
                data.pop(key, None)
            data["pull_type"] = "composition"
            data["open_sub_sets"] = True
            data["sub_sets"] = [pack, pack, pack]
            write(path, data)
            print("   %-6s %-42s 3 Mega Packs"
                  % (code, (data.get("name") or "")[:42]))
        print()


if __name__ == "__main__":
    main()
