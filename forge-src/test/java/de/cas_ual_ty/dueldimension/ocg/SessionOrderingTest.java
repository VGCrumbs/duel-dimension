package de.cas_ual_ty.dueldimension.ocg;

import de.cas_ual_ty.dueldimension.ocg.bot.HeuristicBot;
import de.cas_ual_ty.dueldimension.ocg.deck.StarterDecks;
import de.cas_ual_ty.dueldimension.ocg.session.DuelSession;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Guards the order a duel is reported in.
 * <p>
 * A session emits board snapshots interleaved with the messages that caused
 * them, and the client replays that sequence: events animate, then the board
 * they produced is applied. Collapsing a drain into "all the events plus the
 * last board" breaks it — when a batch ends on a message, the board sent
 * alongside predates the events sent alongside, and the field jumps backwards
 * until the next batch corrects it. That looked like the duel happening wildly
 * out of order.
 * <p>
 * This pins the property that makes the replay correct: every board checkpoint
 * comes directly after the message it was taken for, so a consumer that keeps
 * the interleaving can always pair events with the board that follows them.
 */
class SessionOrderingTest
{
    /** DuelSession.Relay snapshots after exactly these. */
    private static final int[] SNAPSHOT_AFTER = {
        OcgConstants.MSG_MOVE, OcgConstants.MSG_DAMAGE, OcgConstants.MSG_RECOVER,
        OcgConstants.MSG_DRAW, OcgConstants.MSG_WIN,
        OcgConstants.MSG_NEW_TURN, OcgConstants.MSG_NEW_PHASE,
        OcgConstants.MSG_POS_CHANGE, OcgConstants.MSG_FLIPSUMMONING,
        OcgConstants.MSG_SET, OcgConstants.MSG_SUMMONING,
        OcgConstants.MSG_SPSUMMONING, OcgConstants.MSG_SWAP, OcgConstants.MSG_CHAINING,
        OcgConstants.MSG_CHAIN_SOLVED, OcgConstants.MSG_CHAIN_END, OcgConstants.MSG_BATTLE,
        OcgConstants.MSG_PAY_LPCOST, OcgConstants.MSG_LPUPDATE
    };

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

    private static boolean snapshots(int type)
    {
        for(int candidate : SNAPSHOT_AFTER)
        {
            if(candidate == type)
            {
                return true;
            }
        }
        return false;
    }

    @Test
    void everyBoardCheckpointFollowsTheMessageThatCausedIt() throws Exception
    {
        assumeTrue(Files.isRegularFile(lib()), "native core not present");
        assumeTrue(Files.isRegularFile(scripts().resolve("constant.lua")), "CardScripts not present");
        assumeTrue(Files.isRegularFile(cdb()), "cards.cdb not present");

        OcgApi api = OcgApi.load(lib());
        CdbCardProvider cards = new CdbCardProvider(List.of(cdb()));

        DuelSession session = DuelSession.create("ordering", api, OcgConstants.DUEL_MODE_MR5,
            new long[] {99991, 12347, 5551, 4242},
            cards, HeadlessDuelRunner.cardScriptsDirectory(scripts()),
            StarterDecks.YUGI.load().toRunnerDeck(), StarterDecks.JOEY.load().toRunnerDeck(),
            new HeuristicBot(31337, cards, cards.all()),
            new HeuristicBot(7777, cards, cards.all()));

        session.start();
        List<DuelSession.Event> seen = new ArrayList<>();
        long deadline = System.nanoTime() + 60_000_000_000L;
        while(session.isRunning() && System.nanoTime() < deadline)
        {
            session.drainEvents(seen::add);
            Thread.sleep(5);
        }
        session.drainEvents(seen::add);

        int boards = 0;
        int messages = 0;
        for(int i = 0; i < seen.size(); i++)
        {
            if(!(seen.get(i) instanceof DuelSession.Event.Board))
            {
                if(seen.get(i) instanceof DuelSession.Event.Message)
                {
                    messages++;
                }
                continue;
            }
            boards++;
            assertTrue(i > 0, "a board checkpoint arrived before any message");
            DuelSession.Event previous = seen.get(i - 1);
            assertTrue(previous instanceof DuelSession.Event.Message message
                    && snapshots(message.message().type()),
                "board checkpoint at index " + i + " does not follow a snapshotting message; "
                    + "pairing events with the board that follows them is no longer safe");
        }

        System.out.println("ordering: " + messages + " messages, " + boards + " board checkpoints");
        assertTrue(messages > 100, "duel produced too few messages to be meaningful: " + messages);
        assertTrue(boards > 10, "duel produced too few board checkpoints: " + boards);
    }
}
