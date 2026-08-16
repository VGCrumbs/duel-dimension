package de.cas_ual_ty.dueldimension.clientutil;

import com.mojang.blaze3d.vertex.PoseStack;
import de.cas_ual_ty.dueldimension.duel.outfit.Outfits;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;

/**
 * Draws a duelist's outfit over them.
 * <p>
 * The same approach the Crysis mod uses for its suit pieces: not armour models
 * bolted onto the body, but a second player model wearing the outfit as a skin.
 * That keeps a duelist looking like a person in a jacket rather than a person in
 * a box, and it means an outfit is a 64x64 skin — something anyone can draw —
 * instead of geometry.
 * <p>
 * <b>Only the outfit.</b> The Forge version of this class also hid the player's
 * body and redrew it, because the body underneath had to be the shape the
 * clothes were cut for and there was no way to say so. {@link OutfitSkins} says
 * so directly — the game picks the classic or slim renderer from
 * {@code getSkin().model()} — so by the time this runs the arms are already the
 * right width and the player is already wearing the right skin. Two passes
 * became one, and the one that is left is the one this class is named for.
 * <p>
 * Which outfit comes off the render state rather than off the player, because a
 * layer is not given the player any more; see {@link OutfitCarrier}.
 */
public class OutfitLayer extends RenderLayer<AvatarRenderState, PlayerModel>
{
    /**
     * How far the outfit sits outside the skin under it.
     * <p>
     * Both are drawn on the same mesh, so without this they would be exactly
     * coplanar and fight for every pixel.
     * <p>
     * It has to clear the player's OWN outer layer, not just their body.
     * {@code PlayerModel.createMesh} extends whatever deformation it is given
     * by 0.25 for the hat, jacket, sleeves and pants, so a player wearing a
     * skin with any opaque jacket pixels already occupies 0.25. An outfit
     * inflated to 0.25 landed exactly there and z-fought against it, which is
     * why outfits looked broken for anyone without 3D Skin Layers -- that mod
     * voxelizes the player's outer layer OFF this plane, so it hid the clash
     * for everyone who had it installed.
     * <p>
     * At 0.5 the outfit's body clears the player's jacket and its own outer
     * layer sits at 0.75, still under a pixel of padding in total.
     */
    private static final float INFLATE = 0.5F;

    /**
     * Drawn after the body, as an outer layer is. The same number vanilla's own
     * outer layers pass, so an outfit and a drowned's overlay sort the same way.
     */
    private static final int ORDER = 1;

    /** No tint: the outfit is drawn in the colours it was painted. */
    private static final int NO_TINT = -1;

    /**
     * One model per body type, shared by every duelist on screen.
     * <p>
     * Safe to share because a model holds no state between draws: the pose is
     * applied from the render state at the moment the node is drawn, which is
     * the whole point of submitting a model and a state together rather than a
     * posed model.
     * <p>
     * The body type is chosen by the OUTFIT rather than by the player. An Alex
     * skin lays its arms out differently from a Steve skin, so drawing one on
     * the other model puts the sleeve texture in the wrong place; which body a
     * skin was drawn for is a fact about the skin, not about whoever is wearing
     * it.
     */
    private static final java.util.Map<String, PlayerModel> MODELS = new java.util.HashMap<>();
    /** Kept separate because first-person voxel offsets differ from world offsets. */
    private static final java.util.Map<String, PlayerModel> HAND_MODELS = new java.util.HashMap<>();

    public OutfitLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent)
    {
        super(parent);
        models();
    }

    /** Shared with the first-person hand, which has no layer to go through. */
    static void models()
    {
        // Kept as the initialization hook used by the constructor. Models are
        // now per outfit: their injected voxel meshes belong to one texture.
    }

    /** The model an outfit is drawn on. */
    static PlayerModel model(Outfits.Outfit outfit)
    {
        PlayerModel model = MODELS.computeIfAbsent(outfit.id(), ignored -> bake(outfit.slim()));
        SkinLayersCompat.applyOutfit(model, outfit.texture(), outfit.slim());
        return model;
    }

    /** Dedicated retained model for first-person sleeves. */
    static PlayerModel handModel(Outfits.Outfit outfit)
    {
        PlayerModel model = HAND_MODELS.computeIfAbsent(outfit.id(), ignored -> bake(outfit.slim()));
        SkinLayersCompat.applyFirstPerson(model, outfit.texture(), outfit.slim());
        return model;
    }

    /**
     * Built from the PLAYER mesh with an inflation rather than borrowed from
     * the outer-armour layer: that layer's boxes are laid out for an armour
     * texture, so a skin drawn on it would come out scrambled.
     */
    private static PlayerModel bake(boolean thin)
    {
        return new OutfitModel(LayerDefinition.create(
            PlayerModel.createMesh(new CubeDeformation(INFLATE), thin), 64, 64).bakeRoot(), thin);
    }

    /** An equipped outfit is visible independently of the base skin's part toggles. */
    private static final class OutfitModel extends PlayerModel
    {
        private OutfitModel(net.minecraft.client.model.geom.ModelPart root, boolean slim)
        {
            super(root, slim);
        }

        @Override
        public void setupAnim(AvatarRenderState state)
        {
            super.setupAnim(state);
            hat.visible = true;
            jacket.visible = true;
            leftSleeve.visible = true;
            rightSleeve.visible = true;
            leftPants.visible = true;
            rightPants.visible = true;
        }
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light,
        AvatarRenderState state, float yRot, float xRot)
    {
        Outfits.Outfit worn = ((OutfitCarrier)state).dueldimension$outfit();
        if(worn.texture() == null || state.isInvisible || state.isSpectator)
        {
            return;
        }
        // Cutout, not translucent: a skin's transparent pixels are holes to see
        // through, not glass, and sorting them would be a cost paid every frame
        // for nothing. This helper is the cutout one -- the culled variant is
        // the separately named entityCutoutCull -- so a jacket is visible from
        // the inside where it opens, as it was on Forge.
        renderColoredCutoutModel(model(worn), worn.texture(), poseStack, collector, light,
            state, NO_TINT, ORDER);
    }
}
