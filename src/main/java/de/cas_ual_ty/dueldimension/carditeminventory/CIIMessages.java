package de.cas_ual_ty.dueldimension.carditeminventory;

import de.cas_ual_ty.dueldimension.net.DdNetwork;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

import java.util.function.Consumer;

/**
 * The two messages a paged card-item inventory needs: the server telling the
 * client which page it moved to, and the client asking to turn a page.
 * <p>
 * Forge carried a {@code handle(msg, Supplier&lt;Context&gt;)} per message; here
 * the payload is just the data, and the handling lives in {@link DdNetwork}
 * (server side) and {@link DdNetwork#registerClientHandlers()} (client side),
 * registered by direction so a page-turn request cannot be sent to a client.
 */
public class CIIMessages
{
    public static void doForContainer(Player player, Consumer<CIIContainer> consumer)
    {
        if(player != null && player.containerMenu instanceof CIIContainer container)
        {
            consumer.accept(container);
        }
    }

    /** Server → client: the container has moved to {@code page}. */
    public record SetPage(int page) implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<SetPage> TYPE = DdNetwork.type("cii_set_page");

        public static final StreamCodec<RegistryFriendlyByteBuf, SetPage> CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT.cast(), SetPage::page, SetPage::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    /** Client → server: turn to the next page (or the previous one). */
    public record ChangePage(boolean nextPage) implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<ChangePage> TYPE =
            DdNetwork.type("cii_change_page");

        public static final StreamCodec<RegistryFriendlyByteBuf, ChangePage> CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, ChangePage::nextPage, ChangePage::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }
}
