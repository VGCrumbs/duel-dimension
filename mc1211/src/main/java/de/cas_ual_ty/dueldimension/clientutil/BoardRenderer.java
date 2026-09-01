package de.cas_ual_ty.dueldimension.clientutil;

import com.mojang.blaze3d.vertex.PoseStack;
import de.cas_ual_ty.dueldimension.DdDatabase;
import de.cas_ual_ty.dueldimension.card.properties.Properties;
import de.cas_ual_ty.dueldimension.ocg.OcgConstants;
import de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot;
import de.cas_ual_ty.dueldimension.ocg.prompt.EnginePrompt;
import net.minecraft.client.gui.Font;
import de.cas_ual_ty.dueldimension.compat.SubmitNodeCollector;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Draws the duel field as a tilted table: zone rectangles come from
 * {@link FieldLayout} (EDOPro's own coordinates) projected through a
 * trapezoid, so the far side narrows and rows bunch towards the horizon the
 * way the reference client's 3D scene does. Cards are drawn as four-cornered
 * quads rather than upright rectangles, so they lie on the table.
 * <p>
 * Each slot records a {@link Hit} carrying controller, location and
 * sequence, so the screen can ask what that exact card may do.
 * <p>
 * <b>Two passes.</b> {@link #layout} settles where everything is and builds the
 * hits; {@link #render} only paints that. They are separate because the screen
 * needs the hit rectangles during its own extract while the painting happens
 * later in the frame — see {@link #layout}.
 * <p>
 * <b>Everything that draws takes a {@code PoseStack} and a
 * {@code SubmitNodeCollector}, not a {@code GuiGraphicsExtractor.}</b> A
 * trapezoid is not a blit, and the only way to draw one is
 * {@code submitCustomGeometry}, which exists solely inside a render pass. A
 * screen has no pass, so it opens one as a picture-in-picture region (see
 * {@link BoardPip}) and hands the pair down this whole chain. The origin is
 * already the region's top-left, so every coordinate below is an ordinary
 * screen coordinate.
 */
public class BoardRenderer
{
    /** Cards are small, so a light subdivision removes the affine smear. */
    private static final int CARD_STEPS = 4;

    /** Both from {@link FieldLayout}, which is where the one copy lives. */
    private static final float CARD_W = FieldLayout.CARD_W;
    private static final float CARD_H = FieldLayout.CARD_H;

    /**
     * Per-card lift of a pile's top face, in field units. client_field.cpp
     * raises each pile card by 0.01 of Z; a 40-card deck there stands 0.4
     * units tall, and 0.008 here reads the same through our flatter mapping.
     */
    private static final float LIFT_PER_CARD = 0.008F;
    /** Past this the stack stops growing, so a 60-card deck still fits. */
    private static final int LIFT_CAP = 45;

    private static final int COLOUR_ZONE = 0x50FFFFFF;
    private static final int COLOUR_HIGHLIGHT = 0xC000FF66;
    private static final int COLOUR_HIGHLIGHT_FILL = 0x4000FF66;
    /**
     * The persistent zone grid.
     * <p>
     * EDOPro's mat carries no slot dividers of its own — field4.png prints one
     * open panel per player plus the two extra monster zones, and nothing
     * between the five columns (verified against the texture: the panel is a
     * flat #000 at alpha 207 from border to border). EDOPro gets away with that
     * because it only outlines zones while the core is asking you to pick one,
     * via DrawSelectionLine at width 2. Standing still, its board shows no
     * slots at all.
     * <p>
     * A mat with no visible rows or columns reads as broken, so every zone
     * keeps a box, drawn as a 2px outline in EDOPro's own line width.
     * <p>
     * The box is inset from the zone rather than matching it. materials.cpp
     * gives cells a width of 1.1 at a column pitch of 1.1 and rows that meet at
     * y = 2.0, so zones tile edge to edge: outlining them exactly draws every
     * interior line twice and fills them merges all 22 into one slab, which is
     * why the first attempt came out as a grey haze instead of a grid. Insetting
     * leaves a gap between neighbours so each slot reads as its own box.
     * <p>
     * Only the idle grid is inset. A zone the core is actually offering keeps
     * the exact zone quad, matching DrawSelectionLine, so selection still lands
     * on the true zone and reads as the box growing slightly.
     */
    private static final int COLOUR_GRID = 0xC0C8D0DC;
    private static final float GRID_INSET = 0.06F;

    /**
     * On-field ATK/DEF: raised reads blue, lowered red, printed white.
     * <p>
     * Each carries its alpha byte. Forge's {@code Font.draw} filled in an
     * opaque alpha for a colour that had none; the collector does not, and a
     * plain 0xFFFFFF submits fully transparent text — no error, nothing drawn.
     */
    private static final int COLOUR_STAT_PLAIN = 0xFFFFFFFF;
    private static final int COLOUR_STAT_HIGHER = 0xFF66B2FF;
    private static final int COLOUR_STAT_LOWER = 0xFFFF4C4C;
    private static final int COLOUR_STAT_SLASH = 0xFF9A9A9A;
    /** Stats are drawn small so they sit on a card without covering the art. */
    private static final float STAT_SCALE = 0.5F;
    private static final int COLOUR_ACTIONABLE = 0xE0FFD700;
    /** custom_skin_enum.inl: DECLR(DUELFIELD_STACK, 0xffffff00). */
    private static final int COLOUR_STACK = 0xFFFFFF00;

    /**
     * Lit as if in full daylight. The board is a picture, not a thing in the
     * world: its text must not dim because the player is standing in a cave.
     */
    private static final int TEXT_LIGHT = 0xF000F0;

    /** Drawn after the board's geometry rather than under it. */
    /**
     * Above every geometry layer, whatever the board turned out to need.
     * <p>
     * Within one order the text phase runs BEFORE custom geometry, so text
     * sharing a layer with the board is painted over by it. It only has to beat
     * the highest {@link #next()} handed out, and a board has a few hundred
     * draws at most, so this is simply out of reach of the counter.
     */
    private static final int TEXT_ORDER = 1_000_000;


    /**
     * What colour an actionable card breathes: baby blue for spells, pink for
     * traps, yellow for monsters, white when the card is unknown to us.
     */
    private static float[] glowTint(int code)
    {
        Properties card = code == 0 ? null : DdDatabase.PROPERTIES_LIST.get((long)code);
        if(card == null)
        {
            return new float[] {1F, 1F, 1F};
        }
        if(card.getIsSpell())
        {
            return new float[] {0.55F, 0.85F, 1F};
        }
        if(card.getIsTrap())
        {
            return new float[] {1F, 0.55F, 0.8F};
        }
        return new float[] {1F, 0.9F, 0.45F};
    }

    /**
     * How far a card's highlight stands clear of the card, in field units.
     * <p>
     * <b>Not zero, and it cannot be.</b> The world board draws its outline OVER
     * the card, so an outline exactly the card's size is the whole effect there.
     * Here the highlight goes down BEFORE the card art and is a filled box, so a
     * card-sized one would be entirely behind the card and show nothing at all.
     * What is seen is exactly this margin, which is therefore what has to be
     * chosen rather than inherited.
     * <p>
     * At the ~122 screen pixels per field unit the board is laid out at, this is
     * about five pixels of halo -- a quarter of what the zone box gave it on the
     * left and right, and even on all four sides, which the zone never was.
     */
    private static final float GLOW_SPILL = 0.04F;

    /** {@link #GLOW_SPILL} on every side of a rect. */
    private static FieldLayout.Rect spilled(FieldLayout.Rect rect)
    {
        return new FieldLayout.Rect(rect.x() - GLOW_SPILL, rect.y() - GLOW_SPILL,
            rect.w() + GLOW_SPILL * 2F, rect.h() + GLOW_SPILL * 2F);
    }

    /**
     * A soft glow: three nested shells widening by a pixel and a half each,
     * fading as they go, so the edge feathers out instead of cutting off.
     */
    private static void drawFeatheredGlow(PoseStack poseStack, SubmitNodeCollector collector,
        FieldQuad.Corners corners, float[] tint, float strength)
    {
        float[] spread = {1.5F, 3F, 4.5F};
        float[] fade = {0.45F, 0.25F, 0.12F};
        for(int shell = 0; shell < spread.length; shell++)
        {
            float e = spread[shell];
            FieldQuad.Corners ring = new FieldQuad.Corners(
                corners.x0() - e, corners.y0() - e, corners.x1() + e, corners.y1() - e,
                corners.x2() + e, corners.y2() + e, corners.x3() - e, corners.y3() + e);
            FieldQuad.drawCorners(poseStack, collector, DuelTextures.WHITE, ring, 0F, 0F, 1F, 1F,
                tint[0], tint[1], tint[2], strength * fade[shell]);
        }
    }
    /** The reference's numFont is a display size; ours scales up to match. */
    private static final float STACK_NUM_SCALE = 1.4F;

    /**
     * One run of text, drawn inside the board's own pass.
     * <p>
     * A screen draws text through its extractor and there is no extractor in
     * here; the collector takes a {@code FormattedCharSequence} directly. The
     * background and outline colours are 0 — the stat caption paints its own
     * backdrop as a quad, so the font's would double it.
     */
    private static void submitText(PoseStack poseStack, SubmitNodeCollector collector,
        float x, float y, String text, int colour)
    {
        // Order 1, not the default 0, and this is the whole reason text on the
        // board is visible at all. Within one order group
        // FeatureRenderDispatcher.executeTranslucent runs
        //     ... -> texts -> translucentCustomGeometry
        // and FieldQuad's draws are custom geometry, so text submitted at the
        // same order is painted over by the board that was described before it.
        // submitsPerOrder is a sorted map drained ascending, so a higher order
        // runs strictly after -- which is what "on top" means here.
        collector.order(TEXT_ORDER).submitText(poseStack, x, y, Component.literal(text).getVisualOrderText(),
            false, Font.DisplayMode.NORMAL, TEXT_LIGHT, colour, 0, 0);
    }

    /**
     * Whether this Spell/Trap zone is also a Pendulum Zone.
     * <p>
     * Zones 0 and 4, the ends of the backrow. That is where ocgcore puts a
     * scale whenever DUEL_PZONE is set without DUEL_SEPARATE_PZONE, which is
     * every duel this mod runs (MR5). The separate zones FieldLayout still
     * describes at sequence 6 and 7 are the MR3 arrangement and stay empty
     * here, which is why they are not drawn.
     */
    private static boolean isPendulumZone(int location, int sequence)
    {
        return FieldLayout.isPendulumZone(location, sequence);
    }

    /**
     * The scales a pendulum card is currently at, printed on its zone.
     * <p>
     * Blue on the left and red on the right, which is where a card prints them
     * and the order {@code addPendulumTextHeader} writes them in. The values
     * are the engine's live ones, so a scale an effect has moved reads as the
     * duel is actually playing it rather than as the card was printed.
     */
    private void drawScales(PoseStack poseStack, SubmitNodeCollector collector, ZonePlan zone)
    {
        BoardSnapshot.Slot slot = zone.slot();
        if(!slot.hasScale() || !isPendulumZone(zone.hit().location(), zone.hit().sequence()))
        {
            return;
        }
        // The zone's own scale, in the zone's own colour: a blue zone showing a
        // red number would contradict the gem printed under it. The two are the
        // same value on every printed card, and differ only when an effect has
        // moved one of them -- which is exactly when showing the right one
        // matters.
        boolean leftZone = zone.hit().sequence() == 0;
        FieldQuad.Corners corners = zone.hit().corners();
        String scale = Integer.toString(leftZone ? slot.leftScale() : slot.rightScale());
        // Along the bottom of the zone, on the side the gem sits.
        float y = corners.maxY() - 9;
        float x = leftZone ? corners.minX() + 2 : corners.maxX() - 2 - font.width(scale);
        submitText(poseStack, collector, x, y, scale,
            leftZone ? DuelTextures.PENDULUM_BLUE : DuelTextures.PENDULUM_RED);
    }

    /** A drawn slot; piles use sequence -1. */
    public record Hit(FieldQuad.Corners corners, int code, int controller, int location, int sequence,
        int zoneRef, String label, int count, int art)
    {
        /**
         * The shape before per-copy artwork. A hit on something that is not one
         * physical card -- a pile, a zone -- has no artwork of its own.
         */
        public Hit(FieldQuad.Corners corners, int code, int controller, int location, int sequence,
            int zoneRef, String label, int count)
        {
            this(corners, code, controller, location, sequence, zoneRef, label, count, 0);
        }

        public boolean contains(double mouseX, double mouseY)
        {
            return corners.contains(mouseX, mouseY);
        }

        public boolean isPile()
        {
            return sequence < 0;
        }

        /**
         * This hit with its rectangle taken off: what it is, rather than where
         * on the screen it was. The form the legality filter takes, so the
         * board in the world can ask the same question without a rectangle.
         */
        public de.cas_ual_ty.dueldimension.clientutil.BoardTarget target()
        {
            return new de.cas_ual_ty.dueldimension.clientutil.BoardTarget(code, controller,
                location, sequence, zoneRef, label, count, art);
        }

        public int x()
        {
            return corners.minX();
        }

        public int y()
        {
            return corners.minY();
        }

        public int w()
        {
            return corners.maxX() - corners.minX();
        }

        public int h()
        {
            return corners.maxY() - corners.minY();
        }
    }

    /**
     * One thing the layout pass worked out and the draw pass paints, in the
     * order it was worked out.
     * <p>
     * Zones and piles are interleaved -- a side's zones, then that side's piles,
     * then the other side's -- and a pile must be painted over the one behind
     * it, so the two cannot be kept in separate lists and replayed one after the
     * other without moving pixels. One ordered list keeps the draw order the
     * single-pass version had for free.
     */
    private sealed interface Plan permits ZonePlan, PilePlan
    {
    }

    /**
     * A zone and, if it holds one, the card lying in it.
     *
     * @param cardRect null when the zone is empty
     * @param cardTurns quarter turns of that card; see {@link #turnsFor}
     */
    private record ZonePlan(FieldLayout.Rect rect, Hit hit, BoardSnapshot.Slot slot,
        FieldLayout.Rect cardRect, int cardTurns) implements Plan
    {
    }

    /**
     * A pile: its zone, and the block of cards standing on it.
     *
     * @param pileCard the card-sized rectangle the stack is built on, null when
     *                 the pile is empty (and then so are the two corner sets)
     */
    private record PilePlan(int controller, int location, FieldLayout.Rect rect, Hit hit, int count,
        FieldLayout.Rect pileCard, FieldQuad.Corners baseCorners, FieldQuad.Corners topCorners,
        int turns) implements Plan
    {
    }

    /** A bare card square for a zone the playmat does not print. */
    private record SquarePlan(int controller, FieldLayout.Rect box, FieldLayout.Rect rose, int turns)
    {
    }

    /**
     * One card held in a hand, already stood upright.
     *
     * @param slot what the card is; the opponent's are already blanked
     */
    private record HandPlan(BoardSnapshot.Slot slot, Hit hit)
    {
    }

    private final List<Hit> hits = new ArrayList<>();
    /** Each side's mat block, indexed by controller. */
    private final FieldLayout.Rect[] matBands = new FieldLayout.Rect[2];
    private final List<SquarePlan> squarePlans = new ArrayList<>();
    private final List<Plan> fieldPlans = new ArrayList<>();
    private final List<HandPlan> handPlans = new ArrayList<>();
    private Set<Integer> zoneHighlights = Set.of();
    private java.util.function.Predicate<Hit> actionable = hit -> false;
    private java.util.function.Predicate<Hit> canAttack = hit -> false;
    private java.util.function.Predicate<Hit> canActivate = hit -> false;
    private FieldLayout.Projection projection;
    /** Set for the duration of a render, so zone drawing can label stats. */
    private Font font;
    /** The board being drawn, so piles can reach their own contents. */
    private BoardSnapshot currentBoard;
    /** Each side's chosen playmat: index 0 is you, 1 the opponent. */
    private PlayMats[] mats = {PlayMats.CLASSIC, PlayMats.CLASSIC};

    /** The mat this seat brought to the table. */
    public void setMats(PlayMats self, PlayMats opponent)
    {
        mats = new PlayMats[] {self == null ? PlayMats.CLASSIC : self,
            opponent == null ? PlayMats.CLASSIC : opponent};
        matColours = new int[] {DuelClientState.matColour(), DuelClientState.opponentMatColour};
    }

    /**
     * Both mats are now the same neutral texture, tinted per side. One
     * greyscale source multiplied by a colour is what lets a player pick any
     * colour at all instead of choosing from six files.
     */
    private ResourceLocation mat(int controller)
    {
        return de.cas_ual_ty.dueldimension.clientutil.hub.HubTextures.CUSTOM_MAT;
    }

    /** The colour this side's mat and bare zones are drawn in. */
    private int matColour(int controller)
    {
        return matColours[controller];
    }

    /** Per-side mat colours, mirroring {@link #mats}. */
    private int[] matColours = {DuelClientState.DEFAULT_MAT_COLOUR, DuelClientState.DEFAULT_MAT_COLOUR};

    /**
     * How many quarter turns a card of this controller's is drawn at.
     * client_field.cpp: {@code selfATK {0,0,0}} against {@code oppoATK
     * {0,0,PI}} — the opponent's cards face the opponent. A defending monster
     * adds the quarter turn on top of that ({@code selfDEF -HALF_PI},
     * {@code oppoDEF +HALF_PI}).
     */
    private static int turnsFor(int controller, boolean lying)
    {
        return FieldLayout.turnsFor(controller, lying);
    }

    public List<Hit> hits()
    {
        return hits;
    }

    /**
     * Resolves overlapping projected zones using a caller-defined priority.
     * The Extra Monster Zones are shared physical squares, but the engine keeps
     * one logical entry per controller; taking the first match can therefore
     * hide an occupied center card behind its empty logical twin.
     */
    public Hit hitAt(double mouseX, double mouseY,
        java.util.function.ToIntFunction<Hit> priority)
    {
        Hit best = null;
        int bestPriority = Integer.MIN_VALUE;
        for(Hit hit : hits)
        {
            if(!hit.contains(mouseX, mouseY))
            {
                continue;
            }
            int candidatePriority = priority.applyAsInt(hit);
            if(best == null || candidatePriority > bestPriority)
            {
                best = hit;
                bestPriority = candidatePriority;
            }
        }
        return best;
    }

    /**
     * The link between an equip card and the monster it is attached to, drawn
     * while either end is hovered.
     * <p>
     * EDOPro marks the relation rather than drawing it: hovering a card sets
     * {@code is_showequip} on its partner ({@code ClientField::SetShowMark},
     * event_handler.cpp:2746) and the partner then wears {@code tEquip} over
     * its face ({@code drawing.cpp}:404). That badge is kept — it is the
     * reference's own art, at the reference's own size, {@code vSymbol} being
     * 0.7 field units square, exactly the width of a card quad — and a line is
     * drawn between the two so the pairing is legible at a glance even when
     * several equips are out.
     * <p>
     * The core reports one direction only, {@code card::equiping_target}, so
     * both are found by sweeping every on-field slot for an equip whose own
     * zone or whose target zone is the hovered one. This mirrors EDOPro, which
     * builds its reverse {@code equipped} set the same way.
     * <p>
     * Both endpoints are looked up in the hits {@link #layout} built, and the
     * hovered end was picked from that same list, so a link joins two rectangles
     * of one frame.
     */
    public void drawEquipLinks(PoseStack poseStack, SubmitNodeCollector collector,
        BoardSnapshot board, Hit hovered)
    {
        if(hovered == null || hovered.isPile())
        {
            return;
        }
        for(int controller = 0; controller < 2; controller++)
        {
            BoardSnapshot.Side side = controller == 0 ? board.self() : board.opponent();
            drawEquipLinks(poseStack, collector, side.monsters(), controller,
                OcgConstants.LOCATION_MZONE, hovered);
            drawEquipLinks(poseStack, collector, side.spells(), controller,
                OcgConstants.LOCATION_SZONE, hovered);
        }
    }

    private void drawEquipLinks(PoseStack poseStack, SubmitNodeCollector collector,
        List<BoardSnapshot.Slot> slots, int controller, int location, Hit hovered)
    {
        for(int sequence = 0; sequence < slots.size(); sequence++)
        {
            BoardSnapshot.Slot slot = slots.get(sequence);
            if(slot == null || !slot.present() || slot.equip() == null)
            {
                continue;
            }
            boolean fromHovered = at(hovered, controller, location, sequence);
            boolean toHovered = at(hovered, slot.equip().controller(), slot.equip().location(),
                slot.equip().sequence());
            if(!fromHovered && !toHovered)
            {
                continue;
            }
            Hit other = fromHovered
                ? find(slot.equip().controller(), slot.equip().location(), slot.equip().sequence())
                : find(controller, location, sequence);
            if(other != null)
            {
                drawLink(poseStack, collector, hovered.corners(), other.corners());
            }
        }
    }

    private static boolean at(Hit hit, int controller, int location, int sequence)
    {
        return hit.controller() == controller && hit.location() == location && hit.sequence() == sequence;
    }

    private Hit find(int controller, int location, int sequence)
    {
        for(Hit hit : hits)
        {
            if(at(hit, controller, location, sequence))
            {
                return hit;
            }
        }
        return null;
    }

    /** Amber, so an equip link never reads as the red attack line. */
    private static final float LINK_R = 1F;
    private static final float LINK_G = 0.82F;
    private static final float LINK_B = 0.30F;
    private static final float LINK_HALF_WIDTH = 1.6F;

    private static void drawLink(PoseStack poseStack, SubmitNodeCollector collector,
        FieldQuad.Corners from, FieldQuad.Corners to)
    {
        float x1 = centreX(from);
        float y1 = centreY(from);
        float x2 = centreX(to);
        float y2 = centreY(to);
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float)Math.sqrt(dx * dx + dy * dy);
        if(length >= 1F)
        {
            float px = -dy / length * LINK_HALF_WIDTH;
            float py = dx / length * LINK_HALF_WIDTH;
            FieldQuad.drawCorners(poseStack, collector, DuelTextures.WHITE, new FieldQuad.Corners(
                    x1 + px, y1 + py, x2 + px, y2 + py, x2 - px, y2 - py, x1 - px, y1 - py),
                0F, 0F, 1F, 1F, LINK_R, LINK_G, LINK_B, 0.85F);
        }

        // The reference's own mark, on the partner: vSymbol is a square the
        // width of a card quad, centred on the card.
        float half = (to.maxX() - to.minX()) / 2F;
        float cx = centreX(to);
        float cy = centreY(to);
        FieldQuad.drawCorners(poseStack, collector, DuelTextures.EQUIP, new FieldQuad.Corners(
                cx - half, cy - half, cx + half, cy - half, cx + half, cy + half, cx - half, cy + half),
            0F, 0F, 1F, 1F, 1F, 1F, 1F, 1F);
    }

    /**
     * Screen x of the table's centre line. The frustum is off-centre by
     * design, so this is well right of the screen's middle - header elements
     * centred on the window would look misaligned against the table.
     */
    public float tableCentreX()
    {
        return projection == null ? 0
            : projection.x((FieldLayout.FIELD_MIN_X + FieldLayout.FIELD_MAX_X) / 2F, 0F);
    }

    /** The projection last used, so overlays line up with the board. */
    public FieldLayout.Projection projection()
    {
        return projection;
    }

    /** Screen y of the table's far edge, for placing headers above it. */
    public float tableTopY()
    {
        return projection == null ? 0 : projection.y(FieldLayout.FIELD_MIN_Y);
    }

    public void setActionable(java.util.function.Predicate<Hit> actionable)
    {
        this.actionable = actionable;
    }

    /** Cards whose command bitmask includes COMMAND_ATTACK. */
    public void setCanAttack(java.util.function.Predicate<Hit> canAttack)
    {
        this.canAttack = canAttack;
    }

    /** Cards whose command bitmask includes COMMAND_ACTIVATE. */
    public void setCanActivate(java.util.function.Predicate<Hit> canActivate)
    {
        this.canActivate = canActivate;
    }

    /**
     * Where everything on the board is this frame: the projection, every zone,
     * pile and hand rectangle, and the hit list built from them. Nothing is
     * drawn here.
     * <p>
     * This is a separate pass because {@link #render} no longer runs when it is
     * called. The board goes down inside a {@link BoardPip} region and a pip's
     * painter is a callback the renderer invokes later in the frame, after every
     * screen's extract has returned — so a screen that read {@link #hits()}
     * during its own extract, as the click test, the hover preview and the
     * selection marks all must, was reading the rectangles of the frame before.
     * A click landing on the previous frame's slot is a wrong card played, so
     * the layout is settled up front and the painter only paints it.
     */
    public void layout(Font font, BoardSnapshot board, int left, int top, int width, int height,
        Set<Integer> highlights)
    {
        hits.clear();
        squarePlans.clear();
        fieldPlans.clear();
        handPlans.clear();
        this.font = font;
        this.currentBoard = board;
        zoneHighlights = highlights == null ? Set.of() : highlights;
        projection = FieldLayout.fit(left, top, width, height);

        // Each player's playmat, covering exactly their five columns by two
        // rows and nothing else.
        for(int controller = 0; controller <= 1; controller++)
        {
            matBands[controller] = FieldLayout.zoneBand(controller);
        }

        // The zones outside the mat block: the two extra monster zones and the
        // four piles. Same square, no theme.
        for(int controller = 0; controller <= 1; controller++)
        {
            layoutSlotSquare(controller, OcgConstants.LOCATION_MZONE, 5);
            layoutSlotSquare(controller, OcgConstants.LOCATION_MZONE, 6);
            layoutSlotSquare(controller, OcgConstants.LOCATION_SZONE, 5);
            for(int location : new int[] {OcgConstants.LOCATION_DECK, OcgConstants.LOCATION_EXTRA,
                OcgConstants.LOCATION_GRAVE, OcgConstants.LOCATION_REMOVED})
            {
                layoutSlotSquare(controller, location, 0);
            }
        }

        for(int controller = 0; controller <= 1; controller++)
        {
            BoardSnapshot.Side side = controller == 0 ? board.self() : board.opponent();

            for(int sequence = 0; sequence < 7; sequence++)
            {
                layoutZone(side.monsters(), controller,
                    OcgConstants.LOCATION_MZONE, sequence,
                    sequence < 5 ? "Monster Zone " + (sequence + 1) : "Extra Monster Zone");
            }
            for(int sequence = 0; sequence < 6; sequence++)
            {
                layoutZone(side.spells(), controller,
                    OcgConstants.LOCATION_SZONE, sequence,
                    sequence == 5 ? "Field Spell" : "Spell/Trap Zone " + (sequence + 1));
            }

            // Furthest pile first. A pile is a stack standing off the mat, so
            // a nearer one has to be painted over a further one -- and the
            // camera sits at +Y looking back toward the origin, which makes a
            // HIGHER y nearer. Drawn in a fixed order, the player's graveyard
            // (further) was painted over their deck (nearer); the opponent's
            // side, whose coordinates are mirrored through -y, happened to come
            // out right. Sorting by depth is correct for both without either
            // being a special case.
            for(int location : pilesFarToNear(controller))
            {
                switch(location)
                {
                    case OcgConstants.LOCATION_DECK -> layoutPile(controller,
                        location, "Deck", side.deckCount());
                    case OcgConstants.LOCATION_EXTRA -> layoutPile(controller,
                        location, "Extra Deck", side.extra().size());
                    case OcgConstants.LOCATION_GRAVE -> layoutPile(controller,
                        location, "Graveyard", side.grave().size());
                    default -> layoutPile(controller,
                        location, "Banished", side.banished().size());
                }
            }
        }

        layoutHand(board.opponent().hand(), 1, true);
        layoutHand(board.self().hand(), 0, false);
    }

    /**
     * Draws what {@link #layout} worked out, and works nothing out itself: no
     * rectangle is computed and {@link #hits} is not touched here, so what the
     * screen tested the mouse against is exactly what ends up on screen.
     * <p>
     * PORT-NOTE: draw order is no longer guaranteed by call order, and this
     * class depends on it. Forge drew immediately, so a later call painted over
     * an earlier one — that is the whole reason {@link #pilesFarToNear} exists,
     * why a pile's sides go down before its top card and its count, and why the
     * hands are drawn after the field. Submissions are collected into phases
     * instead ({@code SubmitNodeCollection.translucentCustomGeometry} and
     * {@code .texts} are separate ones) and each phase sorts its own submits;
     * {@code SimpleFeatureRenderPhase} even carries a {@code maybeShuffle} that
     * deliberately reorders them, so insertion order is explicitly not a
     * contract. Every draw below is kept in its original order, which is
     * correct if the phase turns out to preserve it. If it does not, the lever
     * is {@code SubmitNodeCollector.order(int)} — it returns a collector for an
     * explicit layer and the layers are drawn in key order — and the pile
     * sides/top/count and the hands would each need their own layer. Which of
     * the two it is can only be settled by looking at a running client.
     */
    public void render(PoseStack poseStack, SubmitNodeCollector collector)
    {
        if(projection == null)
        {
            return; // nothing laid out yet
        }


        // The opponent's mat is turned 180 degrees, so it reads upside down
        // from here the way a mat across a table does.
        //
        // The mat prints its own card squares. Drawing them as line geometry
        // never worked -- FieldQuad.fill has never rendered on this screen
        // despite correct colour, geometry and shader state -- so the squares
        // now travel the same textured path as the mat and the cards, which
        // demonstrably does.
        for(int controller = 0; controller <= 1; controller++)
        {
            // One neutral mat texture, multiplied by this side's chosen colour.
            int tint = matColour(controller);
            FieldQuad.drawProjected(poseStack, collector, mat(controller), projection,
                matBands[controller], 10, controller == 0 ? 0 : 2, 0F, 0F, 1F, 1F,
                (tint >> 16 & 0xFF) / 255F, (tint >> 8 & 0xFF) / 255F, (tint & 0xFF) / 255F, 1F);
        }

        for(SquarePlan square : squarePlans)
        {
            drawSlotSquare(poseStack, collector, square);
        }

        for(Plan plan : fieldPlans)
        {
            if(plan instanceof ZonePlan zone)
            {
                drawSlot(poseStack, collector, zone);
                drawScales(poseStack, collector, zone);
            }
            else if(plan instanceof PilePlan pile)
            {
                drawPile(poseStack, collector, pile);
            }
        }

        for(HandPlan card : handPlans)
        {
            drawHandSlot(poseStack, collector, card);
        }
    }

    private void layoutZone(List<BoardSnapshot.Slot> slots, int controller, int location,
        int sequence, String label)
    {
        FieldLayout.Rect rect = FieldLayout.zone(controller, location, sequence);
        if(rect == null)
        {
            return;
        }
        BoardSnapshot.Slot slot = sequence < slots.size() ? slots.get(sequence) : BoardSnapshot.Slot.EMPTY;
        boolean monsterZone = location == OcgConstants.LOCATION_MZONE;
        Hit hit = new Hit(projection.quad(rect), slot.code(), controller, location, sequence,
            EnginePrompt.zoneRef(controller == 1, monsterZone, sequence), label, 0, slot.art());
        hits.add(hit);

        FieldLayout.Rect cardRect = null;
        int cardTurns = 0;
        if(slot.present())
        {
            // Its own proportions, centred in the zone, rather than stretched
            // to fill it -- FieldLayout's rule, which the world board draws
            // from too.
            boolean lying = slot.defence();
            cardRect = FieldLayout.cardIn(rect, lying);
            cardTurns = turnsFor(controller, lying);
        }
        fieldPlans.add(new ZonePlan(rect, hit, slot, cardRect, cardTurns));
    }

    /** One card square, for a zone the playmat does not print. */
    private void layoutSlotSquare(int controller, int location, int sequence)
    {
        FieldLayout.Rect rect = FieldLayout.zone(controller, location, sequence);
        if(rect == null)
        {
            return;
        }
        FieldLayout.Rect rose = null;
        if(location == OcgConstants.LOCATION_SZONE && sequence == 5)
        {
            // The compass rose is square; the zone is not. Centre it at its own
            // aspect instead of stretching it to the rect.
            float side = Math.min(rect.w(), rect.h()) * 0.82F;
            rose = new FieldLayout.Rect(rect.x() + (rect.w() - side) / 2F,
                rect.y() + (rect.h() - side) / 2F, side, side);
        }
        squarePlans.add(new SquarePlan(controller, inset(rect), rose, turnsFor(controller, false)));
    }

    private void drawSlotSquare(PoseStack poseStack, SubmitNodeCollector collector, SquarePlan square)
    {
        // Every bare zone wears its owner's mat colour, so a duelist's whole
        // side of the table is themed rather than just the mat block.
        int accent = matColour(square.controller());
        float red = (accent >> 16 & 0xFF) / 255F;
        float green = (accent >> 8 & 0xFF) / 255F;
        float blue = (accent & 0xFF) / 255F;
        FieldQuad.drawProjected(poseStack, collector, DuelTextures.SLOT, projection, square.box(), 2,
            square.turns(), 0F, 0F, 1F, 1F, red, green, blue, 1F);

        if(square.rose() != null)
        {
            FieldQuad.drawProjected(poseStack, collector, DuelTextures.FIELD_SPELL, projection,
                square.rose(), 2, square.turns(), 0F, 0F, 1F, 1F, red, green, blue, 1F);
        }
    }

    /**
     * The vertical faces of a pile, between its base outline and the lifted
     * top face: the front (near) edge, and whichever side edge faces the
     * camera's x. Both sample the card-edge stripe with v running once per
     * card, so the side of a pile shows that many white and gray bands.
     */
    private void drawStackSides(PoseStack poseStack, SubmitNodeCollector collector,
        FieldLayout.Rect rect, FieldQuad.Corners base, FieldQuad.Corners top, int count)
    {
        float tiles = Math.min(count, LIFT_CAP);

        drawTiledFace(poseStack, collector,
            top.x3(), top.y3(), top.x2(), top.y2(),
            base.x3(), base.y3(), base.x2(), base.y2(), tiles, 0.92F);

        boolean leftFace = rect.x() + rect.w() / 2F > CAMERA_X;
        if(leftFace)
        {
            drawTiledFace(poseStack, collector, top.x0(), top.y0(), top.x3(), top.y3(),
                base.x0(), base.y0(), base.x3(), base.y3(), tiles, 0.7F);
        }
        else
        {
            drawTiledFace(poseStack, collector, top.x1(), top.y1(), top.x2(), top.y2(),
                base.x1(), base.y1(), base.x2(), base.y2(), tiles, 0.7F);
        }
    }

    /**
     * One vertical face of a pile, the stripe texture TILED once per card
     * between its top and bottom edges. Passing v past 1 and hoping the
     * texture repeats stretched instead -- the wrap mode is not repeat here --
     * so the tiling is done by hand: one slice per card, each sampling the
     * whole stripe.
     */
    private void drawTiledFace(PoseStack poseStack, SubmitNodeCollector collector,
        float topLx, float topLy, float topRx, float topRy,
        float baseLx, float baseLy, float baseRx, float baseRy, float tiles, float shade)
    {
        int slices = Math.max(1, (int)Math.ceil(tiles));
        for(int i = 0; i < slices; i++)
        {
            float f0 = i / tiles;
            float f1 = Math.min(1F, (i + 1) / tiles);
            FieldQuad.Corners slice = new FieldQuad.Corners(
                topLx + (baseLx - topLx) * f0, topLy + (baseLy - topLy) * f0,
                topRx + (baseRx - topRx) * f0, topRy + (baseRy - topRy) * f0,
                topRx + (baseRx - topRx) * f1, topRy + (baseRy - topRy) * f1,
                topLx + (baseLx - topLx) * f1, topLy + (baseLy - topLy) * f1);
            // The last slice may be partial; it samples that much of the stripe.
            float v1 = (f1 - f0) * tiles;
            FieldQuad.drawCorners(poseStack, collector, DuelTextures.STACK_SIDE, slice,
                0F, 0F, 1F, v1, shade, 1F);
        }
    }

    /** A card drawn at explicit screen corners, with its UV window and turn. */
    private void drawCardAtCorners(PoseStack poseStack, SubmitNodeCollector collector,
        ResourceLocation texture, FieldQuad.Corners corners, int turns)
    {
        boolean edoproArt = CardFaces.isCardShaped(texture);
        float u0 = edoproArt ? 0F : DuelTextures.CARD_U0;
        float v0 = edoproArt ? 0F : DuelTextures.CARD_V0;
        float u1 = edoproArt ? 1F : DuelTextures.CARD_U1;
        float v1 = edoproArt ? 1F : DuelTextures.CARD_V1;
        if(turns >= 2)
        {
            float swap = u0;
            u0 = u1;
            u1 = swap;
            swap = v0;
            v0 = v1;
            v1 = swap;
        }
        FieldQuad.drawCorners(poseStack, collector, texture, corners, u0, v0, u1, v1, 1F, 1F);
    }

    /** game.h: FIELD_X, the camera    /** game.h: FIELD_X, the camera's x. Which side face of a pile is seen. */
    private static final float CAMERA_X = 4.2F;

    /**
     * The pile count, laid on the top card of the stack.
     * <p>
     * {@code Game::DrawStackIndicator} puts it on the zone's bottom edge, which
     * works there because a pile is flat. Ours stands proud of the table, so
     * the same spot left the number stranded below the cards; it sits on the
     * top face instead.
     * <p>
     * The size comes from that face rather than being fixed, so a distant
     * pile's count shrinks with the pile -- the opponent's numbers were drawn
     * at the same size as yours despite their zones being half as tall.
     */
    private void drawStackIndicator(PoseStack poseStack, SubmitNodeCollector collector,
        FieldQuad.Corners face, int count)
    {
        String text = Integer.toString(count);
        float faceHeight = ((face.y2() + face.y3()) - (face.y0() + face.y1())) / 2F;
        float digitH = Math.max(4F, Math.abs(faceHeight) * STACK_DIGIT_SCALE);
        float digitW = digitH * STACK_DIGIT_ASPECT;

        float x = centreX(face) - text.length() * digitW / 2F;
        float y = centreY(face) - digitH / 2F;
        for(int i = 0; i < text.length(); i++)
        {
            int digit = text.charAt(i) - '0';
            FieldQuad.Corners cell = new FieldQuad.Corners(
                x + i * digitW, y, x + (i + 1) * digitW, y,
                x + (i + 1) * digitW, y + digitH, x + i * digitW, y + digitH);
            FieldQuad.drawCorners(poseStack, collector, DuelTextures.DIGITS, cell,
                digit / 10F, 0F, (digit + 1) / 10F, 1F, 1F, 1F);
        }
    }

    /** The count's height as a fraction of the card face it sits on. */
    private static final float STACK_DIGIT_SCALE = 0.46F;
    /** The atlas cell's width over its height, so digits keep their shape. */
    private static final float STACK_DIGIT_ASPECT = 48F / 80F;


    /** A zone shrunk to its grid box, so neighbouring slots stay separate. */
    private static FieldLayout.Rect inset(FieldLayout.Rect rect)
    {
        return new FieldLayout.Rect(rect.x() + GRID_INSET, rect.y() + GRID_INSET,
            rect.w() - GRID_INSET * 2F, rect.h() - GRID_INSET * 2F);
    }

    private static float centreX(FieldQuad.Corners corners)
    {
        return (corners.x0() + corners.x1() + corners.x2() + corners.x3()) / 4F;
    }

    private static float centreY(FieldQuad.Corners corners)
    {
        return (corners.y0() + corners.y1() + corners.y2() + corners.y3()) / 4F;
    }

    /**
     * ATK/DEF under a face-up monster, as drawing.cpp's {@code DrawStatus} does.
     * <p>
     * The reference compares each stat against the card's base value and
     * recolours it — {@code GetAtkColor} / {@code GetDefColor}. EDOPro's own
     * palette is yellow for higher and pink for lower
     * ({@code DUELFIELD_HIGHER_CARD_ATK} 0xffffff00,
     * {@code DUELFIELD_LOWER_CARD_ATK} 0xffff2090); these are blue and red
     * instead, which is what this project asked for. Unchanged stays white,
     * as it is there.
     */
    private void drawStats(PoseStack poseStack, SubmitNodeCollector collector,
        BoardSnapshot.Slot slot, Hit hit, boolean inHand)
    {
        // Stats are only drawn when we actually have them. The core reports -1
        // for a value the viewer is not entitled to; that used to be clamped to
        // 0 on the way out, so hidden cards were captioned a confident "0/0".
        if(font == null || inHand || hit.location() != OcgConstants.LOCATION_MZONE
            || !slot.present() || slot.faceDown() || slot.code() == 0
            || slot.attack() < 0 || slot.defense() < 0)
        {
            return;
        }
        String attack = Integer.toString(slot.attack());
        String defense = Integer.toString(slot.defense());
        int attackColour = slot.attackBoosted() ? COLOUR_STAT_HIGHER
            : slot.attackWeakened() ? COLOUR_STAT_LOWER : COLOUR_STAT_PLAIN;
        int defenseColour = slot.defenseBoosted() ? COLOUR_STAT_HIGHER
            : slot.defenseWeakened() ? COLOUR_STAT_LOWER : COLOUR_STAT_PLAIN;

        // Centred on the card's lower edge, small enough to sit on the art.
        FieldQuad.Corners corners = hit.corners();
        float centreX = (corners.x2() + corners.x3()) / 2F;
        float bottomY = (corners.y2() + corners.y3()) / 2F;

        poseStack.pushPose();
        poseStack.scale(STAT_SCALE, STAT_SCALE, 1F);
        int width = font.width(attack) + font.width("/") + font.width(defense);
        int x = Math.round(centreX / STAT_SCALE) - width / 2;
        int y = Math.round(bottomY / STAT_SCALE) - 9;

        // GuiComponent.fill is gone and the extractor that replaced it does not
        // exist inside this pass; the same rectangle goes down as a quad, on
        // the one path that is allowed in here.
        //
        // PORT-NOTE: the backdrop and the caption it sits behind are now in two
        // different phases -- a quad in translucentCustomGeometry, the digits in
        // texts -- so the pair is only composed correctly if texts are drawn
        // after custom geometry. Drawn the other way round the backdrop covers
        // its own caption and the stats vanish. See the note on render().
        FieldQuad.fill(poseStack, collector, new FieldQuad.Corners(
            x - 2, y - 1, x + width + 2, y - 1,
            x + width + 2, y + 8, x - 2, y + 8), 0xB0000000);
        submitText(poseStack, collector, x, y, attack, attackColour);
        x += font.width(attack);
        submitText(poseStack, collector, x, y, "/", COLOUR_STAT_SLASH);
        x += font.width("/");
        submitText(poseStack, collector, x, y, defense, defenseColour);
        poseStack.popPose();
    }

    /** The four piles of one side, furthest from the camera first. */
    private static int[] pilesFarToNear(int controller)
    {
        int[] locations = {OcgConstants.LOCATION_DECK, OcgConstants.LOCATION_EXTRA,
            OcgConstants.LOCATION_GRAVE, OcgConstants.LOCATION_REMOVED};
        Integer[] boxed = {locations[0], locations[1], locations[2], locations[3]};
        java.util.Arrays.sort(boxed, java.util.Comparator.comparingDouble(location ->
        {
            FieldLayout.Rect rect = FieldLayout.zone(controller, location, 0);
            // Read off the layout rather than restated here, so moving a pile
            // moves its place in the order with it.
            return rect == null ? 0F : rect.y();
        }));
        return new int[] {boxed[0], boxed[1], boxed[2], boxed[3]};
    }

    private void layoutPile(int controller, int location, String label, int count)
    {
        FieldLayout.Rect rect = FieldLayout.zone(controller, location, 0);
        if(rect == null)
        {
            return;
        }
        FieldQuad.Corners corners = projection.quad(rect);
        int previewCode = 0;
        if(currentBoard != null && (location == OcgConstants.LOCATION_GRAVE
            || location == OcgConstants.LOCATION_REMOVED))
        {
            BoardSnapshot.Side side = controller == 0
                ? currentBoard.self() : currentBoard.opponent();
            previewCode = newestVisibleCode(location == OcgConstants.LOCATION_GRAVE
                ? side.grave() : side.banished());
        }
        Hit hit = new Hit(corners, previewCode, controller, location, -1, -1,
            label + " (" + count + ")", count);

        FieldLayout.Rect pileCard = null;
        FieldQuad.Corners baseCorners = null;
        FieldQuad.Corners topCorners = null;
        if(count > 0)
        {
            // Depth. client_field.cpp raises each card in a pile by
            // t->Z += 0.01f * sequence, so a full deck stands visibly proud of
            // an empty zone. With no Z here, the same lift is drawn as a small
            // offset per card towards the viewer.
            pileCard = new FieldLayout.Rect(
                rect.x() + (rect.w() - CARD_W) / 2F, rect.y() + (rect.h() - CARD_H) / 2F, CARD_W, CARD_H);
            // The pile as a solid block. The lift is applied in SCREEN space:
            // every corner of the top face moves up by the same pixel count,
            // exactly like client_field.cpp's Z lift, which is a translation
            // along the table normal. Lifting in field-y instead moved the
            // near and far edges by different amounts (perspective), so the
            // whole stack leaned.
            float stackLift = Math.min(count, LIFT_CAP) * LIFT_PER_CARD;
            baseCorners = projection.quad(pileCard);
            float pxPerFieldY = ((baseCorners.y2() + baseCorners.y3()) / 2F
                - (baseCorners.y0() + baseCorners.y1()) / 2F) / pileCard.h();
            float liftPx = stackLift * pxPerFieldY;
            topCorners = new FieldQuad.Corners(
                baseCorners.x0(), baseCorners.y0() - liftPx,
                baseCorners.x1(), baseCorners.y1() - liftPx,
                baseCorners.x2(), baseCorners.y2() - liftPx,
                baseCorners.x3(), baseCorners.y3() - liftPx);
        }
        hits.add(hit);
        fieldPlans.add(new PilePlan(controller, location, rect, hit, count, pileCard,
            baseCorners, topCorners, turnsFor(controller, false)));
    }

    /**
     * The core orders a pile bottom-to-top, so its last visible identity is the
     * newest card. A hidden face-down banished card has already been reduced to
     * code zero by the server snapshot and therefore cannot leak through hover.
     */
    static int newestVisibleCode(List<BoardSnapshot.Slot> pile)
    {
        return pile.isEmpty() ? 0 : pile.get(pile.size() - 1).code();
    }

    private void drawPile(PoseStack poseStack, SubmitNodeCollector collector, PilePlan pile)
    {
        Hit hit = pile.hit();
        boolean canActivateFromHere = actionable.test(hit);
        if(canActivateFromHere)
        {
            FieldQuad.outline(poseStack, collector, hit.corners(), COLOUR_ACTIONABLE);
        }

        if(pile.count() > 0)
        {
            drawStackSides(poseStack, collector, pile.pileCard(), pile.baseCorners(),
                pile.topCorners(), pile.count());

            // The top card. A graveyard is always face up in the reference
            // (client_field.cpp excludes LOCATION_GRAVE from the face-down
            // rotation); banished follows suit unless the engine set it face
            // down, which is a real and distinct game state.
            ResourceLocation top = backFor(pile.controller());
            BoardSnapshot.Slot topCard = topOf(pile.location(), pile.controller());
            if(topCard != null && topCard.code() != 0 && !topCard.faceDown()
                && (pile.location() == OcgConstants.LOCATION_GRAVE
                    || pile.location() == OcgConstants.LOCATION_REMOVED))
            {
                top = textureFor(topCard, false, pile.controller());
            }
            drawCardAtCorners(poseStack, collector, top, pile.topCorners(), pile.turns());
            drawStackIndicator(poseStack, collector, pile.topCorners(), pile.count());
        }
        if(canActivateFromHere)
        {
            // EDOPro draws tAct over a pile whose contents can be activated.
            FieldQuad.drawProjected(poseStack, collector, DuelTextures.ACT, projection, pile.rect(), 2);
        }
    }

    /** Hands sit just beyond the near and far edges of the table. */
    private void layoutHand(List<BoardSnapshot.Slot> hand, int controller, boolean hide)
    {
        if(hand.isEmpty())
        {
            return;
        }
        float cardW = CARD_W;
        float cardH = CARD_H;
        float fieldY = controller == 0 ? FieldLayout.FIELD_MAX_Y + cardH * 0.5F
            : FieldLayout.FIELD_MIN_Y - cardH * 0.5F;

        float centreFieldX = (FieldLayout.FIELD_MIN_X + FieldLayout.FIELD_MAX_X) / 2F;
        float spanUnits = Math.min(FieldLayout.FIELD_MAX_X - FieldLayout.FIELD_MIN_X - 1F,
            hand.size() * (cardW + 0.1F));
        float step = hand.size() > 1 ? (spanUnits - cardW) / (hand.size() - 1) : 0;
        float x = centreFieldX - spanUnits / 2F + cardW / 2F;

        for(int i = 0; i < hand.size(); i++)
        {
            BoardSnapshot.Slot slot = hide
                ? new BoardSnapshot.Slot(true, 0, true, false, 0, 0, 0, 0, -1, -1, 0, null)
                : hand.get(i);
            FieldLayout.Rect rect = new FieldLayout.Rect(x - cardW / 2F, fieldY - cardH / 2F, cardW, cardH);

            // Hands are held toward their owner, not laid on the table:
            // EDOPro tilts them at the camera (handfaceup, -FIELD_ANGLE), so
            // they barely shear. Projecting them flat on the table sheared
            // them like floor tiles. Instead the projected quad is stood
            // upright: the near edge is kept and the far edge is placed
            // directly above it at the same width, which unshears your hand
            // and squares up the backs of the opponent's.
            FieldQuad.Corners sheared = projection.quad(rect);
            float bottomY = (sheared.y2() + sheared.y3()) / 2F;
            // Height comes from the width and the card's own aspect, never
            // from the projection: the opponent's hand row is deep in the
            // distance, where projected height collapses, which is what made
            // their cards come out short and wide.
            float height = (sheared.x2() - sheared.x3()) / DuelTextures.CARD_ASPECT;
            FieldQuad.Corners corners = new FieldQuad.Corners(
                sheared.x3(), bottomY - height, sheared.x2(), bottomY - height,
                sheared.x2(), bottomY, sheared.x3(), bottomY);

            Hit hit = new Hit(corners, slot.code(), controller,
                OcgConstants.LOCATION_HAND, i, -1, "Hand", 0, slot.art());
            hits.add(hit);
            handPlans.add(new HandPlan(slot, hit));
            x += step;
        }
    }

    private void drawHandSlot(PoseStack poseStack, SubmitNodeCollector collector, HandPlan card)
    {
        Hit hit = card.hit();
        if(actionable.test(hit))
        {
            // A soft breathing glow behind a hand card that can act,
            // coloured by what the card is. Only ever true for this
            // player's own prompt -- options exist only in the prompt the
            // server sent them -- so nothing is revealed about the
            // opponent's hand.
            float pulse = 0.5F + 0.3F * (float)Math.sin(System.currentTimeMillis() / 240D);
            drawFeatheredGlow(poseStack, collector, hit.corners(), glowTint(card.slot().code()), pulse);
        }
        drawHandCard(poseStack, collector, card.slot(), hit.corners(), hit.controller());
    }

    /** One upright hand card; the opponent's are shown to us upside down. */
    private void drawHandCard(PoseStack poseStack, SubmitNodeCollector collector,
        BoardSnapshot.Slot slot, FieldQuad.Corners corners, int controller)
    {
        ResourceLocation texture = textureFor(slot, true, controller);
        boolean edoproArt = CardFaces.isCardShaped(texture);
        float u0 = edoproArt ? 0F : DuelTextures.CARD_U0;
        float v0 = edoproArt ? 0F : DuelTextures.CARD_V0;
        float u1 = edoproArt ? 1F : DuelTextures.CARD_U1;
        float v1 = edoproArt ? 1F : DuelTextures.CARD_V1;
        if(controller == 1)
        {
            // The 180-degree turn of everything the opponent owns.
            float swap = u0;
            u0 = u1;
            u1 = swap;
            swap = v0;
            v0 = v1;
            v1 = swap;
        }
        FieldQuad.drawCorners(poseStack, collector, texture, corners, u0, v0, u1, v1, 1F, 1F);
    }

    private void drawSlot(PoseStack poseStack, SubmitNodeCollector collector, ZonePlan zone)
    {
        BoardSnapshot.Slot slot = zone.slot();
        Hit hit = zone.hit();
        FieldLayout.Rect rect = zone.rect();

        // The pendulum mark goes down FIRST, so a card set in the zone covers
        // it. It is a label for an empty zone, not decoration over a card.
        if(isPendulumZone(hit.location(), hit.sequence()))
        {
            // Sequence 0 is that player's LEFT zone and takes the blue gem;
            // sequence 4 is their right and takes the red. The opponent's half
            // is turned with the mat, so their left is the screen's right --
            // which is what a playmat across a table actually looks like.
            float side = Math.min(rect.w(), rect.h()) * 0.72F;
            FieldQuad.drawProjected(poseStack, collector,
                hit.sequence() == 0 ? DuelTextures.PENDULUM_ZONE_LEFT
                    : DuelTextures.PENDULUM_ZONE_RIGHT,
                projection,
                new FieldLayout.Rect(rect.x() + (rect.w() - side) / 2F,
                    rect.y() + (rect.h() - side) / 2F, side, side),
                2, turnsFor(hit.controller(), false), 0F, 0F, 1F, 1F);
        }
        boolean zoneLit = hit.zoneRef() >= 0 && zoneHighlights.contains(hit.zoneRef());
        boolean canAct = actionable.test(hit);

        // What a highlight is drawn AROUND: the card, when the zone holds one.
        //
        // A zone is 1.1 x 1.2 and a card is 0.7 x 1.0, so a highlight at the
        // zone stood a fifth of a unit clear of the card to left and right and
        // only a tenth of one above and below it -- a lopsided box half again
        // as wide as the thing it was pointing at, reading as a lit CELL rather
        // than a lit card. The world board has always drawn this at the card's
        // own placement, so the two presentations disagreed about what was
        // glowing while agreeing about everything else.
        //
        // zoneLit keeps the zone, and that is the distinction rather than an
        // exception: it means the core is offering the SPACE -- somewhere to
        // summon TO -- and the space is the whole cell. Everything below it is
        // the core offering a CARD.
        boolean occupied = zone.cardRect() != null;
        FieldLayout.Rect litRect = occupied ? spilled(zone.cardRect()) : rect;
        int litTurns = occupied ? zone.cardTurns() : turnsFor(hit.controller(), false);

        // Every zone keeps a box so the rows and columns read at a glance, and
        // gains a stronger one when the core is actually offering it.
        if(zoneLit)
        {
            FieldQuad.drawProjected(poseStack, collector, DuelTextures.SLOT_ACTIVE, projection, rect, 2,
                turnsFor(hit.controller(), false), 0F, 0F, 1F, 1F);
        }
        else if(canAct)
        {
            if(canActivate.test(hit))
            {
                // The breathing glow over anything you may activate -- during
                // a chain window that is the set of responses open to you --
                // coloured by the kind of card doing the offering.
                float pulse = 0.5F + 0.3F * (float)Math.sin(System.currentTimeMillis() / 190D);
                drawFeatheredGlow(poseStack, collector, projection.quad(litRect),
                    glowTint(slot.code()), pulse);
            }
            else
            {
                FieldQuad.drawProjected(poseStack, collector, DuelTextures.SLOT_ACTIVE, projection,
                    litRect, 2, litTurns, 0F, 0F, 1F, 1F);
            }
        }
        // The idle box is already down from the grid pass; only the states that
        // override it are drawn here.
        if(!slot.present())
        {
            return;
        }
        drawCardArt(poseStack, collector, textureFor(slot, false, hit.controller()), zone.cardRect(),
            zone.cardTurns());
        if(slot.negated())
        {
            // drawing.cpp composites tNegated over any face-up on-field card
            // whose status carries STATUS_DISABLED or STATUS_FORBIDDEN. The art
            // has shipped in this mod since the port and nothing drew it,
            // because QUERY_STATUS was never asked for.
            FieldQuad.drawProjected(poseStack, collector, DuelTextures.NEGATED, projection, rect, 2);
        }
        if(canAttack.test(hit))
        {
            // drawing.cpp bobs tAttack over any card that may attack.
            FieldQuad.drawProjected(poseStack, collector, DuelTextures.ATTACK, projection, rect, 2);
        }
        if(slot.overlays() > 0)
        {
            // Xyz materials, counted the same way a pile's depth is. The number
            // is the one thing a player needs off a stack they cannot fan out.
            // The zone's own projected quad, which is what the pile indicator
            // uses too -- a Rect is flat screen space and this number has to
            // sit on the tilted field with the card.
            drawStackIndicator(poseStack, collector, hit.corners(), slot.overlays());
        }
        drawStats(poseStack, collector, slot, hit, false);
    }

    /**
     * Cards in hand carry a face-down position in the engine, but you are
     * entitled to see your own hand, so face-down only means "show the back"
     * for cards set on the field. A code of 0 means the snapshot withheld the
     * identity, which is the real test for hiding.
     */
    /**
     * Draws one card. The mod's card images are letterboxed inside a square,
     * so they are sampled through their own window; EDOPro's textures are
     * already card-shaped and use the full range.
     */
    /** @param turns quarter turns of the card; see {@link #turnsFor}. */
    private void drawCardArt(PoseStack poseStack, SubmitNodeCollector collector,
        ResourceLocation texture, FieldLayout.Rect rect, int turns)
    {
        boolean edoproArt = CardFaces.isCardShaped(texture);
        if(edoproArt)
        {
            FieldQuad.drawProjected(poseStack, collector, texture, projection, rect, CARD_STEPS, turns,
                0F, 0F, 1F, 1F);
        }
        else
        {
            FieldQuad.drawProjected(poseStack, collector, texture, projection, rect, CARD_STEPS, turns,
                DuelTextures.CARD_U0, DuelTextures.CARD_V0, DuelTextures.CARD_U1, DuelTextures.CARD_V1);
        }
    }

    /** The card on top of a pile, or null when it is not one we track. */
    private BoardSnapshot.Slot topOf(int location, int controller)
    {
        if(currentBoard == null)
        {
            return null;
        }
        BoardSnapshot.Side side = controller == 0 ? currentBoard.self() : currentBoard.opponent();
        List<BoardSnapshot.Slot> pile = switch(location)
        {
            case OcgConstants.LOCATION_GRAVE -> side.grave();
            case OcgConstants.LOCATION_REMOVED -> side.banished();
            case OcgConstants.LOCATION_EXTRA -> side.extra();
            default -> List.of();
        };
        return pile.isEmpty() ? null : pile.get(pile.size() - 1);
    }

    /**
     * The size the field draws a sleeve from.
     * <p>
     * A card on the field is up to about 70 GUI units tall, which at the
     * client's guiScale of 3 is roughly 210 real pixels, and the card occupies
     * only 87.5% of a sleeve's square file. 512 therefore lands comfortably
     * above what is needed at every zoom rather than being resampled up.
     */
    private static final int SLEEVE_FIELD_SIZE = CardFaces.SLEEVE_FIELD_SIZE;

    /**
     * The back a side's face-down cards wear.
     * <p>
     * Seat 0 is this player, and wears the sleeve on the deck they are actually
     * duelling with — the server names it at the start of the duel rather than
     * the client reading its own active deck, because an illegal deck is
     * silently swapped for a starter and the back should follow the swap.
     * <p>
     * Seat 1 keeps the plain back on purpose. A sleeve is a thing you see from
     * your own seat; giving the opponent one too would just make the field
     * uniform again and lose the signal of which half is yours.
     * <p>
     * No UV special case is needed. The edoproArt tests further down ask whether
     * a texture IS COVER / COVER_OPPONENT / UNKNOWN, and a sleeve is none of
     * them, so it falls to the letterboxed branch — which is exactly right, as
     * sleeve art is a square canvas with the card inside CARD_U0..CARD_V1.
     */
    /**
     * Both of these moved to {@link CardFaces} when the world board needed the
     * same answers. They stay as delegates so this class's call sites read the
     * same as before -- what matters is that there is now ONE implementation of
     * "what does a face-down card look like", because the failure mode of two
     * that disagree is a set card showing its art.
     */
    private static ResourceLocation backFor(int controller)
    {
        return CardFaces.back(controller);
    }

    private ResourceLocation textureFor(BoardSnapshot.Slot slot, boolean inHand, int controller)
    {
        return CardFaces.face(slot, inHand, controller);
    }
}
