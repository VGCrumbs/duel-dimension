package de.cas_ual_ty.dueldimension.ocg.session;

import de.cas_ual_ty.dueldimension.ocg.CdbCardProvider;
import de.cas_ual_ty.dueldimension.ocg.HeadlessDuelRunner;
import de.cas_ual_ty.dueldimension.ocg.OcgApi;
import de.cas_ual_ty.dueldimension.ocg.OcgConstants;
import de.cas_ual_ty.dueldimension.ocg.bot.executor.Duelists;
import de.cas_ual_ty.dueldimension.ocg.bot.executor.ExecutorBot;
import de.cas_ual_ty.dueldimension.ocg.deck.StarterDecks;
import de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot;
import de.cas_ual_ty.dueldimension.ocg.query.CardView;
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
 * Letting a player look through their own deck means asking the engine a
 * question it answers honestly and dangerously: {@code OCG_DuelQueryLocation}
 * walks {@code list_main} front to back, and {@code operations.cpp} draws
 * {@code list_main.back()}, so the last element of an untouched deck query is
 * the very next card that player will draw.
 * <p>
 * {@code BoardObserver.ownDeck} is the one place that is allowed to see that,
 * and it de-orders the list before returning it. This test runs a real duel
 * against the native core and holds the result to three claims:
 * <ul>
 * <li>the list is in canonical (code, art) order, which makes it a function of
 * the deck's MULTISET alone — there is no engine order left in it to read, top
 * card included;</li>
 * <li>it is exactly as long as the {@code deckCount} captured beside it, so the
 * panel cannot contradict the number printed on its own label;</li>
 * <li>each seat's list is its own deck, never the other's.</li>
 * </ul>
 * The first claim is checked rather than sampled deliberately. "The top card
 * was not leaked this time" is a statistic; "the order is a pure function of
 * the multiset" is a proof, and it covers every position at once.
 */
