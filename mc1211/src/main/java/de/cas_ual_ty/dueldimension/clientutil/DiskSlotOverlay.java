package de.cas_ual_ty.dueldimension.clientutil;

import de.cas_ual_ty.dueldimension.clientutil.hub.EditorState;
import de.cas_ual_ty.dueldimension.duel.dueldisk.DiskMessages;
import de.cas_ual_ty.dueldimension.duel.profile.DuelDisks;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The duel disk slot, drawn in the inventory beside the shield.
 * <p>
 * The disk is worn equipment rather than a possession -- there is no item to
 * put anywhere -- so this is not a real {@link Slot} backed by a container. It
 * is the slot the player expects to see for a thing they wear, drawn in the
 * place they expect to see it, and clicking it does what pressing F does.
 * <p>
 * <b>Positioned from the shield slot rather than from a constant.</b> The
 * off-hand slot's coordinates are read off the open menu at draw time, so this
 * sits immediately left of wherever the game actually put it -- which survives
 * a resize, a different GUI scale, and any mod that moves the inventory around.
 */
public final class DiskSlotOverlay
{
    /** The off-hand's index in {@code InventoryMenu}, which is where the shield lives. */
    private static final int OFFHAND_SLOT = 45;
    /** A slot is 18 across including its frame: one full slot of offset. */
    public static final int STEP = 18;

    private DiskSlotOverlay()
    {
    }

    /**
     * The disk this player is currently WEARING, or empty.
     * <p>
     * Answers for ANY player, from the broadcast every client receives, so a
     * duelist across the field is drawn wearing what they actually have on.
     */
    public static ItemStack wornDiskFor(net.minecraft.client.player.AbstractClientPlayer player)
    {
        return ClientWornDisks.worn(player.getUUID());
    }

    private static ItemStack activeDisk()
    {
        String name = EditorState.profile().activeDisk();
        Item item = DuelDisks.item(name);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    /** Draws the slot at the given screen position. */
    public static void draw(net.minecraft.client.gui.GuiGraphicsExtractor graphics,
        int x, int y, int mouseX, int mouseY)
    {

        // The empty-slot frame vanilla itself uses, so this reads as part of the
        // inventory rather than as something painted on top of it.
        graphics.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
            net.minecraft.resources.ResourceLocation.withDefaultNamespace("container/slot"),
            x - 1, y - 1, 18, 18);

        ItemStack disk = activeDisk();
        if(!disk.isEmpty())
        {
            graphics.item(disk, x, y);
            // Worn is drawn as full colour; not worn is dimmed, so the slot
            // says which state it is in without a second icon to learn.
            if(!EditorState.profile().diskWorn())
            {
                graphics.fill(x, y, x + 16, y + 16, 0xA0101014);
            }
        }

        if(mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16)
        {
            boolean worn = EditorState.profile().diskWorn();
            graphics.fill(x, y, x + 16, y + 16, 0x80FFFFFF);
            graphics.setTooltipForNextFrame(net.minecraft.client.Minecraft.getInstance().font,
                Component.literal((disk.isEmpty() ? "Duel disk" : disk.getHoverName().getString())
                    + (worn ? "  (active)" : "  (Left Alt to equip)")),
                mouseX, mouseY);
        }
    }

    /** @return true if the click landed on the slot and was consumed */
    public static boolean click(int x, int y, double mouseX, double mouseY)
    {
        if(mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16)
        {
            // Clicking OPENS THE SHOP; F is what wears and unwears. A slot
            // that both equipped and shopped depending on how it was clicked
            // would be two controls sharing one square.
            ClientPlayNetworking.send(
                new de.cas_ual_ty.dueldimension.shop.DiskShopMessages.RequestShop(
                    de.cas_ual_ty.dueldimension.shop.DiskShopMessages.RequestShop.DISKS));
            return true;
        }
        return false;
    }
}
