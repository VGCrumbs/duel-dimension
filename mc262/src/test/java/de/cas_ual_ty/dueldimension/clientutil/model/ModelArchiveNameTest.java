package de.cas_ual_ty.dueldimension.clientutil.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the installer will and will not write out of the archive.
 * <p>
 * A zip entry is a name chosen by whoever built the zip, and an unpacker that
 * resolves entry names against a folder writes wherever the entry tells it to.
 * The rule here is the same one the loader already uses — if a name is not one
 * this mod would load, it is not a model and is skipped — which means the
 * traversal cases fall out of a check that had to exist anyway.
 * <p>
 * The real archive was measured before this was written: 683 flat entries, all
 * .glb, all already lower case with underscores. So the accepted cases below are
 * what is actually in it, not a guess at what might be.
 */
class ModelArchiveNameTest
{
    /** The stem the installer tests, i.e. the entry name minus ".glb". */
    private static boolean accepts(String entry)
    {
        return entry.toLowerCase().endsWith(".glb")
            && MonsterModels.plain(entry.substring(0, entry.length() - 4));
    }

    @Test
    void theArchivesOwnNamesAreAccepted()
    {
        assertTrue(accepts("curse_of_dragon.glb"));
        assertTrue(accepts("amazon_of_the_seas.glb"));
        assertTrue(accepts("30_000-year_white_turtle.glb"));
        assertTrue(accepts("7_colored_fish.glb"));
        assertTrue(accepts("b._skull_dragon.glb"));
    }

    @Test
    void nothingEscapesTheModelsFolder()
    {
        assertFalse(accepts("../evil.glb"));
        assertFalse(accepts("../../config/anything.glb"));
        assertFalse(accepts("sub/dragon.glb"));
        assertFalse(accepts("sub\\dragon.glb"));
        assertFalse(accepts("/etc/passwd.glb"));
        assertFalse(accepts("C:/Users/Public/evil.glb"));
    }

    @Test
    void onlyModelsAreWritten()
    {
        // The archive is models and nothing else, and an entry that is not one
        // has no business being unpacked into the folder the loader scans.
        assertFalse(accepts("readme.txt"));
        assertFalse(accepts("install.bat"));
        assertFalse(accepts("dragon.glb.exe"));
        assertFalse(accepts(".glb"));
    }
}
