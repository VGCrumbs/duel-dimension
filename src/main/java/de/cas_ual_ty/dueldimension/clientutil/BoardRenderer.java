package de.cas_ual_ty.dueldimension.clientutil;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import de.cas_ual_ty.dueldimension.DdDatabase;
import de.cas_ual_ty.dueldimension.card.properties.Properties;
import de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws a duel field: both players' monster and spell rows, hand, life points
 * and pile counts, with real card art.
 * <p>
 * Card images come from the mod's existing card database and texture pipeline,
 * looked up by passcode — the engine and the collection use the same ids, so a
 * card on the field renders with the same art as the card in your binder.
 * Anything the snapshot marks face-down draws as a card back, because that is
 * all the client was told.
 */
public class BoardRenderer extends GuiComponent
{
    public static final int CARD_WIDTH = 24;
    public static final int CARD_HEIGHT = 35;
    private static final int GAP = 3;

    /** A drawn card and what it belongs to, so clicks can be mapped back. */
    public record Hit(int x, int y, int width, int height, int code, boolean opponent, String zone, int sequence)
    {
        public boolean contains(double mouseX, double mouseY)
        {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    private final List<Hit> hits = new ArrayList<>();

    /** @return the areas drawn this frame, for hover and click handling */
    public List<Hit> hits()
    {
        return hits;
    }

    /**
     * Draws the whole field centred on {@code centreX}, opponent on top.
     *
     * @return the vertical space consumed
     */
    public int render(PoseStack poseStack, Font font, BoardSnapshot board, int centreX, int top)
    {
        hits.clear();
        int y = top;

        // Opponent, mirrored: spells above monsters so the two fields face
        // each other the way they would across a table.
        y = renderRow(poseStack, board.opponent().spells(), centreX, y, true, "spell");
        y = renderRow(poseStack, board.opponent().monsters(), centreX, y, true, "monster");

        y += 4;
        drawCenteredString(poseStack, font,
            board.opponent().lifePoints() + " LP   hand " + board.opponent().hand().size()
                + "   deck " + board.opponent().deckCount() + "   grave " + board.opponent().graveCount(),
            centreX, y, 0xFF8080);
        y += 12;
        drawCenteredString(poseStack, font,
            board.self().lifePoints() + " LP   hand " + board.self().hand().size()
                + "   deck " + board.self().deckCount() + "   grave " + board.self().graveCount(),
            centreX, y, 0x80FF80);
        y += 12;

        y = renderRow(poseStack, board.self().monsters(), centreX, y, false, "monster");
        y = renderRow(poseStack, board.self().spells(), centreX, y, false, "spell");

        if(!board.self().hand().isEmpty())
        {
            y += 4;
            drawCenteredString(poseStack, font, "Hand", centreX, y, 0xC0C0C0);
            y += 10;
            y = renderRow(poseStack, board.self().hand(), centreX, y, false, "hand");
        }
        return y - top;
    }

    private int renderRow(PoseStack poseStack, List<BoardSnapshot.Slot> slots, int centreX, int y,
        boolean opponent, String zone)
    {
        if(slots.isEmpty())
        {
            return y;
        }
        int totalWidth = slots.size() * CARD_WIDTH + (slots.size() - 1) * GAP;
        int x = centreX - totalWidth / 2;

        for(int i = 0; i < slots.size(); i++)
        {
            BoardSnapshot.Slot slot = slots.get(i);
            int slotX = x + i * (CARD_WIDTH + GAP);
            drawSlot(poseStack, slot, slotX, y);
            if(slot.present())
            {
                hits.add(new Hit(slotX, y, CARD_WIDTH, CARD_HEIGHT, slot.code(), opponent, zone, i));
            }
        }
        return y + CARD_HEIGHT + GAP;
    }

    private void drawSlot(PoseStack poseStack, BoardSnapshot.Slot slot, int x, int y)
    {
        // Empty zones stay as outlines so the field's shape is always legible.
        fill(poseStack, x - 1, y - 1, x + CARD_WIDTH + 1, y + CARD_HEIGHT + 1, 0x40FFFFFF);
        fill(poseStack, x, y, x + CARD_WIDTH, y + CARD_HEIGHT, 0x80000000);

        if(!slot.present())
        {
            return;
        }

        RenderSystem.setShaderColor(1, 1, 1, 1);
        ResourceLocation texture = textureFor(slot);
        if(texture != null)
        {
            RenderSystem.setShaderTexture(0, texture);
            if(slot.defence())
            {
                // Defence position: the card lies on its side. Rotating about
                // the card's centre keeps it inside its own zone.
                poseStack.pushPose();
                poseStack.translate(x + CARD_WIDTH / 2.0, y + CARD_HEIGHT / 2.0, 0);
                poseStack.mulPose(com.mojang.math.Vector3f.ZP.rotationDegrees(90));
                poseStack.translate(-CARD_HEIGHT / 2.0, -CARD_WIDTH / 2.0, 0);
                blit(poseStack, 0, 0, 0, 0, CARD_HEIGHT, CARD_WIDTH, CARD_HEIGHT, CARD_WIDTH);
                poseStack.popPose();
            }
            else
            {
                blit(poseStack, x, y, 0, 0, CARD_WIDTH, CARD_HEIGHT, CARD_WIDTH, CARD_HEIGHT);
            }
        }
    }

    private ResourceLocation textureFor(BoardSnapshot.Slot slot)
    {
        if(slot.faceDown() || slot.code() == 0)
        {
            return CardRenderUtil.getMainCardBack();
        }
        Properties properties = DdDatabase.PROPERTIES_LIST.get((long)slot.code());
        if(properties == null)
        {
            return CardRenderUtil.getMainCardBack();
        }
        return properties.getMainImageResourceLocation((byte)0);
    }
}
