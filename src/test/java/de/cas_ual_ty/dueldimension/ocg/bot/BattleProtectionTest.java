package de.cas_ual_ty.dueldimension.ocg.bot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reading a monster's rules text for whether attacking it is worth it.
 * <p>
 * <b>Every string below is verbatim from the card database</b>, not text
 * written to suit the parser. That distinction is the whole value of the test:
 * the wordings that broke the first three attempts at this — an unrelated "once
 * per turn" later in the card, a shield that only holds on its controller's
 * turn, a shield granted to a different monster — are all real cards, and each
 * one is here under its own name.
 * <p>
 * The bias is deliberate and asymmetric. Failing to notice a shield costs one
 * bad attack, which is what the bot did before and will do again on any wording
 * this does not know. Inventing a shield costs an attack that should have been
 * made, and a duelist that will not swing looks broken rather than cautious —
 * so anything conditional reads as NONE.
 */
class BattleProtectionTest
{
    @Test
    void plainShieldsAreRead()
    {
        // B.E.S. Big Core, Blue-Eyes Twin Burst Dragon, Arcana Force 0.
        assertEquals(BattleProtection.ALWAYS, BattleProtection.battlesSurvived(
            "Cannot be destroyed by battle."));
        assertEquals(BattleProtection.ALWAYS, BattleProtection.battlesSurvived(
            "This card cannot be destroyed by battle."));
        assertEquals(BattleProtection.ALWAYS, BattleProtection.battlesSurvived(
            "Cannot be destroyed by battle or card effects."));
    }

    @Test
    void perTurnShieldsAreCounted()
    {
        // Fortress Warrior, Gyroid, Mine Mole, Lightray Madoor.
        assertEquals(1, BattleProtection.battlesSurvived(
            "Once per turn, this card cannot be destroyed by battle."));
        // Shield Wing, Zap Mustung, Assault Blackwing - Sayo.
        assertEquals(2, BattleProtection.battlesSurvived(
            "Twice per turn, this card cannot be destroyed by battle."));
    }

    /**
     * An unrelated limit elsewhere on the card must not be read as the
     * shield's.
     * <p>
     * All three are real, and all three fooled a version of this that looked in
     * a fixed window either side of the phrase rather than in the sentence.
     */
    @Test
    void aLimitOnADifferentEffectIsNotTheShieldsLimit()
    {
        // Argostars - Swift Capane: the "once per turn" belongs to the trap.
        assertEquals(BattleProtection.ALWAYS, BattleProtection.battlesSurvived(
            "This card cannot be destroyed by battle. Once per turn, if a "
                + "Continuous Trap is activated: You can draw 1 card."));
        // Armed Dragon Thunder LV10, whose counter tiers each carry their own.
        assertEquals(BattleProtection.ALWAYS, BattleProtection.battlesSurvived(
            "This card cannot be destroyed by battle. 1000+: Once per turn, "
                + "during your Main Phase: You can destroy 1 card."));
    }

    /**
     * A shield the bot cannot evaluate is no shield, and it attacks as before.
     */
    @Test
    void conditionalShieldsAreDeclined()
    {
        // Abyss Actor - Twinkle Little: protected on ITS controller's turn,
        // which is never the turn the bot is attacking on.
        assertEquals(BattleProtection.NONE, BattleProtection.battlesSurvived(
            "This card cannot be destroyed by battle during your turn."));
        // Archfiend's Awakening, and every "except by battle with" printing.
        assertEquals(BattleProtection.NONE, BattleProtection.battlesSurvived(
            "This card cannot be destroyed by battle, except by battle with a "
                + "Ritual Monster."));
        assertEquals(BattleProtection.NONE, BattleProtection.battlesSurvived(
            "While you control a Token, this card cannot be destroyed by battle."));
    }

    /** A shield on somebody ELSE is not a reason to leave this one alone. */
    @Test
    void shieldsGrantedToOtherMonstersAreNotItsOwn()
    {
        // Bi'an, Earth of the Yang Zing.
        assertEquals(BattleProtection.NONE, BattleProtection.battlesSurvived(
            "A Synchro Monster that used this card as Synchro Material cannot "
                + "be destroyed by battle."));
        // Antidote Nurse, which grants it to a target.
        assertEquals(BattleProtection.NONE, BattleProtection.battlesSurvived(
            "If you targeted a monster on your field, it cannot be destroyed by "
                + "battle or card effects this turn."));
    }

