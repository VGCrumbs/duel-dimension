package de.cas_ual_ty.dueldimension.clientutil;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.cas_ual_ty.dueldimension.card.CardHolder;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * The {@link ItemModel} bound to the card item, wiring it to {@link
 * CardSpecialRenderer}.
 * <p>
 * <b>Forge origin.</b> On Forge the binding of the card item to its dynamic model
 * happened in {@code ClientProxy.modelBake}, which swapped the item's baked model
 * for a {@code CardBakedModel} in the model registry. There was no separate
 * "item model" type — the {@code BakedModel} was the whole story.
 * <p>
 * <b>26.2 mapping.</b> 26.2 splits the job: an {@link ItemModel} decides, per
 * stack, what to draw, and a {@link net.minecraft.client.renderer.special.SpecialModelRenderer}
 * does the drawing. This {@code ItemModel} does the deciding — it extracts the
 * {@link CardHolder} and hands it, with the renderer, to a fresh render layer.
 * The type is registered under {@code dueldimension:card} (see {@link
 * DdCardModels}) and the item's {@code assets/dueldimension/items/card.json}
 * selects it.
 */
public class CardItemModel implements ItemModel
{
    private final CardSpecialRenderer renderer;

    public CardItemModel(CardSpecialRenderer renderer)
    {
        this.renderer = renderer;
    }

    @Override
    public void update(ItemStackRenderState state, ItemStack stack, ItemModelResolver resolver,
        ItemDisplayContext ctx, ClientLevel level, ItemOwner owner, int seed)
    {
        CardHolder card = renderer.extractArgument(stack);
        ItemStackRenderState.LayerRenderState layer = state.newLayer();
        layer.setupSpecialModel(renderer, card);
        // TODO(visual): display transforms. Forge's applyTransform scaled the
        // card to 0.5 and nudged it up in hand/ground and flipped it 180 in the
        // item frame. A card at the default transform still renders; the exact
        // per-context ItemTransform is left for the GPU pass.
    }

    /**
     * The unbaked form the {@link ItemModel} system deserialises from the
     * ClientItem JSON. It carries no fields — the card's identity comes from the
     * stack at render time, not from the model JSON — so its codec is a unit
     * codec and {@link #bake} just constructs the model.
     */
    public record Unbaked() implements ItemModel.Unbaked
    {
        public static final MapCodec<Unbaked> MAP_CODEC =
            RecordCodecBuilder.mapCodec(i -> i.point(new Unbaked()));

        @Override
        public MapCodec<? extends ItemModel.Unbaked> type()
        {
            return MAP_CODEC;
        }

        @Override
        public ItemModel bake(ItemModel.BakingContext ctx, org.joml.Matrix4fc transform)
        {
            return new CardItemModel(new CardSpecialRenderer());
        }

        @Override
        public void resolveDependencies(ResolvableModel.Resolver resolver)
        {
            // No sub-model dependencies to resolve — the card face is drawn from
            // runtime-loaded textures, not from referenced JSON models.
        }
    }
}
