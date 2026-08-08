package de.cas_ual_ty.dueldimension.card;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/**
 * Port note: this is a dependency the deck box needs (the sleeves slot checks
 * for it and reads {@code sleeves}). Two Item hooks changed shape:
 * <ul>
 * <li>{@code appendHoverText} feeds lines to a {@link Consumer} now instead of
 *     editing a list — same behaviour, reached the other way, as in
 *     {@link CardItem}.</li>
 * <li>{@code getRarity(ItemStack)} is gone: rarity is a data component set from
 *     the item's {@code Properties} at registration, so it moves there when the
 *     sleeves items themselves are registered in a later phase.</li>
 * </ul>
 */
public class CardSleevesItem extends Item
{
    public final CardSleevesType sleeves;

    public CardSleevesItem(Properties properties, CardSleevesType sleeves)
    {
        super(properties);
        this.sleeves = sleeves;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
        TooltipDisplay display, Consumer<Component> lines, TooltipFlag flag)
    {
        super.appendHoverText(stack, context, display, lines, flag);

        if(sleeves.isPatreonReward)
        {
            lines.accept(Component.literal(sleeves.patronName + "'s Patreon Sleeves"));
        }
    }
}
