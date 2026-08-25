package de.cas_ual_ty.dueldimension.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;

import java.util.function.Consumer;

/**
 * The extra data a menu's constructor needs, delivered before the menu opens.
 * <p>
 * Forge opened these menus with {@code IContainerFactory}, which handed the
 * constructor a buffer written at the moment of opening: which duel, which
 * block, which entity. There is nothing to port that onto. Vanilla's
 * {@code MenuType} offers only {@code create(int, Inventory)},
 * {@code ServerPlayer.openMenu} takes a bare {@link MenuProvider}, and Fabric
 * API's {@code ExtendedScreenHandlerType} — which used to fill exactly this gap
 * — is not in Fabric API 0.156.0+26.2.
 * <p>
 * So the data is sent as its own payload <em>immediately before</em> the
 * menu-open packet. The "before" is the whole design:
 * <ul>
 * <li>A play connection is one Netty channel, so packets arrive in the order
 *     they were sent. The data is therefore already here when the menu is
 *     constructed — there is no race to lose and no frame where the screen is
 *     open and empty.</li>
 * <li>The constructor can read it, which matters:
 *     {@code DuelBlockContainer} looks up a block entity from its position
 *     <em>while constructing</em>, so anything that arrives afterwards — a
 *     second payload, {@code ContainerData}, a slot sync — is too late by
 *     construction, not merely by timing.</li>
 * <li>It uses nothing but vanilla and this mod's own payload registration. The
 *     two Fabric helpers this port has already watched disappear
 *     ({@code FabricItemGroup}, {@code ExtendedScreenHandlerType}) are a fair
 *     warning against depending on a third.</li>
 * </ul>
 * The alternative — deriving the data server side from what the menu already
 * knows — works for the two block menus and cannot work for the duel menu,
 * which genuinely has to be told which duel. One mechanism that covers all four
 * beats two that each cover some.
 */
public record MenuData(byte[] data) implements CustomPacketPayload
{
    public static final CustomPacketPayload.Type<MenuData> TYPE = DdNetwork.type("menu_data");

    /**
     * Raw bytes rather than a typed payload per menu: what a given menu needs
     * is the menu's business, and every one of them already knows how to read
     * its own buffer. A typed payload per menu would be four more registrations
     * to keep in step for no more safety — the reader and the writer are the
     * same class either way.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, MenuData> CODEC =
        StreamCodec.composite(ByteBufCodecs.BYTE_ARRAY, MenuData::data, MenuData::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }

    /**
     * What the last {@link MenuData} carried, on the client.
     * <p>
     * A single slot, not a queue: the payload is sent one packet before the
     * menu that reads it, so there is never a second one in flight. Read from
     * the menu factory registered in {@code DdContainerTypes}.
     */
    private static byte[] pending = new byte[0];

    public static void receive(byte[] data)
    {
        pending = data;
    }

    /** The bytes the menu being constructed was sent, as a readable buffer. */
    public static RegistryFriendlyByteBuf pending(net.minecraft.core.RegistryAccess registries)
    {
        return new RegistryFriendlyByteBuf(
            io.netty.buffer.Unpooled.wrappedBuffer(pending), registries);
    }

    /**
     * Opens a menu, having first told the client what its constructor needs.
     *
     * @param writer writes the same fields the menu's constructor reads, in the
     *               same order — the two halves of one format, so they belong
     *               next to each other in the menu's own class
     */
    public static void open(ServerPlayer player, MenuProvider provider,
        Consumer<RegistryFriendlyByteBuf> writer)
    {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
            io.netty.buffer.Unpooled.buffer(), player.registryAccess());
        writer.accept(buffer);
        byte[] data = new byte[buffer.readableBytes()];
        buffer.readBytes(data);

        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
            new MenuData(data));
        player.openMenu(provider);
    }
}
