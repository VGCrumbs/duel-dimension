package de.cas_ual_ty.dueldimension.clientutil;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Whether a duellist's head follows where their player is looking.
 *
 * <h2>Why this exists as a switch and not a constant</h2>
 * Partly because it is a reasonable preference -- a head that tracks the camera
 * is right for a character you are inhabiting and can read as unsettling on one
 * you are watching -- and partly because it is the only way to tell, from
 * inside the game, whether a distortion belongs to the head-look or to
 * something underneath it.
 * <p>
 * The male rig is the only one where {@code neck} and {@code neck_spine} sit
 * inside a rotated parent frame: its {@code neck} carries a 40 degree pivot and
 * {@code sys_h} carries the inverse. The algebra says the two cancel and the
 * head lands exactly where it is told, and the algebra has been worked through
 * twice. Turning the look OFF and seeing whether the head straightens is the
 * measurement that settles it either way, and no amount of reading the code is.
 */
public final class HeadTrackingSettings
{
    private static boolean enabled = true;
    private static boolean loaded;

    private HeadTrackingSettings()
    {
    }

    public static boolean enabled()
    {
        load();
        return enabled;
    }

    public static void setEnabled(boolean value)
    {
        load();
        if(enabled != value)
        {
            enabled = value;
            save();
        }
    }

    private static Path file()
    {
        return FabricLoader.getInstance().getConfigDir()
            .resolve("dueldimension-head-tracking.txt");
    }

    private static void load()
    {
        if(loaded)
        {
            return;
        }
        loaded = true;
        try
        {
            Path path = file();
            if(Files.exists(path))
            {
                enabled = Boolean.parseBoolean(
                    Files.readString(path, StandardCharsets.UTF_8).strip());
            }
        }
        catch(IOException unreadable)
        {
            // The default stands; a preference that will not read is not a
            // reason to stop drawing a head.
        }
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
            // Applies for this session regardless.
        }
    }
}
