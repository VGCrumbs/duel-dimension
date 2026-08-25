package de.cas_ual_ty.dueldimension.ocg;

/**
 * Static card data as the rules engine needs it, in the numeric encoding of
 * ocgcore (see OcgConstants TYPE_/ATTRIBUTE_/RACE_/LINK_MARKER_ values).
 * This is the Java-side equivalent of one row of a BabelCDB "datas" table.
 *
 * @param code       card passcode (primary key, matches DuelDimension's card id)
 * @param alias      passcode of the original card if this is an alt art, else 0
 * @param setcodes   archetype setcodes (uint16 each), empty if none
 * @param type       TYPE_* bitfield
 * @param level      level/rank/link rating (low byte); pendulum scales are NOT stored here
 * @param attribute  ATTRIBUTE_* bitfield
 * @param race       RACE_* bitfield
 * @param attack     ATK, -2 for '?'
 * @param defense    DEF, -2 for '?'; link monsters use 0
 * @param lscale     left pendulum scale
 * @param rscale     right pendulum scale
 * @param linkMarker LINK_MARKER_* bitfield
 */
public record OcgCard(int code, int alias, int[] setcodes, int type, int level, int attribute,
                      long race, int attack, int defense, int lscale, int rscale, int linkMarker)
{
    public static final int[] NO_SETCODES = new int[0];
}
