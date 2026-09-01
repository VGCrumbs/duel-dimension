package de.cas_ual_ty.dueldimension.set;

import de.cas_ual_ty.dueldimension.DdItems;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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

    /**
     * Shift + right-click looks the product up instead of opening it.
     *
     * <h2>Client only, and it must stay that way</h2>
     * The browser lives on the player's machine. A server has no browser to
     * open and no business opening one, so this returns before the interaction
     * reaches the server at all -- which also means a sealed pack is never
     * consumed by a lookup, whatever the timing.
     *
     * <h2>Through ConfirmLinkScreen</h2>
     * Vanilla shows the address and asks before following any link it offers,
     * and a card database is no reason to be the exception. The same route
     * {@code CardInfoScreen} takes to TCGplayer.
     */
    private static InteractionResult lookUp(Level world, Player player, InteractionHand hand)
    {
        if(!world.isClientSide())
        {
            return null;
        }
        // Read from the WINDOW, not from Screen.hasShiftDown(): that reads a
        // screen's own cached modifier state, and there is no screen open when
        // a pack is right-clicked in the world. The same reason CardShopScreen
        // reads it this way.
        if(!com.mojang.blaze3d.platform.InputConstants.isKeyDown(
                net.minecraft.client.Minecraft.getInstance().getWindow(),
                org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT)
            && !com.mojang.blaze3d.platform.InputConstants.isKeyDown(
                net.minecraft.client.Minecraft.getInstance().getWindow(),
                org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT))
        {
            return null;
        }
        de.cas_ual_ty.dueldimension.set.CardSet set = null;
        ItemStack held = player.getItemInHand(hand);
        if(held.getItem() instanceof CardSetBaseItem sealed)
        {
            set = sealed.getCardSet(held);
        }
        if(set == null || set.name == null || set.name.isEmpty())
        {
            return null;
        }
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        net.minecraft.client.gui.screens.Screen screen = minecraft.gui.screen();
        String query = java.net.URLEncoder.encode(
            set.name, java.nio.charset.StandardCharsets.UTF_8);
        java.net.URI uri = java.net.URI.create(
            "https://ygoprodeck.com/card-database/?&cardset=" + query);
        minecraft.gui.setScreen(new net.minecraft.client.gui.screens.ConfirmLinkScreen(
            confirmed ->
            {
                if(confirmed)
                {
                    net.minecraft.util.Util.getPlatform().openUri(uri);
                }
                minecraft.gui.setScreen(screen);
            }, uri.toString(), true));
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level world, Player player, InteractionHand hand)
    {
        InteractionResult lookup = lookUp(world, player, hand);
        if(lookup != null)
        {
            return lookup;
        }

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
        // Parallel to `codes`, not to the contents: this loop skips anything
        // that is not a card, so the source is picked up by index rather than
        // carried across whole, or a skipped entry would shift every later
        // card into the wrong pack.
        java.util.List<String> sources = new java.util.ArrayList<>();
        java.util.List<ItemStack> contents =
            de.cas_ual_ty.dueldimension.set.OpenedCardSetItem.contentsOf(openedStack);
        java.util.List<String> pulledFrom =
            de.cas_ual_ty.dueldimension.set.OpenedCardSetItem.sourcesOf(openedStack);
        for(int contentIndex = 0; contentIndex < contents.size(); contentIndex++)
        {
            ItemStack card = contents.get(contentIndex);
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
            sources.add(contentIndex < pulledFrom.size() ? pulledFrom.get(contentIndex)
                : (set == null ? "" : set.code));
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
        java.util.List<Boolean> fresh = freshAmong(trunk, codes);
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
                set == null ? "Card Pack" : set.name, codes, rarities, fresh, sources));
        return true;
    }

    /**
     * Which of these codes the trunk does not already hold.
     * <p>
     * Asked BEFORE anything is added, and each code counted once: the second
     * copy of a card in one payout is not new, even though the first was.
     */
    private static java.util.List<Boolean> freshAmong(
        de.cas_ual_ty.dueldimension.duel.profile.Trunk trunk, java.util.List<Integer> codes)
    {
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        java.util.List<Boolean> fresh = new java.util.ArrayList<>(codes.size());
        for(int code : codes)
        {
            fresh.add(!trunk.has(code) && seen.add(code));
        }
        return fresh;
    }
}
