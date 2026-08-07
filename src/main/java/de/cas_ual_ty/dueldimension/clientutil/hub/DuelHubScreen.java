package de.cas_ual_ty.dueldimension.clientutil.hub;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The duel hub: decks, profile, outfits, settings.
 * <p>
 * <b>Partly ported.</b> The frame is real — the panel, the tab strip, the
 * textures and the widgets underneath are the Forge build's, drawn through the
 * retained-mode API this version uses. What each tab shows is not: those bodies
 * are the deck list, the recipe columns, the wardrobe and the mat picker, and
 * each depends on client state ({@code EditorState}) and on screens
 * ({@code DeckEditorScreen}, {@code CardShopScreen}, {@code DuelLobbyScreen})
 * that have not been ported.
 * <p>
 * It exists in this state on purpose. The tab strip is what proves the
 * foundation — {@link NineSlice}, {@link HubWidgets}, {@link HubTextures} — and
 * a foundation that only compiles is a foundation nobody has checked. Each tab
 * says plainly what it is waiting for rather than showing an empty panel that
 * could be mistaken for a bug.
 */
public class DuelHubScreen extends Screen
{
    /** The panel's size, unchanged from the Forge layout. */
    private static final int WIDTH = 340;
    private static final int HEIGHT = 220;
    private static final int PAD = 8;
    private static final int TAB_W = 66;
    private static final int TAB_H = 18;

    /**
     * The four sections, with what each is still waiting on. The order is the
     * Forge build's, so the strip reads the same.
     */
    private enum Section
    {
        PROFILE("Profile", "the profile panel needs EditorState"),
        DECKS("Decks", "the deck list needs EditorState and the deck editor screen"),
        OUTFIT("Outfit", "the wardrobe needs the outfit preview renderer"),
        SETTINGS("Settings", "the mat picker needs the colour-picker widget");

        private final String label;
        private final String waitingOn;

        Section(String label, String waitingOn)
        {
            this.label = label;
            this.waitingOn = waitingOn;
        }
    }

    private Section section = Section.PROFILE;
    private int left;
    private int top;

    public DuelHubScreen()
    {
        super(Component.literal("Duel Hub"));
    }

    @Override
    protected void init()
    {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        rebuild();
    }

    private void rebuild()
    {
        clearWidgets();
        int tabX = left + PAD;
        for(Section candidate : Section.values())
        {
            Section target = candidate;
            addRenderableWidget(new HubWidgets.TabButton(tabX, top + PAD, TAB_W, TAB_H,
                Component.literal(candidate.label), () -> section == target, pressed ->
            {
                section = target;
                rebuild();
            }));
            tabX += TAB_W + 4;
        }

        addRenderableWidget(new HubWidgets.TextureButton(left + WIDTH - PAD - 80,
            top + HEIGHT - 32, 80, 20, Component.literal("Close"), pressed -> onClose()));
    }

    /**
     * A screen describes itself rather than drawing itself now: the extractor
     * collects everything and the game draws it in one pass afterwards.
     * <p>
     * Note the two different names. A <em>screen</em> implements
     * {@code extractRenderState}, which is {@code Renderable}'s single method;
     * a <em>widget</em> implements {@code extractContents}, which
     * {@code AbstractWidget} calls from its own extract. Getting them the wrong
     * way round compiles as a new method and silently draws nothing.
     */
    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
        float partialTick)
    {
        // The panel goes down BEFORE the widgets. Retained mode draws in the
        // order it was described, so calling super first would paint the tabs
        // and then cover them with the panel they sit on.
        NineSlice.draw(graphics, HubTextures.PANEL, left, top, WIDTH, HEIGHT);
        int bodyTop = top + PAD + TAB_H + 8;
        NineSlice.draw(graphics, HubTextures.PANEL_INSET, left + PAD, bodyTop,
            WIDTH - PAD * 2, HEIGHT - (PAD + TAB_H + 8) - 40);

        graphics.text(font, section.label, left + PAD + 8, bodyTop + 8, 0xFFF4D089, true);
        graphics.text(font, "Not ported yet:", left + PAD + 8, bodyTop + 24, 0xFF8A93A3, true);
        graphics.text(font, section.waitingOn, left + PAD + 8, bodyTop + 36, 0xFFC2C9D6, true);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
