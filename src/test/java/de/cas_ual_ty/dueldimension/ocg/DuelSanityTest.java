package de.cas_ual_ty.dueldimension.ocg;

import de.cas_ual_ty.dueldimension.ocg.bot.HeuristicBot;
import de.cas_ual_ty.dueldimension.ocg.deck.StarterDecks;
import de.cas_ual_ty.dueldimension.ocg.msg.DuelMessage;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Guards two things a player notices immediately: that decks are actually
 * shuffled between duels, and that the opponent plays monsters rather than
 * sitting on an empty field.
 */
class DuelSanityTest
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

    private record Outcome(List<Integer> firstDraws, int selfSummons, int opponentSummons, int turns)
    {
    }

    private Outcome play(OcgApi api, CdbCardProvider cards, long seed)
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

        List<Integer> draws = new ArrayList<>();
        int self = 0;
        int opponent = 0;
        int turns = 0;
        for(RawMessage raw : trace.messages)
        {
            DuelMessage message = DuelMessage.decode(raw);
            if(message instanceof DuelMessage.Draw draw && draws.size() < 5)
            {
                draw.cards().forEach(card -> draws.add(card.code()));
            }
            if(message instanceof DuelMessage.NewTurn)
            {
                turns++;
            }
            if(message instanceof DuelMessage.Move move
                && move.to().location() == OcgConstants.LOCATION_MZONE
                && move.from().location() != OcgConstants.LOCATION_MZONE)
            {
                if(move.to().controller() == 0)
                {
                    self++;
                }
                else
                {
                    opponent++;
                }
            }
        }
        return new Outcome(draws, self, opponent, turns);
    }

    @Test
    void decksAreShuffledAndBothPlayersSummon() throws Exception
    {
        assumeTrue(Files.isRegularFile(lib()), "native core not present");
        assumeTrue(Files.isRegularFile(scripts().resolve("constant.lua")), "CardScripts not present");
        assumeTrue(Files.isRegularFile(cdb()), "cards.cdb not present");

        OcgApi api = OcgApi.load(lib());
        CdbCardProvider cards = new CdbCardProvider(List.of(cdb()));

        List<Outcome> outcomes = new ArrayList<>();
        for(long seed : new long[] {12345, 777777, 20260805})
        {
            Outcome outcome = play(api, cards, seed);
            outcomes.add(outcome);
            System.out.println("seed " + seed + ": opening " + outcome.firstDraws()
                + " summons you=" + outcome.selfSummons() + " opponent=" + outcome.opponentSummons()
                + " turns=" + outcome.turns());
        }

        // Different seeds must not deal the same opening hand.
        assertTrue(!outcomes.get(0).firstDraws().equals(outcomes.get(1).firstDraws())
                || !outcomes.get(1).firstDraws().equals(outcomes.get(2).firstDraws()),
            "every seed dealt the same opening: the deck is not being shuffled");

        for(Outcome outcome : outcomes)
        {
            assertTrue(outcome.opponentSummons() > 0,
                "the opponent never put a monster on the field in " + outcome.turns() + " turns");
            assertTrue(outcome.selfSummons() > 0, "player one never summoned either");
        }
    }
}
