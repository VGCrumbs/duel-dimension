package de.cas_ual_ty.dueldimension.ocg.bot;

import de.cas_ual_ty.dueldimension.ocg.CdbCardProvider;
import de.cas_ual_ty.dueldimension.ocg.HeadlessDuelRunner;
import de.cas_ual_ty.dueldimension.ocg.OcgApi;
import de.cas_ual_ty.dueldimension.ocg.OcgConstants;
import de.cas_ual_ty.dueldimension.ocg.ResponseSource;

import java.nio.file.Path;
import java.util.List;
import java.util.function.LongFunction;

/**
 * Head-to-head bot evaluation. Every AI change gets a number instead of an
 * opinion — and because duels are seeded, two runs of the same matchup are
 * directly comparable.
 * <p>
 * Seats are swapped every other duel: going first is a real advantage in this
 * game, and a bot must not win the arena by being lucky with the coin.
 */
public class BotArena
{
    public record Result(String name, int wins, int losses, int draws, int unfinished, int duels)
    {
        public double winRate()
        {
            int decided = wins + losses;
            return decided == 0 ? 0 : (double)wins / decided;
        }

        @Override
        public String toString()
        {
            return String.format("%s: %d-%d-%d over %d duels (%.1f%% of decided)%s",
                name, wins, losses, draws, duels, winRate() * 100,
                unfinished > 0 ? ", " + unfinished + " UNFINISHED" : "");
        }
    }

    /** Builds a responder for a given seed; lets the arena reseed each duel. */
    public interface BotFactory extends LongFunction<ResponseSource>
    {
    }

    public static Result play(OcgApi api, CdbCardProvider cards, HeadlessDuelRunner.Deck deck,
        String name, BotFactory challenger, BotFactory baseline, int duels, long baseSeed)
    {
        int wins = 0;
        int losses = 0;
        int draws = 0;
        int unfinished = 0;

        for(int i = 0; i < duels; i++)
        {
            long seed = baseSeed + i;
            boolean challengerFirst = (i % 2) == 0;

            HeadlessDuelRunner.DuelTrace trace = HeadlessDuelRunner.builder(api)
                .seed(new long[] {seed, seed * 31 + 7, seed * 131 + 17, ~seed})
                .flags(OcgConstants.DUEL_MODE_MR5)
                .cards(cards)
                .scripts(HeadlessDuelRunner.cardScriptsDirectory(FuzzDecks.scriptsDir()))
                .deck(0, deck)
                .deck(1, deck)
                .responder(0, challengerFirst ? challenger.apply(seed) : baseline.apply(seed))
                .responder(1, challengerFirst ? baseline.apply(seed) : challenger.apply(seed))
                .build()
                .run(20000);

            if(!trace.completed || trace.result == null)
            {
                unfinished++;
                continue;
            }
            int winner = trace.result.winner();
            int challengerSeat = challengerFirst ? 0 : 1;
            if(winner > 1)
            {
                draws++;
            }
            else if(winner == challengerSeat)
            {
                wins++;
            }
            else
            {
                losses++;
            }
        }
        return new Result(name, wins, losses, draws, unfinished, duels);
    }

    /**
     * Usage: BotArena [--duels N] [--seed base] [--lib dll] [--scripts dir] [--cdb file]
     */
    public static void main(String[] argv) throws Exception
    {
        // Unpack the bundled engine if this machine has nothing else; see
        // DuelFuzzer.main. Must come before defaults() below, which only reports
        // what is on disk.
        de.cas_ual_ty.dueldimension.ocg.session.EngineBundle.install();

        // The same discovery the mod itself uses -- properties first, then a
        // real EDOPro install wherever it is on this machine, then the copy this
        // jar carries -- rather than one developer's drive letter spelled out
        // three times.
        de.cas_ual_ty.dueldimension.ocg.session.EngineRuntime.Paths found =
            de.cas_ual_ty.dueldimension.ocg.session.EngineRuntime.Paths.defaults();
        Path lib = found.library();
        Path scripts = found.scriptsDir();
        Path cdb = found.cdb();
        int duels = 200;
        long baseSeed = 1;

        for(int i = 0; i + 1 < argv.length; i += 2)
        {
            String value = argv[i + 1];
            switch(argv[i])
            {
                case "--lib" -> lib = Path.of(value);
                case "--scripts" -> scripts = Path.of(value);
                case "--cdb" -> cdb = Path.of(value);
                case "--duels" -> duels = Integer.parseInt(value);
                case "--seed" -> baseSeed = Long.parseLong(value);
                default -> throw new IllegalArgumentException("Unknown option " + argv[i]);
            }
        }
        FuzzDecks.setScriptsDir(scripts);

        OcgApi api = OcgApi.load(lib);
        CdbCardProvider cards = new CdbCardProvider(List.of(cdb));

        BotFactory heuristic = seed -> new HeuristicBot(seed, cards, cards.all());
        BotFactory randomBot = seed -> new RandomBot(seed ^ 0xABCDEF, cards.all());

        System.out.println("Arena: " + duels + " duels per matchup, base seed " + baseSeed);
        for(String deckName : new String[] {"vanilla", "ritual-synchro", "starter-yugi", "starter-kaiba", "starter-joey"})
        {
            HeadlessDuelRunner.Deck deck = switch(deckName)
            {
                case "vanilla" -> FuzzDecks.vanillaBeatdown();
                case "ritual-synchro" -> FuzzDecks.ritualSynchro();
                case "starter-yugi" -> de.cas_ual_ty.dueldimension.ocg.deck.StarterDecks.YUGI.load().toRunnerDeck();
                case "starter-kaiba" -> de.cas_ual_ty.dueldimension.ocg.deck.StarterDecks.KAIBA.load().toRunnerDeck();
                default -> de.cas_ual_ty.dueldimension.ocg.deck.StarterDecks.JOEY.load().toRunnerDeck();
            };
            long start = System.nanoTime();
            Result result = play(api, cards, deck, "HeuristicBot vs RandomBot [" + deckName + "]",
                heuristic, randomBot, duels, baseSeed);
            System.out.println("  " + result
                + String.format("  (%d ms)", (System.nanoTime() - start) / 1_000_000));
        }
    }
}
