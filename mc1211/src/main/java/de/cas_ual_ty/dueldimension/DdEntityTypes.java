package de.cas_ual_ty.dueldimension;

import de.cas_ual_ty.dueldimension.duel.npc.DuelistEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * The mod's entities.
 * <p>
 * {@code build()} takes the entity's own registry key now rather than a loose
 * string, which is the same tightening items and blocks got: a type has to know
 * what it is called before it exists, so the key is made first and used twice.
 */
public final class DdEntityTypes
{
    public static final ResourceKey<EntityType<?>> DUELIST_KEY = key("duelist");
    public static final ResourceKey<EntityType<?>> DUEL_KEY = key("duel");
    public static final ResourceKey<EntityType<?>> DUEL_BOT_KEY = key("duel_bot");

    /**
     * The duel itself, as an entity.
     * <p>
     * Not something anyone sees: it is a place for a {@code DuelManager} to
     * live when two players duel with disks rather than at a table. Size zero,
     * never saved, and immune to fire because none of that applies to a thing
     * with no body.
     */
    public static final EntityType<de.cas_ual_ty.dueldimension.duel.dueldisk.DuelEntity> DUEL =
        Registry.register(BuiltInRegistries.ENTITY_TYPE, DUEL_KEY,
            EntityType.Builder.<de.cas_ual_ty.dueldimension.duel.dueldisk.DuelEntity>of(
                    de.cas_ual_ty.dueldimension.duel.dueldisk.DuelEntity::new, MobCategory.MISC)
                .noSave()
                .sized(0F, 0F)
                .fireImmune()
                .build("duel"));

    /** An NPC you can challenge. Player-shaped, so player-sized. */
    public static final EntityType<DuelistEntity> DUELIST = Registry.register(
        BuiltInRegistries.ENTITY_TYPE, DUELIST_KEY,
        EntityType.Builder.of(DuelistEntity::new, MobCategory.CREATURE)
            .sized(0.6F, 1.8F)
            .build("duelist"));

    /**
     * A duelling opponent that is furniture: placed, picked up, and told which
     * deck to play. Player-shaped like a duelist, because it wears a duelist
     * skin and is drawn by the same renderer.
     */
    public static final EntityType<de.cas_ual_ty.dueldimension.duel.npc.DuelBotEntity> DUEL_BOT =
        Registry.register(BuiltInRegistries.ENTITY_TYPE, DUEL_BOT_KEY,
            EntityType.Builder.of(
                    de.cas_ual_ty.dueldimension.duel.npc.DuelBotEntity::new,
                    MobCategory.CREATURE)
                .sized(0.6F, 1.8F)
                .build("duel_bot"));

    private DdEntityTypes()
    {
    }

    private static ResourceKey<EntityType<?>> key(String name)
    {
        return ResourceKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(DuelDimension.MOD_ID, name));
    }

    /**
     * Registers the types and the attributes a mob needs to exist.
     * <p>
     * Forge asked for attributes through an event; Fabric asks for them
     * directly, which is one fewer thing to have forgotten to subscribe to. A
     * mob without them crashes on spawn either way.
     */
    public static void register()
    {
        FabricDefaultAttributeRegistry.register(DUELIST, DuelistEntity.createAttributes());
        // One hit point and no movement speed -- see DuelBotEntity.
        FabricDefaultAttributeRegistry.register(DUEL_BOT,
            de.cas_ual_ty.dueldimension.duel.npc.DuelBotEntity.createBotAttributes());
    }

}
