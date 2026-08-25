package de.cas_ual_ty.dueldimension.duel.profile;

import de.cas_ual_ty.dueldimension.duel.match.Banlist;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rules the deck editor enforces while a player is building.
 * <p>
 * These are checked here rather than only in the GUI because the same rules
 * have to hold on the server when a deck is submitted: a client that stops
 * greying out the button must not be able to build an illegal deck anyway.
 */
class DeckLimitsTest
{
    private static final int DARK_HOLE = 53129443;
    private static final int MONSTER_REBORN = 83764719;
    private static final int FISSURE = 66788016;

    private static Trunk trunkOf(Map<Integer, Integer> cards)
    {
        Trunk trunk = new Trunk();
        cards.forEach(trunk::add);
        return trunk;
    }

    private static Banlist listWith(Map<Integer, Integer> limits)
    {
        return new Banlist("test", "Test List", limits);
    }

    /**
     * Registers a card the database can resolve, since the part rule asks it
     * what the card is. Removed again is unnecessary -- the list is keyed by
     * passcode and these ids are not real cards.
     */
    private static int registerMonster(long passcode,
        de.cas_ual_ty.dueldimension.card.properties.MonsterType monsterType)
    {
        de.cas_ual_ty.dueldimension.card.properties.LevelMonsterProperties card =
            new de.cas_ual_ty.dueldimension.card.properties.LevelMonsterProperties();
        card.id = passcode;
        card.name = "Test " + passcode;
        card.type = de.cas_ual_ty.dueldimension.card.properties.Type.MONSTER;
        card.monsterType = monsterType;
        card.species = "Warrior";
        card.attribute = "DARK";
        card.ability = "";
        de.cas_ual_ty.dueldimension.DdDatabase.PROPERTIES_LIST.add(card);
        return (int)passcode;
    }

    @Test
    void theExtraDeckRefusesACardThatDoesNotBelongInIt()
    {
        int mainDeckMonster = registerMonster(90000001L, null);
        DeckList deck = new DeckList("d", DeckList.Origin.SAVED);

        DeckLimits.Verdict verdict = DeckLimits.canAddToDraft(deck, DeckList.Part.EXTRA,
            mainDeckMonster, Banlist.none());

        assertFalse(verdict.allowed());
        assertTrue(verdict.reason().contains("Extra Deck"), verdict.reason());
    }

    @Test
    void theMainDeckRefusesAnExtraDeckMonster()
    {
        int fusion = registerMonster(90000002L,
            de.cas_ual_ty.dueldimension.card.properties.MonsterType.FUSION);
        DeckList deck = new DeckList("d", DeckList.Origin.SAVED);

        DeckLimits.Verdict verdict = DeckLimits.canAddToDraft(deck, DeckList.Part.MAIN,
            fusion, Banlist.none());

        assertFalse(verdict.allowed());
        assertTrue(verdict.reason().contains("Extra Deck"), verdict.reason());
    }

    @Test
    void eachKindIsAcceptedWhereItBelongs()
    {
        int mainDeckMonster = registerMonster(90000003L, null);
        int synchro = registerMonster(90000004L,
            de.cas_ual_ty.dueldimension.card.properties.MonsterType.SYNCHRO);
        DeckList deck = new DeckList("d", DeckList.Origin.SAVED);

        assertTrue(DeckLimits.canAddToDraft(deck, DeckList.Part.MAIN,
            mainDeckMonster, Banlist.none()).allowed());
        assertTrue(DeckLimits.canAddToDraft(deck, DeckList.Part.EXTRA,
            synchro, Banlist.none()).allowed());
    }

    /**
     * The Side Deck is swapped into both halves between games, so it holds
     * either kind. Constraining it would make a perfectly legal side board
     * impossible to build.
     */
    @Test
    void theSideDeckTakesEitherKind()
    {
        int mainDeckMonster = registerMonster(90000005L, null);
        int link = registerMonster(90000006L,
            de.cas_ual_ty.dueldimension.card.properties.MonsterType.LINK);
        DeckList deck = new DeckList("d", DeckList.Origin.SAVED);

        assertTrue(DeckLimits.canAddToDraft(deck, DeckList.Part.SIDE,
            mainDeckMonster, Banlist.none()).allowed());
        assertTrue(DeckLimits.canAddToDraft(deck, DeckList.Part.SIDE,
            link, Banlist.none()).allowed());
    }

