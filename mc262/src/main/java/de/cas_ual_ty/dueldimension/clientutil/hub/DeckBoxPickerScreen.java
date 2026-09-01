package de.cas_ual_ty.dueldimension.clientutil.hub;

import de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil;
import de.cas_ual_ty.dueldimension.clientutil.DuelTextures;
import de.cas_ual_ty.dueldimension.clientutil.layout.Layout;
import de.cas_ual_ty.dueldimension.duel.profile.DeckBoxStyle;
import de.cas_ual_ty.dueldimension.duel.profile.DeckList;
import de.cas_ual_ty.dueldimension.net.ProfilePayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Choosing which deck case a deck is kept in.
 *
 * <h2>Why it was rebuilt</h2>
 * It was a fixed three-column panel with the case's name printed under each
 * tile, sized for the nine cases that existed when it was written. At sixty-six
 * the panel ran off the top and bottom of the screen and the names — 66 pixels
 * of room for "Roar of the Gladiator Beasts" — overlapped each other into an
 * unreadable stripe. Half of them were never abbreviated either: a
 * {@code shortLabel} switch covered six by hand and everything added afterwards
 * fell through to the full name.
 * <p>
 * So it is the deck box SHOP's layout: the highlighted case large down the left,
 * a scaling grid in the middle that fits more per row as the window grows, and
 * the name in a details bar underneath where there is room for it. Two screens
 * showing the same fifty pictures should show them the same way, and a tile with
 * no text in it cannot have text collide in it.
 *
 * <h2>What it decides</h2>
 * Only which of the cases the player ALREADY OWNS is on the deck. Ownership is
 * read off the synced profile, the same object the shop reads, and the choice is
 * sent as a name for the server to apply. Locked cases stay visible and stay
 * unclickable — seeing what is buyable is the point of showing them.
 */
public class DeckBoxPickerScreen extends Screen
{
    /**
     * The sleeve shop's layout, which the deck box shop also uses.
     * <p>
     * Three screens, one set of measurements. A picker that agreed with the shop
     * everywhere except its gaps would be worse than one that plainly differs.
     */
    private static final String LAYOUT = "sleeve_shop";

    /** The Unowned chip, sized to its label the way the editor's row is. */
    private static final int UNOWNED_CHIP_W = 62;
    private static final int UNOWNED_CHIP_H = 14;

    private final Screen parent;

    /**
     * The cases on the shelf right now -- not the whole catalogue.
     * <p>
     * Everything indexes into this: the selection, the scroll and the hit test.
     * {@link #rebuildStyles()} is the only thing that writes it, and it keeps
     * the selection pointed at the same CASE across a rebuild rather than at the
     * same slot.
     */
    private final List<DeckBoxStyle> styles = new ArrayList<>();
    private int selected;
    private int scroll;
    /**
     * What the shelf is ordered by, and which way.
     * <p>
     * The same three the sleeve shop offers, in the same order and with the
     * same default, because they are the same question about the same kind of
     * thing. Colour first: with sixty-six cases the question a player arrives
     * with is what it looks like, and id order answers a question about Master
     * Duel's release schedule instead.
     */
    private SortKey sortKey = SortKey.COLOR;
    private boolean descending;

    /** The sort chips and their arrow, all one row tall. */
    private static final int SORT_ROW_H = 16;

    private enum SortKey
    {
        /** See {@link DeckBoxStyle#compareByColour}. */
        COLOR("Color"),
        NAME("Name"),
        /** Enum order, which is Master Duel's own DeckCase id order. */
        ID("ID");

        final String label;

        SortKey(String label)
        {
            this.label = label;
        }
    }

    private String notice = "";

    /**
     * Whether locked cases are on the shelf.
     * <p>
     * Off by default. There are sixty-six cases and a new profile owns two of
     * them, so the shelf was mostly greyed-out art you cannot pick -- the same
     * problem the trunk has with unowned cards, and it is solved here the same
     * way: a chip that puts them back.
     */
    private boolean showUnowned;

    /** Kept so its label and enabled state can follow the selection. */
    private HubWidgets.TextureButton useButton;

