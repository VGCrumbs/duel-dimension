package de.cas_ual_ty.dueldimension.duel;

import de.cas_ual_ty.dueldimension.DdItems;
import de.cas_ual_ty.dueldimension.card.properties.Properties;
import de.cas_ual_ty.dueldimension.deckbox.DeckHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

public class DeckSource
{
    public static final Supplier<DeckSource> EMPTY_DECK = () -> new DeckSource(DeckHolder.DUMMY, DdItems.CARD.get().createItemForCard(Properties.DUMMY));
    
    public DeckHolder deck;
    public ItemStack source;
    public Component name;
    
    public DeckSource(DeckHolder deck, ItemStack source, Component name)
    {
        this.deck = deck;
        this.source = source;
        this.name = name;
    }
    
    public DeckSource(DeckHolder deck, ItemStack source)
    {
        this(deck, source, source.getHoverName());
    }
}
