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

    /**
     * The sleeve on the deck this player is duelling with, or the plain back.
     * <p>
     * Only ever this player's. The opponent's cards keep the standard back, so a
     * sleeve marks which side of the table is yours rather than restyling the
     * whole field.
     */
    public static volatile de.cas_ual_ty.dueldimension.card.CardSleevesType ownSleeve =
        de.cas_ual_ty.dueldimension.duel.profile.Sleeves.DEFAULT;

    /**
     * The sleeve on the OPPONENT's deck, or the plain back.
     * <p>
     * Kept apart from {@link #ownSleeve} rather than shared, so the two halves
     * of the field stay tellable apart: each side wears its own owner's sleeve,
     * which is both what a real table looks like and what keeps "mine" and
     * "theirs" legible when both players have sleeved up.
     */
    public static volatile de.cas_ual_ty.dueldimension.card.CardSleevesType opponentSleeve =
        de.cas_ual_ty.dueldimension.duel.profile.Sleeves.DEFAULT;
    /**
     * The player's own deck list, waiting for the screen to pick it up, or null.
     * <p>
     * The other piles open straight out of {@link #board} because their contents
     * are already in the board packet. A deck is not and must not be: it is
     * asked for, answered to one player, and arrives here. The screen takes it,
     * shows it and nulls this, so a list can never be shown twice by accident.
     */
    public static volatile java.util.List<BoardSnapshot.Slot> deckView;
    public static volatile String result = "";
    /** Server-calculated reward waiting behind the outcome stinger. */
    private static volatile de.cas_ual_ty.dueldimension.shop.DuelRewardMessages.Result reward;
    public static final Deque<String> log = new ArrayDeque<>();
    /**
     * Updates the screen has not played yet, each holding the events that
     * happened and the board they produced. The board is applied only once its
     * events have been animated, so the field never shows a card that has not
     * finished moving.
     */
    /**
     * One item of the duel's playback stream: an update (events plus the board
     * they produced), a prompt, or the result. All three ride the SAME queue
     * because the reference has exactly one: EDOPro's select messages are
     * cases inside ClientAnalyze, consumed from a single deque, so a prompt
     * cannot be examined before everything preceding it has been animated.
     * Setting the visible prompt straight from the packet handler let it jump
     * this queue -- the screen would ask about an attack the player had not
     * seen yet, because Screen.tick runs before ClientTickEvent.END drains
     * pending into the animator, so the "is playback busy" gate read an
     * animator that had not ingested the same batch's events.
     */
    public record PendingUpdate(java.util.List<de.cas_ual_ty.dueldimension.ocg.prompt.DuelEvent> events,
        BoardSnapshot board, de.cas_ual_ty.dueldimension.ocg.prompt.EnginePrompt promptToShow,
        int promptSerial, boolean over, boolean won, String result)
    {
        public static PendingUpdate ofUpdate(
            java.util.List<de.cas_ual_ty.dueldimension.ocg.prompt.DuelEvent> events, BoardSnapshot board)
        {
            return new PendingUpdate(events, board, null, 0, false, false, "");
        }

        public static PendingUpdate ofPrompt(de.cas_ual_ty.dueldimension.ocg.prompt.EnginePrompt prompt,
            int serial)
        {
            return new PendingUpdate(java.util.List.of(), null, prompt, serial, false, false, "");
        }

        public static PendingUpdate ofOver(boolean won, String result)
        {
            return new PendingUpdate(java.util.List.of(), null, null, 0, true, won, result);
        }
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
                PendingUpdate item = pending.poll();
                animations.accept(item.events(), () -> apply(item));
            }
        }
        animations.tick(now);
    }

    /**
     * Applies one stream item, in its place in the stream: the board first,
     * then a prompt or the result. Runs as a zero-length step in the playback
     * queue, so nothing here can be seen before its predecessors have played.
     */
    private static void apply(PendingUpdate item)
    {
        if(item.board() != null)
        {
            board = item.board();
        }
        if(item.promptToShow() != null)
        {
            promptSerial = item.promptSerial();
            prompt = item.promptToShow();
            promptShownAt = System.currentTimeMillis();
            over = false;
            openScreen();
        }
        if(item.over())
        {
            over = true;
            won = item.won();
            result = item.result();
            overSince = System.currentTimeMillis();
            prompt = null;
            promptShownAt = 0;
            // The duel is decided; the music has nothing left to carry.
            DuelMusic.stop();
            // A player may close the duel screen while waiting for the other
            // seat. The result is not optional, so bring it back for the
            // ordered outcome stinger and the reward that follows it.
            openScreen();
        }
        else
        {
            // Idempotent, so calling it for every update of the duel is the
            // whole of "start the music when a duel starts" -- there is no
            // separate began-a-duel event on the client to hang it off, and a
            // second call while it is already playing does nothing.
            DuelMusic.start();
        }
    }

    /**
     * Brings the duel screen up if the player closed it.
     * <p>
     * The second of the two openers, the other being
     * {@code ClientProxy.updateEngineDuel}. On a world board that one is
     * suppressed entirely -- an opponent's turn is watched by looking at the
     * board -- while this one still runs, because it is called for a PROMPT and
     * for the result, and a duel you cannot answer is not a duel.
     * <p>
     * That is deliberately an interim: phases 12 and 13 replace the prompt
     * screen with world-space targeting and a HUD, at which point this branches
     * too. Until then an overworld duel is played on the board and answered on
     * the screen, which is playable rather than half-built.
     */
    public static void openScreen()
    {
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        // 26.2 moved the current screen off Minecraft and onto its Gui, which
        // now owns it; Minecraft.screen is gone rather than renamed.
        if(!(minecraft.gui.screen() instanceof EngineDuelScreen))
        {
            // Gui.setScreen, not Minecraft.setScreenAndShow: the latter is that
        // plus a forced renderFrame, and this runs inside tickPlayback's lock
        // with the update batch half-drained -- it would paint a frame of a
        // half-applied board. Forge's setScreen forced no frame either.
        minecraft.gui.setScreen(new EngineDuelScreen());
        }
    }

    /** Serial of the prompt on screen, quoted back with its answer. */
    public static volatile int promptSerial;

    /**
     * When the prompt on screen went up, as the clock beside the phase bar
     * counts from. Stamped here rather than sent by the server because the
     * deadline is measured from the moment the question is asked, and this is
     * the ordered point where it becomes the question on screen -- a stamp
     * taken in the packet handler would start running while earlier events
     * were still playing out. Zero when nothing is being asked.
     */
    public static volatile long promptShownAt;

    /** Where the mat choice survives restarts. */
    private static java.nio.file.Path playMatFile()
    {
        // Same file name as the Forge tree's, so a mat chosen there is still
        // the mat here. getConfigDir() makes the folder as FMLPaths did, and
        // only throws when it cannot -- at which point nothing else saves
        // either, so there is nothing useful to do about it here.
        return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
            .resolve("dueldimension-playmat.txt");
    }

    /**
     * The mat colour. One greyscale mat texture is multiplied by this, so any
     * colour is possible without shipping a file per hue.
     */
    public static final int DEFAULT_MAT_COLOUR = 0x8FA3B8;
    private static volatile int matColour = DEFAULT_MAT_COLOUR;

    /** The other duelist's chosen colour, drawn on their half of the table. */
    public static volatile int opponentMatColour = DEFAULT_MAT_COLOUR;

    public static int matColour()
    {
        return matColour;
    }

    public static void setMatColour(int rgb)
    {
        matColour = rgb & 0xFFFFFF;
        savePlayMat();
    }

    /**
     * The colour as the id that travels to the server, which forwards it to the
     * other duelist so they draw your half of the table correctly. Prefixed so
     * the older named mats still parse.
     */
    public static String matColourId()
    {
        return String.format("custom:%06X", matColour);
    }

    /**
     * The deck the player duels with, as the server has it.
     * <p>
     * This returned the word "Starter" from when deck storage did not exist.
     * It does now, and the hub was printing a fixed string underneath a list
     * that marks a different deck as active.
     */
    public static String activeDeckName()
    {
        String active = de.cas_ual_ty.dueldimension.clientutil.hub.EditorState.profile()
            .activeDeck();
        return active == null || active.isEmpty() ? "none chosen" : active;
    }

    public static void savePlayMat()
    {
        try
        {
            java.nio.file.Files.writeString(playMatFile(), matColourId());
        }
        catch(java.io.IOException e)
        {
            // A cosmetic preference is not worth crashing over.
        }
    }

    /** Reads a stored id, tolerating both the new colour form and an old mat name. */
    public static int parseMatColour(String id, int fallback)
    {
        if(id == null)
        {
            return fallback;
        }
        String text = id.strip();
        if(text.startsWith("custom:"))
        {
            try
            {
                return Integer.parseInt(text.substring(7), 16) & 0xFFFFFF;
            }
            catch(NumberFormatException malformed)
            {
                return fallback;
            }
        }
        // An id saved before the picker existed names one of the old mats; its
        // accent is the closest thing it had to a colour.
        PlayMats named = PlayMats.byId(text);
        return named == null ? fallback : named.accent();
    }

    static
    {
        try
        {
            java.nio.file.Path file = playMatFile();
            if(java.nio.file.Files.isRegularFile(file))
            {
                String stored = java.nio.file.Files.readString(file).strip();
                matColour = parseMatColour(stored, DEFAULT_MAT_COLOUR);
            }
        }
        catch(java.io.IOException e)
        {
        }
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

    public static synchronized void acceptReward(
        de.cas_ual_ty.dueldimension.shop.DuelRewardMessages.Result result)
    {
        reward = result;
    }

    public static synchronized de.cas_ual_ty.dueldimension.shop.DuelRewardMessages.Result
        takeReward()
    {
        de.cas_ual_ty.dueldimension.shop.DuelRewardMessages.Result result = reward;
        reward = null;
        return result;
    }

    public static synchronized boolean hasReward()
    {
        return reward != null;
    }

    /**
     * Asks the card-image pipeline for these cards now, rather than the first
     * time each is drawn. Requesting an image is what queues its download, so
     * without this a duel spends its first minutes showing placeholders.
     * <p>
     * <b>Asks {@link ImageHandler} and not {@link DuelTextures}.</b> The
     * pipeline nudge is the whole point of warming; the Identifier is not, and
     * DuelTextures records it with {@link CardTextureCache} as it builds it.
     * That marked every card in the duel resident without a texture existing —
     * so the first-sighting ration was already open when the board finally drew
     * them, and the cache's byte count was carrying a megabyte per card that
     * was never on the GPU.
     */
    public static void warmUpArt(int[] codes)
    {
        for(int code : codes)
        {
            de.cas_ual_ty.dueldimension.card.properties.Properties card =
                de.cas_ual_ty.dueldimension.DdDatabase.PROPERTIES_LIST.get((long)code);
            if(card != null)
            {
                ImageHandler.getReplacementImage(card, (byte)0, DuelTextures.FIELD_CARD_SIZE);
                ImageHandler.getReplacementImage(card, (byte)0, DuelTextures.PREVIEW_CARD_SIZE);
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
        // A copy wearing chosen artwork needs THAT file fetched, not the
        // printed one -- the pre-duel warm-up sends passcodes only, so this is
        // where an alternate artwork first gets asked for. Kept separate from
        // the code list so the ordinary card still costs one distinct() entry
        // and no extra request.
        java.util.List<BoardSnapshot.Slot> dressed = new java.util.ArrayList<>();
        groups.forEach(group -> group.forEach(slot ->
        {
            if(slot.present() && slot.code() != 0)
            {
                codes.add(slot.code());
                if(slot.art() != 0)
                {
                    dressed.add(slot);
                }
            }
        }));
        warmUpArt(codes.stream().mapToInt(Integer::intValue).distinct().toArray());
        for(BoardSnapshot.Slot slot : dressed)
        {
            de.cas_ual_ty.dueldimension.card.properties.Properties card =
                de.cas_ual_ty.dueldimension.DdDatabase.PROPERTIES_LIST.get((long)slot.code());
            if(card != null)
            {
                byte art = DuelTextures.artIndex(card, slot.art());
                // Warming, not drawing -- see warmUpArt.
                ImageHandler.getReplacementImage(card, art, DuelTextures.FIELD_CARD_SIZE);
                ImageHandler.getReplacementImage(card, art, DuelTextures.PREVIEW_CARD_SIZE);
            }
        }
    }

    public static synchronized void reset()
    {
        prompt = null;
        promptShownAt = 0;
        board = BoardSnapshot.EMPTY;
        over = false;
        overSince = 0;
        won = false;
        // selfMat is the player's own preference and outlives a duel.
        opponentMat = PlayMats.CLASSIC;
        // Cleared with the rest of the duel, so the next one does not inherit
        // the sleeve of the deck that played the last.
        ownSleeve = de.cas_ual_ty.dueldimension.duel.profile.Sleeves.DEFAULT;
        opponentSleeve = de.cas_ual_ty.dueldimension.duel.profile.Sleeves.DEFAULT;
        // For the same reason as the sleeve above: a deck list left here would
        // be the PREVIOUS duel's deck, opening itself over the next one.
        deckView = null;
        result = "";
        reward = null;
        log.clear();
        pending.clear();
        animations.clear();
        // Whatever is left of a duel is being thrown away, including its music.
        DuelMusic.stop();
    }
}
