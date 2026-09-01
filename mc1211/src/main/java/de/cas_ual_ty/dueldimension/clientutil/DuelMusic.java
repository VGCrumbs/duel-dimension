package de.cas_ual_ty.dueldimension.clientutil;

import de.cas_ual_ty.dueldimension.DdSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The music a duel is played to.
 * <p>
 * Client-side and personal: which track plays, and whether it plays at all, is
 * a preference of the person listening, so it is decided here and stored beside
 * the mat colour rather than travelling to the server. Two duellists can be
 * listening to different things, or to nothing.
 * <p>
 * Registered under {@code record}, not {@code music}. The Music slider is the
 * one people turn off because they do not want Minecraft's ambient soundtrack,
 * and it is commonly sitting at zero — a duel track filed there is silently
 * never heard, which is exactly what happened the first time this was wired up.
 * {@code record} is vanilla's category for a track that is playing because
 * somebody deliberately put it on, which is what choosing a duel track is, and
 * it keeps the music on a different slider from the duel's effect sounds so the
 * two can be balanced against each other.
 */
public final class DuelMusic
{
    /**
     * A selectable track.
     * <p>
     * A record rather than an enum so adding one is a line here plus a sound
     * entry, and so the id that gets written to disk is stated rather than
     * being whatever {@code name()} happened to return.
     */
    public record Track(String id, String label, SoundEvent sound)
    {
    }

    /**
     * Every track, in the order the settings picker offers them.
     * <p>
     * The first is the default, which is what a fresh install and an
     * unrecognised stored id both fall back to.
     */
    public static final List<Track> TRACKS = List.of(
        new Track("normal", "Normal", DdSounds.MUSIC_NORMAL),
        new Track("something_evil", "Something Evil", DdSounds.MUSIC_SOMETHING_EVIL));

    /** Where the choice and the mute live between sessions. */
    private static Path file()
    {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
            .resolve("dueldimension-music.txt");
    }

    private static Track track = TRACKS.get(0);
    private static boolean muted;

    /**
     * How loud the track plays, 0..1, before the game's own sliders.
     * Fresh installs begin at 50%; the static loader below replaces this with
     * any volume the player has already saved.
     */
    private static float volume = 0.5F;

    /** The instance currently looping, or null when nothing is playing. */
    private static Loop playing;

    private DuelMusic()
    {
    }

    public static Track track()
    {
        return track;
    }

    public static boolean muted()
    {
        return muted;
    }

    public static float volume()
    {
        return volume;
    }

    /**
     * Sets the volume, and is heard while dragging.
     * <p>
     * Nothing is started or stopped here even at zero: the loop reads this
     * every tick, so turning it down is silence from a sound that is still
     * playing and turning it back up is immediate. Stopping the instance
     * instead would mean the track restarted from the beginning.
     */
    public static void setVolume(float value)
    {
        float clamped = Math.max(0F, Math.min(1F, value));
        if(clamped == volume)
        {
            return;
        }
        volume = clamped;
        save();
    }

    /**
     * Whether the game's own volume sliders would silence this even unmuted.
     * <p>
     * Worth asking because the failure is otherwise invisible: the button says
     * the music is on, the code plays it, and nothing comes out. The settings
     * tab says so rather than leaving the player to find the slider.
     */
    public static boolean silencedByGameVolume()
    {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.options != null
            && minecraft.options.getSoundSourceVolume(SoundSource.RECORDS) <= 0F;
    }

    /** The track with this id, or the default — never null, and never a guess. */
    public static Track byId(String id)
    {
        for(Track candidate : TRACKS)
        {
            if(candidate.id().equals(id))
            {
                return candidate;
            }
        }
        return TRACKS.get(0);
    }

    /**
     * Chooses a track. If a duel is playing, it changes at once rather than at
     * the next duel: the player picked it to hear it.
     */
    public static void setTrack(Track chosen)
    {
        if(chosen == null || chosen == track)
        {
            return;
        }
        track = chosen;
        save();
        if(playing != null)
        {
            stop();
            start();
        }
    }

    /** Silences the music, or brings it back, and remembers which. */
    public static void setMuted(boolean value)
    {
        if(muted == value)
        {
            return;
        }
        muted = value;
        save();
        if(muted)
        {
            stop();
        }
        else
        {
            start();
        }
    }

    public static void toggleMuted()
    {
        setMuted(!muted);
    }

