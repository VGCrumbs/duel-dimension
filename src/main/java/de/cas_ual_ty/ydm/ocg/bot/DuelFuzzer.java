package de.cas_ual_ty.ydm.ocg.bot;

import de.cas_ual_ty.ydm.ocg.CdbCardProvider;
import de.cas_ual_ty.ydm.ocg.HeadlessDuelRunner;
import de.cas_ual_ty.ydm.ocg.OcgApi;
import de.cas_ual_ty.ydm.ocg.OcgConstants;
import de.cas_ual_ty.ydm.ocg.RawMessage;
import de.cas_ual_ty.ydm.ocg.Replay;
import de.cas_ual_ty.ydm.ocg.msg.DuelMessage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs many bot-vs-bot duels looking for ways the stack breaks: responses the
 * core rejects (MSG_RETRY), prompts no bot can answer, messages that fail to
 * decode, hangs, and native crashes.
 * <p>
 * Every failure is written out as a {@link Replay} — seed, decks and the exact
 * response sequence — so it reproduces deterministically instead of being a
 * "sometimes it breaks" report. A marker file per worker names the duel in
 * flight, so even a native crash (which takes the JVM with it) leaves the
 * responsible seed behind.
 * <p>
 * Usage: DuelFuzzer --lib &lt;dll&gt; --scripts &lt;dir&gt; --cdb &lt;file&gt;
 * [--count N] [--threads T] [--seed base] [--out dir] [--steps N]
 */
public class DuelFuzzer
{
    private record Args(Path lib, Path scripts, Path cdb, int count, int threads, long baseSeed, Path out, int steps)
    {
    }

    private record Failure(long seed, String reason, Replay replay)
    {
    }

    public static void main(String[] rawArgs) throws Exception
    {
        Args args = parse(rawArgs);
        Files.createDirectories(args.out());

        CdbCardProvider cards = new CdbCardProvider(List.of(args.cdb()));
        OcgApi api = OcgApi.load(args.lib());
        System.out.println("Fuzzing " + args.count() + " duels on " + args.threads() + " thread(s), "
            + cards.size() + " cards loaded, base seed " + args.baseSeed());

        List<Failure> failures = Collections.synchronizedList(new ArrayList<>());
        Map<String, Integer> outcomes = new ConcurrentHashMap<>();
        AtomicInteger next = new AtomicInteger();
        AtomicInteger done = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(args.threads());
        long start = System.nanoTime();

        for(int worker = 0; worker < args.threads(); worker++)
        {
            int workerId = worker;
            Thread thread = new Thread(() ->
            {
                Path marker = args.out().resolve("in-flight-" + workerId + ".txt");
                try
                {
                    for(int index = next.getAndIncrement(); index < args.count(); index = next.getAndIncrement())
                    {
                        long seed = args.baseSeed() + index;
                        // Crash breadcrumb: survives a native abort.
                        Files.writeString(marker, "seed=" + seed + "\n");
                        Failure failure = runOne(api, cards, seed, args.steps(), outcomes);
                        if(failure != null)
                        {
                            failures.add(failure);
                            Path file = args.out().resolve("failure-" + seed + ".replay");
                            Files.writeString(file, failure.replay().encode());
                            System.out.println("  FAIL seed " + seed + ": " + failure.reason() + " -> " + file);
                        }
                        int completed = done.incrementAndGet();
                        if(completed % 25 == 0)
                        {
                            System.out.println("  " + completed + "/" + args.count() + " duels");
                        }
                    }
                    Files.deleteIfExists(marker);
                }
                catch(Exception e)
                {
                    System.err.println("Worker " + workerId + " died: " + e);
                    e.printStackTrace();
                }
                finally
                {
                    latch.countDown();
                }
            }, "fuzz-" + worker);
            thread.start();
        }

        latch.await();
        long millis = (System.nanoTime() - start) / 1_000_000;

        System.out.println("--- fuzz summary ---");
        outcomes.entrySet().stream().sorted(Map.Entry.comparingByKey())
            .forEach(entry -> System.out.println("  " + entry.getKey() + ": " + entry.getValue()));
        System.out.println("  elapsed: " + millis + " ms (" + (millis / Math.max(1, args.count())) + " ms/duel)");
        System.out.println(failures.isEmpty()
            ? "  NO FAILURES"
            : "  " + failures.size() + " FAILURES written to " + args.out());

        System.exit(failures.isEmpty() ? 0 : 1);
    }

