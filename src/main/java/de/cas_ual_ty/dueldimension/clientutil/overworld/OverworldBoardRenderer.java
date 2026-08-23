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

        int matTint = fade(0xFFFFFFFF);
        for(BoardMesh.Piece piece : BoardMesh.pieces(matsByController()))
        {
            Vec3[] corners = transform.corners(piece.rect(), SURFACE_LIFT + piece.lift());
            if(piece.turns() == 0)
            {
                WorldQuad.submit(poseStack, collector, kindFor(matTint), piece.texture(), camera,
                    corners, matTint);
                continue;
            }
            // A mark that belongs to one duellist, stood the right way up for
            // them. Turned through the same table the cards are turned by, so
            // a gem and the card that covers it cannot disagree about which
            // way the board is facing.
            float[][] uv = CardRenderer.turned(false, 0F, 0F, 1F, 1F, piece.turns());
            WorldQuad.submit(poseStack, collector, kindFor(matTint), piece.texture(), camera,
                corners, matTint, uv[0], uv[1]);
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
                    transform.corners(lit.rect(), SURFACE_LIFT + lit.lift()), fade(0xFFFFFFFF));
            }
        }

        drawPlacements(poseStack, collector, transform, camera);
        drawCards(poseStack, collector, transform, camera);
        drawEquipLinks(poseStack, collector, transform, camera);
        drawMarkers(poseStack, collector, transform, camera);
        drawFlips(poseStack, collector, transform, camera);
        drawMoves(poseStack, collector, transform, camera);
        drawAttacks(poseStack, collector, transform, camera);
        drawShatters(poseStack, collector, transform, camera);
    }

    /**
     * Every square a placement is being offered, and which of them are taken.
     * <p>
     * "Where do you want to put this" is a question about a whole row, and a
     * board that lights only the square under the crosshair makes a duellist
     * sweep the mat to find out which zones are even legal -- with the empty
     * ones being exactly the ones a placement is about, and exactly the ones
     * {@code drawRow} skips before any highlight is considered. The flat board
     * has lit them all since it was written.
     * <p>
     * The zone reference is unpacked the way {@link
     * de.cas_ual_ty.dueldimension.ocg.prompt.EnginePrompt#zoneRef} packs it,
     * and the seat-relative controller in it is turned back into a half of the
     * mat -- the board's halves do not move when the seats change.
     */
    private static void drawPlacements(PoseStack poseStack, SubmitNodeCollector collector,
        FieldTransform transform, Vec3 camera)
    {
        de.cas_ual_ty.dueldimension.ocg.prompt.EnginePrompt prompt = DuelClientState.prompt;
        if(prompt == null
            || prompt.kind() != de.cas_ual_ty.dueldimension.ocg.prompt.EnginePrompt.Kind.PLACES)
        {
            return;
        }
        int seat = Math.max(0, ClientDuelField.seat());
        for(int index = 0; index < prompt.options().size(); index++)
        {
            int reference = prompt.options().get(index).zone();
            if(reference < 0)
            {
                continue;
            }
            BoardMesh.Piece lit = BoardMesh.highlight(zoneOfRef(reference, seat));
            if(lit == null)
            {
                continue;
            }
            WorldQuad.submit(poseStack, collector, lit.texture(), camera,
                transform.corners(lit.rect(), SURFACE_LIFT + lit.lift()), fade(0xFFFFFFFF));
            // Green over blue for one already named, the same pair a card
            // selection uses: "you may choose this" and "you have chosen this"
            // are different things, and a placement wanting two zones is a
            // player who has to tell them apart at a glance.
            if(de.cas_ual_ty.dueldimension.clientutil.DuelSelection.has(index))
            {
                WorldQuad.submit(poseStack, collector, DuelHighlight.OUTLINE, camera,
                    transform.corners(lit.rect(), SURFACE_LIFT + lit.lift() + 0.001D),
                    fade(DuelHighlight.tinted(DuelHighlight.CHOSEN_GREEN, 0.95F)));
            }
        }
    }

    /**
     * Chain and become-target markers, over the cards they concern.
     * <p>
     * drawing.cpp lays tChain over a chaining card and tChainTarget over a
     * targeted one, and the board has had both textures and neither reader. It
     * is not decoration: which card is chaining, and which card that chain is
     * pointing at, is the whole content of a chain a duellist has to respond
     * to. A card activated from a hand or a pile has no square to mark, which
     * is what a null zone means here.
     */
    private static void drawMarkers(PoseStack poseStack, SubmitNodeCollector collector,
        FieldTransform transform, Vec3 camera)
    {
        List<de.cas_ual_ty.dueldimension.clientutil.DuelAnimations.OverlayView> markers =
            DuelClientState.animations.overlaysInFlight(System.currentTimeMillis());
        if(markers.isEmpty())
        {
            return;
        }
        int seat = Math.max(0, ClientDuelField.seat());
        for(de.cas_ual_ty.dueldimension.clientutil.DuelAnimations.OverlayView marker : markers)
        {
            FieldLayout.Rect zone = zoneOfRef(marker.zone(), seat);
            if(zone == null)
            {
                continue;
            }
            // On the card, not on the mat: the marker is about the card, and a
            // square lit under a monster reads as the zone being offered.
            WorldQuad.submit(poseStack, collector,
                marker.chaining() ? DuelTextures.CHAIN : DuelTextures.TARGET, camera,
                transform.corners(CardMesh.placement(zone, false),
                    (cardLift(transform) + CardMesh.THICKNESS + MARKER_RUNG)
                        * transform.scale()),
                fade(0xFFFFFFFF));
        }
    }

    /**
     * A card turning over where it lies.
     * <p>
     * The quad narrows to nothing at the halfway point and opens again, which
     * is what a card rotating about its long axis looks like -- and the face
     * swaps at that midpoint, back then front for one being turned up. Which
     * face is showing is the animation's own answer rather than one worked out
     * again here: two copies of that rule is two places to get it backwards.
     * <p>
     * Drawn OVER the settled card rather than instead of it, exactly as the
     * duel screen draws it. The board holds the old face for the event's
     * duration -- the commit is queued behind the animation -- so the card
     * underneath is the card this is turning.
     */
    private static void drawFlips(PoseStack poseStack, SubmitNodeCollector collector,
        FieldTransform transform, Vec3 camera)
    {
        List<de.cas_ual_ty.dueldimension.clientutil.DuelAnimations.FlipView> turning =
            DuelClientState.animations.flipsInFlight(System.currentTimeMillis());
        if(turning.isEmpty())
        {
            return;
        }
        int seat = Math.max(0, ClientDuelField.seat());
        for(de.cas_ual_ty.dueldimension.clientutil.DuelAnimations.FlipView flip : turning)
        {
            FieldLayout.Rect zone = zoneOfRef(flip.zone(), seat);
            if(zone == null)
            {
                continue;
            }
            int controller = FieldTransform.controllerFor(seat, (flip.zone() & 16) == 0);
            FieldLayout.Rect card = CardMesh.placement(zone, false);
            // |cos| gives one full narrow-and-open across the animation, and
            // never quite zero: a quad of no width is a quad with no normal.
            float squash = Math.max(0.04F,
                Math.abs((float)Math.cos(Math.PI * flip.progress())));
            float width = card.w() * squash;
            CardRenderer.submitAt(poseStack, collector, transform, camera,
                new FieldLayout.Rect(card.x() + (card.w() - width) / 2F, card.y(), width,
                    card.h()),
                CardRenderer.turnsFor(controller, false),
                cardLift(transform) + FLIP_RUNG, flip.texture(),
                controller == 0 ? DuelTextures.COVER : DuelTextures.COVER_OPPONENT,
                fade(0xFFFFFFFF));
        }
    }

    /**
     * A card on its way from one zone to another.
     * <p>
     * A card that comes from nowhere on the mat -- out of a hand, off the top
     * of a deck -- has no rectangle to start from, so it comes in over its
     * owner's edge of the board. The screen's own rule; the only difference is
     * that an edge here is a half of a table two people are standing at rather
     * than the top or the bottom of a window.
     * <p>
     * The arc is what makes it read as a card being CARRIED rather than slid,
     * which matters more on a board seen from a low angle than it does on a
     * board seen from above.
     */
    private static void drawMoves(PoseStack poseStack, SubmitNodeCollector collector,
        FieldTransform transform, Vec3 camera)
    {
        List<de.cas_ual_ty.dueldimension.clientutil.DuelAnimations.MoveView> moves =
            DuelClientState.animations.movesInFlight(System.currentTimeMillis());
        if(moves.isEmpty())
        {
            return;
        }
        int seat = Math.max(0, ClientDuelField.seat());
        for(de.cas_ual_ty.dueldimension.clientutil.DuelAnimations.MoveView move : moves)
        {
            FieldLayout.Rect to = zoneOfRef(move.toZone(), seat);
            if(to == null)
            {
                continue;
            }
            FieldLayout.Rect from = zoneOfRef(move.fromZone(), seat);
            int controller = FieldTransform.controllerFor(seat, (move.toZone() & 16) == 0);
            int owner = FieldTransform.controllerFor(seat, move.player() == 0);

            float startX = from != null ? from.x() : to.x();
            float startY = from != null ? from.y()
                : owner == 0 ? FieldLayout.FIELD_MAX_Y : FieldLayout.FIELD_MIN_Y;

            float t = de.cas_ual_ty.dueldimension.clientutil.DuelAnimations.ease(
                move.progress());
            float x = startX + (to.x() - startX) * t;
            float y = startY + (to.y() - startY) * t;
            float arc = (float)Math.sin(Math.PI * t) * MOVE_ARC;

            CardRenderer.submit(poseStack, collector, transform, camera,
                new FieldLayout.Rect(x, y, to.w(), to.h()), controller, false,
                cardLift(transform) + arc, move.texture(),
                owner == 0 ? DuelTextures.COVER : DuelTextures.COVER_OPPONENT,
                fade(0xFFFFFFFF));
        }
    }

    /** How high a carried card rises at the middle of its journey, in field units. */
    private static final float MOVE_ARC = 0.35F;

    /** Clear of the card being turned, which is still drawn underneath it. */
    private static final float FLIP_RUNG = 0.01F;

    /** And clear of the card the marker is about. */
    private static final double MARKER_RUNG = 0.012D;

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
    /** How much of the break is the card turning white before anything moves. */
    private static final float WHITEN = 0.18F;
    /** How high a shard is thrown, in field units, before gravity takes it. */
    private static final float SHARD_RISE = 0.55F;
    /** And how hard it comes back down. */
    private static final float SHARD_FALL = 1.5F;

    /**
     * A destroyed card breaking apart, in three dimensions.
     * <p>
     * The duel screen already breaks cards, and this is the SAME break: the
     * same grid of uneven pieces, the same hash deciding where the fracture
     * runs and how fast each piece leaves, the same easing. Only the geometry
     * is new. Two boards showing one card destroyed two different ways would be
     * two effects to keep in step, and there is no reason for a second fracture
     * pattern to exist.
     * <p>
     * What the flat board cannot do, this does. A shard here leaves the mat --
     * thrown up and out from the point of impact, tumbling about its own axis
     * as it goes, and falling back under its own weight. The 2D board shrinks
     * its pieces to suggest that, because a projected quad cannot turn; out
     * here they really do turn, so they do not have to pretend.
     * <p>
     * The card whitens first and stays whole while it does. Glass goes bright
     * along its fractures a moment before it lets go, and a card that simply
     * bursts reads as one being deleted rather than one being destroyed.
     */
    private static void drawShatters(PoseStack poseStack, SubmitNodeCollector collector,
        FieldTransform transform, Vec3 camera)
    {
        List<de.cas_ual_ty.dueldimension.clientutil.DuelAnimations.ShatterView> breaks =
            DuelClientState.animations.shattersInFlight(System.currentTimeMillis());
        if(breaks.isEmpty())
        {
            return;
        }
        int seat = Math.max(0, ClientDuelField.seat());
        for(de.cas_ual_ty.dueldimension.clientutil.DuelAnimations.ShatterView shatter : breaks)
        {
            // A destroyed card goes to the graveyard, so where it broke is
            // where it came FROM.
            FieldLayout.Rect zone = zoneOfRef(shatter.fromZone(), seat);
            if(zone == null)
            {
                continue;
            }
            FieldLayout.Rect card = CardMesh.placement(zone, false);
            int controller = (shatter.fromZone() & 16) != 0
                ? FieldTransform.controllerFor(seat, false)
                : FieldTransform.controllerFor(seat, true);
            double lift = (cardLift(transform) + CardMesh.THICKNESS + 0.02F) * transform.scale();
            float t = shatter.progress();

            if(t < WHITEN)
            {
                drawWhitening(poseStack, collector, transform, camera, card, controller, lift,
                    shatter, t / WHITEN);
                continue;
            }
            drawShards(poseStack, collector, transform, camera, card, controller, lift, shatter,
                (t - WHITEN) / (1F - WHITEN));
        }
    }

    /** The card still whole, going white along every fracture about to open. */
    private static void drawWhitening(PoseStack poseStack, SubmitNodeCollector collector,
        FieldTransform transform, Vec3 camera, FieldLayout.Rect card, int controller, double lift,
        de.cas_ual_ty.dueldimension.clientutil.DuelAnimations.ShatterView shatter, float t)
    {
        float[][] uv = CardRenderer.turned(false, shatter.u0(), shatter.v0(), shatter.u1(),
            shatter.v1(), CardRenderer.turnsFor(controller, false));
        int faceTint = fade(0xFFFFFFFF);
        WorldQuad.submit(poseStack, collector, kindFor(faceTint), shatter.texture(), camera,
            transform.corners(card, lift), faceTint, uv[0], uv[1]);
        // The white goes OVER the card rather than into its tint, because a
        // tint can only take colour away and this has to add light.
        WorldQuad.submit(poseStack, collector, DuelTextures.WHITE, camera,
            transform.corners(card, lift + 0.004D * transform.scale()),
            fade(Math.round(t * t * 235F) << 24 | 0xFFFFFF));
    }

    /** And then the pieces. */
    private static void drawShards(PoseStack poseStack, SubmitNodeCollector collector,
        FieldTransform transform, Vec3 camera, FieldLayout.Rect card, int controller, double lift,
        de.cas_ual_ty.dueldimension.clientutil.DuelAnimations.ShatterView shatter, float t)
    {
        int code = shatter.code();
        int columns = de.cas_ual_ty.dueldimension.clientutil.DuelAnimations.SHARD_COLUMNS;
        int rows = de.cas_ual_ty.dueldimension.clientutil.DuelAnimations.SHARD_ROWS;
        float[] cutsX = de.cas_ual_ty.dueldimension.clientutil.DuelAnimations
            .shardCuts(code, columns, 21);
        float[] cutsY = de.cas_ual_ty.dueldimension.clientutil.DuelAnimations
            .shardCuts(code, rows, 22);
        int turns = CardRenderer.turnsFor(controller, false);

        // Where it broke: off centre so the break is not symmetrical, and well
        // inside the card so nothing starts on top of the impact. The duel
        // screen picks it from these same two hashes, so one card breaks at the
        // same spot on either board.
        float hitX = card.x() + card.w() * (0.3F + 0.4F * de.cas_ual_ty.dueldimension.clientutil
            .DuelAnimations.shardNoise(code, 0, 11));
        float hitY = card.y() + card.h() * (0.3F + 0.4F * de.cas_ual_ty.dueldimension.clientutil
            .DuelAnimations.shardNoise(code, 0, 12));

        // Out fast, then slowing: a fracture spends its energy at once.
        float burst = 1F - (1F - t) * (1F - t);

        for(int row = 0; row < rows; row++)
        {
            for(int column = 0; column < columns; column++)
            {
                int index = row * columns + column;
                float fu0 = cutsX[column];
                float fu1 = cutsX[column + 1];
                float fv0 = cutsY[row];
                float fv1 = cutsY[row + 1];

                // Each piece on its own schedule, so the group thins out
                // instead of every shard going on one frame.
                float fadeFrom = 0.3F + 0.35F * de.cas_ual_ty.dueldimension.clientutil
                    .DuelAnimations.shardNoise(code, index, 41);
                float alpha = t <= fadeFrom ? 1F
                    : Math.max(0F, 1F - (t - fadeFrom) / (1F - fadeFrom));
                if(alpha <= 0F)
                {
                    continue;
                }

                float x0 = card.x() + card.w() * fu0;
                float y0 = card.y() + card.h() * fv0;
                float x1 = card.x() + card.w() * fu1;
                float y1 = card.y() + card.h() * fv1;
                float midX = (x0 + x1) / 2F;
                float midY = (y0 + y1) / 2F;

                // Away from the impact, faster the closer it started to it.
                float awayX = midX - hitX;
                float awayY = midY - hitY;
                float reach = Math.max(0.001F, (float)Math.sqrt(awayX * awayX + awayY * awayY));
                float speed = (0.9F + 1.4F * de.cas_ual_ty.dueldimension.clientutil
                    .DuelAnimations.shardNoise(code, index, 31))
                    * (1F + card.w() / (reach * 6F));
                float driftX = awayX * burst * speed;
                float driftY = awayY * burst * speed;

                // Up, and then down. Thrown harder the further out it goes, so
                // the break opens like a shell rather than a puff.
                float thrown = SHARD_RISE * (0.4F + 0.6F * de.cas_ual_ty.dueldimension.clientutil
                    .DuelAnimations.shardNoise(code, index, 51));
                double rise = (thrown * t - SHARD_FALL * t * t * 0.5F) * transform.scale();

                Vec3 centre = transform.at(midX + driftX, midY + driftY, lift + rise);
                // Tumbling about an axis of its own, which is the thing a flat
                // board could only suggest by shrinking its pieces.
                Vec3 axis = tumbleAxis(code, index);
                float spin = t * (1.6F + 4F * de.cas_ual_ty.dueldimension.clientutil
                    .DuelAnimations.shardNoise(code, index, 61));

                Vec3[] corners = new Vec3[4];
                float[] cornerX = {x0, x0, x1, x1};
                float[] cornerY = {y0, y1, y1, y0};
                for(int corner = 0; corner < 4; corner++)
                {
                    Vec3 at = transform.at(cornerX[corner] + driftX, cornerY[corner] + driftY,
                        lift + rise);
                    corners[corner] = centre.add(spin(at.subtract(centre), axis, spin));
                }

                float[][] uv = CardRenderer.turned(false,
                    lerp(shatter.u0(), shatter.u1(), fu0), lerp(shatter.v0(), shatter.v1(), fv0),
                    lerp(shatter.u0(), shatter.u1(), fu1), lerp(shatter.v0(), shatter.v1(), fv1),
                    turns);
                // Squared, as the duel screen fades it: light falls away faster
                // than a straight line looks like it should.
                WorldQuad.submit(poseStack, collector, shatter.texture(), camera, corners,
                    fade(Math.round(alpha * alpha * 255F) << 24 | 0xFFFFFF), uv[0], uv[1]);
            }
        }
    }

    private static float lerp(float from, float to, float at)
    {
        return from + (to - from) * at;
    }

    /**
     * A tumble axis for one shard: fixed for that shard, different from its
     * neighbours'.
     * <p>
     * Out of the same hash as everything else about the break, because this
     * runs once a frame and the piece that was turning one way last frame has
     * to still be turning that way in this one.
     */
    private static Vec3 tumbleAxis(int code, int index)
    {
        double yaw = de.cas_ual_ty.dueldimension.clientutil.DuelAnimations
            .shardNoise(code, index, 71) * Math.PI * 2D;
        double pitch = de.cas_ual_ty.dueldimension.clientutil.DuelAnimations
            .shardNoise(code, index, 81) * Math.PI - Math.PI / 2D;
        return new Vec3(Math.cos(pitch) * Math.cos(yaw), Math.sin(pitch),
            Math.cos(pitch) * Math.sin(yaw)).normalize();
    }

    /**
     * Rodrigues' rotation: one offset turned about an axis through the shard's
     * own centre.
     * <p>
     * Applied to the OFFSETS rather than to the corners, so a shard turns about
     * itself and not about the middle of the board -- which is the difference
     * between a piece tumbling and a piece orbiting.
     */
    private static Vec3 spin(Vec3 offset, Vec3 axis, float angle)
    {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return offset.scale(cos)
            .add(axis.cross(offset).scale(sin))
            .add(axis.scale(axis.dot(offset) * (1D - cos)));
    }

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

            // The duel screen's own beats, because an attack should read the
            // same in both places: the LINE stretches out first, and the sword
            // launches along it once the direction has been established. The
            // sword keeps its size the whole way -- it is a sword flying, not
            // a sword being stretched, which is what drawing one quad from
            // attacker to target produced.
            float t = attack.progress();
            float reach = Math.min(1F, t * 2.2F);
            float alpha = t < 0.8F ? 1F : 1F - (t - 0.8F) / 0.2F;
            // How high the leap goes, in field units: proportional to the
            // distance, because a swing at the monster opposite is not the same
            // motion as one across the whole table, and capped so a direct
            // attack down the length of the board does not go over the roof.
            float rise = Math.min(length(fx, fy, tx, ty) * ARC_RISE, ARC_MAX);

            float dx = tx - fx;
            float dy = ty - fy;
            float length = (float)Math.sqrt(dx * dx + dy * dy);
            if(length < 1e-3F)
            {
                continue;
            }
            // Perpendicular, turned the way that survives at()'s flip of the
            // field's y axis -- the same correction the ribbon needed.
            float rightX = dy / length;
            float rightY = -dx / length;
            // The cross axis, pinned to the VIEWER.
            //
            // It is forward turned a quarter, so it reversed whenever the attack
            // did -- and with both axes reversed the quad's corners swap in
            // pairs, which turns the texture through half a turn. The blade
            // still points at its target, so the flight looked right; what
            // swapped was everything across the blade, and a sword is not
            // symmetric across its own length.
            //
            // Pinning it to the field's own +X fixed that for one duellist and
            // left it upside down for the one sitting opposite: same flat icon,
            // other end of the table. So the pin has to name a side of the
            // BOARD, and the board is drawn per client -- which is what seat is
            // for. The line drawn from this vector is symmetric about its centre
            // and does not care which way it points.
            boolean turned = rightX < 0F || (rightX == 0F && rightY < 0F);
            if(seat == 1)
            {
                turned = !turned;
            }
            if(turned)
            {
                rightX = -rightX;
                rightY = -rightY;
            }
            float forwardX = dx / length;
            float forwardY = dy / length;
            double lift = (cardLift(transform) + CardMesh.THICKNESS + 0.03F) * transform.scale();

            // The line: white, tinted red, from the attacker to however far it
            // has reached -- and arcing, which is why it is cut into segments
            // rather than drawn as one quad. A single quad can only be flat,
            // and a flat ribbon on a board that has a third dimension reads as
            // a sticker sliding across the mat.
            int lineTint = Math.round(Math.max(0F, alpha) * 0.8F * 255F) << 24 | 0xFF2626;
            for(int step = 0; step < ARC_SEGMENTS; step++)
            {
                float a0 = reach * step / ARC_SEGMENTS;
                float a1 = reach * (step + 1) / ARC_SEGMENTS;
                float x0 = fx + dx * a0;
                float y0 = fy + dy * a0;
                float x1 = fx + dx * a1;
                float y1 = fy + dy * a1;
                double lift0 = lift + arc(a0) * rise * transform.scale();
                double lift1 = lift + arc(a1) * rise * transform.scale();
                WorldQuad.submit(poseStack, collector, DuelTextures.WHITE, camera, new Vec3[] {
                    transform.at(x0 + rightX * LINE_HALF, y0 + rightY * LINE_HALF, lift0),
                    transform.at(x1 + rightX * LINE_HALF, y1 + rightY * LINE_HALF, lift1),
                    transform.at(x1 - rightX * LINE_HALF, y1 - rightY * LINE_HALF, lift1),
                    transform.at(x0 - rightX * LINE_HALF, y0 - rightY * LINE_HALF, lift0)},
                    lineTint);
            }

            // The sword: one size, riding the line, point first. Its corners
            // are built from the direction rather than from an angle, so there
            // is no handedness to get backwards -- the tip is simply the two
            // corners nearer the target.
            if(t > 0.35F)
            {
                float travel = Math.min(1F, (t - 0.35F) / 0.5F);
                float cx = fx + dx * travel;
                float cy = fy + dy * travel;
                float half = SWORD_SIZE / 2F;
                int swordTint = Math.round(Math.max(0F, alpha) * 255F) << 24 | 0xFFFFFF;
                // The tip and the hilt take their heights from the arc at their
                // OWN points along it, which pitches the blade to the curve
                // without a single angle being worked out: climbing it points
                // up, falling it points down, and at the top it is level. One
                // arc drives the line and the sword, so the two cannot part
                // company halfway across the table.
                float step = half / length;
                double liftTip = lift + arc(travel + step) * rise * transform.scale();
                double liftHilt = lift + arc(travel - step) * rise * transform.scale();
                WorldQuad.submit(poseStack, collector, DuelTextures.ATTACK, camera, new Vec3[] {
                    transform.at(cx + forwardX * half - rightX * half,
                        cy + forwardY * half - rightY * half, liftTip),
                    transform.at(cx - forwardX * half - rightX * half,
                        cy - forwardY * half - rightY * half, liftHilt),
                    transform.at(cx - forwardX * half + rightX * half,
                        cy - forwardY * half + rightY * half, liftHilt),
                    transform.at(cx + forwardX * half + rightX * half,
                        cy + forwardY * half + rightY * half, liftTip)},
                    swordTint);
            }
        }
    }

    /**
     * The leap, as a fraction of its full height at a point along the flight.
     * <p>
     * A parabola through nought at both ends and one in the middle, which is
     * the whole of it: it leaves the attacker on the mat, is highest halfway
     * across, and lands on the target. Clamped because the sword asks for the
     * height slightly ahead of and behind itself, and beyond either end the
     * curve would dive under the table.
     */
    private static float arc(float along)
    {
        float clamped = Math.clamp(along, 0F, 1F);
        return 4F * clamped * (1F - clamped);
    }

    private static float length(float fx, float fy, float tx, float ty)
    {
        return (float)Math.sqrt((tx - fx) * (tx - fx) + (ty - fy) * (ty - fy));
    }

    /**
     * The line between an equip card and what it is equipped to, and the mark
     * the reference puts on the far end.
     * <p>
     * EDOPro does not draw the pairing at all -- it MARKS it. Hovering a card
     * sets {@code is_showequip} on its partner
     * ({@code ClientField::SetShowMark}) and the partner then wears
     * {@code tEquip} over its face ({@code drawing.cpp}). That badge is kept
     * here at the reference's own size, {@code vSymbol} being 0.7 field units
     * square, which is exactly the width of a card. The line is this project's
     * own addition and the 2D board's too: with three equips out, a badge tells
     * you that SOMETHING is attached and a line tells you what.
     * <p>
     * The core reports one direction only -- {@code card::equiping_target} --
     * so both ends are found by sweeping every on-field slot for an equip whose
     * own zone or whose target zone is the one being looked at. EDOPro builds
     * its reverse {@code equipped} set exactly the same way.
     * <p>
     * Only while a card is being looked at, as on the screen. A board with
     * every pairing permanently strung together is a board nobody can read.
     */
    private static void drawEquipLinks(PoseStack poseStack, SubmitNodeCollector collector,
        FieldTransform transform, Vec3 camera)
    {
        de.cas_ual_ty.dueldimension.clientutil.BoardTarget looking =
            ClientDuelTargeting.looking();
        BoardSnapshot board = DuelClientState.board;
        if(looking == null || looking.isPile() || board == null)
        {
            return;
        }
        int seat = Math.max(0, ClientDuelField.seat());
        for(int asked = 0; asked < 2; asked++)
        {
            BoardSnapshot.Side side = asked == 0 ? board.self() : board.opponent();
            if(side == null)
            {
                continue;
            }
            linksIn(poseStack, collector, transform, camera, side.monsters(), asked,
                OcgConstants.LOCATION_MZONE, looking, seat);
            linksIn(poseStack, collector, transform, camera, side.spells(), asked,
                OcgConstants.LOCATION_SZONE, looking, seat);
        }
    }

    private static void linksIn(PoseStack poseStack, SubmitNodeCollector collector,
        FieldTransform transform, Vec3 camera, List<BoardSnapshot.Slot> slots, int asked,
        int location, de.cas_ual_ty.dueldimension.clientutil.BoardTarget looking, int seat)
    {
        if(slots == null)
        {
            return;
        }
        for(int sequence = 0; sequence < slots.size(); sequence++)
        {
            BoardSnapshot.Slot slot = slots.get(sequence);
            if(slot == null || !slot.present() || slot.equip() == null)
            {
                continue;
            }
            boolean fromLooked = looking.isAt(asked, location, sequence);
            boolean toLooked = looking.isAt(slot.equip().controller(), slot.equip().location(),
                slot.equip().sequence());
            if(!fromLooked && !toLooked)
            {
                continue;
            }
            // The badge belongs on the end NOT being looked at, because the end
            // that is being looked at is the one the player already found.
            FieldLayout.Rect here = zoneOf(asked, location, sequence, seat);
            FieldLayout.Rect there = zoneOf(slot.equip().controller(), slot.equip().location(),
                slot.equip().sequence(), seat);
            if(here == null || there == null)
            {
                continue;
            }
            drawLink(poseStack, collector, transform, camera, fromLooked ? here : there,
                fromLooked ? there : here);
        }
    }

    /** A zone in board space, from a controller the engine numbered. */
    private static FieldLayout.Rect zoneOf(int asked, int location, int sequence, int seat)
    {
        return FieldLayout.zone(FieldTransform.controllerFor(seat, asked == 0), location,
            sequence);
    }

    /** Amber, so an equip link never reads as the red attack line. */
    private static final int LINK_TINT = 0xD9FFD14D;
    /** Half the link's thickness, in field units. */
    private static final float LINK_HALF = 0.035F;

    private static void drawLink(PoseStack poseStack, SubmitNodeCollector collector,
        FieldTransform transform, Vec3 camera, FieldLayout.Rect from, FieldLayout.Rect to)
    {
        float x1 = from.x() + from.w() / 2F;
        float y1 = from.y() + from.h() / 2F;
        float x2 = to.x() + to.w() / 2F;
        float y2 = to.y() + to.h() / 2F;
        double lift = (cardLift(transform) + CardMesh.THICKNESS + 0.04F) * transform.scale();

        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float)Math.sqrt(dx * dx + dy * dy);
        if(length > 1e-3F)
        {
            // Perpendicular, turned the way that survives at()'s flip of the
            // field's y axis -- the same correction the attack ribbon needed.
            float rightX = dy / length * LINK_HALF;
            float rightY = -dx / length * LINK_HALF;
            WorldQuad.submit(poseStack, collector, DuelTextures.WHITE, camera, new Vec3[] {
                transform.at(x1 + rightX, y1 + rightY, lift),
                transform.at(x2 + rightX, y2 + rightY, lift),
                transform.at(x2 - rightX, y2 - rightY, lift),
                transform.at(x1 - rightX, y1 - rightY, lift)}, fade(LINK_TINT));
        }

        // vSymbol is a square the width of a card, centred on the partner.
        float half = to.w() / 2F;
        WorldQuad.submit(poseStack, collector, DuelTextures.EQUIP, camera,
            transform.corners(new FieldLayout.Rect(x2 - half, y2 - half, half * 2F, half * 2F),
                lift + 0.01D * transform.scale()),
            fade(0xFFFFFFFF));
    }

    /** How high a leap goes, as a share of how far it travels. */
    private static final float ARC_RISE = 0.18F;
    /** And never higher than this, in field units, however long the flight. */
    private static final float ARC_MAX = 1.6F;
    /** How many quads the arcing line is cut into. */
    private static final int ARC_SEGMENTS = 12;

    /** Half the attack line's thickness, in field units. */
    private static final float LINE_HALF = 0.05F;

    /** The sword's size, about a card's width -- the screen uses zone width times 0.9. */
    private static final float SWORD_SIZE = 1.0F;

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
            // SEAT-RELATIVE, not the board half. CardFaces speaks the engine's
            // language, where 0 is always whoever is being served -- and this
            // controller is the absolute end of the table, which only agrees
            // with that for the duellist sitting at seat 0. Asked the wrong
            // way round, a player at the far seat got their own sleeve drawn
            // on their opponent's cards and their opponent's on theirs.
            Identifier back = CardFaces.back(own ? 0 : 1);

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
            drawPile(poseStack, collector, transform, camera, controller, asked,
                OcgConstants.LOCATION_DECK, side.deckCount(), back, back);
            drawPile(poseStack, collector, transform, camera, controller, asked,
                OcgConstants.LOCATION_EXTRA, size(side.extra()), back, back);
            drawPile(poseStack, collector, transform, camera, controller, asked,
                OcgConstants.LOCATION_GRAVE, size(side.grave()),
                topFace(side.grave(), asked, back), back);
            drawPile(poseStack, collector, transform, camera, controller, asked,
                OcgConstants.LOCATION_REMOVED, size(side.banished()),
                topFace(side.banished(), asked, back), back);

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

            int heldTint = fade(0xFFFFFFFF);
            // Through the CARD window unless the texture is already card
            // shaped. A sleeve is a square canvas with the card letterboxed
            // inside it, so drawing the whole file across a card-shaped quad
            // squeezes the art inwards -- which is exactly how the opponent's
            // sleeves came out skinny, since the backs in a hand are the only
            // place this was drawn full-range.
            boolean whole = CardFaces.isCardShaped(back);
            float u0 = whole ? 0F : DuelTextures.CARD_U0;
            float v0 = whole ? 0F : DuelTextures.CARD_V0;
            float u1 = whole ? 1F : DuelTextures.CARD_U1;
            float v1 = whole ? 1F : DuelTextures.CARD_V1;
            WorldQuad.submit(poseStack, collector, kindFor(heldTint), back, camera,
                new Vec3[] {bottomLeft, topLeft, topRight, bottomRight}, heldTint,
                u0, v0, u1, v1);
            WorldQuad.submit(poseStack, collector, kindFor(heldTint), back, camera,
                new Vec3[] {bottomRight, topRight, topLeft, bottomLeft}, heldTint,
                u0, v0, u1, v1);
        }
    }

    /**
     * A number laid flat on the board, spelled out of the digit atlas.
     * <p>
     * The atlas rather than the font, and not for want of a font: everything a
     * duellist reads off this board is a PNG, and a number drawn in world space
     * out of glyphs would be the one thing on the mat that had to be turned to
     * face somebody. Ten cells sliced across one strip, one quad per character.
     * <p>
     * Sized from the rectangle it sits on rather than fixed, so the opponent's
     * counts shrink with their half of the table exactly as their cards do --
     * the flat board learned that the hard way and the note is still on it.
     *
     * @param over the rectangle to centre the number on, in field units
     * @param lift how far above the mat, already multiplied by the transform
     */
    private static void drawNumber(PoseStack poseStack, SubmitNodeCollector collector,
        FieldTransform transform, Vec3 camera, FieldLayout.Rect over, double lift, int value,
        float height, float bias, int tint)
    {
        String text = Integer.toString(value);
        float digitH = height;
        float digitW = digitH * DIGIT_ASPECT;
        float left = over.x() + over.w() / 2F - text.length() * digitW / 2F + bias;
        float top = over.y() + over.h() / 2F - digitH / 2F;
        for(int at = 0; at < text.length(); at++)
        {
            int digit = text.charAt(at) - '0';
            if(digit < 0 || digit > 9)
            {
                continue;
            }
            WorldQuad.submit(poseStack, collector, DuelTextures.DIGITS, camera,
                transform.corners(new FieldLayout.Rect(left + at * digitW, top, digitW, digitH),
                    lift),
                tint, digit / 10F, 0F, (digit + 1) / 10F, 1F);
        }
    }

    /** The atlas cell's width over its height, so a digit keeps its shape. */
    private static final float DIGIT_ASPECT = 48F / 80F;
    /** A count's height as a fraction of the card it sits on, as on the flat board. */
    private static final float COUNT_SCALE = 0.46F;
    /** A scale is smaller: it shares its zone with the card's own art. */
    private static final float SCALE_SCALE = 0.30F;
    /**
     * Rungs above a card for the two new marks.
     * <p>
     * Above the glow at 0.03 and the chosen outline at 0.035, because these say
     * what a card IS rather than what may be done to it -- a count buried under
     * a highlight is a count nobody reads. Both are blended and write no depth,
     * so the ladder is the only thing keeping them apart.
     */
    private static final double MARK_RUNG = 0.05D;
    private static final double COUNT_RUNG = 0.055D;

    private static void drawPile(PoseStack poseStack, SubmitNodeCollector collector,
        FieldTransform transform, Vec3 camera, int controller, int asked, int location, int count,
        Identifier top, Identifier back)
    {
        FieldLayout.Rect zone = FieldLayout.zone(controller, location, 0);
        if(zone == null || count <= 0)
        {
            return;
        }
        CardRenderer.submitPile(poseStack, collector, transform, camera, zone, controller, count,
            cardLift(transform), top, back, fade(0xFFFFFFFF));

        // A stack the engine is offering something out of glows, exactly as a
        // card does: the extra deck when there is a Special Summon waiting in
        // it, a graveyard when something down there can be brought back. The
        // cards inside are face down and can say nothing for themselves, so the
        // pile has to say it for them -- otherwise the one place a duellist
        // cannot see is the one place they have to keep guessing about.
        //
        // Asked through the same filter that decides the click, so a pile that
        // glows is a pile that will offer something when clicked.
        if(de.cas_ual_ty.dueldimension.clientutil.PromptOptions.actionable(
            DuelClientState.prompt, false, new de.cas_ual_ty.dueldimension.clientutil
                .BoardTarget(0, asked, location, -1, -1, "", count, 0)))
        {
            // On top of the whole stack, not of one card: a full deck stands
            // forty cards proud of the mat, and a glow left at card height
            // would be buried inside it.
            WorldQuad.submit(poseStack, collector, DuelHighlight.OUTLINE, camera,
                transform.corners(CardMesh.placement(zone, false),
                    (cardLift(transform) + PileMesh.height(count) + 0.03F) * transform.scale()),
                DuelHighlight.tint(DuelHighlight.pulse(ticks())));
        }

        // How many are in it, on top of the stack. Thickness alone cannot say:
        // PileMesh stops adding height at forty-five cards, so a full deck and
        // a graveyard holding sixty stand exactly as tall as each other, and
        // the number of cards left in a deck is a thing duels are lost over.
        FieldLayout.Rect face = CardMesh.placement(zone, false);
        drawNumber(poseStack, collector, transform, camera, face,
            (cardLift(transform) + PileMesh.height(count) + 0.04F) * transform.scale(),
            count, face.h() * COUNT_SCALE, 0F, fade(0xFFFFFFFF));
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
    private static Identifier topFace(List<BoardSnapshot.Slot> slots, int asked,
        Identifier back)
    {
        if(slots == null || slots.isEmpty())
        {
            return back;
        }
        BoardSnapshot.Slot top = slots.get(slots.size() - 1);
        return top == null ? back : CardFaces.face(top, false, asked);
    }

    /**
     * The same four corners, read starting from a different one.
     *
     * <p>Which turns the texture over them by a quarter each time, without
     * moving the quad itself: the rectangle is still exactly where it was, so
     * only the picture on it turns.
     */
    private static Vec3[] turned(Vec3[] corners, int turns)
    {
        int by = Math.floorMod(turns, 4);
        if(by == 0)
        {
            return corners;
        }
        Vec3[] out = new Vec3[4];
        for(int i = 0; i < 4; i++)
        {
            out[i] = corners[(i + by) % 4];
        }
        return out;
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
            // A card that is breaking is drawn by the break, and only by it.
            // Otherwise the board keeps drawing the card in its zone while the
            // shatter draws the same card coming apart on top of it, and a
            // destroyed monster reads as two of itself -- one whole and one in
            // pieces -- for as long as the animation lasts.
            if(shattering(asked, location, sequence))
            {
                continue;
            }

            // The top shows what this client is allowed to see. The UNDERSIDE
            // shows the card itself when this client knows it -- a set card is
            // lying face down, so its face is against the table and somebody
            // under it is looking at the face. An opponent's set card arrives
            // with no code, so theirs stays a back.
            CardRenderer.submit(poseStack, collector, transform, camera, zone, controller,
                slot.defence(), cardLift(transform), CardFaces.face(slot, false, asked),
                CardFaces.underside(slot, asked), fade(0xFFFFFFFF));

            drawHologram(poseStack, collector, transform, camera, zone, slot, location,
                asked, controller, sequence);

            // Switched off. drawing.cpp composites tNegated over any face-up
            // card the core has disabled or forbidden, and the flat board has
            // done the same since the status was first asked for. A negated
            // monster still stands there looking exactly like one that works,
            // which is the single most expensive thing a board can lie about.
            //
            // Over the ZONE and not the card, untinted and unturned, because
            // that is what the flat board does: the mark is meant to be bigger
            // than the thing it cancels.
            if(slot.negated())
            {
                WorldQuad.submit(poseStack, collector, DuelTextures.NEGATED, camera,
                    transform.corners(zone,
                        (cardLift(transform) + CardMesh.THICKNESS + MARK_RUNG)
                            * transform.scale()),
                    fade(0xFFFFFFFF));
            }

            // How many materials are under an Xyz monster. Its whole cost is
            // paid in these, so a board that does not count them is a board you
            // cannot plan a turn on.
            if(slot.overlays() > 0)
            {
                FieldLayout.Rect face = CardMesh.placement(zone, slot.defence());
                drawNumber(poseStack, collector, transform, camera, face,
                    (cardLift(transform) + CardMesh.THICKNESS + COUNT_RUNG) * transform.scale(),
                    slot.overlays(), face.h() * COUNT_SCALE, 0F, fade(0xFFFFFFFF));
            }

            // The pendulum scale, in the zone's own colour -- blue on the left
            // and red on the right, where a card prints them. The zone's OWN
            // scale, which is the same number on both sides of every printed
            // card and differs only when an effect has moved one; that is
            // exactly when showing the right one matters.
            //
            // Nudged toward the outer edge rather than centred, so it does not
            // sit on top of the card's own art the way a material count does.
            if(slot.hasScale() && FieldLayout.isPendulumZone(location, sequence))
            {
                boolean leftZone = sequence == 0;
                FieldLayout.Rect face = CardMesh.placement(zone, slot.defence());
                drawNumber(poseStack, collector, transform, camera, face,
                    (cardLift(transform) + CardMesh.THICKNESS + COUNT_RUNG) * transform.scale(),
                    leftZone ? slot.leftScale() : slot.rightScale(),
                    face.h() * SCALE_SCALE,
                    (leftZone ? -1F : 1F) * face.w() * 0.28F,
                    fade(leftZone ? DuelTextures.PENDULUM_BLUE : DuelTextures.PENDULUM_RED));
            }

            de.cas_ual_ty.dueldimension.clientutil.BoardTarget target =
                new de.cas_ual_ty.dueldimension.clientutil.BoardTarget(slot.code(), asked,
                    location, sequence, -1, "", 1, slot.art());

            // drawing.cpp bobs tAttack over any card that may attack, and the
            // 2D board does the same. Without it the battle phase is a board of
            // identical monsters with no sign of which have not swung yet --
            // which is the one thing a player needs to see in it.
            if(offers(target, de.cas_ual_ty.dueldimension.ocg.prompt.CardCommands.COMMAND_ATTACK))
            {
                // Turned to point AWAY from the monster standing under it.
                //
                // corners() numbers a rectangle the same way whoever asks, so an
                // untuned quad points one fixed way down the field: away from
                // seat 0 and therefore back at seat 1. The mark sat the right
                // way round for one duellist and pointed at its own controller
                // for the other -- a sword aimed at the monster carrying it.
                //
                // turnsFor is what the card's own art uses to decide which way
                // up it is printed for its side, which is the same question
                // asked of the same fact.
                WorldQuad.submit(poseStack, collector, DuelTextures.ATTACK, camera,
                    turned(transform.corners(CardMesh.placement(zone, slot.defence()),
                        (cardLift(transform) + CardMesh.THICKNESS + 0.045F) * transform.scale()),
                        FieldLayout.turnsFor(controller, false)),
                    fade(0xFFFFFFFF));
            }

            // Already picked for a selection that wants several. Green rather
            // than the offering blue, because "you may choose this" and "you
            // have chosen this" are different things, and a duellist part way
            // through three tributes has to tell them apart at a glance.
            if(de.cas_ual_ty.dueldimension.clientutil.DuelSelection.holds(
                DuelClientState.prompt, target))
            {
                WorldQuad.submit(poseStack, collector, DuelHighlight.OUTLINE, camera,
                    transform.corners(CardMesh.placement(zone, slot.defence()),
                        (cardLift(transform) + CardMesh.THICKNESS + 0.035F) * transform.scale()),
                    DuelHighlight.tinted(DuelHighlight.CHOSEN_GREEN, 0.95F));
            }

            // A card the engine is offering glows, so a duellist can see what
            // they may do without sweeping the cursor over the whole board.
            // Asked through the same filter that decides the click, so the glow
            // and the click can never disagree.
            if(de.cas_ual_ty.dueldimension.clientutil.PromptOptions.actionable(
                DuelClientState.prompt, false, target))
            {
                // Well clear of the card's own top face. At four thousandths
                // of a unit the glow and the face were close enough for a
                // depth buffer to call it a draw, and two translucent quads
                // that cannot be ordered flicker against each other.
                WorldQuad.submit(poseStack, collector, DuelHighlight.OUTLINE, camera,
                    transform.corners(CardMesh.placement(zone, slot.defence()),
                        (cardLift(transform) + CardMesh.THICKNESS + 0.03F) * transform.scale()),
                    DuelHighlight.tint(DuelHighlight.pulse(ticks())));
            }
        }
    }

    /**
     * Every colour the board is drawn in, taken down by however far its ending
     * has got.
     * <p>
     * One multiplier through one function, applied to every tint on the way
     * out, because a board that faded in parts would not read as a board
     * fading -- it would read as pieces going missing. Full strength for the
     * whole of a live duel, so this costs nothing until it matters.
     */
    private static int fade(int tint)
    {
        float alpha = ClientDuelField.endingAlpha();
        if(alpha >= 1F)
        {
            return tint;
        }
        int was = tint >>> 24;
        return Math.round(was * alpha) << 24 | (tint & 0xFFFFFF);
    }

    /** The board's own name for {@link WorldQuad#kindFor}, which owns the rule. */
    private static WorldQuad.Kind kindFor(int tint)
    {
        return WorldQuad.kindFor(tint);
    }

    /**
     * Is this zone's card currently coming apart?
     * <p>
     * Compared as the engine's own packed zone reference rather than by
     * unpacking one side or the other: the shatter carries the reference the
     * event was built with, and rebuilding it here from the same three facts
     * means the two cannot disagree about which square is breaking.
     */
    /**
     * Notices a battle so its clips can outlast it.
     * <p>
     * The animator only reports an attack while its own 667ms window is open,
     * and a Duelists of the Roses swing runs a median of 8.7 seconds — so the
     * view has to be caught while it exists and remembered afterwards. Latching
     * is idempotent on the attack's own start instant, so doing it once per
     * drawn monster per frame costs nothing and cannot make a clip restart.
     */
    /**
     * How long a clip runs, in seconds, or 0 where the model has no such slot.
     * <p>
     * Read off the file rather than assumed: these run anywhere from 1.7 to 33.7
     * seconds depending on the monster, so "when does it stop" is a property of
     * the creature and not a constant.
     */
    private static float clipLength(de.cas_ual_ty.dueldimension.clientutil.model.ModelMesh mesh,
        String animation)
    {
        int index = mesh.animationIndex(animation);
        return index < 0 || mesh.skeleton() == null ? 0F
            : mesh.skeleton().animations().get(index).duration();
    }

    private static void latchBattle(long now)
    {
        for(de.cas_ual_ty.dueldimension.clientutil.DuelAnimations.AttackView attack
            : DuelClientState.animations.attacksInFlight(now))
        {
            de.cas_ual_ty.dueldimension.clientutil.model.BattleAnimations.latch(
                attack.fromZone(), attack.start(), codeIn(attack.fromZone()));
        }
        // And the flinch from its own, later, event.
        for(de.cas_ual_ty.dueldimension.clientutil.DuelAnimations.BattleView blow
            : DuelClientState.animations.battlesInFlight(now))
        {
            de.cas_ual_ty.dueldimension.clientutil.model.BattleAnimations.latchHurt(
                blow.zone(), blow.start(), codeIn(blow.zone()));
        }
    }

    /**
     * The card standing in a packed zone, so a swing cannot be inherited by
     * whatever occupies that square once its owner has left it.
     */
    private static long codeIn(int ref)
    {
        if(ref < 0)
        {
            return 0L;
        }
        BoardSnapshot board = DuelClientState.board;
        if(board == null)
        {
            return 0L;
        }
        // Unpacked the way EnginePrompt.zoneRef packed it: bit 16 the far seat,
        // bit 8 a monster zone, the low three bits the sequence.
        List<BoardSnapshot.Slot> zones = ((ref & 16) != 0 ? board.opponent() : board.self())
            .monsters();
        int sequence = ref & 7;
        if((ref & 8) == 0 || sequence >= zones.size())
        {
            return 0L;
        }
        return zones.get(sequence).code();
    }

    private static boolean shattering(int asked, int location, int sequence)
    {
        List<de.cas_ual_ty.dueldimension.clientutil.DuelAnimations.ShatterView> breaks =
            DuelClientState.animations.shattersInFlight(System.currentTimeMillis());
        if(breaks.isEmpty())
        {
            return false;
        }
        int ref = de.cas_ual_ty.dueldimension.ocg.prompt.EnginePrompt.zoneRef(asked == 1,
            location == OcgConstants.LOCATION_MZONE, sequence);
        for(de.cas_ual_ty.dueldimension.clientutil.DuelAnimations.ShatterView shatter : breaks)
        {
            if(shatter.fromZone() == ref)
            {
                return true;
            }
        }
        return false;
    }

    /**
     * The monster standing on a card during a duel.
     * <p>
     * Face-up monsters only. A set card is one nobody may identify, and a
     * monster looming over one would announce what it is to the room -- which
     * on a board both duellists walk around is a leak rather than a slip. The
     * client is not even told what an opponent's set monster IS, so the rule is
     * enforced twice over.
     */
    private static void drawHologram(PoseStack poseStack, SubmitNodeCollector collector,
        FieldTransform transform, Vec3 camera, FieldLayout.Rect zone, BoardSnapshot.Slot slot,
        int location, int asked, int controller, int sequence)
    {
        // Asked per side, because the setting has a middle position that keeps
        // the opponent's monsters and drops your own -- yours being the ones
        // standing between your eye and the half of the board you have to read.
        if(!de.cas_ual_ty.dueldimension.clientutil.HologramSettings.showsFor(asked == 0)
            || location != OcgConstants.LOCATION_MZONE || slot.faceDown() || slot.code() == 0)
        {
            return;
        }
        // Out of the way entirely while you are looking across the table.
        //
        // Yours are the NEAR ones. A board is stood at rather than looked down
        // on, so your own monsters are the things between your eye and the far
        // half -- and the far half is where the opponent's zones are, which are
        // the ones you have to read and click on. Thinning them was not enough:
        // half of a Blue-Eyes is still a Blue-Eyes in front of the card you are
        // trying to point at.
        //
        // Only yours, because the asymmetry is real rather than a preference.
        // Their monsters stand on the far side and block nothing of yours.
        // Faded rather than switched. Both of the conditions that hide a
        // monster -- looking across the table, and reading the far back row --
        // change the instant a head turns, so applying them directly made the
        // board flash as you glanced along a row. They become a target here and
        // the fade carries the monster to it.
        long fadeNow = System.currentTimeMillis();
        int fadeRef = de.cas_ual_ty.dueldimension.ocg.prompt.EnginePrompt.zoneRef(asked == 1,
            true, sequence);
        float want = asked == 0 && lookingAcross() ? 0F : targetSolidity(asked);
        float solid = HologramFade.toward(fadeRef, want, fadeNow);
        if(solid <= 0.02F)
        {
            // Gone, and cheap to say so: nothing below this point runs for a
            // monster nobody can see.
            return;
        }
        MonsterSprites.Definition definition = MonsterSprites.of(slot.code());
        SpriteLayer body = MonsterSprites.layerFor(slot.code(), slot.defence());
        // A model may stand in for the sprite, but the sprite is still required:
        // it is what the monster looks like if the .glb is missing, unreadable,
        // or uses a corner of glTF the loader will not guess at.
        de.cas_ual_ty.dueldimension.clientutil.model.ModelMesh mesh =
            definition != null && definition.hasModel()
                ? de.cas_ual_ty.dueldimension.clientutil.model.MonsterModels
                    .get(definition.model())
                : null;
        if(body == null && mesh == null)
        {
            return;
        }
        Wings wings = MonsterSprites.wingsFor(slot.code());
        FieldLayout.Rect card = CardMesh.placement(zone, slot.defence());
        Vec3 feet = transform.at(card.x() + card.w() / 2F, card.y() + card.h() / 2F,
            (cardLift(transform) + CardMesh.THICKNESS + 0.002F) * transform.scale());
        // Measured against a card's LONG side, so a monster is the same height
        // whether its card is standing or lying -- a defending monster that
        // shrank to two thirds would read as a weaker one.
        float height = CardMesh.CARD_H * transform.scale()
            * MonsterSprites.heightFor(slot.code());

        if(mesh != null)
        {
            // Facing the CONTROLLER's way, not the viewer's. A sprite has to
            // turn to whoever is looking or it is seen edge-on and disappears;
            // a model has a front, and the front of a monster belongs to the
            // duellist it is being played against. Both duellists watch the
            // same board from opposite ends, so this cannot come from the
            // camera without the two of them disagreeing about which way a
            // dragon is looking.
            // Swinging, flinching, or standing about. The clip and how far into
            // it are asked separately because a monster whose file has no attack
            // keeps its idle, and one whose attack has finished stands up again
            // while the latch is still holding the zone.
            long now = System.currentTimeMillis();
            latchBattle(now);
            int ref = de.cas_ual_ty.dueldimension.ocg.prompt.EnginePrompt.zoneRef(asked == 1,
                location == OcgConstants.LOCATION_MZONE, sequence);
            String clip = de.cas_ual_ty.dueldimension.clientutil.model.BattleAnimations
                .clipFor(ref);
            String animation = definition.animation();
            float phase = Float.NaN;
            if(clip != null)
            {
                float length = clipLength(mesh, clip);
                float at = de.cas_ual_ty.dueldimension.clientutil.model.BattleAnimations
                    .phaseOf(ref, slot.code(), clip, length, now);
                if(at >= 0F)
                {
                    animation = clip;
                    phase = at;
                }
            }
            de.cas_ual_ty.dueldimension.clientutil.model.ModelHologram.submit(poseStack,
                collector, camera, feet, height, mesh,
                transform.siting().look(controller), fade(solidity(solid)),
                animation, definition.elevation(), definition.turn(),
                definition.offsetX(), definition.offsetZ(), phase);
            return;
        }
        // Straight up in world space, which is the axis the sprite stands on
        // however the field beneath it is turned.
        feet = feet.add(0D, MonsterSprites.bobAt(body, (long)ticks()) * height, 0D);
        MonsterBillboard.submit(poseStack, collector, camera, camera, feet, height, body,
            MonsterSprites.frameAt(body, (long)ticks()), wings,
            wings == null ? 0 : MonsterSprites.frameAt(wings.layer(), (long)ticks()),
            fade(solidity(solid)), slot.code());
    }

    /**
     * How solid a monster is drawn, which is not the same on both sides of the
     * board.
     * <p>
     * YOUR OWN are half there whenever they are drawn at all. They stand
     * between you and your own back row, and you already know what you played
     * -- a duellist needs to SEE their spell and trap line far more than they
     * need to be reminded of the monster they summoned a moment ago. They are
     * not drawn at all while you are looking at the opponent's half; see
     * {@link #drawHologram}, which never reaches this.
     * <p>
     * THEIRS are solid, because those are the ones worth looking at. But they
     * stand between you and the opponent's back row as well, and that row is
     * something you have to read and click on -- so they thin out the moment
     * you look at it, or whenever Shift is held. Shift is the same key the card
     * text and the stats already answer to: one hold, everything gets out of
     * the way.
     */
    /** White at the given solidity, which is what both paths now draw with. */
    private static int solidity(float solid)
    {
        int alpha = Math.clamp(Math.round(solid * 255F), 0, 255);
        return (alpha << 24) | 0x00FFFFFF;
    }

    /**
     * How solid a monster should SETTLE at, before the fade carries it there.
     * <p>
     * The same judgement {@link #hologramTint} made, as a number rather than as
     * one of two colours, so that the value between them means something.
     */
    private static float targetSolidity(int asked)
    {
        return (hologramTint(asked) >>> 24) / 255F;
    }

    private static int hologramTint(int asked)
    {
        if(asked == 0)
        {
            return HOLOGRAM_FAINT;
        }
        de.cas_ual_ty.dueldimension.clientutil.BoardTarget looking =
            ClientDuelTargeting.looking();
        boolean atTheirBackRow = looking != null && looking.controller() == 1
            && looking.location() == OcgConstants.LOCATION_SZONE;
        return atTheirBackRow || ClientDuelField.shiftHeld() ? HOLOGRAM_FAINT : 0xFFFFFFFF;
    }

    /**
     * Is the duellist looking at the opponent's half of the board?
     * <p>
     * Any square of it, not only the back row: a graveyard, a deck and a
     * monster zone are all things over there that a near monster can stand in
     * front of, and a rule that only cleared the view for one of them would
     * leave the others obstructed for no reason a player could work out.
     * <p>
     * Read once into a local. The target is recomputed on the client tick and
     * this runs on the render thread, so asking twice is asking two questions.
     * <p>
     * Answers to the cursor as well as to the crosshair, because
     * {@link ClientDuelTargeting} is set by both -- {@code tick} follows the
     * eye while no screen is open and {@code point} follows the freed pointer
     * while one is. A duellist who has the cursor up is pointing at the
     * opponent's board just as deliberately as one who is aiming at it.
     */
    private static boolean lookingAcross()
    {
        de.cas_ual_ty.dueldimension.clientutil.BoardTarget looking =
            ClientDuelTargeting.looking();
        return looking != null && looking.controller() == 1;
    }

    /** Half there: enough to read the monster, enough to read through it. */
    private static final int HOLOGRAM_FAINT = 0x80FFFFFF;

    /**
     * Does the engine offer this exact command for this card?
     * <p>
     * Asked of the prompt rather than of a status bit, because the prompt is
     * where the answer actually is: ocgcore reports which cards may attack as a
     * list in MSG_SELECT_BATTLECMD, and {@code CardCommands} folds that list
     * into the bitmask this reads. No second opinion, and nothing to drift.
     */
    private static boolean offers(de.cas_ual_ty.dueldimension.clientutil.BoardTarget target,
        int command)
    {
        de.cas_ual_ty.dueldimension.ocg.prompt.EnginePrompt prompt = DuelClientState.prompt;
        if(prompt == null)
        {
            return false;
        }
        for(int index : de.cas_ual_ty.dueldimension.clientutil.PromptOptions.optionsFor(
            prompt, false, target))
        {
            if(prompt.options().get(index).command() == command)
            {
                return true;
            }
        }
        return false;
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
