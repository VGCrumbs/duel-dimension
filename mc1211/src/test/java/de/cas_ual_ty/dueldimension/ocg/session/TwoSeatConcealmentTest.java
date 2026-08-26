package de.cas_ual_ty.dueldimension.ocg.session;

import de.cas_ual_ty.dueldimension.ocg.CdbCardProvider;
import de.cas_ual_ty.dueldimension.ocg.HeadlessDuelRunner;
import de.cas_ual_ty.dueldimension.ocg.OcgApi;
import de.cas_ual_ty.dueldimension.ocg.OcgConstants;
import de.cas_ual_ty.dueldimension.ocg.bot.executor.Duelists;
import de.cas_ual_ty.dueldimension.ocg.bot.executor.ExecutorBot;
import de.cas_ual_ty.dueldimension.ocg.deck.StarterDecks;
import de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Two human players need two different boards, and this proves they get them.
 * <p>
 * A board is not a fact but a point of view: the same face-down card is a known
 * card to its owner and a blank to the other player. With one human that
 * distinction never had to be made, because only one view was ever built. With
 * two, building one view and sending it to both would hand each player the
 * other's hidden information and rely on the client not to look at it.
 * <p>
 * So {@code Event.Board} carries a snapshot per seat, each built from that
 * seat's own {@code BoardObserver}. This test reads both and checks they
 * genuinely disagree in the direction they should, and never in the other.
 */
class TwoSeatConcealmentTest
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
    void eachSeatSeesItsOwnHandAndNotTheOthers() throws Exception
    {
        assumeTrue(Files.isRegularFile(lib()), "native core not present");
        assumeTrue(Files.isRegularFile(scripts().resolve("constant.lua")), "CardScripts not present");
        assumeTrue(Files.isRegularFile(cdb()), "cards.cdb not present");

        OcgApi api = OcgApi.load(lib());
        CdbCardProvider cards = new CdbCardProvider(List.of(cdb()));

        DuelSession session = DuelSession.create("two-seat", api, OcgConstants.DUEL_MODE_MR5,
            new long[] {5150, 271828, 31415, 16180},
            cards, HeadlessDuelRunner.cardScriptsDirectory(scripts()),
            StarterDecks.YUGI.load().toRunnerDeck(), StarterDecks.JOEY.load().toRunnerDeck(),
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

        int handsCompared = 0;
        int seat0KnewOwnHand = 0;
        int seat1KnewOwnHand = 0;
        List<String> leaks = new ArrayList<>();

        for(DuelSession.Event.Board board : boards)
        {
            BoardSnapshot zero = board.forSeat(0);
            BoardSnapshot one = board.forSeat(1);
            if(zero == null || one == null)
            {
                continue;
            }

            // Seat 0's own hand is seat 1's opponent hand, and vice versa.
            handsCompared++;
            seat0KnewOwnHand += known(zero.self().hand());
            seat1KnewOwnHand += known(one.self().hand());

            // The leak that matters: a card seat 0 holds must not arrive
            // identified in the packet seat 1 receives.
            int leakedToOne = known(one.opponent().hand());
            int leakedToZero = known(zero.opponent().hand());
            if(leakedToOne > 0)
            {
                leaks.add("seat 1 could identify " + leakedToOne + " of seat 0's hand cards");
            }
            if(leakedToZero > 0)
            {
                leaks.add("seat 0 could identify " + leakedToZero + " of seat 1's hand cards");
            }

            // And the two views must genuinely be built for different players:
            // each seat's own life total must sit on its own side.
            assertTrue(zero.self().lifePoints() == one.opponent().lifePoints(),
                "seat 0's own LP should be seat 1's opponent LP");
            assertTrue(zero.opponent().lifePoints() == one.self().lifePoints(),
                "seat 1's own LP should be seat 0's opponent LP");
        }

        System.out.println("two-seat views: " + handsCompared + " checkpoints compared");
        System.out.println("  seat 0 could identify its own hand at " + seat0KnewOwnHand + " card-slots");
        System.out.println("  seat 1 could identify its own hand at " + seat1KnewOwnHand + " card-slots");
        System.out.println("  hidden-information leaks: " + leaks.size());

        assertTrue(leaks.isEmpty(), "hidden information crossed seats: "
            + leaks.subList(0, Math.min(5, leaks.size())));
        // Guard against passing by building two EMPTY views: each seat must
        // actually be able to read its own hand, or the test proves nothing.
        assertTrue(seat0KnewOwnHand > 0, "seat 0 could not identify any of its own hand");
        assertTrue(seat1KnewOwnHand > 0, "seat 1 could not identify any of its own hand");
    }

    /** How many slots in this zone carry a real card identity. */
    private static int known(List<BoardSnapshot.Slot> zone)
    {
        int count = 0;
        for(BoardSnapshot.Slot slot : zone)
        {
            if(slot.present() && slot.code() != 0)
            {
                count++;
            }
        }
        return count;
    }
}
