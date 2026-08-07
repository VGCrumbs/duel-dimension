package de.cas_ual_ty.dueldimension.duel.profile;

import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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

        assertEquals(1, profile.ownDecks().size(), "the copy is one of the player's decks");
        assertEquals(0, profile.savedRecipes().size(),
            "and a deck, not a recipe: publishing is something the player says");
        assertEquals(1, profile.structureDecks().size());
    }

    @Test
    void aDeckBecomesARecipeOnlyWhenItIsPublished()
    {
        DuelProfile profile = new DuelProfile();
        DeckList mine = new DeckList("Mine", DeckList.Origin.SAVED, MAIN, List.of(), List.of());
        profile.addDeck(mine);
        assertEquals(0, profile.savedRecipes().size(), "making a deck does not make a recipe");

        mine.publish(true);
        assertEquals(1, profile.savedRecipes().size());
        assertEquals(1, profile.ownDecks().size(), "and it is still a deck");

        // Through the save and back, because the flag is the whole feature.
        DuelProfile back = roundTrip(profile);
        assertEquals(1, back.savedRecipes().size(), "publication survives a reload");

        mine.publish(false);
        assertEquals(0, profile.savedRecipes().size(), "and can be withdrawn");
    }

    @Test
    void aDeckMayShareItsNameWithAGrantedRecipe()
    {
        // The bug: using the "Starter Deck: Joey" recipe made a deck of that
        // name, and every later edit to it was refused as a name already taken
        // -- by the recipe it was copied from. Own decks and granted recipes
        // are separate namespaces.
        DuelProfile profile = openJoey(new DuelProfile());
        assertNull(profile.savedNamed("Starter Deck: Joey"),
            "the granted recipe is not one of the player's decks");

        DeckList mine = new DeckList("Starter Deck: Joey", DeckList.Origin.SAVED,
            MAIN, List.of(), List.of());
        profile.addDeck(mine);
        assertSame(mine, profile.savedNamed("Starter Deck: Joey"));
        assertSame(mine, profile.deckNamed("Starter Deck: Joey"),
            "asked for either, the player's own deck is the one they can change");
        assertEquals(1, profile.structureDecks().size(), "and the recipe is untouched");

        // Removing the deck must not take the recipe with it.
        assertTrue(profile.removeDeck("Starter Deck: Joey"));
        assertEquals(1, profile.structureDecks().size());
        assertNotNull(profile.deckNamed("Starter Deck: Joey"));
    }

    @Test
    void aProfileRoundTripsThroughNbt()
    {
        DuelProfile profile = openJoey(new DuelProfile());
        profile.addDeck(new DeckList("Mine", DeckList.Origin.SAVED, MAIN, List.of(), List.of()));
        profile.setActiveDeck("Mine");

        DuelProfile back = roundTrip(profile);
        assertEquals(2, back.trunk().countOf(46986414));
        assertEquals(2, back.decks().size());
        assertEquals(1, back.structureDecks().size());
        assertEquals(1, back.ownDecks().size());
        assertEquals(0, back.savedRecipes().size(), "an unpublished deck is not a recipe");
        assertTrue(back.unlockedStructures().contains("sdj"),
            "without this a second copy would grant a duplicate deck after a reload");
        assertEquals("Mine", back.activeDeck());
    }

    /**
     * A profile through its Codec and back, which is exactly what storing one
     * does: the attachment persists with {@code DuelProfile.CODEC} and so does
     * a world save. NBT rather than JSON as the intermediate form, because NBT
     * is what it is written to on disk -- a round trip through a format the
     * game does not use would prove less.
     */
    private static DuelProfile roundTrip(DuelProfile profile)
    {
        Tag written = DuelProfile.CODEC
            .encodeStart(NbtOps.INSTANCE, profile)
            .getOrThrow(message -> new AssertionError("could not write the profile: " + message));
        return DuelProfile.CODEC
            .parse(NbtOps.INSTANCE, written)
            .getOrThrow(message -> new AssertionError("could not read the profile back: " + message));
    }
}
