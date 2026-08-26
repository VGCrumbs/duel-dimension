package de.cas_ual_ty.dueldimension.card;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Port note: this is a dependency the deck box needs (the sleeves slot checks
 * for it and reads {@code sleeves}). Two Item hooks changed shape:
 * <ul>
 * <li>{@code appendHoverText} takes the tooltip as a {@link List} here, the
 *     1.19.2 shape; the consumer form is 26.2's. Same behaviour either way,
 *     as in {@link CardItem}.</li>
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
        List<Component> lines, TooltipFlag flag)
    {
        super.appendHoverText(stack, context, lines, flag);

        if(sleeves.isPatreonReward)
        {
            lines.add(Component.literal(sleeves.patronName + "'s Patreon Sleeves"));
        }
    }
}
