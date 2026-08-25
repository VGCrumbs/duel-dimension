package de.cas_ual_ty.dueldimension.clientutil.hub;

import de.cas_ual_ty.dueldimension.duel.dueldisk.DuelDiskItem;
import de.cas_ual_ty.dueldimension.duel.profile.DuelDisks;
import de.cas_ual_ty.dueldimension.shop.DiskShopMessages;
import de.cas_ual_ty.dueldimension.shop.ShopStock;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The duel disk shop: every disk laid out as its own rendered model.
 * <p>
 * Shown as items rather than as flat art because a duel disk already HAS a
 * model -- the same one the player wears -- so drawing anything else would be
 * showing them a picture of a thing instead of the thing. The grid is a
 * wardrobe: what is owned, what is worn, and what is for sale, in one place.
 * <p>
 * Nothing here decides anything. The stock, the prices, what is owned and what
 * is active all arrive from the server, and every button sends a request the
 * server is free to refuse.
 */
public class DiskShopScreen extends Screen
{
    private DiskShopMessages.OpenDiskShop shop;
    /** Named rather than indexed, so a refreshed stock cannot shift the selection. */
    private String selected = "";
    private String notice = "";

    private static final int PAD = 8;
    /** The tile a disk is drawn in. Big enough that the model reads as a model. */
    private static final int CELL_MIN = 44;
    private static final int CELL_MAX = 96;
    private static final int GAP = 6;
    /** Bands kept clear at the top for the title and balance, and below for the buttons. */
    private static final int HEADER = 28;
    private static final int FOOTER = 44;

    public DiskShopScreen(DiskShopMessages.OpenDiskShop shop)
    {
        super(Component.literal("Duel Disks"));
        this.shop = shop;
        this.selected = shop.active();
    }

    /** Replaces the stock in place, keeping the screen and the selection. */
    public void update(DiskShopMessages.OpenDiskShop next)
    {
        shop = next;
        if(!DuelDisks.isKnown(selected))
        {
            selected = next.active();
        }
        rebuildWidgets();
    }

    private Set<String> owned()
    {
        return new LinkedHashSet<>(shop.owned());
    }

    private List<ShopStock.DiskOffer> offers()
    {
        return shop.disks();
    }

    private ShopStock.DiskOffer offerFor(String name)
    {
        for(ShopStock.DiskOffer offer : offers())
        {
            if(offer.disk().equals(name))
            {
                return offer;
            }
        }
        return null;
    }

    /**
     * The grid decides the panel, not the other way round.
     * <p>
     * A fixed panel left ten disks in the corner of an empty box on a large
     * window. Everything below is measured from the tiles: how many fit across,
     * how many rows that makes, and the panel is exactly that plus its margin --
     * then centred. Nothing here is an authored size except the tile bounds.
     */
    private int columns()
    {
        int count = Math.max(1, offers().size());
        // Room for the widest sensible grid, then squared off: ten disks read
        // better as 5x2 than as 10x1 or 4x3.
        int fits = Math.max(1, (width - PAD * 4 + GAP) / (CELL_MIN + GAP));
        int wanted = (int)Math.ceil(Math.sqrt(count * 2D));
        return Math.max(1, Math.min(Math.min(count, fits), Math.max(wanted, 1)));
    }

    private int rows()
    {
        return Math.max(1, (offers().size() + columns() - 1) / columns());
    }

    /** As large as the tiles can be and still fit, within the authored bounds. */
    private int cell()
    {
        int cols = columns();
        int rows = rows();
        int roomW = width - PAD * 4 - GAP * (cols - 1);
        int roomH = height - HEADER - FOOTER - PAD * 2 - GAP * (rows - 1);
        int fit = Math.min(roomW / cols, roomH / rows);
        return Math.max(CELL_MIN, Math.min(CELL_MAX, fit));
    }

    private int gridW()
    {
        return columns() * cell() + GAP * (columns() - 1);
    }

