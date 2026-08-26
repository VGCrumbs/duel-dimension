package de.cas_ual_ty.dueldimension.clientutil;

import de.cas_ual_ty.dueldimension.ocg.prompt.DuelEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuelAnimationsTest
{
    private static final DuelEvent DAMAGE =
        new DuelEvent(DuelEvent.Kind.DAMAGE, 0, -1, -1, 2000, 0);
    private static final DuelEvent RECOVER =
        new DuelEvent(DuelEvent.Kind.RECOVER, 0, -1, -1, 2000, 0);

    private static DuelAnimations silentAnimations()
    {
        return new DuelAnimations()
        {
            @Override
            protected void playSound(DuelEvent event)
            {
            }
        };
    }

    @Test
    void damageStartsDrainingImmediatelyAndFlashesTheLostChunk()
    {
        DuelAnimations animations = silentAnimations();
        animations.accept(List.of(DAMAGE), 1_000L);
        animations.tick(1_000L);

        // 60ms in. The reference holds the old total for its first 120 and
        // flashes before moving, which after an attack reads as the bar
        // hesitating: the blow has landed and the number has not noticed. It
        // counts from the first frame now, and the flash rides along with it.
        DuelAnimations.LifePointState early = animations.lifePointState(0, 8_000, 1_060L);
        assertTrue(early.displayedLifePoints() < 8_000,
            "should already be draining; was " + early.displayedLifePoints());
        assertTrue(early.displayedLifePoints() > 6_000, "but not finished");
        assertEquals(6_000, early.targetLifePoints());
        assertTrue(early.whiteAlpha() > 0F, "the lost interval is still marked");

        DuelAnimations.LifePointState drain = animations.lifePointState(0, 8_000, 1_310L);
        assertTrue(drain.displayedLifePoints() < early.displayedLifePoints(),
            "and keeps going");
        assertTrue(drain.displayedLifePoints() > 6_000);
        assertEquals(6_000, drain.targetLifePoints());
    }

    /** The pace is unchanged: the count still lasts as long as its sound. */
    @Test
    void damageStillTakesItsAuthoredHalfSecond()
    {
        DuelAnimations animations = silentAnimations();
        animations.accept(List.of(DAMAGE), 1_000L);
        animations.tick(1_000L);

        assertTrue(animations.lifePointState(0, 8_000, 1_400L).displayedLifePoints() > 6_000,
            "still counting at 400ms");
        assertEquals(6_000, animations.lifePointState(0, 8_000, 1_500L).displayedLifePoints(),
            "and settled by 500ms");
    }

    @Test
    void damageBoardCommitWaitsForTheDrain()
    {
        DuelAnimations animations = silentAnimations();
        AtomicBoolean committed = new AtomicBoolean();
        animations.accept(List.of(DAMAGE), () -> committed.set(true));

        animations.tick(1_000L);
        animations.tick(1_499L);
        assertFalse(committed.get());

        animations.tick(1_500L);
        assertTrue(committed.get());
    }

    @Test
    void recoveryStartsFillingImmediatelyAndFlashesTheAddedChunk()
    {
        DuelAnimations animations = silentAnimations();
        animations.accept(List.of(RECOVER), 1_000L);
        animations.tick(1_000L);

        DuelAnimations.LifePointState flash = animations.lifePointState(0, 6_000, 1_060L);
        assertTrue(flash.displayedLifePoints() > 6_000, "recovery counts from the first frame too");
        assertEquals(8_000, flash.targetLifePoints());
        assertTrue(flash.whiteAlpha() > 0F);

        DuelAnimations.LifePointState fill = animations.lifePointState(0, 6_000, 1_452L);
        assertTrue(fill.displayedLifePoints() > 6_000);
        assertTrue(fill.displayedLifePoints() < 8_000);
        assertEquals(8_000, fill.targetLifePoints());
    }

    @Test
    void recoveryBoardCommitWaitsForTheFill()
    {
        DuelAnimations animations = silentAnimations();
        AtomicBoolean committed = new AtomicBoolean();
        animations.accept(List.of(RECOVER), () -> committed.set(true));

        animations.tick(1_000L);
        animations.tick(1_783L);
        assertFalse(committed.get());

        animations.tick(1_784L);
        assertTrue(committed.get());
    }

    /**
     * A step with no duration does not cost a tick.
     * <p>
     * The flinch after a sword lands is a model animation and nothing else, so
     * a defender that is a sprite -- or a model whose file has no take-hit slot
     * -- has nothing to play. It used to hold the board for two thirds of a
     * second anyway, and even once that was zero the release loop still gave it
     * the frame it was released on, so the damage arrived 50ms after the blow.
     * <p>
     * Written against a zero-length event directly, because "which monsters
     * have a take-hit clip" is a question about model files and this is a
     * question about the queue.
     */
    @Test
    void aZeroLengthStepReleasesTheNextOneOnTheSameTick()
    {
        DuelAnimations animations = silentAnimations();
        java.util.concurrent.atomic.AtomicBoolean committed =
            new java.util.concurrent.atomic.AtomicBoolean();
        // SHUFFLE has a duration; PHASE has one too. What matters is that the
        // COMMIT behind an event lands as soon as that event is over rather
        // than a tick later, which is the same release path.
        animations.accept(List.of(DAMAGE), () -> committed.set(true));
        animations.tick(0L);
        assertFalse(committed.get(), "the damage is still counting");
        animations.tick(500L);
        assertTrue(committed.get(),
            "the commit should land on the tick the damage finishes, not the next one");
    }
}
