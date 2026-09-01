package de.cas_ual_ty.dueldimension.duel.match;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a duel is played when nobody says.
 * <p>
 * This is one constant and one null check, which is exactly why it is worth a
 * test: a default is invisible from inside the code that reads it, so the only
 * evidence that it is the intended one is a line that says so. It had been
 * {@code SCREEN} since before the board existed, and every lobby opened on the
 * 2D duel as a result -- with nothing anywhere reading as wrong.
 */
class MatchConfigTest
{
    @Test
    void aLobbyNobodyTouchedPlaysOnTheBoard()
    {
        assertEquals(MatchConfig.Presentation.OVERWORLD, MatchConfig.DEFAULT.presentation());
        assertTrue(MatchConfig.DEFAULT.isOverworld());
    }

    @Test
    void anUnstatedPresentationIsTheDefaultRatherThanTheScreen()
    {
        // A packet that omits the field has to land where an untouched lobby
        // lands, or the default only applies to clients that bother to state
        // it -- which is a default in name and not in effect.
        MatchConfig missing = new MatchConfig(Banlist.NO_BANLIST_ID, 8000,
            MatchConfig.Format.SINGLE, 180, null, false);
        assertEquals(MatchConfig.DEFAULT.presentation(), missing.sanitised().presentation());
    }

    @Test
    void statingTheScreenStillGetsTheScreen()
    {
        // The other half of the same guarantee: defaulting to the board must
        // not become forcing it on somebody who chose otherwise.
        MatchConfig chosen = MatchConfig.DEFAULT
            .withPresentation(MatchConfig.Presentation.SCREEN);
        assertEquals(MatchConfig.Presentation.SCREEN, chosen.sanitised().presentation());
        assertEquals(MatchConfig.Presentation.SCREEN, chosen.presentation());
    }

    /** The witheres carry every other field across, this one included. */
    @Test
    void changingOneSettingKeepsTheRest()
    {
        MatchConfig config = MatchConfig.DEFAULT
            .withPresentation(MatchConfig.Presentation.SCREEN)
            .withLifePoints(4000);
        assertEquals(MatchConfig.Presentation.SCREEN, config.presentation());
        assertEquals(4000, config.lifePoints());
        assertEquals(MatchConfig.DEFAULT.turnSeconds(), config.turnSeconds());
        assertEquals(MatchConfig.DEFAULT.format(), config.format());
    }
}
