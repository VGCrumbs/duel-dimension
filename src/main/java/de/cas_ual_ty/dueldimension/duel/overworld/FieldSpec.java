package de.cas_ual_ty.dueldimension.duel.overworld;

import de.cas_ual_ty.dueldimension.clientutil.FieldLayout;

/**
 * How much ground an overworld duel needs, how big the board drawn on it is,
 * and how forgiving the siting is about finding somewhere to put them.
 * <p>
 * Every number the feature uses lives here rather than being written into
 * whichever class needed it first. The footprint, the placement indicators, the
 * stand positions, the world transform and the "someone built in the field"
 * check all measure against one record, so a board that validated cannot later
 * disagree with the board that is drawn.
 * <p>
 * <b>Nothing here is a constant.</b> The whole record is loaded from
 * {@link OverworldSettings} and can be changed in game, so the board can be
 * resized without a recompile; and because the whole spec travels to the client
 * with the field, a board is always drawn to the size it was validated at, even
 * if the setting changes while a duel is running.
 *
 * @param areaWidth          blocks across, left to right as a duellist sees it
 * @param areaDepth          blocks between the two duellists
 * @param clearance          blocks of air the area needs above its floor
 * @param matScale           blocks per EDOPro field unit; the board's size
 * @param lateralTolerance   how far off the shared axis a duellist may stand
 *                           and still count as straight across
 * @param elevationTolerance blocks of height difference allowed between the two
 *                           duellists before the ground is called uneven
 * @param maxSeparation      beyond this the two are not duelling in the world
 *                           at all, however much space is between them
 * @param searchRadius       how far the search may move the board away from
 *                           where the players are standing
 * @param verticalSearch     how far up or down the search may look for a floor
 */
public record FieldSpec(int areaWidth, int areaDepth, int clearance, float matScale,
    int lateralTolerance, int elevationTolerance, int maxSeparation, int searchRadius,
    int verticalSearch)
{
    /** The mat's extent in EDOPro field units, from EDOPro's own vertex table. */
    public static final float MAT_UNITS_WIDE = FieldLayout.FIELD_MAX_X - FieldLayout.FIELD_MIN_X;
    public static final float MAT_UNITS_DEEP = FieldLayout.FIELD_MAX_Y - FieldLayout.FIELD_MIN_Y;

    /** Bounds every setting is clamped into, so a hand-edited file cannot break the game. */
    public static final int MIN_SPAN = 5;
    public static final int MAX_SPAN = 63;
    public static final int MAX_CLEARANCE = 32;
    public static final int MAX_SEARCH = 24;

    /**
     * Clamped and squared off on the way in, so nothing downstream has to
     * re-check it:
     * <ul>
     * <li>the spans are forced odd, because the area is centred on a block and
     * an even span has no centre block to be centred on;</li>
     * <li>the mat is never allowed to be bigger than the ground that was
     * validated for it -- that invariant is the whole point of validating, and
     * a config file is exactly where it would otherwise get broken.</li>
     * </ul>
     */
    public FieldSpec
    {
        areaWidth = odd(Math.clamp(areaWidth, MIN_SPAN, MAX_SPAN));
        areaDepth = odd(Math.clamp(areaDepth, MIN_SPAN, MAX_SPAN));
        clearance = Math.clamp(clearance, 1, MAX_CLEARANCE);
        lateralTolerance = Math.clamp(lateralTolerance, 0, MAX_SPAN);
        elevationTolerance = Math.clamp(elevationTolerance, 0, MAX_SPAN);
        maxSeparation = Math.clamp(maxSeparation, 2, 128);
        searchRadius = Math.clamp(searchRadius, 0, MAX_SEARCH);
        verticalSearch = Math.clamp(verticalSearch, 0, MAX_CLEARANCE);
        matScale = Math.clamp(matScale, 0.05F, fittingScale(areaWidth, areaDepth));
    }

    /** The largest board that fits inside an area of this size, in blocks per field unit. */
    public static float fittingScale(int areaWidth, int areaDepth)
    {
        return Math.min(areaWidth / MAT_UNITS_WIDE, areaDepth / MAT_UNITS_DEEP);
    }

    /** An area of this size with the largest board that fits in it. */
    public static FieldSpec fitting(int areaWidth, int areaDepth, int clearance,
        int lateralTolerance, int elevationTolerance, int maxSeparation, int searchRadius,
        int verticalSearch)
    {
        return new FieldSpec(areaWidth, areaDepth, clearance,
            fittingScale(odd(areaWidth), odd(areaDepth)), lateralTolerance, elevationTolerance,
            maxSeparation, searchRadius, verticalSearch);
    }

    /**
     * Eleven across by nine deep, with the board filling it.
     * <p>
     * The two spans do different jobs, which is why they are no longer equal.
     * The DEPTH sets how far apart the duellists stand ({@link #separation} is
     * {@code areaDepth + 1}, so nine keeps them the ten blocks apart they have
     * always been). The WIDTH only has to cover the board, so widening it to
     * eleven makes the board bigger without moving anybody: the mat goes from
     * 9.0 x 7.2 blocks to 11.0 x 8.8, roughly a block further out on every
     * side, while the walk to the mark is unchanged.
     * <p>
     * The board can grow this way precisely because the ground it grew onto is
     * validated with it -- a board is never allowed to be larger than the area
     * that was checked for it, which is the one invariant this record enforces
     * itself.
     * <p>
     * The separation limit is twenty because that is how far a worn duel disk
     * carries a challenge ({@code DuelReach.CHALLENGE_RANGE}). The two numbers
     * have to agree: a challenge you can shout across a courtyard and then
     * cannot hold a board duel over is a feature refusing itself.
     * <p>
     * The search radius went from eight to twelve when the field widened. A
     * bigger field is refused by more places, so it has to look at more of
     * them; the floor scan is remembered across facings, so the extra reach
     * costs far less than it once would have.
     */
    public static final FieldSpec DEFAULT = fitting(11, 9, 2, 1, 1, 20, 12, 3);

    /** The spec the server is currently siting duels with. */
    public static FieldSpec current()
    {
        return OverworldSettings.spec();
    }

    /** How many blocks apart the two duellists stand, standing just clear of the area. */
    public int separation()
    {
        return areaDepth + 1;
    }

    /** Half the area, in blocks, from the anchor to an edge cell inclusive. */
    public int halfWidth()
    {
        return areaWidth / 2;
    }

    /** Half the area, in blocks, from the anchor to an edge cell inclusive. */
    public int halfDepth()
    {
        return areaDepth / 2;
    }

    /** Blocks per EDOPro field unit. */
    public float blocksPerFieldUnit()
    {
        return matScale;
    }

    /** The board's width in blocks. */
    public float matWidth()
    {
        return MAT_UNITS_WIDE * matScale;
    }

    /** The board's depth in blocks, which is less than the area's: the duellists stand in the rest. */
    public float matDepth()
    {
        return MAT_UNITS_DEEP * matScale;
    }

    private static int odd(int value)
    {
        return (value & 1) == 1 ? value : value + 1;
    }
}