    public DeckBoxPickerScreen(Screen parent)
    {
        super(Component.literal("Deck Box"));
        this.parent = parent;
        rebuildStyles();
        int at = styles.indexOf(EditorState.deck().deckBox());
        // Opens on the one already in use rather than at the top, so the screen
        // answers "which is this deck in" before it is touched.
        selected = Math.max(0, at);
    }

    /**
     * Refills the shelf for the current filter, keeping the selection on the
     * case it was on.
     * <p>
     * The worn case is always kept, whatever the filter says. It is drawn as the
     * one in use and the Use button reads its state, so a shelf that could hide
     * it would be a screen that cannot say what the deck is in.
     */
    private void rebuildStyles()
    {
        DeckBoxStyle keep = current();
        DeckBoxStyle worn = worn();
        styles.clear();
        // Enum order, which is the Master Duel texture id order -- the same
        // shelf order the shop uses, so a case is in the same place in both.
        for(DeckBoxStyle style : DeckBoxStyle.values())
        {
            if(showUnowned || owns(style) || style == worn)
            {
                styles.add(style);
            }
        }
        int at = keep == null ? -1 : styles.indexOf(keep);
        // Falls back to the worn case rather than to slot zero: hiding the
        // locked case you had highlighted should leave you somewhere meaningful.
        selected = at >= 0 ? at : Math.max(0, styles.indexOf(worn));
        scroll = 0;
        clampScroll();
        refreshUseButton();
    }

    /**
     * One case's art, blitted whole.
     * <p>
     * Public and static because the deck editor draws the current case on its
     * own panel with it. A deck case is a tight 422x512 crop, so unlike a sleeve
     * there is no letterbox window to sample through.
     */
    public static void drawBox(GuiGraphicsExtractor graphics, DeckBoxStyle style,
        int x, int y, int width, int height)
    {
        DdBlitUtil.fullBlit(graphics, HubTextures.deckBox(style), x, y, width, height);
    }

    private static void drawBox(GuiGraphicsExtractor graphics, DeckBoxStyle style,
        int x, int y, int width, int height, int tint)
    {
        DdBlitUtil.fullBlit(graphics, HubTextures.deckBox(style), x, y, width, height, tint);
    }

    // ---- state ----

    private static boolean owns(DeckBoxStyle style)
    {
        return EditorState.profile().ownsDeckBox(style);
    }

    private DeckBoxStyle current()
    {
        return styles.isEmpty() ? null
            : styles.get(Math.max(0, Math.min(selected, styles.size() - 1)));
    }

    private DeckBoxStyle worn()
    {
        return EditorState.deck().deckBox();
    }

    // ---- geometry, the shop's ----

    private Layout layout()
    {
        return Layout.of(LAYOUT);
    }

    private int pad()
    {
        return layout().i("pad", 8);
    }

    private int leftWidth()
    {
        return layout().i("left.width", 116);
    }

    private int bottomHeight()
    {
        return layout().i("bottom.height", 46);
    }

    private int cellW()
    {
        int min = layout().i("grid.cellWidth", 32);
        int max = layout().i("grid.cellWidthMax", 64);
        int gap = layout().i("grid.gap", 4);
        int cols = gridColumns();
        int rows = gridRows();
        // The REGION, never gridLeft(): that centres using this method, so
        // asking it here recurses until the stack runs out.
        int roomW = (width - gridRegionLeft() - pad() - gap * (cols - 1)) / Math.max(1, cols);
        int roomH = Math.round(((height - bottomHeight() - 12 - gridTop()
            - gap * (rows - 1)) / (float)Math.max(1, rows)) * HubWidgets.DECK_BOX_ASPECT);
        return Math.max(min, Math.min(max, Math.min(roomW, roomH)));
    }

    private int baseCellW()
    {
        return layout().i("grid.cellWidth", 32);
    }

    private int baseCellH()
    {
        return Math.round(baseCellW() / HubWidgets.DECK_BOX_ASPECT);
    }

    private int cellH()
    {
        return Math.round(cellW() / HubWidgets.DECK_BOX_ASPECT);
    }

    private int gridRegionLeft()
    {
        return leftWidth() + pad() * 2;
    }

    private int gridLeft()
    {
        int region = width - gridRegionLeft() - pad();
        int gap = layout().i("grid.gap", 4);
        int used = gridColumns() * cellW() + gap * (gridColumns() - 1);
        return gridRegionLeft() + Math.max(0, (region - used) / 2);
    }

