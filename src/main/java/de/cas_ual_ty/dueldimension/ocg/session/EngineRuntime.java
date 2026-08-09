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
        public static Paths defaults()
        {
            return new Paths(
                resolve("ocg.lib", "native/ocgcore.dll"),
                resolve("ocg.scripts", "C:/ProjectIgnis/script"),
                resolve("ocg.cdb", "C:/ProjectIgnis/expansions/cards.cdb"),
                resolve("ocg.strings", "C:/ProjectIgnis/config/strings.conf"));
        }

        /**
         * Resolves an engine path, allowing a system-property override.
         * Relative defaults are searched both from the working directory and
         * from its parent, because a dev server runs inside {@code run/} while
         * the built artefacts sit in the project root.
         */
        private static Path resolve(String property, String fallback)
        {
            Path configured = Path.of(System.getProperty(property, fallback));
            if(configured.isAbsolute() || Files.exists(configured))
            {
                return configured;
            }
            Path fromParent = Path.of("..").resolve(configured).normalize();
            return Files.exists(fromParent) ? fromParent : configured;
        }

        /** @return null when everything needed is present, else what is missing */
        public String missing()
        {
            if(!Files.isRegularFile(library))
            {
                return "native core not found at " + library.toAbsolutePath();
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

    /** Loads the engine if it isn't loaded yet. Returns null when unavailable. */
    public static synchronized EngineRuntime get(Paths paths)
    {
        if(instance != null)
        {
            return instance;
        }
        if(paths.missing() != null)
        {
            return null;
        }
        try
        {
            OcgApi api = OcgApi.load(paths.library());
            CdbCardProvider cards = new CdbCardProvider(List.of(paths.cdb()));
            OcgDuel.ScriptProvider scripts = HeadlessDuelRunner.cardScriptsDirectory(paths.scriptsDir());
            DescriptionTable descriptions = new DescriptionTable(paths.stringsConf(), List.of(paths.cdb()));
            instance = new EngineRuntime(api, cards, scripts, descriptions);
            return instance;
        }
        catch(Exception e)
        {
            throw new IllegalStateException("Engine failed to load: " + e, e);
        }
    }

    public static synchronized boolean isLoaded()
    {
        return instance != null;
    }

    /** Human-readable readiness, for commands and logs. */
    public static String status(Paths paths)
    {
        String missing = paths.missing();
        if(missing != null)
        {
            return "unavailable (" + missing + ")";
        }
        return isLoaded() ? "loaded" : "ready (not loaded yet)";
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
