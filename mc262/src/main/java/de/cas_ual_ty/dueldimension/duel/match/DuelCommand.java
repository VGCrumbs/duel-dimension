package de.cas_ual_ty.dueldimension.duel.match;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /duel} — challenge another player, and answer challenges.
 * <p>
 * Registered at the top level rather than under the mod's own command because
 * it is the one command players type constantly, and because the clickable
 * ACCEPT in a challenge runs exactly what a player could type themselves.
 */
public final class DuelCommand
{
    private DuelCommand()
    {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher)
    {
        dispatcher.register(Commands.literal("duel")
            .then(Commands.literal("accept")
                .then(Commands.argument("challenger", StringArgumentType.word())
                    .executes(context -> answer(context.getSource(),
                        StringArgumentType.getString(context, "challenger"), true))))
            .then(Commands.literal("decline")
                .then(Commands.argument("challenger", StringArgumentType.word())
                    .executes(context -> answer(context.getSource(),
                        StringArgumentType.getString(context, "challenger"), false))))
            // The bare form goes last so "accept" and "decline" are not read as
            // player names by the argument parser.
            .then(Commands.argument("target", EntityArgument.player())
                .executes(context -> challenge(context.getSource(),
                    EntityArgument.getPlayer(context, "target")))));
    }

    private static int challenge(CommandSourceStack source, ServerPlayer target)
    {
        ServerPlayer from;
        try
        {
            from = source.getPlayerOrException();
        }
        catch(Exception notAPlayer)
        {
            source.sendFailure(Component.literal("Only a player can challenge someone"));
            return 0;
        }
        String error = DuelInvites.invite(from, target);
        if(error != null)
        {
            source.sendFailure(Component.literal(error));
            return 0;
        }
        return 1;
    }

    private static int answer(CommandSourceStack source, String challenger, boolean accept)
    {
        ServerPlayer player;
        try
        {
            player = source.getPlayerOrException();
        }
        catch(Exception notAPlayer)
        {
            source.sendFailure(Component.literal("Only a player can answer a challenge"));
            return 0;
        }
        String error = accept
            ? DuelInvites.accept(player, challenger)
            : DuelInvites.decline(player, challenger);
        if(error != null)
        {
            source.sendFailure(Component.literal(error));
            return 0;
        }
        if(!accept)
        {
            source.sendSuccess(() -> Component.literal("Challenge declined.")
                .withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }
}
