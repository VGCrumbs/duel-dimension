package de.cas_ual_ty.dueldimension.ocg.prompt;

import de.cas_ual_ty.dueldimension.ocg.OcgCard;
import de.cas_ual_ty.dueldimension.ocg.msg.DuelMessage;
import de.cas_ual_ty.dueldimension.ocg.text.DescriptionTable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The dialog kinds a starter-deck duel never produces, verified on fixtures:
 * each becomes a presentable prompt and each answer encodes exactly what the
 * core's response readers expect (formats from playerop.cpp).
 */
class DialogKindsTest
{
    private final OcgCard vanilla = new OcgCard(1001, 0, OcgCard.NO_SETCODES, 0x11, 4, 1, 1, 1700, 1000, 0, 0, 0);
    private final PromptTranslator translator =
        new PromptTranslator(code -> code == 1001 ? vanilla : null, new DescriptionTable());

    @Test
    void sortBecomesOrderableAndEncodesAPermutation()
    {
        DuelMessage.SortCard sort = new DuelMessage.SortCard(0, false, List.of(
            new DuelMessage.SortableCard(1001, 0, 4, 0),
            new DuelMessage.SortableCard(1001, 0, 4, 1),
            new DuelMessage.SortableCard(1001, 0, 4, 2)));

        EnginePrompt prompt = translator.toPrompt(sort, BoardSnapshot.EMPTY);
        assertEquals(EnginePrompt.Kind.SORT, prompt.kind());
        assertEquals(3, prompt.options().size());
        assertTrue(prompt.cancelable(), "sorting may always be declined");

        assertArrayEquals(new byte[] {2, 0, 1}, translator.toResponse(sort, new int[] {2, 0, 1}, 0));
        assertArrayEquals(new byte[] {-1}, translator.toResponse(sort, new int[0], 0), "decline");
        assertNull(translator.toResponse(sort, new int[] {0, 0, 1}, 0), "not a permutation");
    }

    @Test
    void countersBecomeAmountsAndMustHitTheTotal()
    {
        DuelMessage.SelectCounter counter = new DuelMessage.SelectCounter(0, 0x1, 3, List.of(
            new DuelMessage.CounterCard(1001, 0, 4, 0, 2),
            new DuelMessage.CounterCard(1001, 0, 4, 1, 2)));

        EnginePrompt prompt = translator.toPrompt(counter, BoardSnapshot.EMPTY);
        assertEquals(EnginePrompt.Kind.COUNTERS, prompt.kind());
        assertEquals(2, prompt.options().get(0).max(), "stock is the per-option cap");

        assertArrayEquals(new byte[] {2, 0, 1, 0}, translator.toResponse(counter, new int[] {2, 1}, 0));
        assertNull(translator.toResponse(counter, new int[] {1, 1}, 0), "short of the total");
        assertNull(translator.toResponse(counter, new int[] {3, 0}, 0), "over a card's stock");
    }

    @Test
    void announceKindsRoundTrip()
    {
        // Attribute: bit 0 (EARTH) and bit 3 (FIRE) offered, pick one.
        DuelMessage.AnnounceBits attribute = new DuelMessage.AnnounceBits(0, 1, 0b1001, false);
        EnginePrompt prompt = translator.toPrompt(attribute, BoardSnapshot.EMPTY);
        assertEquals(2, prompt.options().size());
        assertArrayEquals(new byte[] {8, 0, 0, 0}, translator.toResponse(attribute, new int[] {1}, 0),
            "second option is bit 3 = 0x8");

        // Card declare: only the known card passes an empty always-true filter.
        DuelMessage.AnnounceCard announce = new DuelMessage.AnnounceCard(0, new long[] {1});
        assertEquals(EnginePrompt.Kind.DECLARE_CARD, translator.toPrompt(announce, BoardSnapshot.EMPTY).kind());
        assertArrayEquals(new byte[] {(byte)0xE9, 3, 0, 0}, translator.toResponse(announce, new int[0], 1001));
        assertNull(translator.toResponse(announce, new int[0], 999), "unknown card refused");

        // Number and RPS.
        DuelMessage.AnnounceNumber number = new DuelMessage.AnnounceNumber(0, new long[] {2, 4, 6});
        assertArrayEquals(new byte[] {1, 0, 0, 0}, translator.toResponse(number, new int[] {1}, 0));
        DuelMessage.RockPaperScissors rps = new DuelMessage.RockPaperScissors(0);
        assertArrayEquals(new byte[] {3, 0, 0, 0}, translator.toResponse(rps, new int[] {2}, 0), "scissors = 3");
    }
}
