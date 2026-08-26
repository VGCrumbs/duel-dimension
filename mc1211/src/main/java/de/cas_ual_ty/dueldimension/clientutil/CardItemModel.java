package de.cas_ual_ty.dueldimension.clientutil;

import com.mojang.blaze3d.vertex.PoseStack;
import de.cas_ual_ty.dueldimension.compat.SubmitNodeCollector;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Draws the card item, by handing the stack to {@link CardSpecialRenderer}.
 *
 * <h2>Why this is a different class from the 26.2 one</h2>
 *
 * 26.2 splits item rendering in two: an {@code ItemModel} decides what to draw
 * for a stack, and a {@code SpecialModelRenderer} draws it. That split arrived
 * in 1.21.4. Here there is no {@code ItemModel}, no {@code ItemStackRenderState},
 * no unbaked/bake/resolveDependencies protocol and no {@code MapCodec} keyed
 * model type — so this cannot be edited into shape, only replaced.
 * <p>
 * What replaces it is the path 1.21.1 already has for an item that draws itself:
 * a model whose JSON says {@code "parent": "minecraft:builtin/entity"}, which
 * makes {@code BakedModel.isCustomRenderer()} true, which makes
 * {@code ItemRenderer.render} call through to a renderer instead of drawing
 * quads. Fabric exposes that hook as {@code DynamicItemRenderer}, and it hands
 * over exactly the {@code PoseStack} and {@code MultiBufferSource} that
 * {@link SubmitNodeCollector} wraps — so {@link CardSpecialRenderer}'s drawing
 * is untouched.
 *
 * <h2>The binding moved from JSON to Java</h2>
 *
 * On 26.2 {@code assets/dueldimension/items/card.json} names a model TYPE and
 * the type is registered by id. Here the registration is per ITEM, in
 * {@link DdCardModels}. Worth knowing because a missing binding fails quietly:
 * the item simply draws as its plain model.
 */
public final class CardItemModel implements BuiltinItemRendererRegistry.DynamicItemRenderer
{
    private final CardSpecialRenderer renderer = new CardSpecialRenderer();

    @Override
    public void render(ItemStack stack, ItemDisplayContext ctx, PoseStack pose,
        MultiBufferSource buffers, int light, int overlay)
    {
        // Centred, because the two ends disagree about the origin.
        // ItemRenderer.render translates by -0.5 on every axis before calling a
        // custom renderer, so the pose arrives with its origin at the model's
        // CORNER -- while CardSpecialRenderer draws a quad from -0.5 to +0.5
        // about the middle, as the 26.2 item transform expected. Without this
        // the card is drawn half a block off in three directions.
        pose.pushPose();
        pose.translate(0.5F, 0.5F, 0.5F);
        renderer.submit(renderer.extractArgument(stack), pose,
            new SubmitNodeCollector(buffers), light, overlay, false, 0);
        pose.popPose();
    }
}
