package de.cas_ual_ty.dueldimension.ocg.bot.executor;

import de.cas_ual_ty.dueldimension.ocg.OcgConstants;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The three house rules, checked against the behaviour that was asked for
 * rather than against the reference — because the reference has nothing to say
 * about these cards.
 * <p>
 * Boards are built directly, so each case is one stated situation with one
 * expected answer. No engine, no seeds, no sampling: if a rule stops doing what
 * it was asked to do, exactly one of these fails and names the requirement.
 */
class HouseCardRulesTest
{
    private static final int TWO_PRONGED_ATTACK = 83887306;
    private static final int REINFORCEMENTS = 17814387;
    private static final int SHIELD_AND_SWORD = 52097679;

    /** A face-up attack-position monster. */
    private static BotCard monster(int code, int attack, int defense, int controller, int sequence)
    {
        return new BotCard(code, OcgConstants.POS_FACEUP_ATTACK, OcgConstants.TYPE_MONSTER, 4,
            attack, defense, attack, defense, controller, OcgConstants.LOCATION_MZONE, sequence);
    }

    private static BotCard defender(int code, int attack, int defense, int controller, int sequence)
    {
        return new BotCard(code, OcgConstants.POS_FACEUP_DEFENSE, OcgConstants.TYPE_MONSTER, 4,
            attack, defense, attack, defense, controller, OcgConstants.LOCATION_MZONE, sequence);
    }

    /** Puts a duelist in front of a specific board and asks one rule. */
    private static Duelists.Generic on(List<BotCard> ours, List<BotCard> theirs, int phase, boolean ourTurn)
    {
        Duelists.Generic duelist = new Duelists.Generic();
        duelist.setFields(field(ours), field(theirs));
        // player 0 is us; turnPlayer 0 means our turn, so Duel.Player reads 0.
        duelist.setDuelState(3, phase, ourTurn ? 0 : 1, 0, -1, -1);
        return duelist;
    }

    private static BotField field(List<BotCard> monsters)
    {
        List<BotCard> zone = new ArrayList<>(Arrays.asList(new BotCard[7]));
        for(BotCard card : monsters)
        {
            zone.set(card.sequence(), card);
        }
        return BotField.forTest(zone, 8000, 30);
    }

    /** Invokes a registered ACTIVATE rule the way the dispatcher would. */
    private static boolean fires(Executor duelist, int cardId, BotCard card)
    {
        for(CardExecutor exec : duelist.executors())
        {
            if(exec.type() == ExecutorType.ACTIVATE && exec.cardId() == cardId)
            {
                duelist.setCard(ExecutorType.ACTIVATE, card);
                return exec.func() == null || exec.func().getAsBoolean();
            }
        }
        throw new AssertionError("no ACTIVATE rule registered for " + cardId);
    }

    private static BotCard theCard(int code)
    {
        return new BotCard(code, OcgConstants.POS_FACEUP_ATTACK, OcgConstants.TYPE_TRAP, 0,
            0, 0, 0, 0, 0, OcgConstants.LOCATION_SZONE, 0);
    }

    // ---- Two-Pronged Attack: dire threats over 2000 only ----

    @Test
    void twoProngedAttackWaitsForAThreatOverTwoThousand()
    {
        List<BotCard> ours = List.of(monster(1, 1400, 1200, 0, 0), monster(2, 1000, 1000, 0, 1));

        // 2000 exactly is not "over 2000": it must be held.
        Duelists.Generic held = on(ours, List.of(monster(9, 2000, 1500, 1, 0)),
            OcgConstants.PHASE_MAIN1, true);
        assertFalse(fires(held, TWO_PRONGED_ATTACK, theCard(TWO_PRONGED_ATTACK)),
            "spent two monsters on a 2000 ATK monster, which is not a dire threat");

        Duelists.Generic fired = on(ours, List.of(monster(9, 2400, 1500, 1, 0)),
            OcgConstants.PHASE_MAIN1, true);
        assertTrue(fires(fired, TWO_PRONGED_ATTACK, theCard(TWO_PRONGED_ATTACK)),
            "held Two-Pronged Attack against a 2400 ATK threat");
    }

    @Test
    void twoProngedAttackTakesTheStrongestAndGivesUpTheWeakest()
    {
        List<BotCard> ours = List.of(
            monster(1, 1800, 1600, 0, 0),   // our best, must survive
            monster(2, 600, 400, 0, 1),     // cheapest
            monster(3, 900, 700, 0, 2));    // second cheapest
        List<BotCard> theirs = List.of(
            monster(8, 2200, 1000, 1, 0),
            monster(9, 2900, 1200, 1, 1));  // the strongest, must be the target

        Duelists.Generic duelist = on(ours, theirs, OcgConstants.PHASE_MAIN1, true);
        assertTrue(fires(duelist, TWO_PRONGED_ATTACK, theCard(TWO_PRONGED_ATTACK)));

        List<BotCard> chosen = duelist.takeSelection();
        assertEquals(3, chosen.size(), "expected one enemy plus two of ours");
        assertEquals(9, chosen.get(0).code(), "did not target the strongest threat");
        assertEquals(600, chosen.get(1).getDefensePower(), "did not give up the least valuable monster");
        assertEquals(900, chosen.get(2).getDefensePower(), "did not give up the second least valuable");
    }

