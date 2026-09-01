package de.cas_ual_ty.dueldimension.duel.overworld.display;

import com.mojang.serialization.MapCodec;
import de.cas_ual_ty.dueldimension.DdTileEntityTypes;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A pedestal that holds one card, for looking at rather than for duelling with.
 * <p>
 * Crafted rather than creative-only, and so a block anybody may own: the point
 * is the card standing on it, and a card floating over nothing would be a card
 * nobody could reach.
 * <p>
 * <b>Choosing what it shows is still a creative-mode act</b> -- see {@link
 * #useWithoutItem} -- so in survival this is a pedestal that keeps whatever it
 * was given. That split is deliberate but narrow, and it is the one thing about
 * this block worth revisiting if displays are ever meant to be dressed by the
 * people who build with them.
 * <p>
 * It is also the foundation for something larger. A card that knows which way
 * up it is lying is a card that can be given a monster to stand on it, and this
 * is the smallest thing that has to exist before that does.
 */
public class CardDisplayBlock extends HorizontalDirectionalBlock implements EntityBlock
{
    public static final MapCodec<CardDisplayBlock> CODEC = simpleCodec(CardDisplayBlock::new);

    public CardDisplayBlock(BlockBehaviour.Properties properties)
    {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec()
    {
        return CODEC;
    }

    /**
     * Which way the card on top is turned.
     * <p>
     * {@link HorizontalDirectionalBlock} brings the property and, with it,
     * {@code rotate} and {@code mirror} already written -- so a display caught
     * in a structure block or a world edit turns with everything around it
     * rather than being the one block left facing the way it was.
     */
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(FACING);
    }

    /**
     * Placed facing the player, exactly as a furnace is.
     * <p>
     * {@code getOpposite} is the whole of it: {@code getHorizontalDirection}
     * gives the way the player is LOOKING, and a block that faced that way would
     * present its back. Turning it round is what makes the card readable from
     * where you were standing when you put it down, which is the only place
     * anybody is standing at that moment.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context)
    {
        return defaultBlockState().setValue(FACING,
            context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return DdTileEntityTypes.CARD_DISPLAY.create(pos, state);
    }

    /**
     * The pedestal is drawn by its model; the card on it is drawn by
     * {@code CardDisplayRenderer}. Both, unlike an arena marker, which is
     * drawn by nothing.
     */
    @Override
    protected RenderShape getRenderShape(BlockState state)
    {
        return RenderShape.MODEL;
    }

    /**
     * Right-click opens the editor -- for a creative player only.
     * <p>
     * Asked on the SERVER, which is the only side whose answer counts: a client
     * that lied about its game mode would otherwise be editing displays on
     * somebody else's world. The client is told SUCCESS regardless so its arm
     * swings, because the alternative is a block that feels broken while it
     * politely refuses.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
        Player player, BlockHitResult hit)
    {
        if(level.isClientSide())
        {
            return InteractionResult.SUCCESS;
        }
        if(!player.isCreative() || !(player instanceof ServerPlayer server))
        {
            return InteractionResult.PASS;
        }
        if(level.getBlockEntity(pos) instanceof CardDisplayTileEntity display)
        {
            ServerPlayNetworking.send(server, new CardDisplayMessages.OpenEditor(pos,
                display.code(), display.art(), display.position()));
        }
        return InteractionResult.CONSUME;
    }
}
