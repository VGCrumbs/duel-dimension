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

    /**
     * How tall a player model is in its own units, head to feet: two blocks.
     * <p>
     * The parts are stored in blocks, so this is the number that turns a height
     * in pixels into a scale. Note where the zero is — the head box runs from
     * -0.5 to 0 and the legs end at 1.5, so the model's origin is the base of
     * the skull, not the top of the head. Scaling by 1.5 instead, as if the
     * origin were the top, made every figure a third too big and pushed its
     * head out through the lid of its tile.
     */
    private static final float BODY_BLOCKS = 2F;

    /** Where the top of the head sits relative to the model's origin. */
    private static final float HEAD_ABOVE_ORIGIN = 0.5F;

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
     * <p>
     * The transform is worked out rather than borrowed from
     * {@code InventoryScreen}, which drives a whole entity renderer and applies
     * its own conventions on the way. A bare {@link PlayerModel} is simpler
     * than that: its parts are stored in blocks, and its y axis runs downward —
     * head at 0, feet at 1.5 — which is the same direction a screen's does. So
     * the model needs no flipping at all, only placing and scaling.
     *
     * @param topY   where the top of the head goes
     * @param height how tall the figure should stand, head to feet, in pixels
     * @param time   milliseconds, for the turn and the walk. Passed in rather
     *               than read here so every tile on a row is in step.
     */
    public static void draw(PoseStack poseStack, int x, int topY, int height,
        Outfits.Outfit outfit, long time)
    {
        models();
        Minecraft minecraft = Minecraft.getInstance();
        AbstractClientPlayer player = minecraft.player;
        if(player == null)
        {
            return;
        }

        PlayerSkins.Skin skin = PlayerSkins.resolve(player);
        // As in the world: each texture on the body it was drawn for, and the
        // skin forced onto the outfit's body only when a slim outfit would
        // otherwise fail to cover a classic arm.
        boolean skinSlim = outfit.texture() != null && outfit.slim() ? true : skin.slim();
        float spin = (time % (long)SPIN_MS) / SPIN_MS * 360F;
        // A slow walk, so the arms and legs move: a figure that only turns
        // reads as a statue on a turntable rather than as someone wearing
        // something. The swing is small -- this is a fitting room, not a march.
        float limbSwing = time / 220F;
        float limbSwingAmount = 0.5F;

        float scale = height / BODY_BLOCKS;
        poseStack.pushPose();
        // The model's origin is the base of the skull, so it goes half a head
        // below the line the top of the head should sit on.
        poseStack.translate(x, topY + HEAD_ABOVE_ORIGIN * scale, 250);
        poseStack.scale(scale, scale, scale);
        // Half a turn on top of the spin: a player model faces -Z, and -Z is
        // away from whoever is looking at the screen.
        poseStack.mulPose(Vector3f.YP.rotationDegrees(180F + spin));

        com.mojang.blaze3d.platform.Lighting.setupForEntityInInventory();
        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();

        ResourceLocation under = outfit.texture() != null && UnderSkin.texture() != null
            ? UnderSkin.texture() : PlayerSkins.resolve(player).texture();
        pass(poseStack, buffer, player, skinSlim ? alexSkin : classicSkin, under,
            limbSwing, limbSwingAmount);
        if(outfit.texture() != null)
        {
            pass(poseStack, buffer, player, outfit.slim() ? alexOutfit : classicOutfit,
                outfit.texture(), limbSwing, limbSwingAmount);
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
