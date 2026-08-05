package de.cas_ual_ty.dueldimension.ocg.prompt;

import de.cas_ual_ty.dueldimension.ocg.HeadlessDuelRunner;
import de.cas_ual_ty.dueldimension.ocg.OcgConstants;
import de.cas_ual_ty.dueldimension.ocg.RawMessage;
import de.cas_ual_ty.dueldimension.ocg.ResponseSource;
import de.cas_ual_ty.dueldimension.ocg.msg.DuelMessage;
import de.cas_ual_ty.dueldimension.ocg.text.DescriptionTable;
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
 * duels do not run on the server tick thread. An invalid answer (stale click,
 * illegal declare) re-sends the prompt instead of aborting; a timeout keeps a
 * disconnected player from pinning a thread forever.
 */
public class HumanResponseSource implements ResponseSource
{
    /** How long a player may think before the duel gives up on them. */
    private static final long TIMEOUT_MINUTES = 10;
    private static final int MAX_INVALID_ANSWERS = 8;

    /** What the player sent back: option indices plus the declared card code, if any. */
    public record Answer(int[] chosen, int declaredCode)
    {
    }

    private final PromptTranslator translator;
    private final BiConsumer<EnginePrompt, HumanResponseSource> sendPrompt;
    private final SynchronousQueue<Answer> answers = new SynchronousQueue<>();

    private BoardObserver board;
    private volatile DuelMessage pending;
    private volatile ChainPreference chainPreference = ChainPreference.DEFAULT;

    // Turn context, tracked from the observed stream (same thread as the core).
    private int turn;
    private int phase;
    private int turnPlayer;
    private int seat;

    public HumanResponseSource(PromptTranslator translator,
        BiConsumer<EnginePrompt, HumanResponseSource> sendPrompt)
    {
        this.translator = translator;
        this.sendPrompt = sendPrompt;
    }

    @Override
    public void onDuelStart(int playerIndex, BoardObserver observer)
    {
        seat = playerIndex;
        board = observer;
    }

    @Override
    public void observe(RawMessage message)
    {
        if(message.type() == OcgConstants.MSG_NEW_TURN)
        {
            DuelMessage.NewTurn newTurn = (DuelMessage.NewTurn)DuelMessage.decode(message);
            turn++;
            turnPlayer = newTurn.player() == seat ? 0 : 1;
        }
        else if(message.type() == OcgConstants.MSG_NEW_PHASE)
        {
            phase = ((DuelMessage.NewPhase)DuelMessage.decode(message)).phase();
        }
        else if(message.type() == OcgConstants.MSG_HINT)
        {
            // HINT_SELECTMSG is how the core says what a coming selection is
            // FOR ("select a card to discard", "select a monster to tribute").
            // duelclient.cpp stashes it in select_hint and titles the next
            // selection with it; without this every prompt read "Select a card".
            DuelMessage.Hint hint = (DuelMessage.Hint)DuelMessage.decode(message);
            if(hint.hintType() == DescriptionTable.OcgHints.SELECT_MESSAGE)
            {
                translator.noteSelectHint(hint.description());
            }
        }
    }

    @Override
    public byte[] respond(RawMessage prompt)
    {
        DuelMessage decoded = DuelMessage.decode(prompt);
        // Chain windows the policy answers for us never reach the screen.
        byte[] automatic = translator.autoAnswer(decoded, chainPreference);
        if(automatic != null)
        {
            return automatic;
        }
        BoardSnapshot field = board == null ? BoardSnapshot.EMPTY
            : BoardSnapshot.of(board.observe(), turn, phase, turnPlayer);
        EnginePrompt payload = translator.toPrompt(decoded, field);

        if(payload == null || (payload.options().isEmpty() && payload.kind() != EnginePrompt.Kind.DECLARE_CARD))
        {
            // Nothing to decide (an empty chain window, say): answer it for
            // them rather than opening a screen with no buttons. If even that
            // is impossible, refuse instead of guessing on their behalf.
            return translator.autoAnswer(decoded, chainPreference);
        }

        try
        {
            for(int attempt = 0; attempt < MAX_INVALID_ANSWERS; attempt++)
            {
                pending = decoded;
                sendPrompt.accept(payload, this);

                Answer answer = answers.poll(TIMEOUT_MINUTES, TimeUnit.MINUTES);
                if(answer == null)
                {
                    return null; // timed out; run aborts
                }
                byte[] response = translator.toResponse(decoded, answer.chosen(), answer.declaredCode());
                if(response != null)
                {
                    return response;
                }
                // Invalid (stale click, illegal declared name): ask again.
            }
            return null;
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
    public boolean submit(Answer answer)
    {
        if(pending == null)
        {
            return false;
        }
        return answers.offer(answer);
    }

    public void setChainPreference(ChainPreference preference)
    {
        chainPreference = preference;
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
