package de.cas_ual_ty.dueldimension.clientutil.hub;

import com.mojang.blaze3d.vertex.PoseStack;
import de.cas_ual_ty.dueldimension.DuelDimension;
import de.cas_ual_ty.dueldimension.clientutil.DuelClientState;
import de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The Duel Hub, opened with {@code Y}.
 * <p>
 * One place for everything that is not a duel: who you are, what you play, and
 * how it looks. In particular it is now the <em>only</em> place the duel mat is
 * chosen — the in-duel selector is gone, because a setting that can be changed
 * from two places has no single source of truth and the duel screen is the
 * worse of the two: it is the one moment a player is busy.
 * <p>
 * Every surface here is a PNG. Nothing is a filled rectangle and no vanilla
 * widget art is used, so the whole hub can be reskinned by replacing files
 * under {@code textures/gui}.
 */
public class DuelHubScreen extends Screen
{
    /** The hub's sections, in tab order. */
    public enum Section
    {
        PROFILE("Profile"),
        DECKS("Decks"),
        OUTFIT("Outfit"),
        SETTINGS("Settings");

        private final String label;

        Section(String label)
        {
            this.label = label;
        }

        public String label()
        {
            return label;
        }
    }

    private static final int WIDTH = 460;
    private static final int HEIGHT = 280;
    private static final int TAB_W = 92;
    private static final int TAB_H = 22;
    private static final int PAD = 10;

    /** Remembered across openings, so returning to the hub lands where you left. */
    private static Section section = Section.PROFILE;

    private int left;
    private int top;
    private MatColourPicker matPicker;

    /**
     * The deck being renamed, held by identity rather than by index.
     * <p>
     * It was an index, which silently pointed at the wrong row: the rows
     * iterate the player's own decks while the index addressed the full list,
     * so the field was placed against whichever deck happened to share that
     * position -- usually none, so nothing appeared to happen at all.
     */
    private de.cas_ual_ty.dueldimension.duel.profile.DeckList renaming;
    private net.minecraft.client.gui.components.EditBox renameField;
    /** First visible row, so a long deck list can be scrolled. */
    private int deckScroll;
    /** Why the last action was refused. */
    private String notice = "";

    /** The Decks tab has two views; this is which one. */
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

