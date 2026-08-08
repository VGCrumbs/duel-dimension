package de.cas_ual_ty.dueldimension.ocg.bot.executor;

import de.cas_ual_ty.dueldimension.ocg.OcgConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one deliberate deviation from Windbot's activation policy.
 * <p>
 * Windbot activates only what a per-card rule names, so a card it has never
 * heard of is declined — which is why a destroyed Mystic Tomato sat in the
 * graveyard offering a free body and the duelist said no. The duelists take a
 * trigger on a card that is <em>already in the graveyard</em>, because the card
 * is spent either way and declining cannot be the better play.
 * <p>
 * Both halves are pinned here: that it fires where it should, and — more
 * importantly — that it stays narrow. A rule that crept outwards would have the
 * bot chain every trap the moment it could, which is worse than the behaviour
 * it replaced.
 */
class FreeTriggerRuleTest
{
    /** The reference's own answer, which every non-duelist executor keeps. */
    private static final Executor REFERENCE = new DefaultExecutor()
    {
    };

    private static final Executor DUELIST = new Duelists.Generic();

    @Test
    void aTriggerInTheGraveyardIsTaken()
    {
        assertTrue(DUELIST.activateUnlistedOptional(null, OcgConstants.LOCATION_GRAVE));
    }

    @Test
    void aTriggerAnywhereElseIsStillDeclined()
    {
        for(int location : new int[] {OcgConstants.LOCATION_HAND, OcgConstants.LOCATION_MZONE,
            OcgConstants.LOCATION_SZONE, OcgConstants.LOCATION_DECK,
            OcgConstants.LOCATION_EXTRA, OcgConstants.LOCATION_REMOVED})
        {
            assertFalse(DUELIST.activateUnlistedOptional(null, location),
                "location " + location + " must keep Windbot's answer");
        }
    }

    /**
     * The ported class is untouched. If this ever passes as true, the
     * deviation has leaked out of our subclass and into the reference port.
     */
    @Test
    void theReferencePortStillDeclinesEverything()
    {
        for(int location : new int[] {OcgConstants.LOCATION_GRAVE, OcgConstants.LOCATION_HAND,
            OcgConstants.LOCATION_MZONE, OcgConstants.LOCATION_SZONE})
        {
            assertFalse(REFERENCE.activateUnlistedOptional(null, location),
                "DefaultExecutor is a port of Windbot and must not deviate");
        }
    }
}
