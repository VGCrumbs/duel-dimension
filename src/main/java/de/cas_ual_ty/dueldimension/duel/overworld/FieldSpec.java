package de.cas_ual_ty.dueldimension.duel.overworld;

import de.cas_ual_ty.dueldimension.clientutil.FieldLayout;

/**
 * How much ground an overworld duel needs, and how forgiving it is about
 * finding it.
 * <p>
 * Every number the siting code uses lives here rather than being written into
 * the class that happens to need it first. The footprint, the placement
 * indicators, the stand positions, the world transform and the phase-16
 * "someone built in the field" check all measure against the same record, so a
 * board that validated cannot later disagree with the board that is drawn.
 * <p>
 * The board's scale is <em>derived</em> from {@link #areaWidth} rather than
 * chosen separately: {@link FieldLayout} is EDOPro's own vertex table and says
 * the mat spans ten field units across, so a nine-block-wide area fixes the
 * scale at 0.9 blocks per field unit and nothing else is free to pick a
 * different one.
 *
 * @param areaWidth          blocks across, left to right as a duellist sees it
 * @param areaDepth          blocks between the two duellists
 * @param clearance          blocks of air the area needs above its floor
 * @param lateralTolerance   how far off the shared axis a duellist may stand
 *                           and still count as straight across
 * @param elevationTolerance blocks of height difference allowed between the
 *                           two duellists before the ground is called uneven
 * @param maxSeparation      beyond this the two are not duelling in the world
 *                           at all, however much space is between them
 * @param searchRadius       how far the search may move the board away from
 *                           where the players are standing
 * @param verticalSearch     how far up or down the search may look for a floor
 */
public record FieldSpec(int areaWidth, int areaDepth, int clearance, int lateralTolerance,
    int elevationTolerance, int maxSeparation, int searchRadius, int verticalSearch)
{
    /**
     * The area is centred on its anchor block, so both spans have to be odd --
     * an even one has no centre block to be anchored at, and every consumer
     * would then have to agree on which way to round.
     */
    public FieldSpec
    {
        if(areaWidth % 2 == 0 || areaDepth % 2 == 0)
        {
            throw new IllegalArgumentException(
                "the duel area is centred on a block, so its spans must be odd: "
                    + areaWidth + "x" + areaDepth);
        }
    }

    /**
     * Nine by nine, which is the smallest area the mat fits in, with three
     * blocks of headroom.
     * <p>
     * The tolerances are deliberately tight: a duel that begins with the board
     * visibly crooked reads as broken, and the alternative to a tight tolerance
     * is not a refusal but a short walk to a marked spot.
     */
    public static final FieldSpec DEFAULT = new FieldSpec(9, 9, 3, 1, 1, 16, 8, 3);

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

    /**
     * Blocks per EDOPro field unit, derived from the width the mat has to fit
     * across. Not a free parameter: the mat is ten units wide by construction
     * ({@link FieldLayout#FIELD_MIN_X}..{@link FieldLayout#FIELD_MAX_X}), so
     * asking for a nine-block area IS asking for 0.9.
     */
    public float blocksPerFieldUnit()
    {
        return areaWidth / (FieldLayout.FIELD_MAX_X - FieldLayout.FIELD_MIN_X);
    }

    /** The mat's depth in blocks, which is less than the area's: 8 units at the scale above. */
    public float matDepth()
    {
        return (FieldLayout.FIELD_MAX_Y - FieldLayout.FIELD_MIN_Y) * blocksPerFieldUnit();
    }
}
