package de.cas_ual_ty.dueldimension.duel.overworld.arena;

import de.cas_ual_ty.dueldimension.duel.overworld.FieldSiting;
import de.cas_ual_ty.dueldimension.duel.overworld.FieldSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.List;

/**
 * A duel arena somebody built by hand, rather than one the game went looking
 * for.
 * <p>
 * Four corner markers say where the board is and how big; two player points say
 * where the duellists stand. That is the whole of it -- and it is deliberately
 * the whole of it, because a hand-built arena is a statement of intent. The
 * automatic siting refuses ground it does not like (a slab out of place, a
 * torch in the wrong cell, not enough headroom), which is right when it is
 * guessing and wrong when someone has told it. Nothing here checks the floor:
 * the builder placed the markers, and the markers are the answer.
 * <p>
 * Pure. Every rule below is arithmetic over block positions, so the client can
 * ask the same questions while drawing the preview that the server asks while
 * starting a duel, and the two can never disagree about whether an arena is
 * valid or which end a player belongs at.
 */
public final class Arena
{
    private Arena()
    {
    }

    /**
     * How far above the board's own level a player point may sit and still
     * work.
     * <p>
     * Four, because a raised platform behind the board is the obvious way to
     * build one and a duellist looking DOWN at the mat reads it better than one
     * looking across it. Below the board is not allowed at all: the mat would
     * be at eye level, which is a wall rather than a table.
     */
    public static final int MAX_POINT_LIFT = 4;

    /** How far beyond the board's edge a player point may stand. */
    public static final int MAX_POINT_STANDOFF = 4;

    /** The smallest and largest board anybody may mark out, in blocks. */
    public static final int MIN_SPAN = 5;
    public static final int MAX_SPAN = 33;

    /**
     * The rectangle four corner markers make.
     *
     * @param floorY the markers' own level, which is the board's surface: a
     *               corner sits in the air just above the floor, so the mat is
     *               drawn on the top face of the block beneath it
     */
    public record Rect(int minX, int minZ, int maxX, int maxZ, int floorY)
    {
        public int spanX()
        {
            return maxX - minX + 1;
        }

        public int spanZ()
        {
            return maxZ - minZ + 1;
        }

        /** Floored, which is the block a span of odd size is centred on. */
        public int centreX()
        {
            return Math.floorDiv(minX + maxX, 2);
        }

        public int centreZ()
        {
            return Math.floorDiv(minZ + maxZ, 2);
        }

        public boolean contains(int x, int z)
        {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }

        /** The same rectangle with room around it, for "is anybody near this". */
        public boolean within(int x, int z, int margin)
        {
            return x >= minX - margin && x <= maxX + margin
                && z >= minZ - margin && z <= maxZ + margin;
        }
    }

    /**
     * The rectangle these corners describe, or null if they do not describe
     * one.
     * <p>
     * Four markers, all on one level, sitting on the four combinations of the
     * outermost x and z. Three corners do not make a rectangle and five do not
     * make one either -- a fifth marker left behind from a previous attempt is
     * exactly the kind of thing that would otherwise silently move a wall.
     */
    public static Rect rectangle(List<BlockPos> corners)
    {
        if(corners.size() != 4)
        {
            return null;
        }
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        int y = corners.get(0).getY();
        for(BlockPos corner : corners)
        {
            if(corner.getY() != y)
            {
                // A board is flat. Corners at two heights describe a ramp, and
                // there is no reading of that which produces one rectangle.
                return null;
            }
            minX = Math.min(minX, corner.getX());
            minZ = Math.min(minZ, corner.getZ());
            maxX = Math.max(maxX, corner.getX());
            maxZ = Math.max(maxZ, corner.getZ());
        }
        Rect rect = new Rect(minX, minZ, maxX, maxZ, y);
        if(rect.spanX() < MIN_SPAN || rect.spanZ() < MIN_SPAN
            || rect.spanX() > MAX_SPAN || rect.spanZ() > MAX_SPAN)
        {
            return null;
        }
        // Every corner has to BE a corner. Four markers in a line share one
        // bounding box with four markers in a square, and only one of those is
        // an arena.
        for(BlockPos corner : corners)
        {
            boolean onX = corner.getX() == minX || corner.getX() == maxX;
            boolean onZ = corner.getZ() == minZ || corner.getZ() == maxZ;
            if(!onX || !onZ)
            {
                return null;
            }
        }
        return rect;
    }

    /**
     * Is this player point somewhere a duellist could actually stand and play?
     * <p>
     * At an END of the board, not along a side: a duel is played across the
     * mat, and a point off the flank would put the board sideways to the player
     * who used it. Just beyond the edge rather than on it, because standing on
     * the board is standing on the cards. Level with the board or up to
     * {@link #MAX_POINT_LIFT} above it, never below.
     * <p>
     * Both axes are allowed here, and which one this arena actually uses is
     * settled by {@link #of}: a lone point cannot know whether its partner is
     * opposite it or round the side.
     */
    public static boolean pointValid(Rect rect, BlockPos point)
    {
        if(rect == null)
        {
            return false;
        }
        if(point.getY() < rect.floorY() || point.getY() > rect.floorY() + MAX_POINT_LIFT)
        {
            return false;
        }
        return offsetAlong(rect, point, true) != 0 || offsetAlong(rect, point, false) != 0;
    }

