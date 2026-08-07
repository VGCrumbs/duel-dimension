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
     * @param sleeve whether the player has their jacket's sleeve layer enabled
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

        PlayerModel model = OutfitLayer.model(worn);
        ModelPart arm = right ? model.rightArm : model.leftArm;
        arm.resetPose();
        arm.visible = true;
        model.leftSleeve.visible = sleeve;
        model.rightSleeve.visible = sleeve;
        model.leftArm.zRot = -ROLL;
        model.rightArm.zRot = ROLL;

        // Translucent, as the vanilla call is: the second skin layer is drawn
        // this way so a hair fringe or a visor fades rather than cuts out, and
        // a sleeve that sorted differently from the arm under it would flicker
        // against it.
        collector.submitModelPart(arm, poseStack, RenderTypes.entityTranslucent(worn.texture()),
            light, OverlayTexture.NO_OVERLAY, null);
    }
}
