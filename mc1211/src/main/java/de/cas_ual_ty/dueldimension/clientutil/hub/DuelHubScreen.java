package de.cas_ual_ty.dueldimension.clientutil.hub;

import de.cas_ual_ty.dueldimension.clientutil.CardBacks;
import de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The duel hub: profile, decks, shop, settings.
 * <p>
 * Every tab is live. {@link EditorState} holds what the server sent, so the
 * collection, the deck list and the active deck are the player's own, and
 * Settings carries the mat colour, the card back and the duel music.
 * <p>
 * A fifth tab, the wardrobe, chose an outfit to be seen in. Outfits are shelved
 * -- see backup/outfit-system -- and it went with them.
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
    /**
     * The panel's AUTHORED size, and now only a ceiling.
     * <p>
     * Fixed, it was larger than the window at small sizes and small GUI scales:
     * the tab strip was cropped off the top and Close off the bottom, with no
     * way to reach either. A panel is as big as the window allows, never more,
     * so the size is measured per init and these two are what it aims for.
     */
    private static final int WIDTH_MAX = 460;
    private static final int HEIGHT_MAX = 280;

    /** This window's panel size: the authored one, shrunk to fit if it must. */
    private int WIDTH = WIDTH_MAX;
    private int HEIGHT = HEIGHT_MAX;
    private static final int PAD = 10;
    /**
     * The authored tab width, and now only a ceiling.
     * <p>
     * Four tabs at 92 fitted the strip exactly; a fifth pushed the last one 36
     * units past the panel's right edge. A tab strip's width is a fact about
     * how many tabs there are, so it is worked out rather than written down --
     * see {@link #tabW()}. Kept as the maximum so three tabs never stretch into
     * banners.
     */
    private static final int TAB_W = 92;
    private static final int TAB_H = 22;
    private static final int TAB_GAP = 4;

    /** How wide one wardrobe tile is, and how tall the figure inside it stands. */

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
        SHOP("Shop", ""),
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

    /**
     * Re-asks the server for this profile. Shown only while shift is held.
     * <p>
     * Kept as a field because its visibility is answered every frame in
     * {@link #extractRenderState} rather than at build time -- shift goes up and
     * down without the screen rebuilding, and rebuilding on a modifier key would
     * drop focus and scroll position.
     */
    private HubWidgets.TextureButton refreshButton;

    /**
     * How the deck list is laid out.
     * <p>
     * A row carries the deck AND everything you can do to it, which is right
     * when you are managing decks and mostly wasted space when you are looking
     * for one. The grid drops the management and shows three times as many.
     */
    private enum DeckLayout
    {
        LIST,
        GRID
    }

    /** Static for the same reason the view above it is: reopening remembers. */
    private static DeckLayout deckLayout = DeckLayout.GRID;

    /** First deck row shown, when there are more decks than fit. */
    private int deckScroll;

    /** One scroll position per recipe column. */
    private final int[] recipeScroll = new int[3];

    /** Height of one deck row. */
    private static final int ROW_H = 20;

    /** Master Duel's 260x232 tile, scaled to six columns inside this panel. */
    private static final int TILE_H = 58;
    private static final int TILE_GAP = 4;
    private static final int GRID_COLUMNS = 6;

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

    /** Right-click target and the tile bounds laid out by the current rebuild. */
    private de.cas_ual_ty.dueldimension.duel.profile.DeckList contextDeck;
    private int contextX;
    private int contextY;
    private record DeckHit(int x, int y, int width, int height,
        de.cas_ual_ty.dueldimension.duel.profile.DeckList deck)
    {
        boolean contains(double mouseX, double mouseY)
        {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }
    private final java.util.List<DeckHit> deckHits = new java.util.ArrayList<>();
    /** A left press becomes a drag only after moving beyond a small threshold. */
    private de.cas_ual_ty.dueldimension.duel.profile.DeckList dragCandidate;
    private de.cas_ual_ty.dueldimension.duel.profile.DeckList draggingDeck;
    private de.cas_ual_ty.dueldimension.duel.profile.DeckList dragTarget;
    private double dragStartX;
    private double dragStartY;
    private static final int CONTEXT_W = 104;
    private static final int CONTEXT_ITEM_H = 18;
    private static final String[] CONTEXT_ACTIONS =
        { "Use", "Rename", "Duplicate", "Delete", "Save Recipe As" };


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
        // Measured before anything is placed, because every position below is
        // derived from these two and from left/top.
        WIDTH = Math.min(WIDTH_MAX, width - PAD * 2);
        HEIGHT = Math.min(HEIGHT_MAX, height - PAD * 2);
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        rebuild();
    }

    private void rebuild()
    {
        contextDeck = null;
        deckHits.clear();
        clearWidgets();
        int tabW = tabW();
        int tabX = left + PAD;
        for(Section candidate : Section.values())
        {
            Section target = candidate;
            addRenderableWidget(new HubWidgets.TabButton(tabX, top + PAD, tabW, TAB_H,
                Component.literal(candidate.label), () -> section == target, pressed ->
            {
                section = target;
                rebuild();
            }));
            tabX += tabW + TAB_GAP;
        }

        int bodyTop = top + PAD + TAB_H + 8;
        if(section == Section.DECKS && EditorState.isSynced())
        {
            int viewX = left + PAD + 4;
            for(DeckView candidate : DeckView.values())
            {
                DeckView targetView = candidate;
                addRenderableWidget(new HubWidgets.TabButton(viewX, top + HEIGHT - 32, 68, 20,
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
                if(deckLayout == DeckLayout.GRID)
                {
                    buildDeckGrid(bodyTop);
                }
                else
                {
                    buildDeckRows(bodyTop);
                }
            }
            else
            {
                buildRecipeRows(bodyTop);
            }
        }

        addRenderableWidget(new HubWidgets.TextureButton(left + WIDTH - PAD - 80,
            top + HEIGHT - 32, 80, 20, Component.literal("Close"), pressed -> onClose()));

        if(section == Section.DECKS)
        {
            // Hidden behind shift because it is a repair tool, not a feature: a
            // deck list that needs refreshing is a bug, and a button offering
            // that to everyone all the time invites it to become the workaround
            // instead of the bug being fixed.
            refreshButton = new HubWidgets.TextureButton(left + WIDTH - PAD - 168,
                top + HEIGHT - 32, 84, 20, Component.literal("Refresh"), pressed ->
                    net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                        new de.cas_ual_ty.dueldimension.net.ProfilePayloads.RefreshProfile()));
            refreshButton.setTooltipLines(java.util.List.of(
                "Ask the server for your decks again.",
                "Use this if the list looks wrong."));
            refreshButton.visible = false;
            addRenderableWidget(refreshButton);
        }
        else
        {
            refreshButton = null;
        }

        if(section == Section.SHOP)
        {
            int shopX = left + PAD + 8;
            int shopY = bodyTop + 44;
            int shopW = (WIDTH - PAD * 2 - 16 - 16) / 3;
            addRenderableWidget(new HubWidgets.TextureButton(shopX, shopY, shopW, 20,
                Component.literal("Cards"), pressed -> openShop(
                    de.cas_ual_ty.dueldimension.shop.DiskShopMessages.RequestShop.CARDS)));
            addRenderableWidget(new HubWidgets.TextureButton(shopX + shopW + 8, shopY, shopW, 20,
                Component.literal("Sleeves"), pressed -> openShop(
                    de.cas_ual_ty.dueldimension.shop.DiskShopMessages.RequestShop.SLEEVES)));
            addRenderableWidget(new HubWidgets.TextureButton(shopX + (shopW + 8) * 2, shopY,
                shopW, 20, Component.literal("Deck Boxes"), pressed -> openShop(
                    de.cas_ual_ty.dueldimension.shop.DiskShopMessages.RequestShop.DECK_BOXES)));
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
            matPicker = new MatColourPicker(matLeft(), matTop(), matWheelSize(), MAT_SLIDER_W,
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
     * The thing being chosen IS art, so the tile is the art: a list of names
     * would tell a player nothing about what they are picking.
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
        protected void renderWidget(net.minecraft.client.gui.GuiGraphics vanillaGraphics, int mouseX, int mouseY, float partialTick)
    {
        // 26.2 describes itself into a render state; 1.21.1 draws now. The
        // body is unchanged -- it is handed the compatibility surface over
        // the real GuiGraphics.
        GuiGraphicsExtractor graphics = new GuiGraphicsExtractor(vanillaGraphics);

            // The chosen one wears the disabled surface. It stays clickable
            // rather
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
     * Is shift held right now?
     * <p>
     * Read from the WINDOW, not {@code Screen.hasShiftDown()} -- that reports
     * the modifier carried by a key EVENT, and this is a per-frame poll rather
     * than an event. CardShopScreen's paging modifier reads it the same way for
     * the same reason.
     */
    private static boolean shiftHeld()
    {
        return com.mojang.blaze3d.platform.InputConstants.isKeyDown(
                net.minecraft.client.Minecraft.getInstance().getWindow(),
                org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT)
            || com.mojang.blaze3d.platform.InputConstants.isKeyDown(
                net.minecraft.client.Minecraft.getInstance().getWindow(),
                org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT);
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
    public void render(net.minecraft.client.gui.GuiGraphics vanillaGraphics, int mouseX, int mouseY, float partialTick)
    {
        // 26.2 describes itself into a render state; 1.21.1 draws now. The
        // body is unchanged -- it is handed the compatibility surface over
        // the real GuiGraphics.
        GuiGraphicsExtractor graphics = new GuiGraphicsExtractor(vanillaGraphics);

        // Asked here rather than through a supplier on the button itself: a
        // widget's own render is skipped while it is invisible, so a button that
        // hid itself could never decide to come back. The screen always renders.
        if(refreshButton != null)
        {
            refreshButton.visible = shiftHeld();
        }

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
            // SHOP is three buttons and a line of explanation; the buttons are
            // widgets, added in init(), so there is nothing to paint under them.
            case SHOP -> shopPanel(graphics, bodyTop);
            default -> waiting(graphics, bodyTop);
        }

        super.render(graphics.vanilla(), mouseX, mouseY, partialTick);

        renderDeckContextMenu(graphics, mouseX, mouseY);
        renderDeckDrag(graphics, mouseX, mouseY);

        extractTooltip(graphics, mouseX, mouseY);
    }

    /** {@code custom.png} is 1024x448; the preview keeps that or the mat skews. */
    private static final float MAT_ASPECT = 1024F / 448F;

    /** The value slider beside the wheel, which is a fixed width at any size. */
    private static final int MAT_SLIDER_W = 14;

    /** The inset body's top, which is where every panel starts drawing. */
    private int bodyTopY()
    {
        return top + PAD + TAB_H + 8;
    }

    /**
     * And the line it must not draw past, which is what the mat view used to.
     * <p>
     * The same expression the inset is drawn with -- see the NineSlice in
     * {@code extractRenderState} -- rather than a second copy of it, so the
     * region and the picture of the region cannot drift apart.
     */
    private int bodyBottomY()
    {
        return bodyTopY() + HEIGHT - (PAD + TAB_H + 8) - 40;
    }

    /** The mat controls start below the sub-tab row and its heading. */
    private int matTop()
    {
        return top + matLayout().previewY();
    }

    /**
     * The mat view's geometry, relative to the panel's own top left.
     *
     * @param wheel      the colour wheel, square
     * @param previewX   left of the mat preview, right of the wheel and slider
     * @param previewY   its top, level with the wheel
     * @param previewW   and its size, at {@link #MAT_ASPECT}
     * @param hexY       where the hex code goes, under the preview
     * @param bodyBottom the line none of the above may cross
     */
    record MatLayout(int wheel, int previewX, int previewY, int previewW, int previewH,
        int hexY, int bodyBottom)
    {
    }

    /**
     * Sizes the mat view to the panel it is in.
     *
     * <b>This is the scaling that was missing.</b> The wheel was a flat 120 and
     * every offset was written for the authored 280-high panel. At a smaller
     * GUI scale the panel is 230, the body loses fifty units, and a 120 wheel
     * ran out through the bottom of its own inset and under the Apply row --
     * taking the hex code with it. Nothing moved because nothing was measured:
     * these were offsets from a panel size that had stopped being the size.
     * <p>
     * A static function of the panel so it can be TESTED, and it reproduces the
     * authored numbers exactly at 460x280 -- wheel 120, preview 210x92 at
     * x=178. This generalises that layout rather than replacing it.
     *
     * @param lineHeight the font's, so the hex line reserves what it needs
     */
    static MatLayout matLayout(int panelW, int panelH, int lineHeight)
    {
        int bodyTop = PAD + TAB_H + 8;
        int bodyBottom = bodyTop + panelH - (PAD + TAB_H + 8) - 40;
        int matTop = bodyTop + 48;
        // Square, so the room below it binds -- and it takes the room rather
        // than insisting on a minimum.
        //
        // A floor of 40 was the obvious thing to write and it is wrong: below
        // about a 168-high panel the body cannot hold 40, so the floor put the
        // wheel back through the bottom it was there to keep it inside. A
        // clipped wheel lying over the Apply row is worse than a small one, and
        // no floor can conjure space that is not there. The sweep in
        // MatLayoutTest is what found this; the authored size passed either way.
        int room = Math.max(1, bodyBottom - 6 - matTop);
        int wheel = Math.min(120, room);
        // wheel + 10 + slider is MatColourPicker's own geometry, then a gap.
        int x = PAD + 6 + wheel + 10 + MAT_SLIDER_W + 18;
        int h = Math.min(92, Math.max(1, room - lineHeight - 4));
        int w = Math.round(h * MAT_ASPECT);
        // Never wider than what is left of the body either, keeping the ratio:
        // a narrow panel would otherwise push the preview out through the side.
        int maxW = Math.max(1, panelW - PAD - 6 - x);
        if(w > maxW)
        {
            w = maxW;
            h = Math.max(1, Math.round(w / MAT_ASPECT));
        }
        return new MatLayout(wheel, x, matTop, w, h, matTop + h + 4, bodyBottom);
    }

    private MatLayout matLayout()
    {
        return matLayout(WIDTH, HEIGHT, font.lineHeight);
    }

    private int matWheelSize()
    {
        return matLayout().wheel();
    }

    /** Left edge of the wheel, and of the mat controls generally. */
    private int matLeft()
    {
        return left + PAD + 6;
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
            // what is shown here is exactly what reaches the table. Measured
            // rather than authored now -- at the panel's full height these come
            // out at the 210x92 they used to be written as.
            MatLayout mat = matLayout();
            matPicker.renderPreview(graphics, left + mat.previewX(), top + mat.previewY(),
                mat.previewW(), mat.previewH());
            String hex = String.format("#%06X", matPicker.colour());
            // Under the preview, not at a fixed offset from the top: the offset
            // was measured against a panel of one size and put the hex under
            // the Apply row at every other.
            graphics.text(font, hex, left + mat.previewX(), top + mat.hexY(),
                0xFFC2C9D6, true);
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
    public boolean mouseClicked(de.cas_ual_ty.dueldimension.compat.InputEvents.MouseButtonEvent event,
        boolean doubled)
    {
        if(contextDeck != null)
        {
            if(event.button() == 0)
            {
                int action = contextActionAt(event.x(), event.y());
                de.cas_ual_ty.dueldimension.duel.profile.DeckList target = contextDeck;
                contextDeck = null;
                if(action >= 0)
                {
                    runContextAction(action, target);
                }
                return true;
            }
            if(event.button() == 1)
            {
                openDeckContext(deckHitAt(event.x(), event.y()), event.x(), event.y());
                return true;
            }
        }
        if(event.button() == 1 && section == Section.DECKS
            && deckView == DeckView.DECKS && EditorState.isSynced())
        {
            de.cas_ual_ty.dueldimension.duel.profile.DeckList target =
                deckHitAt(event.x(), event.y());
            if(target != null)
            {
                openDeckContext(target, event.x(), event.y());
                return true;
            }
        }
        if(event.button() == 0 && renameField == null && section == Section.DECKS
            && deckView == DeckView.DECKS && deckLayout == DeckLayout.GRID
            && EditorState.isSynced())
        {
            de.cas_ual_ty.dueldimension.duel.profile.DeckList target =
                deckHitAt(event.x(), event.y());
            if(target != null)
            {
                dragCandidate = target;
                draggingDeck = null;
                dragTarget = target;
                dragStartX = event.x();
                dragStartY = event.y();
                return true;
            }
        }
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
    public boolean mouseDragged(de.cas_ual_ty.dueldimension.compat.InputEvents.MouseButtonEvent event,
        double dragX, double dragY)
    {
        if(event.button() == 0 && dragCandidate != null)
        {
            double dx = event.x() - dragStartX;
            double dy = event.y() - dragStartY;
            if(draggingDeck != null || dx * dx + dy * dy >= 16.0)
            {
                draggingDeck = dragCandidate;
                de.cas_ual_ty.dueldimension.duel.profile.DeckList over =
                    deckHitAt(event.x(), event.y());
                dragTarget = over == draggingDeck ? null : over;
            }
            return true;
        }
        if(matPicker != null && matPicker.mouseDragged(event.x(), event.y()))
        {
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(de.cas_ual_ty.dueldimension.compat.InputEvents.MouseButtonEvent event)
    {
        if(event.button() == 0 && dragCandidate != null)
        {
            de.cas_ual_ty.dueldimension.duel.profile.DeckList source = dragCandidate;
            de.cas_ual_ty.dueldimension.duel.profile.DeckList target = dragTarget;
            boolean wasDrag = draggingDeck != null;
            dragCandidate = null;
            draggingDeck = null;
            dragTarget = null;
            if(wasDrag)
            {
                if(target != null && EditorState.moveDeck(source, target))
                {
                    notice = "Moved " + source.name();
                    rebuild();
                }
            }
            else
            {
                EditorState.select(EditorState.indexOf(source));
                if(minecraft != null)
                {
                    minecraft.gui.setScreen(new DeckEditorScreen(this));
                }
            }
            return true;
        }
        if(matPicker != null)
        {
            matPicker.mouseReleased();
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(de.cas_ual_ty.dueldimension.compat.InputEvents.KeyEvent event)
    {
        if(contextDeck != null && event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE)
        {
            contextDeck = null;
            return true;
        }
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
    /**
     * The Shop tab's body: a line saying what this is, under the three buttons
     * added in {@link #init()}. The balance is not shown here because each shop
     * shows its own, sent with its stock -- a second copy here would be a copy
     * that could disagree.
     */
    private void shopPanel(GuiGraphicsExtractor graphics, int bodyTop)
    {
        // No caption: two labelled buttons say what this is, and a sentence
        // explaining them was a sentence to read every time.
        graphics.text(font, "Shop", left + PAD + 8, bodyTop + 8, 0xFFF4D089, true);
    }

    /**
     * One tab's width: the strip shared between however many tabs there are,
     * never wider than the authored {@link #TAB_W}. Floored well above the
     * longest label so a sixth tab shrinks the strip rather than clipping text.
     */
    private int tabW()
    {
        int count = Section.values().length;
        int room = WIDTH - PAD * 2 - TAB_GAP * (count - 1);
        return Math.max(48, Math.min(TAB_W, room / count));
    }

    /** Asks the server to open one; it answers with the stock and the balance. */
    private void openShop(String kind)
    {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
            new de.cas_ual_ty.dueldimension.shop.DiskShopMessages.RequestShop(kind));
    }

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
            ? minecraft.player.getGameProfile().getName() : "-";
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
            renderRecipeHeadings(graphics, bodyTop);
            return;
        }
        int count = EditorState.ownDecks().size();
        int visible = deckPageSize();
        if(count > visible)
        {
            graphics.text(font, (deckScroll + 1) + "-"
                + Math.min(count, deckScroll + visible) + " of " + count,
                left + PAD + 150, top + HEIGHT - 26, 0xFF7A8090, true);
        }
        if(!notice.isEmpty())
        {
            graphics.text(font, notice, left + PAD + 214, top + HEIGHT - 26, 0xFFFF8A80, true);
        }
    }

    private int deckBodyHeight()
    {
        return HEIGHT - (PAD + TAB_H + 8) - 32 - 4;
    }

    private int deckRowsVisible()
    {
        if(deckLayout == DeckLayout.GRID)
        {
            // From the first tile's +4 inset to the Close row. Adding the gap
            // before division counts a final row that does not need a trailing
            // gap; at the authored 280-high panel this is exactly three rows.
            int gridTop = PAD + TAB_H + 8 + 4;
            int gridBottom = HEIGHT - 32;
            return Math.max(1,
                (Math.max(0, gridBottom - gridTop) + TILE_GAP) / (TILE_H + TILE_GAP));
        }
        int step = deckLayout == DeckLayout.GRID ? TILE_H + TILE_GAP : ROW_H;
        return Math.max(1, deckBodyHeight() / step);
    }

    private int deckColumns()
    {
        return deckLayout == DeckLayout.GRID ? GRID_COLUMNS : 1;
    }

    /** How many decks are on screen at once, which is what scrolling steps over. */
    private int deckPageSize()
    {
        int slots = deckRowsVisible() * deckColumns();
        // The first Master Duel cell is always Add Deck, so it does not consume
        // a user-deck position when the visible range is reported or clamped.
        return deckLayout == DeckLayout.GRID ? Math.max(0, slots - 1) : slots;
    }

    private int recipeRowsVisible()
    {
        // One row shorter than a LIST deck list: the heading takes the top of
        // each column rather than a row of the single flattened list.
        //
        // Measured from the row height directly rather than from
        // deckRowsVisible(), which now answers for whichever layout the deck
        // tab is in -- and the recipe columns are not in that layout. Asking it
        // would resize them when somebody switched a different tab to a grid.
        return Math.max(1, deckBodyHeight() / ROW_H - 1);
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
        deckScroll = Math.max(0, Math.min(deckScroll, Math.max(0, decks.size() - deckPageSize())));

        int rowX = left + PAD + 4;
        int rowW = WIDTH - PAD * 2 - 8;
        // Condensed twice over. The strip once took 271 of the row's width and
        // left the deck's own name whatever was over; the five actions are
        // still the same five -- nothing has moved behind a menu -- they are
        // simply no wider than they need to be.
        //
        // Use is now the tick, square and iconic, because it is the one action
        // here that is a STATE rather than a verb: this deck or another. What
        // it costs is the word, and what pays for that is the tooltip, which
        // says "Make Active" on hover and could not have fitted on the button
        // at any width. The other four keep their words -- an icon each would
        // be four things to learn, and they are not pressed often enough to be
        // worth learning.
        int useW = ROW_H - 2;
        int renameW = 40;
        int duplicateW = 36;
        int recipeW = 40;
        int deleteW = 36;
        int gap = 2;
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
                renameField.setY(y + 3 + (renameField.getHeight() - 8) / 2);
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
            HubWidgets.IconButton use = new HubWidgets.IconButton(x, y, useW, ROW_H - 2,
                HubTextures.CHECK, Component.literal("Make Active"), pressed ->
            {
                // Told to the server, which is what actually decides the deck
                // a duel is played with.
                EditorState.setActiveDeck(decks.get(useIndex).name());
                notice = "";
                rebuild();
            });
            // Saving an unfinished deck is fine -- building one is a process --
            // but it cannot be USED until it is legal, so this reports that by
            // being disabled rather than by failing at the duel.
            boolean isActive = deck.name().equals(EditorState.profile().activeDeck());
            use.active = !isActive;
            // Says why on hover, and says a DIFFERENT why for each reason it is
            // not offered. A greyed square with one caption would otherwise be
            // the same non-answer whether the deck is already active or short
            // of cards -- which is the failure an icon invites and the tooltip
            // is here to prevent.
            use.setTooltipLines(isActive ? java.util.List.of("Already your active deck")
                : java.util.List.of("Make Active"));
            addRenderableWidget(use);
            x += useW + gap;

            int renameIndex = index;
            addRenderableWidget(new HubWidgets.TextureButton(x, y, renameW, ROW_H - 2,
                Component.literal("Rename"), pressed ->
                    startRename(EditorState.indexOf(decks.get(renameIndex)))));
            x += renameW + gap;

            int duplicateIndex = index;
            addRenderableWidget(new HubWidgets.TextureButton(x, y, duplicateW, ROW_H - 2,
                Component.literal("Copy"), pressed ->
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

    /** Master Duel's six-column grid, populated by the synced user deck list. */
    private void buildDeckGrid(int bodyTop)
    {
        java.util.List<de.cas_ual_ty.dueldimension.duel.profile.DeckList> decks =
            EditorState.ownDecks();
        int columns = deckColumns();
        int rows = deckRowsVisible();
        deckScroll = Math.max(0, Math.min(deckScroll, Math.max(0, decks.size() - deckPageSize())));

        int rowW = WIDTH - PAD * 2 - 8;
        int tileW = (rowW - TILE_GAP * (columns - 1)) / columns;

        int slots = rows * columns;
        for(int slot = 0; slot < slots; slot++)
        {
            int row = slot / columns;
            int column = slot % columns;
            int x = left + PAD + 4 + column * (tileW + TILE_GAP);
            int y = bodyTop + 4 + row * (TILE_H + TILE_GAP);

            if(slot == 0)
            {
                HubWidgets.DeckTileButton add = new HubWidgets.DeckTileButton(
                    x, y, tileW, TILE_H, Component.literal("Create a new deck"),
                    HubTextures.DECK_PLACEHOLDER, false, true, pressed ->
                {
                    EditorState.newDeck();
                    cancelRename();
                    notice = "";
                    rebuild();
                });
                add.setTooltipLines(java.util.List.of("Create a new deck"));
                addRenderableWidget(add);
                continue;
            }

            int index = deckScroll + slot - 1;
            if(index >= decks.size())
            {
                return;
            }
            de.cas_ual_ty.dueldimension.duel.profile.DeckList deck = decks.get(index);
            boolean active = deck.name().equals(EditorState.profile().activeDeck());
            int target = index;
            HubWidgets.DeckTileButton tile = new HubWidgets.DeckTileButton(
                x, y, tileW, TILE_H, Component.literal(deck.name()),
                HubTextures.deckBox(deck.deckBox()), active, false, pressed ->
            {
                EditorState.select(EditorState.indexOf(decks.get(target)));
                if(minecraft != null)
                {
                    minecraft.gui.setScreen(new DeckEditorScreen(this));
                }
            });
            tile.setTooltipLines(java.util.List.of(deck.name(), deck.main().size() + " cards"));
            markUnusable(tile, deck);
            addRenderableWidget(tile);
            deckHits.add(new DeckHit(x, y, tileW, TILE_H, deck));
            if(deck == renaming && renameField != null)
            {
                renameField.setX(x + 4);
                renameField.setY(y + TILE_H - 18 + (renameField.getHeight() - 8) / 2);
                renameField.setWidth(tileW - 8);
                addRenderableWidget(renameField);
                setFocused(renameField);
                renameField.setFocused(true);
            }
        }
    }

    /** Gold drop target plus a compact label that follows the dragged deck. */
    private void renderDeckDrag(GuiGraphicsExtractor graphics, int mouseX, int mouseY)
    {
        if(draggingDeck == null)
        {
            return;
        }
        for(DeckHit hit : deckHits)
        {
            if(hit.deck() == dragTarget)
            {
                de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil.blit(graphics,
                    HubTextures.DECK_TILE_FRAME, hit.x(), hit.y(), hit.width(), hit.height(),
                    0F, 2F / 3F, 1F, 1F,
                    de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil.NO_TINT);
                break;
            }
        }
        String label = font.plainSubstrByWidth(draggingDeck.name(), 92);
        int w = Math.max(48, font.width(label) + 12);
        int x = Math.clamp(mouseX + 7, left + PAD, left + WIDTH - PAD - w);
        int y = Math.clamp(mouseY + 7, top + PAD, top + HEIGHT - PAD - 16);
        NineSlice.draw(graphics, HubTextures.BUTTON, x, y, w, 16, NineSlice.HOVER, 3);
        graphics.text(font, label, x + (w - font.width(label)) / 2, y + 4,
            0xFFF4D089, true);
    }

    private de.cas_ual_ty.dueldimension.duel.profile.DeckList deckHitAt(
        double mouseX, double mouseY)
    {
        for(DeckHit hit : deckHits)
        {
            if(hit.contains(mouseX, mouseY))
            {
                return hit.deck();
            }
        }
        return null;
    }

    private void openDeckContext(
        de.cas_ual_ty.dueldimension.duel.profile.DeckList deck, double mouseX, double mouseY)
    {
        contextDeck = deck;
        if(deck == null)
        {
            return;
        }
        int height = CONTEXT_ACTIONS.length * CONTEXT_ITEM_H + 4;
        contextX = Math.clamp((int)mouseX, left + PAD, left + WIDTH - PAD - CONTEXT_W);
        contextY = Math.clamp((int)mouseY, top + PAD, top + HEIGHT - PAD - height);
    }

    private int contextActionAt(double mouseX, double mouseY)
    {
        if(mouseX < contextX + 2 || mouseX >= contextX + CONTEXT_W - 2
            || mouseY < contextY + 2
            || mouseY >= contextY + 2 + CONTEXT_ACTIONS.length * CONTEXT_ITEM_H)
        {
            return -1;
        }
        return (int)(mouseY - contextY - 2) / CONTEXT_ITEM_H;
    }

    private boolean contextActionEnabled(int action)
    {
        if(contextDeck == null)
        {
            return false;
        }
        if(action == 0)
        {
            return !contextDeck.name().equals(EditorState.profile().activeDeck());
        }
        return action != 4 || !contextDeck.published();
    }

    private void renderDeckContextMenu(GuiGraphicsExtractor graphics, int mouseX, int mouseY)
    {
        if(contextDeck == null)
        {
            return;
        }
        int height = CONTEXT_ACTIONS.length * CONTEXT_ITEM_H + 4;
        NineSlice.draw(graphics, HubTextures.PANEL, contextX, contextY, CONTEXT_W, height);
        for(int action = 0; action < CONTEXT_ACTIONS.length; action++)
        {
            int y = contextY + 2 + action * CONTEXT_ITEM_H;
            boolean over = contextActionAt(mouseX, mouseY) == action;
            int row = !contextActionEnabled(action) ? NineSlice.DISABLED
                : over ? NineSlice.HOVER : NineSlice.IDLE;
            NineSlice.draw(graphics, HubTextures.BUTTON, contextX + 2, y,
                CONTEXT_W - 4, CONTEXT_ITEM_H, row, 3);
            String label = CONTEXT_ACTIONS[action];
            int colour = contextActionEnabled(action)
                ? over ? 0xFFF4D089 : 0xFFE6EAF2 : 0xFF6A7080;
            graphics.text(font, label, contextX + (CONTEXT_W - font.width(label)) / 2,
                y + (CONTEXT_ITEM_H - 8) / 2, colour, true);
        }
    }

    private void runContextAction(int action,
        de.cas_ual_ty.dueldimension.duel.profile.DeckList deck)
    {
        // Re-check against the target rather than trusting the menu's last
        // rendered state; a profile sync can arrive between drawing and click.
        switch(action)
        {
            case 0 ->
            {
                if(!deck.name().equals(EditorState.profile().activeDeck()))
                {
                    EditorState.setActiveDeck(deck.name());
                    notice = "Using " + deck.name();
                }
                rebuild();
            }
            case 1 ->
            {
                startRename(deck);
            }
            case 2 ->
            {
                de.cas_ual_ty.dueldimension.duel.profile.DeckList copy =
                    EditorState.duplicate(EditorState.indexOf(deck));
                notice = "Duplicated as " + copy.name();
                rebuild();
            }
            case 3 ->
            {
                confirmDelete = deck;
                notice = "";
                rebuild();
            }
            case 4 ->
            {
                if(!deck.published())
                {
                    EditorState.publish(deck, true);
                    notice = "Saved to Recipes";
                }
                rebuild();
            }
            default ->
            {
            }
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

    /** Tints an unusable deck soft red and says why, or leaves it alone. */
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
        renameField.setBordered(false);
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
            int visible = deckLayout == DeckLayout.GRID
                ? deckPageSize() : deckRowsVisible();
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
        if(contextDeck != null)
        {
            return;
        }
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
     * card back or does nothing.
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY)
    {
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
            // A notch moves one ROW, which in a grid is a whole row of tiles:
            // stepping one deck at a time would reflow every tile after it and
            // read as the grid shuffling rather than scrolling.
            int step = deckColumns();
            int max = Math.max(0, EditorState.ownDecks().size() - deckPageSize());
            deckScroll = Math.max(0,
                Math.min(max, deckScroll - (int)Math.signum(scrollY) * step));
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
