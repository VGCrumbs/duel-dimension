package de.cas_ual_ty.dueldimension.shop;

import com.mojang.serialization.Codec;
import de.cas_ual_ty.dueldimension.fabric.DuelDimensionFabric;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

/**
 * How many duels a player has won and lost.
 *
 * <h2>Why this is not on the profile</h2>
 * {@code DuelProfile} is the player's stuff -- decks, trunk, settings -- and it
 * is synced wholesale whenever any of it changes. A win counter changing at the
 * end of every duel would drag the whole profile across the wire with it. Two
 * ints as their own attachments cost nothing and travel on their own.
 * <p>
 * Draws are counted as neither. They are rare enough that a third number would
 * be a column of zeroes in the profile panel, and "wins and losses" is what a
 * player is actually asking when they look.
 *
 * <h2>Two records, not one</h2>
 * A duel against another person and a duel against a bot are not the same
 * achievement, and averaging them makes the number answer neither question: the
 * NPC loop is repeatable and deliberately winnable, so it drags a real record
 * towards whatever the bots are worth, and a player who mostly duels bots ends
 * up unable to see how they do against people at all.
 * <p>
 * So the profile's headline record is PLAYER duels, and the NPC one is kept
 * beside it and shown on request.
 *
 * <h2>What happened to the old numbers</h2>
 * {@code duel_wins} and {@code duel_losses} keep their names and become the
 * PLAYER record. Totals saved before the split are mixed and stay in that
 * column, because the split was never recorded and there is nothing to divide
 * them by -- inventing a ratio would be worse than a slightly generous history.
 * New NPC duels go to the new pair from here on.
 *
 * <p>Registration follows {@link DuelPoints}' nested-class idiom, for the reason
 * documented there at length.
 */
public final class DuelRecord
{
    private static final class Storage
    {
        static final AttachmentType<Integer> WINS = AttachmentRegistry.<Integer>builder()
            .persistent(Codec.INT)
            .copyOnDeath()
            .initializer(() -> 0)
            .buildAndRegister(Identifier.fromNamespaceAndPath(
                DuelDimensionFabric.MOD_ID, "duel_wins"));

        static final AttachmentType<Integer> NPC_WINS = AttachmentRegistry.<Integer>builder()
            .persistent(Codec.INT)
            .copyOnDeath()
            .initializer(() -> 0)
            .buildAndRegister(Identifier.fromNamespaceAndPath(
                DuelDimensionFabric.MOD_ID, "duel_npc_wins"));

        static final AttachmentType<Integer> NPC_LOSSES = AttachmentRegistry.<Integer>builder()
            .persistent(Codec.INT)
            .copyOnDeath()
            .initializer(() -> 0)
            .buildAndRegister(Identifier.fromNamespaceAndPath(
                DuelDimensionFabric.MOD_ID, "duel_npc_losses"));

        static final AttachmentType<Integer> LOSSES = AttachmentRegistry.<Integer>builder()
            .persistent(Codec.INT)
            .copyOnDeath()
            .initializer(() -> 0)
            .buildAndRegister(Identifier.fromNamespaceAndPath(
                DuelDimensionFabric.MOD_ID, "duel_losses"));
    }

    /**
     * Registers both attachments, before a world loads.
     * <p>
     * A world saved with an attachment and loaded before it is registered has
     * that data discarded with a single warning line.
     */
    public static void register()
    {
        java.util.Objects.requireNonNull(Storage.WINS);
        java.util.Objects.requireNonNull(Storage.LOSSES);
        java.util.Objects.requireNonNull(Storage.NPC_WINS);
        java.util.Objects.requireNonNull(Storage.NPC_LOSSES);
    }

    private DuelRecord()
    {
    }

    public static int wins(Player player)
    {
        return player.getAttachedOrCreate(Storage.WINS);
    }

    public static int losses(Player player)
    {
        return player.getAttachedOrCreate(Storage.LOSSES);
    }

    public static int npcWins(Player player)
    {
        return player.getAttachedOrCreate(Storage.NPC_WINS);
    }

    public static int npcLosses(Player player)
    {
        return player.getAttachedOrCreate(Storage.NPC_LOSSES);
    }

    /**
     * Records one finished duel. A draw counts as neither.
     *
     * @param versusPlayer whether the opponent was another person. Taken from
     *                     the duel rather than guessed at, because the caller
     *                     already knows -- {@code DuelistDuels} scales the DP
     *                     reward off the same answer.
     */
    public static void record(Player player, DuelReward.Outcome outcome, boolean versusPlayer)
    {
        AttachmentType<Integer> won = versusPlayer ? Storage.WINS : Storage.NPC_WINS;
        AttachmentType<Integer> lost = versusPlayer ? Storage.LOSSES : Storage.NPC_LOSSES;
        if(outcome == DuelReward.Outcome.WIN)
        {
            player.setAttached(won, player.getAttachedOrCreate(won) + 1);
        }
        else if(outcome == DuelReward.Outcome.LOSS)
        {
            player.setAttached(lost, player.getAttachedOrCreate(lost) + 1);
        }
    }
}
