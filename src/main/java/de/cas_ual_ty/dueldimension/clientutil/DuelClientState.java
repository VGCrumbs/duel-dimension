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
    /**
     * When the duel ended, so the result screen can hold for its five seconds
     * and then hand the player back to the world. 0 while a duel is running.
     */
    public static volatile long overSince;
    /** True when the viewer won; only meaningful once {@link #over}. */
    public static volatile boolean won;
    /** This player's chosen mat; kept between duels so a choice sticks. */
    public static volatile PlayMats selfMat = PlayMats.CLASSIC;
    /** The mat the other duelist brought, as the server reported it. */
    public static volatile PlayMats opponentMat = PlayMats.CLASSIC;
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

    /**
     * The duel's playback, which belongs to the duel and not to the screen.
     * <p>
     * EDOPro runs message playback on a dedicated parsing thread
     * ({@code DuelClient::parsing_thread} draining {@code to_analyze}), which
     * blocks on WaitFrameSignal and keeps going regardless of what is on
     * screen. Ours used to live on EngineDuelScreen and only advanced inside
     * render(), so closing the screen stopped playback dead and reopening it
     * built a fresh queue -- losing every board commit still waiting in the old
     * one, which froze the field on a stale snapshot, and dumping whatever had
     * piled up in one burst. Hence "the opponent spams things and they
     * disappear". Keeping it here means playback survives the screen.
     */
    public static final DuelAnimations animations = new DuelAnimations();

    /**
     * Advances playback. Called every client tick, not from render, so a duel
     * keeps playing at the right pace whether or not its screen is open.
     */
    public static void tickPlayback()
    {
        long now = System.currentTimeMillis();
        synchronized(DuelClientState.class)
        {
            while(!pending.isEmpty())
            {
                PendingUpdate update = pending.poll();
                BoardSnapshot settled = update.board();
                animations.accept(update.events(), () ->
                {
                    if(settled != null)
                    {
                        board = settled;
                    }
                });
            }
        }
        animations.tick(now);
    }

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
        overSince = 0;
        won = false;
        // selfMat is the player's own preference and outlives a duel.
        opponentMat = PlayMats.CLASSIC;
        result = "";
        log.clear();
        pending.clear();
        animations.clear();
    }
}
