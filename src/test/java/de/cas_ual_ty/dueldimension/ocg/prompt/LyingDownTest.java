package de.cas_ual_ty.dueldimension.ocg.prompt;

import de.cas_ual_ty.dueldimension.ocg.OcgConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Only a monster is ever drawn lying down.
 * <p>
 * The core makes this easy to get backwards. {@code POS_FACEDOWN} is
 * {@code FACEDOWN_ATTACK | FACEDOWN_DEFENSE} — 0xA — so a set Spell or Trap
 * tests true for {@code POS_DEFENSE} purely by virtue of being face down, and a
 * rule that reads the bit on its own turns every backrow card sideways as it is
 * played. A Spell has no battle position at all; the zone is what settles it.
 * <p>
 * The bit arithmetic is asserted here rather than trusted, because the whole
 * trap is that the constant does not mean what its name suggests in isolation.
 */
class LyingDownTest
{
    /** The rule under test, as {@code DuelistDuels.lyingDown} applies it. */
    private static boolean lyingDown(int location, int position)
    {
        return location == OcgConstants.LOCATION_MZONE
            && (position & OcgConstants.POS_DEFENSE) != 0;
    }

    @Test
    void faceDownAloneAlreadyCarriesTheDefenceBit()
    {
        // If this ever stops being true the guard below is unnecessary, and
        // whoever finds that out should find out here rather than in a duel.
        assertTrue((OcgConstants.POS_FACEDOWN & OcgConstants.POS_DEFENSE) != 0,
            "POS_FACEDOWN overlaps POS_DEFENSE; that overlap is why the zone is checked");
    }

    @Test
    void aSetSpellIsNotLyingDown()
    {
        assertFalse(lyingDown(OcgConstants.LOCATION_SZONE, OcgConstants.POS_FACEDOWN),
            "a set Spell is face down, not in defence");
        assertFalse(lyingDown(OcgConstants.LOCATION_SZONE,
            OcgConstants.POS_FACEDOWN_DEFENSE), "nor when the core spells it out");
    }

    @Test
    void aSetMonsterIs()
    {
        assertTrue(lyingDown(OcgConstants.LOCATION_MZONE, OcgConstants.POS_FACEDOWN_DEFENSE));
        assertTrue(lyingDown(OcgConstants.LOCATION_MZONE, OcgConstants.POS_FACEDOWN),
            "a set monster is face-down defence, which is what setting one means");
    }

    @Test
    void anAttackingMonsterIsNot()
    {
        assertFalse(lyingDown(OcgConstants.LOCATION_MZONE, OcgConstants.POS_FACEUP_ATTACK));
    }

    @Test
    void aDefendingMonsterIsWhicheverWayItFaces()
    {
        assertTrue(lyingDown(OcgConstants.LOCATION_MZONE, OcgConstants.POS_FACEUP_DEFENSE),
            "special summoned in defence: face up and lying down");
    }

    @Test
    void aFaceUpSpellIsNotEither()
    {
        assertFalse(lyingDown(OcgConstants.LOCATION_SZONE, OcgConstants.POS_FACEUP));
    }
}
