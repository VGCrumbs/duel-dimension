package de.cas_ual_ty.dueldimension.duel.match;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The banlists actually load from the reference client's files.
 * <p>
 * {@link Banlist#parse} was written long before anything called it, so it was
 * correct and inert: no list was ever read, and the only list in the game was
 * "no list". These check the loading, which is the part that was missing.
 */
class BanlistsTest
{
    private static Path lists()
    {
        return Path.of(System.getProperty("ocg.lflists",
            "C:/ProjectIgnis/repositories/lflists"));
    }

    @Test
    void noBanlistIsAlwaysOfferedAndAlwaysFirst()
    {
        List<Banlist> all = Banlists.all();
        assertFalse(all.isEmpty(), "a chooser with nothing in it is worse than one with one entry");
        // True even with no reference install: it is the one list that needs no
        // files, so it is what an otherwise empty chooser falls back to.
        assertEquals(Banlist.NO_BANLIST_ID, all.get(0).id());
    }

    @Test
    void anUnknownIdFallsBackRatherThanFailing()
    {
        // A match configured against a list that has since gone away must still
        // be playable; refusing the duel over a missing file would be worse.
        Banlist missing = Banlists.byId("no-such-list-2099");
        assertNotNull(missing);
        assertEquals(Banlist.NO_BANLIST_ID, missing.id());
    }

    @Test
    void theReferenceListsAreRead()
    {
        assumeTrue(Files.isDirectory(lists()), "no EDOPro lflists directory present");

        List<Banlist> all = Banlists.all();
        assertTrue(all.size() > 1,
            "expected the reference lists as well as \"no banlist\", got " + all.size());

        // Every list keeps a usable identity, since that is what a match config
        // stores and what a chooser shows.
        for(Banlist list : all)
        {
            assertFalse(list.id().isBlank(), "a list with no id cannot be chosen");
            assertFalse(list.displayName().isBlank(), "a list with no name cannot be shown");
            assertEquals(list, Banlists.byId(list.id()), "a list must be findable by its own id");
        }
    }

    @Test
    void aRealListActuallyLimitsSomething()
    {
        assumeTrue(Files.isDirectory(lists()), "no EDOPro lflists directory present");
        List<Banlist> all = Banlists.all();
        assumeTrue(all.size() > 1, "no reference lists loaded");

        // The point of loading them: at least one list has to forbid or limit a
        // card, or the parse succeeded and read nothing.
        boolean anyLimit = all.stream().skip(1).anyMatch(list ->
        {
            for(int passcode : new int[] {21044178, 62320425, 4280258})
            {
                if(list.limitFor(passcode) < Banlist.UNLIMITED)
                {
                    return true;
                }
            }
            return false;
        });
        assertTrue(anyLimit, "no loaded list limited any card: the files parsed to nothing");
    }
}
