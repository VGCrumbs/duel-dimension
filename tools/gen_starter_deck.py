"""Builds a starter deck's .ydk from the card database's own set data.

The three original decks were made this way and the file header says so; this
is that step, written down. Nothing here is a hand-picked list: the deck is the
set, so every card in it is one the shop can sell and a starting deck doubles
as a legitimate collection.

    python tools/gen_starter_deck.py YSDJ jaden

Run with no arguments to rebuild every deck listed in DECKS.
"""
import json
import os
import sys

DB = 'run/ydm_db'
OUT = 'src/main/resources/data/dueldimension/decks'

# The eras, one representative deck each, by the set code the database holds.
DECKS = [
    ('SDY', 'yugi', 'Starter Deck: Yugi'),
    ('SDK', 'kaiba', 'Starter Deck: Kaiba'),
    ('SDJ', 'joey', 'Starter Deck: Joey'),
    ('YSDJ', 'jaden', 'Starter Deck: Jaden Yuki'),
    ('5DS1', 'yusei', "Starter Deck: Yu-Gi-Oh! 5D's"),
    ('YS11', 'yuma', 'Starter Deck: Dawn of the Xyz'),
]

# Monster types that live in the extra deck. Read off the card's own
# monster_type rather than assumed from the era: a 5D's deck is not all
# synchros and an Xyz deck is not all Xyz.
EXTRA_TYPES = {'Fusion', 'Synchro', 'Xyz', 'Link'}


def cards():
    """Every card in the database, by passcode."""
    by_id = {}
    folder = os.path.join(DB, 'cards')
    for name in os.listdir(folder):
        if not name.endswith('.json'):
            continue
        with open(os.path.join(folder, name), encoding='utf-8') as handle:
            card = json.load(handle)
        by_id[card['id']] = card
    return by_id


def sets():
    by_code = {}
    folder = os.path.join(DB, 'sets')
    for name in os.listdir(folder):
        if not name.endswith('.json'):
            continue
        with open(os.path.join(folder, name), encoding='utf-8') as handle:
            entry = json.load(handle)
        by_code.setdefault(entry['code'], entry)
    return by_code


def build(code, slug, display, all_cards, all_sets):
    entry = all_sets.get(code)
    if entry is None:
        raise SystemExit('no set with code %s' % code)

    main, extra, missing = [], [], []
    seen = set()
    for listed in entry['cards']:
        passcode = listed['id']
        if passcode in seen:
            continue
        seen.add(passcode)
        card = all_cards.get(passcode)
        if card is None:
            missing.append(passcode)
            continue
        if card.get('is_illegal'):
            continue
        if card.get('monster_type') in EXTRA_TYPES:
            extra.append(passcode)
        else:
            main.append(passcode)

    lines = ['#created by Duel Dimension - %s (%s), generated from YDM2-DB set data'
             % (display, code), '#main']
    lines += [str(c) for c in main]
    lines.append('#extra')
    lines += [str(c) for c in extra]
    lines.append('!side')

    path = os.path.join(OUT, slug + '.ydk')
    with open(path, 'w', encoding='utf-8', newline='\n') as handle:
        handle.write('\n'.join(lines) + '\n')
    note = '' if len(main) >= 40 else '   <-- UNDER 40, not legal'
    print('  %-6s %-8s main %2d  extra %2d  missing %d%s'
          % (code, slug, len(main), len(extra), len(missing), note))
    return len(main) >= 40


def main():
    all_cards = cards()
    all_sets = sets()
    wanted = DECKS
    if len(sys.argv) == 3:
        wanted = [(sys.argv[1], sys.argv[2], sys.argv[1])]
    ok = True
    for code, slug, display in wanted:
        ok &= build(code, slug, display, all_cards, all_sets)
    if not ok:
        raise SystemExit('a deck came out under the forty card minimum')


if __name__ == '__main__':
    main()
