package de.cas_ual_ty.dueldimension.duel;

import de.cas_ual_ty.dueldimension.deckbox.DeckHolder;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedList;
import java.util.List;

/**
 * "Which decks could this player duel with?", asked of everyone who might know.
 * <p>
 * On Forge this extended {@code PlayerEvent} and went out on the mod bus. There
 * is no such bus here, so it is a Fabric {@link Event} instead — which is the
 * same thing with the inheritance removed: the event object carries the
 * question and collects the answers, and {@link #FIND} is where listeners
 * attach.
 * <p>
 * The mod answers it itself, by walking the player's inventory for deck boxes.
 * It stays an extension point rather than becoming a plain method call because
 * that is what it was: a deck could come from somewhere this mod has never
 * heard of, and nothing about that changed in the port.
 */
public class FindDecksEvent
{
    public interface FindDecks
    {
        void findDecks(FindDecksEvent event);
    }

    /** Every listener is asked; each adds what it knows about. */
    public static final Event<FindDecks> FIND = EventFactory.createArrayBacked(FindDecks.class,
        listeners -> event ->
        {
            for(FindDecks listener : listeners)
            {
                listener.findDecks(event);
            }
        });

    private final Player player;

    public List<DeckSource> decksList;

    public FindDecksEvent(Player player, DuelManager duelManager)
    {
        this.player = player;
        decksList = new LinkedList<>();
    }

    /** Was {@code PlayerEvent.getEntity()}, which this no longer inherits. */
    public Player getEntity()
    {
        return player;
    }

    public FindDecksEvent addDeck(DeckHolder deck, ItemStack itemStack)
    {
        decksList.add(new DeckSource(deck, itemStack));
        return this;
    }

    public FindDecksEvent addDeck(DeckSource deck)
    {
        decksList.add(deck);
        return this;
    }
}
