package de.cas_ual_ty.dueldimension;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

public class DdItemGroup extends CreativeModeTab
{
    private Supplier<Item> supplier;
    
    public DdItemGroup(String label, Supplier<Item> supplier)
    {
        super(label);
        this.supplier = supplier;
    }
    
    @Override
    public ItemStack makeIcon()
    {
        return new ItemStack(supplier.get());
    }
}
