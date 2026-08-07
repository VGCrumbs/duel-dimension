package de.cas_ual_ty.dueldimension.duel.network;

import de.cas_ual_ty.dueldimension.deckbox.DeckHolder;
import de.cas_ual_ty.dueldimension.duel.*;
import de.cas_ual_ty.dueldimension.duel.action.Action;
import net.minecraft.world.entity.player.Player;

import java.util.List;

public interface IDuelManagerProvider
{
    DuelManager getDuelManager();
    
    default DuelMessageHeader getMessageHeader()
    {
        return getDuelManager().headerFactory.get();
    }
    
    default void updateDuelState(DuelState duelState)
    {
        getDuelManager().setDuelStateAndUpdate(duelState);
    }
    
    default void handleAction(Action action)
    {
    }
    
    default void handleAllActions(List<Action> actions)
    {
    }
    
    default void receiveDeckSources(List<DeckSource> deckSources)
    {
    }
    
    default void receiveDeck(int index, DeckHolder deck)
    {
    }
    
    default void deckAccepted(PlayerRole role)
    {
    }
    
    default void receiveMessage(Player player, DuelChatMessage message)
    {
    }
}
