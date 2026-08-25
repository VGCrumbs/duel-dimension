package de.cas_ual_ty.dueldimension.duel.match;

import de.cas_ual_ty.dueldimension.duel.profile.DeckEdits;
import de.cas_ual_ty.dueldimension.duel.profile.DeckList;
import de.cas_ual_ty.dueldimension.duel.profile.DuelProfile;
import de.cas_ual_ty.dueldimension.duel.profile.DuelProfiles;
import de.cas_ual_ty.dueldimension.duel.profile.FreeMode;
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
            start(player.level().getServer(), room);
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
        return DeckEdits.problemsUnder(deck, profile.trunk(), Banlists.byId(config.banlistId()),
            FreeMode.isEnabled(player));
    }

        private static void start(MinecraftServer server, Room room)
        {
            ServerPlayer host = server.getPlayerList().getPlayer(room.host);
            ServerPlayer guest = server.getPlayerList().getPlayer(room.guest);
            close(room);
            if(host == null || guest == null)
            {
                room.machine.cancel("a player left the lobby");
                return;
            }

            for(ServerPlayer player : List.of(host, guest))
            {
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new LobbyMessages.CloseLobby());
            }

            // The toss decides who CHOOSES, not who goes first -- the winner
            // still has to say. So the machine stops at COIN_FLIP here and the
            // duel is started later, by the answer.
            room.machine.tryMoveTo(MatchState.COIN_FLIP);

            // The server's own randomness, and only the server's: a flip the
            // client could see coming is a flip the client could wait out.
            boolean hostWon = server.overworld().getRandom().nextBoolean();
            ServerPlayer winner = hostWon ? host : guest;
            ServerPlayer loser = hostWon ? guest : host;
            String winnerName = winner.getGameProfile().name();

            PENDING.put(winner.getUUID(), new Toss(room.host, room.guest, winner.getUUID(),
                room.machine, room.config, server.overworld().getGameTime() + CHOICE_TIMEOUT_TICKS));

            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(winner,
                new LobbyMessages.CoinToss(true, winnerName));
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(loser,
                new LobbyMessages.CoinToss(false, winnerName));
        }

        /**
         * How long the winner has to choose before the duel starts without
         * them. A duel that waits forever on someone who walked away is worse
         * than one that opens on a default, and the default is the choice
         * almost everybody makes anyway.
         */
        private static final int CHOICE_TIMEOUT_TICKS = 30 * 20;

        /** A toss that has been made and is waiting on its winner. */
        private record Toss(UUID host, UUID guest, UUID winner, MatchStateMachine machine,
            MatchConfig config, long deadline)
        {
        }

        /** Keyed by the winner, because the winner is who may answer. */
        private static final Map<UUID, Toss> PENDING = new ConcurrentHashMap<>();

        /**
         * The winner's answer. Ignored unless this player really is the winner
         * of an outstanding toss, so a client cannot start a duel by asking.
         */
        public static void chooseTurn(ServerPlayer player, boolean goFirst)
        {
            if(player == null)
            {
                return;
            }
            Toss toss = PENDING.remove(player.getUUID());
            if(toss != null)
            {
                begin(player.level().getServer(), toss, goFirst);
            }
        }

        /**
         * Starts the duel a toss decided. The whole of "who goes first" is
         * which player is passed in first -- seat 0 takes the opening turn --
         * so the choice is expressed here and nowhere else.
         */
        private static void begin(MinecraftServer server, Toss toss, boolean winnerFirst)
        {
            if(server == null)
            {
                return;
            }
            ServerPlayer winner = server.getPlayerList().getPlayer(toss.winner());
            UUID otherId = toss.winner().equals(toss.host()) ? toss.guest() : toss.host();
            ServerPlayer other = server.getPlayerList().getPlayer(otherId);
            if(winner == null || other == null)
            {
                toss.machine().cancel("a player left before the duel began");
                return;
            }

            toss.machine().tryMoveTo(MatchState.TURN_CHOICE);
            toss.machine().tryMoveTo(MatchState.DUELING);

            ServerPlayer first = winnerFirst ? winner : other;
            ServerPlayer second = winnerFirst ? other : winner;

            // An overworld duel is the same duel, sited on the ground first.
            // The manager promises to answer exactly once -- start, however the
            // siting went, or cancel only if somebody has actually left -- so
            // the duel cannot be lost to a board that would not fit.
            if(toss.config().isOverworld())
            {
                de.cas_ual_ty.dueldimension.duel.overworld.OverworldDuels.prepare(server, first,
                    second, new de.cas_ual_ty.dueldimension.duel.overworld.OverworldDuels.Outcome()
                    {
                        @Override
                        public void start()
                        {
                            startDuel(server, toss, first.getUUID(), second.getUUID());
                        }

                        @Override
                        public void cancel(String reason)
                        {
                            de.cas_ual_ty.dueldimension.duel.overworld.OverworldDuels
                                .release(server, first.getUUID());
                            toss.machine().cancel(reason);
                        }
                    });
                return;
            }
            startDuel(server, toss, first.getUUID(), second.getUUID());
        }

        /**
         * Starts the duel the toss and the siting settled on.
         * <p>
         * Takes ids rather than players and resolves them again, because an
         * overworld duel can wait half a minute for two people to walk to their
         * marks and a {@code ServerPlayer} does not survive a relog.
         */
        private static void startDuel(MinecraftServer server, Toss toss, UUID firstId,
            UUID secondId)
        {
            ServerPlayer first = server.getPlayerList().getPlayer(firstId);
            ServerPlayer second = server.getPlayerList().getPlayer(secondId);
            if(first == null || second == null)
            {
                de.cas_ual_ty.dueldimension.duel.overworld.OverworldDuels.release(server, firstId);
                toss.machine().cancel("a player left before the duel began");
                return;
            }

            String error = de.cas_ual_ty.dueldimension.duel.npc.DuelistDuels
                .startPlayerDuel(first, second, toss.config());
            if(error != null)
            {
                // The board goes with it: there is no duel for it to stand for.
                de.cas_ual_ty.dueldimension.duel.overworld.OverworldDuels.release(server, firstId);
                toss.machine().cancel(error);
                for(ServerPlayer player : List.of(first, second))
                {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(error)
                        .withStyle(net.minecraft.ChatFormatting.RED));
                }
                return;
            }
            de.cas_ual_ty.dueldimension.duel.npc.DuelistDuels
                .attachMatch(first, toss.machine(), toss.config());
        }

        /**
         * Expires tosses whose winner never answered, starting the duel with
         * them going first. Called every server tick; does nothing at all when
         * no toss is outstanding, which is nearly always.
         */
        public static void tickPending(MinecraftServer server)
        {
            if(PENDING.isEmpty() || server == null)
            {
                return;
            }
            long now = server.overworld().getGameTime();
            for(Map.Entry<UUID, Toss> entry : PENDING.entrySet())
            {
                if(now >= entry.getValue().deadline()
                    && PENDING.remove(entry.getKey(), entry.getValue()))
                {
                    begin(server, entry.getValue(), true);
                }
            }
        }

        /** Drops a disconnecting player's toss, won or lost, so none outlives them. */
        public static void forgetToss(UUID player)
        {
            Toss mine = PENDING.remove(player);
            if(mine != null)
            {
                mine.machine().cancel("a player left before the duel began");
                return;
            }
            PENDING.values().removeIf(toss ->
            {
                boolean theirs = toss.host().equals(player) || toss.guest().equals(player);
                if(theirs)
                {
                    toss.machine().cancel("a player left before the duel began");
                }
                return theirs;
            });
        }

    private static void close(Room room)
    {
        ROOMS.remove(room.host);
        ROOMS.remove(room.guest);
    }

    /** Drops a disconnecting player's room, so a lobby cannot outlive them. */
    public static void forget(ServerPlayer player)
    {
        // The room is gone by the time a toss is outstanding, so the toss has
        // to be dropped separately or the duel starts against an empty seat.
        forgetToss(player.getUUID());
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
