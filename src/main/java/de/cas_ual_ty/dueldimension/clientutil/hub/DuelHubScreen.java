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
    /** The panel's size, and the tab strip's, straight from the Forge layout. */
    private static final int WIDTH = 460;
    private static final int HEIGHT = 280;
    private static final int PAD = 10;
    private static final int TAB_W = 92;
    private static final int TAB_H = 22;

    /** How wide one wardrobe tile is, and how tall the figure inside it stands. */
    private static final int TILE_W = 78;
    private static final int TILE_H = 118;

    /**
     * The four sections, with what each is still waiting on. The order is the
     * Forge build's, so the strip reads the same.
     */
    private enum Section
    {
        PROFILE("Profile", ""),
        DECKS("Decks", ""),
        OUTFIT("Outfit", ""),
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

    /** First tile shown, when there are more outfits than fit across. */
    private int outfitScroll;

    /** What went wrong with the last under-skin import, shown under the row. */
    private String notice = "";

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

        if(section == Section.OUTFIT && EditorState.isSynced())
        {
            buildOutfitRows(top + PAD + TAB_H + 8);
        }

        // One row per deck, over the names the panel draws. The editor is real
        // now, so a deck is something a player can open rather than only read.
        if(section == Section.DECKS && EditorState.isSynced() && minecraft != null)
        {
            int bodyTop = top + PAD + TAB_H + 8;
            java.util.List<de.cas_ual_ty.dueldimension.duel.profile.DeckList> decks =
                EditorState.ownDecks();
            int rows = Math.min(decks.size(), (HEIGHT - (PAD + TAB_H + 8) - 70) / 12);
            for(int i = 0; i < rows; i++)
            {
                int index = i;
                HubWidgets.TextureButton row = new HubWidgets.TextureButton(left + PAD + 6,
                    bodyTop + 24 + i * 12, WIDTH - PAD * 2 - 12, 12,
                    Component.literal(""), pressed ->
                {
                    EditorState.select(EditorState.indexOf(decks.get(index)));
                    minecraft.setScreenAndShow(new DeckEditorScreen(this));
                });
                addRenderableWidget(row);
            }
        }
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
            case OUTFIT -> outfitPanel(graphics, bodyTop);
            default -> waiting(graphics, bodyTop);
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        extractTooltip(graphics, mouseX, mouseY);
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
        graphics.text(font, "Click a deck to edit it.", x, y + 4, 0xFF7A8090, true);
    }

    private int outfitStripX()
    {
        return left + PAD + 6;
    }

    private int outfitTiles()
    {
        return Math.max(1, (WIDTH - PAD * 2 - 12) / TILE_W);
    }

    private static java.util.List<de.cas_ual_ty.dueldimension.duel.outfit.Outfits.Outfit> outfits()
    {
        return de.cas_ual_ty.dueldimension.duel.outfit.Outfits.ALL;
    }

    /**
     * The wardrobe: a row of figures wearing the clothes, the worn one marked.
     * <p>
     * A list of names tells a player nothing about what they are choosing.
     * These are clothes, and "what does it look like" is the only question
     * being asked, so each one is worn by a turning figure rather than written
     * down.
     * <p>
     * What the player is wearing is the server's to say, so a tile asks for a
     * change and the mark follows the profile that comes back.
     */
    private void buildOutfitRows(int bodyTop)
    {
        int across = outfitTiles();
        outfitScroll = Math.max(0, Math.min(outfitScroll, Math.max(0, outfits().size() - across)));

        String worn = EditorState.profile().outfit();
        for(int slot = 0; slot < across && slot + outfitScroll < outfits().size(); slot++)
        {
            de.cas_ual_ty.dueldimension.duel.outfit.Outfits.Outfit outfit =
                outfits().get(slot + outfitScroll);
            boolean on = outfit.id().equals(worn);
            int x = outfitStripX() + slot * TILE_W;
            // The whole tile is the button, with the figure drawn over it: a
            // player picking clothes aims at the clothes.
            HubWidgets.TextureButton tile = new HubWidgets.TextureButton(x, bodyTop + 22,
                TILE_W - 6, TILE_H, Component.literal(""), pressed ->
            {
                EditorState.wear(outfit.id());
                rebuild();
            });
            tile.active = !on;
            if(!outfit.credit().isEmpty())
            {
                // Attribution where somebody choosing it will actually see it.
                tile.setTooltipLines(java.util.List.of(outfit.name(), outfit.credit()));
            }
            addRenderableWidget(tile);
        }

        // ---- the under-skin editor ----
        // A label and two verbs. It was three lines of explanation and two
        // sentences on a button, which is a lot of screen for "the skin under
        // the clothes"; the tooltips still carry the why for anyone who asks.
        int editorY = bodyTop + 22 + TILE_H + 10;
        int labelW = font.width("Underskin") + 8;
        boolean wearing = !worn.isEmpty();

        HubWidgets.TextureButton load = new HubWidgets.TextureButton(
            outfitStripX() + labelW, editorY, 54, 18,
            Component.literal("Load"), pressed -> importUnderSkin());
        load.active = wearing;
        load.setTooltipLines(wearing
            ? java.util.List.of("Import a 64x64 PNG",
                "Your own skin with whatever pokes out from under this outfit removed")
            : java.util.List.of("Only used under an outfit"));
        addRenderableWidget(load);

        HubWidgets.TextureButton reset = new HubWidgets.TextureButton(
            outfitStripX() + labelW + 58, editorY, 54, 18,
            Component.literal("Reset"), pressed ->
        {
            de.cas_ual_ty.dueldimension.clientutil.UnderSkin.clear();
            notice = "";
            rebuild();
        });
        reset.active = de.cas_ual_ty.dueldimension.clientutil.UnderSkin.present();
        reset.setTooltipLines(java.util.List.of("Back to your real skin"));
        addRenderableWidget(reset);
    }

    /**
     * Asks the system for a PNG.
     * <p>
     * Through LWJGL's file dialog, which Minecraft already ships, so the player
     * picks a file the way they would in any other program. If that is missing
     * -- it is a native library, and a native library can be absent -- the
     * known path is read instead and the player is told where it is.
     */
    private void importUnderSkin()
    {
        java.nio.file.Path chosen;
        try
        {
            org.lwjgl.PointerBuffer filters = org.lwjgl.BufferUtils.createPointerBuffer(1);
            filters.put(org.lwjgl.system.MemoryUtil.memUTF8("*.png"));
            filters.flip();
            String path = org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog(
                "Choose your under-skin (64x64 PNG)", "", filters, "PNG image", false);
            if(path == null)
            {
                return; // cancelled, which is an answer
            }
            chosen = java.nio.file.Path.of(path);
        }
        catch(Throwable unavailable)
        {
            chosen = de.cas_ual_ty.dueldimension.clientutil.UnderSkin.file();
            notice = "No file chooser here; reading " + chosen;
        }
        String refusal = de.cas_ual_ty.dueldimension.clientutil.UnderSkin.importFrom(chosen);
        if(refusal != null)
        {
            notice = refusal;
        }
        rebuild();
    }

    private void outfitPanel(GuiGraphicsExtractor graphics, int bodyTop)
    {
        if(!EditorState.isSynced())
        {
            graphics.text(font, "Waiting for the server...", left + PAD + 10, bodyTop + 10,
                0xFF7A8090, true);
            return;
        }

        int across = outfitTiles();
        String worn = EditorState.profile().outfit();
        // One clock for the whole row, so the figures turn together rather than
        // each starting from whenever its tile happened to be built.
        long time = net.minecraft.util.Util.getMillis();

        for(int slot = 0; slot < across && slot + outfitScroll < outfits().size(); slot++)
        {
            de.cas_ual_ty.dueldimension.duel.outfit.Outfits.Outfit outfit =
                outfits().get(slot + outfitScroll);
            int x = outfitStripX() + slot * TILE_W;
            boolean on = outfit.id().equals(worn);

            // Inside the tile with room left for the name under it, rather
            // than filling the tile and standing on its own label. A GUI figure
            // is placed by the rectangle it stands in now, not by a point and a
            // scale, so the tile's own box is what it is given.
            int figure = TILE_H - 28;
            OutfitPreview.draw(graphics, x, bodyTop + 22 + 8,
                x + TILE_W - 6, bodyTop + 22 + 8 + figure, figure, outfit, time);

            String name = font.plainSubstrByWidth(outfit.name(), TILE_W - 12);
            graphics.text(font, name, x + (TILE_W - 6 - font.width(name)) / 2,
                bodyTop + 22 + TILE_H - 12, on ? 0xFFF4D089 : 0xFFC2C9D6, true);
        }

        if(outfits().size() > across)
        {
            graphics.text(font, (outfitScroll + 1) + "-"
                    + Math.min(outfits().size(), outfitScroll + across)
                    + " of " + outfits().size(),
                outfitStripX(), bodyTop + 10, 0xFF7A8090, true);
        }

        int editorY = bodyTop + 22 + TILE_H + 10;
        graphics.text(font, "Underskin", outfitStripX(), editorY + 5,
            worn.isEmpty() ? 0xFF6E7686 : 0xFFF4D089, true);
        if(!notice.isEmpty())
        {
            graphics.text(font, font.plainSubstrByWidth(notice, WIDTH - PAD * 2 - 12),
                outfitStripX(), editorY + 22, 0xFFFF8A80, true);
        }
    }

    /**
     * A hovered button's own explanation.
     * <p>
     * Forge's widgets carried a tooltip and drew it themselves. Here a tooltip
     * is something the SCREEN sets for the next frame, so the screen has to be
     * the one to ask which widget the mouse is over -- and it has to happen
     * after the widgets are extracted, or a tooltip set for this frame would be
     * covered by a widget described later.
     */
    private void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY)
    {
        for(net.minecraft.client.gui.components.events.GuiEventListener child : children())
        {
            if(!(child instanceof HubWidgets.TextureButton button)
                || !button.isHovered() || button.tooltipLines().isEmpty())
            {
                continue;
            }
            java.util.List<Component> lines = button.tooltipLines().stream()
                .map(line -> (Component)Component.literal(line)).toList();
            graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
            return;
        }
    }

    /**
     * The wardrobe scrolls sideways, because it is a row.
     * <p>
     * One tile per notch rather than a pixel offset: the tiles are wide and
     * there is no partial one to reveal, so a notch either shows a different
     * outfit or does nothing.
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY)
    {
        if(section == Section.OUTFIT)
        {
            int max = Math.max(0, outfits().size() - outfitTiles());
            int moved = Math.max(0, Math.min(max, outfitScroll - (int)Math.signum(scrollY)));
            if(moved != outfitScroll)
            {
                outfitScroll = moved;
                rebuild();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
