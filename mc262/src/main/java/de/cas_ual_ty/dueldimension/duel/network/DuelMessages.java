package de.cas_ual_ty.dueldimension.duel.network;

import net.minecraft.network.chat.ComponentSerialization;
import de.cas_ual_ty.dueldimension.deckbox.DeckHolder;
import de.cas_ual_ty.dueldimension.duel.DeckSource;
import de.cas_ual_ty.dueldimension.duel.DuelChatMessage;
import de.cas_ual_ty.dueldimension.duel.DuelState;
import de.cas_ual_ty.dueldimension.duel.PlayerRole;
import de.cas_ual_ty.dueldimension.duel.action.Action;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

public class DuelMessages
{
    public static class SelectRole extends DuelMessage.ServerBaseMessage
    {
        public PlayerRole playerRole;
        
        public SelectRole(DuelMessageHeader header, PlayerRole playerRole)
        {
            super(header);
            this.playerRole = playerRole;
        }
        
        public SelectRole(RegistryFriendlyByteBuf buf)
        {
            super(buf);
        }
        
        @Override
        public void encodeMessage(RegistryFriendlyByteBuf buf)
        {
            DuelMessageUtility.encodePlayerRole(playerRole, buf);
        }
        
        @Override
        public void decodeMessage(RegistryFriendlyByteBuf buf)
        {
            playerRole = DuelMessageUtility.decodePlayerRole(buf);
        }
        
        @Override
        public void handleMessage(Player player, IDuelManagerProvider provider)
        {
            provider.getDuelManager().playerSelectRole(player, playerRole);
        }
    }
    
    public static class UpdateRole extends DuelMessage.ClientBaseMessage
    {
        @Nullable
        public PlayerRole role;
        
        public UUID rolePlayerId;
        
        public UpdateRole(DuelMessageHeader header, @Nullable PlayerRole role, UUID rolePlayerId)
        {
            super(header);
            this.role = role;
            this.rolePlayerId = rolePlayerId;
        }
        
        public UpdateRole(DuelMessageHeader header, @Nullable PlayerRole role, Player rolePlayer)
        {
            this(header, role, rolePlayer.getUUID());
        }
        
        public UpdateRole(RegistryFriendlyByteBuf buf)
        {
            super(buf);
        }
        
        @Override
        public void encodeMessage(RegistryFriendlyByteBuf buf)
        {
            if(role != null)
            {
                buf.writeBoolean(true);
                DuelMessageUtility.encodePlayerRole(role, buf);
            }
            else
            {
                buf.writeBoolean(false);
            }
            
            buf.writeUUID(rolePlayerId);
        }
        
        @Override
        public void decodeMessage(RegistryFriendlyByteBuf buf)
        {
            if(buf.readBoolean())
            {
                role = DuelMessageUtility.decodePlayerRole(buf);
            }
            else
            {
                role = null;
            }
            
            rolePlayerId = buf.readUUID();
        }
        
        @Override
        public void handleMessage(Player player, IDuelManagerProvider provider)
        {
            Player rolePlayer = player.level().getPlayerByUUID(rolePlayerId);
            
            if(role != null && rolePlayer != null)
            {
                provider.getDuelManager().playerSelectRole(rolePlayer, role);
            }
            else
            {
                provider.getDuelManager().playerCloseContainerClient(rolePlayerId);
            }
        }
    }
    
    public static class UpdateDuelState extends DuelMessage.ClientBaseMessage
    {
        public DuelState duelState;
        
        public UpdateDuelState(DuelMessageHeader header, DuelState duelState)
        {
            super(header);
            this.duelState = duelState;
        }
        
        public UpdateDuelState(RegistryFriendlyByteBuf buf)
        {
            super(buf);
        }
        
        @Override
        public void encodeMessage(RegistryFriendlyByteBuf buf)
        {
            DuelMessageUtility.encodeDuelState(duelState, buf);
        }
        
        @Override
        public void decodeMessage(RegistryFriendlyByteBuf buf)
        {
            duelState = DuelMessageUtility.decodeDuelState(buf);
        }
        
        @Override
        public void handleMessage(Player player, IDuelManagerProvider provider)
        {
            provider.updateDuelState(duelState);
        }
    }
    
    public static class RequestFullUpdate extends DuelMessage.ServerBaseMessage
    {
        public RequestFullUpdate(DuelMessageHeader header)
        {
            super(header);
        }
        
        public RequestFullUpdate(RegistryFriendlyByteBuf buf)
        {
            super(buf);
        }
        