    /**
     * Which end of one axis this point stands off, as -1, 0 or 1.
     * <p>
     * Zero means "not at an end of this axis": either it is inside the board,
     * too far past it, or off to one side of it. The sign of a non-zero answer
     * is which end, which is what tells two points they are opposite each other
     * rather than both at the same one.
     */
    private static int offsetAlong(Rect rect, BlockPos point, boolean alongX)
    {
        int along = alongX ? point.getX() : point.getZ();
        int across = alongX ? point.getZ() : point.getX();
        int min = alongX ? rect.minX() : rect.minZ();
        int max = alongX ? rect.maxX() : rect.maxZ();
        int acrossMin = alongX ? rect.minZ() : rect.minX();
        int acrossMax = alongX ? rect.maxZ() : rect.maxX();

        // Across the board's width, or it is at a corner rather than an end.
        if(across < acrossMin || across > acrossMax)
        {
            return 0;
        }
        if(along < min && along >= min - MAX_POINT_STANDOFF)
        {
            return -1;
        }
        if(along > max && along <= max + MAX_POINT_STANDOFF)
        {
            return 1;
        }
        return 0;
    }

    /**
     * A usable arena from a rectangle and exactly two points, or null.
     * <p>
     * The two have to be at OPPOSITE ends of the same axis. Both at the same
     * end is two players standing shoulder to shoulder; one at each end of
     * different axes is a board neither of them is square to.
     */
    public static Built of(Rect rect, List<BlockPos> points)
    {
        if(rect == null || points.size() != 2)
        {
            return null;
        }
        BlockPos first = points.get(0);
        BlockPos second = points.get(1);
        for(boolean alongX : new boolean[] {true, false})
        {
            int a = offsetAlong(rect, first, alongX);
            int b = offsetAlong(rect, second, alongX);
            if(a != 0 && b != 0 && a != b)
            {
                return new Built(rect, first, second);
            }
        }
        return null;
    }

    /**
     * A rectangle with a duellist at each end of it.
     *
     * @param first  one of the two player points, in no particular order
     * @param second the other
     */
    public record Built(Rect rect, BlockPos first, BlockPos second)
    {
        /** Whichever point is nearer this position. */
        public BlockPos nearest(BlockPos to)
        {
            return first.distSqr(to) <= second.distSqr(to) ? first : second;
        }

        /** The other one. */
        public BlockPos opposite(BlockPos point)
        {
            return point.equals(first) ? second : first;
        }

        /**
         * The siting a duel on this arena uses.
         * <p>
         * The board is measured from the rectangle rather than from the
         * settings: that is the entire point of marking one out. What the
         * settings still supply is everything the rectangle says nothing
         * about -- the mat's own lift, the headroom, the tolerances -- so a
         * built arena still honours the knobs that are not about size.
         * <p>
         * The anchor is one block BELOW the markers, because a corner marker
         * sits in the air and the mat is drawn on the top face of the block
         * under it: the marker is at the height of the board's surface, and
         * {@link FieldSiting}'s anchor is the floor whose lid that surface is.
         *
         * @param seatZeroPoint which of the two points seat zero stands on
         */
        public FieldSiting siting(FieldSpec base, BlockPos seatZeroPoint)
        {
            BlockPos other = opposite(seatZeroPoint);
            Direction facing = facing(seatZeroPoint, other);
            boolean alongX = facing.getAxis() == Direction.Axis.X;
            int along = alongX ? rect.spanX() : rect.spanZ();
            int across = alongX ? rect.spanZ() : rect.spanX();

            FieldSpec spec = FieldSpec.fitting(across, along, base.clearance(),
                base.lateralTolerance(), base.elevationTolerance(), base.maxSeparation(),
                base.searchRadius(), base.verticalSearch()).withLift(base.matLift());

            BlockPos anchor = new BlockPos(rect.centreX(), rect.floorY() - 1, rect.centreZ());
            // The points mark where a duellist's FEET go, and a siting's stand
            // is the floor under them -- everything downstream teleports to one
            // block above it. So the override is the block beneath the marker,
            // which is also what lets a point four up put a player four up.
            return new FieldSiting(anchor, facing, spec, seatZeroPoint.below(), other.below());
        }

        /** Which way seat zero looks: from its own point at the other. */
        public static Direction facing(BlockPos from, BlockPos to)
        {
            int dx = to.getX() - from.getX();
            int dz = to.getZ() - from.getZ();
            return Math.abs(dx) >= Math.abs(dz)
                ? (dx > 0 ? Direction.EAST : Direction.WEST)
                : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
        }
    }
}
