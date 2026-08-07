package de.cas_ual_ty.dueldimension.duel.match;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * Every banlist the server offers, read from the reference client's own files.
 * <p>
 * {@link Banlist#parse} has existed all along and nothing called it: no list
 * was ever loaded, so the only list in the game was "no list" and a deck could
 * hold three of anything. These are the same {@code .lflist.conf} files EDOPro
 * ships, so a deck legal here is legal there for the same reasons.
 * <p>
 * Loaded once on the server. The files do not change while a server runs, and
 * a list is asked for on every deck validation.
 */
public final class Banlists
{
    /**
     * Log4j directly rather than through DuelDimension.log: this class is
     * reachable from a plain unit test, and touching the mod class there drags
     * in a static initialiser that needs a running game.
     */
    private static final org.apache.logging.log4j.Logger LOG =
        org.apache.logging.log4j.LogManager.getLogger();

    /**
     * Where the reference client keeps its lists. Overridable, and searched
     * rather than demanded: a server without EDOPro installed still runs, it
     * simply offers nothing but "No banlist".
     */
    private static final String PROPERTY = "ocg.lflists";
    private static final String[] FALLBACKS = {
        "C:/ProjectIgnis/repositories/lflists",
        "C:/ProjectIgnis/lflists",
    };

    private static volatile List<Banlist> loaded;

    private Banlists()
    {
    }

    /**
     * Every list, "No banlist" first.
     * <p>
     * That entry is always present and always first: it is the only list that
     * needs no files to exist, so it is what a server with no reference install
     * falls back to rather than offering an empty chooser.
     */
    public static List<Banlist> all()
    {
        List<Banlist> known = loaded;
        if(known == null)
        {
            known = load();
            loaded = known;
        }
        return known;
    }

    public static Banlist byId(String id)
    {
        for(Banlist list : all())
        {
            if(list.id().equals(id))
            {
                return list;
            }
        }
        // A list that has gone away since a match was configured falls back to
        // no list rather than refusing the duel.
        return Banlist.none();
    }

    /** Drops the cache, for a server reloading its data. */
    public static void invalidate()
    {
        loaded = null;
    }

    private static List<Banlist> load()
    {
        List<Banlist> lists = new ArrayList<>();
        lists.add(Banlist.none());

        Path directory = directory();
        if(directory == null)
        {
            LOG.info("No banlist directory found; offering no banlist only.");
            return List.copyOf(lists);
        }

        try(Stream<Path> files = Files.list(directory))
        {
            List<Path> conf = files.filter(path -> path.getFileName().toString().endsWith(".conf"))
                .sorted().toList();
            for(Path path : conf)
            {
                try(Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8))
                {
                    lists.addAll(Banlist.parse(reader));
                }
                catch(IOException | RuntimeException unreadable)
                {
                    // One malformed file is not a reason to offer no lists at
                    // all; the rest still load.
                    LOG.info("Could not read banlist " + path.getFileName()
                        + ": " + unreadable.getMessage());
                }
            }
        }
        catch(IOException cannotList)
        {
            LOG.info("Could not list " + directory + ": " + cannotList.getMessage());
        }

        LOG.info("Loaded " + (lists.size() - 1) + " banlist(s) from " + directory);
        return List.copyOf(lists);
    }

    private static Path directory()
    {
        String override = System.getProperty(PROPERTY);
        if(override != null && Files.isDirectory(Path.of(override)))
        {
            return Path.of(override);
        }
        for(String fallback : FALLBACKS)
        {
            Path path = Path.of(fallback);
            if(Files.isDirectory(path))
            {
                return path;
            }
        }
        return null;
    }

    /** The lists a chooser shows, as id and display name. */
    public static List<String> ids()
    {
        List<String> ids = new ArrayList<>();
        all().forEach(list -> ids.add(list.id()));
        return Collections.unmodifiableList(ids);
    }
}
