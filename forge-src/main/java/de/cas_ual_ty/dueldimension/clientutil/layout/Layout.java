package de.cas_ual_ty.dueldimension.clientutil.layout;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.cas_ual_ty.dueldimension.DuelDimension;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A screen's numbers, held as data rather than as constants.
 * <p>
 * Every position, size, padding and scale a screen uses is looked up by name
 * with a fallback, so the screen still draws correctly if a key is missing and
 * a designer can retune it without recompiling. It is also what the developer
 * inspector edits: because the whole layout is a flat map of named numbers, the
 * inspector can enumerate it, show a field per entry, and write it back.
 * <p>
 * Values are resolved in two steps. The shipped default lives in
 * {@code assets/dueldimension/layouts/<name>.json}; an override, if present,
 * lives under the game directory in {@code config/dueldimension/layouts/}. The
 * override wins, which is what lets the inspector save without touching the
 * mod jar, and deleting it restores the shipped layout.
 */
public final class Layout
{
    private static final Logger LOGGER = LogManager.getLogger();

    private static final Map<String, Layout> LOADED = new ConcurrentHashMap<>();

    private final String name;
    private final Map<String, Float> values = new LinkedHashMap<>();
    /** Keys read this session with the fallback used, so the inspector can list them. */
    private final Map<String, Float> seen = new LinkedHashMap<>();

    private Layout(String name)
    {
        this.name = name;
    }

    public String name()
    {
        return name;
    }

    /** The named layout, loading it the first time it is asked for. */
    public static Layout of(String name)
    {
        return LOADED.computeIfAbsent(name, key ->
        {
            Layout layout = new Layout(key);
            layout.load();
            return layout;
        });
    }

    /**
     * Re-reads every loaded layout from disk. This is the hot reload: the
     * screen holds no copies of these numbers, so the next frame simply draws
     * with the new ones.
     */
    public static void reloadAll()
    {
        LOADED.values().forEach(Layout::load);
    }

    /** Where an edited layout is written, and read back from in preference. */
    public static Path overrideFile(String name)
    {
        return Minecraft.getInstance().gameDirectory.toPath()
            .resolve("config").resolve(DuelDimension.MOD_ID).resolve("layouts")
            .resolve(name + ".json");
    }

    private void load()
    {
        values.clear();
        // Shipped defaults first, then the override on top, so an override may
        // set a single key without having to restate the whole file.
        readInto(shippedReader(), values);
        Path override = overrideFile(name);
        if(Files.isRegularFile(override))
        {
            try(Reader reader = Files.newBufferedReader(override, StandardCharsets.UTF_8))
            {
                readInto(reader, values);
            }
            catch(IOException failed)
            {
                LOGGER.warn("Could not read layout override {}: {}", override, failed.toString());
            }
        }
    }

    private Reader shippedReader()
    {
        Minecraft minecraft = Minecraft.getInstance();
        if(minecraft == null || minecraft.getResourceManager() == null)
        {
            return null;
        }
        ResourceLocation id = new ResourceLocation(DuelDimension.MOD_ID, "layouts/" + name + ".json");
        Optional<Resource> resource = minecraft.getResourceManager().getResource(id);
        if(resource.isEmpty())
        {
            return null;
        }
        try
        {
            return new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8);
        }
        catch(IOException failed)
        {
            return null;
        }
    }

    private static void readInto(Reader reader, Map<String, Float> into)
    {
        if(reader == null)
        {
            return;
        }
        try(Reader closeable = reader)
        {
            JsonObject root = JsonParser.parseReader(closeable).getAsJsonObject();
            for(String key : root.keySet())
            {
                if(root.get(key).isJsonPrimitive() && root.get(key).getAsJsonPrimitive().isNumber())
                {
                    into.put(key, root.get(key).getAsFloat());
                }
            }
        }
        catch(Exception malformed)
        {
            LOGGER.warn("Malformed layout json: {}", malformed.toString());
        }
    }

    /**
     * A named number, or the fallback when the file does not carry it.
     * <p>
     * The fallback is remembered, so a layout file that has never been written
     * still shows every tunable in the inspector rather than an empty list.
     */
    public float f(String key, float fallback)
    {
        seen.putIfAbsent(key, fallback);
        Float value = values.get(key);
        return value == null ? fallback : value;
    }

    public int i(String key, int fallback)
    {
        return Math.round(f(key, fallback));
    }

    public void set(String key, float value)
    {
        values.put(key, value);
    }

    /** Every tunable: what the file holds, plus anything asked for this session. */
    public Map<String, Float> entries()
    {
        Map<String, Float> all = new LinkedHashMap<>(seen);
        all.putAll(values);
        return all;
    }

    /** Writes the current values as the override. Developer action only. */
    public void save() throws IOException
    {
        Path file = overrideFile(name);
        Files.createDirectories(file.getParent());
        JsonObject root = new JsonObject();
        entries().forEach((key, value) ->
        {
            // Whole numbers are written as integers so the file reads like the
            // pixel values it holds rather than 30.0 everywhere.
            if(value == Math.rint(value))
            {
                root.addProperty(key, value.intValue());
            }
            else
            {
                root.addProperty(key, value);
            }
        });
        Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(root),
            StandardCharsets.UTF_8);
    }
}
