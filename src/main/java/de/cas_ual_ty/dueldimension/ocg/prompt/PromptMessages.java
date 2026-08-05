package de.cas_ual_ty.dueldimension.ocg.prompt;

import de.cas_ual_ty.dueldimension.DuelDimension;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

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
    public record ShowPrompt(EnginePrompt prompt)
    {
        public static void encode(ShowPrompt message, FriendlyByteBuf buffer)
        {
            message.prompt().write(buffer);
        }

        public static ShowPrompt decode(FriendlyByteBuf buffer)
        {
            return new ShowPrompt(EnginePrompt.read(buffer));
        }

        public static void handle(ShowPrompt message, Supplier<NetworkEvent.Context> context)
        {
            context.get().enqueueWork(() -> DuelDimension.proxy.showEnginePrompt(message.prompt()));
            context.get().setPacketHandled(true);
        }
    }

    /** Client -> server: I picked these (indices; declaredCode for name declares). */
    public record AnswerPrompt(int[] chosen, int declaredCode)
    {
        public static void encode(AnswerPrompt message, FriendlyByteBuf buffer)
        {
            buffer.writeVarIntArray(message.chosen());
            buffer.writeVarInt(message.declaredCode());
        }

        public static AnswerPrompt decode(FriendlyByteBuf buffer)
        {
            return new AnswerPrompt(buffer.readVarIntArray(), buffer.readVarInt());
        }

        public static void handle(AnswerPrompt message, Supplier<NetworkEvent.Context> context)
        {
            NetworkEvent.Context ctx = context.get();
            ctx.enqueueWork(() ->
            {
                ServerPlayer sender = ctx.getSender();
                if(sender != null)
                {
                    de.cas_ual_ty.dueldimension.duel.npc.DuelistDuels.submitAnswer(sender,
                        new HumanResponseSource.Answer(message.chosen(), message.declaredCode()));
                }
            });
            ctx.setPacketHandled(true);
        }
    }

    /**
     * Server -> client: the duel moved. Board is optional (log-only updates
     * skip it); {@code over} closes out the duel with a result line.
     */
    public record DuelUpdate(BoardSnapshot board, List<String> log, boolean over, String result, int[] warmUp)
    {
        public DuelUpdate(BoardSnapshot board, List<String> log, boolean over, String result)
        {
            this(board, log, over, result, new int[0]);
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
                buffer.readVarIntArray());
        }

        public static void handle(DuelUpdate message, Supplier<NetworkEvent.Context> context)
        {
            context.get().enqueueWork(() -> DuelDimension.proxy.updateEngineDuel(message));
            context.get().setPacketHandled(true);
        }
    }

    /** Client -> server: change how chain windows are answered. */
    public record SetChainPreference(ChainPreference preference)
    {
        public static void encode(SetChainPreference message, FriendlyByteBuf buffer)
        {
            buffer.writeEnum(message.preference());
        }

        public static SetChainPreference decode(FriendlyByteBuf buffer)
        {
            return new SetChainPreference(buffer.readEnum(ChainPreference.class));
        }

        public static void handle(SetChainPreference message, Supplier<NetworkEvent.Context> context)
        {
            NetworkEvent.Context ctx = context.get();
            ctx.enqueueWork(() ->
            {
                ServerPlayer sender = ctx.getSender();
                if(sender != null)
                {
                    de.cas_ual_ty.dueldimension.duel.npc.DuelistDuels.setChainPreference(sender,
                        message.preference());
                }
            });
            ctx.setPacketHandled(true);
        }
    }

    /** Client -> server: I give up. */
    public record Surrender()
    {
        public static void encode(Surrender message, FriendlyByteBuf buffer)
        {
        }

        public static Surrender decode(FriendlyByteBuf buffer)
        {
            return new Surrender();
        }

        public static void handle(Surrender message, Supplier<NetworkEvent.Context> context)
        {
            NetworkEvent.Context ctx = context.get();
            ctx.enqueueWork(() ->
            {
                ServerPlayer sender = ctx.getSender();
                if(sender != null)
                {
                    de.cas_ual_ty.dueldimension.duel.npc.DuelistDuels.surrender(sender);
                }
            });
            ctx.setPacketHandled(true);
        }
    }
}
