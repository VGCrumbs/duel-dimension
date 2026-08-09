package de.cas_ual_ty.dueldimension.ocg;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Chaos Duel Disk's promise: a card that is drawn on the first turn.
 * <p>
 * Pinned by a test because the guarantee is a silent ordering property. If it
 * broke, the deck would still be legal, the duel would still play, and the only
 * symptom would be a card turning up a few turns later than it should — which
 * is exactly the kind of thing that survives a play session unnoticed.
 * <p>
 * The invariant it rests on is stated by {@code registerDecks}: cards are
 * registered back to front, so element 0 of the list ends up on TOP of the deck.
 * The opening hand is therefore elements 0 to 4, and element 5 is the very next
 * card drawn — the sixth card, which with {@code DUEL_1ST_TURN_DRAW} is drawn on
 * the player's first turn from either seat.
 */
class GuaranteedOpeningCardTest
{
    /** The Seal of Orichalcos. */
    private static final int SEAL = 48179391;

    private static List<Integer> deckOf(int size)
    {
        List<Integer> main = new ArrayList<>();
        for(int i = 0; i < size; i++)
        {
            main.add(1000 + i);
        }
        return main;
    }

    @Test
    void theGuaranteedCardIsSixthFromTheTop()
    {
        List<Integer> main = deckOf(40);
        main.set(31, SEAL);   // buried, where a shuffle would usually leave it

        HeadlessDuelRunner.placeGuaranteed(main, SEAL);

        assertEquals(SEAL, main.get(5),
            "the promised card must be the sixth from the top, so it is the first"
                + " card drawn after the opening hand");
    }

    /**
     * The opening hand is the first five, and the promise is explicitly NOT one
     * of them: it is the card drawn on turn one, not a sixth card dealt for free.
     */
    @Test
    void itIsNotDealtAsPartOfTheOpeningFive()
    {
        List<Integer> main = deckOf(40);
        main.set(20, SEAL);

        HeadlessDuelRunner.placeGuaranteed(main, SEAL);

        assertTrue(main.subList(0, 5).stream().noneMatch(code -> code == SEAL),
            "a card in the opening five would make the hand six cards, not five");
    }

    /** It moves a card. It must never add, drop or duplicate one. */
    @Test
    void theDeckKeepsExactlyTheCardsItHad()
    {
        List<Integer> main = deckOf(40);
        main.set(17, SEAL);
        List<Integer> before = new ArrayList<>(main);

        HeadlessDuelRunner.placeGuaranteed(main, SEAL);

        assertEquals(before.size(), main.size(), "deck size must not change");
        Collections.sort(before);
        List<Integer> after = new ArrayList<>(main);
        Collections.sort(after);
        assertEquals(before, after, "the deck must hold exactly the cards it held");
    }

    /** A deck without the card is handed back untouched; the disk conjures nothing. */
    @Test
    void aDeckWithoutTheCardIsUnchanged()
    {
        List<Integer> main = deckOf(40);
        List<Integer> before = new ArrayList<>(main);

        HeadlessDuelRunner.placeGuaranteed(main, SEAL);

        assertEquals(before, main, "promising a card the deck does not hold must do nothing");
    }

    /** Promising nothing is not the same as promising card 0. */
    @Test
    void noPromiseLeavesTheOrderAlone()
    {
        List<Integer> main = deckOf(40);
        List<Integer> before = new ArrayList<>(main);

        HeadlessDuelRunner.placeGuaranteed(main, 0);

        assertEquals(before, main, "0 means no promise, and must not reorder anything");
    }

    /**
     * A deck shorter than the draw depth still works.
     * <p>
     * Not reachable with a legal 40-card deck, but placeGuaranteed indexes into
     * the list and an out-of-range insert would be an exception in the middle of
     * starting a duel.
     */
    @Test
    void aDeckShorterThanTheDrawDepthDoesNotThrow()
    {
        for(int size = 1; size <= 6; size++)
        {
            List<Integer> main = deckOf(size);
            main.set(0, SEAL);

            HeadlessDuelRunner.placeGuaranteed(main, SEAL);

            assertEquals(size, main.size(), "size " + size + " must survive intact");
            assertEquals(SEAL, main.get(Math.min(5, size - 1)),
                "with fewer cards than the draw depth it goes as deep as it can");
        }
    }
}
