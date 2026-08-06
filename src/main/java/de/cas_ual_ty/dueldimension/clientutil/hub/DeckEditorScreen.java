package de.cas_ual_ty.dueldimension.clientutil.hub;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import de.cas_ual_ty.dueldimension.card.properties.Properties;
import de.cas_ual_ty.dueldimension.clientutil.CardRenderUtil;
import de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil;
import de.cas_ual_ty.dueldimension.clientutil.DuelTextures;
import de.cas_ual_ty.dueldimension.clientutil.DuelTextures;
import de.cas_ual_ty.dueldimension.duel.profile.CardQuery;
import de.cas_ual_ty.dueldimension.duel.profile.DeckLimits;
import de.cas_ual_ty.dueldimension.clientutil.layout.Layout;
import de.cas_ual_ty.dueldimension.duel.profile.DeckList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The deck editor, laid out the way Tag Force does it: the deck you are
 * building down the left, the collection you are building it from down the
 * right.
 * <p>
 * Left is a workspace of three grids stacked in play order — Main, then Extra,
 * then Side — so the whole deck is visible at once rather than behind tabs.
 * Right is the trunk under a search field, filter chips and a sort control.
 * <p>
 * Cards move by the inventory gestures a Minecraft player already has: pick up
 * with a click and drop where it goes, or shift-click to send a card to the
 * place it obviously belongs. Every add is checked by {@link DeckLimits}, and a
 * refusal says which rule refused so the player knows whether to collect
 * another copy or free a slot.
 */
public class DeckEditorScreen extends Screen
{
    /** The tunables, read fresh every layout pass so a hot reload takes effect. */
    private static final String LAYOUT = "deck_editor";

    private final Screen parent;

    // Resolved once per init() from the layout file and the window size.
    private int cardW;
    private int cardH;
    private int gap;
    private int pad;
    private int mainColumns;
    private int extraColumns;
    private int trunkColumns;
    private int titleH;
    private int headerH;
    private int sectionGap;
    private int mainRows;

    private EditBox search;
    private int leftX;
    private int rightX;
    private int panelTop;
    private int leftW;
    private int rightW;
    private int panelH;

    /** The card currently held by the cursor, and where it came from. */
    private Properties carried;
    private DeckList.Part carriedFrom;
    private int carriedIndex = -1;

    /** Why the last attempted add was refused; cleared on the next action. */
    private String refusal = "";
    private int trunkScroll;

    /**
     * Where the mouse went down, so a press-drag-release can be told from a
     * click. A card picked up by pressing is dropped when the button is
     * RELEASED somewhere else; a card picked up by a click-and-let-go stays on
     * the cursor until the next click. Both gestures work, which is what a
     * Minecraft player expects of an inventory.
     */
    private boolean pressedOnCard;
    private double pressX;
    private double pressY;
    /** Past this many pixels a press counts as a drag rather than a click. */
    private static final double DRAG_SLOP = 4;

    /** The right-click menu: what it is on, and where it opened. */
    private Properties menuCard;
    private DeckList.Part menuPart;
    private int menuIndex = -1;
    private int menuX;
    private int menuY;

    /** How far the hovered card's description is scrolled, and whose it is. */
    private int previewScroll;
    private long previewCard = -1;

    /** The rename field, present only while renaming. */
    private EditBox rename;

    public DeckEditorScreen(Screen parent)
    {
        super(Component.literal("Deck Editor"));
        this.parent = parent;
    }

    @Override
    protected void init()
    {
        Layout layout = Layout.of(LAYOUT);
        pad = layout.i("panel.pad", 8);
        gap = layout.i("card.gap", 2);
        mainColumns = Math.max(1, layout.i("deck.mainColumns", 10));
        extraColumns = Math.max(1, layout.i("deck.extraColumns", 15));
        trunkColumns = Math.max(1, layout.i("trunk.columns", 8));
        titleH = layout.i("deck.titleHeight", 14);
        headerH = layout.i("deck.headerHeight", 12);
        sectionGap = layout.i("deck.sectionGap", 6);

        panelTop = layout.i("panel.top", 34);
        panelH = height - panelTop - layout.i("panel.bottom", 30);
        leftX = pad;
        leftW = (width - pad * 3) * Math.max(20, Math.min(80, layout.i("panel.leftPercent", 58))) / 100;
        rightX = leftX + leftW + layout.i("panel.gap", 8);
        rightW = width - rightX - pad;

        // Cards fill the row. The card WIDTH comes from the columns -- ten
        // across with exactly `gap` between -- so the cell pitch IS cardW + gap
        // rather than the panel divided by ten. Dividing the panel and then
        // drawing a smaller card inside each cell is what left the wide empty
        // channels between columns.
        float aspect = layout.f("card.aspect", 480F / 700F);
        int usableW = leftW - pad * 2;
        cardW = Math.max(8, (usableW - (mainColumns - 1) * gap) / mainColumns);
        cardH = Math.max(10, Math.round(cardW / aspect));

        // Rows grow with what is actually in each part, plus one spare so there
        // is always an empty slot to drop onto, capped by what the part can
        // hold. Reserving every part's full capacity meant ten rows on screen
        // at all times and cards too small to read.
        mainRows = rowsFor(DeckList.Part.MAIN);
        int totalRows = rowsFor(DeckList.Part.MAIN)
            + rowsFor(DeckList.Part.EXTRA) + rowsFor(DeckList.Part.SIDE);
        int spaceForRows = panelH - titleH - pad * 2 - (headerH + sectionGap) * 3;

        // If that many rows will not fit, the card shrinks until they do --
        // height is the binding constraint, never a reason to hide a card.
        while(totalRows * (cardH + gap) > spaceForRows && cardW > 12)
        {
            cardW -= 1;
            cardH = Math.max(10, Math.round(cardW / aspect));
        }

        search = new EditBox(font, rightX + pad + 2, panelTop + pad + 2,
            Math.max(40, rightW - pad * 2 - layout.i("trunk.sortWidth", 56)
                - layout.i("trunk.dirWidth", 30) - 12), 14, Component.literal("Search"));
        search.setResponder(value ->
        {
            EditorState.query().setText(value);
            EditorState.invalidate();
            trunkScroll = 0;
        });
        search.setValue(EditorState.query().text());
        addWidget(search);

        rebuildControls();
    }

