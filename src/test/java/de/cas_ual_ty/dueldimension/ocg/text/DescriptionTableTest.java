package de.cas_ual_ty.dueldimension.ocg.text;

import de.cas_ual_ty.dueldimension.ocg.CdbCardProvider;
import de.cas_ual_ty.dueldimension.ocg.HeadlessDuelRunner;
import de.cas_ual_ty.dueldimension.ocg.OcgApi;
import de.cas_ual_ty.dueldimension.ocg.RawMessage;
import de.cas_ual_ty.dueldimension.ocg.bot.HeuristicBot;
import de.cas_ual_ty.dueldimension.ocg.deck.StarterDecks;
import de.cas_ual_ty.dueldimension.ocg.msg.DuelMessage;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DescriptionTableTest
{
    private static Path stringsConf()
    {
        return Path.of(System.getProperty("ocg.strings", "C:/ProjectIgnis/config/strings.conf"));
    }

    private static Path cdb()
    {
        return Path.of(System.getProperty("ocg.cdb", "C:/ProjectIgnis/expansions/cards.cdb"));
    }

    private static Path lib()
    {
        return Path.of(System.getProperty("ocg.lib", "native/ocgcore.dll"));
    }

    private static Path scripts()
    {
        return Path.of(System.getProperty("ocg.scripts", "C:/ProjectIgnis/script"));
    }

    @Test
    void encodingMatchesTheScriptsStringidFunction()
    {
        // aux.Stringid(code, id) = (id & 0xFFFFF) | code << 20
        long dark = ((long)46986414 << 20) | 3;
        assertTrue(DescriptionTable.isCardString(dark));
        assertEquals(46986414, DescriptionTable.cardOf(dark));
        assertEquals(3, DescriptionTable.indexOf(dark));

        assertFalse(DescriptionTable.isCardString(1150), "system strings live below 2^20");
        assertFalse(DescriptionTable.isCardString(0));
    }

    @Test
    void resolvesSystemAndCardStrings() throws Exception
    {
        assumeTrue(Files.isRegularFile(stringsConf()), "strings.conf not present");
        assumeTrue(Files.isRegularFile(cdb()), "cards.cdb not present");

        DescriptionTable table = new DescriptionTable(stringsConf(), List.of(cdb()));
        assertTrue(table.systemStringCount() > 500, "expected many system strings, got " + table.systemStringCount());
        assertTrue(table.cardStringCount() > 100, "expected many card strings, got " + table.cardStringCount());

        assertEquals("Activate", table.describe(1150));
        assertEquals("Normal Summon", table.describe(1151));
        assertEquals("Select the card(s) to Tribute", table.describe(500));

        // A real card string: Labrynth Cooclock's first effect label.
        long labrynth = ((long)2511 << 20);
        assertTrue(table.describe(labrynth).startsWith("Can activate 1 Normal Trap"),
            "unexpected card string: " + table.describe(labrynth));

        // Unknown values must degrade to a label, never null or an exception.
        assertTrue(table.describe(((long)99999999 << 20) | 5).startsWith("?card:"));
        assertEquals("", table.describe(0));
    }

    /** Prompts in a real duel should read as sentences, not numbers. */
    @Test
    void realDuelPromptsGetReadableText() throws Exception
    {
        assumeTrue(Files.isRegularFile(lib()), "native core not present");
        assumeTrue(Files.isRegularFile(scripts().resolve("constant.lua")), "CardScripts not present");
        assumeTrue(Files.isRegularFile(cdb()), "cards.cdb not present");
        assumeTrue(Files.isRegularFile(stringsConf()), "strings.conf not present");

        DescriptionTable table = new DescriptionTable(stringsConf(), List.of(cdb()));
        CdbCardProvider cards = new CdbCardProvider(List.of(cdb()));
        OcgApi api = OcgApi.load(lib());

        HeadlessDuelRunner.DuelTrace trace = HeadlessDuelRunner.builder(api)
            .seed(new long[] {21, 22, 23, 24})
            .cards(cards)
            .scripts(HeadlessDuelRunner.cardScriptsDirectory(scripts()))
            .deck(0, StarterDecks.YUGI.load().toRunnerDeck())
            .deck(1, StarterDecks.KAIBA.load().toRunnerDeck())
            .responder(0, new HeuristicBot(1, cards, cards.all()))
            .responder(1, new HeuristicBot(2, cards, cards.all()))
            .build()
            .run(20000);

        Set<String> descriptions = new LinkedHashSet<>();
        int unresolved = 0;
        for(RawMessage message : trace.messages)
        {
            DuelMessage decoded = DuelMessage.decode(message);
            long description = 0;
            if(decoded instanceof DuelMessage.Hint hint)
            {
                // A hint's payload is only a string id for some hint types;
                // others carry passcodes, masks or plain numbers.
                String text = table.describeHint(hint.hintType(), hint.description());
                descriptions.add(text);
                if(text.startsWith("?"))
                {
                    unresolved++;
                }
                continue;
            }
            else if(decoded instanceof DuelMessage.SelectYesNo yesNo)
            {
                description = yesNo.description();
            }
            else if(decoded instanceof DuelMessage.SelectEffectYesNo effect)
            {
                description = effect.description();
            }
            if(description != 0)
            {
                String text = table.describe(description);
                descriptions.add(text);
                if(text.startsWith("?"))
                {
                    unresolved++;
                }
            }
        }

        System.out.println("Prompt texts seen: " + descriptions);
        assertFalse(descriptions.isEmpty(), "no descriptions in a full duel — nothing to label");
        assertEquals(0, unresolved, "some descriptions could not be resolved: " + descriptions);
    }
}