    private int gridTop()
    {
        return pad() + layout().i("grid.top", 22);
    }

    private int gridColumns()
    {
        int available = width - gridRegionLeft() - pad();
        int cell = baseCellW() + layout().i("grid.gap", 4);
        int fits = Math.max(1, available / Math.max(1, cell));
        return Math.max(1, Math.min(Math.min(layout().i("grid.maxColumns", 12), fits),
            Math.max(1, styles.size())));
    }

    private int gridRows()
    {
        int room = height - bottomHeight() - 12 - gridTop();
        int gap = layout().i("grid.gap", 4);
        int fits = Math.max(1, (room + gap) / (baseCellH() + gap));
        int needed = (styles.size() + gridColumns() - 1) / Math.max(1, gridColumns());
        return Math.max(1, Math.min(fits, Math.max(1, needed)));
    }

    private int closeLeft()
    {
        return width - pad() - layout().i("close.width", 70);
    }

    @Override
    protected void init()
    {
        // The sort row, in the band above the shelf. The sleeve shop puts its
        // balance plate at the right of this band and these chips at the left;
        // matching that keeps the three screens reading as one.
        int sortY = pad() + 1;
        int sortX = gridRegionLeft();
        for(SortKey key : SortKey.values())
        {
            int chipW = Math.max(22, font.width(key.label) + 12);
            addRenderableWidget(new DeckEditorScreen.ChipButton(sortX, sortY, chipW, SORT_ROW_H,
                Component.literal(key.label), () -> sortKey == key, pressed ->
            {
                sortKey = key;
                applySort();
                rebuildWidgets();
            }));
            sortX += chipW + 3;
        }
        // The arrow reads DOWN the list: ascending means the values increase as
        // you read downward, so ascending is the DOWN arrow.
        addRenderableWidget(new HubWidgets.IconButton(sortX + 2, sortY, SORT_ROW_H, SORT_ROW_H,
            descending ? HubTextures.SORT_UP : HubTextures.SORT_DOWN,
            Component.literal(descending ? "Descending" : "Ascending"), pressed ->
        {
            descending = !descending;
            applySort();
            rebuildWidgets();
        }));

        int useY = height - bottomHeight() - 30;
        useButton = new HubWidgets.TextureButton(pad(), useY, leftWidth(), 20,
            Component.literal("Use"), pressed -> use());
        addRenderableWidget(useButton);
        addRenderableWidget(new HubWidgets.TextureButton(closeLeft(), height - 26,
            layout().i("close.width", 70), 20, Component.literal("Back"), pressed -> onClose()));

        // The deck editor's own Unowned chip, the same class and therefore the
        // same art and lit state. It sits in the band above the shelf, which is
        // where the shop puts its DP plate -- the two screens keep their status
        // furniture on the same line.
        int chipW = UNOWNED_CHIP_W;
        int chipH = UNOWNED_CHIP_H;
        addRenderableWidget(new DeckEditorScreen.ChipButton(width - pad() - chipW,
            gridTop() - chipH - 4, chipW, chipH, Component.literal("Unowned"),
            () -> showUnowned, pressed ->
        {
            showUnowned = !showUnowned;
            rebuildStyles();
        }));
        applySort();
        refreshUseButton();
        // A resize fits more per row, which can leave the scroll past the end of
        // a list that now needs fewer rows.
        clampScroll();
    }

    private void clampScroll()
    {
        int columns = gridColumns();
        int maxScroll = Math.max(0, (styles.size() + columns - 1) / columns - gridRows());
        scroll = Math.max(0, Math.min(maxScroll, scroll));
        // Keep the selection on screen, so opening on a case far down the shelf
        // does not open on a grid that is not showing it.
        int row = selected / Math.max(1, columns);
        if(row < scroll)
        {
            scroll = row;
        }
        else if(row >= scroll + gridRows())
        {
            scroll = row - gridRows() + 1;
        }
    }


