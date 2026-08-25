package de.cas_ual_ty.dueldimension.duel.npc;

import de.cas_ual_ty.dueldimension.duel.dueldisk.DuelReach;
import de.cas_ual_ty.dueldimension.duel.overworld.OverworldDuels;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Challenging a duelist: asking how, then doing it.
 * <p>
 * A duel against an NPC starts the instant it is asked for -- there is no lobby
 * and nobody to negotiate with -- so the choice between the duel screen and a
 * board in the world has to be made at the click, and it has to be made
 * <em>before</em> the duel begins. Once the engine is running the presentation
 * is settled, which is why this asks first rather than offering to move a duel
 * that has already started.
 * <p>
 * The reply is re-checked rather than trusted. A client sends back an entity id
 * and a preference; this looks the entity up, confirms it really is a duelist,
 * confirms it is still within a challenge's reach, and only then starts
 * anything. A packet is data, and the alternative is a message that can start a
 * duel with something across the world.
 */
public final class DuelistChallenge
{
    private DuelistChallenge()
    {
    }

    /**
     * A little further than a challenge carries, so a duelist that drifted a
     * step while the menu was open does not refuse the answer to its own
     * question.
     */
    private static final double REPLY_SLACK = 4D;

    /**
     * Asks the player how they would like to play this duelist -- unless they
     * have already said.
     * <p>
     * Two duellists standing on the two player points of a built arena is not a
     * coincidence and it is not ambiguous. Somebody laid those markers out,
     * walked to one and brought an opponent to the other; asking which kind of
     * duel they meant is asking a question they answered by walking. The menu
     * exists for the case where the world says nothing about it.
     * <p>
     * The screen duel is still one step away -- step off the mark and the
     * question comes back -- which is what keeps this a shortcut rather than a
     * setting.
     */
    public static void offer(ServerPlayer player, DuelistEntity duelist)
    {
        if(onTheMarks(player, duelist))
        {
            de.cas_ual_ty.dueldimension.DuelDimension.log("both duellists are on the marks of a"
                + " built arena, so the board is taken as chosen");
            begin(player, duelist.getId(), true);
            return;
        }
        ServerPlayNetworking.send(player, new DuelistChallengeMessages.OfferDuel(
            duelist.getId(), duelist.displayName()));
    }

    /**
     * Whether these two are standing on the two player points of one arena.
     * <p>
     * Both of them, and not the same one twice. Two duellists sharing a mark is
     * two duellists who have not set up a duel, and starting a board for them
     * would put them in the same seat.
     * <p>
     * Searched from the point between them, exactly as the code that goes on to
     * build the field does, so that a pair standing at the edge of one arena's
     * reach cannot have this say yes and the builder then say no.
     */
    private static boolean onTheMarks(ServerPlayer player, DuelistEntity duelist)
    {
        if(!(player.level() instanceof net.minecraft.server.level.ServerLevel level))
        {
            return false;
        }
        BlockPos here = player.blockPosition();
        BlockPos there = duelist.blockPosition();
        BlockPos between = new BlockPos(Math.floorDiv(here.getX() + there.getX(), 2),
            Math.floorDiv(here.getY() + there.getY(), 2),
            Math.floorDiv(here.getZ() + there.getZ(), 2));
        de.cas_ual_ty.dueldimension.duel.overworld.arena.Arena.Built arena =
            de.cas_ual_ty.dueldimension.duel.overworld.arena.ArenaScan.find(level, between);
        if(arena == null)
        {
            return false;
        }
        return (onMark(here, arena.first()) && onMark(there, arena.second()))
            || (onMark(here, arena.second()) && onMark(there, arena.first()));
    }

    /**
     * Standing on a mark, allowing a block either way vertically.
     * <p>
     * A marker has no collision, so a duellist stands INSIDE it rather than on
     * top -- but which block a foot is counted in depends on where the ground
     * under the marker is, so the column is what matters and the height is
     * given a little room.
     */
    private static boolean onMark(BlockPos where, BlockPos mark)
    {
        return where.getX() == mark.getX() && where.getZ() == mark.getZ()
            && Math.abs(where.getY() - mark.getY()) <= 1;
    }

    /**
     * Starts the duel the player picked, having checked they may.
     *
     * @param overworld true for a board built in the world
     */
    public static void begin(ServerPlayer player, int duelistId, boolean overworld)
    {
        Entity entity = player.level().getEntity(duelistId);
        if(!(entity instanceof DuelistEntity duelist))
        {
            de.cas_ual_ty.dueldimension.DuelDimension.warn(
                "challenge reply named entity " + duelistId + ", which is "
                    + (entity == null ? "not there" : entity.getType().toString()));
            return;
        }
        // Logged because this decision is invisible from inside the game: a
        // duel that opens on the screen after the board was asked for looks
        // exactly like a duel that was never asked about, and the difference
        // is one boolean that crossed the wire.
        de.cas_ual_ty.dueldimension.DuelDimension.log("challenge accepted: "
            + player.getGameProfile().getName() + " vs " + duelist.displayName()
            + ", overworld=" + overworld);
        double reach = DuelReach.CHALLENGE_RANGE + REPLY_SLACK;
        if(player.distanceToSqr(duelist) > reach * reach)
        {
            player.sendSystemMessage(Component.literal("That duelist is too far away now")
                .withStyle(ChatFormatting.RED));
            return;
        }

        if(!overworld)
        {
            DuelistDuels.challenge(duelist, player);
            return;
        }

        // A duelist that has been called out stands its ground: the stroll goal
        // is only gated once a duel is RUNNING, so without this it can wander
        // off during the half minute the challenger spends walking to their
        // mark, and the duel would begin with one duellist somewhere else.
        duelist.setStationary(true);
        OverworldDuels.prepareAgainst(player.level().getServer(), player, duelist,
            new OverworldDuels.Outcome()
            {
                @Override
                public void start()
                {
                    DuelistDuels.challenge(duelist, player);
                    // A duel that refused to start leaves no board behind.
                    // isSeated, NOT isDueling: the latter asks about the
                    // DUELIST's id and is always false for a player, so this
                    // guard used to fire every time and take the board with it.
                    if(!DuelistDuels.isSeated(player.getUUID()))
                    {
                        OverworldDuels.release(player.level().getServer(), player.getUUID());
                    }
                }

                /**
                 * The player picked the board out of a menu, so starting a
                 * screen duel instead would silently answer a question they
                 * had already answered. Nothing starts; they can move
                 * somewhere clearer and click the duelist again.
                 */
                @Override
                public void refused(String reason)
                {
                    player.sendSystemMessage(Component.literal(
                        "No duel field could be built here, so no duel was started."
                            + " Move somewhere more open and challenge again, or pick"
                            + " \"Duel screen\" instead.")
                        .withStyle(ChatFormatting.YELLOW));
                }

                @Override
                public void cancel(String reason)
                {
                    player.sendSystemMessage(Component.literal(reason)
                        .withStyle(ChatFormatting.RED));
                }
            });
    }
}
