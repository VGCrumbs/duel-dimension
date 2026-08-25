package de.cas_ual_ty.dueldimension.clientutil;

import de.cas_ual_ty.dueldimension.DuelDimension;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Whether the deck builder's big hover preview waits for Shift.
 * <p>
 * ON by default. The panel covers a third of the collection and follows the
 * cursor, so browsing a grid means it is nearly always over the cards being
 * browsed. It is also the most expensive single thing the image pipeline does
 * -- a 512px decode per card hovered -- so not asking for one per mouse-move is
 * a saving as well as a preference.
 * <p>
 * <b>Its own file rather than a {@code ClientConfig} key.</b> That config is
 * read once at startup and has no public writer; a setting the player changes
 * from a menu needs to persist the moment they change it. Same decision, same
 * shape and same reasoning as {@code SealSettings} and the play mat file.
 */
public final class HoverPreviewSettings
{
    private static boolean needsShift = true;

    private HoverPreviewSettings()
    {
    }

    public static boolean needsShift()
    {
        return needsShift;
    }

    public static void setNeedsShift(boolean value)
    {
        if(needsShift != value)
        {
            needsShift = value;
            save();
        }
    }

    private static Path file()
    {
        return FabricLoader.getInstance().getConfigDir()
            .resolve("dueldimension-hover-preview.txt");
    }

    private static void save()
    {
        try
        {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), Boolean.toString(needsShift), StandardCharsets.UTF_8);
        }
        catch(IOException unwritable)
        {
            // A preference that will not save is still a preference for this
            // session; refusing to apply it would be the worse failure.
            DuelDimension.warn("Could not save " + file() + ": " + unwritable.getMessage());
        }
    }

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
        needsShift = value;
    }
}
