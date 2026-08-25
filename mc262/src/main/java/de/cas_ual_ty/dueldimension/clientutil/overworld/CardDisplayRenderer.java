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
        public long gameTime;
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
        state.faceDown = display.faceDown();
        // The LEVEL's clock, read here rather than in submit: extract is where
        // a renderer is allowed to look at the world, and submit is handed only
        // what extract wrote down.
        state.gameTime = display.getLevel() == null ? 0L : display.getLevel().getGameTime();
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
        DisplayCard.submit(poseStack, collector, (int)state.code, state.art, state.defence,
            state.faceDown, 0xFFFFFFFF);
        drawMonster(state, poseStack, collector, camera);
    }

    /**
     * The monster, standing on its own card.
     * <p>
     * Face-up only, and that is a rule rather than a detail: a set card is a
     * card nobody may identify, and a monster looming over one would announce
     * what it is to the whole room.
     */
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
            definition != null && definition.hasModel()
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
            // The block carries no facing of its own, so "fixed" means fixed at
            // the definition's own Turn -- which is a number somebody sets once
            // while looking at it, rather than one this has to guess.
            de.cas_ual_ty.dueldimension.clientutil.model.ModelHologram.submit(poseStack,
                collector, block, feet, height, mesh, 0F, 0xFFFFFFFF,
                definition.animation(), definition.elevation(), definition.turn(),
                definition.offsetX(), definition.offsetZ());
            return;
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
