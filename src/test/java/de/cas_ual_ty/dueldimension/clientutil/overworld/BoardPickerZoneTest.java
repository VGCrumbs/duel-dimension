package de.cas_ual_ty.dueldimension.clientutil.overworld;

import de.cas_ual_ty.dueldimension.clientutil.BoardTarget;
import de.cas_ual_ty.dueldimension.clientutil.FieldLayout;
import de.cas_ual_ty.dueldimension.ocg.OcgConstants;
import de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One square, two answers, and the duel decides which.
 * <p>
 * An extra monster zone is a single physical square standing for two logical
 * zones, one per controller. The mirror crosses them over -- controller 0's
 * sequence 5 lands on controller 1's sequence 6 -- so a click there is genuinely
 * ambiguous, and occupancy cannot settle it: the square is empty in both
 * readings until somebody summons into it, and picking the lower controller then
 * means a duellist points at a square and summons into the other half of the
 * engine's numbering.
 * <p>
 * The flat board has always asked the prompt, weighting a candidate the duel is
 * offering above one it is not. This is that rule, on the world board, where it
 * had been resolved by controller order.
 * <p>
 * {@code BoardPicker} takes a ray's landing point rather than a camera, which is
 * what lets this be checked with no client behind it.
 */
class BoardPickerZoneTest
{
    /** Both sides bare, which is when the ambiguity is at its worst. */
    private static BoardSnapshot emptyBoard()
    {
        return new BoardSnapshot(BoardSnapshot.Side.empty(), BoardSnapshot.Side.empty(), 1, 0, 0);
    }

    /** The left extra monster zone, as controller 0 numbers it. */
    private static float[] sharedSquare()
    {
        FieldLayout.Rect rect = FieldLayout.zone(0, OcgConstants.LOCATION_MZONE, 5);
        return new float[] {rect.x() + rect.w() / 2F, rect.y() + rect.h() / 2F};
    }

    /** Weight a candidate the way the flat board weights an offered one. */
    private static BoardTarget pickFavouring(int wanted, int seat)
    {
        return BoardPicker.at(emptyBoard(), seat, sharedSquare(),
            target -> target.controller() == wanted ? 4 : 0);
    }

    @Test
    void oneSquareCarriesBothControllersExtraMonsterZone()
    {
        // The premise everything below rests on. If the mirror ever stops
        // crossing 5 onto 6 there is no tie to break and this whole test is
        // measuring nothing, so it is asserted rather than assumed.
        FieldLayout.Rect mine = FieldLayout.zone(0, OcgConstants.LOCATION_MZONE, 5);
        FieldLayout.Rect theirs = FieldLayout.zone(1, OcgConstants.LOCATION_MZONE, 6);
        assertNotNull(mine);
        assertNotNull(theirs);
        assertEquals(mine.x(), theirs.x(), 1e-4F, "the extra monster zones no longer share a square");
        assertEquals(mine.y(), theirs.y(), 1e-4F, "the extra monster zones no longer share a square");

        // And the other pairing is a different square, or the two would be one.
        FieldLayout.Rect other = FieldLayout.zone(1, OcgConstants.LOCATION_MZONE, 5);
        assertTrue(Math.abs(other.x() - mine.x()) > 1e-4F,
            "both extra monster zones collapsed onto one square");
    }

    @Test
    void theZoneTheDuelIsAskingAboutWins()
    {
        BoardTarget mine = pickFavouring(0, 0);
        BoardTarget theirs = pickFavouring(1, 0);

        assertNotNull(mine);
        assertNotNull(theirs);
        assertEquals(0, mine.controller(), "the offered zone lost to controller order");
        assertEquals(5, mine.sequence());
        // Same square, same click, the other answer -- which is the whole point.
        assertEquals(1, theirs.controller(), "the offered zone lost to controller order");
        assertEquals(6, theirs.sequence());
    }

    @Test
    void bothSeatsAnswerTheSameSquareTheSameWay()
    {
        // The seat is only a translation: whichever seat is looking, the zone
        // the duel is offering is the zone that answers. Getting this wrong is
        // how one duellist summons into the other's half. The sequence differs
        // by seat because the mirror crosses them; the controller must not.
        for(int seat = 0; seat <= 1; seat++)
        {
            BoardTarget hit = pickFavouring(1, seat);
            assertNotNull(hit, "nothing was picked at seat " + seat);
            assertEquals(1, hit.controller(),
                "seat " + seat + " picked its own half over the offered one");
            assertEquals(seat == 0 ? 6 : 5, hit.sequence(),
                "seat " + seat + " picked the wrong side of the mirror");
        }
    }

    @Test
    void withNoPromptToConsultTheSquareIsStillPickable()
    {
        // The old rule, kept: nothing to go on means occupancy decides, and an
        // empty shared square still answers rather than falling through to the
        // bare mat behind it.
        BoardTarget hit = BoardPicker.at(emptyBoard(), 0, sharedSquare(), null);
        assertNotNull(hit, "an empty extra monster zone should still be pickable");
        assertEquals(OcgConstants.LOCATION_MZONE, hit.location());
        assertTrue(hit.sequence() == 5 || hit.sequence() == 6,
            "picked something that was not an extra monster zone");
    }
}
