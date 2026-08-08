package de.cas_ual_ty.dueldimension.ocg.bot.executor;

import de.cas_ual_ty.dueldimension.ocg.OcgConstants;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Joey-only decisions that deliberately differ from generic play. */
class JoeyRulesTest
{
    private static final int TIME_WIZARD = 71625222;

    private static BotCard monster(int code, int attack, int controller, int sequence)
    {
        return new BotCard(code, OcgConstants.POS_FACEUP_ATTACK, OcgConstants.TYPE_MONSTER, 4,
            attack, attack, attack, attack, controller, OcgConstants.LOCATION_MZONE, sequence);
    }

    private static BotField field(int lifePoints, BotCard... monsters)
    {
        List<BotCard> zone = new ArrayList<>(Arrays.asList(new BotCard[7]));
        for(BotCard card : monsters)
        {
            zone.set(card.sequence(), card);
        }
        return BotField.forTest(zone, lifePoints, 30);
    }

    private static Duelists.Joey joey(int lifePoints, List<BotCard> ours, List<BotCard> theirs)
    {
        Duelists.Joey joey = new Duelists.Joey();
        joey.setFields(field(lifePoints, ours.toArray(BotCard[]::new)),
            field(8000, theirs.toArray(BotCard[]::new)));
        joey.setDuelState(5, OcgConstants.PHASE_MAIN1, 0, 0, -1, -1);
        return joey;
    }

    private static boolean activates(Executor duelist)
    {
        BotCard timeWizard = monster(TIME_WIZARD, 500, 0, 0);
        for(CardExecutor exec : duelist.executors())
        {
            if(exec.type() == ExecutorType.ACTIVATE && exec.cardId() == TIME_WIZARD)
            {
                duelist.setCard(ExecutorType.ACTIVATE, timeWizard);
                return exec.func() == null || exec.func().getAsBoolean();
            }
        }
        throw new AssertionError("Joey has no Time Wizard activation rule");
    }

    @Test
    void timeWizardIsJoeysRuleOnly()
    {
        assertTrue(hasTimeWizardRule(new Duelists.Joey()));
        assertFalse(hasTimeWizardRule(new Duelists.Generic()));
        assertFalse(hasTimeWizardRule(new Duelists.Yugi()));
        assertFalse(hasTimeWizardRule(new Duelists.Kaiba()));
    }

    @Test
    void joeyGamblesWhenLowOnLifeAndOutpowered()
    {
        Duelists.Joey joey = joey(1800,
            List.of(monster(TIME_WIZARD, 500, 0, 0)),
            List.of(monster(9, 2400, 1, 0)));
        assertTrue(activates(joey));
    }

    @Test
    void joeyGamblesWhenOutnumberedOrFacingLethal()
    {
        Duelists.Joey outnumbered = joey(8000,
            List.of(monster(TIME_WIZARD, 500, 0, 0)),
            List.of(monster(8, 1800, 1, 0), monster(9, 1700, 1, 1),
                monster(10, 1600, 1, 2)));
        assertTrue(activates(outnumbered));

        Duelists.Joey lethal = joey(3000,
            List.of(monster(TIME_WIZARD, 500, 0, 0)),
            List.of(monster(9, 3000, 1, 0)));
        assertTrue(activates(lethal));
    }

    @Test
    void joeyKeepsTheCoinInHisPocketWhenHeCanContestTheBoard()
    {
        Duelists.Joey healthy = joey(8000,
            List.of(monster(TIME_WIZARD, 500, 0, 0)),
            List.of(monster(9, 2400, 1, 0)));
        assertFalse(activates(healthy));

        Duelists.Joey stronger = joey(1800,
            List.of(monster(TIME_WIZARD, 500, 0, 0), monster(7, 2600, 0, 1)),
            List.of(monster(9, 2400, 1, 0)));
        assertFalse(activates(stronger));
    }

    @Test
    void desperateJoeySummonsTimeWizardFaceUp()
    {
        Duelists.Joey joey = joey(1800, List.of(), List.of(monster(9, 2400, 1, 0)));
        BotCard timeWizard = monster(TIME_WIZARD, 500, 0, 0);
        assertFalse(joey.onSelectMonsterSummonOrSet(timeWizard),
            "Time Wizard was set face-down when Joey needed its effect");
    }

    private static boolean hasTimeWizardRule(Executor duelist)
    {
        return duelist.executors().stream().anyMatch(exec ->
            exec.type() == ExecutorType.ACTIVATE && exec.cardId() == TIME_WIZARD);
    }
}
