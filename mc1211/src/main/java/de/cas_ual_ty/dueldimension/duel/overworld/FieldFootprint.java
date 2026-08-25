package de.cas_ual_ty.dueldimension.duel.overworld;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.function.Consumer;

/**
 * Where a duel field sits in the world, as blocks.
 * <p>
 * One definition with five consumers: the validator that accepts a site, the
 * search that proposes one, the indicators that show it, the transform that
 * draws the board on it, and the integrity check that notices somebody built in
 * it. Because they all enumerate through here, a block that passed validation
 * is by construction the same block the integrity check later watches -- there
 * is no second opinion about what "the field" means.
 * <p>
 * The area is centred on its anchor and squared to the compass: the anchor is
 * the FLOOR block at the centre, so the board's surface is one block above it,
 * and {@code facing} points from seat 0's feet towards seat 1's.
 */
public final class FieldFootprint
{
    private FieldFootprint()
    {
    }

    /**
     * The block a duellist stands on.
     * <p>
     * Just clear of the area rather than inside it, because the requirement is
     * a duel area <em>between</em> the two players; standing in it would put a
     * player's feet on the mat.
     *
     * @param seat 0 stands behind the anchor with respect to {@code facing}, 1 in front
     */
    public static BlockPos stand(BlockPos anchor, Direction facing, FieldSpec spec, int seat)
    {
        int reach = spec.halfDepth() + 1;
        int steps = seat == 0 ? -reach : reach;
        return anchor.offset(facing.getStepX() * steps, 0, facing.getStepZ() * steps);
    }

    /** Which way a seat looks: straight at the board and the other duellist. */
    public static Direction look(Direction facing, int seat)
    {
        return seat == 0 ? facing : facing.getOpposite();
    }

    /**
     * Every floor block of the duel area, in a fixed order (near row to far row,
     * left to right) so that anything derived from this -- a checksum, an
     * indicator packet, a test's expectations -- is reproducible.
     */
    public static void forEachFloor(BlockPos anchor, Direction facing, FieldSpec spec,
        Consumer<BlockPos> cells)
    {
        Direction right = facing.getClockWise();
        for(int d = -spec.halfDepth(); d <= spec.halfDepth(); d++)
        {
            for(int w = -spec.halfWidth(); w <= spec.halfWidth(); w++)
            {
                cells.accept(cell(anchor, facing, right, w, d));
            }
        }
    }

    /**
     * The floor blocks along one edge of the area: the row a seat's marker is
     * drawn on and the row that seat stands against.
     */
    public static void forEachEdge(BlockPos anchor, Direction facing, FieldSpec spec, int seat,
        Consumer<BlockPos> cells)
    {
        Direction right = facing.getClockWise();
        int d = seat == 0 ? -spec.halfDepth() : spec.halfDepth();
        for(int w = -spec.halfWidth(); w <= spec.halfWidth(); w++)
        {
            cells.accept(cell(anchor, facing, right, w, d));
        }
    }

    /**
     * Is this position inside the duel field -- the floor slab or the air the
     * board needs above it?
     * <p>
     * The stand blocks are deliberately NOT part of it. A player stands there,
     * and a duel that abandoned itself because a duellist was inside its own
     * field would abandon itself immediately.
     */
    public static boolean contains(BlockPos anchor, Direction facing, FieldSpec spec, BlockPos pos)
    {
        int dy = pos.getY() - anchor.getY();
        if(dy < 0 || dy > spec.clearance())
        {
            return false;
        }
        Direction right = facing.getClockWise();
        // Project the offset back onto the field's own axes rather than
        // comparing world x/z, so this holds for all four facings.
        int dx = pos.getX() - anchor.getX();
        int dz = pos.getZ() - anchor.getZ();
        int along = dx * facing.getStepX() + dz * facing.getStepZ();
        int across = dx * right.getStepX() + dz * right.getStepZ();
        return Math.abs(along) <= spec.halfDepth() && Math.abs(across) <= spec.halfWidth();
    }

    /** How far {@code pos} is from the field's centre line, in blocks, across the facing. */
    public static int lateralOffset(BlockPos anchor, Direction facing, BlockPos pos)
    {
        Direction right = facing.getClockWise();
        return (pos.getX() - anchor.getX()) * right.getStepX()
            + (pos.getZ() - anchor.getZ()) * right.getStepZ();
    }

    /** How far {@code pos} is along the facing from the anchor, in blocks. */
    public static int forwardOffset(BlockPos anchor, Direction facing, BlockPos pos)
    {
        return (pos.getX() - anchor.getX()) * facing.getStepX()
            + (pos.getZ() - anchor.getZ()) * facing.getStepZ();
    }

    private static BlockPos cell(BlockPos anchor, Direction facing, Direction right, int w, int d)
    {
        return anchor.offset(right.getStepX() * w + facing.getStepX() * d, 0,
            right.getStepZ() * w + facing.getStepZ() * d);
    }
}
