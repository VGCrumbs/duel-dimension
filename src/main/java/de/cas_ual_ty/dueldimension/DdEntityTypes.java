package de.cas_ual_ty.dueldimension;

import de.cas_ual_ty.dueldimension.duel.dueldisk.DuelEntity;
import de.cas_ual_ty.dueldimension.duel.npc.DuelistEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class DdEntityTypes
{
    private static final DeferredRegister<EntityType<?>> DEFERRED_REGISTER = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, DuelDimension.MOD_ID);
    public static final RegistryObject<EntityType<?>> DUEL = DEFERRED_REGISTER.register("duel", () -> EntityType.Builder.of(DuelEntity::new, MobCategory.MISC).noSave().setShouldReceiveVelocityUpdates(false).sized(0, 0).fireImmune().build(null));
    
    public static final RegistryObject<EntityType<DuelistEntity>> DUELIST = DEFERRED_REGISTER.register("duelist",
        () -> EntityType.Builder.of(DuelistEntity::new, MobCategory.CREATURE).sized(0.6F, 1.8F).build("duelist"));
    
    public static void register(IEventBus bus)
    {
        DEFERRED_REGISTER.register(bus);
    }
}