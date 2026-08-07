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
    void aCardYouDoNotOwnAtAllIsRefused()
    {
        Trunk trunk = owning(DARK_MAGICIAN, 3);
        // The whole point: a client can put any card id in a packet.
        assertNotNull(DeckEdits.refusalFor(trunk, deckOf(List.of(BLUE_EYES)), Banlist.none()),
            "a deck may not contain a card the player has never owned");
    }

    @Test
    void moreCopiesThanYouOwnIsRefused()
    {
        Trunk trunk = owning(DARK_MAGICIAN, 1);
        assertNotNull(DeckEdits.refusalFor(trunk,
                deckOf(List.of(DARK_MAGICIAN, DARK_MAGICIAN)), Banlist.none()),
            "owning one copy does not allow playing two");
        assertNull(DeckEdits.refusalFor(trunk, deckOf(List.of(DARK_MAGICIAN)), Banlist.none()));
    }

    @Test
    void copiesAreCountedAcrossTheWholeDeckNotEachPart()
    {
        Trunk trunk = owning(DARK_MAGICIAN, 2);
        // Two in the main deck and one in the side is three copies of one card
        // from a collection of two. Counting parts separately would let a
        // player field more of a card than they have by spreading it out.
        DeckList spread = new DeckList("Spread", DeckList.Origin.SAVED,
            List.of(DARK_MAGICIAN, DARK_MAGICIAN), List.of(), List.of(DARK_MAGICIAN));
        assertNotNull(DeckEdits.refusalFor(trunk, spread, Banlist.none()));
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
        assertNotNull(DeckEdits.refusalFor(profile.trunk(), deckOf(List.of(BLUE_EYES)),
            Banlist.none()), "a starred card is still not an owned card");
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
}
