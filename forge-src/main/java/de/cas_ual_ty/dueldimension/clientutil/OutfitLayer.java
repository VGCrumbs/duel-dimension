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
 * Draws a duelist: their own skin, and their outfit over it.
 * <p>
 * The same approach the Crysis mod uses for its suit pieces: not armour models
 * bolted onto the body, but a second player model wearing the outfit as a skin.
 * That keeps a duelist looking like a person in a jacket rather than a person in
 * a box, and it means an outfit is a 64x64 skin — something anyone can draw —
 * instead of geometry.
 * <p>
 * The player's own body is hidden for the frame (see
 * {@code ClientProxy.hideBodyUnderOutfit}) and redrawn here. Two reasons to
 * take it over rather than let it draw itself: a classic body is a pixel wider
 * at each arm than an Alex outfit and stuck out from under it, so the player
 * has to be drawn on the body their clothes were cut for; and a skin this mod
 * supplies ({@link PlayerSkins}) is not one the game knows how to fetch.
 * <p>
 * So this runs for anyone with either, and draws whichever of the two passes
 * applies.
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
    private static PlayerModel<AbstractClientPlayer> classicSkin;
    private static PlayerModel<AbstractClientPlayer> classicOutfit;
    private static PlayerModel<AbstractClientPlayer> alexSkin;
    private static PlayerModel<AbstractClientPlayer> alexOutfit;

    public OutfitLayer(RenderLayerParent<AbstractClientPlayer,
        PlayerModel<AbstractClientPlayer>> parent, EntityModelSet models)
    {
        super(parent);
        models();
    }

    /**
     * Shared by every player renderer, and by the first-person hand.
     * <p>
     * Static because the hand is drawn from an event that does not say which
     * renderer it came from, and because a model holds no state between draws
     * beyond the pose each draw sets. Four of them: two body types, and for
     * each the skin and the outfit over it.
     */
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
        if(!takesOver(player) || player.isInvisible() || player.isSpectator())
        {
            return;
        }
        Outfits.Outfit worn = WornOutfits.of(player.getUUID());
        PlayerSkins.Skin skin = PlayerSkins.resolve(player);

        // Each texture on the body it was drawn for -- see bodyForSkin.
        draw(poseStack, buffer, light, player, limbSwing, limbSwingAmount, partialTick,
            age, yaw, pitch, bodyForSkin(worn, skin) ? alexSkin : classicSkin,
            baseSkin(player, worn, skin));
        if(worn.texture() != null)
        {
            draw(poseStack, buffer, light, player, limbSwing, limbSwingAmount, partialTick,
                age, yaw, pitch, worn.slim() ? alexOutfit : classicOutfit, worn.texture());
        }
    }

    /**
     * Which body the skin UNDER the outfit is drawn on.
     * <p>
     * Its own, normally: the two layouts put the arm faces at different
     * offsets, so a skin on the wrong body samples a neighbouring face down the
     * edge of the hand — a stray column that cannot be erased, because it is
     * not in the part of the texture anyone would think to erase.
     * <p>
     * The exception is a slim outfit over a classic skin. The outfit is the
     * outer surface and has to contain what is under it; a three-pixel sleeve
     * cannot contain a four-pixel arm, and that arm pokes out. A classic outfit
     * over a slim skin has no such problem, which is why the rule is one-sided.
     */
    private static boolean bodyForSkin(Outfits.Outfit worn, PlayerSkins.Skin skin)
    {
        if(worn.texture() != null && worn.slim())
        {
            return true;
        }
        return skin.slim();
    }

    /**
     * What to draw under the outfit.
     * <p>
     * The player's edited skin if they supplied one and are actually wearing
     * something, otherwise their real one. The edit exists to stop a hoodie or
     * a long sleeve poking through an outfit's gaps, so with no outfit on there
     * is nothing for it to solve and the player is simply themselves.
     * <p>
     * Local to this client and never sent: everyone else sees the real skin
     * under the same outfit, which is the honest thing for a cosmetic that
     * only its owner has edited.
     */
    private static ResourceLocation baseSkin(AbstractClientPlayer player, Outfits.Outfit worn,
        PlayerSkins.Skin skin)
    {
        if(worn.texture() != null && player == net.minecraft.client.Minecraft.getInstance().player)
        {
            ResourceLocation edited = UnderSkin.texture();
            if(edited != null)
            {
                return edited;
            }
        }
        return skin.texture();
    }

    /**
     * Whether this mod is drawing this player rather than the game.
     * <p>
     * Asked in one place so the layer and the code that hides the vanilla body
     * cannot disagree — disagreeing in either direction is a player rendered
     * twice or not at all.
     */
    public static boolean takesOver(AbstractClientPlayer player)
    {
        return WornOutfits.of(player.getUUID()).texture() != null
            || PlayerSkins.of(player) != null;
    }

    /**
     * The same two passes, on one arm, for the hand you see in first person.
     * <p>
     * First person does not go through this layer at all: it calls
     * {@code PlayerRenderer.renderRightHand}, which draws one arm of the base
     * model directly and runs no layers. So a duelist wearing an outfit saw
     * everyone else's sleeves and their own bare wrist. Forge fires
     * {@code RenderArmEvent} from exactly that call, and this answers it.
     *
     * @param right which arm, because each has its own model part
     */
    public static void renderArm(PoseStack poseStack, MultiBufferSource buffer, int light,
        AbstractClientPlayer player, boolean right)
    {
        models();
        Outfits.Outfit worn = WornOutfits.of(player.getUUID());
        PlayerSkins.Skin skin = PlayerSkins.resolve(player);

        arm(poseStack, buffer, light, player,
            bodyForSkin(worn, skin) ? alexSkin : classicSkin,
            baseSkin(player, worn, skin), right);
        if(worn.texture() != null)
        {
            arm(poseStack, buffer, light, player,
                worn.slim() ? alexOutfit : classicOutfit, worn.texture(), right);
        }
    }

    private static void arm(PoseStack poseStack, MultiBufferSource buffer, int light,
        AbstractClientPlayer player, PlayerModel<AbstractClientPlayer> model,
        ResourceLocation texture, boolean right)
    {
        // The pose vanilla's own first-person arm uses: no swing, standing, and
        // the arm straight down the screen.
        model.attackTime = 0F;
        model.crouching = false;
        model.swimAmount = 0F;
        model.setupAnim(player, 0F, 0F, 0F, 0F, 0F);
        net.minecraft.client.model.geom.ModelPart limb = right ? model.rightArm : model.leftArm;
        net.minecraft.client.model.geom.ModelPart sleeve =
            right ? model.rightSleeve : model.leftSleeve;
        limb.xRot = 0F;
        sleeve.xRot = 0F;
        limb.visible = true;
        sleeve.visible = true;
        limb.render(poseStack, buffer.getBuffer(RenderType.entityCutoutNoCull(texture)), light,
            OverlayTexture.NO_OVERLAY);
        sleeve.render(poseStack, buffer.getBuffer(RenderType.entityTranslucent(texture)), light,
            OverlayTexture.NO_OVERLAY);
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
