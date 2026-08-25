package de.cas_ual_ty.dueldimension.ocg;

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
    default void onDuelStart(int playerIndex, de.cas_ual_ty.dueldimension.ocg.query.BoardObserver board)
    {
    }

    /** Called for every message the core emits, in order, including prompts. */
    default void observe(RawMessage message)
    {
    }

    /**
     * The core refused the last answer and is asking the same question again.
     * <p>
     * The core validates a response itself and emits MSG_RETRY when it does not
     * accept one -- a tribute summon short a tribute, a sum that does not add
     * up. A player who is simply asked again with no explanation would think
     * the click was dropped, so this exists for the human path to say what
     * happened. A bot needs nothing: it will be asked again regardless.
     */
    default void onAnswerRejected()
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
