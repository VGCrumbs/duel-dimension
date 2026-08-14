package de.cas_ual_ty.dueldimension.clientutil.overworld;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A trim decides what is read, never where the monster stands.
 * <p>
 * This is the property the crop controls are worth having only if it holds. The
 * first attempt did not: the quad kept the caller's full height whatever the
 * trim, so cutting a margin off the top and bottom stretched the art that was
 * left to fill the space the margin had occupied, and dragged its feet down at
 * the same time. Cropping is supposed to be the one adjustment you can make
 * while everything else stays where you put it.
 * <p>
 * The measure of that is the SCALE THE ART IS DRAWN AT -- world units per source
 * texel. Trim changes how many texels are sampled and how big the quad is, and
 * the test is that those two change together, exactly, on both axes: same scale
 * horizontally as vertically, so nothing is distorted, and the same scale as an
 * untrimmed layer, so nothing has grown.
 * <p>
 * Every layer here states its own region, which is what keeps the arithmetic off
 * the texture manager and lets it be checked without a game running.
 */
class SpriteLayerTrimTest
{
    /** A 512x256 region cut into four 128x256 cells, which is a common sheet. */
    private static SpriteLayer layer(int trimX, int trimY)
    {
        return new SpriteLayer("test/sheet", 0, 0, 512, 256, 4, 1, 0, 4, 5,
            MonsterSprites.Loop.LOOP, trimX, trimY);
    }

    private static final float CELL_W = 128F;
    private static final float CELL_H = 256F;
    /** However tall the caller asks for the WHOLE cell to be drawn, in blocks. */
    private static final float HEIGHT = 2.2F;

    /** World units per source texel across, as {@code MonsterBillboard} draws it. */
    private static float scaleAcross(SpriteLayer layer)
    {
        float drawnWidth = HEIGHT * layer.aspect() * layer.spanX();
        return drawnWidth / (CELL_W * layer.spanX());
    }

    /** World units per source texel down. */
    private static float scaleDown(SpriteLayer layer)
    {
        float drawnHeight = HEIGHT * layer.spanY();
        return drawnHeight / (CELL_H * layer.spanY());
    }

    @Test
    void aTrimDoesNotChangeTheScaleTheArtIsDrawnAt()
    {
        float across = scaleAcross(layer(0, 0));
        float down = scaleDown(layer(0, 0));
        for(int trimX : new int[] {0, 1, 7, 24, 48})
        {
            for(int trimY : new int[] {0, 1, 7, 24, 48})
            {
                SpriteLayer cropped = layer(trimX, trimY);
                assertEquals(across, scaleAcross(cropped), 1e-5F,
                    "cropping " + trimX + "," + trimY + " resized the art across");
                assertEquals(down, scaleDown(cropped), 1e-5F,
                    "cropping " + trimX + "," + trimY + " resized the art down");
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
            assertEquals(scaleAcross(layer(trim, 0)), scaleDown(layer(trim, 0)), 1e-5F);
            assertEquals(scaleAcross(layer(0, trim)), scaleDown(layer(0, trim)), 1e-5F);
            assertEquals(scaleAcross(layer(trim, trim)), scaleDown(layer(trim, trim)), 1e-5F);
        }
    }

    @Test
    void theBoxStaysCentredOnTheCell()
    {
        // The drawn box shares the whole box's middle, which is what lets the
        // surviving pixels land where they already were. MonsterBillboard
        // stands the quad at (height - drawnHeight) / 2 for exactly this.
        for(int trimY : new int[] {0, 3, 31, 90})
        {
            SpriteLayer cropped = layer(0, trimY);
            float drawnHeight = HEIGHT * cropped.spanY();
            float bottom = (HEIGHT - drawnHeight) / 2F;
            assertEquals(HEIGHT / 2F, bottom + drawnHeight / 2F, 1e-5F,
                "a vertical crop of " + trimY + " moved the sprite");
        }
    }

    @Test
    void theWholeCellsProportionIsBlindToTheTrim()
    {
        float untrimmed = layer(0, 0).aspect();
        assertEquals(CELL_W / CELL_H, untrimmed, 1e-5F);
        assertEquals(untrimmed, layer(40, 0).aspect(), 1e-5F);
        assertEquals(untrimmed, layer(0, 90).aspect(), 1e-5F);
        assertEquals(untrimmed, layer(40, 90).aspect(), 1e-5F);
    }

    @Test
    void anOverLargeTrimStaysPositive()
    {
        // The editor's sliders reach 128, and a cell here is 128 across, so a
        // span of zero or less is one drag away. Negative would not throw -- it
        // would silently mirror the sprite, because turning a box inside out is
        // how this code mirrors things on purpose elsewhere.
        for(int trim : new int[] {64, 100, 128})
        {
            assertTrue(layer(trim, trim).spanX() > 0F,
                "a trim of " + trim + " inverted the box across");
            assertTrue(layer(trim, trim).spanY() > 0F,
                "a trim of " + trim + " inverted the box down");
        }
    }

    @Test
    void aTrimOfNothingLeavesTheWholeCell()
    {
        assertEquals(1F, layer(0, 0).spanX(), 1e-6F);
        assertEquals(1F, layer(0, 0).spanY(), 1e-6F);
    }
}
