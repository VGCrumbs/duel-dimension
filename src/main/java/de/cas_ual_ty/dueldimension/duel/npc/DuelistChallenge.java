package de.cas_ual_ty.dueldimension.duel.npc;

import de.cas_ual_ty.dueldimension.duel.dueldisk.DuelReach;
import de.cas_ual_ty.dueldimension.duel.overworld.OverworldDuels;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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

    /** Asks the player how they would like to play this duelist. */
    public static void offer(ServerPlayer player, DuelistEntity duelist)
    {
        ServerPlayNetworking.send(player, new DuelistChallengeMessages.OfferDuel(
            duelist.getId(), duelist.displayName()));
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
            return;
        }
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
                    if(!DuelistDuels.isDueling(player.getUUID()))
                    {
                        OverworldDuels.release(player.level().getServer(), player.getUUID());
                    }
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
