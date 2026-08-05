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

    @Test
    void tableFillsTheBoxItWasFittedTo()
    {
        float nearY = projection.y(FieldLayout.FIELD_MAX_Y);
        float farY = projection.y(FieldLayout.FIELD_MIN_Y);
        assertEquals(TOP, farY, 1.5F, "far edge should sit at the top of the box");
        assertEquals(TOP + HEIGHT, nearY, 1.5F, "near edge should sit at the bottom");

        float nearLeft = projection.x(FieldLayout.FIELD_MIN_X, FieldLayout.FIELD_MAX_Y);
        float nearRight = projection.x(FieldLayout.FIELD_MAX_X, FieldLayout.FIELD_MAX_Y);
        assertEquals(LEFT, nearLeft, 1.5F);
        assertEquals(LEFT + WIDTH, nearRight, 1.5F);
    }

    @Test
    void farEdgeIsNarrowerThanNearEdge()
    {
        float nearWidth = projection.x(FieldLayout.FIELD_MAX_X, FieldLayout.FIELD_MAX_Y)
            - projection.x(FieldLayout.FIELD_MIN_X, FieldLayout.FIELD_MAX_Y);
        float farWidth = projection.x(FieldLayout.FIELD_MAX_X, FieldLayout.FIELD_MIN_Y)
            - projection.x(FieldLayout.FIELD_MIN_X, FieldLayout.FIELD_MIN_Y);
        assertTrue(farWidth < nearWidth, "the table must recede");
        assertEquals(0.62F, farWidth / nearWidth, 0.02F, "far/near ratio should be the configured tilt");
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
