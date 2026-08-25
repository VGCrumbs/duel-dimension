package de.cas_ual_ty.dueldimension.duel.profile;

import de.cas_ual_ty.dueldimension.fabric.DuelDimensionFabric;
import de.cas_ual_ty.dueldimension.ocg.deck.StarterDecks;
import de.cas_ual_ty.dueldimension.ocg.deck.YdkDeck;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Where a player's cards and decks actually live: on the server, in that
 * player's own saved data.
 * <p>
 * On Forge this was the player's <em>persisted</em> NBT subtag, because that is
 * the one Forge copies across death and dimension changes. Fabric's answer is a
 * data attachment, and a better one: {@code copyOnDeath()} says the same thing
 * declaratively, and {@code persistent(CODEC)} means the profile is written by
 * the same Codec that describes it rather than by a second hand-written pass.
 * A collection that vanished on a lava death would be worse than no collection.
 * <p>
 * There is no cache here. The Forge version kept a {@code Map<UUID, DuelProfile>}
 * and a matching {@code save()} that had to be called after every change, which
 * is a rule a caller can forget; the attachment IS the storage, so reading gives
 * the live object and there is nothing to remember to flush.
 * <p>
 * Every method here is server side.
 */
public final class DuelProfiles
{
    /**
     * The attachment, held in a nested class so it is registered on first use
     * rather than on first mention of {@link DuelProfiles}.
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
         * Initialised rather than defaulted: {@link #starting()} grants the
         * starter decks and must run exactly once per player. An initializer
         * does that -- the attachment is created on first read and persisted
         * from then on -- where a shared default would hand every player the
         * same object.
         */
        static final AttachmentType<DuelProfile> PROFILE =
            AttachmentRegistry.<DuelProfile>builder()
                .persistent(DuelProfile.CODEC)
                .copyOnDeath()
                .initializer(DuelProfiles::starting)
                .buildAndRegister(ResourceLocation.fromNamespaceAndPath(
                    DuelDimensionFabric.MOD_ID, "profile"));
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
        java.util.Objects.requireNonNull(Storage.PROFILE);
    }
    private DuelProfiles()
    {
    }

    /**
     * That player's profile, creating and granting a starting one the first
     * time they are seen.
     */
    public static DuelProfile get(ServerPlayer player)
    {
        DuelProfile profile = player.getAttachedOrCreate(Storage.PROFILE);
        boolean expanded = grantMissingStarters(profile);
        if(expanded)
        {
            profile = profile.snapshot();
            player.setAttached(Storage.PROFILE, profile);
        }
        return profile;
    }

    /**
     * Marks the profile changed.
     * <p>
     * Attachments are written with the player, and the object handed out by
     * {@link #get} is the stored one, so this only has to say that the stored
     * value moved. It exists at all because the call sites read better for it
     * and because a future move to an immutable profile would need exactly this
     * hook.
     */
    public static void save(ServerPlayer player)
    {
        player.setAttached(Storage.PROFILE, get(player).snapshot());
    }

    /** Saves and then tells the client, which is what every change wants. */
    public static void saveAndSync(ServerPlayer player)
    {
        save(player);
        de.cas_ual_ty.dueldimension.net.ProfilePayloads.sync(player);
    }

    /**
     * What a player begins with: the starter decks, cards and all.
     * <p>
     * An empty profile would leave a new player with a deck editor they cannot
     * use and no way to duel until they had bought packs. Granting the starter
     * decks is the game's own answer to that -- it is the same call opening one
     * as a product makes -- so a new player can build and duel straight away.
     */
    private static DuelProfile starting()
    {
        DuelProfile profile = new DuelProfile();
        grantMissingStarters(profile);
        return profile;
    }

    /** Adds newly bundled starter products to an existing profile exactly once. */
    private static boolean grantMissingStarters(DuelProfile profile)
    {
        boolean changed = false;
        for(StarterDecks.Entry entry : StarterDecks.ALL)
        {
            // Only the protagonist's deck per generation is given; the rest are
            // bought. A deck already unlocked -- granted here before this rule
            // existed, or paid for since -- is left alone, so nobody loses a
            // deck they already had.
            if(!StarterDecks.isFree(entry.id())
                || profile.unlockedStructures().contains(entry.id()))
            {
                continue;
            }
            try
            {
                YdkDeck deck = entry.load();
                changed |= profile.unlockStarterDeck(entry.id(), entry.displayName(),
                    deck.main(), deck.extra(), deck.side());
            }
            catch(Exception unavailable)
            {
                // A deck list that will not load is not worth refusing the
                // player a profile over; they simply start without that one.
                DuelDimensionFabric.LOG.warn("Could not grant starter deck {}: {}",
                    entry.id(), unavailable.getMessage());
            }
        }
        return changed;
    }
}
