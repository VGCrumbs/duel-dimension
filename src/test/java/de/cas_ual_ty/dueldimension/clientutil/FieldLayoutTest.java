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

        // Table centre (field x 4.0) sits right of the box centre.
        float centre = projection.x(4.0F, 0F);
        assertTrue(centre > LEFT + WIDTH / 2F,
            "the off-centre frustum should push the table right, leaving room for the sidebar");
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
