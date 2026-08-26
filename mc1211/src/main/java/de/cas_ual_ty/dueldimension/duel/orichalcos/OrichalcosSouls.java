package de.cas_ual_ty.dueldimension.duel.orichalcos;

import de.cas_ual_ty.dueldimension.DuelDimension;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Seal of Orichalcos takes the loser's soul.
 * <p>
 * Anime flavour, not a rule: the engine has already decided the duel and
 * nothing here changes that. This only reacts to an outcome ocgcore reached on
 * its own, which is why it is optional and why it lives outside the duel
 * packages that mirror EDOPro.
 * <p>
 * <b>Armed by a deadline, not by a packet.</b> The client is told the duel is
 * over and sends word once the player has closed the screen and has their
 * character back, but that signal only ever <em>shortens</em> the wait. A
 * client that is modified, that crashed, or that simply never sends it does not
 * escape — the fallback deadline fires anyway. Trusting the client to volunteer
 * "I am ready to die now" would make the whole thing opt-out by closing the
 * game.
 * <p>
 * <b>Any living entity, not just players.</b> The duel path only ever marks a
 * player, but the sequence itself needs nothing player-specific — the seal
 * grows under a mob exactly as well, and the debug item and command take
 * advantage of that to make the thing testable without playing a duel to a
 * loss.
 */
public final class OrichalcosSouls
{
    /**
     * The damage type, defined as data at
     * {@code data/dueldimension/damage_type/orichalcos.json}. Damage types are
     * a registry loaded from datapacks in this version, so a mod supplies one
     * as a file and looks it up through the level's registry access rather
     * than registering it in code.
     */
    public static final ResourceKey<DamageType> ORICHALCOS = ResourceKey.create(
        Registries.DAMAGE_TYPE,
        ResourceLocation.fromNamespaceAndPath(DuelDimension.MOD_ID, "orichalcos"));

    /** How long the seal takes to close in, in ticks. Five seconds. */
    public static final int GROW_TICKS = 100;

    /**
     * The pause between the seal landing and the soul being taken.
     * <p>
     * One second. The seal reaching full size and the beam firing on the same
     * tick read as a single event; a beat between them gives the moment
     * somewhere to breathe. It also sits in the gap where the buzz has just
     * decayed to nothing and the fade has not yet started.
     */
    public static final int HOLD_TICKS = 20;

    /**
     * The gap between the beam firing and the target dying.
     * <p>
     * A quarter second. The soul is pulled out and the body drops after it;
     * on the same tick the two read as one event and the beam looks like a
     * death effect rather than the cause of it.
     */
    public static final int KILL_DELAY_TICKS = 5;

    /**
     * How long the beam lingers after the kill.
     * <p>
     * 162 rather than a round 160 because it is timed to the sound: the fade
     * clip runs 8.098 seconds, and the beam and seal are meant to go out on its
     * last breath rather than a tenth of a second before it.
     */
    public static final int FADE_TICKS = 162;

    /**
     * The longest a marked target may stay alive if nobody reports being back
     * in the world. Twelve seconds — purely a backstop for a client that never
     * sends the signal, and short enough that waiting it out does not read as
     * the seal having failed.
     */
    private static final int FALLBACK_TICKS = 240;

    /**
     * The Seal of Orichalcos.
     * <p>
     * Matched by passcode rather than by name: a name is localised and a
     * passcode is what the engine actually deals in.
     */
    public static final int SEAL_PASSCODE = 48179391;

    /** Marked targets, by UUID so a logout or a chunk unload does not lose them. */
    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    /**
     * One marked target.
     *
     * @param deadline  the tick the sequence starts at come what may
     * @param startedAt the tick it actually started, or -1 while waiting
     * @param beamed    whether the beam has already fired, so it fires once
     * @param waitFor   the player whose return to the world starts this, or
     *                  null to wait out the deadline. For a duelist that is the
     *                  human at the other seat: the duelist has no screen to
     *                  close, but somebody has to be there to see it.
     */
    private record Pending(long deadline, long startedAt, boolean beamed, UUID waitFor)
    {
        boolean started()
        {
            return startedAt >= 0;
        }
    }

    private OrichalcosSouls()
    {
    }

    /**
     * Whether The Seal of Orichalcos is face-up on either side of this board.
     * <p>
     * Either side on purpose: in the anime the Seal takes the loser whoever
     * played it, and the player who activates it loses to it as readily as
     * their opponent does.
     */
    public static boolean sealOnField(
        de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot board)
    {
        return sealIn(board.self()) || sealIn(board.opponent());
    }

