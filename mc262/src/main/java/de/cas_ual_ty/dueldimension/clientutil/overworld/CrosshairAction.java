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
            // Your own deck first, and before anything about the prompt. It is
            // where the duel's own controls live, and a crosshair that found
            // nothing to do with it used to fall through to the loose options
            // and hand back the phase bar's own commands instead -- three rows
            // that already have somewhere to be.
            if(BoardPointerScreen.isOwnDeck(target))
            {
                client.gui.setScreen(new BoardPointerScreen(target,
                    BoardPointerScreen.deckMenu()));
                return true;
            }

            // Nothing under the crosshair, so the click means the things that
            // are not on the board -- but NOT the phase transitions, which are
            // bays on the phase bar and do not want a second home in a menu.
            // What is left is a question with nowhere to point: Yes or No, or
            // which half of an effect to use.
            List<Integer> loose = PromptOptions.unanchoredOptions(DuelClientState.prompt, false);
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
        // A prompt that wants several things toggles instead of answering,
        // exactly as it does with the cursor: the camera being the player's
        // does not change what a click MEANS.
        if(de.cas_ual_ty.dueldimension.clientutil.DuelSelection.wantsSeveral(
            DuelClientState.prompt))
        {
            de.cas_ual_ty.dueldimension.clientutil.DuelSelection.toggle(
                DuelClientState.prompt,
                de.cas_ual_ty.dueldimension.clientutil.DuelSelection.pick(options));
            // Same rule as the cursor: naming the last zone a placement asked
            // for IS the answer, and holding the camera key does not change
            // what a click means.
            if(de.cas_ual_ty.dueldimension.clientutil.DuelSelection.placementComplete(
                DuelClientState.prompt))
            {
                DuelActionController.answer(
                    de.cas_ual_ty.dueldimension.clientutil.DuelSelection.answer(), 0);
                de.cas_ual_ty.dueldimension.clientutil.DuelSelection.clear();
            }
            return true;
        }

        // Some clicks are already the whole answer -- an empty square asked
        // "where", a tribute asked "which" -- and those never cost the player
        // their camera.
        if(PromptOptions.answersOutright(DuelClientState.prompt, target, options))
        {
            DuelActionController.answer(new int[] {options.get(0)}, 0);
            return true;
        }
        // Anything with something to be DONE to it asks first, even with one
        // row: a trap can only be Set, and a click that Set it outright was a
        // card committed by a misclick with nothing offered in between. This
        // opens AT the card, already listing what can be done with it, so the
        // player who pointed and asked once is not asked to point again.
        // Borrowed for exactly as long as the answer takes, and no forced
        // frame: see BoardPointerScreen.onClose.
        client.gui.setScreen(new BoardPointerScreen(target, options));
        return true;
    }

    /** Is the engine willing to take "nothing" for an answer right now? */
    private static boolean canDecline()
    {
        return PromptOptions.canDecline(DuelClientState.prompt);
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
