package de.cas_ual_ty.dueldimension.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The server telling one client to start or stop preloading card art.
 * <p>
 * The work is the client's alone — it downloads and scales the pictures it is
 * going to draw — so this carries nothing but the instruction. The command lives
 * on the server only because that is where the {@code /dueldimension} tree is.
 */
public final class PreloadMessages
{
    private PreloadMessages()
    {
    }

    /** @param start true to begin, false to stop early */
    public record Preload(boolean start) implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<Preload> TYPE =
            DdNetwork.type("preload_control");

        public static final StreamCodec<ByteBuf, Preload> CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, Preload::start, Preload::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }
}
