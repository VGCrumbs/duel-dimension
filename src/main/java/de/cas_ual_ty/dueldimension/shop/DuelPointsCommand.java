package de.cas_ual_ty.dueldimension.shop;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.List;

/**
 * {@code /dp} — read and adjust Duel Points.
 * <p>
 * A cheat, so it needs permission level 2: DP is meant to be earned, and a
 * command that hands it out is for testing and for operators putting a world
 * right, not for players. Reading your own balance is free, since that is
 * information the shop already shows you.
 */
public final class DuelPointsCommand
{
    /** Command permission level 2 is the vanilla bar for cheats. */
    private static final int CHEAT_LEVEL = 2;

    private DuelPointsCommand()
    {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher)
    {
        dispatcher.register(Commands.literal("dp")
            // Reading your own balance needs no permission.
            .executes(context -> query(context.getSource()))
            .then(Commands.literal("add")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                    .executes(context -> apply(context.getSource(),
                        List.of(context.getSource().getPlayerOrException()),
                        IntegerArgumentType.getInteger(context, "amount"), Mode.ADD))
                    .then(Commands.argument("targets", EntityArgument.players())
                        .executes(context -> apply(context.getSource(),
                            EntityArgument.getPlayers(context, "targets"),
                            IntegerArgumentType.getInteger(context, "amount"), Mode.ADD)))))
            .then(Commands.literal("take")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                    .executes(context -> apply(context.getSource(),
                        List.of(context.getSource().getPlayerOrException()),
                        IntegerArgumentType.getInteger(context, "amount"), Mode.TAKE))
                    .then(Commands.argument("targets", EntityArgument.players())
                        .executes(context -> apply(context.getSource(),
                            EntityArgument.getPlayers(context, "targets"),
                            IntegerArgumentType.getInteger(context, "amount"), Mode.TAKE)))))
            .then(Commands.literal("set")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                    .executes(context -> apply(context.getSource(),
                        List.of(context.getSource().getPlayerOrException()),
                        IntegerArgumentType.getInteger(context, "amount"), Mode.SET))
                    .then(Commands.argument("targets", EntityArgument.players())
                        .executes(context -> apply(context.getSource(),
                            EntityArgument.getPlayers(context, "targets"),
                            IntegerArgumentType.getInteger(context, "amount"), Mode.SET))))));
    }

    private enum Mode
    {
        ADD, TAKE, SET
    }

    private static int query(CommandSourceStack source)
    {
        try
        {
            ServerPlayer player = source.getPlayerOrException();
            int points = DuelPoints.get(player);
            source.sendSuccess(() -> Component.literal("You have ")
                .append(Component.literal(points + " DP").withStyle(ChatFormatting.GOLD)), false);
            return points;
        }
        catch(Exception notAPlayer)
        {
            source.sendFailure(Component.literal("Only a player has a DP balance"));
            return 0;
        }
    }

    private static int apply(CommandSourceStack source, Collection<ServerPlayer> targets,
        int amount, Mode mode)
    {
        for(ServerPlayer player : targets)
        {
            switch(mode)
            {
                case ADD -> DuelPoints.award(player, amount);
                // Spend refuses when short, which is right for a purchase and
                // wrong for an operator taking points away, so this floors at
                // zero instead of failing.
                case TAKE -> DuelPoints.set(player, Math.max(0, DuelPoints.get(player) - amount));
                case SET -> DuelPoints.set(player, amount);
            }
            int now = DuelPoints.get(player);
            // The client caches the balance for the shop badge, so it is told
            // rather than left showing a stale number until the next purchase.
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new ShopMessages.SyncPoints(now));
            source.sendSuccess(() -> Component.literal(player.getGameProfile().name() + " now has ")
                .append(Component.literal(now + " DP").withStyle(ChatFormatting.GOLD)), true);
        }
        return targets.size();
    }
}
