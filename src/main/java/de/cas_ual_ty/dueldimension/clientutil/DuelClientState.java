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
    /**
     * Updates the screen has not played yet, each holding the events that
     * happened and the board they produced. The board is applied only once its
     * events have been animated, so the field never shows a card that has not
     * finished moving.
     */
    public record PendingUpdate(java.util.List<de.cas_ual_ty.dueldimension.ocg.prompt.DuelEvent> events,
        BoardSnapshot board)
    {
    }

    public static final Deque<PendingUpdate> pending = new ArrayDeque<>();

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

    /**
     * Asks the card-image pipeline for these cards now, rather than the first
     * time each is drawn. Requesting an image is what queues its download, so
     * without this a duel spends its first minutes showing placeholders.
     */
    public static void warmUpArt(int[] codes)
    {
        for(int code : codes)
        {
            de.cas_ual_ty.dueldimension.card.properties.Properties card =
                de.cas_ual_ty.dueldimension.DdDatabase.PROPERTIES_LIST.get((long)code);
            if(card != null)
            {
                DuelTextures.card(card, (byte)0, DuelTextures.FIELD_CARD_SIZE);
                DuelTextures.card(card, (byte)0, DuelTextures.PREVIEW_CARD_SIZE);
            }
        }
    }

    /** Everything visible on the board, so opponent cards are fetched on sight. */
    public static void warmUpBoard(BoardSnapshot snapshot)
    {
        java.util.List<java.util.List<BoardSnapshot.Slot>> groups = java.util.List.of(
            snapshot.self().monsters(), snapshot.self().spells(), snapshot.self().hand(),
            snapshot.self().grave(), snapshot.self().banished(), snapshot.self().extra(),
            snapshot.opponent().monsters(), snapshot.opponent().spells(),
            snapshot.opponent().grave(), snapshot.opponent().banished());
        java.util.List<Integer> codes = new java.util.ArrayList<>();
        groups.forEach(group -> group.forEach(slot ->
        {
            if(slot.present() && slot.code() != 0)
            {
                codes.add(slot.code());
            }
        }));
        warmUpArt(codes.stream().mapToInt(Integer::intValue).distinct().toArray());
    }

    public static synchronized void reset()
    {
        prompt = null;
        board = BoardSnapshot.EMPTY;
        over = false;
        result = "";
        log.clear();
        pending.clear();
    }
}
