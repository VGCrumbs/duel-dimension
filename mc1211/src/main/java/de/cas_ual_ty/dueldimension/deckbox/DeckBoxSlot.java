package de.cas_ual_ty.dueldimension.deckbox;

import de.cas_ual_ty.dueldimension.DdItems;
import de.cas_ual_ty.dueldimension.util.YDMItemHandler;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

// Port: SlotItemHandler is a Forge type. YDMItemHandler is a vanilla Container,
// so a plain Slot works with it directly.
public class DeckBoxSlot extends Slot
{
    public DeckBoxSlot(YDMItemHandler itemHandler, int index, int xPosition, int yPosition)
    {
        super(itemHandler, index, xPosition, yPosition);
    }

    @Override
    public boolean mayPlace(ItemStack stack)
    {
        return stack.getItem() == DdItems.CARD;
    }
}
