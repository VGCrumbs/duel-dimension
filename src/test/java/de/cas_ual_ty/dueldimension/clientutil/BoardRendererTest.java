package de.cas_ual_ty.dueldimension.clientutil;

import de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class BoardRendererTest
{
    private static BoardSnapshot.Slot card(int code)
    {
        // -1 scales: not a pendulum card, the same sentinel the engine uses.
        return new BoardSnapshot.Slot(true, code, false, false,
            0, 0, 0, 0, -1, -1, 0, null);
    }

    @Test
    void newestPileCardIsTheLastEngineSequence()
    {
        assertEquals(300, BoardRenderer.newestVisibleCode(
            List.of(card(100), card(200), card(300))));
    }

    @Test
    void anEmptyOrHiddenTopCardHasNoHoverIdentity()
    {
        assertEquals(0, BoardRenderer.newestVisibleCode(List.of()));
        assertEquals(0, BoardRenderer.newestVisibleCode(List.of(card(100), card(0))));
    }

    @Test
    void overlappingCenterZoneUsesTheCallersPriority()
    {
        FieldQuad.Corners shared = new FieldQuad.Corners(
            0, 0, 10, 0, 10, 10, 0, 10);
        BoardRenderer.Hit empty = new BoardRenderer.Hit(shared, 0, 0,
            de.cas_ual_ty.dueldimension.ocg.OcgConstants.LOCATION_MZONE,
            5, 0, "Extra Monster Zone", 0);
        BoardRenderer.Hit occupied = new BoardRenderer.Hit(shared, 123, 1,
            de.cas_ual_ty.dueldimension.ocg.OcgConstants.LOCATION_MZONE,
            6, 0, "Extra Monster Zone", 0);

        BoardRenderer renderer = new BoardRenderer();
        renderer.hits().add(empty);
        renderer.hits().add(occupied);

        assertSame(occupied, renderer.hitAt(5, 5, hit -> hit.code() != 0 ? 1 : 0));
        assertSame(empty, renderer.hitAt(5, 5, hit -> hit.controller() == 0 ? 1 : 0));
    }
}
