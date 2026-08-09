package de.cas_ual_ty.dueldimension.duel.orichalcos;

import de.cas_ual_ty.dueldimension.CommonConfig;
import de.cas_ual_ty.dueldimension.DuelDimension;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Whether losing a duel with The Seal of Orichalcos on the field takes the
 * loser's soul.
 * <p>
 * <b>Why this is not just the {@link CommonConfig} value.</b> That config is
 * read once and immutable — three dozen call sites read it through {@code get()}
 * and none of them expect it to change — so a screen cannot write to it. This is
 * the mod's other config shape, the one {@code DuelMusic} and the play-mat
 * colour already use: a single value in its own tiny file, rewritten whenever it
 * changes and read back at startup. That is what makes a toggle a toggle rather
 * than a restart.
 * <p>
 * The {@code CommonConfig} entry is still what decides the DEFAULT, so a server
 * that ships a config with the rule on gets it on, and it stays the thing a
 * dedicated server's operator edits.
 * <p>
 * <b>This is a server-side rule.</b> It decides whether a player dies, so the
 * server's copy is the one that counts; a client toggling it in single player is
 * toggling its own integrated server, which is the case that matters. On a
 * dedicated server, a client's setting has no effect on anyone.
 */
public final class SealSettings
{
    private static boolean enabled;

    private SealSettings()
    {
    }

    /** Whether the rule is on. */
    public static boolean enabled()
    {
        return enabled;
    }

    /** Turns the rule on or off, and remembers it. */
    public static void setEnabled(boolean value)
    {
        if(enabled != value)
        {
            enabled = value;
            save();
        }
    }

    private static Path file()
    {
        return FabricLoader.getInstance().getConfigDir().resolve("dueldimension-seal.txt");
    }

    private static void save()
    {
        try
        {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), Boolean.toString(enabled), StandardCharsets.UTF_8);
        }
        catch(IOException unwritable)
        {
            // A preference that will not save is still a preference for this
            // session. Refusing to apply it would be the worse failure.
            DuelDimension.warn("Could not save " + file() + ": " + unwritable.getMessage());
        }
    }

    static
    {
        boolean value;
        try
        {
            value = Files.isRegularFile(file())
                ? Boolean.parseBoolean(Files.readString(file(), StandardCharsets.UTF_8).trim())
                // Never set, so the config's value is the answer.
                : CommonConfig.get().sealOfOrichalcosDeath.get();
        }
        catch(IOException | RuntimeException unreadable)
        {
            // Includes the case where there is no config directory at all,
            // which is every unit test.
            value = false;
        }
        enabled = value;
    }
}
