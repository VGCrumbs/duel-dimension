package de.cas_ual_ty.dueldimension.clientutil.overworld;

/**
 * How tall a pile of cards stands, and how its edge is striped.
 * <p>
 * A deck, a graveyard, a banished pile and an extra deck are drawn as ONE solid
 * the height of the whole stack rather than as a card apiece: forty cards is
 * forty solids and two hundred and forty quads for something whose inside is
 * never seen. That is what the 2D board does too, and it is what EDOPro does --
 * {@code client_field.cpp} raises each pile card by 0.01 of Z and stripes the
 * side.
 * <p>
 * The one thing that cannot be done the obvious way is the stripe. The card-side
 * texture is a white row over a grey row meant to repeat once per card, and
 * <b>the wrap mode here is not repeat</b> -- a v of 40 stretches the stripe
 * rather than tiling it. The 2D board discovered that and slices the face by
 * hand, one quad per card; so does this.
 */
public final class PileMesh
{
    private PileMesh()
    {
    }

    /**
     * Height added per card, in field units. EDOPro's own 0.01, unflattened:
     * the 2D board uses 0.008 because it maps the field onto a screen at a
     * shallower angle, and a board standing in the world does not.
     */
    public static final float LIFT_PER_CARD = 0.01F;

    /**
     * Past this the stack stops growing. A deck of sixty and a graveyard of
     * forty already read as "a lot", and an uncapped pile would stand half a
     * block proud of the board and put its top card out of reach of a duellist
     * looking down at it.
     */
    public static final int LIFT_CAP = 45;

    /** How tall this many cards stand, in field units. */
    public static float height(int count)
    {
        return Math.min(Math.max(count, 0), LIFT_CAP) * LIFT_PER_CARD;
    }

    /**
     * How many stripes the side is cut into: one per card, capped, and at least
     * one so a pile of a single card still has an edge.
     */
    public static int stripes(int count)
    {
        return Math.max(1, Math.min(Math.max(count, 0), LIFT_CAP));
    }
}
