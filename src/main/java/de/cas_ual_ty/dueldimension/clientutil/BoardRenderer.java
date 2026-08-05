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
    /** Zones whose card is mid-flight, so the static copy is held back. */
    private java.util.function.IntPredicate arriving = zone -> false;
    private FieldLayout.Projection projection;
    /** Set for the duration of a render, so zone drawing can label stats. */
    private Font font;

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

    /**
     * Zones with a card still flying into them. The board snapshot is applied
     * the moment it arrives, so without this the card was already sitting in
     * its zone while its own set/summon animation was still travelling -- two
     * copies of the same card, the destination one appearing first.
     */
    public void setArriving(java.util.function.IntPredicate arriving)
    {
        this.arriving = arriving;
    }

    public void render(PoseStack poseStack, Font font, BoardSnapshot board, int left, int top,
        int width, int height, Set<Integer> highlights)
    {
        hits.clear();
        this.font = font;
        zoneHighlights = highlights == null ? Set.of() : highlights;
        projection = FieldLayout.fit(left, top, width, height);

        // The mat spans the whole table, so it needs the most subdivision:
        // drawn as one quad its printed zones drift far from the drawn ones.
        FieldQuad.drawProjected(poseStack, DuelTextures.FIELD, projection, new FieldLayout.Rect(
            FieldLayout.FIELD_MIN_X, FieldLayout.FIELD_MIN_Y,
            FieldLayout.FIELD_MAX_X - FieldLayout.FIELD_MIN_X,
            FieldLayout.FIELD_MAX_Y - FieldLayout.FIELD_MIN_Y), 24);

        // The slot grid, drawn as its own pass over the bare mat and under
        // everything else, so no card, pile or overlay can paint across it.
        for(int controller = 0; controller <= 1; controller++)
        {
            for(int sequence = 0; sequence < 7; sequence++)
            {
                drawGridBox(poseStack, controller, OcgConstants.LOCATION_MZONE, sequence);
            }
            for(int sequence = 0; sequence < 6; sequence++)
            {
                drawGridBox(poseStack, controller, OcgConstants.LOCATION_SZONE, sequence);
            }
            for(int location : new int[] {OcgConstants.LOCATION_DECK, OcgConstants.LOCATION_EXTRA,
                OcgConstants.LOCATION_GRAVE, OcgConstants.LOCATION_REMOVED})
            {
                drawGridBox(poseStack, controller, location, 0);
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

    /** One slot's box on the mat. */
    private void drawGridBox(PoseStack poseStack, int controller, int location, int sequence)
    {
        FieldLayout.Rect rect = FieldLayout.zone(controller, location, sequence);
        if(rect != null)
        {
            FieldQuad.outline(poseStack, projection.quad(inset(rect)), COLOUR_GRID);
        }
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
        // A concealed card (code 0) has no stats we are allowed to know: the
        // board state sends -1, which clamps to 0, so drawing them anyway
        // printed a misleading "0/0" under every card whose identity is hidden.
        if(font == null || inHand || hit.location() != OcgConstants.LOCATION_MZONE
            || !slot.present() || slot.faceDown() || slot.code() == 0)
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
            FieldLayout.Rect pileCard = new FieldLayout.Rect(
                rect.x() + (rect.w() - CARD_W) / 2F, rect.y() + (rect.h() - CARD_H) / 2F, CARD_W, CARD_H);
            drawCardArt(poseStack, controller == 0 ? DuelTextures.COVER : DuelTextures.COVER_OPPONENT,
                pileCard, false);
            // Centred on the slot itself. The quad is a trapezoid, so its
            // bounding box is not its middle: average the corners instead.
            String text = Integer.toString(count);
            int textX = Math.round(centreX(corners)) - font.width(text) / 2;
            int textY = Math.round(centreY(corners)) - 4;
            fill(poseStack, textX - 2, textY - 1, textX + font.width(text) + 2, textY + 9, 0xC0000000);
            font.draw(poseStack, text, textX, textY, 0xFFFFFF);
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
            Hit hit = new Hit(projection.quad(rect), slot.code(), controller,
                OcgConstants.LOCATION_HAND, i, -1, "Hand", 0);
            drawSlot(poseStack, slot, hit, rect, true);
            x += step;
        }
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
            FieldQuad.fill(poseStack, hit.corners(), COLOUR_HIGHLIGHT_FILL);
            FieldQuad.outline(poseStack, hit.corners(), COLOUR_HIGHLIGHT);
        }
        else if(canAct)
        {
            if(canActivate.test(hit))
            {
                // A white breath over anything you may activate -- during a
                // chain window that is the set of responses open to you.
                float pulse = 0.45F + 0.4F * (float)Math.sin(System.currentTimeMillis() / 190D);
                int glow = (Math.round(pulse * 255) << 24) | 0xFFFFFF;
                FieldQuad.fill(poseStack, hit.corners(), (Math.round(pulse * 70) << 24) | 0xFFFFFF);
                FieldQuad.outline(poseStack, hit.corners(), glow);
            }
            else
            {
                FieldQuad.outline(poseStack, hit.corners(), COLOUR_ACTIONABLE);
            }
        }
        // The idle box is already down from the grid pass; only the states that
        // override it are drawn here.
        hits.add(hit);

        if(!slot.present() || (hit.zoneRef() >= 0 && arriving.test(hit.zoneRef())))
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
        drawCardArt(poseStack, textureFor(slot, inHand, hit.controller()), cardRect, lying);
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
    private void drawCardArt(PoseStack poseStack, ResourceLocation texture, FieldLayout.Rect rect,
        boolean lying)
    {
        boolean edoproArt = texture.equals(DuelTextures.COVER) || texture.equals(DuelTextures.COVER_OPPONENT)
            || texture.equals(DuelTextures.UNKNOWN);
        if(edoproArt)
        {
            FieldQuad.drawProjected(poseStack, texture, projection, rect, CARD_STEPS, lying);
        }
        else
        {
            FieldQuad.drawProjected(poseStack, texture, projection, rect, CARD_STEPS, lying,
                DuelTextures.CARD_U0, DuelTextures.CARD_V0, DuelTextures.CARD_U1, DuelTextures.CARD_V1);
        }
    }

    private ResourceLocation textureFor(BoardSnapshot.Slot slot, boolean inHand, int controller)
    {
        if(slot.code() == 0 || (slot.faceDown() && !inHand))
        {
            // EDOPro gives each side its own card back (tCover[controler]).
            return controller == 0 ? DuelTextures.COVER : DuelTextures.COVER_OPPONENT;
        }
        Properties properties = DdDatabase.PROPERTIES_LIST.get((long)slot.code());
        return properties == null ? DuelTextures.COVER
            : DuelTextures.card(properties, (byte)0, DuelTextures.FIELD_CARD_SIZE);
    }
}
