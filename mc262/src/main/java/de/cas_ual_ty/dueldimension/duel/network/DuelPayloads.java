package de.cas_ual_ty.dueldimension.duel.network;

import de.cas_ual_ty.dueldimension.DuelDimension;
import de.cas_ual_ty.dueldimension.net.DdNetwork;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.function.Function;

/**
 * The envelope every duel message travels in.
 * <p>
 * Forge gave the mod a {@code SimpleChannel} and let it register nineteen
 * message classes against it, each with its own index, encoder, decoder and
 * handler. Fabric has no such channel: a payload <em>type</em> is the unit, and
 * a receiver is registered per type.
 * <p>
 * Rather than mint nineteen payload types — which would put nineteen new ids on
 * the wire and scatter the ordering across nineteen registration calls — this
 * keeps the shape the protocol already had. Two payloads, one per direction,
 * each carrying an index and then the message's own bytes. The index tables
 * below <b>are</b> the Forge registration list, in the same order, and the
 * order is the wire format: adding a message means appending to the end of a
 * list, exactly as {@code index++} did.
 * <p>
 * Split by direction because the base classes already were. A
 * {@code ServerBaseMessage} is client-to-server and a {@code ClientBaseMessage}
 * is the reverse; giving each its own payload means the registry enforces what
 * used to be a naming convention, and a message can no longer be sent the wrong
 * way by accident.
 */
public final class DuelPayloads
{
    /**
     * One message: the class it is, and how to read one back.
     * <p>
     * Paired rather than kept in two lists, so the class used to number an
     * outgoing message and the factory used to rebuild an incoming one cannot
     * disagree.
     */
    private record Entry(Class<? extends DuelMessage> type,
        Function<RegistryFriendlyByteBuf, ? extends DuelMessage> factory)
    {
    }

    private static Entry entry(Class<? extends DuelMessage> type,
        Function<RegistryFriendlyByteBuf, ? extends DuelMessage> factory)
    {
        return new Entry(type, factory);
    }

    /**
     * Client to server, in registration order. <b>Do not reorder</b> — a
     * message's position in this list is the number that goes on the wire, which
     * is exactly what Forge's {@code index++} meant. New messages append.
     */
    private static final List<Entry> TO_SERVER = List.of(
            entry(DuelMessages.SelectRole.class, DuelMessages.SelectRole::new),
            entry(DuelMessages.RequestFullUpdate.class, DuelMessages.RequestFullUpdate::new),
            entry(DuelMessages.RequestReady.class, DuelMessages.RequestReady::new),
            entry(DuelMessages.RequestDeck.class, DuelMessages.RequestDeck::new),
            entry(DuelMessages.ChooseDeck.class, DuelMessages.ChooseDeck::new),
            entry(DuelMessages.RequestDuelAction.class, DuelMessages.RequestDuelAction::new),
            entry(DuelMessages.SendMessageToServer.class, DuelMessages.SendMessageToServer::new),
            entry(DuelMessages.SendAdmitDefeat.class, DuelMessages.SendAdmitDefeat::new),
            entry(DuelMessages.SendOfferDraw.class, DuelMessages.SendOfferDraw::new));

    /** Server to client, same rule. */
    private static final List<Entry> TO_CLIENT = List.of(
            entry(DuelMessages.UpdateRole.class, DuelMessages.UpdateRole::new),
            entry(DuelMessages.UpdateDuelState.class, DuelMessages.UpdateDuelState::new),
            entry(DuelMessages.UpdateReady.class, DuelMessages.UpdateReady::new),
            entry(DuelMessages.SendAvailableDecks.class, DuelMessages.SendAvailableDecks::new),
            entry(DuelMessages.SendDeck.class, DuelMessages.SendDeck::new),
            entry(DuelMessages.DeckAccepted.class, DuelMessages.DeckAccepted::new),
            entry(DuelMessages.DuelAction.class, DuelMessages.DuelAction::new),
            entry(DuelMessages.AllDuelActions.class, DuelMessages.AllDuelActions::new),
            entry(DuelMessages.SendMessageToClient.class, DuelMessages.SendMessageToClient::new),
            entry(DuelMessages.SendAllMessagesToClient.class, DuelMessages.SendAllMessagesToClient::new));

    private static int indexOf(List<Entry> table, DuelMessage message)
    {
        for(int i = 0; i < table.size(); i++)
        {
            if(table.get(i).type() == message.getClass())
            {
                return i;
            }
        }
        throw new IllegalArgumentException("Duel message " + message.getClass().getName()
            + " is not registered for this direction");
    }

    /** Client to server. */
    public record ToServer(DuelMessage message) implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<ToServer> TYPE = DdNetwork.type("duel_to_server");

        public static final StreamCodec<RegistryFriendlyByteBuf, ToServer> CODEC =
            CustomPacketPayload.codec(
                (payload, buf) ->
                {
                    buf.writeVarInt(indexOf(TO_SERVER, payload.message()));
                    payload.message().encode(buf);
                },
                buf -> new ToServer(TO_SERVER.get(buf.readVarInt()).factory().apply(buf)));

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    /** Server to client. */
    public record ToClient(DuelMessage message) implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<ToClient> TYPE = DdNetwork.type("duel_to_client");

        public static final StreamCodec<RegistryFriendlyByteBuf, ToClient> CODEC =
            CustomPacketPayload.codec(
                (payload, buf) ->
                {
                    buf.writeVarInt(indexOf(TO_CLIENT, payload.message()));
                    payload.message().encode(buf);
                },
                buf -> new ToClient(TO_CLIENT.get(buf.readVarInt()).factory().apply(buf)));

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    private DuelPayloads()
    {
    }

    /** Both directions, on both sides. Called from the common entry point. */
    public static void register()
    {
        PayloadTypeRegistry.serverboundPlay().register(ToServer.TYPE, ToServer.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ToClient.TYPE, ToClient.CODEC);
    }

    /**
     * The server's receiver.
     * <p>
     * Everything Forge's {@code handle} did, in the one place it now belongs:
     * the player comes from the connection rather than from a context, the
     * header says what the message is addressed to, and the work happens on the
     * server thread — which Fabric guarantees for this callback, so the
     * {@code enqueueWork} that wrapped it has nothing left to do.
     */
    public static void registerServerHandlers()
    {
        ServerPlayNetworking.registerGlobalReceiver(ToServer.TYPE, (payload, context) ->
        {
            ServerPlayer player = context.player();
            DuelMessage message = payload.message();
            IDuelManagerProvider provider = message.getDecodedHeader().getDuelManager(player);
            if(provider == null)
            {
                // The block or entity the message names is gone. Dropping it is
                // right: a duel that no longer exists cannot be told anything.
                DuelDimension.debug("Duel message for a duel that is no longer there");
                return;
            }
            message.handleMessage(player, provider);
        });
    }

    /** Sends to one player. Forge said this with {@code PacketDistributor.PLAYER}. */
    public static void send(ServerPlayer player, DuelMessage message)
    {
        ServerPlayNetworking.send(player, new ToClient(message));
    }

    /** The client's half, from the client entry point. */
    public static void registerClientHandlers()
    {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
            ToClient.TYPE, (payload, context) ->
            {
                Player player = context.player();
                DuelMessage message = payload.message();
                IDuelManagerProvider provider = message.getDecodedHeader().getDuelManager(player);
                if(provider == null)
                {
                    return;
                }
                message.handleMessage(player, provider);
            });
    }
}
