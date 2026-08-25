package de.cas_ual_ty.dueldimension.ocg.query;

import de.cas_ual_ty.dueldimension.ocg.CdbCardProvider;
import de.cas_ual_ty.dueldimension.ocg.HeadlessDuelRunner;
import de.cas_ual_ty.dueldimension.ocg.OcgApi;
import de.cas_ual_ty.dueldimension.ocg.OcgConstants;
import de.cas_ual_ty.dueldimension.ocg.RawMessage;
import de.cas_ual_ty.dueldimension.ocg.ResponseSource;
import de.cas_ual_ty.dueldimension.ocg.bot.FuzzDecks;
import de.cas_ual_ty.dueldimension.ocg.bot.RandomBot;
import de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The whole per-copy artwork feature rests on one claim about the engine:
 * {@code card::cover} is a slot the core carries on the card object itself,
 * so it tells two copies of one passcode apart after the deck has been
 * shuffled, drawn from and played out of — which (controller, location,
 * sequence) cannot, because all three are renumbered without being reported.
 * <p>
 * These tests make that claim against the live native core rather than
 * against the C++ it was read from. If they fail, the honest behaviour is
 * every copy on its printed artwork, and no UI should be built on top.
 */
class CardCoverTest
{
    /** Ten of these are in the deck, and each gets an artwork of its own. */
    private static final int GIANT_SOLDIER_OF_STONE = 13039848;

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

    /** Reads both seats' honest views every time it is asked a question. */
    private static class Spy implements ResponseSource
    {
        private final ResponseSource inner;
        private BoardObserver observer;
        final List<BoardState> samples = new ArrayList<>();

        Spy(ResponseSource inner)
        {
            this.inner = inner;
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
            samples.add(observer.observe());
            return inner.respond(prompt);
        }
    }

    /**
     * Ten copies of one card, each dressed differently, stay ten distinguishable
     * copies all the way from the deck list to the field.
     */
    @Test
    void eachCopyKeepsItsOwnArtworkThroughShuffleAndPlay() throws Exception
    {
        assumeTrue(Files.isRegularFile(lib()), "native core not present");
        assumeTrue(Files.isRegularFile(scripts().resolve("constant.lua")), "CardScripts not present");
        assumeTrue(Files.isRegularFile(cdb()), "cards.cdb not present");

        HeadlessDuelRunner.Deck plain = FuzzDecks.vanillaBeatdown();
        // One artwork per copy of the tenfold card, so a copy that turns up
        // wearing artwork 4 can only be the fourth one in the deck list.
        List<Integer> arts = new ArrayList<>();
        int dressedCopies = 0;
        for(int code : plain.main())
        {
            if(code == GIANT_SOLDIER_OF_STONE)
            {
                arts.add(++dressedCopies);
            }
            else
            {
                arts.add(0);
            }
        }
        assertEquals(10, dressedCopies, "the fixture deck should hold ten of them");

        Spy spy = new Spy(new RandomBot(7, cards().all()));
        HeadlessDuelRunner.DuelTrace trace = runner(spy)
            .deck(0, plain.wearing(arts, List.of()))
            .deck(1, plain)
            .build()
            .run(20000);

        assertTrue(trace.completed, "duel must finish");
        assertTrue(!spy.samples.isEmpty(), "nothing was sampled");

        Set<Integer> seen = new HashSet<>();
        for(BoardState sample : spy.samples)
        {
            Set<Integer> thisSample = new HashSet<>();
            for(CardView card : ours(sample))
            {
                if(card.code() == GIANT_SOLDIER_OF_STONE)
                {
                    assertTrue(card.art() >= 1 && card.art() <= dressedCopies,
                        "a dressed copy turned up wearing artwork " + card.art());
                    assertTrue(thisSample.add(card.art()),
                        "two copies visible at once wearing artwork " + card.art()
                            + " -- the engine is not telling them apart");
                    seen.add(card.art());
                }
                else
                {
                    assertEquals(0, card.art(),
                        "card " + card.code() + " was never dressed but reports artwork " + card.art());
                }
            }
        }

        System.out.println("distinct artworks seen in play: " + new java.util.TreeSet<>(seen));
        // Five open the hand and more are drawn every turn, so seeing only a
        // couple would mean the identity was being lost somewhere.
        assertTrue(seen.size() >= 5,
            "only " + seen.size() + " of " + dressedCopies + " dressed copies were ever identified");
    }

    /**
     * The other half of the bargain: a deck nobody dressed pays nothing. No
     * chunk is generated, so every copy reports artwork 0 — which is also what
     * a client that has never heard of this feature would draw.
     */
    @Test
    void anUndressedDeckReportsNoArtworkAtAll() throws Exception
    {
        assumeTrue(Files.isRegularFile(lib()), "native core not present");
        assumeTrue(Files.isRegularFile(scripts().resolve("constant.lua")), "CardScripts not present");
        assumeTrue(Files.isRegularFile(cdb()), "cards.cdb not present");

        Spy spy = new Spy(new RandomBot(11, cards().all()));
        HeadlessDuelRunner.DuelTrace trace = runner(spy)
            .deck(0, FuzzDecks.vanillaBeatdown())
            .deck(1, FuzzDecks.vanillaBeatdown())
            .build()
            .run(20000);

        assertTrue(trace.completed, "duel must finish");
        for(BoardState sample : spy.samples)
        {
            for(CardView card : ours(sample))
            {
                assertEquals(0, card.art(), "an undressed deck produced artwork " + card.art());
            }
        }
    }

