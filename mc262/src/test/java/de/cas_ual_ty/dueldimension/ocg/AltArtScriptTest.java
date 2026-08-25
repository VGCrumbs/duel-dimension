package de.cas_ual_ty.dueldimension.ocg;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The generated dressing chunk and the card/artwork pairing around it, both
 * checkable without the native core.
 */
class AltArtScriptTest
{
    @Test
    void aDeckNobodyDressedGeneratesNothing()
    {
        assertNull(AltArtScript.chunk(0, Map.of(), Map.of()),
            "an ordinary deck must not produce a single line of Lua");
        assertNull(AltArtScript.chunk(1, null, null));
    }

    @Test
    void theChunkNamesTheRightPlayerAndPile()
    {
        Map<Integer, Integer> deck = new LinkedHashMap<>();
        deck.put(39, 7);
        deck.put(38, 3);
        String chunk = AltArtScript.chunk(1, deck, Map.of());

        assertEquals("local a={[38]=3,[39]=7}\n"
            + "local g=Duel.GetFieldGroup(1,LOCATION_DECK,0)\n"
            + "local c=g:GetFirst()\n"
            + "while c do local v=a[c:GetSequence()] if v then c:Cover(v) end c=g:GetNext() end\n",
            chunk);
    }

    @Test
    void bothPilesAreDressedInOneChunk()
    {
        String chunk = AltArtScript.chunk(0, Map.of(5, 1), Map.of(2, 4));
        assertTrue(chunk.contains("LOCATION_DECK"), chunk);
        assertTrue(chunk.contains("LOCATION_EXTRA"), chunk);
        // Everything local, so a card script never meets a global of ours.
        assertTrue(chunk.lines().allMatch(line -> line.startsWith("local ") || line.startsWith("while ")),
            chunk);
    }

    /**
     * A deck's promised opening card is moved after the shuffle, and its
     * artwork has to move with it. If it does not, every chosen artwork from
     * that position on is one place out — and the deck is shuffled, so the
     * result looks random rather than wrong.
     */
    @Test
    void theGuaranteedCardTakesItsArtworkWithIt()
    {
        List<Integer> main = new ArrayList<>(List.of(10, 11, 12, 13, 14, 15, 16, 17));
        List<Integer> arts = new ArrayList<>(List.of(1, 2, 3, 4, 5, 6, 7, 8));

        HeadlessDuelRunner.placeGuaranteed(main, 12, arts);

        assertEquals(List.of(10, 11, 13, 14, 15, 12, 16, 17), main);
        assertEquals(List.of(1, 2, 4, 5, 6, 3, 7, 8), arts);
        for(int i = 0; i < main.size(); i++)
        {
            assertEquals(main.get(i) - 9, arts.get(i),
                "card " + main.get(i) + " lost its artwork at position " + i);
        }
    }

    @Test
    void anUndressedDeckStillMovesItsPromisedCard()
    {
        List<Integer> main = new ArrayList<>(List.of(10, 11, 12));
        HeadlessDuelRunner.placeGuaranteed(main, 10, null);
        assertEquals(List.of(11, 12, 10), main);
    }

    @Test
    void hasAlternateArtIsFalseForEveryOrdinaryDeck()
    {
        assertTrue(!new HeadlessDuelRunner.Deck(List.of(1, 2, 3), List.of()).hasAlternateArt());
        assertTrue(!new HeadlessDuelRunner.Deck(List.of(1, 2, 3), List.of())
            .wearing(List.of(0, 0, 0), List.of()).hasAlternateArt());
        assertTrue(new HeadlessDuelRunner.Deck(List.of(1, 2, 3), List.of())
            .wearing(List.of(0, 2, 0), List.of()).hasAlternateArt());
        // A promise must not undress the deck on its way through.
        assertTrue(new HeadlessDuelRunner.Deck(List.of(1, 2, 3), List.of())
            .wearing(List.of(0, 2, 0), List.of()).guaranteeing(2).hasAlternateArt());
    }
}
