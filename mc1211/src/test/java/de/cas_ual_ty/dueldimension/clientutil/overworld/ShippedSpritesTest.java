package de.cas_ual_ty.dueldimension.clientutil.overworld;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The list the mod ships names sheets the mod ships.
 * <p>
 * A definition pointing at a file that is not there fails in the quietest way
 * this code has: no error, no warning, just a card that never grows a monster.
 * That is exactly what the editor's import made easy to do -- it files a chosen
 * PNG under its bare name, so a sheet that lives at {@code fiend/dark_resonator}
 * in the jar gets referred to as {@code dark_resonator}, which resolves for as
 * long as the imported copy is sitting in the config folder and never again
 * afterwards. Nobody would notice until somebody else installed the mod.
 * <p>
 * So this reads the shipped file the way a build does and checks every name in
 * it against the resources actually on disk.
 */
class ShippedSpritesTest
{
    /**
     * Resolved against the module's own resources first and the shared tree
     * second, which is the order Gradle merges them in.
     * <p>
     * Assets that both modules ship now live in {@code shared/resources} rather
     * than being duplicated per module, so a test that only knew the module path
     * saw an empty directory and reported every sprite missing.
     */
    private static Path resource(String path)
    {
        Path own = Path.of("src/main/resources", path);
        return Files.exists(own) ? own : Path.of("../shared/resources", path);
    }

    private static final Path LIST = resource("assets/dueldimension/monster_sprites.json");
    private static final Path SHEETS =
        resource("assets/dueldimension/textures/duel/monsters");

    private static JsonArray shipped() throws Exception
    {
        assertTrue(Files.isRegularFile(LIST), "the shipped sprite list is missing at " + LIST);
        return JsonParser.parseString(Files.readString(LIST, StandardCharsets.UTF_8))
            .getAsJsonArray();
    }

    @Test
    void everySheetItNamesExists() throws Exception
    {
        List<String> missing = new ArrayList<>();
        for(JsonElement element : shipped())
        {
            JsonObject definition = element.getAsJsonObject();
            for(String sheet : sheetsOf(definition))
            {
                Path file = SHEETS.resolve(sheet + ".png");
                if(!Files.isRegularFile(file))
                {
                    missing.add(definition.get("card").getAsLong() + " -> " + sheet + ".png");
                }
            }
        }
        assertTrue(missing.isEmpty(), "shipped definitions name sheets that are not in "
            + "resources, so those cards would silently have no monster:\n  "
            + String.join("\n  ", missing));
    }

    @Test
    void everySheetNameIsFiledUnderAType()
    {
        // The README's rule: a sheet lives in the folder for the card's type.
        // A bare name is the shape an editor import leaves behind, and it is
        // the one that stops resolving once the config copy is gone.
        List<String> loose = new ArrayList<>();
        try
        {
            for(JsonElement element : shipped())
            {
                JsonObject definition = element.getAsJsonObject();
                for(String sheet : sheetsOf(definition))
                {
                    if(!sheet.contains("/"))
                    {
                        loose.add(definition.get("card").getAsLong() + " -> " + sheet);
                    }
                }
            }
        }
        catch(Exception unreadable)
        {
            throw new AssertionError(unreadable);
        }
        assertTrue(loose.isEmpty(), "shipped sheets with no type folder:\n  "
            + String.join("\n  ", loose));
    }

    @Test
    void theReaderAcceptsEveryEntry() throws Exception
    {
        // Count in against count out. readDefinition drops a malformed entry
        // and carries on, which is right for a hand-edited config and wrong to
        // leave unnoticed in the file the mod ships.
        int written = shipped().size();
        MonsterSprites.loadShipped();
        assertEquals(written, MonsterSprites.shippedCount(),
            "the shipped list has " + written + " entries but only "
                + MonsterSprites.shippedCount() + " could be read");
        assertTrue(written >= 30, "only " + written + " shipped definitions");
    }

    @Test
    void noEntryIsBlankOrDuplicated() throws Exception
    {
        List<Long> codes = new ArrayList<>();
        for(JsonElement element : shipped())
        {
            JsonObject definition = element.getAsJsonObject();
            long code = definition.get("card").getAsLong();
            assertFalse(codes.contains(code), "card " + code + " is listed twice");
            assertTrue(code > 0L, "a shipped definition has no passcode");
            codes.add(code);
            assertTrue(definition.has("body"), "card " + code + " has no body layer");
            for(String sheet : sheetsOf(definition))
            {
                assertFalse(sheet.isBlank(), "card " + code + " names a blank sheet");
            }
        }
    }

    private static List<String> sheetsOf(JsonObject definition)
    {
        List<String> sheets = new ArrayList<>();
        sheets.add(definition.getAsJsonObject("body").get("sheet").getAsString());
        if(definition.has("defence"))
        {
            sheets.add(definition.getAsJsonObject("defence").get("sheet").getAsString());
        }
        if(definition.has("wings"))
        {
            sheets.add(definition.getAsJsonObject("wings").getAsJsonObject("layer")
                .get("sheet").getAsString());
        }
        return sheets;
    }
}
