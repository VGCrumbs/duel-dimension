package de.cas_ual_ty.dueldimension.net;

import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The menu-data buffer survives the round trip its design depends on.
 * <p>
 * {@link MenuData} exists because there is nothing left to port Forge's
 * {@code IContainerFactory} onto: vanilla menus take no extra data and Fabric
 * API's {@code ExtendedScreenHandlerType} is gone from 0.156.0+26.2. So the
 * bytes a menu's constructor needs travel as their own payload, sent one packet
 * ahead of the menu.
 * <p>
 * What is checked here is the half that is this mod's own: that what a writer
 * puts in is exactly what a reader takes out, field for field and in order.
 * The other half — that the payload arrives before the menu-open packet — is a
 * property of the connection rather than of this code: a play connection is a
 * single Netty channel, so packets arrive in the order they were sent. That is
 * not something a unit test can assert, and it is written down in
 * {@code MenuData}'s own comment so the reasoning is not lost.
 */
class MenuDataTest
{
    private static RegistryFriendlyByteBuf buffer()
    {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }

    /** What {@code MenuData.open} does to a writer's output. */
    private static byte[] written(java.util.function.Consumer<RegistryFriendlyByteBuf> writer)
    {
        RegistryFriendlyByteBuf out = buffer();
        writer.accept(out);
        byte[] data = new byte[out.readableBytes()];
        out.readBytes(data);
        return data;
    }

    @Test
    void aBlockPositionComesBackAsItself()
    {
        // Two of the four menus that needed extra data carry exactly this: the
        // duel block and the card supply both sent a position and read it in
        // their constructor.
        BlockPos sent = new BlockPos(1234, -56, 7890);
        byte[] data = written(out -> out.writeBlockPos(sent));

        MenuData.receive(data);
        assertEquals(sent, MenuData.pending(RegistryAccess.EMPTY).readBlockPos());
    }

    @Test
    void severalFieldsComeBackInOrder()
    {
        // The duel entity menu sent an id and a flag. Order is the whole
        // contract: the writer and the reader are two halves of one format.
        byte[] data = written(out ->
        {
            out.writeInt(42);
            out.writeBoolean(true);
        });

        MenuData.receive(data);
        RegistryFriendlyByteBuf back = MenuData.pending(RegistryAccess.EMPTY);
        assertEquals(42, back.readInt());
        assertEquals(true, back.readBoolean());
    }

    @Test
    void theBytesThemselvesAreUnchanged()
    {
        byte[] data = written(out -> out.writeUtf("duel_playmat"));
        MenuData.receive(data);

        RegistryFriendlyByteBuf back = MenuData.pending(RegistryAccess.EMPTY);
        byte[] again = new byte[back.readableBytes()];
        back.readBytes(again);
        assertArrayEquals(data, again, "the payload must not reshape what it carries");
    }

    @Test
    void aMenuThatWasSentNothingReadsAnEmptyBuffer()
    {
        // Most menus need no extra data at all. Reading one that was sent none
        // must be an empty buffer rather than whatever the last menu left.
        MenuData.receive(new byte[0]);
        assertEquals(0, MenuData.pending(RegistryAccess.EMPTY).readableBytes());
    }
}
