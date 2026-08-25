package de.cas_ual_ty.dueldimension.clientutil.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which monster is swinging, which is flinching, and how far through.
 * <p>
 * The duel step is now sized from the clip, so the two run together -- but the
 * phase here is wall-clock elapsed rather than a fraction of any window, which
 * is what keeps the swing at its authored speed regardless. What is pinned is
 * that it plays ONCE and stops, that its length comes from the monster's own
 * file, and that a square changing hands does not hand the swing to whoever
 * takes it.
 */
class BattleAnimationsTest
{
    private static final int ATTACKER = 8;        // own side, monster zone, seq 0
    private static final int DEFENDER = 16 | 8 | 2;
    private static final long DRAGON = 28279543L;
    private static final long OTHER = 74677422L;
    private static final long START = 1_000_000L;

    @BeforeEach
    void forget()
    {
        BattleAnimations.clear();
        // Authored speed, so the phases below read as the seconds they are.
        // The speed itself is covered on its own further down.
        AnimationSettings.setSpeed(1F);
    }

    /** The two beats at one instant, which is what the old single latch did. */
    private static void latchBoth(long at)
    {
        BattleAnimations.latch(ATTACKER, at, DRAGON);
        BattleAnimations.latchHurt(DEFENDER, at, OTHER);
    }

    @Test
    void theFlinchRunsFromTheBlowRatherThanTheDeclaration()
    {
        // The whole point of the split: an attack declared at START whose damage
        // step lands two seconds later. The defender is two seconds behind the
        // attacker, not level with it -- and an attack that never reaches its
        // damage step leaves the defender's latch untouched entirely.
        BattleAnimations.latch(ATTACKER, START, DRAGON);
        BattleAnimations.latchHurt(DEFENDER, START + 2000L, OTHER);

        assertEquals(3.0F, BattleAnimations.phaseOf(ATTACKER, DRAGON, ModelSkeleton.ATTACK,
            9F, START + 3000L), 1e-4F);
        assertEquals(1.0F, BattleAnimations.phaseOf(DEFENDER, OTHER, ModelSkeleton.HURT,
            9F, START + 3000L), 1e-4F);
    }

    @Test
    void anAttackThatIsNegatedNeverFlinchesTheDefender()
    {
        BattleAnimations.latch(ATTACKER, START, DRAGON);
        // No latchHurt: MSG_BATTLE never arrived, so the damage step never was.
        assertTrue(BattleAnimations.phaseOf(ATTACKER, DRAGON, ModelSkeleton.ATTACK, 9F,
            START + 1000L) >= 0F);
        assertTrue(BattleAnimations.phaseOf(DEFENDER, OTHER, ModelSkeleton.HURT, 9F,
            START + 1000L) < 0F, "the defender recoiled from a blow that never landed");
    }

    @Test
    void theAttackerSwingsAndTheDefenderFlinches()
    {
        latchBoth(START);
        assertEquals(ModelSkeleton.ATTACK, BattleAnimations.clipFor(ATTACKER));
        assertEquals(ModelSkeleton.HURT, BattleAnimations.clipFor(DEFENDER));
        assertNull(BattleAnimations.clipFor(8 | 4), "a bystander");
        assertNull(BattleAnimations.clipFor(-1), "a direct attack has no defender zone");
    }

    @Test
    void theClipPlaysThroughAtItsAuthoredSpeed()
    {
        // Four seconds into an 8.73s swing is four seconds in -- the phase is
        // wall-clock elapsed, not a fraction of any window, so the clip runs at
        // the speed it was authored whatever the duel is doing around it.
        latchBoth(START);
        float at = BattleAnimations.phaseOf(ATTACKER, DRAGON, ModelSkeleton.ATTACK, 8.73F,
            START + 4000L);
        assertEquals(4.0F, at, 1e-4F);
    }

    @Test
    void itStopsAtTheEndRatherThanLooping()
    {
        latchBoth(START);
        assertTrue(BattleAnimations.phaseOf(ATTACKER, DRAGON, ModelSkeleton.ATTACK, 3F,
            START + 2999L) >= 0F);
        // The window runs a blend PAST the clip: standing up out of a swing is
        // a transition, not an instant, and the renderer reads a phase beyond
        // the clip's own length as the cross-fade back to the idle.
        assertTrue(BattleAnimations.phaseOf(ATTACKER, DRAGON, ModelSkeleton.ATTACK, 3F,
            START + 3100L) > 3F, "the blend tail should still report a phase");
        // And then it is over. A looped attack would read as a monster swinging
        // at nothing for the rest of the duel.
        long past = START + 3000L + Math.round(ModelSkeleton.BLEND_SECONDS * 1000F) + 20L;
        assertTrue(BattleAnimations.phaseOf(ATTACKER, DRAGON, ModelSkeleton.ATTACK, 3F,
            past) < 0F);
    }

