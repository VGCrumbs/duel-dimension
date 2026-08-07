package de.cas_ual_ty.dueldimension.duel.outfit;

import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** The two things anyone says about an outfit: "I am wearing this" and "they are". */
public final class OutfitMessages
{
    /** How long an outfit id may be on the wire; every real one is far shorter. */
    private static final int ID_LIMIT = 64;

    private OutfitMessages()
    {
    }

    /** Client to server: put me in this. */
    public record Wear(String outfit)
    {
        public static void encode(Wear message, FriendlyByteBuf buffer)
        {
            buffer.writeUtf(message.outfit(), ID_LIMIT);
        }

        public static Wear decode(FriendlyByteBuf buffer)
        {
            return new Wear(buffer.readUtf(ID_LIMIT));
        }

        public static void handle(Wear message, Supplier<NetworkEvent.Context> context)
        {
            context.get().enqueueWork(() ->
            {
                ServerPlayer player = context.get().getSender();
                if(player == null)
                {
                    return;
                }
                // Checked against the catalogue rather than stored as sent: an
                // id the server does not know would be a texture path a client
                // chose, which is not a client's to choose.
                if(!Outfits.exists(message.outfit()))
                {
                    player.sendSystemMessage(Component.literal("No such outfit.")
                        .withStyle(ChatFormatting.RED));
                    return;
                }
                de.cas_ual_ty.dueldimension.duel.profile.DuelProfiles.get(player)
                    .setOutfit(message.outfit());
                de.cas_ual_ty.dueldimension.duel.profile.DuelProfiles.saveAndSync(player);
                WornOutfits.broadcast(player);
            });
            context.get().setPacketHandled(true);
        }
    }

    /** Server to every client: this player is wearing this. */
    public record Worn(UUID player, String outfit)
    {
        public static void encode(Worn message, FriendlyByteBuf buffer)
        {
            buffer.writeUUID(message.player());
            buffer.writeUtf(message.outfit(), ID_LIMIT);
        }

        public static Worn decode(FriendlyByteBuf buffer)
        {
            return new Worn(buffer.readUUID(), buffer.readUtf(ID_LIMIT));
        }

        public static void handle(Worn message, Supplier<NetworkEvent.Context> context)
        {
            context.get().enqueueWork(() ->
                WornOutfits.set(message.player(), message.outfit()));
            context.get().setPacketHandled(true);
        }
    }
}