    /**
     * Puts the shelf in {@link #sortKey} order. Touches nothing else.
     * <p>
     * Separate from {@link #applySort()} because the two callers want different
     * things done with the selection: opening the screen has no selection worth
     * keeping, and carrying an arbitrary one across would scroll the shelf to
     * wherever that case happened to land.
     */
    private void sortStyles()
    {
        java.util.Comparator<DeckBoxStyle> order = switch(sortKey)
        {
            case COLOR -> DeckBoxStyle::compareByColour;
            case NAME -> java.util.Comparator.comparing(
                DeckBoxStyle::label, String.CASE_INSENSITIVE_ORDER);
            // The enum's own order IS the DeckCase id order.
            case ID -> java.util.Comparator.comparingInt(Enum::ordinal);
        };
        // Ties broken by id in every mode, so the order is total and a re-sort
        // cannot shuffle equal entries about.
        order = order.thenComparingInt(Enum::ordinal);
        styles.sort(descending ? order.reversed() : order);
    }

    /** Re-orders the shelf, keeping the selection on the same CASE. */
    private void applySort()
    {
        DeckBoxStyle keep = current();
        sortStyles();
        selected = 0;
        for(int i = 0; i < styles.size(); i++)
        {
            if(styles.get(i) == keep)
            {
                selected = i;
                break;
            }
        }
        clampScroll();
    }
    private void refreshUseButton()
    {
        if(useButton == null)
        {
            return;
        }
        DeckBoxStyle style = current();
        if(style == null)
        {
            useButton.setMessage(Component.literal("Use"));
            useButton.active = false;
            return;
        }
        boolean owned = owns(style);
        useButton.setMessage(Component.literal(
            style == worn() ? "In use" : owned ? "Use" : "Locked"));
        useButton.active = owned && style != worn();
    }

    private void use()
    {
        DeckBoxStyle style = current();
        if(style == null)
        {
            return;
        }
        if(!owns(style))
        {
            notice = "Buy this in the deck box shop";
            return;
        }
        notice = "";
        DeckList deck = EditorState.deck();
        deck.setDeckBox(style);
        ClientPlayNetworking.send(new ProfilePayloads.SetDeckBox(deck.name(), style.name()));
    }

