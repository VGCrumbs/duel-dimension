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
    /**
     * @param fresh per card, whether the collection did NOT already hold it.
     *              Decided server side before the cards were added, because
     *              afterwards the answer is always "it did".
     */
    /**
     * @param sources per card, the code of the set it actually came out of.
     *                <p>
     *                For an ordinary pack this is the product's own code
     *                repeated, and the reveal ignores it. For a TIN it is the
     *                booster each card was in — a tin is five real packs in a
     *                box, and this is the only thing that says which. The
     *                client cannot work it out: the pull happened server side
     *                and a card carries no record of the pack it came from.
     */
    public record OpenPack(String setName, List<Integer> codes, List<String> rarities,
        List<Boolean> fresh, List<String> sources) implements CustomPacketPayload
    {
        public OpenPack
        {
            codes = codes == null ? List.of() : List.copyOf(codes);
            rarities = rarities == null ? List.of() : List.copyOf(rarities);
            fresh = fresh == null ? List.of() : List.copyOf(fresh);
            sources = sources == null ? List.of() : List.copyOf(sources);
        }

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
                // Defaulting to false rather than to true: a reveal that forgot
                // to work it out should mark nothing, not everything.
                buffer.writeBoolean(i < message.fresh().size() && message.fresh().get(i));
                // Empty when a pull did not say, which reads as "the product
                // itself" -- the same safe default the flag above takes.
                buffer.writeUtf(i < message.sources().size()
                    ? message.sources().get(i) : "", 32);
            }
        }

        public static OpenPack decode(FriendlyByteBuf buffer)
        {
            String setName = buffer.readUtf(128);
            int count = buffer.readVarInt();
            List<Integer> codes = new ArrayList<>(count);
            List<String> rarities = new ArrayList<>(count);
            List<Boolean> fresh = new ArrayList<>(count);
            List<String> sources = new ArrayList<>(count);
            for(int i = 0; i < count; i++)
            {
                codes.add(buffer.readVarInt());
                rarities.add(buffer.readUtf(64));
                fresh.add(buffer.readBoolean());
                sources.add(buffer.readUtf(32));
            }
            return new OpenPack(setName, codes, rarities, fresh, sources);
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
