package de.cas_ual_ty.dueldimension.duel.overworld;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The one class in the overworld-duel subsystem that reads the world.
 * <p>
 * Before this, the mod contained no block queries at all -- a grep for
 * {@code getBlockState} over the whole source tree returned nothing. Keeping
 * every read behind one adapter is therefore not ceremony: it is the reason the
 * rest of the siting code can be exercised by a plain unit test, and the one
 * place to look when the answers disagree with what a player sees.
 * <p>
 * Both questions are answered by measuring the block's collision shape rather
 * than by asking a vanilla predicate, because the obvious predicates are wrong
 * for this purpose in ways that only show up on real terrain. Measured against
 * real block states rather than assumed -- see {@code LevelSamplerBlockTest},
 * which pins every case below:
 * <ul>
 * <li>{@code isFaceSturdy(UP)} is false for a dirt path and for farmland, whose
 * collision tops sit at 15/16. Those are villages and farms: refusing to duel
 * on them would refuse a great deal of the ground players actually stand on.</li>
 * <li>An empty collision shape is false for a carpet, a lily pad and a moss
 * carpet, none of which a duel board would notice. Requiring emptiness would
 * refuse a carpeted room while allowing bare stone.</li>
 * </ul>
 */
public record LevelSampler(BlockGetter level) implements BlockSampler
{
    /**
     * How tall a block's collision may be before it counts as being in the way.
     * One sixteenth: carpets, pressure plates and lily pads pass; a snow layer
     * two deep does not.
     */
    private static final double CLUTTER_HEIGHT = 0.125D;

    /**
     * How high a block's collision top must reach to be a floor. Seven eighths
     * admits a path and farmland at 15/16 and excludes a bottom slab at a half,
     * which really is a step and not a floor.
     */
    private static final double FLOOR_HEIGHT = 0.875D;

    /** How far a lid may fall short of the block's edge and still be called full. */
    private static final double EDGE = 0.001D;

    @Override
    public boolean isClear(BlockPos pos)
    {
        BlockState state = level.getBlockState(pos);
        // Fluid is asked separately from collision because water has no
        // collision shape at all, and a board floating in a river is not a duel
        // field.
        if(!state.getFluidState().isEmpty() || harmful(state))
        {
            return false;
        }
        VoxelShape shape = state.getCollisionShape(level, pos);
        return shape.isEmpty() || shape.max(Direction.Axis.Y) <= CLUTTER_HEIGHT;
    }

    @Override
    public boolean isGround(BlockPos pos)
    {
        BlockState state = level.getBlockState(pos);
        if(harmful(state) || !state.getFluidState().isEmpty())
        {
            return false;
        }
        if(state.isFaceSturdy(level, pos, Direction.UP))
        {
            return true;
        }
        // Not sturdy, but a full flat lid at very nearly block height: a dirt
        // path, farmland. Every part of that is load-bearing, and each was
        // measured against real block states rather than assumed:
        //   - "full" excludes iron bars, ladders and doors, which reach the top
        //     of their block while covering a sliver of it;
        //   - "flat", with a ceiling of one block, excludes fences, walls and
        //     gates, whose collision stands 1.5 blocks tall so players cannot
        //     jump them. Testing only how HIGH the shape reached called all of
        //     those a floor, and the clearance check could not see the half of
        //     the fence standing up into the next block, because that block is
        //     air and air is clear.
        AABB bounds = boundsOrNull(state, pos);
        return bounds != null && bounds.maxY >= FLOOR_HEIGHT && bounds.maxY <= 1.0D
            && bounds.minX <= EDGE && bounds.maxX >= 1D - EDGE
            && bounds.minZ <= EDGE && bounds.maxZ >= 1D - EDGE;
    }

    private AABB boundsOrNull(BlockState state, BlockPos pos)
    {
        VoxelShape shape = state.getCollisionShape(level, pos);
        return shape.isEmpty() ? null : shape.bounds();
    }

    /**
     * Blocks a duellist must not be locked standing in or on.
     * <p>
     * This matters more here than anywhere else in the mod. The lock teleports
     * a player onto the stand block and then holds them there for the length of
     * a duel, so a hazard that passes validation is a player burning to death
     * unable to walk away. Fire, portals and cobwebs all report an empty
     * collision shape and would otherwise read as clear air; cactus and magma
     * report a full sturdy top and would read as good floor.
     * <p>
     * An explicit list, because a block state cannot be asked "would you hurt
     * me". Tags where tags exist, so modded members come along for free.
     */
    private static boolean harmful(BlockState state)
    {
        return state.is(BlockTags.FIRE)
            || state.is(BlockTags.PORTALS)
            || state.is(BlockTags.CAMPFIRES)
            || state.is(Blocks.MAGMA_BLOCK)
            || state.is(Blocks.CACTUS)
            || state.is(Blocks.SWEET_BERRY_BUSH)
            || state.is(Blocks.WITHER_ROSE)
            || state.is(Blocks.COBWEB)
            || state.is(Blocks.POWDER_SNOW)
            || state.is(Blocks.END_GATEWAY)
            || state.is(Blocks.LAVA_CAULDRON);
    }
}
