package de.cas_ual_ty.dueldimension.ocg.session;

import de.cas_ual_ty.dueldimension.util.GameDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * The rules engine this mod ships inside its own jar, and how it reaches a
 * directory the engine can actually read.
 * <p>
 * <b>Why any of this exists.</b> Duel Dimension does not implement Yu-Gi-Oh!'s
 * rules; it loads ocgcore and plays Project Ignis's card scripts. Until now
 * those had to come from an EDOPro install the player made themselves, so a
 * clean machine installed the mod and was told to go and install something else
 * before it could duel. The jar now carries a copy, and this class is what puts
 * it somewhere the engine can see.
 * <p>
 * <b>Why it has to be unpacked at all.</b> Nothing in the engine's reading path
 * can see inside a jar. {@link de.cas_ual_ty.dueldimension.ocg.HeadlessDuelRunner#cardScriptsDirectory}
 * is {@code Files.readAllBytes} over a {@link Path};
 * {@link de.cas_ual_ty.dueldimension.ocg.Sqlite#open} builds a
 * {@code jdbc:sqlite:} URL, and the SQLite driver opens a real file rather than
 * a stream; and the native core is handed to JNA by absolute path. A classpath
 * resource is invisible to all three, so the bundle is written to disk once and
 * read from there forever after.
 * <p>
 * <b>What it does not do.</b> It does not overrule an install the player already
 * has. {@link EngineRuntime.Paths#defaults()} prefers an explicit
 * {@code -Docg.*} override first and a discovered EDOPro second, and only then
 * this copy -- theirs is the one that keeps updating and the one whose scripts
 * and database match each other. {@link #install()} asks that question before it
 * unpacks anything, so a machine that has EDOPro never pays for 12,702 scripts
 * it will not read.
 *
 * @see EngineRuntime.Paths#defaults()
 */
public final class EngineBundle
{
    /**
     * Log4j directly, not {@code DuelDimension.log}. This class is reached from
     * {@link EngineRuntime.Paths#defaults()}, which the headless dev tools call
     * with no game running at all; the mod class's static initialiser is not
     * something to drag in from there. Same reason
     * {@link de.cas_ual_ty.dueldimension.duel.match.Banlists} does it.
     */
    private static final org.apache.logging.log4j.Logger LOG =
        org.apache.logging.log4j.LogManager.getLogger();

    /** Everything the bundle ships sits under one prefix inside the jar. */
    private static final String RESOURCES = "/dueldimension_engine/";
    private static final String PROPERTIES = RESOURCES + "bundle.properties";

    /**
     * The card scripts travel as a single zip entry rather than as 12,702 jar
     * entries. It is the same bytes either way, but one entry keeps the jar's
     * own index down from fourteen thousand names to two thousand, and it lets
     * the unpack below stream straight through without an index file naming
     * every script -- a jar's directories cannot be listed through a
     * classloader, which is why the bundled card extras need one.
     */
    private static final String SCRIPTS_ZIP = RESOURCES + "script.zip";

    /**
     * The card database, packed the same way and unpacked by the same code.
     * <p>
     * It is not part of the rules engine and it does not go to the same place --
     * {@link #installCardDatabase(Path)} writes it into {@code ydm_db} beside the
     * game -- but it is the same problem with the same answer: fourteen thousand
     * small files that cannot be listed through a classloader and have to reach
     * a real directory before anything can read them. Rules and cards were two
     * separate downloads a player had to make before either of these existed;
     * one mechanism now carries both, rather than a second one being written to
     * do what this one already does.
     */
    private static final String CARD_DB_ZIP = RESOURCES + "ydm_db.zip";

    /** Where the unpacked copy lives, under the game directory. */
    private static final String FOLDER = "dueldimension_engine";

    /**
     * Written last and only after a complete unpack, so an installation
     * interrupted halfway can never be mistaken for a finished one: with no
     * stamp the next launch simply does the work again.
     */
    private static final String STAMP = ".bundle";

    /** Keys in the stamp file. */
    private static final String STAMP_VERSION = "version";
    private static final String STAMP_PIECES = "pieces";

    /**
     * The card database's own version, kept apart from the engine's.
     * <p>
     * The two payloads change on different schedules -- a card printed this
     * month is not a reason to rewrite 12,727 Lua scripts, and a new core build
     * is not a reason to walk fourteen thousand JSON files -- so each is
     * digested and stamped on its own.
     */
    private static final String STAMP_CARD_DB = "carddb.version";

    /** The four things an engine needs, named so the stamp can record a subset. */
    private static final String LIBRARY = "library";
    private static final String SCRIPTS = "scripts";
    private static final String CDB = "cdb";
    private static final String STRINGS = "strings";

    private static volatile Thread installer;
    private static volatile boolean attempted;
    private static volatile String outcome = "not attempted yet";
    private static volatile String cardDbOutcome = "not attempted yet";

    /** See {@link #installCardDatabase(Path)} for why this is not the class monitor. */
    private static final Object CARD_DB_LOCK = new Object();

    private EngineBundle()
    {
    }

    /** The unpacked bundle's own folder, whether or not anything is in it. */
    public static Path root()
    {
        return GameDir.resolve(FOLDER);
    }

    public static Path scriptsDir()
    {
        return root().resolve("script");
    }

    public static Path cdb()
    {
        return root().resolve("expansions").resolve("cards.cdb");
    }

    public static Path stringsConf()
    {
        return root().resolve("config").resolve("strings.conf");
    }

    /**
     * The unpacked native core for THIS platform.
     * <p>
     * Laid out under JNA's own {@code RESOURCE_PREFIX} -- {@code win32-x86-64},
     * {@code linux-x86-64}, {@code darwin-aarch64} -- so a build that ships
     * cores for more than one platform needs no new naming scheme and no code
     * change here, and a build that ships none for the platform it is running on
     * simply resolves to a file that is not there, which
     * {@link EngineRuntime.Paths#missing()} already knows how to explain.
     */
    public static Path library()
    {
        return root().resolve("native").resolve(com.sun.jna.Platform.RESOURCE_PREFIX)
            .resolve(EngineRuntime.Paths.libraryName());
    }

    /** What the build stamped this bundle with, or null when none is bundled. */
    public static String version()
    {
        Properties properties = read(PROPERTIES);
        return properties == null ? null : properties.getProperty(STAMP_VERSION);
    }

    /** Everything the build recorded about the bundle, for {@code /dueldimension}. */
    public static Properties describe()
    {
        Properties properties = read(PROPERTIES);
        return properties == null ? new Properties() : properties;
    }

    /** Whether this build carries an engine at all. */
    public static boolean isBundled()
    {
        return version() != null;
    }

    /**
     * Whether the jar carries a native core for the platform it is running on.
     * <p>
     * Asked of the jar, not of the unpacked folder, because the two answer
     * different questions and only this one is stable: the folder is empty
     * before the first unpack and on a machine whose EDOPro supplied everything.
     * {@link EngineRuntime.Paths#missing()} needs to tell "this build has no
     * core for your platform, go and install EDOPro" apart from "the core you
     * pointed -Docg.lib at is not there", and those are not the same sentence.
     */
    public static boolean hasNativeHere()
    {
        return EngineBundle.class.getResource(RESOURCES + "native/"
            + com.sun.jna.Platform.RESOURCE_PREFIX + "/"
            + EngineRuntime.Paths.libraryName()) != null;
    }

    /** What the last install attempt did, in one line. */
    public static String outcome()
    {
        return outcome;
    }

    /** What the last card-database install did, in one line. */
    public static String cardDatabaseOutcome()
    {
        return cardDbOutcome;
    }

    /** What the build stamped the bundled card database with, or null when none is bundled. */
    public static String cardDatabaseVersion()
    {
        Properties properties = read(PROPERTIES);
        return properties == null ? null : properties.getProperty(STAMP_CARD_DB);
    }

    /** Whether this build carries a card database at all. */
    public static boolean hasCardDatabase()
    {
        return cardDatabaseVersion() != null
            && EngineBundle.class.getResource(CARD_DB_ZIP) != null;
    }

    /**
     * Unpacks the bundled card database into {@code into}, writing only files
     * that are not already there.
     * <p>
     * <b>Why this exists.</b> The database was a download: tens of megabytes
     * fetched from GitHub on first launch, with the game waiting for it, and on
     * a machine with no connection the mod came up with no cards at all -- every
     * card in the world an unknown card, and no duel startable. The jar now
     * carries one.
     * <p>
     * <b>The rule that shapes every line below: an existing file is never
     * touched.</b> Not compared, not merged, not refreshed -- if a path is
     * occupied, the archive's copy is dropped on the floor. That is a stronger
     * promise than the engine's tree makes, and it is the right one here for a
     * reason the engine's tree does not have: {@code ydm_db} is a folder players
     * edit. Their own {@code alt_art} scans live in it, so do hand-corrected
     * card files, and none of it is anywhere else -- there is no second copy to
     * restore from. A refresh that "helpfully" corrected a file would be
     * unrecoverable, so the bundle never gets to make that call. The cost is
     * that a stale card file is never fixed by this path; correcting one is what
     * {@link de.cas_ual_ty.dueldimension.DdDatabase} bundled extras are for, and
     * they are the mechanism that is allowed to overwrite, on a list of 104
     * files chosen by hand.
     * <p>
     * <b>Why it is synchronous</b>, unlike {@link #beginInstall()}. The first
     * question {@link de.cas_ual_ty.dueldimension.DdDatabase#initDatabase()}
     * asks is whether {@code ydm_db} exists, and it decides whether to download
     * a database on the answer. That question has to be asked after this has
     * finished or it is asked about a folder that is halfway through being
     * written.
     *
     * @param into the database folder, normally {@code <gamedir>/ydm_db}
     * @return a one-line summary, also available from {@link #cardDatabaseOutcome()}
     */
    public static String installCardDatabase(Path into)
    {
        // A lock of its own, NOT `synchronized` on the class. install() is
        // `static synchronized` and holds that monitor for as long as it takes
        // to write 28 MB of card scripts, on the background thread
        // beginInstall() starts a moment before this is called. Sharing the
        // monitor would have made the database wait for the scripts and undone
        // the overlap those two were separated to get.
        synchronized(CARD_DB_LOCK)
        {
            try
            {
                cardDbOutcome = unpackCardDatabase(into);
            }
            catch(IOException | RuntimeException failed)
            {
            // Never fatal, for the same reason the engine's unpack is not: a mod
            // that loads and says why it has no cards is more use than one that
            // refuses to load. DdDatabase still has its download path, and it
            // still reports a database it could not get.
                cardDbOutcome = "failed (" + failed + ")";
                LOG.warn("Could not unpack the bundled card database into "
                    + into.toAbsolutePath() + ": " + failed);
            }
            return cardDbOutcome;
        }
    }

    private static String unpackCardDatabase(Path into) throws IOException
    {
        String version = cardDatabaseVersion();
        if(version == null)
        {
            return "this build bundles no card database";
        }

        // The stamp is the fast path and nothing more. It says "this exact
        // bundle has already been walked here", which after the first launch is
        // true and saves fourteen thousand existence checks on every boot.
        // Losing it costs a walk, not data -- the walk writes nothing that is
        // already there -- which is why deleting it is the documented repair.
        Path stamp = into.resolve(STAMP);
        Properties installed = readFile(stamp);
        if(version.equals(installed.getProperty(STAMP_CARD_DB)))
        {
            return "already installed (" + version + ")";
        }

        long started = System.currentTimeMillis();
        Path root = into.toAbsolutePath().normalize();
        Files.createDirectories(root);

        long written = 0;
        long kept = 0;
        long bytes = 0;
        byte[] buffer = new byte[1 << 16];

        try(InputStream in = EngineBundle.class.getResourceAsStream(CARD_DB_ZIP))
        {
            if(in == null)
            {
                throw new IOException("no " + CARD_DB_ZIP + " in this build");
            }
            try(ZipInputStream zip = new ZipInputStream(new java.io.BufferedInputStream(in)))
            {
                for(ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry())
                {
                    Path target = root.resolve(entry.getName()).normalize();
                    // Checked even though this build wrote the archive, for the
                    // same reason unpackScripts() checks: a ../ in a zip is
                    // written exactly where it says unless somebody looks.
                    if(!target.startsWith(root))
                    {
                        throw new IOException("bundled database entry escapes " + root + ": "
                            + entry.getName());
                    }
                    if(entry.isDirectory())
                    {
                        Files.createDirectories(target);
                        continue;
                    }
                    // The whole promise, in one branch. Files.exists and not
                    // isRegularFile: if a directory is sitting on that name then
                    // the player put it there, and that is still their folder to
                    // arrange, not ours to write over.
                    if(Files.exists(target))
                    {
                        kept++;
                        continue;
                    }
                    Files.createDirectories(target.getParent());
                    try(java.io.OutputStream out = new java.io.BufferedOutputStream(
                        Files.newOutputStream(target)))
                    {
                        for(int read = zip.read(buffer); read > 0; read = zip.read(buffer))
                        {
                            out.write(buffer, 0, read);
                            bytes += read;
                        }
                    }
                    written++;
                }
            }
        }

        // Written last, so an unpack interrupted halfway leaves no stamp and the
        // next launch simply walks the archive again -- which is harmless,
        // because everything it managed to write the first time is now a file it
        // will not touch.
        Properties record = new Properties();
        record.setProperty(STAMP_CARD_DB, version);
        try(java.io.Writer out = Files.newBufferedWriter(stamp, StandardCharsets.UTF_8))
        {
            record.store(out, "Written by Duel Dimension. Delete this file to restore"
                + " anything missing from the bundled card database; files you already"
                + " have are never overwritten.");
        }

        long took = System.currentTimeMillis() - started;
        String summary = "card database: wrote " + written + " files (" + bytes
            + " bytes), kept " + kept + " already present -- " + took + " ms";
        LOG.info(summary);
        return summary;
    }

    /**
     * Starts the unpack on a background thread.
     * <p>
     * Called once from mod initialisation. On a first run this overlaps the card
     * database download that follows it, which is the one other thing that
     * blocks start-up, so the two cost roughly what the slower of them costs
     * rather than their sum. Nothing waits for it here: the engine is not loaded
     * until a duel is actually requested, and {@link #awaitInstall()} is what
     * closes that gap on the rare occasion a player is quicker than the disk.
     */
    public static synchronized void beginInstall()
    {
        if(attempted || installer != null)
        {
            return;
        }
        Thread thread = new Thread(EngineBundle::install, "dueldimension-engine-bundle");
        // A daemon: an unpack in progress is not a reason to keep a JVM alive
        // that has otherwise been asked to shut down.
        thread.setDaemon(true);
        installer = thread;
        thread.start();
    }

    /**
     * Blocks until any install started by {@link #beginInstall()} has finished.
     * <p>
     * Deliberately does not start one. A unit test or a headless tool that asks
     * where the engine lives is asking a question, not asking for 28 MB to be
     * written beside it.
     */
    public static void awaitInstall()
    {
        Thread thread = installer;
        if(thread == null)
        {
            return;
        }
        try
        {
            thread.join();
        }
        catch(InterruptedException interrupted)
        {
            // Preserve the flag and answer with whatever is on disk; a duel that
            // cannot start is better than swallowing an interrupt.
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Unpacks whatever this machine is missing, and nothing it is not.
     * <p>
     * Idempotent and safe to call twice. Three questions decide the work:
     * <ol>
     * <li><b>Is anything bundled?</b> A build with no payload says so and stops.</li>
     * <li><b>What is already supplied?</b> {@link EngineRuntime.Paths#withoutBundle()}
     * is exactly what would be used if this class did not exist -- an override,
     * or a real EDOPro. Every piece it already answers for is left alone, so a
     * player who has EDOPro gets nothing written at all.</li>
     * <li><b>Is what is on disk still current?</b> The stamp records the build's
     * bundle version AND which pieces were unpacked, so a machine that needed
     * only the core last month still gets the scripts when EDOPro is
     * uninstalled.</li>
     * </ol>
     */
    public static synchronized void install()
    {
        try
        {
            outcome = run();
        }
        catch(IOException | RuntimeException failed)
        {
            // Never fatal. The mod loads without an engine and says why; a
            // failed unpack is one more reason to say why, not a crash.
            outcome = "failed (" + failed + ")";
            LOG.warn("Could not unpack the bundled rules engine into "
                + root().toAbsolutePath() + ": " + failed);
        }
        finally
        {
            attempted = true;
            installer = null;
        }
    }

    private static String run() throws IOException
    {
        String version = version();
        if(version == null)
        {
            return "this build bundles no rules engine";
        }

        // What an override or a real EDOPro already answers for. Asked BEFORE
        // anything is written, because the bundle's own folder would otherwise
        // be part of the answer.
        EngineRuntime.Paths supplied = EngineRuntime.Paths.withoutBundle();
        Set<String> required = new LinkedHashSet<>();
        if(!usable(supplied.library()))
        {
            required.add(LIBRARY);
        }
        // Card scripts, not constant.lua. See HeadlessDuelRunner.hasCardScripts:
        // an install can have every shared .lua and no card script at all, and
        // this test used to pass on one and unpack nothing.
        if(!de.cas_ual_ty.dueldimension.ocg.HeadlessDuelRunner
            .hasCardScripts(supplied.scriptsDir()))
        {
            required.add(SCRIPTS);
        }
        if(!Files.isRegularFile(supplied.cdb()))
        {
            required.add(CDB);
        }
        if(!Files.isRegularFile(supplied.stringsConf()))
        {
            required.add(STRINGS);
        }

        if(required.isEmpty())
        {
            return "not needed (the engine is already supplied from "
                + supplied.scriptsDir().toAbsolutePath() + ")";
        }

        Path stamp = root().resolve(STAMP);
        Properties installed = readFile(stamp);
        Set<String> have = pieces(installed);
        if(version.equals(installed.getProperty(STAMP_VERSION)) && have.containsAll(required)
            && present(have))
        {
            return "already unpacked (" + version + ", " + String.join(", ", have) + ")";
        }

        // Anything already unpacked stays unpacked: dropping a piece because
        // this run does not need it would throw work away that the next run --
        // one EDOPro uninstall later -- would immediately have to redo.
        Set<String> wanted = new LinkedHashSet<>(have);
        wanted.addAll(required);

        // Whether the script tree on disk is one THIS build wrote and finished
        // writing. Only an intact stamp naming this exact version can say so,
        // and it has to be read before the stamp is deleted below.
        //
        // This is the whole of the decision, because the alternative was to
        // decide it per file by comparing sizes -- and a script whose new
        // revision happens to be the same length as the old one would then
        // never be rewritten. That is not hypothetical: deleting .bundle is
        // what README.md tells a player to do to force a fresh unpack, and with
        // a per-file size check that repair rewrote nothing at all, because
        // every damaged file was still the size it was supposed to be. A stale
        // card script does not fail on load; it fails mid-duel as a wrong
        // ruling, which is the worst way for this to go wrong.
        boolean scriptsCurrent = version.equals(installed.getProperty(STAMP_VERSION))
            && have.contains(SCRIPTS)
            && Files.isRegularFile(scriptsDir().resolve("constant.lua"));

        long started = System.currentTimeMillis();
        LOG.info("Unpacking the bundled rules engine (" + String.join(", ", wanted)
            + ") into " + root().toAbsolutePath());

        // The stamp goes first, so a run interrupted from here on leaves a tree
        // that describes itself as unfinished rather than as up to date.
        Files.deleteIfExists(stamp);

        List<String> done = new ArrayList<>();
        int files = 0;
        long bytes = 0;

        if(wanted.contains(CDB))
        {
            bytes += copy(RESOURCES + "expansions/cards.cdb", cdb());
            files++;
            done.add(CDB);
        }
        if(wanted.contains(STRINGS))
        {
            bytes += copy(RESOURCES + "config/strings.conf", stringsConf());
            files++;
            done.add(STRINGS);
        }
        if(wanted.contains(LIBRARY))
        {
            long written = copy(RESOURCES + "native/" + com.sun.jna.Platform.RESOURCE_PREFIX
                + "/" + EngineRuntime.Paths.libraryName(), library());
            if(written < 0)
            {
                // Not an error, and not something to keep retrying: this build
                // simply carries no core for this platform. missing() names the
                // download page, which is the only answer there is.
                LOG.info("This build bundles no native core for "
                    + com.sun.jna.Platform.RESOURCE_PREFIX + "; EDOPro's own is needed here.");
            }
            else
            {
                bytes += written;
                files++;
                done.add(LIBRARY);
            }
        }
        if(wanted.contains(SCRIPTS))
        {
            // Skipped as a WHOLE or written as a whole. When the stamp vouches
            // for the tree there is nothing to do -- that is the case where one
            // other piece went stale and 28 MB of scripts should not be rewritten
            // to fix a 1.4 MB core -- and when it does not, every file is written.
            if(!scriptsCurrent)
            {
                long[] counted = unpackScripts();
                files += (int) counted[0];
                bytes += counted[1];
            }
            done.add(SCRIPTS);
        }

        // The licence text travels with the code it covers, always, whether or
        // not any of the code above was needed this time. AGPL-3.0 section 4:
        // "give all recipients a copy of this License along with the Program".
        copy(RESOURCES + "COPYING.ocgcore.txt", root().resolve("COPYING.ocgcore.txt"));
        copy(RESOURCES + "COPYING.AGPL-3.0.txt", root().resolve("COPYING.AGPL-3.0.txt"));
        copy(RESOURCES + "README.md", root().resolve("README.md"));

        Properties written = new Properties();
        written.setProperty(STAMP_VERSION, version);
        written.setProperty(STAMP_PIECES, String.join(",", done));
        try(java.io.Writer out = Files.newBufferedWriter(stamp, StandardCharsets.UTF_8))
        {
            written.store(out, "Written by Duel Dimension. Delete this file to force a re-unpack.");
        }

        long took = System.currentTimeMillis() - started;
        // done is what the STAMP records, which is every piece now present --
        // not every piece written. Saying "unpacked scripts -- 3 files" is how
        // a scripts step that wrote nothing at all read as if it had worked, so
        // the one piece that can be present without being written says so.
        String summary = "unpacked " + String.join(", ", done)
            + (scriptsCurrent && wanted.contains(SCRIPTS) ? " (scripts already current)" : "")
            + " -- " + files + " files, " + bytes + " bytes, " + took + " ms";
        LOG.info(summary);
        return summary;
    }

    /** Whether every piece the stamp claims is actually still on disk. */
    private static boolean present(Set<String> have)
    {
        if(have.contains(SCRIPTS) && !Files.isRegularFile(scriptsDir().resolve("constant.lua")))
        {
            return false;
        }
        if(have.contains(CDB) && !Files.isRegularFile(cdb()))
        {
            return false;
        }
        if(have.contains(STRINGS) && !Files.isRegularFile(stringsConf()))
        {
            return false;
        }
        return !have.contains(LIBRARY) || Files.isRegularFile(library());
    }

    /**
     * A core that exists AND that this JVM could load.
     * <p>
     * Existing is not enough, and this is not a hypothetical: EDOPro's default
     * Windows build is 32-bit while Minecraft 26.2 needs a 64-bit Java 25, so
     * the install a player already has can supply everything except the one file
     * that matters. That machine needs the bundled core and nothing else.
     */
    private static boolean usable(Path core)
    {
        // Both halves matter, and the file check has to come first.
        // NativeArchitecture.loadableHere answers "is there a mismatch I can
        // prove", so a core that is not there at all comes back loadable --
        // there is no header to disagree with. Asking it on its own made a fresh
        // installation decide it needed nothing and unpack nothing, while
        // missing() went on reporting a core it did not have.
        return Files.isRegularFile(core)
            && de.cas_ual_ty.dueldimension.ocg.NativeArchitecture.loadableHere(core);
    }

    private static Set<String> pieces(Properties stamp)
    {
        Set<String> pieces = new LinkedHashSet<>();
        for(String piece : stamp.getProperty(STAMP_PIECES, "").split(","))
        {
            if(!piece.isBlank())
            {
                pieces.add(piece.trim());
            }
        }
        return pieces;
    }

    /** @return bytes written, or -1 when this build does not carry that resource */
    private static long copy(String resource, Path target) throws IOException
    {
        try(InputStream in = EngineBundle.class.getResourceAsStream(resource))
        {
            if(in == null)
            {
                return -1;
            }
            Files.createDirectories(target.getParent());
            return Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Streams the card scripts out of the zip inside the jar.
     * <p>
     * Entry names are checked against the destination even though this archive
     * is one this build produced: a {@code ../} in a zip is written exactly
     * where it says unless someone looks, the same hole
     * {@link de.cas_ual_ty.dueldimension.DdDatabase} closed for the downloaded
     * database, and a rule that only holds while nobody makes a mistake is not
     * a rule.
     * <p>
     * <b>Every entry is written, unconditionally.</b> Whether the tree needs
     * writing at all is decided once, by the stamp, before this is called; once
     * it is called there is nothing left for a per-file check to be right about.
     * Size was tried as that check and is not sound -- see the comment on
     * {@code scriptsCurrent}.
     *
     * @return {files written, bytes written}
     */
    private static long[] unpackScripts() throws IOException
    {
        Path root = scriptsDir().toAbsolutePath().normalize();
        Files.createDirectories(root);

        long files = 0;
        long bytes = 0;
        byte[] buffer = new byte[1 << 16];

        try(InputStream in = EngineBundle.class.getResourceAsStream(SCRIPTS_ZIP))
        {
            if(in == null)
            {
                throw new IOException("no " + SCRIPTS_ZIP + " in this build");
            }
            try(ZipInputStream zip = new ZipInputStream(new java.io.BufferedInputStream(in)))
            {
                for(ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry())
                {
                    Path target = root.resolve(entry.getName()).normalize();
                    if(!target.startsWith(root))
                    {
                        throw new IOException("bundled script escapes " + root + ": "
                            + entry.getName());
                    }
                    if(entry.isDirectory())
                    {
                        Files.createDirectories(target);
                        continue;
                    }
                    Files.createDirectories(target.getParent());
                    try(java.io.OutputStream out = new java.io.BufferedOutputStream(
                        Files.newOutputStream(target)))
                    {
                        for(int read = zip.read(buffer); read > 0; read = zip.read(buffer))
                        {
                            out.write(buffer, 0, read);
                            bytes += read;
                        }
                    }
                    files++;
                }
            }
        }
        return new long[] {files, bytes};
    }

    private static Properties read(String resource)
    {
        try(InputStream in = EngineBundle.class.getResourceAsStream(resource))
        {
            if(in == null)
            {
                return null;
            }
            Properties properties = new Properties();
            properties.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
            return properties;
        }
        catch(IOException unreadable)
        {
            LOG.warn("Could not read " + resource + " from the mod jar: " + unreadable);
            return null;
        }
    }

    private static Properties readFile(Path file)
    {
        Properties properties = new Properties();
        if(!Files.isRegularFile(file))
        {
            return properties;
        }
        try(java.io.Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8))
        {
            properties.load(in);
        }
        catch(IOException | IllegalArgumentException unreadable)
        {
            // An unreadable stamp means "unpack again", which is always safe.
            LOG.info("Could not read " + file + "; unpacking the engine again.");
        }
        return properties;
    }
}
