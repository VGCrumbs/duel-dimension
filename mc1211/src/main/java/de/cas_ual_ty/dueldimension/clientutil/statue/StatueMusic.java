package de.cas_ual_ty.dueldimension.clientutil.statue;

import de.cas_ual_ty.dueldimension.DdSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * The reward screen's music: the disc's intro, then the disc's loop.
 *
 * <h2>Which track, and how that was settled</h2>
 * {@code jngl_win2.xau}, id <b>0x11D</b>. This was NOT chosen by name. The
 * obvious candidate was {@code sys_select} (0x125) — a selection screen, a track
 * called "select", and a tidy 60-second loop — and it was wrong. Reading the
 * running game's memory while the statue screen was actually up returned 0x11D
 * at all three of the addresses that track the current song. See
 * {@code 70 Audio} in the vault.
 *
 * <h2>Intro and loop</h2>
 * The file is <b>41.417 s</b> at 22050 Hz with its loop marked at samples
 * 325,492..651,152 — <b>14.762 s to 29.531 s</b>. So the original plays nearly
 * fifteen seconds of intro before it ever reaches the loop.
 * <p>
 * Only the loop BODY used to ship, because Minecraft cannot express "intro,
 * then loop" in one file: it either plays a sound once or restarts it from the
 * top, and looping the whole track re-triggers the intro on every pass. The
 * cost of that trade was the intro, and the cost of losing the intro was a
 * screen that opened partway through a phrase — which is exactly how it sounded.
 * <p>
 * So it is two sounds instead of one: the intro plays once, and the body starts
 * as it ends.
 *
 * <h3>Why the handover is on the clock and not on {@code delay}</h3>
 * The obvious way is {@code SoundInstance.delay}, and it does not work. That
 * field is counted in TICKS, and the sound engine advances its tick counter
 * from the client tick — so 295 ticks is 14.75 s only if the client never
 * misses one. Over fifteen seconds of a screen that is projecting twenty
 * thousand triangles in software, it misses plenty, and tick time falls behind
 * the wall clock while the audio hardware plays the intro at its own real rate.
 * The intro finishes, and the body is still counting. That is the cut-out.
 *
 * <p>So the body is started by {@link #pump()}, called from the screen every
 * FRAME and comparing real milliseconds. Frame timing has finer granularity
 * than the tick and, more to the point, a stutter cannot make the wall clock
 * run slow.
 *
 * <p>It fires {@link #HANDOVER_LEAD_MS} early on purpose. Whatever slop is left
 * -- a long frame, the engine's own latency opening a channel -- then lands as
 * a brief OVERLAP rather than a gap, and the two pieces overlap at a seam the
 * composer already made continuous. Silence there is obvious; a few tens of
 * milliseconds of doubling is not.
 *
 * <h2>Why this is not SimpleSoundInstance.forUI</h2>
 * That form plays once, cannot loop, and cannot be faded. This is the tickable
 * form with the fields the base class leaves to subclasses: {@code looping}, so
 * a 15-second body carries a screen somebody may sit on; {@code relative}, so it
 * plays at the listener rather than at a point in the world; and {@code delay},
 * which is what schedules the body behind the intro.
 *
 * <h2>Fading</h2>
 * The intro ramps up when the screen opens; both parts ramp down when it
 * leaves. The body does NOT fade in — the intro has already brought the level
 * up, and fading in again at the seam would dip in the middle of the track.
 * <p>
 * The fade out is why {@link #halt()} does not stop anything. Stopping is what
 * an instance does to ITSELF once it has faded to nothing; halt only asks. The
 * static references therefore outlive the screen by the length of the fade,
 * which is deliberate: {@link #isPlaying()} keeps reporting true while they do,
 * so {@code VanillaMusicMixin} holds Minecraft's own music back until the last
 * of ours is gone rather than letting it start over the tail.
 */
public final class StatueMusic extends AbstractTickableSoundInstance
{
    /** The intro and the loop. Either may be null. */
    private static StatueMusic intro;
    private static StatueMusic body;

    /**
     * How long the intro runs, in milliseconds.
     * <p>
     * The disc's loop start, 325,492 samples at 22050 Hz. Confirmed against the
     * encoded file, which probes at 14.7615 s.
     */
    private static final long INTRO_MS = 14762L;

    /** How early the body is started, to absorb slop as overlap not silence. */
    private static final long HANDOVER_LEAD_MS = 60L;

    /** When the intro began, on the wall clock. */
    private static long introStartedAt;

    /**
     * How much the gain moves per client tick.
     * <p>
     * The client ticks at 20 Hz, so 0.05 is one second either way. Long enough
     * to read as a fade and short enough that leaving the screen does not trail
     * music across whatever comes next.
     */
    private static final float FADE_STEP = 0.05F;

    /**
     * Where a fade in begins.
     * <p>
     * Small enough to be a fade and large enough that the engine opens a
     * channel for it. Zero would be the obvious choice and it is the reason the
     * music once vanished entirely: the sound engine refuses a sound whose
     * computed volume is zero -- it returns STARTED_SILENTLY, opens no channel,
     * and the instance is never ticked, so the ramp that was going to raise the
     * volume never runs.
     */
    private static final float FADE_START = 0.15F;

    /**
     * The loudest it gets.
     * <p>
     * Half. The disc's mix has this track sitting under the screen rather than
     * on top of it, and at full against Minecraft's own levels it drowns the
     * pointer and card sounds it is meant to sit behind.
     */
    private static final float FULL_VOLUME = 0.5F;

    private float gain;
    private boolean fadingOut;

    private StatueMusic(SoundEvent sound, boolean loop, int delayTicks, boolean fadeIn)
    {
        super(sound, SoundSource.RECORDS, RandomSource.create());
        looping = loop;
        delay = delayTicks;
        relative = true;
        gain = fadeIn ? FADE_START : 1F;
        volume = gain * FULL_VOLUME;
    }

    /**
     * Starts the pair, or turns a fade-out back around.
     * <p>
     * The second case is not hypothetical: closing the screen and reopening it
     * inside a second finds the old instances still fading, and starting a
     * second pair would leave two copies of the track running out of phase.
     */
    public static void start()
    {
        Minecraft minecraft = Minecraft.getInstance();
        if(minecraft == null)
        {
            return;
        }
        // Still ours? Then turn the fade around rather than starting a second
        // copy. "Still ours" is not simply "a reference exists": an intro that
        // played all the way through leaves its handle behind, because the
        // engine stops ticking a finished non-looping sound and the tick is what
        // releases it. Treating that stale handle as live is what made a second
        // visit open on the LOOP -- start() returned early, and pump() then saw
        // an intro whose elapsed time was minutes and started the body at once.
        long now = System.currentTimeMillis();
        boolean live = body != null
            || (intro != null && now - introStartedAt < INTRO_MS);
        if(live)
        {
            if(intro != null)
            {
                intro.fadingOut = false;
            }
            if(body != null)
            {
                body.fadingOut = false;
            }
            return;
        }
        intro = null;
        body = null;
        introStartedAt = now;
        intro = play(minecraft, new StatueMusic(DdSounds.STATUE_MUSIC_INTRO, false, 0, true));
        // The body is NOT started here and NOT queued with a delay. See pump().
    }

    /**
     * Hands one instance to the engine and reports what it did with it.
     * <p>
     * On 26.2 the engine's verdict is checked here, because two of its three
     * answers are silence with no exception. 1.21.1 does not return one.
     *
     * @return the instance
     */
    private static StatueMusic play(Minecraft minecraft, StatueMusic sound)
    {
        // 1.21.1's play() returns void -- there is no PlayResult to inspect, so
        // this cannot report a refusal the way the 26.2 copy does. Same
        // limitation DuelMusic records on its own start().
        minecraft.getSoundManager().play(sound);
        return sound;
    }

    /**
     * Starts the loop body when the intro is nearly done.
     * <p>
     * Called every frame from the screen. Real milliseconds, not ticks, and
     * early rather than late -- see the class note for why both of those matter.
     * <p>
     * Does nothing once the body exists, so calling it every frame is free after
     * the handover, and nothing at all if the intro never started.
     */
    public static void pump()
    {
        if(intro == null || introStartedAt == 0L)
        {
            return;
        }
        long elapsed = System.currentTimeMillis() - introStartedAt;
        if(body == null && elapsed >= INTRO_MS - HANDOVER_LEAD_MS)
        {
            Minecraft minecraft = Minecraft.getInstance();
            if(minecraft != null)
            {
                body = play(minecraft, new StatueMusic(DdSounds.STATUE_MUSIC, true, 0, false));
            }
        }
        if(elapsed >= INTRO_MS)
        {
            // Played out. The handle goes even though the instance was never
            // ticked to release itself -- holding it would make the next visit
            // think an intro was already running. Its last few milliseconds are
            // under the body by now and stopping it would be the audible thing,
            // not letting it end.
            intro = null;
        }
    }

    /** Whether any of it is still sounding. See DuelMusic.modMusicActive. */
    public static boolean isPlaying()
    {
        return intro != null || body != null;
    }

    /**
     * Asks both parts to fade out. Safe to call when nothing is playing.
     * <p>
     * Named halt() rather than stop() because the base class already has an
     * INSTANCE stop(), which a static method may not hide.
     * <p>
     * Hooked to {@code removed()} rather than {@code onClose()}, and that is the
     * deliberate choice: Minecraft calls removed() whether a screen is closed OR
     * merely replaced, while onClose only fires on the first. Hooking onClose
     * would leave the track playing over whatever screen came next -- a bug that
     * only appears when somebody navigates away in the one way nobody tested.
     */
    public static void halt()
    {
        if(intro != null)
        {
            intro.fadingOut = true;
        }
        if(body != null)
        {
            body.fadingOut = true;
        }
    }

    @Override
    public void tick()
    {
        if(fadingOut)
        {
            gain -= FADE_STEP;
            if(gain <= 0F)
            {
                gain = 0F;
                stop();
                release();
            }
        }
        else if(gain < 1F)
        {
            gain = Math.min(1F, gain + FADE_STEP);
        }
        volume = gain * FULL_VOLUME;
    }

    /**
     * Drops the static handle on this instance.
     * <p>
     * Only reached through a fade. An intro that simply plays to its end is not
     * released here -- the engine stops ticking a finished non-looping sound --
     * so the handle stays until {@link #halt()} fades it. That leaves
     * {@link #isPlaying()} answering true for the length of the body, which is
     * the right answer anyway: the body IS still playing.
     */
    private void release()
    {
        if(intro == this)
        {
            intro = null;
        }
        if(body == this)
        {
            body = null;
        }
    }
}
