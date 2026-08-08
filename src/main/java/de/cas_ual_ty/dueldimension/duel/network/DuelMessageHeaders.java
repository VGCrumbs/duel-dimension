package de.cas_ual_ty.dueldimension.duel.network;

import de.cas_ual_ty.dueldimension.DdDuelRegistries;
import de.cas_ual_ty.dueldimension.DuelDimension;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;

/**
 * What a duel message is addressed to: a container, a block, or an entity.
 * <p>
 * A registry rather than an enum because the type crosses the wire by its id,
 * exactly as the action types do.
 */
public class DuelMessageHeaders
{
    public static final DuelMessageHeaderType CONTAINER = register("container",
        new DuelMessageHeaderType(() ->
            new DuelMessageHeader.ContainerHeader(DuelMessageHeaders.CONTAINER)));
    public static final DuelMessageHeaderType TILE_ENTITY = register("tile_entity",
        new DuelMessageHeaderType(() ->
            new DuelMessageHeader.TileEntityHeader(DuelMessageHeaders.TILE_ENTITY)));
    public static final DuelMessageHeaderType ENTITY = register("entity",
        new DuelMessageHeaderType(() ->
            new DuelMessageHeader.EntityHeader(DuelMessageHeaders.ENTITY)));

    private static DuelMessageHeaderType register(String name, DuelMessageHeaderType entry)
    {
        return Registry.register(DdDuelRegistries.HEADERS,
            Identifier.fromNamespaceAndPath(DuelDimension.MOD_ID, name), entry);
    }

    /**
     * Loads this class, which is what registers everything in it.
     * <p>
     * Note the self-reference in each factory above: the supplier is only run
     * when a header is created, long after the static initialiser has finished,
     * so the field it reads is set by then. Forge's version had the same shape
     * for the same reason.
     */
    public static void register()
    {
    }
}
