package de.cas_ual_ty.dueldimension.duel.npc;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The same assertion as 26.2's, about a different object.
 * <p>
 * There the six second-skin layers are booleans on an {@code AvatarRenderState};
 * here they are {@code ModelPart.visible} on the model itself. The test is worth
 * keeping on both for the same reason: {@code SkinLayersCompat} injects geometry
 * into exactly these six parts, so a part switched off is an injection that
 * silently does not draw.
 * <p>
 * Runs headless. Baking a {@link LayerDefinition} builds {@code ModelPart}
 * objects out of plain numbers — no texture, no GL, no game.
 */
class DuelistRendererTest
{
    @Test
    void npcModelShowsEverySecondSkinLayer()
    {
        PlayerModel<DuelistEntity> model = new PlayerModel<>(
            LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64)
                .bakeRoot(), false);
        // Off first, so a green result means this turned them on rather than
        // that they happened to default that way.
        model.hat.visible = false;
        model.jacket.visible = false;
        model.leftSleeve.visible = false;
        model.rightSleeve.visible = false;
        model.leftPants.visible = false;
        model.rightPants.visible = false;

        DuelistRenderer.showAllSkinLayers(model);

        assertAll(
            () -> assertTrue(model.hat.visible),
            () -> assertTrue(model.jacket.visible),
            () -> assertTrue(model.leftSleeve.visible),
            () -> assertTrue(model.rightSleeve.visible),
            () -> assertTrue(model.leftPants.visible),
            () -> assertTrue(model.rightPants.visible));
    }
}
