package de.cas_ual_ty.dueldimension.shop;

import com.mojang.serialization.Codec;
import de.cas_ual_ty.dueldimension.fabric.DuelDimensionFabric;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * Duel Energy, the currency the Monuments take.
 *
 * <h2>Why a second currency</h2>
 * DP is earned in the hundreds and spent in the hundreds; it is priced for a
 * shop where a player buys a pack whenever they feel like one. The Monuments
 * are not that. A duel pays <b>3 DE</b> for a win and <b>1</b> otherwise, and a
 * choice at the statues costs <b>5</b> -- so a reward is roughly two duels of
 * work whether they go well or badly, and no amount of shopping brings it
 * closer. Expressing that in DP would have meant a price the shop's own numbers
 * make meaningless.
 *
 * <h2>The shape is DuelPoints'</h2>
 * Same attachment idiom, same nested-class registration, and for the same
 * reasons -- see {@link DuelPoints}, where both are explained at length. The one
 * difference is the opening balance: DP starts at 500 so the shop is not a
 * locked door on day one, and DE starts at <b>zero</b> because the Monuments are
 * meant to be earned rather than sampled.
 */
public final class DuelEnergy
{
    /*
     * The stored id is still "god_points", and that is deliberate.
     *
     * An attachment's id IS its key in the player's saved data. Renaming it to
     * match the class would not migrate anything -- the old key would simply
     * stop being read, and every player's balance would silently become zero on
     * the next load. A currency quietly resetting is a worse bug than a name
     * that does not match, and the id is not shown to anyone.
     *
     * If a clean key is wanted, it needs a migration that reads the old one
     * once and writes the new, not a rename.
     */
    /** What a duel pays for a win. */
    public static final int WIN_AWARD = 3;
    /** What it pays for anything else. A duel played is still a duel played. */
    public static final int PLAYED_AWARD = 1;
    /** What one choice at the Monuments costs. */
    public static final int MONUMENT_COST = 5;

    /**
     * The attachment, held in a nested class so it is registered on first use
     * rather than on first mention of {@link DuelEnergy}. Touching a static field
     * of the outer class would pull in Fabric's registry, and that needs a
     * running game -- which is how a pure unit test once died on a
     * NoClassDefFoundError before reaching its question.
     */
    private static final class Storage
    {
        static final AttachmentType<Integer> POINTS = AttachmentRegistry.<Integer>builder()
            .persistent(Codec.INT)
            .copyOnDeath()
            .initializer(() -> 0)
            .buildAndRegister(ResourceLocation.fromNamespaceAndPath(
                DuelDimensionFabric.MOD_ID, "god_points"));
    }

    /**
     * Registers the attachment, and this has to be called before a world loads.
     * <p>
     * A world saved with an attachment and loaded before that attachment is
     * registered has its data <b>discarded</b>, with one warning line. Touching
     * the field is the registration; the empty body is the point.
     */
    public static void register()
    {
        java.util.Objects.requireNonNull(Storage.POINTS);
    }

    private DuelEnergy()
    {
    }

    public static int get(Player player)
    {
        return player.getAttachedOrCreate(Storage.POINTS);
    }

    public static void set(Player player, int points)
    {
        player.setAttached(Storage.POINTS, Math.max(0, points));
    }

    /** Awards points. Negative amounts are ignored rather than quietly charging. */
    public static void award(Player player, int points)
    {
        if(points <= 0)
        {
            return;
        }
        set(player, get(player) + points);
    }

    /**
     * Spends points if the player has them.
     *
     * @return true if the balance covered it and was charged
     */
    public static boolean spend(Player player, int cost)
    {
        if(cost < 0 || get(player) < cost)
        {
            return false;
        }
        set(player, get(player) - cost);
        return true;
    }

    /** What a duel of this outcome pays. */
    public static int awardFor(DuelReward.Outcome outcome)
    {
        return outcome == DuelReward.Outcome.WIN ? WIN_AWARD : PLAYED_AWARD;
    }
}
