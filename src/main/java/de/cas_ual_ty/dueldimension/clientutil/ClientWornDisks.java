package de.cas_ual_ty.dueldimension.clientutil;

import de.cas_ual_ty.dueldimension.duel.profile.DuelDisks;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What every player is wearing on their disk arm, as this client knows it.
 * <p>
 * The mirror of {@code WornOutfits} for disks, and it exists for the same
 * reason: a {@code DuelProfile} is synced only to its owner, so without a
 * broadcast every other client would draw a duelist holding their shield.
 * <p>
 * Cleared on disconnect. The map is keyed by UUID and nothing else empties it,
 * so a second server would otherwise start with the first one's disks on
 * strangers who happen to share a UUID.
 */
public final class ClientWornDisks
{
    /** Only players actually WEARING one appear here; taking it off removes them. */
    private static final Map<UUID, String> WORN = new ConcurrentHashMap<>();

    private ClientWornDisks()
    {
    }

    public static void set(UUID player, String disk, boolean worn)
    {
        if(!worn || !DuelDisks.isKnown(disk))
        {
            WORN.remove(player);
            return;
        }
        WORN.put(player, disk);
    }

    public static void forget(UUID player)
    {
        WORN.remove(player);
    }

    public static void clear()
    {
        WORN.clear();
    }

    /** The disk this player is wearing, or empty if they are not wearing one. */
    public static ItemStack worn(UUID player)
    {
        String name = WORN.get(player);
        if(name == null)
        {
            return ItemStack.EMPTY;
        }
        Item item = DuelDisks.item(name);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }
}
