package de.cas_ual_ty.dueldimension.clientutil.hub;

import de.cas_ual_ty.dueldimension.clientutil.CardBacks;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The duel hub: decks, profile, outfits, settings.
 * <p>
 * All four tabs are live. {@link EditorState} holds what the server sent, so
 * the collection, the deck list and the active deck are the player's own; the
 * wardrobe draws real figures wearing the outfits; and Settings carries the mat
 * colour and the duel music.
 * <p>
 * Deck management is the Forge screen's, whole: use, rename, duplicate,
 * publish-as-recipe and delete (behind its confirmation), plus the recipe
 * list in its three columns. The renames of the piece parts are the port's
 * usual ones -- the extractor for the pose stack, an event object per mouse
 * or key event -- and nothing else moved.
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
     * One card-back tile in the settings tab, and the art inside it.
     * <p>
     * 48x70 is the printed card exactly -- 480/700, which is
     * {@code DuelTextures.CARD_ASPECT} -- so a back is never stretched to fit
     * its tile. The art sits inside the tile's own 3px nine-slice frame, at 42
     * wide and the 61 tall that keeps the same ratio.
     */
    private static final int BACK_W = 48;
    private static final int BACK_H = 70;
    private static final int BACK_ART_W = 42;
    private static final int BACK_ART_H = 61;

    /**
     * Tile pitch, and how many fit in the strip beside the mat preview. Two
     * today; a further back is an entry rather than more width, which is what
     * the scroll arm in {@link #mouseScrolled} is for.
     * <p>
     * Sleeves do not appear here. They are bought, server-authoritative and
     * chosen per deck, so they are picked from the deck editor
     * ({@code SleevePickerScreen}), not from this strip of free client-local
     * card backs.
     */
    private static final int BACK_PITCH = 54;
    private static final int BACK_TILES = 7;

    /**
     * The four sections, with what each is still waiting on. The order is the
     * Forge build's, so the strip reads the same.
     */
    private enum Section
    {
        PROFILE("Profile", ""),
        DECKS("Decks", ""),
        OUTFIT("Outfit", ""),
        SETTINGS("Settings", "");

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

    /** The two halves of the Decks tab: the player's decks, and recipes. */
    /**
     * The Settings tab's own sub-sections.
     * <p>
     * Three unrelated preferences were sharing one body and crowding each other
     * out — the mat preview had been shrunk to make room for the card backs, and
     * the backs were down to two visible tiles. Each of these now owns the whole
     * body instead. Same shape as {@link DeckView}, deliberately: the Decks tab
     * already introduced sub-tabs and a second idiom would only be a second
     * thing to learn.
     */
    private enum SettingsView
    {
        MAT("Duel Mat"),
        CARDS("Cards"),
        AUDIO("Audio");

        private final String label;

        SettingsView(String label)
        {
            this.label = label;
        }
    }

    private SettingsView settingsView = SettingsView.MAT;

    private enum DeckView
    {
        DECKS("Decks"),
        RECIPES("Recipes");

        private final String label;

        DeckView(String label)
        {
            this.label = label;
        }
    }

    /** Static like the section used to be on Forge: reopening remembers. */
    private static DeckView deckView = DeckView.DECKS;

    /** First deck row shown, when there are more decks than fit. */
    private int deckScroll;

    /** One scroll position per recipe column. */
    private final int[] recipeScroll = new int[3];

    /** Height of one deck row. */
    private static final int ROW_H = 20;

    /** Gap between the three recipe columns, and the scrollbar's lane. */
    private static final int COLUMN_GAP = 6;
    private static final int BAR_W = 6;
    private static final int RECIPE_INSET = 4;

    /** The deck being renamed, held by identity rather than by index. */
    private de.cas_ual_ty.dueldimension.duel.profile.DeckList renaming;
    private net.minecraft.client.gui.components.EditBox renameField;

    /**
     * The deck a Delete press is waiting on confirmation for, if any.
     * <p>
     * Held rather than acted on: deleting is the one row action that cannot be
     * undone, and it sits between Duplicate and the edge of the panel.
     */
    private de.cas_ual_ty.dueldimension.duel.profile.DeckList confirmDelete;

    /** First tile shown, when there are more outfits than fit across. */
    private int outfitScroll;

    /** First tile shown, when there are more card backs than fit across. */
    private int backScroll;

    /** What went wrong with the last under-skin import, shown under the row. */
    private String notice = "";

    /** The mat colour wheel, built only while the settings tab is open. */
    private MatColourPicker matPicker;

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

        int bodyTop = top + PAD + TAB_H + 8;
        if(section == Section.DECKS && EditorState.isSynced())
        {
            int viewX = left + PAD + 4;
            for(DeckView candidate : DeckView.values())
            {
                DeckView targetView = candidate;
                addRenderableWidget(new HubWidgets.TabButton(viewX, bodyTop + 3, 68, 16,
                    Component.literal(candidate.label), () -> deckView == targetView, pressed ->
                {
                    deckView = targetView;
                    cancelRename();
                    deckScroll = 0;
                    notice = "";
                    rebuild();
                }));
                viewX += 70;
            }
            if(confirmDelete != null)
            {
                // Its two answers are the only widgets, so a click cannot reach
                // the row it is asking about -- or the four other rows near it.
                buildDeleteConfirm();
                return;
            }
            if(deckView == DeckView.DECKS)
            {
                buildDeckRows(bodyTop + 22);
                addRenderableWidget(new HubWidgets.TextureButton(left + PAD + 6, top + HEIGHT - 32,
                    96, 20, Component.literal("New Deck"), pressed ->
                {
                    EditorState.newDeck();
                    cancelRename();
                    notice = "";
                    rebuild();
                }));
            }
            else
            {
                buildRecipeRows(bodyTop + 22);
            }
        }

        addRenderableWidget(new HubWidgets.TextureButton(left + WIDTH - PAD - 80,
            top + HEIGHT - 32, 80, 20, Component.literal("Close"), pressed -> onClose()));

        if(section == Section.OUTFIT && EditorState.isSynced())
        {
            buildOutfitRows(top + PAD + TAB_H + 8);
        }

        // The mat picker is rebuilt with the tab rather than kept, so it always
        // opens showing the colour actually in force. Nulled on every other tab
        // because render and the mouse handlers all key off it being non-null.
        matPicker = null;
        if(section == Section.SETTINGS)
        {
            int viewX = left + PAD + 4;
            for(SettingsView candidate : SettingsView.values())
            {
                SettingsView targetView = candidate;
                addRenderableWidget(new HubWidgets.TabButton(viewX, bodyTop + 3, 68, 16,
                    Component.literal(candidate.label), () -> settingsView == targetView,
                    pressed ->
                {
                    settingsView = targetView;
                    backScroll = 0;
                    rebuild();
                }));
                viewX += 70;
            }
        }
        if(section == Section.SETTINGS && settingsView == SettingsView.MAT)
        {
            matPicker = new MatColourPicker(left + PAD + 6, bodyTop + 48, 120, 14,
                de.cas_ual_ty.dueldimension.clientutil.DuelClientState.matColour());
            // Apply and Reset belong to the mat alone -- a colour is dragged and
            // needs settling on. They used to be built for the whole tab, so the
            // music and card-back views offered an Apply that applied nothing.
            addRenderableWidget(new HubWidgets.TextureButton(left + PAD + 6, top + HEIGHT - 32,
                96, 20, Component.literal("Apply"), pressed -> applyMat()));
            addRenderableWidget(new HubWidgets.TextureButton(left + PAD + 108, top + HEIGHT - 32,
                96, 20, Component.literal("Reset"), pressed ->
            {
                matPicker.setColour(de.cas_ual_ty.dueldimension.clientutil.DuelClientState.DEFAULT_MAT_COLOUR);
                applyMat();
            }));
        }
        if(section == Section.SETTINGS && settingsView == SettingsView.AUDIO)
        {

            // ---- duel music, under the mat ----
            // Both take effect immediately rather than waiting on Apply: Apply
            // is the mat's, because a colour is dragged and needs a moment to
            // settle on, while these are single choices that are their own
            // confirmation. Changing the track mid-duel swaps it there and then.
            de.cas_ual_ty.dueldimension.clientutil.DuelMusic.Track current =
                de.cas_ual_ty.dueldimension.clientutil.DuelMusic.track();
            HubWidgets.TextureButton trackButton = new HubWidgets.TextureButton(
                left + PAD + 6, bodyTop + 48, 130, 18,
                Component.literal(current.label()), pressed ->
            {
                java.util.List<de.cas_ual_ty.dueldimension.clientutil.DuelMusic.Track> all =
                    de.cas_ual_ty.dueldimension.clientutil.DuelMusic.TRACKS;
                de.cas_ual_ty.dueldimension.clientutil.DuelMusic.setTrack(
                    all.get((all.indexOf(de.cas_ual_ty.dueldimension.clientutil.DuelMusic.track())
                        + 1) % all.size()));
                rebuild();
            });
            // One track is not a choice, so the button says so rather than
            // looking pressable and doing nothing.
            trackButton.active =
                de.cas_ual_ty.dueldimension.clientutil.DuelMusic.TRACKS.size() > 1;
            trackButton.setTooltipLines(trackButton.active
                ? java.util.List.of("The music a duel is played to")
                : java.util.List.of("The music a duel is played to",
                    "Only one track is installed"));
            addRenderableWidget(trackButton);

            boolean quiet = de.cas_ual_ty.dueldimension.clientutil.DuelMusic.muted();
            HubWidgets.TextureButton muteButton = new HubWidgets.TextureButton(
                left + PAD + 140, bodyTop + 48, 68, 18,
                Component.literal(quiet ? "Muted" : "On"), pressed ->
            {
                de.cas_ual_ty.dueldimension.clientutil.DuelMusic.toggleMuted();
                rebuild();
            });
            muteButton.setLabelColour(quiet ? 0xFF8A93A3 : 0xFFF4D089);
            // If the game's own sliders are down, "On" is a lie by omission --
            // so it says which slider, rather than leaving the player to
            // wonder why an unmuted track makes no sound.
            boolean silenced =
                de.cas_ual_ty.dueldimension.clientutil.DuelMusic.silencedByGameVolume();
            muteButton.setTooltipLines(quiet
                ? java.util.List.of("Duels are played in silence",
                    "The same switch as the one in a duel")
                : silenced
                    ? java.util.List.of("Music plays during a duel",
                        "Silenced by Options > Music & Sounds",
                        "Raise Jukebox/Note Blocks to hear it")
                    : java.util.List.of("Music plays during a duel",
                        "The same switch as the one in a duel"));
            addRenderableWidget(muteButton);
        }
        if(section == Section.SETTINGS && settingsView == SettingsView.CARDS)
        {
            // On click, for the reason the two music buttons are: a back is a
            // single named choice and is its own confirmation. Apply belongs to
            // the mat, whose colour is dragged and needs settling on.
            buildCardBackTiles(bodyTop);
        }

    }

    /**
     * The card-back chooser: each back drawn as itself, the one in use marked.
     * <p>
     * The thing being chosen IS art, so the tile is the art -- the same
     * reasoning the wardrobe uses for outfits, where a list of names would tell
     * a player nothing about what they are picking.
     * <p>
     * Each tile draws its back from inside its own {@code extractContents}
     * rather than having {@link #settingsPanel} draw it. That is not a style
     * choice: settingsPanel runs BEFORE {@code super.extractRenderState}, so a
     * back drawn there would be painted over by the button covering the same
     * rectangle -- the exact trap the wardrobe works around by drawing after
     * super. A widget that draws its own art cannot be covered by itself.
     */
    private void buildCardBackTiles(int bodyTop)
    {
        java.util.List<CardBacks.Back> backs = CardBacks.ALL;
        backScroll = Math.max(0, Math.min(backScroll, Math.max(0, backs.size() - BACK_TILES)));
        for(int slot = 0; slot < BACK_TILES && slot + backScroll < backs.size(); slot++)
        {
            CardBacks.Back back = backs.get(slot + backScroll);
            CardBackTile tile = new CardBackTile(backStripX() + slot * BACK_PITCH, bodyTop + 48,
                back, pressed -> CardBacks.set(back));
            tile.setTooltipLines(java.util.List.of(back.label(),
                "Drawn on every face-down card"));
            addRenderableWidget(tile);
        }
    }

    /**
     * Where the card-back strip begins.
     * <p>
     * Five pixels clear of the mat preview's frame, which ends at
     * {@code left + PAD + 321} now the preview is 150 wide, and 12 clear of the
     * panel's right border. Two 48-wide tiles at a 54 pitch reach
     * {@code left + PAD + 428}.
     */
    private int backStripX()
    {
        return left + PAD + 6;
    }

    /**
     * One card back, drawn as itself on a button surface.
     * <p>
     * It asks which back is in use at draw time rather than being told when it
     * was built, and that buys two things: choosing a back does not need the
     * tab rebuilt, and a mat colour the player has dragged but not yet applied
     * survives the click -- {@code rebuild()} reseeds the picker from the
     * colour actually in force and would otherwise discard it.
     */
    private static class CardBackTile extends HubWidgets.TextureButton
    {
        private final CardBacks.Back back;

        private CardBackTile(int x, int y, CardBacks.Back back, OnPress onPress)
        {
            // No label: the name is drawn under the tile by settingsPanel, the
            // way the wardrobe names its figures, so nothing is written across
            // the art.
            super(x, y, BACK_W, BACK_H, Component.literal(""), onPress);
            this.back = back;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
            float partialTick)
        {
            // The chosen one wears the disabled surface, which is how the
            // wardrobe marks the outfit being worn. It stays clickable rather
            // than being switched inactive, because active is fixed at build
            // time and this tile is never rebuilt; picking the back already in
            // use is a no-op in CardBacks.set anyway.
            boolean on = CardBacks.back() == back;
            int row = on ? NineSlice.DISABLED
                : isHoveredOrFocused() ? NineSlice.HOVER : NineSlice.IDLE;
            NineSlice.draw(graphics, HubTextures.BUTTON, getX(), getY(), getWidth(), getHeight(),
                row, 3);
            // Inside the frame, and never dimmed with it: the art is the whole
            // reason the tile exists.
            NineSlice.image(graphics, back.texture(), getX() + 3, getY() + 4,
                BACK_ART_W, BACK_ART_H);
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
            case SETTINGS -> settingsPanel(graphics, bodyTop);
            // OUTFIT draws nothing here on purpose: its wardrobe goes down
            // AFTER the widgets, below. Without this arm it fell to the default
            // and painted "Not ported yet:" under the tiles, where the corner
            // of it showed between them.
            case OUTFIT -> { }
            default -> waiting(graphics, bodyTop);
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        // The wardrobe goes AFTER the widgets, and that is not a detail. Each
        // outfit's tile IS a button covering the whole cell, so a figure drawn
        // before it is painted over by it -- which is exactly what happened:
        // the previews were being drawn and then hidden. Forge ordered it the
        // same way for the same reason, calling renderOutfit after super.render
        // while Profile and Decks went before.
        if(section == Section.OUTFIT)
        {
            outfitPanel(graphics, bodyTop);
        }

        extractTooltip(graphics, mouseX, mouseY);
    }

    private void settingsPanel(GuiGraphicsExtractor graphics, int bodyTop)
    {
        // Everything below the sub-tab row, which occupies bodyTop + 3 to + 19.
        int headingY = bodyTop + 32;
        if(settingsView == SettingsView.MAT && matPicker != null)
        {
            graphics.text(font, "Duel Mat", left + PAD + 6, headingY, 0xFFF4D089, true);
            matPicker.render(graphics);
            // The preview is the real mat texture under the chosen tint, so
            // what is shown here is exactly what reaches the table. Back to
            // 210x92 now that the card backs are not sharing this body --
            // custom.png is 1024x448 and 2.28 keeps its 2.29.
            matPicker.renderPreview(graphics, left + PAD + 168, bodyTop + 48, 210, 92);
            String hex = String.format("#%06X", matPicker.colour());
            graphics.text(font, hex, left + PAD + 168, bodyTop + 146, 0xFFC2C9D6, true);
            return;
        }
        if(settingsView == SettingsView.AUDIO)
        {
            // Drawn here rather than built as a widget because it is a label,
            // and this panel draws its own.
            graphics.text(font, "Duel Music", left + PAD + 6, headingY, 0xFFF4D089, true);
            return;
        }

        // ---- card back ----
        // Only the words: each tile paints its own back, because this runs
        // before the widgets and would otherwise be covered by them. The names
        // are safe here -- they sit below the tiles rather than inside them.
        graphics.text(font, "Card Back", backStripX(), headingY, 0xFFF4D089, true);
        java.util.List<CardBacks.Back> backs = CardBacks.ALL;
        for(int slot = 0; slot < BACK_TILES && slot + backScroll < backs.size(); slot++)
        {
            CardBacks.Back back = backs.get(slot + backScroll);
            boolean on = CardBacks.back() == back;
            String name = font.plainSubstrByWidth(back.label(), BACK_PITCH - 4);
            graphics.text(font, name,
                backStripX() + slot * BACK_PITCH + (BACK_W - font.width(name)) / 2,
                bodyTop + 48 + BACK_H + 4, on ? 0xFFF4D089 : 0xFFC2C9D6, true);
        }
        if(backs.size() > BACK_TILES)
        {
            graphics.text(font, (backScroll + 1) + "-"
                    + Math.min(backs.size(), backScroll + BACK_TILES)
                    + " of " + backs.size(),
                backStripX(), bodyTop + 48 + BACK_H + 18, 0xFF7A8090, true);
        }
    }

    private void applyMat()
    {
        if(matPicker == null)
        {
            return;
        }
        de.cas_ual_ty.dueldimension.clientutil.DuelClientState.setMatColour(matPicker.colour());
        // The other duelist draws your mat on their far half, so the colour has
        // to travel; it is a preference, not hidden information.
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
            new de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.SetPlayMat(
                de.cas_ual_ty.dueldimension.clientutil.DuelClientState.matColourId()));
    }

    // The picker is not an AbstractWidget -- it is a wheel and a slider drawn
    // directly -- so the three mouse events reach it by hand, and it gets first
    // refusal ahead of the widgets so a drag across the wheel is not stolen.
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event,
        boolean doubled)
    {
        if(matPicker != null && matPicker.mouseClicked(event.x(), event.y()))
        {
            return true;
        }
        if(renameField != null && !renameField.isMouseOver(event.x(), event.y()))
        {
            commitRename();
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event,
        double dragX, double dragY)
    {
        if(matPicker != null && matPicker.mouseDragged(event.x(), event.y()))
        {
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event)
    {
        if(matPicker != null)
        {
            matPicker.mouseReleased();
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event)
    {
        if(confirmDelete != null && event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE)
        {
            // Escape answers the question rather than leaving the hub, which is
            // the safe reading of it while a delete is waiting.
            confirmDelete = null;
            rebuild();
            return true;
        }
        if(renameField != null && renameField.isFocused())
        {
            if(event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
                || event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER)
            {
                commitRename();
                return true;
            }
            if(event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE)
            {
                cancelRename();
                rebuild();
                return true;
            }
            // The field is a real widget here, so its own editing keys reach it
            // through super rather than needing the hand-forwarding Forge did.
        }
        return super.keyPressed(event);
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

    /** The deck panel's non-widget half: counters, notice, and the dialogs. */
    private void deckPanel(GuiGraphicsExtractor graphics, int bodyTop)
    {
        if(!EditorState.isSynced())
        {
            graphics.text(font, "Waiting for the server...", left + PAD + 10, bodyTop + 10,
                0xFF7A8090, true);
            return;
        }
        if(confirmDelete != null)
        {
            renderDeleteConfirm(graphics);
            return;
        }
        if(deckView == DeckView.RECIPES)
        {
            renderRecipeHeadings(graphics, bodyTop + 22);
            return;
        }
        int count = EditorState.ownDecks().size();
        int visible = deckRowsVisible();
        if(count > visible)
        {
            graphics.text(font, (deckScroll + 1) + "-"
                + Math.min(count, deckScroll + visible) + " of " + count,
                left + PAD + 4, top + HEIGHT - 28, 0xFF7A8090, true);
        }
        if(!notice.isEmpty())
        {
            graphics.text(font, notice, left + PAD + 110, top + HEIGHT - 26, 0xFFFF8A80, true);
        }
    }

    private int deckRowsVisible()
    {
        int bodyHeight = HEIGHT - (PAD + TAB_H + 8) - 40;
        return Math.max(1, (bodyHeight - 26) / ROW_H);
    }

    private int recipeRowsVisible()
    {
        // One row shorter than the deck list's: the heading takes the top of
        // each column rather than a row of the single flattened list.
        return Math.max(1, deckRowsVisible() - 1);
    }

    private int recipeColumnW()
    {
        return (WIDTH - PAD * 2 - 8 - COLUMN_GAP * 2) / 3;
    }

    private int recipeColumnX(int column)
    {
        return left + PAD + 4 + column * (recipeColumnW() + COLUMN_GAP);
    }

    private int recipeColumnAt(double mouseX)
    {
        for(int column = 0; column < recipeScroll.length; column++)
        {
            int x = recipeColumnX(column);
            if(mouseX >= x && mouseX < x + recipeColumnW())
            {
                return column;
            }
        }
        return -1;
    }

    /** One group of recipes, with the heading it is listed under. */
    private record RecipeGroup(String heading, java.util.List<
        de.cas_ual_ty.dueldimension.duel.profile.DeckList> decks)
    {
    }

    private java.util.List<RecipeGroup> recipeGroups()
    {
        EditorState.decks();
        return java.util.List.of(
            new RecipeGroup("Saved", EditorState.profile().savedRecipes()),
            new RecipeGroup("Starter Decks", EditorState.profile().starterDecks()),
            new RecipeGroup("Structure Decks", EditorState.profile().structureDecks()));
    }

    private void buildDeckRows(int bodyTop)
    {
        java.util.List<de.cas_ual_ty.dueldimension.duel.profile.DeckList> decks =
            EditorState.ownDecks();
        int visible = deckRowsVisible();
        deckScroll = Math.max(0, Math.min(deckScroll, Math.max(0, decks.size() - visible)));

        int rowX = left + PAD + 4;
        int rowW = WIDTH - PAD * 2 - 8;
        int useW = 32;
        int renameW = 52;
        int duplicateW = 76;
        int recipeW = 50;
        int deleteW = 46;
        int gap = 3;
        int actionsW = useW + renameW + duplicateW + recipeW + deleteW + gap * 5;
        int nameW = Math.max(60, rowW - actionsW);

        for(int row = 0; row < visible; row++)
        {
            int index = row + deckScroll;
            if(index >= decks.size())
            {
                break;
            }
            de.cas_ual_ty.dueldimension.duel.profile.DeckList deck = decks.get(index);
            int y = bodyTop + 4 + row * ROW_H;
            int x = rowX;

            if(deck == renaming && renameField != null)
            {
                renameField.setX(x + 2);
                renameField.setY(y + 3);
                renameField.setWidth(nameW - 6);
                // Renderable, not just a listener: an EditBox is a widget and
                // extracts itself, where Forge drew it by hand in renderDecks.
                addRenderableWidget(renameField);
                setFocused(renameField);
                renameField.setFocused(true);
            }
            else
            {
                // Active deck is marked, so "Use" has visible consequence.
                boolean active = deck.name().equals(EditorState.profile().activeDeck());
                String label = (active ? "▸ " : "") + deck.name()
                    + "  (" + deck.main().size() + ")";
                int target = index;
                HubWidgets.TextureButton name = new HubWidgets.TextureButton(x, y, nameW,
                    ROW_H - 2, Component.literal(label), pressed ->
                {
                    EditorState.select(EditorState.indexOf(decks.get(target)));
                    if(minecraft != null)
                    {
                        minecraft.gui.setScreen(new DeckEditorScreen(this));
                    }
                });
                // The player's own decks are the ones that end up short or full
                // of cards they no longer own, so this list needs the warning
                // more than the recipe list does -- and only had it there.
                markUnusable(name, deck);
                addRenderableWidget(name);
            }
            x += nameW + gap;

            int useIndex = index;
            HubWidgets.TextureButton use = new HubWidgets.TextureButton(x, y, useW, ROW_H - 2,
                Component.literal("Use"), pressed ->
            {
                // Told to the server, which is what actually decides the deck
                // a duel is played with.
                EditorState.setActiveDeck(decks.get(useIndex).name());
                notice = "";
                rebuild();
            });
            // Saving an unfinished deck is fine -- building one is a process --
            // but it cannot be USED until it is legal, so Use reports that by
            // being disabled rather than by failing at the duel.
            boolean legal = de.cas_ual_ty.dueldimension.duel.profile.DeckLimits
                .validate(deck, EditorState.trunk(), EditorState.banlist(),
                    EditorState.freeMode()).isEmpty();
            use.active = legal && !deck.name().equals(EditorState.profile().activeDeck());
            addRenderableWidget(use);
            x += useW + gap;

            int renameIndex = index;
            addRenderableWidget(new HubWidgets.TextureButton(x, y, renameW, ROW_H - 2,
                Component.literal("Rename"), pressed ->
                    startRename(EditorState.indexOf(decks.get(renameIndex)))));
            x += renameW + gap;

            int duplicateIndex = index;
            addRenderableWidget(new HubWidgets.TextureButton(x, y, duplicateW, ROW_H - 2,
                Component.literal("Duplicate As"), pressed ->
            {
                // Duplicating drops straight into renaming the copy: the point
                // of "as" is that the copy gets its own name.
                EditorState.duplicate(EditorState.indexOf(decks.get(duplicateIndex)));
                startRename(EditorState.currentIndex());
            }));
            x += duplicateW + gap;

            // Offering a deck as a recipe is now something the player says.
            // Making a deck used to publish it automatically, which turned the
            // recipe list into a second copy of the deck list.
            HubWidgets.TextureButton recipe = new HubWidgets.TextureButton(x, y, recipeW,
                ROW_H - 2, Component.literal("Recipe"), pressed ->
            {
                EditorState.publish(deck, !deck.published());
                rebuild();
            });
            recipe.setLabelColour(deck.published() ? 0xFFF4D089 : 0xFF8A93A3);
            recipe.setTooltipLines(deck.published()
                ? java.util.List.of("Shown in the recipe list", "Click to withdraw it")
                : java.util.List.of("Not offered as a recipe", "Click to add it to the list"));
            addRenderableWidget(recipe);
            x += recipeW + gap;

            int deleteIndex = index;
            HubWidgets.TextureButton delete = new HubWidgets.TextureButton(x, y, deleteW, ROW_H - 2,
                Component.literal("Delete"), pressed ->
            {
                // Asked first. A deck is a long evening's work and the button
                // sits next to four that are not destructive.
                confirmDelete = decks.get(deleteIndex);
                notice = "";
                rebuild();
            });
            // A granted structure deck is the record of what was opened, so it
            // reports that by being disabled rather than failing when pressed.
            delete.active = deck.origin()
                != de.cas_ual_ty.dueldimension.duel.profile.DeckList.Origin.STRUCTURE;
            addRenderableWidget(delete);
        }
    }

    /**
     * The recipe list: three lists side by side, one per source.
     * <p>
     * Flattened into a single scrolling column, the starter decks sat below
     * however many saved recipes the player had, so finding one meant scrolling
     * past the others. Three columns each scroll on their own and each say how
     * far down they are.
     * <p>
     * A recipe's action is to be used, which takes a copy of it: the original
     * is the record of what was saved or opened, and editing it in place would
     * destroy that. Editing a saved recipe is done from the deck list, where
     * the deck it was published from lives.
     */
    private void buildRecipeRows(int bodyTop)
    {
        java.util.List<RecipeGroup> groups = recipeGroups();
        int visible = recipeRowsVisible();
        int columnW = recipeColumnW();
        int nameW = columnW - RECIPE_INSET * 2 - BAR_W - 2;

        for(int column = 0; column < groups.size() && column < recipeScroll.length; column++)
        {
            java.util.List<de.cas_ual_ty.dueldimension.duel.profile.DeckList> decks =
                groups.get(column).decks();
            recipeScroll[column] = Math.max(0, Math.min(recipeScroll[column],
                Math.max(0, decks.size() - visible)));
            int x = recipeColumnX(column) + RECIPE_INSET;

            for(int row = 0; row < visible; row++)
            {
                int index = row + recipeScroll[column];
                if(index >= decks.size())
                {
                    break;
                }
                de.cas_ual_ty.dueldimension.duel.profile.DeckList recipe = decks.get(index);
                int y = bodyTop + 14 + row * ROW_H;
                String shownName = recipeDisplayName(recipe);
                HubWidgets.TextureButton recipeName = new HubWidgets.TextureButton(x, y, nameW,
                    ROW_H - 2, Component.literal(font.plainSubstrByWidth(
                        shownName + "  (" + recipe.main().size() + ")", nameW - 8)),
                    pressed -> useRecipe(recipe));
                markUnusable(recipeName, recipe);
                if(recipeName.tooltipLines().isEmpty())
                {
                    recipeName.setTooltipLines(java.util.List.of(shownName,
                        "Makes a new deck from this recipe"));
                }
                addRenderableWidget(recipeName);
            }
        }
    }

    /** Product category belongs in the column heading, not every row. */
    private static String recipeDisplayName(
        de.cas_ual_ty.dueldimension.duel.profile.DeckList recipe)
    {
        String name = recipe.name();
        return switch(recipe.origin())
        {
            case STARTER -> withoutProductWords(name, "Starter Deck");
            case STRUCTURE -> withoutProductWords(name, "Structure Deck");
            default -> name;
        };
    }

    private static String withoutProductWords(String name, String product)
    {
        String shortened = name;
        if(shortened.startsWith(product))
        {
            shortened = shortened.substring(product.length());
        }
        if(shortened.endsWith(product))
        {
            shortened = shortened.substring(0, shortened.length() - product.length());
        }
        shortened = shortened.strip();
        if(shortened.startsWith(":"))
        {
            shortened = shortened.substring(1).strip();
        }
        return shortened.isEmpty() ? name : shortened;
    }

    /**
     * Why this deck cannot be duelled with, empty if it can.
     * <p>
     * Shared by every list that shows a deck. Two lists each deciding this for
     * themselves is how one of them ended up saying nothing.
     */
    private static java.util.List<String> unusableReasons(
        de.cas_ual_ty.dueldimension.duel.profile.DeckList deck)
    {
        java.util.List<String> why = new java.util.ArrayList<>();
        // Size is not something free mode relaxes: a deck under forty is
        // illegal however generous the collection is.
        if(deck.main().size() < de.cas_ual_ty.dueldimension.duel.match.Banlist.MAIN_MIN)
        {
            why.add("A deck requires 40 or more cards to use");
        }
        java.util.List<Integer> missing = EditorState.freeMode()
            ? java.util.List.of() : EditorState.missingFrom(deck);
        if(!missing.isEmpty())
        {
            why.add("Cannot be duelled with: " + missing.size()
                + (missing.size() == 1 ? " card" : " cards") + " you do not own");
            why.add("Earn them, take them out, or turn free mode on");
        }
        return why;
    }

    /** Reddens a deck row and says why, or leaves it alone. */
    private static void markUnusable(HubWidgets.TextureButton row,
        de.cas_ual_ty.dueldimension.duel.profile.DeckList deck)
    {
        java.util.List<String> why = unusableReasons(deck);
        if(!why.isEmpty())
        {
            row.setLabelColour(0xFFFF6B6B);
            row.setTooltipLines(why);
        }
    }

    /** Use: a new deck from the recipe, then name it. */
    private void useRecipe(de.cas_ual_ty.dueldimension.duel.profile.DeckList recipe)
    {
        EditorState.useRecipe(EditorState.indexOf(recipe));
        deckView = DeckView.DECKS;
        deckScroll = 0;
        notice = "";
        startRename(EditorState.currentIndex());
    }

    private void startRename(int index)
    {
        java.util.List<de.cas_ual_ty.dueldimension.duel.profile.DeckList> all = EditorState.decks();
        startRename(all.get(Math.max(0, Math.min(index, all.size() - 1))));
    }

    /**
     * Turns that deck's name button into an editable field until it is
     * confirmed. Deck management is in the Decks view, so renaming switches
     * there rather than leaving the field somewhere the player cannot see it.
     */
    private void startRename(de.cas_ual_ty.dueldimension.duel.profile.DeckList deck)
    {
        deckView = DeckView.DECKS;
        renaming = deck;
        renameField = new net.minecraft.client.gui.components.EditBox(font, 0, 0, 100, 14,
            Component.literal("Deck name"));
        renameField.setMaxLength(40);
        renameField.setValue(deck.name());
        // The boolean is "select to the cursor", grown since 1.19.2; false is
        // the plain jump-to-end the Forge call was.
        renameField.moveCursorToEnd(false);
        // Scrolled to, or a rename on an off-screen row would edit something
        // the player cannot see.
        int row = EditorState.ownDecks().indexOf(deck);
        if(row >= 0)
        {
            int visible = deckRowsVisible();
            if(row < deckScroll || row >= deckScroll + visible)
            {
                deckScroll = Math.max(0, row - visible / 2);
            }
        }
        rebuild();
    }

    private void cancelRename()
    {
        renaming = null;
        renameField = null;
    }

    /** Applies a pending rename. Commits on Enter or on clicking away. */
    private void commitRename()
    {
        if(renaming == null || renameField == null)
        {
            return;
        }
        EditorState.select(EditorState.indexOf(renaming));
        if(!EditorState.rename(renameField.getValue()))
        {
            notice = "That name is already used";
        }
        cancelRename();
        rebuild();
    }

    /** The two answers to the delete question; the box is drawn by deckPanel. */
    private void buildDeleteConfirm()
    {
        if(confirmDelete == null)
        {
            return;
        }
        int boxW = 240;
        int boxH = 74;
        int boxX = left + (WIDTH - boxW) / 2;
        int boxY = top + (HEIGHT - boxH) / 2;
        de.cas_ual_ty.dueldimension.duel.profile.DeckList doomed = confirmDelete;
        addRenderableWidget(new HubWidgets.TextureButton(boxX + 12, boxY + boxH - 26, 100, 20,
            Component.literal("Delete"), pressed ->
        {
            confirmDelete = null;
            EditorState.select(EditorState.indexOf(doomed));
            String error = EditorState.deleteCurrent();
            notice = error == null ? "" : error;
            cancelRename();
            rebuild();
        }));
        addRenderableWidget(new HubWidgets.TextureButton(boxX + boxW - 112, boxY + boxH - 26,
            100, 20, Component.literal("Cancel"), pressed ->
        {
            confirmDelete = null;
            rebuild();
        }));
    }

    private void renderDeleteConfirm(GuiGraphicsExtractor graphics)
    {
        if(confirmDelete == null)
        {
            return;
        }
        int boxW = 240;
        int boxH = 74;
        int boxX = left + (WIDTH - boxW) / 2;
        int boxY = top + (HEIGHT - boxH) / 2;
        // Over the rows it is asking about, and dark enough that the list
        // behind it cannot be mistaken for something still clickable. The two
        // answer buttons are widgets, described after this, so they sit on top.
        graphics.fill(left, top, left + WIDTH, top + HEIGHT, 0xC0000000);
        NineSlice.draw(graphics, HubTextures.PANEL, boxX, boxY, boxW, boxH);
        graphics.text(font, "Delete this deck?", boxX + 12, boxY + 10, 0xFFF4D089, true);
        String named = "\"" + confirmDelete.name() + "\"  ("
            + confirmDelete.main().size() + " cards)";
        graphics.text(font, font.plainSubstrByWidth(named, boxW - 24),
            boxX + 12, boxY + 24, 0xFFE6EAF2, true);
        graphics.text(font, "This cannot be undone.", boxX + 12, boxY + 36, 0xFFFF6B6B, true);
    }

    private void renderRecipeHeadings(GuiGraphicsExtractor graphics, int bodyTop)
    {
        java.util.List<RecipeGroup> groups = recipeGroups();
        int visible = recipeRowsVisible();
        int columnW = recipeColumnW();

        for(int column = 0; column < groups.size() && column < recipeScroll.length; column++)
        {
            RecipeGroup group = groups.get(column);
            int x = recipeColumnX(column);
            int bubbleH = 14 + visible * ROW_H + 14;
            NineSlice.draw(graphics, HubTextures.PANEL_INSET,
                x - 2, bodyTop - 2, columnW + 4, bubbleH);
            graphics.text(font, font.plainSubstrByWidth(group.heading(), columnW),
                x + RECIPE_INSET, bodyTop + 2, 0xFFF4D089, true);
            if(group.decks().isEmpty())
            {
                graphics.text(font, "(none)", x + RECIPE_INSET, bodyTop + 18, 0xFF7A8090, true);
                scrollbar(graphics, x + columnW - BAR_W - 2, bodyTop + 14,
                    visible * ROW_H, 0, visible, 0);
                continue;
            }
            scrollbar(graphics, x + columnW - BAR_W - 2, bodyTop + 14, visible * ROW_H,
                group.decks().size(), visible, recipeScroll[column]);
            if(group.decks().size() > visible)
            {
                graphics.text(font, (recipeScroll[column] + 1) + "-"
                        + Math.min(group.decks().size(), recipeScroll[column] + visible)
                        + " of " + group.decks().size(),
                    x + RECIPE_INSET, bodyTop + 14 + visible * ROW_H + 2, 0xFF7A8090, true);
            }
        }
    }

    private void scrollbar(GuiGraphicsExtractor graphics, int x, int y, int height,
        int total, int visible, int offset)
    {
        if(height <= 0)
        {
            return;
        }
        NineSlice.draw(graphics, HubTextures.SCROLLBAR, x, y, 4, height, 0, 2);
        if(total <= 0)
        {
            return;
        }
        int overflow = Math.max(0, total - visible);
        int thumbH = Math.max(12, height * visible / Math.max(1, total));
        thumbH = Math.min(height, thumbH);
        int thumbY = overflow == 0 ? y : y + (height - thumbH) * offset / overflow;
        NineSlice.draw(graphics, HubTextures.SCROLLBAR, x, thumbY, 4, thumbH, 1, 2);
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
        // The card-back strip is a row too, and scrolls the same way -- but only
        // once there are more backs than fit. Taking the notch unconditionally
        // would swallow scrolling on a settings tab that has nothing to scroll.
        if(section == Section.SETTINGS && settingsView == SettingsView.CARDS
            && CardBacks.ALL.size() > BACK_TILES)
        {
            int max = CardBacks.ALL.size() - BACK_TILES;
            int moved = Math.max(0, Math.min(max, backScroll - (int)Math.signum(scrollY)));
            if(moved != backScroll)
            {
                backScroll = moved;
                rebuild();
            }
            return true;
        }
        if(section == Section.DECKS && deckView == DeckView.RECIPES)
        {
            // The column under the cursor, so three lists side by side scroll
            // independently rather than together.
            int column = recipeColumnAt(mouseX);
            if(column < 0)
            {
                return true;
            }
            java.util.List<RecipeGroup> groups = recipeGroups();
            int total = column < groups.size() ? groups.get(column).decks().size() : 0;
            int max = Math.max(0, total - recipeRowsVisible());
            recipeScroll[column] = Math.max(0,
                Math.min(max, recipeScroll[column] - (int)Math.signum(scrollY)));
            rebuild();
            return true;
        }
        if(section == Section.DECKS)
        {
            int max = Math.max(0, EditorState.ownDecks().size() - deckRowsVisible());
            deckScroll = Math.max(0, Math.min(max, deckScroll - (int)Math.signum(scrollY)));
            rebuild();
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
