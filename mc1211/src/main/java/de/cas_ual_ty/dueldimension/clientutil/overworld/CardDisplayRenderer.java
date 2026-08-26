package de.cas_ual_ty.dueldimension.clientutil.overworld;

import com.mojang.blaze3d.vertex.PoseStack;
import de.cas_ual_ty.dueldimension.duel.overworld.display.CardDisplayTileEntity;
import de.cas_ual_ty.dueldimension.compat.SubmitNodeCollector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
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
 * translated the pose by (this block - the eye) before this is called, so
 * anything drawn in BLOCK-LOCAL coordinates lands on this block and needs to
 * know nothing else -- which is how the card is drawn. The billboard is the
 * exception: which way it turns depends on where the viewer is standing, which
 * is the one fact block-local coordinates cannot express, so it works in world
 * space and is given both. A MODEL does not turn to the viewer -- it is placed,
 * not aimed -- but it shares that path, since it stands in the same spot the
 * billboard would.
 *
 * <h2>Why the 26.2 shape is kept without the 26.2 types</h2>
 *
 * 26.2 splits a block entity renderer in two: {@code extractRenderState} copies
 * what a frame needs off the block entity, and {@code submit} draws from the
 * copy and may not look at the world. 1.21.1 has one method,
 * {@code render(T, float, PoseStack, MultiBufferSource, int, int)}, called with
 * the block entity itself -- there is no {@code BlockEntityRenderState}, no
 * {@code CameraRenderState}, and no extract phase to put in one.
 * <p>
 * The split is kept anyway, as a plain object rather than a vanilla supertype.
 * Not out of tidiness: {@link #drawMonster} is forty lines of placement rules
 * shared with the duel board, and keeping its parameters identical is what makes
 * the two versions the same text and a change to one a change to both. What
 * changes is only who fills the state in and when.
 */
public class CardDisplayRenderer implements BlockEntityRenderer<CardDisplayTileEntity>
{
    /** What a frame needs to know, copied off the block entity before drawing. */
    public static class State
    {
        public BlockPos blockPos;
        public long code;
        public byte art;
        public boolean defence;
        public boolean faceDown;
        public long gameTime;
    }

    /**
     * Reused rather than allocated per frame.
     * <p>
     * 26.2 has {@code createRenderState} and the game pools the result; here the
     * pooling is this field. Sound because block entity rendering is on the
     * render thread and one {@code render} call is finished with the state
     * before the next begins -- the same reason 26.2's pool is safe.
     */
    private final State state = new State();

    public CardDisplayRenderer(BlockEntityRendererProvider.Context context)
    {
    }

    @Override
    public void render(CardDisplayTileEntity display, float partialTick, PoseStack poseStack,
        MultiBufferSource bufferSource, int packedLight, int packedOverlay)
    {
        extractRenderState(display, state);
        if(state.code == 0L)
        {
            // Nothing chosen yet. Not a failure -- a display block starts
            // empty, and an empty one is a bare pedestal.
            return;
        }
        SubmitNodeCollector collector = new SubmitNodeCollector(bufferSource);
        DisplayCard.submit(poseStack, collector, (int)state.code, state.art, state.defence,
            state.faceDown, 0xFFFFFFFF);
        drawMonster(state, poseStack, collector,
            Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());
    }

    private static void extractRenderState(CardDisplayTileEntity display, State state)
    {
        state.blockPos = display.getBlockPos();
        state.code = display.code();
        state.art = display.art();
        state.defence = display.defence();
        state.faceDown = display.faceDown();
        // The LEVEL's clock. On 26.2 this has to be read in extract, because
        // submit is handed only what extract wrote down; here the separation is
        // convention rather than enforcement, but reading it in one place is
        // still what stops the card and the monster it carries disagreeing about
        // what time it is.
        state.gameTime = display.getLevel() == null ? 0L : display.getLevel().getGameTime();
    }

    /**
     * The monster, standing on its own card.
     * <p>
     * Face-up only, and that is a rule rather than a detail: a set card is a
     * card nobody may identify, and a monster looming over one would announce
     * what it is to the whole room.
     */
    private static void drawMonster(State state, PoseStack poseStack,
        SubmitNodeCollector collector, Vec3 cameraPos)
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
        MonsterBillboard.submit(poseStack, collector, block, cameraPos, feet,
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
     * <p>
     * Takes the block entity here where 26.2 takes nothing; same answer.
     */
    @Override
    public boolean shouldRenderOffScreen(CardDisplayTileEntity display)
    {
        return true;
    }
}
