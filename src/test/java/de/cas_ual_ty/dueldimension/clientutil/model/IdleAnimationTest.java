package de.cas_ual_ty.dueldimension.clientutil.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every monster idles, and it idles on slot_0.
 * <p>
 * Duelists of the Roses names an animation after the slot the game loaded it
 * into, so the numbers are the game's own. Measured across all 683 monsters:
 * slot_0 is on 682 of them, runs a median of two seconds, and returns to its
 * own first pose in 89% of cases. slot_4 is equally universal and returns to
 * its first pose in NONE of them. Short, universal and cyclic against
 * universal and one-shot: the idle is slot_0.
 * <p>
 * What is pinned here is the FALLBACK CHAIN rather than the survey, because the
 * chain is what a monster's appearance actually depends on: an unset animation,
 * or one naming a slot that this particular model does not have, must still
 * leave the creature moving rather than standing in the pose it was rigged in.
 */
class IdleAnimationTest
{
    static boolean dragonPresent()
    {
        return GlbModelTest.dragonPresent();
    }

    private static ModelSkeleton dragon() throws IOException
    {
        GlbModel model = GlbModel.load(Files.readAllBytes(GlbModelTest.DRAGON));
        assertNotNull(model.skin(), "the dragon has no skin to pose");
        return new ModelSkeleton(model);
    }

    @Test
    @EnabledIf("dragonPresent")
    void theIdleIsSlotZero() throws IOException
    {
        ModelSkeleton skeleton = dragon();
        assertEquals("slot_0", ModelSkeleton.IDLE);
        assertEquals(skeleton.indexOf("slot_0"), skeleton.idleIndex());
        assertTrue(skeleton.idleIndex() >= 0, "the dragon has no idle");
    }

    @Test
    @EnabledIf("dragonPresent")
    void anUnknownOrMissingNameStillIdles() throws IOException
    {
        ModelSkeleton skeleton = dragon();
        // What the renderer does with these: indexOf says -1, and -1 means idle
        // rather than bind pose.
        assertEquals(-1, skeleton.indexOf(""));
        assertEquals(-1, skeleton.indexOf(null));
        assertEquals(-1, skeleton.indexOf("slot_99"), "a slot this model lacks");
        assertTrue(skeleton.idleIndex() >= 0);
    }

    @Test
    void onlyTheSlotsTheExecutableNamesAreNamed()
    {
        // Three, and only three. The game's battle state machine sets slot 2 on
        // the attacker and slot 5 on the defender, and idles in slot 0 between
        // them; nothing anywhere says what 1, 3, 4 or 6 are for.
        //
        // The bound this pins is the honest one: it is fine for this list to
        // GROW when somebody reads more of the executable, and not fine for it
        // to grow because a slot looked like an attack in a viewer. Slot 4 is
        // the exact trap -- it is the only slot that never returns to its own
        // first pose, on all 681 models that carry it, and it is not the attack.
        assertEquals("idle", ModelSkeleton.meaning(ModelSkeleton.IDLE));
        assertEquals("attack", ModelSkeleton.meaning(ModelSkeleton.ATTACK));
        assertEquals("hit", ModelSkeleton.meaning(ModelSkeleton.HURT));
        for(String undocumented : new String[] {"slot_1", "slot_3", "slot_4", "slot_6"})
        {
            assertNull(ModelSkeleton.meaning(undocumented), undocumented + " gained a meaning");
        }
        assertNull(ModelSkeleton.meaning(null));
    }

    @Test
    @EnabledIf("dragonPresent")
    void anExplicitChoiceIsStillHonoured() throws IOException
    {
        // The default must not swallow a deliberate pick.
        ModelSkeleton skeleton = dragon();
        int chosen = skeleton.indexOf("slot_4");
        assertTrue(chosen >= 0, "the dragon should have slot_4");
        assertTrue(chosen != skeleton.idleIndex(), "slot_4 must not be the idle");
    }
}
