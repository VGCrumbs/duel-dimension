package de.cas_ual_ty.dueldimension.deckbox;

import de.cas_ual_ty.dueldimension.DdItems;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;

public class DeckBoxSlot extends SlotItemHandler
{
    public DeckBoxSlot(IItemHandler itemHandler, int index, int xPosition, int yPosition)
    {
        super(itemHandler, index, xPosition, yPosition);
    }
    
    @Override
    public boolean mayPlace(ItemStack stack)
    {
        return stack.getItem() == DdItems.CARD.get();
    }
}