    @Test
    void twoProngedAttackNeedsTwoMonstersToGive()
    {
        Duelists.Generic duelist = on(List.of(monster(1, 1400, 1200, 0, 0)),
            List.of(monster(9, 2900, 1200, 1, 0)), OcgConstants.PHASE_MAIN1, true);
        assertFalse(fires(duelist, TWO_PRONGED_ATTACK, theCard(TWO_PRONGED_ATTACK)),
            "fired with only one monster to sacrifice");
    }

    // ---- Reinforcements: only a battle it turns ----

    @Test
    void reinforcementsFiresOnlyWhenTheFiveHundredDecidesTheBattle()
    {
        BotCard ourAttacker = monster(1, 1600, 1200, 0, 0);

        // 1600 vs 1900: loses now, wins at 2100. Worth it.
        Duelists.Generic turns = on(List.of(ourAttacker), List.of(monster(9, 1900, 1000, 1, 0)),
            OcgConstants.PHASE_BATTLE, true);
        assertTrue(fires(turns, REINFORCEMENTS, theCard(REINFORCEMENTS)),
            "declined a boost that would have won the battle");
        assertEquals(1, turns.takeSelection().size(), "did not name a target");

        // 1600 vs 1400: already winning, so the card is wasted.
        Duelists.Generic alreadyWinning = on(List.of(ourAttacker), List.of(monster(9, 1400, 1000, 1, 0)),
            OcgConstants.PHASE_BATTLE, true);
        assertFalse(fires(alreadyWinning, REINFORCEMENTS, theCard(REINFORCEMENTS)),
            "spent Reinforcements on a battle it was already winning");

        // 1600 vs 2600: still loses at 2100, so the card changes nothing.
        Duelists.Generic hopeless = on(List.of(ourAttacker), List.of(monster(9, 2600, 1000, 1, 0)),
            OcgConstants.PHASE_BATTLE, true);
        assertFalse(fires(hopeless, REINFORCEMENTS, theCard(REINFORCEMENTS)),
            "spent Reinforcements on a battle it still loses");
    }

    @Test
    void reinforcementsProtectsOnTheOpponentsTurnToo()
    {
        // Being attacked: same arithmetic, their turn. It must still fire.
        Duelists.Generic duelist = on(List.of(monster(1, 1600, 1200, 0, 0)),
            List.of(monster(9, 1900, 1000, 1, 0)), OcgConstants.PHASE_BATTLE, false);
        assertTrue(fires(duelist, REINFORCEMENTS, theCard(REINFORCEMENTS)),
            "did not protect a monster it could have saved");
    }

    @Test
    void reinforcementsIgnoresMonstersItCannotHelp()
    {
        // A monster in defence gains ATK it will never use.
        Duelists.Generic duelist = on(List.of(defender(1, 1600, 1200, 0, 0)),
            List.of(monster(9, 1900, 1000, 1, 0)), OcgConstants.PHASE_BATTLE, false);
        assertFalse(fires(duelist, REINFORCEMENTS, theCard(REINFORCEMENTS)),
            "boosted the ATK of a monster sitting in defence");
    }

    // ---- Shield & Sword: only when inverting makes a kill available ----

    @Test
    void shieldAndSwordFiresWhenInvertingTurnsALossIntoAKill()
    {
        // Ours 1000/2000, theirs 2500/500. Now: 1000 vs 2500, we lose.
        // After the swap: ours swings 2000, theirs defends on 500. We kill it.
        Duelists.Generic duelist = on(List.of(monster(1, 1000, 2000, 0, 0)),
            List.of(monster(9, 2500, 500, 1, 0)), OcgConstants.PHASE_MAIN1, true);
        assertTrue(fires(duelist, SHIELD_AND_SWORD, theCard(SHIELD_AND_SWORD)),
            "missed a kill the swap would have made available");
    }

    @Test
    void shieldAndSwordHoldsWhenTheSwapChangesNothing()
    {
        // Ours 2000/1000 already beats theirs 1500/1200, so it is not needed.
        Duelists.Generic winning = on(List.of(monster(1, 2000, 1000, 0, 0)),
            List.of(monster(9, 1500, 1200, 1, 0)), OcgConstants.PHASE_MAIN1, true);
        assertFalse(fires(winning, SHIELD_AND_SWORD, theCard(SHIELD_AND_SWORD)),
            "spent Shield & Sword on a fight it was already winning");

        // Ours 1000/1200 vs theirs 2500/2400: the swap leaves us losing.
        Duelists.Generic stillLosing = on(List.of(monster(1, 1000, 1200, 0, 0)),
            List.of(monster(9, 2500, 2400, 1, 0)), OcgConstants.PHASE_MAIN1, true);
        assertFalse(fires(stillLosing, SHIELD_AND_SWORD, theCard(SHIELD_AND_SWORD)),
            "swapped into a board where it still cannot kill anything");
    }

    @Test
    void shieldAndSwordIsOurTurnOnly()
    {
        // The swap lasts one turn and exists to enable an attack, so on their
        // turn it only hands them the same opportunity.
        Duelists.Generic duelist = on(List.of(monster(1, 1000, 2000, 0, 0)),
            List.of(monster(9, 2500, 500, 1, 0)), OcgConstants.PHASE_MAIN1, false);
        assertFalse(fires(duelist, SHIELD_AND_SWORD, theCard(SHIELD_AND_SWORD)),
            "swapped stats on the opponent's turn, when we cannot attack into it");
    }
}
