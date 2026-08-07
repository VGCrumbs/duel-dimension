package de.cas_ual_ty.dueldimension.duel.match;

import de.cas_ual_ty.dueldimension.duel.npc.DuelistDuels;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Challenges between players: {@code /duel <player>}, a clickable acceptance,
 * and the duel that follows.
 * <p>
 * Each invitation owns a {@link MatchStateMachine}, so the same object that
 * governs a lobby also governs the moment before one exists. That is what makes
 * a double-clicked ACCEPT harmless: the second click finds the machine already
 * past {@link MatchState#INVITED} and is refused, rather than starting a second
 * duel over the top of the first.
 */
public final class DuelInvites
{
    /** How long an unanswered invitation stands, in server ticks (60s). */
    public static final int EXPIRY_TICKS = 20 * 60;

    /** One outstanding challenge, keyed by who it was sent TO. */
    private record Invite(UUID challenger, String challengerName, MatchStateMachine machine, long expiresAt)
    {
    }

    private static final Map<UUID, Invite> PENDING = new ConcurrentHashMap<>();

    private DuelInvites()
    {
    }

    /** Sends a challenge. Returns null on success, else why not. */
    public static String invite(ServerPlayer from, ServerPlayer to)
    {
        if(from.getUUID().equals(to.getUUID()))
        {
            return "You cannot duel yourself";
        }
        Invite existing = PENDING.get(to.getUUID());
        if(existing != null && existing.challenger.equals(from.getUUID()))
        {
            return "You have already challenged " + to.getGameProfile().name();
        }

        MatchStateMachine machine = new MatchStateMachine();
        machine.moveTo(MatchState.INVITED);
        PENDING.put(to.getUUID(), new Invite(from.getUUID(), from.getGameProfile().name(),
            machine, to.level().getServer().overworld().getGameTime() + EXPIRY_TICKS));

        from.sendSystemMessage(Component.literal("Challenge sent to ")
            .withStyle(ChatFormatting.GRAY)
            .append(Component.literal(to.getGameProfile().name()).withStyle(ChatFormatting.YELLOW)));

        // The clickable half of the invitation. RUN_COMMAND rather than
        // SUGGEST_COMMAND so accepting is one click, and the command it runs is
        // the same one a player could type, so there is no privileged path.
        to.sendSystemMessage(Component.literal(from.getGameProfile().name())
            .withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(" challenges you to a duel! ").withStyle(ChatFormatting.GOLD))
            .append(button("[ACCEPT]", ChatFormatting.GREEN,
                "/duel accept " + from.getGameProfile().name(), "Accept the challenge"))
            .append(Component.literal(" "))
            .append(button("[DECLINE]", ChatFormatting.RED,
                "/duel decline " + from.getGameProfile().name(), "Turn it down")));
        return null;
    }

    private static Component button(String label, ChatFormatting colour, String command, String tooltip)
    {
        return Component.literal(label).setStyle(Style.EMPTY
            .withColor(colour)
            .withBold(true)
            .withClickEvent(new ClickEvent.RunCommand(command))
            .withHoverEvent(new HoverEvent.ShowText(Component.literal(tooltip))));
    }

    /** Accepts the challenge from this player. Returns null on success, else why not. */
    public static String accept(ServerPlayer target, String challengerName)
    {
        Invite invite = PENDING.get(target.getUUID());
        if(invite == null || !invite.challengerName.equalsIgnoreCase(challengerName))
        {
            return "No pending challenge from " + challengerName;
        }
        // The machine is what makes a second click harmless: CONFIGURING is
        // only reachable from INVITED, so the duplicate is refused here rather
        // than starting a second duel.
        if(!invite.machine.tryMoveTo(MatchState.CONFIGURING))
        {
            return "That challenge is no longer open";
        }

        ServerPlayer challenger = target.level().getServer().getPlayerList().getPlayer(invite.challenger);
        if(challenger == null)
        {
            PENDING.remove(target.getUUID());
            invite.machine.cancel("challenger left");
            return challengerName + " is no longer online";
        }

        PENDING.remove(target.getUUID());

        // Both players go to the lobby. The duel starts from there, once they
        // have agreed a banlist, life points and format and both said ready --
        // CONFIGURING used to be walked straight through on defaults.
        DuelLobby.open(challenger, target, invite.machine);
        return null;
    }

    /** Declines the challenge from this player. */
    public static String decline(ServerPlayer target, String challengerName)
    {
        Invite invite = PENDING.get(target.getUUID());
        if(invite == null || !invite.challengerName.equalsIgnoreCase(challengerName))
        {
            return "No pending challenge from " + challengerName;
        }
        PENDING.remove(target.getUUID());
        invite.machine.cancel("declined");

        ServerPlayer challenger = target.level().getServer().getPlayerList().getPlayer(invite.challenger);
        if(challenger != null)
        {
            challenger.sendSystemMessage(Component.literal(target.getGameProfile().name()
                + " declined your challenge.").withStyle(ChatFormatting.RED));
        }
        return null;
    }

    /**
     * Drops invitations nobody answered. Without this a challenge that is
     * simply ignored would sit in the map forever and block the next one.
     */
    public static void tick(MinecraftServer server)
    {
        if(PENDING.isEmpty())
        {
            return;
        }
        long now = server.overworld().getGameTime();
        for(Iterator<Map.Entry<UUID, Invite>> it = PENDING.entrySet().iterator(); it.hasNext(); )
        {
            Map.Entry<UUID, Invite> entry = it.next();
            if(now < entry.getValue().expiresAt)
            {
                continue;
            }
            it.remove();
            entry.getValue().machine.cancel("timed out");
            ServerPlayer challenger = server.getPlayerList().getPlayer(entry.getValue().challenger);
            if(challenger != null)
            {
                challenger.sendSystemMessage(Component.literal("Your challenge expired.")
                    .withStyle(ChatFormatting.DARK_GRAY));
            }
        }
    }

    /** Forgets everything, for server shutdown. */
    public static void clear()
    {
        PENDING.clear();
    }
}