    private static boolean sealIn(de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot.Side side)
    {
        for(de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot.Slot slot : side.spells())
        {
            // Face-down is not active, and a set card's code is the server's
            // business anyway.
            if(slot.present() && !slot.faceDown() && slot.code() == SEAL_PASSCODE)
            {
                return true;
            }
        }
        return false;
    }

    /** Whether this target is already marked, so nothing marks them twice. */
    public static boolean isMarked(UUID target)
    {
        return PENDING.containsKey(target);
    }

    /**
     * Marks a duel's loser. Takes effect once they are out of the duel screen,
     * or after the fallback deadline, whichever comes first.
     */
    public static void mark(LivingEntity loser)
    {
        if(loser == null)
        {
            return;
        }
        // The live setting, not the read-once config: the config seeds its
        // default, but a toggle has to be able to take effect without a restart.
        if(!SealSettings.enabled())
        {
            return;
        }
        // A player waits for their own screen to close.
        arm(loser, loser.level().getGameTime() + FALLBACK_TICKS, loser.getUUID());
    }

    /** Marks whatever is behind this id, wherever in the world it is. */
    public static void markById(MinecraftServer server, UUID id, UUID waitFor)
    {
        if(id == null)
        {
            return;
        }
        LivingEntity target = resolve(server, id);
        if(target == null
            || !de.cas_ual_ty.dueldimension.duel.orichalcos.SealSettings.enabled())
        {
            return;
        }
        arm(target, target.level().getGameTime() + FALLBACK_TICKS,
            waitFor == null ? target.getUUID() : waitFor);
    }

    /**
     * Marks any living entity, starting immediately.
     * <p>
     * For the debug item and the command. There is no duel to leave, so there
     * is nothing to wait for and no config to consult — someone holding the
     * item has already said what they want.
     *
     * @return whether the mark was taken (false if already marked)
     */
    public static boolean force(LivingEntity target)
    {
        if(target == null || target.level().isClientSide())
        {
            return false;
        }
        return arm(target, target.level().getGameTime(), null);
    }

    private static boolean arm(LivingEntity target, long deadline, UUID waitFor)
    {
        if(PENDING.putIfAbsent(target.getUUID(),
            new Pending(deadline, -1, false, waitFor)) != null)
        {
            return false;
        }
        DuelDimension.log("Orichalcos: marked " + target.getName().getString());
        return true;
    }

    /**
     * The client saying the player has closed the duel and has their character
     * back. Only brings the sequence forward; it can never call it off.
     */
    public static void playerLeftDuel(ServerPlayer player)
    {
        if(player == null)
        {
            return;
        }
        long now = player.level().getGameTime();
        // Everything waiting on THIS player, which is their own mark and any
        // duelist they just beat. Not every pending mark: another player's
        // duel, somewhere else entirely, is none of this one's business.
        PENDING.replaceAll((id, pending) ->
            pending.started() || !player.getUUID().equals(pending.waitFor()) ? pending
                : new Pending(Math.min(pending.deadline(), now),
                    pending.startedAt(), pending.beamed(), pending.waitFor()));
    }

    /** Forgets a marked target. */
    public static void clear(UUID target)
    {
        PENDING.remove(target);
    }

    /**
     * Advances every marked target. Ticked after the duels are, so a duel that
     * concludes this tick has already marked its loser before the queue looks.
     */
    public static void tick(MinecraftServer server)
    {
        if(PENDING.isEmpty())
        {
            return;
        }
        long now = server.overworld().getGameTime();

        for(Iterator<Map.Entry<UUID, Pending>> it = PENDING.entrySet().iterator(); it.hasNext();)
        {
            Map.Entry<UUID, Pending> entry = it.next();
            LivingEntity target = resolve(server, entry.getKey());
            if(target == null || !target.isAlive())
            {
                if(target != null)
                {
                    // Died some other way first. The seal has nothing left to
                    // take, so it stops rather than waiting for a corpse.
                    it.remove();
                }
                // Otherwise offline or unloaded, and kept rather than dropped:
                // logging out to dodge the seal only postpones it.
                continue;
            }

            Pending pending = entry.getValue();
            if(!pending.started())
            {
                if(now >= pending.deadline())
                {
                    entry.setValue(new Pending(pending.deadline(), now, false,
                        pending.waitFor()));
                    begin(target);
                }
                continue;
            }

            long elapsed = now - pending.startedAt();
            if(!pending.beamed() && elapsed >= GROW_TICKS + HOLD_TICKS)
            {
                // The beam, and the sound that goes with it. Guarded by the
                // flag rather than by an exact tick comparison so a tick the
                // server ran long can never skip it.
                playAt(target, de.cas_ual_ty.dueldimension.DdSounds.ORICHALCOS_FADE);
                entry.setValue(new Pending(pending.deadline(), pending.startedAt(), true,
                    pending.waitFor()));
            }
            if(elapsed >= GROW_TICKS + HOLD_TICKS + KILL_DELAY_TICKS)
            {
                kill(target);
                it.remove();
            }
        }
    }

