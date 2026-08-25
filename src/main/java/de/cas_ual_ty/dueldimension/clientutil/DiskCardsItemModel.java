package de.cas_ual_ty.dueldimension.clientutil;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The layer of a duel disk that is the cards on it.
 * <p>
 * Selected from {@code assets/dueldimension/items/duel_disk.json} as the second
 * half of a {@code minecraft:composite}: the first is {@code
 * item/duel_disk_frame}, the ordinary baked model, and this adds the board on
 * top. Registered under {@code dueldimension:duel_disk_cards} (see {@link
 * DdCardModels}).
 * <p>
 * <b>Why an {@link ItemModel} and not just a {@link
 * net.minecraft.client.renderer.special.SpecialModelRenderer}.</b> A special
 * renderer is handed the {@link ItemStack} and nothing else, and a duel disk
 * stack says nothing about whose arm it is on. An {@code ItemModel} is handed
 * the {@link ItemOwner} as well, which is the one place the wearer can be
 * identified — so the rack is resolved here and passed to the renderer as its
 * argument. That is also why {@code minecraft:special} cannot be used directly,
 * even though this otherwise does exactly what {@code SpecialModelWrapper} does.
 * <p>
 * <b>Whose board.</b> Only the wearer's own, and only when the wearer is this
 * client's player. Every other duel disk in the world draws a bare plate,
 * because a client is never sent another player's board and must not be: the one
 * board it holds is the redacted one for its own seat. A duellist across the
 * field wearing a full board would be a client that had been told something it
 * is not entitled to know.
 */
public class DiskCardsItemModel implements ItemModel
{
    private final DiskCardsRenderer renderer;
    /**
     * The frame model's display transforms, particle and lighting.
     * <p>
     * <b>Not optional, and the reason the first attempt drew nothing.</b> A
     * layer is not automatically placed where the item is: the {@code display}
     * block that puts the disk on the arm belongs to the frame model, and a
     * second layer that never asks for it renders at the item's untransformed
     * origin — a different place and a different scale, which looks exactly like
     * "the cards are not appearing". {@code SpecialModelWrapper} ends its own
     * {@code update} with this call for the same reason.
     */
    private final ModelRenderProperties properties;
    private final Matrix4fc transformation;
    private final Supplier<Vector3fc[]> extents;

    public DiskCardsItemModel(DiskCardsRenderer renderer, ModelRenderProperties properties,
        Matrix4fc transformation, Supplier<Vector3fc[]> extents)
    {
        this.renderer = renderer;
        this.properties = properties;
        this.transformation = transformation;
        this.extents = extents;
    }

    @Override
    public void update(ItemStackRenderState state, ItemStack stack, ItemModelResolver resolver,
        ItemDisplayContext ctx, ClientLevel level, ItemOwner owner, int seed)
    {
        DiskCardsRenderer.Rack rack = rackFor(owner);
        // Appended even when empty, and BEFORE the early return. The render
        // state is cached against this identity; a rack that changed without
        // saying so would leave the disk showing the board as it was when the
        // state was last built, which for a duel that has only just started is
        // an empty plate for the rest of it.
        state.appendModelIdentityElement(rack);
        if(rack.isEmpty())
        {
            // No layer at all rather than a layer that draws nothing: an empty
            // rack is the common case — every disk in the world outside a duel.
            return;
        }
        ItemStackRenderState.LayerRenderState layer = state.newLayer();
        layer.setExtents(extents);
        layer.setLocalTransform(transformation);
        layer.setupSpecialModel(renderer, rack);
        properties.applyToLayer(layer, ctx);
    }

    /**
     * The board this disk is showing.
     * <p>
     * Controller 0 throughout, because the only board that can be reached from
     * here is this player's own, and 0 is their seat in their own snapshot. That
     * also picks their card back and sleeve out of {@link CardFaces#back} — the
     * anime or TCG cover they chose, the same one their cards wear on the field.
     */
    private static DiskCardsRenderer.Rack rackFor(ItemOwner owner)
    {
        Minecraft minecraft = Minecraft.getInstance();
        if(owner == null || minecraft.player == null || !isLocal(owner, minecraft))
        {
            return DiskCardsRenderer.Rack.EMPTY;
        }
        // DuelClientState.board, not ClientDuelField.boardToDraw(): the latter
        // answers with the SPECTATOR copy when watching somebody else's duel,
        // and its self() is then one of the two duellists rather than this
        // player. A spectator is wearing a disk with nothing on it, which is
        // true — they are not in a duel.
        return DiskCardsRenderer.rackFor(DuelClientState.board.self(), 0);
    }

    /**
     * Is this the player at the keyboard?
     * <p>
     * Not a bare {@code ==}: {@link ItemOwner} is an interface an entity
     * implements, and {@link ItemOwner#offsetFromOwner} wraps one, so an owner
     * that IS the player can arrive as something that is not identical to it.
     * Asking for the living entity behind it covers both.
     */
    private static boolean isLocal(ItemOwner owner, Minecraft minecraft)
    {
        return owner == minecraft.player || owner.asLivingEntity() == minecraft.player;
    }

    /**
     * @param base the frame model, for its display transforms. The composite's
     *             other half names the same model; this layer has to be told
     *             about it because a layer carries its own transforms
     */
    public record Unbaked(Identifier base) implements ItemModel.Unbaked
    {
        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(
            i -> i.group(Identifier.CODEC.fieldOf("base").forGetter(Unbaked::base))
                .apply(i, Unbaked::new));

        @Override
        public MapCodec<? extends ItemModel.Unbaked> type()
        {
            return MAP_CODEC;
        }

        @Override
        public ItemModel bake(ItemModel.BakingContext ctx, Matrix4fc transform)
        {
            // Lifted from SpecialModelWrapper.Unbaked.getProperties, which is
            // the one place vanilla does this: resolve the base model, take its
            // top texture slots, and read the transforms off it.
            ModelBaker baker = ctx.blockModelBaker();
            ResolvedModel model = baker.getModel(base);
            ModelRenderProperties properties = ModelRenderProperties.fromResolvedModel(
                baker, model, model.getTopTextureSlots());

            DiskCardsRenderer renderer = new DiskCardsRenderer();
            Vector3fc[] extents = extentsOf(renderer);
            return new DiskCardsItemModel(renderer, properties, transform, () -> extents);
        }

        @Override
        public void resolveDependencies(ResolvableModel.Resolver resolver)
        {
            // The frame, so it is baked before this asks the baker for it. The
            // composite's other half depends on it too, but a model that only
            // resolves because something ELSE happens to want it is a model that
            // breaks when that something changes.
            resolver.markDependency(base);
        }

        private static Vector3fc[] extentsOf(DiskCardsRenderer renderer)
        {
            List<Vector3fc> corners = new ArrayList<>();
            renderer.getExtents(corners::add);
            return corners.toArray(new Vector3fc[0]);
        }
    }
}
