package de.cas_ual_ty.dueldimension.duel.dueldisk;

import de.cas_ual_ty.dueldimension.duel.profile.DuelDisks;
import de.cas_ual_ty.dueldimension.duel.profile.DuelProfile;
import de.cas_ual_ty.dueldimension.duel.profile.DuelProfiles;
import net.minecraft.server.level.ServerPlayer;

/**
 * The duel disk slot: which disk is worn, and whether it is on.
 * <p>
 * <b>Its own slot rather than the off-hand.</b> Wearing the disk as an item
 * meant it competed with shields and totems, could be dropped or burned, and
 * left "is this player wearing a disk" answered in four different places. It is
 * now one question with one answer, on the profile, beside the entitlement that
 * says which disk they are entitled to wear.
 * <p>
 * There is no inventory item involved at all. A disk is a right, not a
 * possession -- {@link DuelDisks#FREE} grants everyone the plain one -- so
 * putting it on cannot fail for want of a free slot, and taking it off cannot
 * lose it.
 */
public final class WornDisks
{
    private WornDisks()
    {
    }

    /**
     * The disk this player wears. Everyone owns the plain one from the start,
     * so this always answers -- the shop widens the choice, it does not grant
     * the first one.
     */
    public static DuelDiskItem active(ServerPlayer player)
    {
        String name = DuelProfiles.get(player).activeDisk();
        DuelDiskItem disk = DuelDisks.item(name);
        // Falls back rather than failing: a profile naming a disk this build no
        // longer registers must still be able to duel.
        return disk == null ? de.cas_ual_ty.dueldimension.DdItems.DUEL_DISK : disk;
    }

    /**
     * Whether this player has their disk on.
     * <p>
     * The single question every duel entry point asks: starting a duel, the
     * Chaos Disk's opening promise, and challenging someone by clicking them.
     */
    public static boolean isWearing(ServerPlayer player)
    {
        return player != null && DuelProfiles.get(player).diskWorn();
    }

    /** Whether this player is wearing that particular disk. */
    public static boolean isWearing(ServerPlayer player, DuelDiskItem disk)
    {
        return isWearing(player) && active(player) == disk;
    }

    /** Puts the disk on, or takes it off. */
    public static void toggle(ServerPlayer player)
    {
        if(player == null)
        {
            return;
        }
        DuelProfile profile = DuelProfiles.get(player);
        profile.setDiskWorn(!profile.diskWorn());
        DuelProfiles.save(player);
        // The profile for the owner's own UI, the broadcast for everyone's
        // renderer: the first is private state, the second is what other
        // clients need to draw them.
        de.cas_ual_ty.dueldimension.net.ProfilePayloads.sync(player);
        announce(player);
    }

    /**
     * Tells everyone what this player has on, and tells this player what
     * everyone else has on.
     * <p>
     * Both halves matter on a join, exactly as they do for outfits: the
     * arriving client knows nothing, and the clients already there have never
     * heard of this player.
     */
    public static void announce(ServerPlayer player)
    {
        net.minecraft.server.MinecraftServer server = player.level().getServer();
        if(server == null)
        {
            return;
        }
        DuelProfile profile = DuelProfiles.get(player);
        DiskMessages.WornDisk mine = new DiskMessages.WornDisk(player.getUUID(),
            profile.activeDisk(), profile.diskWorn());

        // Sent directly rather than found by iterating the player list: on JOIN
        // the arriving player may not be in it yet.
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, mine);

        for(ServerPlayer other : server.getPlayerList().getPlayers())
        {
            if(other == player)
            {
                continue;
            }
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(other, mine);
            DuelProfile theirs = DuelProfiles.get(other);
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                new DiskMessages.WornDisk(other.getUUID(), theirs.activeDisk(),
                    theirs.diskWorn()));
        }
    }
}
