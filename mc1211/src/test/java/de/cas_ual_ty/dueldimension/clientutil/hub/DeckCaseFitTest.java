package de.cas_ual_ty.dueldimension.clientutil.hub;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The deck case on a hub tile: as large as the tile allows, still its own shape.
 * <p>
 * What this replaced was {@code 0.32 * width} by {@code 0.49 * height} -- two
 * unrelated fractions of two different dimensions, which makes the case's ratio
 * a function of the TILE's ratio rather than of the artwork. At the hub's own
 * tile that worked out square against a source that is 422x512, so every case
 * was drawn about a fifth too wide, and floors of 18 and 24 left it stranded at
 * a fixed size while the tile shrank around it.
 * <p>
 * Both halves are swept here, because either alone would have passed: a case
 * can be the right shape and far too small, or fill its tile and be stretched.
 */
class DeckCaseFitTest
{
    private static final int NAME_BAND = 13;
    private static final int PAD = 3;

    @Test
    void theCaseKeepsTheArtworksShape()
    {
        for(int h = 30; h <= 200; h += 2)
        {
            for(int w = 30; w <= 300; w += 3)
            {
                HubWidgets.CaseFit fit = HubWidgets.fitDeckCase(w, h, NAME_BAND, PAD);
                if(fit.h() < 8)
                {
                    // Rounding dominates at a few units and nobody reads a
                    // silhouette that small for its proportions.
                    continue;
                }
                float ratio = fit.w() / (float)fit.h();
                assertTrue(Math.abs(ratio - HubWidgets.DECK_BOX_ASPECT) < 0.05F,
                    "case was stretched to " + ratio + " on a " + w + "x" + h + " tile");
            }
        }
    }

    @Test
    void theCaseStaysInsideTheTileAndClearsTheName()
    {
        for(int h = 30; h <= 200; h += 2)
        {
            for(int w = 30; w <= 300; w += 3)
            {
                HubWidgets.CaseFit fit = HubWidgets.fitDeckCase(w, h, NAME_BAND, PAD);
                assertTrue(fit.x() >= 0 && fit.x() + fit.w() <= w,
                    "case left the tile sideways on " + w + "x" + h);
                assertTrue(fit.y() >= 0, "case started above the tile on " + w + "x" + h);
                assertTrue(fit.y() + fit.h() <= h - NAME_BAND,
                    "case ran into the name band on " + w + "x" + h
                        + ": case ends " + (fit.y() + fit.h())
                        + ", name starts " + (h - NAME_BAND));
            }
        }
    }

    /**
     * The fit half: a case has to actually USE the room it is given.
     * <p>
     * The old sizing failed this long before it failed the shape check -- on
     * the hub's roughly 86x58 tile it drew 28x28 into a space 39 units tall.
     */
    @Test
    void theCaseFillsTheRoomItIsGiven()
    {
        for(int h = 40; h <= 200; h += 2)
        {
            for(int w = 40; w <= 300; w += 3)
            {
                HubWidgets.CaseFit fit = HubWidgets.fitDeckCase(w, h, NAME_BAND, PAD);
                int space = h - NAME_BAND - PAD * 2;
                boolean fillsHeight = fit.h() >= space - 1;
                boolean widthBound = fit.w() >= w - PAD * 2 - 1;
                assertTrue(fillsHeight || widthBound,
                    "case used " + fit.w() + "x" + fit.h() + " of a " + w + "x" + h
                        + " tile with " + space + " units of room");
            }
        }
    }

    /** And it beats what it replaced on the tile that prompted the change. */
    @Test
    void itIsBiggerAndTruerThanTheOldSizingOnTheHubsTile()
    {
        int w = 86;
        int h = 58;
        int oldW = Math.max(18, Math.round(w * 0.32F));
        int oldH = Math.max(24, Math.round(h * 0.49F));
        HubWidgets.CaseFit fit = HubWidgets.fitDeckCase(w, h, NAME_BAND, PAD);
        assertTrue(fit.h() > oldH, "no taller than the old " + oldH);
        assertTrue(fit.w() * fit.h() > oldW * oldH, "no larger than the old case");
        float oldRatio = oldW / (float)oldH;
        float newRatio = fit.w() / (float)fit.h();
        assertTrue(Math.abs(newRatio - HubWidgets.DECK_BOX_ASPECT)
            < Math.abs(oldRatio - HubWidgets.DECK_BOX_ASPECT),
            "no closer to the artwork's shape than the old " + oldRatio);
    }
}
