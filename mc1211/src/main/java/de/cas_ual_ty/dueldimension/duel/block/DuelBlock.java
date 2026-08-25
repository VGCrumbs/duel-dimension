package de.cas_ual_ty.dueldimension.duel.block;

import com.mojang.serialization.MapCodec;
import de.cas_ual_ty.dueldimension.DdTileEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.MenuProvider;

public class DuelBlock extends HorizontalDirectionalBlock implements EntityBlock
{
    /**
     * A block must be able to describe itself as data now, and the codec is not
     * optional -- {@code codec()} is abstract. The shape is a constructor
     * argument rather than a property, so {@code simpleCodec} cannot be used:
     * this pair of blocks is created in code, never from a data pack, so the
     * codec only has to exist and be honest about the properties half.
     */
    public static final MapCodec<DuelBlock> CODEC =
        simpleCodec(properties -> new DuelBlock(properties, null));

    protected final VoxelShape shape;
    
    public DuelBlock(Properties properties, VoxelShape shape)
    {
        super(properties);
        this.shape = shape;
    }
    
    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec()
    {
        return CODEC;
    }

    /**
     * Right-clicking with an empty hand opens the duel.
     * <p>
     * {@code use} split in two: this is the empty-handed case, and the one with
     * an item in hand is {@code useItemOn}. A playmat has nothing to do with a
     * held item, so only this half is wanted -- which is a slightly better
     * answer than the old one, where right-clicking with a card also opened the
     * board.
     * <p>
     * The block position travels in the menu's extra data, the way every other
     * block in this mod opens its screen; {@code NetworkHooks.openScreen} was
     * Forge's version of exactly that.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level worldIn, BlockPos pos,
        Player player, BlockHitResult hit)
    {
        if(!worldIn.isClientSide() && player instanceof ServerPlayer p)
        {
            de.cas_ual_ty.dueldimension.net.MenuData.open(p, getTE(worldIn, pos),
                buf -> buf.writeBlockPos(pos));
        }

        return InteractionResult.SUCCESS;
    }
    
    public DuelTileEntity getTE(Level world, BlockPos pos)
    {
        BlockEntity te = world.getBlockEntity(pos);
        return te instanceof DuelTileEntity ? (DuelTileEntity) te : null;
    }
    
    @Override
    public BlockEntity newBlockEntity(BlockPos pPos, BlockState pState)
    {
        return DdTileEntityTypes.DUEL.create(pPos, pState);
    }
    
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context)
    {
        return defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, context.getHorizontalDirection().getOpposite());
    }
    
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> pBuilder)
    {
        pBuilder.add(HorizontalDirectionalBlock.FACING);
    }
    
    @Override
    protected RenderShape getRenderShape(BlockState state)
    {
        return RenderShape.MODEL;
    }
    
    @Override
    protected VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext)
    {
        return shape;
    }
}
