package de.cas_ual_ty.dueldimension.duel.profile;

import de.cas_ual_ty.dueldimension.duel.match.Banlist;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The server's answer to "may I keep this deck?".
 * <p>
 * This is the check that did not exist. The editor enforced these rules, but
 * the editor runs on the client, and a client is the one thing in a multiplayer
 * game that cannot be trusted to enforce a rule against its own player. Each
 * case below is a deck a modified client could ask the server to store.
 */
class DeckEditsTest
{
    private static final int DARK_MAGICIAN = 46986414;
    private static final int BLUE_EYES = 89631139;

    private static Trunk owning(int passcode, int copies)
    {
        Trunk trunk = new Trunk();
        trunk.add(passcode, copies);
        return trunk;
    }

    private static DeckList deckOf(List<Integer> main)
    {
        return new DeckList("Test", DeckList.Origin.SAVED, main, List.of(), List.of());
    }

    @Test
    void aDeckOfCardsYouOwnIsKept()
    {
        Trunk trunk = owning(DARK_MAGICIAN, 3);
        assertNull(DeckEdits.refusalFor(trunk,
            deckOf(List.of(DARK_MAGICIAN, DARK_MAGICIAN, DARK_MAGICIAN)), Banlist.none()));
    }

    @Test
    void aCardYouDoNotOwnAtAllMayBeSavedInADraft()
    {
        Trunk trunk = owning(DARK_MAGICIAN, 3);
        assertNull(DeckEdits.refusalFor(trunk, deckOf(List.of(BLUE_EYES)), Banlist.none()));
    }

    @Test
    void moreCopiesThanYouOwnMayBeSavedButAreNotDuelReady()
    {
        Trunk trunk = owning(DARK_MAGICIAN, 1);
        assertNull(DeckEdits.refusalFor(trunk,
            deckOf(List.of(DARK_MAGICIAN, DARK_MAGICIAN)), Banlist.none()));
        assertNull(DeckEdits.refusalFor(trunk, deckOf(List.of(DARK_MAGICIAN)), Banlist.none()));
    }

    @Test
    void ownershipValidationCountsCopiesAcrossTheWholeDeck()
    {
        Trunk trunk = owning(DARK_MAGICIAN, 2);
        // Two in the main deck and one in the side is three copies of one card
        // from a collection of two. Counting parts separately would let a
        // player field more of a card than they have by spreading it out.
        DeckList spread = new DeckList("Spread", DeckList.Origin.SAVED,
            List.of(DARK_MAGICIAN, DARK_MAGICIAN), List.of(), List.of(DARK_MAGICIAN));
        assertFalse(DeckEdits.problemsUnder(spread, trunk, Banlist.none()).isEmpty());
    }

    @Test
    void anEmptyDeckIsAllowed()
    {
        // Saving an unfinished deck is not cheating; building one is a process.
        // Whether it can be duelled with is a separate question, asked later.
        assertNull(DeckEdits.refusalFor(new Trunk(), deckOf(List.of()), Banlist.none()));
    }

    @Test
    void theThreeCopyCeilingStillApplies()
    {
        // Owning six of a card does not make a four-of legal.
        Trunk trunk = owning(DARK_MAGICIAN, 6);
        assertNotNull(DeckEdits.refusalFor(trunk, deckOf(
            List.of(DARK_MAGICIAN, DARK_MAGICIAN, DARK_MAGICIAN, DARK_MAGICIAN)), Banlist.none()));
    }

    // ---- free mode ----

    // ---- favourites ----

    @Test
    void aCardYouDoNotOwnMayStillBeStarred()
    {
        // The bug this covers: the card info page is reached by following
        // related cards, which are the cards a player has NOT got yet. Refusing
        // to star those made the star on that page appear to do nothing -- it
        // lit up on the client and the next sync put it back.
        DuelProfile profile = new DuelProfile();
        assertNull(DeckEdits.toggleFavourite(profile, BLUE_EYES),
            "starring a card you do not own is allowed");
        assertTrue(profile.isFavourite(BLUE_EYES));

        assertNull(DeckEdits.toggleFavourite(profile, BLUE_EYES), "and unstarring it");
        assertFalse(profile.isFavourite(BLUE_EYES));
    }

