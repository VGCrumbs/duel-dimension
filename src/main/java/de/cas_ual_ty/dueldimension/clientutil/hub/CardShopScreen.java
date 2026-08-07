package de.cas_ual_ty.dueldimension.clientutil.hub;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import de.cas_ual_ty.dueldimension.DuelDimension;
import de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil;
import de.cas_ual_ty.dueldimension.clientutil.DuelTextures;
import de.cas_ual_ty.dueldimension.clientutil.layout.Layout;
import de.cas_ual_ty.dueldimension.shop.ShopMessages;
import de.cas_ual_ty.dueldimension.shop.ShopStock;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * The card shop, arranged as Tag Force arranges it: the highlighted pack and
 * its price down the left, a grid of every pack and deck filling the middle,
 * and a details bar with the description underneath.
 * <p>
 * The balance shown is the server's, pushed on open and after every purchase.
 * Nothing here decides what a pack costs or what comes out of it — a purchase
 * sends only which pack, and the server does the rest — so the screen cannot be
 * used to buy cheaply.
 */
public class CardShopScreen extends Screen
{
    private static final String LAYOUT = "card_shop";

    /** The balance, as last reported by the server. */
    private static int points;

    public static void setPoints(int value)
    {
        points = value;
    }

    public static int points()
    {
        return points;
    }

    /** Everything the server offered, and the part of it currently on show. */
    private final List<ShopStock.Pack> packs;
    private List<ShopStock.Pack> shown;
    private int selected;
    private int scroll;
    private String notice = "";

    /** What the grid is filtered by, and in what order it is arranged. */
    private EditBox search;
    private Sort sort = Sort.NAME;
    private boolean descending;

    /**
     * How many packs one press buys. The offered steps rather than a free
     * number: a stepper to reach ten is nine presses, and these are the counts
     * anyone actually wants.
     */
    private static final int[] BULK = {1, 3, 5, 10};
    private int bulk = 1;

    /**
     * The orders the shop offers, alphabetical first because that is the one a
     * player can navigate without knowing anything about the catalogue: with
     * three hundred sets, finding one you can name beats browsing by date.
     * Every order runs either way, so "oldest first" is the ascending release
     * order rather than a fifth entry in this list.
     */
    private enum Sort
    {
        NAME("Name"), RELEASE("Release"), PRICE("Price"), OWNED("Owned");

        private final String label;

        Sort(String label)
        {
            this.label = label;
        }

        String label()
        {
            return label;
        }

