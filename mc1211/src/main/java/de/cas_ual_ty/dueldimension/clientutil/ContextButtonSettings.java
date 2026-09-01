package de.cas_ual_ty.dueldimension.clientutil;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Whether a card's actions are offered as icons or as a list of words.
 *
 * <h2>Two systems, both kept</h2>
 * ICONS is World Championship 2011's: a horizontal row of picture buttons
 * centred above the card, using that game's own extracted art. LIST is
 * EDOPro's: a vertical stack of captioned rows, which is what this mod had
 * first and what {@code backup/context-menu} preserves.
 * <p>
 * The toggle exists because neither is simply better. The icons are the game
 * this mod is imitating and they read faster once learned; the words need no
 * learning at all, which matters for a menu whose entries are Yu-Gi-Oh rules
 * terms. A player who has never held a DS should not have to decode a sword.
 * <p>
 * <b>Its own file rather than a {@code ClientConfig} key</b>, for the reason
 * {@link HoverPreviewSettings} gives: that config is read once at startup and
 * has no public writer, and a setting changed from a menu has to persist the
 * moment it changes.
 */
public final class ContextButtonSettings
{
    /** How a card's actions are drawn. */
    public enum Style
    {
        /** The DS game's picture buttons, in a row above the card. */
        ICONS("ICONS"),
        /** EDOPro's captioned rows, stacked beside the card. */
        LIST("LIST");

        private final String label;

        Style(String label)
        {
            this.label = label;
        }

        public String label()
        {
            return label;
        }

        public Style next()
        {
            return this == ICONS ? LIST : ICONS;
        }
    }

    /**
     * Icons by default, because they are the thing that was asked for and the
     * thing the extracted art is here to serve. The list is one press away.
     */
    private static Style style = Style.ICONS;

    private static boolean loaded;

    private ContextButtonSettings()
    {
    }

    public static Style style()
    {
        load();
        return style;
    }

    public static boolean icons()
    {
        return style() == Style.ICONS;
    }

    public static void setStyle(Style value)
    {
        load();
        if(value != null && style != value)
        {
            style = value;
            save();
        }
    }

    private static Path file()
    {
        return FabricLoader.getInstance().getConfigDir()
            .resolve("dueldimension-context-buttons.txt");
    }

    /**
     * Read once, on the first ask.
     * <p>
     * Lazily rather than at mod init: this is asked from the duel screen and
     * from the settings menu, both of which are long after the config directory
     * exists, and neither of which wants a file read on the render thread more
     * than once.
     */
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
                String text = Files.readString(path, StandardCharsets.UTF_8).strip();
                for(Style candidate : Style.values())
                {
                    if(candidate.name().equalsIgnoreCase(text))
                    {
                        style = candidate;
                        return;
                    }
                }
            }
        }
        catch(IOException unreadable)
        {
            // The default stands. A preference file that cannot be read is not
            // a reason to refuse to draw a menu.
        }
    }

    private static void save()
    {
        try
        {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), style.name(), StandardCharsets.UTF_8);
        }
        catch(IOException unwritable)
        {
            // Same as above: the setting still applies for this session.
        }
    }
}
