package de.cas_ual_ty.dueldimension.duel.npc;

import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuelistRendererTest
{
    @Test
    void npcAvatarStateShowsEverySecondSkinLayer()
    {
        AvatarRenderState state = new AvatarRenderState();

        DuelistRenderer.showAllSkinLayers(state);

        assertAll(
            () -> assertTrue(state.showHat),
            () -> assertTrue(state.showJacket),
            () -> assertTrue(state.showLeftSleeve),
            () -> assertTrue(state.showRightSleeve),
            () -> assertTrue(state.showLeftPants),
            () -> assertTrue(state.showRightPants));
    }
}
