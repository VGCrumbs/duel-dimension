package de.cas_ual_ty.dueldimension.ocg.deck;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Destiny Card flags a deck carries, and the one property of the file
 * format that makes them safe to ship.
 */
class YdkDestinyTest
{
    private static YdkDeck deck(List<Integer> destiny)
    {
        return new YdkDeck("Test", List.of(1, 2, 3), List.of(4), List.of(5), destiny);
    }

    @Test
    @DisplayName("a deck with no flags writes exactly what it always did")
    void noFlagsNoSection()
    {
        // The whole point of omitting the section: turning the feature on must
        // not rewrite every deck file on disk.
        assertFalse(deck(List.of()).toYdkText().contains("destiny"));
        assertFalse(new YdkDeck("Test", List.of(1), List.of(), List.of())
            .toYdkText().contains("destiny"));
    }

    @Test
    @DisplayName("flags survive a round trip")
    void roundTrip()
    {
        YdkDeck written = deck(List.of(1, 3));
        YdkDeck read = YdkDeck.parse("Test", written.toYdkText());
        assertEquals(List.of(1, 3), read.destiny());
        assertTrue(read.isDestiny(1));
        assertTrue(read.isDestiny(3));
        assertFalse(read.isDestiny(2));
    }

    @Test
    @DisplayName("THE FORMAT RULE: the codes are on the header line, never under it")
    void codesShareTheHeaderLine()
    {
        // This is the property the whole format choice rests on. A reader that
        // does not know the word "destiny" -- EDOPro, any other .ydk tool, and
        // this parser before today -- treats the line as a comment and then
        // reads whatever follows into the section that was open. Bare codes
        // under an unrecognised header land in the side deck.
        //
        // So: every flag must be on the "#destiny" line itself, and no line
        // after it may be a bare passcode.
        String text = deck(List.of(11, 22)).toYdkText();
        String[] lines = text.split("\\R");
        int header = -1;
        for(int i = 0; i < lines.length; i++)
        {
            if(lines[i].startsWith("#destiny"))
            {
                header = i;
            }
        }
        assertTrue(header >= 0, "the section should be written");
        assertTrue(lines[header].contains("11") && lines[header].contains("22"),
            "both codes belong on the header line, not beneath it");
        for(int i = header + 1; i < lines.length; i++)
        {
            assertFalse(lines[i].trim().matches("\\d+"),
                "line " + i + " is a bare passcode after #destiny, which another reader "
                    + "would swallow into the side deck");
        }
    }

    @Test
    @DisplayName("a foreign deck that never heard of destiny cards still parses")
    void unknownSectionIsHarmless()
    {
        YdkDeck read = YdkDeck.parse("Test", "#created by somebody\n#main\n1\n2\n#extra\n4\n!side\n5\n");
        assertEquals(List.of(1, 2), read.main());
        assertEquals(List.of(5), read.side());
        assertTrue(read.destiny().isEmpty());
    }

    @Test
    @DisplayName("flagging is by card, so duplicates collapse")
    void deduplicated()
    {
        assertEquals(List.of(7), deck(List.of(7, 7, 7)).destiny());
    }

    @Test
    @DisplayName("withDestiny toggles, and returns the same deck when nothing changes")
    void toggle()
    {
        YdkDeck none = deck(List.of());
        assertSame(none, none.withDestiny(1, false), "no change should not copy");

        YdkDeck one = none.withDestiny(1, true);
        assertTrue(one.isDestiny(1));
        assertSame(one, one.withDestiny(1, true));

        assertFalse(one.withDestiny(1, false).isDestiny(1));
        // The rest of the deck is untouched by a flag change.
        assertEquals(none.main(), one.main());
    }

    @Test
    @DisplayName("a hand-edited line with rubbish in it does not refuse the deck")
    void tolerantOfJunk()
    {
        YdkDeck read = YdkDeck.parse("Test", "#main\n1\n#destiny 11, oops, 22\n");
        assertEquals(List.of(11, 22), read.destiny());
        assertEquals(List.of(1), read.main());
    }
}