    /**
     * The marked entity, wherever it is.
     * <p>
     * The player list first because that is the common case and it is a map
     * lookup; only then the levels, because {@code getEntityInAnyDimension}
     * has to be asked per level and a marked mob could have gone through a
     * portal between being marked and the seal closing.
     */
    private static LivingEntity resolve(MinecraftServer server, UUID id)
    {
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        if(player != null)
        {
            return player;
        }
        for(ServerLevel level : server.getAllLevels())
        {
            // 26.2 has getEntityInAnyDimension; 1.21.1 does not, so the
            // server's levels are walked. Same answer, and the same null when
            // the entity is gone -- this is a lookup by UUID either way.
            Entity entity = null;
            if(level.getServer() != null)
            {
                for(net.minecraft.server.level.ServerLevel candidate
                    : level.getServer().getAllLevels())
                {
                    entity = candidate.getEntity(id);
                    if(entity != null)
                    {
                        break;
                    }
                }
            }
            if(entity instanceof LivingEntity living)
            {
                return living;
            }
        }
        return null;
    }

    /**
     * Starts the sequence: the seal closing in under the target.
     * <p>
     * The visuals are the client's; the server only says when. Sending the
     * start rather than each frame means a viewer whose connection stutters
     * still sees a seal that finishes when the kill lands.
     */
    private static void begin(LivingEntity target)
    {
        DuelDimension.log("Orichalcos: the seal closes on " + target.getName().getString());

        // The buzz runs the length of the expansion -- it is loudest as the
        // seal appears and has decayed to nothing by the time it lands.
        playAt(target, de.cas_ual_ty.dueldimension.DdSounds.ORICHALCOS_BUZZ);

        OrichalcosMessages.SealBegin message =
            new OrichalcosMessages.SealBegin(target.getId(), GROW_TICKS, HOLD_TICKS,
                FADE_TICKS);
        // Everyone who can see it, plus the target if the target is a player --
        // PlayerLookup.tracking does not include a player in its own tracker
        // set, so the one person the seal is actually taking would otherwise be
        // the one person who could not see it.
        for(ServerPlayer viewer : net.fabricmc.fabric.api.networking.v1.PlayerLookup
            .tracking(target))
        {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(viewer, message);
        }
        if(target instanceof ServerPlayer self)
        {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(self, message);
        }
    }

    /**
     * Plays a sound where the seal is, for everyone near enough to see it.
     * <p>
     * Both clips are mono, which is what makes them positional: this version
     * only applies 3D attenuation and panning to mono audio, and a stereo clip
     * would play at the same volume however far away you stood.
     */
    private static void playAt(LivingEntity target, net.minecraft.sounds.SoundEvent sound)
    {
        target.level().playSound(null, target.getX(), target.getY(), target.getZ(),
            sound, net.minecraft.sounds.SoundSource.PLAYERS, 1F, 1F);
    }

    /**
     * The kill.
     * <p>
     * {@code hurtServer} rather than {@code hurt}: the two-argument form does
     * not exist in this version — damage is dealt with the level in hand so
     * the server half is explicit. MAX_VALUE rather than the target's current
     * health because health can change between the seal starting and closing,
     * and a soul is not taken by degrees.
     */
    private static void kill(LivingEntity target)
    {
        if(!(target.level() instanceof ServerLevel level))
        {
            return;
        }
        // With the beam, not after it: this is the sound of the soul leaving,
        // and it lasts exactly as long as the beam takes to fade.
        playAt(target, de.cas_ual_ty.dueldimension.DdSounds.ORICHALCOS_FADE);

        Holder<DamageType> type = level.registryAccess()
            .lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(ORICHALCOS);
        if(!target.hurt(new DamageSource(type), Float.MAX_VALUE))
        {
            // Creative, invulnerable, or a gamerule in the way. Said out loud
            // rather than swallowed: a seal that visibly closes and then does
            // nothing is a bug report waiting to happen.
            DuelDimension.log("Orichalcos: " + target.getName().getString()
                + " could not be harmed; the seal closed on nothing");
        }
    }
}
