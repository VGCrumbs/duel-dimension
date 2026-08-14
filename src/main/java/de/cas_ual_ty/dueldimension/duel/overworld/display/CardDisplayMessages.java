package de.cas_ual_ty.dueldimension.duel.overworld.display;

import de.cas_ual_ty.dueldimension.net.DdNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The two things a display block has to say.
 * <p>
 * A packet and a screen rather than a menu and a container. A container is a
 * set of slots, and this editor has none: it is a grid of cards to search
 * through and three buttons for which way up. Borrowing the slot machinery to
 * carry a passcode would mean inventing an item to put in a slot to stand for a
 * card, which is a worse lie than the one it saves.
 */
public final class CardDisplayMessages
{
    private CardDisplayMessages()
    {
    }

    /**
     * Server to client: open the editor for this block, showing what it holds.
     * <p>
     * The current contents ride along so the screen opens on the card that is
     * already there rather than on nothing -- a builder adjusting a display
     * should see what they are adjusting.
     */
    public record OpenEditor(BlockPos pos, long code, byte art, int position)
        implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<OpenEditor> TYPE =
            DdNetwork.type("card_display_open");

        public static final StreamCodec<RegistryFriendlyByteBuf, OpenEditor> CODEC =
            CustomPacketPayload.codec(OpenEditor::encode, OpenEditor::decode);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }

        public static void encode(OpenEditor message, RegistryFriendlyByteBuf buffer)
        {
            buffer.writeBlockPos(message.pos());
            buffer.writeLong(message.code());
            buffer.writeByte(message.art());
            buffer.writeVarInt(message.position());
        }

        public static OpenEditor decode(RegistryFriendlyByteBuf buffer)
        {
            return new OpenEditor(buffer.readBlockPos(), buffer.readLong(), buffer.readByte(),
                buffer.readVarInt());
        }
    }

    /**
     * Client to server: put this card on that block.
     * <p>
     * Everything about it is checked again at the far end. A packet naming a
     * block position is a packet that can name ANY block position, so the
     * server re-tests creative mode, reach, and that the block is what the
     * client thinks it is -- see the handler.
     */
    public record SetCard(BlockPos pos, long code, byte art, int position)
        implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<SetCard> TYPE =
            DdNetwork.type("card_display_set");

        public static final StreamCodec<RegistryFriendlyByteBuf, SetCard> CODEC =
            CustomPacketPayload.codec(SetCard::encode, SetCard::decode);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }

        public static void encode(SetCard message, RegistryFriendlyByteBuf buffer)
        {
            buffer.writeBlockPos(message.pos());
            buffer.writeLong(message.code());
            buffer.writeByte(message.art());
            buffer.writeVarInt(message.position());
        }

        public static SetCard decode(RegistryFriendlyByteBuf buffer)
        {
            return new SetCard(buffer.readBlockPos(), buffer.readLong(), buffer.readByte(),
                buffer.readVarInt());
        }
    }
}
