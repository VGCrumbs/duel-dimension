package de.cas_ual_ty.dueldimension.util;

import java.io.File;
import java.nio.file.Path;

/**
 * Where this installation keeps its files.
 * <p>
 * <b>Why this exists.</b> Everything the mod writes -- the card database, the
 * image cache, exported decks, a player's binders -- used to be named with a
 * bare relative path ({@code new File("ydm_db")}), which java resolves against
 * the PROCESS WORKING DIRECTORY. That is not the game directory. It happens to
 * be the same folder when the vanilla launcher starts a client, and when a
 * server is started from inside its own folder, which is why nobody noticed;
 * it is a different folder for a launcher that passes {@code --gameDir}, for a
 * systemd unit with no {@code WorkingDirectory=}, and for any panel wrapper
 * that runs {@code java -jar /srv/mc/server.jar} from somewhere else. The
 * config already went to the right place ({@code FabricLoader.getConfigDir()}),
 * so an installation could end up with its settings in one tree and its 55&nbsp;MB
 * database in another, and two instances sharing a working directory would
 * share -- and race on -- one database.
 * <p>
 * The answer is the loader's own: {@code getGameDir()} is the {@code --gameDir}
 * program argument, falling back to the working directory, which is exactly the
 * question being asked. It is looked up through a {@code Throwable} guard
 * because this mod's non-Minecraft half is reachable from plain unit tests and
 * from the headless dev tools, where there is no loader at all -- there
 * {@code getGameDir()} throws {@code IllegalStateException("invoked too
 * early?")} -- and the working directory is then the only honest answer, which
 * is also the answer those callers got before.
 * <p>
 * <b>Nothing changes for a dev run.</b> Loom starts the client and the server
 * with the working directory set to {@code run/} and no {@code --gameDir}, so
 * the loader's game directory and the working directory are the same folder and
 * every path below resolves to the byte-identical place it did before.
 */
public final class GameDir
{
    /**
     * Cached once found. Only a real answer is remembered: the fallback is
     * recomputed each time, so a call made before the loader knows where the
     * game is does not freeze the wrong answer in for the rest of the run.
     */
    private static volatile Path resolved;

    private GameDir()
    {
    }

    /** The game directory, absolute; the working directory when there is no loader. */
    public static Path path()
    {
        Path known = resolved;
        if(known != null)
        {
            return known;
        }
        try
        {
            // Throwable, not Exception: outside a launched game this class may
            // not even be on the classpath, and that arrives as NoClassDefFoundError.
            Path fromLoader = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir();
            if(fromLoader != null)
            {
                known = fromLoader.toAbsolutePath().normalize();
                resolved = known;
                return known;
            }
        }
        catch(Throwable noLoader)
        {
            // Deliberately quiet: a unit test asking where a deck file goes is
            // not a broken installation, and this is asked from static
            // initialisers where logging would be noise on every run.
        }
        return Path.of("").toAbsolutePath().normalize();
    }

    /** A file directly inside the game directory. */
    public static File file(String name)
    {
        return path().resolve(name).toFile();
    }

    /** A path directly inside the game directory. */
    public static Path resolve(String name)
    {
        return path().resolve(name);
    }
}
