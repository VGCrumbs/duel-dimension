package de.cas_ual_ty.dueldimension.duel.overworld.arena;

import de.cas_ual_ty.dueldimension.DdBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds the arena markers somebody left in the world.
 * <p>
 * The one class here that reads blocks, kept apart from {@link Arena} so every
 * rule about what makes an arena valid stays arithmetic that can be reasoned
 * about without a world to run it in.
 * <p>
 * A scan rather than a registry of placed markers. A registry would be faster
 * and would also be wrong the first time somebody built an arena with a
 * structure block, a schematic, a world edit or a hand-written chunk -- none of
 * which run a block's placement hook. The world is the record.
 */
public final class ArenaScan
{
    private ArenaScan() {}

    /**
     * How far to look, and how far up and down.
     * <p>
     * Wide enough to hold the largest arena anybody may mark out plus the
     * standoff at both ends, so a duellist standing at one end can still find
     * the far corners. The vertical reach covers a raised platform and a sunken
     * pit without dragging in the floor of whatever is built above.
     */
    public static final int RADIUS = 24;
    public static final int HEIGHT = 8;

    /** Every marker of both kinds within reach of a position. */
    public record Markers(List<BlockPos> corners, List<BlockPos> points)
    {
        public boolean isEmpty()
        {
            return corners.isEmpty() && points.isEmpty();
        }
    }

    public static Markers markers(BlockGetter level, BlockPos around, int radius, int height)
    {
        List<BlockPos> corners = new ArrayList<>();
        List<BlockPos> points = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for(int y = around.getY() - height; y <= around.getY() + height; y++)
        {
            for(int x = around.getX() - radius; x <= around.getX() + radius; x++)
            {
                for(int z = around.getZ() - radius; z <= around.getZ() + radius; z++)
                {
                    cursor.set(x, y, z);
                    net.minecraft.world.level.block.Block block =
                        level.getBlockState(cursor).getBlock();
                    if(block == DdBlocks.ARENA_CORNER)
                    {
                        corners.add(cursor.immutable());
                    }
                    else if(block == DdBlocks.ARENA_POINT)
                    {
                        points.add(cursor.immutable());
                    }
                    // More than an arena's worth is not an arena. Bailing out
                    // stops a builder's off-cut pile of spare markers from
                    // turning one scan into a search of the whole area.
                    if(corners.size() > 8 || points.size() > 8)
                    {
                        return new Markers(corners, points);
                    }
                }
            }
        }
        return new Markers(corners, points);
    }

    /**
     * The arena around this position, or null if there is not exactly one.
     * <p>
     * Everything is asked of {@link Arena}, so an arena the server will start a
     * duel on is precisely an arena the client drew as valid.
     */
    public static Arena.Built find(BlockGetter level, BlockPos around)
    {
        Markers found = markers(level, around, RADIUS, HEIGHT);
        if(found.isEmpty())
        {
            return null;
        }
        Arena.Built built = Arena.of(Arena.rectangle(found.corners()), found.points());
        // Near it, not merely within sight of it. Two duellists in the next
        // room should get the field the game finds for them, not the one
        // somebody built through the wall.
        if(built == null || !built.rect().within(around.getX(), around.getZ(), NEARBY))
        {
            return null;
        }
        return built;
    }

    /** How far outside a marked board still counts as being at it. */
    public static final int NEARBY = 10;
}
