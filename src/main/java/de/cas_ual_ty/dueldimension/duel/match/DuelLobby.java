package de.cas_ual_ty.dueldimension.duel.match;

import de.cas_ual_ty.dueldimension.duel.profile.DeckEdits;
import de.cas_ual_ty.dueldimension.duel.profile.DeckList;
import de.cas_ual_ty.dueldimension.duel.profile.DuelProfile;
import de.cas_ual_ty.dueldimension.duel.profile.DuelProfiles;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The room two players sit in between accepting a challenge and duelling.
 * <p>
 * This is the {@link MatchState#CONFIGURING} step, which existed as a state and
 * never as a place: accepting a challenge walked straight through it on
 * defaults, so a banlist could be chosen nowhere and a deck was never checked
 * against one before the duel began.
 * <p>
 * The challenger owns the settings. Both sides see them, both must declare
 * themselves ready, and a change by the host un-readies everyone — agreeing to
 * a format and then having it changed underneath you is the one thing a lobby
 * exists to prevent.
 */
public final class DuelLobby
{
    /** One lobby, keyed from both players so either can reach it. */
    public static final class Room
    {
        final UUID host;
        final UUID guest;
        final MatchStateMachine machine;
        MatchConfig config = MatchConfig.DEFAULT;
        boolean hostReady;
        boolean guestReady;

        Room(UUID host, UUID guest, MatchStateMachine machine)
        {
            this.host = host;
            this.guest = guest;
            this.machine = machine;
        }

        public MatchConfig config()
        {
            return config;
        }

        public boolean isHost(ServerPlayer player)
        {
            return host.equals(player.getUUID());
        }

        public boolean readyFor(ServerPlayer player)
        {
            return isHost(player) ? hostReady : guestReady;
        }

        public boolean bothReady()
        {
            return hostReady && guestReady;
        }
    }

    private static final Map<UUID, Room> ROOMS = new ConcurrentHashMap<>();

    private DuelLobby()
    {
    }

    public static Room roomOf(ServerPlayer player)
    {
        return player == null ? null : ROOMS.get(player.getUUID());
    }

    /** Puts two players in a room and shows it to both. */
    public static void open(ServerPlayer host, ServerPlayer guest, MatchStateMachine machine)
    {
        Room room = new Room(host.getUUID(), guest.getUUID(), machine);
        ROOMS.put(host.getUUID(), room);
        ROOMS.put(guest.getUUID(), room);
        sendTo(host, room);
        sendTo(guest, room);
    }

    /**
     * Changes a setting. Only the host may, and any change un-readies both:
     * a player who agreed to one format has not agreed to another.
     */
    public static void configure(ServerPlayer player, MatchConfig proposed)
    {
        Room room = roomOf(player);
        if(room == null || !room.isHost(player))
        {
            return;
        }
        // Sanitised rather than trusted: a packet is data, and the offered
        // choices are the only legal ones.
        room.config = proposed.sanitised();
        room.hostReady = false;
        room.guestReady = false;
        broadcast(player.level().getServer(), room);
    }

    /**
     * Declares a player ready, or takes it back. A player whose deck will not
     * do cannot become ready: the lobby is where that is found out, rather than
     * at the moment the duel refuses to start.
     */
    public static void ready(ServerPlayer player, boolean ready)
    {
        Room room = roomOf(player);
        if(room == null)
        {
            return;
        }
        if(ready && !problemsFor(player, room.config).isEmpty())
        {
            return;
        }
        if(room.isHost(player))
        {
            room.hostReady = ready;
        }
        else
        {
            room.guestReady = ready;
        }
        broadcast(player.level().getServer(), room);

        if(room.bothReady())
        {
            // Where the duel would begin. start() is parked below with the
            // duel-session phase, so both players simply stay ready until it
            // lands rather than the lobby pretending to have started one.
            de.cas_ual_ty.dueldimension.DuelDimension.log(
                "Both duellists are ready; starting a duel needs the session phase.");
        }
    }

    /** Leaves the room, cancelling the match for both. */
    public static void leave(ServerPlayer player)
    {
        Room room = roomOf(player);
        if(room == null)
        {
            return;
        }
        close(room);
        room.machine.cancel("lobby left");
        MinecraftServer server = player.level().getServer();
        for(UUID id : List.of(room.host, room.guest))
        {
            ServerPlayer other = server.getPlayerList().getPlayer(id);
            if(other != null && !other.getUUID().equals(player.getUUID()))
            {
                other.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    player.getGameProfile().name() + " left the lobby.")
                    .withStyle(net.minecraft.ChatFormatting.YELLOW));
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(other, new LobbyMessages.CloseLobby());
            }
        }
    }

    /**
     * Why this player's chosen deck will not do under these settings, empty if
     * it will.
     * <p>
     * The banlist is the reason this matters: a deck legal with no list can be
     * illegal under a real one, and that is exactly what the lobby is for
     * finding out before anyone commits to a duel.
     */
    public static List<String> problemsFor(ServerPlayer player, MatchConfig config)
    {
        DuelProfile profile = DuelProfiles.get(player);
        String active = profile.activeDeck();
        if(active.isEmpty())
        {
            return List.of("No deck chosen");
        }
        DeckList deck = profile.deckNamed(active);
        if(deck == null)
        {
            return List.of("No deck chosen");
        }
        return DeckEdits.problemsUnder(deck, profile.trunk(), Banlists.byId(config.banlistId()));
    }

    /*
     * Parked with the duel-session phase: starting a match hands off to
     * DuelistDuels, which drives the engine and has not been ported. The
     * lobby up to this point -- opening, configuring, readying, leaving --
     * works without it.
     *     private static void start(MinecraftServer server, Room room)
     *     {
     *         ServerPlayer host = server.getPlayerList().getPlayer(room.host);
     *         ServerPlayer guest = server.getPlayerList().getPlayer(room.guest);
     *         close(room);
     *         if(host == null || guest == null)
     *         {
     *             room.machine.cancel("a player left the lobby");
     *             return;
     *         }
     * 
     *         for(ServerPlayer player : List.of(host, guest))
     *         {
     *             net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new LobbyMessages.CloseLobby());
     *         }
     * 
     *         // The coin flip and the turn choice are still walked through rather
     *         // than played: they are the next thing to build, and the machine
     *         // records that they happened so the states stay honest.
     *         room.machine.tryMoveTo(MatchState.COIN_FLIP);
     *         room.machine.tryMoveTo(MatchState.TURN_CHOICE);
     *         room.machine.tryMoveTo(MatchState.DUELING);
     * 
     *         String error = de.cas_ual_ty.dueldimension.duel.npc.DuelistDuels
     *             .startPlayerDuel(host, guest, room.config);
     *         if(error != null)
     *         {
     *             room.machine.cancel(error);
     *             host.sendSystemMessage(net.minecraft.network.chat.Component.literal(error)
     *                 .withStyle(net.minecraft.ChatFormatting.RED));
     *             guest.sendSystemMessage(net.minecraft.network.chat.Component.literal(error)
     *                 .withStyle(net.minecraft.ChatFormatting.RED));
     *             return;
     *         }
     *         de.cas_ual_ty.dueldimension.duel.npc.DuelistDuels
     *             .attachMatch(host, room.machine, room.config);
     *     }
     */

    private static void close(Room room)
    {
        ROOMS.remove(room.host);
        ROOMS.remove(room.guest);
    }

    /** Drops a disconnecting player's room, so a lobby cannot outlive them. */
    public static void forget(ServerPlayer player)
    {
        Room room = roomOf(player);
        if(room != null)
        {
            leave(player);
        }
    }

    private static void broadcast(MinecraftServer server, Room room)
    {
        for(UUID id : List.of(room.host, room.guest))
        {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if(player != null)
            {
                sendTo(player, room);
            }
        }
    }

    private static void sendTo(ServerPlayer player, Room room)
    {
        MinecraftServer server = player.level().getServer();
        ServerPlayer host = server.getPlayerList().getPlayer(room.host);
        ServerPlayer guest = server.getPlayerList().getPlayer(room.guest);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new LobbyMessages.OpenLobby(
                room.config,
                room.isHost(player),
                host == null ? "?" : host.getGameProfile().name(),
                guest == null ? "?" : guest.getGameProfile().name(),
                room.hostReady,
                room.guestReady,
                Banlists.all().stream().map(Banlist::id).toList(),
                Banlists.all().stream().map(Banlist::displayName).toList(),
                problemsFor(player, room.config)));
    }
}
