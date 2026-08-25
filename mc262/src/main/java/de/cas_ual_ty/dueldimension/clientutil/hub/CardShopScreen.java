package de.cas_ual_ty.dueldimension.clientutil.hub;

import de.cas_ual_ty.dueldimension.clientutil.CardImageManager;
import de.cas_ual_ty.dueldimension.clientutil.ClientProxy;
import de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil;
import de.cas_ual_ty.dueldimension.clientutil.DuelTextures;
import de.cas_ual_ty.dueldimension.clientutil.layout.Layout;
import de.cas_ual_ty.dueldimension.shop.ShopMessages;
import de.cas_ual_ty.dueldimension.shop.ShopStock;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

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
 * <p>
 * Ported to retained mode: {@code render} became {@code extractRenderState}, the
 * immediate-mode {@code RenderSystem} pack art and focus ring became
 * {@link DdBlitUtil} blits with a tint argument, and {@code font.drawShadow} the
 * extractor's shadowed {@code text}. The Forge {@code bindSmooth} filtered card
 * art per draw; there is no per-draw filter now (see PORTING.md / DuelTextures),
 * so the art is drawn as the pipeline stored it.
 * <p>
 * Every size on this screen comes out of {@link #measure()} in ONE ordered pass
 * (see {@link Geom}). Each region used to measure itself from the window edge
 * independently, which is why a bigger window showed barely more and a smaller
 * one drew the toolbar, the grid and the details bar on top of each other: no
 * two regions knew about each other. They are laid out in order now, each with
 * a minimum, and the leftover goes to the grid.
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
    /**
     * How many packs the CURRENT SHELF holds, filled by {@link #refresh()}.
     * <p>
     * The caption counts against this rather than against the whole catalogue:
     * "491 of 641" reads as a search result when it is really the tab's own
     * size, and it never tells the player how big the tab was.
     */
    private int categoryTotal;
    private int selected;
    private int scroll;
    /**
     * Where the grid was last frame, and when that frame was, so the same
     * scroll-velocity gate the collection uses can be asked here.
     * {@code drawing.cpp}:1375-1378.
     */
    private int lastScroll;
    private long lastFrameAt;
    /** When the player last actually scrolled, which is what the gate reads. */
    private long lastScrollAt;
    /** How long after a scroll the grid still counts as moving. */
    private static final long SCROLL_QUIET_MS = 250L;
    /**
     * Whether this frame may ASK for art it has never seen. False while the
     * list is moving faster than the eye reads it -- EDOPro does not queue a
     * decode for a row that is about to leave the screen, which is the whole
     * reason a flick does not stall there.
     */
    private boolean loadImages = true;
    private String notice = "";

    /** What the grid is filtered by, and in what order it is arranged. */
    private EditBox search;
    /**
     * The needle, held by the SCREEN rather than by the field.
     * <p>
     * The field is destroyed and rebuilt by every {@code rebuild()} and every
     * resize. Reading the needle off the widget meant a rebuild could lose the
     * typed text and silently un-filter the grid; holding it here means the
     * filter survives whatever happens to the widget.
     */
    private String searchText = "";
    private Sort sort = Sort.NAME;
    private boolean descending;

    /** The Buy button, kept so its label can be set without a rebuild. */
    private HubWidgets.TextureButton buyButton;

    /** Where in the thumb a scrollbar drag took hold, or -1 when not dragging. */
    private int scrollGrab = -1;
    /** Slack either side of the bar, so catching it does not need pixel aim. */
    private static final int BAR_GRAB = 3;

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

    /**
     * The four shelves the catalogue is split across.
     * <p>
     * Read off each set's own type, which the database already carries and the
     * server already sends with the stock — so switching tabs is a filter over
     * a list already in hand, not a second request.
     * <p>
     * Boosters is deliberately the catch-all rather than there being a fifth
     * "Other" tab. Everything that is not a deck and is not a single printing is
     * something you open for a random result, which is exactly what the tab
     * means; a Mega Pack and a Duelist Pack belong beside a booster, not in a
     * bucket of leftovers.
     */
    private enum Category
    {
        BOOSTERS("Boosters"), STARTER("Starter Decks"),
        STRUCTURE("Structure Decks"), SINGLES("Singles");

        private final String label;

        Category(String label)
        {
            this.label = label;
        }

        String label()
        {
            return label;
        }

        /** Which shelf a pack sits on. Matches the types the database writes. */
        static Category of(ShopStock.Pack pack)
        {
            String type = pack.type() == null ? "" : pack.type();
            return switch(type)
            {
                case "Single" -> SINGLES;
                case "Structure Deck" -> STRUCTURE;
                case "Starter Deck" -> STARTER;
                default -> BOOSTERS;
            };
        }
    }

    /**
     * Static, so the shelf you were browsing is still the one showing when you
     * come back — after opening a pack, or after closing and reopening the shop.
     * A player working through the Structure Decks should not be put back on
     * Boosters every time they buy something.
     * <p>
     * Session-lived rather than written to disk, which is the same treatment the
     * balance above gets.
     */
    private static Category category = Category.BOOSTERS;
    private boolean categoryOpen;

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

    /** Bounded both ways in one expression, since every size here is. */
    private static int clamp(int min, int max, int value)
    {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Every derived number for one window size.
     * <p>
     * A pure function of (width, height, font metrics, layout values) — NOT of
     * {@code shown}, {@code selected}, {@code search} or {@code points}. That is
     * the invariant that lets {@link #init()} place a widget and the render pass
     * draw beside it and have the two agree: a height that followed the list or
     * the selection would move under widgets that were positioned once, and the
     * search field's responder runs {@link #refresh()} on every keystroke
     * without rebuilding (rebuilding drops focus mid-word).
     */
    private record Geom(int leftW, int gridLeft, int gridTop, int controlsLines,
        int catW, int sortW, int dirW, int searchX, int searchW, int rowRight,
        int cols, int rows, int cell, int gridUsedW, int gridUsedH,
        int barH, int barTop, int captionTop, int gridBottom,
        int previewRoom, int artH, int previewH, int buyTop)
    {
    }

    /** Invalidated at the top of init() and of every frame; never stale. */
    private Geom geom;

    /**
     * The measurements for this window size.
     * <p>
     * Nothing {@link #measure()} calls may read this, or the first call
     * recurses: measure() works with locals and the accessors below are the
     * readers of what it produced.
     */
    private Geom geom()
    {
        if(geom == null)
        {
            geom = measure();
        }
        return geom;
    }

    /**
     * Lays the whole screen out, once, top to bottom.
     * <p>
     * The order matters and is the point of the method: the left column is
     * sized first because the grid's width comes off it, the toolbar next
     * because the grid's top comes off that, then the height is divided between
     * the grid and the details bar, and finally the left column's own contents
     * are fitted into what the bar left. Every region has a minimum and the
     * ones that can yield do so in a fixed order — the cell shrinks first
     * (free, and it buys rows), then the bar drops from two description lines
     * to one, then the row count falls to one and to none, then the art shrinks
     * to its minimum and is dropped. The caption row, the toolbar's lines and
     * the Buy row never shrink.
     */
    private Geom measure()
    {
        Layout layout = layout();
        int pad = pad();
        int gap = gap();
        int cellMin = Math.max(1, layout.i("grid.cellMin", 40));
        int cellMax = Math.max(cellMin, layout.i("grid.cellMax", 72));
        float aspect = Math.max(0.01F, layout.f("pack.aspect", 1F));
        int captionH = captionH();
        int captionGap = layout.i("caption.gap", 4);
        int margin = layout.i("bottom.margin", 4);

        // ---- the left column's WIDTH ------------------------------------
        // Measured against the PREFERRED bar rather than the final one. The
        // final bar is measured from the grid, the grid from gridLeft and
        // gridLeft from this, so taking the preferred height here is what cuts
        // the circle; the bar only ever grows past it, and a taller bar can
        // only shorten this column, which previewHeight() handles by clamping
        // to previewRoom().
        int barTopProv = height - margin - barHeight(layout.i("bottom.lines", 2));
        int artRoomProv = barTopProv - buyReserve() - detailBoxHeight() - 22;
        // Never wider than the art it can draw: a column whose width did not
        // know the art's height left blank panel down each side of a picture
        // squeezed by a short window.
        int leftW = Math.min((int)(width * layout.f("left.share", 0.26F)), artRoomProv + 16);
        // The grid always keeps one column, whatever the share works out to.
        leftW = Math.min(leftW, width - 3 * pad - cellMin - layout.i("grid.gutter", 10));
        leftW = clamp(layout.i("left.minWidth", 104), layout.i("left.maxWidth", 200), leftW);
        int gridLeft = leftW + pad * 2;
        int availW = availWidth(gridLeft);

        // ---- the toolbar ------------------------------------------------
        // Widths derived from the font rather than hand-tuned, because
        // HubWidgets.drawLabel CENTRES its label and therefore lets a button
        // narrower than its text bleed out of both ends of itself. Each is a
        // max over every label the button can ever show, so pressing one does
        // not re-flow the row.
        int rowH = rowH();
        int labelPad = layout.i("label.padding", 14);
        int rowRight = balanceLeft() - layout.i("balance.gap", 8);
        int lineW = Math.max(1, rowRight - gridLeft);
        int catW = clamp(layout.i("category.minWidth", 88), lineW, widestCategory() + labelPad);
        int sortW = clamp(layout.i("sort.minWidth", 62), lineW, widestSort() + labelPad);
        int dirW = clamp(layout.i("dir.minWidth", 30), lineW,
            Math.max(font.width("ASC"), font.width("DESC")) + labelPad);
        // Sort and direction are one inseparable group, right-anchored at
        // rowRight on whichever line they end up on: two anchors is how the
        // pair came to be subtracted leftward under the category button.
        int sortGroup = sortW + 2 + dirW;
        int searchMin = layout.i("search.minWidth", 72);
        int controlsLines = catW + 2 + searchMin + 2 + sortGroup <= lineW ? 1
            : catW + 2 + sortGroup <= lineW ? 2
            : 3;
        int searchX;
        int searchW;
        if(controlsLines == 1)
        {
            searchX = gridLeft + catW + 2;
            // The WHOLE remainder. Halving it was what left a wide window's
            // row stopping two thirds of the way across.
            searchW = rowRight - sortGroup - 2 - searchX;
        }
        else
        {
            // A line of its own, spanning the row. The field is never dropped:
            // it is the only way to find one of several hundred sets, and
            // exactly the window where scrolling is worst is the one that used
            // to take it away.
            searchX = gridLeft;
            searchW = rowRight - gridLeft;
        }
        searchW = Math.max(16, searchW);

        int controlsTop = pad + layout.i("controls.top", 0);
        int controlsH = controlsLines * rowH
            + (controlsLines - 1) * layout.i("controls.lineGap", 2);
        int gridTop = controlsTop + controlsH + layout.i("controls.gap", 6);

        // ---- the height, divided in order -------------------------------
        int room = height - margin - gridTop - captionH - 2 * captionGap;
        int minLines = Math.max(1, layout.i("bottom.minLines", 1));
        int wantLines = Math.max(minLines, layout.i("bottom.lines", 2));
        int maxLines = Math.max(wantLines, layout.i("bottom.maxLines", 4));
        // The bar yields to the grid's one row before the grid gives it up.
        int barH = clamp(barHeight(minLines), barHeight(wantLines), room - cellMin);
        int gridRoom = Math.max(0, room - barH);

        // The COUNTS come from the minimum cell and the SIZE from the fit,
        // which is the whole of "a bigger window shows more": counting from a
        // preferred cell keeps the same few columns and turns the rest of the
        // window into margin.
        int maxColumns = Math.max(1, layout.i("grid.maxColumns", 24));
        int rows = Math.max(0, (gridRoom + gap) / (cellMin + gap));
        int cols = clamp(1, maxColumns, (availW + gap) / (cellMin + gap));
        // The +gap credits the trailing gap that the last column and the last
        // row never draw; without it a whole column's width was thrown away.
        int widthFit = (availW + gap) / cols - gap;
        // Read back through the aspect, so growing a cell on one axis cannot
        // cost a row on the other when the art is not square.
        int heightFit = rows <= 0 ? widthFit
            : Math.round(((gridRoom + gap) / rows - gap) * aspect);
        int cell = clamp(cellMin, cellMax, Math.min(widthFit, heightFit));
        int cellH = Math.max(1, Math.round(cell / aspect));
        // Re-fit: a cell that grew must never overrun what it was counted into.
        cols = clamp(1, maxColumns, (availW + gap) / (cell + gap));
        rows = Math.max(0, (gridRoom + gap) / (cellH + gap));
        int gridUsedW = Math.max(0, cols * (cell + gap) - gap);
        int gridUsedH = Math.max(0, rows * (cellH + gap) - gap);
        // Whatever the grid could not spend on a whole row goes to the bar,
        // which is the one region that can use a few units. Growth order:
        // rows first, then the bar up to its maximum, then the residue sits
        // above the caption, where it is the only place it is invisible.
        barH = clamp(barHeight(minLines), barHeight(maxLines), barH + (gridRoom - gridUsedH));
        int barTop = height - margin - barH;
        // Pinned to the BAR rather than floating under the last row: the row
        // count floors, and the remainder under the last row belonged to
        // nothing -- up to a whole row's worth of window.
        int captionTop = barTop - captionGap - captionH;
        int gridBottom = captionTop - captionGap;

        // ---- the left column's CONTENT ----------------------------------
        // The same equation as previewRoomProv read from the other end, so the
        // Buy row riding the panel can never walk into the bar.
        int previewRoom = Math.max(0, barTop - buyReserve());
        int artRoom = previewRoom - detailBoxHeight() - 22;
        int artWanted = Math.round((leftW - 16) / aspect);
        int artMin = layout.i("art.minHeight", 32);
        // Dropped outright below its minimum rather than drawn as a smear.
        int artH = artWanted <= artRoom ? Math.max(0, artWanted)
            : artRoom >= artMin ? artRoom
            : 0;
        // Sized to its CONTENT and capped by its room, not stretched to fill:
        // a panel measured from the window went empty below "Released" by a
        // unit for every unit the window grew.
        int previewH = clamp(detailBoxHeight() + 16, previewRoom,
            (artH > 0 ? 22 + artH : 16) + detailBoxHeight());
        int buyTop = pad + previewH + layout.i("preview.gap", 8);

        return new Geom(leftW, gridLeft, gridTop, controlsLines, catW, sortW, dirW,
            searchX, searchW, rowRight, cols, rows, cell, gridUsedW, gridUsedH,
            barH, barTop, captionTop, gridBottom, previewRoom, artH, previewH, buyTop);
    }

    private int pad()
    {
        return layout().i("pad", 8);
    }

    private int gap()
    {
        return layout().i("grid.gap", 4);
    }

    /** ONE height for the whole top row: buttons, search frame, balance plate. */
    private int rowH()
    {
        return Math.max(1, layout().i("controls.height", 16));
    }

    private int captionH()
    {
        return font.lineHeight + 2;
    }

    /**
     * What the Buy row costs the left column, summed from its parts.
     * <p>
     * It was one undecomposed number that did not know how tall the button is,
     * so changing the button height or adding a third button walked the row
     * into the details bar without anything noticing.
     */
    private int buyReserve()
    {
        Layout layout = layout();
        return layout.i("bottom.gap", 4) + layout.i("buy.height", 20)
            + layout.i("preview.gap", 8) + layout.i("pad", 8);
    }

    private int availWidth(int gridLeft)
    {
        // The gutter is the scroll bar's furniture -- 4 of inset frame, 2 of
        // gap and 4 of bar -- so it is part of the column budget rather than
        // borrowed from the window's own padding.
        return Math.max(layout().i("grid.cellMin", 40),
            width - gridLeft - pad() - layout().i("grid.gutter", 10));
    }

    private int availW()
    {
        return availWidth(gridLeft());
    }

    private int leftWidth()
    {
        return geom().leftW();
    }

    private int gridLeft()
    {
        return geom().gridLeft();
    }

    /** The widest a category button will ever have to be, arrow included. */
    private int widestCategory()
    {
        int widest = 0;
        for(Category option : Category.values())
        {
            widest = Math.max(widest, Math.max(font.width(option.label() + " ▾"),
                font.width(option.label() + " ▴")));
        }
        return widest;
    }

    private int widestSort()
    {
        int widest = 0;
        for(Sort option : Sort.values())
        {
            widest = Math.max(widest, font.width(option.label()));
        }
        return widest;
    }

    /**
     * The balance plate's width: a fixed digit budget, not the balance's own.
     * <p>
     * The control row stops short of this plate, and the row is placed once in
     * init() while the plate is measured every frame. A width that followed
     * {@code points} therefore slid the plate under a button the moment a
     * purchase changed the figure — the reported "ASC 027". A plate that cannot
     * change width needs no rebuild on the packet at all.
     */
    private int balanceWidth()
    {
        return font.width("0".repeat(Math.max(1, layout().i("balance.digits", 6)))) + 34;
    }

    private int balanceLeft()
    {
        return width - pad() - balanceWidth();
    }

    /** Where the control row has to stop, so it cannot run under the plate. */
    private int rowRight()
    {
        return geom().rowRight();
    }

    private int categoryWidth()
    {
        return geom().catW();
    }

    private int sortWidth()
    {
        return geom().sortW();
    }

    private int dirWidth()
    {
        return geom().dirW();
    }

    private int searchX()
    {
        return geom().searchX();
    }

    private int searchWidth()
    {
        return geom().searchW();
    }

    /** The row the search field and the sort controls sit on. */
    private int controlsTop()
    {
        return pad() + layout().i("controls.top", 0);
    }

    private int controlsLines()
    {
        return geom().controlsLines();
    }

    private int controlsHeight()
    {
        return controlsLines() * rowH()
            + (controlsLines() - 1) * layout().i("controls.lineGap", 2);
    }

    private int controlsLineY(int line)
    {
        return controlsTop() + line * (rowH() + layout().i("controls.lineGap", 2));
    }

    /** Which line the sort group ended up on: its own only when wrapped twice. */
    private int sortLine()
    {
        return controlsLines() == 3 ? 1 : 0;
    }

    /** Which line the search field ended up on: the last one, always. */
    private int searchLine()
    {
        return controlsLines() - 1;
    }

    /**
     * Where the open category list starts.
     * <p>
     * Over the GRID rather than under its button, and the reason is a trap
     * worth the comment: children are drawn and hit-tested in addition order,
     * but drawing takes the LAST and hit-testing takes the FIRST. A list added
     * last draws on top and loses its clicks to whatever is underneath it. The
     * grid is not a widget, so nothing there can steal them — and with a
     * wrapped toolbar a button-anchored list would cover the search field.
     */
    private int dropdownTop()
    {
        return gridTop() - 2;
    }

    private int gridTop()
    {
        return geom().gridTop();
    }

    private int gridBottom()
    {
        return geom().gridBottom();
    }

    private int gridColumns()
    {
        return geom().cols();
    }

    /** Legally zero on a very short window: nothing may assume a row fits. */
    private int gridRows()
    {
        return geom().rows();
    }

    private int cellW()
    {
        return geom().cell();
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
        return Math.max(1, Math.round(cellW() / Math.max(0.01F, layout().f("pack.aspect", 1F))));
    }

    /** One row of the grid, cell and the gap after it. */
    private int cellPitch()
    {
        return cellH() + gap();
    }

    private int gridUsedW()
    {
        return geom().gridUsedW();
    }

    private int gridUsedH()
    {
        return geom().gridUsedH();
    }

    /**
     * Whether the pointer is over the cells themselves.
     * <p>
     * Read by the wheel and by {@link #packAt}, so the two cannot disagree
     * about where the grid is. The wheel used to consume every scroll on the
     * screen, which moved the grid while the pointer was on the preview panel
     * or on an open dropdown and left no other region able to have it.
     */
    private boolean overGridRegion(double mouseX, double mouseY)
    {
        return gridRows() > 0 && gridColumns() > 0
            && mouseX >= gridLeft() && mouseX < gridLeft() + gridUsedW()
            && mouseY >= gridTop() && mouseY < gridTop() + gridUsedH();
    }

    private int totalRows()
    {
        int columns = Math.max(1, gridColumns());
        return (shown.size() + columns - 1) / columns;
    }

    private int maxScroll()
    {
        return Math.max(0, totalRows() - gridRows());
    }

    /**
     * Pinned to the window's right edge rather than to the last column.
     * <p>
     * The columns floor, so there is up to a pitch of horizontal remainder;
     * hanging the bar off the last cell left that remainder as bare gradient
     * at the window edge, and putting the bar at the edge turns it into a
     * gutter between two pieces of furniture instead.
     */
    private int trackX()
    {
        return width - pad() - 4;
    }

    private int trackY()
    {
        return gridTop();
    }

    private int trackH()
    {
        return gridUsedH();
    }

    private int thumbHeight()
    {
        int track = trackH();
        return Math.min(Math.max(0, track),
            Math.max(12, track * gridRows() / Math.max(1, totalRows())));
    }

    private int thumbY()
    {
        int overflow = maxScroll();
        if(overflow <= 0)
        {
            return trackY();
        }
        return trackY() + (trackH() - thumbHeight()) * Math.min(scroll, overflow) / overflow;
    }

    /**
     * Takes hold of the grid's scrollbar.
     * <p>
     * The bar reported the position and took no input at all before, which with
     * five hundred boosters over four visible rows meant fifty wheel notches to
     * reach the end and an inert bar where the player reaches. Same handling as
     * the deck editor's own.
     *
     * @return whether the bar took this click
     */
    private boolean grabScrollBar(double mouseX, double mouseY)
    {
        int track = trackH();
        if(track <= 0 || maxScroll() <= 0
            || mouseX < trackX() - BAR_GRAB || mouseX >= trackX() + 4 + BAR_GRAB
            || mouseY < trackY() || mouseY >= trackY() + track)
        {
            return false;
        }
        int thumbH = thumbHeight();
        int thumbY = thumbY();
        // Grab the thumb where it was taken hold of; clicking bare track puts
        // the thumb's middle under the cursor, as every other bar does.
        scrollGrab = mouseY >= thumbY && mouseY < thumbY + thumbH
            ? (int)(mouseY - thumbY) : thumbH / 2;
        dragScrollBar(mouseY);
        return true;
    }

    /** Scrubs the grid to wherever the thumb has been dragged. */
    private void dragScrollBar(double mouseY)
    {
        int travel = trackH() - thumbHeight();
        int max = maxScroll();
        if(travel <= 0 || max <= 0)
        {
            scroll = 0;
            return;
        }
        double top = mouseY - scrollGrab - trackY();
        scroll = (int)Math.clamp(Math.round(top / travel * max), 0, max);
        // Dragging the bar is scrolling too, and it is the faster of the two.
        lastScrollAt = System.currentTimeMillis();
    }

    /** Holds the scroll position inside what there is to scroll through. */
    private void clampScroll()
    {
        scroll = clamp(0, maxScroll(), scroll);
    }

    /**
     * How tall a details bar holding this many description lines is.
     * <p>
     * A line count rather than a pixel constant, and the same expression the
     * bar is DRAWN from: the title row and its gap (22), the lines at their own
     * pitch, and the inset's own padding (6). The old constant 54 honestly held
     * two lines while the draw computed three, which is the reported clipping;
     * both numbers come from here now.
     */
    private int barHeight(int lines)
    {
        return 22 + Math.max(0, lines) * (font.lineHeight + 1) + 6;
    }

    /** How many description lines the bar was actually granted. */
    private int barLines()
    {
        return Math.max(1, (geom().barH() - 28) / (font.lineHeight + 1));
    }

    private int barTop()
    {
        return geom().barTop();
    }

    private int barBottom()
    {
        return geom().barTop() + geom().barH();
    }

    /** The line under the grid, shared by the count and by the notice. */
    private int captionTop()
    {
        return geom().captionTop();
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

    /**
     * And where it starts vertically, which used to be a bare literal that only
     * fitted because the bar happened to be at least 22 tall.
     */
    private int closeTop()
    {
        return clamp(barTop() + 2, barBottom() - 22, barTop() + (geom().barH() - 20) / 2);
    }

    /** The width the blurb wraps to, read by both the measure and the draw. */
    private int descriptionWrapWidth()
    {
        int right = closeLeft() - layout().i("close.gap", 4);
        return Math.max(16, right - 6 - (pad() + 6) - 12);
    }

    /** How many of those lines fit in the bar as it was granted. */
    private int descriptionLines()
    {
        return barLines();
    }

    /** One row of the preview's fact list. */
    private static final int DETAIL_ROW_H = 12;

    /**
     * How many facts the preview lists — a CONSTANT four.
     * <p>
     * The fourth row is the release date when the set carries one and the set
     * code when it does not, so no row is ever blank and the box never changes
     * size. Omitting the row instead made the box's height follow the
     * selection, which made the panel's height follow it, which moved the Buy
     * button — and the Buy button is placed in init() while the panel is drawn
     * per frame.
     */
    private static final int DETAIL_ROWS = 4;

    /** The fact list's box: a row each, plus the inset's own padding. */
    private static int detailBoxHeight()
    {
        return DETAIL_ROWS * DETAIL_ROW_H + 7;
    }

    /** Guard only: a retuned panel cannot push half a row through its border. */
    private int detailRowsShown()
    {
        return clamp(0, DETAIL_ROWS, (previewHeight() - 23) / DETAIL_ROW_H);
    }

    /** How much room the left column HAS, once the Buy row is reserved. */
    private int previewRoom()
    {
        return geom().previewRoom();
    }

    private int artRoom()
    {
        return previewRoom() - detailBoxHeight() - 22;
    }

    /**
     * How tall the pack art may be drawn, or zero when there is no room worth
     * drawing it in.
     * <p>
     * The fact list is sized FIRST and the art takes what is left over, because
     * the list is text and half a price row is unreadable while a smaller
     * picture is not. Measured against the column's ROOM rather than against
     * its height, which would be circular -- the height is derived from this.
     */
    private int previewArtHeight()
    {
        return geom().artH();
    }

    /** The left panel's height: its content, capped by its room. */
    private int previewHeight()
    {
        return geom().previewH();
    }

    /** Where the left column's panel ends. */
    private int previewBottom()
    {
        return pad() + previewHeight();
    }

    /**
     * Where the Buy row starts.
     * <p>
     * It rides the PANEL rather than being pinned above the details bar: the
     * button acts on the panel above it and has to stay under it. previewRoom()
     * is the same equation read from the other end, so riding the panel can
     * never walk into the bar.
     */
    private int buyTop()
    {
        return geom().buyTop();
    }

    @Override
    protected void init()
    {
        // Measured fresh, so a resize or a hot-reloaded layout is picked up.
        geom = null;
        // BEFORE the first widget. refresh() is what decides which packs are
        // shown, and the Buy label below reads current() out of that list --
        // read the other way round the shop opened quoting one pack's price
        // under another pack's picture. init() also runs again on every resize,
        // and a wider window fits more per row, which can leave the scroll
        // position past the end of a list that now needs fewer rows.
        refresh();
        clampScroll();

        Layout layout = layout();
        int pad = pad();
        int rowH = rowH();
        int catW = categoryWidth();
        int sortY = controlsLineY(sortLine());
        int searchY = controlsLineY(searchLine());
        // Right-anchored unconditionally, so the pair has one anchor rather
        // than two and can never be subtracted leftward under the category
        // button on a narrow window.
        int dirX = rowRight() - dirWidth();
        int sortX = dirX - 2 - sortWidth();

        // Category, search, sort and direction share the control row while
        // there is room, and the row WRAPS when there is not. A strip of four
        // tabs cost a whole row of the grid -- at 920p the cells pitch 50 apart
        // and the strip ate exactly that -- and a dropdown says the same thing
        // in a quarter of the width.
        addRenderableWidget(new HubWidgets.TextureButton(gridLeft(), controlsLineY(0), catW, rowH,
            Component.literal(category.label() + (categoryOpen ? " ▴" : " ▾")),
            pressed ->
        {
            categoryOpen = !categoryOpen;
            rebuild();
        }));

        // Always built, never dropped. The text lives on the screen, so a
        // rebuild or a resize can neither lose it nor silently un-filter the
        // grid; the responder is attached after the value is set so restoring
        // it does not count as a keystroke.
        search = new EditBox(font, searchX() + 4, searchY + 3,
            Math.max(1, searchWidth() - 8), font.lineHeight + 3,
            Component.literal("Search"));
        search.setBordered(false);
        search.setY(search.getY() + (search.getHeight() - 8) / 2);
        search.setValue(searchText);
        search.setResponder(value ->
        {
            searchText = value;
            scroll = 0;
            refresh();
        });
        addWidget(search);

        addRenderableWidget(new HubWidgets.TextureButton(sortX, sortY,
            sortWidth(), rowH, Component.literal(sort.label()), pressed ->
        {
            sort = sort.next();
            scroll = 0;
            refresh();
            rebuild();
        }));
        addRenderableWidget(new HubWidgets.TextureButton(dirX, sortY, dirWidth(), rowH,
            Component.literal(descending ? "DESC" : "ASC"), pressed ->
        {
            descending = !descending;
            scroll = 0;
            refresh();
            rebuild();
        }));

        // The open list, added after the row it belongs to so it is extracted
        // after it and therefore drawn over it. Nothing it covers on the grid
        // is a widget, so nothing underneath can take its clicks -- see
        // dropdownTop().
        if(categoryOpen)
        {
            Category[] categories = Category.values();
            for(int i = 0; i < categories.length; i++)
            {
                Category option = categories[i];
                addRenderableWidget(new HubWidgets.TabButton(gridLeft(),
                    dropdownTop() + i * rowH, catW, rowH, Component.literal(option.label()),
                    () -> category == option, pressed ->
                {
                    category = option;
                    categoryOpen = false;
                    scroll = 0;
                    refresh();
                    rebuild();
                }));
            }
        }

        // The button carries the whole cost rather than the unit price: buying
        // ten is the one time a player wants to know the total before pressing.
        int leftW = leftWidth();
        int buyH = layout.i("buy.height", 20);
        int bulkW = Math.min(layout.i("bulk.width", 34), Math.max(1, leftW / 3));
        int buyW = Math.max(1, leftW - bulkW - 2);
        int buyY = buyTop();
        buyButton = new HubWidgets.TextureButton(pad, buyY, buyW, buyH,
            Component.literal("Buy"), pressed -> buy());
        addRenderableWidget(buyButton);
        refreshBuyLabel();
        addRenderableWidget(new HubWidgets.TextureButton(pad + buyW + 2, buyY, bulkW, buyH,
            Component.literal("x" + bulk), pressed ->
        {
            bulk = nextBulk();
            rebuild();
        }));
        addRenderableWidget(new HubWidgets.TextureButton(closeLeft(), closeTop(),
            layout.i("close.width", 70), 20, Component.literal("Close"), pressed -> onClose()));
    }

    /**
     * Puts the current total on the Buy button.
     * <p>
     * Set on the widget every frame rather than rebuilt with the selection: a
     * rebuild recreates the search field and drops focus mid-word, and the
     * balance the price is compared against arrives from the server on its own
     * schedule. SleeveShopScreen's refreshBuyButton documents the same reason.
     */
    private void refreshBuyLabel()
    {
        if(buyButton == null)
        {
            return;
        }
        ShopStock.Pack pack = current();
        buyButton.setMessage(Component.literal(pack == null ? "Buy"
            : isCreative() ? "Buy (free)"
            : "Buy  " + pack.price() * bulk));
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
        String needle = searchText.trim().toLowerCase(java.util.Locale.ROOT);

        List<ShopStock.Pack> matching = new ArrayList<>();
        int total = 0;
        for(ShopStock.Pack pack : packs)
        {
            // The tab narrows first, then the search narrows within it. A search
            // that reached across tabs would return results the player cannot
            // see without guessing which tab they landed on.
            if(Category.of(pack) != category)
            {
                continue;
            }
            // Counted here, in the pass that is already running, so the caption
            // can say how big the shelf is rather than how big the catalogue is.
            total++;
            if(needle.isEmpty() || matches(pack, needle))
            {
                matching.add(pack);
            }
        }
        categoryTotal = total;
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
        notice = "";
        // Here as well as in init(), because this runs from the search field's
        // responder without a rebuild: a needle that shortens the list would
        // otherwise leave the scroll position past the end of it and the grid
        // drawing nothing until the player scrolled back up.
        clampScroll();
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
        ClientPlayNetworking.send(new ShopMessages.Buy(pack.code(), bulk));
    }

    // ---- input ----

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick)
    {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if(button == 1 && search != null && search.isMouseOver(mouseX, mouseY))
        {
            // Right-click empties a search box, here as in the deck editor.
            search.setValue("");
            search.setFocused(true);
            return true;
        }
        // Widgets first. The grid covers most of the screen, and a control
        // drawn over it should be the thing that receives a click on it.
        if(super.mouseClicked(event, doubleClick))
        {
            return true;
        }
        // An open list closes on a click that missed it, and swallows that
        // click: it is covering the grid, so letting it through would select
        // whatever happened to be underneath the option the player was aiming
        // at and just missed.
        if(categoryOpen)
        {
            categoryOpen = false;
            rebuild();
            return true;
        }
        if(grabScrollBar(mouseX, mouseY))
        {
            return true;
        }
        int index = packAt(mouseX, mouseY);
        if(index >= 0)
        {
            selected = index;
            notice = "";
            // No rebuild: the Buy label is set on the widget every frame, and
            // rebuilding here stole the focus from the search field.
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY)
    {
        if(scrollGrab >= 0)
        {
            dragScrollBar(event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event)
    {
        scrollGrab = -1;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double delta)
    {
        // The art gate is told the player is moving. Measured travel cannot be
        // used for this: a resize reflows the grid and re-clamps `scroll`,
        // which reads as movement nobody made, and the packs blink between
        // their art and the placeholder as the gate follows it.
        lastScrollAt = System.currentTimeMillis();
        // Only over the cells. Consuming every scroll on the screen moved the
        // grid while the pointer was on the preview panel or an open dropdown,
        // and left no other region able to be given the wheel.
        if(!overGridRegion(mouseX, mouseY))
        {
            return super.mouseScrolled(mouseX, mouseY, scrollX, delta);
        }
        // A page at a time with a modifier held: several hundred boosters over
        // four visible rows is a long way at one row a notch.
        int step = pagingModifier() ? Math.max(1, gridRows()) : 1;
        scroll -= (int)Math.signum(delta) * step;
        clampScroll();
        return true;
    }

    /**
     * Whether Shift or Control is held for a paging wheel notch.
     * <p>
     * Read from the window rather than from {@code Screen.hasShiftDown()},
     * which reports the modifier carried by a key EVENT -- and a wheel notch is
     * not one. Same reading DeckEditorScreen's own wheel does.
     */
    private static boolean pagingModifier()
    {
        return keyDown(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT)
            || keyDown(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT)
            || keyDown(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL)
            || keyDown(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    private static boolean keyDown(int key)
    {
        return com.mojang.blaze3d.platform.InputConstants.isKeyDown(
            net.minecraft.client.Minecraft.getInstance().getWindow(), key);
    }

    @Override
    public boolean keyPressed(KeyEvent event)
    {
        // The search field takes the keyboard while it has focus, so typing a
        // set's name does not also fire whatever the letters are bound to --
        // and Escape still closes the shop rather than being swallowed.
        if(search != null && search.isFocused() && event.key() != 256
            && search.keyPressed(event))
        {
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event)
    {
        if(search != null && search.isFocused() && search.charTyped(event))
        {
            return true;
        }
        return super.charTyped(event);
    }

    private int packAt(double mouseX, double mouseY)
    {
        // Bounded BEFORE the divide. A cast to int truncates toward zero, so a
        // click above the grid gave (int)(-0.36) == 0 rather than something
        // negative, the row < 0 guard never fired, and pressing the search box
        // selected whatever pack was in the top-left.
        if(!overGridRegion(mouseX, mouseY))
        {
            return -1;
        }
        int columns = gridColumns();
        int column = (int)((mouseX - gridLeft()) / (cellW() + gap()));
        int row = (int)((mouseY - gridTop()) / cellPitch());
        if(column >= columns || row >= gridRows())
        {
            return -1;
        }
        int index = (row + scroll) * columns + column;
        return index < shown.size() ? index : -1;
    }

    // ---- rendering ----

    @Override
    public void extractRenderState(GuiGraphicsExtractor poseStack, int mouseX, int mouseY, float partialTick)
    {
        // One measure per frame. Fifty int operations, and it is what keeps the
        // layout inspector's hot reload working: the screen holds no copy of
        // any layout number for longer than a frame.
        geom = null;

        // Gated on the player's INPUT, not on a position delta.
        //
        // Measuring how far the grid moved sounds equivalent and is not: a
        // resize reflows the grid and re-clamps `scroll`, so the delta flips
        // between zero and non-zero from one frame to the next with no input at
        // all. The gate alternated with it and every pack blinked between its
        // art and the placeholder. Asking "did they scroll recently" cannot
        // oscillate, because only a real scroll sets it.
        long now = System.currentTimeMillis();
        loadImages = now - lastScrollAt >= SCROLL_QUIET_MS;
        lastFrameAt = now;
        lastScroll = scroll;
        // The dim Forge's renderBackground drew, not extractBackground: that
        // BLURS in 26.2, the blur is once-per-frame, and the frame a screen
        // opens over another that already asked for it took the client down.
        // Same decision as EngineDuelScreen, for the same crash.
        poseStack.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);

        refreshBuyLabel();
        renderControls(poseStack);
        renderPreview(poseStack);
        renderGrid(poseStack, mouseX, mouseY);
        renderDetails(poseStack, mouseX, mouseY);
        renderNotice(poseStack);
        renderBalance(poseStack);

        super.extractRenderState(poseStack, mouseX, mouseY, partialTick);
        if(search != null)
        {
            search.extractRenderState(poseStack, mouseX, mouseY, partialTick);
        }
    }

    /** Left: the highlighted pack, large, with what it costs. */
    private void renderPreview(GuiGraphicsExtractor poseStack)
    {
        int pad = pad();
        int leftW = leftWidth();
        int panelH = previewHeight();
        if(leftW <= 0 || panelH <= 0)
        {
            return;
        }
        NineSlice.draw(poseStack, HubTextures.PANEL, pad, pad, leftW, panelH);
        ShopStock.Pack pack = current();
        if(pack == null)
        {
            return;
        }
        int artH = previewArtHeight();
        int boxY = pad + 8;
        if(artH > 0)
        {
            // Centred in the width it was allowed, since a clamped art is
            // narrower than the panel and hard against its left border
            // otherwise. The column's width is capped by what the art can be
            // tall, so that margin only shows at the minimum window size.
            int artW = Math.round(artH * layout().f("pack.aspect", 1F));
            drawPackArt(poseStack, pack, pad + 8 + (leftW - 16 - artW) / 2, pad + 8, artW, artH);
            boxY = pad + 8 + artH + 6;
        }

        // The facts about the product, as a label-and-value list rather than
        // three sentences stacked up. Labels down the left in one weight,
        // values down the right in another: the eye reads either column on its
        // own, which is what makes a list of unrelated facts scannable.
        String released = releaseDate(pack);
        int boxX = pad + 6;
        int boxW = leftW - 12;
        int boxH = detailBoxHeight();
        if(boxW <= 0 || boxH <= 0)
        {
            return;
        }
        NineSlice.draw(poseStack, HubTextures.PANEL_INSET, boxX, boxY, boxW, boxH);

        int rows = detailRowsShown();
        int rowY = boxY + 5;
        if(rows > 0)
        {
            detail(poseStack, boxX, boxW, rowY, "Contents", pack.deck() ? "1 DECK" : "1 PACK",
                0xFFC2C9D6);
            rowY += DETAIL_ROW_H;
        }
        if(rows > 1)
        {
            // How many cards actually come out, which is what the price is per.
            detail(poseStack, boxX, boxW, rowY, "Cards",
                Integer.toString(pack.cardsPerPack()), 0xFFC2C9D6);
            rowY += DETAIL_ROW_H;
        }
        if(rows > 2)
        {
            detail(poseStack, boxX, boxW, rowY, "Price",
                isCreative() ? "FREE" : pack.price() + " DP",
                isCreative() ? 0xFF7CE38B : 0xFFF4D089);
            rowY += DETAIL_ROW_H;
        }
        if(rows > 3)
        {
            // The grid runs newest first, so the date is what tells a player
            // where in the run of sets they are looking. A set without one
            // shows its CODE instead of leaving the row out: an absent row made
            // the box's height follow the selection, and the Buy button that
            // rides under the box is placed once rather than per frame.
            detail(poseStack, boxX, boxW, rowY,
                released != null ? "Released" : "Code",
                released != null ? released : pack.code(), 0xFF9AA2B2);
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
    private void detail(GuiGraphicsExtractor poseStack, int x, int width, int y, String label,
        String value, int colour)
    {
        poseStack.text(font, label, x + 6, y, 0xFF6E7686, true);
        String shown = shorten(value, width - 12 - font.width(label) - 6);
        poseStack.text(font, shown, x + width - 6 - font.width(shown), y, colour, true);
    }

    /**
     * A value cut down to the room it has, from the FRONT.
     * <p>
     * Marked with an ellipsis when anything was dropped. A date is still
     * recognisable from its tail, but "12000 DP" quietly shortened to "000 DP"
     * reads as a smaller number rather than as a truncation.
     */
    private String shorten(String value, int room)
    {
        if(font.width(value) <= room)
        {
            return value;
        }
        String shown = value;
        while(font.width("..." + shown) > room && shown.length() > 1)
        {
            shown = shown.substring(1);
        }
        return "..." + shown;
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

    /** How much of the shelf is showing, and where in it the grid is. */
    private String countCaption()
    {
        String count = shown.size() == categoryTotal
            ? categoryTotal + " " + category.label()
            : shown.size() + " of " + categoryTotal;
        // No row readout. The scrollbar already shows the position, and a
        // caption that changes on every notch reads as the screen glitching
        // rather than as information.
        return count;
    }

    /** The search field's frame, and how much of the catalogue is showing. */
    private void renderControls(GuiGraphicsExtractor poseStack)
    {
        // Drawn at the width the row RESERVED, not at the field's own plus
        // eight: that was two wider than reserved and ate the gap before the
        // sort button.
        NineSlice.draw(poseStack, HubTextures.SEARCH_FIELD, searchX(),
            controlsLineY(searchLine()), searchWidth(), rowH());

        // Under the grid rather than above it: above put it behind the search
        // field, which is drawn later and covered it. Its row is pinned to the
        // details bar, so the bar cannot cover it either. Dropped for the frame
        // when a notice needs the row -- the notice is the urgent one, and the
        // count comes back by widening the window.
        String count = countCaption();
        if(notice.isEmpty() || !noticeCoversCount(count))
        {
            poseStack.text(font, count, gridLeft(), captionTop(), 0xFF7A8090, true);
        }

        // The thumb's length reports how much of the catalogue is on screen and
        // its position where in it you are.
        int track = trackH();
        if(maxScroll() > 0 && track > 0)
        {
            NineSlice.draw(poseStack, HubTextures.SCROLLBAR, trackX(), trackY(), 4, track, 0, 2);
            NineSlice.draw(poseStack, HubTextures.SCROLLBAR, trackX(), thumbY(), 4,
                thumbHeight(), 1, 2);
        }
    }

    /** Whether the notice and the count are fighting over the same row. */
    private boolean noticeCoversCount(String count)
    {
        int right = closeLeft() - layout().i("close.gap", 4);
        return right - 8 - font.width(notice) < gridLeft() + font.width(count) + 8;
    }

    /** Centre: every pack, the highlighted one ringed. */
    private void renderGrid(GuiGraphicsExtractor poseStack, int mouseX, int mouseY)
    {
        int columns = gridColumns();
        int rows = gridRows();
        int cellW = cellW();
        int cellH = cellH();
        int gap = gap();
        int left = gridLeft();
        int top = gridTop();
        int usedW = gridUsedW();
        int usedH = gridUsedH();
        if(rows <= 0 || columns <= 0 || usedW <= 0 || usedH <= 0)
        {
            return;
        }

        // Hugging the cells rather than a trailing gap, which is what let the
        // frame cross into the caption row.
        NineSlice.draw(poseStack, HubTextures.PANEL_INSET, left - 4, top - 4,
            usedW + 8, usedH + 8);

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
    private void renderDetails(GuiGraphicsExtractor poseStack, int mouseX, int mouseY)
    {
        int pad = pad();
        int y = barTop();
        int bottomH = barBottom() - barTop();
        // The box stops where the Close button starts. It used to run the full
        // width of the screen and the button sat on top of it, so the two read
        // as one piece of furniture with a button embedded in its corner; they
        // are separate things and now look it.
        int right = closeLeft() - layout().i("close.gap", 4);
        if(right - pad <= 0 || bottomH <= 0)
        {
            return;
        }
        NineSlice.draw(poseStack, HubTextures.PANEL, pad, y, right - pad, bottomH);

        ShopStock.Pack pack = current();
        if(pack == null)
        {
            return;
        }
        int textY = y + 7;
        // The metrics run along the right of the title row, as the reference's
        // bar does: how many cards, what it costs, how much of it you have.
        String cards = "x " + pack.cardsPerPack();
        String price = isCreative() ? "FREE" : pack.price() + " DP";
        int owned = ownedIn(pack);
        int total = Math.max(0, pack.distinctCards());
        String complete = percentage(owned, total) + "%";
        // Each place is measured before anything is drawn, and from the box's
        // own edge rather than the screen's, so the metrics stay inside it now
        // that it is shorter -- and so the name below knows where they start.
        int completeX = right - 8 - font.width(complete);
        int priceX = completeX - 12 - font.width(price);
        int cardsX = priceX - 12 - font.width(cards);
        int glyphX = cardsX - 9;

        // Trimmed to what the row has left rather than drawn at full length.
        // The catalogue really does carry eighty-character set names, and one
        // ran straight through the card glyph, the counts and the price and out
        // past the panel's right edge. Dropped from the END, because a set is
        // known by its opening words -- the opposite of detail(), whose values
        // are told apart by their tails.
        String kind = pack.deck() ? "DECK" : "PACK";
        String name = font.plainSubstrByWidth(pack.name(),
            Math.max(0, glyphX - 6 - (pad + 12) - font.width(kind)));
        poseStack.text(font, name, pad + 8, textY, 0xFFE6EAF2, true);
        poseStack.text(font, kind, pad + 12 + font.width(name), textY,
            pack.deck() ? 0xFF9FD4FF : 0xFF7A8090, true);

        poseStack.text(font, complete, completeX, textY, 0xFF9FD4FF, true);
        // A percentage says how close you are and not how far there is to go.
        // The counts behind it are what a collector actually wants, so they are
        // one hover away rather than taking a permanent place on the row.
        boolean overCompletion = mouseX >= completeX && mouseX < completeX + font.width(complete)
            && mouseY >= textY - 2 && mouseY < textY + 10;
        String ratio = overCompletion ? owned + " of " + total : null;
        poseStack.text(font, price, priceX, textY, isCreative() ? 0xFF7CE38B : 0xFFF4D089, true);
        poseStack.text(font, cards, cardsX, textY, 0xFFC2C9D6, true);
        // A card glyph before the count, standing in for the reference's icon.
        NineSlice.image(poseStack, DuelTextures.COVER, glyphX, textY - 1, 5, 8);

        int descriptionY = textY + 14;
        // Inset within the box, which is itself already clear of the button.
        int descriptionX = pad + 6;
        int wrap = descriptionWrapWidth();
        // The 22 is the title row and its gap, the same 22 barHeight() reserves.
        // It is NOT the nine-slice's cell size: the generator draws the frame as
        // two 1px rounded outlines, so the visible rim is 2px.
        int insetH = Math.max(0, bottomH - 22);
        if(insetH > 0)
        {
            NineSlice.draw(poseStack, HubTextures.PANEL_INSET, descriptionX, descriptionY - 3,
                wrap + 12, insetH);
        }
        // Both the line budget and the bar's height come from barHeight() now,
        // so the box holds exactly what it says it holds. It is derived from the
        // WINDOW and never from the shown list: refresh() runs from the search
        // field's responder on every keystroke without rebuilding, so a
        // list-derived height would move the bar out from under the Close and
        // Buy widgets, and gridRows() measures from the bar, so the grid would
        // re-flow under the cursor as the player typed.
        int maxLines = descriptionLines();
        int pitch = font.lineHeight + 1;
        List<net.minecraft.util.FormattedCharSequence> lines =
            font.split(Component.literal(pack.description()), wrap);
        for(int i = 0; i < Math.min(maxLines, lines.size()); i++)
        {
            net.minecraft.util.FormattedCharSequence line = lines.get(i);
            poseStack.text(font, line, descriptionX + 6, descriptionY, 0xFFC2C9D6);
            if(i == maxLines - 1 && lines.size() > maxLines)
            {
                // Marked, the way detail() marks a shortened value, so a
                // truncation reads as one. Held inside the wrap width, since
                // the line it follows may already fill it.
                int mark = font.width("…");
                poseStack.text(font, "…", Math.min(descriptionX + 6 + font.width(line),
                    descriptionX + 6 + wrap - mark), descriptionY, 0xFFC2C9D6, true);
            }
            descriptionY += pitch;
        }

        // Last, so it is over the blurb it is standing on rather than under it.
        if(ratio != null)
        {
            int tipW = font.width(ratio) + 12;
            // Anchored to where the completion was MEASURED. The running cursor
            // this used to read had already walked left past the price and the
            // count by the time it got here, so the tip appeared eighty pixels
            // away over the card glyph it does not explain.
            int tipX = Math.max(2, Math.min(completeX + font.width(complete) / 2 - tipW / 2,
                width - tipW - 2));
            int tipY = textY - 16;
            NineSlice.draw(poseStack, HubTextures.PANEL, tipX, tipY, tipW, 14);
            poseStack.text(font, ratio, tipX + 6, tipY + 3, 0xFF9FD4FF, true);
        }
    }

    /**
     * Why the last click did nothing.
     * <p>
     * On the strip between the grid and the details bar rather than inside the
     * bar, where it was pinned to {@code height - 18} and written straight
     * across the blurb's third line -- SleeveShopScreen moved its own notice out
     * of that box for this exact collision. The count line already owns the left
     * of the strip, so this takes the right of it, and the count gives way when
     * the two cannot both fit.
     */
    private void renderNotice(GuiGraphicsExtractor poseStack)
    {
        if(notice.isEmpty())
        {
            return;
        }
        int right = closeLeft() - layout().i("close.gap", 4);
        String count = countCaption();
        // Floored against the count's own width rather than against gridLeft,
        // so the two are not written over each other -- and when even that
        // floor cannot hold them apart, renderControls has already dropped the
        // count for this frame and the notice takes the whole row.
        int floor = noticeCoversCount(count) ? gridLeft() : gridLeft() + font.width(count) + 8;
        poseStack.text(font, notice, Math.max(floor, right - 8 - font.width(notice)),
            captionTop(), 0xFFFF8A80, true);
    }

    /** Top right: the balance, as the reference shows it. */
    private void renderBalance(GuiGraphicsExtractor poseStack)
    {
        int w = balanceWidth();
        int x = balanceLeft();
        int y = controlsTop();
        int h = rowH();
        // One height with the buttons it shares a line with, rather than the
        // 18 that hung two units below them.
        NineSlice.draw(poseStack, HubTextures.PANEL, x, y, w, h);
        int textY = y + (h - 8) / 2;
        // Gold letters, white figure: the same split the section headers use,
        // so the label reads as a label and the balance as the number.
        poseStack.text(font, "DP", x + 6, textY, 0xFFF4D089, true);
        // Right-aligned in a plate sized for a fixed digit budget, and
        // shortened from the front past it the way detail() does.
        String value = shorten(Integer.toString(points), w - 12 - font.width("DP") - 6);
        poseStack.text(font, value, x + w - 6 - font.width(value), textY, 0xFFFFFFFF, true);
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
     * <p>
     * The Forge {@code bindSmooth} filter is gone (retained mode has no per-draw
     * filter); the art is drawn as the pipeline stored it.
     * <p>
     * <b>Through the image manager, because a blit is where the cost lands.</b>
     * Handing an Identifier MC has not loaded yet to {@code fullBlit} makes
     * {@code TextureManager.getTexture} miss, and a miss is read, decoded and
     * uploaded inline on the render thread — a watchdog sample caught this very
     * method 137 ms into a stall, standing in {@code stbi_load_from_memory}.
     * Set art never went through the manager, so a scroll revealed a whole grid
     * row of cold packs and paid for all of them in one frame, and none of them
     * was ever released again either.
     */
    private void drawPackArt(GuiGraphicsExtractor poseStack, ShopStock.Pack pack, int x, int y, int w, int h)
    {
        de.cas_ual_ty.dueldimension.set.CardSet set = ShopStock.setOf(pack.code());
        // The gate stops a fast flick REQUESTING art it will never show; it
        // must not stop it DRAWING art that is already resident, or a pack
        // alternates between its face and the card back as the gate flips
        // frame to frame -- which reads as flickering.
        Identifier art = set == null ? null : CardImageManager.peekTextureCard(
            set.getInfoImageResourceLocation(), ClientProxy.activeSetInfoImageSize, loadImages);
        // Still decoding, the pack wears the card back for a frame and is asked
        // again on the next one, so it resolves within a frame or two. Nothing
        // is remembered about that: a stored refusal would never be retried and
        // the pack would keep the placeholder for good.
        if(art == null || art == DuelTextures.UNKNOWN)
        {
            art = DuelTextures.COVER;
        }
        DdBlitUtil.fullBlit(poseStack, art, x, y, w, h);
    }

    /** The focus ring: four thin bars, so the art inside is untouched. */
    private void drawSelection(GuiGraphicsExtractor poseStack, int x, int y, int w, int h)
    {
        int tint = DdBlitUtil.tint(0.42F, 0.72F, 1F, 1F);
        int t = 2;
        DdBlitUtil.fullBlit(poseStack, DuelTextures.WHITE, x, y, w, t, tint);
        DdBlitUtil.fullBlit(poseStack, DuelTextures.WHITE, x, y + h - t, w, t, tint);
        DdBlitUtil.fullBlit(poseStack, DuelTextures.WHITE, x, y, t, h, tint);
        DdBlitUtil.fullBlit(poseStack, DuelTextures.WHITE, x + w - t, y, t, h, tint);
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
