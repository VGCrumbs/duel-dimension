package de.cas_ual_ty.dueldimension.set;

import de.cas_ual_ty.dueldimension.DdItems;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
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

            if(!world.isClientSide() && !revealing)
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
        ItemStack newStack = DdItems.OPENED_SET.createItemForSet(getCardSet(itemStack));
        player.setItemInHand(hand, newStack);
        // The pull has already happened and is written onto the stack, so this
        // reports what was drawn rather than drawing it again -- a second roll
        // would show the player cards they did not get.
        boolean revealing = announcePull(getCardSet(itemStack), newStack, player);
        if(revealing && !player.level().isClientSide())
        {
            // The reveal is the opening: the cards were recorded in the
            // player's collection by announcePull, and the spent wrapper goes
            // away. They are NOT also handed over as items -- the collection is
            // where a card is owned now, and putting each one in the inventory
            // as well would be the same fact twice, filling a hotbar with
            // things that can be dropped by accident.
            player.setItemInHand(hand, ItemStack.EMPTY);
        }

        if(itemStack.getCount() > 1)
        {
            itemStack.shrink(1);

            if(!player.level().isClientSide())
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

    public static ItemStack getActiveSet(Player player)
    {
        if(player.getMainHandItem().getItem() == DdItems.SET)
        {
            return player.getMainHandItem();
        }
        else if(player.getOffhandItem().getItem() == DdItems.SET)
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
        java.util.List<Integer> arts = new java.util.ArrayList<>();
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
            // The artwork this printing specifies, which the set file named and
            // the puller has been carrying on the holder all along. Gathered in
            // the same pass as the rarity so the three lists stay in step.
            arts.add((int)holder.getImageIndex());
        }
        if(codes.isEmpty())
        {
            return false;
        }
        // Recorded on the server, exactly as a shop purchase is: a pack opened
        // in the world and a pack bought at the counter are the same event as
        // far as the collection is concerned.
        // With the rarity, which was gathered two lines up for the reveal and
        // then thrown away here -- every card opened in the world landed under
        // Trunk.UNKNOWN_RARITY, so the primary way a player acquires cards told
        // the collection nothing about which printing it had. And with the
        // artwork beside it, for the same reason and from the same holder: a
        // printing that names image 2 is a copy that wears image 2, and the
        // deck editor defaults a new copy's art from what is recorded here. The
        // three lists are filled in lockstep above (a card that is skipped is
        // skipped from all three), so index i is the same card in each.
        de.cas_ual_ty.dueldimension.duel.profile.Trunk trunk =
            de.cas_ual_ty.dueldimension.duel.profile.DuelProfiles.get(serverPlayer).trunk();
        for(int i = 0; i < codes.size(); i++)
        {
            trunk.add(codes.get(i), rarities.get(i), arts.get(i), 1);
        }
        de.cas_ual_ty.dueldimension.duel.profile.DuelProfiles.saveAndSync(serverPlayer);
        // Fabric registers a receiver by direction and hands it the payload and
        // the sender, so the reveal is a plain send now rather than a
        // PacketDistributor target.
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(serverPlayer,
            new de.cas_ual_ty.dueldimension.set.PackMessages.OpenPack(
                set == null ? "Card Pack" : set.name, codes, rarities));
        return true;
    }
}
