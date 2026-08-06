package de.cas_ual_ty.dueldimension.clientutil.hub;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import de.cas_ual_ty.dueldimension.DuelDimension;
import de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil;
import de.cas_ual_ty.dueldimension.clientutil.DuelTextures;
import de.cas_ual_ty.dueldimension.clientutil.layout.Layout;
import de.cas_ual_ty.dueldimension.shop.ShopMessages;
import de.cas_ual_ty.dueldimension.shop.ShopStock;
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

    private final List<ShopStock.Pack> packs;
    private int selected;
    private int scroll;
    private String notice = "";

    public CardShopScreen(List<ShopStock.Pack> packs)
    {
        super(Component.literal("Card Shop"));
        this.packs = new ArrayList<>(packs);
    }

    /** Whether this player pays for packs at all. */
    private boolean isCreative()
    {
        return minecraft != null && minecraft.player != null && minecraft.player.isCreative();
    }

    private ShopStock.Pack current()
    {
        return packs.isEmpty() ? null : packs.get(Math.max(0, Math.min(selected, packs.size() - 1)));
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

    private int gridRows()
    {
        return Math.max(1, layout().i("grid.rows", 4));
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

        addRenderableWidget(new HubWidgets.TextureButton(pad, height - layout.i("bottom.height", 62) - 30,
            buyW, 20, Component.literal("Buy"), pressed -> buy()));
        addRenderableWidget(new HubWidgets.TextureButton(closeLeft(), height - 26,
            layout.i("close.width", 70), 20, Component.literal("Close"), pressed -> onClose()));
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
        if(!isCreative() && points < pack.price())
        {
            notice = "Not enough DP";
            return;
        }
        notice = "";
        // Only the pack is named. Price, contents and the balance check are the
        // server's, so this cannot ask for a discount.
        DuelDimension.channel.sendToServer(new ShopMessages.Buy(pack.code()));
    }

    // ---- input ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        int index = packAt(mouseX, mouseY);
        if(index >= 0)
        {
            selected = index;
            notice = "";
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta)
    {
        int maxScroll = Math.max(0, (packs.size() + gridColumns() - 1) / gridColumns() - gridRows());
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int)Math.signum(delta)));
        return true;
    }

    private int packAt(double mouseX, double mouseY)
    {
        int columns = gridColumns();
        int cellW = cellW();
        int cellH = cellH();
        int gap = layout().i("grid.gap", 4);
        int column = (int)((mouseX - gridLeft()) / (cellW + gap));
        int row = (int)((mouseY - gridTop()) / (cellH + gap));
        if(column < 0 || column >= columns || row < 0 || row >= gridRows())
        {
            return -1;
        }
        int index = (row + scroll) * columns + column;
        return index < packs.size() ? index : -1;
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

        renderPreview(poseStack, pad, leftW, bottomH);
        renderGrid(poseStack, mouseX, mouseY);
        renderDetails(poseStack, bottomH, mouseX, mouseY);
        renderBalance(poseStack);

        super.render(poseStack, mouseX, mouseY, partialTick);
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

        int y = pad + 8 + artH + 6;
        String quantity = pack.deck() ? "1 DECK" : "1 PACK";
        font.drawShadow(poseStack, quantity, pad + 8, y, 0xFFC2C9D6);
        String price = isCreative() ? "FREE (creative)" : pack.price() + " DP";
        font.drawShadow(poseStack, price, pad + 8, y + 12,
            isCreative() ? 0xFF7CE38B : 0xFFF4D089);

        // The grid runs newest first, so the date is what tells a player where
        // in the run of sets they are looking. Dimmer than the price because it
        // is context rather than a thing to act on, and omitted rather than
        // written as "unknown" when a set carries no date -- an absent line
        // says the same thing without occupying one.
        String released = releaseDate(pack);
        if(released != null)
        {
            font.drawShadow(poseStack, released, pad + 8, y + 24, 0xFF7A8090);
        }
    }

    /**
     * A set's release date, written the way the player's own locale writes one,
     * or null when the set data carries no date.
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
            return date.format(java.time.format.DateTimeFormatter
                .ofLocalizedDate(java.time.format.FormatStyle.MEDIUM));
        }
        catch(RuntimeException unreadable)
        {
            return null;
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
                if(index >= packs.size())
                {
                    continue;
                }
                int x = left + column * (cellW + gap);
                int y = top + row * (cellH + gap);
                drawPackArt(poseStack, packs.get(index), x, y, cellW, cellH);
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
        font.drawShadow(poseStack, "DP", x + 6, pad + 5, 0xFF9FA6B4);
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
