package de.cas_ual_ty.dueldimension.clientutil;

import de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Answering the engine, from wherever the answer was given.
 * <p>
 * One sender for the 2D screen and the world board, because the thing that must
 * not vary is the SERIAL. {@code HumanResponseSource.submit} silently drops an
 * answer whose serial does not match the prompt it is answering, and the duel
 * thread then sits parked on a prompt nobody can answer any more -- a duel that
 * looks alive and is not. Quoting {@code DuelClientState.promptSerial} back
 * verbatim is the whole contract, and it is easier to keep in one place than in
 * two.
 * <p>
 * Clearing the local prompt is part of the same step: an answer already sent
 * must not be sendable twice, and the turn clock beside the phase bar stops when
 * the deadline is met rather than running on into the opponent's turn.
 */
public final class DuelActionController
{
    private DuelActionController()
    {
    }

    /**
     * Sends an answer for the prompt currently on the client.
     *
     * @param chosen       the option indices the player picked
     * @param declaredCode a card code for prompts that ask for one, else 0
     * @return false if there was nothing outstanding to answer
     */
    public static boolean answer(int[] chosen, int declaredCode)
    {
        if(DuelClientState.prompt == null)
        {
            return false;
        }
        DuelClientState.prompt = null;
        DuelClientState.promptShownAt = 0;
        ClientPlayNetworking.send(new PromptMessages.AnswerPrompt(chosen, declaredCode,
            DuelClientState.promptSerial));
        return true;
    }
}
