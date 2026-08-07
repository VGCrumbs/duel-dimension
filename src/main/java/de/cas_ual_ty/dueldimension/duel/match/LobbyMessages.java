package de.cas_ual_ty.dueldimension.duel.match;

import de.cas_ual_ty.dueldimension.DuelDimension;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import de.cas_ual_ty.dueldimension.net.DdNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

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
        List<String> banlistNames, List<String> problems) implements CustomPacketPayload
    {
        /** Names this message on the wire. */
        public static final CustomPacketPayload.Type<OpenLobby> TYPE =
            DdNetwork.type("lobby_open_lobby");

        /**
         * Built from the encode/decode pair below rather than rewritten
         * as a composite: those two methods ARE the wire format, and
         * retyping a format is how a port quietly changes one.
         */
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenLobby> CODEC =
            CustomPacketPayload.codec(OpenLobby::encode, OpenLobby::decode);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }

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

        /*
         * The Forge handler. Fabric registers a receiver by direction
         * and hands it the payload and the sender, so this body belongs
         * at the registration site. Kept here until it moves, because
         * it is the record of what this message is for.
         * public static void handle(OpenLobby message, Supplier<NetworkEvent.Context> context)
         * {
         * context.get().enqueueWork(() -> DuelDimension.proxy.openDuelLobby(message));
         * context.get().setPacketHandled(true);
         * }
         */
    }

    /** Server to client: the room is gone, because the duel started or someone left. */
    public record CloseLobby() implements CustomPacketPayload
    {
        /** Names this message on the wire. */
        public static final CustomPacketPayload.Type<CloseLobby> TYPE =
            DdNetwork.type("lobby_close_lobby");

        /**
         * Built from the encode/decode pair below rather than rewritten
         * as a composite: those two methods ARE the wire format, and
         * retyping a format is how a port quietly changes one.
         */
        public static final StreamCodec<RegistryFriendlyByteBuf, CloseLobby> CODEC =
            CustomPacketPayload.codec(CloseLobby::encode, CloseLobby::decode);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }

        public static void encode(CloseLobby message, FriendlyByteBuf buffer)
        {
        }

        public static CloseLobby decode(FriendlyByteBuf buffer)
        {
            return new CloseLobby();
        }

        /*
         * The Forge handler. Fabric registers a receiver by direction
         * and hands it the payload and the sender, so this body belongs
         * at the registration site. Kept here until it moves, because
         * it is the record of what this message is for.
         * public static void handle(CloseLobby message, Supplier<NetworkEvent.Context> context)
         * {
         * context.get().enqueueWork(DuelDimension.proxy::closeDuelLobby);
         * context.get().setPacketHandled(true);
         * }
         */
    }

    /** Client to server: change the settings. Ignored unless the sender hosts. */
    public record Configure(MatchConfig config) implements CustomPacketPayload
    {
        /** Names this message on the wire. */
        public static final CustomPacketPayload.Type<Configure> TYPE =
            DdNetwork.type("lobby_configure");

        /**
         * Built from the encode/decode pair below rather than rewritten
         * as a composite: those two methods ARE the wire format, and
         * retyping a format is how a port quietly changes one.
         */
        public static final StreamCodec<RegistryFriendlyByteBuf, Configure> CODEC =
            CustomPacketPayload.codec(Configure::encode, Configure::decode);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }

        public static void encode(Configure message, FriendlyByteBuf buffer)
        {
            message.config().write(buffer);
        }

        public static Configure decode(FriendlyByteBuf buffer)
        {
            return new Configure(MatchConfig.read(buffer));
        }

        /*
         * The Forge handler. Fabric registers a receiver by direction
         * and hands it the payload and the sender, so this body belongs
         * at the registration site. Kept here until it moves, because
         * it is the record of what this message is for.
         * public static void handle(Configure message, Supplier<NetworkEvent.Context> context)
         * {
         * server(context, player -> DuelLobby.configure(player, message.config()));
         * }
         */
    }

    /** Client to server: I am ready, or I am not any more. */
    public record Ready(boolean ready) implements CustomPacketPayload
    {
        /** Names this message on the wire. */
        public static final CustomPacketPayload.Type<Ready> TYPE =
            DdNetwork.type("lobby_ready");

        /**
         * Built from the encode/decode pair below rather than rewritten
         * as a composite: those two methods ARE the wire format, and
         * retyping a format is how a port quietly changes one.
         */
        public static final StreamCodec<RegistryFriendlyByteBuf, Ready> CODEC =
            CustomPacketPayload.codec(Ready::encode, Ready::decode);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }

        public static void encode(Ready message, FriendlyByteBuf buffer)
        {
            buffer.writeBoolean(message.ready());
        }

        public static Ready decode(FriendlyByteBuf buffer)
        {
            return new Ready(buffer.readBoolean());
        }

        /*
         * The Forge handler. Fabric registers a receiver by direction
         * and hands it the payload and the sender, so this body belongs
         * at the registration site. Kept here until it moves, because
         * it is the record of what this message is for.
         * public static void handle(Ready message, Supplier<NetworkEvent.Context> context)
         * {
         * server(context, player -> DuelLobby.ready(player, message.ready()));
         * }
         */
    }

    /** Client to server: I am leaving. Cancels the match for both. */
    public record Leave() implements CustomPacketPayload
    {
        /** Names this message on the wire. */
        public static final CustomPacketPayload.Type<Leave> TYPE =
            DdNetwork.type("lobby_leave");

        /**
         * Built from the encode/decode pair below rather than rewritten
         * as a composite: those two methods ARE the wire format, and
         * retyping a format is how a port quietly changes one.
         */
        public static final StreamCodec<RegistryFriendlyByteBuf, Leave> CODEC =
            CustomPacketPayload.codec(Leave::encode, Leave::decode);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }

        public static void encode(Leave message, FriendlyByteBuf buffer)
        {
        }

        public static Leave decode(FriendlyByteBuf buffer)
        {
            return new Leave();
        }

        /*
         * The Forge handler. Fabric registers a receiver by direction
         * and hands it the payload and the sender, so this body belongs
         * at the registration site. Kept here until it moves, because
         * it is the record of what this message is for.
         * public static void handle(Leave message, Supplier<NetworkEvent.Context> context)
         * {
         * server(context, DuelLobby::leave);
         * }
         */
    }

    // The Forge `server(context, change)` helper is gone: it wrapped
    // every handler in enqueueWork and getSender, and DdNetwork.onServer
    // does both, so there is nothing left for it to do.

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
