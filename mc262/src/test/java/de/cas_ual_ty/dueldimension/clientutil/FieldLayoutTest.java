package de.cas_ual_ty.dueldimension.clientutil;

import de.cas_ual_ty.dueldimension.ocg.OcgConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the duel field's layout and projection maths — the half of the
 * field-render port that is pure geometry and so can be checked with no GPU.
 * The values are EDOPro's own (materials.cpp / game.h), so a regression here is
 * a regression against the reference client, not against an invented number.
 */
class FieldLayoutTest
{
    private static final float EPS = 1.0e-4F;

    @Test
    void selfMonsterZonesSitOnTheColumnPitch()
    {
        // materials.cpp: first column at x 1.2, pitch 1.1, cells 1.1 x 1.2.
        for(int seq = 0; seq < 5; seq++)
        {
            FieldLayout.Rect zone = FieldLayout.zone(0, OcgConstants.LOCATION_MZONE, seq);
            assertEquals(1.2F + seq * 1.1F, zone.x(), EPS);
            assertEquals(0.8F, zone.y(), EPS);
            assertEquals(1.1F, zone.w(), EPS);
            assertEquals(1.2F, zone.h(), EPS);
        }
    }

    @Test
    void extraMonsterZonesStraddleTheCentreLine()
    {
        FieldLayout.Rect left = FieldLayout.zone(0, OcgConstants.LOCATION_MZONE, 5);
        FieldLayout.Rect right = FieldLayout.zone(0, OcgConstants.LOCATION_MZONE, 6);
        assertEquals(2.3F, left.x(), EPS);
        assertEquals(4.5F, right.x(), EPS);
        assertEquals(-0.6F, left.y(), EPS);
        assertEquals(-0.6F, right.y(), EPS);
    }

    @Test
    void opponentSideIsThePointReflection()
    {
        // (x, y) -> (MIRROR_X - x - w, -y - h), MIRROR_X = 7.9, exactly as the
        // opponent-side copies are listed in materials.cpp.
        int[] locations = {OcgConstants.LOCATION_MZONE, OcgConstants.LOCATION_SZONE,
            OcgConstants.LOCATION_DECK, OcgConstants.LOCATION_GRAVE};
        for(int location : locations)
        {
            for(int seq = 0; seq < 5; seq++)
            {
                FieldLayout.Rect self = FieldLayout.zone(0, location, seq);
                FieldLayout.Rect opp = FieldLayout.zone(1, location, seq);
                if(self == null)
                {
                    assertNull(opp);
                    continue;
                }
                assertEquals(7.9F - self.x() - self.w(), opp.x(), EPS);
                assertEquals(-self.y() - self.h(), opp.y(), EPS);
                assertEquals(self.w(), opp.w(), EPS);
                assertEquals(self.h(), opp.h(), EPS);
            }
        }
    }

    @Test
    void anUnknownLocationHasNoZone()
    {
        assertNull(FieldLayout.zone(0, OcgConstants.LOCATION_HAND, 0));
    }

    @Test
    void theFrustumIsWiderThanItIsTall()
    {
        // r-l = 1.35, t-b = 0.84, so one NDC x unit is 1.607 NDC y units. A
        // single scale for both axes is exactly the bug this guards against.
        assertEquals(1.35F / 0.84F, FieldLayout.Projection.FRUSTUM_ASPECT, EPS);
    }

    @Test
    void perspectiveNarrowsAZoneTowardsTheFarEdge()
    {
        FieldLayout.Projection projection = FieldLayout.fit(0, 0, 1634, 920);
        FieldLayout.Rect zone = FieldLayout.zone(0, OcgConstants.LOCATION_MZONE, 2);
        FieldQuad.Corners quad = projection.quad(zone);

        // Corners are TL(far), TR(far), BR(near), BL(near). The near edge (the
        // one closer to the viewer, lower on screen) must be wider.
        float farWidth = quad.x1() - quad.x0();
        float nearWidth = quad.x2() - quad.x3();
        assertTrue(nearWidth > farWidth,
            "near edge " + nearWidth + " should be wider than far edge " + farWidth);
        // ...and the near edge is lower on the screen (greater y).
        assertTrue(quad.y2() > quad.y0(), "near edge should sit below the far edge");
    }

    @Test
    void screenYIncreasesAsTheTableComesForward()
    {
        // Larger field y is nearer the camera and lower on the screen.
        FieldLayout.Projection projection = FieldLayout.fit(0, 0, 1634, 920);
        assertTrue(projection.y(FieldLayout.FIELD_MAX_Y) > projection.y(FieldLayout.FIELD_MIN_Y));
    }

