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
 * its price down the left, a grid of every pack in the middle, the shopkeeper
 * on the right, and a details bar with the description underneath.
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

    private ShopStock.Pack current()
    {
        return packs.isEmpty() ? null : packs.get(Math.max(0, Math.min(selected, packs.size() - 1)));
    }

    // ---- geometry ----

    private Layout layout()
    {
        return Layout.of(LAYOUT);
    }

    private int gridColumns()
    {
        return Math.max(1, layout().i("grid.columns", 7));
    }

    private int cellW()
    {
        return layout().i("grid.cellWidth", 40);
    }

    private int cellH()
    {
        return Math.round(cellW() / layout().f("pack.aspect", 0.68F));
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
            buyW, 20, Component.literal("Buy 1 Pack"), pressed -> buy()));
        addRenderableWidget(new HubWidgets.TextureButton(width - pad - 70, height - 26, 70, 20,
            Component.literal("Close"), pressed -> onClose()));
    }

    private void buy()
    {
        ShopStock.Pack pack = current();
        if(pack == null)
        {
            return;
        }
        if(points < pack.price())
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
        renderVendor(poseStack);
        renderDetails(poseStack, bottomH);
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
        int artH = Math.round(artW / layout().f("pack.aspect", 0.68F));
        drawPackArt(poseStack, pack, pad + 8, pad + 8, artW, artH);

        int y = pad + 8 + artH + 6;
        String quantity = "1 PACK";
        font.drawShadow(poseStack, quantity, pad + 8, y, 0xFFC2C9D6);
        String price = pack.price() + " DP";
        font.drawShadow(poseStack, price, pad + 8, y + 12, 0xFFF4D089);
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

    /** Right: the shopkeeper. */
    private void renderVendor(PoseStack poseStack)
    {
        Layout layout = layout();
        int w = layout.i("vendor.width", 96);
        int h = layout.i("vendor.height", 128);
        int x = width - layout.i("pad", 8) - w;
        int y = layout.i("pad", 8) + layout.i("vendor.top", 22);
        NineSlice.draw(poseStack, HubTextures.PANEL, x - 4, y - 4, w + 8, h + 8);
        NineSlice.image(poseStack, HubTextures.SHOPKEEPER, x, y, w, h);
    }

    /** Bottom: name, cards per pack, price, completion, and the blurb. */
    private void renderDetails(PoseStack poseStack, int bottomH)
    {
        int pad = layout().i("pad", 8);
        int y = height - bottomH - 4;
        NineSlice.draw(poseStack, HubTextures.PANEL, pad, y, width - pad * 2, bottomH);

        ShopStock.Pack pack = current();
        if(pack == null)
        {
            return;
        }
        int textY = y + 7;
        font.drawShadow(poseStack, pack.name(), pad + 8, textY, 0xFFE6EAF2);

        // The metrics run along the right of the title row, as the reference's
        // bar does: how many cards, what it costs, how much of it you have.
        String cards = "x " + pack.cardsPerPack();
        String price = pack.price() + " DP";
        String complete = completion(pack) + "%";
        int metricsX = width - pad - 8;
        metricsX -= font.width(complete);
        font.drawShadow(poseStack, complete, metricsX, textY, 0xFF9FD4FF);
        metricsX -= font.width(price) + 12;
        font.drawShadow(poseStack, price, metricsX, textY, 0xFFF4D089);
        metricsX -= font.width(cards) + 12;
        font.drawShadow(poseStack, cards, metricsX, textY, 0xFFC2C9D6);
        // A card glyph before the count, standing in for the reference's icon.
        NineSlice.image(poseStack, DuelTextures.COVER, metricsX - 9, textY - 1, 5, 8);

        int descriptionY = textY + 14;
        NineSlice.draw(poseStack, HubTextures.PANEL_INSET, pad + 6, descriptionY - 3,
            width - pad * 2 - 12, bottomH - 22);
        for(net.minecraft.util.FormattedCharSequence line
            : font.split(Component.literal(pack.description()), width - pad * 2 - 24))
        {
            font.draw(poseStack, line, pad + 12, descriptionY, 0xFFC2C9D6);
            descriptionY += 10;
        }

        if(!notice.isEmpty())
        {
            font.drawShadow(poseStack, notice, pad + 12, height - 16, 0xFFFF8A80);
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

    /** How much of this pack's card list the player owns. */
    private int completion(ShopStock.Pack pack)
    {
        if(pack.distinctCards() <= 0)
        {
            return 0;
        }
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
        return Math.round(owned * 100F / pack.distinctCards());
    }

    /** Pack art, falling back to a card back while the image is unavailable. */
    private void drawPackArt(PoseStack poseStack, ShopStock.Pack pack, int x, int y, int w, int h)
    {
        RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        RenderSystem.enableBlend();
        ResourceLocation art = new ResourceLocation(DuelDimension.MOD_ID,
            "textures/gui/shop/packs/" + pack.code().toLowerCase(java.util.Locale.ROOT) + ".png");
        // Set art is not shipped for every one of several hundred sets, so a
        // missing file falls back to the card back rather than a magenta square.
        if(net.minecraft.client.Minecraft.getInstance().getResourceManager().getResource(art).isEmpty())
        {
            art = DuelTextures.COVER;
        }
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
