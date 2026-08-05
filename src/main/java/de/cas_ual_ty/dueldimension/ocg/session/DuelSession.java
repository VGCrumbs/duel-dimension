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

        /** A fresh view of the field for seat 0, captured on the duel thread. */
        record Board(de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot snapshot) implements Event
        {
        }
    }

    private final String id;
    private final HeadlessDuelRunner runner;
    private final ConcurrentLinkedQueue<Event> events = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean running = new AtomicBoolean();
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

        HeadlessDuelRunner runner = HeadlessDuelRunner.builder(api)
            .seed(seed)
            .flags(flags)
            .cards(cards)
            .scripts(scripts)
            .deck(0, deck0)
            .deck(1, deck1)
            .responder(0, new Relay(player0,
                message -> holder[0].events.add(new Event.Message(message)),
                snapshot -> holder[0].events.add(new Event.Board(snapshot))))
            .responder(1, player1)
            .build();

        holder[0] = new DuelSession(id, runner);
        return holder[0];
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
            try
            {
                trace = runner.run(200000);
                events.add(new Event.Finished(trace.result, trace.completed, trace.steps));
            }
            catch(Throwable e)
            {
                events.add(new Event.Failed(e.toString()));
            }
            finally
            {
                running.set(false);
            }
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
     * Passes a responder through untouched while copying its message stream
     * out, and captures board snapshots after state-changing messages — on
     * the duel thread, the only thread allowed to query the core.
     */
    private static final class Relay implements ResponseSource
    {
        private final ResponseSource inner;
        private final Consumer<RawMessage> tap;
        private final Consumer<de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot> boardTap;
        private BoardObserver board;
        private int turn;
        private int phase;
        private int turnPlayer;
        private int seat;

        private Relay(ResponseSource inner, Consumer<RawMessage> tap,
            Consumer<de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot> boardTap)
        {
            this.inner = inner;
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
                    turnPlayer = (message.payload().length > 0 && (message.payload()[0] & 0xFF) == seat) ? 0 : 1;
                    snapshot();
                }
                case OcgConstants.MSG_NEW_PHASE ->
                {
                    phase = message.payload().length >= 2
                        ? (message.payload()[0] & 0xFF) | ((message.payload()[1] & 0xFF) << 8) : phase;
                    snapshot();
                }
                case OcgConstants.MSG_MOVE, OcgConstants.MSG_DAMAGE, OcgConstants.MSG_RECOVER,
                    OcgConstants.MSG_DRAW, OcgConstants.MSG_WIN -> snapshot();
                default ->
                {
                }
            }
        }

        private void snapshot()
        {
            if(board != null && boardTap != null)
            {
                boardTap.accept(de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot.of(
                    board.observe(), turn, phase, turnPlayer));
            }
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
