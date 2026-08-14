package de.cas_ual_ty.dueldimension.duel.overworld.arena;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * A marker for building duel arenas with: invisible, intangible, and only a
 * creative-mode builder's business.
 * <p>
 * Barrier's arrangement, for barrier's reasons. The render shape is
 * {@code INVISIBLE}, so the game never draws one -- what a creative player sees
 * is drawn by {@code ArenaRenderer}, which is also what makes "visible in
 * creative only" true rather than merely usually true: there is no state a
 * survival player can get into where the block appears, because nothing in the
 * normal pipeline can draw it at all.
 * <p>
 * Unbreakable in the same way bedrock is unbreakable, which is to say
 * unbreakable everywhere except creative mode, where hardness is not consulted.
 * That is one rule doing the work of two: a survival player cannot mine one
 * out, and a builder can pull one up and move it without a tool.
 * <p>
 * No collision, so a marker never trips a player up, never blocks a jump, and
 * can sit exactly where a duellist is going to stand. It drops nothing when
 * broken because there is nothing to drop: the item comes out of the creative
 * menu or it does not come at all.
 */
public class ArenaMarkerBlock extends Block
{
    public static final MapCodec<ArenaMarkerBlock> CODEC = simpleCodec(ArenaMarkerBlock::new);

    public ArenaMarkerBlock(BlockBehaviour.Properties properties)
    {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec()
    {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state)
    {
        return RenderShape.INVISIBLE;
    }

    /**
     * A marker sits in the air where a floor or a pair of feet will be, so it
     * has to survive being placed in mid-air, next to nothing, on nothing.
     */
    @Override
    protected boolean canSurvive(BlockState state, net.minecraft.world.level.LevelReader level,
        BlockPos pos)
    {
        return true;
    }

    /** The properties every marker shares. */
    public static BlockBehaviour.Properties properties(ResourceKey<Block> key)
    {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.NONE)
            // Bedrock's: refused by every tool, ignored by creative mode.
            .strength(-1F, 3600000F)
            .noCollision()
            .noOcclusion()
            .noLootTable()
            // A piston shoving an arena's corner one block sideways would move
            // a wall of the board without anybody touching it.
            .pushReaction(PushReaction.BLOCK)
            .sound(SoundType.EMPTY)
            .setId(key);
    }
}