    @Test
    void aFourthCopyIsRefusedEvenWithNoBanlistAndPlentyOwned()
    {
        Trunk trunk = trunkOf(Map.of(FISSURE, 9));
        DeckList deck = new DeckList("d", DeckList.Origin.SAVED);
        for(int i = 0; i < 3; i++)
        {
            assertTrue(DeckLimits.canAdd(deck, DeckList.Part.MAIN, FISSURE, trunk, Banlist.none()).allowed());
            deck.main().add(FISSURE);
        }
        DeckLimits.Verdict fourth =
            DeckLimits.canAdd(deck, DeckList.Part.MAIN, FISSURE, trunk, Banlist.none());
        assertFalse(fourth.allowed());
        assertTrue(fourth.reason().contains("Maximum 3"), fourth.reason());
    }

    @Test
    void copiesAreCountedAcrossMainExtraAndSideTogether()
    {
        Trunk trunk = trunkOf(Map.of(FISSURE, 9));
        DeckList deck = new DeckList("d", DeckList.Origin.SAVED);
        deck.main().add(FISSURE);
        deck.side().add(FISSURE);
        deck.side().add(FISSURE);
        // Three already exist across the deck, so a fourth is refused wherever
        // it is dropped -- including a part that is nowhere near full.
        assertFalse(DeckLimits.canAdd(deck, DeckList.Part.MAIN, FISSURE, trunk, Banlist.none()).allowed());
        assertFalse(DeckLimits.canAdd(deck, DeckList.Part.SIDE, FISSURE, trunk, Banlist.none()).allowed());
    }

    @Test
    void theBanlistTightensTheLimitAndSaysSo()
    {
        Trunk trunk = trunkOf(Map.of(MONSTER_REBORN, 3, DARK_HOLE, 3));
        Banlist list = listWith(Map.of(MONSTER_REBORN, 1, DARK_HOLE, 0));
        DeckList deck = new DeckList("d", DeckList.Origin.SAVED);

        assertTrue(DeckLimits.canAdd(deck, DeckList.Part.MAIN, MONSTER_REBORN, trunk, list).allowed());
        deck.main().add(MONSTER_REBORN);
        DeckLimits.Verdict second =
            DeckLimits.canAdd(deck, DeckList.Part.MAIN, MONSTER_REBORN, trunk, list);
        assertFalse(second.allowed());
        assertTrue(second.reason().contains("Limited to 1"), second.reason());

        DeckLimits.Verdict forbidden =
            DeckLimits.canAdd(deck, DeckList.Part.MAIN, DARK_HOLE, trunk, list);
        assertFalse(forbidden.allowed());
        assertTrue(forbidden.reason().contains("Forbidden"), forbidden.reason());
    }

    @Test
    void owningFewerCopiesDoesNotCapADeckDraft()
    {
        // Ownership is checked when the deck is used, not while it is planned.
        Trunk trunk = trunkOf(Map.of(FISSURE, 1));
        DeckList deck = new DeckList("d", DeckList.Origin.SAVED);
        assertTrue(DeckLimits.canAdd(deck, DeckList.Part.MAIN, FISSURE, trunk, Banlist.none()).allowed());
        deck.main().add(FISSURE);

        DeckLimits.Verdict second =
            DeckLimits.canAdd(deck, DeckList.Part.MAIN, FISSURE, trunk, Banlist.none());
        assertTrue(second.allowed());
    }

    @Test
    void aCardYouDoNotOwnCanBeAddedToADraft()
    {
        DeckLimits.Verdict verdict = DeckLimits.canAdd(new DeckList("d", DeckList.Origin.SAVED),
            DeckList.Part.MAIN, FISSURE, new Trunk(), Banlist.none());
        assertTrue(verdict.allowed());
    }

    @Test
    void theTrunkIsNeverSpentSoEveryDeckMayHoldTheSameCopies()
    {
        // The shared pool, stated as a test: owning three copies lets ALL of a
        // player's decks hold three each at once. If the trunk were a container
        // the second deck would be refused.
        Trunk trunk = trunkOf(Map.of(FISSURE, 3));
        DeckList first = new DeckList("one", DeckList.Origin.SAVED);
        DeckList second = new DeckList("two", DeckList.Origin.SAVED);
        for(int i = 0; i < 3; i++)
        {
            assertTrue(DeckLimits.canAdd(first, DeckList.Part.MAIN, FISSURE, trunk, Banlist.none()).allowed());
            first.main().add(FISSURE);
        }
        for(int i = 0; i < 3; i++)
        {
            assertTrue(DeckLimits.canAdd(second, DeckList.Part.MAIN, FISSURE, trunk, Banlist.none()).allowed(),
                "building a second deck must not be blocked by the first");
            second.main().add(FISSURE);
        }
        assertEquals(3, trunk.countOf(FISSURE), "building a deck must not consume the collection");
    }

