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
 * Draws the duel field the way a duel sim does: opponent mirrored on top, your
 * side below, each side laid out as
 * {@code [extra] [5 monster zones] [grave]} and
 * {@code [field] [5 spell/trap] [deck]}, the two shared extra monster zones
 * between the rows, and hands along the outer edges.
 * <p>
 * Every drawn slot records a {@link Hit} carrying its controller, location and
 * sequence, so the screen can ask "what can this exact card do?" rather than
 * matching on passcode (which breaks with three copies of a card).
 */
public class BoardRenderer extends GuiComponent
{
    public static final int CARD_W = 26;
    public static final int CARD_H = 38;
    private static final int GAP = 3;
    private static final int PILE_GAP = 10;

    private static final int COLOUR_ZONE = 0x40FFFFFF;
    private static final int COLOUR_ZONE_FILL = 0x60000000;
    private static final int COLOUR_HIGHLIGHT = 0xA000FF66;
    private static final int COLOUR_ACTIONABLE = 0xA0FFD700;

    /**
     * A drawn slot. {@code location} is a LOCATION_* value; piles use their own
     * location constant with sequence -1.
     */
    public record Hit(int x, int y, int code, int controller, int location, int sequence, int zoneRef,
        String label, int count)
    {
        public boolean contains(double mouseX, double mouseY)
        {
            return mouseX >= x && mouseX < x + CARD_W && mouseY >= y && mouseY < y + CARD_H;
        }

        public boolean isPile()
        {
            return sequence < 0;
        }
    }

    private final List<Hit> hits = new ArrayList<>();
    private Set<Integer> zoneHighlights = Set.of();
    private java.util.function.BiPredicate<Hit, Void> actionable;

    public List<Hit> hits()
    {
        return hits;
    }

    /** Slots the current prompt can act on, drawn with a gold border. */
    public void setActionable(java.util.function.BiPredicate<Hit, Void> actionable)
    {
        this.actionable = actionable;
    }

    public static int width()
    {
        return 9 * CARD_W + 8 * GAP + 2 * PILE_GAP;
    }

    public static int height()
    {
        return 7 * (CARD_H + GAP) + 26;
    }

    public void render(PoseStack poseStack, Font font, BoardSnapshot board, int centreX, int top,
        Set<Integer> highlights)
    {
        hits.clear();
        zoneHighlights = highlights == null ? Set.of() : highlights;
        int y = top;

        // Opponent: hand, then backrow, then monsters (mirrored so the two
        // fields face each other across the middle).
        y = handRow(poseStack, board.opponent().hand(), centreX, y, 1, true);
        y = mainRow(poseStack, board.opponent().spells(), centreX, y, 1, false,
            pile(board.opponent(), true), pile(board.opponent(), false));
        y = mainRow(poseStack, board.opponent().monsters(), centreX, y, 1, true,
            graveOf(board.opponent(), 1), extraOf(board.opponent(), 1));

        y = extraMonsterRow(poseStack, board, centreX, y);

        y = mainRow(poseStack, board.self().monsters(), centreX, y, 0, true,
            extraOf(board.self(), 0), graveOf(board.self(), 0));
        y = mainRow(poseStack, board.self().spells(), centreX, y, 0, false,
            pileSelf(board.self(), true), pileSelf(board.self(), false));
        y = handRow(poseStack, board.self().hand(), centreX, y, 0, false);

        drawCenteredString(poseStack, font,
            "Opponent " + board.opponent().lifePoints() + " LP", centreX, top - 12, 0xFF8080);
        drawCenteredString(poseStack, font,
            "You " + board.self().lifePoints() + " LP", centreX, y + 2, 0x80FF80);
    }

    // Pile descriptors: [left, right] per row.
    private PileInfo extraOf(BoardSnapshot.Side side, int controller)
    {
        return new PileInfo("Extra Deck", side.extra().size(), OcgConstants.LOCATION_EXTRA, controller);
    }

    private PileInfo graveOf(BoardSnapshot.Side side, int controller)
    {
        return new PileInfo("Graveyard", side.grave().size(), OcgConstants.LOCATION_GRAVE, controller);
    }

    private PileInfo pile(BoardSnapshot.Side side, boolean left)
    {
        return left ? new PileInfo("Deck", side.deckCount(), OcgConstants.LOCATION_DECK, 1)
            : new PileInfo("Banished", side.banished().size(), OcgConstants.LOCATION_REMOVED, 1);
    }

    private PileInfo pileSelf(BoardSnapshot.Side side, boolean left)
    {
        return left ? new PileInfo("Banished", side.banished().size(), OcgConstants.LOCATION_REMOVED, 0)
            : new PileInfo("Deck", side.deckCount(), OcgConstants.LOCATION_DECK, 0);
    }

    private record PileInfo(String label, int count, int location, int controller)
    {
    }

    /** A five-zone row flanked by two piles. */
    private int mainRow(PoseStack poseStack, List<BoardSnapshot.Slot> slots, int centreX, int y,
        int controller, boolean monsterZone, PileInfo left, PileInfo right)
    {
        int rowWidth = width();
        int x = centreX - rowWidth / 2;

        drawPile(poseStack, left, x, y);
        x += CARD_W + PILE_GAP;

        int[] order = monsterZone ? new int[] {0, 1, 2, 3, 4} : new int[] {5, 0, 1, 2, 3, 4, 6, 7};
        if(!monsterZone)
        {
            // Field spell and the two pendulum zones flank the five backrow
            // slots; keep the row the same width as the monster row.
            order = new int[] {5, 0, 1, 2, 3, 4, 6};
        }
        for(int sequence : order)
        {
            BoardSnapshot.Slot slot = sequence < slots.size() ? slots.get(sequence) : BoardSnapshot.Slot.EMPTY;
            int zoneRef = EnginePrompt.zoneRef(controller == 1, monsterZone, sequence);
            drawSlot(poseStack, slot, x, y, zoneRef, controller,
                monsterZone ? OcgConstants.LOCATION_MZONE : OcgConstants.LOCATION_SZONE, sequence,
                labelFor(monsterZone, sequence));
            x += CARD_W + GAP;
        }

        drawPile(poseStack, right, centreX + rowWidth / 2 - CARD_W, y);
        return y + CARD_H + GAP;
    }

