package de.cas_ual_ty.dueldimension.clientutil.hub;

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

/** A small visual storefront for premium Master Duel deck cases. */
public final class DeckBoxShopScreen extends Screen
{
    private record Entry(DeckBoxStyle style, int price)
    {
    }

    /**
     * Where the art and the two lines sit inside one tile, relative to it.
     * <p>
     * Its own type, and worked out by a static function of the tile's size, so
     * the one property that matters can be TESTED rather than looked at: the
     * art must end before the name begins, at every size a window can produce.
     * It used to be four independent expressions -- a floor of 58 on the art,
     * offsets of 34 and 17 up from the bottom -- which agreed only near the
     * authored height and overlapped everywhere else.
     */
    record TileLayout(int artX, int artY, int artW, int artH, int nameY, int priceY)
    {
        /** The bottom of the art, which is the line the name must clear. */
        int artBottom()
        {
            return artY + artH;
        }
    }

    /**
     * @param lineHeight the font's, passed in so this stays free of the client
     */
    static TileLayout tileLayout(int tileW, int tileH, int lineHeight)
    {
        int textBlock = lineHeight * 2 + LINE_GAP;
        // The text is anchored to the bottom and the art takes what is left
        // above it. That ordering is the fix: whatever a tile's height, the
        // lines are placed first and the art can only have the remainder.
        int nameY = tileH - TILE_PAD - textBlock;
        int space = Math.max(0, nameY - TILE_PAD - TILE_PAD);
        int artH = space;
        int artW = Math.round(artH * BOX_ASPECT);
        // Clamped by the WIDTH too, keeping the aspect: a narrow column would
        // otherwise push a case out through the sides of a tile it had been
        // sized into by height alone.
        int maxW = Math.max(0, tileW - TILE_PAD * 2);
        if(artW > maxW)
        {
            artW = maxW;
            artH = Math.round(artW / BOX_ASPECT);
        }
        return new TileLayout((tileW - artW) / 2, TILE_PAD + Math.max(0, (space - artH) / 2),
            artW, artH, nameY, nameY + lineHeight + LINE_GAP);
    }

    private static final int PREFERRED_TILE_W = 118;
    private static final int TILE_H = 112;
    private static final int MIN_TILE_PIXELS = 276;
    private static final int MAX_COLUMNS = 3;
    private static final int MAX_VISIBLE_ROWS = 2;
    private static final int HEADER_H = 32;
    private static final int FOOTER_H = 38;
    private static final int GAP = 8;
    private static final int BAR_GRAB = 4;
    /** Breathing room inside a tile, used on every side of everything in it. */
    private static final int TILE_PAD = 3;
    /** Between the name and the price. */
    private static final int LINE_GAP = 2;
    /** The case art's width over its height, from the source PNGs. */
    private static final float BOX_ASPECT = 0.824F;
    /**
     * The smallest a case may be drawn and still be told apart from another.
     * <p>
     * A floor on the ART rather than on the tile, because it is what the tile
     * exists to show; the tile's own minimum is derived from it.
     */
    private static final int MIN_ART_H = 44;

    private final List<Entry> offers = new ArrayList<>();
    private int selected;
    private int left;
    private int top;
    private int panelW;
    private int panelH;
    private int columns;
    private int tileW;
    private int tileH;
    private int scrollRow;
    private int scrollGrab = -1;
    private HubWidgets.TextureButton buyButton;

    public static void open(int points, List<ShopStock.DeckBoxOffer> offers)
    {
        CardShopScreen.setPoints(points);
        Minecraft.getInstance().gui.setScreen(new DeckBoxShopScreen(offers));
    }

    private DeckBoxShopScreen(List<ShopStock.DeckBoxOffer> offers)
    {
        super(Component.literal("Deck Box Shop"));
        for(ShopStock.DeckBoxOffer offer : offers)
        {
            DeckBoxStyle style = DeckBoxStyle.known(offer.deckBox());
            if(style != null && style.isPurchasable())
            {
                this.offers.add(new Entry(style, offer.price()));
            }
        }
    }

