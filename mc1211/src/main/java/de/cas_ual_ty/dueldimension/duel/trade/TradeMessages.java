package de.cas_ual_ty.dueldimension.duel.trade;

import de.cas_ual_ty.dueldimension.net.DdNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * What the two sides of a trade say to each other, which is only ever through
 * the server.
 *
 * <h2>One state message, not a stream of edits</h2>
 * The client is never told "they added a card"; it is told what the whole table
 * looks like now. A trade is small — eighteen slots and two numbers — so the
 * saving from sending differences is nothing, while the cost is real: two
 * clients applying their own edits optimistically and the server's edits on top
 * is exactly how the two sides come to disagree about what is being traded, and
 * disagreeing about THAT is the one thing this screen must never do.
 * <p>
 * It also means the ready flags and the countdown arrive with the offer they
 * belong to, so a client cannot show "both ready" beside a table that has since
 * changed.
 */
public final class TradeMessages
{
    private TradeMessages()
    {
    }

    /** Client to server: what to do with the player who was right-clicked. */
    public record PlayerAction(java.util.UUID target, boolean trade)
        implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<PlayerAction> TYPE =
            DdNetwork.type("trade_player_action");
        public static final StreamCodec<RegistryFriendlyByteBuf, PlayerAction> CODEC =
            CustomPacketPayload.codec(PlayerAction::encode, PlayerAction::decode);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }

        public static void encode(PlayerAction message, FriendlyByteBuf buffer)
        {
            buffer.writeUUID(message.target());
            buffer.writeBoolean(message.trade());
        }

        public static PlayerAction decode(FriendlyByteBuf buffer)
        {
            return new PlayerAction(buffer.readUUID(), buffer.readBoolean());
        }
    }

    /**
     * Server to client: this player was right-clicked, so offer the menu.
     * <p>
     * Sent by the server rather than decided on the client, because whether a
     * player may be duelled or traded with at all is the server's answer -- and
     * a menu that offered something the server would refuse is a menu that
     * lies.
     */
    public record OfferMenu(java.util.UUID target, String name)
        implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<OfferMenu> TYPE =
            DdNetwork.type("trade_offer_menu");
        public static final StreamCodec<RegistryFriendlyByteBuf, OfferMenu> CODEC =
            CustomPacketPayload.codec(OfferMenu::encode, OfferMenu::decode);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }

        public static void encode(OfferMenu message, FriendlyByteBuf buffer)
        {
            buffer.writeUUID(message.target());
            buffer.writeUtf(message.name(), 64);
        }

        public static OfferMenu decode(FriendlyByteBuf buffer)
        {
            return new OfferMenu(buffer.readUUID(), buffer.readUtf(64));
        }
    }

    /** Client to server: put this printing in this slot, or clear it. */
    public record SetSlot(int slot, int passcode, String rarity, int art)
        implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<SetSlot> TYPE =
            DdNetwork.type("trade_set_slot");
        public static final StreamCodec<RegistryFriendlyByteBuf, SetSlot> CODEC =
            CustomPacketPayload.codec(SetSlot::encode, SetSlot::decode);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }

        public static void encode(SetSlot message, FriendlyByteBuf buffer)
        {
            buffer.writeVarInt(message.slot());
            buffer.writeVarInt(message.passcode());
            buffer.writeUtf(message.rarity() == null ? "" : message.rarity(), 64);
            buffer.writeVarInt(message.art());
        }

        public static SetSlot decode(FriendlyByteBuf buffer)
        {
            return new SetSlot(buffer.readVarInt(), buffer.readVarInt(),
                buffer.readUtf(64), buffer.readVarInt());
        }
    }

    /** Client to server: offer this many DP. */
    public record SetPoints(int points) implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<SetPoints> TYPE =
            DdNetwork.type("trade_set_points");
        public static final StreamCodec<RegistryFriendlyByteBuf, SetPoints> CODEC =
            StreamCodec.composite(net.minecraft.network.codec.ByteBufCodecs.VAR_INT,
                SetPoints::points, SetPoints::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    /** Client to server: agree to what is on the table, or take it back. */
    public record SetReady(boolean ready) implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<SetReady> TYPE =
            DdNetwork.type("trade_set_ready");
        public static final StreamCodec<RegistryFriendlyByteBuf, SetReady> CODEC =
            StreamCodec.composite(net.minecraft.network.codec.ByteBufCodecs.BOOL,
                SetReady::ready, SetReady::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    /** Client to server: call the whole thing off. */
    public record Cancel() implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<Cancel> TYPE =
            DdNetwork.type("trade_cancel");
        public static final StreamCodec<RegistryFriendlyByteBuf, Cancel> CODEC =
            StreamCodec.unit(new Cancel());

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    /** Development only: open a trade against the stand-in. */
    public record OpenDebug() implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<OpenDebug> TYPE =
            DdNetwork.type("trade_open_debug");
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenDebug> CODEC =
            StreamCodec.unit(new OpenDebug());

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    /**
     * Server to client: the whole table.
     *
     * @param other     who is across the table, for the caption
     * @param mine      this player's nine slots, in order; a passcode of 0 is empty
     * @param theirs    the other side's nine
     * @param countdown ticks left, or -1 when nobody is counting
     * @param open      false to close the screen -- the trade is over, one way
     *                  or the other
     */
    public record State(String other, List<Slot> mine, List<Slot> theirs,
        int myPoints, int theirPoints, boolean myReady, boolean theirReady,
        int countdown, boolean open) implements CustomPacketPayload
    {
        /** One slot on the wire. A passcode of 0 means nothing is in it. */
        public record Slot(int passcode, String rarity, int art)
        {
        }

        public static final CustomPacketPayload.Type<State> TYPE =
            DdNetwork.type("trade_state");
        public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC =
            CustomPacketPayload.codec(State::encode, State::decode);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }

        private static void writeSlots(List<Slot> slots, FriendlyByteBuf buffer)
        {
            for(int i = 0; i < TradeSession.SLOTS; i++)
            {
                Slot slot = i < slots.size() ? slots.get(i) : null;
                buffer.writeVarInt(slot == null ? 0 : slot.passcode());
                buffer.writeUtf(slot == null || slot.rarity() == null ? "" : slot.rarity(), 64);
                buffer.writeVarInt(slot == null ? 0 : slot.art());
            }
        }

        private static List<Slot> readSlots(FriendlyByteBuf buffer)
        {
            List<Slot> slots = new ArrayList<>(TradeSession.SLOTS);
            for(int i = 0; i < TradeSession.SLOTS; i++)
            {
                slots.add(new Slot(buffer.readVarInt(), buffer.readUtf(64),
                    buffer.readVarInt()));
            }
            return slots;
        }

        public static void encode(State message, FriendlyByteBuf buffer)
        {
            buffer.writeUtf(message.other(), 64);
            writeSlots(message.mine(), buffer);
            writeSlots(message.theirs(), buffer);
            buffer.writeVarInt(message.myPoints());
            buffer.writeVarInt(message.theirPoints());
            buffer.writeBoolean(message.myReady());
            buffer.writeBoolean(message.theirReady());
            buffer.writeVarInt(message.countdown() + 1);
            buffer.writeBoolean(message.open());
        }

        public static State decode(FriendlyByteBuf buffer)
        {
            String other = buffer.readUtf(64);
            List<Slot> mine = readSlots(buffer);
            List<Slot> theirs = readSlots(buffer);
            return new State(other, mine, theirs, buffer.readVarInt(), buffer.readVarInt(),
                buffer.readBoolean(), buffer.readBoolean(),
                buffer.readVarInt() - 1, buffer.readBoolean());
        }
    }
}