    @Test
    void aStarredCardIsNotAlsoGranted()
    {
        // Starring is a wishlist, not a purchase.
        DuelProfile profile = new DuelProfile();
        DeckEdits.toggleFavourite(profile, BLUE_EYES);
        assertFalse(profile.trunk().has(BLUE_EYES));
        assertFalse(DeckEdits.problemsUnder(deckOf(List.of(BLUE_EYES)), profile.trunk(),
            Banlist.none()).isEmpty(), "a starred card is still not an owned card");
    }

    @Test
    void theFavouriteListIsBounded()
    {
        // Ownership used to bound what a client could make the server store.
        // Nothing else did, so the cap replaces it.
        DuelProfile profile = new DuelProfile();
        for(int i = 0; i < DeckEdits.MAX_FAVOURITES; i++)
        {
            assertNull(DeckEdits.toggleFavourite(profile, BLUE_EYES + i));
        }
        assertNotNull(DeckEdits.toggleFavourite(profile, 1), "past the cap it is refused");
        // Taking one back off is always allowed, cap or no cap.
        assertNull(DeckEdits.toggleFavourite(profile, BLUE_EYES));
        assertNull(DeckEdits.toggleFavourite(profile, 1));
    }

    @Test
    void freeModeAllowsCardsNobodyOwns()
    {
        Trunk empty = new Trunk();
        assertNull(DeckEdits.refusalFor(empty,
            deckOf(List.of(BLUE_EYES, BLUE_EYES, BLUE_EYES)), Banlist.none(), true),
            "free mode is the whole point: build with anything");
    }

    @Test
    void freeModeStillObeysTheBanlist()
    {
        // Free mode widens what a player HAS, not what the rules allow. A
        // four-of is illegal however generous the collection is.
        Trunk empty = new Trunk();
        assertNotNull(DeckEdits.refusalFor(empty, deckOf(
            List.of(BLUE_EYES, BLUE_EYES, BLUE_EYES, BLUE_EYES)), Banlist.none(), true));
    }

    @Test
    void turningFreeModeOffMakesTheSameDeckUnusableWithoutChangingIt()
    {
        // A legal-SIZE deck of cards nobody owns. Size is not what free mode
        // relaxes -- a two-card deck is illegal either way -- so the deck has
        // to clear the forty-card minimum for ownership to be the only thing
        // left to fail on.
        List<Integer> main = new java.util.ArrayList<>();
        for(int i = 0; i < 40; i++)
        {
            main.add(BLUE_EYES + i);
        }
        Trunk empty = new Trunk();
        DeckList built = deckOf(main);

        assertTrue(DeckEdits.problemsUnder(built, empty, Banlist.none(), true).isEmpty(),
            "free mode: a deck of cards nobody owns is playable");

        List<String> refused = DeckEdits.problemsUnder(built, empty, Banlist.none(), false);
        assertFalse(refused.isEmpty(), "free mode off: the same deck is refused");
        assertTrue(refused.get(0).contains("own"), "and refused for OWNERSHIP: " + refused.get(0));
        assertEquals(40, built.main().size(), "the deck itself must not be altered");

        // Back on, and it plays again with no repair needed.
        assertTrue(DeckEdits.problemsUnder(built, empty, Banlist.none(), true).isEmpty());
    }

    // ---- the artwork a new copy is born wearing ----

    /**
     * Registers a card the database can resolve, with as many artworks as
     * asked for. Never removed again: the list is keyed by passcode and these
     * ids are not real cards.
     */
    private static int registerCard(long passcode, int artworks)
    {
        de.cas_ual_ty.dueldimension.card.properties.LevelMonsterProperties card =
            new de.cas_ual_ty.dueldimension.card.properties.LevelMonsterProperties();
        card.id = passcode;
        card.name = "Test " + passcode;
        card.type = de.cas_ual_ty.dueldimension.card.properties.Type.MONSTER;
        card.species = "Warrior";
        card.attribute = "DARK";
        card.ability = "";
        card.images = new String[] { "printed" };
        if(artworks > 1)
        {
            String[] alternates = new String[artworks - 1];
            java.util.Arrays.fill(alternates, "alternate");
            card.addArtwork(alternates);
        }
        de.cas_ual_ty.dueldimension.DdDatabase.PROPERTIES_LIST.add(card);
        return (int)passcode;
    }

    /**
     * The worked example, in miniature: own the MVP1 printing and that copy
     * wears its artwork.
     */
    @Test
    void aNewCopyWearsThePrintingYouOwn()
    {
        int obelisk = registerCard(91000001L, 3);
        Trunk trunk = new Trunk();
        trunk.add(obelisk, "Ultra Rare", 2, 1);

        assertEquals(2, DeckEdits.artForNewCopy(trunk, deckOf(List.of()), obelisk));
    }

