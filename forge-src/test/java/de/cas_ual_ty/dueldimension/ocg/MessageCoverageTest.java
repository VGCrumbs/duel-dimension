package de.cas_ual_ty.dueldimension.ocg;

import de.cas_ual_ty.dueldimension.ocg.bot.HeuristicBot;
import de.cas_ual_ty.dueldimension.ocg.deck.StarterDecks;
import de.cas_ual_ty.dueldimension.ocg.msg.DuelMessage;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Decodes every message a real duel produces.
 * <p>
 * {@code DuelMessage.decode} calls {@code expectEnd()} on anything it claims to
 * understand, so a payload read even one field short throws — in game that
 * would take the duel thread down. This runs the decoder over whole duels so a
 * mis-read layout fails here instead.
 * <p>
 * It also reports how often the chain, target and flip-summon messages actually
 * occur, which is the evidence for whether effects were firing invisibly.
 */
class MessageCoverageTest
{
    private static Path lib()
    {
        return Path.of(System.getProperty("ocg.lib", "native/ocgcore.dll"));
    }

    private static Path scripts()
    {
        return Path.of(System.getProperty("ocg.scripts", "C:/ProjectIgnis/script"));
    }

    private static Path cdb()
    {
        return Path.of(System.getProperty("ocg.cdb", "C:/ProjectIgnis/expansions/cards.cdb"));
    }

    @Test
    void everyMessageInARealDuelDecodes() throws Exception
    {
        assumeTrue(Files.isRegularFile(lib()), "native core not present");
        assumeTrue(Files.isRegularFile(scripts().resolve("constant.lua")), "CardScripts not present");
        assumeTrue(Files.isRegularFile(cdb()), "cards.cdb not present");

        OcgApi api = OcgApi.load(lib());
        CdbCardProvider cards = new CdbCardProvider(List.of(cdb()));

        Map<String, Integer> counts = new TreeMap<>();
        int decoded = 0;

        for(long seed : new long[] {12345, 777777, 20260805, 424242})
        {
            HeadlessDuelRunner.DuelTrace trace = HeadlessDuelRunner.builder(api)
                .seed(new long[] {seed | 1, seed * 31 + 7, seed * 131 + 17, ~seed})
                .flags(OcgConstants.DUEL_MODE_MR5)
                .cards(cards)
                .scripts(HeadlessDuelRunner.cardScriptsDirectory(scripts()))
                .deck(0, StarterDecks.YUGI.load().toRunnerDeck())
                .deck(1, StarterDecks.JOEY.load().toRunnerDeck())
                .responder(0, new HeuristicBot(seed, cards, cards.all()))
                .responder(1, new HeuristicBot(seed * 2 + 1, cards, cards.all()))
                .build()
                .run(20000);

            for(RawMessage raw : trace.messages)
            {
                // Throws if a payload layout is wrong; that is the point.
                DuelMessage message = DuelMessage.decode(raw);
                decoded++;
                String name = message instanceof DuelMessage.Unknown
                    ? "Unknown(type " + raw.type() + ")"
                    : message.getClass().getSimpleName();
                counts.merge(name, 1, Integer::sum);
            }
        }

        System.out.println("decoded " + decoded + " messages across four duels:");
        counts.forEach((name, count) -> System.out.println("  " + name + " x" + count));

        assertTrue(decoded > 500, "suspiciously few messages decoded: " + decoded);
        // The three that had no decoder at all until now.
        assertTrue(counts.getOrDefault("Chaining", 0) > 0,
            "no effect ever chained, so the chain feedback cannot be verified");
        assertTrue(counts.getOrDefault("BecomeTarget", 0) > 0,
            "nothing was ever targeted, so target feedback cannot be verified");
    }
}
