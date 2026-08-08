package de.cas_ual_ty.dueldimension.ocg.deck;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The .ydk we write is the .ydk other clients read.
 * <p>
 * Checked by round-tripping through our own parser — which was written against
 * real files (the vendored Windbot decks) rather than against the writer — so a
 * change to either half that breaks the pairing fails here.
 */
class YdkRoundTripTest
{
    private static final YdkDeck DECK = new YdkDeck("Test",
        List.of(46986414, 46986414, 89631139),
        List.of(23995346),
        List.of(44095762, 44095762));

    @Test
    void whatWeWriteIsWhatWeRead()
    {
        YdkDeck back = YdkDeck.parse("Test", DECK.toYdkText());

        assertEquals(DECK.main(), back.main());
        assertEquals(DECK.extra(), back.extra());
        assertEquals(DECK.side(), back.side());
    }

    /**
     * The side marker is {@code !side}, not {@code #side}.
     * <p>
     * The inconsistency is the format's own. A file written with a hash is
     * read by other clients as more of the extra deck, so this is pinned
     * rather than left to whoever edits the writer next.
     */
    @Test
    void theSectionMarkersAreTheOnesOtherClientsExpect()
    {
        String text = DECK.toYdkText();

        assertTrue(text.contains("\n#main\n"), text);
        assertTrue(text.contains("\n#extra\n"), text);
        assertTrue(text.contains("\n!side\n"), text);
        assertTrue(text.startsWith("#created by "), text);
    }

    /** A copy is one line per copy, which is how every real file writes them. */
    @Test
    void copiesAreRepeatedLinesRatherThanCounts()
    {
        long blueEyes = DECK.toYdkText().lines()
            .filter(line -> line.equals("46986414")).count();

        assertEquals(2, blueEyes);
    }

    /**
     * An empty deck still writes all three sections, so a client reading it
     * finds the structure it expects instead of guessing where a section ended.
     */
    @Test
    void anEmptyDeckStillWritesItsSections()
    {
        YdkDeck empty = new YdkDeck("Empty", List.of(), List.of(), List.of());
        YdkDeck back = YdkDeck.parse("Empty", empty.toYdkText());

        assertTrue(back.main().isEmpty());
        assertTrue(back.extra().isEmpty());
        assertTrue(back.side().isEmpty());
    }
}
