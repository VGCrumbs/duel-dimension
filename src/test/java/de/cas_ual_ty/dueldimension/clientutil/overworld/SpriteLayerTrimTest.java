package de.cas_ual_ty.dueldimension.clientutil.overworld;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A trim bites at the region's outer edge, and never moves the monster.
 * <p>
 * Two properties, and the crop controls are worth having only if both hold.
 * <p>
 * <b>Outer edges only.</b> The trim exists because a sample reaching past the
 * region's boundary wraps and comes back with the far side of the sheet. That
 * cannot happen at a division between two cells, so cutting there throws art
 * away to fix a fault it does not have -- a four-frame strip has two outer
 * vertical edges and three internal ones, and trimming all five paid for one
 * problem five times over.
 * <p>
 * <b>Nothing moves.</b> The first attempt kept the caller's full height whatever
 * the trim, so cropping the top and bottom stretched what was left to fill the
 * space the margin had occupied and dragged its feet down. The measure of that
 * is where a sampled texel lands in the world: it has to land where it already
 * was, whatever has been cut off around it.
 * <p>
 * Every layer here states its own region, which keeps the arithmetic off the
 * texture manager and lets it be checked without a game running.
 */
class SpriteLayerTrimTest
{
    private static final float CELL_W = 128F;
    private static final float CELL_H = 256F;
    private static final int COLUMNS = 4;
    /** However tall the caller asks for the WHOLE cell to be drawn, in blocks. */
    private static final float HEIGHT = 2.2F;

    /** A 512x256 region cut into four 128x256 cells, which is a common sheet. */
    private static SpriteLayer strip(int trimX, int trimY)
    {
        return new SpriteLayer("test/sheet", 0, 0, 512, 256, COLUMNS, 1, 0, COLUMNS, 5,
            MonsterSprites.Loop.LOOP, trimX, trimY);
    }

    // ------------------------------------------------- outer edges only --

    @Test
    void theDivisionsBetweenCellsAreNeverTrimmed()
    {
        SpriteLayer cropped = strip(12, 0);
        for(int cell = 1; cell < COLUMNS - 1; cell++)
        {
            float[] window = cropped.windowAt(cell);
            assertEquals(0F, window[0], 1e-6F, "cell " + cell + " lost art at its left division");
            assertEquals(1F, window[2], 1e-6F, "cell " + cell + " lost art at its right division");
        }
    }

    @Test
    void theEndsOfTheRunAreTrimmedOnTheirOutsideOnly()
    {
        SpriteLayer cropped = strip(12, 0);
        float inset = 12F / CELL_W;

        float[] first = cropped.windowAt(0);
        assertEquals(inset, first[0], 1e-6F, "the first cell was not trimmed on its outer edge");
        assertEquals(1F, first[2], 1e-6F, "the first cell was trimmed on its inner edge");

        float[] last = cropped.windowAt(COLUMNS - 1);
        assertEquals(0F, last[0], 1e-6F, "the last cell was trimmed on its inner edge");
        assertEquals(1F - inset, last[2], 1e-6F, "the last cell was not trimmed on its outer edge");
    }

    @Test
    void aSingleRowIsOuterTopAndOuterBottomAtOnce()
    {
        // Not a special case -- the same rule. One row means its top and its
        // bottom are both on the boundary, so both are cut, which is what makes
        // a vertical crop work at all on the strip sheets most of these are.
        SpriteLayer cropped = strip(0, 20);
        float inset = 20F / CELL_H;
        for(int cell = 0; cell < COLUMNS; cell++)
        {
            float[] window = cropped.windowAt(cell);
            assertEquals(inset, window[1], 1e-6F, "cell " + cell + " kept its top margin");
            assertEquals(1F - inset, window[3], 1e-6F, "cell " + cell + " kept its bottom margin");
        }
    }

    @Test
    void aTrimOfNothingLeavesTheWholeCell()
    {
        for(int cell = 0; cell < COLUMNS; cell++)
        {
            float[] window = strip(0, 0).windowAt(cell);
            assertEquals(0F, window[0], 1e-6F);
            assertEquals(0F, window[1], 1e-6F);
            assertEquals(1F, window[2], 1e-6F);
            assertEquals(1F, window[3], 1e-6F);
        }
    }

