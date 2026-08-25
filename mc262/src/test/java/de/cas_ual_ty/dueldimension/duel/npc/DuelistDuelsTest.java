package de.cas_ual_ty.dueldimension.duel.npc;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuelistDuelsTest
{
    @SuppressWarnings("unchecked")
    private static Map<DuelistDuels.Watcher, DuelistDuels.RunningDuel> active() throws Exception
    {
        Field field = DuelistDuels.class.getDeclaredField("ACTIVE");
        field.setAccessible(true);
        return (Map<DuelistDuels.Watcher, DuelistDuels.RunningDuel>)field.get(null);
    }

    @Test
    void releasingSurrenderedNpcDuelImmediatelyFreesItsPlayerForRematch() throws Exception
    {
        DuelistDuels.Watcher player = new DuelistDuels.Watcher(UUID.randomUUID(), false);
        DuelistDuels.RunningDuel surrendered =
            new DuelistDuels.RunningDuel(null, player, null, null);
        Map<DuelistDuels.Watcher, DuelistDuels.RunningDuel> active = active();
        active.put(player, surrendered);
        assertSame(surrendered, active.get(player));

        DuelistDuels.releaseSeats(null, surrendered);

        assertFalse(active.containsKey(player));
    }

    @Test
    void lateCleanupCannotRemoveANewerRematch() throws Exception
    {
        DuelistDuels.Watcher player = new DuelistDuels.Watcher(UUID.randomUUID(), false);
        DuelistDuels.RunningDuel surrendered =
            new DuelistDuels.RunningDuel(null, player, null, null);
        DuelistDuels.RunningDuel rematch =
            new DuelistDuels.RunningDuel(null, player, null, null);
        Map<DuelistDuels.Watcher, DuelistDuels.RunningDuel> active = active();
        active.put(player, rematch);

        DuelistDuels.releaseSeats(null, surrendered);

        assertSame(rematch, active.remove(player));
    }

    @Test
    void surrenderDisqualifiesOnlyThatSeatFromDpRewards()
    {
        DuelistDuels.Watcher first = new DuelistDuels.Watcher(UUID.randomUUID(), false);
        DuelistDuels.Watcher second = new DuelistDuels.Watcher(UUID.randomUUID(), false);
        DuelistDuels.RunningDuel duel =
            new DuelistDuels.RunningDuel(null, first, second, null);

        duel.disqualifyReward(0);

        assertFalse(duel.canReward(0));
        assertTrue(duel.canReward(1));
    }

    @Test
    void surrenderRewardDisqualificationSurvivesMatchGameTransition()
    {
        DuelistDuels.Watcher first = new DuelistDuels.Watcher(UUID.randomUUID(), false);
        DuelistDuels.Watcher second = new DuelistDuels.Watcher(UUID.randomUUID(), false);
        DuelistDuels.RunningDuel previous =
            new DuelistDuels.RunningDuel(null, first, second, null);
        DuelistDuels.RunningDuel next =
            new DuelistDuels.RunningDuel(null, first, second, null);
        previous.disqualifyReward(1);

        next.carryRewardEligibilityFrom(previous);

        assertTrue(next.canReward(0));
        assertFalse(next.canReward(1));
    }

    @Test
    void duelConclusionCanOnlyBeClaimedOnce()
    {
        DuelistDuels.RunningDuel duel =
            new DuelistDuels.RunningDuel(null, null, null, null);

        assertTrue(duel.beginConclusion());
        assertFalse(duel.beginConclusion());
    }
}
