package de.cas_ual_ty.dueldimension.clientutil;

import de.cas_ual_ty.dueldimension.ocg.prompt.DuelEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Measures the playback queue with a controlled clock, because "the code looks
 * right" repeatedly failed to predict what a duel actually did on screen.
 * <p>
 * The invariants pinned here are the reference client's, from
 * {@code gframe/duelclient.cpp}: one event plays at a time, each holds the
 * stream for its own duration (WaitFrameSignal's frame counts at 60fps), a
 * board commit only lands after the events before it, and live play is never
 * compressed — EDOPro's only speed-up is {@code isCatchingUp}, which is for
 * replays and reconnects, not for a busy turn.
 */
class DuelAnimationsPacingTest
{
    /** The queue under test, with sounds stubbed out (no Minecraft here). */
    private static DuelAnimations silent()
    {
        return new DuelAnimations()
        {
            @Override
            protected void playSound(DuelEvent event)
            {
            }
        };
    }

    private static DuelEvent event(DuelEvent.Kind kind)
    {
        return new DuelEvent(kind, 0, -1, -1, 0, 1);
    }

    /** Runs the clock in 50ms client ticks, returning each commit's time. */
    private static List<Long> playOut(DuelAnimations animations, long untilMs)
    {
        List<Long> commits = new ArrayList<>();
        for(long now = 0; now <= untilMs; now += 50)
        {
            animations.tick(now);
        }
        return commits;
    }

    @Test
    void eventsHoldTheStreamForTheirOwnDurations()
    {
        DuelAnimations animations = silent();
        List<Long> commits = new ArrayList<>();
        long[] clock = {0};

        // Three updates as the server sends them: announce, slide, phase.
        animations.accept(List.of(event(DuelEvent.Kind.SUMMON)), () -> commits.add(clock[0]));
        animations.accept(List.of(event(DuelEvent.Kind.MOVE)), () -> commits.add(clock[0]));
        animations.accept(List.of(event(DuelEvent.Kind.PHASE)), () -> commits.add(clock[0]));

        for(clock[0] = 0; clock[0] <= 4000; clock[0] += 50)
        {
            animations.tick(clock[0]);
        }

        assertEquals(3, commits.size(), "every board commit must eventually run");

        // The frame counts, at 60fps with 50ms tick granularity:
        // SUMMON = 41 frames = 683ms, MOVE = 10 = 167ms, PHASE = 40 = 667ms.
        assertTrue(Math.abs(commits.get(0) - 683) <= 60,
            "summon should hold ~683ms, held " + commits.get(0));
        long moveGap = commits.get(1) - commits.get(0);
        assertTrue(Math.abs(moveGap - 167) <= 60, "move should hold ~167ms, held " + moveGap);
        long phaseGap = commits.get(2) - commits.get(1);
        assertTrue(Math.abs(phaseGap - 667) <= 60, "phase should hold ~667ms, held " + phaseGap);
    }

    @Test
    void nothingCommitsOnArrival()
    {
        DuelAnimations animations = silent();
        boolean[] committed = {false};
        animations.accept(List.of(event(DuelEvent.Kind.SUMMON)), () -> committed[0] = true);

        animations.tick(0);
        assertTrue(!committed[0],
            "the board must not be applied the moment its update arrives; that is the "
                + "cards-appear-before-their-animation bug");
        assertTrue(animations.isBusy(), "the event must be playing instead");
    }

    /**
     * A full opponent turn — announce+move pairs, phases, an attack, damage —
     * must play at exactly full pace. The old backlog scale compressed this to
     * 0.7x (and busy turns to 0.4x) because its threshold was below one turn's
     * event count and it counted the zero-length commit steps as backlog.
     */
    @Test
    void aWholeTurnQueuedAtOnceIsNotCompressed()
    {
        DuelAnimations animations = silent();
        List<Long> commits = new ArrayList<>();
        long[] clock = {0};

        DuelEvent.Kind[] turn = {
            DuelEvent.Kind.NEW_TURN, DuelEvent.Kind.DRAW, DuelEvent.Kind.PHASE,
            DuelEvent.Kind.PHASE, DuelEvent.Kind.SUMMON, DuelEvent.Kind.MOVE,
            DuelEvent.Kind.SET, DuelEvent.Kind.MOVE, DuelEvent.Kind.PHASE,
            DuelEvent.Kind.ATTACK, DuelEvent.Kind.DAMAGE, DuelEvent.Kind.PHASE,
            DuelEvent.Kind.PHASE
        };
        long expected = 0;
        for(DuelEvent.Kind kind : turn)
        {
            animations.accept(List.of(event(kind)), () -> commits.add(clock[0]));
            expected += switch(kind)
            {
                case NEW_TURN, PHASE, ATTACK -> 667;
                case DRAW -> 83;
                case SUMMON -> 683;
                case MOVE -> 167;
                case SET -> 83;
                // Damage holds for half its duration before the next event.
                case DAMAGE -> 342;
                default -> 0;
            };
        }

        for(clock[0] = 0; clock[0] <= 15000; clock[0] += 50)
        {
            animations.tick(clock[0]);
        }

        assertEquals(turn.length, commits.size(), "every commit must run");
        long total = commits.get(commits.size() - 1);
        assertTrue(Math.abs(total - expected) <= 50L * turn.length,
            "a queued turn must play at full pace (expected ~" + expected + "ms, took " + total
                + "ms): live play is never compressed, only genuine multi-turn catch-up is");
    }
}