    // ---- input ----

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick)
    {
        if(super.mouseClicked(event, doubleClick))
        {
            return true;
        }
        int index = styleAt(event.x(), event.y());
        if(index >= 0)
        {
            selected = index;
            notice = "";
            refreshUseButton();
            // A click SELECTS. It does not equip.
            //
            // It used to do both, on the reasoning that the trip to the Use
            // button was a step nobody wants -- and the result was that every
            // case you clicked immediately became the worn one, so the status
            // read "In use" whatever you touched and the button was greyed out
            // permanently. Selection and use have to be different things for
            // either of them to mean anything.
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double delta)
    {
        scroll -= (int)Math.signum(delta);
        int columns = gridColumns();
        int maxScroll = Math.max(0, (styles.size() + columns - 1) / columns - gridRows());
        scroll = Math.max(0, Math.min(maxScroll, scroll));
        return true;
    }

    /**
     * Which cell is under the cursor, or -1.
     * <p>
     * Bounded BEFORE the divide: a cast to int truncates toward zero, so a click
     * above the grid gives {@code (int)(-0.4) == 0} and a {@code row < 0} test
     * never fires.
     */
    private int styleAt(double mouseX, double mouseY)
    {
        if(mouseX < gridLeft() || mouseY < gridTop())
        {
            return -1;
        }
        int gap = layout().i("grid.gap", 4);
        int column = (int)((mouseX - gridLeft()) / (cellW() + gap));
        int row = (int)((mouseY - gridTop()) / (cellH() + gap));
        if(column >= gridColumns() || row >= gridRows())
        {
            return -1;
        }
        int index = (row + scroll) * gridColumns() + column;
        return index < styles.size() ? index : -1;
    }

    // ---- rendering ----

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
        float partialTick)
    {
        // fillGradient, not extractBackground: that blurs in 26.2, and a screen
        // opening over one that already asked for it took the client down.
        graphics.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
        refreshUseButton();

        renderPreview(graphics);
        renderGrid(graphics, mouseX, mouseY);
        renderDetails(graphics);
        renderNotice(graphics);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void renderPreview(GuiGraphicsExtractor graphics)
    {
        int pad = pad();
        int leftW = leftWidth();
        int panelH = height - bottomHeight() - pad - 38;
        NineSlice.draw(graphics, HubTextures.PANEL, pad, pad, leftW, panelH);

        DeckBoxStyle style = current();
        if(style == null)
        {
            return;
        }

        int rows = 2;
        int rowH = 12;
        int boxH = rows * rowH + 7;

        int artW = leftW - 16;
        int artH = Math.round(artW / HubWidgets.DECK_BOX_ASPECT);
        int room = panelH - boxH - 22;
        if(artH > room)
        {
            artH = Math.max(16, room);
            artW = Math.round(artH * HubWidgets.DECK_BOX_ASPECT);
        }
        drawBox(graphics, style, pad + 8 + (leftW - 16 - artW) / 2, pad + 8, artW, artH);

        int boxX = pad + 6;
        int boxW = leftW - 12;
        int boxY = pad + 8 + artH + 6;
        NineSlice.draw(graphics, HubTextures.PANEL_INSET, boxX, boxY, boxW, boxH);

        boolean owned = owns(style);
        int rowY = boxY + 5;
        detail(graphics, boxX, boxW, rowY, "Status",
            style == worn() ? "IN USE" : owned ? "OWNED" : "LOCKED",
            style == worn() ? MenuInk.title() : owned ? 0xFF7CE38B : 0xFFFF8A80);
        rowY += rowH;
        // Against the whole catalogue, not the shelf. With locked cases hidden
        // the shelf IS what you own, so "12 of 12" would be a progress line that
        // always reads complete.
        detail(graphics, boxX, boxW, rowY, "Owned",
            ownedCount() + "/" + DeckBoxStyle.values().length, 0xFF9AA2B2);
    }

    private void detail(GuiGraphicsExtractor graphics, int x, int width, int y, String label,
        String value, int colour)
    {
        graphics.text(font, label, x + 6, y, 0xFF6E7686, true);
        int room = width - 12 - font.width(label) - 6;
        // CUT FROM THE END, AND SAID SO.
        //
        // This used to drop characters off the FRONT until the value fitted,
        // which for a number is not a shortened value but a WRONG one: the
        // collection line read "1 of 260", did not fit by a few pixels, and was
        // drawn as " of 260" -- a figure that looks complete and says nothing.
        // Trailing dots cannot be mistaken for a whole number.
        String shown = value;
        if(font.width(shown) > room)
        {
            while(font.width(shown + "..") > room && shown.length() > 1)
            {
                shown = shown.substring(0, shown.length() - 1);
            }
            shown = shown + "..";
        }
        graphics.text(font, shown, x + width - 6 - font.width(shown), y, colour, true);
    }

    /** How many of the CATALOGUE are owned, whatever the shelf is showing. */
    private int ownedCount()
    {
        int owned = 0;
        for(DeckBoxStyle style : DeckBoxStyle.values())
        {
            if(owns(style))
            {
                owned++;
            }
        }
        return owned;
    }

    private void renderGrid(GuiGraphicsExtractor graphics, int mouseX, int mouseY)
    {
        int columns = gridColumns();
        int rows = gridRows();
        int cellW = cellW();
        int cellH = cellH();
        int gap = layout().i("grid.gap", 4);
        int left = gridLeft();
        int top = gridTop();
        DeckBoxStyle worn = worn();

        NineSlice.draw(graphics, HubTextures.PANEL_INSET, left - 4, top - 4,
            columns * (cellW + gap) + 4, rows * (cellH + gap) + 4);

        for(int row = 0; row < rows; row++)
        {
            for(int column = 0; column < columns; column++)
            {
                int index = (row + scroll) * columns + column;
                if(index >= styles.size())
                {
                    continue;
                }
                DeckBoxStyle style = styles.get(index);
                int x = left + column * (cellW + gap);
                int y = top + row * (cellH + gap);
                boolean owned = owns(style);

                // Locked cases are dimmed rather than hidden: what is buyable is
                // worth seeing, and the shop is one screen away.
                drawBox(graphics, style, x, y, cellW, cellH,
                    owned ? DdBlitUtil.NO_TINT : DdBlitUtil.alpha(0.35F));
                if(style == worn)
                {
                    // Gold under the one actually on the deck, so "in use" and
                    // "highlighted" cannot be confused.
                    DdBlitUtil.fullBlit(graphics, DuelTextures.WHITE, x, y + cellH - 2, cellW, 2,
                        DdBlitUtil.tint(0.84F, 0.67F, 0.33F, 1F));
                }
                if(index == selected)
                {
                    drawRing(graphics, x - 2, y - 2, cellW + 4, cellH + 4,
                        DdBlitUtil.tint(0.42F, 0.72F, 1F, 1F), 2);
                }
                else if(mouseX >= x && mouseX < x + cellW && mouseY >= y && mouseY < y + cellH)
                {
                    drawRing(graphics, x - 1, y - 1, cellW + 2, cellH + 2,
                        DdBlitUtil.tint(0.42F, 0.72F, 1F, 0.35F), 1);
                }
            }
        }

        int contentRows = (styles.size() + columns - 1) / columns;
        int overflow = Math.max(0, contentRows - rows);
        if(overflow > 0)
        {
            int trackX = left + columns * (cellW + gap) + 2;
            int trackH = rows * (cellH + gap) - gap;
            int thumbH = Math.max(12, trackH * rows / Math.max(1, contentRows));
            int thumbY = top + (trackH - thumbH) * scroll / overflow;
            NineSlice.draw(graphics, HubTextures.SCROLLBAR, trackX, top, 4, trackH, 0, 2);
            NineSlice.draw(graphics, HubTextures.SCROLLBAR, trackX, thumbY, 4, thumbH, 1, 2);
        }
    }

    /**
     * Bottom: the case's full name and what it is.
     * <p>
     * The whole reason the tiles carry no text. "Roar of the Gladiator Beasts"
     * has room here and never had it under a 66 pixel tile.
     */
    private void renderDetails(GuiGraphicsExtractor graphics)
    {
        int pad = pad();
        int bottomH = bottomHeight();
        int y = height - bottomH - 4;
        int right = closeLeft() - layout().i("close.gap", 4);
        NineSlice.draw(graphics, HubTextures.PANEL, pad, y, right - pad, bottomH);

        DeckBoxStyle style = current();
        if(style == null)
        {
            return;
        }
        int textY = y + 7;
        graphics.text(font, style.label(), pad + 8, textY, MenuInk.label(), MenuInk.shadow());

        boolean owned = owns(style);
        String state = style == worn() ? "IN USE" : owned ? "OWNED" : "LOCKED";
        graphics.text(font, state, right - 8 - font.width(state), textY,
            style == worn() ? MenuInk.title() : owned ? 0xFF7CE38B : 0xFFFF8A80, true);

        String blurb = owned
            ? "Shown on this deck in the hub and on the duel table."
            : "Not owned yet. Deck cases are sold in the deck box shop.";
        int descriptionY = textY + 14;
        int descriptionX = pad + 6;
        int descriptionW = Math.max(40, right - 6 - descriptionX);
        NineSlice.draw(graphics, HubTextures.PANEL_INSET, descriptionX, descriptionY - 3,
            descriptionW, bottomH - 22);
        for(net.minecraft.util.FormattedCharSequence line
            : font.split(Component.literal(blurb), descriptionW - 12))
        {
            graphics.text(font, line, descriptionX + 6, descriptionY, MenuInk.body());
            descriptionY += 10;
        }
    }

    private void renderNotice(GuiGraphicsExtractor graphics)
    {
        if(notice.isEmpty())
        {
            return;
        }
        graphics.text(font, notice, gridLeft(), height - bottomHeight() - 16, 0xFFFF8A80, true);
    }

    /** A hollow rectangle, drawn as four bars off the white pixel. */
    private void drawRing(GuiGraphicsExtractor graphics, int x, int y, int w, int h,
        int tint, int thickness)
    {
        DdBlitUtil.fullBlit(graphics, DuelTextures.WHITE, x, y, w, thickness, tint);
        DdBlitUtil.fullBlit(graphics, DuelTextures.WHITE, x, y + h - thickness, w, thickness, tint);
        DdBlitUtil.fullBlit(graphics, DuelTextures.WHITE, x, y, thickness, h, tint);
        DdBlitUtil.fullBlit(graphics, DuelTextures.WHITE, x + w - thickness, y, thickness, h, tint);
    }

    @Override
    public void onClose()
    {
        if(minecraft != null)
        {
            minecraft.setScreenAndShow(parent);
        }
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
