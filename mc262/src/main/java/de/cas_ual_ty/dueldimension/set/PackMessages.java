package de.cas_ual_ty.dueldimension.set;

import de.cas_ual_ty.dueldimension.DuelDimension;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import de.cas_ual_ty.dueldimension.net.DdNetwork;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Tells a client what came out of a pack, so it can be revealed rather than
 * simply appearing.
 * <p>
 * The pull already happens server side when the pack is unsealed — the cards
 * are rolled and written onto the opened-pack stack — so this reports what was
 * drawn rather than deciding it. A client that ignored or forged this packet
 * would change nothing about what it actually received.
 */
public final class PackMessages
{
    private PackMessages()
    {
    }

    /**
     * @param setName  the pack, for the header
     * @param codes    each card pulled, in pull order
     * @param rarities each card's rarity, parallel to {@code codes}
     */
    public record OpenPack(String setName, List<Integer> codes, List<String> rarities) implements CustomPacketPayload
    {
        /** Names this message on the wire. */
        public static final CustomPacketPayload.Type<OpenPack> TYPE =
            DdNetwork.type("pack_open_pack");

        /**
         * Built from the encode/decode pair below rather than rewritten
         * as a composite: those two methods ARE the wire format, and
         * retyping a format is how a port quietly changes one.
         */
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenPack> CODEC =
            CustomPacketPayload.codec(OpenPack::encode, OpenPack::decode);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }

        public static void encode(OpenPack message, FriendlyByteBuf buffer)
        {
            buffer.writeUtf(message.setName(), 128);
            buffer.writeVarInt(message.codes().size());
            for(int i = 0; i < message.codes().size(); i++)
            {
                buffer.writeVarInt(message.codes().get(i));
                buffer.writeUtf(i < message.rarities().size() ? message.rarities().get(i) : "", 64);
            }
        }

        public static OpenPack decode(FriendlyByteBuf buffer)
        {
            String setName = buffer.readUtf(128);
            int count = buffer.readVarInt();
            List<Integer> codes = new ArrayList<>(count);
            List<String> rarities = new ArrayList<>(count);
            for(int i = 0; i < count; i++)
            {
                codes.add(buffer.readVarInt());
                rarities.add(buffer.readUtf(64));
            }
            return new OpenPack(setName, codes, rarities);
        }

        /*
         * The Forge handler. Fabric registers a receiver by direction
         * and hands it the payload and the sender, so this body belongs
         * at the registration site. Kept here until it moves, because
         * it is the record of what this message is for.
         * public static void handle(OpenPack message, Supplier<NetworkEvent.Context> context)
         * {
         * context.get().enqueueWork(() ->
         * DuelDimension.proxy.openPackReveal(message.setName(), message.codes(), message.rarities()));
         * context.get().setPacketHandled(true);
         * }
         */
    }
}
