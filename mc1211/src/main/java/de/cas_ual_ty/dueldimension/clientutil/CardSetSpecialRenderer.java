package de.cas_ual_ty.dueldimension.clientutil;

import com.mojang.blaze3d.vertex.PoseStack;
import de.cas_ual_ty.dueldimension.DdItems;
import de.cas_ual_ty.dueldimension.DuelDimension;
import de.cas_ual_ty.dueldimension.set.CardSet;
import de.cas_ual_ty.dueldimension.compat.SubmitNodeCollector;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.function.Consumer;

/**
 * The card-SET item's face — a booster pack — drawn in inventory and hand.
 * <p>
 * <b>Forge origin.</b> The reincarnation of {@code CardSetBakedModel} plus
 * {@code FinalCardSetBakedModel}. There, a set item was a {@code BakedModel}
 * whose {@code getQuads} drew a "blanc_set" back on both faces and then, when the
 * stack held a real set, the set's art over both. An {@code ItemOverrides} pulled
 * the {@code ItemStack} in so the quads could depend on which set it was.
 * <p>
 * <b>26.2 mapping.</b> Identical in shape to {@link CardSpecialRenderer}: {@link
 * #extractArgument} is the {@code ItemOverrides.resolve} equivalent (it lifts the
 * {@link CardSet} off the stack) and {@link #submit} is the {@code getQuads}
 * equivalent (it draws front and back). The drawing reuses {@link FieldQuad}.
 * <p>
 * <b>Texture note.</b> The Forge model sampled the set's atlas sprite via {@code
 * getItemImageResourceLocation()} (an {@code item/...} atlas id). {@link
 * FieldQuad} draws from a real texture file, not an atlas sprite, so this uses
 * {@link CardSet#getInfoImageResourceLocation()} — the set's own {@code
 * textures/item/....png} art — for the front, and a same-resolution
 * {@code blanc_set.png} for the back. See TODO(visual) about the {@code
 * opened_set} overlay, which is dropped for now.
 */
public class CardSetSpecialRenderer implements SpecialModelRenderer<CardSet>
{
    @Override
    public void submit(CardSet set, PoseStack pose, SubmitNodeCollector collector,
        int light, int overlay, boolean glint, int outlineColor)
    {
        ResourceLocation back = blancSetBack();

        // The front: the set's own art if it is a real set, otherwise the blanc
        // back again (Forge drew set art only when set != CardSet.DUMMY).
        //
        // Asked THROUGH the manager, like every other producer of this
        // ResourceLocation. Handing the raw one to the renderer looked harmless --
        // it is the same texture the shop draws -- but the shop registers it,
        // the cache adopts it, and sweep() later releases it. The next pack
        // item drawn in a hand, an inventory or on the ground then missed in
        // TextureManager and paid read + decode + upload inline on the render
        // thread: the exact stall this whole subsystem exists to remove,
        // arriving by the one path nothing was watching. Until it is decoded
        // the item wears the plain back, as a card does.
        ResourceLocation front = back;
        if(set != null && set != CardSet.DUMMY)
        {
            ResourceLocation art = CardImageManager.getTextureCard(
                set.getInfoImageResourceLocation(), ClientProxy.activeSetInfoImageSize);
            front = art == null || art == DuelTextures.UNKNOWN ? back : art;
        }

        CardSpecialRenderer.drawTwoFaces(pose, collector, front, back);
    }

    /**
     * The {@code blanc_set} placeholder as a real texture file, mirroring {@link
     * CardRenderUtil#getMainCardBack()} but at the set-info resolution so it
     * matches the front art drawn from {@link CardSet#getInfoImageResourceLocation()}.
     */
    private static ResourceLocation blancSetBack()
    {
        return ResourceLocation.fromNamespaceAndPath(DuelDimension.MOD_ID,
            "textures/item/" + ClientProxy.activeSetInfoImageSize + "/blanc_set.png");
    }

    @Override
    public void getExtents(Consumer<Vector3fc> consumer)
    {
        final float h = CardSpecialRenderer.HALF;
        final float z = CardSpecialRenderer.Z;
        consumer.accept(new Vector3f(-h, -h, -z));
        consumer.accept(new Vector3f(h, -h, -z));
        consumer.accept(new Vector3f(h, h, z));
        consumer.accept(new Vector3f(-h, h, z));
    }

    @Override
    public CardSet extractArgument(ItemStack stack)
    {
        // The Forge ItemOverrides.resolve equivalent. DdItems.SET is a
        // CardSetItem (extends CardSetBaseItem, which owns getCardSet). Both the
        // sealed SET and OPENED_SET items share this renderer via their ClientItem
        // JSON; getCardSet works for either since both are CardSetBaseItems.
        return DdItems.SET.getCardSet(stack);
    }
}
