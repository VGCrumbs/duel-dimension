package de.cas_ual_ty.dueldimension.simplebinder;

import de.cas_ual_ty.dueldimension.DdContainerTypes;
import de.cas_ual_ty.dueldimension.DuelDimension;
import de.cas_ual_ty.dueldimension.carditeminventory.HeldCIIContainer;
import de.cas_ual_ty.dueldimension.util.DdUtil;
import de.cas_ual_ty.dueldimension.util.YDMItemHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Port: the simple binder stores its cards on the stack, so where Forge reached
 * for the {@code CARD_ITEM_INVENTORY} capability it now seeds a
 * {@link YDMItemHandler} from the {@code CARD_INVENTORY} component through
 * {@link YDMItemHandler#boundTo(ItemStack, int)} — the handler writes every
 * change back into the stack, which is what the capability's live storage did.
 * So the {@code getShareTag}/{@code readShareTag}/{@code shouldOverrideMultiplayerNbt}
 * hand-sync overrides are gone (a component syncs on its own), {@code use}
 * returns {@link InteractionResult}, and {@code LazyOptional} is gone with the
 * capability it wrapped — {@link #getItemHandler} hands back the handler
 * directly.
 */
public class SimpleBinderItem extends Item
{
    public final int binderSize;

    public SimpleBinderItem(Properties properties, int binderSize)
    {
        super(properties);
        this.binderSize = binderSize;
    }

    @Override
    public InteractionResult use(Level world, Player player, InteractionHand hand)
    {
        if(!world.isClientSide() && hand == DdUtil.getActiveItem(player, this))
        {
            ItemStack itemStack = player.getItemInHand(hand);

            YDMItemHandler handler = getItemHandler(itemStack);

            HeldCIIContainer.openGui(player, hand, binderSize, new MenuProvider()
            {
                @Override
                public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player)
                {
                    return new SimpleBinderContainer(DdContainerTypes.SIMPLE_BINDER, id, playerInventory, handler, hand);
                }

                @Override
                public Component getDisplayName()
                {
                    return Component.translatable("container." + DuelDimension.MOD_ID + ".simple_binder");
                }
            });

            return InteractionResult.SUCCESS;
        }

        return super.use(world, player, hand);
    }

    public YDMItemHandler getItemHandler(ItemStack itemStack)
    {
        return YDMItemHandler.boundTo(itemStack, binderSize);
    }
}
