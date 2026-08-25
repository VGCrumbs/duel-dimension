package de.cas_ual_ty.dueldimension.ocg.prompt;

import de.cas_ual_ty.dueldimension.ocg.msg.DuelMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the auto-chain decision to duelclient.cpp's MSG_SELECT_CHAIN handler:
 * <pre>
 * !select_trigger &amp;&amp; !chain_forced
 *   &amp;&amp; (ignore_chain || ((count == 0 || specount == 0) &amp;&amp; !always_chain))
 *   &amp;&amp; (count == 0 || !chain_when_avail)
 * </pre>
 */
class ChainPreferenceTest
{
    private static DuelMessage.SelectChain chain(int chainable, int specialCount, boolean forced)
    {
        List<DuelMessage.ChainOption> options = new java.util.ArrayList<>();
        for(int i = 0; i < chainable; i++)
        {
            options.add(new DuelMessage.ChainOption(1000 + i, null, 0, 0));
        }
        return new DuelMessage.SelectChain(0, specialCount, forced, 0, 0, options);
    }

    @Test
    void defaultAsksOnlyWhenTheWindowIsSpecial()
    {
        // Nothing chainable, or nothing special: answered silently.
        assertTrue(ChainPreference.DEFAULT.declinesWithoutAsking(chain(0, 0, false)));
        assertTrue(ChainPreference.DEFAULT.declinesWithoutAsking(chain(2, 0, false)),
            "count > 0 but specount == 0 still auto-declines");
        // Special window with something to chain: the player decides.
        assertFalse(ChainPreference.DEFAULT.declinesWithoutAsking(chain(2, 1, false)));
    }

    @Test
    void forcedAndTriggerWindowsAlwaysAsk()
    {
        assertFalse(ChainPreference.IGNORE.declinesWithoutAsking(chain(1, 0, true)),
            "a forced chain must be answered by the player");
        assertFalse(ChainPreference.IGNORE.declinesWithoutAsking(chain(1, 0x7F, false)),
            "spe_count 0x7f marks the select-trigger variant");
    }

    @Test
    void togglesBehaveLikeTheirButtons()
    {
        // Ignore: never ask.
        assertTrue(ChainPreference.IGNORE.declinesWithoutAsking(chain(3, 5, false)));
        // Always: ask whenever something is chainable, even if not special.
        assertFalse(ChainPreference.ALWAYS.declinesWithoutAsking(chain(2, 0, false)));
        // Per the source, "always" also suppresses the auto-decline for an
        // empty window - the formula's !always_chain term makes the whole
        // condition false. The translator still answers that window rather
        // than showing a screen with no buttons; see the test below.
        assertFalse(ChainPreference.ALWAYS.declinesWithoutAsking(chain(0, 0, false)));
        // When available: ask if any card is chainable.
        assertFalse(ChainPreference.WHEN_AVAILABLE.declinesWithoutAsking(chain(1, 0, false)));
        assertTrue(ChainPreference.WHEN_AVAILABLE.declinesWithoutAsking(chain(0, 0, false)));
    }

    /**
     * Whatever the policy says, a window with nothing in it must never reach
     * the player as an empty screen, and must never abort the duel.
     */
    @Test
    void emptyWindowsAreAlwaysAnswered()
    {
        PromptTranslator translator = new PromptTranslator(code -> null,
            new de.cas_ual_ty.dueldimension.ocg.text.DescriptionTable());
        for(ChainPreference preference : ChainPreference.values())
        {
            assertTrue(translator.autoAnswer(chain(0, 0, false), preference) != null,
                preference + " left an empty chain window unanswered");
        }
    }

    @Test
    void cyclingCoversEveryMode()
    {
        ChainPreference preference = ChainPreference.IGNORE;
        java.util.Set<ChainPreference> seen = new java.util.HashSet<>();
        for(int i = 0; i < ChainPreference.values().length; i++)
        {
            seen.add(preference);
            preference = preference.next();
        }
        assertTrue(seen.size() == ChainPreference.values().length, "cycle reaches every mode");
        assertTrue(preference == ChainPreference.IGNORE, "and returns to the start");
    }
}
