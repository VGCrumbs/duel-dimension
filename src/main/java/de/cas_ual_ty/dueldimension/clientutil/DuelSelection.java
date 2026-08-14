package de.cas_ual_ty.dueldimension.clientutil;

import de.cas_ual_ty.dueldimension.ocg.prompt.EnginePrompt;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What a duellist has picked out so far, for a prompt that wants several
 * things.
 * <p>
 * "Select tributes (0/3)" cannot be answered by one click, so the board has to
 * remember between clicks -- and it cannot remember in the cursor screen,
 * because that screen comes and goes every time the camera key is held. A
 * player part way through choosing three tributes who glances around the board
 * would come back to nothing selected. So the running answer lives out here,
 * where nothing that happens to the view can disturb it.
 * <p>
 * Kept in the ENGINE's own terms -- option indices, exactly what the answer is
 * built from -- rather than as board positions, so nothing has to be translated
 * back at the moment it matters and a card that moves mid-selection cannot
 * quietly become a different card.
 */
public final class DuelSelection
{
    private DuelSelection()
    {
    }

    private static final Set<Integer> chosen = new LinkedHashSet<>();
    private static EnginePrompt owner;

    /**
     * Ties the running selection to one prompt, and throws it away when the
     * question changes.
     * <p>
     * Identity rather than equality: two prompts that look alike are still two
     * questions, and carrying an answer from one to the next would answer the
     * second with the first's cards.
     */
    public static void sync(EnginePrompt prompt)
    {
        if(prompt != owner)
        {
            owner = prompt;
            chosen.clear();
        }
    }

    /** Does this prompt want more than one thing? */
    public static boolean wantsSeveral(EnginePrompt prompt)
    {
        return prompt != null && prompt.kind() == EnginePrompt.Kind.MULTI && prompt.maxSelect() > 1;
    }

    /**
     * Adds or removes one option, refusing to go past what the engine asked
     * for.
     * <p>
     * Refusing rather than pushing the oldest out: a player who has chosen
     * three of three and clicks a fourth has made a mistake, and silently
     * swapping one of their choices for it is a worse answer than doing
     * nothing.
     */
    public static void toggle(EnginePrompt prompt, int option)
    {
        sync(prompt);
        if(chosen.contains(option))
        {
            chosen.remove(option);
        }
        else if(prompt != null && chosen.size() < prompt.maxSelect())
        {
            chosen.add(option);
        }
    }

    public static boolean has(int option)
    {
        return chosen.contains(option);
    }

    /** Is anything the engine offers for this thing on the board already picked? */
    public static boolean holds(EnginePrompt prompt, BoardTarget target)
    {
        if(!wantsSeveral(prompt) || target == null)
        {
            return false;
        }
        for(int option : PromptOptions.optionsFor(prompt, false, target))
        {
            if(chosen.contains(option))
            {
                return true;
            }
        }
        return false;
    }

    public static int count()
    {
        return chosen.size();
    }

    /** Enough to send? The engine states its own minimum and this is it. */
    public static boolean ready(EnginePrompt prompt)
    {
        return prompt != null && chosen.size() >= prompt.minSelect()
            && chosen.size() <= prompt.maxSelect();
    }

    /** The answer, in the order it was picked -- which is the order it matters in. */
    public static int[] answer()
    {
        int[] indices = new int[chosen.size()];
        int at = 0;
        for(int option : chosen)
        {
            indices[at++] = option;
        }
        return indices;
    }

    public static List<Integer> chosen()
    {
        return List.copyOf(chosen);
    }

    public static void clear()
    {
        chosen.clear();
        owner = null;
    }
}
