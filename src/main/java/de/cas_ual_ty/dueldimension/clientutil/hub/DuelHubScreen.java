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
        if(section == Section.SETTINGS)
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

    /**
     * The recipe list: three groups under their headings, flattened into rows
     * so one scroll position covers the lot.
     * <p>
     * A recipe's action is Duplicate As rather than an edit. Loading a recipe
     * means taking a copy of it -- the original is the record of what was
     * saved or opened, and editing it in place would destroy that.
     */
    private void buildRecipeRows(int bodyTop)
    {
        int visible = deckRowsVisible();
        java.util.List<Object> flat = new java.util.ArrayList<>();
        for(RecipeGroup group : recipeGroups())
        {
            flat.add(group.heading());
            flat.addAll(group.decks());
            if(group.decks().isEmpty())
            {
                flat.add("");
            }
        }
        deckScroll = Math.max(0, Math.min(deckScroll, Math.max(0, flat.size() - visible)));

        int rowX = left + PAD + 4;
        int rowW = WIDTH - PAD * 2 - 8;
        int duplicateW = 90;

        for(int row = 0; row < visible; row++)
        {
            int index = row + deckScroll;
            if(index >= flat.size())
            {
                break;
            }
            Object entry = flat.get(index);
            int y = bodyTop + 2 + row * ROW_H;
            if(entry instanceof de.cas_ual_ty.dueldimension.duel.profile.DeckList recipe)
            {
                // Saved recipes are the player's own, so they can be edited and
                // removed. A granted deck is the record of what was opened, so
                // it offers Use and nothing else -- there is no Delete to press
                // and be refused by.
                boolean own = recipe.origin() == de.cas_ual_ty.dueldimension.duel.profile
                    .DeckList.Origin.SAVED;
                int useW = 34;
                int editW = 34;
                int deleteW = 46;
                int gap = 3;
                int actions = own ? useW + editW + deleteW + gap * 3 : useW + gap;
                int nameW = Math.max(60, rowW - actions - 10);
                int x = rowX + 10;


                HubWidgets.TextureButton recipeName = new HubWidgets.TextureButton(x, y, nameW,
                    ROW_H - 2, Component.literal(recipe.name() + "  (" + recipe.main().size() + ")"),
                    pressed -> useRecipe(recipe));
                markUnusable(recipeName, recipe);
                addRenderableWidget(recipeName);
                x += nameW + gap;

                addRenderableWidget(new HubWidgets.TextureButton(x, y, useW, ROW_H - 2,
                    Component.literal("Use"), pressed -> useRecipe(recipe)));
                x += useW + gap;

                if(own)
                {
                    addRenderableWidget(new HubWidgets.TextureButton(x, y, editW, ROW_H - 2,
                        Component.literal("Edit"), pressed ->
                    {
                        EditorState.select(EditorState.indexOf(recipe));
                        if(minecraft != null)
                        {
                            minecraft.setScreen(new DeckEditorScreen(this));
                        }
                    }));
                    x += editW + gap;
                    addRenderableWidget(new HubWidgets.TextureButton(x, y, deleteW, ROW_H - 2,
                        Component.literal("Delete"), pressed ->
                    {
                        EditorState.select(EditorState.indexOf(recipe));
                        String error = EditorState.deleteCurrent();
                        notice = error == null ? "" : error;
                        rebuild();
                    }));
                }
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

    /** Draws the headings and empty markers the recipe rows sit between. */
    private void renderRecipeHeadings(PoseStack poseStack, int bodyTop)
    {
        int visible = deckRowsVisible();
        java.util.List<Object> flat = new java.util.ArrayList<>();
        for(RecipeGroup group : recipeGroups())
        {
            flat.add(group.heading());
            flat.addAll(group.decks());
            if(group.decks().isEmpty())
            {
                flat.add("");
            }
        }
        for(int row = 0; row < visible; row++)
        {
            int index = row + deckScroll;
            if(index >= flat.size())
            {
                break;
            }
            Object entry = flat.get(index);
            int y = bodyTop + 2 + row * ROW_H;
            if(entry instanceof String heading)
            {
                font.drawShadow(poseStack, heading.isEmpty() ? "   (none)" : heading,
                    left + PAD + 6, y + 5, heading.isEmpty() ? 0xFF7A8090 : 0xFFF4D089);
            }
        }
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
        int deleteW = 46;
        int gap = 3;
        int actionsW = useW + renameW + duplicateW + deleteW + gap * 4;
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

            int deleteIndex = index;
            HubWidgets.TextureButton delete = new HubWidgets.TextureButton(x, y, deleteW, ROW_H - 2,
                Component.literal("Delete"), pressed ->
            {
                EditorState.select(EditorState.indexOf(decks.get(deleteIndex)));
                String error = EditorState.deleteCurrent();
                notice = error == null ? "" : error;
                cancelRename();
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
        if(section == Section.DECKS)
        {
            int max = Math.max(0, (deckView == DeckView.RECIPES
                ? EditorState.decks().size() + 3 : EditorState.ownDecks().size())
                - deckRowsVisible());
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
