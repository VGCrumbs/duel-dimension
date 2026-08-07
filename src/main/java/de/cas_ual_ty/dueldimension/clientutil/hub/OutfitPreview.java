package de.cas_ual_ty.dueldimension.clientutil.hub;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Vector3f;
import de.cas_ual_ty.dueldimension.clientutil.PlayerSkins;
import de.cas_ual_ty.dueldimension.clientutil.UnderSkin;
import de.cas_ual_ty.dueldimension.duel.outfit.Outfits;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * A turning, walking figure wearing one outfit, drawn into a screen.
 * <p>
 * A wardrobe of names tells a player nothing about what they are choosing —
 * these are clothes, and the question "what does it look like" is the only one
 * being asked. So the same two passes the world uses (the player's skin, then
 * the outfit over it) are run into the GUI, on a model that turns and walks so
 * the back and the sides are as visible as the front.
 */
public final class OutfitPreview
{
    /**
     * The outfit sits this far outside the skin, as in the world: on the same
     * mesh they would otherwise be coplanar and fight for every pixel.
     */
    private static final float INFLATE = 0.25F;

    /** A full turn every this many milliseconds — slow enough to look at. */
    private static final float SPIN_MS = 9000F;

    private static PlayerModel<AbstractClientPlayer> classicSkin;
    private static PlayerModel<AbstractClientPlayer> classicOutfit;
    private static PlayerModel<AbstractClientPlayer> alexSkin;
    private static PlayerModel<AbstractClientPlayer> alexOutfit;

    private OutfitPreview()
    {
    }

    private static void models()
    {
        if(classicSkin != null)
        {
            return;
        }
        classicSkin = bake(false, 0F);
        classicOutfit = bake(false, INFLATE);
        alexSkin = bake(true, 0F);
        alexOutfit = bake(true, INFLATE);
    }

    private static PlayerModel<AbstractClientPlayer> bake(boolean slim, float inflate)
    {
        return new PlayerModel<>(LayerDefinition.create(
            PlayerModel.createMesh(new CubeDeformation(inflate), slim), 64, 64).bakeRoot(), slim);
    }

    /**
     * Draws one figure, centred on {@code x} with its feet at {@code footY}.
     *
     * @param scale pixels per model unit; a player is 32 units tall, so 3 gives
     *              a figure about ninety pixels high
     * @param time  milliseconds, for the turn and the walk. Passed in rather
     *              than read here so every tile on a row is in step.
     */
    public static void draw(PoseStack poseStack, int x, int footY, float scale,
        Outfits.Outfit outfit, long time)
    {
        models();
        Minecraft minecraft = Minecraft.getInstance();
        AbstractClientPlayer player = minecraft.player;
        if(player == null)
        {
            return;
        }

        boolean slim = outfit.texture() != null ? outfit.slim()
            : PlayerSkins.resolve(player).slim();
        float spin = (time % (long)SPIN_MS) / SPIN_MS * 360F;
        // A slow walk, so the arms and legs move: a figure that only turns
        // reads as a statue on a turntable rather than as someone wearing
        // something. The swing is small -- this is a fitting room, not a march.
        float limbSwing = time / 220F;
        float limbSwingAmount = 0.55F;

        poseStack.pushPose();
        poseStack.translate(x, footY, 200);
        poseStack.scale(scale, scale, scale);
        // Y down in a GUI, Y up in the world.
        poseStack.mulPose(Vector3f.ZP.rotationDegrees(180F));
        poseStack.mulPose(Vector3f.YP.rotationDegrees(spin));
        // The model's origin is the top of the head, so drop it a body's height
        // to stand the figure on the line asked for.
        poseStack.translate(0, -24 / 16F, 0);
        poseStack.scale(-1F, -1F, 1F);
        poseStack.translate(0, -1.5F, 0);

        com.mojang.blaze3d.platform.Lighting.setupForEntityInInventory();
        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();

        ResourceLocation under = outfit.texture() != null && UnderSkin.texture() != null
            ? UnderSkin.texture() : PlayerSkins.resolve(player).texture();
        pass(poseStack, buffer, player, slim ? alexSkin : classicSkin, under,
            limbSwing, limbSwingAmount);
        if(outfit.texture() != null)
        {
            pass(poseStack, buffer, player, slim ? alexOutfit : classicOutfit, outfit.texture(),
                limbSwing, limbSwingAmount);
        }

        buffer.endBatch();
        com.mojang.blaze3d.platform.Lighting.setupFor3DItems();
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        poseStack.popPose();
    }

    private static void pass(PoseStack poseStack, MultiBufferSource buffer,
        AbstractClientPlayer player, PlayerModel<AbstractClientPlayer> model,
        ResourceLocation texture, float limbSwing, float limbSwingAmount)
    {
        model.young = false;
        model.crouching = false;
        model.swimAmount = 0F;
        model.attackTime = 0F;
        model.rightArmPose = net.minecraft.client.model.HumanoidModel.ArmPose.EMPTY;
        model.leftArmPose = net.minecraft.client.model.HumanoidModel.ArmPose.EMPTY;
        model.setupAnim(player, limbSwing, limbSwingAmount, 0F, 0F, 0F);
        model.setAllVisible(true);
        model.renderToBuffer(poseStack,
            buffer.getBuffer(RenderType.entityCutoutNoCull(texture)),
            0xF000F0, OverlayTexture.NO_OVERLAY, 1F, 1F, 1F, 1F);
    }
}