    @Test
    void ordinaryMonstersAreUnaffected()
    {
        assertEquals(BattleProtection.NONE, BattleProtection.battlesSurvived(""));
        assertEquals(BattleProtection.NONE, BattleProtection.battlesSurvived(null));
        assertEquals(BattleProtection.NONE, BattleProtection.battlesSurvived(
            "A legendary dragon revered for its power."));
        // Destruction by EFFECT is a different sentence and must not count.
        assertEquals(BattleProtection.NONE, BattleProtection.battlesSurvived(
            "This card cannot be destroyed by card effects."));
    }

    /**
     * The exception the request turns on: attack it anyway if the attack itself
     * answers it.
     */
    @Test
    void attackersThatAnswerAShieldAreRecognised()
    {
        assertTrue(BattleProtection.bypassesProtection(
            "If this card attacks a monster, after damage calculation: Return "
                + "it to the hand."));
        assertTrue(BattleProtection.bypassesProtection(
            "When this card attacks an opponent's monster, at the end of the "
                + "Damage Step: Banish that monster."));
        assertTrue(BattleProtection.bypassesProtection(
            "If this card attacks a Defense Position monster, after damage "
                + "calculation: Destroy that monster."));
    }

    @Test
    void anUnrelatedBounceIsNotABypass()
    {
        // The outcome without the battle trigger. Plenty of cards return
        // something to the hand for reasons that have nothing to do with
        // attacking, and reading those as a bypass would send a monster into a
        // shield it cannot get through.
        assertFalse(BattleProtection.bypassesProtection(
            "Once per turn: You can target 1 monster your opponent controls; "
                + "return it to the hand."));
        assertFalse(BattleProtection.bypassesProtection(
            "If this card attacks a monster, it gains 500 ATK during damage "
                + "calculation only."));
        assertFalse(BattleProtection.bypassesProtection(""));
    }

    /** Curly quotes and mid-sentence line breaks are what the database holds. */
    @Test
    void databasePunctuationDoesNotBreakIt()
    {
        assertEquals(1, BattleProtection.battlesSurvived(
            "Once per turn, this card\ncannot be destroyed  by battle."));
        assertTrue(BattleProtection.bypassesProtection(
            "When this card attacks an opponent’s monster, after damage "
                + "calculation: Return it to the hand."));
    }

    /**
     * Piercing, which is the other way an attack pays off against a wall.
     * <p>
     * A monster that cannot be destroyed by battle still lets the difference
     * through when it is attacked in defence by something that pierces — so the
     * attack is worth making even though nothing dies.
     */
    @Test
    void aMonsterThatPiercesOnItsOwnAttacksIsRecognised()
    {
        // Airknight Parshath, Ancient Gear Golem, and 82 others.
        assertTrue(BattleProtection.pierces(
            "If this card attacks a Defense Position monster, inflict piercing "
                + "battle damage."));
        assertTrue(BattleProtection.pierces(
            "If this card attacks a Defense Position monster, inflict the "
                + "difference as battle damage to your opponent."));
    }

    /**
     * But piercing GRANTED to other monsters is not this monster's.
     * <p>
     * Half the cards that mention piercing hand it to something else — Amazoness
     * Empress, Aromage Bergamot, Ally of Justice Thunder Armor. Reading those as
     * self-piercing would send the wrong monster into a wall.
     */
    @Test
    void piercingGrantedToOtherMonstersIsNotItsOwn()
    {
        assertFalse(BattleProtection.pierces(
            "If your \"Amazoness\" monster attacks a Defense Position monster, "
                + "inflict piercing battle damage to your opponent."));
        assertFalse(BattleProtection.pierces(
            "All monsters you control inflict piercing battle damage."));
        assertFalse(BattleProtection.pierces(""));
        assertFalse(BattleProtection.pierces(
            "If this card attacks a Defense Position monster, it gains 500 ATK."));
    }
}
