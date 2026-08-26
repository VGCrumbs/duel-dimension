package de.cas_ual_ty.dueldimension.cardbinder;

import de.cas_ual_ty.dueldimension.DdComponents;
import de.cas_ual_ty.dueldimension.DdContainerTypes;
import de.cas_ual_ty.dueldimension.DuelDimension;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/**
 * Port: the binder's cards are still a server-side {@link CardBinderCardsManager}
 * keyed by the binder's own id — that has not changed. What changed is where the
 * id sits. Forge kept it in a {@code UUID_HOLDER} capability on the stack, with a
 * {@code getShareTag}/{@code readShareTag} pair to carry it to the client and a
 * one-time migration reading an older {@code binder_uuid} tag key. Fabric has no
 * capabilities and {@link ItemStack} has no tag, so the id is a
 * {@link DdComponents#BINDER_UUID} component: it syncs like any component (the
 * share-tag overrides and {@code shouldOverrideMultiplayerNbt} are gone), and it
 * is read and written with {@code stack.get}/{@code stack.set}.
 * <p>
 * The old-tag migration branch is dropped with the tag it read: there is no NBT
 * on a stack to migrate from, so a stack that predates the component simply
 * mints a fresh id the first time it is used, exactly as an empty holder did.
 */
public class CardBinderItem extends Item implements MenuProvider
{
    private static final HashMap<UUID, CardBinderCardsManager> MANAGER_MAP = new HashMap<>();

    public CardBinderItem(Properties properties)
    {
        super(properties);
    }

    public CardBinderCardsManager getInventoryManager(ItemStack itemStack)
    {
        CardBinderCardsManager manager;
        UUID uuid = getUUID(itemStack);

        if(uuid == null || !CardBinderItem.MANAGER_MAP.containsKey(uuid))
        {
            manager = new CardBinderCardsManager();

            if(uuid == null)
            {
                manager.generateUUIDIfNull();
                uuid = manager.getUUID();
                setUUID(itemStack, uuid);
            }
            else
            {
                manager.setUUID(uuid);
            }

            CardBinderItem.MANAGER_MAP.put(uuid, manager);
        }
        else
        {
            manager = CardBinderItem.MANAGER_MAP.get(uuid);
        }

        return manager;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
        List<Component> tooltip, TooltipFlag flagIn)
    {
        super.appendHoverText(stack, context, tooltip, flagIn);

        tooltip.add(Component.translatable(getDescriptionId() + ".uuid"));

        UUID uuid = getUUID(stack);

        if(uuid != null)
        {
            tooltip.add(Component.literal(uuid.toString()));
        }
        else
        {
            tooltip.add(Component.translatable(getDescriptionId() + ".uuid.empty"));
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(net.minecraft.world.level.Level world, Player player, InteractionHand hand)
    {
        // must also fix UUID on client side if player is in creative mode
        getUUID(player.getItemInHand(hand));

        ItemStack stack = getActiveBinder(player);

        if(player.getItemInHand(hand) == stack)
        {
            if(world.isClientSide())
            {
                // The binder is a READER now: it reports how much of each pack
                // has been collected and holds nothing. That makes it a plain
                // client screen over the already-synced profile rather than a
                // container menu, so there is no menu to open and no server
                // round trip to wait on.
                de.cas_ual_ty.dueldimension.DuelDimension.proxy.openCollectionBinder();
            }
            return InteractionResultHolder.success(stack);
        }

        return super.use(world, player, hand);
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInv, Player player)
    {
        ItemStack s = getActiveBinder(player);
        return new CardBinderContainer(DdContainerTypes.CARD_BINDER, id, playerInv, getInventoryManager(s), s);
    }

    @Override
    public Component getDisplayName()
    {
        return Component.translatable("container." + DuelDimension.MOD_ID + ".card_binder");
    }

    public ItemStack getActiveBinder(Player player)
    {
        if(player.getMainHandItem().getItem() == this)
        {
            return player.getMainHandItem();
        }
        else if(player.getOffhandItem().getItem() == this)
        {
            return player.getOffhandItem();
        }
        else
        {
            return ItemStack.EMPTY;
        }
    }

    public UUID getUUID(ItemStack itemStack)
    {
        return itemStack.get(DdComponents.BINDER_UUID);
    }

    public void setUUID(ItemStack itemStack, UUID uuid)
    {
        itemStack.set(DdComponents.BINDER_UUID, uuid);
    }

    public void setUUIDAndUpdateManager(ItemStack itemStack, UUID uuid)
    {
        CardBinderCardsManager manager = getInventoryManager(itemStack);
        itemStack.set(DdComponents.BINDER_UUID, uuid);
        MANAGER_MAP.remove(manager.getUUID());
        manager.setUUID(uuid);
        MANAGER_MAP.put(uuid, manager);
    }
}
