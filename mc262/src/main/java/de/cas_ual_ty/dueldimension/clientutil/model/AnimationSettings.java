package de.cas_ual_ty.dueldimension.clientutil.model;

import de.cas_ual_ty.dueldimension.DuelDimension;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Whether a modelled monster does anything but stand there.
 * <p>
 * On by default, because a monster that swings when it attacks is most of the
 * point of it being a model. Off leaves the idle, which every monster has and
 * which loops harmlessly for ever.
 * <p>
 * <b>This costs duel pace, which is why it is a setting.</b> Duelists of the
 * Roses blocks its battle on the animation — its state machine sets a slot and
 * polls until the clip reports finished before advancing — and its attacks were
 * authored as cutscenes: a median of 8.7 seconds across the 683 monsters, up to
 * 33.7. Emulating that faithfully means an attack holds the board for as long as
 * the swing takes, and a duellist who would rather play quickly should not have
 * to choose between that and having models at all.
 * <p>
 * Switching it off restores EDOPro's own beat, two thirds of a second, because
 * with nothing to watch there is nothing to wait for.
 * <p>
 * <b>Its own file rather than a {@code ClientConfig} key</b>, for the reason
 * every setting in this mod has its own file: that config is read once at
 * startup and has no public writer, and a setting changed from a menu has to
 * persist the moment it changes.
 */
public final class AnimationSettings
{
    private static boolean extras;
    private static float speed;

    /**
     * The speeds offered, cycled one at a time.
     * <p>
     * A short list of round numbers rather than a slider, because this screen is
     * a column of one-line choices and a slider in it would be the only thing
     * needing a drag. Real speed, not real time: the duel step shortens with the
     * clip, so these are honestly what they say.
     */
    private static final float[] SPEEDS = {1F, 2F, 3F, 4F, 6F, 8F};

    /** Four, because a faithful swing is a long time to watch every battle. */
    public static final float DEFAULT_SPEED = 4F;

    private AnimationSettings()
    {
    }

    /**
     * How much faster than authored the battle animations play.
     * <p>
     * Duelists of the Roses ran its attacks as cutscenes — a median of 8.7
     * seconds across the 683 monsters, up to 33.7 — and the game blocked its
     * battle on them, which is what this mod now emulates. Faithful and slow are
     * the same thing here, so this is the dial between them.
     * <p>
     * It scales BOTH halves or it would be worse than useless: the phase the
     * model is posed at, and the length of the duel step waiting for it. Speed
     * up only the clip and the monster finishes early and stands about; speed up
     * only the step and the card breaks mid-swing, which is the bug this whole
     * area started with.
     */
    public static float speed()
    {
        return speed;
    }

    public static void setSpeed(float times)
    {
        float clamped = Math.clamp(times, 0.25F, 16F);
        if(speed != clamped)
        {
            speed = clamped;
            save();
        }
    }

    /** The next speed on the list, wrapping. */
    public static void cycleSpeed()
    {
        int at = 0;
        for(int i = 0; i < SPEEDS.length; i++)
        {
            if(Math.abs(SPEEDS[i] - speed) < 1.0e-3F)
            {
                at = i;
                break;
            }
        }
        setSpeed(SPEEDS[(at + 1) % SPEEDS.length]);
    }

    /** "4x", or "1.5x" for a value typed into the file by hand. */
    public static String speedLabel()
    {
        return speed == Math.rint(speed)
            ? (int)speed + "x" : String.format("%.2gx", speed);
    }

    /** Whether attack and hit animations play at all. */
    public static boolean extras()
    {
        return extras;
    }

    public static void setExtras(boolean play)
    {
        if(extras != play)
        {
            extras = play;
            save();
        }
    }

    private static Path file()
    {
        return FabricLoader.getInstance().getConfigDir()
            .resolve("dueldimension-model-animations.txt");
    }

    private static void save()
    {
        try
        {
            Files.createDirectories(file().getParent());
            // Two values on one line, the boolean first, so a file written
            // before the speed existed still reads as the setting it held.
            Files.writeString(file(), extras + " " + speed, StandardCharsets.UTF_8);
        }
        catch(Exception unwritable)
        {
            DuelDimension.warn("could not save the model animation setting: " + unwritable);
        }
    }

    /**
     * Read when the class is first touched, as every other setting in this mod
     * is. No initialiser to remember to call, and therefore none to forget.
     */
    static
    {
        boolean value = true;
        float times = DEFAULT_SPEED;
        try
        {
            if(Files.isRegularFile(file()))
            {
                String[] parts = Files.readString(file(), StandardCharsets.UTF_8)
                    .trim().split("\s+");
                value = parts.length == 0 || !"false".equalsIgnoreCase(parts[0]);
                if(parts.length > 1)
                {
                    try
                    {
                        times = Math.clamp(Float.parseFloat(parts[1]), 0.25F, 16F);
                    }
                    catch(NumberFormatException notANumber)
                    {
                        // An unreadable speed is the default speed, not a
                        // refusal to read the setting beside it.
                    }
                }
            }
        }
        catch(Throwable unreadable)
        {
            // Throwable, not IOException. Asking Fabric where the config folder
            // is throws outside a running client, and a static initialiser that
            // throws does not merely lose its setting -- it poisons the class
            // for the rest of the process, so everything that reads this becomes
            // a NoClassDefFoundError. The unit tests found exactly that.
            //
            // Unreadable, or unaskable, reads as the default.
            DuelDimension.warn("could not read the model animation setting: " + unreadable);
        }
        extras = value;
        speed = times;
    }
}
