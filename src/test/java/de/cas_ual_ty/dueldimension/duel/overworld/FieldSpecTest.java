package de.cas_ual_ty.dueldimension.duel.overworld;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The field's size is a setting, not a constant, so the things that used to be
 * true by construction have to be true by clamping instead.
 * <p>
 * The invariant that matters is that the board never outgrows the ground that
 * was validated for it. Anything else is a matter of taste and can be typed
 * into a config file; that one cannot, because a board hanging over a cliff is
 * a board whose duel was checked against ground that is not under it.
 */
public class FieldSpecTest
{
    @Test
    public void theShippedDefaultIsNineByNineWithTheBoardFillingIt()
    {
        FieldSpec spec = FieldSpec.DEFAULT;

        assertEquals(9, spec.areaWidth());
        assertEquals(9, spec.areaDepth());
        assertEquals(0.9F, spec.matScale(), 1e-6F);
        assertEquals(9.0F, spec.matWidth(), 1e-5F);
        assertEquals(7.2F, spec.matDepth(), 1e-5F);
    }

    /**
     * A span with no centre block has no anchor to be centred on, so it is
     * rounded up rather than refused: this arrives from a hand-edited config
     * file, and a game that will not start because someone typed 8 is worse
     * than a field one block wider than they asked for.
     */
    @Test
    public void anEvenSpanIsRoundedUpRatherThanRefused()
    {
        FieldSpec spec = FieldSpec.fitting(8, 12, 3, 1, 1, 16, 8, 3);

        assertEquals(9, spec.areaWidth());
        assertEquals(13, spec.areaDepth());
    }

    @Test
    public void aBiggerAreaGivesABiggerBoard()
    {
        FieldSpec small = FieldSpec.fitting(9, 9, 3, 1, 1, 16, 8, 3);
        FieldSpec large = FieldSpec.fitting(21, 21, 3, 1, 1, 16, 8, 3);

        assertTrue(large.matScale() > small.matScale());
        assertEquals(21F, large.matWidth(), 1e-4F, "the board fills the width it was given");
    }

    /** A wide, shallow area is limited by its depth, not by its width. */
    @Test
    public void theBoardIsCappedByWhicheverDimensionRunsOutFirst()
    {
        FieldSpec shallow = FieldSpec.fitting(31, 9, 3, 1, 1, 16, 8, 3);

        assertEquals(9F / FieldSpec.MAT_UNITS_DEEP, shallow.matScale(), 1e-6F);
        assertTrue(shallow.matWidth() < shallow.areaWidth(),
            "a board that filled this width would run off both ends of the depth");
    }

    /**
     * The cap is on the record itself, so it holds however the spec is built --
     * including one decoded from a packet, where the numbers are whatever
     * arrived.
     */
    @Test
    public void aBoardCanNeverBeBiggerThanTheGroundCheckedForIt()
    {
        FieldSpec absurd = new FieldSpec(9, 9, 3, 1000F, 1, 1, 16, 8, 3);

        assertTrue(absurd.matWidth() <= absurd.areaWidth(), "board wider than its area");
        assertTrue(absurd.matDepth() <= absurd.areaDepth(), "board deeper than its area");
    }

    @Test
    public void nonsenseIsClampedRatherThanTrusted()
    {
        FieldSpec nonsense = new FieldSpec(-40, 9999, -1, -5F, -3, -3, 0, 9999, -1);

        assertTrue(nonsense.areaWidth() >= FieldSpec.MIN_SPAN);
        assertTrue(nonsense.areaDepth() <= FieldSpec.MAX_SPAN);
        assertTrue(nonsense.clearance() >= 1);
        assertTrue(nonsense.matScale() > 0F);
        assertTrue(nonsense.searchRadius() <= FieldSpec.MAX_SEARCH);
    }

    /**
     * Resizing the field moves the four points the board is drawn between, and
     * nothing else has to be told: that is the whole reason the board is
     * defined as four points rather than as a size somebody remembers.
     */
    @Test
    public void theBoardsFourCornersFollowItsSize()
    {
        FieldSiting small = new FieldSiting(new BlockPos(0, 63, 0), Direction.EAST,
            FieldSpec.fitting(9, 9, 3, 1, 1, 16, 8, 3));
        FieldSiting large = new FieldSiting(new BlockPos(0, 63, 0), Direction.EAST,
            FieldSpec.fitting(21, 21, 3, 1, 1, 16, 8, 3));

        Vec3[] smallCorners = new FieldTransform(small).matCorners(0D);
        Vec3[] largeCorners = new FieldTransform(large).matCorners(0D);

        assertEquals(4, smallCorners.length);
        for(int corner = 0; corner < 4; corner++)
        {
            assertTrue(largeCorners[corner].distanceToSqr(0.5D, 64D, 0.5D)
                > smallCorners[corner].distanceToSqr(0.5D, 64D, 0.5D),
                "corner " + corner + " did not move outwards when the field grew");
        }
    }

    /**
     * Why the search only tries three facings and not four. A field and the
     * same field turned around are accepted or refused together, so trying both
     * was half the search's block reads spent re-deciding.
     */
    @Test
    public void aFieldAndItsOppositeAreAlwaysJudgedTheSame()
    {
        java.util.Set<BlockPos> obstacles = new java.util.HashSet<>();
        obstacles.add(new BlockPos(2, 64, 1));
        obstacles.add(new BlockPos(-3, 64, -2));
        BlockSampler world = new BlockSampler()
        {
            @Override
            public boolean isClear(BlockPos pos)
            {
                return pos.getY() > 63 && !obstacles.contains(pos);
            }

            @Override
            public boolean isGround(BlockPos pos)
            {
                return pos.getY() <= 63;
            }
        };

        for(Direction facing : Direction.Plane.HORIZONTAL)
        {
            FieldSiting siting = new FieldSiting(new BlockPos(0, 63, 0), facing,
                FieldSpec.DEFAULT);
            assertEquals(FieldValidator.check(world, siting),
                FieldValidator.check(world, siting.flipped()),
                "facing " + facing + " and its opposite disagreed");
        }
    }
}
