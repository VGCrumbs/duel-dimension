"""Ports the Forge hub's deck-management half onto the Fabric hub.

Part 2 of the port: the builders and render helpers. Part 1 (fields and the
rebuild flow) was applied inline. Everything below is the Forge original with
the port's usual renames -- GuiGraphicsExtractor for the pose stack,
graphics.text for font.drawShadow, setX/setY for the public fields, and
moveCursorToEnd taking its new boolean.
"""
import io

P = "src/main/java/de/cas_ual_ty/dueldimension/clientutil/hub/DuelHubScreen.java"

BODY = '''    private int deckRowsVisible()
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
                String label = (active ? "\\u25B8 " : "") + deck.name()
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
        String named = "\\"" + confirmDelete.name() + "\\"  ("
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
            graphics.text(font, font.plainSubstrByWidth(group.heading(), columnW),
                x, bodyTop + 2, 0xFFF4D089, true);
            if(group.decks().isEmpty())
            {
                graphics.text(font, "(none)", x, bodyTop + 18, 0xFF7A8090, true);
                continue;
            }
            scrollbar(graphics, x + columnW - BAR_W, bodyTop + 14, visible * ROW_H,
                group.decks().size(), visible, recipeScroll[column]);
            if(group.decks().size() > visible)
            {
                graphics.text(font, (recipeScroll[column] + 1) + "-"
                        + Math.min(group.decks().size(), recipeScroll[column] + visible)
                        + " of " + group.decks().size(),
                    x, bodyTop + 14 + visible * ROW_H + 2, 0xFF7A8090, true);
            }
        }
    }

    private void scrollbar(GuiGraphicsExtractor graphics, int x, int y, int height,
        int total, int visible, int offset)
    {
        int overflow = Math.max(0, total - visible);
        if(overflow <= 0 || height <= 0)
        {
            return;
        }
        NineSlice.draw(graphics, HubTextures.SCROLLBAR, x, y, 4, height, 0, 2);
        int thumbH = Math.max(12, height * visible / Math.max(1, total));
        int thumbY = y + (height - thumbH) * offset / overflow;
        NineSlice.draw(graphics, HubTextures.SCROLLBAR, x, thumbY, 4, thumbH, 1, 2);
    }

'''

s = io.open(P, encoding="utf-8").read()
anchor = "    private int outfitStripX()"
assert anchor in s, "anchor moved"
s = s.replace(anchor, BODY + anchor, 1)

# ---------- deckPanel: replace the read-only body with Forge's renderDecks ----------
start = s.index("    /**\n     * The deck list, read-only for now.")
end = s.index("    private int outfitStripX()")
DECKPANEL = '''    /** The deck panel's non-widget half: counters, notice, and the dialogs. */
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

'''
s = s[:start] + DECKPANEL + s[end:]
io.open(P, "w", encoding="utf-8", newline="\n").write(s)
print("part 2: builders, helpers, deckPanel")
