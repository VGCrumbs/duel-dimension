package de.cas_ual_ty.dueldimension.ocg.prompt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A position change is two questions, and the animation needs both answers.
 * <p>
 * The event used to carry one bit — "does it end face-up" — which made all
 * three of these look identical to the client: a card turning over. Switching
 * to defence turns nothing over, it lies down. A set card flipped up does both
 * at once. Drawing the same motion for all three is why a repositioning card
 * flipped and then snapped into its new footprint.
 * <p>
 * These are the transitions a duel can actually produce, each named for what a
 * player would call it.
 */
class PositionChangeTest
{
    private static DuelEvent change(boolean faceUp, boolean defence,
        boolean wasFaceUp, boolean wasDefence)
    {
        return new DuelEvent(DuelEvent.Kind.POSITION, 12345678, -1, 0,
            DuelEvent.posture(faceUp, defence, wasFaceUp, wasDefence), 0);
    }

    /** Set monster flip summoned: face-down defence to face-up attack. */
    @Test
    void aFlipSummonBothTurnsOverAndStandsUp()
    {
        DuelEvent event = change(true, false, false, true);
        assertTrue(DuelEvent.turnsOver(event));
        assertTrue(DuelEvent.liesDown(event), "it changes posture too");
        assertTrue(DuelEvent.endsFaceUp(event));
        assertFalse(DuelEvent.endsInDefence(event));
    }

    /** Face-up attack to face-up defence: it lies down and shows the same face. */
    @Test
    void switchingToDefenceDoesNotTurnTheCardOver()
    {
        DuelEvent event = change(true, true, true, false);
        assertFalse(DuelEvent.turnsOver(event), "nothing is revealed by lying down");
        assertTrue(DuelEvent.liesDown(event));
        assertTrue(DuelEvent.endsInDefence(event));
    }

    /** And back the other way. */
    @Test
    void switchingToAttackDoesNotTurnTheCardOverEither()
    {
        DuelEvent event = change(true, false, true, true);
        assertFalse(DuelEvent.turnsOver(event));
        assertTrue(DuelEvent.liesDown(event), "posture changed");
        assertFalse(DuelEvent.endsInDefence(event));
    }

    /**
     * Face-down defence to face-up defence, which is what a flip effect does.
     * It turns over and stays lying down — the case that proves the two bits
     * are genuinely independent.
     */
    @Test
    void aFlipEffectTurnsOverWithoutStandingUp()
    {
        DuelEvent event = change(true, true, false, true);
        assertTrue(DuelEvent.turnsOver(event));
        assertFalse(DuelEvent.liesDown(event), "it was lying down and still is");
        assertTrue(DuelEvent.endsInDefence(event));
    }

    /** Turning a face-up attacker face-down, which some effects do. */
    @Test
    void beingTurnedFaceDownIsRead()
    {
        DuelEvent event = change(false, true, true, false);
        assertTrue(DuelEvent.turnsOver(event));
        assertTrue(DuelEvent.liesDown(event));
        assertFalse(DuelEvent.endsFaceUp(event));
    }

    /**
     * A flip summon arriving as {@link DuelEvent.Kind#FLIP} carries amount 1,
     * which predates the second bit.
     * <p>
     * Bit 0 kept its old meaning precisely so this still reads as "ends
     * face-up, turned over, in attack" rather than as something new.
     */
    @Test
    void theOlderFlipEncodingStillReads()
    {
        DuelEvent flip = new DuelEvent(DuelEvent.Kind.FLIP, 12345678, -1, 0, 1, 0);
        assertTrue(DuelEvent.endsFaceUp(flip));
        assertTrue(DuelEvent.turnsOver(flip));
        assertFalse(DuelEvent.endsInDefence(flip));
        assertFalse(DuelEvent.liesDown(flip));
    }

    /**
     * But a flip summon must SAY it was lying down, or it does not stand up.
     * <p>
     * This is the bug the older encoding hid. "Ends face up" alone leaves the
     * posture bits zero, which reads as a card that was already in attack — so
     * the animation turned it over without the quarter turn, and a set monster
     * flip summoned stayed lying on its side.
     */
    @Test
    void aFlipSummonStandsUpAsWellAsTurningOver()
    {
        DuelEvent flip = new DuelEvent(DuelEvent.Kind.FLIP, 12345678, -1, 0,
            DuelEvent.posture(true, false, false, true), 0);
        assertTrue(DuelEvent.turnsOver(flip), "face-down to face-up");
        assertTrue(DuelEvent.liesDown(flip), "defence to attack is a posture change");
        assertFalse(DuelEvent.endsInDefence(flip), "it ends standing up");
    }

    /**
     * A MOVE says what the card is arriving AS, not what it is turning into.
     * <p>
     * Both ends carry the destination, so nothing reads it as a turn: a card
     * being set is face down before it leaves the hand and lands face down, and
     * a monster special summoned in defence is lying flat the whole way.
     * <p>
     * The client cannot work this out for itself. The board is not applied
     * until the events it arrived with have played, so a card asking its
     * destination zone what is landing there is told "nothing" — which is how a
     * set card slid across the table wearing its own face and standing upright.
     */
    @Test
    void aMoveCarriesTheArrivalPostureAndIsNotATurn()
    {
        DuelEvent set = new DuelEvent(DuelEvent.Kind.MOVE, 12345678, -1, 0,
            DuelEvent.posture(false, true, false, true), 0);
        assertFalse(DuelEvent.endsFaceUp(set), "a set card arrives face down");
        assertTrue(DuelEvent.endsInDefence(set), "and lying down");
        assertFalse(DuelEvent.turnsOver(set), "it is not turning over on the way");
        assertFalse(DuelEvent.liesDown(set), "nor lying down on the way");

        DuelEvent summon = new DuelEvent(DuelEvent.Kind.MOVE, 12345678, -1, 0,
            DuelEvent.posture(true, true, true, true), 0);
        assertTrue(DuelEvent.endsFaceUp(summon), "a defence special summon is face up");
        assertTrue(DuelEvent.endsInDefence(summon), "and lying down");
        assertFalse(DuelEvent.turnsOver(summon));
        assertFalse(DuelEvent.liesDown(summon));
    }
}
