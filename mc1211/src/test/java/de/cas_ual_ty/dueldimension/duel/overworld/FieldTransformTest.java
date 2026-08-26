package de.cas_ual_ty.dueldimension.duel.overworld;

import de.cas_ual_ty.dueldimension.clientutil.FieldLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mapping from EDOPro's field units to blocks in the world.
 * <p>
 * The properties worth pinning are the ones a rendering bug would quietly
 * violate: that the mat lands inside the ground that was validated for it, that
 * a seat's own cards are the ones nearest that seat, and that world and field
 * coordinates round-trip -- because the picker converts one way and the drawing
 * converts the other, and a board where you cannot click what you can see is a
 * board where those two disagreed.
 */
public class FieldTransformTest
{
    private static final FieldSpec SPEC = FieldSpec.DEFAULT;

    private static FieldTransform transform(Direction facing)
    {
        return new FieldTransform(new FieldSiting(new BlockPos(0, 63, 0), facing, SPEC));
    }

    @Test
    public void theMatIsCentredOnTheAnchorAndFitsTheValidatedArea()
    {
        FieldTransform transform = transform(Direction.EAST);
        FieldLayout.Rect mat = FieldTransform.mat();
        Vec3[] corners = transform.corners(mat, 0D);

        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        for(Vec3 corner : corners)
        {
            minX = Math.min(minX, corner.x);
            maxX = Math.max(maxX, corner.x);
            minZ = Math.min(minZ, corner.z);
            maxZ = Math.max(maxZ, corner.z);
        }

        // Facing east: the mat's width runs north-south (across) and its depth
        // runs east-west (along the facing). Measured from the spec, because
        // the field is a setting and these numbers move with it.
        assertEquals(SPEC.matWidth(), maxZ - minZ, 1e-5D, "the mat spans the area across");
        assertEquals(SPEC.matDepth(), maxX - minX, 1e-5D,
            "and leaves the duellists room along it");
        assertEquals(0.5D, (minX + maxX) / 2D, 1e-6D, "centred on the anchor block");
        assertEquals(0.5D, (minZ + maxZ) / 2D, 1e-6D, "centred on the anchor block");
    }

    /**
     * Every corner of the mat has to land on ground the validator actually
     * checked. If the board outgrows the footprint, a duel can be sited on a
     * cliff edge and drawn out over the drop.
     * <p>
     * Measured in continuous coordinates, not block coordinates: the area 9
     * blocks wide reaches 4.5 either side of the anchor's centre and the mat
     * spans exactly that, so the corner sits on the outer face of the last
     * block. Asking which block contains that point answers "the next one",
     * which is a fact about boundaries and not about overhang.
     */
    @Test
    public void theMatNeverLeavesTheFootprint()
    {
        for(Direction facing : Direction.Plane.HORIZONTAL)
        {
            FieldTransform transform = transform(facing);
            FieldSpec spec = transform.siting().spec();
            double halfWidth = spec.areaWidth() / 2D + 0.5D;
            double halfDepth = spec.areaDepth() / 2D + 0.5D;
            for(Vec3 corner : transform.matCorners(0D))
            {
                double dx = corner.x - (transform.siting().anchor().getX() + 0.5D);
                double dz = corner.z - (transform.siting().anchor().getZ() + 0.5D);
                double across = dx * transform.right().getStepX() + dz * transform.right().getStepZ();
                double along = dx * facing.getStepX() + dz * facing.getStepZ();
                assertTrue(Math.abs(across) <= halfWidth + 1e-6D,
                    "facing " + facing + ": mat reaches " + across + " across a "
                        + spec.areaWidth() + "-block area");
                assertTrue(Math.abs(along) <= halfDepth + 1e-6D,
                    "facing " + facing + ": mat reaches " + along + " along a "
                        + spec.areaDepth() + "-block area");
            }
        }
    }

    /** Your own monsters are the ones in front of you, not across the board. */
    @Test
    public void aSeatsOwnRowIsTheRowNearestThatSeat()
    {
        FieldTransform transform = transform(Direction.EAST);
        BlockPos standZero = transform.siting().stand(0);
        BlockPos standOne = transform.siting().stand(1);

        // Controller 0's monster row, from EDOPro's own table.
        FieldLayout.Rect mine = FieldLayout.zone(0, de.cas_ual_ty.dueldimension.ocg.OcgConstants
            .LOCATION_MZONE, 2);
        FieldLayout.Rect theirs = FieldLayout.zone(1, de.cas_ual_ty.dueldimension.ocg.OcgConstants
            .LOCATION_MZONE, 2);
        Vec3 mineAt = transform.at(mine.x() + mine.w() / 2F, mine.y() + mine.h() / 2F, 0D);
        Vec3 theirsAt = transform.at(theirs.x() + theirs.w() / 2F, theirs.y() + theirs.h() / 2F, 0D);

        double mineToZero = mineAt.distanceToSqr(standZero.getX() + 0.5D, transform.surfaceY(),
            standZero.getZ() + 0.5D);
        double mineToOne = mineAt.distanceToSqr(standOne.getX() + 0.5D, transform.surfaceY(),
            standOne.getZ() + 0.5D);
        assertTrue(mineToZero < mineToOne,
            "controller 0's monsters must sit at seat 0's end of the board");

        double theirsToOne = theirsAt.distanceToSqr(standOne.getX() + 0.5D, transform.surfaceY(),
            standOne.getZ() + 0.5D);
        double theirsToZero = theirsAt.distanceToSqr(standZero.getX() + 0.5D, transform.surfaceY(),
            standZero.getZ() + 0.5D);
        assertTrue(theirsToOne < theirsToZero,
            "controller 1's monsters must sit at seat 1's end of the board");
    }

    @Test
    public void worldAndFieldCoordinatesRoundTrip()
    {
        for(Direction facing : Direction.Plane.HORIZONTAL)
        {
            FieldTransform transform = transform(facing);
            for(float fx = -1F; fx <= 9F; fx += 1.7F)
            {
                for(float fy = -4F; fy <= 4F; fy += 1.3F)
                {
                    float[] back = transform.toField(transform.at(fx, fy, 0D));
                    assertEquals(fx, back[0], 1e-4F, "x round trip, facing " + facing);
                    assertEquals(fy, back[1], 1e-4F, "y round trip, facing " + facing);
                }
            }
        }
    }

    @Test
    public void offTheMatIsOffTheMat()
    {
        FieldTransform transform = transform(Direction.SOUTH);

        assertTrue(FieldTransform.onMat(transform.toField(transform.at(4F, 0F, 0D))));
        assertTrue(FieldTransform.onMat(transform.toField(transform.at(-1F, -4F, 0D))));
        assertFalse(FieldTransform.onMat(transform.toField(transform.at(9.5F, 0F, 0D))));
        assertFalse(FieldTransform.onMat(transform.toField(transform.at(4F, 4.5F, 0D))));
    }

    /**
     * The board is one object that both duellists walk around, so "self" has to
     * become an absolute half before anything is drawn.
     */
    @Test
    public void eachSeatsOwnCardsGoOnItsOwnHalf()
    {
        assertEquals(0, FieldTransform.controllerFor(0, true));
        assertEquals(1, FieldTransform.controllerFor(0, false));
        assertEquals(1, FieldTransform.controllerFor(1, true));
        assertEquals(0, FieldTransform.controllerFor(1, false));
    }

    @Test
    public void theBoardSitsOnTopOfTheFloorBlock()
    {
        assertEquals(64D, transform(Direction.WEST).surfaceY(), 1e-9D);
    }
}
