package de.cas_ual_ty.dueldimension.duel.overworld.arena;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A marker for building duel arenas with: invisible, intangible, and only a
 * creative-mode builder's business.
 * <p>
 * Barrier's arrangement, for barrier's reasons. The render shape is
 * {@code INVISIBLE}, so the game never draws one -- what a builder sees is
 * drawn by {@code ArenaRenderer}, which is also what makes "not visible"
 * true rather than merely usually true: there is no state a player can get
 * into where the block appears, because nothing in the normal pipeline can
 * draw it at all.
 * <p>
 * <b>And nothing but a marker in your hand makes one solid to look at.</b>
 * Invisible was never the whole of it: an invisible block still answers the
 * crosshair, so a survival player walking past one still got a selection
 * wireframe hanging in the air and a nameplate telling them what it was. The
 * shape is empty unless the player is holding the same marker, which is the
 * arrangement vanilla's light block uses and for the same reason -- a builder's
 * block should be there for the builder holding it and not there for anyone
 * else. One rule removes the outline, the nameplate, and the ability to hit it
 * at all, because all three come from the same shape.
 * <p>
 * Bedrock-hard to everybody, and instant to somebody holding one. Hardness
 * cannot ask who is mining, so the two cases are split in
 * {@link #getDestroyProgress}: a marker cannot be mined out by someone who does
 * not know it is there, and comes straight up for the builder who put it down.
 * Creative never consults either, as with bedrock.
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
     * Solid to look at only for somebody holding the same marker.
     * <p>
     * This one shape is the outline, the crosshair target and the thing a tool
     * swings at, so emptying it takes all three away together. Vanilla's light
     * block is written exactly this way -- {@code isHoldingItem(Items.LIGHT)
     * ? Shapes.block() : Shapes.empty()} -- and a marker wants the same deal.
     * <p>
     * {@code asItem()} rather than a list, so a corner answers to a corner and
     * a player point to a player point: the block you are holding is the block
     * you are working on, and the other kind stays out of the way.
     */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
        CollisionContext context)
    {
        // Asked of the ENTITY, never of asItem(). Vanilla's light block writes
        // this as isHoldingItem(Items.LIGHT), which is fine for a block whose
        // item is a constant and disastrous for one asking about itself:
        // Block.asItem() CACHES what it finds, shapes are queried while the
        // block's states are being built, and at that moment the item does not
        // exist yet. The block would remember Items.AIR for the rest of the
        // run -- which turns its creative-menu stack into an empty one and
        // crashes the tab that tries to show it.
        return context instanceof EntityCollisionContext carried
            && carried.getEntity() instanceof Player player && holding(player)
            ? Shapes.block() : Shapes.empty();
    }

    /**
     * Instant for a builder holding one, and refused for everybody else.
     * <p>
     * Hardness alone cannot say this -- it is a number, and it does not know
     * who is swinging. Left to {@code strength(-1F, ...)} a marker is bedrock
     * to a survival player even when they are holding the very block, which
     * makes a marker something you can put down and never take back up outside
     * creative.
     */
    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level,
        BlockPos pos)
    {
        return holding(player) ? 1F : 0F;
    }

    /** Whether this player has one of THESE in either hand. */
    public boolean holding(Player player)
    {
        return player != null
            && (places(player.getMainHandItem()) || places(player.getOffhandItem()));
    }

    /**
     * Whether this stack is the one that puts THIS block down.
     * <p>
     * Read off the stack rather than compared against {@code asItem()}, for the
     * same reason as above: asking a block for its item caches the answer, and
     * this question gets asked from places that run before there is one.
     */
    private boolean places(ItemStack stack)
    {
        return stack.getItem() instanceof BlockItem placer && placer.getBlock() == this;
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
