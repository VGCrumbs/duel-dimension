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
    private static final int COLOUR_ZONE = 0x50FFFFFF;
    private static final int COLOUR_ZONE_FILL = 0x40000000;
    private static final int COLOUR_HIGHLIGHT = 0xC000FF66;
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
    private FieldLayout.Projection projection;

    public List<Hit> hits()
    {
        return hits;
    }

    public void setActionable(java.util.function.Predicate<Hit> actionable)
    {
        this.actionable = actionable;
    }

    public void render(PoseStack poseStack, Font font, BoardSnapshot board, int left, int top,
        int width, int height, Set<Integer> highlights)
    {
        hits.clear();
        zoneHighlights = highlights == null ? Set.of() : highlights;
        projection = FieldLayout.fit(left, top, width, height);

        // EDOPro's mat, projected onto the same trapezoid as the zones.
        FieldQuad.draw(poseStack, DuelTextures.FIELD, projection.quad(new FieldLayout.Rect(
            FieldLayout.FIELD_MIN_X, FieldLayout.FIELD_MIN_Y,
            FieldLayout.FIELD_MAX_X - FieldLayout.FIELD_MIN_X,
            FieldLayout.FIELD_MAX_Y - FieldLayout.FIELD_MIN_Y)));

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
        drawSlot(poseStack, slot, hit, rect);
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
        FieldQuad.fill(poseStack, corners, COLOUR_ZONE_FILL);
        FieldQuad.outline(poseStack, corners, canActivateFromHere ? COLOUR_ACTIONABLE : COLOUR_ZONE);

        if(count > 0)
        {
            FieldQuad.draw(poseStack, DuelTextures.COVER, corners);
            String text = Integer.toString(count);
            int textX = corners.minX() + (corners.maxX() - corners.minX() - font.width(text)) / 2;
            int textY = corners.maxY() - 10;
            fill(poseStack, textX - 2, textY - 1, textX + font.width(text) + 2, textY + 9, 0xC0000000);
            font.draw(poseStack, text, textX, textY, 0xFFFFFF);
        }
        if(canActivateFromHere)
        {
            // EDOPro draws tAct over a pile whose contents can be activated.
            FieldQuad.draw(poseStack, DuelTextures.ACT, corners);
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
        float cardW = 1.1F;
        float cardH = 1.2F;
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
            FieldQuad.Corners corners = projection.cardQuad(x, fieldY, cardW, cardH);
            Hit hit = new Hit(corners, slot.code(), controller, OcgConstants.LOCATION_HAND, i, -1, "Hand", 0);
            drawSlot(poseStack, slot, hit, null);
            x += step;
        }
    }

    private void drawSlot(PoseStack poseStack, BoardSnapshot.Slot slot, Hit hit, FieldLayout.Rect rect)
    {
        boolean zoneLit = hit.zoneRef() >= 0 && zoneHighlights.contains(hit.zoneRef());
        boolean canAct = actionable.test(hit);

        FieldQuad.fill(poseStack, hit.corners(), COLOUR_ZONE_FILL);
        FieldQuad.outline(poseStack, hit.corners(),
            zoneLit ? COLOUR_HIGHLIGHT : canAct ? COLOUR_ACTIONABLE : COLOUR_ZONE);
        hits.add(hit);

        if(!slot.present())
        {
            return;
        }
        if(slot.defence() && rect != null)
        {
            // A defence-position monster lies on its side. Rotating the corner
            // order keeps the card flat on the projected table.
            FieldLayout.Rect turned = new FieldLayout.Rect(
                rect.x() + (rect.w() - rect.h()) / 2F, rect.y() + (rect.h() - rect.w()) / 2F,
                rect.h(), rect.w());
            FieldQuad.Corners t = projection.quad(turned);
            FieldQuad.draw(poseStack, textureFor(slot),
                new FieldQuad.Corners(t.x3(), t.y3(), t.x0(), t.y0(), t.x1(), t.y1(), t.x2(), t.y2()));
        }
        else
        {
            FieldQuad.draw(poseStack, textureFor(slot), hit.corners());
        }
    }

    private ResourceLocation textureFor(BoardSnapshot.Slot slot)
    {
        if(slot.faceDown() || slot.code() == 0)
        {
            return DuelTextures.COVER;
        }
        Properties properties = DdDatabase.PROPERTIES_LIST.get((long)slot.code());
        return properties == null ? DuelTextures.COVER
            : DuelTextures.card(properties, (byte)0, DuelTextures.FIELD_CARD_SIZE);
    }
}
