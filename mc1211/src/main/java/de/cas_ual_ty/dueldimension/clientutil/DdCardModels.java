package de.cas_ual_ty.dueldimension.clientutil;

import de.cas_ual_ty.dueldimension.DdItems;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;

/**
 * Binds the mod's self-drawing items to their renderers.
 *
 * <h2>Per item here, per model type on 26.2</h2>
 *
 * On 26.2 an item names a model TYPE in its ClientItem JSON, and the type is
 * registered by id into a private {@code ItemModels.ID_MAPPER} — which needed an
 * access-widener entry, since Fabric has no hook for it. None of that exists in
 * 1.21.1: there are no model-type ids and no ID_MAPPER, so the whole mechanism
 * and the widening it required are gone.
 * <p>
 * What 1.21.1 has instead is Fabric's {@code BuiltinItemRendererRegistry}, keyed
 * by ITEM. The item's model JSON must still say
 * {@code "parent": "minecraft:builtin/entity"} — that is what makes
 * {@code BakedModel.isCustomRenderer()} true and sends vanilla down the
 * renderer path at all — but the binding itself is a Java call.
 *
 * <h2>The failure mode to know about</h2>
 *
 * A missing binding does not throw. The item simply draws as its plain model,
 * with no card art. On 26.2 a set and an opened set shared one model type
 * through two identical JSON files, so binding both happened without anyone
 * deciding to; here it is two calls, and forgetting one is silent. Hence the
 * single {@link CardSetItemModel} instance registered twice, deliberately.
 */
public final class DdCardModels
{
    private DdCardModels()
    {
    }

    public static void register()
    {
        BuiltinItemRendererRegistry.INSTANCE.register(DdItems.CARD, new CardItemModel());

        // ONE renderer, BOTH set items -- see the class note. A CardSetBaseItem
        // either way, so the renderer answers for each.
        CardSetItemModel sets = new CardSetItemModel();
        BuiltinItemRendererRegistry.INSTANCE.register(DdItems.SET, sets);
        BuiltinItemRendererRegistry.INSTANCE.register(DdItems.OPENED_SET, sets);

        // Every duel disk draws the wearer's board, so every disk item binds to
        // the same renderer. Walked from DuelDisks.ALL -- the list the rest of
        // the mod already treats as the set of disks -- rather than named one at
        // a time, so a disk added later is not silently left plain.
        DiskCardsItemModel disks = new DiskCardsItemModel();
        for(String name : de.cas_ual_ty.dueldimension.duel.profile.DuelDisks.ALL)
        {
            de.cas_ual_ty.dueldimension.duel.dueldisk.DuelDiskItem disk =
                de.cas_ual_ty.dueldimension.duel.profile.DuelDisks.item(name);
            if(disk != null)
            {
                BuiltinItemRendererRegistry.INSTANCE.register(disk, disks);
            }
        }
    }
}
