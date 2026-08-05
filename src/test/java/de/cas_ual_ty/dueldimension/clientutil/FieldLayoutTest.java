package de.cas_ual_ty.dueldimension.clientutil;

import de.cas_ual_ty.dueldimension.ocg.OcgConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Geometry checks that would have caught the warped board: a projected card
 * must keep its shape (only shrinking with distance), the table must stay
 * inside the box it was fitted to, and zones must not overlap or invert.
 */
class FieldLayoutTest
{
    private static final int LEFT = 100;
    private static final int TOP = 40;
    private static final int WIDTH = 800;
    private static final int HEIGHT = 400;

    private final FieldLayout.Projection projection = FieldLayout.fit(LEFT, TOP, WIDTH, HEIGHT);

    /**
     * The board has to use the box it is given, without changing shape.
     * <p>
     * Mapping raw NDC to the box left the mat filling 89% of the width and 64%
     * of the height, which reads as a small table adrift in empty space. Fitting
     * the framed area fixes that, and because one scale is used for both axes
     * the table keeps a fixed aspect: it fills the limiting axis exactly and is
     * centred on the other, so the leftover is an even margin rather than a
     * stretch.
     */
    @Test
    void theFramedBoardFillsItsBox()
    {
        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        // The framed area is the mat plus a card's height of hand at each end.
        for(float fieldX : new float[] {FieldLayout.FIELD_MIN_X, FieldLayout.FIELD_MAX_X})
        {
            for(float fieldY : new float[] {FieldLayout.FIELD_MIN_Y - 1F, FieldLayout.FIELD_MAX_Y + 1F})
            {
                minX = Math.min(minX, projection.x(fieldX, fieldY));
                maxX = Math.max(maxX, projection.x(fieldX, fieldY));
            }
        }
        float nearY = projection.y(FieldLayout.FIELD_MAX_Y + 1F);
        float farY = projection.y(FieldLayout.FIELD_MIN_Y - 1F);

        float drawnWidth = maxX - minX;
        float drawnHeight = Math.max(nearY, farY) - Math.min(nearY, farY);

        // Inside the box on both axes, touching it on at least one.
        assertTrue(minX >= LEFT - 1F && maxX <= LEFT + WIDTH + 1F, "board must stay inside its box");
        assertTrue(Math.min(nearY, farY) >= TOP - 1F && Math.max(nearY, farY) <= TOP + HEIGHT + 1F,
            "board must stay inside its box");
        assertTrue(drawnWidth >= WIDTH - 1F || drawnHeight >= HEIGHT - 1F,
            "board should fill the limiting axis, not float in the middle");

        // Centred on whichever axis has room left over.
        assertEquals((LEFT + LEFT + WIDTH) / 2F, (minX + maxX) / 2F, 1F, "board should be centred");
        assertEquals((TOP + TOP + HEIGHT) / 2F, (nearY + farY) / 2F, 1F, "board should be centred");

        // A fixed aspect. NOT one scale for both axes: NDC x spans the
        // frustum's width (1.35) and y its height (0.84), so undistorted means
        // scaleX / scaleY = 1.35 / 0.84. Forcing them equal squashes the table
        // horizontally by that factor, which is what made it look skinny.
        float frustumAspect = (0.45F + 0.90F) / (0.42F + 0.42F);
        assertEquals(frustumAspect, projection.scaleX() / projection.scaleY(), 0.001F,
            "the table must not be squashed: pixels per NDC unit differ by the frustum's aspect");
        FieldLayout.Projection wide = FieldLayout.fit(0, 0, WIDTH * 2, HEIGHT);
        assertEquals(projection.scaleX() / projection.scaleY(), wide.scaleX() / wide.scaleY(), 0.001F,
            "the board must keep its shape when the window's shape changes");
    }

    /**
     * The mat's near corners run off the edge in the reference client too -
     * the frustum is wider than the table - but every zone a player has to
     * interact with must be fully on screen.
     */
    @Test
    void everyPlayableZoneLandsOnScreen()
    {
        int[] locations = {OcgConstants.LOCATION_MZONE, OcgConstants.LOCATION_SZONE,
            OcgConstants.LOCATION_DECK, OcgConstants.LOCATION_EXTRA,
            OcgConstants.LOCATION_GRAVE, OcgConstants.LOCATION_REMOVED};
        for(int controller = 0; controller <= 1; controller++)
        {
            for(int location : locations)
            {
                int slots = location == OcgConstants.LOCATION_MZONE ? 7
                    : location == OcgConstants.LOCATION_SZONE ? 6 : 1;
                for(int sequence = 0; sequence < slots; sequence++)
                {
                    FieldLayout.Rect rect = FieldLayout.zone(controller, location, sequence);
                    if(rect == null)
                    {
                        continue;
                    }
                    FieldQuad.Corners corners = projection.quad(rect);
                    assertTrue(corners.minX() >= LEFT - 1 && corners.maxX() <= LEFT + WIDTH + 1,
                        "zone " + location + "/" + sequence + " runs off horizontally");
                    assertTrue(corners.minY() >= TOP - 1 && corners.maxY() <= TOP + HEIGHT + 1,
                        "zone " + location + "/" + sequence + " runs off vertically");
                }
            }
        }
    }

