package de.cas_ual_ty.dueldimension.clientutil;

import de.cas_ual_ty.dueldimension.DuelDimension;
import net.minecraft.client.renderer.item.ItemModels;
import net.minecraft.resources.Identifier;

/**
 * Registers the mod's custom {@link net.minecraft.client.renderer.item.ItemModel}
 * types so the card and set items can select them from their ClientItem JSON.
 * <p>
 * <b>Why this is manual.</b> On Forge the card item's dynamic model was inserted
 * straight into the baked-model registry from {@code ClientProxy.modelBake}. In
 * 26.2 an {@code ItemModel} type is looked up by id from a private {@code
 * ItemModels.ID_MAPPER}, and there is no Fabric API hook to add one. So the field
 * is opened with an access-widener entry (see {@code dueldimension.accesswidener})
 * and the two types are put in directly, keyed by the ids the item JSONs name.
 */
public class DdCardModels
{
    public static final Identifier CARD = Identifier.fromNamespaceAndPath(
        DuelDimension.MOD_ID, "card");
    public static final Identifier CARD_SET = Identifier.fromNamespaceAndPath(
        DuelDimension.MOD_ID, "card_set");
    /** The cards on a worn duel disk; see {@link DiskCardsItemModel}. */
    public static final Identifier DUEL_DISK_CARDS = Identifier.fromNamespaceAndPath(
        DuelDimension.MOD_ID, "duel_disk_cards");

    public static void register()
    {
        ItemModels.ID_MAPPER.put(CARD, CardItemModel.Unbaked.MAP_CODEC);
        ItemModels.ID_MAPPER.put(CARD_SET, CardSetItemModel.Unbaked.MAP_CODEC);
        ItemModels.ID_MAPPER.put(DUEL_DISK_CARDS, DiskCardsItemModel.Unbaked.MAP_CODEC);
    }
}
