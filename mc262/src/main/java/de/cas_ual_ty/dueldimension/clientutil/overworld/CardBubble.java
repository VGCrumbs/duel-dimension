package de.cas_ual_ty.dueldimension.clientutil.overworld;

import de.cas_ual_ty.dueldimension.DdDatabase;
import de.cas_ual_ty.dueldimension.card.properties.Properties;
import de.cas_ual_ty.dueldimension.clientutil.hub.CardInfoPanel;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;


/**
 * A card's own words, in a bubble that follows the cursor.
 * <p>
 * Held on Shift, the same as the deck builder's preview, and reading the same
 * three things from the same card object: its name, the facts line the card
 * itself prints, and its text. No artwork -- the card is already on screen at
 * the size the board draws it, and the thing a duellist is squinting at during
 * a turn is the wording.
 * <p>
 * Shows nothing for a card it does not have. A face-down card arrives with no
 * code at all, so there is no branch here that could leak one: the lookup simply
 * finds nothing and the bubble does not open.
 */
public final class CardBubble
{
    private CardBubble()
    {
    }

    private static final int WRAP = 168;
    private static final int PAD = 6;

    /**
     * Draws the bubble beside the cursor.
     *
     * @param code the card's passcode; 0 for anything the client was not told
     */
    public static void draw(GuiGraphicsExtractor extractor, Font font, int code, int mouseX,
        int mouseY, int screenW, int screenH)
    {
        draw(extractor, font, code, mouseX, mouseY, screenW, screenH, 0L);
    }

    /**
     * The same bubble, told what the duel currently says this card IS.
     *
     * @param liveRace the engine's race for this copy, or 0 where there is none
     *                 to be had -- a list of passcodes has no board behind it
     */
    public static void draw(GuiGraphicsExtractor extractor, Font font, int code, int mouseX,
        int mouseY, int screenW, int screenH, long liveRace)
    {
        if(code == 0)
        {
            return;
        }
        Properties card = DdDatabase.PROPERTIES_LIST.get((long)code);
        if(card == null)
        {
            return;
        }
        // No artwork: the card is already on screen at the size the board draws
        // it, and the thing a duellist is squinting at during a turn is the
        // wording.
        // Sized by the header and capped only by the window: a long name widens
        // the bubble rather than being drawn smaller than the text beneath it.
        // WRAP is what it settles at for an ordinary name, not a ceiling.
        int panelW = CardInfoPanel.preferredWidth(font, card, Math.max(WRAP + PAD * 2,
            screenW - 8), true);
        // Measured before it is placed, because the bubble follows the cursor
        // and cannot be centred on a height it does not know yet.
        int maxHeight = screenH - 8;
        int panelH = CardInfoPanel.height(font, card, panelW, maxHeight, 0, true);

        // Beside the cursor, and flipped to the other side rather than pushed
        // off the screen when there is no room -- the same rule the deck
        // builder's preview follows.
        int x = mouseX + 14;
        if(x + panelW > screenW - 4)
        {
            x = mouseX - 14 - panelW;
        }
        x = Math.max(4, Math.min(x, screenW - panelW - 4));
        int y = Math.max(4, Math.min(mouseY - panelH / 2, screenH - panelH - 4));

        // No scrolling: this bubble is held open by a key rather than clicked
        // into, so there is nothing holding still for a wheel to act on. A card
        // longer than the window is read on the card info screen.
        CardInfoPanel.draw(extractor, font, card, x, y, panelW, maxHeight, 0, -1, true);
    }
}
