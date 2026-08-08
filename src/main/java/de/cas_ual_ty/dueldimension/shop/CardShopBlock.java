package de.cas_ual_ty.dueldimension.shop;

import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The card shop: right-click to buy booster packs.
 * <p>
 * The stock is built server side and sent with the open, so the shop a player
 * sees is the shop the server will actually sell from. Sending nothing and
 * letting the client list its own database would mean two machines disagreeing
 * about what exists and what it costs.
 * <p>
 * Port: two block-layer changes since 1.19 — a block supplies a {@link MapCodec}
 * ({@link #codec()}), and the empty-handed right-click hook is
 * {@link #useWithoutItem} rather than {@code use}. The open goes out through
 * {@link ServerPlayNetworking#send} now instead of Forge's
 * {@code PacketDistributor}. {@code Level.isClientSide} is a method here.
 */
public class CardShopBlock extends Block
{
    public static final MapCodec<CardShopBlock> CODEC = simpleCodec(CardShopBlock::new);

    public CardShopBlock(Properties properties)
    {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec()
    {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
        BlockHitResult hit)
    {
        if(level.isClientSide())
        {
            return InteractionResult.SUCCESS;
        }
        if(player instanceof ServerPlayer serverPlayer)
        {
            ServerPlayNetworking.send(serverPlayer,
                new ShopMessages.OpenShop(DuelPoints.get(serverPlayer), ShopStock.available()));
        }
        return InteractionResult.CONSUME;
    }
}