    @Test
    void eachPartHasItsOwnCapacity()
    {
        Trunk trunk = new Trunk();
        for(int code = 1; code <= 100; code++)
        {
            trunk.add(code, 3);
        }
        DeckList deck = new DeckList("d", DeckList.Origin.SAVED);
        for(int i = 0; i < 15; i++)
        {
            assertTrue(DeckLimits.canAdd(deck, DeckList.Part.EXTRA, i + 1, trunk, Banlist.none()).allowed());
            deck.extra().add(i + 1);
        }
        DeckLimits.Verdict full = DeckLimits.canAdd(deck, DeckList.Part.EXTRA, 99, trunk, Banlist.none());
        assertFalse(full.allowed());
        assertTrue(full.reason().contains("Extra Deck is full"), full.reason());
        // The main deck is untouched by the extra deck being full.
        assertTrue(DeckLimits.canAdd(deck, DeckList.Part.MAIN, 99, trunk, Banlist.none()).allowed());
    }

    @Test
    void maxCopiesReportsTheTightestCeilingForTheTrunkPanel()
    {
        Banlist list = listWith(Map.of(MONSTER_REBORN, 1));
        assertEquals(1, DeckLimits.maxCopies(MONSTER_REBORN, trunkOf(Map.of(MONSTER_REBORN, 3)), list));
        assertEquals(2, DeckLimits.maxCopies(FISSURE, trunkOf(Map.of(FISSURE, 2)), list));
        assertEquals(3, DeckLimits.maxCopies(FISSURE, trunkOf(Map.of(FISSURE, 5)), list));
        assertEquals(0, DeckLimits.maxCopies(FISSURE, new Trunk(), list));
    }

    @Test
    void validationCatchesADeckThatOutgrewItsCollection()
    {
        // Decks are saved as codes, so a deck can outlive the copies that
        // justified it -- cards can be spent or traded away elsewhere.
        Trunk trunk = trunkOf(Map.of(FISSURE, 1));
        DeckList deck = new DeckList("d", DeckList.Origin.SAVED);
        for(int i = 0; i < 39; i++)
        {
            deck.main().add(1000 + i);
            trunk.add(1000 + i, 1);
        }
        deck.main().add(FISSURE);
        deck.main().add(FISSURE);
        java.util.List<String> problems = DeckLimits.validate(deck, trunk, Banlist.none());
        assertEquals(1, problems.size(), problems.toString());
        assertTrue(problems.get(0).contains("you own 1"), problems.get(0));
    }

    @Test
    void aDeckRoundTripsThroughNbtWithItsOrigin()
    {
        DeckList deck = new DeckList("Structure: Joey", DeckList.Origin.STRUCTURE);
        deck.main().add(FISSURE);
        deck.extra().add(DARK_HOLE);
        deck.side().add(MONSTER_REBORN);
        DeckList back = roundTrip(DeckList.CODEC, deck);
        assertEquals("Structure: Joey", back.name());
        assertEquals(DeckList.Origin.STRUCTURE, back.origin(),
            "origin is what splits Saved Recipes from Structure Decks, so it must survive");
        assertEquals(deck.main(), back.main());
        assertEquals(deck.extra(), back.extra());
        assertEquals(deck.side(), back.side());
    }

    @Test
    void aTrunkRoundTripsThroughNbt()
    {
        Trunk trunk = trunkOf(Map.of(FISSURE, 3, DARK_HOLE, 1));
        Trunk back = roundTrip(Trunk.CODEC, trunk);
        assertEquals(3, back.countOf(FISSURE));
        assertEquals(1, back.countOf(DARK_HOLE));
        assertEquals(2, back.distinctCards());
        assertEquals(4, back.totalCards());
    }

    /**
     * A value through its Codec and back, in NBT -- the form it is actually
     * stored in, so the trip proves what storing it would do rather than what
     * some other format would.
     */
    private static <T> T roundTrip(com.mojang.serialization.Codec<T> codec, T value)
    {
        Tag written = codec.encodeStart(NbtOps.INSTANCE, value)
            .getOrThrow(message -> new AssertionError("could not write: " + message));
        return codec.parse(NbtOps.INSTANCE, written)
            .getOrThrow(message -> new AssertionError("could not read back: " + message));
    }
}
