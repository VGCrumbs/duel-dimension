package de.cas_ual_ty.dueldimension.clientutil;

import de.cas_ual_ty.dueldimension.DuelDimension;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Whether monsters stand on their cards during an actual duel, and on whose
 * side of the board.
 * <p>
 * {@link Mode#BOTH} by default: a monster looming over its card is most of the
 * reason to play on a board in the world rather than on a screen, and a
 * duellist who wanted the flat version has the flat version one click away.
 * <p>
 * <b>Three states rather than two, because the board is not symmetrical.</b> A
 * board is stood at rather than looked down on, so your own monsters are the
 * ones between your eye and the far half — and the far half is where the
 * opponent's zones are, which are the ones you have to read and click on.
 * Theirs stand on the far side and block nothing of yours. So "off" and "on"
 * were the wrong two choices to offer: the useful third is to keep the monsters
 * that are worth looking at and drop the ones that are in the way.
 * <p>
 * That asymmetry is already handled in two places for the same reason — your
 * own are drawn faint whenever they are drawn at all, and they vanish entirely
 * while you are looking across the table. This setting is the permanent version
 * of the same judgement.
 * <p>
 * The display pedestal is NOT governed by this. That block exists to show a
 * monster; switching this off to unclutter a duel should not empty somebody's
 * museum.
 * <p>
 * <b>Its own file rather than a {@code ClientConfig} key</b>, for the reason
 * every setting in this mod has its own file: that config is read once at
 * startup and has no public writer, and a setting changed from a menu has to
 * persist the moment it changes.
 */
public final class HologramSettings
{
    /**
     * Ordered by how many monsters end up on the board, so cycling the button
     * moves in one direction rather than jumping about.
     */
    public enum Mode
    {
        OFF("Holograms Off"),
        ENEMY_ONLY("Enemy Only"),
        BOTH("Both");

        private final String label;

        Mode(String label)
        {
            this.label = label;
        }

        public String label()
        {
            return label;
        }

        public Mode next()
        {
            Mode[] all = values();
            return all[(ordinal() + 1) % all.length];
        }
    }

    private static Mode mode = Mode.BOTH;

    private HologramSettings()
    {
    }

    public static Mode mode()
    {
        return mode;
    }

    public static void setMode(Mode value)
    {
        if(mode != value)
        {
            mode = value;
            save();
        }
    }

    /**
     * Whether a monster on one side of the board is drawn at all.
     *
     * @param own {@code true} for the duellist's own half. The renderer speaks
     *            in the engine's {@code asked} numbering, where 0 is whoever is
     *            being served, so this is {@code asked == 0} there.
     */
    public static boolean showsFor(boolean own)
    {
        switch(mode)
        {
            case OFF:
                return false;
            case ENEMY_ONLY:
                return !own;
            default:
                return true;
        }
    }

    private static Path file()
    {
        return FabricLoader.getInstance().getConfigDir()
            .resolve("dueldimension-monster-holograms.txt");
    }

    private static void save()
    {
        try
        {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), mode.name(), StandardCharsets.UTF_8);
        }
        catch(IOException unwritable)
        {
            DuelDimension.warn("could not save the monster hologram setting: " + unwritable);
        }
    }

    /**
     * Reads the mode, accepting the {@code true}/{@code false} this setting
     * used to be.
     * <p>
     * The file name is unchanged, so an existing one still holds a boolean. A
     * duellist who had turned holograms off would otherwise have had them
     * silently come back — {@code Mode.valueOf("false")} throws, and every
     * failure here falls back to the default, which is BOTH.
     */
    private static Mode parse(String saved)
    {
        String text = saved.trim();
        for(Mode candidate : Mode.values())
        {
            if(candidate.name().equalsIgnoreCase(text))
            {
                return candidate;
            }
        }
        if(text.equalsIgnoreCase("true"))
        {
            return Mode.BOTH;
        }
        if(text.equalsIgnoreCase("false"))
        {
            return Mode.OFF;
        }
        return Mode.BOTH;
    }

    /**
     * Read when the class is first touched, as every other setting in this mod
     * is. No initialiser to remember to call, and therefore none to forget.
     */
    static
    {
        Mode value = Mode.BOTH;
        try
        {
            if(Files.isRegularFile(file()))
            {
                value = parse(Files.readString(file(), StandardCharsets.UTF_8));
            }
        }
        catch(IOException unreadable)
        {
            // Unreadable reads as the default rather than failing the client.
            DuelDimension.warn("Could not read " + file() + ": " + unreadable.getMessage());
        }
        mode = value;
    }
}
