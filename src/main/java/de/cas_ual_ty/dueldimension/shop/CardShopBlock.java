package de.cas_ual_ty.dueldimension.shop;

import de.cas_ual_ty.dueldimension.DuelDimension;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.PacketDistributor;

/**
 * The card shop: right-click to buy booster packs.
 * <p>
 * The stock is built server side and sent with the open, so the shop a player
 * sees is the shop the server will actually sell from. Sending nothing and
 * letting the client list its own database would mean two machines disagreeing
 * about what exists and what it costs.
 */
public class CardShopBlock extends Block
{
    public CardShopBlock(Properties properties)
    {
        super(properties);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
        InteractionHand hand, BlockHitResult hit)
    {
        if(level.isClientSide)
        {
            return InteractionResult.SUCCESS;
        }
        if(player instanceof ServerPlayer serverPlayer)
        {
            DuelDimension.channel.send(PacketDistributor.PLAYER.with(() -> serverPlayer),
                new ShopMessages.OpenShop(DuelPoints.get(serverPlayer), ShopStock.available()));
        }
        return InteractionResult.CONSUME;
    }
}
