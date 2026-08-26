package de.cas_ual_ty.dueldimension;


import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class CosmeticItem extends Item
{
    public CosmeticItem(Properties properties)
    {
        super(properties);
    }
    
    /**
     * The tooltip is still a {@link List} to add to on 1.21.1; what changed
     * from 1.19.2 is that the level became a {@link TooltipContext}.
     */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
        List<Component> lines, TooltipFlag flag)
    {
        super.appendHoverText(stack, context, lines, flag);
        lines.add(Component.translatable(getDescriptionId() + ".desc"));
    }
}
