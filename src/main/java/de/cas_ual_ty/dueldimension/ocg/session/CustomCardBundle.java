package de.cas_ual_ty.dueldimension.ocg.session;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import de.cas_ual_ty.dueldimension.DuelDimension;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Custom cards shipped inside the mod.
 * <p>
 * A custom card needs three things and they live in three places: its rules in
 * a {@code .cdb} the engine reads, its behaviour in a Lua script, and its art
 * in the image cache. Its display JSON is handled separately and already —
 * {@code ydm_extras}, merged by {@link de.cas_ual_ty.dueldimension.DdDatabase}.
 * This unpacks the other two so a player installs the mod and has the cards,
 * rather than assembling folders by hand.
 *
 * <h2>Why unpack at all</h2>
 * Neither consumer can read out of a jar. The engine's database is opened
 * through SQLite, which needs a real file, and the script provider walks
 * directories. So both are written to the game directory on first run.
 *
 * <h2>What it will not do</h2>
 * <b>It never overwrites a file that is already there.</b> The unpacked folder
 * is also where a player authors their OWN cards — that is the whole point of
 * {@code EngineRuntime.Paths.customRoot()} — and a bundle that replaced its
 * contents every boot would delete their work. A shipped card whose file was
 * edited stays edited; deleting it is how the bundled copy comes back.
 */
public final class CustomCardBundle
{
    /** Where the bundle lives in the jar; the index names its contents. */
    private static final String RESOURCES = "/dueldimension_custom/";
    private static final String INDEX = RESOURCES + "index.json";

    private CustomCardBundle()
    {
    }

    /**
     * Unpacks anything missing. Cheap after the first run: it stats each named
     * file and copies only what is absent.
     */
    public static void install()
    {
        JsonArray index = readIndex();
        if(index == null)
        {
            return; // no bundled cards, which is a normal state
        }
        Path root = EngineRuntime.Paths.customRoot();
        int written = 0;
        for(int i = 0; i < index.size(); i++)
        {
            String name = index.get(i).getAsString();
            // The image cache is not under the custom root, so an entry may
            // name a destination outside it; "images/" is the one such prefix.
            Path target = name.startsWith("images/")
                ? imageTarget(name.substring("images/".length()))
                : root.resolve(name);
            if(target == null || Files.exists(target))
            {
                continue;
            }
            if(copy(RESOURCES + name, target))
            {
                written++;
            }
        }
        if(written > 0)
        {
            DuelDimension.log("Custom cards: unpacked " + written + " file(s) to " + root);
        }
    }

    /**
     * Where a bundled image goes. Named {@code <passcode>_<art>.<ext>} under a
     * size folder, exactly as a downloaded one is, so nothing downstream can
     * tell the difference between shipped art and fetched art.
     */
    private static Path imageTarget(String name)
    {
        int slash = name.indexOf('/');
        if(slash <= 0)
        {
            return null;
        }
        String folder = name.substring(0, slash);
        String file = name.substring(slash + 1);
        java.io.File cards = de.cas_ual_ty.dueldimension.clientutil.ClientProxy.cardImagesFolder;
        return new java.io.File(new java.io.File(cards, folder), file).toPath();
    }

    private static JsonArray readIndex()
    {
        try(InputStream in = CustomCardBundle.class.getResourceAsStream(INDEX))
        {
            if(in == null)
            {
                return null;
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                .getAsJsonArray();
        }
        catch(Exception unreadable)
        {
            DuelDimension.warn("Custom cards: could not read " + INDEX + ": "
                + unreadable.getMessage());
            return null;
        }
    }

    private static boolean copy(String resource, Path target)
    {
        try(InputStream in = CustomCardBundle.class.getResourceAsStream(resource))
        {
            if(in == null)
            {
                DuelDimension.warn("Custom cards: " + resource + " is named in the index but"
                    + " is not in the jar");
                return false;
            }
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            return true;
        }
        catch(IOException unwritable)
        {
            // One card failing to unpack is not worth refusing to start over;
            // it simply will not be playable, and the engine-unknown filter
            // hides it rather than letting it corrupt a deck.
            DuelDimension.warn("Custom cards: could not write " + target + ": "
                + unwritable.getMessage());
            return false;
        }
    }
}
