package de.cas_ual_ty.dueldimension.duel.profile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Where a Destiny Card mark goes when the game is closed.
 *
 * <h2>What this is chasing</h2>
 * The mark reaches the server and is accepted -- the log says so, in the
 * player's own words: {@code Destiny flags for "Starter Deck: Yugi": 2 arrived,
 * 2 kept} -- and the file written at logout contains no {@code Destiny} key at
 * all. Everything between those two points is object copying, and
 * {@link DuelProfile#snapshot()} is the copy that persists.
 * <p>
 * A field can be on the class, in the codec, and still save as empty forever if
 * the snapshot forgets it. {@code DeckList.copy} says exactly that about the
 * artworks, which were lost the same way once.
 */
class DestinyPersistenceTest
{
    private static final int DARK_MAGICIAN = 46986414;
    private static final int SUMMONED_SKULL = 70781052;

    private static DuelProfile profileWith(DeckList.Origin origin)
    {
        DuelProfile profile = new DuelProfile();
        profile.addDeck(new DeckList("Starter Deck: Yugi", origin,
            List.of(DARK_MAGICIAN, SUMMONED_SKULL), List.of(), List.of()));
        return profile;
    }

    @Test
    @DisplayName("a mark survives the snapshot that gets persisted")
    void snapshotKeepsTheFlags()
    {
        DuelProfile profile = profileWith(DeckList.Origin.SAVED);
        profile.deckNamed("Starter Deck: Yugi").setDestiny(List.of(DARK_MAGICIAN));

        assertEquals(List.of(DARK_MAGICIAN),
            profile.deckNamed("Starter Deck: Yugi").destiny(),
            "the live profile lost the flag before anything was even copied");
        assertEquals(List.of(DARK_MAGICIAN),
            profile.snapshot().deckNamed("Starter Deck: Yugi").destiny(),
            "snapshot() is what gets written, and it dropped the flag");
    }

    /**
     * The same, on a GRANTED deck, which is what the report was actually about.
     * <p>
     * "Starter Deck: Yugi" is a deck the game handed out, not one the player
     * built, and granted decks are described as loadable but never edited in
     * place. If marks survive on a SAVED deck and not on this one, that
     * distinction is the bug.
     */
    @Test
    @DisplayName("a mark on a granted deck survives it too")
    void grantedDecksKeepTheirFlags()
    {
        DuelProfile profile = profileWith(DeckList.Origin.STARTER);
        profile.deckNamed("Starter Deck: Yugi").setDestiny(List.of(SUMMONED_SKULL));

        assertEquals(List.of(SUMMONED_SKULL),
            profile.snapshot().deckNamed("Starter Deck: Yugi").destiny(),
            "a granted deck's flags did not survive the snapshot");
    }
}
