package de.cas_ual_ty.dueldimension.duel.profile;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The trunk's narrowing and sorting.
 * <p>
 * Tested against a stand-in card rather than the mod's model, which is the
 * reason {@link CardQuery} takes a {@code Facets} instead of reaching into
 * {@code Properties}: the rules can be checked without loading a card database.
 */
class CardQueryTest
{
    private record Card(long id, String name, String text, CardQuery.Kind kind,
        String attribute, String species, int level, int attack, int defence,
        String subType, java.util.Set<String> abilities)
    {
        Card(long id, String name, String text, CardQuery.Kind kind, String attribute,
            String species, int level, int attack, int defence)
        {
            this(id, name, text, kind, attribute, species, level, attack, defence,
                null, java.util.Set.of());
        }
    }

    private static final CardQuery.Facets<Card> FACETS = new CardQuery.Facets<>()
    {
        public String name(Card card)
        {
            return card.name();
        }

        public String text(Card card)
        {
            return card.text();
        }

        public CardQuery.Kind kind(Card card)
        {
            return card.kind();
        }

        public String attribute(Card card)
        {
            return card.attribute();
        }

        public String species(Card card)
        {
            return card.species();
        }

        public int level(Card card)
        {
            return card.level();
        }

        public int attack(Card card)
        {
            return card.attack();
        }

        public int defence(Card card)
        {
            return card.defence();
        }

        public long id(Card card)
        {
            return card.id();
        }

        public String subType(Card card)
        {
            return card.subType();
        }

        public java.util.Set<String> abilities(Card card)
        {
            return card.abilities();
        }
    };

    private static final Card DARK_MAGICIAN =
        new Card(46986414L, "Dark Magician", "The ultimate wizard in terms of attack and defense.",
            CardQuery.Kind.MONSTER, "DARK", "Spellcaster", 7, 2500, 2100);
    private static final Card FERAL_IMP =
        new Card(41392891L, "Feral Imp", "A monster with sharp eyes.",
            CardQuery.Kind.MONSTER, "DARK", "Fiend", 4, 1300, 1400);
    private static final Card CELTIC_GUARDIAN =
        new Card(91152256L, "Celtic Guardian", "An elf who learned to wield a sword.",
            CardQuery.Kind.MONSTER, "EARTH", "Warrior", 4, 1400, 1200);
    private static final Card DARK_HOLE =
        new Card(53129443L, "Dark Hole", "Destroy all monsters on the field.",
            CardQuery.Kind.SPELL, null, null, 0, 0, 0);
    private static final Card TRAP_HOLE =
        new Card(4206964L, "Trap Hole", "Destroy that target.",
            CardQuery.Kind.TRAP, null, null, 0, 0, 0);

    private static final List<Card> TRUNK =
        List.of(DARK_MAGICIAN, FERAL_IMP, CELTIC_GUARDIAN, DARK_HOLE, TRAP_HOLE);

    private static CardQuery<Card> query()
    {
        return new CardQuery<>(FACETS);
    }

    private static List<String> names(List<Card> cards)
    {
        return cards.stream().map(Card::name).toList();
    }

    @Test
    void anEmptyQueryReturnsTheWholeTrunk()
    {
        assertEquals(TRUNK.size(), query().apply(TRUNK).size());
        assertTrue(query().isClear());
    }

    @Test
    void textSearchesNameAndEffectText()
    {
        CardQuery<Card> q = query();
        q.setText("dark");
        // Dark Magician by name, Dark Hole by name.
        assertEquals(List.of("Dark Hole", "Dark Magician"), names(q.apply(TRUNK)));

        // And by effect text, so a card can be found without knowing its name.
        q.setText("destroy all monsters");
        assertEquals(List.of("Dark Hole"), names(q.apply(TRUNK)));
    }

    @Test
    void anEmptyFilterSetMeansDoNotNarrowRatherThanMatchNothing()
    {
        // The whole filter bar depends on this: unticking the last box must
        // widen back to everything, not collapse to an empty trunk.
        CardQuery<Card> q = query();
        q.toggleKind(CardQuery.Kind.SPELL);
        assertEquals(1, q.apply(TRUNK).size());
        q.toggleKind(CardQuery.Kind.SPELL);
        assertEquals(TRUNK.size(), q.apply(TRUNK).size());
    }