    @Override
    protected void init()
    {
        // Size the window around its contents. Expanding to a large fullscreen
        // cap made six fixed-size cases occupy one corner of a mostly empty
        // panel, and four columns left the last catalogue row with two lonely
        // cells. Three columns gives this shop two balanced rows and remains a
        // useful density as more cases are added.
        int availablePanelW = Math.max(1, width - 20);
        int availableGridW = Math.max(1, availablePanelW - 34);
        columns = Math.clamp((availableGridW + GAP) / (100 + GAP), 1, MAX_COLUMNS);
        tileW = Math.min(PREFERRED_TILE_W,
            Math.max(76, (availableGridW - (columns - 1) * GAP) / columns));
        panelW = Math.min(availablePanelW,
            Math.max(260, columns * tileW + (columns - 1) * GAP + 34));

        int rows = (offers.size() + columns - 1) / columns;
        int shownRows = Math.max(1, Math.min(MAX_VISIBLE_ROWS, rows));
        int wantedGridH = shownRows * TILE_H + (shownRows - 1) * GAP;
        panelH = Math.min(Math.max(1, height - 20),
            Math.max(180, HEADER_H + FOOTER_H + wantedGridH));
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;

        // GUI scale rounding can leave the viewport only a handful of pixels
        // short of the requested rows. Treating the cases as rigidly 112 high
        // then discarded an entire row and left half the panel empty. Compress
        // both rows slightly when they remain comfortably legible; genuinely
        // short windows still fall back to one scrollable row.
        int guiScale = Math.max(1, minecraft.getWindow().getGuiScale());
        // Legibility is a physical-pixel constraint. At automatic GUI scale 3
        // this remains the old 92-unit minimum; at scale 5 the same physical
        // size needs only 56 layout units, preventing high-resolution displays
        // from paradoxically fitting fewer rows than low-resolution ones.
        //
        // Floored at what a tile's own CONTENTS need rather than at a flat 56.
        // A tile is art over two lines of text, and 56 was below the sum of
        // those -- so at scale 5, where the physical constraint relaxes to 56,
        // two rows were squeezed into a height neither could hold. What that
        // produced was this screen's actual bug: the art kept a fixed floor
        // while the tile shrank under it, so a case overlapped its own name at
        // every window that landed in the band. Derived, so the two cannot
        // disagree again.
        int minimumTileH = Math.max(minimumTileH(),
            (MIN_TILE_PIXELS + guiScale - 1) / guiScale);
        int rowsThatFit = Math.min(shownRows,
            Math.max(1, (gridHeight() + GAP) / (minimumTileH + GAP)));
        tileH = Math.min(TILE_H,
            Math.max(1, (gridHeight() - (rowsThatFit - 1) * GAP) / rowsThatFit));
        clampScroll();
        scrollGrab = -1;

        int footerY = top + panelH - 28;
        buyButton = addRenderableWidget(new HubWidgets.TextureButton(left + panelW - 172, footerY,
            76, 20, Component.literal("Buy"), pressed -> buySelected()));
        buyButton.setActiveSupplier(this::canBuySelected);
        addRenderableWidget(new HubWidgets.TextureButton(left + panelW - 88, footerY, 76, 20,
            Component.literal("Close"), pressed -> onClose()));
    }

    /** The name and the price, which every tile carries whatever its size. */
    private int textBlockH()
    {
        return font.lineHeight * 2 + LINE_GAP;
    }

    /**
     * The shortest tile that can hold what a tile holds.
     * <p>
     * Top pad, art, a pad, both lines of text, bottom pad. Asked of the font
     * rather than written down, so a resource pack with a taller font gets a
     * taller tile instead of a collision.
     */
    private int minimumTileH()
    {
        return MIN_ART_H + textBlockH() + TILE_PAD * 3;
    }

    private int gridLeft()
    {
        return left + 12;
    }

    private int gridRight()
    {
        return left + panelW - 22;
    }

    private int gridTop()
    {
        return top + HEADER_H;
    }

    private int gridHeight()
    {
        return Math.max(1, panelH - HEADER_H - FOOTER_H);
    }

    private int visibleRows()
    {
        return Math.max(1, (gridHeight() + GAP) / (tileH + GAP));
    }

    private int totalRows()
    {
        return (offers.size() + columns - 1) / columns;
    }

