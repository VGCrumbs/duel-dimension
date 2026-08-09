package de.cas_ual_ty.dueldimension.duel.orichalcos;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The server telling clients that the seal has begun closing on something.
 * <p>
 * One message, sent once, at the start. The client runs the whole six seconds
 * from it rather than being driven frame by frame: a viewer whose connection
 * stutters still sees a seal that finishes when the kill lands, and the server
 * does not spend a packet per tick per watcher on an animation.
 * <p>
 * The target is its network id, not its UUID — the client resolves entities by
 * network id, and the id is what it already has.
 */
public final class OrichalcosMessages
{
    private OrichalcosMessages()
    {
    }

    /**
     * The player saying they have closed the duel and have their character back.
     * <p>
     * Carries nothing: the sender is the message. It only ever SHORTENS the
     * wait — see {@link OrichalcosSouls#playerLeftDuel} — so a client that never
     * sends it, or sends it early, changes nothing it should not.
     */
    public record LeftDuel() implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<LeftDuel> TYPE =
            de.cas_ual_ty.dueldimension.net.DdNetwork.type("orichalcos_left_duel");

        public static final StreamCodec<ByteBuf, LeftDuel> CODEC =
            StreamCodec.unit(new LeftDuel());

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    /**
     * @param entityId   the network id of whatever the seal is closing on
     * @param growTicks  how long the seal takes to reach full size
     * @param holdTicks  the pause between the seal landing and the beam firing
     * @param fadeTicks  how long the beam lingers after the kill
     */
    public record SealBegin(int entityId, int growTicks, int holdTicks, int fadeTicks)
        implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<SealBegin> TYPE =
            de.cas_ual_ty.dueldimension.net.DdNetwork.type("orichalcos_seal");

        // Typed on plain ByteBuf: none of these three touch a registry, and a
        // RegistryFriendlyByteBuf is one, so this still fits where the network
        // registration wants it.
        public static final StreamCodec<ByteBuf, SealBegin> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SealBegin::entityId,
            ByteBufCodecs.VAR_INT, SealBegin::growTicks,
            ByteBufCodecs.VAR_INT, SealBegin::holdTicks,
            ByteBufCodecs.VAR_INT, SealBegin::fadeTicks,
            SealBegin::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }
}