    @Test
    void theFittedTableStaysInsideItsBox()
    {
        FieldLayout.Projection projection = FieldLayout.fit(0, 0, 1634, 920);
        // The mat corners project inside the box (with a pixel of tolerance).
        for(float fx : new float[] {FieldLayout.FIELD_MIN_X, FieldLayout.FIELD_MAX_X})
        {
            for(float fy : new float[] {FieldLayout.FIELD_MIN_Y, FieldLayout.FIELD_MAX_Y})
            {
                float x = projection.x(fx, fy);
                float y = projection.y(fy);
                assertTrue(x >= -1F && x <= 1635F, "x in box: " + x);
                assertTrue(y >= -1F && y <= 921F, "y in box: " + y);
            }
        }
    }

    // ---- the rules both presentations now share ----
    //
    // These moved into FieldLayout because each was written down twice, once
    // per duel, and two copies of a rule is a rule that can disagree with
    // itself. Pinned here because this is the file that can be tested with no
    // client behind it, which is the whole reason they came here rather than
    // to either thing that draws.

    @Test
    void aCardIsItsOwnSizeAndCentredInItsZone()
    {
        FieldLayout.Rect zone = FieldLayout.zone(0, OcgConstants.LOCATION_MZONE, 2);
        FieldLayout.Rect card = FieldLayout.cardIn(zone, false);
        assertEquals(FieldLayout.CARD_W, card.w(), EPS);
        assertEquals(FieldLayout.CARD_H, card.h(), EPS);
        // Centred: the margin is equal on both sides, which is the rule two
        // separate comments recorded as "filling the zone is what made every
        // card look stretched wide".
        assertEquals(zone.x() + zone.w() / 2F, card.x() + card.w() / 2F, EPS);
        assertEquals(zone.y() + zone.h() / 2F, card.y() + card.h() / 2F, EPS);
        assertTrue(card.w() < zone.w(), "a card should not fill its zone");
    }

    @Test
    void aDefendingMonsterLiesOnItsSideAndStaysCentred()
    {
        FieldLayout.Rect zone = FieldLayout.zone(0, OcgConstants.LOCATION_MZONE, 2);
        FieldLayout.Rect lying = FieldLayout.cardIn(zone, true);
        assertEquals(FieldLayout.CARD_H, lying.w(), EPS);
        assertEquals(FieldLayout.CARD_W, lying.h(), EPS);
        assertEquals(zone.x() + zone.w() / 2F, lying.x() + lying.w() / 2F, EPS);
        assertEquals(zone.y() + zone.h() / 2F, lying.y() + lying.h() / 2F, EPS);
    }

    @Test
    void theZoneAspectIsNotTheCardAspect()
    {
        // The reason ZONE_ASPECT was renamed: a reader who wired CARD_W and
        // CARD_H to a constant called CARD_ASPECT would have got neither.
        assertTrue(Math.abs(FieldLayout.ZONE_ASPECT - FieldLayout.CARD_W / FieldLayout.CARD_H) > 0.1F,
            "the two aspects are close enough to be confused again");
    }

    @Test
    void theOpponentsCardsFaceTheOpponent()
    {
        // client_field.cpp: selfATK {0,0,0} against oppoATK {0,0,PI}, and a
        // defending monster adds a quarter on top of that.
        assertEquals(0, FieldLayout.turnsFor(0, false));
        assertEquals(1, FieldLayout.turnsFor(0, true));
        assertEquals(2, FieldLayout.turnsFor(1, false));
        assertEquals(3, FieldLayout.turnsFor(1, true));
    }

    @Test
    void onlyTheEndsOfTheBackrowArePendulumZones()
    {
        // MR5: ocgcore sets DUEL_PZONE without DUEL_SEPARATE_PZONE, so a scale
        // occupies backrow 0 or 4 rather than a zone of its own.
        assertTrue(FieldLayout.isPendulumZone(OcgConstants.LOCATION_SZONE, 0));
        assertTrue(FieldLayout.isPendulumZone(OcgConstants.LOCATION_SZONE, 4));
        for(int sequence : new int[] {1, 2, 3})
        {
            assertTrue(!FieldLayout.isPendulumZone(OcgConstants.LOCATION_SZONE, sequence),
                "backrow " + sequence + " is not a pendulum zone");
        }
        // The MR3 separate zones are still described by the layout and stay
        // empty in every duel this mod runs, so they are not pendulum zones
        // for this purpose either.
        assertTrue(!FieldLayout.isPendulumZone(OcgConstants.LOCATION_SZONE, 6));
        assertTrue(!FieldLayout.isPendulumZone(OcgConstants.LOCATION_SZONE, 7));
        // And a monster zone never is, whatever its sequence.
        assertTrue(!FieldLayout.isPendulumZone(OcgConstants.LOCATION_MZONE, 0));
        assertTrue(!FieldLayout.isPendulumZone(OcgConstants.LOCATION_MZONE, 4));
    }
}
