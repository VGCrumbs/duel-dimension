package de.cas_ual_ty.dueldimension.clientutil.overworld;

import com.mojang.blaze3d.vertex.PoseStack;
import de.cas_ual_ty.dueldimension.clientutil.DuelClientState;
import de.cas_ual_ty.dueldimension.clientutil.PlayMats;
import de.cas_ual_ty.dueldimension.duel.overworld.FieldSiting;
import de.cas_ual_ty.dueldimension.duel.overworld.FieldTransform;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The duel board, drawn on the ground between two duellists.
 * <p>
 * A thin submit loop and nothing else: {@link BoardMesh} decides what the board
 * is made of and {@link FieldTransform} decides where each piece lands, so this
 * class has no opinion about either and cannot disagree with the ground that was
 * validated for the field.
 * <p>
 * Drawn for anyone who has been sent the field, which today is the two
 * duellists. Nothing here reads a hand, a deck or a face-down card -- the board
 * is furniture, and the cards on it are a later phase with its own rules about
 * what may be seen.
 */
public final class OverworldBoardRenderer
{
    private OverworldBoardRenderer()
    {
    }

    /** How far the board floats over the ground: enough not to z-fight the floor. */
    private static final double SURFACE_LIFT = 0.02D;

    public static void render(LevelRenderContext context)
    {
        FieldSiting siting = ClientDuelField.siting();
        // Only once the duel is actually on. While a player is still walking to
        // their mark the placement guide has the floor, and drawing a board
        // through it would say the duel had already started.
        if(siting == null || !ClientDuelField.locked())
        {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if(client.player == null)
        {
            return;
        }

        FieldTransform transform = new FieldTransform(siting);
        Vec3 camera = client.gameRenderer.mainCamera().position();
        PoseStack poseStack = context.poseStack();
        SubmitNodeCollector collector = context.submitNodeCollector();

        for(BoardMesh.Piece piece : BoardMesh.pieces(matsByController()))
        {
            WorldQuad.submit(poseStack, collector, piece.texture(), camera,
                transform.corners(piece.rect(), SURFACE_LIFT + piece.lift()), 0xFFFFFFFF);
        }
    }

    /**
     * Each controller's playmat, indexed by controller rather than by seat.
     * <p>
     * {@code DuelClientState} holds them the way the 2D board wants them --
     * "mine" and "theirs", because that board is drawn from one seat's point of
     * view. A board standing in the world is not: both duellists walk around the
     * same one, and seat 1's own mat has to be on seat 1's end of it even on
     * seat 0's screen. {@link FieldTransform#controllerFor} is where that
     * translation lives, and this is its one caller for the mats.
     */
    private static PlayMats[] matsByController()
    {
        int seat = Math.max(0, ClientDuelField.seat());
        PlayMats[] mats = new PlayMats[2];
        mats[FieldTransform.controllerFor(seat, true)] = DuelClientState.selfMat;
        mats[FieldTransform.controllerFor(seat, false)] = DuelClientState.opponentMat;
        return mats;
    }

    /** The board's four corners in world space, for anything that needs its extent. */
    public static List<Vec3> corners()
    {
        FieldSiting siting = ClientDuelField.siting();
        return siting == null ? List.of()
            : List.of(new FieldTransform(siting).matCorners(SURFACE_LIFT));
    }
}
