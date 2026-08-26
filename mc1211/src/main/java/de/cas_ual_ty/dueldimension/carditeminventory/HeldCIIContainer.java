package de.cas_ual_ty.dueldimension.carditeminventory;

import de.cas_ual_ty.dueldimension.net.MenuData;
import de.cas_ual_ty.dueldimension.util.YDMItemHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;

import java.util.function.Consumer;

/**
 * A {@link CIIContainer} over an item held in the hand, which cannot show the
 * held stack as a pickup-able inventory slot (you would be able to pick up the
 * very item whose contents you are looking at). The extra data carries the
 * handler size and which hand, delivered by {@link MenuData} ahead of the menu.
 */
public abstract class HeldCIIContainer extends CIIContainer
{
    protected final InteractionHand hand;
    protected final net.minecraft.world.item.ItemStack itemStack;

    public HeldCIIContainer(MenuType<?> type, int id, Inventory playerInventoryIn, YDMItemHandler itemHandler, InteractionHand hand)
    {
        super(type, id, playerInventoryIn, itemHandler);
        this.hand = hand;
        itemStack = player.getItemInHand(hand);
    }

    public HeldCIIContainer(MenuType<?> type, int id, Inventory playerInventoryIn, FriendlyByteBuf extraData)
    {
        this(type, id, playerInventoryIn, new YDMItemHandler(extraData.readInt()), extraData.readBoolean() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
    }

    @Override
    protected void createBottomSlots(Inventory playerInventoryIn)
    {
        if(hand == InteractionHand.OFF_HAND)
        {
            super.createBottomSlots(playerInventoryIn);
            return;
        }

        final int i = (6 - 4) * 18;

        int id;

        for(int l = 0; l < 3; ++l)
        {
            for(int j1 = 0; j1 < 9; ++j1)
            {
                id = j1 + l * 9 + 9;

                if(id == playerInventoryIn.selected)
                {
                    addSlot(new Slot(playerInventoryIn, id, 8 + j1 * 18, 103 + l * 18 + i)
                    {
                        @Override
                        public boolean mayPickup(Player playerIn)
                        {
                            return false;
                        }
                    });
                }
                else
                {
                    addSlot(new Slot(playerInventoryIn, id, 8 + j1 * 18, 103 + l * 18 + i));
                }
            }
        }

        for(int i1 = 0; i1 < 9; ++i1)
        {
            id = i1;

            if(id == playerInventoryIn.selected)
            {
                addSlot(new Slot(playerInventoryIn, i1, 8 + i1 * 18, 161 + i)
                {
                    @Override
                    public boolean mayPickup(Player playerIn)
                    {
                        return false;
                    }
                });
            }
            else
            {
                addSlot(new Slot(playerInventoryIn, i1, 8 + i1 * 18, 161 + i));
            }
        }
    }

    public static void openGui(Player player, InteractionHand hand, int itemHandlerSize, MenuProvider p)
    {
        MenuData.open((ServerPlayer) player, p, (Consumer<RegistryFriendlyByteBuf>) extraData ->
        {
            extraData.writeInt(itemHandlerSize);
            extraData.writeBoolean(hand == InteractionHand.MAIN_HAND);
        });
    }
}