        @Override
        public void encodeMessage(RegistryFriendlyByteBuf buf)
        {
            
        }
        
        @Override
        public void decodeMessage(RegistryFriendlyByteBuf buf)
        {
            
        }
        
        @Override
        public void handleMessage(Player player, IDuelManagerProvider provider)
        {
            provider.getDuelManager().sendAllTo(player);
        }
    }
    
    public static class RequestReady extends DuelMessage.ServerBaseMessage
    {
        public boolean ready;
        
        public RequestReady(DuelMessageHeader header, boolean ready)
        {
            super(header);
            this.ready = ready;
        }
        
        public RequestReady(RegistryFriendlyByteBuf buf)
        {
            super(buf);
        }
        
        @Override
        public void encodeMessage(RegistryFriendlyByteBuf buf)
        {
            buf.writeBoolean(ready);
        }
        
        @Override
        public void decodeMessage(RegistryFriendlyByteBuf buf)
        {
            ready = buf.readBoolean();
        }
        
        @Override
        public void handleMessage(Player player, IDuelManagerProvider provider)
        {
            provider.getDuelManager().requestReady(player, ready);
        }
    }
    
    public static class UpdateReady extends DuelMessage.ClientBaseMessage
    {
        public PlayerRole role;
        public boolean ready;
        
        public UpdateReady(DuelMessageHeader header, PlayerRole role, boolean ready)
        {
            super(header);
            this.role = role;
            this.ready = ready;
        }
        
        public UpdateReady(RegistryFriendlyByteBuf buf)
        {
            super(buf);
        }
        
        @Override
        public void encodeMessage(RegistryFriendlyByteBuf buf)
        {
            DuelMessageUtility.encodePlayerRole(role, buf);
            buf.writeBoolean(ready);
        }
        
        @Override
        public void decodeMessage(RegistryFriendlyByteBuf buf)
        {
            role = DuelMessageUtility.decodePlayerRole(buf);
            ready = buf.readBoolean();
        }
        
        @Override
        public void handleMessage(Player player, IDuelManagerProvider provider)
        {
            provider.getDuelManager().updateReady(role, ready);
        }
    }
    
    public static class SendAvailableDecks extends DuelMessage.ClientBaseMessage
    {
        public List<DeckSource> deckSources;
        
        public SendAvailableDecks(DuelMessageHeader header, List<DeckSource> deckSources)
        {
            super(header);
            this.deckSources = deckSources;
        }
        
        public SendAvailableDecks(RegistryFriendlyByteBuf buf)
        {
            super(buf);
        }
        
        @Override
        public void encodeMessage(RegistryFriendlyByteBuf buf)
        {
            DuelMessageUtility.encodeList(deckSources, buf, DuelMessageUtility::encodeDeckSourceParams);
        }
        
        @Override
        public void decodeMessage(RegistryFriendlyByteBuf buf)
        {
            deckSources = DuelMessageUtility.decodeList(buf, DuelMessageUtility::decodeDeckSourceParams);
        }
        
        @Override
        public void handleMessage(Player player, IDuelManagerProvider provider)
        {
            provider.receiveDeckSources(deckSources);
        }
    }
    
    public static class RequestDeck extends DuelMessage.ServerBaseMessage
    {
        public int index;
        
        public RequestDeck(DuelMessageHeader header, int index)
        {
            super(header);
            this.index = index;
        }
        
        public RequestDeck(RegistryFriendlyByteBuf buf)
        {
            super(buf);
        }
        
        @Override
        public void encodeMessage(RegistryFriendlyByteBuf buf)
        {
            buf.writeInt(index);
        }
        
        @Override
        public void decodeMessage(RegistryFriendlyByteBuf buf)
        {
            index = buf.readInt();
        }
        
        @Override
        public void handleMessage(Player player, IDuelManagerProvider provider)
        {
            provider.getDuelManager().requestDeck(index, player);
        }
    }
    
    public static class SendDeck extends DuelMessage.ClientBaseMessage
    {
        public int index;
        public DeckHolder deck;
        
        public SendDeck(DuelMessageHeader header, int index, DeckHolder deck)
        {
            super(header);
            this.index = index;
            this.deck = deck;
        }
        
        public SendDeck(RegistryFriendlyByteBuf buf)
        {
            super(buf);
        }
        
        @Override
        public void encodeMessage(RegistryFriendlyByteBuf buf)
        {
            buf.writeInt(index);
            DuelMessageUtility.encodeDeckHolder(deck, buf);
        }
        
