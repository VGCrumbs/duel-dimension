package de.cas_ual_ty.dueldimension.shop;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuelAssessmentTest
{
    @Test
    void everyVisibleLineAddsToTheCommittedTotal()
    {
        DuelReward.Breakdown reward = DuelReward.calculate(DuelReward.Outcome.WIN,
            metrics(8, 800, 40, false, false), 1D);

        assertEquals(reward.lines().stream().mapToInt(DuelReward.Line::amount).sum(),
            reward.total());
        assertTrue(reward.lines().stream().anyMatch(line -> line.id().equals("low_lp")));
        assertFalse(reward.lines().stream().anyMatch(line -> line.id().equals("critical_lp")));
    }

    @Test
    void criticalLifeAndEmptyDeckThresholdsStackLikeTagForce()
    {
        DuelReward.Breakdown reward = DuelReward.calculate(DuelReward.Outcome.WIN,
            metrics(9, 100, 0, true, false), 1D);

        assertTrue(has(reward, "low_lp"));
        assertTrue(has(reward, "critical_lp"));
        assertTrue(has(reward, "low_deck"));
        assertTrue(has(reward, "zero_deck"));
    }

    @Test
    void npcScaleChangesEveryLineAndStillExplainsTheExactTotal()
    {
        DuelReward.Metrics metrics = metrics(6, 4000, 22, false, false);
        DuelReward.Breakdown pvp = DuelReward.calculate(DuelReward.Outcome.WIN, metrics, 1D);
        DuelReward.Breakdown npc = DuelReward.calculate(DuelReward.Outcome.WIN, metrics, 0.65D);

        assertTrue(npc.total() < pvp.total());
        assertEquals(npc.lines().stream().mapToInt(DuelReward.Line::amount).sum(), npc.total());
        assertEquals(pvp.lines().size(), npc.lines().size());
    }

    @Test
    void forfeitingCannotFarmUnusedCardBonuses()
    {
        DuelReward.Breakdown reward = DuelReward.calculate(DuelReward.Outcome.LOSS,
            metrics(2, 8000, 40, false, true), 1D);

        assertEquals(85, reward.total()); // 75 completion + 2 turns × 5
        assertEquals(2, reward.lines().size());
        assertFalse(has(reward, "no_spells"));
        assertFalse(has(reward, "no_special"));
    }

    private static boolean has(DuelReward.Breakdown reward, String id)
    {
        return reward.lines().stream().anyMatch(line -> line.id().equals(id));
    }

    private static DuelReward.Metrics metrics(int turns, int lifePoints, int deck,
        boolean exactZero, boolean forfeit)
    {
        return new DuelReward.Metrics(1, turns, lifePoints, 8000, deck,
            lifePoints < 8000, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, false, false, false, false, exactZero, forfeit);
    }
}
