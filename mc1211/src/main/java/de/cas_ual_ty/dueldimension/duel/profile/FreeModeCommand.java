package de.cas_ual_ty.dueldimension.duel.profile;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * {@code /dueldimension freemode <true|false>} — build with any card, or only
 * with the ones you own.
 * <p>
 * Permission level 2, the vanilla bar for a cheat, and for the same reason
 * {@code /dp} needs it: this decides whether the collection means anything, and
 * that is an operator's call rather than a player's.
 */
public final class FreeModeCommand
{
    private static final int CHEAT_LEVEL = 2;

    private FreeModeCommand()
    {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher)
    {
        dispatcher.register(Commands.literal("dueldimension")
            .then(Commands.literal("freemode")
                // Reading the setting is free: a player needs to know whether
                // the deck they are building will be playable.
                .executes(context -> report(context.getSource()))
                .then(Commands.argument("enabled", BoolArgumentType.bool())
                    .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .executes(context -> set(context.getSource(),
                        BoolArgumentType.getBool(context, "enabled"))))));
    }

    private static int report(CommandSourceStack source)
    {
        boolean on = FreeMode.isEnabled(source.getServer());
        source.sendSuccess(() -> Component.literal("Free mode is ")
            .append(Component.literal(on ? "on" : "off")
                .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.YELLOW))
            .append(Component.literal(on
                ? " - any card may be used in a deck."
                : " - decks may only use cards you own.")), false);
        return on ? 1 : 0;
    }

    private static int set(CommandSourceStack source, boolean enabled)
    {
        FreeMode.set(source.getServer(), enabled);
        de.cas_ual_ty.dueldimension.net.ProfilePayloads.syncFreeMode(source.getServer());
        // Announced to everyone, because it changes whether other players'
        // decks are legal and they would otherwise find out at a duel.
        source.getServer().getPlayerList().broadcastSystemMessage(
            Component.literal(enabled
                    ? "Free mode is ON - any card may be used in a deck."
                    : "Free mode is OFF - decks using cards you do not own cannot be duelled with.")
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.YELLOW), false);
        source.sendSuccess(() -> Component.literal("Free mode " + (enabled ? "enabled" : "disabled")), true);
        return enabled ? 1 : 0;
    }
}
