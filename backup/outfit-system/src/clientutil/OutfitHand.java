package de.cas_ual_ty.dueldimension.clientutil;

import com.mojang.blaze3d.vertex.PoseStack;
import de.cas_ual_ty.dueldimension.duel.outfit.Outfits;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * The outfit's sleeve on the arm you see in first person.
 * <p>
 * {@link OutfitLayer} never runs for that arm: the hand is drawn by
 * {@code AvatarRenderer.renderRightHand}, which poses one model part directly
 * and runs no layers at all. So the same arm gets the same treatment here — the
 * outfit's own inflated model, the same part, posed the same way — and a duelist
 * looking down sees their own sleeve rather than a bare wrist.
 * <p>
 * The pose is copied from the call this follows rather than derived: reset,
 * visible, and a tenth of a radian of roll outward. Anything else and the sleeve
 * would sit at a slightly different angle from the arm inside it.
 */
public final class OutfitHand
{
    /** How far the arm rolls away from the centre of the screen, per vanilla. */
    private static final float ROLL = 0.1F;

    private OutfitHand()
    {
    }

    /**
     * @param right  which arm, because each has its own model part
     * @param sleeve ignored: an equipped outfit is independent of the base
     *               skin's cosmetic-part toggle
     */
    public static void draw(PoseStack poseStack, SubmitNodeCollector collector, int light,
        boolean right, boolean sleeve)
    {
        AbstractClientPlayer player = Minecraft.getInstance().player;
        if(player == null)
        {
            return;
        }
        Outfits.Outfit worn = OutfitSkins.worn(player);
        if(worn.texture() == null)
        {
            return;
        }

        PlayerModel model = OutfitLayer.handModel(worn);
        ModelPart arm = right ? model.rightArm : model.leftArm;
        ModelPart outer = right ? model.rightSleeve : model.leftSleeve;
        arm.resetPose();
        model.leftArm.zRot = -ROLL;
        model.rightArm.zRot = ROLL;

        // The local player's vanilla arm uses the editable underskin. The
        // outfit's base arm is therefore required here, followed by its true
        // outer sleeve. Both use the mod's first-person voxel offsets when the
        // compatibility bridge is active.
        arm.visible = true;
        collector.submitModelPart(arm, poseStack, RenderTypes.entityTranslucent(worn.texture()),
            light, OverlayTexture.NO_OVERLAY, null);
        outer.loadPose(arm.storePose());
        outer.visible = true;
        collector.submitModelPart(outer, poseStack, RenderTypes.entityTranslucent(worn.texture()),
            light, OverlayTexture.NO_OVERLAY, null);
    }
}
