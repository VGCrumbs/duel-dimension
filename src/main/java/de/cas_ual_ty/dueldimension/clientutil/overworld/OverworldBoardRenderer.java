package de.cas_ual_ty.dueldimension.clientutil.overworld;

import com.mojang.blaze3d.vertex.PoseStack;
import de.cas_ual_ty.dueldimension.clientutil.CardFaces;
import de.cas_ual_ty.dueldimension.clientutil.DuelClientState;
import de.cas_ual_ty.dueldimension.clientutil.FieldLayout;
import de.cas_ual_ty.dueldimension.clientutil.PlayMats;
import de.cas_ual_ty.dueldimension.ocg.OcgConstants;
import de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot;
import net.minecraft.resources.Identifier;
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

    /**
     * How high a card sits above the board, in field units. Above every piece
     * of the board including a lit zone square, so a card is never in a
     * coplanar fight with the square it is standing on.
     */
    private static final float CARD_LIFT = 0.02F;

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

        drawCards(poseStack, collector, transform, camera);
    }

    /**
     * The cards on the field, as real objects standing on the board.
     * <p>
     * Read from {@code DuelClientState.board} and from nothing else. That is
     * the paced, ordered view the animations commit into; a prompt's own field
     * snapshot is a settled server state captured when the core asked, and
     * preferring it is the documented cause of cards appearing before the
     * animation that puts them there. On a world board it would also show a
     * face-down card's zone filling before the move that justifies it.
     */
    private static void drawCards(PoseStack poseStack, SubmitNodeCollector collector,
        FieldTransform transform, Vec3 camera)
    {
        BoardSnapshot board = DuelClientState.board;
        if(board == null)
        {
            return;
        }
        int seat = Math.max(0, ClientDuelField.seat());

        for(boolean own : new boolean[] {true, false})
        {
            BoardSnapshot.Side side = own ? board.self() : board.opponent();
            if(side == null)
            {
                continue;
            }
            // The snapshot is seat-relative -- "self" is whoever is being
            // served -- and the board is not, because both duellists walk
            // around the same one. This is where that is translated, and the
            // only place it is.
            int controller = FieldTransform.controllerFor(seat, own);
            Identifier back = CardFaces.back(controller);

            drawRow(poseStack, collector, transform, camera, side.monsters(), controller,
                OcgConstants.LOCATION_MZONE, back);
            drawRow(poseStack, collector, transform, camera, side.spells(), controller,
                OcgConstants.LOCATION_SZONE, back);

            // The four piles. A deck and an extra deck show their backs
            // because that is all anyone may see of them; a graveyard and a
            // banished pile show their top card, because both are public --
            // and the top card is the last one to arrive, which is what makes
            // a graveyard read as a graveyard rather than as a list.
            drawPile(poseStack, collector, transform, camera, controller,
                OcgConstants.LOCATION_DECK, side.deckCount(), back, back);
            drawPile(poseStack, collector, transform, camera, controller,
                OcgConstants.LOCATION_EXTRA, size(side.extra()), back, back);
            drawPile(poseStack, collector, transform, camera, controller,
                OcgConstants.LOCATION_GRAVE, size(side.grave()),
                topFace(side.grave(), controller, back), back);
            drawPile(poseStack, collector, transform, camera, controller,
                OcgConstants.LOCATION_REMOVED, size(side.banished()),
                topFace(side.banished(), controller, back), back);
        }
    }

    private static void drawPile(PoseStack poseStack, SubmitNodeCollector collector,
        FieldTransform transform, Vec3 camera, int controller, int location, int count,
        Identifier top, Identifier back)
    {
        FieldLayout.Rect zone = FieldLayout.zone(controller, location, 0);
        if(zone == null || count <= 0)
        {
            return;
        }
        CardRenderer.submitPile(poseStack, collector, transform, camera, zone, controller, count,
            CARD_LIFT, top, back);
    }

    private static int size(List<BoardSnapshot.Slot> slots)
    {
        return slots == null ? 0 : slots.size();
    }

    /**
     * What is showing on top of a public pile: its most recent card, which is
     * the last one in the list. Asked through {@link CardFaces} like every
     * other face, so a face-down banished card is still face down on top of its
     * pile.
     */
    private static Identifier topFace(List<BoardSnapshot.Slot> slots, int controller,
        Identifier back)
    {
        if(slots == null || slots.isEmpty())
        {
            return back;
        }
        BoardSnapshot.Slot top = slots.get(slots.size() - 1);
        return top == null ? back : CardFaces.face(top, false, controller);
    }

    private static void drawRow(PoseStack poseStack, SubmitNodeCollector collector,
        FieldTransform transform, Vec3 camera, List<BoardSnapshot.Slot> slots, int controller,
        int location, Identifier back)
    {
        if(slots == null)
        {
            return;
        }
        for(int sequence = 0; sequence < slots.size(); sequence++)
        {
            BoardSnapshot.Slot slot = slots.get(sequence);
            if(slot == null || !slot.present())
            {
                continue;
            }
            FieldLayout.Rect zone = FieldLayout.zone(controller, location, sequence);
            if(zone == null)
            {
                continue;
            }
            CardRenderer.submit(poseStack, collector, transform, camera, zone, controller,
                slot.defence(), CARD_LIFT, CardFaces.face(slot, false, controller), back);
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
