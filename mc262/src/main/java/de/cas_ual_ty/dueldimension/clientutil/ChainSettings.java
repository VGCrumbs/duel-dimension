package de.cas_ual_ty.dueldimension.clientutil;

import de.cas_ual_ty.dueldimension.DuelDimension;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Whether the duel asks about every chain, or only when asked to.
 *
 * <h2>What manual actually changes</h2>
 * Nothing about the rules. The engine still offers every window it always
 * offered; manual mode answers the OPTIONAL ones with "no" before the player
 * sees them. A forced chain -- one the engine says must be answered -- is never
 * skipped, because declining it is not a legal answer and the duel would sit
 * there waiting.
 *
 * <h2>One gesture, two meanings</h2>
 * Holding right-click has always passed a chain window. It now means "do the
 * other thing", which is the same gesture in both modes: automatic asks and the
 * hold passes, manual passes and the hold asks. That is one rule to learn
 * rather than two, and it needs no second key.
 *
 * <h2>Its own file</h2>
 * Same shape and same reasoning as {@code HoverPreviewSettings}: the client
 * config is read once at startup and has no public writer, and a setting the
 * player toggles mid-duel has to persist the moment it changes.
 */
public final class ChainSettings
{
    private static boolean manual;

    private ChainSettings()
    {
    }

    public static boolean manual()
    {
        return manual;
    }

    public static void toggle()
    {
        manual = !manual;
        save();
    }

    private static Path file()
    {
        return FabricLoader.getInstance().getConfigDir()
            .resolve("dueldimension-chain-mode.txt");
    }

    public static void load()
    {
        try
        {
            Path path = file();
            if(Files.isRegularFile(path))
            {
                manual = Boolean.parseBoolean(
                    Files.readString(path, StandardCharsets.UTF_8).trim());
            }
        }
        catch(Exception unreadable)
        {
            DuelDimension.warn("could not read the chain mode: " + unreadable);
        }
    }

    private static void save()
    {
        try
        {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), Boolean.toString(manual), StandardCharsets.UTF_8);
        }
        catch(IOException unwritable)
        {
            DuelDimension.warn("could not save the chain mode: " + unwritable);
        }
    }
}
