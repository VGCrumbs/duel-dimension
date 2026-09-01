package de.cas_ual_ty.dueldimension.clientutil.overworld;

import com.mojang.blaze3d.vertex.PoseStack;
import de.cas_ual_ty.dueldimension.duel.overworld.display.CardDisplayTileEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;

/**
 * The card on a display block, and the monster standing on the card.
 * <p>
 * The first block entity renderer in this mod. It draws the card ITSELF, in
 * {@link DisplayCard}, rather than borrowing the duel board's renderer: that
 * one is built around a mapping from EDOPro's field units into the world, and a
 * card on a pedestal has none of the neighbours that mapping exists to place it
 * among. A pedestal is a block, so its card is measured in blocks.
 * <p>
 * <b>Two frames of reference, and only two.</b> The level renderer has already
 * translated the pose by (this block - the eye) before {@code submit} is
 * called, so anything drawn in BLOCK-LOCAL coordinates lands on this block and
 * needs to know nothing else -- which is how the card is drawn. The billboard
 * is the exception: which way it turns depends on where the viewer is standing,
 * which is the one fact block-local coordinates cannot express, so it works in
 * world space and is given both. A MODEL does not turn to the viewer -- it is
 * placed, not aimed -- but it shares that path, since it stands in the same
 * spot the billboard would.
 */
