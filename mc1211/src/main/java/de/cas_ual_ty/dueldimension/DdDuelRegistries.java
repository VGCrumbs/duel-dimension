package de.cas_ual_ty.dueldimension;

import de.cas_ual_ty.dueldimension.duel.action.ActionIcon;
import de.cas_ual_ty.dueldimension.duel.action.ActionType;
import de.cas_ual_ty.dueldimension.duel.network.DuelMessageHeaderType;
import de.cas_ual_ty.dueldimension.duel.playfield.ZoneType;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;

/**
 * The duel's own registries: what an action is, what it looks like, and what a
 * zone is.
 * <p>
 * These were three Forge {@code DeferredRegister}s over custom registries, and
 * they stay registries here rather than becoming a static table, because two
 * things depend on it being one:
 * <ul>
 * <li>Every one of them has a <b>name</b>, and the name is the translation key.
 *     An action is titled {@code action.dueldimension.attack} and a zone
 *     {@code dueldimension.zone.graveyard}, both built from the registry id at
 *     the point of display.</li>
 * <li>An action type crosses the <b>wire</b> by its numeric id. Forge wrote it
 *     with {@code writeRegistryIdUnsafe}; the same integer comes from
 *     {@link Registry#getId} and goes back through {@code byId}.</li>
 * </ul>
 * Not synced to the client, as on Forge. The ids are assigned in registration
 * order from a static initialiser, so both sides of a duel derive the same
 * numbering from the same jar — which is the assumption Forge's "unsafe" was
 * warning about, and it holds here for the same reason it held there.
 */
public final class DdDuelRegistries
{
    public static final ResourceKey<Registry<ActionType>> ACTION_TYPE_KEY =
        ResourceKey.createRegistryKey(
            ResourceLocation.fromNamespaceAndPath(DuelDimension.MOD_ID, "action_types"));
    public static final ResourceKey<Registry<ActionIcon>> ACTION_ICON_KEY =
        ResourceKey.createRegistryKey(
            ResourceLocation.fromNamespaceAndPath(DuelDimension.MOD_ID, "action_icons"));
    public static final ResourceKey<Registry<DuelMessageHeaderType>> HEADER_KEY =
        ResourceKey.createRegistryKey(
            ResourceLocation.fromNamespaceAndPath(DuelDimension.MOD_ID, "duel_message_headers"));
    public static final ResourceKey<Registry<ZoneType>> ZONE_TYPE_KEY =
        ResourceKey.createRegistryKey(
            ResourceLocation.fromNamespaceAndPath(DuelDimension.MOD_ID, "zone_types"));

    /**
     * Built eagerly, unlike Forge's deferred ones.
     * <p>
     * A {@code DeferredRegister} existed because Forge registration happened
     * during a mod-loading event and a static field could not be filled before
     * it. Here a registry is created and written to directly, so the three
     * {@code ...Types} classes can register from their own static initialisers
     * and every {@code RegistryObject.get()} in the ported code collapses to
     * the object itself.
     */
    public static final Registry<ActionType> ACTION_TYPES =
        FabricRegistryBuilder.createSimple(ACTION_TYPE_KEY).buildAndRegister();
    public static final Registry<ActionIcon> ACTION_ICONS =
        FabricRegistryBuilder.createSimple(ACTION_ICON_KEY).buildAndRegister();
    public static final Registry<DuelMessageHeaderType> HEADERS =
        FabricRegistryBuilder.createSimple(HEADER_KEY).buildAndRegister();
    public static final Registry<ZoneType> ZONE_TYPES =
        FabricRegistryBuilder.createSimple(ZONE_TYPE_KEY).buildAndRegister();

    private DdDuelRegistries()
    {
    }

    /**
     * Forces the three content classes to initialise.
     * <p>
     * Their entries are static fields registered on class load, so nothing is
     * in the registries until something touches the class. Forge had the same
     * requirement and met it by handing the deferred registers to the event
     * bus; this is the same act, spelled out.
     */
    public static void register()
    {
        de.cas_ual_ty.dueldimension.duel.action.ActionTypes.register();
        de.cas_ual_ty.dueldimension.duel.action.ActionIcons.register();
        de.cas_ual_ty.dueldimension.duel.playfield.ZoneTypes.register();
        de.cas_ual_ty.dueldimension.duel.network.DuelMessageHeaders.register();
    }
}
