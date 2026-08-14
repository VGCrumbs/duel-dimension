package de.cas_ual_ty.dueldimension.clientutil.overworld;

import com.mojang.blaze3d.vertex.PoseStack;
import de.cas_ual_ty.dueldimension.clientutil.CardFaces;
import de.cas_ual_ty.dueldimension.duel.overworld.display.CardDisplayTileEntity;
import de.cas_ual_ty.dueldimension.duel.overworld.display.PedestalSpace;
import de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot;
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
 * The first block entity renderer in this mod, and it draws the duel board's
 * own geometry: the same six quads, the same textures, the same thickness. A
 * card here is not a picture OF a card in a duel, it is the same object in a
 * different place -- which is the whole reason {@code CardSpace} exists.
 * <p>
 * <b>On the camera argument.</b> The level renderer has already translated the
 * pose by (this block - the eye) before {@code submit} is called, so a corner
 * handed to {@link WorldQuad} must be measured from the BLOCK rather than from
 * the eye. Passing the block's own corner as the "camera" makes the two
 * translations cancel exactly. The billboard still needs the real eye, because
 * where the viewer is standing is the one thing it cannot work out from the
 * block.
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
            return;
        }
        Vec3 block = Vec3.atLowerCornerOf(state.blockPos);
        // The middle of the block's upper face, which is where a card put down
        // on it would lie.
        Vec3 surface = block.add(0.5D, 1D, 0.5D);
        PedestalSpace space = new PedestalSpace(surface, PedestalSpace.CARD_ON_BLOCK);

        // Built as a board slot so every question about which face shows is
        // answered by the same code that answers it in a duel -- including the
        // one that matters here, that a face-down card shows its back.
        BoardSnapshot.Slot slot = new BoardSnapshot.Slot(true, (int)state.code, state.faceDown,
            state.defence, 0, 0, 0, 0, 0, 0, 0, null, false, state.art);

        CardRenderer.submit(poseStack, collector, space, block, PedestalSpace.lone(), 0,
            state.defence, 0F, CardFaces.face(slot, false, 0), CardFaces.underside(slot, 0),
            0xFFFFFFFF);

        drawMonster(state, poseStack, collector, camera, block, surface);
    }

    /**
     * The monster, standing on its own card.
     * <p>
     * Face-up only, and that is a rule rather than a detail: a set card is a
     * card nobody may identify, and a monster looming over one would announce
     * what it is to the whole room.
     */
    private static void drawMonster(State state, PoseStack poseStack,
        SubmitNodeCollector collector, CameraRenderState camera, Vec3 block, Vec3 surface)
    {
        if(state.faceDown)
        {
            return;
        }
        MonsterSprites.Sheet sheet = MonsterSprites.sheetFor(state.code, state.defence);
        if(sheet == null)
        {
            return;
        }
        // On top of the card rather than on top of the block, so the monster
        // stands on the thing it belongs to and not through it.
        Vec3 feet = surface.add(0D,
            (CardMesh.THICKNESS + 0.002F) * PedestalSpace.CARD_ON_BLOCK, 0D);
        MonsterBillboard.submit(poseStack, collector, block, camera.pos, feet,
            PedestalSpace.CARD_ON_BLOCK * sheet.heightInCards(), sheet,
            MonsterSprites.frameAt(sheet, state.gameTime), 0xFFFFFFFF);
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
}
