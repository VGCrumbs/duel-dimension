package de.cas_ual_ty.dueldimension.cardsupply;

import com.mojang.serialization.MapCodec;
import de.cas_ual_ty.dueldimension.DdContainerTypes;
import de.cas_ual_ty.dueldimension.DuelDimension;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Opens the card-supply picker. Two block-layer changes since 1.19: a block
 * must supply a {@link MapCodec} ({@link #codec()}), and the right-click hook
 * is {@link #useWithoutItem} (the empty-handed case) rather than {@code use}.
 * The menu's block position travels as {@link de.cas_ual_ty.dueldimension.net.MenuData}
 * one packet ahead, since {@code NetworkHooks.openScreen} is gone.
 */
public class CardSupplyBlock extends Block
{
    public static final MapCodec<CardSupplyBlock> CODEC = simpleCodec(CardSupplyBlock::new);

    public CardSupplyBlock(Properties properties)
    {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec()
    {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level worldIn, BlockPos pos, Player player, BlockHitResult hit)
    {
        if(!worldIn.isClientSide() && player instanceof ServerPlayer p)
        {
            de.cas_ual_ty.dueldimension.net.MenuData.open(p, getMenuProvider(state, worldIn, pos),
                buf -> buf.writeBlockPos(pos));
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public MenuProvider getMenuProvider(BlockState state, Level worldIn, BlockPos pos)
    {
        return new SimpleMenuProvider(
            (id, inventory, player) -> new CardSupplyContainer(DdContainerTypes.CARD_SUPPLY, id, inventory, pos),
            Component.translatable("container." + DuelDimension.MOD_ID + ".card_supply"));
    }
}