    private int maxScroll()
    {
        return Math.max(0, totalRows() - visibleRows());
    }

    private void clampScroll()
    {
        scrollRow = Math.clamp(scrollRow, 0, maxScroll());
    }

    private int rowWidth()
    {
        return columns * tileW + (columns - 1) * GAP;
    }

    private int startX()
    {
        return gridLeft() + Math.max(0, (gridRight() - gridLeft() - rowWidth()) / 2);
    }

    private int trackX()
    {
        return left + panelW - 12;
    }

    private int trackH()
    {
        return Math.min(gridHeight(), visibleRows() * (tileH + GAP) - GAP);
    }

    private int thumbH()
    {
        return Math.min(trackH(), Math.max(12,
            trackH() * visibleRows() / Math.max(1, totalRows())));
    }

    private int thumbY()
    {
        int max = maxScroll();
        return max <= 0 ? gridTop()
            : gridTop() + (trackH() - thumbH()) * scrollRow / max;
    }

    private void drawScrollbar(GuiGraphicsExtractor graphics)
    {
        if(maxScroll() <= 0)
        {
            return;
        }
        NineSlice.draw(graphics, HubTextures.SCROLLBAR, trackX(), gridTop(), 4, trackH(), 0, 2);
        NineSlice.draw(graphics, HubTextures.SCROLLBAR, trackX(), thumbY(), 4, thumbH(), 1, 2);
    }

    private boolean grabScrollbar(double mouseX, double mouseY)
    {
        if(maxScroll() <= 0 || mouseX < trackX() - BAR_GRAB
            || mouseX >= trackX() + 4 + BAR_GRAB
            || mouseY < gridTop() || mouseY >= gridTop() + trackH())
        {
            return false;
        }
        int thumbY = thumbY();
        scrollGrab = mouseY >= thumbY && mouseY < thumbY + thumbH()
            ? (int)(mouseY - thumbY) : thumbH() / 2;
        dragScrollbar(mouseY);
        return true;
    }

    private void dragScrollbar(double mouseY)
    {
        int travel = trackH() - thumbH();
        if(travel <= 0 || maxScroll() <= 0)
        {
            scrollRow = 0;
            return;
        }
        double top = mouseY - scrollGrab - gridTop();
        scrollRow = (int)Math.clamp(Math.round(top / travel * maxScroll()), 0, maxScroll());
    }

    private String fit(String text, int maxWidth)
    {
        if(font.width(text) <= maxWidth)
        {
            return text;
        }
        String suffix = "...";
        int length = text.length();
        while(length > 0 && font.width(text.substring(0, length) + suffix) > maxWidth)
        {
            length--;
        }
        return text.substring(0, length) + suffix;
    }

    /**
     * The balance, as a plate rather than as two words in the corner.
     * <p>
     * The card shop's, deliberately: same {@code PANEL} nine-slice, same gold
     * label against a white figure, same six-digit budget, same right
     * alignment. These are two doors into one wallet, and a balance that
     * changed shape between them read as two different numbers.
     * <p>
     * Right-aligned in a plate sized for the digits rather than for the value,
     * so the figure does not shuffle sideways as it is spent.
     */
    private void drawBalance(GuiGraphicsExtractor graphics)
    {
        String value = Integer.toString(CardShopScreen.points());
        int w = font.width("000000") + 34;
        int h = 18;
        int x = left + panelW - 12 - w;
        int y = top + 6;
        NineSlice.draw(graphics, HubTextures.PANEL, x, y, w, h);
        int textY = y + (h - font.lineHeight) / 2 + 1;
        graphics.text(font, "DP", x + 6, textY, 0xFFF4D089, true);
        // Trimmed from the FRONT, not the back, and aligned on what is actually
        // drawn. A balance is recognisable from its tail -- "...053" is a number
        // being clipped, where "60..." reads as a smaller number.
        String shown = shortenFront(value, w - 12 - font.width("DP") - 6);
        graphics.text(font, shown, x + w - 6 - font.width(shown), textY, 0xFFFFFFFF, true);
    }

