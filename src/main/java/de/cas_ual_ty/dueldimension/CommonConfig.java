package de.cas_ual_ty.dueldimension;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The mod's settings.
 * <p>
 * Forge supplied {@code ForgeConfigSpec}; Fabric supplies nothing, taking the
 * view that a config format is a mod's own business. So this is a small JSON
 * file in the config directory, read once, with the defaults built in.
 * <p>
 * The fields are still {@link Value}s answering {@code get()}, which is what
 * {@code ForgeConfigSpec.ConfigValue} did. That is deliberate: three dozen call
 * sites across the mod say {@code commonConfig.logBinderIO.get()} and none of
 * them care where the value came from. Keeping the shape means the port moves
 * where settings live without touching anything that reads one.
 */
public class CommonConfig
{
    /** A setting, read the way the Forge config's values were read. */
    public record Value<T>(T value)
    {
        public T get()
        {
            return value;
        }
    }

    public final Value<String> dbSourceUrl;

    public final Value<Integer> drawCooldown;
    public final Value<Integer> loserCooldown;
    public final Value<Integer> winnerCooldown;
    public final Value<Boolean> cooldownOnlyWhileOnServer;

    public final Value<List<? extends String>> defeatBothOnCDCommands;
    public final Value<List<? extends String>> defeatWinnerOffCDCommands;
    public final Value<List<? extends String>> defeatLoserOffCDCommands;
    public final Value<List<? extends String>> defeatBothOffCDCommands;

    public final Value<List<? extends String>> drawBothOnCDCommands;
    public final Value<List<? extends String>> drawPlayer1OffCDCommands;
    public final Value<List<? extends String>> drawPlayer2OffCDCommands;
    public final Value<List<? extends String>> drawBothOffCDCommands;

    public final Value<Boolean> mohistWorkaround;
    public final Value<Boolean> logBinderIO;

    private CommonConfig(JsonObject json)
    {
        dbSourceUrl = string(json, "dbSourceUrl",
            "https://raw.githubusercontent.com/CAS-ual-TY/YDM2-DB/main/db.json");
        // The Mohist workaround is a Forge-server quirk and means nothing here,
        // but the code that reads it should not have to branch on loader, so it
        // stays and simply answers false.
        mohistWorkaround = bool(json, "mohistWorkaround", false);
        logBinderIO = bool(json, "logInfiniteBinders", false);

        // Ticks; twenty to the second.
        drawCooldown = integer(json, "drawCooldown", 20 * 30);
        loserCooldown = integer(json, "loserCooldown", 20 * 30);
        winnerCooldown = integer(json, "winnerCooldown", 20 * 30);
        cooldownOnlyWhileOnServer = bool(json, "cooldownOnlyWhileOnServer", true);

        defeatBothOnCDCommands = strings(json, "defeatBothOnCDCommands");
        defeatWinnerOffCDCommands = strings(json, "defeatWinnerOffCDCommands");
        defeatLoserOffCDCommands = strings(json, "defeatLoserOffCDCommands");
        defeatBothOffCDCommands = strings(json, "defeatBothOffCDCommands");
        drawBothOnCDCommands = strings(json, "drawBothOnCDCommands");
        drawPlayer1OffCDCommands = strings(json, "drawPlayer1OffCDCommands");
        drawPlayer2OffCDCommands = strings(json, "drawPlayer2OffCDCommands");
        drawBothOffCDCommands = strings(json, "drawBothOffCDCommands");
    }

    private static Value<String> string(JsonObject json, String key, String fallback)
    {
        return new Value<>(json.has(key) ? json.get(key).getAsString() : fallback);
    }

    private static Value<Boolean> bool(JsonObject json, String key, boolean fallback)
    {
        return new Value<>(json.has(key) ? json.get(key).getAsBoolean() : fallback);
    }

    private static Value<Integer> integer(JsonObject json, String key, int fallback)
    {
        return new Value<>(json.has(key) ? json.get(key).getAsInt() : fallback);
    }

    private static Value<List<? extends String>> strings(JsonObject json, String key)
    {
        if(!json.has(key))
        {
            return new Value<>(List.of());
        }
        return new Value<>(json.getAsJsonArray(key).asList().stream()
            .map(element -> element.getAsString()).toList());
    }

    /**
     * Reads the config, writing the defaults out if there is not one yet.
     * <p>
     * A failure to read is not a failure to start: the defaults are the same
     * ones the file would have held, and refusing to load the mod because a
     * player put a comma in the wrong place would be a poor trade.
     */
    public static CommonConfig load()
    {
        Path file = FabricLoader.getInstance().getConfigDir()
            .resolve(DuelDimension.MOD_ID + ".json");
        try
        {
            if(Files.isRegularFile(file))
            {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                JsonObject json = new Gson().fromJson(text, JsonObject.class);
                return new CommonConfig(json == null ? new JsonObject() : json);
            }
            CommonConfig defaults = new CommonConfig(new JsonObject());
            Files.createDirectories(file.getParent());
            Files.writeString(file, defaults.write(), StandardCharsets.UTF_8);
            return defaults;
        }
        catch(IOException | RuntimeException unreadable)
        {
            DuelDimension.warn("Could not read " + file + " (" + unreadable.getMessage()
                + "); using defaults.");
            return new CommonConfig(new JsonObject());
        }
    }

    private String write()
    {
        JsonObject json = new JsonObject();
        json.addProperty("dbSourceUrl", dbSourceUrl.get());
        json.addProperty("mohistWorkaround", mohistWorkaround.get());
        json.addProperty("logInfiniteBinders", logBinderIO.get());
        json.addProperty("drawCooldown", drawCooldown.get());
        json.addProperty("loserCooldown", loserCooldown.get());
        json.addProperty("winnerCooldown", winnerCooldown.get());
        json.addProperty("cooldownOnlyWhileOnServer", cooldownOnlyWhileOnServer.get());
        return new GsonBuilder().setPrettyPrinting().create().toJson(json);
    }

    private static CommonConfig loaded;

    /**
     * The settings, read on first use.
     * <p>
     * Lazy because reading them touches the loader's config directory, which is
     * not there in a unit test -- and a test asking whether a deck is legal
     * should not need a game directory to find out.
     */
    public static synchronized CommonConfig get()
    {
        if(loaded == null)
        {
            loaded = load();
        }
        return loaded;
    }
}
