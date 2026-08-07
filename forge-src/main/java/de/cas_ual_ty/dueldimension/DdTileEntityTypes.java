package de.cas_ual_ty.dueldimension;

import de.cas_ual_ty.dueldimension.duel.block.DuelTileEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class DdTileEntityTypes
{
    private static final DeferredRegister<BlockEntityType<?>> DEFERRED_REGISTER = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, DuelDimension.MOD_ID);
    public static final RegistryObject<BlockEntityType<DuelTileEntity>> DUEL = DEFERRED_REGISTER.register("duel", () -> BlockEntityType.Builder.of((pos, state) -> new DuelTileEntity(DdTileEntityTypes.DUEL.get(), pos, state), DdBlocks.DUEL_PLAYMAT.get(), DdBlocks.DUEL_TABLE.get()).build(null));
    
    public static void register(IEventBus bus)
    {
        DEFERRED_REGISTER.register(bus);
    }
}