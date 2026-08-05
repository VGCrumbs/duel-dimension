package de.cas_ual_ty.dueldimension.ocg.prompt;

import de.cas_ual_ty.dueldimension.ocg.HeadlessDuelRunner;
import de.cas_ual_ty.dueldimension.ocg.RawMessage;
import de.cas_ual_ty.dueldimension.ocg.ResponseSource;
import de.cas_ual_ty.dueldimension.ocg.msg.DuelMessage;
import de.cas_ual_ty.dueldimension.ocg.query.BoardObserver;

import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * A seat played by a person. To the engine it is just another
 * {@link ResponseSource}; the difference is that answering takes a round trip
 * to a client instead of a few microseconds of thinking.
 * <p>
 * The duel thread blocks here while the player decides, which is exactly why
 * duels do not run on the server tick thread. A timeout keeps a disconnected
 * or idle player from pinning a thread forever — the duel aborts rather than
 * leaking.
 */
public class HumanResponseSource implements ResponseSource
{
    /** How long a player may think before the duel gives up on them. */
    private static final long TIMEOUT_MINUTES = 10;

    private final PromptTranslator translator;
    private final BiConsumer<EnginePrompt, HumanResponseSource> sendPrompt;
    private final SynchronousQueue<int[]> answers = new SynchronousQueue<>();

    private BoardObserver board;
    private volatile DuelMessage pending;

    public HumanResponseSource(PromptTranslator translator,
        BiConsumer<EnginePrompt, HumanResponseSource> sendPrompt)
    {
        this.translator = translator;
        this.sendPrompt = sendPrompt;
    }

    @Override
    public void onDuelStart(int playerIndex, BoardObserver observer)
    {
        board = observer;
    }

    @Override
    public byte[] respond(RawMessage prompt)
    {
        DuelMessage decoded = DuelMessage.decode(prompt);
        EnginePrompt payload = translator.toPrompt(decoded, board == null ? null : board.observe());
        if(payload == null || payload.options().isEmpty())
        {
            // Nothing to decide (an empty chain window, say): answer it for
            // them rather than opening a screen with no buttons. If even that
            // is impossible, refuse instead of guessing on their behalf.
            return translator.autoAnswer(decoded);
        }

        pending = decoded;
        sendPrompt.accept(payload, this);

        try
        {
            int[] chosen = answers.poll(TIMEOUT_MINUTES, TimeUnit.MINUTES);
            if(chosen == null)
            {
                return null; // timed out; run aborts
            }
            return translator.toResponse(decoded, chosen);
        }
        catch(InterruptedException e)
        {
            Thread.currentThread().interrupt();
            return null;
        }
        finally
        {
            pending = null;
        }
    }

    /**
     * Called from the network thread when the player clicks. Returns false if
     * nothing was waiting for an answer (a stale or duplicated packet).
     */
    public boolean submit(int[] chosen)
    {
        if(pending == null)
        {
            return false;
        }
        return answers.offer(chosen);
    }

    public boolean isWaiting()
    {
        return pending != null;
    }

    @Override
    public void onDuelEnd(HeadlessDuelRunner.DuelResult result)
    {
        pending = null;
    }
}
