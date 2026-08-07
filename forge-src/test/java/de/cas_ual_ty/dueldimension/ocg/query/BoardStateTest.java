package de.cas_ual_ty.dueldimension.ocg.query;

import de.cas_ual_ty.dueldimension.ocg.CdbCardProvider;
import de.cas_ual_ty.dueldimension.ocg.HeadlessDuelRunner;
import de.cas_ual_ty.dueldimension.ocg.OcgApi;
import de.cas_ual_ty.dueldimension.ocg.OcgConstants;
import de.cas_ual_ty.dueldimension.ocg.RawMessage;
import de.cas_ual_ty.dueldimension.ocg.ResponseSource;
import de.cas_ual_ty.dueldimension.ocg.bot.FuzzDecks;
import de.cas_ual_ty.dueldimension.ocg.bot.RandomBot;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The board snapshot has to be right in two different ways: it must actually
 * see the field (a bot reasoning about an empty board is playing blind), and
 * it must NOT see what the viewer isn't entitled to (an NPC reading face-down
 * cards is silently cheating).
 */
class BoardStateTest
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

    /** Captures both views the first time the given prompt type shows up. */
    private static class Spy implements ResponseSource
    {
        private final ResponseSource inner;
        private final int captureOn;
        private BoardObserver observer;
        BoardState honest;
        BoardState omniscient;
        final List<BoardState> samples = new ArrayList<>();

        Spy(ResponseSource inner, int captureOn)
        {
            this.inner = inner;
            this.captureOn = captureOn;
        }

        @Override
        public void onDuelStart(int playerIndex, BoardObserver board)
        {
            observer = board;
            inner.onDuelStart(playerIndex, board);
        }

        @Override
        public void observe(RawMessage message)
        {
            inner.observe(message);
        }

        @Override
        public byte[] respond(RawMessage prompt)
        {
            if(prompt.type() == captureOn)
            {
                BoardState state = observer.observe();
                samples.add(state);
                if(honest == null && state.self().monsterCount() > 0)
                {
                    honest = state;
                    omniscient = observer.observeOmnisciently();
                }
            }
            return inner.respond(prompt);
        }
    }

    @Test
    void boardStateSeesTheFieldAndHidesWhatItShould() throws Exception
    {
        assumeTrue(Files.isRegularFile(lib()), "native core not present");
        assumeTrue(Files.isRegularFile(scripts().resolve("constant.lua")), "CardScripts not present");
        assumeTrue(Files.isRegularFile(cdb()), "cards.cdb not present");

        OcgApi api = OcgApi.load(lib());
        CdbCardProvider cards = new CdbCardProvider(List.of(cdb()));
        Spy spy = new Spy(new RandomBot(7, cards.all()), OcgConstants.MSG_SELECT_BATTLECMD);

        HeadlessDuelRunner.DuelTrace trace = HeadlessDuelRunner.builder(api)
            .seed(new long[] {5, 15, 25, 35})
            .cards(cards)
            .scripts(HeadlessDuelRunner.cardScriptsDirectory(scripts()))
            .deck(0, FuzzDecks.vanillaBeatdown())
            .deck(1, FuzzDecks.vanillaBeatdown())
            .responder(0, spy)
            .responder(1, new RandomBot(9, cards.all()))
            .build()
            .run(20000);

        assertTrue(trace.completed, "duel must finish");
        assertFalse(spy.samples.isEmpty(), "never reached a battle phase — nothing was sampled");

        BoardState state = spy.honest;
        assertNotNull(state, "never saw our own monster on the field: board query is blind");

        // Sanity: life points and pile sizes must be real numbers.
        assertTrue(state.self().lifePoints() > 0 && state.self().lifePoints() <= 8000,
            "implausible LP " + state.self().lifePoints());
        assertTrue(state.self().deckCount() > 0 && state.self().deckCount() < 40,
            "implausible deck count " + state.self().deckCount());
        assertEquals(BoardState.MONSTER_ZONES, state.self().monsters().size(), "monster zone count");
        assertEquals(BoardState.SPELL_ZONES, state.self().spells().size(), "spell zone count");

        // Our monsters must carry real stats — this is what the bot's threat
        // model reads. (A given battle phase may legitimately have only
        // defence-position monsters, so check the stats, not the posture.)
        boolean sawRealStats = state.self().monsters().stream()
            .anyMatch(card -> card != null && card.attack() > 0 && card.code() != 0);
        assertTrue(sawRealStats, "our monsters have no stats: " + state.self().monsters());

        // Across a whole duel someone must at some point hold an attacker,
        // or the threat model would be permanently blind.
        assertTrue(spy.samples.stream().anyMatch(sample -> sample.self().strongestAttacker() > 0
                || sample.opponent().strongestAttacker() > 0),
            "no face-up attack-position monster in any of " + spy.samples.size() + " samples");

        // Honesty: our own hand is fully known, and nothing of ours is hidden.
        for(CardView card : state.self().hand())
        {
            if(card != null)
            {
                assertFalse(card.hidden(), "our own hand card was hidden from us");
            }
        }

        // Honesty: any non-public opponent card must be stripped in the honest
        // view but present in the omniscient one.
        int hiddenFromUs = 0;
        for(CardView card : spy.honest.opponent().hand())
        {
            if(card != null && card.hidden())
            {
                hiddenFromUs++;
            }
        }
        int visibleWhenCheating = 0;
        for(CardView card : spy.omniscient.opponent().hand())
        {
            if(card != null && card.code() != 0)
            {
                visibleWhenCheating++;
            }
        }
        System.out.println("opponent hand: " + spy.honest.opponent().hand().size()
            + " cards, hidden from us=" + hiddenFromUs + ", readable when cheating=" + visibleWhenCheating);
        assertTrue(hiddenFromUs > 0, "opponent's hand was fully readable — the bot would be cheating");
        assertTrue(visibleWhenCheating >= hiddenFromUs,
            "omniscient view should reveal what the honest view hides");
    }
}
