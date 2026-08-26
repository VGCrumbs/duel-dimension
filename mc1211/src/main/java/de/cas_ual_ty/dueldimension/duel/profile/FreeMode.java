package de.cas_ual_ty.dueldimension.duel.profile;

import de.cas_ual_ty.dueldimension.DuelDimension;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Whether the deck builder ignores what a player actually owns.
 * <p>
 * On, every card in the database can be put in a deck and every deck can be
 * duelled with — for building, for testing, for a server that would rather not
 * run an economy at all. Off, a deck is only as good as the collection behind
 * it.
 * <p>
 * A property of the world, not of a player, so it lives in the overworld's
 * saved data the way any world-wide switch does.
 * <p>
 * {@link SavedData} is tag-driven in 1.21.1: a {@link SavedData.Factory} carries
 * the empty constructor, the loader and the data-fix type, the file name is a
 * plain string passed alongside it, and {@code save(CompoundTag, Provider)} is
 * an abstract override. The state is one boolean, so all of that is four lines.
 */
public class FreeMode extends SavedData
{
    /**
     * The file this lives in, under the world's {@code data} folder. The same
     * name the Forge build used, so a world carried across keeps its setting.
     */
    private static final String NAME = DuelDimension.MOD_ID + "_freemode";

    private static final SavedData.Factory<FreeMode> FACTORY = new SavedData.Factory<>(
        () -> new FreeMode(false),
        (tag, registries) -> new FreeMode(tag.getBoolean("Enabled")),
        DataFixTypes.LEVEL);

    /**
     * What the client believes, so the deck editor can grey a card without
     * asking the server about every one. Told to it on join and on each change.
     */
    private static volatile boolean clientBelief;

    private boolean enabled;

    public FreeMode(boolean enabled)
    {
        this.enabled = enabled;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries)
    {
        tag.putBoolean("Enabled", enabled);
        return tag;
    }

    private static FreeMode of(MinecraftServer server)
    {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public static boolean isEnabled(MinecraftServer server)
    {
        return server != null && of(server).enabled;
    }

    public static boolean isEnabled(ServerPlayer player)
    {
        return player != null && isEnabled(player.level().getServer());
    }

    public static void set(MinecraftServer server, boolean enabled)
    {
        FreeMode state = of(server);
        state.enabled = enabled;
        state.setDirty();
    }

    /** What the client was last told. Client side. */
    public static boolean clientBelief()
    {
        return clientBelief;
    }

    public static void setClientBelief(boolean enabled)
    {
        clientBelief = enabled;
    }
}
