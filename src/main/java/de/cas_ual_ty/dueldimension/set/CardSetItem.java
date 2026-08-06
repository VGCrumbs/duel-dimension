package de.cas_ual_ty.dueldimension.set;

import de.cas_ual_ty.dueldimension.DdDatabase;
import de.cas_ual_ty.dueldimension.DdItems;
import net.minecraft.core.NonNullList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class CardSetItem extends CardSetBaseItem
{
    public CardSetItem(Item.Properties properties)
    {
        super(properties);
    }
    
    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand)
    {
        ItemStack stack = CardSetItem.getActiveSet(player);
        
        if(player.getItemInHand(hand) == stack)
        {
            boolean revealing = unseal(stack, player, hand);
            
            if(!world.isClientSide && !revealing)
            {
                // Only fall through to the opened pack's own screen when there
                // is no reveal to watch. Chaining into it unconditionally meant
                // the container was opened in the same click and replaced the
                // reveal before a single card had turned.
                return player.getItemInHand(hand).getItem().use(world, player, hand);
            }
        }
        
        return super.use(world, player, hand);
    }
    
    /**
     * Opens the pack.
     *
     * @return true if a reveal was sent, so the caller should leave the screen
     *         to it rather than opening the pack's container over the top
     */
    public boolean unseal(ItemStack itemStack, Player player, InteractionHand hand)
    {
        ItemStack newStack = DdItems.OPENED_SET.get().createItemForSet(getCardSet(itemStack));
        player.setItemInHand(hand, newStack);
        // The pull has already happened and is written onto the stack, so this
        // reports what was drawn rather than drawing it again -- a second roll
        // would show the player cards they did not get.
        boolean revealing = announcePull(getCardSet(itemStack), newStack, player);
        if(revealing && !player.level.isClientSide)
        {
            // The reveal is the opening, so the cards go straight to the player
            // and the spent wrapper goes away. Leaving an empty pack in hand to
            // be emptied by hand afterwards made the reveal a preview of work
            // still to do rather than the opening itself.
            for(ItemStack card : de.cas_ual_ty.dueldimension.set.OpenedCardSetItem.contentsOf(newStack))
            {
                player.getInventory().placeItemBackInInventory(card.copy());
            }
            player.setItemInHand(hand, ItemStack.EMPTY);
        }
        
        if(itemStack.getCount() > 1)
        {
            itemStack.shrink(1);
            
            if(!player.level.isClientSide)
            {
                player.getInventory().placeItemBackInInventory(itemStack);
            }
        }
        return revealing;
    }
    
    public ItemStack createItemForSet(CardSet set)
    {
        ItemStack itemStack = new ItemStack(this);
        setCardSet(itemStack, set);
        return itemStack;
    }
    
    @Override
    public void fillItemCategory(CreativeModeTab group, NonNullList<ItemStack> items)
    {
        if(!allowedIn(group))
        {
            return;
        }
        
        for(CardSet set : DdDatabase.SETS_LIST)
        {
            if(set.isIndependentAndItem())
            {
                items.add(createItemForSet(set));
            }
        }
    }
    
    public static ItemStack getActiveSet(Player player)
    {
        if(player.getMainHandItem().getItem() == DdItems.SET.get())
        {
            return player.getMainHandItem();
        }
        else if(player.getOffhandItem().getItem() == DdItems.SET.get())
        {
            return player.getOffhandItem();
        }
        else
        {
            return ItemStack.EMPTY;
        }
    }

    /**
     * Sends the reveal to the player who opened it.
     *
     * @return true if there was something to reveal
     */
    private static boolean announcePull(CardSet set, ItemStack openedStack, Player player)
    {
        if(!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer))
        {
            return false;
        }
        java.util.List<Integer> codes = new java.util.ArrayList<>();
        java.util.List<String> rarities = new java.util.ArrayList<>();
        for(ItemStack card : de.cas_ual_ty.dueldimension.set.OpenedCardSetItem.contentsOf(openedStack))
        {
            if(card.isEmpty() || !(card.getItem() instanceof de.cas_ual_ty.dueldimension.card.CardItem item))
            {
                continue;
            }
            de.cas_ual_ty.dueldimension.card.CardHolder holder = item.getCardHolder(card);
            if(holder == null || holder.getCard() == null)
            {
                continue;
            }
            codes.add((int)holder.getCard().getId());
            rarities.add(holder.getRarity() == null ? "" : holder.getRarity());
        }
        if(codes.isEmpty())
        {
            return false;
        }
        de.cas_ual_ty.dueldimension.DuelDimension.channel.send(
            net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> serverPlayer),
            new de.cas_ual_ty.dueldimension.set.PackMessages.OpenPack(
                set == null ? "Card Pack" : set.name, codes, rarities));
        return true;
    }
}