class OwnDeckViewTest
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
    void eachSeatGetsItsOwnDeckDeOrderedAndComplete() throws Exception
    {
        assumeTrue(Files.isRegularFile(lib()), "native core not present");
        assumeTrue(Files.isRegularFile(scripts().resolve("constant.lua")), "CardScripts not present");
        assumeTrue(Files.isRegularFile(cdb()), "cards.cdb not present");

        OcgApi api = OcgApi.load(lib());
        CdbCardProvider cards = new CdbCardProvider(List.of(cdb()));

        HeadlessDuelRunner.Deck yugi = StarterDecks.YUGI.load().toRunnerDeck();
        HeadlessDuelRunner.Deck joey = StarterDecks.JOEY.load().toRunnerDeck();
        // The two decks have to actually differ, or "seat 1 did not get seat 0's
        // deck" is a claim about two identical lists and proves nothing.
        Set<Integer> onlyYugi = new HashSet<>(yugi.main());
        onlyYugi.removeAll(new HashSet<>(joey.main()));
        Set<Integer> onlyJoey = new HashSet<>(joey.main());
        onlyJoey.removeAll(new HashSet<>(yugi.main()));
        assumeTrue(!onlyYugi.isEmpty() && !onlyJoey.isEmpty(),
            "the two starter decks share every card; nothing to tell apart");

        DuelSession session = DuelSession.create("own-deck", api, OcgConstants.DUEL_MODE_MR5,
            new long[] {2718, 314159, 1618, 4142},
            cards, HeadlessDuelRunner.cardScriptsDirectory(scripts()),
            yugi, joey,
            new ExecutorBot(1, Duelists.forProfile("yugi"), cards, cards.all()),
            new ExecutorBot(2, Duelists.forProfile("joey"), cards, cards.all()));

        session.start();
        List<DuelSession.Event.Board> boards = new ArrayList<>();
        long deadline = System.nanoTime() + 60_000_000_000L;
        while(session.isRunning() && System.nanoTime() < deadline && boards.size() < 200)
        {
            session.drainEvents(event ->
            {
                if(event instanceof DuelSession.Event.Board board)
                {
                    boards.add(board);
                }
            });
            Thread.sleep(5);
        }

        assumeTrue(boards.size() > 10, "not enough checkpoints to judge: " + boards.size());

        int checked = 0;
        int cardsSeen = 0;
        List<String> disorder = new ArrayList<>();
        List<String> miscount = new ArrayList<>();
        List<String> crossed = new ArrayList<>();

        for(DuelSession.Event.Board board : boards)
        {
            for(int seat = 0; seat < 2; seat++)
            {
                List<CardView> deck = board.deckForSeat(seat);
                BoardSnapshot snapshot = board.forSeat(seat);
                if(deck == null || snapshot == null)
                {
                    continue;
                }
                checked++;
                cardsSeen += deck.size();

                // Canonical order: (code, then art) never decreasing. An engine
                // order surviving here would be a shuffled deck, so this fails
                // instantly if the sort is ever dropped.
                for(int i = 1; i < deck.size(); i++)
                {
                    CardView previous = deck.get(i - 1);
                    CardView current = deck.get(i);
                    boolean ordered = previous.code() < current.code()
                        || (previous.code() == current.code() && previous.art() <= current.art());
                    if(!ordered)
                    {
                        disorder.add("seat " + seat + " at index " + i + ": "
                            + previous.code() + "/" + previous.art() + " then "
                            + current.code() + "/" + current.art());
                    }
                }

                // The list and the count come from one instant, so they agree.
                if(deck.size() != snapshot.self().deckCount())
                {
                    miscount.add("seat " + seat + ": " + deck.size() + " cards listed, "
                        + snapshot.self().deckCount() + " counted");
                }

                // And it is this seat's deck. A card only the other player owns
                // turning up here is the whole failure this feature could have.
                Set<Integer> foreign = seat == 0 ? onlyJoey : onlyYugi;
                for(CardView card : deck)
                {
                    if(foreign.contains(card.code()))
                    {
                        crossed.add("seat " + seat + " was shown card " + card.code()
                            + ", which only the other seat owns");
                    }
                }
            }
        }

        System.out.println("own-deck checkpoints checked: " + checked);
        System.out.println("  card entries inspected: " + cardsSeen);
        System.out.println("  out-of-canonical-order pairs: " + disorder.size());
        System.out.println("  list/count disagreements: " + miscount.size());
        System.out.println("  cards from the other seat's deck: " + crossed.size());

        assertTrue(disorder.isEmpty(), "a deck list carried engine order: "
            + disorder.subList(0, Math.min(5, disorder.size())));
        assertTrue(miscount.isEmpty(), "the deck list and the deck count disagree: "
            + miscount.subList(0, Math.min(5, miscount.size())));
        assertTrue(crossed.isEmpty(), "a seat was shown the other seat's cards: "
            + crossed.subList(0, Math.min(5, crossed.size())));
        // Guard against passing on empty lists: a feature that returns nothing
        // satisfies every rule above and shows the player nothing.
        assertTrue(cardsSeen > 0, "no deck card was ever returned -- the test proved nothing");
    }

    /**
     * The deck query must not drag positional fields along for the ride.
     * <p>
     * {@code QueryParser.BOARD_FLAGS} asks for QUERY_EQUIP_CARD, which arrives
     * as a loc_info of controller, location and sequence. DECK_FLAGS asks for a
     * passcode and an artwork, so an equip — the one field in a CardView that
     * can carry a position — must always be absent here.
     */
    @Test
    void nothingPositionalRidesAlongWithTheDeck() throws Exception
    {
        assumeTrue(Files.isRegularFile(lib()), "native core not present");
        assumeTrue(Files.isRegularFile(scripts().resolve("constant.lua")), "CardScripts not present");
        assumeTrue(Files.isRegularFile(cdb()), "cards.cdb not present");

        OcgApi api = OcgApi.load(lib());
        CdbCardProvider cards = new CdbCardProvider(List.of(cdb()));

        DuelSession session = DuelSession.create("own-deck-flags", api, OcgConstants.DUEL_MODE_MR5,
            new long[] {9001, 4242, 777, 13},
            cards, HeadlessDuelRunner.cardScriptsDirectory(scripts()),
            StarterDecks.YUGI.load().toRunnerDeck(), StarterDecks.JOEY.load().toRunnerDeck(),
            new ExecutorBot(3, Duelists.forProfile("yugi"), cards, cards.all()),
            new ExecutorBot(4, Duelists.forProfile("joey"), cards, cards.all()));

        session.start();
        List<DuelSession.Event.Board> boards = new ArrayList<>();
        long deadline = System.nanoTime() + 60_000_000_000L;
        while(session.isRunning() && System.nanoTime() < deadline && boards.size() < 60)
        {
            session.drainEvents(event ->
            {
                if(event instanceof DuelSession.Event.Board board)
                {
                    boards.add(board);
                }
            });
            Thread.sleep(5);
        }
        assumeTrue(!boards.isEmpty(), "no checkpoint arrived");

        int inspected = 0;
        for(DuelSession.Event.Board board : boards)
        {
            for(int seat = 0; seat < 2; seat++)
            {
                for(CardView card : board.deckForSeat(seat))
                {
                    inspected++;
                    assertEquals(null, card.equip(),
                        "a deck card arrived carrying an equip target, i.e. a "
                            + "controller/location/sequence");
                }
            }
        }
        System.out.println("deck cards checked for positional fields: " + inspected);
        assertTrue(inspected > 0, "no deck card was inspected");
    }
}
