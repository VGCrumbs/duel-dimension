package de.cas_ual_ty.dueldimension.shop;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a duel against another player pays.
 * <p>
 * The rule is pulled out of the duel loop so it can be checked without a
 * server, two players and a running engine — the loop's job is to notice the
 * contest ended and to find the two people in it, and that is a different job
 * from deciding what they are owed.
 */
class DuelRewardTest
{
    @Test
    void theWinnerIsPaidMoreThanTheLoser()
    {
        assertEquals(1000, DuelPoints.rewardFor(2, 1), "winning a match pays 1000");
        assertEquals(500, DuelPoints.rewardFor(1, 2), "and losing it pays 500");
        assertEquals(1000, DuelPoints.rewardFor(1, 0), "a single duel is the same rule");
        assertEquals(500, DuelPoints.rewardFor(0, 1));
    }

    @Test
    void aDrawPaysBothTheLosingRate()
    {
        // Nobody won it. Paying nobody would punish two players for a game that
        // ran long, and paying both the winner's rate would make a stall the
        // most profitable way to duel.
        assertEquals(500, DuelPoints.rewardFor(1, 1));
        assertEquals(500, DuelPoints.rewardFor(0, 0));
    }

    @Test
    void losingStillPays()
    {
        // Deliberate. If losing paid nothing, the way to earn would be to avoid
        // opponents who might beat you, which is the opposite of the point.
        assertTrue(DuelPoints.LOSS_REWARD > 0);
        assertTrue(DuelPoints.WIN_REWARD > DuelPoints.LOSS_REWARD);
    }

    @Test
    void aWinBuysMoreThanALoss()
    {
        // The reward only means something against what it buys, so it is
        // measured against the shop's price rather than left as a bare number:
        // a win is six packs and a loss three, at the reference's 150 DP.
        assertEquals(6, DuelPoints.WIN_REWARD / ShopStock.BASE_PRICE);
        assertEquals(3, DuelPoints.LOSS_REWARD / ShopStock.BASE_PRICE);
    }
}
