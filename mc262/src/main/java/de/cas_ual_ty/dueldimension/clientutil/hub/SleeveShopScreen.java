package de.cas_ual_ty.dueldimension.clientutil.hub;

import de.cas_ual_ty.dueldimension.card.CardSleevesType;
import de.cas_ual_ty.dueldimension.clientutil.ClientProxy;
import de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil;
import de.cas_ual_ty.dueldimension.clientutil.DuelTextures;
import de.cas_ual_ty.dueldimension.clientutil.layout.Layout;
import de.cas_ual_ty.dueldimension.duel.profile.Sleeves;
import de.cas_ual_ty.dueldimension.shop.ShopMessages;
import de.cas_ual_ty.dueldimension.shop.ShopStock;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * The sleeve shop, arranged as the card shop arranges itself: the highlighted
 * product large down the left with what it costs, a grid of everything on sale
 * filling the middle, and a details bar underneath.
 * <p>
 * Deliberately the same furniture as {@link CardShopScreen} — the same panels,
 * the same balance plate, the same focus ring — because the two are counters of
 * one shop and a player should not have to learn a second shop to buy a second
 * kind of thing.
 *
 * <h2>What the screen decides, which is nothing</h2>
 * The balance shown is the server's, pushed on open and after every purchase.
 * The price shown is the server's, sent with the stock. What is owned is read
 * off the profile the server synced. A purchase sends the sleeve's id and
 * nothing else, so this screen cannot ask for a discount, cannot buy a sleeve
 * that is not stock, and cannot claim to own one. Every check here exists to
 * avoid a pointless round trip, never to make the decision.
 *
 * <h2>Drawing a sleeve</h2>
 * A sleeve PNG is <b>square</b>, with the card-shaped art letterboxed inside it
 * at exactly {@link DuelTextures#CARD_U0}..{@link DuelTextures#CARD_V1} —
 * measured, not assumed: every {@code sleeves_*.png} in the tree has its opaque
 * bounds at u 0.19922..0.80078, v 0.0625..0.9375. Drawing the whole square would
 * put a wide transparent margin down each side of every sleeve and squash the
 * art, so this samples that window and shapes the rectangle to
 * {@link DuelTextures#CARD_ASPECT}.
 */
public class SleeveShopScreen extends Screen
{
    private static final String LAYOUT = "sleeve_shop";

    /**
     * How a sleeve is named for a human.
     * <p>
     * The item's own translation key, so the shop and the item in a hand and the
     * deck editor's picker all say the same word, and a translator writes each
     * name once.
     */
    private static final String NAME_KEY = "item.dueldimension.";

    /**
     * One thing on sale: the sleeve, and what the server says it costs.
     * <p>
     * Resolved from the id once, at open, rather than per frame: the wire
     * carries names because an enum index is not safe to store or to send across
     * builds, but a screen redrawing sixty times a second should not be walking
     * the enum to turn a string back into a constant.
     */
    private record Entry(CardSleevesType sleeve, int price)
    {
    }

    private final List<Entry> offers;
    private int selected;
    private int scroll;
    private String notice = "";

    /** Kept so its label and its enabled state can follow the selection. */
    private HubWidgets.TextureButton buyButton;

    /** Cached by {@link #descriptionLines()}; 0 means "not measured yet". */
    private int descriptionLines;

    /**
     * What the shelf is ordered by, and which way.
     * <p>
     * The order is a property of the SCREEN, not of the stock: {@code offers} is
     * this screen's own list, so sorting it in place cannot disturb
     * {@code ShopStock}'s catalogue, which other screens share and which the
     * server's prices are keyed to.
     */
    private SortKey sortKey = SortKey.COLOR;
    private boolean descending;

    /**
     * The three orders the shelf can be read in, in the order they are offered.
     * <p>
     * Colour first and by default. With 260 sleeves the question a player
     * actually arrives with is "what does it look like", and id order -- which
     * is Master Duel's release grouping -- answers a question about Konami's
     * catalogue rather than about the art.
     */
    private enum SortKey
    {
        /** See {@link CardSleevesType#compareByColour}. */
        COLOR("Color"),
        NAME("Name"),
        /**
         * Enum order, which is Master Duel's own item id order -- the same shelf
         * order the deck boxes use, and the order the game itself lists them in.
         */
        ID("ID");

        final String label;

        SortKey(String label)
        {
            this.label = label;
        }
    }

    /**
     * Opens the shop on the stock the server sent.
     * <p>
     * {@code minecraft.gui.setScreen} and not {@code setScreenAndShow}: the
     * latter forces a frame immediately, which is the right thing only for a
     * blocking transition and the wrong thing everywhere else (see PORTING.md —
     * it painted a half-applied duel board once).
     */
    public static void open(int points, List<ShopStock.SleeveOffer> sleeves)
    {
        // One balance, one place to keep it. The card shop already holds what
        // the server last said and SyncPoints already updates it, so reusing it
        // means a purchase made at either counter is reflected at both without
        // a second field that could disagree.
        CardShopScreen.setPoints(points);
        Minecraft.getInstance().gui.setScreen(new SleeveShopScreen(sleeves));
    }

    public SleeveShopScreen(List<ShopStock.SleeveOffer> sleeves)
    {
        super(Component.literal("Sleeve Shop"));
        this.offers = new ArrayList<>();
        for(ShopStock.SleeveOffer offer : sleeves)
        {
            CardSleevesType sleeve = Sleeves.byName(offer.sleeve());
            if(sleeve != null)
            {
                // An id this build does not know is dropped rather than drawn as
                // a blank cell that cannot be bought. It can only happen against
                // a server running a build with sleeves this one has never heard
                // of, and a shop is a better place to be missing an entry than
                // to have one that does nothing.
                offers.add(new Entry(sleeve, offer.price()));
            }
        }
        // The stock arrives in id order and the default is colour, so the order
        // is applied once, here, before anything has been selected or scrolled.
        // Nothing in sortOffers needs the screen's size, which the constructor
        // does not have.
        sortOffers();
    }

    // ---- what the player owns and can afford ----

    private static boolean owns(CardSleevesType sleeve)
    {
        // The profile the server synced, which is the same object the deck
        // editor's picker reads. No second source, so the shop and the picker
        // cannot disagree about what has been bought.
        return EditorState.profile().ownsSleeve(sleeve);
    }

    /** Whether this player pays for anything at all. */
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

    private static String nameOf(CardSleevesType sleeve)
    {
        return Component.translatable(NAME_KEY + sleeve.getResourceName()).getString();
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

    /**
     * The details bar, tall enough for the longest description in the stock.
     * <p>
     * The authored 46 fits two lines, which was right when the blurb was one
     * fixed sentence. It is Master Duel's own flavour text now: 159 of the 231
     * descriptions wrap to two lines at 920p, 60 to three and 11 to four, so a
     * fixed bar cut the tail off a third of the catalogue.
     * <p>
     * Measured rather than raised to a new constant. The wrap depends on the
     * window's width, and at the narrowest window the game allows the worst
     * description takes seven lines -- a number no constant chosen at 920p would
     * have known about.
     * <p>
     * Bounded to a third of the screen so a small window still has a grid. The
     * clamp does not bind at the size this mod is laid out for; where it does,
     * {@link #renderDetails} draws whole lines up to the room it has rather than
     * half of one.
     */
    private int bottomHeight()
    {
        int authored = layout().i("bottom.height", 46);
        int wanted = DESCRIPTION_TOP + descriptionLines() * DESCRIPTION_LINE
            + DESCRIPTION_BOTTOM;
        return Math.min(Math.max(authored, wanted), Math.max(authored, height / 3));
    }

    /** Where the description's own box starts, measured from the bar's top. */
    private static final int DESCRIPTION_TOP = 18;
    /** And how much sits below it. */
    private static final int DESCRIPTION_BOTTOM = 10;
    private static final int DESCRIPTION_LINE = 10;

    /** The text width a description wraps to. Independent of the bar's height. */
    private int descriptionWrapWidth()
    {
        int right = closeLeft() - layout().i("close.gap", 4);
        return Math.max(40, right - 6 - (pad() + 6)) - 12;
    }

    /**
     * The most lines any one sleeve in this shop needs.
     * <p>
     * Over the whole stock, not the selection: a bar that resized as you moved
     * along the grid would move the grid, and a grid that moves under the cursor
     * is worse than a bar with a spare line in it.
     * <p>
     * Cached because it walks every offer through the font, and cleared in
     * {@link #init} because a resize changes the wrap.
     */
    private int descriptionLines()
    {
        if(descriptionLines > 0)
        {
            return descriptionLines;
        }
        int limit = descriptionWrapWidth();
        int most = 1;
        for(Entry entry : offers)
        {
            for(boolean owned : new boolean[] {false, true})
            {
                most = Math.max(most, font.split(
                    Component.literal(blurbFor(entry.sleeve(), owned)), limit).size());
            }
        }
        descriptionLines = most;
        return most;
    }

    /**
     * The tile SCALES to the space instead of staying at its authored size.
     * <p>
     * A fixed 32 filled a wide window with many tiny sleeves rather than fewer
     * legible ones -- the authored number is a floor, not the answer. Grown
     * until the chosen grid fills the room it has, bounded so a small window
     * still shrinks back to what was drawn for it.
     */
    private int cellW()
    {
        return cellWFor(gridRows());
    }

    /**
     * The cell a grid of this many rows would get.
     * <p>
     * Split out from {@link #cellW()} so {@link #gridRows()} can ask what a
     * candidate row count would actually cost. It must not call
     * {@code gridRows()} itself -- that is the recursion this parameter exists
     * to break.
     */
    private int cellWFor(int rows)
    {
        int min = layout().i("grid.cellWidth", 32);
        int max = layout().i("grid.cellWidthMax", 64);
        int gap = layout().i("grid.gap", 4);
        int cols = gridColumns();
        // The REGION, never gridLeft(): that centres the grid using this very
        // method, so asking it here is infinite recursion -- a StackOverflowError
        // the moment the screen opens.
        int roomW = (width - gridRegionLeft() - pad() - gap * (cols - 1)) / Math.max(1, cols);
        // Height matters as much: a cell is card-shaped, so a wide window with a
        // short one would otherwise grow tiles straight past the detail box.
        int roomH = Math.round(((gridRoom() - gap * (rows - 1))
            / (float)Math.max(1, rows)) * DuelTextures.CARD_ASPECT);
        return Math.max(min, Math.min(max, Math.min(roomW, roomH)));
    }

    /** The vertical band the grid has, between its top and the details bar. */
    private int gridRoom()
    {
        return height - bottomHeight() - 12 - gridTop();
    }

    /** The authored cell, which is what the column and row counts are chosen against. */
    private int baseCellW()
    {
        return layout().i("grid.cellWidth", 32);
    }

    private int baseCellH()
    {
        return Math.round(baseCellW() / DuelTextures.CARD_ASPECT);
    }

    /**
     * Cell height from the width and the card's own proportions.
     * <p>
     * The card shop's cells are square because a set's info image is; a sleeve
     * is a card, so its cell is card-shaped. Same arithmetic, different aspect.
     */
    private int cellH()
    {
        return Math.round(cellW() / DuelTextures.CARD_ASPECT);
    }

    /** The left edge of the grid REGION; the tiles are centred within it below. */
    private int gridRegionLeft()
    {
        return leftWidth() + pad() * 2;
    }

    /**
     * Centred in the space beside the left panel, rather than pinned to it.
     * <p>
     * With the tiles scaling, a grid anchored left drifted away from the middle
     * of the window as it grew; centring keeps the selection under the eye.
     */
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

    /** As many columns as the space allows, so the grid fills a wide window. */
    private int gridColumns()
    {
        // Also the REGION, for the same reason: gridLeft() centres using this
        // count, so reading it here would recurse.
        int available = width - gridRegionLeft() - pad();
        int cell = baseCellW() + layout().i("grid.gap", 4);
        int fits = Math.max(1, available / Math.max(1, cell));
        // Never more columns than there are sleeves, or a full row of empty
        // tiles is drawn beside the last one.
        return Math.max(1, Math.min(Math.min(layout().i("grid.maxColumns", 12), fits),
            Math.max(1, offers.size())));
    }

    /** As many rows as fit between the header and the detail box. */
    /**
     * As many rows as the room really holds.
     * <p>
     * <b>Measured against the cell that will be DRAWN, not the authored one.</b>
     * This used to divide the room by {@link #baseCellH()} -- the 32-wide cell
     * the layout file names -- and then {@link #cellWFor} grew the tile from
     * there to fill the width. The two never agreed: at 920p the room held three
     * authored rows, the tiles then grew to 48 tall, and three of those left a
     * forty-unit band of nothing between the last row and the details bar.
     * <p>
     * So the count is searched instead. Each candidate is asked what cell it
     * would produce and whether that many of them fit; the largest that does
     * wins. Growing the row count shrinks the cell, so the test tightens
     * monotonically and the first failure ends it.
     */
    private int gridRows()
    {
        int room = gridRoom();
        int gap = layout().i("grid.gap", 4);
        // No more rows than the stock needs, so the grid is as tall as its
        // contents rather than as tall as the window.
        int needed = Math.max(1, (offers.size() + gridColumns() - 1)
            / Math.max(1, gridColumns()));
        int best = 1;
        for(int rows = 1; rows <= needed; rows++)
        {
            int cellH = Math.round(cellWFor(rows) / DuelTextures.CARD_ASPECT);
            if(rows * cellH + (rows - 1) * gap > room)
            {
                break;
            }
            best = rows;
        }
        return best;
    }

    /**
     * Where the Close button starts.
     * <p>
     * Asked for by the button placed there AND by the detail box that has to
     * stop short of it, so the two cannot drift apart — the same reason
     * {@code CardShopScreen.closeLeft} exists, and the bug it was written for
     * (the Close button ended up sitting on top of the box).
     */
    private int closeLeft()
    {
        return width - pad() - layout().i("close.width", 70);
    }

    @Override
    protected void init()
    {
        // A resize changes the wrap, so the measurement is no longer the
        // measurement of anything.
        descriptionLines = 0;

        // A resize changes how many tiles a row holds, so a scroll that was
        // inside the shelf may no longer be. The ORDER is not touched here --
        // the constructor set it, and re-applying it would drag the shelf to
        // the selection every time the window changed.
        clampScroll();

        // The sort row, in the band above the shelf and to the LEFT of the
        // balance plate, which owns the right of that band. Chips choose the
        // key; the arrow beside them chooses the direction, because a chip that
        // also toggled direction would have no way to show both states at once.
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
        // An arrow, not the word: ASC and DESC are four and five characters for
        // something a triangle says in a square. The narration is still the
        // word, because a screen reader cannot read a triangle.
        // The arrow reads DOWN the list, not up it: ascending means the values
        // increase as you read downward, so it is the down arrow that says so.
        // Pointing it the other way -- at the end the smallest value is at --
        // was the first reading, and it looks backwards on screen.
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
        refreshBuyButton();
        // init() runs again on every resize, and a wider window fits more per
        // row -- which can put the scroll position past the end of a list that
        // now needs fewer rows, leaving the grid drawing nothing until the
        // player happens to scroll back up.
        clampScroll();
    }

    /** Holds the scroll position inside what there is to scroll through. */
    private void clampScroll()
    {
        int columns = gridColumns();
        int maxScroll = Math.max(0, (offers.size() + columns - 1) / columns - gridRows());
        scroll = Math.max(0, Math.min(maxScroll, scroll));
    }

    /**
     * Puts the current price, or the reason there is none, on the Buy button.
     * <p>
     * Called every frame rather than only when the selection changes, because
     * two of the three things it reads arrive from the server on their own
     * schedule: the balance in {@code SyncPoints} and the ownership inside the
     * profile sync that follows a purchase. Rebuilding the widget list from a
     * network thread's schedule would mean replacing children mid-frame; setting
     * the label on the widget that is already there costs nothing and cannot.
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
        boolean owned = owns(entry.sleeve());
        buyButton.setMessage(Component.literal(owned ? "Owned"
            : isCreative() ? "Buy (free)"
            : "Buy  " + entry.price()));
        // Greyed rather than hidden: a bought sleeve is still worth pointing at,
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
        if(owns(entry.sleeve()))
        {
            // Refused here as well as on the server. This is the click that
            // would otherwise cost a second 500 if the server ever stopped
            // checking, and it is also the one that saves a round trip -- but
            // the server's check is the one that matters, not this.
            notice = "Already owned";
            return;
        }
        if(!isCreative() && points() < entry.price())
        {
            notice = "Not enough DP";
            return;
        }
        notice = "";
        // Only which sleeve. The price, the balance and the entitlement are all
        // looked up on the server, so this cannot ask for a discount.
        ClientPlayNetworking.send(new ShopMessages.BuySleeve(Sleeves.nameOf(entry.sleeve())));
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
        int index = sleeveAt(event.x(), event.y());
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
     * {@code row < 0} test never fires. The card shop shipped that bug and
     * selecting the top-left pack by clicking the search box is how it was found.
     */
    private int sleeveAt(double mouseX, double mouseY)
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
        // The dim that Forge's renderBackground drew, not extractBackground:
        // that BLURS in 26.2, the blur is once per frame, and a screen opening
        // over one that already asked for it took the client down. Same
        // decision, and the same crash, as CardShopScreen and EngineDuelScreen.
        poseStack.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);

        // Before super, so the widget the label belongs to is described with the
        // label it should have this frame rather than last frame's.
        refreshBuyButton();

        renderPreview(poseStack);
        renderGrid(poseStack, mouseX, mouseY);
        renderDetails(poseStack);
        renderNotice(poseStack);
        renderBalance(poseStack);

        super.extractRenderState(poseStack, mouseX, mouseY, partialTick);
    }

    /** Left: the highlighted sleeve, large, with what it costs. */
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

        // Price and Collected. Contents was the third and is gone.
        int rows = 2;
        int rowH = 12;
        int boxH = rows * rowH + 7;

        // The art takes the width it is given unless the panel is too short for
        // it, in which case the HEIGHT is what is scarce and the width follows.
        // Derived rather than fixed because the panel's height comes from the
        // window's, and a fixed art size overflowed the panel on a short window.
        int artW = leftW - 16;
        int artH = Math.round(artW / DuelTextures.CARD_ASPECT);
        int room = panelH - boxH - 22;
        if(artH > room)
        {
            artH = Math.max(16, room);
            artW = Math.round(artH * DuelTextures.CARD_ASPECT);
        }
        drawSleeve(poseStack, entry.sleeve(), pad + 8 + (leftW - 16 - artW) / 2, pad + 8,
            artW, artH, DdBlitUtil.NO_TINT);

        // The facts, as a label-and-value list: labels down the left in one
        // weight, values down the right in another, so either column can be read
        // on its own. The card shop's preview does the same, deliberately.
        int boxX = pad + 6;
        int boxW = leftW - 12;
        int boxY = pad + 8 + artH + 6;
        NineSlice.draw(poseStack, HubTextures.PANEL_INSET, boxX, boxY, boxW, boxH);

        boolean owned = owns(entry.sleeve());
        int rowY = boxY + 5;
        // No "Contents: 1 SLEEVE" row. It answered a question the sleeve shop
        // cannot raise -- every product here is one sleeve -- and the deck box
        // shop lost the same row for the same reason.
        detail(poseStack, boxX, boxW, rowY, "Price",
            owned ? "OWNED" : isCreative() ? "FREE" : entry.price() + " DP",
            owned ? 0xFF7CE38B : isCreative() ? 0xFF7CE38B : MenuInk.title());
        rowY += rowH;
        // How many of the shop's own list are already bought. A collector's
        // figure, the same question the card shop answers per pack.
        detail(poseStack, boxX, boxW, rowY, "Collected", ownedCount() + "/" + offers.size(),
            0xFF9AA2B2);
    }

    /**
     * One row of the fact list: label against the left edge, value against the
     * right. The value is shortened from the FRONT when it will not fit, so what
     * survives is the part that distinguishes it.
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
            if(owns(entry.sleeve()))
            {
                owned++;
            }
        }
        return owned;
    }

    /** Centre: every sleeve on sale, the highlighted one ringed. */
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
                boolean owned = owns(entry.sleeve());

                // An owned sleeve is drawn faint, so the eye lands on what is
                // still for sale, and marked with a gold bar underneath so
                // "faint" cannot be misread as "cannot afford".
                drawSleeve(poseStack, entry.sleeve(), x, y, cellW, cellH,
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

    /** Bottom: the sleeve's name, its state, and where it is actually worn. */
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
        poseStack.text(font, nameOf(entry.sleeve()), pad + 8, textY, MenuInk.label(), MenuInk.shadow());

        boolean owned = owns(entry.sleeve());
        String state = owned ? "OWNED" : isCreative() ? "FREE" : entry.price() + " DP";
        poseStack.text(font, state, right - 8 - font.width(state), textY,
            owned ? 0xFF7CE38B : isCreative() ? 0xFF7CE38B : MenuInk.title(), MenuInk.shadow());

        String blurb = blurbFor(entry.sleeve(), owned);
        int descriptionX = pad + 6;
        int descriptionW = Math.max(40, right - 6 - descriptionX);
        // The box is the bar minus the name row above it and the margin below,
        // so the two agree by construction rather than by both being told 22.
        int boxY = y + DESCRIPTION_TOP;
        int boxH = Math.max(DESCRIPTION_LINE + 6, bottomH - DESCRIPTION_TOP - 4);
        NineSlice.draw(poseStack, HubTextures.PANEL_INSET, descriptionX, boxY, descriptionW, boxH);

        java.util.List<net.minecraft.util.FormattedCharSequence> lines =
            font.split(Component.literal(blurb), descriptionW - 12);
        // Whole lines only. The bar is sized for the longest description in the
        // stock, so this bites only when the clamp in bottomHeight did -- and
        // half a line of text reads as a rendering fault where a missing one
        // reads as a small window.
        int room = (boxH - 6) / DESCRIPTION_LINE;
        int descriptionY = boxY + 3;
        for(int i = 0; i < Math.min(room, lines.size()); i++)
        {
            poseStack.text(font, lines.get(i), descriptionX + 6, descriptionY, MenuInk.body());
            descriptionY += DESCRIPTION_LINE;
        }
    }

    /**
     * Why the last click did nothing.
     * <p>
     * In the gap between the grid and the detail box rather than inside the box,
     * which is where the card shop puts its own and where it collides with the
     * blurb: that box is 46 tall here, so a line at {@code height - 18} would
     * have been written across the description it is not part of.
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
     * A sleeve's art, sampled through the window it is letterboxed in.
     * <p>
     * <b>The window is the whole point.</b> A sleeve file is square and the card
     * occupies only the middle 60% of its width; a full blit would draw wide
     * transparent margins and squeeze the art into whatever rectangle it was
     * given. {@link DuelTextures#CARD_U0}..{@link DuelTextures#CARD_V1} is that
     * window, already measured for card art and confirmed against every
     * {@code sleeves_*.png} in the tree.
     * <p>
     * The size asked for is the same one the duel field asks for when it draws a
     * card back, so a player who has turned card art down gets smaller sleeve
     * art here too rather than this screen overriding their setting.
     */
    private void drawSleeve(GuiGraphicsExtractor poseStack, CardSleevesType sleeve,
        int x, int y, int width, int height, int tint)
    {
        // The tier is chosen by how big this is DRAWN, not by the duel field's
        // setting. cardMainImageSize is 64 by default -- right for a card on the
        // field, and four times too small for a preview panel three hundred
        // pixels tall, which is why the preview was visibly pixelated. Sleeves
        // ship at every tier up to 1024, so asking for a fitting one costs
        // nothing but the texture that was going to be loaded anyway.
        Identifier art = sleeve.getMainRL(tierFor(width, height));
        DdBlitUtil.blit(poseStack, art, x, y, width, height,
            DuelTextures.CARD_U0, DuelTextures.CARD_V0,
            DuelTextures.CARD_U1, DuelTextures.CARD_V1, tint);
    }

    /**
     * The smallest shipped tier that still has a source pixel per device pixel.
     *
     * <h2>Two multipliers the drawn size does not carry</h2>
     * A GUI unit is not a pixel: at gui scale 3 a 120-unit tile is 360 device
     * pixels, so a tier chosen from the unit count is a third of the resolution
     * the screen actually asks for. And only the {@link DuelTextures#CARD_U0}
     * window of the file is sampled -- roughly 60% across and 87% down -- so the
     * tier has to be that much larger again to put a texel on each of them.
     * <p>
     * Together those were the whole of the "sleeves look low res" problem: a
     * grid tile 120 units tall picked the 128 tier, of which about 112 rows were
     * inside the window, and then drew them across 360 device pixels. It now
     * asks for 512, which is a source pixel to spare.
     * <p>
     * Bounded by what actually exists: {@link CardSleevesType#MAX_SIZE} is the
     * largest sleeve texture, and asking beyond it would resolve to a missing
     * file rather than a big one.
     */
    private static int tierFor(int width, int height)
    {
        double gui = Math.max(1D, net.minecraft.client.Minecraft.getInstance()
            .getWindow().getGuiScale());
        // Per axis, because the window is not square: the sleeve is letterboxed
        // much harder across than it is down.
        double across = width * gui / (DuelTextures.CARD_U1 - DuelTextures.CARD_U0);
        double down = height * gui / (DuelTextures.CARD_V1 - DuelTextures.CARD_V0);
        double wanted = Math.max(across, down);
        for(int tier : new int[] {16, 32, 64, 128, 256, 512})
        {
            if(tier >= wanted)
            {
                return tier;
            }
        }
        return CardSleevesType.MAX_SIZE;
    }

    /** The sort chips and their arrow, all one row tall. */
    private static final int SORT_ROW_H = 16;

    /**
     * Puts the shelf in {@link #sortKey} order. Touches nothing else.
     * <p>
     * Split from {@link #applySort()} because the two callers want different
     * things done with the selection afterwards, and conflating them is what
     * made the shop open half way down its own list: on the first sort there is
     * no selection worth keeping -- {@code selected} is still 0, meaning
     * "whatever happened to be first in ID order" -- and carrying that across
     * into colour order scrolled the shelf to wherever that one sleeve landed.
     */
    private void sortOffers()
    {
        java.util.Comparator<Entry> order = switch(sortKey)
        {
            // The enum's own order IS the id order: the constants are generated
            // in Master Duel id order, which CardSleevesType records per
            // constant. Comparing ordinals avoids parsing an id back out of a
            // name at sixty frames a second.
            case ID -> java.util.Comparator.comparingInt(e -> e.sleeve().ordinal());
            case NAME -> java.util.Comparator.comparing(
                e -> nameOf(e.sleeve()), String.CASE_INSENSITIVE_ORDER);
            case COLOR -> (a, b) -> CardSleevesType.compareByColour(a.sleeve(), b.sleeve());
        };
        // Ties broken by id in every mode, so the order is total and a re-sort
        // cannot shuffle equal entries about.
        order = order.thenComparingInt(e -> e.sleeve().ordinal());
        offers.sort(descending ? order.reversed() : order);
    }

    /**
     * Re-orders the shelf, keeping the selection on the same SLEEVE and
     * bringing it back into view.
     * <p>
     * For when the PLAYER changes the order. The selection is an index, so a
     * sort moves it to a different product unless it is carried across
     * explicitly -- and having carried it, the shelf has to scroll to wherever
     * it went or the highlight is off screen.
     */
    private void applySort()
    {
        CardSleevesType keep = current() == null ? null : current().sleeve();
        sortOffers();

        selected = 0;
        for(int i = 0; i < offers.size(); i++)
        {
            if(offers.get(i).sleeve() == keep)
            {
                selected = i;
                break;
            }
        }
        int columns = Math.max(1, gridColumns());
        int rows = Math.max(1, gridRows());
        int row = selected / columns;
        int contentRows = (offers.size() + columns - 1) / columns;
        scroll = Math.max(0, Math.min(Math.max(0, contentRows - rows),
            Math.max(0, row - rows / 2)));
    }

    /** A frame of four thin bars, so whatever it rings is untouched. */
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
     * What the details bar says about a sleeve.
     * <p>
     * Master Duel writes a line of flavour for every protector -- "Protectors
     * depicting monsters fusing amid strangely glittering black gems", and so on
     * for all of them -- and that is what a player is actually choosing between.
     * It is shipped as {@code item.dueldimension.sleeves_<id>.desc}, one key per
     * sleeve, written by {@code tools/import_sleeves.py}.
     * <p>
     * <b>Falls back rather than showing a raw key.</b> {@code Component
     * .translatable} returns the key itself when there is no entry, which would
     * put "item.dueldimension.sleeves_x.desc" on screen; the catalogue is
     * imported from a dump that may not have a line for every id, so the generic
     * description is kept for the ones it does not. That generic line is also
     * the more useful one for a sleeve already owned, which is why ownership
     * still overrides it: at that point the question is no longer what it is but
     * where to put it on.
     */
    private static String blurbFor(CardSleevesType sleeve, boolean owned)
    {
        if(owned)
        {
            return "Choose these on a deck in the deck editor.";
        }
        String key = NAME_KEY + sleeve.getResourceName() + ".desc";
        if(net.minecraft.locale.Language.getInstance().has(key))
        {
            return Component.translatable(key).getString();
        }
        // What the product does, which is not obvious from a picture of a card
        // back: a sleeve is chosen PER DECK, in the editor, and it is the back
        // the deck's cards are printed on during a duel.
        return "Dresses one of your decks. Chosen per deck in the deck editor.";
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

