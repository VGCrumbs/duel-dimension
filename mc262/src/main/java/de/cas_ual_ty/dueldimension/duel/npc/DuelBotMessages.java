package de.cas_ual_ty.dueldimension.duel.npc;

import de.cas_ual_ty.dueldimension.net.DdNetwork;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Asking a player which program a Duel Bot should run, and hearing back.
 * <p>
 * The same shape as {@link DuelistChallengeMessages} and for the same reason: a
 * duel against an NPC starts the moment it is asked for, so the question has to
 * be put at the click.
 * <p>
 * <b>The player's own deck names travel with the question.</b> The bot can play
 * a deck belonging to the challenger, and the client cannot offer a list it does
 * not have. This is not a hidden-information leak in the sense the mod cares
 * about -- these are the challenger's OWN decks going only to the challenger,
 * by name and not by contents, and the server re-checks the name it gets back
 * against that same profile before anything is loaded.
 */
public final class DuelBotMessages
{
    private DuelBotMessages()
    {
    }

    /** How many of the player's decks the question will carry. */
    private static final int MAX_DECKS = 128;

    /** Server to client: you clicked a bot wearing a disk, so which program? */
    public record OfferProgram(int botId, String botName, List<String> ownDecks)
        implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<OfferProgram> TYPE =
            DdNetwork.type("duel_bot_offer");

        public static final StreamCodec<RegistryFriendlyByteBuf, OfferProgram> CODEC =
            CustomPacketPayload.codec(OfferProgram::encode, OfferProgram::decode);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }

        public static void encode(OfferProgram message, RegistryFriendlyByteBuf buffer)
        {
            buffer.writeVarInt(message.botId());
            buffer.writeUtf(message.botName(), 64);
            List<String> decks = message.ownDecks();
            int count = Math.min(decks.size(), MAX_DECKS);
            buffer.writeVarInt(count);
            for(int i = 0; i < count; i++)
            {
                buffer.writeUtf(decks.get(i), 64);
            }
        }

        public static OfferProgram decode(RegistryFriendlyByteBuf buffer)
        {
            int id = buffer.readVarInt();
            String name = buffer.readUtf(64);
            // Bounded before it is used as a size: a count is data, and an
            // unbounded one from the wire is an allocation somebody else chose.
            int count = Math.min(buffer.readVarInt(), MAX_DECKS);
            List<String> decks = new ArrayList<>(count);
            for(int i = 0; i < count; i++)
            {
                decks.add(buffer.readUtf(64));
            }
            return new OfferProgram(id, name, List.copyOf(decks));
        }
    }

    /**
     * Client to server: run this program.
     *
     * @param program one of {@link DuelBotEntity#STARTER},
     *                {@link DuelBotEntity#STRUCTURE} or
     *                {@link DuelBotEntity#CUSTOM}
     * @param deckId  which deck within it; empty to let the server choose, which
     *                is what the starter and structure buttons do
     */
    public record ChooseProgram(int botId, String program, String deckId, boolean destinyDraw)
        implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<ChooseProgram> TYPE =
            DdNetwork.type("duel_bot_choose");

        public static final StreamCodec<RegistryFriendlyByteBuf, ChooseProgram> CODEC =
            CustomPacketPayload.codec(ChooseProgram::encode, ChooseProgram::decode);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }

        public static void encode(ChooseProgram message, RegistryFriendlyByteBuf buffer)
        {
            buffer.writeVarInt(message.botId());
            buffer.writeUtf(message.program(), 32);
            buffer.writeUtf(message.deckId() == null ? "" : message.deckId(), 64);
            buffer.writeBoolean(message.destinyDraw());
        }

        public static ChooseProgram decode(RegistryFriendlyByteBuf buffer)
        {
            return new ChooseProgram(buffer.readVarInt(), buffer.readUtf(32),
                buffer.readUtf(64), buffer.readBoolean());
        }
    }
}
