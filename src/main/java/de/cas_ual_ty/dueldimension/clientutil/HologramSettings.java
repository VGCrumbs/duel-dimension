package de.cas_ual_ty.dueldimension.clientutil;

import de.cas_ual_ty.dueldimension.DuelDimension;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Whether monsters stand on their cards during an actual duel.
 * <p>
 * ON by default: a monster looming over its card is most of the reason to play
 * on a board in the world rather than on a screen, and a duellist who wanted
 * the flat version has the flat version one key away.
 * <p>
 * It is a setting rather than a given because it is a real trade. Ten sprites
 * standing on a board are ten things between a player and the cards behind
 * them, and how much that matters depends on the deck, the board size and how
 * close the two duellists are standing -- which is exactly the kind of question
 * that is settled by trying it rather than by choosing for everybody.
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
    private static boolean enabled = true;

    private HologramSettings()
    {
    }

    public static boolean enabled()
    {
        return enabled;
    }

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
        return FabricLoader.getInstance().getConfigDir()
            .resolve("dueldimension-monster-holograms.txt");
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
            DuelDimension.warn("could not save the monster hologram setting: " + unwritable);
        }
    }

    /**
     * Read when the class is first touched, as every other setting in this mod
     * is. No initialiser to remember to call, and therefore none to forget.
     */
    static
    {
        boolean value = true;
        try
        {
            if(Files.isRegularFile(file()))
            {
                value = Boolean.parseBoolean(
                    Files.readString(file(), StandardCharsets.UTF_8).trim());
            }
        }
        catch(IOException unreadable)
        {
            // Unreadable reads as the default rather than failing the client.
            DuelDimension.warn("Could not read " + file() + ": " + unreadable.getMessage());
        }
        enabled = value;
    }
}