    /**
     * An artwork index says almost as much as a passcode — barely a hundred
     * cards have a second artwork — so it must never travel for a card the
     * viewer may not identify. The snapshot is what reaches the client, so
     * that is what is checked.
     */
    @Test
    void anArtworkNeverTravelsWithoutAnIdentity() throws Exception
    {
        assumeTrue(Files.isRegularFile(lib()), "native core not present");
        assumeTrue(Files.isRegularFile(scripts().resolve("constant.lua")), "CardScripts not present");
        assumeTrue(Files.isRegularFile(cdb()), "cards.cdb not present");

        HeadlessDuelRunner.Deck plain = FuzzDecks.vanillaBeatdown();
        List<Integer> arts = new ArrayList<>();
        for(int code : plain.main())
        {
            // Every single copy dressed, so a leak has nowhere to hide.
            arts.add(code == GIANT_SOLDIER_OF_STONE ? 3 : 1);
        }

        Spy spy = new Spy(new RandomBot(13, cards().all()));
        // Seat 0 watches; seat 1 is the one holding dressed cards, so
        // everything of seat 1's that seat 0 may not read must arrive blank.
        HeadlessDuelRunner.DuelTrace trace = runner(spy)
            .deck(0, plain)
            .deck(1, plain.wearing(arts, List.of()))
            .build()
            .run(20000);

        assertTrue(trace.completed, "duel must finish");
        int concealed = 0;
        // Counted per zone, because one total cannot tell "the field was
        // checked and was clean" from "the field was never concealed at all
        // and only the hand was ever tested". The set monster is the case the
        // rule exists for, so it gets its own count and its own floor below.
        String[] names = {"monsters", "spells", "hand", "grave", "banished", "extra"};
        int[] perZone = new int[names.length];
        // Leaks are tallied rather than thrown on sight. Failing at the first
        // one names whichever zone happens to come up earliest -- the opponent
        // hand, always, since it is concealed from the opening draw -- and says
        // nothing about the set monster, which is the case the rule exists for.
        int[] leaks = new int[names.length];
        for(BoardState sample : spy.samples)
        {
            BoardSnapshot snapshot = BoardSnapshot.of(sample);
            List<List<BoardSnapshot.Slot>> groups = List.of(snapshot.opponent().monsters(),
                snapshot.opponent().spells(), snapshot.opponent().hand(),
                snapshot.opponent().grave(), snapshot.opponent().banished(),
                snapshot.opponent().extra());
            for(int zone = 0; zone < groups.size(); zone++)
            {
                for(BoardSnapshot.Slot slot : groups.get(zone))
                {
                    if(slot.present() && slot.code() == 0)
                    {
                        concealed++;
                        perZone[zone]++;
                        if(slot.art() != 0)
                        {
                            leaks[zone]++;
                        }
                    }
                }
            }
        }
        StringBuilder census = new StringBuilder();
        for(int zone = 0; zone < names.length; zone++)
        {
            census.append("\n  ").append(names[zone]).append(": ").append(perZone[zone])
                .append(" concealed, ").append(leaks[zone]).append(" wearing artwork");
        }
        System.out.println("concealed opponent slots checked: " + concealed + census);
        for(int zone = 0; zone < names.length; zone++)
        {
            assertEquals(0, leaks[zone],
                "cards we may not identify arrived wearing artwork in " + names[zone] + census);
        }
        assertTrue(concealed > 0, "never saw a concealed card -- the test proved nothing");
        // A face-down monster is the whole point: an artwork index on one names
        // a handful of cards, and it is the only zone where a concealed card
        // sits in plain sight on the board.
        assertTrue(perZone[0] > 0,
            "no set monster was ever concealed -- the field half of this test proved nothing");
    }

    private static CdbCardProvider cardProvider;

    private static CdbCardProvider cards() throws Exception
    {
        if(cardProvider == null)
        {
            cardProvider = new CdbCardProvider(List.of(cdb()));
        }
        return cardProvider;
    }

    private HeadlessDuelRunner.Builder runner(Spy spy) throws Exception
    {
        return HeadlessDuelRunner.builder(OcgApi.load(lib()))
            .seed(new long[] {5, 15, 25, 35})
            .cards(cards())
            .scripts(HeadlessDuelRunner.cardScriptsDirectory(scripts()))
            .responder(0, spy)
            .responder(1, new RandomBot(9, cards().all()));
    }

    /** Every card of ours this view can identify, wherever it is. */
    private static List<CardView> ours(BoardState sample)
    {
        List<CardView> all = new ArrayList<>();
        for(List<CardView> group : List.of(sample.self().monsters(), sample.self().spells(),
            sample.self().hand(), sample.self().grave(), sample.self().banished()))
        {
            for(CardView card : group)
            {
                if(card != null && card.code() != 0)
                {
                    all.add(card);
                }
            }
        }
        return all;
    }
}
