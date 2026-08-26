package de.cas_ual_ty.dueldimension.ocg.session;

import de.cas_ual_ty.dueldimension.ocg.CdbCardProvider;
import de.cas_ual_ty.dueldimension.ocg.HeadlessDuelRunner;
import de.cas_ual_ty.dueldimension.ocg.OcgApi;
import de.cas_ual_ty.dueldimension.ocg.OcgConstants;
import de.cas_ual_ty.dueldimension.ocg.OcgDuel;
import de.cas_ual_ty.dueldimension.ocg.text.DescriptionTable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Holds the engine's process-wide pieces: the loaded native library, the card
 * database, the script directory and the text tables. Loading these costs real
 * time and memory, so it happens once, lazily, the first time a duel is
 * actually requested.
 * <p>
 * Everything is optional at runtime: a server without the native library still
 * runs the mod perfectly well, it just cannot host ruled duels. {@link #status}
 * explains what is missing so that surfaces as a clear message rather than a
 * crash.
 */
public final class EngineRuntime
{
    private static EngineRuntime instance;

    private final OcgApi api;
    private final CdbCardProvider cards;
    private final OcgDuel.ScriptProvider scripts;
    private final DescriptionTable descriptions;

    private EngineRuntime(OcgApi api, CdbCardProvider cards, OcgDuel.ScriptProvider scripts,
        DescriptionTable descriptions)
    {
        this.api = api;
        this.cards = cards;
        this.scripts = scripts;
        this.descriptions = descriptions;
    }

    /** Where the engine's data lives; defaults suit a local EDOPro install. */
    public record Paths(Path library, Path scriptsDir, Path cdb, Path stringsConf)
    {
        /**
         * Where a current EDOPro keeps everything it has learned since its
         * installer was cut.
         * <p>
         * EDOPro does NOT merge its updates back into {@code expansions/cards.cdb}
         * -- that file stays at whatever the installer shipped, and every card
         * printed since arrives as a delta in this repository instead, with the
         * matching scripts beside it. Reading only the base is reading a
         * snapshot that stopped moving the day the install was made, which is
         * how a card the player owns became a card the engine had never heard
         * of, and an Xyz monster ended up on top of the main deck.
         */
        private static Path deltaRepository()
        {
            Path root = edoproRoot();
            if(root == null)
            {
                return null;
            }
            Path repository = root.resolve("repositories").resolve("delta-bagooska");
            return Files.isDirectory(repository) ? repository : null;
        }

        /**
         * The card databases to read, in the order they layer: the base first,
         * then the delta over it. {@link de.cas_ual_ty.dueldimension.ocg.CdbCardProvider}
         * loads a list in order and later rows win, which is the same rule
         * EDOPro's own expansions mechanism uses.
         */
        public List<Path> cdbChain()
        {
            java.util.List<Path> chain = new java.util.ArrayList<>();
            chain.add(cdb);
            Path repository = deltaRepository();
            if(repository != null)
            {
                Path delta = repository.resolve("cards.delta.cdb");
                if(Files.isRegularFile(delta))
                {
                    chain.add(delta);
                }
            }
            // Custom cards LAST, so they win. CdbCardProvider merges the list in
            // order and later rows replace earlier ones, which is EDOPro's own
            // expansions rule -- and it means a custom entry can also correct a
            // stock card without editing anybody else's database.
            for(Path custom : customCdbs())
            {
                chain.add(custom);
            }
            return List.copyOf(chain);
        }

        /**
         * Where custom cards live: {@code dueldimension_custom/} in the game
         * directory, holding any number of {@code .cdb} files and a
         * {@code script/} folder beside them.
         * <p>
         * Deliberately OUTSIDE both the EDOPro install and {@code ydm_db}. The
         * first is not ours to write into and updates itself; the second is
         * deleted recursively whenever the card database is refreshed, which
         * would take every custom card with it.
         */
        public static Path customRoot()
        {
            return de.cas_ual_ty.dueldimension.util.GameDir.file("dueldimension_custom").toPath();
        }

        /**
         * The passcode block reserved for this mod's custom cards.
         * <p>
         * Konami's printed cards are eight digits and stop well below this;
         * EDOPro's own unofficial sets sit in their own ranges. Nine-digit
         * codes from 900,000,000 are clear of both, so a custom card can never
         * collide with a real one -- and a colliding id would not fail loudly,
         * it would silently replace a real card in the chain.
         */
        public static final int CUSTOM_PASSCODE_FIRST = 900_000_000;
        public static final int CUSTOM_PASSCODE_LAST = 999_999_999;

        /** Whether this id belongs to the custom block. */
        public static boolean isCustomPasscode(int code)
        {
            return code >= CUSTOM_PASSCODE_FIRST && code <= CUSTOM_PASSCODE_LAST;
        }

        /** Every custom database, sorted so the order is the same on every machine. */
        public static List<Path> customCdbs()
        {
            Path root = customRoot();
            if(!Files.isDirectory(root))
            {
                return List.of();
            }
            try(java.util.stream.Stream<Path> files = Files.list(root))
            {
                return files.filter(p -> p.getFileName().toString().endsWith(".cdb"))
                    .filter(Files::isRegularFile)
                    .sorted()
                    .toList();
            }
            catch(java.io.IOException unreadable)
            {
                // An unreadable custom folder is not worth refusing to duel
                // over; the cards in it simply do not exist this session.
                de.cas_ual_ty.dueldimension.DuelDimension.warn(
                    "Could not list " + root + ": " + unreadable.getMessage());
                return List.of();
            }
        }

        /**
         * The script roots to search, newest first: a card reprinted with an
         * errata has its corrected script in the delta, and the stale copy in
         * the base install must not shadow it.
         */
        public List<Path> scriptRoots()
        {
            java.util.List<Path> roots = new java.util.ArrayList<>();
            // Custom scripts FIRST: the roots are searched in order and the
            // first hit wins, so this is the same "custom overrides stock"
            // precedence the database chain gives, expressed the other way up.
            Path customScripts = customRoot().resolve("script");
            if(Files.isDirectory(customScripts))
            {
                roots.add(customScripts);
            }
            Path repository = deltaRepository();
            if(repository != null)
            {
                Path scripts = repository.resolve("script");
                if(Files.isDirectory(scripts))
                {
                    roots.add(scripts);
                }
            }
            roots.add(scriptsDir);
            return List.copyOf(roots);
        }

        /**
         * Where the engine's pieces are, in order of preference.
         * <p>
         * <b>An explicit override, then a real EDOPro, then the copy inside this
         * jar.</b> That order is deliberate and it is not the order a "make it
         * self-contained" change naturally produces. A player who has EDOPro has
         * something better than the bundle: an install that updates itself
         * several times a week, whose scripts and card database were cut from
         * the same commit as each other. The bundle is a snapshot, and a
         * snapshot is stale the day it is taken -- it is what makes a clean
         * machine work, not what a stocked machine should be dragged onto.
         */
        public static Paths defaults()
        {
            // Anything the bundle is going to unpack must be on disk before the
            // paths below are tested for existence. Waits only when an unpack is
            // actually in flight, which is a first run and nothing else.
            EngineBundle.awaitInstall();
            return resolveAll(true);
        }

        /**
         * The same answer the mod would give if it bundled nothing at all.
         * <p>
         * This is the question {@link EngineBundle#install()} asks before it
         * writes anything: every piece answered for here is a piece it must
         * leave alone. Package-private and bundle-blind on purpose -- calling
         * {@link #defaults()} from inside the unpack would wait on the thread
         * doing the unpacking.
         */
        static Paths withoutBundle()
        {
            return resolveAll(false);
        }

        private static Paths resolveAll(boolean withBundle)
        {
            // Found, not hardcoded. Every path used to point at C:/ProjectIgnis,
            // which is one person's install on one machine -- the mod could not
            // run anywhere else without system properties nobody would think to
            // set.
            Path root = edoproRoot();
            return new Paths(
                // EDOPro's OWN core, in preference to any copy shipped beside
                // the mod: it is the one guaranteed to match the scripts and
                // the database sitting next to it.
                //
                // Only if it can actually be loaded, though. That preference
                // was written believing EDOPro's copy is "whatever architecture
                // that install is", which is true and is exactly the problem --
                // EDOPro's default Windows build is 32-bit and Minecraft 26.2
                // requires a 64-bit Java 25, so on the machine this was
                // developed on the preferred core was the one that could not be
                // loaded at all. A core whose header disagrees with this JVM is
                // skipped here, and named in missing() so a player is told
                // which one they have.
                resolve("ocg.lib", loadableCore(root),
                    withBundle ? EngineBundle.library() : null, "native/" + libraryName()),
                // The one entry that is not resolved on existence alone. A
                // directory with the shared .lua files and no card scripts
                // EXISTS, wins the preference, and then fails every card in the
                // duel -- so this asks whether EDOPro's copy can actually answer
                // for a card, and falls through to the bundle's complete tree
                // when it cannot. See HeadlessDuelRunner.hasCardScripts.
                resolve("ocg.scripts", cardScripts(root),
                    withBundle ? EngineBundle.scriptsDir() : null, "script"),
                resolve("ocg.cdb", root == null ? null : root.resolve("expansions/cards.cdb"),
                    withBundle ? EngineBundle.cdb() : null, "expansions/cards.cdb"),
                resolve("ocg.strings", root == null ? null : root.resolve("config/strings.conf"),
                    withBundle ? EngineBundle.stringsConf() : null, "config/strings.conf"));
        }

        /**
         * EDOPro's card scripts, if that install actually has some.
         * <p>
         * Null when it does not, which is what lets {@link #resolve} fall
         * through to the bundle -- the same shape as {@link #loadableCore}, and
         * for the same kind of reason: a piece that is present but cannot do its
         * job is worse than one that is absent, because it wins the preference
         * and then fails at the point of use.
         */
        private static Path cardScripts(Path root)
        {
            if(root == null)
            {
                return null;
            }
            Path scripts = root.resolve("script");
            return de.cas_ual_ty.dueldimension.ocg.HeadlessDuelRunner.hasCardScripts(scripts)
                ? scripts : null;
        }

        /**
         * EDOPro's own core, if it is one this JVM could load.
         * <p>
         * Returning null when it is not lets {@link #resolve} fall through to
         * the copy beside the mod, which is the only other candidate there is.
         */
        private static Path loadableCore(Path root)
        {
            if(root == null)
            {
                return null;
            }
            Path core = root.resolve(libraryName());
            return de.cas_ual_ty.dueldimension.ocg.NativeArchitecture.loadableHere(core)
                ? core : null;
        }

        /**
         * What the native core is called on this platform.
         * <p>
         * Package-private rather than private because {@link EngineBundle} lays
         * the unpacked core out under the same name, and two places deciding
         * separately what a shared library is called is one place too many.
         */
        static String libraryName()
        {
            String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
            if(os.contains("win"))
            {
                return "ocgcore.dll";
            }
            return os.contains("mac") ? "libocgcore.dylib" : "libocgcore.so";
        }

        /**
         * Where EDOPro is installed, or null if it cannot be found.
         * <p>
         * Checked in order of how certain each answer is: an explicit override
         * first, then beside the game (which is what a packaged install would
         * do), then the places the installer actually puts it on each platform.
         * A directory only counts if it holds the card scripts, so a leftover
         * empty folder does not win over a real install further down the list.
         */
        public static Path edoproRoot()
        {
            java.util.List<Path> candidates = new java.util.ArrayList<>();
            String override = System.getProperty("ocg.edopro", System.getenv("EDOPRO_HOME"));
            if(override != null && !override.isBlank())
            {
                candidates.add(Path.of(override));
            }
            // Beside the game, so a server or modpack can ship it alongside.
            // The game directory, specifically -- this used to be a bare
            // relative path, which is the process working directory and is only
            // the game directory by coincidence. A modpack that ships EDOPro
            // next to the instance was found only if the launcher happened to
            // start the JVM there.
            Path game = de.cas_ual_ty.dueldimension.util.GameDir.path();
            candidates.add(game.resolve("ProjectIgnis"));
            candidates.add(game.resolve("..").resolve("ProjectIgnis").normalize());

            String home = System.getProperty("user.home", "");
            String appData = System.getenv("APPDATA");
            String localAppData = System.getenv("LOCALAPPDATA");
            String programFiles = System.getenv("ProgramFiles");
            for(String base : new String[] {appData, localAppData, programFiles, home})
            {
                if(base != null && !base.isBlank())
                {
                    candidates.add(Path.of(base, "ProjectIgnis"));
                }
            }
            if(!home.isBlank())
            {
                candidates.add(Path.of(home, ".local", "share", "ProjectIgnis"));
                candidates.add(Path.of(home, "Library", "Application Support", "ProjectIgnis"));
            }
            // The historical default, kept last so an existing install still
            // works without anyone changing anything.
            candidates.add(Path.of("C:/ProjectIgnis"));

            for(Path candidate : candidates)
            {
                // The scripts are the thing that proves this is a real install:
                // constant.lua is what every card script loads.
                if(Files.isRegularFile(candidate.resolve("script/constant.lua")))
                {
                    return candidate;
                }
            }
            return null;
        }

        /**
         * An override, else a discovered EDOPro, else the bundle, else a
         * relative fallback.
         * <p>
         * The bundle sits third and not first for the reason
         * {@link #defaults()} gives: it is a snapshot, and the install a player
         * maintains themselves is newer than it. It sits ahead of the relative
         * fallback because that fallback only means anything in a dev checkout.
         *
         * @param bundled the unpacked bundle's path, or null to ignore the bundle
         */
        private static Path resolve(String property, Path discovered, Path bundled,
            String fallback)
        {
            String configured = System.getProperty(property);
            if(configured != null && !configured.isBlank())
            {
                return Path.of(configured);
            }
            if(discovered != null && Files.exists(discovered))
            {
                return discovered;
            }
            if(bundled != null && Files.exists(bundled))
            {
                return bundled;
            }
            return resolve(property, fallback);
        }

        /**
         * Resolves an engine path, allowing a system-property override.
         * <p>
         * Relative defaults are searched from the working directory, from the
         * game directory and from each of their parents, because a dev server
         * runs inside {@code run/} while the built artefacts sit in the project
         * root -- and because for anyone who is not in a dev checkout, "beside
         * the game" is the only one of those that means anything.
         */
        private static Path resolve(String property, String fallback)
        {
            Path configured = Path.of(System.getProperty(property, fallback));
            if(configured.isAbsolute() || Files.exists(configured))
            {
                return configured;
            }
            Path game = de.cas_ual_ty.dueldimension.util.GameDir.path();
            for(Path base : new Path[] {Path.of(".."), game, game.resolve("..")})
            {
                Path candidate = base.resolve(configured).normalize();
                if(Files.exists(candidate))
                {
                    return candidate;
                }
            }
            return configured;
        }

        /** @return null when everything needed is present, else what is missing */
        public String missing()
        {
            if(!Files.isRegularFile(library))
            {
                // Say WHY, not just where. When an EDOPro install is present but
                // its core is the wrong architecture, defaults() skipped it and
                // fell through to a fallback that is not there either -- and
                // "native core not found at .../native/ocgcore.dll" would send a
                // player looking for a file that was never the problem.
                if(System.getProperty("ocg.lib") == null)
                {
                    Path root = edoproRoot();
                    String wrongArchitecture = root == null ? null
                        : de.cas_ual_ty.dueldimension.ocg.NativeArchitecture
                            .mismatch(root.resolve(libraryName()));
                    if(wrongArchitecture != null)
                    {
                        return wrongArchitecture;
                    }
                }
                // Which platform, not just which path. This build carries a
                // core for the platforms it was built with cores for, and a
                // player on any other one is not missing a file they could go
                // and find -- they need EDOPro's, and saying so is the only
                // useful thing to tell them.
                if(EngineBundle.isBundled() && !EngineBundle.hasNativeHere())
                {
                    return "this build bundles no rules engine for "
                        + com.sun.jna.Platform.RESOURCE_PREFIX + ", and none was found at "
                        + library.toAbsolutePath();
                }
                return "native core not found at " + library.toAbsolutePath();
            }
            // Existing is not the same as usable. Without this the only report
            // of a 32-bit core under a 64-bit game was an UnsatisfiedLinkError
            // thrown off the server thread, which is an Error and so was not
            // caught by anything on the way up.
            String wrongArchitecture =
                de.cas_ual_ty.dueldimension.ocg.NativeArchitecture.mismatch(library);
            if(wrongArchitecture != null)
            {
                return wrongArchitecture;
            }
            if(!Files.isRegularFile(scriptsDir.resolve("constant.lua")))
            {
                return "card scripts not found at " + scriptsDir.toAbsolutePath();
            }
            if(!Files.isRegularFile(cdb))
            {
                return "card database not found at " + cdb.toAbsolutePath();
            }
            return null;
        }
    }

    /**
     * Where EDOPro is downloaded from.
     * <p>
     * ProjectIgnis's own page. This mod does not reimplement the rules -- it
     * embeds ocgcore and plays EDOPro's card scripts -- so without that install
     * there is no duel to be had, and telling a player only that something is
     * "missing" leaves them nothing to act on.
     */
    public static final String DOWNLOAD_URL = "https://projectignis.github.io/download.html";

    /**
     * What to tell a player when the engine is not installed.
     * <p>
     * One place, so the join notice and every refused duel say the same thing.
     * The link is a real click event rather than pasted text: a URL a player has
     * to retype by hand is a URL they do not visit.
     * <p>
     * <b>Whose machine is missing it matters.</b> Every check that produces this
     * message runs on the SERVER -- the engine is server-side, and the join
     * notice reads the server's own filesystem. Telling a player on a dedicated
     * server to install EDOPro was telling them to fix something on their own
     * computer that could not possibly help, so a hosted game says who actually
     * has to act. The download link is still offered either way, because the
     * person reading it may well be the one who runs the server.
     *
     * @param hostedElsewhere true when the game the player is on is not their own
     */
    public static net.minecraft.network.chat.Component requirementMessage(String missing,
        boolean hostedElsewhere)
    {
        net.minecraft.network.chat.MutableComponent link =
            net.minecraft.network.chat.Component.literal("Download EDOPro")
                .withStyle(style -> style
                    .withColor(net.minecraft.ChatFormatting.AQUA)
                    .withUnderlined(true)
                    .withClickEvent(new net.minecraft.network.chat.ClickEvent.OpenUrl(
                        java.net.URI.create(DOWNLOAD_URL)))
                    .withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(
                        net.minecraft.network.chat.Component.literal(DOWNLOAD_URL))));

        String lead = hostedElsewhere
            ? "This server has no rules engine, so no duel can be played on it."
                + " Whoever runs it needs to install EDOPro. "
            : "Duel Dimension needs EDOPro installed to play duels. ";

        return net.minecraft.network.chat.Component.literal(lead)
            .withStyle(net.minecraft.ChatFormatting.RED)
            .append(link)
            .append(net.minecraft.network.chat.Component.literal("  (missing: " + missing + ")")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    /**
     * Whether this player is a guest on someone else's game.
     * <p>
     * Singleplayer runs an integrated server on the player's own machine, so
     * "the server is missing it" and "you are missing it" are the same sentence
     * there; on a dedicated server they are not.
     */
    public static boolean hostedElsewhere(net.minecraft.server.level.ServerPlayer player)
    {
        // Through the level: ServerPlayer keeps its server in a private field on
        // 26.2 and Entity has no accessor, but a level on the server side has
        // one and a ServerPlayer is only ever in one of those.
        net.minecraft.server.MinecraftServer server = player.level().getServer();
        return server != null && !server.isSingleplayer();
    }

    /**
     * Why the last load attempt failed, or null. Read by {@link #status} and by
     * the callers that have a player to tell.
     */
    private static volatile String loadError;

    /** Loads the engine if it isn't loaded yet. Returns null when unavailable. */
    public static synchronized EngineRuntime get(Paths paths)
    {
        if(instance != null)
        {
            return instance;
        }
        String missing = paths.missing();
        if(missing != null)
        {
            loadError = missing;
            return null;
        }
        try
        {
            OcgApi api = OcgApi.load(paths.library());
            // The base plus whatever EDOPro has learned since, in that order:
            // reading the base alone is reading the day the install was made.
            CdbCardProvider cards = new CdbCardProvider(paths.cdbChain());
            OcgDuel.ScriptProvider scripts =
                HeadlessDuelRunner.cardScriptsDirectories(paths.scriptRoots());
            DescriptionTable descriptions =
                new DescriptionTable(paths.stringsConf(), paths.cdbChain());
            instance = new EngineRuntime(api, cards, scripts, descriptions);
            loadError = null;
            return instance;
        }
        catch(Throwable e)
        {
            // Throwable, not Exception. Loading a native library fails with
            // UnsatisfiedLinkError -- an Error -- which sailed straight through
            // the old catch, out of this method, out of the duel request that
            // called it and onto the server thread, where a player who
            // right-clicked a duelist got a crash instead of a sentence telling
            // them what to install. A duel that cannot start is not a reason to
            // stop the server.
            loadError = e.toString();
            de.cas_ual_ty.dueldimension.DuelDimension.warn("Rules engine failed to load from "
                + paths.library().toAbsolutePath() + ": " + e);
            return null;
        }
    }

    public static synchronized boolean isLoaded()
    {
        return instance != null;
    }

    /** Why duels are unavailable, or null when they are not. */
    public static String unavailable(Paths paths)
    {
        String missing = paths.missing();
        if(missing != null)
        {
            return missing;
        }
        return isLoaded() ? null : loadError;
    }

    /** Human-readable readiness, for commands and logs. */
    public static String status(Paths paths)
    {
        String missing = paths.missing();
        if(missing != null)
        {
            return "unavailable (" + missing + ")";
        }
        if(isLoaded())
        {
            return "loaded";
        }
        return loadError == null ? "ready (not loaded yet)"
            : "unavailable (" + loadError + ")";
    }

    public OcgApi api()
    {
        return api;
    }

    public CdbCardProvider cards()
    {
        return cards;
    }

    public OcgDuel.ScriptProvider scripts()
    {
        return scripts;
    }

    public DescriptionTable descriptions()
    {
        return descriptions;
    }

    public long defaultFlags()
    {
        // MR5 plus the first-turn draw. Modern rules have the player going
        // first skip their draw, opening on five cards while the second player
        // reaches six; this hands that draw back, so whoever goes first begins
        // their first turn holding six. DUEL_1ST_TURN_DRAW is ocgcore's own
        // option -- MR1, MR2 and Rush all carry it -- so the engine applies it
        // rather than anything here counting cards.
        return OcgConstants.DUEL_MODE_MR5 | OcgConstants.DUEL_1ST_TURN_DRAW;
    }
}
