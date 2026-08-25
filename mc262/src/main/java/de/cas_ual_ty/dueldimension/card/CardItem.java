package de.cas_ual_ty.dueldimension.card;

import de.cas_ual_ty.dueldimension.clientutil.CardPresentation;
import de.cas_ual_ty.dueldimension.DuelDimension;
import de.cas_ual_ty.dueldimension.rarity.Rarities;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** A single card, as an item. */
public class CardItem extends Item
{
    public CardItem(Properties properties)
    {
        super(properties);
    }

    /**
     * The whole tooltip, replacing the default rather than adding to it.
     * <p>
     * The signature changed: a tooltip is built by feeding lines to a
     * {@link Consumer} now, not by editing a list. The old code called
     * {@code tooltip.clear()} first, which was how it dropped the item's own
     * name and description in favour of the card's. There is nothing to clear
     * when nothing has been handed over yet, so the clear simply goes.
     */
    @Override
    public void appendHoverText(ItemStack itemStack, TooltipContext context,
        TooltipDisplay display, Consumer<Component> lines, TooltipFlag flag)
    {
        List<Component> information = new ArrayList<>();
        CardPresentation.addInformation(getCardHolder(itemStack), information);
        information.forEach(lines);
    }

    @Override
    public Component getName(ItemStack itemStack)
    {
        return Component.literal(getCardHolder(itemStack).getCard().getName());
    }

    /**
     * Right-clicking a card opens its inspect screen.
     * <p>
     * {@code InteractionResultHolder<ItemStack>} is gone -- the result no
     * longer carries the stack back, because nothing did anything with it that
     * the stack itself could not.
     */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand)
    {
        ItemStack itemStack = player.getItemInHand(hand);
        CardHolder cardHolder = getCardHolder(itemStack);
        if(cardHolder != null && player.level().isClientSide())
        {
            DuelDimension.proxy.openCardInspectScreen(cardHolder);
            return InteractionResult.SUCCESS;
        }
        return super.use(level, player, hand);
    }

    public CardHolder getCardHolder(ItemStack itemStack)
    {
        return new ItemStackCardHolder(itemStack);
    }

    public ItemStack createItemForCard(de.cas_ual_ty.dueldimension.card.properties.Properties card,
        byte imageIndex, String rarity, String code)
    {
        ItemStack itemStack = new ItemStack(this);
        getCardHolder(itemStack).override(new CardHolder(card, imageIndex, rarity, code));
        return itemStack;
    }

    public ItemStack createItemForCard(de.cas_ual_ty.dueldimension.card.properties.Properties card,
        byte imageIndex, String rarity)
    {
        ItemStack itemStack = new ItemStack(this);
        getCardHolder(itemStack).override(new CardHolder(card, imageIndex, rarity));
        return itemStack;
    }

    public ItemStack createItemForCard(de.cas_ual_ty.dueldimension.card.properties.Properties card)
    {
        return createItemForCard(card, (byte)0, Rarities.CREATIVE.name);
    }

    public ItemStack createItemForCardHolder(CardHolder card)
    {
        ItemStack itemStack = new ItemStack(this);
        getCardHolder(itemStack).override(card);
        return itemStack;
    }

    // fillItemCategory is gone. An item no longer decides what a creative tab
    // shows; a tab is built from a list the mod supplies, so the cards tab is
    // filled by DdItemGroup instead. Same behaviour, reached from the other end.
}
