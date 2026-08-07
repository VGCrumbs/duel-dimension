package de.cas_ual_ty.dueldimension.duel.profile;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.cas_ual_ty.dueldimension.fabric.DuelDimensionFabric;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Whether the deck builder ignores what a player owns.
 * <p>
 * A property of the world, not of a player, so it lives in the overworld's
 * saved data the way any world-wide switch does.
 * <p>
 * {@link SavedData} is Codec-driven in this version -- a {@link SavedDataType}
 * carries the id, the constructor and the Codec together, and the old
 * {@code save(CompoundTag)} override is gone. That suits this: the state is one
 * boolean, and describing it once is the whole implementation.
 */
public class FreeMode extends SavedData
{
    private static final Codec<FreeMode> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.BOOL.optionalFieldOf("Enabled", false).forGetter(state -> state.enabled)
        ).apply(instance, FreeMode::new));

    private static final SavedDataType<FreeMode> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(DuelDimensionFabric.MOD_ID, "freemode"),
        () -> new FreeMode(false), CODEC, DataFixTypes.LEVEL);

    /**
     * What the client believes, so the deck editor can grey a card without
     * asking the server per card. Told to it on join and on every change.
     */
    private static volatile boolean clientBelief;

    private boolean enabled;

    public FreeMode(boolean enabled)
    {
        this.enabled = enabled;
    }

    private static FreeMode of(MinecraftServer server)
    {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
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
