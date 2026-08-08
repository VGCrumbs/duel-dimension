"""Adds the music volume preference behind the duel's slider.

Read fresh by the loop every tick rather than captured into the sound
instance, so dragging the slider is heard while dragging. Nothing is started
or stopped at zero: the loop stays open and silent, because stopping it would
mean the track restarted from the beginning when it came back.
"""
import io

P = "src/main/java/de/cas_ual_ty/dueldimension/clientutil/DuelMusic.java"
s = io.open(P, encoding="utf-8").read()


def sub(old, new, label):
    global s
    assert old in s, "anchor missing: " + label
    s = s.replace(old, new, 1)
    print("   ok:", label)


sub("""    private static Track track = TRACKS.get(0);
    private static boolean muted;""",
    """    private static Track track = TRACKS.get(0);
    private static boolean muted;

    /** How loud the track plays, 0..1, before the game's own sliders. */
    private static float volume = 1F;""", "volume field")

sub("""    public static boolean muted()
    {
        return muted;
    }""",
    """    public static boolean muted()
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
    }""", "volume accessors")

sub("""            if(fadedIn < FADE_IN_TICKS)
            {
                fadedIn++;
                volume = FULL_VOLUME * (fadedIn / (float)FADE_IN_TICKS);
            }""",
    """            if(fadedIn < FADE_IN_TICKS)
            {
                fadedIn++;
            }
            // Read fresh rather than captured, so the slider is heard as it
            // moves and not only at the next duel.
            volume = DuelMusic.volume() * (fadedIn / (float)FADE_IN_TICKS);""",
    "loop reads volume live")

sub("""            fadedIn = 1;""",
    """            fadedIn = 1;
            // The player's volume is deliberately NOT applied to this first
            // value. It only has to be non-zero to get a channel opened (see
            // above); the very next tick replaces it with the real level,
            // which is what lets the track start even with the slider at zero
            // and become audible the moment it is raised.""",
    "ctor note")

sub('Files.writeString(file(), track.id() + "\\n" + muted + "\\n");',
    'Files.writeString(file(),\n'
    '                track.id() + "\\n" + muted + "\\n" + volume + "\\n");',
    "save three lines")

sub("""                if(lines.size() > 1)
                {
                    muted = Boolean.parseBoolean(lines.get(1).strip());
                }""",
    """                if(lines.size() > 1)
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
                }""", "load third line")

io.open(P, "w", encoding="utf-8", newline="\n").write(s)
print("DuelMusic: volume preference wired")
