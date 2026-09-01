package de.cas_ual_ty.dueldimension.clientutil.character;

import de.cas_ual_ty.dueldimension.character.CharacterLook;
import de.cas_ual_ty.dueldimension.clientutil.model.ModelHologram;
import de.cas_ual_ty.dueldimension.clientutil.model.ModelMesh;
import de.cas_ual_ty.dueldimension.clientutil.model.ModelSkeleton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Plays a duellist's footsteps when their feet actually land.
 *
 * <h2>What this replaces</h2>
 * Vanilla fires a step sound every time distance travelled passes a counter, so
 * the rate is right and the individual step is wherever the counter fell. That
 * is invisible on a cube and audible on a character with feet.
 * {@code StepSoundMixin} silences that one and this plays its own, at the
 * contact times {@link FootSteps} reads out of the clip.
 *
 * <h2>The local player, and why not everybody</h2>
 * Only the player this client is running. That is not a shortcut, it is where
 * the sound comes from: {@code Entity.move} runs client-side for the local
 * player only -- everyone else is moved by interpolation and their footsteps
 * arrive as sound packets the server has already timed and sent. Retiming those
 * would mean the server knowing which clip a client is drawing, which it does
 * not and should not.
 * <p>
 * So a duellist hears their own steps land with their own feet, and hears
 * everybody else's on Minecraft's timing. Written down because the asymmetry is
 * deliberate and would otherwise look like a bug.
 *
 * <h2>On the tick, not the frame</h2>
 * Twenty checks a second against contacts that are a third of a second apart at
 * a walk, so no step can fall between two ticks. Doing it per frame would fire
 * the same step twice on a fast machine unless the crossing test carried frame
 * timing as well, and a sound does not need sub-tick placement to land right.
 */
public final class StepSounds
{
    /**
     * How far through the clip we were last tick, so a contact crossed between
     * then and now can be spotted.
     * <p>
     * NaN means "no idea yet" -- the first tick after starting to walk, or after
     * changing clip. A step is not played on that tick: without a previous
     * position there is no interval to test, and guessing one fires a step at
     * whatever phase the walk happened to begin at.
     */
    private static float previous = Float.NaN;

    /** The clip the phase above belongs to, so a change of gait resets it. */
    private static String was;

    private StepSounds()
    {
    }

    public static void clear()
    {
        previous = Float.NaN;
        was = null;
    }

    /** Called once per client tick. */
    public static void tick(Minecraft client)
    {
        AbstractClientPlayer player = client.player;
        if(player == null || client.level == null)
        {
            clear();
            return;
        }
        CharacterLook look = ClientCharacters.look(player.getUUID());
        if(look == null)
        {
            clear();
            return;
        }
        ModelMesh mesh = CharacterModels.mesh(look.gender());
        ModelSkeleton skeleton = mesh == null ? null : mesh.skeleton();
        if(skeleton == null)
        {
            clear();
            return;
        }
        String clip = CharacterRenderer.clipOf(player, look);
        // Airborne feet do not land. Vanilla makes the same call -- it stops
        // accumulating step distance off the ground -- and without it a duellist
        // would tap out a walk cycle while falling.
        if(!CharacterRenderer.stepsDuring(clip) || !player.onGround() || player.isPassenger())
        {
            clear();
            return;
        }
        int index = skeleton.indexOf(clip);
        if(index < 0)
        {
            clear();
            return;
        }
        float[] contacts = FootSteps.contacts(mesh, clip);
        if(contacts.length == 0)
        {
            clear();
            return;
        }
        float duration = skeleton.animations().get(index).duration();
        float now = ModelHologram.loopPhase(skeleton, index,
            CharacterRenderer.rateOf(player, clip));
        if(!clip.equals(was) || Float.isNaN(previous))
        {
            was = clip;
            previous = now;
            return;
        }
        if(crossed(contacts, previous, now, duration))
        {
            play(client, player);
        }
        previous = now;
    }

    /**
     * Whether a contact time falls in the interval just elapsed.
     * <p>
     * <b>The interval wraps.</b> A cycle that ran past its end between two ticks
     * leaves {@code to} behind {@code from}, and a straight comparison finds
     * nothing -- which silently drops one step per loop, the one nearest the
     * end. Split at the loop point instead and test both halves.
     */
    private static boolean crossed(float[] contacts, float from, float to, float duration)
    {
        if(to >= from)
        {
            return between(contacts, from, to);
        }
        return between(contacts, from, duration) || between(contacts, -1F, to);
    }

    /** Half-open, so a contact exactly on a tick boundary fires once and not twice. */
    private static boolean between(float[] contacts, float from, float to)
    {
        for(float at : contacts)
        {
            if(at > from && at <= to)
            {
                return true;
            }
        }
        return false;
    }

    /**
     * The sound the block underfoot makes, at the volume vanilla uses.
     * <p>
     * {@code playLocalSound} rather than {@code level.playSound}: this is a
     * client-side retiming of a sound this client would have made anyway, and
     * broadcasting it would have everyone else hear a second set of footsteps
     * on top of the ones the server already sent.
     */
    private static void play(Minecraft client, AbstractClientPlayer player)
    {
        BlockPos at = BlockPos.containing(player.getX(),
            player.getY() - 0.2D, player.getZ());
        BlockState under = client.level.getBlockState(at);
        if(under.isAir())
        {
            return;
        }
        SoundType sound = under.getSoundType();
        client.level.playLocalSound(player.getX(), player.getY(), player.getZ(),
            sound.getStepSound(), SoundSource.PLAYERS,
            sound.getVolume() * 0.15F, sound.getPitch(), false);
    }
}