    private void rebuildControls()
    {
        clearWidgets();
        addWidget(search);

        Layout layout = Layout.of(LAYOUT);
        int rowH = layout.i("trunk.searchHeight", 16) + 4;
        int chipY = panelTop + pad + rowH;
        int chipX = rightX + pad;
        int chipW = layout.i("trunk.chipWidth", 42);
        int chipH = layout.i("trunk.chipHeight", 14);
        // Kind chips. A chip is on or off, never disabled, so its third atlas
        // row is the lit state rather than a greyed one.
        for(CardQuery.Kind kind : CardQuery.Kind.values())
        {
            CardQuery.Kind target = kind;
            addRenderableWidget(new ChipButton(chipX, chipY, chipW, chipH,
                Component.literal(label(kind)),
                () -> EditorState.query().kinds().contains(target), pressed ->
            {
                EditorState.query().toggleKind(target);
                EditorState.invalidate();
                trunkScroll = 0;
            }));
            chipX += chipW + 2;
        }

        // Sort cycles through the offered orders; the arrow flips direction.
        int sortW = layout.i("trunk.sortWidth", 56);
        int dirW = layout.i("trunk.dirWidth", 30);
        addRenderableWidget(new HubWidgets.TextureButton(rightX + rightW - pad - sortW - dirW - 2,
            panelTop + pad, sortW, 16, Component.literal(EditorState.query().sort().label()), pressed ->
        {
            EditorState.query().setSort(EditorState.query().sort().next());
            EditorState.invalidate();
            rebuildControls();
        }));
        addRenderableWidget(new HubWidgets.TextureButton(rightX + rightW - pad - dirW, panelTop + pad,
            dirW, 16, Component.literal(EditorState.query().descending() ? "DESC" : "ASC"), pressed ->
        {
            EditorState.query().setDescending(!EditorState.query().descending());
            EditorState.invalidate();
            rebuildControls();
        }));

        // Clear Filters: greyed out when there is nothing to clear, so the
        // button itself reports whether anything is narrowing.
        HubWidgets.TextureButton clear = new HubWidgets.TextureButton(
            rightX + pad, chipY + chipH + 3, Math.min(92, rightW - pad * 2), 16,
            Component.literal("Clear Filters"), pressed ->
        {
            EditorState.query().clear();
            search.setValue("");
            EditorState.invalidate();
            trunkScroll = 0;
            rebuildControls();
        });
        clear.active = !EditorState.query().isClear();
        addRenderableWidget(clear);

        // Sorting a deck is the same idea as sorting the trunk, so it reuses
        // the trunk's chosen order rather than inventing a second one.
        addRenderableWidget(new HubWidgets.TextureButton(leftX + pad, height - 26, 92, 20,
            Component.literal("Sort Deck"), pressed ->
        {
            sortDeck();
            refusal = "";
        }));
        addRenderableWidget(new HubWidgets.TextureButton(width - pad - 80, height - 26, 80, 20,
            Component.literal("Done"), pressed -> onClose()));
    }

