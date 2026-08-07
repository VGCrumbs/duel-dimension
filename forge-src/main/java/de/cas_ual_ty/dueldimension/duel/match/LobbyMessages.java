package de.cas_ual_ty.dueldimension.duel.match;

import de.cas_ual_ty.dueldimension.DuelDimension;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The lobby's traffic.
 * <p>
 * Shaped like the shop's and the deck editor's: the server sends the room's
 * whole state and the client asks for changes. Nothing here decides anything —
 * a proposed configuration is sanitised on arrival, and a player who is not the
 * host simply has their proposal ignored.
 */
public final class LobbyMessages
{
    private static final int NAME_LIMIT = 32;
    /** More banlists than any install carries, so a real list always fits. */
    private static final int LIST_LIMIT = 64;
    private static final int PROBLEM_LIMIT = 16;

    private LobbyMessages()
    {
    }

    /**
     * Server to client: this is the room.
     *
     * @param problems why THIS player's deck will not do, empty if it will
     */
    public record OpenLobby(MatchConfig config, boolean host, String hostName, String guestName,
        boolean hostReady, boolean guestReady, List<String> banlistIds,
        List<String> banlistNames, List<String> problems)
    {
        public static void encode(OpenLobby message, FriendlyByteBuf buffer)
        {
            message.config().write(buffer);
            buffer.writeBoolean(message.host());
            buffer.writeUtf(message.hostName(), NAME_LIMIT);
            buffer.writeUtf(message.guestName(), NAME_LIMIT);
            buffer.writeBoolean(message.hostReady());
            buffer.writeBoolean(message.guestReady());
            writeStrings(buffer, message.banlistIds(), LIST_LIMIT);
            writeStrings(buffer, message.banlistNames(), LIST_LIMIT);
            writeStrings(buffer, message.problems(), PROBLEM_LIMIT);
        }

        public static OpenLobby decode(FriendlyByteBuf buffer)
        {
            return new OpenLobby(MatchConfig.read(buffer), buffer.readBoolean(),
                buffer.readUtf(NAME_LIMIT), buffer.readUtf(NAME_LIMIT),
                buffer.readBoolean(), buffer.readBoolean(),
                readStrings(buffer, LIST_LIMIT), readStrings(buffer, LIST_LIMIT),
                readStrings(buffer, PROBLEM_LIMIT));
        }

        public static void handle(OpenLobby message, Supplier<NetworkEvent.Context> context)
        {
            context.get().enqueueWork(() -> DuelDimension.proxy.openDuelLobby(message));
            context.get().setPacketHandled(true);
        }
    }

    /** Server to client: the room is gone, because the duel started or someone left. */
    public record CloseLobby()
    {
        public static void encode(CloseLobby message, FriendlyByteBuf buffer)
        {
        }

        public static CloseLobby decode(FriendlyByteBuf buffer)
        {
            return new CloseLobby();
        }

        public static void handle(CloseLobby message, Supplier<NetworkEvent.Context> context)
        {
            context.get().enqueueWork(DuelDimension.proxy::closeDuelLobby);
            context.get().setPacketHandled(true);
        }
    }

    /** Client to server: change the settings. Ignored unless the sender hosts. */
    public record Configure(MatchConfig config)
    {
        public static void encode(Configure message, FriendlyByteBuf buffer)
        {
            message.config().write(buffer);
        }

        public static Configure decode(FriendlyByteBuf buffer)
        {
            return new Configure(MatchConfig.read(buffer));
        }

        public static void handle(Configure message, Supplier<NetworkEvent.Context> context)
        {
            server(context, player -> DuelLobby.configure(player, message.config()));
        }
    }

    /** Client to server: I am ready, or I am not any more. */
    public record Ready(boolean ready)
    {
        public static void encode(Ready message, FriendlyByteBuf buffer)
        {
            buffer.writeBoolean(message.ready());
        }

        public static Ready decode(FriendlyByteBuf buffer)
        {
            return new Ready(buffer.readBoolean());
        }

        public static void handle(Ready message, Supplier<NetworkEvent.Context> context)
        {
            server(context, player -> DuelLobby.ready(player, message.ready()));
        }
    }

    /** Client to server: I am leaving. Cancels the match for both. */
    public record Leave()
    {
        public static void encode(Leave message, FriendlyByteBuf buffer)
        {
        }

        public static Leave decode(FriendlyByteBuf buffer)
        {
            return new Leave();
        }

        public static void handle(Leave message, Supplier<NetworkEvent.Context> context)
        {
            server(context, DuelLobby::leave);
        }
    }

    private static void server(Supplier<NetworkEvent.Context> context,
        java.util.function.Consumer<ServerPlayer> action)
    {
        context.get().enqueueWork(() ->
        {
            ServerPlayer player = context.get().getSender();
            if(player != null)
            {
                action.accept(player);
            }
        });
        context.get().setPacketHandled(true);
    }

    private static void writeStrings(FriendlyByteBuf buffer, List<String> values, int limit)
    {
        int count = Math.min(values.size(), limit);
        buffer.writeVarInt(count);
        for(int i = 0; i < count; i++)
        {
            buffer.writeUtf(values.get(i), 128);
        }
    }

    private static List<String> readStrings(FriendlyByteBuf buffer, int limit)
    {
        int count = buffer.readVarInt();
        if(count < 0 || count > limit)
        {
            // A length is the one field that makes a decoder allocate, so it is
            // bounded before it is believed.
            throw new IllegalArgumentException("String list length out of range: " + count);
        }
        List<String> values = new ArrayList<>(count);
        for(int i = 0; i < count; i++)
        {
            values.add(buffer.readUtf(128));
        }
        return values;
    }
}
