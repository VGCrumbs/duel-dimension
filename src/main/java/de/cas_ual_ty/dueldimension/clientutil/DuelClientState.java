package de.cas_ual_ty.dueldimension.clientutil;

import de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot;
import de.cas_ual_ty.dueldimension.ocg.prompt.EnginePrompt;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The client's view of its running duel: latest board, log tail, and the
 * prompt currently awaiting an answer (null = waiting for the opponent).
 * One duel per client, so plain static state.
 */
public final class DuelClientState
{
    private static final int LOG_LIMIT = 60;

    public static volatile EnginePrompt prompt;
    public static volatile BoardSnapshot board = BoardSnapshot.EMPTY;
    public static volatile boolean over;
    public static volatile String result = "";
    public static final Deque<String> log = new ArrayDeque<>();

    private DuelClientState()
    {
    }

    public static synchronized void addLog(String line)
    {
        log.addLast(line);
        while(log.size() > LOG_LIMIT)
        {
            log.removeFirst();
        }
    }

    public static synchronized void reset()
    {
        prompt = null;
        board = BoardSnapshot.EMPTY;
        over = false;
        result = "";
        log.clear();
    }
}
