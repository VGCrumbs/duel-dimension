package de.cas_ual_ty.dueldimension.ocg.bot.executor;

import de.cas_ual_ty.dueldimension.ocg.OcgConstants;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bot walking every monster it has into a Marshmallon.
 * <p>
 * Marshmallon reads "Cannot be destroyed by battle." as its first sentence and
 * sits in face-down defence, which is the whole point of it. Attacking it with a
 * monster that cannot pierce achieves nothing: it survives, no damage is dealt
 * either way, and the attack is spent.
 * <p>
 * The rules that should stop this were each verified on their own — the text
 * classifier reads Marshmallon as ALWAYS protected, and the executor declines an
 * ALWAYS defender — but never together, through the method the battle phase
 * actually calls. That gap is what this closes: it runs the real
 * {@code onSelectAttackTarget} against a real defender and asserts no attack
 * comes out of it.
 */
class MarshmallonTest
{
    private static final String MARSHMALLON =
        "Cannot be destroyed by battle. After damage calculation, if this card was "
            + "attacked, and was face-down at the start of the Damage Step: The "
            + "attacking player takes 1000 damage.";

    /**
     * A monster in a zone.
     * <p>
     * The controller matters and is easy to get wrong: {@code textOf} below
     * tells attacker from defender by it, and building both on the same side
     * hands the attacker the defender's rules text — which is how the piercing
     * case first came out backwards.
     */
    private static BotCard monster(int code, int position, int attack, int defence,
        int controller)
    {
        return new BotCard(code, position, OcgConstants.TYPE_MONSTER, 4, attack, defence,
            controller, OcgConstants.LOCATION_MZONE, 0);
    }

    /** Ours, which does the attacking. */
    private static BotCard attacker(int attack)
    {
        return monster(0, OcgConstants.POS_FACEUP_ATTACK, attack, 1000, 0);
    }

    /** Theirs, which is the one with the shield. */
    private static BotCard defender(int position)
    {
        return monster(31305911, position, 300, 500, 1);
    }

    /**
     * The real executor, with only the database lookup replaced.
     * <p>
     * Everything under test — the protection reading, the damage exceptions,
     * the per-turn arithmetic — runs exactly as it does in a duel. Only "what
     * does this card say", which needs 13,000 files on disk, is supplied here.
     */
    private static DefaultExecutor executorSaying(String defenderText, String attackerText)
    {
        return new DefaultExecutor()
        {
            @Override
            protected String textOf(BotCard card)
            {
                return card != null && card.controller() == 1 ? defenderText : attackerText;
            }
        };
    }

    @Test
    void aStrongerMonsterDoesNotAttackAFaceDownMarshmallon()
    {
        DefaultExecutor executor = executorSaying(MARSHMALLON, "");
        BotCard attacker = attacker(2000);
        BotCard marshmallon = defender(OcgConstants.POS_FACEDOWN_DEFENSE);

        assertFalse(executor.onPreBattleBetween(attacker, marshmallon),
            "nothing about this attack achieves anything");
        assertNull(executor.onSelectAttackTarget(attacker, List.of(marshmallon)),
            "so no attack should be declared");
    }

    /** And not with six of them either, which is what was reported. */
    @Test
    void noNumberOfAttackersMakesItWorthwhile()
    {
        DefaultExecutor executor = executorSaying(MARSHMALLON, "");
        BotCard marshmallon = defender(OcgConstants.POS_FACEDOWN_DEFENSE);
        for(int remaining = 6; remaining >= 1; remaining--)
        {
            executor.setAttackersLeft(remaining);
            BotCard attacker = attacker(2000);
            assertNull(executor.onSelectAttackTarget(attacker, List.of(marshmallon)),
                "declined with " + remaining + " attackers left; a shield that never "
                    + "lapses is not worn down by numbers");
        }
    }

    /** Face-UP defence is the same answer: the shield does not care. */
    @Test
    void norFaceUp()
    {
        DefaultExecutor executor = executorSaying(MARSHMALLON, "");
        BotCard attacker = attacker(2000);
        BotCard marshmallon = defender(OcgConstants.POS_FACEUP_DEFENSE);
        assertFalse(executor.onPreBattleBetween(attacker, marshmallon));
    }

    /**
     * But a piercer goes in, because the difference is real damage.
     * <p>
     * The amendment: it is damage or removal that makes an attack worth making,
     * not the kill.
     */
    @Test
    void aPiercerAttacksItAnyway()
    {
        DefaultExecutor executor = executorSaying(MARSHMALLON,
            "If this card attacks a Defense Position monster, inflict piercing battle damage.");
        BotCard attacker = attacker(2000);
        BotCard marshmallon = defender(OcgConstants.POS_FACEUP_DEFENSE);

        assertTrue(executor.onPreBattleBetween(attacker, marshmallon));
        assertSame(marshmallon, executor.onSelectAttackTarget(attacker, List.of(marshmallon)),
            "2000 against 500 defence, pierced, is 1500 damage");
    }

    /**
     * A card the database cannot describe is attacked as it always was.
     * <p>
     * The failure mode worth naming: if the text lookup comes back empty the
     * bot has no way to know anything about the card, and every rule here reads
     * as "no objection". That is the old behaviour rather than a new bug, but it
     * is indistinguishable in a duel from the rule not working — which is why
     * the executor logs it.
     */
    @Test
    void anUndescribedCardFallsBackToAttacking()
    {
        DefaultExecutor executor = executorSaying("", "");
        BotCard attacker = attacker(2000);
        BotCard unknown = defender(OcgConstants.POS_FACEUP_DEFENSE);
        assertTrue(executor.onPreBattleBetween(attacker, unknown));
    }
}