    /**
     * EDOPro's frustum is asymmetric (l=-0.90, r=+0.45) so the table sits
     * right of centre, leaving the left of the screen for the card-info
     * column. The near edge must also be wider than the far edge.
     */
    @Test
    void perspectiveMatchesTheReferenceFrustum()
    {
        float nearWidth = projection.x(FieldLayout.FIELD_MAX_X, FieldLayout.FIELD_MAX_Y)
            - projection.x(FieldLayout.FIELD_MIN_X, FieldLayout.FIELD_MAX_Y);
        float farWidth = projection.x(FieldLayout.FIELD_MAX_X, FieldLayout.FIELD_MIN_Y)
            - projection.x(FieldLayout.FIELD_MIN_X, FieldLayout.FIELD_MIN_Y);
        assertTrue(farWidth < nearWidth, "the table must recede");

        // Ratio follows from the camera: depth at the far edge over the near.
        float expected = (float)((8.0 * 8.0 + 7.8 * 7.8 - 8.0 * 4.0)
            / (8.0 * 8.0 + 7.8 * 7.8 - 8.0 * -4.0));
        assertEquals(expected, farWidth / nearWidth, 0.01F,
            "far/near width ratio should equal the camera's depth ratio");

        // EDOPro's frustum pushes the table right to clear its own panel and
        // leaves most of the box unused; fit takes that offset out and scales
        // the framed area (mat plus hands) to the box. The table's centre LINE
        // is then near, but not exactly on, the box's middle: the projected mat
        // is a trapezoid, so its bounding box is not symmetric about that line.
        // Being off by a few pixels is the cost of actually filling the box.
        float centre = projection.x((FieldLayout.FIELD_MIN_X + FieldLayout.FIELD_MAX_X) / 2F, 0F);
        assertEquals(LEFT + WIDTH / 2F, centre, WIDTH * 0.02F,
            "table should sit near the middle of its box");
    }

    /**
     * A card lying flat on a tilted table is legitimately foreshortened - that
     * is what a receding surface looks like, and the reference client shows it
     * too. What must NOT happen is shearing: a zone stays symmetric about its
     * own centre line, and squashes more the further away it is.
     */
    @Test
    void zonesForeshortenWithDepthWithoutShearing()
    {
        float previousSquash = Float.MAX_VALUE;
        // Walk from your side (near) to the opponent's (far).
        for(int controller : new int[] {0, 1})
        {
            FieldLayout.Rect rect = FieldLayout.zone(controller, OcgConstants.LOCATION_MZONE, 2);
            FieldQuad.Corners corners = projection.quad(rect);

            float topWidth = corners.x1() - corners.x0();
            float bottomWidth = corners.x2() - corners.x3();
            float height = corners.y3() - corners.y0();

            assertTrue(topWidth > 0 && bottomWidth > 0 && height > 0, "degenerate quad");
            assertTrue(topWidth <= bottomWidth + 0.01F, "the far edge of a zone must not be wider");

            // Symmetric about its centre: the two sloping sides mirror.
            float centreTop = (corners.x0() + corners.x1()) / 2F;
            float centreBottom = (corners.x3() + corners.x2()) / 2F;
            assertEquals(centreTop, centreBottom, Math.max(1F, bottomWidth * 0.02F),
                "zone is sheared, not just foreshortened");

            // Squash factor: screen height per unit of field height, relative
            // to width. Smaller means flatter, i.e. further away.
            float squash = (height / rect.h()) / (bottomWidth / rect.w());
            assertTrue(squash < previousSquash,
                "the opponent's side should be flatter than yours (" + squash + " vs " + previousSquash + ")");
            previousSquash = squash;
        }
    }

    @Test
    void yourSideIsNearerThanTheOpponents()
    {
        FieldLayout.Rect mine = FieldLayout.zone(0, OcgConstants.LOCATION_MZONE, 2);
        FieldLayout.Rect theirs = FieldLayout.zone(1, OcgConstants.LOCATION_MZONE, 2);
        assertTrue(projection.quad(mine).minY() > projection.quad(theirs).minY(),
            "your monsters should be drawn lower on screen than the opponent's");
    }

    @Test
    void monsterRowZonesDoNotOverlapAndRunLeftToRight()
    {
        float previousRight = Float.NEGATIVE_INFINITY;
        for(int sequence = 0; sequence < 5; sequence++)
        {
            FieldQuad.Corners corners =
                projection.quad(FieldLayout.zone(0, OcgConstants.LOCATION_MZONE, sequence));
            assertTrue(corners.x0() >= previousRight - 0.5F,
                "monster zone " + sequence + " overlaps the previous one");
            previousRight = corners.x1();
        }
    }

    @Test
    void hitTestingMatchesTheDrawnShape()
    {
        FieldQuad.Corners corners = projection.quad(FieldLayout.zone(0, OcgConstants.LOCATION_MZONE, 2));
        float centreX = (corners.x0() + corners.x1() + corners.x2() + corners.x3()) / 4F;
        float centreY = (corners.y0() + corners.y1() + corners.y2() + corners.y3()) / 4F;
        assertTrue(corners.contains(centreX, centreY), "centre of a zone must hit it");
        assertTrue(!corners.contains(corners.minX() - 20, centreY), "well outside must miss");
    }
}
