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
 * bolted onto the body, but a second player model wearing the outfit as a skin.
 * That keeps a duelist looking like a person in a jacket rather than a person in
 * a box, and it means an outfit is a 64x64 skin — something anyone can draw —
 * instead of geometry.
 * <p>
 * The player's own body is hidden for the frame (see
 * {@code ClientProxy.hideBodyUnderOutfit}) and redrawn here on the outfit's
 * body type, with the outfit over it. That is the point of taking it away: a
 * classic body is a pixel wider at each arm than an Alex outfit and stuck out
 * from under it, and the fix is to draw the player on the body their clothes
 * were cut for rather than to lose the player.
 * <p>
 * A layer rather than an override of the player's skin, which would need a
 * mixin into {@code AbstractClientPlayer} and a build set up to run one.
 */
public class OutfitLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>
{
    /**
     * How far the outfit sits outside the skin under it.
     * <p>
     * Both are drawn on the same mesh, so without this they would be exactly
     * coplanar and fight for every pixel. Small enough to read as clothing
     * rather than padding — the two surfaces are a quarter of a pixel apart.
     */
    private static final float INFLATE = 0.25F;

    /**
     * Two body types, and for each the player's own skin and the outfit over
     * it. The body type is chosen by the OUTFIT rather than by the player.
     * <p>
     * An Alex skin lays its arms out differently from a Steve skin, so drawing
     * one on the other model puts the sleeve texture in the wrong place. Which
     * body a skin was drawn for is a fact about the skin, not about whoever is
     * wearing it, so the outfit carries it — and the player underneath is drawn
     * on that same body, which is what stops anything sticking out.
     */
    private final PlayerModel<AbstractClientPlayer> classicSkin;
    private final PlayerModel<AbstractClientPlayer> classicOutfit;
    private final PlayerModel<AbstractClientPlayer> alexSkin;
    private final PlayerModel<AbstractClientPlayer> alexOutfit;

    public OutfitLayer(RenderLayerParent<AbstractClientPlayer,
        PlayerModel<AbstractClientPlayer>> parent, EntityModelSet models)
    {
        super(parent);
        classicSkin = bake(false, 0F);
        classicOutfit = bake(false, INFLATE);
        alexSkin = bake(true, 0F);
        alexOutfit = bake(true, INFLATE);
    }

    /**
     * Built from the PLAYER mesh with an inflation rather than borrowed from
     * the outer-armour layer: that layer's boxes are laid out for an armour
     * texture, so a skin drawn on it would come out scrambled.
     */
    private static PlayerModel<AbstractClientPlayer> bake(boolean slim, float inflate)
    {
        LayerDefinition definition = LayerDefinition.create(
            PlayerModel.createMesh(new CubeDeformation(inflate), slim), 64, 64);
        return new PlayerModel<>(definition.bakeRoot(), slim);
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

        // The player first, then their clothes: an outfit leaves gaps -- a bare
        // forearm, a face -- and what shows through them should be the person
        // wearing it.
        draw(poseStack, buffer, light, player, limbSwing, limbSwingAmount, partialTick,
            age, yaw, pitch, worn.slim() ? alexSkin : classicSkin,
            player.getSkinTextureLocation());
        draw(poseStack, buffer, light, player, limbSwing, limbSwingAmount, partialTick,
            age, yaw, pitch, worn.slim() ? alexOutfit : classicOutfit, texture);
    }

    private void draw(PoseStack poseStack, MultiBufferSource buffer, int light,
        AbstractClientPlayer player, float limbSwing, float limbSwingAmount, float partialTick,
        float age, float yaw, float pitch, PlayerModel<AbstractClientPlayer> model,
        ResourceLocation texture)
    {
        getParentModel().copyPropertiesTo(model);
        model.prepareMobModel(player, limbSwing, limbSwingAmount, partialTick);
        model.setupAnim(player, limbSwing, limbSwingAmount, age, yaw, pitch);
        // Everything on, including the second layer: a skin's hair and a jacket
        // both live there, and the body this replaces had them on too.
        model.setAllVisible(true);

        // Cutout, not translucent: a skin's transparent pixels are holes to see
        // through, not glass, and sorting them would be a cost paid every frame
        // for nothing.
        model.renderToBuffer(poseStack,
            buffer.getBuffer(RenderType.entityCutoutNoCull(texture)), light,
            OverlayTexture.NO_OVERLAY, 1F, 1F, 1F, 1F);
    }

}
