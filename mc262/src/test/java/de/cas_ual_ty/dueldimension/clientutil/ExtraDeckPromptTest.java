package de.cas_ual_ty.dueldimension.clientutil;

import de.cas_ual_ty.dueldimension.ocg.OcgConstants;
import de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot;
import de.cas_ual_ty.dueldimension.ocg.prompt.EnginePrompt;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * "Which of these Extra Deck monsters do you want to Special Summon?" is a
 * question the world board can ask.
 * <p>
 * It was answering it on the 2D duel screen instead, which yanks a duellist out
 * of the board mid-summon. The board already has the panel for this — the same
 * one a graveyard opens in — so the only question is whether
 * {@link PromptOptions#boardCanAnswer} says so.
 * <p>
 * Written as a test rather than reasoned about because the predicate is five
 * clauses deep and each one plausibly explains the symptom. This says which.
 */
class ExtraDeckPromptTest
{
    private static EnginePrompt summonFromExtra(int howMany, EnginePrompt.Kind kind, int max)
    {
        EnginePrompt.Option[] options = new EnginePrompt.Option[howMany];
        for(int i = 0; i < howMany; i++)
        {
            // Controller 0, LOCATION_EXTRA, and its own sequence in the pile --
            // which is what the engine names a selectable card by.
            options[i] = new EnginePrompt.Option("Argostars - Adventurous Arion", "",
                12345678 + i, 0, OcgConstants.LOCATION_EXTRA, i);
        }
        return new EnginePrompt(kind, "Special Summon", List.of(options), 1, max, true,
            BoardSnapshot.EMPTY);
    }

    @Test
    void pickingOneOfTwoExtraDeckMonstersStaysOnTheBoard()
    {
        assertTrue(PromptOptions.boardCanAnswer(summonFromExtra(2, EnginePrompt.Kind.CHOOSE, 1)),
            "CHOOSE of two Extra Deck monsters went to the duel screen");
    }

    @Test
    void theSameQuestionAsAOneOfMultiAlsoStaysOnTheBoard()
    {
        // MSG_SELECT_CARD with min 1 max 1 arrives as MULTI, and isSingleChoice
        // is the engine's own test for "that is a CHOOSE wearing a label".
        assertTrue(PromptOptions.boardCanAnswer(summonFromExtra(2, EnginePrompt.Kind.MULTI, 1)),
            "MULTI(1) of two Extra Deck monsters went to the duel screen");
    }

    /**
     * The other shape the same moment can take: the option names the PILE
     * rather than a card in it.
     * <p>
     * An idle prompt offers "Special Summon" against the Extra Deck as a verb,
     * and a pile has no sequence -- which is exactly what {@code hasSlot()}
     * requires. If this is the shape that arrives, every clause that looks at
     * locations sees nothing to look at.
     */
    @Test
    void theVerbAgainstThePileIsAlsoTheBoards()
    {
        EnginePrompt.Option verb = new EnginePrompt.Option("Special Summon", "", 0, -1, 0,
            0, OcgConstants.LOCATION_EXTRA, -1, 4);
        EnginePrompt prompt = new EnginePrompt(EnginePrompt.Kind.CHOOSE, "Special Summon",
            List.of(verb), 1, 1, true, BoardSnapshot.EMPTY);
        assertTrue(PromptOptions.boardCanAnswer(prompt),
            "the Extra Deck verb went to the duel screen");
    }

    @Test
    void oneCandidateIsStillTheBoardsToAsk()
    {
        assertTrue(PromptOptions.boardCanAnswer(summonFromExtra(1, EnginePrompt.Kind.CHOOSE, 1)),
            "a single Extra Deck candidate went to the duel screen");
    }
}
