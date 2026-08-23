package de.cas_ual_ty.dueldimension.clientutil.hub;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That the billboard editor's button strips fit inside its panel.
 * <p>
 * There is no scrolling and no clipping on that screen, so a strip that does not
 * fit is not awkward to use — it is drawn off the side of the screen and cannot
 * be used at all. That is what happened when a fourth tab was added to an enum
 * while the tab strip went on dividing the panel by three: it compiled, it threw
 * nothing, and the two tabs you could see looked perfectly normal.
 * <p>
 * So the assertion is on the INVARIANT rather than on the number. Pinning
 * "four tabs are 53 wide" would need updating the moment a fifth arrived, which
 * is precisely the maintenance step that failed last time.
 */
class BillboardEditorLayoutTest
{
    private static final int GAP = 2;

    @Test
    void everyStripFitsInsideThePanel()
    {
        int full = BillboardEditorScreen.content();
        for(int count = 1; count <= 12; count++)
        {
            int each = BillboardEditorScreen.share(full, GAP, count);
            // Where the last button's right edge lands: count buttons and the
            // gaps between them, which is one fewer gap than buttons.
            int used = count * each + GAP * (count - 1);
            assertTrue(used <= full,
                count + " buttons of " + each + " need " + used + " of " + full);
            assertTrue(each > 0, count + " buttons came out " + each + " wide");
        }
    }

    @Test
    void theTabStripFitsHoweverManyTabsThereAre()
    {
        // The real case, read off the real enum, so adding a tab makes this
        // test speak about the new count rather than about the old one.
        int full = BillboardEditorScreen.content();
        int tabs = BillboardEditorScreen.Tab.values().length;
        int each = BillboardEditorScreen.share(full, GAP, tabs);
        assertTrue(tabs * each + GAP * (tabs - 1) <= full,
            tabs + " tabs of " + each + " do not fit in " + full);

        // And wide enough for the widest label plus the brackets that mark the
        // live one. Six characters at six pixels, plus two brackets at four --
        // "[Frames]" is the longest, at 44.
        assertTrue(each >= 44, tabs + " tabs leave only " + each + " for a label");
    }

    @Test
    void theOldThreeWayFormulaWouldNotHaveFitFourTabs()
    {
        // The regression itself, stated once: dividing by a literal three while
        // there are four tabs overflows the panel by 72 units. Kept so that the
        // test above is understood as guarding something that really happened.
        int full = BillboardEditorScreen.content();
        int wrong = BillboardEditorScreen.share(full, GAP, 3);
        assertTrue(4 * wrong + GAP * 3 > full,
            "four tabs at the three-tab width would have fitted after all");
        assertEquals(218, full);
        assertEquals(71, wrong);
    }

    @Test
    void aStripOfNothingDoesNotDivideByZero()
    {
        assertEquals(100, BillboardEditorScreen.share(100, GAP, 0));
    }
}
