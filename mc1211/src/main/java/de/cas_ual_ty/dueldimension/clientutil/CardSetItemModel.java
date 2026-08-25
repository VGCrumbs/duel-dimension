package de.cas_ual_ty.dueldimension.clientutil;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.cas_ual_ty.dueldimension.set.CardSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * The {@link ItemModel} bound to the card-SET item, wiring it to {@link
 * CardSetSpecialRenderer}. The set-item mirror of {@link CardItemModel}; see that
 * class for the Forge-to-26.2 mapping. Registered under {@code
 * dueldimension:card_set} (see {@link DdCardModels}) and selected by {@code
 * assets/dueldimension/items/card_set.json}.
 */
public class CardSetItemModel implements ItemModel
{
    private final CardSetSpecialRenderer renderer;

    public CardSetItemModel(CardSetSpecialRenderer renderer)
    {
        this.renderer = renderer;
    }

    @Override
    public void update(ItemStackRenderState state, ItemStack stack, ItemModelResolver resolver,
        ItemDisplayContext ctx, ClientLevel level, ItemOwner owner, int seed)
    {
        CardSet set = renderer.extractArgument(stack);
        ItemStackRenderState.LayerRenderState layer = state.newLayer();
        layer.setupSpecialModel(renderer, set);
        // TODO(visual): display transforms, as in CardItemModel.
    }

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
            return new CardSetItemModel(new CardSetSpecialRenderer());
        }

        @Override
        public void resolveDependencies(ResolvableModel.Resolver resolver)
        {
        }
    }
}
