package de.cas_ual_ty.dueldimension.clientutil.hub;

import de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil;
import de.cas_ual_ty.dueldimension.clientutil.DuelTextures;
import de.cas_ual_ty.dueldimension.clientutil.layout.Layout;
import de.cas_ual_ty.dueldimension.duel.profile.DeckBoxStyle;
import de.cas_ual_ty.dueldimension.shop.ShopMessages;
import de.cas_ual_ty.dueldimension.shop.ShopStock;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The deck box shop, arranged as the sleeve shop arranges itself: the
 * highlighted product large down the left with what it costs, a grid of
 * everything on sale filling the middle, and a details bar underneath.
 *
 * <h2>Why it was rebuilt</h2>
 * It used to be a fixed three-column grid inside a small panel, which was a
 * reasonable shape for nine deck boxes and a poor one for fifty: the tiles were
 * small, the scrollbar did most of the work, and it looked nothing like the
 * counter next door selling the other cosmetic. This is the same furniture as
 * {@link SleeveShopScreen} and {@link CardShopScreen} for the reason those two
 * match each other — they are counters of one shop, and a player should not have
 * to learn a third layout to buy a third kind of thing.
 * <p>
 * One test went with the old layout. {@code DeckBoxTileLayoutTest} swept every
 * tile size looking for the case art colliding with the name printed under it --
 * a real bug, and one that only appeared at some window sizes. A tile here
 * carries no text at all: the name is in the details bar, the price is in the
 * details bar, and the only thing drawn in a cell is the box and its ring. The
 * collision is designed out rather than merely untested, which is why the test
 * was deleted instead of ported.
 *
 * <h2>What the screen decides, which is nothing</h2>
 * The balance shown is the server's, pushed on open and after every purchase.
 * The price shown is the server's, sent with the stock. What is owned is read
 * off the profile the server synced. A purchase sends the style's id and nothing
 * else, so this screen cannot ask for a discount, cannot buy a box that is not
 * stock, and cannot claim to own one. Every check here exists to avoid a
 * pointless round trip, never to make the decision.
 *
 * <h2>Drawing a deck box</h2>
 * Unlike a sleeve, a deck box PNG is <b>already a tight crop</b> — 422x512 with
 * transparency around the case — so it is blitted whole rather than sampled
 * through a letterbox window. {@link HubWidgets#DECK_BOX_ASPECT} is that shape, and it is what
 * the cells and the preview are built against; using the card aspect here would
 * squeeze every box into a card's proportions.
 */
public class DeckBoxShopScreen extends Screen
{
    /**
     * The sleeve shop's layout, deliberately.
     * <p>
     * Same panel widths, same gaps, same close button: two shops that look alike
     * should be measured alike, and a second config file would let them drift
     * apart one number at a time. The one thing that differs is the cell shape,
     * and that comes from {@link HubWidgets#DECK_BOX_ASPECT} rather than from the layout.
     */
    private static final String LAYOUT = "sleeve_shop";

    /**
     * One thing on sale: the style, and what the server says it costs.
     * <p>
     * Resolved from the id once, at open, rather than per frame: the wire
     * carries names because an enum index is not safe to store or to send across
     * builds, but a screen redrawing sixty times a second should not be walking
     * the enum to turn a string back into a constant.
     */
    private record Entry(DeckBoxStyle style, int price)
    {
    }

    private final List<Entry> offers;
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

    /** Kept so its label and its enabled state can follow the selection. */
    private HubWidgets.TextureButton buyButton;

    public static void open(int points, List<ShopStock.DeckBoxOffer> offers)
    {
        // One balance, one place to keep it. The card shop already holds what
        // the server last said and SyncPoints already updates it, so a purchase
        // made at any counter is reflected at all of them.
        CardShopScreen.setPoints(points);
        Minecraft.getInstance().gui.setScreen(new DeckBoxShopScreen(offers));
    }

    private DeckBoxShopScreen(List<ShopStock.DeckBoxOffer> stock)
    {
        super(Component.literal("Deck Box Shop"));
        this.offers = new ArrayList<>();
        for(ShopStock.DeckBoxOffer offer : stock)
        {
            DeckBoxStyle style = DeckBoxStyle.known(offer.deckBox());
            if(style != null)
            {
                // An id this build does not know is dropped rather than drawn as
                // a blank cell that cannot be bought.
                offers.add(new Entry(style, offer.price()));
            }
        }
    }

    // ---- what the player owns and can afford ----

    private static boolean owns(DeckBoxStyle style)
    {
        // The profile the server synced, which is the same object the deck
        // editor's picker reads, so the shop and the picker cannot disagree.
        return EditorState.profile().ownsDeckBox(style);
    }

    private boolean isCreative()
    {
        return minecraft != null && minecraft.player != null && minecraft.player.isCreative();
    }

    private int points()
    {
        return CardShopScreen.points();
    }

    private Entry current()
    {
        return offers.isEmpty() ? null
            : offers.get(Math.max(0, Math.min(selected, offers.size() - 1)));
    }

    // ---- geometry ----

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

    /**
     * The tile SCALES to the space instead of staying at its authored size, so a
     * wide window shows fewer legible boxes rather than many tiny ones.
     */
    private int cellW()
    {
        int min = layout().i("grid.cellWidth", 32);
        int max = layout().i("grid.cellWidthMax", 64);
        int gap = layout().i("grid.gap", 4);
        int cols = gridColumns();
        int rows = gridRows();
        // The REGION, never gridLeft(): that centres the grid using this very
        // method, so asking it here is infinite recursion -- a StackOverflowError
        // the moment the screen opens. The sleeve shop carries the same warning.
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

    /** The left edge of the grid REGION; the tiles are centred within it below. */
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
        // Also the REGION, for the same reason gridLeft() is avoided in cellW().
        int available = width - gridRegionLeft() - pad();
        int cell = baseCellW() + layout().i("grid.gap", 4);
        int fits = Math.max(1, available / Math.max(1, cell));
        return Math.max(1, Math.min(Math.min(layout().i("grid.maxColumns", 12), fits),
            Math.max(1, offers.size())));
    }

    private int gridRows()
    {
        int bottom = height - bottomHeight() - 12;
        int room = bottom - gridTop();
        int gap = layout().i("grid.gap", 4);
        int fits = Math.max(1, (room + gap) / (baseCellH() + gap));
        int needed = (offers.size() + gridColumns() - 1) / Math.max(1, gridColumns());
        return Math.max(1, Math.min(fits, Math.max(1, needed)));
    }

    /** Asked for by the Close button AND by the detail box that stops short of it. */
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

        int buyY = height - bottomHeight() - 30;
        buyButton = new HubWidgets.TextureButton(pad(), buyY, leftWidth(), 20,
            Component.literal("Buy"), pressed -> buy());
        addRenderableWidget(buyButton);
        addRenderableWidget(new HubWidgets.TextureButton(closeLeft(), height - 26,
            layout().i("close.width", 70), 20, Component.literal("Close"), pressed -> onClose()));
        // The stock arrives in id order and the default is colour, so the
        // order is applied once the screen has a size to scroll within.
        applySort();
        refreshBuyButton();
        // init() runs again on every resize, and a wider window fits more per
        // row -- which can put the scroll position past the end of a list that
        // now needs fewer rows, leaving the grid drawing nothing.
        clampScroll();
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
        // Built over the STYLE and then lifted onto the entry: the shop's list
        // carries a price alongside each case, and the order is a property of
        // the case.
        java.util.Comparator<DeckBoxStyle> byStyle = switch(sortKey)
        {
            case COLOR -> DeckBoxStyle::compareByColour;
            case NAME -> java.util.Comparator.comparing(
                DeckBoxStyle::label, String.CASE_INSENSITIVE_ORDER);
            // The enum's own order IS the DeckCase id order.
            case ID -> java.util.Comparator.comparingInt(Enum::ordinal);
        };
        // Ties broken by id in every mode, so the order is total and a re-sort
        // cannot shuffle equal entries about.
        byStyle = byStyle.thenComparingInt(Enum::ordinal);
        java.util.Comparator<Entry> order =
            java.util.Comparator.comparing(Entry::style, byStyle);
        offers.sort(descending ? order.reversed() : order);
    }

    /** Re-orders the shelf, keeping the selection on the same CASE. */
    private void applySort()
    {
        // current() is an Entry here, not a style: the shop's list carries a
        // price alongside each case.
        DeckBoxStyle keep = current() == null ? null : current().style();
        sortStyles();
        selected = 0;
        for(int i = 0; i < offers.size(); i++)
        {
            if(offers.get(i).style() == keep)
            {
                selected = i;
                break;
            }
        }
        clampScroll();
    }
    private void clampScroll()
    {
        int columns = gridColumns();
        int maxScroll = Math.max(0, (offers.size() + columns - 1) / columns - gridRows());
        scroll = Math.max(0, Math.min(maxScroll, scroll));
    }

    /**
     * Puts the current price, or the reason there is none, on the Buy button.
     * <p>
     * Every frame rather than only on a selection change: the balance and the
     * ownership both arrive from the server on their own schedule, and setting a
     * label on a widget that already exists costs nothing where rebuilding the
     * widget list from a network thread's timing would be a real hazard.
     */
    private void refreshBuyButton()
    {
        if(buyButton == null)
        {
            return;
        }
        Entry entry = current();
        if(entry == null)
        {
            buyButton.setMessage(Component.literal("Buy"));
            buyButton.active = false;
            return;
        }
        boolean owned = owns(entry.style());
        buyButton.setMessage(Component.literal(owned ? "Owned"
            : isCreative() ? "Buy (free)"
            : "Buy  " + entry.price()));
        // Greyed rather than hidden: a bought box is still worth pointing at,
        // and a button that vanishes moves everything under it.
        buyButton.active = !owned;
    }

    private void buy()
    {
        Entry entry = current();
        if(entry == null)
        {
            return;
        }
        if(owns(entry.style()))
        {
            // Refused here as well as on the server. This saves a round trip;
            // the server's check is the one that matters.
            notice = "Already owned";
            return;
        }
        if(!isCreative() && points() < entry.price())
        {
            notice = "Not enough DP";
            return;
        }
        notice = "";
        // Only which box. The price, the balance and the entitlement are all
        // looked up on the server.
        ClientPlayNetworking.send(new ShopMessages.BuyDeckBox(entry.style().name()));
    }

    // ---- input ----

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick)
    {
        // Widgets first: the grid covers most of the screen, and a control drawn
        // over it should be what receives a click on it.
        if(super.mouseClicked(event, doubleClick))
        {
            return true;
        }
        int index = boxAt(event.x(), event.y());
        if(index >= 0)
        {
            selected = index;
            notice = "";
            refreshBuyButton();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double delta)
    {
        scroll -= (int)Math.signum(delta);
        clampScroll();
        return true;
    }

    /**
     * Which cell is under the cursor, or -1.
     * <p>
     * Bounded BEFORE the divide, which is not fussiness: a cast to int truncates
     * toward zero, so a click above the grid gives {@code (int)(-0.4) == 0} and a
     * {@code row < 0} test never fires. The card shop shipped that bug once.
     */
    private int boxAt(double mouseX, double mouseY)
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
        return index < offers.size() ? index : -1;
    }

    // ---- rendering ----

    @Override
    public void extractRenderState(GuiGraphicsExtractor poseStack, int mouseX, int mouseY,
        float partialTick)
    {
        // fillGradient, not extractBackground: that BLURS in 26.2, once per
        // frame, and a screen opening over one that already asked for it took
        // the client down. Same decision as CardShopScreen and SleeveShopScreen.
        poseStack.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);

        // Before super, so the widget is described with the label it should have
        // this frame rather than last frame's.
        refreshBuyButton();

        renderPreview(poseStack);
        renderGrid(poseStack, mouseX, mouseY);
        renderDetails(poseStack);
        renderNotice(poseStack);
        renderBalance(poseStack);

        super.extractRenderState(poseStack, mouseX, mouseY, partialTick);
    }

    /** Left: the highlighted box, large, with what it costs. */
    private void renderPreview(GuiGraphicsExtractor poseStack)
    {
        int pad = pad();
        int leftW = leftWidth();
        int panelH = height - bottomHeight() - pad - 38;
        NineSlice.draw(poseStack, HubTextures.PANEL, pad, pad, leftW, panelH);

        Entry entry = current();
        if(entry == null)
        {
            return;
        }

        // Two rows, not three. "Contents: 1 DECK BOX" said nothing a picture of
        // one deck box does not: the sleeve shop lists it because a sleeve pack
        // could plausibly hold several, and this never could.
        int rows = 2;
        int rowH = 12;
        int boxH = rows * rowH + 7;

        // The art takes the width it is given unless the panel is too short, in
        // which case the HEIGHT is what is scarce and the width follows. Derived
        // rather than fixed because the panel's height comes from the window's.
        int artW = leftW - 16;
        int artH = Math.round(artW / HubWidgets.DECK_BOX_ASPECT);
        int room = panelH - boxH - 22;
        if(artH > room)
        {
            artH = Math.max(16, room);
            artW = Math.round(artH * HubWidgets.DECK_BOX_ASPECT);
        }
        drawBox(poseStack, entry.style(), pad + 8 + (leftW - 16 - artW) / 2, pad + 8,
            artW, artH, DdBlitUtil.NO_TINT);

        int boxX = pad + 6;
        int boxW = leftW - 12;
        int boxY = pad + 8 + artH + 6;
        NineSlice.draw(poseStack, HubTextures.PANEL_INSET, boxX, boxY, boxW, boxH);

        boolean owned = owns(entry.style());
        int rowY = boxY + 5;
        detail(poseStack, boxX, boxW, rowY, "Price",
            owned ? "OWNED" : isCreative() ? "FREE" : entry.price() + " DP",
            owned ? 0xFF7CE38B : isCreative() ? 0xFF7CE38B : MenuInk.title());
        rowY += rowH;
        detail(poseStack, boxX, boxW, rowY, "Collected", ownedCount() + "/" + offers.size(),
            0xFF9AA2B2);
    }

    /**
     * One row of the fact list: label left, value right. The value is shortened
     * from the FRONT when it will not fit, so what survives distinguishes it.
     */
    private void detail(GuiGraphicsExtractor poseStack, int x, int width, int y, String label,
        String value, int colour)
    {
        poseStack.text(font, label, x + 6, y, 0xFF6E7686, true);
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
        poseStack.text(font, shown, x + width - 6 - font.width(shown), y, colour, true);
    }

    private int ownedCount()
    {
        int owned = 0;
        for(Entry entry : offers)
        {
            if(owns(entry.style()))
            {
                owned++;
            }
        }
        return owned;
    }

    /** Centre: every box on sale, the highlighted one ringed. */
    private void renderGrid(GuiGraphicsExtractor poseStack, int mouseX, int mouseY)
    {
        int columns = gridColumns();
        int rows = gridRows();
        int cellW = cellW();
        int cellH = cellH();
        int gap = layout().i("grid.gap", 4);
        int left = gridLeft();
        int top = gridTop();

        NineSlice.draw(poseStack, HubTextures.PANEL_INSET, left - 4, top - 4,
            columns * (cellW + gap) + 4, rows * (cellH + gap) + 4);

        for(int row = 0; row < rows; row++)
        {
            for(int column = 0; column < columns; column++)
            {
                int index = (row + scroll) * columns + column;
                if(index >= offers.size())
                {
                    continue;
                }
                Entry entry = offers.get(index);
                int x = left + column * (cellW + gap);
                int y = top + row * (cellH + gap);
                boolean owned = owns(entry.style());

                // An owned box is drawn faint, so the eye lands on what is still
                // for sale, and marked with a gold bar underneath so "faint"
                // cannot be misread as "cannot afford".
                drawBox(poseStack, entry.style(), x, y, cellW, cellH,
                    owned ? DdBlitUtil.alpha(0.45F) : DdBlitUtil.NO_TINT);
                if(owned)
                {
                    DdBlitUtil.fullBlit(poseStack, DuelTextures.WHITE, x, y + cellH - 2, cellW, 2,
                        DdBlitUtil.tint(0.84F, 0.67F, 0.33F, 1F));
                    drawCollectedTick(poseStack, x, y, cellW, cellH);
                }
                if(index == selected)
                {
                    // A ring rather than a tint, so the art inside stays
                    // readable -- the reference marks focus the same way.
                    drawRing(poseStack, x - 2, y - 2, cellW + 4, cellH + 4,
                        DdBlitUtil.tint(0.42F, 0.72F, 1F, 1F), 2);
                }
                else if(mouseX >= x && mouseX < x + cellW && mouseY >= y && mouseY < y + cellH)
                {
                    drawRing(poseStack, x - 1, y - 1, cellW + 2, cellH + 2,
                        DdBlitUtil.tint(0.42F, 0.72F, 1F, 0.35F), 1);
                }
            }
        }

        // The grid scrolls, so it says so when there is anything below.
        int contentRows = (offers.size() + columns - 1) / columns;
        int overflow = Math.max(0, contentRows - rows);
        if(overflow > 0)
        {
            int trackX = left + columns * (cellW + gap) + 2;
            int trackH = rows * (cellH + gap) - gap;
            int thumbH = Math.max(12, trackH * rows / Math.max(1, contentRows));
            int thumbY = top + (trackH - thumbH) * scroll / overflow;
            NineSlice.draw(poseStack, HubTextures.SCROLLBAR, trackX, top, 4, trackH, 0, 2);
            NineSlice.draw(poseStack, HubTextures.SCROLLBAR, trackX, thumbY, 4, thumbH, 1, 2);
        }
    }

    /** Bottom: the box's name, its state, and where it is actually used. */
    private void renderDetails(GuiGraphicsExtractor poseStack)
    {
        int pad = pad();
        int bottomH = bottomHeight();
        int y = height - bottomH - 4;
        // The box stops where the Close button starts, so the two read as
        // separate pieces of furniture rather than one with a button embedded.
        int right = closeLeft() - layout().i("close.gap", 4);
        NineSlice.draw(poseStack, HubTextures.PANEL, pad, y, right - pad, bottomH);

        Entry entry = current();
        if(entry == null)
        {
            return;
        }
        int textY = y + 7;
        poseStack.text(font, entry.style().label(), pad + 8, textY, MenuInk.label(), MenuInk.shadow());

        boolean owned = owns(entry.style());
        String state = owned ? "OWNED" : isCreative() ? "FREE" : entry.price() + " DP";
        poseStack.text(font, state, right - 8 - font.width(state), textY,
            owned ? 0xFF7CE38B : isCreative() ? 0xFF7CE38B : MenuInk.title(), MenuInk.shadow());

        // What the product actually does, which a picture of a case does not say.
        String blurb = owned
            ? "Choose this on a deck in the deck editor."
            : "Holds one of your decks. Chosen per deck in the deck editor.";
        int descriptionY = textY + 14;
        int descriptionX = pad + 6;
        int descriptionW = Math.max(40, right - 6 - descriptionX);
        NineSlice.draw(poseStack, HubTextures.PANEL_INSET, descriptionX, descriptionY - 3,
            descriptionW, bottomH - 22);
        for(net.minecraft.util.FormattedCharSequence line
            : font.split(Component.literal(blurb), descriptionW - 12))
        {
            poseStack.text(font, line, descriptionX + 6, descriptionY, MenuInk.body());
            descriptionY += 10;
        }
    }

    /**
     * Why the last click did nothing.
     * <p>
     * In the gap between the grid and the detail box rather than inside it: that
     * box is 46 tall, so a line at {@code height - 18} would be written across
     * the description it is not part of.
     */
    private void renderNotice(GuiGraphicsExtractor poseStack)
    {
        if(notice.isEmpty())
        {
            return;
        }
        poseStack.text(font, notice, gridLeft(), height - bottomHeight() - 16, 0xFFFF8A80, true);
    }

    /** Top right: the balance, exactly as the card shop plates it. */
    private void renderBalance(GuiGraphicsExtractor poseStack)
    {
        int pad = pad();
        String value = Integer.toString(points());
        int w = font.width(value) + 34;
        int x = width - pad - w;
        NineSlice.draw(poseStack, HubTextures.PANEL, x, pad, w, 18);
        poseStack.text(font, "DP", x + 6, pad + 5, MenuInk.title(), MenuInk.shadow());
        poseStack.text(font, value, x + w - 6 - font.width(value), pad + 5, 0xFFFFFFFF, true);
    }

    /**
     * A deck box's art.
     * <p>
     * Blitted whole, unlike a sleeve. The sleeve shop samples a letterbox window
     * because a sleeve file is square with the card inside it; a deck case is
     * exported as a tight 422x512 crop with transparency around the case, so
     * there is no margin to skip and the full range is correct.
     */
    private void drawBox(GuiGraphicsExtractor poseStack, DeckBoxStyle style,
        int x, int y, int width, int height, int tint)
    {
        DdBlitUtil.fullBlit(poseStack, HubTextures.deckBox(style), x, y, width, height, tint);
    }

    /** A hollow rectangle, drawn as four bars off the white pixel. */
    /**
     * The tick on a tile that is already collected.
     *
     * <h2>In the shop and not in the picker</h2>
     * A shop is a list of things you may or may not have, so "have it" is the
     * one fact worth marking. A picker is a list of things you DO have -- every
     * tile in it would wear the tick, which is a mark that has stopped marking
     * anything.
     *
     * <h2>Over the corner, on its own dark disc</h2>
     * {@link HubTextures#CHECK} is white art meant to be tinted, and white on
     * pale sleeve art is invisible. The disc is the same plate the hub puts
     * behind its other corner marks, so the tick reads against whatever the
     * tile happens to be.
     */
    private void drawCollectedTick(GuiGraphicsExtractor poseStack, int x, int y, int cellW,
        int cellH)
    {
        int size = Math.max(7, Math.min(12, cellW / 3));
        int tickX = x + cellW - size - 2;
        int tickY = y + 2;
        DdBlitUtil.fullBlit(poseStack, DuelTextures.WHITE, tickX - 1, tickY - 1,
            size + 2, size + 2, DdBlitUtil.tint(0.05F, 0.07F, 0.10F, 0.78F));
        DdBlitUtil.fullBlit(poseStack, HubTextures.CHECK, tickX, tickY, size, size,
            DdBlitUtil.tint(0.49F, 0.89F, 0.55F, 1F));
    }

    private void drawRing(GuiGraphicsExtractor poseStack, int x, int y, int w, int h,
        int tint, int thickness)
    {
        DdBlitUtil.fullBlit(poseStack, DuelTextures.WHITE, x, y, w, thickness, tint);
        DdBlitUtil.fullBlit(poseStack, DuelTextures.WHITE, x, y + h - thickness, w, thickness, tint);
        DdBlitUtil.fullBlit(poseStack, DuelTextures.WHITE, x, y, thickness, h, tint);
        DdBlitUtil.fullBlit(poseStack, DuelTextures.WHITE, x + w - thickness, y, thickness, h, tint);
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
    /**
     * The hub screen this was opened from, or null. See {@link HubReturn}.
     * <p>
     * Read at construction, because that is the one moment the screen it is
     * replacing is still on show.
     */
    private final net.minecraft.client.gui.screens.Screen dueldimension$parent =
        HubReturn.parent();

    /** Back to the hub if that is where this came from, otherwise to the world. */
    @Override
    public void onClose()
    {
        if(!HubReturn.back(dueldimension$parent))
        {
            super.onClose();
        }
    }
}