    private static DeckView deckView = DeckView.DECKS;

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
                Component.literal(candidate.label()), () -> section == target, pressed ->
            {
                section = target;
                rebuild();
            }));
            tabX += TAB_W + 4;
        }

        int bodyTop = top + PAD + TAB_H + 8;
        if(section == Section.OUTFIT)
        {
            matPicker = null;
            buildOutfitRows(bodyTop);
        }
        else if(section == Section.SETTINGS)
        {
            matPicker = new MatColourPicker(left + PAD + 6, bodyTop + 26, 120, 14,
                DuelClientState.matColour());
            addRenderableWidget(new HubWidgets.TextureButton(left + PAD + 6, top + HEIGHT - 32,
                96, 20, Component.literal("Apply"), pressed -> applyMat()));
            addRenderableWidget(new HubWidgets.TextureButton(left + PAD + 108, top + HEIGHT - 32,
                96, 20, Component.literal("Reset"), pressed ->
            {
                matPicker.setColour(DuelClientState.DEFAULT_MAT_COLOUR);
                applyMat();
            }));
        }
        else if(section == Section.DECKS)
        {
            matPicker = null;
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
        else
        {
            matPicker = null;
        }

        addRenderableWidget(new HubWidgets.TextureButton(left + WIDTH - PAD - 80,
            top + HEIGHT - 32, 80, 20, Component.literal("Close"), pressed -> onClose()));
    }

    /** Height of one deck row, and how many fit in the body. */
    private static final int ROW_H = 20;

    private int deckRowsVisible()
    {
        int bodyHeight = HEIGHT - (PAD + TAB_H + 8) - 40;
        return Math.max(1, (bodyHeight - 26) / ROW_H);
    }

    /** One scroll position per recipe column. */
    private final int[] recipeScroll = new int[3];

    /** Gap between the three columns, and the width the scrollbar sits in. */
    private static final int COLUMN_GAP = 6;
    private static final int BAR_W = 6;

    private int recipeColumnW()
    {
        return (WIDTH - PAD * 2 - 8 - COLUMN_GAP * 2) / 3;
    }

    private int recipeColumnX(int column)
    {
        return left + PAD + 4 + column * (recipeColumnW() + COLUMN_GAP);
    }

    private int recipeRowsVisible()
    {
        // One row shorter than the deck list's: the heading takes the top of
        // each column rather than a row of the single flattened list.
        return Math.max(1, deckRowsVisible() - 1);
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
        int nameW = columnW - BAR_W - 2;

        for(int column = 0; column < groups.size() && column < recipeScroll.length; column++)
        {
            java.util.List<de.cas_ual_ty.dueldimension.duel.profile.DeckList> decks =
                groups.get(column).decks();
            recipeScroll[column] = Math.max(0, Math.min(recipeScroll[column],
                Math.max(0, decks.size() - visible)));
            int x = recipeColumnX(column);

            for(int row = 0; row < visible; row++)
            {
                int index = row + recipeScroll[column];
                if(index >= decks.size())
                {
                    break;
                }
                de.cas_ual_ty.dueldimension.duel.profile.DeckList recipe = decks.get(index);
                int y = bodyTop + 14 + row * ROW_H;
                HubWidgets.TextureButton recipeName = new HubWidgets.TextureButton(x, y, nameW,
                    ROW_H - 2, Component.literal(font.plainSubstrByWidth(
                        recipe.name() + "  (" + recipe.main().size() + ")", nameW - 8)),
                    pressed -> useRecipe(recipe));
                markUnusable(recipeName, recipe);
                if(recipeName.tooltipLines().isEmpty())
                {
                    recipeName.setTooltipLines(java.util.List.of(recipe.name(),
                        "Makes a new deck from this recipe"));
                }
                addRenderableWidget(recipeName);
            }
        }
    }

    /** Use: a new deck from the recipe, then name it. */
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

    private void useRecipe(de.cas_ual_ty.dueldimension.duel.profile.DeckList recipe)
    {
        EditorState.useRecipe(EditorState.indexOf(recipe));
        deckView = DeckView.DECKS;
        deckScroll = 0;
        notice = "";
        startRename(EditorState.currentIndex());
    }

    /**
     * The deck a Delete press is waiting on confirmation for, if any.
     * <p>
     * Held rather than acted on: deleting is the one row action that cannot be
     * undone, and it sits between Duplicate and the edge of the panel.
     */
    private de.cas_ual_ty.dueldimension.duel.profile.DeckList confirmDelete;

    /** Draws the confirmation over the list, and the two answers to it. */
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

    private void renderDeleteConfirm(PoseStack poseStack)
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
        // behind it cannot be mistaken for something still clickable.
        fill(poseStack, left, top, left + WIDTH, top + HEIGHT, 0xC0000000);
        NineSlice.draw(poseStack, HubTextures.PANEL, boxX, boxY, boxW, boxH);
        font.drawShadow(poseStack, "Delete this deck?", boxX + 12, boxY + 10, 0xFFF4D089);
        String named = "\"" + confirmDelete.name() + "\"  ("
            + confirmDelete.main().size() + " cards)";
        font.drawShadow(poseStack, font.plainSubstrByWidth(named, boxW - 24),
            boxX + 12, boxY + 24, 0xFFE6EAF2);
        font.drawShadow(poseStack, "This cannot be undone.", boxX + 12, boxY + 36, 0xFFFF6B6B);
    }

    /** How wide one preview tile is, and how tall the figure inside it stands. */
    private static final int TILE_W = 78;
    private static final int TILE_H = 118;

    /** First tile shown, when there are more outfits than fit across. */
    private int outfitScroll;

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
        int editorY = bodyTop + 22 + TILE_H + 8;
        boolean wearing = !worn.isEmpty();
        HubWidgets.TextureButton pick = new HubWidgets.TextureButton(outfitStripX(), editorY,
            110, 20, Component.literal("Import PNG..."), pressed -> importUnderSkin());
        pick.active = wearing;
        pick.setTooltipLines(wearing
            ? java.util.List.of("Your own skin, edited",
                "Take off the sleeves or hood that poke out from under this outfit")
            : java.util.List.of("Only used under an outfit",
                "With none on you are drawn with your real skin"));
        addRenderableWidget(pick);

        HubWidgets.TextureButton clear = new HubWidgets.TextureButton(outfitStripX() + 116,
            editorY, 96, 20, Component.literal("Use Real Skin"), pressed ->
        {
            de.cas_ual_ty.dueldimension.clientutil.UnderSkin.clear();
            notice = "";
            rebuild();
        });
        clear.active = de.cas_ual_ty.dueldimension.clientutil.UnderSkin.present();
        clear.setTooltipLines(java.util.List.of("Forget the imported skin"));
        addRenderableWidget(clear);
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

    private void renderOutfit(PoseStack poseStack, int bodyTop)
    {
        int across = outfitTiles();
        String worn = EditorState.profile().outfit();
        // One clock for the whole row, so the figures turn together rather than
        // each starting from whenever its tile happened to be built.
        long time = net.minecraft.Util.getMillis();

        for(int slot = 0; slot < across && slot + outfitScroll < outfits().size(); slot++)
        {
            de.cas_ual_ty.dueldimension.duel.outfit.Outfits.Outfit outfit =
                outfits().get(slot + outfitScroll);
            int x = outfitStripX() + slot * TILE_W;
            boolean on = outfit.id().equals(worn);

            NineSlice.draw(poseStack, HubTextures.PANEL_INSET, x, bodyTop + 22,
                TILE_W - 6, TILE_H);
            OutfitPreview.draw(poseStack, x + (TILE_W - 6) / 2, bodyTop + 22 + TILE_H - 20,
                2.6F, outfit, time);

            String name = font.plainSubstrByWidth(outfit.name(), TILE_W - 12);
            font.drawShadow(poseStack, name, x + (TILE_W - 6 - font.width(name)) / 2,
                bodyTop + 22 + TILE_H - 12, on ? 0xFFF4D089 : 0xFFC2C9D6);
        }

        if(outfits().size() > across)
        {
            font.drawShadow(poseStack, (outfitScroll + 1) + "-"
                    + Math.min(outfits().size(), outfitScroll + across)
                    + " of " + outfits().size(),
                outfitStripX(), bodyTop + 10, 0xFF7A8090);
        }

        int editorY = bodyTop + 22 + TILE_H + 8;
        String state = worn.isEmpty()
            ? "No outfit: drawn with your own skin."
            : de.cas_ual_ty.dueldimension.clientutil.UnderSkin.present()
                ? "Under-skin: your imported edit."
                : "Under-skin: your own skin.";
        font.drawShadow(poseStack, state, outfitStripX() + 218, editorY + 6,
            worn.isEmpty() ? 0xFF7A8090 : 0xFFC2C9D6);
        if(!notice.isEmpty())
        {
            font.drawShadow(poseStack, font.plainSubstrByWidth(notice, WIDTH - PAD * 2 - 12),
                outfitStripX(), editorY + 26, 0xFFFF8A80);
        }
    }

    /** Draws each column's heading, its scrollbar, and its empty marker. */
    private void renderRecipeHeadings(PoseStack poseStack, int bodyTop)
    {
        java.util.List<RecipeGroup> groups = recipeGroups();
        int visible = recipeRowsVisible();
        int columnW = recipeColumnW();

        for(int column = 0; column < groups.size() && column < recipeScroll.length; column++)
        {
            RecipeGroup group = groups.get(column);
            int x = recipeColumnX(column);
            font.drawShadow(poseStack, font.plainSubstrByWidth(group.heading(), columnW),
                x, bodyTop + 2, 0xFFF4D089);
            if(group.decks().isEmpty())
            {
                font.drawShadow(poseStack, "(none)", x, bodyTop + 18, 0xFF7A8090);
                continue;
            }
            scrollbar(poseStack, x + columnW - BAR_W, bodyTop + 14, visible * ROW_H,
                group.decks().size(), visible, recipeScroll[column]);
            if(group.decks().size() > visible)
            {
                font.drawShadow(poseStack, (recipeScroll[column] + 1) + "-"
                        + Math.min(group.decks().size(), recipeScroll[column] + visible)
                        + " of " + group.decks().size(),
                    x, bodyTop + 14 + visible * ROW_H + 2, 0xFF7A8090);
            }
        }
    }

    /** The same bar the editor draws, over a count of rows. */
    private void scrollbar(PoseStack poseStack, int x, int y, int height,
        int total, int visible, int offset)
    {
        int overflow = Math.max(0, total - visible);
        if(overflow <= 0 || height <= 0)
        {
            return;
        }
        NineSlice.draw(poseStack, HubTextures.SCROLLBAR, x, y, 4, height, 0, 2);
        int thumbH = Math.max(12, height * visible / Math.max(1, total));
        int thumbY = y + (height - thumbH) * offset / overflow;
        NineSlice.draw(poseStack, HubTextures.SCROLLBAR, x, thumbY, 4, thumbH, 1, 2);
    }

    /** Which recipe column the cursor is over, or -1. */
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

    /**
     * One row per deck: the name, then what can be done to it.
     * <p>
     * The name is a button rather than a label because opening the editor is
     * the most likely thing to want from a deck, and giving it its own column
     * would push the actions off the panel.
     */
    private void buildDeckRows(int bodyTop)
    {
        java.util.List<de.cas_ual_ty.dueldimension.duel.profile.DeckList> decks = EditorState.ownDecks();
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
                renameField.x = x + 2;
                renameField.y = y + 3;
                renameField.setWidth(nameW - 6);
                addWidget(renameField);
                setFocused(renameField);
                renameField.setFocus(true);
            }
            else
            {
                // Active deck is marked, so "Use" has visible consequence.
                boolean active = deck.name().equals(EditorState.profile().activeDeck());
                String label = (active ? "\u25B8 " : "") + deck.name()
                    + "  (" + deck.main().size() + ")";
                int target = index;
                HubWidgets.TextureButton name = new HubWidgets.TextureButton(x, y, nameW,
                    ROW_H - 2, Component.literal(label), pressed ->
                {
                    EditorState.select(EditorState.indexOf(decks.get(target)));
                    if(minecraft != null)
                    {
                        minecraft.setScreen(new DeckEditorScreen(this));
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
        renameField.moveCursorToEnd();
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

    private void applyMat()
    {
        if(matPicker == null)
        {
            return;
        }
        DuelClientState.setMatColour(matPicker.colour());
        // The other duelist draws your mat on their far half, so the colour has
        // to travel; it is a preference, not hidden information.
        DuelDimension.channel.sendToServer(
            new PromptMessages.SetPlayMat(DuelClientState.matColourId()));
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick)
    {
        renderBackground(poseStack);
        NineSlice.draw(poseStack, HubTextures.PANEL, left, top, WIDTH, HEIGHT);

        int bodyTop = top + PAD + TAB_H + 8;
        int bodyHeight = HEIGHT - (bodyTop - top) - 40;
        NineSlice.draw(poseStack, HubTextures.PANEL_INSET, left + PAD, bodyTop,
            WIDTH - PAD * 2, bodyHeight);

        switch(section)
        {
            case PROFILE -> renderProfile(poseStack, bodyTop);
            case DECKS -> renderDecks(poseStack, bodyTop);
            case OUTFIT -> renderOutfit(poseStack, bodyTop);
            case SETTINGS -> renderSettings(poseStack, bodyTop);
        }

        super.render(poseStack, mouseX, mouseY, partialTick);
        // Why a red row cannot be used, on the row itself.
        for(net.minecraft.client.gui.components.events.GuiEventListener child : children())
        {
            if(child instanceof HubWidgets.TextureButton button && button.visible
                && button.isMouseOver(mouseX, mouseY) && !button.tooltipLines().isEmpty())
            {
                renderComponentTooltip(poseStack, button.tooltipLines().stream()
                    .map(net.minecraft.network.chat.Component::literal)
                    .map(line -> (net.minecraft.network.chat.Component)line).toList(),
                    mouseX, mouseY);
                break;
            }
        }
    }

    private void renderProfile(PoseStack poseStack, int bodyTop)
    {
        int x = left + PAD + 10;
        int y = bodyTop + 10;
        font.drawShadow(poseStack, "Profile", x, y, 0xFFF4D089);
        y += 16;
        String name = minecraft != null && minecraft.player != null
            ? minecraft.player.getGameProfile().getName() : "-";
        font.drawShadow(poseStack, "Duelist: " + name, x, y, 0xFFE6EAF2);
        y += 12;
        font.drawShadow(poseStack, "Active deck: " + DuelClientState.activeDeckName(), x, y, 0xFFC2C9D6);
        y += 18;
        // Stats arrive with the profile packet; until then this states plainly
        // that it is not wired rather than showing a convincing zero.
        font.drawShadow(poseStack, "Statistics are not recorded yet.", x, y, 0xFF7A8090);
    }

    private void renderDecks(PoseStack poseStack, int bodyTop)
    {
        if(confirmDelete != null)
        {
            renderDeleteConfirm(poseStack);
            return;
        }
        if(renameField != null)
        {
            renameField.render(poseStack, 0, 0, 0F);
        }
        if(deckView == DeckView.RECIPES)
        {
            renderRecipeHeadings(poseStack, bodyTop + 22);
            return;
        }
        int count = EditorState.ownDecks().size();
        int visible = deckRowsVisible();
        if(count > visible)
        {
            font.drawShadow(poseStack, (deckScroll + 1) + "-"
                + Math.min(count, deckScroll + visible) + " of " + count,
                left + PAD + 4, top + HEIGHT - 28, 0xFF7A8090);
        }
        if(!notice.isEmpty())
        {
            font.drawShadow(poseStack, notice, left + PAD + 110, top + HEIGHT - 26, 0xFFFF8A80);
        }
    }

    private void renderSettings(PoseStack poseStack, int bodyTop)
    {
        int x = left + PAD + 10;
        font.drawShadow(poseStack, "Duel Mat", x, bodyTop + 10, 0xFFF4D089);
        if(matPicker != null)
        {
            matPicker.render(poseStack);
            // The preview is the real mat texture under the chosen tint, so
            // what is shown here is exactly what reaches the table.
            matPicker.renderPreview(poseStack, left + PAD + 168, bodyTop + 30, 210, 92);
            String hex = String.format("#%06X", matPicker.colour());
            font.drawShadow(poseStack, hex, left + PAD + 168, bodyTop + 128, 0xFFC2C9D6);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if(matPicker != null && matPicker.mouseClicked(mouseX, mouseY))
        {
            return true;
        }
        if(renameField != null && !renameField.isMouseOver(mouseX, mouseY))
        {
            commitRename();
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers)
    {
        if(confirmDelete != null && key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE)
        {
            // Escape answers the question rather than leaving the hub, which is
            // the safe reading of it while a delete is waiting.
            confirmDelete = null;
            rebuild();
            return true;
        }
        if(renameField != null && renameField.isFocused())
        {
            if(key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER)
            {
                commitRename();
                return true;
            }
            if(key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE)
            {
                cancelRename();
                rebuild();
                return true;
            }
            if(renameField.keyPressed(key, scan, modifiers))
            {
                return true;
            }
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean charTyped(char typed, int modifiers)
    {
        if(renameField != null && renameField.isFocused() && renameField.charTyped(typed, modifiers))
        {
            return true;
        }
        return super.charTyped(typed, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta)
    {
        if(section == Section.OUTFIT)
        {
            int max = Math.max(0, outfits().size() - outfitTiles());
            outfitScroll = Math.max(0, Math.min(max, outfitScroll - (int)Math.signum(delta)));
            rebuild();
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
                Math.min(max, recipeScroll[column] - (int)Math.signum(delta)));
            rebuild();
            return true;
        }
        if(section == Section.DECKS)
        {
            int max = Math.max(0, EditorState.ownDecks().size() - deckRowsVisible());
            deckScroll = Math.max(0, Math.min(max, deckScroll - (int)Math.signum(delta)));
            rebuild();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY)
    {
        if(matPicker != null && matPicker.mouseDragged(mouseX, mouseY))
        {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button)
    {
        if(matPicker != null)
        {
            matPicker.mouseReleased();
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
