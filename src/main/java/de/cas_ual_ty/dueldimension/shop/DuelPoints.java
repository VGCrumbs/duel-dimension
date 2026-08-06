package de.cas_ual_ty.dueldimension.shop;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/**
 * Duel Points: what a player spends in the card shop.
 * <p>
 * Held in the player's <em>persisted</em> NBT rather than plain persistent data,
 * because Forge copies only that subtag across death and dimension changes.
 * Currency that vanished on a lava death would be worse than no currency at all.
 * <p>
 * Every method here is server side. The client is told its balance by
 * {@link ShopMessages.SyncPoints} and never decides it, so a client that lies
 * about its balance is simply refused at the purchase.
 */
public final class DuelPoints
{
    /** Forge copies this subtag across respawns; the outer tag it lives in does not survive. */
    private static final String PERSISTED = Player.PERSISTED_NBT_TAG;
    private static final String KEY = "dueldimension:duel_points";

    /** What a player starts with, so the shop is not a locked door on day one. */
    public static final int STARTING_POINTS = 500;

    private DuelPoints()
    {
    }

    private static CompoundTag persisted(Player player)
    {
        CompoundTag root = player.getPersistentData();
        if(!root.contains(PERSISTED, net.minecraft.nbt.Tag.TAG_COMPOUND))
        {
            root.put(PERSISTED, new CompoundTag());
        }
        return root.getCompound(PERSISTED);
    }

    public static int get(Player player)
    {
        CompoundTag tag = persisted(player);
        // A player who has never been given points starts with the opening
        // balance rather than zero, and the tag is written so it is only ever
        // granted once.
        if(!tag.contains(KEY))
        {
            tag.putInt(KEY, STARTING_POINTS);
        }
        return tag.getInt(KEY);
    }

    public static void set(Player player, int points)
    {
        persisted(player).putInt(KEY, Math.max(0, points));
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