    @Test
    void kindsAttributesAndSpeciesNarrowTogether()
    {
        CardQuery<Card> q = query();
        q.toggleKind(CardQuery.Kind.MONSTER);
        assertEquals(3, q.apply(TRUNK).size());

        q.toggleAttribute("DARK");
        assertEquals(List.of("Dark Magician", "Feral Imp"), names(q.apply(TRUNK)));

        q.toggleSpecies("Fiend");
        assertEquals(List.of("Feral Imp"), names(q.apply(TRUNK)));
    }

    @Test
    void severalValuesOnOneAxisAreAlternatives()
    {
        CardQuery<Card> q = query();
        q.toggleAttribute("DARK");
        q.toggleAttribute("EARTH");
        assertEquals(3, q.apply(TRUNK).size(), "two attributes should widen, not exclude each other");
    }

    @Test
    void aLevelBandExcludesCardsThatHaveNoLevel()
    {
        // A spell is not level 0; it has nothing to compare, so a level filter
        // must drop it rather than sorting it to the bottom of the band.
        CardQuery<Card> q = query();
        q.setLevelRange(4, 4);
        assertEquals(List.of("Celtic Guardian", "Feral Imp"), names(q.apply(TRUNK)));

        q.setLevelRange(5, 0);
        assertEquals(List.of("Dark Magician"), names(q.apply(TRUNK)));
    }

    @Test
    void sortingCoversEveryOfferedOrder()
    {
        CardQuery<Card> q = query();
        q.toggleKind(CardQuery.Kind.MONSTER);

        q.setSort(CardQuery.Sort.ATTACK);
        assertEquals(List.of("Feral Imp", "Celtic Guardian", "Dark Magician"), names(q.apply(TRUNK)));

        q.setDescending(true);
        assertEquals(List.of("Dark Magician", "Celtic Guardian", "Feral Imp"), names(q.apply(TRUNK)));

        q.setDescending(false);
        q.setSort(CardQuery.Sort.DEFENCE);
        assertEquals(List.of("Celtic Guardian", "Feral Imp", "Dark Magician"), names(q.apply(TRUNK)));

        q.setSort(CardQuery.Sort.LEVEL);
        assertEquals("Dark Magician", names(q.apply(TRUNK)).get(2));

        q.setSort(CardQuery.Sort.ID);
        assertEquals(List.of("Feral Imp", "Dark Magician", "Celtic Guardian"), names(q.apply(TRUNK)));
    }

    @Test
    void equalKeysStillSortStablySoTheTrunkDoesNotShuffle()
    {
        // Celtic Guardian and Feral Imp are both level 4. Without the name/id
        // tie-break their order could differ between openings, which is
        // disorienting to build in.
        CardQuery<Card> q = query();
        q.toggleKind(CardQuery.Kind.MONSTER);
        q.setSort(CardQuery.Sort.LEVEL);
        List<String> first = names(q.apply(TRUNK));
        List<String> again = names(q.apply(List.of(FERAL_IMP, CELTIC_GUARDIAN, DARK_MAGICIAN)));
        assertEquals(first, again, "the same cards in a different input order must sort the same");
    }

    @Test
    void clearResetsNarrowingButKeepsTheSort()
    {
        CardQuery<Card> q = query();
        q.setSort(CardQuery.Sort.ATTACK);
        q.setText("dark");
        q.toggleKind(CardQuery.Kind.MONSTER);
        q.toggleAttribute("DARK");
        q.setLevelRange(1, 4);
        assertFalse(q.isClear());

        q.clear();
        assertTrue(q.isClear());
        assertEquals(TRUNK.size(), q.apply(TRUNK).size());
        // Clearing a search should not also reorder what the player was reading.
        assertEquals(CardQuery.Sort.ATTACK, q.sort());
    }

    @Test
    void searchIsCaseInsensitive()
    {
        CardQuery<Card> q = query();
        q.setText("DARK MAGICIAN");
        assertEquals(List.of("Dark Magician"), names(q.apply(TRUNK)));
    }

    // ---- the filters the official editors offer ----

