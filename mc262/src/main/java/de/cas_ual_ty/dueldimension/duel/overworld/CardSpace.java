package de.cas_ual_ty.dueldimension.duel.overworld;

import net.minecraft.world.phys.Vec3;

/**
 * Somewhere a card can be drawn: a mapping from EDOPro's field units to a place
 * in the world.
 * <p>
 * Pulled out of {@link FieldTransform} because a card is not only ever on a
 * duel field. A card lying on a pedestal in somebody's museum is the same six
 * quads with the same textures and the same thickness -- the only thing that
 * differs is where the field units land, and that is precisely these two
 * methods. {@code CardRenderer} asked for a FieldTransform and used exactly
 * this much of it.
 * <p>
 * Two methods rather than one because the lift is in BLOCKS while the plane is
 * in field units, and the thing that knows how to convert between them is the
 * thing that knows its own scale.
 */
public interface CardSpace
{
    /**
     * A point on the card's plane, in world coordinates.
     *
     * @param fx   field x, across
     * @param fy   field y, away
     * @param lift blocks above the plane
     */
    Vec3 at(float fx, float fy, double lift);

    /** Blocks per field unit. */
    float scale();
}
