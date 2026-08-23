package de.cas_ual_ty.dueldimension.clientutil.hub;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A deck case must never be drawn over its own name.
 * <p>
 * This is the bug this test exists for, and it was only visible at SOME window
 * sizes. The tile's contents were four independent expressions -- a floor of 58
 * on the art, and offsets of 34 and 17 measured up from the bottom -- which
 * agreed near the authored 112 and collided below about 95. Getting there took
 * a high-resolution display: a larger automatic GUI scale relaxed the tile's
 * physical-pixel minimum, two rows were fitted where one belonged, and every
 * tile in the shop landed in the colliding band. Low resolutions were fine,
 * which is exactly why nobody caught it by looking.
 * <p>
 * So the property is swept rather than sampled. One height would have passed
 * before the fix.
 */
class DeckBoxTileLayoutTest
{
    /** Minecraft's default. Varied below, because a resource pack may not be. */
    private static final int LINE_HEIGHT = 9;

    @Test
    void artNeverReachesTheName()
    {
        for(int tileH = 40; tileH <= 200; tileH++)
        {
            for(int tileW = 40; tileW <= 200; tileW += 4)
            {
                DeckBoxShopScreen.TileLayout tile =
                    DeckBoxShopScreen.tileLayout(tileW, tileH, LINE_HEIGHT);
                assertTrue(tile.artBottom() <= tile.nameY(),
                    "art ran into the name at " + tileW + "x" + tileH
                        + ": art ends " + tile.artBottom() + ", name starts " + tile.nameY());
            }
        }
    }

    @Test
    void theTwoLinesDoNotOverlapEachOtherAndStayInTheTile()
    {
        for(int tileH = 40; tileH <= 200; tileH++)
        {
            DeckBoxShopScreen.TileLayout tile =
                DeckBoxShopScreen.tileLayout(118, tileH, LINE_HEIGHT);
            assertTrue(tile.priceY() >= tile.nameY() + LINE_HEIGHT,
                "price sat on the name at height " + tileH);
            assertTrue(tile.priceY() + LINE_HEIGHT <= tileH,
                "price fell out of the bottom at height " + tileH);
        }
    }

    @Test
    void artStaysInsideTheTile()
    {
        // The case is sized from the tile's HEIGHT, so a narrow column is the
        // case that pushes it out sideways. Both bounds, because the clamp that
        // fixes the width also has to leave the height inside.
        for(int tileH = 40; tileH <= 200; tileH += 3)
        {
            for(int tileW = 20; tileW <= 200; tileW += 3)
            {
                DeckBoxShopScreen.TileLayout tile =
                    DeckBoxShopScreen.tileLayout(tileW, tileH, LINE_HEIGHT);
                assertTrue(tile.artX() >= 0 && tile.artX() + tile.artW() <= tileW,
                    "art left the tile sideways at " + tileW + "x" + tileH);
                assertTrue(tile.artY() >= 0 && tile.artBottom() <= tileH,
                    "art left the tile vertically at " + tileW + "x" + tileH);
            }
        }
    }

    @Test
    void aTallerFontTakesRoomFromTheArtRatherThanFromTheName()
    {
        // The text block is asked of the font, so a resource pack with a taller
        // one gets a smaller case instead of a collision.
        for(int lineHeight = 6; lineHeight <= 20; lineHeight++)
        {
            DeckBoxShopScreen.TileLayout tile =
                DeckBoxShopScreen.tileLayout(118, 112, lineHeight);
            assertTrue(tile.artBottom() <= tile.nameY(),
                "art ran into the name with a " + lineHeight + "-unit font");
            assertTrue(tile.priceY() + lineHeight <= 112,
                "price fell out with a " + lineHeight + "-unit font");
        }
    }

    @Test
    void theCaseKeepsItsShape()
    {
        // Whichever bound is binding, the art stays the proportion of the source
        // PNG -- a stretched case is the other way this can look wrong.
        for(int tileH = 60; tileH <= 200; tileH += 7)
        {
            for(int tileW = 40; tileW <= 200; tileW += 7)
            {
                DeckBoxShopScreen.TileLayout tile =
                    DeckBoxShopScreen.tileLayout(tileW, tileH, LINE_HEIGHT);
                if(tile.artH() < 8)
                {
                    // Rounding dominates at a handful of units, and a case that
                    // small is not being read for its shape.
                    continue;
                }
                float ratio = tile.artW() / (float)tile.artH();
                assertTrue(Math.abs(ratio - 0.824F) < 0.06F,
                    "case was stretched to " + ratio + " at " + tileW + "x" + tileH);
            }
        }
    }
}
