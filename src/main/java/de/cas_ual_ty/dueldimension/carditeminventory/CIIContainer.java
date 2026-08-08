package de.cas_ual_ty.dueldimension.carditeminventory;

import de.cas_ual_ty.dueldimension.net.MenuData;
import de.cas_ual_ty.dueldimension.util.YDMItemHandler;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;
import java.util.function.Consumer;

/**
 * A paged view over a {@link YDMItemHandler}, the base of every card-holding
 * menu (binders, deck boxes, card sets).
 * <p>
 * Two things changed from the Forge version, both mechanical:
 * <ul>
 * <li>{@code SlotItemHandler} is gone. It existed only to make a Forge
 *     {@code IItemHandler} look like a vanilla {@link net.minecraft.world.Container}
 *     to a {@link Slot}; {@link YDMItemHandler} already <em>is</em> a Container
 *     here, so a plain {@code Slot} works with no adapter.</li>
 * <li>The extra data (the handler size) that Forge wrote into an
 *     {@code IContainerFactory} buffer now arrives as {@link MenuData}, one
 *     packet ahead of the menu — the {@link FriendlyByteBuf} constructor is
 *     unchanged and reads it exactly as before.</li>
 * </ul>
 */
public class CIIContainer extends AbstractContainerMenu
{
    public static final int INV_SIZE = 4 * 9;
    public static final int PAGE_SIZE = 6 * 9;

    protected final Player player;
    protected final YDMItemHandler itemHandler;

    protected int page;
    protected final int maxPage;
    protected boolean filling;

    public CIIContainer(MenuType<?> type, int id, Inventory playerInventoryIn, YDMItemHandler itemHandler)
    {
        super(type, id);

        player = playerInventoryIn.player;
        this.itemHandler = itemHandler;

        itemHandler.load();

        page = 0;
        maxPage = Mth.ceil(this.itemHandler.getSlots() / (double) PAGE_SIZE);

        updateSlots();
    }

    public CIIContainer(MenuType<?> type, int id, Inventory playerInventoryIn, int itemHandlerSize)
    {
        this(type, id, playerInventoryIn, new YDMItemHandler(itemHandlerSize));
    }

    public CIIContainer(MenuType<?> type, int id, Inventory playerInventoryIn, FriendlyByteBuf extraData)
    {
        this(type, id, playerInventoryIn, extraData.readInt());
    }

    protected void createTopSlots()
    {
        for(int j = 0; j < 6; ++j)
        {
            for(int k = 0; k < 9; ++k)
            {
                int slotIndex = k + j * 9;
                int itemIndex = page * PAGE_SIZE + slotIndex;
                slotIndex += 4 * 9;

                if(itemIndex >= itemHandler.getSlots())
                {
                    continue;
                }

                addSlot(new Slot(itemHandler, itemIndex, 8 + k * 18, 18 + j * 18)
                {
                    @Override
                    public boolean mayPlace(@Nonnull ItemStack stack)
                    {
                        return canPutStack(stack);
                    }

                    @Override
                    public boolean mayPickup(Player playerIn)
                    {
                        return canTakeStack(playerIn, getItem());
                    }
                });
            }
        }
    }

    protected void createBottomSlots(Inventory playerInventoryIn)
    {
        final int i = (6 - 4) * 18;

        for(int l = 0; l < 3; ++l)
        {
            for(int j1 = 0; j1 < 9; ++j1)
            {
                addSlot(new Slot(playerInventoryIn, j1 + l * 9 + 9, 8 + j1 * 18, 103 + l * 18 + i));
            }
        }

        for(int i1 = 0; i1 < 9; ++i1)
        {
            addSlot(new Slot(playerInventoryIn, i1, 8 + i1 * 18, 161 + i));
        }
    }

    public boolean canPutStack(ItemStack itemStack)
    {
        return false;
    }

    public boolean canTakeStack(Player player, ItemStack itemStack)
    {
        return true;
    }

    // sets the page but does not update anything
    public void setPage(int page)
    {
        this.page = page;
    }

    public int getPage()
    {
        return page;
    }

    public int getMaxPage()
    {
        return maxPage;
    }

    protected void updatePage()
    {
        ServerPlayNetworking.send((ServerPlayer) player, new CIIMessages.SetPage(page));
    }

    public void nextPage()
    {
        ++page;

        if(page >= maxPage)
        {
            page = 0;
        }

        updatePage();
        updateSlots();
    }

    public void prevPage()
    {
        --page;

        if(page < 0)
        {
            page = maxPage - 1;
        }

        updatePage();
        updateSlots();
    }

    public void updateSlots()
    {
        itemHandler.save();
        slots.clear();
        createBottomSlots(player.getInventory());
        createTopSlots();
    }

    @Override
    public boolean stillValid(Player playerIn)
    {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player playerIn, int index)
    {
        Slot slot = slots.get(index);
        ItemStack original = slot.getItem().copy();

        if(!original.isEmpty())
        {
            ItemStack itemStack = slot.getItem().split(1);

            if(index < INV_SIZE)
            {
                // move into container
                if(moveItemStackTo(itemStack, INV_SIZE, slots.size(), false))
                {
                    return slot.getItem();
                }
            }
            // move to inventory
            else if(moveItemStackTo(itemStack, 0, INV_SIZE, false))
            {
                return slot.getItem();
            }

            slot.set(original);
        }

        return ItemStack.EMPTY;
    }

    @Override
    public void removed(Player pPlayer)
    {
        itemHandler.save();
        super.removed(pPlayer);
    }

    public static void openGui(Player player, int itemHandlerSize, MenuProvider p)
    {
        MenuData.open((ServerPlayer) player, p, (Consumer<net.minecraft.network.RegistryFriendlyByteBuf>) extraData ->
            extraData.writeInt(itemHandlerSize));
    }
}
