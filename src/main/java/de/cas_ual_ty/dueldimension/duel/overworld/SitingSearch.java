package de.cas_ual_ty.dueldimension.duel.overworld;

import de.cas_ual_ty.dueldimension.duel.overworld.FieldValidator.Refusal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Finds where two duellists can hold an overworld duel: first by asking whether
 * they can hold it exactly where they stand, and failing that by looking
 * outwards for the nearest place a field fits.
 * <p>
 * Deterministic by construction -- candidates ordered by distance with a fixed
 * tie-break, and a fixed order of facings -- because both clients and the server
 * all have to agree on which of several equally good spots is <em>the</em> spot
 * without another round trip to settle it.
 * <p>
 * Pure. Every world read goes through the {@link BlockSampler} it is handed.
 */
public final class SitingSearch
{
    private SitingSearch()
    {
    }

    /**
     * @param floorA the block seat 0 is standing on
     * @param floorB the block seat 1 is standing on
     */
    public static SitingResult site(BlockSampler sampler, BlockPos floorA, BlockPos floorB,
        FieldSpec spec)
    {
        Refusal pair = FieldValidator.checkPair(floorA, floorB, spec);
        // Being far apart is the one complaint no amount of searching answers:
        // any field found would be nowhere near at least one of them.
        if(pair == Refusal.TOO_FAR || pair == Refusal.TOO_CLOSE)
        {
            return new SitingResult.Refused(pair);
        }

        Direction facing = facing(floorA, floorB);
        BlockPos midpoint = midpoint(floorA, floorB);

        // The obvious field first: centred between them, along the line they
        // already stand on. If that works and they are already in the right
        // places, nobody has to move at all.
        FieldSiting natural = new FieldSiting(midpoint, facing, spec);
        if(pair == null && FieldValidator.check(sampler, natural) == null)
        {
            boolean placed = natural.stand(0).equals(floorA) && natural.stand(1).equals(floorB);
            return placed ? new SitingResult.Ready(natural) : new SitingResult.Move(natural);
        }

        FieldSiting nearest = search(sampler, midpoint, facing, spec);
        if(nearest != null)
        {
            return new SitingResult.Move(nearest);
        }
        // Report what was wrong with where they were standing if that was the
        // problem, so the message names something the player can act on rather
        // than the generic "no room".
        return new SitingResult.Refused(pair != null ? pair : Refusal.NO_ROOM);
    }

    /**
     * The nearest valid field to {@code around}, or null.
     * <p>
     * Candidates are sorted by true distance rather than walked in rings: a
     * ring is a square, so its far corner is further away than the near edge of
     * the next ring out, and "nearest" would have meant "nearest in a shape the
     * player cannot see". The whole square is only a few hundred columns, so
     * sorting it costs nothing next to one block read.
     * <p>
     * The preferred facing is exhausted everywhere before any other facing is
     * tried anywhere: a field further away that keeps the duellists on the
     * sides they were already standing on is the better answer, and the last
     * facing tried is the one that swaps them over.
     */
    public static FieldSiting search(BlockSampler sampler, BlockPos around, Direction preferred,
        FieldSpec spec)
    {
        List<BlockPos> columns = candidates(around, spec);
        for(Direction facing : facings(preferred))
        {
            for(BlockPos column : columns)
            {
                FieldSiting candidate = at(sampler, column, facing, spec);
                if(candidate != null)
                {
                    return candidate;
                }
            }
        }
        return null;
    }

    /**
     * Every column the search may consider, nearest first. Ties break on x then
     * z -- arbitrary, but fixed, so the same world always yields the same field
     * and no client has to be told which of two equally good spots was meant.
     */
    private static List<BlockPos> candidates(BlockPos around, FieldSpec spec)
    {
        List<BlockPos> columns = new ArrayList<>();
        for(int dx = -spec.searchRadius(); dx <= spec.searchRadius(); dx++)
        {
            for(int dz = -spec.searchRadius(); dz <= spec.searchRadius(); dz++)
            {
                columns.add(around.offset(dx, 0, dz));
            }
        }
        columns.sort(Comparator
            .comparingInt((BlockPos pos) -> squared(pos.getX() - around.getX())
                + squared(pos.getZ() - around.getZ()))
            .thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ));
        return columns;
    }

    private static int squared(int value)
    {
        return value * value;
    }

    /**
     * A field centred on this column, if one fits. The floor is found first and
     * the field validated once against it, rather than validating the whole
     * field at every height in range: a 9x9 field is over three hundred block
     * reads and the search visits hundreds of columns.
     */
    private static FieldSiting at(BlockSampler sampler, BlockPos column, Direction facing,
        FieldSpec spec)
    {
        int floorY = floorAt(sampler, column, spec);
        if(floorY == NO_FLOOR)
        {
            return null;
        }
        FieldSiting candidate = new FieldSiting(
            new BlockPos(column.getX(), floorY, column.getZ()), facing, spec);
        return FieldValidator.check(sampler, candidate) == null ? candidate : null;
    }

    private static final int NO_FLOOR = Integer.MIN_VALUE;

    /**
     * The height of the ground in this column, searched from the given level
     * outwards -- level first, then down, then up, so a field prefers the floor
     * the players are already standing on over a roof above them.
     */
    public static int floorAt(BlockSampler sampler, BlockPos column, FieldSpec spec)
    {
        for(int step = 0; step <= spec.verticalSearch(); step++)
        {
            for(int sign : step == 0 ? ZERO : DOWN_THEN_UP)
            {
                int y = column.getY() + sign * step;
                BlockPos pos = new BlockPos(column.getX(), y, column.getZ());
                if(sampler.isGround(pos) && sampler.isClear(pos.above()))
                {
                    return y;
                }
            }
        }
        return NO_FLOOR;
    }

    private static final int[] ZERO = {0};
    private static final int[] DOWN_THEN_UP = {-1, 1};

    /**
     * The cardinal direction from A to B: whichever axis they are further apart
     * on. Ties break to the x axis, which is arbitrary but must be fixed so
     * that two clients never disagree about it.
     */
    public static Direction facing(BlockPos floorA, BlockPos floorB)
    {
        int dx = floorB.getX() - floorA.getX();
        int dz = floorB.getZ() - floorA.getZ();
        if(Math.abs(dx) >= Math.abs(dz))
        {
            return dx >= 0 ? Direction.EAST : Direction.WEST;
        }
        return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    /**
     * The block halfway between the two, rounded down. Rounding rather than
     * offsetting keeps the anchor on the line the two players define, which is
     * what makes the "nobody has to move" case reachable at all.
     */
    public static BlockPos midpoint(BlockPos floorA, BlockPos floorB)
    {
        return new BlockPos(Math.floorDiv(floorA.getX() + floorB.getX(), 2),
            Math.min(floorA.getY(), floorB.getY()),
            Math.floorDiv(floorA.getZ() + floorB.getZ(), 2));
    }

    /**
     * The preferred facing, then the two that turn the board a quarter, then
     * the one that swaps which duellist stands where. Swapping sides is a valid
     * field but a surprising one, so it is the last resort rather than an
     * equal-ranked alternative.
     */
    private static Direction[] facings(Direction preferred)
    {
        return new Direction[] {preferred, preferred.getClockWise(),
            preferred.getCounterClockWise(), preferred.getOpposite()};
    }
}
