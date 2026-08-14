package de.cas_ual_ty.dueldimension.clientutil;

/**
 * A thing on the duel field that a player has picked out, with no opinion about
 * how they picked it.
 * <p>
 * {@code BoardRenderer.Hit} carries a rectangle on a screen; a duellist looking
 * at a board in the world has no rectangle, only a ray. What both produce is
 * this: which card, in which zone, on whose side. The legality filter takes one
 * of these, so the 2D screen and the 3D board ask their question in the same
 * words -- and therefore get the same answer, which is the whole point of not
 * writing a second one.
 *
 * @param code       the card's passcode, or 0 for something with no identity
 * @param controller whose side of the field it is on
 * @param location   an ocgcore LOCATION_ constant
 * @param sequence   which zone within that location; negative for a pile
 * @param zoneRef    the engine's own zone reference, for a PLACES prompt
 * @param label      what to call it, for a tooltip or a menu heading
 * @param count      how many cards, for a pile
 * @param art        which artwork this copy is wearing
 */
public record BoardTarget(int code, int controller, int location, int sequence, int zoneRef,
    String label, int count, int art)
{
    /**
     * A pile rather than a single card. Same test the 2D board uses: a pile has
     * no sequence within its location, so the sequence is negative.
     */
    public boolean isPile()
    {
        return sequence < 0;
    }

    /**
     * Is there actually a card here, or is this an empty square?
     * <p>
     * The count carries it already: a zone is built holding one when it is
     * occupied and none when it is not, a pile with however many it has, and a
     * card in hand with one. Worth its own name because the answer decides
     * whether clicking asks a question or simply answers one -- a card has
     * things that can be DONE to it, and an empty square is not a thing at all,
     * it is the answer to "where".
     */
    public boolean hasCard()
    {
        return count > 0;
    }

    /** Do these two point at the same thing on the field? */
    public boolean sameSlot(BoardTarget other)
    {
        return other != null && controller == other.controller && location == other.location
            && sequence == other.sequence;
    }
}
