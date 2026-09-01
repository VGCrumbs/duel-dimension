"""Builds every structure deck's .ydk from the card database's own set data.

The sibling of gen_starter_deck.py, and the same principle: nothing here is a
hand-picked list, the deck IS the set. Every card in a generated deck is one the
shop can sell, so a deck the Duel Bot plays is also a deck a player could own.

    python tools/gen_structure_deck.py            # rebuild all of them
    python tools/gen_structure_deck.py SDSH       # rebuild one, by set code

Structure decks are DISCOVERED rather than listed. gen_starter_deck.py names its
eighteen decks in a table because it is choosing one representative per era; a
structure deck has no such judgement to make, so hard-coding sixty of them would
be a list that silently goes stale the next time the database is updated.

Decks under the forty-card minimum are SKIPPED, not emitted. Six of the sets
carrying "Structure Deck" in their name are special editions and box sets that
list no cards at all, or early decks that list too few -- and a deck that cannot
legally be played is worse than a deck that is not offered, because the first
one fails at the duel and the second fails in the picker.

Also writes StructureDecks.java, so the registry cannot drift from the files it
names. Editing that class by hand will be undone the next time this runs.
"""
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DB = os.path.join(ROOT, 'bundle', 'ydm_db')
OUT = os.path.join(ROOT, 'common', 'src', 'main', 'resources',
                   'data', 'dueldimension', 'decks', 'structure')
JAVA = os.path.join(ROOT, 'common', 'src', 'main', 'java', 'de', 'cas_ual_ty',
                    'dueldimension', 'ocg', 'deck', 'StructureDecks.java')

# Same rule as the starter generator: the extra deck is read off the card's own
# monster_type, never guessed from the era.
EXTRA_TYPES = {'Fusion', 'Synchro', 'Xyz', 'Link'}

MINIMUM = 40

# The TCG limit. Guards against a set whose data lists more prints of a card
# than a deck could legally contain.
MAX_COPIES = 3


def cards():
    by_id = {}
    folder = os.path.join(DB, 'cards')
    for name in os.listdir(folder):
        if not name.endswith('.json'):
            continue
        with open(os.path.join(folder, name), encoding='utf-8') as handle:
            card = json.load(handle)
        by_id[card['id']] = card
    return by_id


def structure_sets():
    """Every set whose name says it is a structure deck, by code."""
    out = []
    folder = os.path.join(DB, 'sets')
    for name in sorted(os.listdir(folder)):
        if not name.endswith('.json'):
            continue
        with open(os.path.join(folder, name), encoding='utf-8') as handle:
            entry = json.load(handle)
        if 'structure deck' not in entry.get('name', '').lower():
            continue
        out.append(entry)
    return out


def slug_for(name):
    """A file name from a deck name, in the alphabet the mod's loaders accept."""
    s = name.lower()
    s = s.replace('structure deck:', '').replace('structure deck', '')
    s = re.sub(r"[^a-z0-9]+", '_', s).strip('_')
    return s or 'structure'


