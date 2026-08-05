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
    private FieldLayout.Projection projection;

    public List<Hit> hits()
    {
        return hits;
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

    public void render(PoseStack poseStack, Font font, BoardSnapshot board, int left, int top,
        int width, int height, Set<Integer> highlights)
    {
        hits.clear();
        zoneHighlights = highlights == null ? Set.of() : highlights;
        projection = FieldLayout.fit(left, top, width, height);

        // The mat spans the whole table, so it needs the most subdivision:
        // drawn as one quad its printed zones drift far from the drawn ones.
        FieldQuad.drawProjected(poseStack, DuelTextures.FIELD, projection, new FieldLayout.Rect(
            FieldLayout.FIELD_MIN_X, FieldLayout.FIELD_MIN_Y,
            FieldLayout.FIELD_MAX_X - FieldLayout.FIELD_MIN_X,
            FieldLayout.FIELD_MAX_Y - FieldLayout.FIELD_MIN_Y), 24);

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
            String text = Integer.toString(count);
            int textX = corners.minX() + (corners.maxX() - corners.minX() - font.width(text)) / 2;
            int textY = corners.maxY() - 10;
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
                ? new BoardSnapshot.Slot(true, 0, true, false, 0, 0, 0)
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

        // The mat already prints the grid; only mark a zone when it is
        // selectable or holds something the player can act on.
        if(zoneLit)
        {
            FieldQuad.fill(poseStack, hit.corners(), COLOUR_HIGHLIGHT_FILL);
            FieldQuad.outline(poseStack, hit.corners(), COLOUR_HIGHLIGHT);
        }
        else if(canAct)
        {
            FieldQuad.outline(poseStack, hit.corners(), COLOUR_ACTIONABLE);
        }
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
        drawCardArt(poseStack, textureFor(slot, inHand, hit.controller()), cardRect, lying);
        if(!inHand && canAttack.test(hit))
        {
            // drawing.cpp bobs tAttack over any card that may attack.
            FieldQuad.drawProjected(poseStack, DuelTextures.ATTACK, projection, rect, 2);
        }
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
