package de.cas_ual_ty.dueldimension.clientutil.hub;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The duel hub: decks, profile, outfits, settings.
 * <p>
 * <b>Partly ported.</b> Profile and Decks show real data: {@link EditorState}
 * holds what the server sent, so the collection, the deck list and the active
 * deck are the player's own. Outfit and Settings do not, because the wardrobe
 * needs the outfit preview renderer and the mat picker needs a colour-picker
 * widget, and neither is ported.
 * <p>
 * Nothing here CHANGES a deck. Use, rename, duplicate, delete and the editor
 * itself all open {@code DeckEditorScreen} or need the confirmation dialogue,
 * and showing buttons that do nothing would be worse than showing none. A tab
 * with no body says what it is waiting for rather than leaving a blank panel
 * that could be mistaken for a bug.
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
        PROFILE("Profile", ""),
        DECKS("Decks", ""),
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

        switch(section)
        {
            case PROFILE -> profilePanel(graphics, bodyTop);
            case DECKS -> deckPanel(graphics, bodyTop);
            default -> waiting(graphics, bodyTop);
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    /** A section whose body has not been ported, saying what it waits on. */
    private void waiting(GuiGraphicsExtractor graphics, int bodyTop)
    {
        graphics.text(font, section.label, left + PAD + 8, bodyTop + 8, 0xFFF4D089, true);
        graphics.text(font, "Not ported yet:", left + PAD + 8, bodyTop + 24, 0xFF8A93A3, true);
        graphics.text(font, section.waitingOn, left + PAD + 8, bodyTop + 36, 0xFFC2C9D6, true);
    }

    private void profilePanel(GuiGraphicsExtractor graphics, int bodyTop)
    {
        int x = left + PAD + 10;
        int y = bodyTop + 10;
        graphics.text(font, "Profile", x, y, 0xFFF4D089, true);
        y += 16;
        String name = minecraft != null && minecraft.player != null
            ? minecraft.player.getGameProfile().name() : "-";
        graphics.text(font, "Duelist: " + name, x, y, 0xFFE6EAF2, true);
        y += 12;

        String active = EditorState.profile().activeDeck();
        graphics.text(font, "Active deck: " + (active.isEmpty() ? "none chosen" : active),
            x, y, 0xFFC2C9D6, true);
        y += 18;

        // What the server has actually told us. Before the sync arrives these
        // would all read zero, which is indistinguishable from a new player, so
        // it says which it is.
        if(!EditorState.isSynced())
        {
            graphics.text(font, "Waiting for the server...", x, y, 0xFF7A8090, true);
            return;
        }
        graphics.text(font, "Cards owned: " + EditorState.trunk().totalCards()
            + "  (" + EditorState.trunk().distinctCards() + " distinct)", x, y, 0xFFC2C9D6, true);
        y += 12;
        graphics.text(font, "Decks: " + EditorState.ownDecks().size(), x, y, 0xFFC2C9D6, true);
        y += 12;
        graphics.text(font, "Free mode: "
            + (EditorState.freeMode() ? "on" : "off"), x, y, 0xFFC2C9D6, true);
    }

    /**
     * The deck list, read-only for now.
     * <p>
     * The rows are here because {@code EditorState} is: what a player owns and
     * has built is real. What is not here is anything that CHANGES a deck --
     * use, rename, duplicate, delete, and the editor itself -- because those
     * open {@code DeckEditorScreen} or need the confirmation dialogue, and
     * neither is ported. Showing buttons that do nothing would be worse than
     * showing none.
     */
    private void deckPanel(GuiGraphicsExtractor graphics, int bodyTop)
    {
        int x = left + PAD + 10;
        int y = bodyTop + 10;
        graphics.text(font, "Decks", x, y, 0xFFF4D089, true);
        y += 16;

        if(!EditorState.isSynced())
        {
            graphics.text(font, "Waiting for the server...", x, y, 0xFF7A8090, true);
            return;
        }

        java.util.List<de.cas_ual_ty.dueldimension.duel.profile.DeckList> decks =
            EditorState.ownDecks();
        if(decks.isEmpty())
        {
            graphics.text(font, "No decks yet.", x, y, 0xFF7A8090, true);
            return;
        }

        String active = EditorState.profile().activeDeck();
        int rows = Math.min(decks.size(), (HEIGHT - (PAD + TAB_H + 8) - 70) / 12);
        for(int i = 0; i < rows; i++)
        {
            de.cas_ual_ty.dueldimension.duel.profile.DeckList deck = decks.get(i);
            boolean on = deck.name().equals(active);
            graphics.text(font, (on ? "▸ " : "") + deck.name()
                    + "  (" + deck.main().size() + ")",
                x, y, on ? 0xFFF4D089 : 0xFFE6EAF2, true);
            y += 12;
        }
        if(decks.size() > rows)
        {
            graphics.text(font, "...and " + (decks.size() - rows) + " more",
                x, y, 0xFF7A8090, true);
            y += 12;
        }
        graphics.text(font, "Editing needs the deck editor screen.",
            x, y + 4, 0xFF7A8090, true);
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
