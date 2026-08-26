package de.cas_ual_ty.dueldimension.card;

import de.cas_ual_ty.dueldimension.clientutil.CardPresentation;
import de.cas_ual_ty.dueldimension.DuelDimension;
import de.cas_ual_ty.dueldimension.rarity.Rarities;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/** A single card, as an item. */
public class CardItem extends Item
{
    public CardItem(Properties properties)
    {
        super(properties);
    }

    /**
     * The card's own description, added under the item's name line.
     * <p>
     * 1.21.1 hands the tooltip over as a {@link List}, the shape 1.19.2 used,
     * so the lines are added to it rather than fed to a consumer. 26.2's
     * behaviour is kept otherwise: the list is NOT cleared first. 1.19.2 called
     * {@code tooltip.clear()} here to drop the item's own name in favour of the
     * card's; 26.2's consumer had nothing to clear, so the clear went, and the
     * name line now sits above a card description that repeats it. Adding
     * {@code lines.clear();} as the first statement would restore the 1.19.2
     * tooltip -- that is a change to what the working mod does, so it is not
     * made here.
     */
    @Override
    public void appendHoverText(ItemStack itemStack, TooltipContext context,
        List<Component> lines, TooltipFlag flag)
    {
        List<Component> information = new ArrayList<>();
        CardPresentation.addInformation(getCardHolder(itemStack), information);
        lines.addAll(information);
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
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        ItemStack itemStack = player.getItemInHand(hand);
        CardHolder cardHolder = getCardHolder(itemStack);
        if(cardHolder != null && player.level().isClientSide())
        {
            DuelDimension.proxy.openCardInspectScreen(cardHolder);
            return InteractionResultHolder.success(itemStack);
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
