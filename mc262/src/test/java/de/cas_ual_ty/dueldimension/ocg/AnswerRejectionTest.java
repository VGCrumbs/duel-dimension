package de.cas_ual_ty.dueldimension.ocg;

import de.cas_ual_ty.dueldimension.ocg.prompt.EnginePrompt;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * What happens when the core refuses an answer.
 * <p>
 * The core validates a response itself and emits MSG_RETRY when it will not
 * take one — a tribute summon short a tribute, a sum that does not add up. The
 * duel loop responds to the <em>most recent message</em>, so a rejection that
 * was allowed to become that message made the loop ask a responder to answer
 * the rejection: a message it cannot read, whose first payload byte —
 * meaning nothing — chose which player was being asked.
 * <p>
 * These check the two facts that fix depends on, without needing a live core:
 * that MSG_RETRY is distinguishable, and that a prompt can be re-put with an
 * explanation attached rather than reappearing unchanged.
 */
class AnswerRejectionTest
{
    @Test
    void aRejectionIsTellableFromAPrompt()
    {
        RawMessage retry = RawMessage.of(new byte[] {(byte)OcgConstants.MSG_RETRY});
        assertEquals(OcgConstants.MSG_RETRY, retry.type());
        // The value that used to pick the player. It is the message type's own
        // payload, not a player index, which is exactly why answering the
        // rejection chose a player at random.
        assertNotEquals(OcgConstants.MSG_SELECT_IDLECMD, retry.type());
    }

    @Test
    void aRejectedPromptCanBeReAskedWithAReason()
    {
        EnginePrompt original = new EnginePrompt(EnginePrompt.Kind.CHOOSE, "Select tributes",
            List.of(new EnginePrompt.Option("Dark Magician")), 1, 1, false, null);

        EnginePrompt again = original.withTitle("Not allowed: " + original.title());

        assertEquals("Not allowed: Select tributes", again.title());
        // Everything else is the same question: the same options, the same
        // limits. Only the heading changed.
        assertEquals(original.options(), again.options());
        assertEquals(original.kind(), again.kind());
        assertEquals(original.minSelect(), again.minSelect());
        assertEquals(original.maxSelect(), again.maxSelect());
        assertEquals(original.cancelable(), again.cancelable());
        assertSame(original.field(), again.field());
    }

    @Test
    void anUntitledPromptGetsAReasonRatherThanAnEmptyHeading()
    {
        EnginePrompt blank = new EnginePrompt(EnginePrompt.Kind.CHOOSE, "",
            List.of(new EnginePrompt.Option("Summon")), 1, 1, false, null);
        // An idle command prompt carries no title, so prefixing one would leave
        // "Not allowed: " trailing into nothing.
        EnginePrompt again = blank.withTitle("That move was not allowed");
        assertEquals("That move was not allowed", again.title());
    }
}
