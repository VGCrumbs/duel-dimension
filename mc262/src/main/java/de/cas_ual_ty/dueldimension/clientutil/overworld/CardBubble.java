package de.cas_ual_ty.dueldimension.clientutil.overworld;

import de.cas_ual_ty.dueldimension.DdDatabase;
import de.cas_ual_ty.dueldimension.card.properties.Properties;
import de.cas_ual_ty.dueldimension.clientutil.hub.HubTextures;
import de.cas_ual_ty.dueldimension.clientutil.hub.NineSlice;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

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
    private static final int LINE = 10;

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

        List<FormattedCharSequence> name = font.split(
            Component.literal(card.getName() == null ? "" : card.getName()), WRAP);
        // The card's own facts line -- type, attribute, level, statistics --
        // built by the card rather than assembled here, so it says exactly what
        // it says everywhere else in the mod.
        List<Component> facts = de.cas_ual_ty.dueldimension.clientutil.CardFacts.of(card,
            liveRace);
        List<FormattedCharSequence> factLines = new ArrayList<>();
        for(Component fact : facts)
        {
            factLines.addAll(font.split(fact, WRAP));
        }
        List<FormattedCharSequence> text = font.split(
            Component.literal(card.getText() == null ? "" : card.getText()), WRAP);

        int lines = name.size() + factLines.size() + text.size();
        int panelW = WRAP + PAD * 2;
        int panelH = PAD * 2 + lines * LINE + 4;

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

        NineSlice.draw(extractor, HubTextures.PANEL, x, y, panelW, panelH);

        int at = y + PAD;
        for(FormattedCharSequence line : name)
        {
            extractor.text(font, line, x + PAD, at, 0xFFF4D089, true);
            at += LINE;
        }
        for(FormattedCharSequence line : factLines)
        {
            // The default colour, which a styled run overrides on its own: a
            // race the duel has changed carries its own blue and must not be
            // repainted the colour of everything around it.
            extractor.text(font, line, x + PAD, at, 0xFF9FB4CC, true);
            at += LINE;
        }
        at += 4;
        for(FormattedCharSequence line : text)
        {
            extractor.text(font, line, x + PAD, at, 0xFFDDE3EC, true);
            at += LINE;
        }
    }
}
