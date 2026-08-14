package de.cas_ual_ty.dueldimension.duel.overworld;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The one class in the overworld-duel subsystem that reads the world.
 * <p>
 * Before this, the mod contained no block queries at all -- a grep for
 * {@code getBlockState} over the whole source tree returned nothing. Keeping
 * every read behind one adapter is therefore not ceremony: it is the reason the
 * rest of the siting code can be exercised by a plain unit test, and the one
 * place to look when the answers disagree with what a player sees.
 */
public record LevelSampler(BlockGetter level) implements BlockSampler
{
    @Override
    public boolean isClear(BlockPos pos)
    {
        BlockState state = level.getBlockState(pos);
        // Asked as "would this stop something", not "is this air": tall grass
        // and flowers are clear, and a player standing in them is standing on
        // clear ground. Fluid is asked separately because water has no
        // collision shape but a board floating in a river is not a duel field.
        return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
    }

    @Override
    public boolean isGround(BlockPos pos)
    {
        // The same question vanilla asks before letting a block be placed on
        // top of another, so a surface that carries a torch carries a duel.
        return level.getBlockState(pos).isFaceSturdy(level, pos, Direction.UP);
    }
}
