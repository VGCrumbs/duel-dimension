package de.cas_ual_ty.dueldimension.duel.outfit;

import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import de.cas_ual_ty.dueldimension.net.DdNetwork;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** The two things anyone says about an outfit: "I am wearing this" and "they are". */
public final class OutfitMessages
{
    /** How long an outfit id may be on the wire; every real one is far shorter. */
    private static final int ID_LIMIT = 64;

    private OutfitMessages()
    {
    }

    /** Client to server: put me in this. */
    public record Wear(String outfit) implements CustomPacketPayload
    {
        /** Names this message on the wire. */
        public static final CustomPacketPayload.Type<Wear> TYPE =
            DdNetwork.type("outfit_wear");

        /**
         * Built from the encode/decode pair below rather than rewritten
         * as a composite: those two methods ARE the wire format, and
         * retyping a format is how a port quietly changes one.
         */
        public static final StreamCodec<RegistryFriendlyByteBuf, Wear> CODEC =
            CustomPacketPayload.codec(Wear::encode, Wear::decode);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }

        public static void encode(Wear message, RegistryFriendlyByteBuf buffer)
        {
            buffer.writeUtf(message.outfit(), ID_LIMIT);
        }

        public static Wear decode(RegistryFriendlyByteBuf buffer)
        {
            return new Wear(buffer.readUtf(ID_LIMIT));
        }

        /*
         * The Forge handler. Fabric registers a receiver by direction
         * and hands it the payload and the sender, so this body belongs
         * at the registration site. Kept here until it moves, because
         * it is the record of what this message is for.
         * public static void handle(Wear message, Supplier<NetworkEvent.Context> context)
         * {
         * context.get().enqueueWork(() ->
         * {
         * ServerPlayer player = context.get().getSender();
         * if(player == null)
         * {
         * return;
         * }
         * // Checked against the catalogue rather than stored as sent: an
         * // id the server does not know would be a texture path a client
         * // chose, which is not a client's to choose.
         * if(!Outfits.exists(message.outfit()))
         * {
         * player.sendSystemMessage(Component.literal("No such outfit.")
         * .withStyle(ChatFormatting.RED));
         * return;
         * }
         * de.cas_ual_ty.dueldimension.duel.profile.DuelProfiles.get(player)
         * .setOutfit(message.outfit());
         * de.cas_ual_ty.dueldimension.duel.profile.DuelProfiles.saveAndSync(player);
         * WornOutfits.broadcast(player);
         * });
         * context.get().setPacketHandled(true);
         * }
         */
    }

    /** Server to every client: this player is wearing this. */
    public record Worn(UUID player, String outfit) implements CustomPacketPayload
    {
        /** Names this message on the wire. */
        public static final CustomPacketPayload.Type<Worn> TYPE =
            DdNetwork.type("outfit_worn");

        /**
         * Built from the encode/decode pair below rather than rewritten
         * as a composite: those two methods ARE the wire format, and
         * retyping a format is how a port quietly changes one.
         */
        public static final StreamCodec<RegistryFriendlyByteBuf, Worn> CODEC =
            CustomPacketPayload.codec(Worn::encode, Worn::decode);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }

        public static void encode(Worn message, RegistryFriendlyByteBuf buffer)
        {
            buffer.writeUUID(message.player());
            buffer.writeUtf(message.outfit(), ID_LIMIT);
        }

        public static Worn decode(RegistryFriendlyByteBuf buffer)
        {
            return new Worn(buffer.readUUID(), buffer.readUtf(ID_LIMIT));
        }

        /*
         * The Forge handler. Fabric registers a receiver by direction
         * and hands it the payload and the sender, so this body belongs
         * at the registration site. Kept here until it moves, because
         * it is the record of what this message is for.
         * public static void handle(Worn message, Supplier<NetworkEvent.Context> context)
         * {
         * context.get().enqueueWork(() ->
         * WornOutfits.set(message.player(), message.outfit()));
         * context.get().setPacketHandled(true);
         * }
         */
    }
}
