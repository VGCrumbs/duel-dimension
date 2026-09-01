"""Proves every tin can actually fill the pack it now promises.

`DistributionCardPuller.chooseCardsFromPool` does not fail when a set has fewer
cards of a rarity than the pull entry asks for -- it adds whatever it has and
returns. So a distribution that asks for four Ultra Rares from a set holding two
yields a two-card pack, quietly, forever. That is the same class of bug as the
wrong pack sizes themselves, and asking "does this set have ANY card of that
rarity" does not catch it.

This replicates the puller exactly:

  * `makeCardPool` collects every card whose rarity string matches one of the
    entry's, comparing exactly -- "Prismatic Secret Rare" is not "Secret Rare";
  * `countUniqueCards` counts DISTINCT cards, because the draw removes every
    holder of a card once one of them is taken, so a set listing one card at
    three rarities can still only answer one slot.

Reported per pull, since a weighted distribution's pulls do not all ask the
same thing, and a shortfall in the rare pull is the one nobody would notice.
"""
import collections
import io
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
DB = os.path.join(REPO, "bundle", "ydm_db")

TINS = ["BPT", "CT1", "CT2", "CT03", "CT04", "CT05", "CT06", "CT07", "CT08",
        "CT09", "CT10", "CT11", "CT12", "CT13", "CT14", "CT15", "TN19", "TN23",
        "PRC1", "DPCT", "DPC5", "ZTIN",
        "MP20", "MP21", "MP22", "MP23", "MP24", "MP25"]


def load(folder):
    out = {}
    for file in os.listdir(os.path.join(DB, folder)):
        if file.endswith(".json"):
            data = json.load(io.open(os.path.join(DB, folder, file), encoding="utf-8"))
            if data.get("name"):
                out[data["name"] if folder == "distributions" else data.get("code")] = data
    return out


def main():
    dists = load("distributions")
    sets = load("sets")

    problems = 0
    print("%-6s %-44s %s" % ("code", "product", "pack"))
    for code in TINS:
        data = sets.get(code)
        if data is None:
            print("%-6s MISSING FROM DATABASE" % code)
            problems += 1
            continue
        dist = dists.get(data.get("distribution"))
        if dist is None:
            print("%-6s no distribution %r" % (code, data.get("distribution")))
            problems += 1
            continue

        # rarity -> distinct card ids carrying it
        byRarity = collections.defaultdict(set)
        for card in data.get("cards", []):
            byRarity[card.get("rarity")].add(card.get("id"))

        sizes = []
        notes = []
        for index, pull in enumerate(dist.get("pulls", [])):
            got = 0
            for entry in pull.get("entries", []):
                want = entry.get("count", 1)
                unique = set()
                for rarity in entry.get("rarities", []):
                    unique |= byRarity.get(rarity, set())
                got += min(want, len(unique))
                if len(unique) < want:
                    notes.append("pull %d wants %d %s, set has %d"
                                 % (index, want, "/".join(entry["rarities"]), len(unique)))
            sizes.append(got)

        promised = {sum(e.get("count", 1) for e in p.get("entries", []))
                    for p in dist.get("pulls", [])}
        actual = sorted(set(sizes))
        ok = actual == sorted(promised) and len(actual) == 1
        print("%-6s %-44s %s%s" % (code, (data.get("name") or "")[:44],
                                   actual[0] if len(actual) == 1 else actual,
                                   "" if ok else "   <-- SHORT"))
        for note in notes:
            print("        %s" % note)
            problems += 1

    print()
    if problems:
        print("%d problem(s)" % problems)
        return 1
    print("every tin fills its pack")
    return 0


if __name__ == "__main__":
    sys.exit(main())
