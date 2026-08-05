package de.cas_ual_ty.dueldimension.ocg.prompt;

import de.cas_ual_ty.dueldimension.DuelDimension;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * The two packets a duel needs: a prompt going out, and the chosen option
 * indices coming back. Deliberately minimal — the client is told what it may
 * pick and replies with which of those it picked, nothing more.
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
            context.get().enqueueWork(() -> DuelDimension.proxy.openEnginePromptScreen(message.prompt()));
            context.get().setPacketHandled(true);
        }
    }

    /** Client -> server: I picked these. */
    public record AnswerPrompt(int[] chosen)
    {
        public static void encode(AnswerPrompt message, FriendlyByteBuf buffer)
        {
            buffer.writeVarIntArray(message.chosen());
        }

        public static AnswerPrompt decode(FriendlyByteBuf buffer)
        {
            return new AnswerPrompt(buffer.readVarIntArray());
        }

        public static void handle(AnswerPrompt message, Supplier<NetworkEvent.Context> context)
        {
            NetworkEvent.Context ctx = context.get();
            ctx.enqueueWork(() ->
            {
                ServerPlayer sender = ctx.getSender();
                if(sender != null)
                {
                    de.cas_ual_ty.dueldimension.duel.npc.DuelistDuels.submitAnswer(sender, message.chosen());
                }
            });
            ctx.setPacketHandled(true);
        }
    }
}
