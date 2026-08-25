package de.cas_ual_ty.dueldimension.set;

import de.cas_ual_ty.dueldimension.DuelDimension;
import de.cas_ual_ty.dueldimension.DdComponents;
import de.cas_ual_ty.dueldimension.DdContainerTypes;
import de.cas_ual_ty.dueldimension.carditeminventory.HeldCIIContainer;
import de.cas_ual_ty.dueldimension.util.YDMItemHandler;
import de.cas_ual_ty.dueldimension.util.DdUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Port: an opened pack's contents lived in the Forge {@code CARD_ITEM_INVENTORY}
 * capability, synced by hand through {@code getShareTag}/{@code readShareTag}.
 * They are the {@link DdComponents#CARD_INVENTORY} component now, reached through
 * {@link YDMItemHandler#boundTo}, which seeds a handler from the component and
 * writes every change back into the stack — so the hand-sync overrides are gone.
 */
public class OpenedCardSetItem extends CardSetBaseItem
{
    public OpenedCardSetItem(Properties properties)
    {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack itemStack, TooltipContext context,
        TooltipDisplay display, Consumer<Component> lines, TooltipFlag flag)
    {
        super.appendHoverText(itemStack, context, display, lines, flag);
        lines.accept(Component.translatable(getDescriptionId() + ".desc").withStyle((s) -> s.applyFormat(ChatFormatting.RED)));
    }

    @Override
    public InteractionResult use(Level world, Player player, InteractionHand hand)
    {
        if(!world.isClientSide() && hand == DdUtil.getActiveItem(player, this))
        {
            ItemStack itemStack = player.getItemInHand(hand);

            YDMItemHandler itemHandler = getItemHandler(itemStack);
            itemHandler.load();
            HeldCIIContainer.openGui(player, hand, itemHandler.getSlots(), new MenuProvider()
            {
                @Override
                public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player)
                {
                    return new CardSetContainer(DdContainerTypes.CARD_SET, id, playerInventory, itemHandler, hand);
                }

                @Override
                public Component getDisplayName()
                {
                    return Component.translatable("container." + DuelDimension.MOD_ID + ".card_set");
                }
            });

            return InteractionResult.SUCCESS;
        }

        return super.use(world, player, hand);
    }

    public int getSize(ItemStack itemStack)
    {
        return itemStack.getOrDefault(DdComponents.CARD_INVENTORY, List.of()).size();
    }

    public YDMItemHandler getItemHandler(ItemStack itemStack)
    {
        return YDMItemHandler.boundTo(itemStack, getSize(itemStack));
    }

    /**
     * What is inside an opened pack, in pull order.
     * <p>
     * Read rather than re-rolled: the contents were decided when the pack was
     * unsealed and written onto this stack, so drawing again would show the
     * player cards they did not actually get.
     */
    public static List<ItemStack> contentsOf(ItemStack openedStack)
    {
        List<ItemStack> cards = new java.util.ArrayList<>();
        YDMItemHandler handler = YDMItemHandler.boundTo(openedStack,
            openedStack.getOrDefault(DdComponents.CARD_INVENTORY, List.of()).size());
        handler.load();
        for(int slot = 0; slot < handler.getSlots(); slot++)
        {
            ItemStack card = handler.getStackInSlot(slot);
            if(!card.isEmpty())
            {
                cards.add(card);
            }
        }
        return cards;
    }

    public ItemStack createItemForSet(CardSet set, YDMItemHandler itemHandler)
    {
        ItemStack itemStack = new ItemStack(this);
        setCardSet(itemStack, set);
        YDMItemHandler current = YDMItemHandler.boundTo(itemStack, itemHandler.getSlots());
        current.setSize(itemHandler.getSlots());
        for(int i = 0; i < itemHandler.getSlots(); i++)
        {
            current.setStackInSlot(i, itemHandler.getStackInSlot(i));
        }
        return itemStack;
    }

    public ItemStack createItemForSet(CardSet set)
    {
        List<ItemStack> cards = set.open(new Random());
        NonNullList<ItemStack> items;

        if(cards == null)
        {
            items = NonNullList.withSize(0, ItemStack.EMPTY);
        }
        else
        {
            items = NonNullList.of(ItemStack.EMPTY, cards.toArray(ItemStack[]::new));
        }

        return createItemForSet(set, items);
    }

    public ItemStack createItemForSet(CardSet set, NonNullList<ItemStack> items)
    {
        ItemStack itemStack = new ItemStack(this);
        setCardSet(itemStack, set);
        YDMItemHandler current = YDMItemHandler.boundTo(itemStack, items.size());
        current.setSize(items.size());
        for(int i = 0; i < items.size(); i++)
        {
            current.setStackInSlot(i, items.get(i));
        }
        return itemStack;
    }
}
