package de.cas_ual_ty.dueldimension.clientutil;

import de.cas_ual_ty.dueldimension.DuelDimension;
import de.cas_ual_ty.dueldimension.clientutil.hub.MenuTheme;
import de.cas_ual_ty.dueldimension.clientutil.hub.MenuThemes;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Which menu palette the player chose, remembered between sessions.
 * <p>
 * <b>Its own file rather than a {@code ClientConfig} key</b>, for the same
 * reason {@code HoverPreviewSettings} and {@code SealSettings} are: that config
 * is read once at startup and has no public writer, and a setting changed from
 * a menu has to persist the moment it changes.
 * <p>
 * One line: the preset's id, or {@code custom} followed by the surface colour
 * and the font colour as hex. A file naming a preset that no longer exists
 * falls back to Graphite rather than refusing to load.
 */
public final class MenuThemeSettings
{
    private MenuThemeSettings()
    {
    }

    private static Path file()
    {
        return FabricLoader.getInstance().getConfigDir()
            .resolve("dueldimension-menu-theme.txt");
    }

    /** Read once, at client start. */
    public static void load()
    {
        try
        {
            Path path = file();
            if(!Files.isRegularFile(path))
            {
                return;
            }
            String[] parts = Files.readString(path, StandardCharsets.UTF_8).trim().split("\\s+");
            if(parts.length == 0 || parts[0].isEmpty())
            {
                return;
            }
            // Three fields now: the id, the surface and the FONT. A preset
            // supplies its own surface and accent, but not its font -- that is
            // a setting a player may have changed, so it is read back rather
            // than taken from the preset. A one-field file is an older one and
            // means the preset exactly as it ships.
            if(MenuTheme.CUSTOM.equals(parts[0]) && parts.length >= 3)
            {
                MenuThemes.choose(MenuTheme.custom(
                    (int)Long.parseLong(parts[1], 16), (int)Long.parseLong(parts[2], 16)));
                return;
            }
            MenuTheme preset = MenuTheme.preset(parts[0]);
            MenuThemes.choose(parts.length >= 3
                ? preset.withText((int)Long.parseLong(parts[2], 16)) : preset);
        }
        catch(Exception unreadable)
        {
            DuelDimension.warn("could not read the menu theme: " + unreadable);
        }
    }

    public static void save()
    {
        MenuTheme theme = MenuThemes.chosen();
        String line = theme.id() + ' ' + Integer.toHexString(theme.surface())
            + ' ' + Integer.toHexString(theme.text());
        try
        {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), line, StandardCharsets.UTF_8);
        }
        catch(IOException unwritable)
        {
            DuelDimension.warn("could not save the menu theme: " + unwritable);
        }
    }

    /** Chooses and remembers in one step, which is all any caller wants. */
    public static void apply(MenuTheme theme)
    {
        MenuThemes.choose(theme);
        save();
    }
}
