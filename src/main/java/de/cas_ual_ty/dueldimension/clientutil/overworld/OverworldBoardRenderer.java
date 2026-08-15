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
            WorldQuad.submit(poseStack, collector, kindFor(matTint), piece.texture(), camera,
                transform.corners(piece.rect(), SURFACE_LIFT + piece.lift()), matTint);
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

        drawCards(poseStack, collector, transform, camera);
        drawEquipLinks(poseStack, collector, transform, camera);
        drawAttacks(poseStack, collector, transform, camera);
        drawShatters(poseStack, collector, transform, camera);
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
            drawPile(poseStack, collector, transform, camera, controller, asked,
                OcgConstants.LOCATION_DECK, side.deckCount(), back, back);
            drawPile(poseStack, collector, transform, camera, controller, asked,
                OcgConstants.LOCATION_EXTRA, size(side.extra()), back, back);
            drawPile(poseStack, collector, transform, camera, controller, asked,
                OcgConstants.LOCATION_GRAVE, size(side.grave()),
                topFace(side.grave(), controller, back), back);
            drawPile(poseStack, collector, transform, camera, controller, asked,
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

            int heldTint = fade(0xFFFFFFFF);
            WorldQuad.submit(poseStack, collector, kindFor(heldTint), back, camera,
                new Vec3[] {bottomLeft, topLeft, topRight, bottomRight}, heldTint);
            WorldQuad.submit(poseStack, collector, kindFor(heldTint), back, camera,
                new Vec3[] {bottomRight, topRight, topLeft, bottomLeft}, heldTint);
        }
    }

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
                slot.defence(), cardLift(transform), CardFaces.face(slot, false, controller),
                CardFaces.underside(slot, controller), fade(0xFFFFFFFF));

            drawHologram(poseStack, collector, transform, camera, zone, slot, location, asked);

            de.cas_ual_ty.dueldimension.clientutil.BoardTarget target =
                new de.cas_ual_ty.dueldimension.clientutil.BoardTarget(slot.code(), asked,
                    location, sequence, -1, "", 1, slot.art());

            // drawing.cpp bobs tAttack over any card that may attack, and the
            // 2D board does the same. Without it the battle phase is a board of
            // identical monsters with no sign of which have not swung yet --
            // which is the one thing a player needs to see in it.
            if(offers(target, de.cas_ual_ty.dueldimension.ocg.prompt.CardCommands.COMMAND_ATTACK))
            {
                WorldQuad.submit(poseStack, collector, DuelTextures.ATTACK, camera,
                    transform.corners(CardMesh.placement(zone, slot.defence()),
                        (cardLift(transform) + CardMesh.THICKNESS + 0.045F) * transform.scale()),
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

    /**
     * Which render type a tint can actually be drawn through -- and the reason
     * a five second fade used to look like a board vanishing in one frame.
     * <p>
     * SOLID is an alpha-TESTED cutout. The test keeps a texel or throws it
     * away; it does not mix. So the mat and the card backs, which were always
     * SOLID, took the faded tint and drew themselves at full strength anyway,
     * frame after frame, until the alpha crossed the threshold -- at which
     * point the entire board went out at once. The fade was running correctly
     * the whole time and the render type was discarding the answer.
     * <p>
     * Anything short of opaque therefore goes through the blended type. It
     * writes no depth, which for a stack of flat parallel planes drawn from the
     * mat upwards is no loss: they are already submitted in the order they sit
     * in.
     */
    private static WorldQuad.Kind kindFor(int tint)
    {
        return (tint >>> 24) >= 0xFF ? WorldQuad.Kind.SOLID : WorldQuad.Kind.GLOW;
    }

    /**
     * Is this zone's card currently coming apart?
     * <p>
     * Compared as the engine's own packed zone reference rather than by
     * unpacking one side or the other: the shatter carries the reference the
     * event was built with, and rebuilding it here from the same three facts
     * means the two cannot disagree about which square is breaking.
     */
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
        int location, int asked)
    {
        if(!de.cas_ual_ty.dueldimension.clientutil.HologramSettings.enabled()
            || location != OcgConstants.LOCATION_MZONE || slot.faceDown() || slot.code() == 0)
        {
            return;
        }
        SpriteLayer body = MonsterSprites.layerFor(slot.code(), slot.defence());
        if(body == null)
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
        // Straight up in world space, which is the axis the sprite stands on
        // however the field beneath it is turned.
        feet = feet.add(0D, MonsterSprites.bobAt(body, (long)ticks()) * height, 0D);
        MonsterBillboard.submit(poseStack, collector, camera, camera, feet, height, body,
            MonsterSprites.frameAt(body, (long)ticks()), wings,
            wings == null ? 0 : MonsterSprites.frameAt(wings.layer(), (long)ticks()),
            fade(hologramTint(asked)), slot.code());
    }

    /**
     * How solid a monster is drawn, which is not the same on both sides of the
     * board.
     * <p>
     * YOUR OWN are always half there. They stand between you and your own back
     * row, and you already know what you played -- a duellist needs to SEE their
     * spell and trap line far more than they need to be reminded of the monster
     * they summoned a moment ago.
     * <p>
     * THEIRS are solid, because those are the ones worth looking at. But they
     * stand between you and the opponent's back row as well, and that row is
     * something you have to read and click on -- so they thin out the moment
     * you look at it, or whenever Shift is held. Shift is the same key the card
     * text and the stats already answer to: one hold, everything gets out of
     * the way.
     */
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
