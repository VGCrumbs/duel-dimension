package de.cas_ual_ty.dueldimension.duel.npc;

import de.cas_ual_ty.dueldimension.net.DdNetwork;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Asking a player how they want to duel a duelist, and hearing back.
 * <p>
 * A duel against an NPC has no lobby to agree anything in -- it starts the
 * moment you click -- so the one choice that matters has to be asked for
 * somewhere, and the click itself is the only moment there is.
 * <p>
 * The reply carries the duelist's entity id rather than a remembered "pending
 * challenge", so the server has something concrete to re-check: it looks the
 * entity up, confirms it is a duelist, and confirms it is still within reach.
 * A remembered challenge would have been state to expire, and a reply that
 * outlived the thing it was about.
 */
public final class DuelistChallengeMessages
{
    private DuelistChallengeMessages()
    {
    }

    /** Server to client: you clicked a duelist, so how would you like to play it? */
    public record OfferDuel(int duelistId, String duelistName) implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<OfferDuel> TYPE =
            DdNetwork.type("duelist_offer");

        public static final StreamCodec<RegistryFriendlyByteBuf, OfferDuel> CODEC =
            CustomPacketPayload.codec(OfferDuel::encode, OfferDuel::decode);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }

        public static void encode(OfferDuel message, RegistryFriendlyByteBuf buffer)
        {
            buffer.writeVarInt(message.duelistId());
            buffer.writeUtf(message.duelistName(), 64);
        }

        public static OfferDuel decode(RegistryFriendlyByteBuf buffer)
        {
            return new OfferDuel(buffer.readVarInt(), buffer.readUtf(64));
        }
    }

    /**
     * Client to server: this one, played this way.
     *
     * @param overworld true for a board built in the world, false for the screen
     */
    public record ChooseDuel(int duelistId, boolean overworld) implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<ChooseDuel> TYPE =
            DdNetwork.type("duelist_choose");

        public static final StreamCodec<RegistryFriendlyByteBuf, ChooseDuel> CODEC =
            CustomPacketPayload.codec(ChooseDuel::encode, ChooseDuel::decode);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }

        public static void encode(ChooseDuel message, RegistryFriendlyByteBuf buffer)
        {
            buffer.writeVarInt(message.duelistId());
            buffer.writeBoolean(message.overworld());
        }

        public static ChooseDuel decode(RegistryFriendlyByteBuf buffer)
        {
            return new ChooseDuel(buffer.readVarInt(), buffer.readBoolean());
        }
    }
}
