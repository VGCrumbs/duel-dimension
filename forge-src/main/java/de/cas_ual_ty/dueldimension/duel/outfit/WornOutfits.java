package de.cas_ual_ty.dueldimension.duel.outfit;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who is wearing what, on the client that has to draw them.
 * <p>
 * An outfit is not private the way a collection is — it is the thing everyone
 * in the room can see — so unlike the profile it is told to every client rather
 * than only to its owner. What is <em>not</em> sent is anything else about the
 * profile: this carries one string per player and nothing more.
 */
public final class WornOutfits
{
    private static final Map<UUID, String> WORN = new ConcurrentHashMap<>();

    private WornOutfits()
    {
    }

    /** The outfit that player is in, never null. */
    public static Outfits.Outfit of(UUID player)
    {
        return Outfits.byId(WORN.get(player));
    }

    public static void set(UUID player, String outfit)
    {
        if(outfit == null || outfit.isEmpty())
        {
            WORN.remove(player);
            return;
        }
        WORN.put(player, outfit);
    }

    public static void forget(UUID player)
    {
        WORN.remove(player);
    }

    /** Dropped on disconnect, so a rejoin is told afresh rather than remembered wrong. */
    public static void clear()
    {
        WORN.clear();
    }

    // ---- server side ----

    /**
     * Tells everyone what this player is wearing, and tells this player what
     * everyone else is.
     * <p>
     * Both halves are needed on a join: the arriving client knows nothing, and
     * the clients already there have never heard of this player.
     */
    public static void announce(ServerPlayer player)
    {
        MinecraftServer server = player.getServer();
        if(server == null)
        {
            return;
        }
        String mine = de.cas_ual_ty.dueldimension.duel.profile.DuelProfiles.get(player).outfit();
        de.cas_ual_ty.dueldimension.DuelDimension.channel.send(
            net.minecraftforge.network.PacketDistributor.ALL.noArg(),
            new OutfitMessages.Worn(player.getUUID(), mine));

        for(ServerPlayer other : server.getPlayerList().getPlayers())
        {
            if(other == player)
            {
                continue;
            }
            de.cas_ual_ty.dueldimension.DuelDimension.channel.send(
                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                new OutfitMessages.Worn(other.getUUID(),
                    de.cas_ual_ty.dueldimension.duel.profile.DuelProfiles.get(other).outfit()));
        }
    }

    /** Tells everyone this player has changed. */
    public static void broadcast(ServerPlayer player)
    {
        de.cas_ual_ty.dueldimension.DuelDimension.channel.send(
            net.minecraftforge.network.PacketDistributor.ALL.noArg(),
            new OutfitMessages.Worn(player.getUUID(),
                de.cas_ual_ty.dueldimension.duel.profile.DuelProfiles.get(player).outfit()));
    }
}
