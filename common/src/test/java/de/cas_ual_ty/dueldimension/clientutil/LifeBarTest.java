package de.cas_ual_ty.dueldimension.clientutil;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The life bar's fill rule.
 * <p>
 * Untested until now, which is part of why it went so long measuring every duel
 * against a hardcoded 8000: nothing said out loud what it was supposed to
 * measure against.
 */
class LifeBarTest
{
    /** The 2D duel screen's bar. Its well is barW - 4 wide. */
    private static final int BAR_W = 104;
    private static final int USABLE = BAR_W - 4;

    @Test
    void fullAtTheStartingAmount()
    {
        // The point of the whole change: a duel started at 4000 opens with a
        // full bar, not the half-empty one a fixed 8000 gave it.
        for(int starting : new int[] {8000, 4000, 2000, 16000})
        {
            assertEquals(USABLE, LifeBar.fill(BAR_W, starting, starting),
                "a duel should open with a full bar at " + starting);
            assertEquals(0, LifeBar.overflow(BAR_W, starting, starting),
                "and with nothing in the overflow at " + starting);
        }
    }

    @Test
    void scalesByFractionNotByAbsoluteLife()
    {
        // Half of 2000 and half of 16000 are wildly different numbers and the
        // same bar. That is the property the old code did not have.
        assertEquals(LifeBar.fill(BAR_W, 1000, 2000), LifeBar.fill(BAR_W, 8000, 16000));
        assertEquals(USABLE / 2, LifeBar.fill(BAR_W, 1000, 2000));
    }

    @Test
    void emptyAtZero()
    {
        assertEquals(0, LifeBar.fill(BAR_W, 0, 8000));
        assertEquals(0, LifeBar.overflow(BAR_W, 0, 8000));
    }

    @Test
    void overflowFillsBetweenOneAndTwoTimesTheStart()
    {
        assertEquals(0, LifeBar.overflow(BAR_W, 8000, 8000));
        assertEquals(USABLE / 2, LifeBar.overflow(BAR_W, 12000, 8000));
        assertEquals(USABLE, LifeBar.overflow(BAR_W, 16000, 8000),
            "200% of the starting life should fill the overflow bar completely");
    }

    @Test
    void mainBarStaysFullWhileOverflowing()
    {
        // It must not keep growing past the frame once life is above the start:
        // the overflow carries that, and the bar underneath stays complete.
        assertEquals(USABLE, LifeBar.fill(BAR_W, 12000, 8000));
        assertEquals(USABLE, LifeBar.fill(BAR_W, 16000, 8000));
    }

    @Test
    void bothClampRatherThanOverrunTheFrame()
    {
        // Three times the starting life is not unreachable in this game.
        assertEquals(USABLE, LifeBar.fill(BAR_W, 24000, 8000));
        assertEquals(USABLE, LifeBar.overflow(BAR_W, 24000, 8000));
    }

    @Test
    void anUnstatedStartingTotalFallsBackToTheEngineDefault()
    {
        // A snapshot from before this field existed, or an EMPTY one, carries
        // zero. It has to read as the engine's own default rather than as a
        // division by zero or an empty bar.
        assertEquals(LifeBar.fill(BAR_W, 4000, LifeBar.DEFAULT_LIFE_POINTS),
            LifeBar.fill(BAR_W, 4000, 0));
        assertEquals(USABLE, LifeBar.fill(BAR_W, LifeBar.DEFAULT_LIFE_POINTS, 0));
    }

    @Test
    void negativeLifeDoesNotDrawBackwards()
    {
        assertTrue(LifeBar.fill(BAR_W, -500, 8000) >= 0);
        assertTrue(LifeBar.overflow(BAR_W, -500, 8000) >= 0);
    }

    @Test
    void aBarTooSmallToDrawInDoesNotGoNegative()
    {
        // barHeight() on the world board floors at 9px and barW can be small on
        // a narrow window; barW - 4 must not come out below zero.
        assertEquals(0, LifeBar.fill(2, 8000, 8000));
        assertEquals(0, LifeBar.overflow(2, 16000, 8000));
    }
}
