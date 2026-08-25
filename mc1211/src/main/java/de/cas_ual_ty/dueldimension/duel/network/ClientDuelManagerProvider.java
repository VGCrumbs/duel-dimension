package de.cas_ual_ty.dueldimension.duel.network;

import de.cas_ual_ty.dueldimension.clientutil.ClientProxy;
import de.cas_ual_ty.dueldimension.deckbox.DeckHolder;
import de.cas_ual_ty.dueldimension.duel.*;
import de.cas_ual_ty.dueldimension.duel.action.Action;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.function.Consumer;

public class ClientDuelManagerProvider implements IDuelManagerProvider
{
    protected DuelManager duelManager;
    
    public ClientDuelManagerProvider(DuelManager duelManager)
    {
        this.duelManager = duelManager;
    }
    
    @Override
    public DuelManager getDuelManager()
    {
        return duelManager;
    }
    
    @Override
    public void updateDuelState(DuelState duelState)
    {
        IDuelManagerProvider.super.updateDuelState(duelState);
        ClientDuelManagerProvider.doForScreen((screen) -> screen.duelStateChanged());
    }
    
    @Override
    public void handleAction(Action action)
    {
        ClientDuelManagerProvider.doForScreen((screen) -> screen.handleAction(action));
    }
    
    @Override
    public void handleAllActions(List<Action> actions)
    {
        // just do all actions without animation
        
        for(Action action : actions)
        {
            action.initClient(getDuelManager().getPlayField());
            action.doAction();
            getDuelManager().logAction(action);
        }
    }
    
    @Override
    public void receiveDeckSources(List<DeckSource> deckSources)
    {
        ClientDuelManagerProvider.doForScreen((screen) -> screen.populateDeckSources(deckSources));
    }
    
    @Override
    public void receiveDeck(int index, DeckHolder deck)
    {
        ClientDuelManagerProvider.doForScreen((screen) -> screen.receiveDeck(index, deck));
    }
    
    @Override
    public void deckAccepted(PlayerRole role)
    {
        if(role == PlayerRole.PLAYER1)
        {
            getDuelManager().player1Deck = DeckHolder.DUMMY;
        }
        else if(role == PlayerRole.PLAYER2)
        {
            getDuelManager().player2Deck = DeckHolder.DUMMY;
        }
        
        ClientDuelManagerProvider.doForScreen((screen) -> screen.deckAccepted(role));
    }
    
    @Override
    public void receiveMessage(Player player, DuelChatMessage message)
    {
        getDuelManager().messages.add(message);
    }
    
    /**
     * Hands the open duel screen to whoever wants to update it.
     * <p>
     * This class keeps the client's copy of the duel in step whether or not
     * anything is on screen to show it, so a closed screen is not an error --
     * the update still happened, there was just nobody watching.
     * <p>
     * The one thing that changed from Forge: {@code Minecraft.screen} is gone
     * in 26.2, and the open screen is asked for through {@code gui.screen()}.
     */
    public static void doForScreen(
        Consumer<de.cas_ual_ty.dueldimension.duel.screen.DuelContainerScreen<
            ? extends de.cas_ual_ty.dueldimension.duel.DuelContainer>> consumer)
    {
        net.minecraft.client.gui.screens.Screen screen =
            ClientProxy.getMinecraft().screen;

        if(screen instanceof de.cas_ual_ty.dueldimension.duel.screen.DuelContainerScreen)
        {
            consumer.accept(
                (de.cas_ual_ty.dueldimension.duel.screen.DuelContainerScreen<?>)screen);
        }
    }
}
