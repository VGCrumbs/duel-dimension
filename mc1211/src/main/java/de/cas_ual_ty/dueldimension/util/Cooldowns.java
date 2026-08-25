package de.cas_ual_ty.dueldimension.util;

import de.cas_ual_ty.dueldimension.DuelDimension;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * How long until a player may claim a duel reward again.
 * <p>
 * On Forge this was a capability, attached to every player by an event and
 * copied by hand when one respawned. A Fabric data attachment is the same idea
 * with the bookkeeping removed: it says how to persist itself, and
 * {@code copyOnDeath} replaces the clone handler outright.
 */
public final class Cooldowns
{
    /**
     * The attachment lives in a nested class on purpose.
     * <p>
     * A static {@code AttachmentType} means merely loading the enclosing class
     * initialises Fabric's registry, which needs a running game — and a unit
     * test asking a pure question would die on {@code NoClassDefFoundError}
     * before reaching it. Same shape as {@code DuelPoints.Storage} and
     * {@code DuelProfiles.Storage}, and for the same reason.
     */
    private static final class Storage
    {
        static final AttachmentType<CooldownHolder> COOLDOWN =
            AttachmentRegistry.<CooldownHolder>builder()
                .persistent(CooldownHolder.CODEC)
                .copyOnDeath()
                .initializer(CooldownHolder::new)
                .buildAndRegister(ResourceLocation.fromNamespaceAndPath(
                    DuelDimension.MOD_ID, "cooldown_holder"));
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
        java.util.Objects.requireNonNull(Storage.COOLDOWN);
    }
    private Cooldowns()
    {
    }

    /**
     * This player's cooldown, created on first ask.
     * <p>
     * Never null, which is the difference worth noting: Forge's
     * {@code getCapability(...).ifPresent(...)} could silently do nothing if
     * the capability was missing, and two of those nested inside each other —
     * as the reward commands did — meant a failure to attach on either player
     * skipped the rewards without a word.
     */
    public static CooldownHolder get(Player player)
    {
        return player.getAttachedOrCreate(Storage.COOLDOWN);
    }

    /**
     * Counts every online player down by one.
     * <p>
     * Forge ticked each player from {@code TickEvent.PlayerTickEvent} at the
     * END phase. Fabric has no per-player server tick, so this runs once at the
     * end of the server tick and walks the list — the same players, the same
     * number of times, one iteration instead of one event dispatch each.
     */
    public static void tick(MinecraftServer server)
    {
        for(ServerPlayer player : server.getPlayerList().getPlayers())
        {
            get(player).tick();
        }
    }
}
