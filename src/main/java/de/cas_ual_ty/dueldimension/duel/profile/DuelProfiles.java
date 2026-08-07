package de.cas_ual_ty.dueldimension.duel.profile;

import de.cas_ual_ty.dueldimension.DuelDimension;
import de.cas_ual_ty.dueldimension.ocg.deck.StarterDecks;
import de.cas_ual_ty.dueldimension.ocg.deck.YdkDeck;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Where a player's cards and decks actually live: on the server, in that
 * player's own saved data.
 * <p>
 * This is the half that was missing. The deck editor was working against a
 * collection held in a static field on the client and seeded from the whole
 * card database, which is fine for one person looking at one screen and wrong
 * in every other way — every player on a server shared it, nothing survived a
 * relog, and the server had no idea what anyone owned, so it could not have
 * refused a deck built from cards a player did not have.
 * <p>
 * Stored in the player's <em>persisted</em> NBT for the same reason
 * {@link de.cas_ual_ty.dueldimension.shop.DuelPoints} is: Forge copies only that
 * subtag across death and dimension changes, and a collection that vanished on
 * a lava death would be worse than no collection at all.
 * <p>
 * Every method here is server side.
 */
public final class DuelProfiles
{
    /** Forge copies this subtag across respawns; the outer tag it lives in does not. */
    private static final String PERSISTED = Player.PERSISTED_NBT_TAG;
    private static final String KEY = "dueldimension:profile";

    /**
     * Parsed profiles, by player.
     * <p>
     * Only an optimisation: every change is written straight back to the NBT as
     * well, so losing this map loses nothing. Touched only from the server
     * thread, which is where every caller already is.
     */
    private static final Map<UUID, DuelProfile> CACHE = new HashMap<>();

    private DuelProfiles()
    {
    }

    private static CompoundTag persisted(Player player)
    {
        CompoundTag root = player.getPersistentData();
        if(!root.contains(PERSISTED, Tag.TAG_COMPOUND))
        {
            root.put(PERSISTED, new CompoundTag());
        }
        return root.getCompound(PERSISTED);
    }

    /**
     * That player's profile, creating and granting a starting one the first
     * time they are seen.
     */
    public static DuelProfile get(ServerPlayer player)
    {
        DuelProfile known = CACHE.get(player.getUUID());
        if(known != null)
        {
            return known;
        }

        CompoundTag tag = persisted(player);
        DuelProfile profile;
        if(tag.contains(KEY, Tag.TAG_COMPOUND))
        {
            profile = DuelProfile.load(tag.getCompound(KEY));
        }
        else
        {
            profile = starting();
            // Written immediately, so the starting grant happens exactly once
            // even if nothing else about this player ever changes.
            tag.put(KEY, profile.save());
        }
        CACHE.put(player.getUUID(), profile);
        return profile;
    }

    /**
     * What a player begins with: the starter decks, cards and all.
     * <p>
     * An empty profile would leave a new player with a deck editor they cannot
     * use and no way to duel until they had bought packs. Granting the starter
     * decks is the game's own answer to that — it is the same call opening one
     * as a product makes — so a new player can build and duel straight away.
     */
    private static DuelProfile starting()
    {
        DuelProfile profile = new DuelProfile();
        for(StarterDecks.Entry entry : StarterDecks.ALL)
        {
            try
            {
                YdkDeck deck = entry.load();
                profile.unlockStarterDeck(entry.id(), entry.displayName(),
                    deck.main(), deck.extra(), deck.side());
            }
            catch(Exception unavailable)
            {
                // A deck list that will not load is not worth refusing the
                // player a profile over; they simply start without that one.
                DuelDimension.log("Could not grant starter deck " + entry.id()
                    + ": " + unavailable.getMessage());
            }
        }
        return profile;
    }

    /**
     * Writes the profile back to the player's saved data. Call after every
     * change; it is a few hundred bytes and it is the only thing standing
     * between a player and losing their collection to a crash.
     */
    public static void save(ServerPlayer player)
    {
        DuelProfile profile = CACHE.get(player.getUUID());
        if(profile == null)
        {
            return;
        }
        persisted(player).put(KEY, profile.save());
        writeThrough(player, false);
    }

    /**
     * How long a change may sit in memory before the next one forces a write.
     * <p>
     * A player rearranging a deck produces a change a tick, and writing player
     * files twenty times a second to record forty cards being reordered would
     * be a stutter in exchange for nothing. Two seconds bounds the loss to the
     * last couple of edits, which the following edit then writes anyway.
     */
    private static final long WRITE_GAP_NANOS = 2_000_000_000L;

    private static long lastWriteNanos;

    /**
     * Puts the player's saved data on disk.
     * <p>
     * The tag alone is not enough. Putting the profile in the player's NBT only
     * queues it for whenever the world next saves — the autosave, or a clean
     * shutdown. Anything that ends the process without one takes the collection
     * with it, which is the exact failure {@link #save} claims to prevent.
     *
     * @param force write now, whatever the throttle says: for a player leaving,
     *              where there is no next change to carry the write
     */
    private static void writeThrough(ServerPlayer player, boolean force)
    {
        if(player.getServer() != null)
        {
            long now = System.nanoTime();
            if(!force && now - lastWriteNanos < WRITE_GAP_NANOS)
            {
                return;
            }
            lastWriteNanos = now;
            try
            {
                player.getServer().getPlayerList().saveAll();
            }
            catch(Exception unwritable)
            {
                // A profile that could not be written is worth a line in the
                // log and not worth dropping the player's session over; it is
                // still in memory and the next change will try again.
                DuelDimension.log("Could not write profile for " + player.getGameProfile().getName()
                    + ": " + unwritable.getMessage());
            }
        }
    }

    /** Sends the player their own profile, and only their own. */
    public static void sync(ServerPlayer player)
    {
        DuelDimension.channel.send(PacketDistributor.PLAYER.with(() -> player),
            new ProfileMessages.Sync(get(player).save()));
    }

    /** Saves and then tells the client, which is what every change wants. */
    public static void saveAndSync(ServerPlayer player)
    {
        save(player);
        sync(player);
    }

    /**
     * Forgets the cached copy; the saved data is untouched.
     * <p>
     * The last write is forced on the way out. A player who edits a deck and
     * immediately disconnects has no next change to carry the throttled write,
     * and leaving is exactly when the loss would be noticed.
     */
    public static void forget(Player player)
    {
        if(player instanceof ServerPlayer leaving && CACHE.containsKey(player.getUUID()))
        {
            writeThrough(leaving, true);
        }
        CACHE.remove(player.getUUID());
    }

    /** For tests and for a server shutting down. */
    public static void forgetAll()
    {
        CACHE.clear();
    }
}
