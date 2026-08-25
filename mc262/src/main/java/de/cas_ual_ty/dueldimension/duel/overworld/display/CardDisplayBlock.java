package de.cas_ual_ty.dueldimension.duel.overworld.display;

import com.mojang.serialization.MapCodec;
import de.cas_ual_ty.dueldimension.DdTileEntityTypes;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A pedestal that holds one card, for looking at rather than for duelling with.
 * <p>
 * A builder's block, like the arena markers, and creative-only for the same
 * reason: it exists to lay things out, not to be part of anyone's game. Unlike
 * a marker it is visible -- the point is the card standing on it, and a card
 * floating over nothing would be a card nobody could reach.
 * <p>
 * It is also the foundation for something larger. A card that knows which way
 * up it is lying is a card that can be given a monster to stand on it, and this
 * is the smallest thing that has to exist before that does.
 */
public class CardDisplayBlock extends Block implements EntityBlock
{
    public static final MapCodec<CardDisplayBlock> CODEC = simpleCodec(CardDisplayBlock::new);

    public CardDisplayBlock(BlockBehaviour.Properties properties)
    {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec()
    {
        return CODEC;
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
