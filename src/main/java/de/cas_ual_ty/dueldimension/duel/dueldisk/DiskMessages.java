package de.cas_ual_ty.dueldimension.duel.dueldisk;

import de.cas_ual_ty.dueldimension.net.DdNetwork;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The duel disk's own traffic.
 * <p>
 * Wearing a disk is a server decision, not a client one: which disks a player
 * owns and which is active lives beside the money, and a client that could put
 * an item into its own off-hand could put anything there.
 */
public final class DiskMessages
{
    private DiskMessages()
    {
    }

    /**
     * Client to server: put the active disk on, or take it off.
     * <p>
     * Carries nothing. The server already knows who asked and what they own,
     * and a message that named the disk would be a message that could ask for
     * a disk the player has not bought.
     */
    public record ToggleDisk() implements CustomPacketPayload
    {
        /** Names this message on the wire. */
        public static final CustomPacketPayload.Type<ToggleDisk> TYPE =
            DdNetwork.type("disk_toggle");

        public static final StreamCodec<RegistryFriendlyByteBuf, ToggleDisk> CODEC =
            CustomPacketPayload.codec(ToggleDisk::encode, ToggleDisk::decode);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }

        public static void encode(ToggleDisk message, RegistryFriendlyByteBuf buffer)
        {
        }

        public static ToggleDisk decode(RegistryFriendlyByteBuf buffer)
        {
            return new ToggleDisk();
        }
    }

    /**
     * Server to everyone: this player is wearing this disk, or has taken it off.
     * <p>
     * Broadcast rather than left on the owner's profile, for the same reason
     * outfits are: a profile is synced only to the player it belongs to, so
     * without this every other client draws them holding a shield. Carries the
     * disk id as well as the flag, because which disk is worn is exactly what
     * the other clients have to draw.
     */
    public record WornDisk(java.util.UUID player, String disk, boolean worn)
        implements CustomPacketPayload
    {
        /** Names this message on the wire. */
        public static final CustomPacketPayload.Type<WornDisk> TYPE =
            DdNetwork.type("disk_worn");

        public static final StreamCodec<RegistryFriendlyByteBuf, WornDisk> CODEC =
            CustomPacketPayload.codec(WornDisk::encode, WornDisk::decode);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }

        public static void encode(WornDisk message, RegistryFriendlyByteBuf buffer)
        {
            buffer.writeUUID(message.player());
            buffer.writeUtf(message.disk(), 64);
            buffer.writeBoolean(message.worn());
        }

        public static WornDisk decode(RegistryFriendlyByteBuf buffer)
        {
            return new WornDisk(buffer.readUUID(), buffer.readUtf(64), buffer.readBoolean());
        }
    }
}
