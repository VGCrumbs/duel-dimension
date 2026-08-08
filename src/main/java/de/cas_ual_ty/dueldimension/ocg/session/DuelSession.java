package de.cas_ual_ty.dueldimension.ocg.session;

import de.cas_ual_ty.dueldimension.ocg.HeadlessDuelRunner;
import de.cas_ual_ty.dueldimension.ocg.OcgApi;
import de.cas_ual_ty.dueldimension.ocg.OcgConstants;
import de.cas_ual_ty.dueldimension.ocg.OcgDuel;
import de.cas_ual_ty.dueldimension.ocg.RawMessage;
import de.cas_ual_ty.dueldimension.ocg.ResponseSource;
import de.cas_ual_ty.dueldimension.ocg.query.BoardObserver;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * One running duel, driven on its own thread.
 * <p>
 * The engine must not run on the server tick thread: a single
 * {@code OCG_DuelProcess} call can chew through a long chain, and a duel
 * blocks while it waits for a player to answer a prompt. Each session
 * therefore owns a thread, and everything it wants to tell the game is queued
 * for the tick thread to drain via {@link #drainEvents}.
 * <p>
 * Fuzzing showed separate duel handles are safe to run concurrently, so a
 * server can host as many sessions as it has duels.
 */
public class DuelSession
{
    /** Something that happened in the duel, for the game thread to react to. */
    public sealed interface Event
    {
        record Message(RawMessage message) implements Event
        {
        }

        record Finished(HeadlessDuelRunner.DuelResult result, boolean completed, int steps) implements Event
        {
        }

        record Failed(String reason) implements Event
        {
        }

        /** A server-authoritative concession, distinct from an engine failure. */
        record Forfeited(int winner) implements Event
        {
        }

        /**
         * A fresh view of the field, captured on the duel thread — one per
         * seat, because a board is not a fact but a point of view.
         * <p>
         * Each snapshot is built from that seat's own {@code BoardObserver},
         * so a face-down card is blank in the snapshot its owner's opponent
         * receives and only in that one. With two human players this is the
         * whole of the concealment story: neither client is ever sent the
         * other's hidden information and then trusted not to look.
         * <p>
         * {@code self} and {@code opponent} are also swapped per seat, so each
         * player sees their own side nearest.
         */
        record Board(de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot seat0,
            de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot seat1) implements Event
        {
            /** The view belonging to one seat. */
            public de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot forSeat(int seat)
            {
                return seat == 0 ? seat0 : seat1;
            }
        }

        /**
         * The duel is waiting on the player, and this is the question. A
         * prompt travels in the same queue as the messages before it because
         * that is how the reference client stays ordered: EDOPro's select
         * messages are cases inside ClientAnalyze (duelclient.cpp:1702, 1778,
         * 1976), consumed from one stream, so a prompt can never be handled
         * before everything preceding it has been animated. Sending ours on a
         * side channel from the duel thread let it overtake the event stream.
         */
        record Prompt(de.cas_ual_ty.dueldimension.ocg.prompt.EnginePrompt prompt, int serial, int seat)
            implements Event
        {
        }
    }

    private final String id;
    private final HeadlessDuelRunner runner;
    private final ConcurrentLinkedQueue<Event> events = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean running = new AtomicBoolean();
    private final Object terminationLock = new Object();
    /** Guarded by {@link #terminationLock}; -1 when no concession was accepted. */
    private int forcedWinner = -1;
    private volatile Thread thread;
    private volatile HeadlessDuelRunner.DuelTrace trace;

    private DuelSession(String id, HeadlessDuelRunner runner)
    {
        this.id = id;
        this.runner = runner;
    }

    /**
     * Wires a duel between two responders. Each responder is wrapped so that
     * every message it observes is also queued as an {@link Event}, which is
     * how the game thread learns what happened without touching the engine.
     */
    public static DuelSession create(String id, OcgApi api, long flags, long[] seed,
        OcgDuel.CardProvider cards, OcgDuel.ScriptProvider scripts,
        HeadlessDuelRunner.Deck deck0, HeadlessDuelRunner.Deck deck1,
        ResponseSource player0, ResponseSource player1)
    {
        DuelSession[] holder = new DuelSession[1];
        SeatView seat1 = new SeatView(player1);

        HeadlessDuelRunner runner = HeadlessDuelRunner.builder(api)
            .seed(seed)
            .flags(flags)
            .cards(cards)
            .scripts(scripts)
            .deck(0, deck0)
            .deck(1, deck1)
            // Seat 1 is wrapped only to capture its observer: the message tap
            // and the checkpoint trigger stay on seat 0, so the stream is
            // produced once and stays single-threaded and ordered.
            .responder(1, seat1)
            .responder(0, new Relay(player0, seat1,
                message -> holder[0].events.add(new Event.Message(message)),
                board -> holder[0].events.add(board)))
            .build();

        holder[0] = new DuelSession(id, runner);
        return holder[0];
    }

    /**
     * Posts the prompt the duel is now blocked on. Must be called from the
     * duel thread (it is: HumanResponseSource.respond runs there), so it lands
     * in the queue after every message that led up to it.
     */
    public void postPrompt(de.cas_ual_ty.dueldimension.ocg.prompt.EnginePrompt prompt, int serial, int seat)
    {
        events.add(new Event.Prompt(prompt, serial, seat));
    }

    /** Starts the duel thread. Returns immediately. */
    public void start()
    {
        if(!running.compareAndSet(false, true))
        {
            throw new IllegalStateException("Session " + id + " already started");
        }
        thread = new Thread(() ->
        {
            Throwable failure = null;
            try
            {
                trace = runner.run(200000);
            }
            catch(Throwable e)
            {
                failure = e;
            }
            Event terminal;
            synchronized(terminationLock)
            {
                // Publish the stopped state before its terminal event. The
                // server may drain immediately after the add below; it must
                // never observe "finished" while isRunning still says true.
                running.set(false);
                if(trace != null && trace.completed && trace.result != null)
                {
                    terminal = new Event.Finished(trace.result, true, trace.steps);
                }
                else if(forcedWinner >= 0)
                {
                    terminal = new Event.Forfeited(forcedWinner);
                }
                else if(failure != null)
                {
                    terminal = new Event.Failed(failure.toString());
                }
                else
                {
                    terminal = new Event.Finished(trace == null ? null : trace.result,
                        false, trace == null ? 0 : trace.steps);
                }
            }
            events.add(terminal);
        }, "duel-" + id);
        thread.setDaemon(true);
        thread.start();
    }

    /** Called from the game thread; hands over everything queued since last time. */
    public void drainEvents(Consumer<Event> consumer)
    {
        Event event;
        while((event = events.poll()) != null)
        {
            consumer.accept(event);
        }
    }

    public boolean isRunning()
    {
        return running.get();
    }

    public String id()
    {
        return id;
    }

    public HeadlessDuelRunner.DuelTrace trace()
    {
        return trace;
    }

    /** Asks the duel thread to stop; the run aborts at its next prompt. */
    public void stop()
    {
        Thread current = thread;
        if(current != null)
        {
            current.interrupt();
        }
    }

    /**
     * Ends this session as a concession and emits exactly one ordered terminal
     * event once the engine thread has unwound.
     *
     * @return true only for the request which actually claimed the finish
     */
    public boolean forfeit(int winner)
    {
        if(winner < 0 || winner > 1)
        {
            throw new IllegalArgumentException("Winner seat must be 0 or 1");
        }
        Thread current;
        synchronized(terminationLock)
        {
            if(!running.get() || forcedWinner >= 0)
            {
                return false;
            }
            forcedWinner = winner;
            current = thread;
        }
        if(current != null)
        {
            current.interrupt();
        }
        return true;
    }

    /**
     * Passes a responder through untouched while copying its message stream
     * out, and captures board snapshots after state-changing messages — on
     * the duel thread, the only thread allowed to query the core.
     */
    /**
     * Passes a responder through untouched, keeping hold of the
     * {@link BoardObserver} the runner hands it. That observer is the only way
     * to build this seat's honest view, and the runner gives it to the
     * responder rather than to us.
     */
    private static final class SeatView implements ResponseSource
    {
        private final ResponseSource inner;
        private BoardObserver board;

        private SeatView(ResponseSource inner)
        {
            this.inner = inner;
        }

        @Override
        public void onDuelStart(int playerIndex, BoardObserver board)
        {
            this.board = board;
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
            return inner.respond(prompt);
        }

        @Override
        public void onDuelEnd(HeadlessDuelRunner.DuelResult result)
        {
            inner.onDuelEnd(result);
        }
    }

    private static final class Relay implements ResponseSource
    {
        private final ResponseSource inner;
        private final Consumer<RawMessage> tap;
        private final Consumer<Event.Board> boardTap;
        private final SeatView other;
        private BoardObserver board;
        private int turn;
        private int phase;
        /** The seat whose turn it is, in the core's own numbering. */
        private int turnPlayer;
        private int seat;

        private Relay(ResponseSource inner, SeatView other, Consumer<RawMessage> tap,
            Consumer<Event.Board> boardTap)
        {
            this.inner = inner;
            this.other = other;
            this.tap = tap;
            this.boardTap = boardTap;
        }

        @Override
        public void onDuelStart(int playerIndex, BoardObserver board)
        {
            seat = playerIndex;
            this.board = board;
            inner.onDuelStart(playerIndex, board);
        }

        @Override
        public void observe(RawMessage message)
        {
            tap.accept(message);
            inner.observe(message);

            switch(message.type())
            {
                case OcgConstants.MSG_NEW_TURN ->
                {
                    turn++;
                    // Kept absolute here and made relative per seat below: the
                    // two players disagree about whose turn "0" is.
                    turnPlayer = message.payload().length > 0 ? message.payload()[0] & 0xFF : turnPlayer;
                    snapshot();
                }
                case OcgConstants.MSG_NEW_PHASE ->
                {
                    phase = message.payload().length >= 2
                        ? (message.payload()[0] & 0xFF) | ((message.payload()[1] & 0xFF) << 8) : phase;
                    snapshot();
                }
                // Anything that changes what a card looks like has to produce a
                // checkpoint, not just anything that moves one. A flip summon
                // and a battle-position change alter only the card's position,
                // so with these missing the client was never told: the card sat
                // face down until some later, unrelated snapshot corrected it.
                case OcgConstants.MSG_MOVE, OcgConstants.MSG_DAMAGE, OcgConstants.MSG_RECOVER,
                    OcgConstants.MSG_DRAW, OcgConstants.MSG_WIN,
                    OcgConstants.MSG_POS_CHANGE, OcgConstants.MSG_FLIPSUMMONING,
                    OcgConstants.MSG_SET, OcgConstants.MSG_SUMMONING,
                    OcgConstants.MSG_SPSUMMONING, OcgConstants.MSG_SWAP,
                    OcgConstants.MSG_CHAINING,
                    // Stat changes land when a chain RESOLVES, and the battle
                    // message carries the values damage was computed from; a
                    // board that only refreshed later showed a boosted card at
                    // its old attack while the battle played out.
                    OcgConstants.MSG_CHAIN_SOLVED, OcgConstants.MSG_CHAIN_END,
                    OcgConstants.MSG_BATTLE,
                    // Life changes are board state too: without these the bars
                    // held their old numbers until something else moved.
                    OcgConstants.MSG_PAY_LPCOST, OcgConstants.MSG_LPUPDATE -> snapshot();
                default ->
                {
                }
            }
        }

        private void snapshot()
        {
            if(board == null || boardTap == null)
            {
                return;
            }
            de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot mine =
                de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot.of(
                    board.observe(), turn, phase, turnPlayer == seat ? 0 : 1);
            // Seat 1's view is built from ITS observer, never derived from
            // seat 0's: deriving it would mean reading, and then hiding,
            // information that must not cross in the first place.
            de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot theirs =
                other != null && other.board != null
                    ? de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot.of(
                        other.board.observe(), turn, phase, turnPlayer == 1 ? 0 : 1)
                    : mine;
            boardTap.accept(new Event.Board(mine, theirs));
        }

        @Override
        public byte[] respond(RawMessage prompt)
        {
            if(Thread.currentThread().isInterrupted())
            {
                return null; // aborts the run
            }
            return inner.respond(prompt);
        }

        @Override
        public void onDuelEnd(HeadlessDuelRunner.DuelResult result)
        {
            inner.onDuelEnd(result);
        }
    }

    /** Convenience: is this message one that ends the duel? */
    public static boolean isTerminal(RawMessage message)
    {
        return message.type() == OcgConstants.MSG_WIN;
    }
}
