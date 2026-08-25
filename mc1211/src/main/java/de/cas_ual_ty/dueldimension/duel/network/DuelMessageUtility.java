package de.cas_ual_ty.dueldimension.duel.network;

import de.cas_ual_ty.dueldimension.card.CardHolderNbt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.ComponentSerialization;
import de.cas_ual_ty.dueldimension.DdDuelRegistries;
import de.cas_ual_ty.dueldimension.DuelDimension;
import de.cas_ual_ty.dueldimension.card.CardHolder;
import de.cas_ual_ty.dueldimension.deckbox.DeckHolder;
import de.cas_ual_ty.dueldimension.duel.*;
import de.cas_ual_ty.dueldimension.duel.action.Action;
import de.cas_ual_ty.dueldimension.duel.action.ActionType;
import de.cas_ual_ty.dueldimension.duel.playfield.CardPosition;
import de.cas_ual_ty.dueldimension.duel.playfield.DuelCard;
import de.cas_ual_ty.dueldimension.duel.playfield.ZoneOwner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class DuelMessageUtility
{
    public static void encodeHeader(DuelMessageHeader header, RegistryFriendlyByteBuf buf)
    {
        // Forge called this "unsafe" because a registry id is only meaningful
        // if both ends numbered the registry identically. That held there and
        // holds here for the same reason: the entries are registered in a fixed
        // order from a static initialiser, so the same jar always agrees with
        // itself. The vanilla registry gives the same integer.
        buf.writeVarInt(DdDuelRegistries.HEADERS.getId(header.type));
        header.writeToBuf(buf);
    }
    
    public static DuelMessageHeader decodeHeader(RegistryFriendlyByteBuf buf)
    {
        DuelMessageHeader header =
            DdDuelRegistries.HEADERS.byId(buf.readVarInt()).createHeader();
        header.readFromBuf(buf);
        return header;
    }
    
    public static <U> void encodeList(List<U> list, RegistryFriendlyByteBuf buf, BiConsumer<U, RegistryFriendlyByteBuf> encoder)
    {
        buf.writeInt(list.size());
        
        for(U u : list)
        {
            encoder.accept(u, buf);
        }
    }
    
    public static <U> List<U> decodeList(RegistryFriendlyByteBuf buf, Function<RegistryFriendlyByteBuf, U> decoder, Function<Integer, List<U>> listCreator)
    {
        int size = buf.readInt();
        List<U> list = listCreator.apply(size);
        
        for(int i = 0; i < size; ++i)
        {
            list.add(decoder.apply(buf));
        }
        
        return list;
    }
    
    public static <U> List<U> decodeList(RegistryFriendlyByteBuf buf, Function<RegistryFriendlyByteBuf, U> decoder)
    {
        return DuelMessageUtility.decodeList(buf, decoder, (size) -> new ArrayList<>(size));
    }
    
    public static void encodeActions(List<Action> actions, RegistryFriendlyByteBuf buf)
    {
        DuelMessageUtility.encodeList(actions, buf, DuelMessageUtility::encodeAction);
    }
    
    public static List<Action> decodeActions(RegistryFriendlyByteBuf buf)
    {
        return DuelMessageUtility.decodeList(buf, DuelMessageUtility::decodeAction, (size) -> new LinkedList<>());
    }
    
    public static void encodeAction(Action action, RegistryFriendlyByteBuf buf)
    {
        DuelMessageUtility.encodeActionType(action.actionType, buf);
        action.writeToBuf(buf);
    }
    
    public static Action decodeAction(RegistryFriendlyByteBuf buf)
    {
        ActionType actionType = DuelMessageUtility.decodeActionType(buf);
        return actionType.factory.create(actionType, buf);
    }
    
    public static void encodeActionType(ActionType type, RegistryFriendlyByteBuf buf)
    {
        buf.writeVarInt(DdDuelRegistries.ACTION_TYPES.getId(type));
    }
    
    public static ActionType decodeActionType(RegistryFriendlyByteBuf buf)
    {
        return DdDuelRegistries.ACTION_TYPES.byId(buf.readVarInt());
    }
    
    public static void encodeDuelState(DuelState duelState, RegistryFriendlyByteBuf buf)
    {
        buf.writeByte(duelState.getIndex());
    }
    
    public static DuelState decodeDuelState(RegistryFriendlyByteBuf buf)
    {
        return DuelState.getFromIndex(buf.readByte());
    }
    
    public static void encodePlayerId(Player player, RegistryFriendlyByteBuf buf)
    {
        buf.writeInt(player.getId());
    }
    
    public static int decodePlayerId(RegistryFriendlyByteBuf buf)
    {
        return buf.readInt();
    }
    
    public static void encodePlayerRole(PlayerRole role, RegistryFriendlyByteBuf buf)
    {
        buf.writeByte(role.getIndex());
    }
    
    public static PlayerRole decodePlayerRole(RegistryFriendlyByteBuf buf)
    {
        return PlayerRole.getFromIndex(buf.readByte());
    }
    
    public static void encodeZoneOwner(ZoneOwner owner, RegistryFriendlyByteBuf buf)
    {
        buf.writeByte(owner.getIndex());
    }
    
    public static ZoneOwner decodeZoneOwner(RegistryFriendlyByteBuf buf)
    {
        return ZoneOwner.getFromIndex(buf.readByte());
    }
    
    public static void encodeCardHolder(@Nullable CardHolder card, RegistryFriendlyByteBuf buf)
    {
        if(card != null)
        {
            buf.writeBoolean(true);
            CompoundTag nbt = new CompoundTag();
            CardHolderNbt.write(card, nbt);
            buf.writeNbt(nbt);
        }
        else
        {
            buf.writeBoolean(false);
        }
    }
    
    public static CardHolder decodeCardHolder(RegistryFriendlyByteBuf buf)
    {
        return buf.readBoolean() ? CardHolderNbt.read(buf.readNbt()) : null;
    }
    
    public static void encodeDeckHolder(DeckHolder deck, RegistryFriendlyByteBuf buf)
    {
        DuelMessageUtility.encodeList(deck.getMainDeck(), buf, (card, buf1) -> DuelMessageUtility.encodeCardHolder(card, buf1));
        DuelMessageUtility.encodeList(deck.getExtraDeck(), buf, (card, buf1) -> DuelMessageUtility.encodeCardHolder(card, buf1));
        DuelMessageUtility.encodeList(deck.getSideDeck(), buf, (card, buf1) -> DuelMessageUtility.encodeCardHolder(card, buf1));
    }
    
    public static DeckHolder decodeDeckHolder(RegistryFriendlyByteBuf buf)
    {
        List<CardHolder> mainDeck = DuelMessageUtility.decodeList(buf, (buf1) -> DuelMessageUtility.decodeCardHolder(buf1));
        List<CardHolder> extraDeck = DuelMessageUtility.decodeList(buf, (buf1) -> DuelMessageUtility.decodeCardHolder(buf1));
        List<CardHolder> sideDeck = DuelMessageUtility.decodeList(buf, (buf1) -> DuelMessageUtility.decodeCardHolder(buf1));
        
        return new DeckHolder(mainDeck, extraDeck, sideDeck);
    }
    
    public static void encodeCardPosition(CardPosition cardPosition, RegistryFriendlyByteBuf buf)
    {
        buf.writeByte(cardPosition.getIndex());
    }
    
    public static CardPosition decodeCardPosition(RegistryFriendlyByteBuf buf)
    {
        return CardPosition.getFromIndex(buf.readByte());
    }
    
    public static void encodeDuelCard(DuelCard card, RegistryFriendlyByteBuf buf)
    {
        DuelMessageUtility.encodeCardHolder(card.getCardHolder(), buf);
        buf.writeBoolean(card.getIsToken());
        DuelMessageUtility.encodeCardPosition(card.getCardPosition(), buf);
        DuelMessageUtility.encodeZoneOwner(card.getOwner(), buf);
    }
    
    public static DuelCard decodeDuelCard(RegistryFriendlyByteBuf buf)
    {
        return new DuelCard(DuelMessageUtility.decodeCardHolder(buf), buf.readBoolean(), DuelMessageUtility.decodeCardPosition(buf), DuelMessageUtility.decodeZoneOwner(buf));
    }
    
    public static void encodeDuelChatMessage(DuelChatMessage message, RegistryFriendlyByteBuf buf)
    {
        ComponentSerialization.STREAM_CODEC.encode(buf, message.message);
        ComponentSerialization.STREAM_CODEC.encode(buf, message.playerName);
        DuelMessageUtility.encodePlayerRole(message.sourceRole, buf);
        buf.writeBoolean(message.isAnnouncement);
    }
    
    public static DuelChatMessage decodeDuelChatMessage(RegistryFriendlyByteBuf buf)
    {
        return new DuelChatMessage(ComponentSerialization.STREAM_CODEC.decode(buf), ComponentSerialization.STREAM_CODEC.decode(buf), DuelMessageUtility.decodePlayerRole(buf), buf.readBoolean());
    }
    
    public static void encodeDeckSourceParams(DeckSource deck, RegistryFriendlyByteBuf buf)
    {
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, deck.source);
        ComponentSerialization.STREAM_CODEC.encode(buf, deck.name);
    }
    
    public static DeckSource decodeDeckSourceParams(RegistryFriendlyByteBuf buf)
    {
        return new DeckSource(null, ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), ComponentSerialization.STREAM_CODEC.decode(buf));
    }
    
    public static void encodePhase(DuelPhase phase, RegistryFriendlyByteBuf buf)
    {
        buf.writeByte(phase.getIndex());
    }
    
    public static DuelPhase decodePhase(RegistryFriendlyByteBuf buf)
    {
        return DuelPhase.getFromIndex(buf.readByte());
    }
}