    /**
     * The deck bar: which deck is open, and what can be done to it.
     * <p>
     * Rename swaps the title for a text field in place rather than opening a
     * dialogue, so the deck stays visible while it is being named -- and
     * commits on Enter or on losing focus, since a rename that is only applied
     * by a button is easy to lose.
     */
    private void buildDeckBar()
    {
        int y = panelTop + pad - 2;
        int right = leftX + leftW - pad;
        int buttonW = 46;

        // Delete is refused for a granted structure deck, so it reports that by
        // being disabled rather than by failing when pressed.
        HubWidgets.TextureButton delete = new HubWidgets.TextureButton(right - buttonW, y,
            buttonW, 16, Component.literal("Delete"), pressed ->
        {
            String error = EditorState.deleteCurrent();
            refusal = error == null ? "" : error;
            rename = null;
            rebuildControls();
        });
        delete.active = EditorState.deck().origin() != DeckList.Origin.STRUCTURE;
        addRenderableWidget(delete);

        addRenderableWidget(new HubWidgets.TextureButton(right - buttonW * 2 - 2, y,
            buttonW, 16, Component.literal("Rename"), pressed -> startRename()));

        addRenderableWidget(new HubWidgets.TextureButton(right - buttonW * 3 - 4, y,
            buttonW, 16, Component.literal("New"), pressed ->
        {
            EditorState.newDeck();
            rename = null;
            refusal = "";
            rebuildControls();
        }));

        // Stepping through decks, which doubles as showing how many there are.
        List<DeckList> all = EditorState.decks();
        if(all.size() > 1)
        {
            addRenderableWidget(new HubWidgets.TextureButton(right - buttonW * 3 - 4 - 34, y,
                16, 16, Component.literal("<"), pressed ->
            {
                EditorState.select((EditorState.currentIndex() - 1 + all.size()) % all.size());
                rename = null;
                rebuildControls();
            }));
            addRenderableWidget(new HubWidgets.TextureButton(right - buttonW * 3 - 4 - 16, y,
                16, 16, Component.literal(">"), pressed ->
            {
                EditorState.select((EditorState.currentIndex() + 1) % all.size());
                rename = null;
                rebuildControls();
            }));
        }

        if(rename != null)
        {
            rename.x = leftX + pad;
            rename.y = y + 2;
            rename.setWidth(Math.max(60, right - buttonW * 3 - 4 - 40 - (leftX + pad)));
            addWidget(rename);
            setFocused(rename);
            rename.setFocus(true);
        }
    }

    private void startRename()
    {
        int y = panelTop + pad;
        rename = new EditBox(font, leftX + pad, y, 120, 14, Component.literal("Deck name"));
        rename.setValue(EditorState.deck().name());
        rename.setMaxLength(40);
        rebuildControls();
    }

    /** Applies the pending rename, if any. Silently keeps the old name if taken. */
    private void commitRename()
    {
        if(rename == null)
        {
            return;
        }
        if(!EditorState.rename(rename.getValue()))
        {
            refusal = "That name is already used";
        }
        rename = null;
        rebuildControls();
    }

    /**
     * Orders every part of the deck by the trunk's current sort.
     * <p>
     * The same comparator the right-hand panel is using, so a player who has
     * sorted the trunk by ATK gets a deck sorted by ATK -- one idea of "in
     * order" rather than two.
     */
    private void sortDeck()
    {
        DeckList deck = EditorState.deck();
        for(DeckList.Part part : DeckList.Part.values())
        {
            List<Integer> codes = deck.partFor(part);
            List<Properties> cards = new java.util.ArrayList<>();
            List<Integer> unknown = new java.util.ArrayList<>();
            for(int code : codes)
            {
                Properties card = card(code);
                if(card == null)
                {
                    unknown.add(code);
                }
                else
                {
                    cards.add(card);
                }
            }
            List<Properties> sorted = EditorState.query().sortOnly(cards);
            codes.clear();
            for(Properties card : sorted)
            {
                codes.add((int)card.getId());
            }
            // A card the database does not know still belongs to the deck, so
            // it is kept rather than dropped by the sort.
            codes.addAll(unknown);
        }
    }

    private static String label(CardQuery.Kind kind)
    {
        return switch(kind)
        {
            case MONSTER -> "Monster";
            case SPELL -> "Spell";
            case TRAP -> "Trap";
        };
    }

    // ---- layout ----

    private int mainTop()
    {
        // Below the deck NAME as well as the section heading; these two used to
        // be drawn at the same y and overlapped.
        return panelTop + pad + titleH + headerH + sectionGap;
    }

    private int mainRows()
    {
        return mainRows;
    }

    private int extraTop()
    {
        return mainTop() + rowsFor(DeckList.Part.MAIN) * (cardH + gap) + headerH + sectionGap;
    }

    private int sideTop()
    {
        return extraTop() + rowsFor(DeckList.Part.EXTRA) * (cardH + gap) + headerH + sectionGap;
    }

    /**
     * How many rows a part is drawn with: enough for what it holds, plus one
     * spare to drop into, never more than it can hold.
     */
    private int rowsFor(DeckList.Part part)
    {
        int columns = partColumns(part);
        int max = (int)Math.ceil(part.capacity() / (double)columns);
        int used = (int)Math.ceil(EditorState.deck().partFor(part).size() / (double)columns);
        return Math.max(1, Math.min(max, used + 1));
    }

    private int partTop(DeckList.Part part)
    {
        return switch(part)
        {
            case MAIN -> mainTop();
            case EXTRA -> extraTop();
            case SIDE -> sideTop();
        };
    }

    private int partColumns(DeckList.Part part)
    {
        return mainColumns;
    }

    /** Which slot of which part a point falls in, or null. */
    private DeckList.Part partAt(double mouseX, double mouseY)
    {
        for(DeckList.Part part : DeckList.Part.values())
        {
            int top = partTop(part);
            int rows = rowsFor(part);
            if(mouseX >= leftX + pad && mouseX < leftX + leftW - pad
                && mouseY >= top && mouseY < top + rows * (cardH + gap))
            {
                return part;
            }
        }
        return null;
    }