    private static String labelFor(boolean monsterZone, int sequence)
    {
        if(monsterZone)
        {
            return "Monster Zone " + (sequence + 1);
        }
        return switch(sequence)
        {
            case 5 -> "Field Spell";
            case 6, 7 -> "Pendulum Zone";
            default -> "Spell/Trap Zone " + (sequence + 1);
        };
    }

    private int extraMonsterRow(PoseStack poseStack, BoardSnapshot board, int centreX, int y)
    {
        int x = centreX - CARD_W - PILE_GAP / 2;
        for(int sequence = 5; sequence <= 6; sequence++)
        {
            BoardSnapshot.Slot own = slotAt(board.self().monsters(), sequence);
            BoardSnapshot.Slot theirs = slotAt(board.opponent().monsters(), sequence);
            boolean ownsIt = own.present();
            BoardSnapshot.Slot shown = ownsIt ? own : theirs;
            int controller = ownsIt ? 0 : (theirs.present() ? 1 : 0);
            drawSlot(poseStack, shown, x, y, EnginePrompt.zoneRef(false, true, sequence), controller,
                OcgConstants.LOCATION_MZONE, sequence, "Extra Monster Zone");
            x += CARD_W + PILE_GAP;
        }
        return y + CARD_H + GAP;
    }

    private int handRow(PoseStack poseStack, List<BoardSnapshot.Slot> hand, int centreX, int y,
        int controller, boolean hide)
    {
        if(hand.isEmpty())
        {
            return y + CARD_H + GAP;
        }
        // Hands overlap when large, like a real hand of cards.
        int step = hand.size() * (CARD_W + GAP) > width() ? (width() - CARD_W) / Math.max(1, hand.size() - 1)
            : CARD_W + GAP;
        int totalWidth = (hand.size() - 1) * step + CARD_W;
        int x = centreX - totalWidth / 2;

        for(int i = 0; i < hand.size(); i++)
        {
            BoardSnapshot.Slot slot = hide
                ? new BoardSnapshot.Slot(true, 0, true, false, 0, 0, 0)
                : hand.get(i);
            drawSlot(poseStack, slot, x, y, -1, controller, OcgConstants.LOCATION_HAND, i, "Hand");
            x += step;
        }
        return y + CARD_H + GAP;
    }

    private static BoardSnapshot.Slot slotAt(List<BoardSnapshot.Slot> slots, int index)
    {
        return index < slots.size() ? slots.get(index) : BoardSnapshot.Slot.EMPTY;
    }

    private void drawPile(PoseStack poseStack, PileInfo pile, int x, int y)
    {
        fill(poseStack, x - 1, y - 1, x + CARD_W + 1, y + CARD_H + 1, COLOUR_ZONE);
        fill(poseStack, x, y, x + CARD_W, y + CARD_H, COLOUR_ZONE_FILL);
        if(pile.count() > 0)
        {
            ScreenUtil.white();
            CardRenderUtil.bindMainResourceLocation(CardRenderUtil.getMainCardBack());
            DdBlitUtil.fullBlit(poseStack, x, y, CARD_W, CARD_H);
        }
        hits.add(new Hit(x, y, 0, pile.controller(), pile.location(), -1, -1,
            pile.label() + " (" + pile.count() + ")", pile.count()));
    }

    private void drawSlot(PoseStack poseStack, BoardSnapshot.Slot slot, int x, int y, int zoneRef,
        int controller, int location, int sequence, String label)
    {
        Hit hit = new Hit(x, y, slot.code(), controller, location, sequence, zoneRef, label, 0);

        boolean zoneLit = zoneRef >= 0 && zoneHighlights.contains(zoneRef);
        boolean canAct = actionable != null && actionable.test(hit, null);
        int border = zoneLit ? COLOUR_HIGHLIGHT : canAct ? COLOUR_ACTIONABLE : COLOUR_ZONE;

        fill(poseStack, x - 1, y - 1, x + CARD_W + 1, y + CARD_H + 1, border);
        fill(poseStack, x, y, x + CARD_W, y + CARD_H, COLOUR_ZONE_FILL);

        if(slot.present())
        {
            ScreenUtil.white();
            CardRenderUtil.bindMainResourceLocation(textureFor(slot));
            if(slot.defence())
            {
                DdBlitUtil.fullBlit90Degree(poseStack, x, y, CARD_W, CARD_H);
            }
            else
            {
                DdBlitUtil.fullBlit(poseStack, x, y, CARD_W, CARD_H);
            }
        }
        hits.add(hit);
    }

    private ResourceLocation textureFor(BoardSnapshot.Slot slot)
    {
        if(slot.faceDown() || slot.code() == 0)
        {
            return CardRenderUtil.getMainCardBack();
        }
        Properties properties = DdDatabase.PROPERTIES_LIST.get((long)slot.code());
        return properties == null ? CardRenderUtil.getMainCardBack()
            : properties.getMainImageResourceLocation((byte)0);
    }
}
