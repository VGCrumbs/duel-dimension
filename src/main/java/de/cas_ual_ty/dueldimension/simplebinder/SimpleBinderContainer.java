package de.cas_ual_ty.dueldimension.simplebinder;

import de.cas_ual_ty.dueldimension.DdItems;
import de.cas_ual_ty.dueldimension.carditeminventory.HeldCIIContainer;
import de.cas_ual_ty.dueldimension.util.YDMItemHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

public class SimpleBinderContainer extends HeldCIIContainer
{
    public SimpleBinderContainer(MenuType<?> type, int id, Inventory playerInventoryIn, YDMItemHandler itemHandler, InteractionHand hand)
    {
        super(type, id, playerInventoryIn, itemHandler, hand);
    }
    
    public SimpleBinderContainer(MenuType<?> type, int id, Inventory playerInventoryIn, FriendlyByteBuf extraData)
    {
        super(type, id, playerInventoryIn, extraData);
    }
    
    @Override
    public boolean canPutStack(ItemStack itemStack)
    {
        return itemStack.getItem() == DdItems.CARD.get();
    }
}
