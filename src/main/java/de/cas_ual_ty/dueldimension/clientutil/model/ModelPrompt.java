package de.cas_ual_ty.dueldimension.clientutil.model;

import de.cas_ual_ty.dueldimension.DuelDimension;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Whether to offer a duellist the monster models they have not got.
 * <p>
 * The models are a large download and they are not shipped in the jar, so a
 * fresh install has none and every monster falls back to its sprite. That works
 * — the sprite is the fallback by design — but nothing on screen says a better
 * version exists, so the feature is invisible to anyone who was not told about
 * it.
 * <p>
 * <b>Asked once, and only when there is nothing at all.</b> A duellist with even
 * one {@code .glb} has found the folder and is not in need of directions, and
 * one who has said no should not be asked again on every launch. Those two
 * conditions are what keeps this from being nagging.
 */
public final class ModelPrompt
{
    /** Where the models are offered from. */
    public static final String URL =
        "https://drive.google.com/file/d/1vXZ7Ng9eaZbhH2o915jiFnv2n7PI8KtH/view?usp=sharing";

    private static boolean asked;
    /** So a "not now" lasts the session without being written down. */
    private static boolean offeredThisSession;
    /**
     * Set when the title screen is seen, acted on by the next client tick.
     * <p>
     * <b>Never opened from the screen event itself.</b> The first title screen
     * is built inside Minecraft's own constructor, so a listener that opens a
     * screen there reaches setScreenAndShow before the client has finished
     * being constructed -- and that renders a frame, against a frame limiter
     * that does not exist yet. The client dies on startup with a
     * NullPointerException that names none of this.
     * <p>
     * A tick is the earliest moment that cannot happen, and it costs nothing:
     * the title screen is still up when it arrives.
     */
    private static volatile net.minecraft.client.gui.screens.Screen wanted;

    private ModelPrompt()
    {
    }

    /**
     * Whether to put the question up.
     * <p>
     * Asked of the FOLDER rather than of a flag: a duellist who installed the
     * models by hand, or who deleted them again, gets the right answer without
     * anything having recorded either event.
     */
    public static boolean shouldOffer()
    {
        if(asked || offeredThisSession)
        {
            return false;
        }
        return MonsterModels.names().isEmpty();
    }

    /**
     * Noticed the title screen; the tick will put the question up.
     * <p>
     * The screen itself is kept, not just a flag, so the prompt can go back to
     * the one already standing. Building a second title screen to return to
     * would re-run its init -- which is what raised this notice in the first
     * place, and would raise it again.
     */
    public static void request(net.minecraft.client.gui.screens.Screen title)
    {
        wanted = title;
    }

    /** The screen to return to, or null if there is nothing to ask. */
    public static net.minecraft.client.gui.screens.Screen takeRequest()
    {
        net.minecraft.client.gui.screens.Screen title = wanted;
        if(title == null)
        {
            return null;
        }
        wanted = null;
        // Re-checked rather than trusted: a tick is a moment later than the
        // notice, and "not now" may have been answered in between.
        return shouldOffer() ? title : null;
    }

    /** Not now: silent until the next launch. */
    public static void notNow()
    {
        offeredThisSession = true;
    }

    /** Never again: written down, because it has to outlive the session. */
    public static void never()
    {
        offeredThisSession = true;
        if(!asked)
        {
            asked = true;
            save();
        }
    }

    private static Path file()
    {
        return FabricLoader.getInstance().getConfigDir()
            .resolve("dueldimension-model-prompt.txt");
    }

    private static void save()
    {
        try
        {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), "asked", StandardCharsets.UTF_8);
        }
        catch(Exception unwritable)
        {
            DuelDimension.warn("could not save the model prompt setting: " + unwritable);
        }
    }

    static
    {
        boolean value = false;
        try
        {
            value = Files.isRegularFile(file());
        }
        catch(Throwable unreadable)
        {
            // Throwable: asking Fabric where the config folder is throws outside
            // a running client, and a static initialiser that throws poisons the
            // class for the whole process rather than merely losing its setting.
            DuelDimension.warn("could not read the model prompt setting: " + unreadable);
        }
        asked = value;
    }
}