    private int gridH()
    {
        return rows() * cell() + GAP * (rows() - 1);
    }

    private int panelW()
    {
        return Math.min(width - PAD * 2, gridW() + PAD * 2);
    }

    private int panelH()
    {
        return Math.min(height - HEADER - FOOTER, gridH() + PAD * 2);
    }

    private int panelX()
    {
        return (width - panelW()) / 2;
    }

    /** Centred in what is left once the title and the buttons have their bands. */
    private int panelTop()
    {
        return HEADER + Math.max(0, (height - HEADER - FOOTER - panelH()) / 2);
    }

    /** The grid inside the panel, centred both ways. */
    private int gridLeft()
    {
        return panelX() + (panelW() - gridW()) / 2;
    }

    private int gridTop()
    {
        return panelTop() + (panelH() - gridH()) / 2;
    }

    @Override
    protected void init()
    {
        int buttonsY = height - 28;
        int buttonW = Math.min(120, (panelW() - PAD) / 3);
        int x = panelX();

        ShopStock.DiskOffer offer = offerFor(selected);
        boolean isOwned = owned().contains(selected);
        boolean free = minecraft != null && minecraft.player != null
            && minecraft.player.isCreative();

        // Three states, one button: BUY what you do not own, EQUIP what you do,
        // and nothing to do for the one already on. Anything else -- a Buy
        // button on an owned disk, or a dead button on open -- reads as a shop
        // that does not work.
        boolean isActive = selected.equals(shop.active());
        String label = !isOwned
            ? (free ? "Buy (free)" : "Buy  " + (offer == null ? "?" : offer.price()))
            : isActive ? "Equipped" : "Equip";
        HubWidgets.TextureButton action = new HubWidgets.TextureButton(x, buttonsY, buttonW, 20,
            Component.literal(label), pressed -> act(isOwned));
        action.active = offer != null && !(isOwned && isActive);
        addRenderableWidget(action);

        addRenderableWidget(new HubWidgets.TextureButton(x + buttonW + PAD, buttonsY, buttonW, 20,
            Component.literal("Back"), pressed -> minecraft.setScreenAndShow(new DuelHubScreen())));
        addRenderableWidget(new HubWidgets.TextureButton(
            panelX() + panelW() - buttonW, buttonsY, buttonW, 20,
            Component.literal("Close"), pressed -> onClose()));
    }

    private void act(boolean isOwned)
    {
        notice = "";
        if(selected.isEmpty())
        {
            return;
        }
        // The server answers both of these with a fresh shop, which is what
        // refreshes this screen -- nothing is assumed to have worked.
        ClientPlayNetworking.send(isOwned
            ? new DiskShopMessages.SetActiveDisk(selected)
            : new DiskShopMessages.BuyDisk(selected));
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event,
        boolean doubled)
    {
        int hit = diskAt(event.x(), event.y());
        if(hit >= 0)
        {
            selected = offers().get(hit).disk();
            notice = "";
            rebuildWidgets();
            return true;
        }
        return super.mouseClicked(event, doubled);
    }