        @Override
        public void decodeMessage(RegistryFriendlyByteBuf buf)
        {
            index = buf.readInt();
            deck = DuelMessageUtility.decodeDeckHolder(buf);
        }
        
        @Override
        public void handleMessage(Player player, IDuelManagerProvider provider)
        {
            provider.receiveDeck(index, deck);
        }
    }
    
    public static class ChooseDeck extends DuelMessage.ServerBaseMessage
    {
        public int index;
        
        public ChooseDeck(DuelMessageHeader header, int index)
        {
            super(header);
            this.index = index;
        }
        
        public ChooseDeck(RegistryFriendlyByteBuf buf)
        {
            super(buf);
        }
        
        @Override
        public void encodeMessage(RegistryFriendlyByteBuf buf)
        {
            buf.writeInt(index);
        }
        
        @Override
        public void decodeMessage(RegistryFriendlyByteBuf buf)
        {
            index = buf.readInt();
        }
        
        @Override
        public void handleMessage(Player player, IDuelManagerProvider provider)
        {
            provider.getDuelManager().chooseDeck(index, player);
        }
    }
    
    public static class DeckAccepted extends DuelMessage.ClientBaseMessage
    {
        public PlayerRole role;
        
        public DeckAccepted(DuelMessageHeader header, PlayerRole role)
        {
            super(header);
            this.role = role;
        }
        
        public DeckAccepted(RegistryFriendlyByteBuf buf)
        {
            super(buf);
        }
        
        @Override
        public void encodeMessage(RegistryFriendlyByteBuf buf)
        {
            DuelMessageUtility.encodePlayerRole(role, buf);
        }
        
        @Override
        public void decodeMessage(RegistryFriendlyByteBuf buf)
        {
            role = DuelMessageUtility.decodePlayerRole(buf);
        }
        
        @Override
        public void handleMessage(Player player, IDuelManagerProvider provider)
        {
            provider.deckAccepted(role);
        }
    }
    
    public static class RequestDuelAction extends DuelMessage.ServerBaseMessage
    {
        public Action action;
        
        public RequestDuelAction(DuelMessageHeader header, Action action)
        {
            super(header);
            this.action = action;
        }
        
        public RequestDuelAction(RegistryFriendlyByteBuf buf)
        {
            super(buf);
        }
        
        @Override
        public void encodeMessage(RegistryFriendlyByteBuf buf)
        {
            DuelMessageUtility.encodeAction(action, buf);
        }
        
        @Override
        public void decodeMessage(RegistryFriendlyByteBuf buf)
        {
            action = DuelMessageUtility.decodeAction(buf);
        }
        
        @Override
        public void handleMessage(Player player, IDuelManagerProvider provider)
        {
            provider.getDuelManager().receiveActionFrom(player, action);
        }
    }
    
    public static class DuelAction extends DuelMessage.ClientBaseMessage
    {
        //        public PlayerRole source;
        public Action action;
        
        // must be PlayerRole (not ZoneOwner) because Judges will also be able to do stuff
        // player role require?
        public DuelAction(DuelMessageHeader header, /* PlayerRole source,*/ Action action)
        {
            super(header);
            //            this.source = source;
            this.action = action;
        }
        
        public DuelAction(RegistryFriendlyByteBuf buf)
        {
            super(buf);
        }
        
        @Override
        public void encodeMessage(RegistryFriendlyByteBuf buf)
        {
            //encodePlayerRole
            DuelMessageUtility.encodeAction(action, buf);
        }
        
        @Override
        public void decodeMessage(RegistryFriendlyByteBuf buf)
        {
            //decodePlayerRole
            action = DuelMessageUtility.decodeAction(buf);
        }
        
        @Override
        public void handleMessage(Player player, IDuelManagerProvider provider)
        {
            provider.handleAction(action);
        }
    }
    
    public static class AllDuelActions extends DuelMessage.ClientBaseMessage
    {
        public List<Action> actions;
        
        public AllDuelActions(DuelMessageHeader header, List<Action> actions)
        {
            super(header);
            this.actions = actions;
        }
        
        public AllDuelActions(RegistryFriendlyByteBuf buf)
        {
            super(buf);
        }
        
        @Override
        public void encodeMessage(RegistryFriendlyByteBuf buf)
        {
            //encodePlayerRole ?? if this is done in DuelAction class, might need to do it here too
            DuelMessageUtility.encodeActions(actions, buf);
        }
        
