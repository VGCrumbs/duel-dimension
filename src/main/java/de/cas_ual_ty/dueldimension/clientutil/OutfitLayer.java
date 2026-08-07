package de.cas_ual_ty.dueldimension.clientutil;

import com.mojang.blaze3d.vertex.PoseStack;
import de.cas_ual_ty.dueldimension.duel.outfit.Outfits;
import de.cas_ual_ty.dueldimension.duel.outfit.WornOutfits;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws a duelist's outfit over their own skin.
 * <p>
 * The same approach the Crysis mod uses for its suit pieces: not armour models
 * bolted onto the body, but a second player model wearing the outfit as a skin,
 * inflated just enough to sit outside the player's own. That keeps a duelist
 * looking like a person in a jacket rather than a person in a box, and it means
 * an outfit is a 64x64 skin — something anyone can draw — instead of geometry.
 * <p>
 * A layer rather than an override of the player's skin: overriding would need a
 * mixin into {@code AbstractClientPlayer}, and would also replace the player's
 * face and hair, which an outfit has no business doing.
 */
public class OutfitLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>
{
    /**
     * How far the outfit sits outside the body.
     * <p>
     * Small enough to look like clothing rather than a shell, and large enough
     * that the two surfaces are never coplanar — coplanar is where z-fighting
     * comes from, and a duelist shimmering at range would be worse than a
     * slightly loose jacket.
     */
    private static final float INFLATE = 0.28F;

    /**
     * One model per body type, chosen by the OUTFIT rather than by the player.
     * <p>
     * An Alex skin lays its arms out differently from a Steve skin, so drawing
     * one on the other model puts the sleeve texture in the wrong place. Which
     * body a skin was drawn for is a fact about the skin, not about whoever is
     * wearing it, so the outfit carries it and both models are kept ready.
     */
    private final PlayerModel<AbstractClientPlayer> classic;
    private final PlayerModel<AbstractClientPlayer> alex;

    public OutfitLayer(RenderLayerParent<AbstractClientPlayer,
        PlayerModel<AbstractClientPlayer>> parent, EntityModelSet models)
    {
        super(parent);
        classic = bake(false);
        alex = bake(true);
    }

    /**
     * Built from the PLAYER mesh with an inflation rather than borrowed from
     * the outer-armour layer: that layer's boxes are laid out for an armour
     * texture, so a skin drawn on it would come out scrambled.
     */
    private static PlayerModel<AbstractClientPlayer> bake(boolean slim)
    {
        LayerDefinition inflated = LayerDefinition.create(
            PlayerModel.createMesh(new CubeDeformation(INFLATE), slim), 64, 64);
        return new PlayerModel<>(inflated.bakeRoot(), slim);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int light,
        AbstractClientPlayer player, float limbSwing, float limbSwingAmount, float partialTick,
        float age, float yaw, float pitch)
    {
        Outfits.Outfit worn = WornOutfits.of(player.getUUID());
        ResourceLocation texture = worn.texture();
        if(texture == null || player.isInvisible() || player.isSpectator())
        {
            return;
        }

        PlayerModel<AbstractClientPlayer> outfit = worn.slim() ? alex : classic;
        getParentModel().copyPropertiesTo(outfit);
        outfit.prepareMobModel(player, limbSwing, limbSwingAmount, partialTick);
        outfit.setupAnim(player, limbSwing, limbSwingAmount, age, yaw, pitch);
        // The hat and the second-layer parts belong to the player's own skin;
        // an outfit that drew them would be putting a jacket over a face.
        outfit.hat.visible = false;
        outfit.jacket.visible = false;
        outfit.leftSleeve.visible = false;
        outfit.rightSleeve.visible = false;
        outfit.leftPants.visible = false;
        outfit.rightPants.visible = false;

        // Cutout, not translucent: a skin's transparent pixels are holes to see
        // the player through, not glass, and sorting them would be a cost paid
        // every frame for nothing.
        outfit.renderToBuffer(poseStack,
            buffer.getBuffer(RenderType.entityCutoutNoCull(texture)), light,
            OverlayTexture.NO_OVERLAY, 1F, 1F, 1F, 1F);
    }

}
