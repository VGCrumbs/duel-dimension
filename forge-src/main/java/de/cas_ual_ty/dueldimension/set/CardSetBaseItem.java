package de.cas_ual_ty.dueldimension.set;

import de.cas_ual_ty.dueldimension.DuelDimension;
import de.cas_ual_ty.dueldimension.DdContainerTypes;
import de.cas_ual_ty.dueldimension.DdDatabase;
import de.cas_ual_ty.dueldimension.DdItems;
import de.cas_ual_ty.dueldimension.card.CardHolder;
import de.cas_ual_ty.dueldimension.carditeminventory.CIIContainer;
import de.cas_ual_ty.dueldimension.util.JsonKeys;
import de.cas_ual_ty.dueldimension.util.YDMItemHandler;
import de.cas_ual_ty.dueldimension.util.DdUtil;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.SortedArraySet;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public abstract class CardSetBaseItem extends Item
{
    public CardSetBaseItem(Properties properties)
    {
        super(properties);
    }
    
    @Override
    public void appendHoverText(ItemStack itemStack, Level worldIn, List<Component> tooltip, TooltipFlag flagIn)
    {
        CardSet set = getCardSet(itemStack);
        tooltip.clear();
        set.addItemInformation(tooltip);
    }
    
    @Override
    public Component getName(ItemStack itemStack)
    {
        CardSet set = getCardSet(itemStack);
        return Component.literal(set.name);
    }
    
    public CardSet getCardSet(ItemStack itemStack)
    {
        String code = getNBT(itemStack).getString(JsonKeys.CODE);
        
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
        getNBT(itemStack).putString(JsonKeys.CODE, set.code);
    }
    
    public CompoundTag getNBT(ItemStack itemStack)
    {
        return itemStack.getOrCreateTag();
    }
    
    @Override
    public abstract void fillItemCategory(CreativeModeTab group, NonNullList<ItemStack> items);
    
    @Override
    public boolean shouldOverrideMultiplayerNbt()
    {
        return true;
    }
    
    public void viewSetContents(Level world, Player player, ItemStack itemStack)
    {
        if(!world.isClientSide)
        {
            CardSet set = getCardSet(itemStack);
            SortedArraySet<CardHolder> cardsSet = set.getAllCardEntries();
            CardHolder[] cards = cardsSet.toArray(new CardHolder[0]);
            
            CIIContainer.openGui(player, cards.length, new MenuProvider()
            {
                @Override
                public AbstractContainerMenu createMenu(int id, Inventory playerInv, Player p_createMenu_3_)
                {
                    YDMItemHandler itemHandler = new YDMItemHandler(cards.length, itemStack::getOrCreateTag);
                    
                    for(int i = 0; i < cards.length; ++i)
                    {
                        itemHandler.insertItem(i, DdItems.CARD.get().createItemForCardHolder(cards[i]), false);
                    }
                    
                    return new CardSetContentsContainer(DdContainerTypes.CARD_SET_CONTENTS.get(), id, playerInv, itemHandler);
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