    // ----------------------------------------------------- nothing moves --

    /** Where one source texel lands, in blocks -- the scale the art is drawn at. */
    private static final float PER_TEXEL = HEIGHT / CELL_H;

    @Test
    void aSampledTexelLandsWhereItAlreadyDid()
    {
        for(int trimX : new int[] {0, 3, 12, 40})
        {
            for(int trimY : new int[] {0, 3, 12, 40})
            {
                SpriteLayer cropped = strip(trimX, trimY);
                float half = HEIGHT * cropped.aspect() / 2F;
                for(int cell = 0; cell < COLUMNS; cell++)
                {
                    float[] window = cropped.windowAt(cell);
                    // Exactly as MonsterBillboard places the quad.
                    float drawnHalf = half * (window[2] - window[0]);
                    float drawnHeight = HEIGHT * (window[3] - window[1]);
                    float shift = half * (window[0] + window[2] - 1F);
                    float lift = HEIGHT * (1F - window[3]);

                    String where = "cell " + cell + " at trim " + trimX + "," + trimY;
                    assertEquals(-half + window[0] * CELL_W * PER_TEXEL, shift - drawnHalf, 1e-5F,
                        where + ": the left edge of the art moved");
                    assertEquals(-half + window[2] * CELL_W * PER_TEXEL, shift + drawnHalf, 1e-5F,
                        where + ": the right edge of the art moved");
                    assertEquals(HEIGHT * (1F - window[3]), lift, 1e-5F,
                        where + ": the bottom of the art moved");
                    assertEquals(HEIGHT * (1F - window[1]), lift + drawnHeight, 1e-5F,
                        where + ": the top of the art moved");
                }
            }
        }
    }

    @Test
    void theArtIsNeverDistortedByACrop()
    {
        // Square texels: one texel is as wide as it is tall no matter what has
        // been cut off. A crop that failed this would squash the sprite on one
        // axis, which is the exact failure a separate x and y trim invites.
        for(int trim : new int[] {0, 5, 16, 40})
        {
            for(SpriteLayer cropped : new SpriteLayer[] {
                strip(trim, 0), strip(0, trim), strip(trim, trim)})
            {
                float half = HEIGHT * cropped.aspect() / 2F;
                float[] window = cropped.windowAt(0);
                float across = half * 2F * (window[2] - window[0]) / (CELL_W
                    * (window[2] - window[0]));
                float down = HEIGHT * (window[3] - window[1]) / (CELL_H
                    * (window[3] - window[1]));
                assertEquals(across, down, 1e-5F, "a crop of " + trim + " distorted the art");
                assertEquals(PER_TEXEL, across, 1e-5F, "a crop of " + trim + " resized the art");
            }
        }
    }

    @Test
    void theWholeCellsProportionIsBlindToTheTrim()
    {
        float untrimmed = strip(0, 0).aspect();
        assertEquals(CELL_W / CELL_H, untrimmed, 1e-5F);
        assertEquals(untrimmed, strip(40, 0).aspect(), 1e-5F);
        assertEquals(untrimmed, strip(0, 90).aspect(), 1e-5F);
        assertEquals(untrimmed, strip(40, 90).aspect(), 1e-5F);
    }

    @Test
    void anOverLargeTrimNeverInvertsTheBox()
    {
        // The editor's sliders reach 128, and a cell here is 128 across, so an
        // empty or inside-out box is one drag away. Inverted would not throw --
        // it would silently mirror the sprite, because turning a box inside out
        // is how this code mirrors things on purpose elsewhere.
        for(int trim : new int[] {64, 100, 128})
        {
            for(int cell = 0; cell < COLUMNS; cell++)
            {
                float[] window = strip(trim, trim).windowAt(cell);
                assertTrue(window[2] > window[0],
                    "a trim of " + trim + " inverted cell " + cell + " across");
                assertTrue(window[3] > window[1],
                    "a trim of " + trim + " inverted cell " + cell + " down");
            }
        }
    }
}
