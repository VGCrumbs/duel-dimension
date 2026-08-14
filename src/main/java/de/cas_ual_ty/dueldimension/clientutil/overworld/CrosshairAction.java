package de.cas_ual_ty.dueldimension.clientutil.overworld;

import de.cas_ual_ty.dueldimension.clientutil.BoardTarget;
import de.cas_ual_ty.dueldimension.clientutil.DuelActionController;
import de.cas_ual_ty.dueldimension.clientutil.DuelClientState;
import de.cas_ual_ty.dueldimension.clientutil.PromptOptions;
import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * Playing a turn without ever letting go of the camera.
 * <p>
 * The mouse belongs to the player. Freeing it is something they ask for with
 * the act key, and nothing else should take it from them -- so a click while
 * the camera is theirs acts on whatever the crosshair is on, exactly as a click
 * with the cursor acts on whatever the cursor is on. A whole turn of one-option
 * plays -- summon, attack, end phase -- never releases the mouse at all.
 * <p>
 * The one case that still has to: a card with more than one legal action needs
 * a choice made between them, and a choice needs something to point at. The
 * pointer opens for that and closes again on the answer, which is the smallest
 * the mouse can be borrowed for.
 */
public final class CrosshairAction
{
    private CrosshairAction()
    {
    }

    /**
     * Handles a click made while the camera is captured.
     *
     * @return true if the duel took the click, so it does not also reach the
     *         world as a swing at the air
     */
    public static boolean click(Minecraft client, boolean secondary)
    {
        if(!ClientDuelField.locked() || client.gui.screen() != null)
        {
            return false;
        }
        // Right-click declines, the same as it does with the cursor free and
        // the same as it does on the duel screen. A chain window is a question
        // you answer by saying nothing, it arrives constantly, and in camera
        // mode there is no row to click -- so without this a duellist holding
        // their own camera could not pass one at all.
        if(secondary && canDecline())
        {
            DuelActionController.answer(new int[0], 0);
            return true;
        }
        BoardTarget target = ClientDuelTargeting.looking();
        List<Integer> options = PromptOptions.optionsFor(DuelClientState.prompt, false, target);
        if(options.isEmpty())
        {
            // Nothing under the crosshair, so the click means the things that
            // are not on the board: ending a phase, passing a chain. One of
            // those and it is unambiguous; more and the player picks.
            List<Integer> loose = PromptOptions.looseOptions(DuelClientState.prompt, false);
            if(loose.size() == 1)
            {
                DuelActionController.answer(new int[] {loose.get(0)}, 0);
                return true;
            }
            if(!loose.isEmpty())
            {
                // Several, so a choice has to be made and the cursor is the
                // only thing that can make it. Returning false here left a
                // click that did nothing at all, which reads as a duel that has
                // stopped rather than as a question waiting to be answered.
                // Borrowed with the rows already up, and pinned so that still
                // holding the camera key does not take them away again.
                client.gui.setScreen(new BoardPointerScreen(null, loose));
                return true;
            }
            return false;
        }
        // The menu, always -- even for a card with exactly one legal action.
        // A trap in hand can only be Set, and a click that Set it outright was
        // a card committed by a misclick with nothing offered in between. This
        // opens AT the card, already listing what can be done with it: the
        // player pointed and asked once, and is not asked to point again.
        // Borrowed for exactly as long as the answer takes, and no forced
        // frame: see BoardPointerScreen.onClose.
        client.gui.setScreen(new BoardPointerScreen(target, options));
        return true;
    }

    /** Is the engine willing to take "nothing" for an answer right now? */
    private static boolean canDecline()
    {
        de.cas_ual_ty.dueldimension.ocg.prompt.EnginePrompt prompt = DuelClientState.prompt;
        return prompt != null && prompt.cancelable();
    }

    /** Is there anything the crosshair could act on right now? */
    public static boolean armed()
    {
        if(!ClientDuelField.locked())
        {
            return false;
        }
        return ClientDuelTargeting.actionable()
            || !PromptOptions.looseOptions(DuelClientState.prompt, false).isEmpty();
    }
}
