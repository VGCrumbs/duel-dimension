package de.cas_ual_ty.dueldimension.shop;

import com.mojang.serialization.Codec;
import de.cas_ual_ty.dueldimension.fabric.DuelDimensionFabric;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

/**
 * Duel Points: what a player spends in the card shop.
 * <p>
 * A data attachment, for the reason the Forge build used the persisted NBT
 * subtag: currency that vanished on a lava death would be worse than no
 * currency at all. {@code copyOnDeath()} states that outright instead of
 * relying on which tag the loader happens to copy.
 * <p>
 * The client is told its balance and never decides it, so a client that lies
 * about its balance is simply refused at the purchase.
 */
public final class DuelPoints
{
    /** What a player starts with, so the shop is not a locked door on day one. */
    public static final int STARTING_POINTS = 500;

    /**
     * The attachment, held in a nested class so it is registered on first use
     * rather than on first mention of {@link DuelPoints}.
     * <p>
     * Registering it from a static field of the outer class made loading the
     * class enough to pull in Fabric's registry, and that needs a running game:
     * a unit test asking a pure question -- what does a win pay, is this deck
     * legal -- died on a NoClassDefFoundError before reaching the question. A
     * nested class initialises when it is first touched and not before, which
     * is exactly when the storage is actually wanted.
     */
    private static final class Storage
    {
        /**
         * The balance is one number, so its Codec is the number's. Initialised
         * to the opening balance, which every player shares because an int is
         * immutable and sharing one is exactly right.
         */
        static final AttachmentType<Integer> POINTS = AttachmentRegistry.<Integer>builder()
            .persistent(Codec.INT)
            .copyOnDeath()
            .initializer(() -> STARTING_POINTS)
            .buildAndRegister(Identifier.fromNamespaceAndPath(
                DuelDimensionFabric.MOD_ID, "duel_points"));
    }

    /**
     * Registers the attachment, and this has to be called before a world loads.
     * <p>
     * The type is a static field on a nested class, so it exists only once
     * something touches that class -- and until then Fabric does not know the
     * id. A world saved with it and loaded before it is registered has its data
     * <b>discarded</b>, with one warning line: "Found unknown attachment type".
     * That is how a player's whole collection went missing once.
     * <p>
     * Touching the field is the registration; the empty body is the point.
     */
    public static void register()
    {
        java.util.Objects.requireNonNull(Storage.POINTS);
    }
    private DuelPoints()
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
        if(!canAfford(get(player), cost))
        {
            return false;
        }
        set(player, get(player) - cost);
        return true;
    }

    /**
     * Whether a balance covers a cost. Pure, so the rule can be checked without
     * a player: a free item is affordable, a negative price is not a gift.
     */
    public static boolean canAfford(int balance, int cost)
    {
        return cost >= 0 && balance >= cost;
    }
}
