package de.cas_ual_ty.dueldimension.duel.profile;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What opening a structure deck actually grants.
 * <p>
 * The requirement is that it is not just cards: the deck itself must arrive
 * playable, and its recipe must survive the player pulling the deck apart. Both
 * are the same object here, so these check that one action produces all three.
 */
class DuelProfileTest
{
    private static final List<Integer> MAIN = List.of(46986414, 46986414, 41392891, 53129443);
    private static final List<Integer> EXTRA = List.of(24094653);
    private static final List<Integer> SIDE = List.of(4206964);

    private static DuelProfile openJoey(DuelProfile profile)
    {
        profile.unlockStructureDeck("sdj", "Starter Deck: Joey", MAIN, EXTRA, SIDE);
        return profile;
    }

    @Test
    void openingAStructureDeckGrantsCardsTheDeckAndTheRecipe()
    {
        DuelProfile profile = openJoey(new DuelProfile());

        // 1. the cards
        assertEquals(2, profile.trunk().countOf(46986414), "two copies were in the deck");
        assertEquals(1, profile.trunk().countOf(41392891));
        assertEquals(1, profile.trunk().countOf(24094653), "extra deck cards count too");
        assertEquals(1, profile.trunk().countOf(4206964), "side deck cards count too");

        // 2. the deck, playable as opened
        DeckList granted = profile.deckNamed("Starter Deck: Joey");
        assertNotNull(granted, "the deck itself must be granted, not just its cards");
        assertEquals(MAIN, granted.main());
        assertEquals(EXTRA, granted.extra());
        assertEquals(SIDE, granted.side());

        // 3. the recipe, which is the same object under its origin
        assertEquals(DeckList.Origin.STRUCTURE, granted.origin());
        assertTrue(granted.origin().isGranted());
        assertEquals(1, profile.structureDecks().size());
        assertTrue(profile.savedRecipes().isEmpty(), "a granted deck is not one of the player's builds");
        assertTrue(profile.unlockedStructures().contains("sdj"));
    }

    @Test
    void aSecondCopyGrantsMoreCardsButNotASecondDeck()
    {
        // Two copies of a product really are two sets of cards -- that is how a
        // player reaches three-of. But two identically named decks they cannot
        // tell apart is not what anyone wants.
        DuelProfile profile = new DuelProfile();
        assertTrue(openJoey(profile).unlockedStructures().contains("sdj"));
        boolean second = profile.unlockStructureDeck("sdj", "Starter Deck: Joey", MAIN, EXTRA, SIDE);

        assertFalse(second, "the second copy must not report granting the deck again");
        assertEquals(4, profile.trunk().countOf(46986414), "cards accumulate across copies");
        assertEquals(1, profile.structureDecks().size(), "but the deck list must not gain a duplicate");
    }

    @Test
    void theFirstDeckOpenedBecomesActiveSoAPlayerCanDuelImmediately()
    {
        DuelProfile profile = openJoey(new DuelProfile());
        assertEquals("Starter Deck: Joey", profile.activeDeck());

        // A later unlock must not steal the active slot from a chosen deck.
        profile.unlockStructureDeck("sdk", "Starter Deck: Kaiba", MAIN, List.of(), List.of());
        assertEquals("Starter Deck: Joey", profile.activeDeck());
    }

    @Test
    void aRecipeSurvivesTheDeckBeingPulledApart()
    {
        // The point of keeping the recipe: a player can gut the granted deck and
        // still rebuild it, or copy it as a starting point of their own.
        DuelProfile profile = openJoey(new DuelProfile());
        DeckList copy = profile.copyAsRecipe("Starter Deck: Joey", "My Joey");
        assertNotNull(copy);
        assertEquals(DeckList.Origin.SAVED, copy.origin(), "a copy is the player's own build");
        assertEquals(MAIN, copy.main());

        copy.main().clear();
        assertEquals(MAIN, profile.deckNamed("Starter Deck: Joey").main(),
            "editing the copy must not touch the recipe it came from");

        assertEquals(1, profile.savedRecipes().size());
        assertEquals(1, profile.structureDecks().size());
    }

    @Test
    void aProfileRoundTripsThroughNbt()
    {
        DuelProfile profile = openJoey(new DuelProfile());
        profile.addDeck(new DeckList("Mine", DeckList.Origin.SAVED, MAIN, List.of(), List.of()));
        profile.setActiveDeck("Mine");

        DuelProfile back = DuelProfile.load(profile.save());
        assertEquals(2, back.trunk().countOf(46986414));
        assertEquals(2, back.decks().size());
        assertEquals(1, back.structureDecks().size());
        assertEquals(1, back.savedRecipes().size());
        assertTrue(back.unlockedStructures().contains("sdj"),
            "without this a second copy would grant a duplicate deck after a reload");
        assertEquals("Mine", back.activeDeck());
    }
}
