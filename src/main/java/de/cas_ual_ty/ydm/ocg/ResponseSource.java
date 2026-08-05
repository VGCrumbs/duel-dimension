package de.cas_ual_ty.ydm.ocg;

/**
 * A thing that plays one side of a duel: a bot, a human GUI bridge, or a
 * scripted test responder. The engine loop cannot tell implementations apart.
 * <p>
 * Implementations receive the <em>entire</em> message stream via
 * {@link #observe} (fuel for memory, dialogue triggers, state tracking), and
 * are asked to answer only the prompts directed at their player index.
 * <p>
 * Hidden information: {@code observe} currently receives raw messages as the
 * core emits them to an omniscient observer. Once per-player visibility
 * filtering exists (Phase 3), the runner will filter the stream per player;
 * bot implementations must not depend on hidden payload contents.
 */
public interface ResponseSource
{
    /**
     * Called once before the first {@link OcgDuel#process()} call.
     *
     * @param board a window onto the field for this player; honest by default
     */
    default void onDuelStart(int playerIndex, de.cas_ual_ty.ydm.ocg.query.BoardObserver board)
    {
    }

    /** Called for every message the core emits, in order, including prompts. */
    default void observe(RawMessage message)
    {
    }

    /**
     * Answers the prompt the core is AWAITING, in the core's response
     * encoding for that message type. Returning null aborts the duel run
     * (no legal responder available) — the runner treats that as a failed
     * run, never as a pass.
     */
    byte[] respond(RawMessage prompt);

    /** Called once after the duel ends or the run aborts; result may be null on abort. */
    default void onDuelEnd(HeadlessDuelRunner.DuelResult result)
    {
    }
}
