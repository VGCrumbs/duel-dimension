package de.cas_ual_ty.dueldimension.ocg.deck;

import de.cas_ual_ty.dueldimension.ocg.CdbCardProvider;
import de.cas_ual_ty.dueldimension.ocg.HeadlessDuelRunner;
import de.cas_ual_ty.dueldimension.ocg.OcgApi;
import de.cas_ual_ty.dueldimension.ocg.OcgCard;
import de.cas_ual_ty.dueldimension.ocg.OcgConstants;
import de.cas_ual_ty.dueldimension.ocg.RawMessage;
import de.cas_ual_ty.dueldimension.ocg.bot.HeuristicBot;
import de.cas_ual_ty.dueldimension.ocg.msg.DuelMessage;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A starter deck the player can pick but the engine can't play would be a
 * miserable first experience, so each one is checked end to end: legal
 * construction, every card known to the engine, every card scripted (directly
 * or through its alt-art alias), and a full duel played with it.
 */
class StarterDeckTest
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
    void allStarterDecksLoadAndAreLegallyConstructed()
    {
        assertFalse(StarterDecks.ALL.isEmpty());
        for(StarterDecks.Entry entry : StarterDecks.ALL)
        {
            YdkDeck deck = entry.load();
            assertTrue(deck.isLegalSize(),
                entry.displayName() + " has an illegal size: main=" + deck.main().size()
                    + " extra=" + deck.extra().size());
            assertTrue(deck.side().isEmpty(), entry.displayName() + " should ship without a side deck");
            System.out.println(entry.displayName() + " (" + entry.setCode() + "): "
                + deck.main().size() + " main cards");
        }
    }

    @Test
    void everyStarterCardIsKnownAndPlayableByTheEngine() throws Exception
    {
        assumeTrue(Files.isRegularFile(cdb()), "cards.cdb not present");
        assumeTrue(Files.isDirectory(scripts()), "CardScripts not present");

        CdbCardProvider cards = new CdbCardProvider(List.of(cdb()));

        for(StarterDecks.Entry entry : StarterDecks.ALL)
        {
            YdkDeck deck = entry.load();
            List<Integer> unknown = new ArrayList<>();
            List<Integer> unscripted = new ArrayList<>();

            for(int code : deck.main())
            {
                OcgCard card = cards.get(code);
                if(card == null)
                {
                    unknown.add(code);
                    continue;
                }
                // Vanilla normal monsters need no script. Everything else does
                // — and for an alt-art print the script lives under its alias,
                // which the core resolves for us (interpreter.cpp).
                boolean vanillaMonster = (card.type() & 0x11) == 0x11 && (card.type() & 0x20) == 0;
                if(vanillaMonster)
                {
                    continue;
                }
                int scriptCode = card.alias() != 0 ? card.alias() : card.code();
                boolean hasScript = Files.isRegularFile(scripts().resolve("official/c" + scriptCode + ".lua"))
                    || Files.isRegularFile(scripts().resolve("c" + scriptCode + ".lua"));
                if(!hasScript)
                {
                    unscripted.add(code);
                }
            }

            assertTrue(unknown.isEmpty(), entry.displayName() + " has cards missing from the cdb: " + unknown);
            assertTrue(unscripted.isEmpty(), entry.displayName() + " has unplayable cards: " + unscripted);
        }
    }

    @Test
    void starterDecksPlayFullDuelsAgainstEachOther() throws Exception
    {
        assumeTrue(Files.isRegularFile(lib()), "native core not present");
        assumeTrue(Files.isRegularFile(scripts().resolve("constant.lua")), "CardScripts not present");
        assumeTrue(Files.isRegularFile(cdb()), "cards.cdb not present");

        OcgApi api = OcgApi.load(lib());
        CdbCardProvider cards = new CdbCardProvider(List.of(cdb()));

        // Yugi vs Kaiba, Kaiba vs Joey, Joey vs Yugi.
        for(int i = 0; i < StarterDecks.ALL.size(); i++)
        {
            StarterDecks.Entry first = StarterDecks.ALL.get(i);
            StarterDecks.Entry second = StarterDecks.ALL.get((i + 1) % StarterDecks.ALL.size());
            long seed = 1000L + i;

            HeadlessDuelRunner.DuelTrace trace = HeadlessDuelRunner.builder(api)
                .seed(new long[] {seed, seed * 3 + 1, seed * 7 + 2, ~seed})
                .flags(OcgConstants.DUEL_MODE_MR5)
                .cards(cards)
                .scripts(HeadlessDuelRunner.cardScriptsDirectory(scripts()))
                .deck(0, first.load().toRunnerDeck())
                .deck(1, second.load().toRunnerDeck())
                .responder(0, new HeuristicBot(seed, cards, cards.all()))
                .responder(1, new HeuristicBot(seed * 2, cards, cards.all()))
                .build()
                .run(20000);

            assertFalse(trace.sawMessage(OcgConstants.MSG_RETRY),
                first.displayName() + " vs " + second.displayName() + ": engine rejected a response");
            assertTrue(trace.completed,
                first.displayName() + " vs " + second.displayName() + " did not finish (steps " + trace.steps + ")");
            assertNotNull(trace.result);

            for(RawMessage message : trace.messages)
            {
                DuelMessage.decode(message);
            }
            System.out.println(first.displayName() + " vs " + second.displayName()
                + " -> winner seat " + trace.result.winner() + " after " + trace.responses.size() + " decisions");
        }
    }
}
