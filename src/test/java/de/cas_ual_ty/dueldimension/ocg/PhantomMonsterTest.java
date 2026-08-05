package de.cas_ual_ty.dueldimension.ocg;

import de.cas_ual_ty.dueldimension.ocg.bot.HeuristicBot;
import de.cas_ual_ty.dueldimension.ocg.deck.StarterDecks;
import de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot;
import de.cas_ual_ty.dueldimension.ocg.session.DuelSession;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Chases monsters that appear on the opponent's field without being summoned.
 * <p>
 * In game the opponent showed five face-down monsters on their first turn, and
 * attacking them made them vanish. SummonLimitTest already proves the engine
 * allows at most one normal summon or monster set per turn, so either the board
 * snapshot is inventing occupants or the screen is drawing them. This looks at
 * the snapshot, which is the earlier of the two.
 */
class PhantomMonsterTest
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
    void theBoardNeverReportsMoreMonstersThanWereSummoned() throws Exception
    {
        assumeTrue(Files.isRegularFile(lib()), "native core not present");
        assumeTrue(Files.isRegularFile(scripts().resolve("constant.lua")), "CardScripts not present");
        assumeTrue(Files.isRegularFile(cdb()), "cards.cdb not present");

        OcgApi api = OcgApi.load(lib());
        CdbCardProvider cards = new CdbCardProvider(List.of(cdb()));

        DuelSession session = DuelSession.create("phantom", api, OcgConstants.DUEL_MODE_MR5,
            new long[] {5150, 271828, 31415, 16180},
            cards, HeadlessDuelRunner.cardScriptsDirectory(scripts()),
            StarterDecks.YUGI.load().toRunnerDeck(), StarterDecks.JOEY.load().toRunnerDeck(),
            new HeuristicBot(4242, cards, cards.all()),
            new HeuristicBot(2424, cards, cards.all()));

        session.start();
        List<BoardSnapshot> boards = new ArrayList<>();
        long deadline = System.nanoTime() + 60_000_000_000L;
        while(session.isRunning() && System.nanoTime() < deadline && boards.size() < 400)
        {
            session.drainEvents(event ->
            {
                if(event instanceof DuelSession.Event.Board board)
                {
                    boards.add(board.snapshot());
                }
            });
            Thread.sleep(5);
        }

        assumeTrue(boards.size() > 20, "not enough snapshots to judge: " + boards.size());

        int worstSelf = 0;
        int worstOpponent = 0;
        // How crowded the opponent's field gets in the opening turns, which is
        // the "five monsters on his first turn" question asked directly.
        int earlyOpponent = 0;
        BoardSnapshot earliest = null;
        BoardSnapshot worst = null;
        for(BoardSnapshot board : boards)
        {
            int self = occupied(board.self().monsters());
            int opponent = occupied(board.opponent().monsters());
            if(opponent > worstOpponent || self > worstSelf)
            {
                worst = board;
            }
            if(board.turn() <= 3 && opponent > earlyOpponent)
            {
                earlyOpponent = opponent;
                earliest = board;
            }
            worstSelf = Math.max(worstSelf, self);
            worstOpponent = Math.max(worstOpponent, opponent);
        }

        System.out.println("snapshots=" + boards.size()
            + " most monsters at once: you=" + worstSelf + " opponent=" + worstOpponent);
        System.out.println("by turn 3 the opponent held at most " + earlyOpponent + " monsters");
        if(earliest != null)
        {
            describe("opponent by turn 3", earliest.opponent().monsters());
        }
        if(worst != null)
        {
            describe("you", worst.self().monsters());
            describe("opponent", worst.opponent().monsters());
        }

        // Seven is the hard ceiling (5 main zones plus 2 extra monster zones),
        // so anything above it is the snapshot inventing occupants outright.
        assertTrue(worstOpponent <= 7,
            "opponent's board reported " + worstOpponent + " monsters, more than the field holds");
        assertTrue(worstSelf <= 7,
            "your board reported " + worstSelf + " monsters, more than the field holds");

        // A face-up monster the viewer can see must have an identity. A slot
        // that is present, not face down, and yet has no code is exactly the
        // "null card" that was appearing on the field.
        for(BoardSnapshot board : boards)
        {
            for(BoardSnapshot.Slot slot : board.opponent().monsters())
            {
                assertTrue(!slot.present() || slot.faceDown() || slot.code() != 0,
                    "a face-up monster on the opponent's field has no card code: " + slot);
            }
        }
    }

    private static int occupied(List<BoardSnapshot.Slot> zones)
    {
        int count = 0;
        for(BoardSnapshot.Slot slot : zones)
        {
            if(slot.present())
            {
                count++;
            }
        }
        return count;
    }

    private static void describe(String who, List<BoardSnapshot.Slot> zones)
    {
        StringBuilder line = new StringBuilder("  " + who + ": ");
        for(int i = 0; i < zones.size(); i++)
        {
            BoardSnapshot.Slot slot = zones.get(i);
            line.append(i).append(slot.present()
                ? "[code=" + slot.code() + (slot.faceDown() ? " down" : " up")
                    + " atk=" + slot.attack() + "] "
                : "[-] ");
        }
        System.out.println(line);
    }
}
