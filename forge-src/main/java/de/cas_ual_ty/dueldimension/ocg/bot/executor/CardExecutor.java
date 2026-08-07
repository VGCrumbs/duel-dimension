package de.cas_ual_ty.dueldimension.ocg.bot.executor;

import java.util.function.BooleanSupplier;

/**
 * One registered rule: "for this action kind, on this card, when this holds".
 * <p>
 * Transliterated from {@code ExecutorBase/Game/AI/CardExecutor.cs}:
 * <pre>
 * public class CardExecutor
 * {
 *     public int CardId { get; private set; }
 *     public ExecutorType Type { get; private set; }
 *     public Func&lt;bool&gt; Func { get; private set; }
 * }
 * </pre>
 *
 * @param type   the action this rule authorises
 * @param cardId the passcode it applies to, or {@link #ANY} for every card
 * @param func   the condition, or null meaning "always" — a null Func is
 *               WindBot's way of writing an unconditional rule, as
 *               {@code AddExecutor(ExecutorType.Activate, CardId.Fissure)}
 *               does in OldSchoolExecutor
 */
public record CardExecutor(ExecutorType type, int cardId, BooleanSupplier func)
{
    /** {@code exec.CardId == -1}: the wildcard that matches any card. */
    public static final int ANY = -1;
}
