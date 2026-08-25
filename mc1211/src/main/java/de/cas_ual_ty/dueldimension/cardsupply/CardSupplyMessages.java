package de.cas_ual_ty.dueldimension.cardsupply;

import de.cas_ual_ty.dueldimension.net.DdNetwork;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

import java.util.function.Consumer;

/**
 * The supply block's one message: a client asking for a card by id. The wire
 * carries the raw id and artwork index; the server resolves it against
 * {@code DdDatabase.PROPERTIES_LIST} in the handler ({@link DdNetwork}) rather
 * than trusting a {@code Properties} sent from the client — the same
 * server-resolves-ids discipline the rest of the mod keeps.
 */
public class CardSupplyMessages
{
    public static void doForBinderContainer(Player player, Consumer<CardSupplyContainer> consumer)
    {
        if(player != null && player.containerMenu instanceof CardSupplyContainer container)
        {
            consumer.accept(container);
        }
    }

    /** Client → server: mint the card with this id and artwork index. */
    public record RequestCard(long cardId, byte imageIndex) implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<RequestCard> TYPE =
            DdNetwork.type("card_supply_request");

        public static final StreamCodec<RegistryFriendlyByteBuf, RequestCard> CODEC =
            StreamCodec.composite(
                ByteBufCodecs.VAR_LONG.cast(), RequestCard::cardId,
                ByteBufCodecs.BYTE.cast(), RequestCard::imageIndex,
                RequestCard::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }
}