    private static final Card BLUE_EYES = new Card(89631139L, "Blue-Eyes White Dragon",
        "This legendary dragon is a powerful engine of destruction.",
        CardQuery.Kind.MONSTER, "LIGHT", "Dragon", 8, 3000, 2500, "Normal", java.util.Set.of());
    private static final Card TOON_MERMAID = new Card(65458948L, "Toon Mermaid",
        "A toon.", CardQuery.Kind.MONSTER, "WATER", "Aqua", 4, 1400, 1500,
        "Effect", java.util.Set.of("Toon"));
    private static final Card DARK_PALADIN = new Card(98502113L, "Dark Paladin",
        "A fusion.", CardQuery.Kind.MONSTER, "DARK", "Spellcaster", 8, 2900, 2400,
        "Fusion", java.util.Set.of());
    private static final Card MIRROR_FORCE = new Card(44095762L, "Mirror Force",
        "Destroy all attacking monsters.", CardQuery.Kind.TRAP, null, null, 0, 0, 0,
        "Normal", java.util.Set.of());

    private static List<Card> everything()
    {
        return List.of(BLUE_EYES, TOON_MERMAID, DARK_PALADIN, MIRROR_FORCE);
    }

    @Test
    void aSubTypeNarrowsToThatSubTypeAlone()
    {
        CardQuery<Card> query = new CardQuery<>(FACETS);
        query.toggleSubType("Fusion");
        assertEquals(List.of(DARK_PALADIN), query.apply(everything()));
    }

    @Test
    void aSubTypeNameSharedBetweenKindsStaysWithinTheChosenKind()
    {
        // "Normal" names a monster type AND a trap type. Choosing Normal alone
        // finds both, which is right; adding the Trap chip is how a player says
        // which one they meant.
        CardQuery<Card> query = new CardQuery<>(FACETS);
        query.toggleSubType("Normal");
        assertEquals(List.of(BLUE_EYES, MIRROR_FORCE), query.apply(everything()));
        query.toggleKind(CardQuery.Kind.TRAP);
        assertEquals(List.of(MIRROR_FORCE), query.apply(everything()));
    }

    @Test
    void anAbilityFindsTheCardsCarryingIt()
    {
        CardQuery<Card> query = new CardQuery<>(FACETS);
        query.toggleAbility("Toon");
        assertEquals(List.of(TOON_MERMAID), query.apply(everything()));
    }

    @Test
    void anAttackBandExcludesCardsWithNoAttackAtAll()
    {
        CardQuery<Card> query = new CardQuery<>(FACETS);
        query.setAttackRange(2000, -1);
        // A trap is not a zero-attack monster; it has nothing to compare, so a
        // band excludes it rather than sorting it to the bottom.
        assertEquals(List.of(BLUE_EYES, DARK_PALADIN), query.apply(everything()));
        assertFalse(query.apply(everything()).contains(MIRROR_FORCE));
    }

    @Test
    void aZeroFloorIsARealFilterRatherThanNoFilter()
    {
        CardQuery<Card> query = new CardQuery<>(FACETS);
        query.setAttackRange(0, -1);
        // "at least 0" is a filter a player can set, and it must still exclude
        // the trap. This is why the unbounded marker is -1 and not 0.
        assertEquals(List.of(BLUE_EYES, DARK_PALADIN, TOON_MERMAID), query.apply(everything()));
        assertFalse(query.isClear());
    }

    @Test
    void aDefenceBandIsBoundedAtBothEnds()
    {
        CardQuery<Card> query = new CardQuery<>(FACETS);
        query.setDefenceRange(1500, 2450);
        assertEquals(List.of(DARK_PALADIN, TOON_MERMAID), query.apply(everything()));
    }

    @Test
    void clearingPutsEveryNewFilterBack()
    {
        CardQuery<Card> query = new CardQuery<>(FACETS);
        query.toggleSubType("Fusion");
        query.toggleAbility("Toon");
        query.setAttackRange(100, 200);
        query.setDefenceRange(100, 200);
        assertFalse(query.isClear());
        query.clear();
        assertTrue(query.isClear(), "a filter left behind by clear is a filter a player cannot switch off");
        assertEquals(everything().size(), query.apply(everything()).size());
    }
}
