package de.cas_ual_ty.dueldimension.duel.overworld;

import de.cas_ual_ty.dueldimension.duel.overworld.FieldValidator.Refusal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        Refusal underfoot = FieldValidator.check(sampler, natural);
        if(pair == null && underfoot == null)
        {
            boolean placed = natural.stand(0).equals(floorA) && natural.stand(1).equals(floorB);
            return placed ? new SitingResult.Ready(natural) : new SitingResult.Move(natural);
        }

        FieldSiting nearest = search(sampler, midpoint, facing, spec);
        if(nearest != null)
        {
            return new SitingResult.Move(nearest);
        }
        // What was wrong WHERE THEY STAND, in preference to the generic "no
        // room". Both are true once the search comes back empty, but only one
        // of them tells a player whether to look up at a ceiling, down at a
        // hole, or around at the furniture -- and "no room" was reported twice
        // before anybody could act on it.
        if(underfoot != null)
        {
            return new SitingResult.Refused(underfoot);
        }
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
     * The preferred facing is exhausted everywhere before either other facing is
     * tried anywhere: a field further away that keeps the duellists on the
     * sides they were already standing on is the better answer than a nearer
     * one that turns the board under them.
     */
    public static FieldSiting search(BlockSampler sampler, BlockPos around, Direction preferred,
        FieldSpec spec)
    {
        List<BlockPos> columns = candidates(around, spec);
        // Where the floor is in a column does not depend on which way the board
        // faces, and finding it is a vertical scan. Remembered across the
        // facings rather than repeated: measured at 188,000 block reads for one
        // failing search before this, on the server thread.
        Map<BlockPos, Integer> floors = new HashMap<>();
        for(Direction facing : facings(preferred))
        {
            for(BlockPos column : columns)
            {
                int floorY = floors.computeIfAbsent(column, at -> floorAt(sampler, at, spec));
                if(floorY == NO_FLOOR)
                {
                    continue;
                }
                FieldSiting candidate = new FieldSiting(
                    new BlockPos(column.getX(), floorY, column.getZ()), facing, spec);
                if(FieldValidator.check(sampler, candidate) == null)
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

    /** No floor within reach of a column. Never packed into a BlockPos. */
    public static final int NO_FLOOR = Integer.MIN_VALUE;

    /**
     * The height of the ground in this column, searched from the given level
     * outwards -- level first, then down, then up, so a field prefers the floor
     * the players are already standing on over a roof above them.
     *
     * @return the floor's y, or {@link #NO_FLOOR}, which a caller must test
     *         for: it is not a coordinate, and truncates if used as one
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
     * The preferred facing, then the two that turn the board a quarter turn.
     * <p>
     * The opposite facing is deliberately absent: it can never change the
     * answer. The footprint is symmetric about the anchor on both axes, so
     * negating both is a permutation of the same cells, and the two stand
     * blocks merely swap -- a field and its opposite are accepted or refused
     * together, always. Trying it was a whole extra pass re-deciding what the
     * first pass had already decided, which was half the search's block reads.
     */
    private static Direction[] facings(Direction preferred)
    {
        return new Direction[] {preferred, preferred.getClockWise(),
            preferred.getCounterClockWise()};
    }
}
