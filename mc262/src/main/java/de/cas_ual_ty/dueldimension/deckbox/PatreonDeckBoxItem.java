package de.cas_ual_ty.dueldimension.deckbox;

import de.cas_ual_ty.dueldimension.DdItems;
import de.cas_ual_ty.dueldimension.duel.DeckSource;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

/**
 * Port: the Forge build filled the creative tab with its prebuilt decks by
 * overriding {@code fillItemCategory}. An item no longer decides what a tab
 * shows — a tab supplies its own contents — so that walk moved to
 * {@link de.cas_ual_ty.dueldimension.DdItemGroup}, which calls
 * {@link #makeItemStackFromDeckSource(DeckSource)} for each patreon deck.
 */
public class PatreonDeckBoxItem extends DeckBoxItem
{
    public PatreonDeckBoxItem(Properties properties)
    {
        super(properties);
    }

    public ItemStack makeItemStackFromDeckSource(DeckSource s)
    {
        ItemStack itemStack = new ItemStack(DdItems.PATREON_DECK_BOX);
        setDeckHolder(itemStack, s.deck);
        // setHoverName is gone: a custom name is the CUSTOM_NAME component now.
        itemStack.set(DataComponents.CUSTOM_NAME, s.name);
        return itemStack;
    }
}
