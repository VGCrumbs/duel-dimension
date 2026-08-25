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
 * The sleeve shop: right-click to buy card sleeves.
 * <p>
 * {@link CardShopBlock}'s shape exactly, and that is a decision rather than
 * laziness. The card shop extends {@link Block} with no block entity, no
 * {@code MenuType} and no container: it has nothing to store — the stock is
 * derived, the balance is on the player, the purchase is one message — so a
 * block entity would be an empty object ticking in every loaded chunk, and a
 * menu would be a set of slots with nothing to put in them. A sleeve shop
 * stores even less. So it is a plain block that answers a right-click by
 * sending the player a screen.
 * <p>
 * What differs is only what is sent: {@link ShopMessages.OpenSleeveShop} rather
 * than {@link ShopMessages.OpenShop}, carrying the sleeve catalogue instead of
 * the pack one. Both are built server side and sent with the open, so the shop a
 * player sees is the shop the server will actually sell from.
 * <p>
 * Port notes are its sibling's: a block supplies a {@link MapCodec}
 * ({@link #codec()}), the empty-handed right-click hook is
 * {@link #useWithoutItem} rather than {@code use}, the open goes out through
 * {@link ServerPlayNetworking#send} rather than Forge's
 * {@code PacketDistributor}, and {@code Level.isClientSide} is a method.
 */
public class SleeveShopBlock extends Block
{
    public static final MapCodec<SleeveShopBlock> CODEC = simpleCodec(SleeveShopBlock::new);

    public SleeveShopBlock(Properties properties)
    {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec()
    {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
        Player player, BlockHitResult hit)
    {
        if(level.isClientSide())
        {
            return InteractionResult.SUCCESS;
        }
        if(player instanceof ServerPlayer serverPlayer)
        {
            ServerPlayNetworking.send(serverPlayer,
                new ShopMessages.OpenSleeveShop(DuelPoints.get(serverPlayer), ShopStock.sleeves()));
        }
        return InteractionResult.CONSUME;
    }
}