public class CardDisplayRenderer
    implements BlockEntityRenderer<CardDisplayTileEntity, CardDisplayRenderer.State>
{
    /** What a frame needs to know, copied off the block entity before drawing. */
    public static class State extends BlockEntityRenderState
    {
        public long code;
        public byte art;
        public boolean defence;
        public boolean faceDown;
        /**
         * Which way the pedestal is turned.
         * <p>
         * FACING points from the block towards whoever placed it, exactly as a
         * furnace's does, so it is also the way the card should be read from
         * and the way a monster standing on it should look.
         */
        public net.minecraft.core.Direction facing;
        public long gameTime;
        /**
         * The world's light around the monster, read in extract.
         * <p>
         * Here rather than in submit because submit is handed what extract
         * wrote down and may not look at the level itself -- the same rule
         * gameTime above obeys, and for the same reason.
         */
        public de.cas_ual_ty.dueldimension.clientutil.model.ModelLight light;
    }

    public CardDisplayRenderer(BlockEntityRendererProvider.Context context)
    {
    }

    @Override
    public State createRenderState()
    {
        return new State();
    }

    @Override
    public void extractRenderState(CardDisplayTileEntity display, State state, float partialTick,
        Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay crumbling)
    {
        BlockEntityRenderState.extractBase(display, state, crumbling);
        state.code = display.code();
        state.art = display.art();
        state.defence = display.defence();
        // Off the block state rather than the block entity: the facing is the
        // BLOCK's, so it lives where the block's own data lives and needs no
        // saving of its own.
        net.minecraft.world.level.block.state.BlockState block = display.getBlockState();
        state.facing = block.hasProperty(
            de.cas_ual_ty.dueldimension.duel.overworld.display.CardDisplayBlock.FACING)
            ? block.getValue(
                de.cas_ual_ty.dueldimension.duel.overworld.display.CardDisplayBlock.FACING)
            : net.minecraft.core.Direction.NORTH;
        state.faceDown = display.faceDown();
        // The LEVEL's clock, read here rather than in submit: extract is where
        // a renderer is allowed to look at the world, and submit is handed only
        // what extract wrote down.
        state.gameTime = display.getLevel() == null ? 0L : display.getLevel().getGameTime();
        // Above the pedestal, never inside it -- see ModelLight.standing.
        if(display.getLevel() != null)
        {
            net.minecraft.core.BlockPos at = display.getBlockPos();
            state.light = de.cas_ual_ty.dueldimension.clientutil.model.ModelLight.standing(
                display.getLevel(),
                new Vec3(at.getX() + 0.5D, at.getY() + 1.0D, at.getZ() + 0.5D), 2.0D);
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector,
        CameraRenderState camera)
    {
        if(state.code == 0L)
        {
            // Nothing chosen yet. Not a failure -- a display block starts
            // empty, and an empty one is a bare pedestal.
            return;
        }
        // TURNED TO FACE WHOEVER PLACED IT.
        //
        // The card is drawn in block-local coordinates around the block's own
        // centre, so turning it is a rotation of the pose about that centre and
        // nothing else -- which is why this wraps only the card. drawMonster
        // below works in WORLD space, and a rotated pose would move it off the
        // pedestal rather than turn it; it is given the heading as a number
        // instead.
        //
        // -toYRot, and the sign is derived rather than guessed. DisplayCard
        // winds its top face so that the texture's top edge lies along the
        // NORTH edge and its left along the WEST -- which is a card the right
        // way up to somebody standing to the SOUTH looking north. So the
        // undrawn-rotation card already faces south, and what is wanted is the
        // turn that takes south to FACING: 0 for south, 90 for east, 180 for
        // north, 270 for west. That is -toYRot for all four, since toYRot is
        // south 0, west 90, north 180, east 270.
        float heading = state.facing == null ? 0F : -state.facing.toYRot();
        poseStack.pushPose();
        poseStack.translate(0.5F, 0F, 0.5F);
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(heading));
        poseStack.translate(-0.5F, 0F, -0.5F);
        DisplayCard.submit(poseStack, collector, (int)state.code, state.art, state.defence,
            state.faceDown, 0xFFFFFFFF);
        poseStack.popPose();
        drawMonster(state, poseStack, collector, camera);
    }

    /**
     * The monster, standing on its own card.
     * <p>
     * Face-up only, and that is a rule rather than a detail: a set card is a
     * card nobody may identify, and a monster looming over one would announce
     * what it is to the whole room.
     */
    /**
     * The pedestal's heading in Minecraft's own yaw, for whatever stands on it.
     * <p>
     * {@code toYRot} straight through, with no sign flipped: yaw and FACING
     * share a convention -- south 0, west 90, north 180, east 270 -- so a
     * monster given the block's facing looks the way the block's front points,
     * which is at whoever placed it. That is the OPPOSITE convention to the
     * card's rotation a few lines up, and deliberately so: one is a heading, the
     * other is a correction applied to art that already faced somewhere.
     */
    private static float blockYaw(State state)
    {
        return state.facing == null ? 0F : state.facing.toYRot();
    }

    private static void drawMonster(State state, PoseStack poseStack,
        SubmitNodeCollector collector, CameraRenderState camera)
    {
        if(state.faceDown)
        {
            return;
        }
        MonsterSprites.Definition definition = MonsterSprites.of(state.code);
        SpriteLayer body = MonsterSprites.layerFor(state.code, state.defence);
        de.cas_ual_ty.dueldimension.clientutil.model.ModelMesh mesh =
            definition != null && definition.usesModel()
                ? de.cas_ual_ty.dueldimension.clientutil.model.MonsterModels
                    .get(definition.model())
                : null;
        if(body == null && mesh == null)
        {
            return;
        }
        Wings wings = MonsterSprites.wingsFor(state.code);
        // World space for this one, because which way it turns depends on where
        // the viewer is standing. The block's own corner is handed in as the
        // origin, which cancels the translation the level renderer already
        // applied -- the same two frames meeting, from the other side.
        Vec3 block = Vec3.atLowerCornerOf(state.blockPos);
        float height = DisplayCard.LENGTH * MonsterSprites.heightFor(state.code);
        // The whole creature rises together, wings included, because it is one
        // creature -- so the lift goes on the point everything is measured from
        // rather than on the body alone.
        Vec3 feet = block.add(0.5D, DisplayCard.surface() + 0.002D
            + MonsterSprites.bobAt(body, state.gameTime) * height, 0.5D);
        if(mesh != null)
        {
            // A fixed heading, not one taken off the camera. This followed the
            // viewer, the way the sprites do -- but a sprite has to turn,
            // because a flat picture seen edge-on is nothing, while a model has
            // a back and a front and a shape that reads differently from every
            // side. A dragon that pivots to keep facing you as you walk round it
            // is a dragon that never seems to be standing anywhere.
            //
            // "Fixed" now means fixed at the BLOCK's heading, with the
            // definition's own Turn on top of it. It used to mean fixed at the
            // Turn alone, because the block had no facing to add -- and every
            // display in a row therefore faced the same way however it had been
            // placed. Now that the pedestal is turned when it is put down, the
            // thing standing on it turns with it, and Turn goes back to being
            // what it was written to be: a per-monster correction, not a
            // heading.
            de.cas_ual_ty.dueldimension.clientutil.model.ModelHologram.submit(poseStack,
                collector, block, feet, height, mesh, blockYaw(state), 0xFFFFFFFF,
                definition.animation(), definition.elevation(), definition.turn(),
                definition.offsetX(), definition.offsetZ(), Float.NaN, state.light);
            return;
        }
        // Lift, Off x and Off z, which a sprite now obeys as a model does. The
        // yaw is the block's, for the same reason the model above takes it.
        feet = MonsterBillboard.stand(feet, blockYaw(state), definition);
        // As on the board: the block's heading plus the definition's Turn, which
        // together decide which way a Doom sheet considers its front.
        // definition can be null -- layerFor falls back to a bare sheet for a
        // card nobody has authored -- and a null one simply has no Turn.
        float turn = (definition == null ? 0F : definition.turn()) + blockYaw(state);
        body = body.facing(MonsterSprites.directionAt(body, camera.pos, feet, turn));
        if(wings != null)
        {
            wings = new Wings(wings.layer().facing(MonsterSprites.directionAt(
                wings.layer(), camera.pos, feet, turn)),
                wings.anchor(), wings.spacing(), wings.scale());
        }
        MonsterBillboard.submit(poseStack, collector, block, camera.pos, feet,
            height, body,
            MonsterSprites.frameAt(body, state.gameTime), wings,
            wings == null ? 0 : MonsterSprites.frameAt(wings.layer(), state.gameTime),
            0xFFFFFFFF, state.code);
    }

    /**
     * A monster stands well above its card, so the block it is on can be off
     * screen while the sprite is not.
     */
    @Override
    public int getViewDistance()
    {
        return 96;
    }

    /**
     * Drawn even when its own block has left the view.
     * <p>
     * A block entity is normally culled with the block it belongs to, which is
     * right for a chest and wrong for this: the sprite stands a good two blocks
     * above the pedestal, so looking slightly down -- or standing close enough
     * that the block falls below the screen -- took the monster with it. What
     * is on screen is the monster; the block is only where it is standing.
     */
    @Override
    public boolean shouldRenderOffScreen()
    {
        return true;
    }
}
