package de.cas_ual_ty.dueldimension.clientutil.hub;

import de.cas_ual_ty.dueldimension.clientutil.OutfitSkins;
import de.cas_ual_ty.dueldimension.duel.outfit.Outfits;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Pose;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A turning, walking figure wearing one outfit, drawn into a screen.
 * <p>
 * A wardrobe of names tells a player nothing about what they are choosing —
 * these are clothes, and the question "what does it look like" is the only one
 * being asked.
 * <p>
 * The Forge version answered it by driving two bare {@code PlayerModel}s
 * straight into the GUI, because the GUI was immediate-mode and that was the
 * cheapest honest thing to do. It cannot be done that way now and it should not
 * be: a screen describes what it wants and the game draws it afterwards, and the
 * thing to describe is <em>the player</em>. So this extracts a real render state
 * from the real renderer and hands it over. The outfit, the body type, the
 * under-skin and the inflation are then not this class's business at all — they
 * are whatever the world would have shown, because it is the same code.
 * <p>
 * The one thing that is this class's business is telling that code to answer for
 * an outfit the player is not wearing yet, which is what
 * {@link OutfitSkins#previewing} is for.
 */
public final class OutfitPreview
{
    /**
     * How tall a player model is in its own units, head to feet: two blocks.
     * <p>
     * The number that turns a height in pixels into a scale, because the scale
     * a GUI entity takes is pixels per block.
     */
    private static final float BODY_BLOCKS = 2F;

    /** A full turn every this many milliseconds — slow enough to look at. */
    private static final float SPIN_MS = 9000F;

    private OutfitPreview()
    {
    }

    /**
     * Draws one figure inside a rectangle.
     *
     * @param height how tall the figure should stand, head to feet, in pixels
     * @param time   milliseconds, for the turn and the walk. Passed in rather
     *               than read here so every tile on a row is in step.
     */
    public static void draw(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1,
        int height, Outfits.Outfit outfit, long time)
    {
        Minecraft minecraft = Minecraft.getInstance();
        AbstractClientPlayer player = minecraft.player;
        if(player == null)
        {
            return;
        }

        AvatarRenderState state = extract(minecraft, player, outfit);
        if(state == null)
        {
            return;
        }
        pose(state, time);

        // The conventions are vanilla's own, taken from the inventory's figure
        // rather than worked out again: a half turn about Z because a screen's
        // y axis runs the other way from the world's, and the entity centred in
        // its rectangle by half its own height.
        graphics.entity(state, height / BODY_BLOCKS,
            new Vector3f(0F, state.boundingBoxHeight / 2F, 0F),
            new Quaternionf().rotateZ((float)Math.PI), null, x0, y0, x1, y1);
    }

    /**
     * The player as the world would draw them, but wearing this instead.
     * <p>
     * A fresh state per call, not the renderer's own: what is extracted here is
     * described now and drawn later, so several tiles are in flight at once and
     * a shared one would leave them all wearing the last outfit on the row.
     */
    private static AvatarRenderState extract(Minecraft minecraft, AbstractClientPlayer player,
        Outfits.Outfit outfit)
    {
        OutfitSkins.previewing(outfit);
        try
        {
            // Asked for AFTER the override is set: the choice between the
            // classic and slim renderer is made from the skin's model type, and
            // a slim outfit changes it.
            AvatarRenderer<AbstractClientPlayer> renderer =
                minecraft.getEntityRenderDispatcher().getPlayerRenderer(player);
            AvatarRenderState state = renderer.createRenderState();
            renderer.extractRenderState(player, state, 1F);

            // Said outright rather than left to the mixin. That mixin injects
            // into extractRenderState(Entity, EntityRenderState, float) -- the
            // erased signature the dispatcher calls through -- but the call
            // above binds to the more specific
            // extractRenderState(AvatarlikeEntity, AvatarRenderState, float),
            // so the injection never runs for a preview. Outfits worked in the
            // world and every tile here stood bare.
            //
            // Setting it here is also the more honest of the two: a preview
            // knows exactly which outfit it is asking for, and does not need to
            // route the answer through a static the layer reads back later.
            ((de.cas_ual_ty.dueldimension.clientutil.OutfitCarrier)state)
                .dueldimension$setOutfit(outfit);
            return state;
        }
        catch(Exception undrawable)
        {
            // A preview is not worth taking the screen down for.
            de.cas_ual_ty.dueldimension.DuelDimension.warn(
                "Could not preview outfit " + outfit.id() + ": " + undrawable);
            return null;
        }
        finally
        {
            OutfitSkins.previewing(null);
        }
    }

    /**
     * Standing, turning, and walking slowly on the spot.
     * <p>
     * The extracted state describes whatever the player is actually doing —
     * crouched in a boat, swimming, holding a card — and none of that is what a
     * fitting room is for. The swing is small: this is someone trying clothes
     * on, not marching.
     */
    private static void pose(AvatarRenderState state, long time)
    {
        float spin = (time % (long)SPIN_MS) / SPIN_MS * 360F;
        // bodyRot turns the figure; yRot is the head's yaw RELATIVE to it, not
        // an absolute angle -- extractRenderState stores
        // -wrapDegrees(headYaw - bodyRot) there. Giving both the spin therefore
        // asked for a head turned a further `spin` degrees off the shoulders,
        // which is why it wandered on its own while the body turned properly.
        // Zero is "looking the way the body faces".
        state.bodyRot = spin;
        state.yRot = 0F;
        state.xRot = 0F;
        // A player caught mid-elytra would otherwise have that pitch applied
        // on top of the turn.
        state.shouldApplyFlyingYRot = false;
        state.flyingYRot = 0F;
        state.walkAnimationPos = time / 220F;
        state.walkAnimationSpeed = 0.5F;

        state.pose = Pose.STANDING;
        state.isCrouching = false;
        state.isVisuallySwimming = false;
        state.isFallFlying = false;
        state.isPassenger = false;
        state.isUsingItem = false;
        state.isAutoSpinAttack = false;
        state.swimAmount = 0F;
        state.attackTime = 0F;
        state.deathTime = 0F;
        state.isInvisible = false;
        state.isInvisibleToPlayer = false;
        state.isSpectator = false;

        // Empty hands: an outfit is the subject, and whatever the player
        // happened to be holding when they opened the hub is not.
        state.rightArmPose = HumanoidModel.ArmPose.EMPTY;
        state.leftArmPose = HumanoidModel.ArmPose.EMPTY;
        state.rightHandItemState.clear();
        state.leftHandItemState.clear();
        state.headItem.clear();
    }
}
