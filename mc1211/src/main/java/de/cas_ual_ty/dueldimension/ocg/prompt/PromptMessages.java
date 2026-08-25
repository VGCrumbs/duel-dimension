package de.cas_ual_ty.dueldimension.ocg.prompt;

import de.cas_ual_ty.dueldimension.DuelDimension;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import de.cas_ual_ty.dueldimension.net.DdNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * The duel's network surface: a prompt going out, the choice coming back, a
 * stream of board/log updates, and surrender. Deliberately minimal — the
 * client is told what it may pick and replies with which of those it picked
 * (plus a card code for the announce search, which the server re-validates).
 */
public final class PromptMessages
{
    private PromptMessages()
    {
    }

    /** Server -> client: here is a decision to make. */
    public record ShowPrompt(EnginePrompt prompt, int serial) implements CustomPacketPayload
    {
        /** Names this message on the wire. */
        public static final CustomPacketPayload.Type<ShowPrompt> TYPE =
            DdNetwork.type("prompt_show_prompt");

        /**
         * Built from the encode/decode pair below rather than rewritten
         * as a composite: those two methods ARE the wire format, and
         * retyping a format is how a port quietly changes one.
         */
        public static final StreamCodec<RegistryFriendlyByteBuf, ShowPrompt> CODEC =
            CustomPacketPayload.codec(ShowPrompt::encode, ShowPrompt::decode);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }

        public static void encode(ShowPrompt message, FriendlyByteBuf buffer)
        {
            message.prompt().write(buffer);
            buffer.writeVarInt(message.serial());
        }

        public static ShowPrompt decode(FriendlyByteBuf buffer)
        {
            return new ShowPrompt(EnginePrompt.read(buffer), buffer.readVarInt());
        }

