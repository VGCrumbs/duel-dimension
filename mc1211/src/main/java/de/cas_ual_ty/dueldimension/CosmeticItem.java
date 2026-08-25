package de.cas_ual_ty.dueldimension;


import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

public class CosmeticItem extends Item
{
    public CosmeticItem(Properties properties)
    {
        super(properties);
    }
    
    /**
     * A tooltip is fed to a consumer now rather than added to a list, and the
     * level it used to be given became a {@link TooltipContext}.
     */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
        TooltipDisplay display, Consumer<Component> lines, TooltipFlag flag)
    {
        super.appendHoverText(stack, context, display, lines, flag);
        lines.accept(Component.translatable(getDescriptionId() + ".desc"));
    }
}