    /** Which tile the pointer is over, or -1. */
    private int diskAt(double mouseX, double mouseY)
    {
        int cols = columns();
        int cell = cell();
        int left = gridLeft();
        int top = gridTop();
        for(int i = 0; i < offers().size(); i++)
        {
            int x = left + (i % cols) * (cell + GAP);
            int y = top + (i / cols) * (cell + GAP);
            if(mouseX >= x && mouseX < x + cell && mouseY >= y && mouseY < y + cell)
            {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY,
        float partialTick)
    {
        // fillGradient, never extractBackground: it blurs in 26.2 and has taken
        // the client down when one screen opened over another.
        extractor.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);

        String title = "Duel Disks";
        extractor.text(font, title, (width - font.width(title)) / 2, PAD, 0xFFF4D089, true);

        String balance = "DP  " + shop.points();
        extractor.text(font, balance, panelX() + panelW() - font.width(balance), PAD, 0xFFF4D089,
            true);

        NineSlice.draw(extractor, HubTextures.PANEL, panelX(), panelTop(), panelW(), panelH());

        int cols = columns();
        int cell = cell();
        int left = gridLeft();
        int top = gridTop();
        Set<String> owned = owned();

        for(int i = 0; i < offers().size(); i++)
        {
            ShopStock.DiskOffer offer = offers().get(i);
            int x = left + (i % cols) * (cell + GAP);
            int y = top + (i / cols) * (cell + GAP);
            drawTile(extractor, offer, x, y, cell, owned.contains(offer.disk()));
        }

        // The widgets, AFTER the panel and the tiles: retained mode draws in
        // the order it is described, so a super call first would paint the
        // buttons and then cover them with the panel. Missing entirely, which
        // is what it was, the buttons exist and answer clicks but are never
        // painted -- a shop with an invisible Buy button.
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);

        drawFooter(extractor);
    }

    private void drawTile(GuiGraphicsExtractor extractor, ShopStock.DiskOffer offer,
        int x, int y, int cell, boolean isOwned)
    {
        NineSlice.draw(extractor, HubTextures.PANEL_INSET, x, y, cell, cell);

        DuelDiskItem disk = DuelDisks.item(offer.disk());
        if(disk != null)
        {
            // The item's own model, scaled up from the 16x16 an item is drawn
            // at. Scaling the POSE rather than asking for a bigger item is the
            // only lever there is -- and it is why this is a 3D preview rather
            // than a sprite: the model renders with its real depth and lighting.
            float scale = (cell - 12) / 16F;
            extractor.pose().pushMatrix();
            extractor.pose().translate(x + cell / 2F, y + cell / 2F);
            extractor.pose().scale(scale, scale);
            extractor.item(new ItemStack(disk), -8, -8);
            extractor.pose().popMatrix();
        }

        boolean active = offer.disk().equals(shop.active());
        if(active)
        {
            drawRing(extractor, x, y, cell, 0xFF7CE38B);
        }
        else if(offer.disk().equals(selected))
        {
            drawRing(extractor, x, y, cell, 0xFF6BB8FF);
        }

        // The price sits on unowned stock only: a row of zeroes under things
        // the player already has is noise.
        if(!isOwned)
        {
            String price = Integer.toString(offer.price());
            extractor.text(font, price, x + cell - font.width(price) - 3,
                y + cell - font.lineHeight - 2, 0xFFF4D089, true);
        }
    }

    /** Four thin bars, so the model inside is untouched. */
    private void drawRing(GuiGraphicsExtractor extractor, int x, int y, int size, int colour)
    {
        int t = 2;
        extractor.fill(x, y, x + size, y + t, colour);
        extractor.fill(x, y + size - t, x + size, y + size, colour);
        extractor.fill(x, y, x + t, y + size, colour);
        extractor.fill(x + size - t, y, x + size, y + size, colour);
    }

    private void drawFooter(GuiGraphicsExtractor extractor)
    {
        int y = Math.min(height - FOOTER + 4, panelTop() + panelH() + 4);
        DuelDiskItem disk = DuelDisks.item(selected);
        String name = disk == null ? "" : new ItemStack(disk).getHoverName().getString();
        extractor.text(font, name, panelX(), y, 0xFFFFFFFF, true);

        String state = selected.equals(shop.active()) ? "Worn"
            : owned().contains(selected) ? "Owned"
                : "";
        if(!state.isEmpty())
        {
            extractor.text(font, state, panelX() + font.width(name) + 6, y, 0xFF7CE38B, true);
        }
        if(!notice.isEmpty())
        {
            extractor.text(font, notice, panelX(), y + font.lineHeight + 2, 0xFFC1362F, true);
        }
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
