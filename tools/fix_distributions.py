"""Make every distribution-pull set actually able to produce cards.

THE BUG. DistributionCardPuller.makeCardPool skips any pull entry whose rarities
the set does not contain, and openDistribution returns whatever survived. So a
pull whose every entry misses yields an EMPTY pack -- the set lists in the shop,
takes the money, and hands back nothing. 33 sets did that every single time and
96 more did it some of the time, because the first pass picked a distribution
from a coarse name/rarity heuristic without ever checking the set could satisfy
the pulls.

THE FIX, in two steps and in that order:

  1. Re-select from the distributions already in the tree. Those are hand-authored
     and model real pack structure, so one that fits is always better than one
     invented here. A candidate qualifies only if EVERY pull in it can yield at
     least one card from this set; among the qualifiers the one covering the most
     of the set's rarities wins, ties going to the one with the most pulls (the
     richer structure) and then by name so the choice is stable.

  2. Only when nothing qualifies, synthesise one from the set's OWN composition:
     one pull per rarity, each giving a single card, weighted by how many cards
     carry that rarity. The odds are then the set's real rarity spread rather
     than a number invented here, and it can never come back empty. These are
     shared per rarity-signature, not per set, so 129 broken sets need far fewer
     files than they have names.

Nothing that already works is touched.
"""
import io
import json
import os
import re
from collections import Counter, defaultdict

DB = "run/ydm_db"


def load_dists():
    out = {}
    for p in os.listdir(f"{DB}/distributions"):
        d = json.load(io.open(f"{DB}/distributions/{p}", encoding="utf-8"))
        out[d["name"]] = d
    return out


def pull_ok(pull, have):
    """Whether this pull can yield anything from a set with these rarities."""
    return any(set(e["rarities"]) & have for e in pull["pulls_entries"]) \
        if "pulls_entries" in pull else any(set(e["rarities"]) & have for e in pull["entries"])


def works(dist, have):
    return all(pull_ok(pull, have) for pull in dist["pulls"])


def coverage(dist, have):
    rar = set()
    for pull in dist["pulls"]:
        for e in pull["entries"]:
            rar.update(e["rarities"])
    return len(rar & have)


def signature_name(rarities):
    """
    A stable, readable name for a synthesised distribution.
    <p>
    Truncation carries a hash of the FULL signature. Without it the Duel Terminal
    sets collided: DTP1 has three of the four DT rarities and the full names only
    diverge past 110 characters, so DTP1 silently adopted the four-rarity
    distribution and kept an Ultra Parallel pull it could never fill -- the exact
    empty-pack bug this script exists to remove.
    """
    ordered = sorted(rarities)
    slug = "_".join(re.sub(r"[^a-z0-9]+", "_", r.lower()).strip("_") for r in ordered)
    name = f"composition_{slug}"
    if len(name) <= 110:
        return name
    # Deterministic across runs, unlike hash().
    import hashlib
    digest = hashlib.sha1("|".join(ordered).encode("utf-8")).hexdigest()[:8]
    return name[:101] + "_" + digest


def synthesise(name, counts):
    """One pull per rarity, one card each, weighted by the set's own spread."""
    pulls = []
    for rarity, n in sorted(counts.items(), key=lambda kv: (-kv[1], kv[0])):
        pulls.append({
            "weight": max(1, n),
            "entries": [{"count": 1, "rarities": [rarity]}],
        })
    return {"name": name, "pulls": pulls}


def main():
    dists = load_dists()
    made = {}
    changed = []
    still_broken = []

    for p in sorted(os.listdir(f"{DB}/sets")):
        path = f"{DB}/sets/{p}"
        s = json.load(io.open(path, encoding="utf-8"))
        if s.get("pull_type") != "distribution":
            continue
        cards = s.get("cards") or []
        if not cards:
            continue
        have = set(c["rarity"] for c in cards)

        current = dists.get(s.get("distribution"))
        if current is not None and works(current, have):
            continue                                  # already fine, leave alone

        # 1. the best existing distribution that can actually run
        best = None
        for name, d in dists.items():
            if not works(d, have):
                continue
            key = (coverage(d, have), len(d["pulls"]), name)
            if best is None or key > best[0]:
                best = (key, name)

        if best is not None:
            s["distribution"] = best[1]
            changed.append((s["code"], s.get("distribution"), "existing"))
        else:
            # 2. synthesised from this set's own rarity spread
            counts = Counter(c["rarity"] for c in cards)
            name = signature_name(have)
            if name not in dists:
                d = synthesise(name, counts)
                dists[name] = d
                made[name] = d
            s["distribution"] = name
            changed.append((s["code"], name, "synthesised"))

        io.open(path, "w", encoding="utf-8", newline="\n").write(
            json.dumps(s, indent=2, ensure_ascii=False) + "\n")

    for name, d in made.items():
        io.open(f"{DB}/distributions/{name}.json", "w", encoding="utf-8",
                newline="\n").write(json.dumps(d, indent=2, ensure_ascii=False) + "\n")

    print(f"sets repaired            : {len(changed)}")
    print(f"  reusing an existing one: {sum(1 for c in changed if c[2] == 'existing')}")
    print(f"  needing a new one      : {sum(1 for c in changed if c[2] == 'synthesised')}")
    print(f"new distribution files   : {len(made)}")
    for n in sorted(made):
        print(f"    {n}")

    # Re-verify the WHOLE database, not just what was touched.
    dists = load_dists()
    for p in os.listdir(f"{DB}/sets"):
        s = json.load(io.open(f"{DB}/sets/{p}", encoding="utf-8"))
        if s.get("pull_type") != "distribution" or not s.get("cards"):
            continue
        d = dists.get(s.get("distribution"))
        have = set(c["rarity"] for c in s["cards"])
        if d is None or not works(d, have):
            still_broken.append(s["code"])
    print()
    print(f"sets that can still open empty: {len(still_broken)} {still_broken[:20]}")


if __name__ == "__main__":
    main()
