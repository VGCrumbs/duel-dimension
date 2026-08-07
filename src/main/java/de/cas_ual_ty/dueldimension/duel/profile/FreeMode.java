package de.cas_ual_ty.dueldimension.duel.profile;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Whether the deck builder ignores what a player actually owns.
 * <p>
 * On, every card in the database can be put in a deck and every deck can be
 * duelled with — for building, for testing, for a server that would rather not
 * run an economy at all. Off, a deck is only as good as the collection behind
 * it.
 * <p>
 * A world setting rather than a per-player one: two players in a duel have to
 * agree about whether the cards in their decks are real, and that is not
 * something either of them can be allowed to decide alone.
 * <p>
 * Turning it off does not damage anything. A deck built under it keeps every
 * card; it simply cannot be duelled with until the missing cards are earned or
 * taken out, and turning it back on makes it playable again untouched. That is
 * the whole reason the check lives at the point of use rather than at the point
 * of saving.
 */
public final class FreeMode
{
    private static final String NAME = "dueldimension_freemode";

    /**
     * The client's copy, so the editor can grey or pulse without asking the
     * server per frame. Server-authoritative: this is told, never decided.
     */
    private static volatile boolean clientBelief;

    private FreeMode()
    {
    }

    /** The stored flag, saved with the world. */
    public static final class State extends SavedData
    {
        private boolean enabled;

        public State()
        {
        }

        public static State load(CompoundTag tag)
        {
            State state = new State();
            state.enabled = tag.getBoolean("Enabled");
            return state;
        }

        @Override
        public CompoundTag save(CompoundTag tag)
        {
            tag.putBoolean("Enabled", enabled);
            return tag;
        }
    }

    private static State state(MinecraftServer server)
    {
        // Kept on the overworld because it applies to the whole world, and the
        // overworld is the one dimension guaranteed to exist.
        return server.overworld().getDataStorage()
            .computeIfAbsent(State::load, State::new, NAME);
    }

    public static boolean isEnabled(MinecraftServer server)
    {
        return server != null && state(server).enabled;
    }

    /** True for a player's server, or the client's belief when off-thread. */
    public static boolean isEnabled(ServerPlayer player)
    {
        return player != null && isEnabled(player.server);
    }

    /**
     * Reads the flag from either side.
     * <p>
     * On a server this is the stored value; on a client it is what the server
     * last said. A level is enough to tell which, and the editor asks with no
     * server to hand.
     */
    public static boolean isEnabled(Level level)
    {
        if(level == null)
        {
            return clientBelief;
        }
        return level.isClientSide ? clientBelief : isEnabled(level.getServer());
    }

    public static void set(MinecraftServer server, boolean enabled)
    {
        State state = state(server);
        state.enabled = enabled;
        state.setDirty();
        server.getPlayerList().getPlayers().forEach(FreeMode::sync);
    }

    /** What the client believes, set only by the server telling it. */
    public static boolean clientBelief()
    {
        return clientBelief;
    }

    public static void setClientBelief(boolean value)
    {
        clientBelief = value;
    }

    public static void sync(ServerPlayer player)
    {
        de.cas_ual_ty.dueldimension.DuelDimension.channel.send(
            net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
            new ProfileMessages.SyncFreeMode(isEnabled(player)));
    }
}
