package de.cas_ual_ty.dueldimension.ocg.session;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bundled card database, and the one promise it makes.
 * <p>
 * {@code ydm_db} is a folder players edit — their {@code alt_art} scans live in
 * it, so do hand-corrected card files, and none of it is anywhere else. The
 * unpack is therefore not allowed to correct, merge or refresh anything: it
 * writes a file only where no file exists. That is not a property you assert in
 * a comment and hope for, because the failure is silent, unrecoverable, and
 * happens on somebody else's machine.
 */
class BundledCardDatabaseTest
{
    @Test
    void thisBuildCarriesADatabase()
    {
        assertTrue(EngineBundle.hasCardDatabase(),
            "the build should have packed bundle/ydm_db into the jar");
        assertNotNull(EngineBundle.cardDatabaseVersion());
    }

    @Test
    void aCleanMachineGetsTheWholeDatabase(@TempDir Path into) throws IOException
    {
        String outcome = EngineBundle.installCardDatabase(into);

        assertTrue(outcome.startsWith("card database: wrote "), outcome);
        assertTrue(Files.isRegularFile(into.resolve("db.json")));
        // The grant travels with the data it covers, not just with the repo.
        assertTrue(Files.isRegularFile(into.resolve("UNLICENSE.txt")));
        assertTrue(Files.isRegularFile(into.resolve("BUNDLED.md")));
        assertTrue(count(into.resolve("cards")) > 13000,
            "the whole card set, not a sample");
        assertTrue(count(into.resolve("sets")) > 600);
        assertTrue(count(into.resolve("rarity_images")) > 0);

        // Nothing that is not cleared for redistribution rides along.
        assertFalse(Files.exists(into.resolve("alt_art")),
            "Yugipedia's scans are not ours to ship");
        assertFalse(Files.exists(into.resolve("_konami_probe")),
            "saved pages from Konami's own database are not ours to ship");
    }

    @Test
    void aFileThePlayerAlreadyHasIsNeverTouched(@TempDir Path into) throws IOException
    {
        // A card file edited by hand, and a passcode folder of their own
        // artwork. Both are things this machine has and no backup does.
        Path edited = into.resolve("cards").resolve("dark_magician.json");
        Files.createDirectories(edited.getParent());
        Files.writeString(edited, "{\"mine\":true}", StandardCharsets.UTF_8);
        Path scan = into.resolve("alt_art").resolve("46986414").resolve("0.png");
        Files.createDirectories(scan.getParent());
        Files.writeString(scan, "not really a png", StandardCharsets.UTF_8);

        String outcome = EngineBundle.installCardDatabase(into);

        assertEquals("{\"mine\":true}", Files.readString(edited, StandardCharsets.UTF_8),
            "the bundle overwrote an edited card file: " + outcome);
        assertEquals("not really a png", Files.readString(scan, StandardCharsets.UTF_8));
        assertTrue(outcome.contains("kept 1 already present"),
            "exactly the one occupied path should have been kept: " + outcome);
        // And the rest still arrived, so "do not overwrite" did not become
        // "do not install".
        assertTrue(count(into.resolve("cards")) > 13000);
    }

    @Test
    void theSecondLaunchDoesNoWork(@TempDir Path into)
    {
        EngineBundle.installCardDatabase(into);
        assertTrue(EngineBundle.installCardDatabase(into).startsWith("already installed ("),
            "the stamp should short-circuit the walk");
    }

    @Test
    void deletingTheStampRestoresWhatWentMissingAndNothingElse(@TempDir Path into)
        throws IOException
    {
        EngineBundle.installCardDatabase(into);

        Path lost = Files.list(into.resolve("cards")).findFirst().orElseThrow();
        Path kept = Files.list(into.resolve("sets")).findFirst().orElseThrow();
        Files.delete(lost);
        Files.writeString(kept, "edited after install", StandardCharsets.UTF_8);
        // The documented repair: delete .bundle to make the mod walk again.
        Files.delete(into.resolve(".bundle"));

        String outcome = EngineBundle.installCardDatabase(into);

        assertTrue(Files.isRegularFile(lost), "the missing file should be back: " + outcome);
        assertEquals("edited after install", Files.readString(kept, StandardCharsets.UTF_8),
            "a repair must not undo an edit: " + outcome);
        assertTrue(outcome.contains("wrote 1 files"), outcome);
    }

    private static long count(Path directory) throws IOException
    {
        try(Stream<Path> files = Files.list(directory))
        {
            return files.count();
        }
    }
}
