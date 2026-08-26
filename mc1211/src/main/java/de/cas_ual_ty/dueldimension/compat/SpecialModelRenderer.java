package de.cas_ual_ty.dueldimension.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3fc;

import java.util.function.Consumer;

/**
 * 26.2's {@code SpecialModelRenderer}, as a mod-owned interface.
 * <p>
 * 1.21.4 split item rendering into an {@code ItemModel} that decides WHAT to
 * draw and a {@code SpecialModelRenderer} that draws it. 1.21.1 has neither —
 * the whole {@code net.minecraft.client.renderer.item} package there is
 * {@code ItemProperties} and two friends — so the interface is provided here
 * instead, exactly as {@link SubmitNodeCollector} provides the collector it
 * takes.
 * <p>
 * Same three methods as 26.2's, with one substitution: the collector is the
 * compatibility one. That is the only difference, which is what makes the three
 * renderer classes that implement it — {@code CardSpecialRenderer},
 * {@code CardSetSpecialRenderer}, {@code DiskCardsRenderer} — port with an
 * import swap and no change to a single line of their drawing.
 *
 * <h2>Who calls it here</h2>
 *
 * On 26.2 the game does, through the item-model system. On 1.21.1 nothing in
 * vanilla knows this type exists: the callers are the mod's own
 * {@code DynamicItemRenderer}s, which Fabric's
 * {@code BuiltinItemRendererRegistry} binds per ITEM rather than per model-type
 * id. The renderers keep their shape; only what reaches them changes.
 *
 * @param <T> what the renderer needs off the stack — a card, a set, a board
 */
public interface SpecialModelRenderer<T>
{
    void submit(T argument, PoseStack pose, SubmitNodeCollector collector,
        int light, int overlay, boolean glint, int outlineColor);

    void getExtents(Consumer<Vector3fc> consumer);

    T extractArgument(ItemStack stack);
}
