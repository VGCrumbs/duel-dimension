package de.cas_ual_ty.dueldimension.mixin.client;

import de.cas_ual_ty.dueldimension.clientutil.DuelMusic;
import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps Minecraft's own background music out of the mod's.
 *
 * <h2>Why a mixin and not a volume</h2>
 * There is no hook for "do not choose a song". {@code MusicManager.tick} picks
 * a track from {@code Minecraft.getSituationalMusic} and starts it on its own
 * schedule, and nothing the mod can register gets a say. Turning the player's
 * MUSIC slider down would work and is not ours to touch -- it would outlive the
 * duel and the player would have to put it back.
 *
 * <h2>What it does</h2>
 * While the mod is playing something -- a duel, the hub, the reward screen --
 * this cancels the vanilla manager's tick and stops whatever it had already
 * started. Cancelling alone is not enough: a song begun before the duel opened
 * keeps playing to its end, because the manager only touches
 * {@code currentMusic} from inside the tick this cancels.
 *
 * <p>The stop is called once per transition rather than every tick.
 * {@code stopPlaying()} is cheap but not free, and calling it sixty times a
 * second for the length of a duel is the sort of thing that shows up in a
 * profile long after anyone remembers putting it there.
 *
 * <h2>Muted is still ours</h2>
 * Suppression does not check {@link DuelMusic#muted()}. Silence during a duel
 * is a choice about the DUEL, and answering it by letting the overworld
 * soundtrack in would be a strange reading of it. Muting the mod means quiet,
 * not something else.
 */
@Mixin(MusicManager.class)
public abstract class VanillaMusicMixin
{
    /**
     * Whether the vanilla manager was silenced on the previous tick, so the
     * stop happens on the edge rather than continuously.
     */
    private boolean dueldimension$suppressed;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void dueldimension$holdVanillaMusic(CallbackInfo callback)
    {
        if(!DuelMusic.modMusicActive())
        {
            dueldimension$suppressed = false;
            return;
        }
        if(!dueldimension$suppressed)
        {
            dueldimension$suppressed = true;
            ((MusicManager)(Object)this).stopPlaying();
        }
        callback.cancel();
    }
}
