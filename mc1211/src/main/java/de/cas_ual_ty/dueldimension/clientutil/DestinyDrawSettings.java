package de.cas_ual_ty.dueldimension.clientutil;

import de.cas_ual_ty.dueldimension.DuelDimension;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Whether a duel against a duel bot is played with Destiny Draws.
 *
 * <h2>ON, unlike the version between two players</h2>
 * A duel against a bot is practice, and this is the setting that decides
 * whether your Destiny Cards are worth choosing at all -- so the place you go
 * to try them is the place that has them switched on. A duel between two
 * players is an agreement, and an agreement starts from whatever both sides
 * expect, which is the ordinary rules; that one is settled per challenge in the
 * lobby.
 *
 * <h2>Remembered, and remembered on the CLIENT</h2>
 * Because the answer is about the person, not about the bot. A player who has
 * turned it off has turned it off for practising, not for one particular block
 * they happened to place -- and putting it on the bot would mean setting it
 * again on the next one.
 * <p>
 * Which also makes it a plain file rather than anything synced: nothing on the
 * server has an opinion about it, the client sends the answer with the duel it
 * asks for, and the server does as it is told for that duel only.
 *
 * <h2>Its own file rather than a {@code ClientConfig} key</h2>
 * The reason every setting in this mod has one: that config is read once at
 * startup and has no public writer, and a setting changed from a menu has to
 * persist the moment it changes.
 */
public final class DestinyDrawSettings
{
    private static boolean versusBots = true;

    private DestinyDrawSettings()
    {
    }

    /** Whether the next duel against a bot should offer Destiny Draws. */
    public static boolean versusBots()
    {
        return versusBots;
    }

    public static void setVersusBots(boolean value)
    {
        if(versusBots != value)
        {
            versusBots = value;
            save();
        }
    }

    private static Path file()
    {
        return FabricLoader.getInstance().getConfigDir()
            .resolve("dueldimension-destiny-draw.txt");
    }

    private static void save()
    {
        try
        {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), "bots=" + versusBots, StandardCharsets.UTF_8);
        }
        catch(IOException unwritable)
        {
            DuelDimension.warn("could not save the Destiny Draw setting: " + unwritable);
        }
    }

    /**
     * Read when the class is first touched, as every other setting in this mod
     * is. No initialiser to remember to call, and therefore none to forget.
     */
    static
    {
        try
        {
            if(Files.isRegularFile(file()))
            {
                // Absent reads as ON, which is what a file written by an older
                // build says by saying nothing.
                versusBots = !Files.readString(file(), StandardCharsets.UTF_8)
                    .contains("bots=false");
            }
        }
        catch(IOException unreadable)
        {
            DuelDimension.warn("Could not read " + file() + ": " + unreadable.getMessage());
        }
    }
}
