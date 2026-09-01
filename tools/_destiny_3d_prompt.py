"""The Destiny Draw offer, lifted out of the 2D screen so the 3D one can ask it too."""
import sys

CLASS = '''package de.cas_ual_ty.dueldimension.clientutil;

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

    public static void render(EXTRACTOR poseStack, Font font, int width, int height,
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

    private static void choice(EXTRACTOR poseStack, Font font, int[] box, int mouseX,
        int mouseY, RESLOC icon, String label, String detail)
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
'''

RENDER_NEW = '''    /**
     * The Destiny Draw offer, over a dimmed board.
     * <p>
     * The panel itself is {@link de.cas_ual_ty.dueldimension.clientutil.DestinyPrompt},
     * shared with the 3D duel so the same question looks the same in both. The
     * dim stays here because it is this screen's: only the board darkens, and
     * the sidebar beside it stays readable.
     */
    private void renderDestiny(GuiGraphicsExtractor poseStack, int mouseX, int mouseY)
    {
        if(!destinyOpen())
        {
            return;
        }
        dimBoard(poseStack, 0xB0000000);
        de.cas_ual_ty.dueldimension.clientutil.DestinyPrompt.render(poseStack, font,
            width, height, mouseX, mouseY);
    }

'''

CLICK_OLD = '''        int[][] buttons = destinyButtons();
        for(int i = 0; i < buttons.length; i++)
        {
            int[] box = buttons[i];
            if(mouseX >= box[0] && mouseX < box[0] + box[2]
                && mouseY >= box[1] && mouseY < box[1] + box[3])
            {
                // A yes/no: index 0 is yes and index 1 is no, which is exactly
                // what toResponse already maps for every SelectYesNo. Sent as
                // the index rather than as an empty "cancel", because this
                // prompt cannot be cancelled -- the draw happens either way and
                // the only question is which card it takes.
                answer(new int[] {i}, 0);
                return true;
            }
        }
'''

CLICK_NEW = '''        int choice = de.cas_ual_ty.dueldimension.clientutil.DestinyPrompt.clicked(
            width, height, mouseX, mouseY);
        if(choice >= 0)
        {
            // A yes/no: index 0 is yes and index 1 is no, which is exactly what
            // toResponse already maps for every SelectYesNo. Sent as the index
            // rather than as an empty "cancel", because this prompt cannot be
            // cancelled -- the draw happens either way and the only question is
            // which card it takes.
            answer(new int[] {choice}, 0);
            return true;
        }
'''

# ---- the 3D screen ------------------------------------------------------
HUD_ANCHOR = '''        // No label and no strip. What a zone is called is written on the board'''

HUD_NEW = '''        // LAST, and over everything: the engine is waiting on this one, and the
        // panel covers the board it is asking about. Drawn from the same class
        // the 2D screen draws it from -- see DestinyPrompt for why it is not a
        // second copy.
        if(de.cas_ual_ty.dueldimension.clientutil.DestinyPrompt.isOffered(
            DuelClientState.prompt))
        {
            // Full width here, where the duel screen dims only its board: this
            // view has no sidebar to keep readable.
            extractor.fill(0, 0, width, height, 0xB0000000);
            de.cas_ual_ty.dueldimension.clientutil.DestinyPrompt.render(extractor, font,
                width, height, mouseX, mouseY);
        }

'''

CLICK_ANCHOR = '''        MouseButtonEvent event = new MouseButtonEvent(vanillaX, vanillaY, vanillaButton);
        boolean doubled = false;
'''

CLICK_3D = '''
        // Before every other target, including the confirm button. This question
        // is modal -- the panel is drawn over the board and the duel is parked
        // on the answer -- so a click that misses both choices is swallowed
        // rather than passed to whatever is underneath it.
        if(de.cas_ual_ty.dueldimension.clientutil.DestinyPrompt.isOffered(
            DuelClientState.prompt))
        {
            int destiny = de.cas_ual_ty.dueldimension.clientutil.DestinyPrompt.clicked(
                width, height, event.x(), event.y());
            if(destiny >= 0)
            {
                DuelActionController.answer(new int[] {destiny}, 0);
                dismiss();
            }
            return true;
        }
'''

TREES = {
    'mc1211': ('de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor', 'net.minecraft.resources.ResourceLocation'),
    'mc262': ('net.minecraft.client.gui.GuiGraphicsExtractor', 'net.minecraft.resources.Identifier'),
}


def cut(s, start, end, path, what):
    i = s.find(start)
    if i < 0:
        sys.exit('%s: no start for %s' % (path, what))
    j = s.find(end, i)
    if j < 0:
        sys.exit('%s: no end for %s' % (path, what))
    return s[:i], s[j + len(end):]


def patch(path, edits):
    raw = open(path, 'rb').read().decode('utf-8')
    crlf = '\r\n' in raw
    s = raw.replace('\r\n', '\n')
    for old, new in edits:
        if s.count(old) != 1:
            sys.exit('%s: %d matches for %r' % (path, s.count(old), old[:60]))
        s = s.replace(old, new)
    open(path, 'wb').write((s.replace('\n', '\r\n') if crlf else s).encode('utf-8'))


for tree, (extractor, resloc) in TREES.items():
    base = tree + '/src/main/java/de/cas_ual_ty/dueldimension/clientutil/'

    body = CLASS.replace('EXTRACTOR', extractor).replace('RESLOC', resloc)
    with open(base + 'DestinyPrompt.java', 'wb') as f:
        f.write(body.replace('\n', '\r\n').encode('utf-8'))
    print('wrote', tree, 'DestinyPrompt')

    # The 2D screen hands its panel over.
    duel = base + 'EngineDuelScreen.java'
    raw = open(duel, 'rb').read().decode('utf-8')
    crlf = '\r\n' in raw
    s = raw.replace('\r\n', '\n')
    head, tail = cut(s, '    /** The two choices, as one rectangle each: {x, y, w, h}. */',
                     '        poseStack.pose().popMatrix();\n    }\n\n', duel, 'the destiny panel')
    s = head + RENDER_NEW + tail
    if s.count(CLICK_OLD) != 1:
        sys.exit('%s: %d matches for the click loop' % (duel, s.count(CLICK_OLD)))
    s = s.replace(CLICK_OLD, CLICK_NEW)
    open(duel, 'wb').write((s.replace('\n', '\r\n') if crlf else s).encode('utf-8'))
    print('  2D screen delegates')

    # The 3D screen gains it.
    patch(tree + '/src/main/java/de/cas_ual_ty/dueldimension/clientutil/overworld/BoardPointerScreen.java',
          [(HUD_ANCHOR, HUD_NEW + HUD_ANCHOR),
           (CLICK_ANCHOR, CLICK_ANCHOR + CLICK_3D)])
    print('  3D screen renders and answers it')
