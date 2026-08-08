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
    void damageFlashesTheLostChunkBeforeCountingDown()
    {
        DuelAnimations animations = silentAnimations();
        animations.accept(List.of(DAMAGE), 1_000L);
        animations.tick(1_000L);

        DuelAnimations.LifePointState flash = animations.lifePointState(0, 8_000, 1_060L);
        assertEquals(8_000, flash.displayedLifePoints());
        assertEquals(6_000, flash.targetLifePoints());
        assertTrue(flash.whiteAlpha() > 0F);

        DuelAnimations.LifePointState drain = animations.lifePointState(0, 8_000, 1_310L);
        assertTrue(drain.displayedLifePoints() < 8_000);
        assertTrue(drain.displayedLifePoints() > 6_000);
        assertEquals(6_000, drain.targetLifePoints());
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
    void recoveryFlashesTheAddedChunkBeforeCountingUp()
    {
        DuelAnimations animations = silentAnimations();
        animations.accept(List.of(RECOVER), 1_000L);
        animations.tick(1_000L);

        DuelAnimations.LifePointState flash = animations.lifePointState(0, 6_000, 1_060L);
        assertEquals(6_000, flash.displayedLifePoints());
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
}