    /**
     * Fanciest first, one copy at a time. A player holding a BP01 and an MVP1
     * Obelisk who runs two sees artwork 2 and artwork 1 -- the pair of cards
     * they actually own -- and a third copy falls back to the printed art.
     */
    @Test
    void eachCopyGetsTheNextPrintingDown()
    {
        int obelisk = registerCard(91000002L, 3);
        Trunk trunk = new Trunk();
        trunk.add(obelisk, "Ultra Rare", 2, 1);
        trunk.add(obelisk, "Rare", 1, 1);

        DeckList deck = deckOf(List.of());
        assertEquals(2, DeckEdits.artForNewCopy(trunk, deck, obelisk));
        deck.main().add(obelisk);
        assertEquals(1, DeckEdits.artForNewCopy(trunk, deck, obelisk));
        deck.main().add(obelisk);
        assertEquals(0, DeckEdits.artForNewCopy(trunk, deck, obelisk),
            "a third copy is one they do not own, so it is the printed art");
    }

    /** Copies are counted across the whole deck, as the copy limit is. */
    @Test
    void aCopyInTheSideDeckStillCountsAsOne()
    {
        int obelisk = registerCard(91000003L, 3);
        Trunk trunk = new Trunk();
        trunk.add(obelisk, "Ultra Rare", 2, 1);
        trunk.add(obelisk, "Rare", 1, 1);

        DeckList deck = new DeckList("Spread", DeckList.Origin.SAVED,
            List.of(), List.of(), List.of(obelisk));
        assertEquals(1, DeckEdits.artForNewCopy(trunk, deck, obelisk),
            "the side deck already holds the fanciest copy");
    }

    /**
     * The cost for the other 13,740 cards. A card with one artwork is answered
     * without the collection ever being asked.
     */
    @Test
    void aCardWithOneArtworkIsAlwaysThePrintedArt()
    {
        int plain = registerCard(91000004L, 1);
        Trunk trunk = new Trunk();
        trunk.add(plain, "Ultra Rare", 2, 1);

        assertEquals(0, DeckEdits.artForNewCopy(trunk, deckOf(List.of()), plain),
            "the collection says 2 and the card has no 2; the card is the authority");
    }

    /**
     * An artwork this build's database does not have folds back to the printed
     * art, the same way a value off the wire does. A set file can name an
     * artwork an alt_art folder no longer provides.
     */
    @Test
    void anArtworkTheCardDoesNotHaveFoldsToThePrintedArt()
    {
        int obelisk = registerCard(91000005L, 2);
        Trunk trunk = new Trunk();
        trunk.add(obelisk, "Ultra Rare", 7, 1);

        assertEquals(0, DeckEdits.artForNewCopy(trunk, deckOf(List.of()), obelisk));
    }

    /** A card nobody owns, and a card the database has never heard of. */
    @Test
    void nothingOwnedMeansThePrintedArt()
    {
        int obelisk = registerCard(91000006L, 3);
        assertEquals(0, DeckEdits.artForNewCopy(new Trunk(), deckOf(List.of()), obelisk));
        assertEquals(0, DeckEdits.artForNewCopy(new Trunk(), deckOf(List.of()), 91009999));
    }

    /**
     * The default is a creation-time value and never a render-time fallback,
     * which is how a deliberate artwork 0 is told from an undecided one: what
     * the picker wrote survives canonicalArts untouched, because nothing asks
     * this question about a position that already exists.
     */
    @Test
    void aDeliberateChoiceIsNotOverwrittenByTheDefault()
    {
        int obelisk = registerCard(91000007L, 3);
        Trunk trunk = new Trunk();
        trunk.add(obelisk, "Ultra Rare", 2, 1);

        // Two copies, the first dressed by the default and the second dressed
        // by hand back down to the printed art.
        List<Integer> cards = List.of(obelisk, obelisk);
        List<Integer> arts = List.of(2, 0);

        assertEquals(List.of(2), DeckEdits.canonicalArts(cards, arts),
            "the chosen 0 is stored as a trailing absence, which reads back as 0");
        assertEquals(2, DeckEdits.artForNewCopy(trunk, deckOf(List.of()), obelisk),
            "and the default is unchanged for the next copy that IS created");
    }
}
