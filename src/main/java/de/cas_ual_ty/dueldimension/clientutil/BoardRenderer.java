package de.cas_ual_ty.dueldimension.clientutil;

import com.mojang.blaze3d.vertex.PoseStack;
import de.cas_ual_ty.dueldimension.DdDatabase;
import de.cas_ual_ty.dueldimension.card.properties.Properties;
import de.cas_ual_ty.dueldimension.ocg.OcgConstants;
import de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot;
import de.cas_ual_ty.dueldimension.ocg.prompt.EnginePrompt;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
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
 * Each drawn slot records a {@link Hit} carrying controller, location and
 * sequence, so the screen can ask what that exact card may do.
 */
public class BoardRenderer extends GuiComponent
{
    /** Cards are small, so a light subdivision removes the affine smear. */
    private static final int CARD_STEPS = 4;

    /**
     * A card's own size on the table, from materials.cpp:28
     * {@code SetS3DVertex(vCardFront, -0.35f, -0.5f, 0.35f, 0.5f, ...)} - so
     * 0.7 x 1.0 field units, drawn centred in its 1.1 x 1.2 zone. Filling the
     * whole zone instead is what made every card look stretched wide.
     */
    private static final float CARD_W = 0.7F;
    private static final float CARD_H = 1.0F;

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

    /** On-field ATK/DEF: raised reads blue, lowered red, printed white. */
    private static final int COLOUR_STAT_PLAIN = 0xFFFFFF;
    private static final int COLOUR_STAT_HIGHER = 0x66B2FF;
    private static final int COLOUR_STAT_LOWER = 0xFF4C4C;
    private static final int COLOUR_STAT_SLASH = 0x9A9A9A;
    /** Stats are drawn small so they sit on a card without covering the art. */
    private static final float STAT_SCALE = 0.5F;
    private static final int COLOUR_ACTIONABLE = 0xE0FFD700;
    /** custom_skin_enum.inl: DECLR(DUELFIELD_STACK, 0xffffff00). */
    private static final int COLOUR_STACK = 0xFFFFFF00;

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
     * A soft glow: three nested shells widening by a pixel and a half each,
     * fading as they go, so the edge feathers out instead of cutting off.
     */
    private static void drawFeatheredGlow(PoseStack poseStack, FieldQuad.Corners corners,
        float[] tint, float strength)
    {
        float[] spread = {1.5F, 3F, 4.5F};
        float[] fade = {0.45F, 0.25F, 0.12F};
        for(int shell = 0; shell < spread.length; shell++)
        {
            float e = spread[shell];
            FieldQuad.Corners ring = new FieldQuad.Corners(
                corners.x0() - e, corners.y0() - e, corners.x1() + e, corners.y1() - e,
                corners.x2() + e, corners.y2() + e, corners.x3() - e, corners.y3() + e);
            FieldQuad.drawCorners(poseStack, DuelTextures.WHITE, ring, 0F, 0F, 1F, 1F,
                tint[0], tint[1], tint[2], strength * fade[shell]);
        }
    }
    /** The reference's numFont is a display size; ours scales up to match. */
    private static final float STACK_NUM_SCALE = 1.4F;