    @Test
    void theBlendIsTheSixFramesTheGameUses()
    {
        // Not a chosen number: SzModel_SetAnimIntr exists in the game only to
        // supply 6 to the routine that changes an animation, and the keyframes
        // are authored at 30 per second.
        assertEquals(6F / 30F, ModelSkeleton.BLEND_SECONDS, 1e-6F);
    }

    @Test
    void aShortClipStopsSoonerThanALongOne()
    {
        // Length comes from the monster's own file: these run from 1.7s to
        // 33.7s, so when to stand up is a property of the creature.
        latchBoth(START);
        assertTrue(BattleAnimations.phaseOf(ATTACKER, DRAGON, ModelSkeleton.ATTACK, 1.7F,
            START + 2000L) < 0F);
        assertTrue(BattleAnimations.phaseOf(ATTACKER, DRAGON, ModelSkeleton.ATTACK, 33.7F,
            START + 2000L) >= 0F);
    }

    @Test
    void theSwingIsNotInheritedByWhoeverTakesTheSquareNext()
    {
        // The defender is destroyed and something else is summoned into its
        // zone while the clip would still be running. A zone is a square, not
        // an identity, and the new monster is not the one that was hit.
        latchBoth(START);
        assertTrue(BattleAnimations.phaseOf(DEFENDER, OTHER, ModelSkeleton.HURT, 5F,
            START + 2000L) >= 0F);
        assertTrue(BattleAnimations.phaseOf(DEFENDER, DRAGON, ModelSkeleton.HURT, 5F,
            START + 2000L) < 0F, "a new occupant played the old one's flinch");
    }

    @Test
    void latchingTheSameAttackAgainDoesNotRestartIt()
    {
        // The view is handed out every frame while its window is open, so this
        // happens sixty times a second. Restarting would make the first two
        // thirds of a second of every swing stutter in place.
        latchBoth(START);
        latchBoth(START);
        assertEquals(2.0F, BattleAnimations.phaseOf(ATTACKER, DRAGON, ModelSkeleton.ATTACK, 9F,
            START + 2000L), 1e-4F);
    }

    @Test
    void aNewAttackReplacesTheOldOne()
    {
        latchBoth(START);
        BattleAnimations.latch(DEFENDER, START + 5000L, OTHER);
        BattleAnimations.latchHurt(ATTACKER, START + 5000L, DRAGON);
        assertEquals(ModelSkeleton.ATTACK, BattleAnimations.clipFor(DEFENDER));
        assertEquals(ModelSkeleton.HURT, BattleAnimations.clipFor(ATTACKER));
        assertEquals(1.0F, BattleAnimations.phaseOf(DEFENDER, OTHER, ModelSkeleton.ATTACK, 9F,
            START + 6000L), 1e-4F);
    }

    @Test
    void aNewDuelInheritsNothing()
    {
        latchBoth(START);
        BattleAnimations.clear();
        assertNull(BattleAnimations.clipFor(ATTACKER));
        assertTrue(BattleAnimations.phaseOf(ATTACKER, DRAGON, ModelSkeleton.ATTACK, 9F,
            START + 100L) < 0F);
    }

    @Test
    void speedScalesThePhaseAndTheDefaultIsFour()
    {
        // Four by default, because a faithful swing runs a median of 8.7
        // seconds and the duel is blocked on it.
        assertEquals(4F, AnimationSettings.DEFAULT_SPEED, 1e-6F);

        AnimationSettings.setSpeed(4F);
        latchBoth(START);
        // One wall-clock second is four seconds of animation.
        assertEquals(4.0F, BattleAnimations.phaseOf(ATTACKER, DRAGON, ModelSkeleton.ATTACK,
            8.73F, START + 1000L), 1e-4F);
        // And an 8.73s clip is over in 8.73/4 = 2.18s of wall clock. The duel
        // step is divided by the same factor, so the card breaks as it ends.
        assertTrue(BattleAnimations.phaseOf(ATTACKER, DRAGON, ModelSkeleton.ATTACK, 8.73F,
            START + 2100L) >= 0F);
        assertTrue(BattleAnimations.phaseOf(ATTACKER, DRAGON, ModelSkeleton.ATTACK, 8.73F,
            START + 2300L) < 0F);
    }

    @Test
    void aModelWithNoSuchClipIsNeverPosed()
    {
        // length 0 means "this monster's file has no bit for that slot", which
        // the eight-bit presence mask makes an ordinary case rather than an
        // error. It keeps idling.
        latchBoth(START);
        assertTrue(BattleAnimations.phaseOf(ATTACKER, DRAGON, ModelSkeleton.ATTACK, 0F,
            START + 100L) < 0F);
    }
}