        @Override
        public void decodeMessage(RegistryFriendlyByteBuf buf)
        {
            actions = DuelMessageUtility.decodeActions(buf);
        }
        
        @Override
        public void handleMessage(Player player, IDuelManagerProvider provider)
        {
            provider.handleAllActions(actions);
        }
    }
    
    public static class SendMessageToServer extends DuelMessage.ServerBaseMessage
    {
        public Component message;
        
        public SendMessageToServer(DuelMessageHeader header, Component message)
        {
            super(header);
            this.message = message;
        }
        
        public SendMessageToServer(RegistryFriendlyByteBuf buf)
        {
            super(buf);
        }
        
        @Override
        public void encodeMessage(RegistryFriendlyByteBuf buf)
        {
            ComponentSerialization.STREAM_CODEC.encode(buf, message);
        }
        
        @Override
        public void decodeMessage(RegistryFriendlyByteBuf buf)
        {
            message = ComponentSerialization.STREAM_CODEC.decode(buf);
        }
        
        @Override
        public void handleMessage(Player player, IDuelManagerProvider provider)
        {
            provider.getDuelManager().receiveMessageFromClient(player, message);
        }
    }
    
    public static class SendMessageToClient extends DuelMessage.ClientBaseMessage
    {
        public DuelChatMessage message;
        
        public SendMessageToClient(DuelMessageHeader header, DuelChatMessage message)
        {
            super(header);
            this.message = message;
        }
        
        public SendMessageToClient(RegistryFriendlyByteBuf buf)
        {
            super(buf);
        }
        
        @Override
        public void encodeMessage(RegistryFriendlyByteBuf buf)
        {
            DuelMessageUtility.encodeDuelChatMessage(message, buf);
        }
        
        @Override
        public void decodeMessage(RegistryFriendlyByteBuf buf)
        {
            message = DuelMessageUtility.decodeDuelChatMessage(buf);
        }
        
        @Override
        public void handleMessage(Player player, IDuelManagerProvider provider)
        {
            provider.receiveMessage(player, message);
        }
    }
    
    public static class SendAllMessagesToClient extends DuelMessage.ClientBaseMessage
    {
        public List<DuelChatMessage> messages;
        
        public SendAllMessagesToClient(DuelMessageHeader header, List<DuelChatMessage> message)
        {
            super(header);
            messages = message;
        }
        
        public SendAllMessagesToClient(RegistryFriendlyByteBuf buf)
        {
            super(buf);
        }
        
        @Override
        public void encodeMessage(RegistryFriendlyByteBuf buf0)
        {
            DuelMessageUtility.encodeList(messages, buf0, DuelMessageUtility::encodeDuelChatMessage);
        }
        
        @Override
        public void decodeMessage(RegistryFriendlyByteBuf buf0)
        {
            messages = DuelMessageUtility.decodeList(buf0, DuelMessageUtility::decodeDuelChatMessage);
        }
        
        @Override
        public void handleMessage(Player player, IDuelManagerProvider provider)
        {
            for(DuelChatMessage message : messages)
            {
                provider.receiveMessage(player, message);
            }
        }
    }
    
    public static class SendAdmitDefeat extends DuelMessage.ServerBaseMessage
    {
        public SendAdmitDefeat(DuelMessageHeader header)
        {
            super(header);
        }
        
        public SendAdmitDefeat(RegistryFriendlyByteBuf buf)
        {
            super(buf);
        }
        
        @Override
        public void encodeMessage(RegistryFriendlyByteBuf buf)
        {
        
        }
        
        @Override
        public void decodeMessage(RegistryFriendlyByteBuf buf)
        {
        
        }
        
        @Override
        public void handleMessage(Player player, IDuelManagerProvider provider)
        {
            provider.getDuelManager().playerAdmitsDefeat(player);
        }
    }
    
    public static class SendOfferDraw extends DuelMessage.ServerBaseMessage
    {
        public SendOfferDraw(DuelMessageHeader header)
        {
            super(header);
        }
        
        public SendOfferDraw(RegistryFriendlyByteBuf buf)
        {
            super(buf);
        }
        
        @Override
        public void encodeMessage(RegistryFriendlyByteBuf buf)
        {
        
        }
        
        @Override
        public void decodeMessage(RegistryFriendlyByteBuf buf)
        {
        
        }
        
        @Override
        public void handleMessage(Player player, IDuelManagerProvider provider)
        {
            provider.getDuelManager().playerOffersDraw(player);
        }
    }
}
