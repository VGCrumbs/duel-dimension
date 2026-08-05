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
 * Draws the duel field using {@link FieldLayout} — EDOPro's own zone table —
 * so the arrangement matches the reference client: five monster and five
 * spell/trap columns on a 1.1-unit pitch, field spell and graveyard/banished
 * in the side columns, deck and extra deck at the outer corners, the two extra
 * monster zones straddling the centre line, and the opponent's side as the
 * point reflection of yours.
 * <p>
 * Every drawn slot records a {@link Hit} carrying controller, location and
 * sequence, so the screen can ask what that exact card may do.
 */
public class BoardRenderer extends GuiComponent
{
    private static final int COLOUR_ZONE = 0x40FFFFFF;
    private static final int COLOUR_ZONE_FILL = 0x50000000;
    private static final int COLOUR_HIGHLIGHT = 0xA000FF66;
    private static final int COLOUR_ACTIONABLE = 0xC0FFD700;

    /** A drawn slot; piles use sequence -1. */
    public record Hit(int x, int y, int w, int h, int code, int controller, int location, int sequence,
        int zoneRef, String label, int count)
    {
        public boolean contains(double mouseX, double mouseY)
        {
            return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        }

        public boolean isPile()
        {
            return sequence < 0;
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

        // EDOPro's own mat, stretched across the play area the zone table
        // describes, so the printed zones sit under the drawn ones.
        int matLeft = projection.x(FieldLayout.FIELD_MIN_X);
        int matTop = projection.y(FieldLayout.FIELD_MIN_Y);
        int matRight = projection.x(FieldLayout.FIELD_MAX_X);
        int matBottom = projection.y(FieldLayout.FIELD_MAX_Y);
        ScreenUtil.white();
        CardRenderUtil.bindMainResourceLocation(DuelTextures.FIELD);
        DdBlitUtil.fullBlit(poseStack, matLeft, matTop, matRight - matLeft, matBottom - matTop);

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

        drawHand(poseStack, board.opponent().hand(), 1, left, top, width, true);
        drawHand(poseStack, board.self().hand(), 0, left, top, width, false);
    }

    private void drawZone(PoseStack poseStack, List<BoardSnapshot.Slot> slots, int controller, int location,
        int sequence, String label)
    {
        FieldLayout.Rect rect = FieldLayout.zone(controller, location, sequence);
        if(rect == null)
        {
            return;
        }
        int[] box = projection.rect(rect);
        BoardSnapshot.Slot slot = sequence < slots.size() ? slots.get(sequence) : BoardSnapshot.Slot.EMPTY;
        boolean monsterZone = location == OcgConstants.LOCATION_MZONE;
        int zoneRef = EnginePrompt.zoneRef(controller == 1, monsterZone, sequence);

        Hit hit = new Hit(box[0], box[1], box[2], box[3], slot.code(), controller, location, sequence,
            zoneRef, label, 0);
        drawSlot(poseStack, slot, hit);
    }

    private void drawPile(PoseStack poseStack, Font font, int controller, int location, String label, int count)
    {
        FieldLayout.Rect rect = FieldLayout.zone(controller, location, 0);
        if(rect == null)
        {
            return;
        }
        int[] box = projection.rect(rect);
        Hit hit = new Hit(box[0], box[1], box[2], box[3], 0, controller, location, -1, -1,
            label + " (" + count + ")", count);

        fill(poseStack, box[0] - 1, box[1] - 1, box[0] + box[2] + 1, box[1] + box[3] + 1,
            actionable.test(hit) ? COLOUR_ACTIONABLE : COLOUR_ZONE);
        fill(poseStack, box[0], box[1], box[0] + box[2], box[1] + box[3], COLOUR_ZONE_FILL);
        if(count > 0)
        {
            ScreenUtil.white();
            CardRenderUtil.bindMainResourceLocation(DuelTextures.COVER);
            DdBlitUtil.fullBlit(poseStack, box[0], box[1], box[2], box[3]);
            // Count badge sits inside the pile, not spilling onto neighbours.
            String text = Integer.toString(count);
            int badgeW = font.width(text) + 4;
            fill(poseStack, box[0] + box[2] - badgeW - 1, box[1] + box[3] - 10,
                box[0] + box[2] - 1, box[1] + box[3] - 1, 0xC0000000);
            font.draw(poseStack, text, box[0] + box[2] - badgeW + 1, box[1] + box[3] - 9, 0xFFFFFF);
        }
        hits.add(hit);
    }

    /** Hands run along the outer edges, fanned to fit the field's width. */
    private void drawHand(PoseStack poseStack, List<BoardSnapshot.Slot> hand, int controller,
        int left, int top, int width, boolean hide)
    {
        if(hand.isEmpty())
        {
            return;
        }
        int cardW = projection.size(1.1F);
        int cardH = projection.size(1.2F);
        // Your hand below the mat, the opponent's above it.
        int y = controller == 0 ? projection.y(FieldLayout.FIELD_MAX_Y) + 2
            : projection.y(FieldLayout.FIELD_MIN_Y) - cardH - 2;

        int span = Math.min(width - 20, hand.size() * (cardW + 2));
        int step = hand.size() > 1 ? (span - cardW) / (hand.size() - 1) : 0;
        int x = left + (width - span) / 2;

        for(int i = 0; i < hand.size(); i++)
        {
            BoardSnapshot.Slot slot = hide
                ? new BoardSnapshot.Slot(true, 0, true, false, 0, 0, 0)
                : hand.get(i);
            Hit hit = new Hit(x, y, cardW, cardH, slot.code(), controller,
                OcgConstants.LOCATION_HAND, i, -1, "Hand", 0);
            drawSlot(poseStack, slot, hit);
            x += step;
        }
    }

    private void drawSlot(PoseStack poseStack, BoardSnapshot.Slot slot, Hit hit)
    {
        boolean zoneLit = hit.zoneRef() >= 0 && zoneHighlights.contains(hit.zoneRef());
        boolean canAct = actionable.test(hit);
        int border = zoneLit ? COLOUR_HIGHLIGHT : canAct ? COLOUR_ACTIONABLE : COLOUR_ZONE;

        fill(poseStack, hit.x() - 1, hit.y() - 1, hit.x() + hit.w() + 1, hit.y() + hit.h() + 1, border);
        fill(poseStack, hit.x(), hit.y(), hit.x() + hit.w(), hit.y() + hit.h(), COLOUR_ZONE_FILL);

        if(slot.present())
        {
            ScreenUtil.white();
            CardRenderUtil.bindMainResourceLocation(textureFor(slot));
            if(slot.defence())
            {
                DdBlitUtil.fullBlit90Degree(poseStack, hit.x(), hit.y(), hit.w(), hit.h());
            }
            else
            {
                DdBlitUtil.fullBlit(poseStack, hit.x(), hit.y(), hit.w(), hit.h());
            }
        }
        hits.add(hit);
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
