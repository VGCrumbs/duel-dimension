package de.cas_ual_ty.dueldimension.clientutil;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import de.cas_ual_ty.dueldimension.DdDatabase;
import de.cas_ual_ty.dueldimension.card.properties.Properties;
import de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot;
import de.cas_ual_ty.dueldimension.ocg.prompt.EnginePrompt;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Draws the full duel field, mirroring EDOPro's table layout in miniature:
 * per side a backrow of [field spell][5 spell/trap][2 pendulum], a monster
 * row of 5, the two shared extra monster zones between the players, hands
 * (opponent's as backs), and pile counts. Zones eligible for a pending place
 * selection are highlighted and clickable.
 * <p>
 * Card art comes from the mod's own image pipeline by passcode — the same art
 * as the card in your binder. A slot the snapshot marks face-down draws as a
 * card back, because that is all the client was told.
 */
public class BoardRenderer extends GuiComponent
{
    public static final int CARD_W = 20;
    public static final int CARD_H = 29;
    private static final int GAP = 2;
    private static final int HIGHLIGHT = 0x8000FF00;

    /** A drawn slot and what it maps to. {@code zoneRef} matches {@link EnginePrompt#zoneRef}. */
    public record Hit(int x, int y, int code, String zone, int sequence, boolean opponent, int zoneRef)
    {
        public boolean contains(double mouseX, double mouseY)
        {
            return mouseX >= x && mouseX < x + CARD_W && mouseY >= y && mouseY < y + CARD_H;
        }
    }

    private final List<Hit> hits = new ArrayList<>();

    public List<Hit> hits()
    {
        return hits;
    }

    /** Total height the board occupies. */
    public static int height(BoardSnapshot board)
    {
        // opp hand, opp backrow, opp monsters, EMZ, own monsters, own backrow,
        // own hand, plus two pile-count text lines.
        return 7 * (CARD_H + GAP) + 24;
    }

    /**
     * @param highlights zoneRefs eligible for the current place selection
     */
    public void render(PoseStack poseStack, Font font, BoardSnapshot board, int centreX, int top,
        Set<Integer> highlights)
    {
        hits.clear();
        currentHighlights = highlights;
        int y = top;

        y = row(poseStack, board.opponent().hand(), centreX, y, true, "hand", null, true);
        y = backrow(poseStack, board.opponent().spells(), centreX, y, true, highlights);
        y = monsterRow(poseStack, board.opponent().monsters(), centreX, y, true, highlights);
        y = extraMonsterRow(poseStack, board, centreX, y, highlights);
        y = monsterRow(poseStack, board.self().monsters(), centreX, y, false, highlights);
        y = backrow(poseStack, board.self().spells(), centreX, y, false, highlights);
        y = row(poseStack, board.self().hand(), centreX, y, false, "hand", null, false);

        drawCenteredString(poseStack, font, "Them: deck " + board.opponent().deckCount()
            + "  extra " + board.opponent().extra().size()
            + "  grave " + board.opponent().grave().size()
            + "  banished " + board.opponent().banished().size(), centreX, y, 0xFF9090);
        y += 11;
        drawCenteredString(poseStack, font, "You: deck " + board.self().deckCount()
            + "  extra " + board.self().extra().size()
            + "  grave " + board.self().grave().size()
            + "  banished " + board.self().banished().size(), centreX, y, 0x90FF90);
    }

    /** Backrow order: field spell (5), spells 0-4, pendulum zones (6, 7). */
    private int backrow(PoseStack poseStack, List<BoardSnapshot.Slot> spells, int centreX, int y,
        boolean opponent, Set<Integer> highlights)
    {
        List<BoardSnapshot.Slot> ordered = new ArrayList<>(8);
        List<Integer> sequences = new ArrayList<>(8);
        int[] order = {5, 0, 1, 2, 3, 4, 6, 7};
        for(int sequence : order)
        {
            ordered.add(sequence < spells.size() ? spells.get(sequence) : BoardSnapshot.Slot.EMPTY);
            sequences.add(sequence);
        }
        return row(poseStack, ordered, centreX, y, opponent, "spell", refs(sequences, opponent, false, highlights),
            false);
    }

    private int monsterRow(PoseStack poseStack, List<BoardSnapshot.Slot> monsters, int centreX, int y,
        boolean opponent, Set<Integer> highlights)
    {
        List<BoardSnapshot.Slot> main = monsters.subList(0, Math.min(5, monsters.size()));
        List<Integer> sequences = List.of(0, 1, 2, 3, 4);
        return row(poseStack, main, centreX, y, opponent, "monster",
            refs(sequences, opponent, true, highlights), false);
    }

    /** The two shared extra monster zones: whoever controls one, its card shows there. */
    private int extraMonsterRow(PoseStack poseStack, BoardSnapshot board, int centreX, int y,
        Set<Integer> highlights)
    {
        int x = centreX - CARD_W - GAP;
        for(int sequence = 5; sequence <= 6; sequence++)
        {
            BoardSnapshot.Slot own = slotAt(board.self().monsters(), sequence);
            BoardSnapshot.Slot theirs = slotAt(board.opponent().monsters(), sequence);
            boolean opponentOwns = !own.present() && theirs.present();
            BoardSnapshot.Slot shown = own.present() ? own : theirs;

            int ownRef = EnginePrompt.zoneRef(false, true, sequence);
            boolean lit = highlights != null && highlights.contains(ownRef);
            drawSlot(poseStack, shown, x, y, lit);
            hits.add(new Hit(x, y, shown.code(), "monster", sequence, opponentOwns, ownRef));
            x += CARD_W + GAP * 2;
        }
        return y + CARD_H + GAP;
    }

    private static BoardSnapshot.Slot slotAt(List<BoardSnapshot.Slot> slots, int index)
    {
        return index < slots.size() ? slots.get(index) : BoardSnapshot.Slot.EMPTY;
    }

    private List<Integer> refs(List<Integer> sequences, boolean opponent, boolean monsterZone,
        Set<Integer> highlights)
    {
        List<Integer> result = new ArrayList<>(sequences.size());
        for(int sequence : sequences)
        {
            result.add(EnginePrompt.zoneRef(opponent, monsterZone, sequence));
        }
        return result;
    }

    private int row(PoseStack poseStack, List<BoardSnapshot.Slot> slots, int centreX, int y, boolean opponent,
        String zone, List<Integer> zoneRefs, boolean hideAll)
    {
        if(slots.isEmpty())
        {
            return y;
        }
        int totalWidth = slots.size() * CARD_W + (slots.size() - 1) * GAP;
        int x = centreX - totalWidth / 2;

        for(int i = 0; i < slots.size(); i++)
        {
            BoardSnapshot.Slot slot = hideAll && slots.get(i).present()
                ? new BoardSnapshot.Slot(true, 0, true, false, 0, 0, 0)
                : slots.get(i);
            int slotX = x + i * (CARD_W + GAP);
            int zoneRef = zoneRefs != null ? zoneRefs.get(i) : -1;
            drawSlot(poseStack, slot, slotX, y, zoneRef >= 0 && currentHighlights != null
                && currentHighlights.contains(zoneRef));
            hits.add(new Hit(slotX, y, slot.code(), zone, zoneRefs != null ? (zoneRef & 7) : i, opponent, zoneRef));
        }
        return y + CARD_H + GAP;
    }

    private Set<Integer> currentHighlights;

    public void setHighlights(Set<Integer> highlights)
    {
        currentHighlights = highlights;
    }

    private void drawSlot(PoseStack poseStack, BoardSnapshot.Slot slot, int x, int y, boolean highlighted)
    {
        fill(poseStack, x - 1, y - 1, x + CARD_W + 1, y + CARD_H + 1,
            highlighted ? HIGHLIGHT : 0x30FFFFFF);
        fill(poseStack, x, y, x + CARD_W, y + CARD_H, 0x80000000);

        if(!slot.present())
        {
            return;
        }
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.setShaderTexture(0, textureFor(slot));
        if(slot.defence())
        {
            poseStack.pushPose();
            poseStack.translate(x + CARD_W / 2.0, y + CARD_H / 2.0, 0);
            poseStack.mulPose(com.mojang.math.Vector3f.ZP.rotationDegrees(90));
            poseStack.translate(-CARD_H / 2.0, -CARD_W / 2.0, 0);
            blit(poseStack, 0, 0, 0, 0, CARD_H, CARD_W, CARD_H, CARD_W);
            poseStack.popPose();
        }
        else
        {
            blit(poseStack, x, y, 0, 0, CARD_W, CARD_H, CARD_W, CARD_H);
        }
        if(slot.overlays() > 0)
        {
            fill(poseStack, x, y, x + 8, y + 8, 0xC0000000);
        }
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