    private int slotIndexAt(DeckList.Part part, double mouseX, double mouseY)
    {
        int columns = partColumns(part);
        int cellW = cardW + gap;
        int column = (int)((mouseX - (leftX + pad)) / cellW);
        int row = (int)((mouseY - partTop(part)) / (cardH + gap));
        if(column < 0 || column >= columns || row < 0)
        {
            return -1;
        }
        return row * columns + column;
    }

    /** Below the search row, the chip row and the Clear row. */
    private int trunkGridTop()
    {
        Layout layout = Layout.of(LAYOUT);
        int rowH = layout.i("trunk.searchHeight", 16) + 4;
        return panelTop + pad + rowH + layout.i("trunk.chipHeight", 14) + 3 + 16 + 6;
    }

    private int trunkIndexAt(double mouseX, double mouseY)
    {
        trunkColumns = Math.max(1, (rightW - pad * 2 + gap) / (cardW + gap));
        int gridTop = trunkGridTop();
        int cellW = cardW + gap;
        if(mouseX < rightX + pad || mouseX >= rightX + rightW - pad || mouseY < gridTop)
        {
            return -1;
        }
        int column = (int)((mouseX - (rightX + pad)) / cellW);
        int row = (int)((mouseY - gridTop) / (cardH + gap));
        if(column < 0 || column >= trunkColumns || row < 0)
        {
            return -1;
        }
        return (row + trunkScroll) * trunkColumns + column;
    }

    // ---- interaction ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        // An open menu takes the click before anything else, so choosing from
        // it cannot also pick up the card underneath.
        if(menuCard != null)
        {
            if(handleMenuClick(mouseX, mouseY))
            {
                return true;
            }
            closeMenu();
        }
        if(button == 1)
        {
            return openMenu(mouseX, mouseY);
        }
        refusal = "";
        boolean shift = hasShiftDown();

        DeckList.Part part = partAt(mouseX, mouseY);
        if(part != null)
        {
            int index = slotIndexAt(part, mouseX, mouseY);
            List<Integer> cards = EditorState.deck().partFor(part);
            if(carried != null)
            {
                place(part);
                return true;
            }
            if(index >= 0 && index < cards.size())
            {
                if(shift)
                {
                    // Shift-click in the deck sends a card back to the trunk,
                    // which is simply removing it: the trunk never lost it.
                    cards.remove(index);
                    return true;
                }
                carried = card(cards.remove(index));
                carriedFrom = part;
                carriedIndex = index;
                pressedOnCard = true;
                pressX = mouseX;
                pressY = mouseY;
                return true;
            }
            return true;
        }

        int trunkIndex = trunkIndexAt(mouseX, mouseY);
        if(trunkIndex >= 0)
        {
            List<Properties> shown = EditorState.visible();
            if(trunkIndex < shown.size())
            {
                Properties picked = shown.get(trunkIndex);
                if(shift)
                {
                    // The obvious destination: extra-deck monsters go to the
                    // Extra grid, everything else to Main.
                    add(picked, picked.getIsInExtraDeck() ? DeckList.Part.EXTRA : DeckList.Part.MAIN);
                }
                else
                {
                    carried = picked;
                    carriedFrom = null;
                    carriedIndex = -1;
                    pressedOnCard = true;
                    pressX = mouseX;
                    pressY = mouseY;
                }
                return true;
            }
        }

