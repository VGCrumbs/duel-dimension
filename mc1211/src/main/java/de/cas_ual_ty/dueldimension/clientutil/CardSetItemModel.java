package de.cas_ual_ty.dueldimension.clientutil;

import com.mojang.blaze3d.vertex.PoseStack;
import de.cas_ual_ty.dueldimension.compat.SubmitNodeCollector;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Draws the card-SET item, by handing the stack to {@link CardSetSpecialRenderer}.
 * <p>
 * The set-item mirror of {@link CardItemModel}; see that class for why 26.2's
 * item-model split has no counterpart here and what replaces it.
 *
 * <h2>One instance, two items</h2>
 *
 * <b>This must be registered against BOTH the sealed and the opened set.</b> On
 * 26.2 that happens in JSON — {@code items/set.json} and
 * {@code items/opened_set.json} are identical files naming the same model type —
 * so binding both is something the 26.2 build does without anyone deciding to.
 * Here the binding is a Java call per item, and forgetting the second one does
 * not fail: an opened pack simply draws as its plain model, with no art. See
 * {@link DdCardModels#register}, which registers one instance twice for exactly
 * this reason.
 * <p>
 * Both items are {@code CardSetBaseItem}s, so
 * {@link CardSetSpecialRenderer#extractArgument} answers for either.
 */
public final class CardSetItemModel implements BuiltinItemRendererRegistry.DynamicItemRenderer
{
    private final CardSetSpecialRenderer renderer = new CardSetSpecialRenderer();

    @Override
    public void render(ItemStack stack, ItemDisplayContext ctx, PoseStack pose,
        MultiBufferSource buffers, int light, int overlay)
    {
        // See CardItemModel: ItemRenderer translates to the model's corner
        // before calling a custom renderer, and these quads are centred.
        pose.pushPose();
        pose.translate(0.5F, 0.5F, 0.5F);
        renderer.submit(renderer.extractArgument(stack), pose,
            new SubmitNodeCollector(buffers), light, overlay, false, 0);
        pose.popPose();
    }
}