    /**
     * Starts the music, if it should be playing and is not already.
     * <p>
     * Safe to call every time the duel screen opens: a duel that is watched,
     * closed and reopened should not stack a second copy of the track on top of
     * the first, so an existing loop is left alone rather than restarted.
     */
    public static void start()
    {
        if(muted || playing != null)
        {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if(minecraft == null)
        {
            return;
        }
        playing = new Loop(track.sound());
        // BEHAVIOUR NOTE: 1.21.1's SoundManager.play returns void -- there is no
        // PlayResult to inspect -- so this can no longer say whether the engine
        // opened a channel, only that the sound was handed to it. The failure this
        // logging exists to catch (silence with no exception) is no longer
        // distinguishable here, and `playing` is no longer cleared on a refusal;
        // the volume check that explains the commonest cause is kept.
        minecraft.getSoundManager().play(playing);
        de.cas_ual_ty.dueldimension.DuelDimension.log("Duel music: playing " + track.id()
            + (silencedByGameVolume()
                ? " -- but Jukebox/Note Blocks volume is at zero" : ""));
    }

    /**
     * Fades the music out. Safe when nothing is playing.
     * <p>
     * The instance is released here but keeps ticking itself down and stops
     * when it reaches silence, so a new track may start over the tail of the
     * old one — which is what makes changing track mid-duel a crossfade rather
     * than a gap.
     */
    /**
     * Whether the mod currently owns the music.
     * <p>
     * Asked by {@code VanillaMusicMixin} once a tick to decide whether
     * Minecraft's own background music may play. It covers every track the mod
     * starts, not just this class's -- the reward screen runs its own loop, and
     * a player standing in it should not hear the overworld either.
     * <p>
     * Deliberately NOT gated on {@link #muted()}: see the mixin's note.
     */
    public static boolean modMusicActive()
    {
        return playing != null
            || de.cas_ual_ty.dueldimension.clientutil.statue.StatueMusic.isPlaying();
    }

    public static void stop()
    {
        if(playing == null)
        {
            return;
        }
        playing.beginFadeOut();
        playing = null;
    }

    /**
     * Stops at once, with no fade.
     * <p>
     * For leaving the world, where there is nothing left to fade over: the
     * sound engine is about to be torn down and a tail of duel music over the
     * title screen is not a nicety.
     */
    public static void stopNow()
    {
        if(playing == null)
        {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if(minecraft != null)
        {
            minecraft.getSoundManager().stop(playing);
        }
        playing = null;
    }

    /** The volume a fade climbs to, and how long each direction takes. */
    private static final float FULL_VOLUME = 1F;
    private static final int FADE_IN_TICKS = 40;
    private static final int FADE_OUT_TICKS = 30;

    /**
     * A looping, positionless sound that fades in and out.
     * <p>
     * {@code SimpleSoundInstance.forUI} — what the duel's effect sounds use —
     * plays once and cannot loop, so this is the tickable form with the fields
     * the base class leaves to subclasses set: relative, so it plays at the
     * listener rather than at a point in the world, and looping, so a
     * two-minute track carries a duel that runs longer.
     * <p>
     * The fade is done by moving {@code volume} from {@link #tick()}, which
     * works because {@code SoundEngine.tickInGameSound} re-reads the volume of
     * every ticking sound after ticking it and pushes the new value to the
     * channel. Starting at silence is safe for the same kind of reason: nothing
     * in {@code SoundEngine.play} rejects a sound for being quiet, so the first
     * tick is what makes it audible.
     */
    private static final class Loop extends AbstractTickableSoundInstance
    {
        /** How far into the fade-in, in ticks; stops climbing at the top. */
        private int fadedIn;

        /** How far into the fade-out, or -1 while the sound is not ending. */
        private int fadedOut = -1;

        /** The volume the fade-out started from, so it can begin mid-fade-in. */
        private float fadeOutFrom;

        private Loop(SoundEvent sound)
        {
            super(sound, SoundSource.RECORDS, RandomSource.create());
            looping = true;
            delay = 0;
            relative = true;
            attenuation = Attenuation.NONE;
            // The fade starts at its FIRST STEP, not at silence, and that is
            // not cosmetic. SoundEngine.play tests the calculated volume
            // against zero exactly; a sound that is silent at that instant is
            // flagged silent and comes back STARTED_SILENTLY with no channel
            // ever opened, so ramping it up afterwards raises the volume of
            // something that is not playing. One fortieth of full volume is
            // inaudible and is a real number.
            fadedIn = 1;
            // The player's volume is deliberately NOT applied to this first
            // value. It only has to be non-zero to get a channel opened (see
            // above); the very next tick replaces it with the real level,
            // which is what lets the track start even with the slider at zero
            // and become audible the moment it is raised.
            volume = FULL_VOLUME / FADE_IN_TICKS;
        }

        /**
         * Begins the fade out. Idempotent: asking twice does not restart it and
         * does not jump the volume back up.
         */
        private void beginFadeOut()
        {
            if(fadedOut < 0)
            {
                // From wherever it actually is. Muting during the fade-in would
                // otherwise snap to full volume and then fade from there.
                fadeOutFrom = volume;
                fadedOut = 0;
            }
        }

        @Override
        public void tick()
        {
            if(fadedOut >= 0)
            {
                fadedOut++;
                if(fadedOut >= FADE_OUT_TICKS)
                {
                    volume = 0F;
                    // The protected stop; the engine drops it after this tick.
                    stop();
                    return;
                }
                volume = fadeOutFrom * (1F - fadedOut / (float)FADE_OUT_TICKS);
                return;
            }
            if(fadedIn < FADE_IN_TICKS)
            {
                fadedIn++;
            }
            // Read fresh rather than captured, so the slider is heard as it
            // moves and not only at the next duel.
            volume = DuelMusic.volume() * (fadedIn / (float)FADE_IN_TICKS);
        }
    }

    private static void save()
    {
        try
        {
            Files.writeString(file(),
                track.id() + "\n" + muted + "\n" + volume + "\n");
        }
        catch(IOException unwritable)
        {
            // A listening preference is not worth crashing over, and the next
            // launch simply starts from the default again.
        }
    }

    static
    {
        try
        {
            Path stored = file();
            if(Files.isRegularFile(stored))
            {
                List<String> lines = Files.readAllLines(stored);
                if(!lines.isEmpty())
                {
                    track = byId(lines.get(0).strip());
                }
                if(lines.size() > 1)
                {
                    muted = Boolean.parseBoolean(lines.get(1).strip());
                }
                if(lines.size() > 2)
                {
                    // A file written before the slider existed has two lines
                    // and simply keeps the default.
                    try
                    {
                        volume = Math.max(0F, Math.min(1F,
                            Float.parseFloat(lines.get(2).strip())));
                    }
                    catch(NumberFormatException malformed)
                    {
                    }
                }
            }
        }
        catch(IOException unreadable)
        {
        }
    }
}