        /*
         * The Forge handler. Fabric registers a receiver by direction
         * and hands it the payload and the sender, so this body belongs
         * at the registration site. Kept here until it moves, because
         * it is the record of what this message is for.
         * public static void handle(ShowPrompt message, Supplier<NetworkEvent.Context> context)
         * {
         * context.get().enqueueWork(
         * () -> DuelDimension.proxy.showEnginePrompt(message.prompt(), message.serial()));
         * context.get().setPacketHandled(true);
         * }
         */
    }

    /** Client -> server: I picked these (indices; declaredCode for name declares). */
    public record AnswerPrompt(int[] chosen, int declaredCode, int serial) implements CustomPacketPayload
    {
        /** Names this message on the wire. */
        public static final CustomPacketPayload.Type<AnswerPrompt> TYPE =
            DdNetwork.type("prompt_answer_prompt");

        /**
         * Built from the encode/decode pair below rather than rewritten
         * as a composite: those two methods ARE the wire format, and
         * retyping a format is how a port quietly changes one.
         */
        public static final StreamCodec<RegistryFriendlyByteBuf, AnswerPrompt> CODEC =
            CustomPacketPayload.codec(AnswerPrompt::encode, AnswerPrompt::decode);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }

        public static void encode(AnswerPrompt message, FriendlyByteBuf buffer)
        {
            buffer.writeVarIntArray(message.chosen());
            buffer.writeVarInt(message.declaredCode());
            buffer.writeVarInt(message.serial());
        }

        public static AnswerPrompt decode(FriendlyByteBuf buffer)
        {
            return new AnswerPrompt(buffer.readVarIntArray(), buffer.readVarInt(),
                buffer.readVarInt());
        }

        /*
         * The Forge handler. Fabric registers a receiver by direction
         * and hands it the payload and the sender, so this body belongs
         * at the registration site. Kept here until it moves, because
         * it is the record of what this message is for.
         * public static void handle(AnswerPrompt message, Supplier<NetworkEvent.Context> context)
         * {
         * NetworkEvent.Context ctx = context.get();
         * ctx.enqueueWork(() ->
         * {
         * ServerPlayer sender = ctx.getSender();
         * if(sender != null)
         * {
         * de.cas_ual_ty.dueldimension.duel.npc.DuelistDuels.submitAnswer(sender,
         * new HumanResponseSource.Answer(message.chosen(), message.declaredCode(),
         * message.serial()));
         * }
         * });
         * ctx.setPacketHandled(true);
         * }
         */
    }

    /**
     * Server -> client: the duel moved. Board is optional (log-only updates
     * skip it); {@code over} closes out the duel with a result line.
     */
    public record DuelUpdate(BoardSnapshot board, List<String> log, boolean over, String result, int[] warmUp,
        List<DuelEvent> events) implements CustomPacketPayload
    {
        /** Names this message on the wire. */
        public static final CustomPacketPayload.Type<DuelUpdate> TYPE =
            DdNetwork.type("prompt_duel_update");

        /**
         * Built from the encode/decode pair below rather than rewritten
         * as a composite: those two methods ARE the wire format, and
         * retyping a format is how a port quietly changes one.
         */
        public static final StreamCodec<RegistryFriendlyByteBuf, DuelUpdate> CODEC =
            CustomPacketPayload.codec(DuelUpdate::encode, DuelUpdate::decode);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }

        public DuelUpdate(BoardSnapshot board, List<String> log, boolean over, String result)
        {
            this(board, log, over, result, new int[0], List.of());
        }

        public DuelUpdate(BoardSnapshot board, List<String> log, boolean over, String result, int[] warmUp)
        {
            this(board, log, over, result, warmUp, List.of());
        }

        public static void encode(DuelUpdate message, FriendlyByteBuf buffer)
        {
            buffer.writeBoolean(message.board() != null);
            if(message.board() != null)
            {
                message.board().write(buffer);
            }
            buffer.writeVarInt(message.log().size());
            message.log().forEach(line -> buffer.writeUtf(line, 256));
            buffer.writeBoolean(message.over());
            buffer.writeUtf(message.result(), 128);
            buffer.writeVarIntArray(message.warmUp());
            DuelEvent.writeList(buffer, message.events());
        }

        public static DuelUpdate decode(FriendlyByteBuf buffer)
        {
            BoardSnapshot board = buffer.readBoolean() ? BoardSnapshot.read(buffer) : null;
            int count = buffer.readVarInt();
            List<String> log = new ArrayList<>(count);
            for(int i = 0; i < count; i++)
            {
                log.add(buffer.readUtf(256));
            }
            return new DuelUpdate(board, log, buffer.readBoolean(), buffer.readUtf(128),
                buffer.readVarIntArray(), DuelEvent.readList(buffer));
        }

        /*
         * The Forge handler. Fabric registers a receiver by direction
         * and hands it the payload and the sender, so this body belongs
         * at the registration site. Kept here until it moves, because
         * it is the record of what this message is for.
         * public static void handle(DuelUpdate message, Supplier<NetworkEvent.Context> context)
         * {
         * context.get().enqueueWork(() -> DuelDimension.proxy.updateEngineDuel(message));
         * context.get().setPacketHandled(true);
         * }
         */
    }

    /** Client -> server: change how chain windows are answered. */
    public record SetChainPreference(ChainPreference preference) implements CustomPacketPayload
    {
        /** Names this message on the wire. */
        public static final CustomPacketPayload.Type<SetChainPreference> TYPE =
            DdNetwork.type("prompt_set_chain_preference");

        /**
         * Built from the encode/decode pair below rather than rewritten
         * as a composite: those two methods ARE the wire format, and
         * retyping a format is how a port quietly changes one.
         */
        public static final StreamCodec<RegistryFriendlyByteBuf, SetChainPreference> CODEC =
            CustomPacketPayload.codec(SetChainPreference::encode, SetChainPreference::decode);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }

        public static void encode(SetChainPreference message, FriendlyByteBuf buffer)
        {
            buffer.writeEnum(message.preference());
        }

        public static SetChainPreference decode(FriendlyByteBuf buffer)
        {
            return new SetChainPreference(buffer.readEnum(ChainPreference.class));
        }

        /*
         * The Forge handler. Fabric registers a receiver by direction
         * and hands it the payload and the sender, so this body belongs
         * at the registration site. Kept here until it moves, because
         * it is the record of what this message is for.
         * public static void handle(SetChainPreference message, Supplier<NetworkEvent.Context> context)
         * {
         * NetworkEvent.Context ctx = context.get();
         * ctx.enqueueWork(() ->
         * {
         * ServerPlayer sender = ctx.getSender();
         * if(sender != null)
         * {
         * de.cas_ual_ty.dueldimension.duel.npc.DuelistDuels.setChainPreference(sender,
         * message.preference());
         * }
         * });
         * ctx.setPacketHandled(true);
         * }
         */
    }

    /**
     * Client -> server: the mat I brought. The server keeps it against the
     * player so the other duelist's client can draw it on their far half.
     */
    public record SetPlayMat(String matId) implements CustomPacketPayload
    {
        /** Names this message on the wire. */
        public static final CustomPacketPayload.Type<SetPlayMat> TYPE =
            DdNetwork.type("prompt_set_play_mat");

        /**
         * Built from the encode/decode pair below rather than rewritten
         * as a composite: those two methods ARE the wire format, and
         * retyping a format is how a port quietly changes one.
         */
        public static final StreamCodec<RegistryFriendlyByteBuf, SetPlayMat> CODEC =
            CustomPacketPayload.codec(SetPlayMat::encode, SetPlayMat::decode);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }

        public static void encode(SetPlayMat message, FriendlyByteBuf buffer)
        {
            buffer.writeUtf(message.matId(), 64);
        }

        public static SetPlayMat decode(FriendlyByteBuf buffer)
        {
            return new SetPlayMat(buffer.readUtf(64));
        }

        /*
         * The Forge handler. Fabric registers a receiver by direction
         * and hands it the payload and the sender, so this body belongs
         * at the registration site. Kept here until it moves, because
         * it is the record of what this message is for.
         * public static void handle(SetPlayMat message, Supplier<NetworkEvent.Context> context)
         * {
         * NetworkEvent.Context ctx = context.get();
         * ctx.enqueueWork(() ->
         * {
         * ServerPlayer sender = ctx.getSender();
         * if(sender != null)
         * {
         * de.cas_ual_ty.dueldimension.duel.npc.DuelistDuels.setPlayMat(sender,
         * message.matId());
         * }
         * });
         * ctx.setPacketHandled(true);
         * }
         */
    }

    /**
     * Server -> client: the sleeve on the deck THIS player is duelling with.
     * <p>
     * Sent once as the duel starts. It names the deck actually in play, which is
     * not always the one the client made active — an illegal deck is swapped for
     * a starter deck server-side, and the back on the table should be that
     * deck's, not the one that was refused.
     */
    /**
     * Server -> client: the sleeve on the deck the OPPONENT is duelling with.
     * <p>
     * A sleeve belongs to the cards, not to the seat looking at them. Sending
     * only the player's own meant the person across the table saw a plain back
     * on cards that were sleeved -- which is the one audience a sleeve is for.
     * <p>
     * This is cosmetic and carries no hidden information: it names a sleeve,
     * not a deck's contents.
     */
    public record OpponentSleeve(String sleeve) implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<OpponentSleeve> TYPE =
            DdNetwork.type("prompt_opponent_sleeve");

        public static final StreamCodec<RegistryFriendlyByteBuf, OpponentSleeve> CODEC =
            CustomPacketPayload.codec(OpponentSleeve::encode, OpponentSleeve::decode);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }

        public static void encode(OpponentSleeve message, FriendlyByteBuf buffer)
        {
            buffer.writeUtf(message.sleeve(), 64);
        }

        public static OpponentSleeve decode(FriendlyByteBuf buffer)
        {
            return new OpponentSleeve(buffer.readUtf(64));
        }
    }

    public record OwnSleeve(String sleeve) implements CustomPacketPayload
    {
        /** Names this message on the wire. */
        public static final CustomPacketPayload.Type<OwnSleeve> TYPE =
            DdNetwork.type("prompt_own_sleeve");

        public static final StreamCodec<RegistryFriendlyByteBuf, OwnSleeve> CODEC =
            CustomPacketPayload.codec(OwnSleeve::encode, OwnSleeve::decode);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }

        public static void encode(OwnSleeve message, FriendlyByteBuf buffer)
        {
            buffer.writeUtf(message.sleeve(), 64);
        }

        public static OwnSleeve decode(FriendlyByteBuf buffer)
        {
            return new OwnSleeve(buffer.readUtf(64));
        }
    }

    /** Server -> client: the mat the opponent is playing on. */
    public record OpponentPlayMat(String matId) implements CustomPacketPayload
    {
        /** Names this message on the wire. */
        public static final CustomPacketPayload.Type<OpponentPlayMat> TYPE =
            DdNetwork.type("prompt_opponent_play_mat");

        /**
         * Built from the encode/decode pair below rather than rewritten
         * as a composite: those two methods ARE the wire format, and
         * retyping a format is how a port quietly changes one.
         */
        public static final StreamCodec<RegistryFriendlyByteBuf, OpponentPlayMat> CODEC =
            CustomPacketPayload.codec(OpponentPlayMat::encode, OpponentPlayMat::decode);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }

        public static void encode(OpponentPlayMat message, FriendlyByteBuf buffer)
        {
            buffer.writeUtf(message.matId(), 64);
        }

        public static OpponentPlayMat decode(FriendlyByteBuf buffer)
        {
            return new OpponentPlayMat(buffer.readUtf(64));
        }

        /*
         * The Forge handler. Fabric registers a receiver by direction
         * and hands it the payload and the sender, so this body belongs
         * at the registration site. Kept here until it moves, because
         * it is the record of what this message is for.
         * public static void handle(OpponentPlayMat message, Supplier<NetworkEvent.Context> context)
         * {
         * NetworkEvent.Context ctx = context.get();
         * ctx.enqueueWork(() -> DuelDimension.proxy.setOpponentPlayMat(message.matId()));
         * ctx.setPacketHandled(true);
         * }
         */
    }

    /**
     * Client -> server: show me MY deck.
     * <p>
     * No payload, and that is the whole of the authorisation story. A field
     * naming a controller, a location or a seat would be a request the client
     * gets to aim, and the first bug in validating it puts the opponent's deck
     * on a screen. The server derives the seat from the sender, so there is
     * nothing here to validate and nothing to get wrong.
     */
    /**
     * Who the two duellists are, told to one of them.
     * <p>
     * The bars said "You" and "Opponent", which is true of every duel ever
     * played and so says nothing. Names are not hidden information -- both
     * players can see each other standing there, and a duelist's name is
     * written over its head -- so this is a label, not a leak.
     *
     * @param self     what to call the seat being told
     * @param opponent what to call the other one
     */
    public record DuelNames(String self, String opponent) implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<DuelNames> TYPE =
            DdNetwork.type("prompt_duel_names");

        public static final StreamCodec<RegistryFriendlyByteBuf, DuelNames> CODEC =
            CustomPacketPayload.codec(DuelNames::encode, DuelNames::decode);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }

        public static void encode(DuelNames message, RegistryFriendlyByteBuf buffer)
        {
            buffer.writeUtf(message.self(), 64);
            buffer.writeUtf(message.opponent(), 64);
        }

        public static DuelNames decode(RegistryFriendlyByteBuf buffer)
        {
            return new DuelNames(buffer.readUtf(64), buffer.readUtf(64));
        }
    }

    public record ViewOwnDeck() implements CustomPacketPayload
    {
        /** Names this message on the wire. */
        public static final CustomPacketPayload.Type<ViewOwnDeck> TYPE =
            DdNetwork.type("prompt_view_own_deck");

        public static final StreamCodec<RegistryFriendlyByteBuf, ViewOwnDeck> CODEC =
            CustomPacketPayload.codec(ViewOwnDeck::encode, ViewOwnDeck::decode);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }

        public static void encode(ViewOwnDeck message, FriendlyByteBuf buffer)
        {
        }

        public static ViewOwnDeck decode(FriendlyByteBuf buffer)
        {
            return new ViewOwnDeck();
        }
    }

    /**
     * Server -> client: the cards in YOUR deck, in a shuffled order.
     * <p>
     * Two parallel arrays and nothing else, deliberately: this is a MULTISET of
     * (passcode, artwork) pairs, and a multiset has no order to leak. It is not
     * a list of {@link BoardSnapshot.Slot} because a Slot carries a
     * {@code CardView.Equip(controller, location, sequence)} and an overlay
     * count — fields that CAN encode a position. A field that is not on the wire
     * cannot be filled in by a later change that looks like tidying up.
     * <p>
     * No index, no sequence, no count: the array length IS the deck size, which
     * is already public (both clients are told {@code deckCount} and the pile
     * label prints it), so a separate number could only ever disagree with it.
     */
    public record OwnDeckList(int[] codes, int[] arts) implements CustomPacketPayload
    {
        /** Names this message on the wire. */
        public static final CustomPacketPayload.Type<OwnDeckList> TYPE =
            DdNetwork.type("prompt_own_deck_list");

        public static final StreamCodec<RegistryFriendlyByteBuf, OwnDeckList> CODEC =
            CustomPacketPayload.codec(OwnDeckList::encode, OwnDeckList::decode);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }

        public static void encode(OwnDeckList message, FriendlyByteBuf buffer)
        {
            buffer.writeVarIntArray(message.codes());
            buffer.writeVarIntArray(message.arts());
        }

        public static OwnDeckList decode(FriendlyByteBuf buffer)
        {
            return new OwnDeckList(buffer.readVarIntArray(), buffer.readVarIntArray());
        }
    }

    /** Client -> server: I give up. */
    public record Surrender() implements CustomPacketPayload
    {
        /** Names this message on the wire. */
        public static final CustomPacketPayload.Type<Surrender> TYPE =
            DdNetwork.type("prompt_surrender");

        /**
         * Built from the encode/decode pair below rather than rewritten
         * as a composite: those two methods ARE the wire format, and
         * retyping a format is how a port quietly changes one.
         */
        public static final StreamCodec<RegistryFriendlyByteBuf, Surrender> CODEC =
            CustomPacketPayload.codec(Surrender::encode, Surrender::decode);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }

        public static void encode(Surrender message, FriendlyByteBuf buffer)
        {
        }

        public static Surrender decode(FriendlyByteBuf buffer)
        {
            return new Surrender();
        }

        /*
         * The Forge handler. Fabric registers a receiver by direction
         * and hands it the payload and the sender, so this body belongs
         * at the registration site. Kept here until it moves, because
         * it is the record of what this message is for.
         * public static void handle(Surrender message, Supplier<NetworkEvent.Context> context)
         * {
         * NetworkEvent.Context ctx = context.get();
         * ctx.enqueueWork(() ->
         * {
         * ServerPlayer sender = ctx.getSender();
         * if(sender != null)
         * {
         * de.cas_ual_ty.dueldimension.duel.npc.DuelistDuels.surrender(sender);
         * }
         * });
         * ctx.setPacketHandled(true);
         * }
         */
    }
}
