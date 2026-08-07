package de.cas_ual_ty.dueldimension.net;

import de.cas_ual_ty.dueldimension.DuelDimension;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * How this mod talks over the wire.
 * <p>
 * Forge gave every mod a {@code SimpleChannel} with a hand-assigned integer per
 * message and a {@code NetworkEvent.Context} that answered
 * {@code getSender()} on both sides. Minecraft has since grown its own version
 * of the same idea and it is stricter in the ways that matter: a message is a
 * {@link CustomPacketPayload} with an {@link CustomPacketPayload.Type} naming
 * it, a {@link StreamCodec} that reads and writes it, and a registration that
 * says which <em>direction</em> it may travel. There are no ids to keep in
 * step, and a client-to-server message registered as server-to-client is
 * refused rather than mis-parsed.
 * <p>
 * The mod's existing {@code encode}/{@code decode} pairs port straight across:
 * their signatures already match what {@link CustomPacketPayload#codec} wants,
 * so each message needs a type, a codec built from the methods it already has,
 * and one line saying which way it goes.
 */
public final class DdNetwork
{
    private DdNetwork()
    {
    }

    /** Names a message. The path is what appears on the wire. */
    public static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String name)
    {
        return new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath(DuelDimension.MOD_ID, name));
    }

    /**
     * Everything the mod sends, registered before anything can send it.
     * <p>
     * Direction is declared per message rather than per channel, which is the
     * substantive difference from the Forge version: {@code serverboundPlay}
     * is what a client may send, {@code clientboundPlay} what a server may. Registering one in
     * the wrong list is a connection error rather than a silent misread.
     */
    public static void register()
    {
        ProfilePayloads.register();
    }

    /** Registers the server's side of every message a client may send. */
    public static void registerServerHandlers()
    {
        ProfilePayloads.registerServerHandlers();
    }

    // ---- helpers the payload classes share ----

    /**
     * Registers a client-to-server message and its handler in one place, so a
     * message cannot be declared without someone to receive it.
     */
    public static <T extends CustomPacketPayload> void serverbound(
        CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec)
    {
        PayloadTypeRegistry.serverboundPlay().register(type, codec);
    }

    public static <T extends CustomPacketPayload> void clientbound(
        CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec)
    {
        PayloadTypeRegistry.clientboundPlay().register(type, codec);
    }

    /**
     * A handler that runs on the server thread with the sending player.
     * <p>
     * Replaces the Forge helper that wrapped every server-side handler in
     * {@code enqueueWork} and {@code getSender()}. Fabric hands the player
     * straight to the handler and has already moved to the server thread, so
     * the wrapper is thinner -- but it stays, because the one thing worth
     * keeping from the old one is that no handler can forget either step.
     */
    public interface ServerHandler<T extends CustomPacketPayload>
    {
        void handle(T message, ServerPlayer player);
    }

    public static <T extends CustomPacketPayload> void onServer(
        CustomPacketPayload.Type<T> type, ServerHandler<T> handler)
    {
        ServerPlayNetworking.registerGlobalReceiver(type,
            (payload, context) -> handler.handle(payload, context.player()));
    }
}
