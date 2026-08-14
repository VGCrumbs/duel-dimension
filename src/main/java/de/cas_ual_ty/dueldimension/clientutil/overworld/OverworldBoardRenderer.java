package de.cas_ual_ty.dueldimension.clientutil.overworld;

import com.mojang.blaze3d.vertex.PoseStack;
import de.cas_ual_ty.dueldimension.clientutil.BoardTarget;
import de.cas_ual_ty.dueldimension.clientutil.CardFaces;
import de.cas_ual_ty.dueldimension.clientutil.DuelClientState;
import de.cas_ual_ty.dueldimension.clientutil.DuelTextures;
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

    /** The game's tick count, which the glow's pulse breathes on. */
    private static float ticks()
    {
        Minecraft client = Minecraft.getInstance();
        return client.level == null ? 0F
            : client.level.getGameTime() % 100000L
                + client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
    }

    /** How far the board floats over the ground: enough not to z-fight the floor. */
    private static final double SURFACE_LIFT = 0.02D;

    /**
     * Clear air between the top of the board and the bottom of a card, in field
     * units. Small: a card lying on a table is on it.
     */
    private static final float CARD_GAP = 0.02F;

    /**
     * How high a card sits above the board, in field units.
     * <p>
     * Derived from the board's own top layer rather than guessed. It was
     * guessed, and guessed low: the board's pieces are lifted in BLOCKS and a
     * card's in FIELD UNITS, and at 0.9 blocks per unit the card came out at
     * 0.018 blocks against a mat at 0.02 -- so every card on the field was
     * being drawn fractionally UNDER the mat it was lying on, and took turns
     * with it for the pixel.
     */
    private static float cardLift(FieldTransform transform)
    {
        return (float)((SURFACE_LIFT + BoardMesh.TOP_LAYER) / transform.scale()) + CARD_GAP;
    }

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

        // The zone being looked at, lit with the same square the 2D board
        // uses for a zone the engine is offering -- and only when the engine
        // IS offering something, so a highlight always means "you may act
        // here" rather than "your crosshair is here".
        BoardTarget looking = ClientDuelTargeting.looking();
        if(looking != null && ClientDuelTargeting.actionable())
        {
            // Back from the engine's numbering to the board's halves: a
            // target says "mine" or "theirs", and the board has a left and a
            // right that do not move when the seats change.
            int half = FieldTransform.controllerFor(Math.max(0, ClientDuelField.seat()),
                looking.controller() == 0);
            BoardMesh.Piece lit = BoardMesh.highlight(half, looking.location(),
                Math.max(looking.sequence(), 0));
            if(lit != null)
            {
                WorldQuad.submit(poseStack, collector, lit.texture(), camera,
                    transform.corners(lit.rect(), SURFACE_LIFT + lit.lift()), 0xFFFFFFFF);
            }
        }

        drawCards(poseStack, collector, transform, camera);
        drawAttacks(poseStack, collector, transform, camera);
    }

    /**
     * The attacks in flight, as a bolt across the board from attacker to
     * target.
     * <p>
     * The timing is the duel screen's -- the same animation state, already
     * ticked by tickPlayback -- and only the geometry is new, because a lunge
     * drawn in a projected 2D board has nothing in common with one drawn on the
     * ground. Without it an attack is a card that was there and then is not,
     * with nothing in between to say who did it.
     */
    private static void drawAttacks(PoseStack poseStack, SubmitNodeCollector collector,
        FieldTransform transform, Vec3 camera)
    {
        List<de.cas_ual_ty.dueldimension.clientutil.DuelAnimations.AttackView> attacks =
            DuelClientState.animations.attacksInFlight(System.currentTimeMillis());
        if(attacks.isEmpty())
        {
            return;
        }
        int seat = Math.max(0, ClientDuelField.seat());
        for(de.cas_ual_ty.dueldimension.clientutil.DuelAnimations.AttackView attack : attacks)
        {
            FieldLayout.Rect from = zoneOfRef(attack.fromZone(), seat);
            FieldLayout.Rect to = zoneOfRef(attack.toZone(), seat);
            if(from == null)
            {
                continue;
            }
            float fx = from.x() + from.w() / 2F;
            float fy = from.y() + from.h() / 2F;
            // A direct attack has no target zone: it goes at the duellist, so
            // it runs off the far end of the board rather than to a card.
            float tx = to == null ? FieldTransform.CENTRE_X : to.x() + to.w() / 2F;
            float ty = to == null
                ? (fy > FieldTransform.CENTRE_Y ? FieldLayout.FIELD_MIN_Y : FieldLayout.FIELD_MAX_Y)
                : to.y() + to.h() / 2F;

            // Out and back: the bolt reaches the target at the halfway point of
            // the animation and fades from there, which is the beat the 2D
            // board's lunge keeps.
            float reach = Math.min(1F, attack.progress() * 2F);
            float alpha = attack.progress() < 0.5F ? 1F : 1F - (attack.progress() - 0.5F) * 2F;
            float headX = fx + (tx - fx) * reach;
            float headY = fy + (ty - fy) * reach;

            // A ribbon along the line, one card wide, lying just over the mat.
            float dx = headX - fx;
            float dy = headY - fy;
            float length = (float)Math.sqrt(dx * dx + dy * dy);
            if(length < 1e-3F)
            {
                continue;
            }
            float halfW = 0.28F;
            float acrossX = -dy / length * halfW;
            float acrossY = dx / length * halfW;
            double lift = (cardLift(transform) + CardMesh.THICKNESS + 0.01F) * transform.scale();

            WorldQuad.submit(poseStack, collector, DuelTextures.ATTACK, camera, new Vec3[] {
                transform.at(fx - acrossX, fy - acrossY, lift),
                transform.at(headX - acrossX, headY - acrossY, lift),
                transform.at(headX + acrossX, headY + acrossY, lift),
                transform.at(fx + acrossX, fy + acrossY, lift)},
                Math.round(Math.max(0F, alpha) * 255F) << 24 | 0xFFFFFF);
        }
    }

    /**
     * The zone a packed reference names, as a rectangle on the board.
     * <p>
     * Unpacked with the bits that packed it -- {@code EnginePrompt.zoneRef}
     * puts the opponent flag at 16, the monster-zone flag at 8 and the sequence
     * in the low three -- and then turned from the engine's numbering into the
     * board's absolute halves.
     */
    private static FieldLayout.Rect zoneOfRef(int ref, int seat)
    {
        if(ref < 0)
        {
            return null;
        }
        boolean opponent = (ref & 16) != 0;
        boolean monsterZone = (ref & 8) != 0;
        int sequence = ref & 7;
        int half = FieldTransform.controllerFor(seat, !opponent);
        return FieldLayout.zone(half, monsterZone ? OcgConstants.LOCATION_MZONE
            : OcgConstants.LOCATION_SZONE, sequence);
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
        // A duellist draws their own state; a spectator draws the redacted
        // copy the server sent them. Neither can be handed the other's.
        BoardSnapshot board = ClientDuelField.boardToDraw();
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

            // The engine numbers controllers from the seat it is asking; the
            // board's halves are absolute. Both are needed here -- one to draw
            // in the right place, one to ask what is legal.
            int asked = own ? 0 : 1;
            drawRow(poseStack, collector, transform, camera, side.monsters(), controller,
                asked, OcgConstants.LOCATION_MZONE, back);
            drawRow(poseStack, collector, transform, camera, side.spells(), controller,
                asked, OcgConstants.LOCATION_SZONE, back);

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

            // The other duellist's hand stands up in front of them, backs out,
            // the way a hand of cards is held. NOT this client's own -- that
            // stays a flat overlay, because a row of card-high cards standing
            // between a duellist and the board is a duellist who cannot see
            // the board.
            //
            // Only backs can be drawn here whatever this code did: a hand the
            // server did not send belongs to arrives with no codes in it at
            // all. A spectator, who owns neither hand, sees both stand up.
            if(!own || ClientDuelField.seat() < 0)
            {
                drawStandingHand(poseStack, collector, transform, camera, controller,
                    size(side.hand()), back);
            }
        }
    }

    /**
     * How tall a held card stands, in blocks. A metre: the size a card would be
     * if a person were holding it up, which is what makes the opponent's hand
     * read as a hand rather than as decoration on the far side of the table.
     */
    private static final float HELD_HEIGHT = 1.0F;

    /** How far in front of their edge of the mat the cards are held. */
    private static final float HELD_INSET = 0.5F;

    /**
     * The other duellist's hand, as cards standing on end.
     * <p>
     * Drawn as two faces rather than a solid: a card held up is seen from one
     * side or the other and never from its edge-on middle, and both faces are
     * the same back anyway. Wound in opposite directions so each is visible
     * from its own side, which is what lets a spectator walk round the table
     * and still see a hand rather than a row of nothing.
     */
    private static void drawStandingHand(PoseStack poseStack, SubmitNodeCollector collector,
        FieldTransform transform, Vec3 camera, int controller, int cards, Identifier back)
    {
        if(cards <= 0)
        {
            return;
        }
        // Their own edge of the mat, stepped inwards so the cards stand in
        // front of the duellist rather than through them.
        float edge = controller == 0
            ? de.cas_ual_ty.dueldimension.clientutil.FieldLayout.FIELD_MAX_Y - HELD_INSET
            : de.cas_ual_ty.dueldimension.clientutil.FieldLayout.FIELD_MIN_Y + HELD_INSET;

        float widthUnits = HELD_HEIGHT * de.cas_ual_ty.dueldimension.clientutil
            .DuelTextures.CARD_ASPECT / transform.scale();
        // Laid across the middle of the mat, overlapping when there are enough
        // of them to need it, exactly as a real hand fans.
        float span = Math.min(6.5F, cards * widthUnits * 1.05F);
        float step = cards <= 1 ? 0F : span / (cards - 1);
        float start = FieldTransform.CENTRE_X - (cards <= 1 ? 0F : span / 2F);

        for(int card = 0; card < cards; card++)
        {
            float middle = start + step * card;
            float left = middle - widthUnits / 2F;
            float right = middle + widthUnits / 2F;

            Vec3 bottomLeft = transform.at(left, edge, cardLift(transform));
            Vec3 bottomRight = transform.at(right, edge, cardLift(transform));
            Vec3 topLeft = transform.at(left, edge, cardLift(transform) + HELD_HEIGHT);
            Vec3 topRight = transform.at(right, edge, cardLift(transform) + HELD_HEIGHT);

            WorldQuad.submit(poseStack, collector, back, camera,
                new Vec3[] {bottomLeft, topLeft, topRight, bottomRight}, 0xFFFFFFFF);
            WorldQuad.submit(poseStack, collector, back, camera,
                new Vec3[] {bottomRight, topRight, topLeft, bottomLeft}, 0xFFFFFFFF);
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
            cardLift(transform), top, back);
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
        int asked, int location, Identifier back)
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
                slot.defence(), cardLift(transform), CardFaces.face(slot, false, controller), back);

            // A card the engine is offering glows, so a duellist can see what
            // they may do without sweeping the cursor over the whole board.
            // Asked through the same filter that decides the click, so the glow
            // and the click can never disagree.
            if(de.cas_ual_ty.dueldimension.clientutil.PromptOptions.actionable(
                DuelClientState.prompt, false, new de.cas_ual_ty.dueldimension.clientutil
                    .BoardTarget(slot.code(), asked, location, sequence, -1, "", 1, slot.art())))
            {
                WorldQuad.submit(poseStack, collector, DuelHighlight.OUTLINE, camera,
                    transform.corners(CardMesh.placement(zone, slot.defence()),
                        (cardLift(transform) + CardMesh.THICKNESS + 0.004F) * transform.scale()),
                    DuelHighlight.tint(DuelHighlight.pulse(ticks())));
            }
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
