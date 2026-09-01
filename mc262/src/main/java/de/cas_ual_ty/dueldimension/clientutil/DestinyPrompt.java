package de.cas_ual_ty.dueldimension.clientutil;

import de.cas_ual_ty.dueldimension.clientutil.hub.HubTextures;
import de.cas_ual_ty.dueldimension.clientutil.hub.MenuInk;
import de.cas_ual_ty.dueldimension.clientutil.hub.MenuText;
import de.cas_ual_ty.dueldimension.clientutil.hub.NineSlice;
import de.cas_ual_ty.dueldimension.ocg.prompt.EnginePrompt;
import net.minecraft.client.gui.Font;

/**
 * The Destiny Draw offer: take a card you nominated, or draw normally.
 *
 * <h2>Why this is not the ordinary response window</h2>
 * The effect is a {@code GlobalEffect} with no card, so the generic picker
 * would be asked to show a card that does not exist and the list menu would
 * offer a blank name. The two buttons map straight onto the engine's own
 * answers -- index 0 is yes and index 1 is no, which is what
 * {@code toResponse} already maps for every {@code SelectYesNo}.
 *
 * <h2>Why it lives here and not in a screen</h2>
 * Both duel views ask it. The 2D screen asked it first and the 3D one did not
 * ask it at all -- {@code PromptTranslator} produced the prompt, but nothing in
 * the overworld path had a case for it, so a duellist playing on the board got
 * the generic two-option chooser with none of the artwork. A second copy in the
 * other screen would have been two panels to keep in step, and they would have
 * drifted the first time either was touched.
 * <p>
 * The Destiny side wears the SAME badge the deck editor puts on a flagged card.
 * That is the whole point of it: the player is being asked about the cards they
 * marked, and the mark is what they marked them with.
 * <p>
 * Dimming is the CALLER's, because the two views dim differently and both are
 * right: the 2D screen darkens only the board and leaves its sidebar readable,
 * and the 3D one has no sidebar to spare.
 */
public final class DestinyPrompt
{
    private DestinyPrompt()
    {
    }

    private static final int PAD = 12;
    private static final int BUTTON_W = 104;
    private static final int BUTTON_H = 76;
    private static final int GAP = 10;
    private static final int ICON = 32;
    /** Room above the buttons for the panel's own title. */
    private static final int TITLE_BAND = 26;

    /** Whether this is the Destiny Draw being offered, rather than any other question. */
    public static boolean isOffered(EnginePrompt prompt)
    {
        return prompt != null && prompt.kind() == EnginePrompt.Kind.DESTINY;
    }

    private static int panelW()
    {
        return BUTTON_W * 2 + GAP + PAD * 2;
    }

    private static int left(int width)
    {
        return (width - panelW()) / 2;
    }

    private static int top(int height)
    {
        return height / 2 - 62;
    }

    /** The two choices, as one rectangle each: {x, y, w, h}. */
    public static int[][] buttons(int width, int height)
    {
        int left = left(width);
        int top = top(height) + TITLE_BAND;
        return new int[][] {
            {left + PAD, top, BUTTON_W, BUTTON_H},
            {left + PAD + BUTTON_W + GAP, top, BUTTON_W, BUTTON_H}};
    }

    public static void render(net.minecraft.client.gui.GuiGraphicsExtractor poseStack, Font font, int width, int height,
        int mouseX, int mouseY)
    {
        int left = left(width);
        int top = top(height);
        int[][] buttons = buttons(width, height);

        NineSlice.draw(poseStack, HubTextures.PANEL, left, top, panelW(),
            BUTTON_H + TITLE_BAND + PAD);

        String title = "Destiny Draw";
        poseStack.text(font, title, left + (panelW() - font.width(title)) / 2, top + 8,
            MenuInk.title(), MenuInk.shadow());

        choice(poseStack, font, buttons[0], mouseX, mouseY, HubTextures.DESTINY_CARD,
            "Destiny Draw", "One of your marked cards");
        choice(poseStack, font, buttons[1], mouseX, mouseY, HubTextures.NORMAL_DRAW,
            "Normal Draw", "Whatever is on top");
    }

    /**
     * @return 0 for the Destiny Draw, 1 for the normal draw, -1 for a click that
     *         hit neither -- which the caller should still swallow, because the
     *         panel is over the board it is asking about
     */
    public static int clicked(int width, int height, double mouseX, double mouseY)
    {
        int[][] buttons = buttons(width, height);
        for(int i = 0; i < buttons.length; i++)
        {
            int[] box = buttons[i];
            if(mouseX >= box[0] && mouseX < box[0] + box[2]
                && mouseY >= box[1] && mouseY < box[1] + box[3])
            {
                return i;
            }
        }
        return -1;
    }

    private static void choice(net.minecraft.client.gui.GuiGraphicsExtractor poseStack, Font font, int[] box, int mouseX,
        int mouseY, net.minecraft.resources.Identifier icon, String label, String detail)
    {
        boolean hovered = mouseX >= box[0] && mouseX < box[0] + box[2]
            && mouseY >= box[1] && mouseY < box[1] + box[3];
        NineSlice.draw(poseStack, HubTextures.BUTTON, box[0], box[1], box[2], box[3],
            hovered ? NineSlice.HOVER : 0, 3, 1F);

        DdBlitUtil.blit(poseStack, icon, box[0] + (box[2] - ICON) / 2, box[1] + 8, ICON, ICON,
            0F, 0F, 1F, 1F, DdBlitUtil.NO_TINT);
        poseStack.text(font, label, box[0] + (box[2] - font.width(label)) / 2,
            box[1] + 46, MenuInk.title(), MenuInk.shadow());
        // The consequence, in the smaller ink, because the label alone does not
        // say what the choice costs.
        float small = MenuText.oneStepSmaller();
        int detailW = Math.round(font.width(detail) * small);
        poseStack.pose().pushMatrix();
        poseStack.pose().scale(small, small);
        poseStack.text(font, detail,
            Math.round((box[0] + (box[2] - detailW) / 2) / small),
            Math.round((box[1] + 60) / small), MenuInk.body(), MenuInk.shadow());
        poseStack.pose().popMatrix();
    }
}