    private static Failure runOne(OcgApi api, CdbCardProvider cards, long seed, int steps, Map<String, Integer> outcomes)
    {
        // Alternate deck archetypes so both the simple and the hard prompt
        // paths get fuzzed.
        HeadlessDuelRunner.Deck deck = (seed & 1) == 0 ? FuzzDecks.vanillaBeatdown() : FuzzDecks.ritualSynchro();
        long[] seeds = {seed, seed * 6364136223846793005L + 1, seed * 1442695040888963407L + 2, ~seed};

        HeadlessDuelRunner.DuelTrace trace;
        try
        {
            trace = HeadlessDuelRunner.builder(api)
                .seed(seeds)
                .flags(OcgConstants.DUEL_MODE_MR5)
                .cards(cards)
                .scripts(HeadlessDuelRunner.cardScriptsDirectory(FuzzDecks.scriptsDir()))
                .deck(0, deck)
                .deck(1, deck)
                .responder(0, new RandomBot(seed * 31, cards.all()))
                .responder(1, new RandomBot(seed * 37, cards.all()))
                .build()
                .run(steps);
        }
        catch(RuntimeException e)
        {
            count(outcomes, "exception");
            return new Failure(seed, "exception: " + e,
                Replay.of(OcgConstants.DUEL_MODE_MR5, seeds, deck, deck, List.of()));
        }

        Replay replay = Replay.of(OcgConstants.DUEL_MODE_MR5, seeds, deck, deck, trace.responses);

        if(trace.sawMessage(OcgConstants.MSG_RETRY))
        {
            count(outcomes, "retry");
            return new Failure(seed, "core rejected a response (MSG_RETRY)", replay);
        }
        for(RawMessage message : trace.messages)
        {
            try
            {
                DuelMessage.decode(message);
            }
            catch(RuntimeException e)
            {
                count(outcomes, "decode-error");
                return new Failure(seed, "undecodable " + message.name() + ": " + e, replay);
            }
        }
        if(!trace.completed)
        {
            // Either a prompt no bot could answer, or the step cap: both mean
            // we cannot yet play every position this deck reaches.
            count(outcomes, trace.steps >= steps ? "step-cap" : "unanswered-prompt");
            return new Failure(seed, trace.steps >= steps
                ? "hit step cap " + steps + " without a result"
                : "no legal response available at step " + trace.steps, replay);
        }

        count(outcomes, "completed");
        return null;
    }

    private static void count(Map<String, Integer> outcomes, String key)
    {
        outcomes.merge(key, 1, Integer::sum);
    }

    private static Args parse(String[] argv)
    {
        Path lib = Path.of(System.getProperty("ocg.lib", "native/ocgcore.dll"));
        Path scripts = Path.of(System.getProperty("ocg.scripts", "C:/ProjectIgnis/script"));
        Path cdb = Path.of(System.getProperty("ocg.cdb", "C:/ProjectIgnis/expansions/cards.cdb"));
        int count = 100;
        int threads = 1;
        long baseSeed = 1;
        Path out = Path.of("build/fuzz");
        int steps = 20000;

        for(int i = 0; i + 1 < argv.length; i += 2)
        {
            String value = argv[i + 1];
            switch(argv[i])
            {
                case "--lib" -> lib = Path.of(value);
                case "--scripts" -> scripts = Path.of(value);
                case "--cdb" -> cdb = Path.of(value);
                case "--count" -> count = Integer.parseInt(value);
                case "--threads" -> threads = Integer.parseInt(value);
                case "--seed" -> baseSeed = Long.parseLong(value);
                case "--out" -> out = Path.of(value);
                case "--steps" -> steps = Integer.parseInt(value);
                default -> throw new IllegalArgumentException("Unknown option " + argv[i]);
            }
        }
        FuzzDecks.setScriptsDir(scripts);
        return new Args(lib, scripts, cdb, count, threads, baseSeed, out, steps);
    }
}
