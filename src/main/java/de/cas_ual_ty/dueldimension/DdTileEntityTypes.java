package de.cas_ual_ty.dueldimension;

import de.cas_ual_ty.dueldimension.duel.block.DuelTileEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.Set;

/**
 * The block entities: one, for the duel playmat and the duel table.
 * <p>
 * {@code BlockEntityType.Builder} is gone — the constructor takes the supplier
 * and the set of blocks it is valid on directly, which is all the builder ever
 * assembled. The trailing {@code build(null)} that carried a datafixer type
 * went with it.
 * <p>
 * Both blocks share one type because they share one behaviour: a playmat and a
 * table are the same duel, at different heights.
 * <p>
 * The supplier names {@link #DUEL} while {@link #DUEL} is being assigned. That
 * is fine and deliberate: the lambda only runs when a block entity is created,
 * long after the field is set. Forge's version had the same shape.
 */
public final class DdTileEntityTypes
{
    public static final BlockEntityType<DuelTileEntity> DUEL = register("duel",
        new BlockEntityType<>((pos, state) -> new DuelTileEntity(DdTileEntityTypes.DUEL, pos, state),
            Set.of(DdBlocks.DUEL_PLAYMAT, DdBlocks.DUEL_TABLE)));

    private DdTileEntityTypes()
    {
    }

    private static <T extends BlockEntityType<?>> T register(String name, T type)
    {
        return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(DuelDimension.MOD_ID, name), type);
    }

    /** Touching this class registers everything in it. */
    public static void register()
    {
    }
}
