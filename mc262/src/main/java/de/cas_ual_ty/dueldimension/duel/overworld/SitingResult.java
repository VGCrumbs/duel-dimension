package de.cas_ual_ty.dueldimension.duel.overworld;

import de.cas_ual_ty.dueldimension.duel.overworld.FieldValidator.Refusal;

/**
 * What the siting search decided, in the three shapes the feature actually
 * distinguishes: begin now, walk there first, or it cannot be done here.
 * <p>
 * Sealed because those three are the whole space -- a fourth answer would be a
 * new behaviour, and the compiler should insist someone decides what it means
 * rather than letting it fall through a default branch into "begin now".
 */
public sealed interface SitingResult
{
    /**
     * The pair are already standing correctly and the ground is good: the duel
     * may start immediately, with only the small snap the lock performs.
     */
    record Ready(FieldSiting siting) implements SitingResult
    {
    }

    /**
     * A field fits, but at least one duellist is not standing in it yet. The
     * markers go down and the duel waits.
     */
    record Move(FieldSiting siting) implements SitingResult
    {
    }

    /** No field fits, and the duel falls back to the ordinary screen. */
    record Refused(Refusal reason) implements SitingResult
    {
    }

    /** The field this result describes, or null when refused. */
    default FieldSiting siting()
    {
        if(this instanceof Ready ready)
        {
            return ready.siting();
        }
        return this instanceof Move move ? move.siting() : null;
    }
}