    /** Drops leading digits until the figure fits, as the card shop does. */
    private String shortenFront(String value, int room)
    {
        if(font.width(value) <= room)
        {
            return value;
        }
        String shown = value;
        while(shown.length() > 1 && font.width("..." + shown) > room)
        {
            shown = shown.substring(1);
        }
        return "..." + shown;
    }

    private Entry current()
    {
        return offers.isEmpty() ? null : offers.get(Math.min(selected, offers.size() - 1));
    }

    private boolean owns(DeckBoxStyle style)
    {
        return EditorState.profile().ownsDeckBox(style);
    }

    private boolean canBuySelected()
    {
        Entry entry = current();
        boolean creative = minecraft != null && minecraft.player != null
            && minecraft.player.isCreative();
        return entry != null && !owns(entry.style())
            && (creative || CardShopScreen.points() >= entry.price());
    }

    private void buySelected()
    {
        Entry entry = current();
        if(entry != null && canBuySelected())
        {
            ClientPlayNetworking.send(new ShopMessages.BuyDeckBox(entry.style().name()));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
        float partialTick)
    {
        graphics.fillGradient(0, 0, width, height, 0xD0101014, 0xE0101014);
        NineSlice.draw(graphics, HubTextures.PANEL, left, top, panelW, panelH);
        graphics.text(font, "Deck Box Shop", left + 12, top + 10, 0xFFF4D089, true);
        drawBalance(graphics);

        int first = scrollRow * columns;
        int last = Math.min(offers.size(), first + visibleRows() * columns);
        for(int i = first; i < last; i++)
        {
            Entry entry = offers.get(i);
            int shown = i - first;
            int x = startX() + (shown % columns) * (tileW + GAP);
            int y = gridTop() + (shown / columns) * (tileH + GAP);
            boolean over = mouseX >= x && mouseX < x + tileW
                && mouseY >= y && mouseY < y + tileH;
            int state = i == selected ? NineSlice.SELECTED
                : over ? NineSlice.HOVER : NineSlice.IDLE;
            NineSlice.draw(graphics, HubTextures.CHIP, x, y, tileW, tileH, state, 3);

            TileLayout tile = tileLayout(tileW, tileH, font.lineHeight);
            DeckBoxPickerScreen.drawBox(graphics, entry.style(), x + tile.artX(),
                y + tile.artY(), tile.artW(), tile.artH());

            String name = fit(entry.style().label(), tileW - TILE_PAD * 2);
            graphics.text(font, name, x + (tileW - font.width(name)) / 2, y + tile.nameY(),
                i == selected ? 0xFFF4D089 : 0xFFE8E8E8, true);
            String price = owns(entry.style()) ? "Owned" : entry.price() + " DP";
            graphics.text(font, price, x + (tileW - font.width(price)) / 2, y + tile.priceY(),
                owns(entry.style()) ? 0xFF8BE0A4 : 0xFFCAD3E6, true);
        }
        drawScrollbar(graphics);

        Entry entry = current();
        if(buyButton != null)
        {
            buyButton.setMessage(Component.literal(entry != null && owns(entry.style())
                ? "Owned" : "Buy"));
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick)
    {
        if(super.mouseClicked(event, doubleClick))
        {
            return true;
        }
        if(event.button() == 0)
        {
            if(grabScrollbar(event.x(), event.y()))
            {
                return true;
            }
            int first = scrollRow * columns;
            int last = Math.min(offers.size(), first + visibleRows() * columns);
            for(int i = first; i < last; i++)
            {
                int shown = i - first;
                int x = startX() + (shown % columns) * (tileW + GAP);
                int y = gridTop() + (shown / columns) * (tileH + GAP);
                if(event.x() >= x && event.x() < x + tileW
                    && event.y() >= y && event.y() < y + tileH)
                {
                    selected = i;
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY)
    {
        if(scrollGrab >= 0)
        {
            dragScrollbar(event.y());
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
        if(mouseX < gridLeft() || mouseX >= left + panelW - 6
            || mouseY < gridTop() || mouseY >= gridTop() + gridHeight())
        {
            return super.mouseScrolled(mouseX, mouseY, scrollX, delta);
        }
        scrollRow -= (int)Math.signum(delta);
        clampScroll();
        return true;
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
