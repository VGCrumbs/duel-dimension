package de.cas_ual_ty.dueldimension.clientutil.hub;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the deck panel actually settles on, checked rather than reasoned about.
 * <p>
 * This exists because the reasoning kept being wrong. The grid's size was
 * argued from arithmetic done by hand -- how much room, how many rows, how big
 * a card -- and the answer disagreed with the screen more than once, in both
 * directions: a band of empty panel under the Side Deck one time, a card that
 * would not fit the next. Every input here is a number, so there is no reason
 * for any of it to be a matter of opinion.
 * <p>
 * The deck under test is the one from the reports: 49 main, 9 extra, 15 side.
 */
class DeckFitTest
{
    private static final float ASPECT = 0.6857F;
    private static final int GAP = 2;
    private static final int CARD_MIN = 10;
    private static final int CARD_MAX = 72;
    /** Three headings and the gap under each. */
    private static final int CHROME = (12 + 6) * 3;

    private static final int[] HELD = {49, 9, 15};
    private static final int[] CAPACITY = {60, 15, 15};

    private static DeckEditorScreen.DeckFit fit(int usableW, int viewH)
    {
        return DeckEditorScreen.deckFit(usableW, viewH, CHROME, ASPECT, GAP,
            CARD_MIN, CARD_MAX, HELD, CAPACITY);
    }

    /**
     * The reported window: 1282x752 at GUI scale 3, so 427x250 units, split
     * down the middle. That leaves the deck 179 usable and a 198-unit view.
     */
    @Test
    void theReportedWindowFillsItsPanel()
    {
        DeckEditorScreen.DeckFit fit = fit(179, 198);
        assertEquals(198, fit.content(), "should reach the Sort row exactly");
        assertTrue(fit.cardW() >= 11, "card was " + fit.cardW() + " wide");
    }

    /**
     * A part whose cards fill its last row exactly draws no empty row.
     * <p>
     * 48 cards across 12 columns is four full rows. A spare row used to be
     * added on top of that as somewhere to drop the next card, which is a band
     * of nothing inside the container -- and only for the column counts that
     * happen to divide the deck, so it appeared and vanished as cards were
     * added. Checked at every count rather than at twelve, because which counts
     * divide depends on how many cards are held.
     */
    @Test
    void aFullLastRowIsNotFollowedByAnEmptyOne()
    {
        // From one card: an EMPTY part keeps a row, because a container with no
        // height is nothing to drop the first card onto.
        for(int held = 1; held <= 60; held++)
        {
            for(int columns = 1; columns <= 20; columns++)
            {
                int rows = DeckEditorScreen.rowsFor(held, 60, columns);
                assertTrue(rows * columns - held < columns,
                    held + " cards in " + columns + " columns drew " + rows
                        + " rows, which is a whole empty one");
                assertTrue(rows * columns >= held || rows == (int)Math.ceil(60D / columns),
                    held + " cards in " + columns + " columns had only " + rows + " rows");
            }
        }
    }

    /** Whatever it picks, it must not overflow the panel it was fitted into. */
    @Test
    void nothingOverflows()
    {
        for(int usableW = 60; usableW <= 500; usableW += 7)
        {
            for(int viewH = 120; viewH <= 500; viewH += 11)
            {
                DeckEditorScreen.DeckFit fit = fit(usableW, viewH);
                // fits(), not a guess at it from the card size: the fallback
                // cram returns whatever the width holds, which is often above
                // the floor, so "bigger than minimum" does not mean "fitted".
                assertTrue(!fit.fits() || fit.content() <= viewH,
                    "claimed a fit that overflowed at " + usableW + "x" + viewH
                        + ": content " + fit.content() + " > " + viewH);
            }
        }
    }

    /**
     * And nothing else it could have chosen would have been better.
     * <p>
     * Brute force over every column count and every card width, scored the way
     * the screen scores: biggest card first, and between equals the one that
     * leaves least of the panel empty. This is the property the band under the
     * Side Deck actually violated -- not "no space is ever left", which cannot
     * hold when the card is limited by the panel's WIDTH and the spare height
     * has nowhere to go.
     */
    @Test
    void nothingBetterWasAvailable()
    {
        for(int usableW = 120; usableW <= 400; usableW += 7)
        {
            for(int viewH = 160; viewH <= 400; viewH += 11)
            {
                DeckEditorScreen.DeckFit fit = fit(usableW, viewH);
                if(!fit.fits())
                {
                    continue;
                }
                int bestW = 0;
                int bestFill = -1;
                for(int columns = 1; columns <= usableW; columns++)
                {
                    int byWidth = (usableW - (columns - 1) * GAP) / columns;
                    if(byWidth < CARD_MIN)
                    {
                        break;
                    }
                    int rows = 0;
                    for(int part = 0; part < HELD.length; part++)
                    {
                        rows += DeckEditorScreen.rowsFor(HELD[part], CAPACITY[part], columns);
                    }
                    for(int w = Math.min(CARD_MAX, byWidth); w >= CARD_MIN; w--)
                    {
                        int content = CHROME + rows * (Math.max(8, Math.round(w / ASPECT)) + GAP);
                        if(content > viewH)
                        {
                            continue;
                        }
                        if(w > bestW || (w == bestW && content > bestFill))
                        {
                            bestW = w;
                            bestFill = content;
                        }
                        break;
                    }
                }
                assertEquals(bestW, fit.cardW(),
                    "a better card existed at " + usableW + "x" + viewH);
                assertEquals(bestFill, fit.content(),
                    "a fuller layout existed at " + usableW + "x" + viewH);
            }
        }
    }

    /** A bigger panel never gives a smaller card. */
    @Test
    void moreRoomIsNeverWorse()
    {
        int previous = 0;
        for(int viewH = 160; viewH <= 420; viewH += 2)
        {
            int card = fit(260, viewH).cardW();
            assertTrue(card >= previous,
                "card shrank from " + previous + " to " + card + " at height " + viewH);
            previous = card;
        }
    }

    /** The columns must actually hold the cards they were counted for. */
    @Test
    void theColumnsFitTheWidth()
    {
        for(int usableW = 60; usableW <= 500; usableW += 3)
        {
            DeckEditorScreen.DeckFit fit = fit(usableW, 300);
            int used = fit.columns() * fit.cardW() + (fit.columns() - 1) * GAP;
            assertTrue(used <= usableW,
                "columns overran the width at " + usableW + ": used " + used);
        }
    }
}