        Sort next()
        {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    public CardShopScreen(List<ShopStock.Pack> packs)
    {
        super(Component.literal("Card Shop"));
        this.packs = new ArrayList<>(packs);
        this.shown = this.packs;
    }

    /** Whether this player pays for packs at all. */
    private boolean isCreative()
    {
        return minecraft != null && minecraft.player != null && minecraft.player.isCreative();
    }

    private ShopStock.Pack current()
    {
        return shown.isEmpty() ? null : shown.get(Math.max(0, Math.min(selected, shown.size() - 1)));
    }

    // ---- geometry ----

    private Layout layout()
    {
        return Layout.of(LAYOUT);
    }

    /**
     * As many columns as the space allows rather than a fixed count. The
     * shopkeeper used to occupy the right of the screen; with it gone the grid
     * would otherwise leave that space empty.
     */
    private int gridColumns()
    {
        int available = width - gridLeft() - layout().i("pad", 8);
        int cell = cellW() + layout().i("grid.gap", 4);
        return Math.max(1, Math.min(layout().i("grid.maxColumns", 12), available / Math.max(1, cell)));
    }

    private int cellW()
    {
        return layout().i("grid.cellWidth", 40);
    }

    /**
     * Cell height from the width and the art's aspect.
     * <p>
     * The pipeline stores every set image in a SQUARE, fitting the art inside
     * with a margin and preserving its proportions. Drawing that square into a
     * tall cell squashed it horizontally, which is what made the packs look
     * thin; a square cell shows the art as stored.
     */
    private int cellH()
    {
        return Math.round(cellW() / layout().f("pack.aspect", 1F));
    }

    /**
     * As many rows as fit between the controls and the detail box.
     * <p>
     * Fixed at four before, which left the grid floating in whatever space
     * happened to be there and broke as soon as a control row was added above
     * it. Deriving it means the grid grows into a taller window instead.
     */
    private int gridRows()
    {
        int bottom = height - layout().i("bottom.height", 62) - 12;
        int room = bottom - gridTop();
        int gap = layout().i("grid.gap", 4);
        return Math.max(1, (room + gap) / (cellH() + gap));
    }

    private int gridLeft()
    {
        return layout().i("left.width", 120) + layout().i("pad", 8) * 2;
    }

    private int gridTop()
    {
        return layout().i("pad", 8) + layout().i("grid.top", 22);
    }

    @Override
    protected void init()
    {
        Layout layout = layout();
        int pad = layout.i("pad", 8);
        int buyW = layout.i("left.width", 120);

        // Kept across a resize, so typing a search and then scaling the window
        // does not silently empty the field.
        String typed = search == null ? "" : search.getValue();
        int controlsY = controlsTop();
        int sortW = layout.i("sort.width", 62);
        int dirW = layout.i("dir.width", 30);
        int searchW = Math.max(60, width - pad - gridLeft() - sortW - dirW - 8);

        search = new EditBox(font, gridLeft() + 4, controlsY + 3, searchW - 6, 12,
            Component.literal("Search"));
        search.setValue(typed);
        search.setResponder(value -> refresh());
        addWidget(search);

        addRenderableWidget(new HubWidgets.TextureButton(gridLeft() + searchW + 2, controlsY,
            sortW, 16, Component.literal(sort.label()), pressed ->
        {
            sort = sort.next();
            refresh();
            rebuild();
        }));
        addRenderableWidget(new HubWidgets.TextureButton(gridLeft() + searchW + sortW + 4,
            controlsY, dirW, 16, Component.literal(descending ? "DESC" : "ASC"), pressed ->
        {
            descending = !descending;
            refresh();
            rebuild();
        }));

        // The button carries the whole cost rather than the unit price: buying
        // ten is the one time a player wants to know the total before pressing.
        ShopStock.Pack shownPack = current();
        int bulkW = layout.i("bulk.width", 34);
        int buyLabelW = buyW - bulkW - 2;
        String cost = shownPack == null ? "Buy"
            : isCreative() ? "Buy (free)"
            : "Buy  " + shownPack.price() * bulk;
        int buyY = height - layout.i("bottom.height", 62) - 30;
        addRenderableWidget(new HubWidgets.TextureButton(pad, buyY, buyLabelW, 20,
            Component.literal(cost), pressed -> buy()));
        addRenderableWidget(new HubWidgets.TextureButton(pad + buyLabelW + 2, buyY, bulkW, 20,
            Component.literal("x" + bulk), pressed ->
        {
            bulk = nextBulk();
            rebuild();
        }));
        addRenderableWidget(new HubWidgets.TextureButton(closeLeft(), height - 26,
            layout.i("close.width", 70), 20, Component.literal("Close"), pressed -> onClose()));

        refresh();
    }

    /** The next offered quantity, wrapping back to one after the largest. */
    private int nextBulk()
    {
        for(int i = 0; i < BULK.length; i++)
        {
            if(BULK[i] == bulk)
            {
                return BULK[(i + 1) % BULK.length];
            }
        }
        return BULK[0];
    }

    /** Rebuilds the widgets so a button's own label can change. */
    private void rebuild()
    {
        clearWidgets();
        init();
    }

    /** The row the search field and the sort controls sit on. */
    private int controlsTop()
    {
        return layout().i("pad", 8) + layout().i("controls.top", 22);
    }

    /**
     * Rebuilds what the grid shows from the search text and the chosen order.
     * <p>
     * The selected pack is kept by CODE rather than by position: filtering
     * changes what index a pack sits at, and a player who had Burst of Destiny
     * selected should still have it selected after typing, not whatever has
     * taken its place.
     */
    private void refresh()
    {
        String selectedCode = current() == null ? null : current().code();
        String needle = search == null ? "" : search.getValue().trim().toLowerCase(java.util.Locale.ROOT);

        List<ShopStock.Pack> matching = new ArrayList<>();
        for(ShopStock.Pack pack : packs)
        {
            if(needle.isEmpty() || matches(pack, needle))
            {
                matching.add(pack);
            }
        }
        matching.sort(order());
        shown = matching;

        selected = 0;
        for(int i = 0; i < shown.size(); i++)
        {
            if(shown.get(i).code().equals(selectedCode))
            {
                selected = i;
                break;
            }
        }
        scroll = 0;
        notice = "";
    }

    /**
     * Whether a pack answers the search.
     * <p>
     * Name, set code and type are all searched, so "BODE", "burst" and "series
     * 11" each find the same pack -- a player looking for a set knows it by
     * whichever of those they happen to remember.
     */
    private static boolean matches(ShopStock.Pack pack, String needle)
    {
        return contains(pack.name(), needle) || contains(pack.code(), needle)
            || contains(pack.type(), needle);
    }

    private static boolean contains(String haystack, String needle)
    {
        return haystack != null && haystack.toLowerCase(java.util.Locale.ROOT).contains(needle);
    }

    private java.util.Comparator<ShopStock.Pack> order()
    {
        java.util.Comparator<ShopStock.Pack> byChosen = switch(sort)
        {
            case RELEASE -> java.util.Comparator.comparingLong(ShopStock.Pack::released);
            case NAME -> java.util.Comparator.comparing(ShopStock.Pack::name,
                String.CASE_INSENSITIVE_ORDER);
            case PRICE -> java.util.Comparator.comparingInt(ShopStock.Pack::price);
            case OWNED -> java.util.Comparator.comparingInt(this::completion);
        };
        // Ties broken by code, so two sets released the same day keep a stable
        // order rather than shuffling every time the list is rebuilt.
        java.util.Comparator<ShopStock.Pack> full =
            byChosen.thenComparing(ShopStock.Pack::code);
        return descending ? full.reversed() : full;
    }

    /**
     * Where the Close button starts.
     * <p>
     * Asked for by the button that is placed there AND by the blurb box that
     * has to stop short of it, so the two cannot drift apart -- which is how
     * the button came to be sitting on top of the box in the first place.
     */
    private int closeLeft()
    {
        Layout layout = layout();
        return width - layout.i("pad", 8) - layout.i("close.width", 70);
    }

    private void buy()
    {
        ShopStock.Pack pack = current();
        if(pack == null)
        {
            return;
        }
        // A creative player pays nothing, so the affordability check would
        // otherwise block a purchase the server would happily allow. The server
        // decides either way; this only avoids refusing the click locally.
        if(!isCreative() && points < pack.price() * bulk)
        {
            notice = "Not enough DP";
            return;
        }
        notice = "";
        // Only the pack and how many are named. Price, contents and the balance
        // check are the server's, so this cannot ask for a discount -- and the
        // count is clamped there too, since this is a number from a client.
        DuelDimension.channel.sendToServer(new ShopMessages.Buy(pack.code(), bulk));
    }

    // ---- input ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if(button == 1 && search != null && search.isMouseOver(mouseX, mouseY))
        {
            // Right-click empties a search box, here as in the deck editor.
            search.setValue("");
            search.setFocus(true);
            return true;
        }
        // Widgets first. The grid covers most of the screen, and a control
        // drawn over it should be the thing that receives a click on it.
        if(super.mouseClicked(mouseX, mouseY, button))
        {
            return true;
        }
        int index = packAt(mouseX, mouseY);
        if(index >= 0)
        {
            selected = index;
            notice = "";
            // The Buy button carries this pack's price, so it is rebuilt with
            // the selection rather than showing the last pack's cost.
            rebuild();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta)
    {
        int maxScroll = Math.max(0, (shown.size() + gridColumns() - 1) / gridColumns() - gridRows());
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int)Math.signum(delta)));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers)
    {
        // The search field takes the keyboard while it has focus, so typing a
        // set's name does not also fire whatever the letters are bound to --
        // and Escape still closes the shop rather than being swallowed.
        if(search != null && search.isFocused() && key != 256
            && search.keyPressed(key, scan, modifiers))
        {
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean charTyped(char typed, int modifiers)
    {
        if(search != null && search.isFocused() && search.charTyped(typed, modifiers))
        {
            return true;
        }
        return super.charTyped(typed, modifiers);
    }

    private int packAt(double mouseX, double mouseY)
    {
        int columns = gridColumns();
        int cellW = cellW();
        int cellH = cellH();
        int gap = layout().i("grid.gap", 4);
        // Bounded BEFORE the divide. A cast to int truncates toward zero, so a
        // click above the grid gave (int)(-0.36) == 0 rather than something
        // negative, the row < 0 guard never fired, and pressing the search box
        // selected whatever pack was in the top-left.
        if(mouseX < gridLeft() || mouseY < gridTop())
        {
            return -1;
        }
        int column = (int)((mouseX - gridLeft()) / (cellW + gap));
        int row = (int)((mouseY - gridTop()) / (cellH + gap));
        if(column >= columns || row >= gridRows())
        {
            return -1;
        }
        int index = (row + scroll) * columns + column;
        return index < shown.size() ? index : -1;
    }

    // ---- rendering ----

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick)
    {
        renderBackground(poseStack);
        Layout layout = layout();
        int pad = layout.i("pad", 8);
        int bottomH = layout.i("bottom.height", 62);
        int leftW = layout.i("left.width", 120);

        renderControls(poseStack);
        renderPreview(poseStack, pad, leftW, bottomH);
        renderGrid(poseStack, mouseX, mouseY);
        renderDetails(poseStack, bottomH, mouseX, mouseY);
        renderBalance(poseStack);

        super.render(poseStack, mouseX, mouseY, partialTick);
        if(search != null)
        {
            search.render(poseStack, mouseX, mouseY, partialTick);
        }
    }

    /** Left: the highlighted pack, large, with what it costs. */
    private void renderPreview(PoseStack poseStack, int pad, int leftW, int bottomH)
    {
        NineSlice.draw(poseStack, HubTextures.PANEL, pad, pad, leftW,
            height - bottomH - pad - 38);
        ShopStock.Pack pack = current();
        if(pack == null)
        {
            return;
        }
        int artW = leftW - 16;
        int artH = Math.round(artW / layout().f("pack.aspect", 1F));
        drawPackArt(poseStack, pack, pad + 8, pad + 8, artW, artH);

        // The facts about the product, as a label-and-value list rather than
        // three sentences stacked up. Labels down the left in one weight,
        // values down the right in another: the eye reads either column on its
        // own, which is what makes a list of unrelated facts scannable.
        String released = releaseDate(pack);
        int rows = released == null ? 3 : 4;
        int rowH = 12;
        int boxX = pad + 6;
        int boxW = leftW - 12;
        int boxY = pad + 8 + artH + 6;
        int boxH = rows * rowH + 7;
        NineSlice.draw(poseStack, HubTextures.PANEL_INSET, boxX, boxY, boxW, boxH);

        int rowY = boxY + 5;
        detail(poseStack, boxX, boxW, rowY, "Contents", pack.deck() ? "1 DECK" : "1 PACK",
            0xFFC2C9D6);
        rowY += rowH;
        // How many cards actually come out, which is what the price is per.
        detail(poseStack, boxX, boxW, rowY, "Cards",
            Integer.toString(pack.cardsPerPack()), 0xFFC2C9D6);
        rowY += rowH;
        detail(poseStack, boxX, boxW, rowY, "Price",
            isCreative() ? "FREE" : pack.price() + " DP",
            isCreative() ? 0xFF7CE38B : 0xFFF4D089);
        // The grid runs newest first, so the date is what tells a player where
        // in the run of sets they are looking. Omitted rather than written as
        // "unknown" when a set carries no date -- an absent row says the same
        // thing without occupying one, and the box shrinks to match.
        if(released != null)
        {
            rowY += rowH;
            detail(poseStack, boxX, boxW, rowY, "Released", released, 0xFF9AA2B2);
        }
    }

    /**
     * One row of the preview's fact list: its label against the left edge, its
     * value against the right.
     * <p>
     * The value is shortened from the front rather than clipped, so a value too
     * wide for the column loses its beginning and keeps the part that
     * distinguishes it -- a year, or the last digits of a price.
     */
    private void detail(PoseStack poseStack, int x, int width, int y, String label,
        String value, int colour)
    {
        font.drawShadow(poseStack, label, x + 6, y, 0xFF6E7686);
        int room = width - 12 - font.width(label) - 6;
        String shown = value;
        while(font.width(shown) > room && shown.length() > 1)
        {
            shown = shown.substring(1);
        }
        font.drawShadow(poseStack, shown, x + width - 6 - font.width(shown), y, colour);
    }

    /**
     * A set's release month, or null when the set data carries no date.
     * <p>
     * Month and year rather than the full date. The day a set was printed is
     * not how anyone identifies one -- sets are spoken of by the month they
     * came out -- and a localised full date ("September 24, 2017") is wider
     * than the column beside its label, so keeping it would mean either
     * truncating it or giving it a row to itself. The month name is
     * abbreviated by the player's own locale rather than by cutting it.
     */
    private static String releaseDate(ShopStock.Pack pack)
    {
        if(pack.released() <= 0L)
        {
            return null;
        }
        try
        {
            java.time.LocalDate date = java.time.Instant.ofEpochMilli(pack.released())
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate();
            return date.format(java.time.format.DateTimeFormatter.ofPattern("MMM yyyy"));
        }
        catch(RuntimeException unreadable)
        {
            return null;
        }
    }

    /** The search field's frame, and how much of the catalogue is showing. */
    private void renderControls(PoseStack poseStack)
    {
        if(search == null)
        {
            return;
        }
        NineSlice.draw(poseStack, HubTextures.SEARCH_FIELD, search.x - 4, controlsTop(),
            search.getWidth() + 8, 16);
        String count = shown.size() == packs.size()
            ? packs.size() + " packs"
            : shown.size() + " of " + packs.size();
        // Under the grid rather than above it: above put it behind the search
        // field, which is drawn later and covered it.
        int gap = layout().i("grid.gap", 4);
        int below = gridTop() + gridRows() * (cellH() + gap) + 2;
        font.drawShadow(poseStack, count, gridLeft(), below, 0xFF7A8090);

        // The grid has always scrolled and never said so. The thumb's length
        // reports how much of the catalogue is on screen and its position
        // where in it you are.
        int rows = (shown.size() + gridColumns() - 1) / gridColumns();
        int visible = gridRows();
        int overflow = Math.max(0, rows - visible);
        if(overflow > 0)
        {
            int trackX = gridLeft() + gridColumns() * (cellW() + gap) + 2;
            int trackY = gridTop();
            int trackH = visible * (cellH() + gap) - gap;
            int thumbH = Math.max(12, trackH * visible / Math.max(1, rows));
            int thumbY = trackY + (trackH - thumbH) * scroll / overflow;
            NineSlice.draw(poseStack, HubTextures.SCROLLBAR, trackX, trackY, 4, trackH, 0, 2);
            NineSlice.draw(poseStack, HubTextures.SCROLLBAR, trackX, thumbY, 4, thumbH, 1, 2);
        }
    }

    /** Centre: every pack, the highlighted one ringed. */
    private void renderGrid(PoseStack poseStack, int mouseX, int mouseY)
    {
        Layout layout = layout();
        int columns = gridColumns();
        int rows = gridRows();
        int cellW = cellW();
        int cellH = cellH();
        int gap = layout.i("grid.gap", 4);
        int left = gridLeft();
        int top = gridTop();

        NineSlice.draw(poseStack, HubTextures.PANEL_INSET, left - 4, top - 4,
            columns * (cellW + gap) + 4, rows * (cellH + gap) + 4);

        for(int row = 0; row < rows; row++)
        {
            for(int column = 0; column < columns; column++)
            {
                int index = (row + scroll) * columns + column;
                if(index >= shown.size())
                {
                    continue;
                }
                int x = left + column * (cellW + gap);
                int y = top + row * (cellH + gap);
                drawPackArt(poseStack, shown.get(index), x, y, cellW, cellH);
                if(index == selected)
                {
                    // The reference marks focus with a blue ring rather than a
                    // tint, so the art underneath stays readable.
                    drawSelection(poseStack, x - 2, y - 2, cellW + 4, cellH + 4);
                }
            }
        }
    }

    /** Bottom: name, cards per pack, price, completion, and the blurb. */
    private void renderDetails(PoseStack poseStack, int bottomH, int mouseX, int mouseY)
    {
        int pad = layout().i("pad", 8);
        int y = height - bottomH - 4;
        // The box stops where the Close button starts. It used to run the full
        // width of the screen and the button sat on top of it, so the two read
        // as one piece of furniture with a button embedded in its corner; they
        // are separate things and now look it.
        int right = closeLeft() - layout().i("close.gap", 4);
        NineSlice.draw(poseStack, HubTextures.PANEL, pad, y, right - pad, bottomH);

        ShopStock.Pack pack = current();
        if(pack == null)
        {
            return;
        }
        int textY = y + 7;
        font.drawShadow(poseStack, pack.name(), pad + 8, textY, 0xFFE6EAF2);
        String kind = pack.deck() ? "DECK" : "PACK";
        font.drawShadow(poseStack, kind, pad + 12 + font.width(pack.name()), textY,
            pack.deck() ? 0xFF9FD4FF : 0xFF7A8090);

        // The metrics run along the right of the title row, as the reference's
        // bar does: how many cards, what it costs, how much of it you have.
        String cards = "x " + pack.cardsPerPack();
        String price = isCreative() ? "FREE" : pack.price() + " DP";
        int owned = ownedIn(pack);
        int total = Math.max(0, pack.distinctCards());
        String complete = percentage(owned, total) + "%";
        // Measured from the box's own edge rather than the screen's, so the
        // metrics stay inside it now that it is shorter.
        int metricsX = right - 8;
        metricsX -= font.width(complete);
        font.drawShadow(poseStack, complete, metricsX, textY, 0xFF9FD4FF);
        // A percentage says how close you are and not how far there is to go.
        // The counts behind it are what a collector actually wants, so they are
        // one hover away rather than taking a permanent place on the row.
        boolean overCompletion = mouseX >= metricsX && mouseX < metricsX + font.width(complete)
            && mouseY >= textY - 2 && mouseY < textY + 10;
        String ratio = overCompletion ? owned + " of " + total : null;
        metricsX -= font.width(price) + 12;
        font.drawShadow(poseStack, price, metricsX, textY, isCreative() ? 0xFF7CE38B : 0xFFF4D089);
        metricsX -= font.width(cards) + 12;
        font.drawShadow(poseStack, cards, metricsX, textY, 0xFFC2C9D6);
        // A card glyph before the count, standing in for the reference's icon.
        NineSlice.image(poseStack, DuelTextures.COVER, metricsX - 9, textY - 1, 5, 8);

        int descriptionY = textY + 14;
        // Inset within the box, which is itself already clear of the button.
        int descriptionX = pad + 6;
        int descriptionW = Math.max(40, right - 6 - descriptionX);
        NineSlice.draw(poseStack, HubTextures.PANEL_INSET, descriptionX, descriptionY - 3,
            descriptionW, bottomH - 22);
        for(net.minecraft.util.FormattedCharSequence line
            : font.split(Component.literal(pack.description()), descriptionW - 12))
        {
            font.draw(poseStack, line, descriptionX + 6, descriptionY, 0xFFC2C9D6);
            descriptionY += 10;
        }

        if(!notice.isEmpty())
        {
            font.drawShadow(poseStack, notice, pad + 12, height - 18, 0xFFFF8A80);
        }

        // Last, so it is over the blurb it is standing on rather than under it.
        if(ratio != null)
        {
            int tipW = font.width(ratio) + 12;
            int tipX = Math.min(metricsX - tipW / 2, width - tipW - 2);
            int tipY = textY - 16;
            NineSlice.draw(poseStack, HubTextures.PANEL, tipX, tipY, tipW, 14);
            font.drawShadow(poseStack, ratio, tipX + 6, tipY + 3, 0xFF9FD4FF);
        }
    }

    /** Top right: the balance, as the reference shows it. */
    private void renderBalance(PoseStack poseStack)
    {
        Layout layout = layout();
        int pad = layout.i("pad", 8);
        String value = Integer.toString(points);
        int w = font.width(value) + 34;
        int x = width - pad - w;
        NineSlice.draw(poseStack, HubTextures.PANEL, x, pad, w, 18);
        // Gold letters, white figure: the same split the section headers use,
        // so the label reads as a label and the balance as the number.
        font.drawShadow(poseStack, "DP", x + 6, pad + 5, 0xFFF4D089);
        font.drawShadow(poseStack, value, x + w - 6 - font.width(value), pad + 5, 0xFFFFFFFF);
    }

    /** How many distinct cards of this pack's list the player owns. */
    private int ownedIn(ShopStock.Pack pack)
    {
        var set = ShopStock.setOf(pack.code());
        if(set == null)
        {
            return 0;
        }
        int owned = 0;
        for(int code : ShopStock.cardIds(set))
        {
            if(EditorState.trunk().has(code))
            {
                owned++;
            }
        }
        return owned;
    }

    /** The figure shown beside the count, from the same two numbers. */
    private static int percentage(int owned, int total)
    {
        return total <= 0 ? 0 : Math.round(owned * 100F / total);
    }

    /** How much of this pack's card list the player owns, as a percentage. */
    private int completion(ShopStock.Pack pack)
    {
        return percentage(ownedIn(pack), pack.distinctCards());
    }

    /**
     * Product art, from the set's own image.
     * <p>
     * Nothing is shipped for this. Every set already carries an image URL and
     * the mod already has a pipeline that fetches and caches set images -- the
     * same one the set item uses -- so this asks for the art rather than
     * bundling several hundred pictures. A set still downloading yields the
     * pipeline's own placeholder, and the next frame picks up the real thing.
     */
    private void drawPackArt(PoseStack poseStack, ShopStock.Pack pack, int x, int y, int w, int h)
    {
        RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        RenderSystem.enableBlend();
        de.cas_ual_ty.dueldimension.set.CardSet set = ShopStock.setOf(pack.code());
        ResourceLocation art = set == null ? DuelTextures.COVER : set.getInfoImageResourceLocation();
        DuelTextures.bindSmooth(art);
        DdBlitUtil.fullBlit(poseStack, x, y, w, h);
    }

    /** The focus ring: four thin bars, so the art inside is untouched. */
    private void drawSelection(PoseStack poseStack, int x, int y, int w, int h)
    {
        RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexShader);
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(0.42F, 0.72F, 1F, 1F);
        RenderSystem.setShaderTexture(0, DuelTextures.WHITE);
        int t = 2;
        DdBlitUtil.fullBlit(poseStack, x, y, w, t);
        DdBlitUtil.fullBlit(poseStack, x, y + h - t, w, t);
        DdBlitUtil.fullBlit(poseStack, x, y, t, h);
        DdBlitUtil.fullBlit(poseStack, x + w - t, y, t, h);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
