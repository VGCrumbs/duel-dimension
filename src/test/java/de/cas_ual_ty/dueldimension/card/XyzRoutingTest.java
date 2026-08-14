package de.cas_ual_ty.dueldimension.card;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.cas_ual_ty.dueldimension.card.properties.Properties;
import de.cas_ual_ty.dueldimension.util.DdUtil;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An Xyz monster has to report itself as an Extra Deck card, because that one
 * answer is what every add path routes on -- the editor, the pack-opening
 * menu and the deck validator all ask {@code getIsInExtraDeck()} and put the
 * card wherever it says. A monster that answers wrongly is silently built into
 * an illegal deck.
 */
public class XyzRoutingTest
{
    /**
     * Built the way the database builds them, from the real card files, so
     * this exercises the subclass dispatch rather than a hand-made object.
     */
    private static Properties build(Path file) throws IOException
    {
        JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        return DdUtil.buildProperties(json);
    }

    @Test
    public void xyzMonstersBelongInTheExtraDeck() throws IOException
    {
        Path cards = Path.of("run", "ydm_db", "cards");
        Assumptions.assumeTrue(Files.isDirectory(cards), "no card database on this machine");

        List<Path> xyz;
        try(Stream<Path> all = Files.list(cards))
        {
            xyz = all.filter(p -> p.toString().endsWith(".json")).filter(p ->
            {
                try
                {
                    return Files.readString(p).contains("\"monster_type\": \"Xyz\"");
                }
                catch(IOException e)
                {
                    return false;
                }
            }).limit(40).toList();
        }
        Assumptions.assumeFalse(xyz.isEmpty(), "no Xyz cards in the database");

        for(Path file : xyz)
        {
            Properties card = build(file);
            assertTrue(card.getIsInExtraDeck(),
                file.getFileName() + " is an Xyz monster but says it is not an Extra Deck card"
                    + " (built as " + card.getClass().getSimpleName() + ")");
        }
    }
}
