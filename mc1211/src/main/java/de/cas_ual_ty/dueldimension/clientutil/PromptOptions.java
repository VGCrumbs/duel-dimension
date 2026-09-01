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
     * Is this click already the whole answer, or does it still have to ask?
     * <p>
     * One thing asks: a card in HAND whose single option is an ACTION. That is
     * where a misclick costs something irreversible -- a trap can only be Set,
     * so a cursor a few pixels off used to put it face-down on the field with
     * nothing offered in between, and a card played from hand does not come
     * back. The menu costs one click and buys the chance to change your mind.
     * <p>
     * An action is exactly what the engine puts in an option's command: Summon,
     * Set, Activate. A SELECTION carries no command at all, and being asked
     * which card to discard is not the same question as being asked what to do
     * with one -- the card IS the answer, so a one-row menu repeats the card
     * just clicked back at the player and asks them to click it twice. The same
     * is true of a tribute, of chain material, and of a hand card offered as a
     * cost, all of which arrive through MSG_SELECT_CARD carrying no command.
     * <p>
     * Everything off the hand answers on the click for the same reason. An
     * empty square is the answer to "where", an attack target the answer to
     * "what are you hitting". More than one option is a real choice wherever it
     * is, and always asks.
     */
    public static boolean answersOutright(EnginePrompt prompt, BoardTarget target,
        List<Integer> options)
    {
        return target != null && answersOutright(prompt, target.isPile(), options);
    }

    /**
     * The same question, for a caller whose idea of a square is its own.
     * <p>
     * The flat board's {@code Hit} and the world board's {@code BoardTarget}
     * are two records for one thing, and the only fact this rule needs from
     * either is whether it is a stack. Taking the boolean lets the screen ask
     * the shared question instead of keeping the fourth copy of the answer --
     * which it did, and which had already drifted: its copy was missing the
     * pile clause, so a single-target Monster Reborn committed the revival the
     * instant the graveyard was clicked, without ever naming the monster it
     * brought back.
     */
    public static boolean answersOutright(EnginePrompt prompt, boolean pile,
        List<Integer> options)
    {
        if(prompt == null || options.size() != 1)
        {
            return false;
        }
        // A stack never answers on the click. Its cards are face down, so one
        // option means one card the player has not seen -- and committing it
        // sight unseen is exactly what the verb list and the picker exist to
        // prevent. The cursor already routed piles that way; the crosshair did
        // not, and would summon a monster out of a graveyard on a single click.
        if(pile)
        {
            return false;
        }
        // A chain window is activations wearing a selection's clothes.
        // MSG_SELECT_CHAIN names cards rather than commands, so every one of
        // its options carries command 0 -- but clicking one SETS OFF a trap,
        // which is the single most irreversible click in a duel and the very
        // thing the menu was asked for. EDOPro puts a confirmation in front of
        // it too.
        if(prompt.chainWindow())
        {
            return false;
        }
        return prompt.options().get(options.get(0)).command() == 0;
    }

    /**
     * Can a duellist standing at a world board answer a prompt that wants
     * SEVERAL things?
     * <p>
     * Tributes, mostly. Three monsters are three cards on the board, every one
     * of them somewhere a duellist can point -- so the only reason this went to
     * the duel screen was that the board could not remember between clicks, and
     * that is now {@link DuelSelection}'s job rather than a reason to leave.
     */
    private static boolean severalPointable(EnginePrompt prompt)
    {
        return prompt.kind() == EnginePrompt.Kind.MULTI;
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
        // THE DESTINY DRAW, stated rather than derived.
        //
        // Its two options name no card, no zone and no phase, so every test
        // below reads it as a question with nothing to point at and hands it to
        // the flat screen -- which is what yanked a duellist off the board to
        // answer it. That reasoning was right before the board had a panel for
        // it and is wrong now that it does: see DestinyPrompt, which both duel
        // views draw from.
        if(de.cas_ual_ty.dueldimension.clientutil.DestinyPrompt.isOffered(prompt))
        {
            return true;
        }
        // isSingleChoice is the engine's own test for "one option, sent as one
        // index", and it is what the duel screen already trusts for exactly
        // this question -- a MULTI with a maximum of one is a CHOOSE wearing a
        // different label, and MSG_SELECT_CARD produces precisely that. POSITION
        // is four postures of one card, which is a list to pick one from and
        // nothing to do with the board; a summon asks it, so refusing it here
        // meant every summon pulled the screen over the board.
        // PLACES at any size. Every option is a zone on the mat, which is the
        // one thing a world board is better at than a screen -- and a placement
        // wanting two of them is still nothing but zones. POSITION keeps its
        // limit: it is four postures of one card rather than four things to
        // point at, so more than one of it is a list and not a board.
        if(!prompt.isSingleChoice() && !severalPointable(prompt)
            && prompt.kind() != EnginePrompt.Kind.PLACES
            && !(prompt.kind() == EnginePrompt.Kind.POSITION && prompt.maxSelect() <= 1))
        {
            return false;
        }
        // Everything hidden and more than one to pick: the picker cannot count
        // and the board has nothing to point at, so this is the duel screen's.
        if(allHidden(prompt) && !needsPicker(prompt))
        {
            return false;
        }
        // A prompt the PICKER answers needs nothing pointed at. It is a panel
        // of named cards, which is exactly how the board asks about a
        // graveyard -- and asking pointable() of it anyway vetoed the very
        // prompts needsPicker was added to keep.
        //
        // A chain window offering a graveyard effect is the case that matters:
        // every option is in a pile, none of them is on the field, so nothing
        // was pickable and the flat board came up over the duel to ask a
        // question the board already had a way to ask. Answering a chain is
        // the most frequent thing a duel asks after a phase, and it was the
        // one thing that could not be done where the duel was happening.
        if(needsPicker(prompt))
        {
            return true;
        }
        return pointable(prompt) && (aboutTheBoard(prompt) || needsList(prompt));
    }

    /**
     * Is this a question the board can only ask as a LIST?
     * <p>
     * "Change this card's Type to the destroyed monster's original Type?" has
     * two answers and no subject: neither Yes nor No is a card, a zone or a
     * phase, so there is nothing on the board a duellist could point at to give
     * either one. A position prompt is the same shape -- one card in four
     * postures, and the card is very often not on the field yet to be clicked.
     * <p>
     * These used to be handed to the duel screen, which meant an ordinary flip
     * effect yanked a player out of the world board and put them back a second
     * later. The screen was never needed: the cursor is already there and a
     * list of two rows is not a thing only a screen can draw. So the board
     * keeps them and opens the list itself.
     * <p>
     * NOT for anything with a subject. A prompt with even one option on the
     * board is answered by pointing at it, which is the whole reason for
     * standing at one.
     */
    public static boolean needsList(EnginePrompt prompt)
    {
        if(prompt == null)
        {
            return false;
        }
        // POSITION belongs here too, which is what this javadoc has always
        // said. It is not isSingleChoice by kind, and leaving it out meant a
        // Synchro or a revival asked for a posture while the card was still in
        // the Extra Deck or the graveyard -- nothing on the board carried its
        // code, the prompt refuses an empty answer, and every click did
        // nothing. A duel parked on "attack or defence" with no way to say
        // either.
        if(!prompt.isSingleChoice() && prompt.kind() != EnginePrompt.Kind.POSITION)
        {
            return false;
        }
        for(EnginePrompt.Option option : prompt.options())
        {
            if(option.hasSlot())
            {
                return false;
            }
        }
        List<Integer> rows = unanchoredOptions(prompt, false);
        // And only if it fits on the screen. "Declare a Type" offers
        // twenty-five, which is taller than the window: the rows below the
        // bottom edge cannot be clicked, the prompt refuses an empty answer,
        // and the list reopens itself every tick. The duel screen wraps its
        // buttons and has always handled these, so anything this long keeps it.
        return !rows.isEmpty() && rows.size() <= MAX_LIST_ROWS;
    }

    /**
     * The tallest list the board will take on.
     * <p>
     * Eight covers everything a duel actually asks a duellist to read at the
     * board: Yes and No, an effect's two or three halves, the seven Attributes,
     * a posture, rock-paper-scissors. What it excludes is the declaration
     * prompts, which are card indexes rather than questions and want a screen.
     */
    private static final int MAX_LIST_ROWS = 8;

    /**
     * Does this prompt ask about anything that is ON the board?
     * <p>
     * "Change this card's Type to the destroyed monster's original Type?" is a
     * question with two answers and no subject: neither Yes nor No is a card,
     * a zone or a phase, so there is nothing on the board to point at and the
     * board can offer no way to answer it. Left to the board, that prompt
     * arrived with nothing to click and the duel stopped there.
     * <p>
     * The duel screen has real buttons for exactly this, so a question with no
     * subject goes to it -- and comes straight back, because the screen hands
     * the board over again the moment the question is answered.
     * <p>
     * A phase counts as a subject: the phase bar is on screen and is clickable.
     */
    private static boolean aboutTheBoard(EnginePrompt prompt)
    {
        for(EnginePrompt.Option option : prompt.options())
        {
            if(option.hasSlot() || option.zone() >= 0
                || CardCommands.isPhaseAction(option.command()))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Is everything this prompt offers something a duellist can actually point
     * at?
     * <p>
     * A card in a zone or in your hand is drawn where you can look at it. A
     * card inside a deck, a graveyard, a banished pile or an extra deck is not
     * drawn one by one -- the board draws each of those as ONE object, a stack,
     * deliberately, because that is what they are -- but the stack itself is
     * something a duellist can point at, and clicking it offers whatever the
     * prompt holds in it. That is not an invention either: duelclient.cpp
     * raises deck_act, grave_act, remove_act and extra_act against the PILE and
     * not against a card nobody can see, and {@link #optionsFor} has matched
     * pile clicks that way all along.
     * <p>
     * Refusing every such prompt was throwing the whole question at the duel
     * screen over one option the board could already have answered -- and a
     * chain window with a graveyard effect in it alongside a card on the field
     * is an ordinary turn, not an edge case, so the screen kept swinging over
     * the board mid-duel and back again.
     * <p>
     * But a question whose answers are ALL inside piles is a different thing.
     * "Send a card from your Extra Deck to the graveyard" names six monsters
     * nobody can see, and pointing at the stack they are in says nothing about
     * which -- so the board would be asking a player to choose between six
     * identical card backs. That belongs on the screen, whose picker draws each
     * one with its artwork and its name, exactly as it does in a duel played
     * entirely on the screen. The rule is therefore not "are any options in a
     * pile" but "is there anything to point AT": at least one option somewhere
     * a duellist can actually look at.
     * <p>
     * And what stays unpointable altogether is unpointable in either case: a
     * card underneath an Xyz monster, a card in the opponent's hand.
     */
    private static boolean pointable(EnginePrompt prompt)
    {
        for(EnginePrompt.Option option : prompt.options())
        {
            if(!option.hasSlot())
            {
                // Loose: a phase action, or one side of a Yes/No. Neither is in
                // a pile and neither stops the board answering.
                continue;
            }
            if(!pickable(option))
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Is every answer to this question inside a stack, where nobody can see it?
     * <p>
     * The board used to refuse these outright, because pointing at a stack that
     * holds six candidates says nothing about which -- six identical backs is
     * not a choice. It has a picker of its own now, so the question is no
     * longer whether the board can SEE them but whether it can ASK about them.
     */
    private static boolean allHidden(EnginePrompt prompt)
    {
        boolean anySlot = false;
        for(EnginePrompt.Option option : prompt.options())
        {
            if(!option.hasSlot())
            {
                return false;
            }
            anySlot = true;
            if(!isPile(option.location()))
            {
                return false;
            }
        }
        return anySlot;
    }

    /**
     * Should the board open its card picker for this?
     * <p>
     * Only for a question with ONE answer. The picker shows a card at a time
     * and takes a single click; a selection that wants two out of a graveyard
     * needs a running count and a confirm, which is what the duel screen's own
     * picker already has. And only while the list is short enough to lay out --
     * past that the screen is genuinely the better tool, not a fallback.
     */
    public static boolean needsPicker(EnginePrompt prompt)
    {
        return prompt != null && prompt.isSingleChoice() && allHidden(prompt)
            && prompt.options().size() <= de.cas_ual_ty.dueldimension.clientutil.overworld
                .CardChooser.MAX_CARDS;
    }

    /**
     * Is this one of the four stacks -- drawn as a single object, with its
     * cards face down inside it?
     * <p>
     * A card in one can be REACHED by clicking the stack, which is why a prompt
     * with one of these among things on the board still belongs to the board.
     * It cannot be told apart from its neighbours by pointing, which is why a
     * prompt made of nothing else does not.
     */
    private static boolean isPile(int location)
    {
        return location == de.cas_ual_ty.dueldimension.ocg.OcgConstants.LOCATION_DECK
            || location == de.cas_ual_ty.dueldimension.ocg.OcgConstants.LOCATION_EXTRA
            || location == de.cas_ual_ty.dueldimension.ocg.OcgConstants.LOCATION_GRAVE
            || location == de.cas_ual_ty.dueldimension.ocg.OcgConstants.LOCATION_REMOVED;
    }

    /**
     * Is this somewhere the board actually draws, and the picker actually
     * returns?
     * <p>
     * The same seven the picker knows: its zone table is the monster and
     * spell/trap rows, its pile table is the four stacks, and the hand is drawn
     * by the HUD. Kept as one list because a location the board cannot pick is
     * a prompt the board cannot answer, and the two answers drifting apart is a
     * duel parked on a question with nothing on screen to click.
     */
    private static boolean pickable(EnginePrompt.Option option)
    {
        int location = option.location();
        if(location == de.cas_ual_ty.dueldimension.ocg.OcgConstants.LOCATION_HAND)
        {
            // YOUR hand only. The board draws the opponent's as backs standing
            // in front of them -- deliberately, because their faces are theirs
            // -- so "look at your opponent's hand and take one" has as many
            // things to click as it has cards you can identify, which is none.
            // The picker on the duel screen is where that question is answered.
            return option.controller() == 0;
        }
        return location == de.cas_ual_ty.dueldimension.ocg.OcgConstants.LOCATION_MZONE
            || location == de.cas_ual_ty.dueldimension.ocg.OcgConstants.LOCATION_SZONE
            || location == de.cas_ual_ty.dueldimension.ocg.OcgConstants.LOCATION_DECK
            || location == de.cas_ual_ty.dueldimension.ocg.OcgConstants.LOCATION_EXTRA
            || location == de.cas_ual_ty.dueldimension.ocg.OcgConstants.LOCATION_GRAVE
            || location == de.cas_ual_ty.dueldimension.ocg.OcgConstants.LOCATION_REMOVED;
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
    /**
     * Is the engine willing to take "nothing" for an answer right now?
     * <p>
     * One copy, because everything that offers a way out asks it -- the cursor,
     * the crosshair, and the cancel button in the corner. A way out offered
     * where the engine will not accept one is a click that parks the duel on a
     * question it has already refused to drop.
     */
    public static boolean canDecline(EnginePrompt prompt)
    {
        return prompt != null && prompt.cancelable();
    }

    /**
     * The loose options that genuinely need a list, which is not the same set.
     * <p>
     * A phase transition is loose -- "End Turn" is not about a card -- but it
     * is not homeless: the phase bar is drawn across the top of the screen and
     * every one of those options is a bay on it. Offering them again in a menu
     * puts the same three commands in two places, and the menu is the worse of
     * the two because it has to be summoned and the bar is simply there.
     * <p>
     * What is left is a question with nowhere to be pointed at: Yes and No, or
     * which of an effect's two halves to apply. Those have no home at all, and
     * a list is the only thing that can ask them.
     */
    public static List<Integer> unanchoredOptions(EnginePrompt prompt, boolean answered)
    {
        List<Integer> found = new ArrayList<>();
        for(int index : looseOptions(prompt, answered))
        {
            if(!CardCommands.isPhaseAction(prompt.options().get(index).command()))
            {
                found.add(index);
            }
        }
        return found;
    }

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
