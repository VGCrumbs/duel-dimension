package de.cas_ual_ty.dueldimension.set;

import de.cas_ual_ty.dueldimension.DuelDimension;
import de.cas_ual_ty.dueldimension.DdComponents;
import de.cas_ual_ty.dueldimension.DdContainerTypes;
import de.cas_ual_ty.dueldimension.DdDatabase;
import de.cas_ual_ty.dueldimension.DdItems;
import de.cas_ual_ty.dueldimension.card.CardHolder;
import de.cas_ual_ty.dueldimension.carditeminventory.CIIContainer;
import de.cas_ual_ty.dueldimension.util.YDMItemHandler;
import de.cas_ual_ty.dueldimension.util.DdUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.util.SortedArraySet;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Port: the Forge build kept a set-item's code in the stack's tag under
 * {@code CODE}. There is no tag; the code is the {@link DdComponents#SET_CODE}
 * component now, read and written through {@link #getCardSet}/{@link #setCardSet}.
 * The {@code getShareTag}/{@code shouldOverrideMultiplayerNbt} overrides are gone
 * — a component syncs on its own — and {@code fillItemCategory} moved to
 * {@link de.cas_ual_ty.dueldimension.DdItemGroup}, since an item no longer names
 * its own creative tab.
 */
public abstract class CardSetBaseItem extends Item
{
    public CardSetBaseItem(Properties properties)
    {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack itemStack, TooltipContext context,
        TooltipDisplay display, Consumer<Component> lines, TooltipFlag flag)
    {
        CardSet set = getCardSet(itemStack);
        List<Component> tooltip = new ArrayList<>();
        set.addItemInformation(tooltip);
        tooltip.forEach(lines);
    }

    @Override
    public Component getName(ItemStack itemStack)
    {
        CardSet set = getCardSet(itemStack);
        return Component.literal(set.name);
    }

    public CardSet getCardSet(ItemStack itemStack)
    {
        String code = itemStack.getOrDefault(DdComponents.SET_CODE, "");

        if(code.isEmpty())
        {
            return CardSet.DUMMY;
        }

        CardSet set = DdDatabase.SETS_LIST.get(code);

        if(set == null)
        {
            set = CardSet.DUMMY;
        }

        return set;
    }

    public void setCardSet(ItemStack itemStack, CardSet set)
    {
        itemStack.set(DdComponents.SET_CODE, set.code);
    }

    public void viewSetContents(Level world, Player player, ItemStack itemStack)
    {
        if(!world.isClientSide())
        {
            CardSet set = getCardSet(itemStack);
            SortedArraySet<CardHolder> cardsSet = set.getAllCardEntries();
            CardHolder[] cards = cardsSet.toArray(new CardHolder[0]);

            CIIContainer.openGui(player, cards.length, new MenuProvider()
            {
                @Override
                public AbstractContainerMenu createMenu(int id, Inventory playerInv, Player p_createMenu_3_)
                {
                    YDMItemHandler itemHandler = new YDMItemHandler(cards.length);

                    for(int i = 0; i < cards.length; ++i)
                    {
                        itemHandler.insertItem(i, DdItems.CARD.createItemForCardHolder(cards[i]), false);
                    }

                    return new CardSetContentsContainer(DdContainerTypes.CARD_SET_CONTENTS, id, playerInv, itemHandler);
                }

                @Override
                public Component getDisplayName()
                {
                    return Component.translatable("container." + DuelDimension.MOD_ID + ".card_set_contents");
                }
            });
        }
    }

    public static InteractionHand getActiveSetItem(Player player)
    {
        return DdUtil.getActiveItem(player, (i) -> (i.getItem() instanceof CardSetBaseItem));
    }
}
