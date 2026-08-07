package de.cas_ual_ty.dueldimension;

import de.cas_ual_ty.dueldimension.duel.npc.DuelistEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
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

    /** An NPC you can challenge. Player-shaped, so player-sized. */
    public static final EntityType<DuelistEntity> DUELIST = Registry.register(
        BuiltInRegistries.ENTITY_TYPE, DUELIST_KEY,
        EntityType.Builder.of(DuelistEntity::new, MobCategory.CREATURE)
            .sized(0.6F, 1.8F)
            .build(DUELIST_KEY));

    private DdEntityTypes()
    {
    }

    private static ResourceKey<EntityType<?>> key(String name)
    {
        return ResourceKey.create(Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(DuelDimension.MOD_ID, name));
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
    }

    // The duel entity -- the invisible marker a duel runs on -- is parked with
    // the container phase: it exists to carry a menu, and menus have not been
    // ported.
}
