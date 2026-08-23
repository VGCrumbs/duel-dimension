package de.cas_ual_ty.dueldimension.duel.overworld;

import de.cas_ual_ty.dueldimension.duel.overworld.FieldValidator.Refusal;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Duels played on a board built in the world: siting them, walking the players
 * to their marks, holding them there, and taking the board away again.
 * <p>
 * <b>The one invariant.</b> This class can never stop a duel from happening. The
 * two players agreed a duel, and where it is drawn is not part of that
 * agreement -- so every path through here ends in either {@link Outcome#start()}
 * or, only when a player has actually gone, {@link Outcome#cancel}. A field that
 * cannot be sited, a player who never walks over, a board that gets built on:
 * all of them fall back to the ordinary duel screen with the duel intact. That
 * is also why a griefer cannot cost anyone a game by dropping a block on the
 * field.
 * <p>
 * Everything geometric is delegated to {@link SitingSearch} and
 * {@link FieldValidator}, which are pure and tested; what lives here is the
 * server-side lifecycle those two cannot express.
 * <p>
 * <b>Entities are deliberately not a disruption.</b> A cow wandering across the
 * field does not invalidate it: the board is a projection and passes through
 * whatever walks over it, and living things move, so a duel that fell back
 * because a bat flew past would fall back constantly for no benefit anyone
 * could see. The things that DO pull a board down are the ones that persist and
 * that a duellist cannot play around -- the ground going away, a wall going up,
 * a player leaving, dying or being moved.
 */
public final class OverworldDuels
{
    private OverworldDuels()
    {
    }

    /** What the caller wants done once this class has finished deciding. */
    public interface Outcome
    {
        /** Start the duel now. Called exactly once, board or no board. */
        void start();

        /** Do not start it: somebody is no longer here. */
        void cancel(String reason);

        /**
         * No board could be built. By default the duel happens anyway, on the
         * screen -- which is right for two players who have already agreed a
         * match and had a coin tossed for it, because there is a state machine
         * waiting on a duel and nothing to be gained by stranding it.
         * <p>
         * It is NOT right for a player who has just picked "overworld board"
         * out of a menu. Starting the other kind of duel silently answers a
         * question they had already answered, so that caller overrides this to
         * explain and stop.
         */
        default void refused(String reason)
        {
            start();
        }
    }

    /** How long the two get to walk to their marks before the duel gives up on the idea. */
    public static final int WALK_TIMEOUT_TICKS = 30 * 20;

    /**
     * How far off their mark a duellist may be and still count as arrived. A
     * block, because a player aiming at a marked square lands on it and not on
     * its centre, and the lock snaps them the rest of the way.
     */
    private static final double ARRIVAL_TOLERANCE = 1.0D;

    /**
     * How far a locked duellist may drift before the server puts them back.
     * Generous enough that knockback jitter does not fight the client, tight
     * enough that a player cannot walk off the mark.
     */
    private static final double DRIFT_TOLERANCE = 0.6D;

    /**
     * Past this, they did not drift -- something moved them. A command, a
     * pearl, a portal, a plugin. The lock exists to stop a player WALKING away,
     * not to drag them back across the world against something that outranks
     * it, so this is a fall-back and not a longer piece of elastic.
     */
    private static final double TELEPORTED_AWAY = 8D;

    /** A duel whose players have been shown their marks and have not both reached them. */
    private record Waiting(UUID seat0, UUID seat1, ResourceKey<Level> level, FieldSiting siting,
        long deadline, Outcome outcome)
    {
    }

    /** A duel being played on a board. */
    public record Board(UUID seat0, UUID seat1, ResourceKey<Level> level, FieldSiting siting)
    {
        /** Which seat this player has, or -1. */
        public int seatOf(UUID player)
        {
            if(seat0.equals(player))
            {
                return 0;
            }
            return seat1.equals(player) ? 1 : -1;
        }
    }

    // Both maps are keyed by BOTH players, so any question about one player is
    // one lookup. Membership is the state: a player is locked exactly when they
    // are in BOARDS, which means a crash between two writes can strand nobody.
    private static final Map<UUID, Waiting> WAITING = new ConcurrentHashMap<>();
    private static final Map<UUID, Board> BOARDS = new ConcurrentHashMap<>();

    /**
     * The last public view of each board, and who is currently watching it.
     * <p>
     * Both keyed by seat zero, which names a board without being a board: a
     * Board is a record, so two duels laid out identically would be one key.
     * <p>
     * These exist because a spectator used to be told about a duel ONLY at the
     * moment the board changed -- the field and the view went out together, in
     * the same broadcast, to whoever happened to be standing there at the time.
     * Walk up during a long think, a chain window or anybody's prompt and there
     * was nothing to see and nothing coming: the next board change might be
     * half a minute away, and until then a duel in front of you was bare
     * ground. Keeping the last view means somebody arriving can be shown the
     * duel as it stands rather than as it will next be.
     */
    private static final Map<UUID, de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot>
        PUBLIC_VIEW = new ConcurrentHashMap<>();
    private static final Map<UUID, java.util.Set<UUID>> WATCHERS = new ConcurrentHashMap<>();

    /** The board this player is duelling on, or null. */
    public static Board boardOf(UUID player)
    {
        return BOARDS.get(player);
    }

    /** Is this player standing at a board right now, and so not free to walk away? */
    public static boolean isLocked(UUID player)
    {
        return BOARDS.containsKey(player);
    }

    /** Is this player either walking to a mark or standing at a board? */
    public static boolean isEngaged(UUID player)
    {
        return WAITING.containsKey(player) || BOARDS.containsKey(player);
    }

    /**
     * Site a duel and either start it now or after both players have walked to
     * their marks.
     *
     * @param first  seat 0, who takes the opening turn
     * @param second seat 1
     */
    public static void prepare(MinecraftServer server, ServerPlayer first, ServerPlayer second,
        Outcome outcome)
    {
        if(first.level() != second.level())
        {
            // Nothing to site: they are not in the same world to build in.
            refuse(first, second, Refusal.DIFFERENT_WORLD);
            outcome.start();
            return;
        }
        de.cas_ual_ty.dueldimension.DuelDimension.log("siting a duel between "
            + first.getGameProfile().name() + " and " + second.getGameProfile().name());

        ServerLevel level = (ServerLevel)first.level();
        LevelSampler sampler = new LevelSampler(level);
        BlockPos floorA = floorUnder(first);
        BlockPos floorB = floorUnder(second);

        // A marked arena wins outright. The search below exists to GUESS
        // where a duel should be held; somebody who has put four corners and
        // two player points in the ground has already answered, and a guess
        // that overrides an answer is not a feature.
        FieldSiting built = markedArena(level, floorA, floorB, floorA);
        if(built != null)
        {
            lock(first, built, 0);
            lock(second, built, 1);
            open(first, second, level, built);
            outcome.start();
            return;
        }

        // The live setting, not a constant: the board can be resized in game.
        // Whatever it is at this moment is frozen into the siting and travels
        // to both clients with it, so a duel is never resized underneath it.
        SitingResult result = SitingSearch.site(sampler, floorA, floorB, FieldSpec.current());
        if(result instanceof SitingResult.Refused refused)
        {
            refuse(first, second, refused.reason());
            outcome.start();
            return;
        }

        FieldSiting siting = result.siting();
        if(result instanceof SitingResult.Ready)
        {
            lock(first, siting, 0);
            lock(second, siting, 1);
            open(first, second, level, siting);
            outcome.start();
            return;
        }

        // They have to walk. The markers go down, the duel waits, and the
        // deadline guarantees the wait ends.
        Waiting waiting = new Waiting(first.getUUID(), second.getUUID(), level.dimension(),
            siting, level.getGameTime() + WALK_TIMEOUT_TICKS, outcome);
        WAITING.put(first.getUUID(), waiting);
        WAITING.put(second.getUUID(), waiting);
        show(first, siting, 0, false);
        show(second, siting, 1, false);
        tell(first, Component.literal("Stand on the marked square to begin the duel")
            .withStyle(ChatFormatting.YELLOW));
        tell(second, Component.literal("Stand on the marked square to begin the duel")
            .withStyle(ChatFormatting.YELLOW));
    }

    /**
     * Sites a duel between a player and something that is not one -- an NPC
     * duelist standing at a table.
     * <p>
     * The opponent does not walk to its mark and is not sent anything: it has
     * no client to tell and no legs worth waiting for, so it is simply put
     * where it belongs. Only the player has to arrive.
     * <p>
     * Everything else is the two-player path: the same siting, the same board,
     * the same promise that a duel happens whether or not a field could be
     * found for it.
     */
    public static void prepareAgainst(MinecraftServer server, ServerPlayer player,
        net.minecraft.world.entity.LivingEntity opponent, Outcome outcome)
    {
        if(server == null || player.level() != opponent.level())
        {
            // Was silent, which made it indistinguishable from a duel that had
            // never asked for a board -- and silence is exactly what made this
            // undiagnosable the first two times it was reported.
            de.cas_ual_ty.dueldimension.DuelDimension.warn("cannot site a duel for "
                + player.getGameProfile().name() + ": server=" + (server != null)
                + " sameLevel=" + (player.level() == opponent.level()));
            tell(player, refusalMessage(Refusal.DIFFERENT_WORLD));
            outcome.refused(Refusal.DIFFERENT_WORLD.name());
            return;
        }
        ServerLevel level = (ServerLevel)player.level();
        BlockPos playerFloor = floorUnder(player);
        BlockPos opponentFloor = opponent.blockPosition().below();

        // The same arena, and the same reasoning. A duelist standing at a
        // marked board is exactly the case somebody built the board FOR.
        FieldSiting built = markedArena(level, playerFloor, opponentFloor, playerFloor);
        if(built != null)
        {
            placeOpponent(opponent, built);
            lock(player, built, 0);
            openAgainst(player, opponent, level, built);
            outcome.start();
            return;
        }

        SitingResult result = SitingSearch.site(new LevelSampler(level), playerFloor,
            opponentFloor, FieldSpec.current());
        de.cas_ual_ty.dueldimension.DuelDimension.log("siting against "
            + opponent.getType() + " for " + player.getGameProfile().name() + ": "
            + result.getClass().getSimpleName()
            + (result.siting() == null ? "" : " at " + result.siting().anchor()
                + " facing " + result.siting().facing()));
        if(result instanceof SitingResult.Refused refused)
        {
            // The spec goes in the log with it: "there was no room" is a very
            // different report depending on whether the field being asked for
            // was eleven by nine or thirty-one by thirty-one, and the answer is
            // a setting the player can change.
            de.cas_ual_ty.dueldimension.DuelDimension.log("  refused: " + refused.reason()
                + ", asking for " + FieldSpec.current().areaWidth() + "x"
                + FieldSpec.current().areaDepth() + " with "
                + FieldSpec.current().clearance() + " headroom, searching "
                + FieldSpec.current().searchRadius() + " blocks out");
            tell(player, refusalMessage(refused.reason()));
            outcome.refused(refused.reason().name());
            return;
        }

        FieldSiting siting = result.siting();
        // The opponent is placed rather than asked. Seat 1 by convention, the
        // same as the second player in a two-player duel.
        placeOpponent(opponent, siting);
        if(result instanceof SitingResult.Ready)
        {
            lock(player, siting, 0);
            openAgainst(player, opponent, level, siting);
            outcome.start();
            return;
        }

        Waiting waiting = new Waiting(player.getUUID(), opponent.getUUID(), level.dimension(),
            siting, level.getGameTime() + WALK_TIMEOUT_TICKS, outcome);
        WAITING.put(player.getUUID(), waiting);
        show(player, siting, 0, false);
        tell(player, Component.literal("Stand on the marked square to begin the duel")
            .withStyle(ChatFormatting.YELLOW));
    }

    /**
     * The hand-built arena these two are standing at, or null.
     * <p>
     * Looked for around the point BETWEEN them, so it is found whether they
     * came at it from the same end or from opposite ones, and seats are handed
     * out by proximity: the player nearer a point gets that point. Nobody is
     * asked to walk anywhere -- a marked arena says exactly where two duellists
     * belong, so they are simply put there, which is the whole reason for
     * marking one.
     *
     * @param seatZeroNear whichever duellist should be treated as seat zero,
     *                     given as the block they are standing on
     */
    private static FieldSiting markedArena(ServerLevel level, BlockPos floorA, BlockPos floorB,
        BlockPos seatZeroNear)
    {
        BlockPos between = new BlockPos(Math.floorDiv(floorA.getX() + floorB.getX(), 2),
            Math.floorDiv(floorA.getY() + floorB.getY(), 2),
            Math.floorDiv(floorA.getZ() + floorB.getZ(), 2));
        de.cas_ual_ty.dueldimension.duel.overworld.arena.Arena.Built arena =
            de.cas_ual_ty.dueldimension.duel.overworld.arena.ArenaScan.find(level, between);
        if(arena == null)
        {
            return null;
        }
        // Both marks have to be free of anybody who is not about to duel on
        // them. A point is where a duellist is PUT, not where they walk to, so
        // a bystander standing on one is not moved aside -- the duellist is
        // teleported into them, and two bodies in one block is the sort of
        // thing that ends with somebody suffocated in a wall or shoved through
        // the arena.
        //
        // Refused rather than worked around: this arena is unusable while
        // somebody is standing in it, and returning null puts the duel back on
        // the ordinary siting search, which finds open ground. A duel that
        // moves is better than a duel that lands on a person.
        if(taken(level, arena.first(), floorA, floorB)
            || taken(level, arena.second(), floorA, floorB))
        {
            de.cas_ual_ty.dueldimension.DuelDimension.log(
                "a marked arena was skipped: somebody is standing on a player point");
            return null;
        }
        FieldSiting siting = arena.siting(FieldSpec.current(), arena.nearest(seatZeroNear));
        de.cas_ual_ty.dueldimension.DuelDimension.log("using a marked arena: "
            + siting.spec().areaWidth() + "x" + siting.spec().areaDepth()
            + " anchored at " + siting.anchor() + " facing " + siting.facing()
            + ", seats at " + siting.stand(0) + " and " + siting.stand(1));
        return siting;
    }

    /**
     * Is somebody standing on this player point who is not one of the two about
     * to duel here?
     * <p>
     * The duellists themselves do not count, and cannot: standing on your mark
     * before the duel starts is the ordinary way to ask for one, and the whole
     * point of a marked arena is that walking to it is the setup. They are
     * recognised by the floor they are standing on, which is the same thing the
     * caller matched the arena against.
     * <p>
     * A whole block either way, because a mark is a block and anything inside
     * it is in the way of what is about to be put there.
     */
    private static boolean taken(ServerLevel level, BlockPos point, BlockPos floorA,
        BlockPos floorB)
    {
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(point);
        for(net.minecraft.world.entity.LivingEntity body : level.getEntitiesOfClass(
            net.minecraft.world.entity.LivingEntity.class, box))
        {
            BlockPos where = body.blockPosition();
            if(where.equals(floorA) || where.equals(floorB)
                || where.equals(floorA.above()) || where.equals(floorB.above()))
            {
                continue;
            }
            return true;
        }
        return false;
    }

    /** Stands the opponent on its mark, facing across the board. */
    private static void placeOpponent(net.minecraft.world.entity.LivingEntity opponent,
        FieldSiting siting)
    {
        BlockPos stand = siting.stand(1);
        Direction look = siting.look(1);
        opponent.snapTo(stand.getX() + 0.5D, stand.getY() + 1, stand.getZ() + 0.5D,
            look.toYRot(), 0F);
        opponent.setYHeadRot(look.toYRot());
    }

    private static void openAgainst(ServerPlayer player,
        net.minecraft.world.entity.LivingEntity opponent, ServerLevel level, FieldSiting siting)
    {
        LINGERING.remove(player.getUUID());
        Board board = new Board(player.getUUID(), opponent.getUUID(), level.dimension(), siting);
        BOARDS.put(player.getUUID(), board);
        show(player, siting, 0, true);
    }

    /**
     * Watches for arrivals, expires waits, and keeps locked duellists on their
     * marks. Cheap when nothing is happening, which is nearly always: two empty
     * maps and an immediate return.
     */
    public static void tick(MinecraftServer server)
    {
        if(!WAITING.isEmpty())
        {
            tickWaiting(server);
        }
        if(!BOARDS.isEmpty())
        {
            tickBoards(server);
        }
        if(!LINGERING.isEmpty())
        {
            expireLingering(server);
        }
    }

    /**
     * Takes down a finished board the client never got round to clearing.
     * <p>
     * A client that ran its ending has already cleared its own copy, so this
     * usually sends a hide to somebody who has nothing to hide. That is the
     * point: the fade is driven where the animations are, and this only catches
     * the client that disconnected, crashed, or walked into another dimension
     * halfway through being told it had won.
     */
    private static void expireLingering(MinecraftServer server)
    {
        long now = server.overworld().getGameTime();
        LINGERING.entrySet().removeIf(entry ->
        {
            if(now < entry.getValue())
            {
                return false;
            }
            hideIfOnline(server, entry.getKey());
            return true;
        });
    }

    private static void tickWaiting(MinecraftServer server)
    {
        // Collected first because both keys point at one Waiting and settling
        // it removes both -- iterating the map while doing that is asking for
        // a duel to be started twice.
        List<Waiting> pending = new ArrayList<>(new java.util.LinkedHashSet<>(WAITING.values()));
        for(Waiting waiting : pending)
        {
            ServerPlayer first = server.getPlayerList().getPlayer(waiting.seat0());
            ServerPlayer second = server.getPlayerList().getPlayer(waiting.seat1());
            // Seat 1 may be an NPC, which is never in the player list. It was
            // put on its mark when the field was sited and does not have to be
            // waited for.
            boolean againstEntity = !BOARDS.containsKey(waiting.seat1())
                && second == null && server.getPlayerList().getPlayer(waiting.seat0()) != null
                && waiting.seat1() != null && !isOnline(server, waiting.seat1());
            if(first == null || (second == null && !againstEntity))
            {
                settle(waiting);
                waiting.outcome().cancel("a player left before the duel began");
                continue;
            }

            ServerLevel level = server.getLevel(waiting.level());
            if(level == null || first.level() != level
                || (second != null && second.level() != level))
            {
                // Somebody stepped through a portal. The duel is still on; the
                // board is not.
                settle(waiting);
                hide(first);
                hide(second);
                tell(first, refusalMessage(Refusal.DIFFERENT_WORLD));
                tell(second, refusalMessage(Refusal.DIFFERENT_WORLD));
                // second may be absent for a duel against an NPC; tell and hide
                // both tolerate that.
                waiting.outcome().start();
                continue;
            }

            if(arrived(first, waiting.siting(), 0)
                && (second == null || arrived(second, waiting.siting(), 1)))
            {
                settle(waiting);
                lock(first, waiting.siting(), 0);
                if(second != null)
                {
                    lock(second, waiting.siting(), 1);
                    open(first, second, level, waiting.siting());
                }
                else
                {
                    Board board = new Board(first.getUUID(), waiting.seat1(), level.dimension(),
                        waiting.siting());
                    BOARDS.put(first.getUUID(), board);
                    show(first, waiting.siting(), 0, true);
                }
                waiting.outcome().start();
                continue;
            }

            if(level.getGameTime() >= waiting.deadline())
            {
                settle(waiting);
                hide(first);
                hide(second);
                Component gaveUp = Component.literal(
                    "Nobody reached the duel field; playing on the duel screen instead")
                    .withStyle(ChatFormatting.YELLOW);
                tell(first, gaveUp);
                tell(second, gaveUp);
                waiting.outcome().start();
            }
        }
    }

    /**
     * How often a running board re-checks the ground it was built on. Once a
     * second: a 9x9 field is a few hundred block reads and a duel lasts
     * minutes, so this is nothing, and a board somebody has built a wall
     * through should not survive a whole turn.
     */
    private static final int INTEGRITY_INTERVAL = 20;

    private static void tickBoards(MinecraftServer server)
    {
        for(Board board : new java.util.LinkedHashSet<>(BOARDS.values()))
        {
            // A board belongs to one world. Without this the hold would keep
            // teleporting a duellist to the board's x/y/z in whatever dimension
            // they had ended up in -- pinning them to a spot in the Nether that
            // corresponds to nothing, every tick, until the duel ended.
            if(elsewhere(server, board, 0) || elsewhere(server, board, 1))
            {
                release(server, board.seat0());
                Component left = Component.literal(
                    "The duel field was left behind; playing on the duel screen instead")
                    .withStyle(ChatFormatting.YELLOW);
                tell(server.getPlayerList().getPlayer(board.seat0()), left);
                tell(server.getPlayerList().getPlayer(board.seat1()), left);
                continue;
            }
            ServerLevel level = server.getLevel(board.level());
            if(level == null)
            {
                continue;
            }

            tickWatchers(server, level, board);

            // A player who has died is not standing anywhere, and holding a
            // corpse on its mark would be both grim and useless. The duel
            // carries on, on the screen, exactly as it does for every other
            // disruption.
            if(dead(server, board, 0) || dead(server, board, 1))
            {
                fallBack(server, board, "A duellist fell");
                continue;
            }

            // Something moved a duellist that outranks a duel: a command, a
            // pearl, a plugin. Yanking them back would be the lock fighting the
            // server, so the board yields instead.
            if(taken(server, board, 0) || taken(server, board, 1))
            {
                fallBack(server, board, "A duellist was moved away from the field");
                continue;
            }

            // Somebody built a wall through the field, or dug the floor out
            // from under it. Asked with the SAME validator that accepted the
            // site in the first place, against the same footprint, so a board
            // can never be pulled down for failing a check it never passed.
            // Not for a marked arena. That check exists to notice somebody
            // building into ground the game PICKED, and a board somebody laid
            // out by hand is standing on whatever they laid it out on -- so
            // asking would abandon the duel for the crime of being held
            // somewhere with a decorated floor.
            if(!board.siting().marked() && level.getGameTime() % INTEGRITY_INTERVAL == 0
                && FieldValidator.check(new LevelSampler(level), board.siting()) != null)
            {
                fallBack(server, board, "The duel field was disturbed");
                continue;
            }

            for(int seat = 0; seat < 2; seat++)
            {
                ServerPlayer player = server.getPlayerList()
                    .getPlayer(seat == 0 ? board.seat0() : board.seat1());
                if(player == null)
                {
                    continue;
                }
                hold(player, board.siting(), seat);
            }
        }
    }

    /**
     * Takes the board down and leaves the duel running.
     * <p>
     * The policy for every disruption, and the reason it is the same one every
     * time: the engine is the authority on the duel and the presentation is not
     * part of the rules, so nothing that happens to the ground can decide a
     * game. It is also what stops a bystander with a stack of dirt from costing
     * somebody a match.
     */
    private static void fallBack(MinecraftServer server, Board board, String why)
    {
        release(server, board.seat0());
        Component message = Component.literal(why + "; playing on the duel screen instead")
            .withStyle(ChatFormatting.YELLOW);
        tell(server.getPlayerList().getPlayer(board.seat0()), message);
        tell(server.getPlayerList().getPlayer(board.seat1()), message);
    }

    /** Has this seat's player been moved much further than walking could explain? */
    private static boolean taken(MinecraftServer server, Board board, int seat)
    {
        ServerPlayer player = server.getPlayerList()
            .getPlayer(seat == 0 ? board.seat0() : board.seat1());
        if(player == null)
        {
            return false;
        }
        BlockPos stand = board.siting().stand(seat);
        return player.distanceToSqr(stand.getX() + 0.5D, stand.getY() + 1, stand.getZ() + 0.5D)
            > TELEPORTED_AWAY * TELEPORTED_AWAY;
    }

    /** Is this seat's player online and no longer alive? */
    private static boolean dead(MinecraftServer server, Board board, int seat)
    {
        ServerPlayer player = server.getPlayerList()
            .getPlayer(seat == 0 ? board.seat0() : board.seat1());
        return player != null && !player.isAlive();
    }

    /** Is this seat's player online but in a different world from the board? */
    private static boolean elsewhere(MinecraftServer server, Board board, int seat)
    {
        ServerPlayer player = server.getPlayerList()
            .getPlayer(seat == 0 ? board.seat0() : board.seat1());
        return player != null && !player.level().dimension().equals(board.level());
    }

    /**
     * Puts a duellist back on their mark if they have drifted off it.
     * <p>
     * Position only: their view is their own, because looking around the field
     * is how an overworld duel is played. The client suppresses its own
     * movement so this normally never fires; it stays because the client is not
     * the authority on where a player is.
     */
    private static void hold(ServerPlayer player, FieldSiting siting, int seat)
    {
        BlockPos stand = siting.stand(seat);
        double x = stand.getX() + 0.5D;
        double y = stand.getY() + 1;
        double z = stand.getZ() + 0.5D;
        if(player.distanceToSqr(x, y, z) > DRIFT_TOLERANCE * DRIFT_TOLERANCE)
        {
            player.connection.teleport(x, y, z, player.getYRot(), player.getXRot());
        }
    }

    /**
     * Puts a duellist on their mark and turns them to face the board and the
     * other duellist. The one time their view is taken from them, because a
     * duel that opens with a player facing the wrong way reads as broken.
     */
    private static void lock(ServerPlayer player, FieldSiting siting, int seat)
    {
        BlockPos stand = siting.stand(seat);
        Direction look = siting.look(seat);
        // A little downward, because the board is on the ground in front of
        // them and level is looking over it.
        player.connection.teleport(stand.getX() + 0.5D, stand.getY() + 1, stand.getZ() + 0.5D,
            look.toYRot(), 25F);
    }

    private static void open(ServerPlayer first, ServerPlayer second, ServerLevel level,
        FieldSiting siting)
    {
        // A new board cancels the old one's goodbye. Without this, the previous
        // duel's linger would expire mid-duel and take this board down with it.
        LINGERING.remove(first.getUUID());
        LINGERING.remove(second.getUUID());
        Board board = new Board(first.getUUID(), second.getUUID(), level.dimension(), siting);
        BOARDS.put(first.getUUID(), board);
        BOARDS.put(second.getUUID(), board);
        show(first, siting, 0, true);
        show(second, siting, 1, true);
    }

    /**
     * Takes the board away from whichever duel this player was in, and tells
     * both ends. Safe to call for a player who was never at a board, which is
     * how the duel-ended hooks can call it unconditionally.
     */
    public static void release(MinecraftServer server, UUID player)
    {
        LINGERING.remove(player);
        Board board = BOARDS.remove(player);
        Waiting waiting = WAITING.remove(player);
        if(waiting != null)
        {
            WAITING.remove(waiting.seat0());
            WAITING.remove(waiting.seat1());
            hideIfOnline(server, waiting.seat0());
            hideIfOnline(server, waiting.seat1());
            // The invariant holds even here: a wait torn down from outside is
            // still owed an answer, or the match state machine sits in DUELING
            // with no duel under it forever.
            waiting.outcome().cancel("the duel was called off");
        }
        if(board == null)
        {
            hideIfOnline(server, player);
            return;
        }
        BOARDS.remove(board.seat0());
        BOARDS.remove(board.seat1());
        hideIfOnline(server, board.seat0());
        hideIfOnline(server, board.seat1());
        // The audience too. They were never in BOARDS -- watching is not
        // playing -- so nothing else here would ever have told them, and a
        // board whose duel has ended would have stayed drawn in front of them
        // until they walked out of range of a duel that no longer exists.
        java.util.Set<UUID> watching = WATCHERS.remove(board.seat0());
        PUBLIC_VIEW.remove(board.seat0());
        if(watching != null)
        {
            watching.forEach(id -> stopWatching(server, id));
        }
    }

    /**
     * Boards whose duel is over, and the tick each stops being drawn.
     * <p>
     * A duel is decided the moment the engine says so, but the client is
     * several seconds behind that: the attack that won it and the damage it
     * dealt are still queued, because they ride the same ordered stream as the
     * result. Taking the board down on the engine's word meant a duel won by a
     * direct attack ended with the board simply gone -- the attack never played
     * at all, because there was nothing left to play it on.
     * <p>
     * So a finished board LINGERS. The client runs the ending -- the rest of
     * the animations, who won, a fade -- and clears its own copy when it is
     * done, which is why this side does not need to know how long that took.
     * This is the backstop for the client that never says: a disconnect, a
     * crash, a chunk unload.
     */
    private static final Map<UUID, Long> LINGERING = new java.util.HashMap<>();

    /** Ten seconds, which is longer than any ending and shorter than a nuisance. */
    public static final int LINGER_TICKS = 200;

    /**
     * The duel is over: leave the board standing while its ending plays.
     * <p>
     * Distinct from {@link #release} on purpose. A duel that ENDED has an
     * ending to show; a duel that was called off, abandoned or interrupted has
     * nothing to say and should get out of the way at once.
     */
    public static void finish(MinecraftServer server, UUID player)
    {
        Board board = BOARDS.remove(player);
        if(board == null)
        {
            // Already lingering, which is this duel's OTHER seat having got
            // here first.
            //
            // One board serves both seats and one call takes both of them out
            // of BOARDS, so the second call finds nothing to remove -- and
            // releasing on that reading sent an immediate hide to the very seat
            // the linger had just been granted to. Half of every finished duel
            // lost its board in the tick the engine decided, before the client
            // had played the winning blow, said who won, or faded: exactly the
            // ending this method exists to preserve. The seat it hit was
            // whichever sat second in the array, so it looked intermittent.
            if(LINGERING.containsKey(player))
            {
                return;
            }
            release(server, player);
            return;
        }
        BOARDS.remove(board.seat0());
        BOARDS.remove(board.seat1());
        long until = server.overworld().getGameTime() + LINGER_TICKS;
        LINGERING.put(board.seat0(), until);
        LINGERING.put(board.seat1(), until);
    }

    /** Every board goes away: the server is stopping, or all duels were stopped. */
    public static void releaseAll(MinecraftServer server)
    {
        for(UUID player : new ArrayList<>(BOARDS.keySet()))
        {
            release(server, player);
        }
        for(UUID player : new ArrayList<>(WAITING.keySet()))
        {
            WAITING.remove(player);
            hideIfOnline(server, player);
        }
    }

    /**
     * Shows the duel to anyone standing near enough to watch it.
     * <p>
     * The board's geometry is public -- it is a physical object in the world --
     * and the state that rides with it has already been reduced to what a
     * bystander may see. Neither duellist's own update is ever forwarded: this
     * takes a snapshot that was built for a seat and strips it, which is a
     * projection that can only remove and so cannot reveal.
     *
     * @param seatSnapshot either seat's board; both reduce to the same view
     */
    public static void showToSpectators(MinecraftServer server, UUID duellist,
        de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot seatSnapshot)
    {
        Board board = BOARDS.get(duellist);
        if(board == null || server == null || seatSnapshot == null)
        {
            return;
        }
        ServerLevel level = server.getLevel(board.level());
        if(level == null)
        {
            return;
        }
        de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot view =
            de.cas_ual_ty.dueldimension.ocg.prompt.StrangerView.of(seatSnapshot);
        // Kept, so somebody who walks up between changes can be shown this
        // rather than waiting for the next one.
        PUBLIC_VIEW.put(board.seat0(), view);

        OverworldPayloads.SpectatorBoard update = new OverworldPayloads.SpectatorBoard(view);
        OverworldPayloads.ShowField field = new OverworldPayloads.ShowField(board.siting(),
            board.level(), OverworldPayloads.SPECTATOR, true);

        for(ServerPlayer viewer : net.fabricmc.fabric.api.networking.v1.PlayerLookup
            .around(level, net.minecraft.world.phys.Vec3.atCenterOf(board.siting().anchor()),
                SPECTATOR_RANGE))
        {
            if(viewer.getUUID().equals(board.seat0()) || viewer.getUUID().equals(board.seat1())
                || isEngaged(viewer.getUUID()))
            {
                continue;
            }
            ServerPlayNetworking.send(viewer, field);
            ServerPlayNetworking.send(viewer, update);
            watchersOf(board).add(viewer.getUUID());
        }
    }

    /**
     * Takes a watched board away from somebody who has stopped watching it --
     * unless they have stopped by starting a duel of their own.
     * <p>
     * This is the difference between leaving and being promoted, and getting it
     * wrong put a duellist on the 2D screen the moment their own duel began.
     * The scan above skips engaged players deliberately, so a spectator who
     * sits down to play drops out of it and reads exactly like one who walked
     * away -- and hiding "the board" for them hides the board they are now
     * standing at. They are dropped from the audience in silence instead: their
     * own field is not this one's to take.
     */
    private static void stopWatching(MinecraftServer server, UUID id)
    {
        if(!isEngaged(id))
        {
            hideIfOnline(server, id);
        }
    }

    private static java.util.Set<UUID> watchersOf(Board board)
    {
        return WATCHERS.computeIfAbsent(board.seat0(),
            key -> java.util.concurrent.ConcurrentHashMap.newKeySet());
    }

    /**
     * Starts and stops people watching as they walk up to a duel and away again.
     * <p>
     * Range is the whole of it. There is no asking to spectate and nothing to
     * join: a duel on the ground is a thing happening in the world, and being
     * near enough to see it is the only qualification. Run every tick against
     * who is actually there, so arriving shows the duel as it stands and
     * leaving takes it away rather than leaving a board painted over the
     * countryside behind you.
     * <p>
     * Only ever the stripped view, and only to people who are not playing --
     * the same two exclusions the broadcast makes, made again here rather than
     * assumed, because this is the path that hands a board to somebody the duel
     * knows nothing about.
     */
    private static void tickWatchers(MinecraftServer server, ServerLevel level, Board board)
    {
        java.util.Set<UUID> watching = watchersOf(board);
        java.util.Set<UUID> near = new java.util.HashSet<>();
        for(ServerPlayer viewer : net.fabricmc.fabric.api.networking.v1.PlayerLookup
            .around(level, net.minecraft.world.phys.Vec3.atCenterOf(board.siting().anchor()),
                SPECTATOR_RANGE))
        {
            UUID id = viewer.getUUID();
            // Not the duellists, and not anybody engaged in a duel of their
            // own. A player has ONE field on their client, so handing this one
            // to somebody already playing at another board -- or walking to
            // their mark for one -- would paint this duel over theirs, and take
            // theirs away again when they wandered out of range of a duel they
            // were never in.
            if(id.equals(board.seat0()) || id.equals(board.seat1()) || isEngaged(id))
            {
                continue;
            }
            near.add(id);
            if(watching.add(id))
            {
                ServerPlayNetworking.send(viewer, new OverworldPayloads.ShowField(board.siting(),
                    board.level(), OverworldPayloads.SPECTATOR, true));
                de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot view =
                    PUBLIC_VIEW.get(board.seat0());
                if(view != null)
                {
                    ServerPlayNetworking.send(viewer,
                        new OverworldPayloads.SpectatorBoard(view));
                }
            }
        }
        // And anybody who has walked off, or logged off, is told the board is
        // gone -- otherwise it stays drawn wherever they go next.
        watching.removeIf(id ->
        {
            if(near.contains(id))
            {
                return false;
            }
            stopWatching(server, id);
            return true;
        });
    }

    /**
     * How far away a duel can be watched from. Past this the cards are a few
     * pixels across anyway, and the board is only sent to people who could
     * plausibly read it.
     */
    private static final double SPECTATOR_RANGE = 48D;

    /**
     * The block a player is standing on. Their own block position is the space
     * their feet occupy, so the floor is one below it.
     */
    public static BlockPos floorUnder(ServerPlayer player)
    {
        return player.blockPosition().below();
    }

    /**
     * Close enough to their mark to be called there: the right column, and
     * within a block vertically. Exact equality would refuse a player standing
     * on the correct square in a way the block grid disagrees with -- on a
     * slab, on a path, half inside the block edge.
     */
    private static boolean arrived(ServerPlayer player, FieldSiting siting, int seat)
    {
        BlockPos stand = siting.stand(seat);
        double dx = player.getX() - (stand.getX() + 0.5D);
        double dz = player.getZ() - (stand.getZ() + 0.5D);
        double dy = player.getY() - (stand.getY() + 1);
        return dx * dx + dz * dz <= ARRIVAL_TOLERANCE * ARRIVAL_TOLERANCE && Math.abs(dy) <= 1.5D;
    }

    private static boolean isOnline(MinecraftServer server, UUID id)
    {
        return server.getPlayerList().getPlayer(id) != null;
    }

    private static void settle(Waiting waiting)
    {
        WAITING.remove(waiting.seat0());
        WAITING.remove(waiting.seat1());
    }

    private static void show(ServerPlayer player, FieldSiting siting, int seat, boolean locked)
    {
        if(player == null)
        {
            return;
        }
        ServerPlayNetworking.send(player, new OverworldPayloads.ShowField(siting,
            player.level().dimension(), seat, locked));
    }

    private static void hide(ServerPlayer player)
    {
        if(player != null)
        {
            ServerPlayNetworking.send(player, new OverworldPayloads.HideField());
        }
    }

    private static void hideIfOnline(MinecraftServer server, UUID id)
    {
        ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(id);
        if(player != null && !player.hasDisconnected())
        {
            hide(player);
        }
    }

    private static void refuse(ServerPlayer first, ServerPlayer second, Refusal reason)
    {
        Component message = refusalMessage(reason);
        tell(first, message);
        tell(second, message);
    }

    private static Component refusalMessage(Refusal reason)
    {
        return Component.translatable(reason.key()).withStyle(ChatFormatting.YELLOW);
    }

    private static void tell(ServerPlayer player, Component message)
    {
        if(player != null && !player.hasDisconnected())
        {
            player.sendSystemMessage(message);
        }
    }
}