    /** A drawn slot; piles use sequence -1. */
    public record Hit(FieldQuad.Corners corners, int code, int controller, int location, int sequence,
        int zoneRef, String label, int count)
    {
        public boolean contains(double mouseX, double mouseY)
        {
            return corners.contains(mouseX, mouseY);
        }

        public boolean isPile()
        {
            return sequence < 0;
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

    private final List<Hit> hits = new ArrayList<>();
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
    }

    private ResourceLocation mat(int controller)
    {
        return mats[controller].texture();
    }

    /**
     * How many quarter turns a card of this controller's is drawn at.
     * client_field.cpp: {@code selfATK {0,0,0}} against {@code oppoATK
     * {0,0,PI}} — the opponent's cards face the opponent. A defending monster
     * adds the quarter turn on top of that ({@code selfDEF -HALF_PI},
     * {@code oppoDEF +HALF_PI}).
     */
    private static int turnsFor(int controller, boolean lying)
    {
        return (controller == 1 ? 2 : 0) + (lying ? 1 : 0);
    }

    public List<Hit> hits()
    {
        return hits;
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

    public void render(PoseStack poseStack, Font font, BoardSnapshot board, int left, int top,
        int width, int height, Set<Integer> highlights)
    {
        hits.clear();
        this.font = font;
        this.currentBoard = board;
        zoneHighlights = highlights == null ? Set.of() : highlights;
        projection = FieldLayout.fit(left, top, width, height);

        // Each player's playmat, covering exactly their five columns by two
        // rows and nothing else. The opponent's is turned 180 degrees, so it
        // reads upside down from here the way a mat across a table does.
        //
        // The mat prints its own card squares. Drawing them as line geometry
        // never worked -- FieldQuad.fill has never rendered on this screen
        // despite correct colour, geometry and shader state -- so the squares
        // now travel the same textured path as the mat and the cards, which
        // demonstrably does.
        for(int controller = 0; controller <= 1; controller++)
        {
            FieldQuad.drawProjected(poseStack, mat(controller), projection,
                FieldLayout.zoneBand(controller), 10, controller == 0 ? 0 : 2, 0F, 0F, 1F, 1F);
        }

        // The zones outside the mat block: the two extra monster zones and the
        // four piles. Same square, no theme.
        for(int controller = 0; controller <= 1; controller++)
        {
            drawSlotSquare(poseStack, controller, OcgConstants.LOCATION_MZONE, 5);
            drawSlotSquare(poseStack, controller, OcgConstants.LOCATION_MZONE, 6);
            drawSlotSquare(poseStack, controller, OcgConstants.LOCATION_SZONE, 5);
            for(int location : new int[] {OcgConstants.LOCATION_DECK, OcgConstants.LOCATION_EXTRA,
                OcgConstants.LOCATION_GRAVE, OcgConstants.LOCATION_REMOVED})
            {
                drawSlotSquare(poseStack, controller, location, 0);
            }
        }

        for(int controller = 0; controller <= 1; controller++)
        {
            BoardSnapshot.Side side = controller == 0 ? board.self() : board.opponent();

            for(int sequence = 0; sequence < 7; sequence++)
            {
                drawZone(poseStack, side.monsters(), controller, OcgConstants.LOCATION_MZONE, sequence,
                    sequence < 5 ? "Monster Zone " + (sequence + 1) : "Extra Monster Zone");
            }
            for(int sequence = 0; sequence < 6; sequence++)
            {
                drawZone(poseStack, side.spells(), controller, OcgConstants.LOCATION_SZONE, sequence,
                    sequence == 5 ? "Field Spell" : "Spell/Trap Zone " + (sequence + 1));
            }

            drawPile(poseStack, font, controller, OcgConstants.LOCATION_DECK, "Deck", side.deckCount());
            drawPile(poseStack, font, controller, OcgConstants.LOCATION_EXTRA, "Extra Deck", side.extra().size());
            drawPile(poseStack, font, controller, OcgConstants.LOCATION_GRAVE, "Graveyard", side.grave().size());
            drawPile(poseStack, font, controller, OcgConstants.LOCATION_REMOVED, "Banished", side.banished().size());
        }

        drawHand(poseStack, board.opponent().hand(), 1, true);
        drawHand(poseStack, board.self().hand(), 0, false);
    }

    private void drawZone(PoseStack poseStack, List<BoardSnapshot.Slot> slots, int controller, int location,
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
            EnginePrompt.zoneRef(controller == 1, monsterZone, sequence), label, 0);
        drawSlot(poseStack, slot, hit, rect, false);
    }

    /** One card square, for a zone the playmat does not print. */
    private void drawSlotSquare(PoseStack poseStack, int controller, int location, int sequence)
    {
        FieldLayout.Rect rect = FieldLayout.zone(controller, location, sequence);
        if(rect != null)
        {
            // The field spell zone carries its own emblem; every other bare
            // zone takes the plain square.
            ResourceLocation square = location == OcgConstants.LOCATION_SZONE && sequence == 5
                ? DuelTextures.FIELD_SPELL : DuelTextures.SLOT;
            FieldQuad.drawProjected(poseStack, square, projection, inset(rect), 2,
                turnsFor(controller, false), 0F, 0F, 1F, 1F);
        }
    }

    /**
     * The vertical faces of a pile, between its base outline and the lifted
     * top face: the front (near) edge, and whichever side edge faces the
     * camera's x. Both sample the card-edge stripe with v running once per
     * card, so the side of a pile shows that many white and gray bands.
     */
    private void drawStackSides(PoseStack poseStack, FieldLayout.Rect rect,
        FieldQuad.Corners base, FieldQuad.Corners top, int count)
    {
        float tiles = Math.min(count, LIFT_CAP);

        drawTiledFace(poseStack,
            top.x3(), top.y3(), top.x2(), top.y2(),
            base.x3(), base.y3(), base.x2(), base.y2(), tiles, 0.92F);

        boolean leftFace = rect.x() + rect.w() / 2F > CAMERA_X;
        if(leftFace)
        {
            drawTiledFace(poseStack, top.x0(), top.y0(), top.x3(), top.y3(),
                base.x0(), base.y0(), base.x3(), base.y3(), tiles, 0.7F);
        }
        else
        {
            drawTiledFace(poseStack, top.x1(), top.y1(), top.x2(), top.y2(),
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
    private void drawTiledFace(PoseStack poseStack,
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
            FieldQuad.drawCorners(poseStack, DuelTextures.STACK_SIDE, slice,
                0F, 0F, 1F, v1, shade, 1F);
        }
    }

    /** A card drawn at explicit screen corners, with its UV window and turn. */
    private void drawCardAtCorners(PoseStack poseStack, ResourceLocation texture,
        FieldQuad.Corners corners, int turns)
    {
        boolean edoproArt = texture.equals(DuelTextures.COVER)
            || texture.equals(DuelTextures.COVER_OPPONENT) || texture.equals(DuelTextures.UNKNOWN);
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
        FieldQuad.drawCorners(poseStack, texture, corners, u0, v0, u1, v1, 1F, 1F);
    }

    /** game.h: FIELD_X, the camera    /** game.h: FIELD_X, the camera's x. Which side face of a pile is seen. */
    private static final float CAMERA_X = 4.2F;

    /**
     * The pile count, ported from {@code Game::DrawStackIndicator}
     * (drawing.cpp:753), whose own comment reads "Draws the text in the middle
     * of the bottom side of the zone".
     * <pre>
     * x0 = (v[0].Pos.X + v[1].Pos.X) / 2      // the zone's middle
     * y0 = opponent ? v[0].Pos.Y : v[2].Pos.Y // the zone's bottom edge
     * DrawShadowText(numFont, text, rect centred on that point, ...,
     *                skin::DUELFIELD_STACK_VAL /* 0xffffff00 *&#47;, 0xff000000)
     * </pre>
     * So it sits centred on the edge of the zone nearest the viewer, in yellow
     * with a black shadow -- not boxed in the middle of the zone, which is
     * where ours was.
     */
    private void drawStackIndicator(PoseStack poseStack, Font font, FieldQuad.Corners corners,
        int controller, int count)
    {
        String text = Integer.toString(count);
        // v[0]/v[1] are the far corners and v[2]/v[3] the near ones, so the
        // "bottom" edge is the far one for the opponent and the near one for us.
        float edgeY = controller == 1 ? (corners.y0() + corners.y1()) / 2F
            : (corners.y2() + corners.y3()) / 2F;
        // DrawStackIndicator centres numFont's dimension on that edge point
        // (rect from coords - dim to coords + dim); numFont is a display
        // face, noticeably larger than the body text, hence the scale.
        poseStack.pushPose();
        poseStack.scale(STACK_NUM_SCALE, STACK_NUM_SCALE, 1F);
        int x = Math.round(centreX(corners) / STACK_NUM_SCALE) - font.width(text) / 2;
        int y = Math.round(edgeY / STACK_NUM_SCALE) - 4;
        // DrawShadowText's offset is Resize(0, 1, 0, 1): one pixel down-right.
        font.draw(poseStack, text, x + 1, y + 1, 0xFF000000);
        font.draw(poseStack, text, x, y, COLOUR_STACK);
        poseStack.popPose();
    }

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
    private void drawStats(PoseStack poseStack, BoardSnapshot.Slot slot, Hit hit, boolean inHand)
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

        fill(poseStack, x - 2, y - 1, x + width + 2, y + 8, 0xB0000000);
        font.draw(poseStack, attack, x, y, attackColour);
        x += font.width(attack);
        font.draw(poseStack, "/", x, y, COLOUR_STAT_SLASH);
        x += font.width("/");
        font.draw(poseStack, defense, x, y, defenseColour);
        poseStack.popPose();
    }

    private void drawPile(PoseStack poseStack, Font font, int controller, int location, String label, int count)
    {
        FieldLayout.Rect rect = FieldLayout.zone(controller, location, 0);
        if(rect == null)
        {
            return;
        }
        FieldQuad.Corners corners = projection.quad(rect);
        Hit hit = new Hit(corners, 0, controller, location, -1, -1, label + " (" + count + ")", count);

        boolean canActivateFromHere = actionable.test(hit);
        if(canActivateFromHere)
        {
            FieldQuad.outline(poseStack, corners, COLOUR_ACTIONABLE);
        }

        if(count > 0)
        {
            // Depth. client_field.cpp raises each card in a pile by
            // t->Z += 0.01f * sequence, so a full deck stands visibly proud of
            // an empty zone. With no Z here, the same lift is drawn as a small
            // offset per card towards the viewer.
            FieldLayout.Rect pileCard = new FieldLayout.Rect(
                rect.x() + (rect.w() - CARD_W) / 2F, rect.y() + (rect.h() - CARD_H) / 2F, CARD_W, CARD_H);
            int turns = turnsFor(controller, false);
            // The pile as a solid block. The lift is applied in SCREEN space:
            // every corner of the top face moves up by the same pixel count,
            // exactly like client_field.cpp's Z lift, which is a translation
            // along the table normal. Lifting in field-y instead moved the
            // near and far edges by different amounts (perspective), so the
            // whole stack leaned.
            float stackLift = Math.min(count, LIFT_CAP) * LIFT_PER_CARD;
            FieldQuad.Corners baseCorners = projection.quad(pileCard);
            float pxPerFieldY = ((baseCorners.y2() + baseCorners.y3()) / 2F
                - (baseCorners.y0() + baseCorners.y1()) / 2F) / pileCard.h();
            float liftPx = stackLift * pxPerFieldY;
            FieldQuad.Corners topCorners = new FieldQuad.Corners(
                baseCorners.x0(), baseCorners.y0() - liftPx,
                baseCorners.x1(), baseCorners.y1() - liftPx,
                baseCorners.x2(), baseCorners.y2() - liftPx,
                baseCorners.x3(), baseCorners.y3() - liftPx);
            drawStackSides(poseStack, pileCard, baseCorners, topCorners, count);

            // The top card. A graveyard is always face up in the reference
            // (client_field.cpp excludes LOCATION_GRAVE from the face-down
            // rotation); banished follows suit unless the engine set it face
            // down, which is a real and distinct game state.
            ResourceLocation top = controller == 0 ? DuelTextures.COVER : DuelTextures.COVER_OPPONENT;
            BoardSnapshot.Slot topCard = topOf(location, controller);
            if(topCard != null && topCard.code() != 0 && !topCard.faceDown()
                && (location == OcgConstants.LOCATION_GRAVE
                    || location == OcgConstants.LOCATION_REMOVED))
            {
                top = textureFor(topCard, false, controller);
            }
            drawCardAtCorners(poseStack, top, topCorners, turns);
            drawStackIndicator(poseStack, font, corners, controller, count);
        }
        if(canActivateFromHere)
        {
            // EDOPro draws tAct over a pile whose contents can be activated.
            FieldQuad.drawProjected(poseStack, DuelTextures.ACT, projection, rect, 2);
        }
        hits.add(hit);
    }

    /** Hands sit just beyond the near and far edges of the table. */
    private void drawHand(PoseStack poseStack, List<BoardSnapshot.Slot> hand, int controller, boolean hide)
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
                ? new BoardSnapshot.Slot(true, 0, true, false, 0, 0, 0, 0, 0)
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
                OcgConstants.LOCATION_HAND, i, -1, "Hand", 0);
            if(actionable.test(hit))
            {
                // A soft breathing glow behind a hand card that can act,
                // coloured by what the card is. Only ever true for this
                // player's own prompt -- options exist only in the prompt the
                // server sent them -- so nothing is revealed about the
                // opponent's hand.
                float pulse = 0.5F + 0.3F * (float)Math.sin(System.currentTimeMillis() / 240D);
                drawFeatheredGlow(poseStack, corners, glowTint(slot.code()), pulse);
            }
            drawHandCard(poseStack, slot, corners, controller);
            hits.add(hit);
            x += step;
        }
    }

    /** One upright hand card; the opponent's are shown to us upside down. */
    private void drawHandCard(PoseStack poseStack, BoardSnapshot.Slot slot,
        FieldQuad.Corners corners, int controller)
    {
        ResourceLocation texture = textureFor(slot, true, controller);
        boolean edoproArt = texture.equals(DuelTextures.COVER)
            || texture.equals(DuelTextures.COVER_OPPONENT) || texture.equals(DuelTextures.UNKNOWN);
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
        FieldQuad.drawCorners(poseStack, texture, corners, u0, v0, u1, v1, 1F, 1F);
    }

    private void drawSlot(PoseStack poseStack, BoardSnapshot.Slot slot, Hit hit, FieldLayout.Rect rect,
        boolean inHand)
    {
        boolean zoneLit = hit.zoneRef() >= 0 && zoneHighlights.contains(hit.zoneRef());
        boolean canAct = actionable.test(hit);

        // Every zone keeps a box so the rows and columns read at a glance, and
        // gains a stronger one when the core is actually offering it.
        if(zoneLit)
        {
            FieldQuad.drawProjected(poseStack, DuelTextures.SLOT_ACTIVE, projection, rect, 2,
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
                drawFeatheredGlow(poseStack, hit.corners(), glowTint(slot.code()), pulse);
            }
            else
            {
                FieldQuad.drawProjected(poseStack, DuelTextures.SLOT_ACTIVE, projection, rect, 2,
                    turnsFor(hit.controller(), false), 0F, 0F, 1F, 1F);
            }
        }
        // The idle box is already down from the grid pass; only the states that
        // override it are drawn here.
        hits.add(hit);

        if(!slot.present())
        {
            return;
        }
        // Draw the card at its own proportions, centred in the zone, rather
        // than stretched to fill it.
        boolean lying = slot.defence() && !inHand;
        float drawW = lying ? CARD_H : CARD_W;
        float drawH = lying ? CARD_W : CARD_H;
        FieldLayout.Rect cardRect = new FieldLayout.Rect(
            rect.x() + (rect.w() - drawW) / 2F, rect.y() + (rect.h() - drawH) / 2F, drawW, drawH);
        drawCardArt(poseStack, textureFor(slot, inHand, hit.controller()), cardRect,
            turnsFor(hit.controller(), lying));
        if(!inHand && canAttack.test(hit))
        {
            // drawing.cpp bobs tAttack over any card that may attack.
            FieldQuad.drawProjected(poseStack, DuelTextures.ATTACK, projection, rect, 2);
        }
        drawStats(poseStack, slot, hit, inHand);
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
    private void drawCardArt(PoseStack poseStack, ResourceLocation texture, FieldLayout.Rect rect,
        int turns)
    {
        boolean edoproArt = texture.equals(DuelTextures.COVER) || texture.equals(DuelTextures.COVER_OPPONENT)
            || texture.equals(DuelTextures.UNKNOWN);
        if(edoproArt)
        {
            FieldQuad.drawProjected(poseStack, texture, projection, rect, CARD_STEPS, turns,
                0F, 0F, 1F, 1F);
        }
        else
        {
            FieldQuad.drawProjected(poseStack, texture, projection, rect, CARD_STEPS, turns,
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

    private ResourceLocation textureFor(BoardSnapshot.Slot slot, boolean inHand, int controller)
    {
        if(slot.code() == 0 || (slot.faceDown() && !inHand))
        {
            // EDOPro gives each side its own card back (tCover[controler]).
            return controller == 0 ? DuelTextures.COVER : DuelTextures.COVER_OPPONENT;
        }
        Properties properties = DdDatabase.PROPERTIES_LIST.get((long)slot.code());
        // A face-up card we have no art for is NOT a face-down card. Falling
        // back to the card back made every engine card missing from the mod's
        // own database look like a set monster -- and the engine plays from
        // EDOPro's cards.cdb, which is far larger than that database, so this
        // hit a good share of the opponent's field. The reference's "unknown
        // card" art says "no picture" without lying about the game state.
        return properties == null ? DuelTextures.UNKNOWN
            : DuelTextures.card(properties, (byte)0, DuelTextures.FIELD_CARD_SIZE);
    }
}