        if(carried != null)
        {
            // Dropped on nothing: put it back where it came from rather than
            // losing it.
            returnCarried();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void place(DeckList.Part part)
    {
        Properties held = carried;
        if(held == null)
        {
            return;
        }
        carried = null;
        if(carriedFrom == part)
        {
            // Moved within the same grid: it was already removed, so this is
            // just putting it back.
            EditorState.deck().partFor(part).add((int)held.getId());
            carriedFrom = null;
            return;
        }
        if(!add(held, part))
        {
            returnCarriedTo(held);
        }
        carriedFrom = null;
    }

    private void returnCarried()
    {
        Properties held = carried;
        carried = null;
        returnCarriedTo(held);
    }

    private void returnCarriedTo(Properties held)
    {
        if(held != null && carriedFrom != null)
        {
            List<Integer> cards = EditorState.deck().partFor(carriedFrom);
            int at = Math.min(Math.max(0, carriedIndex), cards.size());
            cards.add(at, (int)held.getId());
        }
        carriedFrom = null;
        carriedIndex = -1;
    }

    /** Menu row height and width; small, since it holds two choices. */
    private static final int MENU_ROW = 14;
    private static final int MENU_W = 74;

    /** Right-click opens a menu on whatever card is under the cursor. */
    private boolean openMenu(double mouseX, double mouseY)
    {
        Properties target = cardAt(mouseX, mouseY);
        if(target == null)
        {
            closeMenu();
            return false;
        }
        menuCard = target;
        menuPart = partAt(mouseX, mouseY);
        menuIndex = menuPart == null ? -1 : slotIndexAt(menuPart, mouseX, mouseY);
        menuX = (int)mouseX;
        menuY = (int)mouseY;
        refusal = "";
        return true;
    }

    private void closeMenu()
    {
        menuCard = null;
        menuPart = null;
        menuIndex = -1;
    }

    /** True when the click landed on a menu row and was consumed. */
    private boolean handleMenuClick(double mouseX, double mouseY)
    {
        int rows = menuPart == null ? 1 : 2;
        if(mouseX < menuX || mouseX > menuX + MENU_W
            || mouseY < menuY || mouseY > menuY + rows * MENU_ROW)
        {
            return false;
        }
        int row = (int)((mouseY - menuY) / MENU_ROW);
        Properties target = menuCard;
        DeckList.Part part = menuPart;
        int index = menuIndex;
        closeMenu();

        if(row == 0)
        {
            // Add one, into the part it belongs in.
            DeckList.Part destination = part != null ? part
                : target.getIsInExtraDeck() ? DeckList.Part.EXTRA : DeckList.Part.MAIN;
            add(target, destination);
            return true;
        }
        if(part != null && index >= 0)
        {
            List<Integer> cards = EditorState.deck().partFor(part);
            if(index < cards.size())
            {
                cards.remove(index);
            }
        }
        return true;
    }

    /** Draws the menu, and greys "Add 1" when the rules refuse another copy. */
    private void drawMenu(PoseStack poseStack, int mouseX, int mouseY)
    {
        DeckList.Part destination = menuPart != null ? menuPart
            : menuCard.getIsInExtraDeck() ? DeckList.Part.EXTRA : DeckList.Part.MAIN;
        DeckLimits.Verdict verdict = DeckLimits.canAdd(EditorState.deck(), destination,
            (int)menuCard.getId(), EditorState.trunk(), EditorState.banlist());
        int rows = menuPart == null ? 1 : 2;

        NineSlice.draw(poseStack, HubTextures.PANEL, menuX, menuY, MENU_W, rows * MENU_ROW + 2);
        boolean onAdd = mouseX >= menuX && mouseX <= menuX + MENU_W
            && mouseY >= menuY && mouseY < menuY + MENU_ROW;
        font.drawShadow(poseStack, "Add 1", menuX + 6, menuY + 4,
            !verdict.allowed() ? 0xFF6A7080 : onAdd ? 0xFFFFE9B0 : 0xFFE6EAF2);
        if(rows > 1)
        {
            boolean onRemove = mouseY >= menuY + MENU_ROW && mouseY < menuY + MENU_ROW * 2;
            font.drawShadow(poseStack, "Remove", menuX + 6, menuY + MENU_ROW + 4,
                onRemove ? 0xFFFFB0A8 : 0xFFE6EAF2);
        }
    }

    /** Adds a card if every rule allows it, else records why not. */
    private boolean add(Properties card, DeckList.Part part)
    {
        DeckLimits.Verdict verdict = DeckLimits.canAdd(EditorState.deck(), part,
            (int)card.getId(), EditorState.trunk(), EditorState.banlist());
        if(!verdict.allowed())
        {
            refusal = verdict.reason();
            return false;
        }
        EditorState.deck().partFor(part).add((int)card.getId());
        return true;
    }

    /**
     * Releasing the button drops the carried card where the cursor is, but only
     * if the mouse actually travelled. Without the distance test a plain click
     * would pick a card up and immediately put it back down, so click-to-carry
     * would be impossible.
     */
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button)
    {
        if(carried != null && pressedOnCard)
        {
            boolean dragged = Math.abs(mouseX - pressX) > DRAG_SLOP
                || Math.abs(mouseY - pressY) > DRAG_SLOP;
            pressedOnCard = false;
            if(dragged)
            {
                DeckList.Part part = partAt(mouseX, mouseY);
                if(part != null)
                {
                    place(part);
                }
                else if(trunkIndexAt(mouseX, mouseY) >= 0)
                {
                    // Dropped back on the trunk: the deck simply loses it, and
                    // the trunk never lost it in the first place.
                    carried = null;
                    carriedFrom = null;
                    carriedIndex = -1;
                }
                else
                {
                    returnCarried();
                }
                return true;
            }
        }
        pressedOnCard = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta)
    {
        // A card under the cursor takes the wheel: its description is the thing
        // the player is looking at, and the trunk is still scrollable anywhere
        // else in the panel.
        if(carried == null && cardAt(mouseX, mouseY) != null)
        {
            previewScroll = Math.max(0, previewScroll - (int)Math.signum(delta));
            return true;
        }
        if(mouseX >= rightX)
        {
            int rows = (EditorState.visible().size() + trunkColumns - 1) / trunkColumns;
            int visibleRows = Math.max(1, (panelH - 48) / (cardH + gap));
            trunkScroll = Math.max(0, Math.min(Math.max(0, rows - visibleRows),
                trunkScroll - (int)Math.signum(delta)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers)
    {
        if(rename != null && rename.isFocused())
        {
            if(key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
                || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER)
            {
                commitRename();
                return true;
            }
            if(key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE)
            {
                rename = null;
                rebuildControls();
                return true;
            }
            if(rename.keyPressed(key, scan, modifiers))
            {
                return true;
            }
        }
        if(search != null && search.isFocused() && search.keyPressed(key, scan, modifiers))
        {
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean charTyped(char typed, int modifiers)
    {
        if(rename != null && rename.isFocused() && rename.charTyped(typed, modifiers))
        {
            return true;
        }
        if(search != null && search.isFocused() && search.charTyped(typed, modifiers))
        {
            return true;
        }
        return super.charTyped(typed, modifiers);
    }

    // ---- rendering ----

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick)
    {
        renderBackground(poseStack);
        NineSlice.draw(poseStack, HubTextures.PANEL, leftX, panelTop, leftW, panelH);
        NineSlice.draw(poseStack, HubTextures.PANEL, rightX, panelTop, rightW, panelH);

        renderDeckSide(poseStack, mouseX, mouseY);
        renderTrunkSide(poseStack, mouseX, mouseY);

        super.render(poseStack, mouseX, mouseY, partialTick);
        search.render(poseStack, mouseX, mouseY, partialTick);
        if(rename != null)
        {
            rename.render(poseStack, mouseX, mouseY, partialTick);
        }

        if(!refusal.isEmpty())
        {
            int textWidth = font.width(refusal);
            NineSlice.draw(poseStack, HubTextures.PANEL, width / 2 - textWidth / 2 - 8,
                height - 52, textWidth + 16, 18);
            font.drawShadow(poseStack, refusal, width / 2F - textWidth / 2F, height - 47, 0xFFFF8A80);
        }

        // A hovered card is previewed large, with its name and effect text.
        // Skipped while carrying, since the cursor already has a card on it.
        if(carried == null && menuCard == null)
        {
            Properties hovered = cardAt(mouseX, mouseY);
            if(hovered != null)
            {
                // Raised above everything already drawn. Font rendering batches
                // into a buffer that flushes at the end of the frame, so text
                // drawn EARLIER can otherwise appear on top of a panel drawn
                // later -- which is why the deck headings were showing through
                // the preview. Vanilla tooltips solve it the same way.
                poseStack.pushPose();
                poseStack.translate(0, 0, 400);
                drawPreview(poseStack, hovered, mouseX, mouseY);
                poseStack.popPose();
            }
        }
        if(menuCard != null)
        {
            poseStack.pushPose();
            poseStack.translate(0, 0, 400);
            drawMenu(poseStack, mouseX, mouseY);
            poseStack.popPose();
        }

        // The carried card rides the cursor, as an inventory stack does.
        if(carried != null)
        {
            drawCard(poseStack, carried, mouseX - cardW / 2, mouseY - cardH / 2, 1F);
        }
    }

    /** Whatever card is under the cursor, in either panel. */
    private Properties cardAt(double mouseX, double mouseY)
    {
        DeckList.Part part = partAt(mouseX, mouseY);
        if(part != null)
        {
            List<Integer> cards = EditorState.deck().partFor(part);
            int index = slotIndexAt(part, mouseX, mouseY);
            return index >= 0 && index < cards.size() ? card(cards.get(index)) : null;
        }
        int trunkIndex = trunkIndexAt(mouseX, mouseY);
        List<Properties> shown = EditorState.visible();
        return trunkIndex >= 0 && trunkIndex < shown.size() ? shown.get(trunkIndex) : null;
    }

    /**
     * The preview panel, built the way the duel screen's sidebar is.
     * <p>
     * Three things matter here and all three were wrong before. The art comes
     * from {@link DuelTextures#card} at {@code PREVIEW_CARD_SIZE} with bilinear
     * filtering, because the mod's MAIN image is sized for item icons and
     * upscaling it is what made the preview blurry. It is sampled out of its
     * letterboxed square, or it stretches. And the card's own header lines --
     * type, race, level, attribute, ATK/DEF -- are printed under the name,
     * because a preview without them cannot answer the question a player is
     * actually asking.
     * <p>
     * The panel is anchored to whichever side of the cursor has room and
     * clamped to the screen, so it can never be the thing that overflows.
     */
    private void drawPreview(PoseStack poseStack, Properties card, int mouseX, int mouseY)
    {
        Layout layout = Layout.of(LAYOUT);
        int artW = layout.i("preview.width", 78);
        int artH = Math.round(artW / layout.f("card.aspect", DuelTextures.CARD_ASPECT));
        int inner = 5;
        int panelW = Math.max(artW, layout.i("preview.textWidth", 132)) + inner * 2;
        int textW = panelW - inner * 2;

        // Text is drawn at half size, so it is wrapped to twice the width and
        // its line height is halved to match.
        float scale = layout.f("preview.textScale", 0.5F);
        int wrapW = Math.round(textW / scale);
        int lineH = Math.max(1, Math.round(9 * scale));

        if(previewCard != card.getId())
        {
            // A different card starts at the top of its own description.
            previewCard = card.getId();
            previewScroll = 0;
        }

        java.util.List<Component> header = new java.util.ArrayList<>();
        card.addHeader(header);
        if(!header.isEmpty())
        {
            header.remove(0);
        }
        java.util.List<net.minecraft.util.FormattedCharSequence> headerLines =
            new java.util.ArrayList<>();
        for(Component component : header)
        {
            headerLines.addAll(font.split(component, wrapW));
        }
        java.util.List<net.minecraft.util.FormattedCharSequence> nameLines =
            font.split(Component.literal(card.getName() == null ? "" : card.getName()), wrapW);
        java.util.List<net.minecraft.util.FormattedCharSequence> textLines =
            font.split(Component.literal(card.getText() == null ? "" : card.getText()), wrapW);

        int maxLines = layout.i("preview.maxLines", 10);
        int maxScroll = Math.max(0, textLines.size() - maxLines);
        previewScroll = Math.min(previewScroll, maxScroll);
        int shownLines = Math.min(textLines.size() - previewScroll, maxLines);

        int panelH = inner * 2 + artH + 4
            + nameLines.size() * lineH + 2
            + headerLines.size() * lineH + 3
            + shownLines * lineH + (maxScroll > 0 ? lineH + 2 : 0);

        int x = mouseX + 14;
        if(x + panelW > width - 4)
        {
            x = mouseX - 14 - panelW;
        }
        x = Math.max(4, Math.min(x, width - panelW - 4));
        int y = Math.max(4, Math.min(mouseY - panelH / 2, height - panelH - 4));

        // Half transparent, so the board behind stays readable while pointing.
        NineSlice.draw(poseStack, HubTextures.PANEL, x, y, panelW, panelH,
            NineSlice.IDLE, 1, layout.f("preview.opacity", 0.5F));

        RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        RenderSystem.enableBlend();
        DuelTextures.bindSmooth(DuelTextures.card(card, (byte)0, DuelTextures.PREVIEW_CARD_SIZE));
        DdBlitUtil.blit(poseStack, x + (panelW - artW) / 2, y + inner, artW, artH,
            DuelTextures.CARD_U0, DuelTextures.CARD_V0,
            DuelTextures.CARD_U1 - DuelTextures.CARD_U0,
            DuelTextures.CARD_V1 - DuelTextures.CARD_V0, 1, 1);

        // Everything below is half size. Coordinates are divided by the scale
        // so the text still lands where the panel arithmetic put it.
        poseStack.pushPose();
        poseStack.scale(scale, scale, 1F);
        float sx = (x + inner) / scale;
        float sy = (y + inner + artH + 4) / scale;
        float step = 9F;

        for(net.minecraft.util.FormattedCharSequence line : nameLines)
        {
            font.drawShadow(poseStack, line, sx, sy, 0xFFF4D089);
            sy += step;
        }
        sy += 2F / scale;
        for(net.minecraft.util.FormattedCharSequence line : headerLines)
        {
            font.draw(poseStack, line, sx, sy, 0xFF9FD4FF);
            sy += step;
        }
        sy += 3F / scale;
        for(int i = 0; i < shownLines; i++)
        {
            font.draw(poseStack, textLines.get(previewScroll + i), sx, sy, 0xFFC2C9D6);
            sy += step;
        }
        if(maxScroll > 0)
        {
            font.drawShadow(poseStack, "scroll  " + (previewScroll + shownLines)
                + "/" + textLines.size(), sx, sy + 2F / scale, 0xFF7A8090);
        }
        poseStack.popPose();
    }

    private void renderDeckSide(PoseStack poseStack, int mouseX, int mouseY)
    {
        DeckList deck = EditorState.deck();
        if(rename == null)
        {
            String title = deck.name();
            List<DeckList> all = EditorState.decks();
            if(all.size() > 1)
            {
                title += "  (" + (EditorState.currentIndex() + 1) + "/" + all.size() + ")";
            }
            if(deck.origin() == DeckList.Origin.STRUCTURE)
            {
                title += "  [structure]";
            }
            font.drawShadow(poseStack, title, leftX + pad, panelTop + pad, 0xFFF4D089);
        }

        for(DeckList.Part part : DeckList.Part.values())
        {
            int top = partTop(part);
            List<Integer> cards = deck.partFor(part);
            String heading = switch(part)
            {
                case MAIN -> "Main Deck";
                case EXTRA -> "Extra Deck";
                case SIDE -> "Side Deck";
            };
            // The count doubles as the legality hint: a main deck under 40 is
            // not playable, so the number is the thing to watch.
            boolean ok = part != DeckList.Part.MAIN
                || (cards.size() >= 40 && cards.size() <= 60);
            font.drawShadow(poseStack, heading + "  " + cards.size() + " / " + part.capacity(),
                leftX + pad, top - headerH + 3, ok ? 0xFFC2C9D6 : 0xFFFF8A80);

            int columns = partColumns(part);
            int cellW = cardW + gap;
            int rows = part == DeckList.Part.MAIN ? mainRows : 1;
            // One rectangle for the whole area rather than a frame per card:
            // a grid of empty slots is a lot of visual noise for something the
            // cards themselves already make obvious.
            NineSlice.draw(poseStack, HubTextures.PANEL_INSET, leftX + pad - 2, top - 2,
                leftW - pad * 2 + 4, rows * (cardH + gap) + 4);
            for(int row = 0; row < rows; row++)
            {
                for(int column = 0; column < columns; column++)
                {
                    int index = row * columns + column;
                    if(index >= cards.size())
                    {
                        continue;
                    }
                    Properties card = card(cards.get(index));
                    if(card != null)
                    {
                        drawCard(poseStack, card, leftX + pad + column * cellW,
                            top + row * (cardH + gap), 1F);
                    }
                }
            }
        }
    }

    private void renderTrunkSide(PoseStack poseStack, int mouseX, int mouseY)
    {
        NineSlice.draw(poseStack, HubTextures.SEARCH_FIELD, rightX + pad, panelTop + pad,
            search.getWidth() + 6, Layout.of(LAYOUT).i("trunk.searchHeight", 16) + 2);

        List<Properties> shown = EditorState.visible();
        trunkColumns = Math.max(1, (rightW - pad * 2 + gap) / (cardW + gap));
        int gridTop = trunkGridTop();
        int cellW = cardW + gap;
        int visibleRows = Math.max(1, (panelTop + panelH - gridTop - pad - 12) / (cardH + gap));
        NineSlice.draw(poseStack, HubTextures.PANEL_INSET, rightX + pad - 2, gridTop - 2,
            rightW - pad * 2 + 4, visibleRows * (cardH + gap) + 4);

        for(int row = 0; row < visibleRows; row++)
        {
            for(int column = 0; column < trunkColumns; column++)
            {
                int index = (row + trunkScroll) * trunkColumns + column;
                int x = rightX + pad + column * cellW;
                int y = gridTop + row * (cardH + gap);
                if(index >= shown.size())
                {
                    continue;
                }
                Properties card = shown.get(index);
                int inDeck = EditorState.deck().copiesOf((int)card.getId());
                int max = DeckLimits.maxCopies((int)card.getId(), EditorState.trunk(),
                    EditorState.banlist());
                // A card already at its limit is dimmed, so the trunk shows
                // what is still available at a glance rather than on refusal.
                drawCard(poseStack, card, x, y, inDeck >= max ? 0.35F : 1F);
                if(inDeck > 0)
                {
                    String count = inDeck + "/" + max;
                    font.drawShadow(poseStack, count, x + cardW - font.width(count),
                        y + cardH - 8, inDeck >= max ? 0xFFFF8A80 : 0xFFF4D089);
                }
            }
        }

        font.drawShadow(poseStack, shown.size() + " cards", rightX + pad,
            panelTop + panelH - 12, 0xFF7A8090);
    }

    private static Properties card(int code)
    {
        return de.cas_ual_ty.dueldimension.DdDatabase.PROPERTIES_LIST.get((long)code);
    }

    private void drawCard(PoseStack poseStack, Properties card, int x, int y, float alpha)
    {
        drawCard(poseStack, card, x, y, cardW, cardH, alpha);
    }

    /**
     * Draws a card at its true proportions.
     * <p>
     * The mod stores card art letterboxed inside a SQUARE image, with the card
     * occupying u 0.199..0.801 and v 0.0625..0.9375 (see DuelTextures). Blitting
     * the whole square into a card-shaped rect therefore squeezes the art
     * horizontally, which is the stretch that was visible on every card in both
     * panels. Sampling the letterbox window instead keeps it true.
     */
    private void drawCard(PoseStack poseStack, Properties card, int x, int y,
        int w, int h, float alpha)
    {
        RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexShader);
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1F, 1F, 1F, alpha);
        CardRenderUtil.bindMainResourceLocation(card, (byte)0);
        // Passing a nominal file size of 1 lets the window be given as the
        // fractions DuelTextures already measured.
        DdBlitUtil.blit(poseStack, x, y, w, h,
            DuelTextures.CARD_U0, DuelTextures.CARD_V0,
            DuelTextures.CARD_U1 - DuelTextures.CARD_U0,
            DuelTextures.CARD_V1 - DuelTextures.CARD_V0, 1, 1);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
    }

    @Override
    public void tick()
    {
        super.tick();
        // An edit reaches the server within a tick of being made, so closing
        // the game rather than the screen still keeps the deck.
        EditorState.flush();
    }

    @Override
    public void onClose()
    {
        EditorState.flush();
        if(minecraft != null)
        {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }

    /** A filter chip: lit when its filter is on. */
    private static class ChipButton extends HubWidgets.TextureButton
    {
        private final java.util.function.BooleanSupplier lit;

        ChipButton(int x, int y, int width, int height, Component label,
            java.util.function.BooleanSupplier lit, OnPress onPress)
        {
            super(x, y, width, height, label, onPress);
            this.lit = lit;
        }

        @Override
        public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTick)
        {
            int row = lit.getAsBoolean() ? NineSlice.SELECTED
                : isHoveredOrFocused() ? NineSlice.HOVER : NineSlice.IDLE;
            NineSlice.draw(poseStack, HubTextures.CHIP, x, y, width, height, row, 3);
            drawLabel(poseStack, lit.getAsBoolean() ? 0xFFFFE9B0 : 0xFFC2C9D6);
        }
    }
}
