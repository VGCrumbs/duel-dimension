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
 * {@link Mode#OFF} by default.
 * <p>
 * That is a reversal. This used to default to {@link Mode#BOTH}, on the
 * reasoning that a monster looming over its card is most of the reason to play
 * on a board in the world rather than on a screen -- which is still true of what
 * a hologram IS, and was the wrong thing to decide a default on. A monster
 * standing on the board is also the single most expensive thing the duel draws
 * and the thing most likely to be in front of a zone that has to be read and
 * clicked, and both of those are paid on every duel by every player, including
 * the ones who never wanted it.
 * <p>
 * So the flat board is what a duel starts as, and the holograms are one click
 * away in the duel hub's settings rather than one click away from being turned
 * off. The three modes are unchanged; only which of them is the starting one.
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

    private static Mode mode = Mode.OFF;

    /**
     * Whether a monster with a 3D model is drawn as one at all.
     * <p>
     * Separate from {@link Mode}, and the difference matters: the mode decides
     * WHOSE monsters appear on the board, and this decides WHAT they appear as.
     * Off, a monster that has a model falls back to its flat sprite -- the same
     * thing a duellist who never installed the models sees, rather than an empty
     * square. So the two compose: holograms off and models on still shows
     * nothing, and models off with holograms on shows sprites.
     */
    private static boolean models = true;

    /**
     * Whether a model's texture is filtered the way the console that drew it
     * filtered, which is bilinearly.
     * <p>
     * On by default because it is the accurate answer, not the prettier one:
     * these skins are 64 and 128 pixels stretched over a whole dragon, and the
     * PlayStation 2's Graphics Synthesizer smoothed them. Point-sampled at the
     * size a Minecraft monster is drawn they read as a mosaic of hard squares
     * that the artist never saw.
     * <p>
     * Off is a real preference rather than a fallback, which is why it is a
     * setting: Minecraft is a game of hard texels, and a duellist who wants the
     * holograms to match everything else around them is not wrong.
     */
    private static boolean ps2 = true;

    private HologramSettings()
    {
    }

    public static Mode mode()
    {
        return mode;
    }

    /** Whether a monster with a model is drawn as one rather than as a sprite. */
    public static boolean models()
    {
        return models;
    }

    public static void setModels(boolean use)
    {
        if(models != use)
        {
            models = use;
            save();
        }
    }

    public static void setMode(Mode value)
    {
        if(mode != value)
        {
            mode = value;
            save();
        }
    }

    /** Whether model textures are filtered bilinearly, as the PS2 did. */
    public static boolean ps2()
    {
        return ps2;
    }

    /**
     * Changing this FORGETS EVERY BAKED MODEL, and has to.
     * <p>
     * The sampler is chosen when a texture is registered, because Minecraft
     * builds it in a private method and offers no say in it afterwards -- see
     * {@code Ps2Texture}. So a model already on the board keeps whatever
     * filtering it was born with, and the toggle would appear to do nothing
     * until something else happened to reload it. Dropping the cache makes the
     * next frame rebuild them, which is the same thing the editor's Reload
     * button does and costs a few milliseconds once.
     */
    public static void setPs2(boolean use)
    {
        if(ps2 != use)
        {
            ps2 = use;
            save();
            de.cas_ual_ty.dueldimension.clientutil.model.MonsterModels.clear();
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
            // Two values on one line, the mode first, so a file written by an
            // older build still parses -- parse() reads the first token and
            // ignores what it does not recognise.
            Files.writeString(file(), mode.name() + " models=" + models + " ps2=" + ps2,
                StandardCharsets.UTF_8);
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
        String text = saved.trim().split("\\s+")[0];
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
                String saved = Files.readString(file(), StandardCharsets.UTF_8);
                value = parse(saved);
                // Absent means ON, which is what every existing file says by
                // saying nothing: the models were not optional before this.
                models = !saved.contains("models=false");
                // Same rule: absent reads as ON, because it was not optional
                // before this and every existing file says nothing about it.
                ps2 = !saved.contains("ps2=false");
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