def build(entry, all_cards):
    code = entry.get('code')
    display = entry.get('name')
    main, extra, missing = [], [], []
    copies = {}
    for listed in entry.get('cards', []):
        passcode = listed['id']
        # REPEATED ENTRIES ARE COPIES, and dropping them is the difference
        # between 25 usable decks and 55.
        #
        # A set lists one entry per physical card, each with its own set code --
        # Dinosmasher's Fury carries 81823360 as both SR04-003 and SR04-0xx
        # because the deck really does contain two of it. There is no quantity
        # field; the repeat IS the quantity.
        #
        # gen_starter_deck.py dedupes by passcode, which is right for what it
        # does: a starter deck lists each card once, so every one of them counts
        # the same either way (checked -- SDY 46/46, SDJ 48/48, YS18 40/40).
        # Structure decks are built around multiples, so the same rule quietly
        # shaved them below the forty-card floor, and they clustered at exactly
        # 39 because it was usually one duplicate being lost.
        copies[passcode] = copies.get(passcode, 0) + 1
        if copies[passcode] > MAX_COPIES:
            continue                       # the TCG limit, in case data is odd
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

    if len(main) < MINIMUM:
        print('  SKIP  %-8s %-46s main %2d  (under %d)'
              % (code, display[:46], len(main), MINIMUM))
        return None

    slug = slug_for(display)
    lines = ['#created by Duel Dimension - %s (%s), generated from YDM2-DB set data'
             % (display, code), '#main']
    lines += [str(c) for c in main]
    lines.append('#extra')
    lines += [str(c) for c in extra]
    lines.append('!side')

    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, slug + '.ydk'), 'w',
              encoding='utf-8', newline='\n') as handle:
        handle.write('\n'.join(lines) + '\n')
    print('  ok    %-8s %-46s main %2d  extra %2d  missing %d'
          % (code, display[:46], len(main), len(extra), len(missing)))
    return {'slug': slug, 'code': code, 'name': display,
            'main': len(main), 'extra': len(extra)}


HEADER = '''package de.cas_ual_ty.dueldimension.ocg.deck;

import java.util.List;

/**
 * Every structure deck the card database describes, as a deck that can be
 * played. Generated by {@code tools/gen_structure_deck.py} from the set data --
 * the deck IS the set, so every card in one is obtainable in game.
 * <p>
 * <b>Do not edit by hand.</b> The generator writes both the {@code .ydk} files
 * and this class, precisely so the registry cannot name a deck the resources do
 * not contain.
 * <p>
 * Sets listing fewer than forty main-deck cards are absent: those are special
 * editions and box sets that list no cards at all, plus a couple of early decks
 * that were genuinely short. A deck that cannot legally be played is worse than
 * one that is not offered.
 */
public final class StructureDecks
{
    /**
     * @param id          resource/deck slug
     * @param displayName shown to the player
     * @param setCode     the TCG set this deck reproduces
     * @param mainCount   main deck size, for a picker that wants to show it
     * @param extraCount  extra deck size
     */
    public record Entry(String id, String displayName, String setCode,
        int mainCount, int extraCount)
    {
        public String resourcePath()
        {
            return "data/dueldimension/decks/structure/" + id + ".ydk";
        }

        public YdkDeck load()
        {
            return YdkDeck.loadResource(resourcePath());
        }
    }

    private StructureDecks()
    {
    }

'''


def write_java(entries):
    parts = [HEADER]
    parts.append('    public static final List<Entry> ALL = List.of(\n')
    rows = []
    for e in entries:
        name = e['name'].replace('\\', '\\\\').replace('"', '\\"')
        rows.append('        new Entry("%s", "%s", "%s", %d, %d)'
                    % (e['slug'], name, e['code'], e['main'], e['extra']))
    parts.append(',\n'.join(rows))
    parts.append(');\n\n')
    parts.append('''    /** The deck of that id, or null if there is not one. */
    public static Entry byId(String id)
    {
        for(Entry entry : ALL)
        {
            if(entry.id().equals(id))
            {
                return entry;
            }
        }
        return null;
    }
}
''')
    os.makedirs(os.path.dirname(JAVA), exist_ok=True)
    with open(JAVA, 'w', encoding='utf-8', newline='\n') as handle:
        handle.write(''.join(parts))
    print('wrote %s with %d decks' % (os.path.relpath(JAVA, ROOT), len(entries)))


def main():
    all_cards = cards()
    sets = structure_sets()
    only = sys.argv[1] if len(sys.argv) > 1 else None
    print('%d sets named as structure decks' % len(sets))
    built = []
    for entry in sets:
        if only and entry.get('code') != only:
            continue
        made = build(entry, all_cards)
        if made:
            built.append(made)
    built.sort(key=lambda e: e['slug'])
    if not only:
        write_java(built)
    print('%d decks generated, %d skipped' % (len(built), len(sets) - len(built)))


if __name__ == '__main__':
    main()
