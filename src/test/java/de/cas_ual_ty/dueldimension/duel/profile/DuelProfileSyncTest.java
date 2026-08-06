package de.cas_ual_ty.dueldimension.duel.profile;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A profile survives the round trip it now makes on every change.
 * <p>
 * The collection is held on the server and sent to the client as NBT after
 * every edit, so {@code save} then {@code load} is not a save-file concern any
 * more — it is the wire format, travelling many times a session. Anything it
 * drops is a card or a deck a player watches disappear.
 */
class DuelProfileSyncTest
{
    private static DuelProfile populated()
    {
        DuelProfile profile = new DuelProfile();
        profile.unlockStarterDeck("yugi", "Starter Deck: Yugi",
            List.of(46986414, 46986414, 89631139), List.of(), List.of(70781052));
        profile.trunk().add(55144522, 2);
        profile.addDeck(new DeckList("Burn", DeckList.Origin.SAVED,
            List.of(55144522, 55144522), List.of(), List.of()));
        profile.setActiveDeck("Burn");
        return profile;
    }

    @Test
    void everythingSurvivesTheRoundTrip()
    {
        DuelProfile before = populated();
        DuelProfile after = DuelProfile.load(before.save());

        assertEquals(before.trunk().totalCards(), after.trunk().totalCards(),
            "a card lost in transit is a card the player watches vanish");
        assertEquals(before.trunk().distinctCards(), after.trunk().distinctCards());
        assertEquals(before.decks().size(), after.decks().size());
        assertEquals("Burn", after.activeDeck(), "the chosen deck decides what a duel is played with");
        assertTrue(after.unlockedStructures().contains("yugi"));
    }

    @Test
    void copiesAreCountedNotJustPresence()
    {
        DuelProfile after = DuelProfile.load(populated().save());
        // Two of a card and one of a card are different decks, so a round trip
        // that kept only "owned" would silently rewrite what is legal.
        assertEquals(2, after.trunk().countOf(55144522));
        assertEquals(2, after.trunk().countOf(46986414), "the starter deck ran two of these");
    }

    @Test
    void aGrantedDeckStaysGrantedAcrossTheWire()
    {
        DuelProfile after = DuelProfile.load(populated().save());
        DeckList starter = after.deckNamed("Starter Deck: Yugi");
        assertNotNull(starter);
        // Origin is what stops a granted deck being edited or deleted. If it
        // did not survive, the server would let a player rewrite the record of
        // what they opened.
        assertTrue(starter.origin().isGranted());
        assertEquals(DeckList.Origin.SAVED, after.deckNamed("Burn").origin());
    }

    @Test
    void aSecondCopyOfAStructureDeckGivesCardsButNotASecondDeck()
    {
        DuelProfile profile = new DuelProfile();
        profile.unlockStructureDeck("dragons", "Dragons", List.of(89631139), List.of(), List.of());
        profile.unlockStructureDeck("dragons", "Dragons", List.of(89631139), List.of(), List.of());

        assertEquals(2, profile.trunk().countOf(89631139),
            "a second box really is a second set of cards");
        assertEquals(1, profile.decks().size(),
            "two decks with the same name could not be told apart");
    }

    @Test
    void anEmptyProfileLoadsRatherThanFailing()
    {
        // A player joining for the first time has no tag at all, and a server
        // that threw here would refuse them the game.
        DuelProfile empty = DuelProfile.load(new CompoundTag());
        assertEquals(0, empty.trunk().totalCards());
        assertTrue(empty.decks().isEmpty());
        assertEquals("", empty.activeDeck());
        assertNull(empty.deckNamed("anything"));
    }

    @Test
    void favouritesSurviveTheRoundTrip()
    {
        DuelProfile before = populated();
        before.toggleFavourite(55144522);
        before.toggleFavourite(46986414);

        DuelProfile after = DuelProfile.load(before.save());
        assertTrue(after.isFavourite(55144522));
        assertTrue(after.isFavourite(46986414));
        assertEquals(2, after.favourites().size());
    }

    @Test
    void starringTwiceUnstars()
    {
        DuelProfile profile = populated();
        assertTrue(profile.toggleFavourite(55144522), "the first press stars it");
        assertFalse(profile.toggleFavourite(55144522), "the second press takes it back off");
        assertTrue(profile.favourites().isEmpty());
    }

    @Test
    void aProfileWithNoFavouritesLoadsCleanly()
    {
        // The tag predates the field, so an old save has no Favourites entry
        // at all and must not come back with a phantom one.
        DuelProfile after = DuelProfile.load(new DuelProfile().save());
        assertTrue(after.favourites().isEmpty());
        assertFalse(after.isFavourite(46986414));
    }
}
