package de.cas_ual_ty.dueldimension.duel.overworld;

import de.cas_ual_ty.dueldimension.clientutil.FieldLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * The one mapping between EDOPro's field units and blocks in the world.
 * <p>
 * Everything the overworld board draws, everything it picks, and the footprint
 * that was validated for it all pass through here, so the board cannot be drawn
 * anywhere other than the ground that was checked for it. It deliberately does
 * NOT call {@link FieldLayout#fit} or its projection: that one maps the field
 * onto a 2D screen, and reusing it would tie the world board to the GUI's
 * letterboxing.
 * <p>
 * What it does reuse is {@link FieldLayout}'s vertex table, which is EDOPro's
 * own -- the zone rectangles, the 1.1-unit column pitch and the mat's extent
 * are that table's numbers, not ours. FieldLayout is pure arithmetic with no
 * Minecraft import, so the server may read it as freely as the client.
 * <p>
 * <b>Axes.</b> Field x runs across the board and maps to the siting's right;
 * field y runs from the opponent's end to yours and maps to the NEGATIVE
 * facing, because {@link FieldLayout} puts controller 0's own rows at positive
 * y and the siting's facing points away from seat 0. Get that sign wrong and
 * every card is on the wrong side of the board, which is the kind of mistake
 * that looks like a rendering bug for a day.
 */
public record FieldTransform(FieldSiting siting) implements CardSpace
{
    /** The middle of the mat in field units: the point the anchor block sits under. */
    public static final float CENTRE_X = (FieldLayout.FIELD_MIN_X + FieldLayout.FIELD_MAX_X) / 2F;
    public static final float CENTRE_Y = (FieldLayout.FIELD_MIN_Y + FieldLayout.FIELD_MAX_Y) / 2F;

    /** Blocks per field unit. */
    public float scale()
    {
        return siting().spec().blocksPerFieldUnit();
    }

    /**
     * The world height of the board's surface: the top face of the anchor
     * block, plus however far the board has been raised.
     * <p>
     * The lift is presentation only -- the ground that was validated is still
     * the ground under the anchor -- but it goes through here, so the cards,
     * the picker and the drawn board all move together and a board you can see
     * is a board you can point at.
     */
    public double surfaceY()
    {
        return siting().anchor().getY() + 1 + siting().spec().matLift();
    }

    /** Which way is right, looking along the board from seat 0. */
    public Direction right()
    {
        return siting().facing().getClockWise();
    }

    /**
     * A point on the board, in world coordinates.
     *
     * @param fx   field x, from {@link FieldLayout#FIELD_MIN_X} to FIELD_MAX_X
     * @param fy   field y; positive is controller 0's own half
     * @param lift blocks above the board's surface
     */
    public Vec3 at(float fx, float fy, double lift)
    {
        Direction facing = siting().facing();
        Direction right = right();
        BlockPos anchor = siting().anchor();
        double across = (fx - CENTRE_X) * scale();
        double along = -(fy - CENTRE_Y) * scale();
        return new Vec3(
            anchor.getX() + 0.5D + right.getStepX() * across + facing.getStepX() * along,
            surfaceY() + lift,
            anchor.getZ() + 0.5D + right.getStepZ() * across + facing.getStepZ() * along);
    }

    /**
     * Where a world position falls on the board, as field units. The inverse of
     * {@link #at}, and the reason the picker can work in field space where the
     * zone rectangles already live rather than in world space where they do not.
     *
     * @return {x, y} in field units; outside the mat's extent means off the board
     */
    public float[] toField(Vec3 world)
    {
        Direction facing = siting().facing();
        Direction right = right();
        BlockPos anchor = siting().anchor();
        double dx = world.x - (anchor.getX() + 0.5D);
        double dz = world.z - (anchor.getZ() + 0.5D);
        double across = dx * right.getStepX() + dz * right.getStepZ();
        double along = dx * facing.getStepX() + dz * facing.getStepZ();
        return new float[] {(float)(across / scale()) + CENTRE_X,
            (float)(-along / scale()) + CENTRE_Y};
    }

    /** Is this point within the mat's own extent, ignoring height? */
    public static boolean onMat(float[] field)
    {
        return field[0] >= FieldLayout.FIELD_MIN_X && field[0] <= FieldLayout.FIELD_MAX_X
            && field[1] >= FieldLayout.FIELD_MIN_Y && field[1] <= FieldLayout.FIELD_MAX_Y;
    }

    /**
     * The four world corners of a field-unit rectangle, wound so that the first
     * two are its far edge as controller 0 sees it. Order matters: a quad wound
     * the other way faces down into the ground.
     */
    public Vec3[] corners(FieldLayout.Rect rect, double lift)
    {
        float x0 = rect.x();
        float y0 = rect.y();
        float x1 = rect.x() + rect.w();
        float y1 = rect.y() + rect.h();
        return new Vec3[] {at(x0, y0, lift), at(x0, y1, lift), at(x1, y1, lift), at(x1, y0, lift)};
    }

    /**
     * The four world-space corners of the board, in the order near-left,
     * far-left, far-right, near-right as controller 0 sees it.
     * <p>
     * This is the board's definition as far as anything drawing it is
     * concerned: four points in space. Resize the field and these four move;
     * nothing downstream has its own idea of how big the board is.
     */
    public Vec3[] matCorners(double lift)
    {
        return corners(mat(), lift);
    }

    /** The mat itself, as a rectangle in field units. */
    public static FieldLayout.Rect mat()
    {
        return new FieldLayout.Rect(FieldLayout.FIELD_MIN_X, FieldLayout.FIELD_MIN_Y,
            FieldLayout.FIELD_MAX_X - FieldLayout.FIELD_MIN_X,
            FieldLayout.FIELD_MAX_Y - FieldLayout.FIELD_MIN_Y);
    }

    /**
     * Which controller's half of the board a given seat's own cards belong on.
     * <p>
     * The board is one object in the world and both duellists walk around the
     * same one, so it cannot be seat-relative the way {@code BoardSnapshot} is:
     * that is per-seat by construction, with "self" meaning whoever is being
     * served. Seat 0's own cards live on controller 0's half, seat 1's on
     * controller 1's, and this is the one place that translation happens.
     *
     * @param viewerSeat which seat the client rendering this is
     * @param own        true for that client's own cards
     */
    public static int controllerFor(int viewerSeat, boolean own)
    {
        return own ? viewerSeat : 1 - viewerSeat;
    }
}
