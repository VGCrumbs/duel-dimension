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

    private int bottomHeight()
    {
        return layout().i("bottom.height", 46);
    }

    private int cellW()
    {
        return layout().i("grid.cellWidth", 32);
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

    private int gridLeft()
    {
        return leftWidth() + pad() * 2;
    }

    private int gridTop()
    {
        return pad() + layout().i("grid.top", 22);
    }

    /** As many columns as the space allows, so the grid fills a wide window. */
    private int gridColumns()
    {
        int available = width - gridLeft() - pad();
        int cell = cellW() + layout().i("grid.gap", 4);
        return Math.max(1, Math.min(layout().i("grid.maxColumns", 12),
            available / Math.max(1, cell)));
    }

    /** As many rows as fit between the header and the detail box. */
    private int gridRows()
    {
        int bottom = height - bottomHeight() - 12;
        int room = bottom - gridTop();
        int gap = layout().i("grid.gap", 4);
        return Math.max(1, (room + gap) / (cellH() + gap));
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

        int rows = 3;
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
        detail(poseStack, boxX, boxW, rowY, "Contents", "1 SLEEVE", 0xFFC2C9D6);
        rowY += rowH;
        detail(poseStack, boxX, boxW, rowY, "Price",
            owned ? "OWNED" : isCreative() ? "FREE" : entry.price() + " DP",
            owned ? 0xFF7CE38B : isCreative() ? 0xFF7CE38B : 0xFFF4D089);
        rowY += rowH;
        // How many of the shop's own list are already bought. A collector's
        // figure, the same question the card shop answers per pack.
        detail(poseStack, boxX, boxW, rowY, "Collected", ownedCount() + " of " + offers.size(),
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
        String shown = value;
        while(font.width(shown) > room && shown.length() > 1)
        {
            shown = shown.substring(1);
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
        poseStack.text(font, nameOf(entry.sleeve()), pad + 8, textY, 0xFFE6EAF2, true);

        boolean owned = owns(entry.sleeve());
        String state = owned ? "OWNED" : isCreative() ? "FREE" : entry.price() + " DP";
        poseStack.text(font, state, right - 8 - font.width(state), textY,
            owned ? 0xFF7CE38B : isCreative() ? 0xFF7CE38B : 0xFFF4D089, true);

        // What the product actually does, which is not obvious from a picture of
        // a card back: a sleeve is chosen PER DECK, in the editor, and it is the
        // back the deck's cards are printed on during a duel.
        String blurb = owned
            ? "Choose these on a deck in the deck editor."
            : "Dresses one of your decks. Chosen per deck in the deck editor.";
        int descriptionY = textY + 14;
        int descriptionX = pad + 6;
        int descriptionW = Math.max(40, right - 6 - descriptionX);
        NineSlice.draw(poseStack, HubTextures.PANEL_INSET, descriptionX, descriptionY - 3,
            descriptionW, bottomH - 22);
        for(net.minecraft.util.FormattedCharSequence line
            : font.split(Component.literal(blurb), descriptionW - 12))
        {
            poseStack.text(font, line, descriptionX + 6, descriptionY, 0xFFC2C9D6);
            descriptionY += 10;
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
        poseStack.text(font, "DP", x + 6, pad + 5, 0xFFF4D089, true);
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
        Identifier art = sleeve.getMainRL(ClientProxy.activeCardMainImageSize);
        DdBlitUtil.blit(poseStack, art, x, y, width, height,
            DuelTextures.CARD_U0, DuelTextures.CARD_V0,
            DuelTextures.CARD_U1, DuelTextures.CARD_V1, tint);
    }

    /** A frame of four thin bars, so whatever it rings is untouched. */
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
}
