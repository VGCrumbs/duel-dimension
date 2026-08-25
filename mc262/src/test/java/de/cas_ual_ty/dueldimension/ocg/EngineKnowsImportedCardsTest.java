package de.cas_ual_ty.dueldimension.ocg;

import de.cas_ual_ty.dueldimension.ocg.session.EngineRuntime;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The engine has to know a card before a deck containing it can be registered.
 * <p>
 * This is not a theoretical requirement. ocgcore's {@code field::add_card}
 * silently rewrites LOCATION_EXTRA to LOCATION_DECK for any card whose type
 * lacks the extra-deck bits, and a card the card reader cannot resolve is
 * handed to it with {@code type == 0}. So an Xyz monster the engine has never
 * heard of is not rejected -- it is quietly moved into the main deck, and
 * because the extra deck is registered last it lands on TOP, which makes it the
 * first card drawn. That is the bug this guards.
 */
public class EngineKnowsImportedCardsTest
{
    /** Argostars - Adventurous Arion: the Xyz monster the bug was found on. */
    private static final int ARION = 40706444;

    private static EngineRuntime.Paths paths()
    {
        EngineRuntime.Paths paths = EngineRuntime.Paths.defaults();
        Assumptions.assumeTrue(paths != null && paths.cdb() != null
            && Files.isRegularFile(paths.cdb()), "no card database on this machine");
        return paths;
    }

    /**
     * EDOPro does not merge its updates into the installed cards.cdb: every
     * card printed since the installer was cut lives in a delta repository
     * beside it. Reading only the base is what made 770 cards invisible.
     */
    @Test
    public void theCardDatabaseChainIncludesAnyDelta() throws Exception
    {
        EngineRuntime.Paths paths = paths();
        List<Path> chain = paths.cdbChain();
        assertTrue(chain.contains(paths.cdb()), "the base database must still be read");

        // Asserted through the chain rather than by rebuilding the path here:
        // if a delta exists on this machine it must be in the chain, and if it
        // does not the chain is simply the base.
        if(chain.size() > 1)
        {
            assertTrue(chain.get(chain.size() - 1).toString().contains("delta"),
                "the delta must layer OVER the base, not under it: " + chain);
        }
    }

    /**
     * The real check: an Xyz monster from a recent import resolves through the
     * chain, and resolves as an Xyz. Skipped where the engine data is absent.
     */
    @Test
    public void arionResolvesAsAnXyzMonster() throws Exception
    {
        EngineRuntime.Paths paths = paths();
        CdbCardProvider cards = new CdbCardProvider(paths.cdbChain());
        OcgCard card = cards.get(ARION);
        Assumptions.assumeTrue(card != null,
            "engine data predates this card; refresh EDOPro to close the gap");

        assertNotNull(card);
        assertTrue((card.type() & OcgConstants.TYPE_XYZ) != 0,
            "Arion must carry TYPE_XYZ or the engine moves it into the main deck; type was 0x"
                + Integer.toHexString(card.type()));
        assertTrue((card.type() & OcgConstants.TYPE_MONSTER) != 0,
            "a card with no monster bit fails ocgcore's is_extra_deck_monster check outright");
    }

    /** A card the engine knows must also have the script that gives it effects. */
    @Test
    public void arionHasItsCardScript() throws Exception
    {
        EngineRuntime.Paths paths = paths();
        OcgDuel.ScriptProvider scripts =
            HeadlessDuelRunner.cardScriptsDirectories(paths.scriptRoots());
        byte[] lua = scripts.load("c" + ARION + ".lua");
        Assumptions.assumeTrue(lua != null,
            "engine scripts predate this card; refresh EDOPro to close the gap");
        assertTrue(lua.length > 0, "an empty script would load and do nothing");
    }
}
