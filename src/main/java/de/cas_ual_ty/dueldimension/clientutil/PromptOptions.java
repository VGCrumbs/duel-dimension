package de.cas_ual_ty.dueldimension.clientutil;

import de.cas_ual_ty.dueldimension.ocg.prompt.CardCommands;
import de.cas_ual_ty.dueldimension.ocg.prompt.EnginePrompt;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Which of the engine's offered options act on a given thing on the field.
 * <p>
 * <b>The rules live in the engine, and this is not a second copy of them.</b>
 * Every option here came from ocgcore in the prompt; all this does is ask which
 * of them are about the card being pointed at. That is why the world board can
 * offer a contextual menu without knowing a single Yu-Gi-Oh! rule -- and why it
 * must go through here rather than deciding for itself what looks legal.
 * <p>
 * Lifted out of {@code EngineDuelScreen}, where it was private, so the 3D board
 * and the 2D screen cannot come to different conclusions about what a player may
 * do with a card. The screen still calls it; it just no longer owns it.
 */
public final class PromptOptions
{
    private PromptOptions()
    {
    }

    /**
     * Option indices acting on this exact target, in the order a menu should
     * list them.
     *
     * @param answered true once this prompt has been answered, after which
     *                 nothing is actionable and the menu must be empty
     */
    public static List<Integer> optionsFor(EnginePrompt prompt, boolean answered,
        BoardTarget target)
    {
        List<Integer> found = new ArrayList<>();
        if(prompt == null || answered || target == null)
        {
            return found;
        }
        for(int i = 0; i < prompt.options().size(); i++)
        {
            EnginePrompt.Option option = prompt.options().get(i);
            if(prompt.kind() == EnginePrompt.Kind.PLACES)
            {
                if(target.zoneRef() >= 0 && option.zone() == target.zoneRef())
                {
                    found.add(i);
                }
            }
            else if(option.hasSlot()
                && option.isAt(target.controller(), target.location(), target.sequence()))
            {
                found.add(i);
            }
            else if(target.isPile() && option.hasSlot()
                && option.controller() == target.controller()
                && option.location() == target.location())
            {
                // duelclient.cpp raises deck_act/grave_act/remove_act/extra_act
                // for activations from a pile; the pile is the click target.
                found.add(i);
            }
            else if(!option.hasSlot() && option.cardCode() != 0
                && option.cardCode() == target.code() && !target.isPile())
            {
                found.add(i);
            }
        }
        found.sort(Comparator.comparingInt(index ->
            CardCommands.menuIndex(prompt.options().get(index).command())));
        return found;
    }

    /**
     * Can a duellist standing at a world board answer this prompt without the
     * duel screen?
     * <p>
     * CHOOSE is one option out of a list, and PLACES is picking zones on the
     * board -- both of which the board does at least as well as the screen, and
     * PLACES rather better. The rest genuinely cannot be done by looking at
     * things: MULTI picks several and confirms, SORT puts them in an order,
     * COUNTERS distributes a total, DECLARE_CARD wants a card NAME typed, and
     * the position prompt offers the same card in four postures rather than
     * four things on the board. Those keep the screen, which is what it is good
     * at, and is why the screen is not going anywhere.
     */
    public static boolean boardCanAnswer(EnginePrompt prompt)
    {
        if(prompt == null)
        {
            return false;
        }
        // isSingleChoice is the engine's own test for "one option, sent as one
        // index", and it is what the duel screen already trusts for exactly
        // this question -- a MULTI with a maximum of one is a CHOOSE wearing a
        // different label, and MSG_SELECT_CARD produces precisely that. POSITION
        // is four postures of one card, which is a list to pick one from and
        // nothing to do with the board; a summon asks it, so refusing it here
        // meant every summon pulled the screen over the board.
        return prompt.isSingleChoice()
            || ((prompt.kind() == EnginePrompt.Kind.PLACES
                || prompt.kind() == EnginePrompt.Kind.POSITION) && prompt.maxSelect() <= 1);
    }

    /**
     * The options that are not about anything on the board: ending a phase,
     * going to battle, declining a chain.
     * <p>
     * A card is answered by looking at it, but "End Phase" is not somewhere a
     * duellist can point. These are what the act key offers when it is not
     * aimed at a card, which is what makes a turn finishable without the
     * screen.
     */
    public static List<Integer> looseOptions(EnginePrompt prompt, boolean answered)
    {
        List<Integer> found = new ArrayList<>();
        if(prompt == null || answered)
        {
            return found;
        }
        for(int i = 0; i < prompt.options().size(); i++)
        {
            EnginePrompt.Option option = prompt.options().get(i);
            // Not about anything on the board is the whole test. Requiring
            // no card code as well threw away "Yes": a yes/no about an effect
            // is built slotless but carrying the effect's code on purpose, so
            // only "No" reached the row and the answer could not be given at
            // all unless a card with the same code happened to be on the
            // field -- impossible for a graveyard or deck trigger.
            if(!option.hasSlot())
            {
                found.add(i);
            }
        }
        found.sort(Comparator.comparingInt(index ->
            CardCommands.menuIndex(prompt.options().get(index).command())));
        return found;
    }

    /** Is there anything at all this player can do with the thing they are pointing at? */
    public static boolean actionable(EnginePrompt prompt, boolean answered, BoardTarget target)
    {
        return !optionsFor(prompt, answered, target).isEmpty();
    }
}
