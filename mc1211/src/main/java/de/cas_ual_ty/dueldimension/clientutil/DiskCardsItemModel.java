package de.cas_ual_ty.dueldimension.clientutil;

import com.mojang.blaze3d.vertex.PoseStack;
import de.cas_ual_ty.dueldimension.compat.SubmitNodeCollector;
import net.fabricmc.fabric.api.client.model.loading.v1.FabricBakedModelManager;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The cards standing on a worn duel disk.
 *
 * <h2>The problem this class exists to solve, on both versions</h2>
 *
 * A duel disk stack does not say whose arm it is on, and the cards drawn on it
 * are the wearer's board. So the renderer needs to know WHO is being drawn, and
 * an item renderer is normally handed only the stack.
 * <p>
 * On 26.2 the answer is the item-model layer: an {@code ItemModel} is given the
 * {@code ItemOwner} as well. 1.21.1 has no item-model layer at all, and its
 * {@code DynamicItemRenderer.render} is handed the stack, a display context, a
 * pose and a buffer source — no entity, anywhere.
 *
 * <h2>How it is answered here instead</h2>
 *
 * The one place 1.21.1 does know the wearer is the layer that draws a held item,
 * and this mod is already in there:
 * {@link de.cas_ual_ty.dueldimension.mixin.client.ItemInHandLayerMixin} redirects
 * {@code getOffhandItem} to substitute the worn disk. That redirect knows the
 * entity, so it records whether it is about to hand the disk to the LOCAL
 * player, and this reads the answer a moment later.
 * <p>
 * <b>That coupling is real and worth stating plainly.</b> It works because
 * rendering is single-threaded and the redirect runs immediately before the
 * layer draws the stack it returned — so the flag cannot be read by a different
 * draw than the one that set it. It is cleared here on read, so any OTHER way a
 * duel disk gets drawn — in a GUI slot, in an item frame, on the ground — sees
 * it unset and draws a bare plate, which is the correct answer for all three.
 *
 * <h2>Whose board, and why only one</h2>
 *
 * Only the local player's. Every other disk in the world draws a bare plate,
 * because a client is only ever sent the redacted board for its own seat. A
 * duellist across the field wearing a visible board would mean the server had
 * told this client something it is not entitled to know.
 *
 * <h2>The disk itself is drawn here too, which it is not on 26.2</h2>
 *
 * 26.2 draws a disk with a {@code minecraft:composite} of two models: the plate,
 * and this. 1.21.1 has no composite -- an item resolves to exactly one model, and
 * a model that says {@code builtin/entity} contributes no quads at all. So making
 * the disk self-drawing takes its own geometry away, and this has to put it back.
 * <p>
 * It is the same two things in the same order, moved from JSON into Java: the
 * frame's baked quads, then the cards standing on it. The frame model is the one
 * 26.2's composite names, under the same {@code _frame} suffix the stock disk
 * already used -- {@code tools/port_resources_1211.py} moves the other nine to
 * match, so there is one rule here rather than a special case.
 */
public final class DiskCardsItemModel implements BuiltinItemRendererRegistry.DynamicItemRenderer
{
    /**
     * Set by the in-hand layer when the disk it is about to draw belongs to this
     * client's player. See the class note for why a static is sound here.
     */
    private static boolean drawingForLocalPlayer;

    private final DiskCardsRenderer renderer = new DiskCardsRenderer();

    /** Called from the mixin, immediately before the layer draws the disk. */
    public static void markLocalPlayer()
    {
        drawingForLocalPlayer = true;
    }

    /**
     * Whether this draw is the local player's disk, clearing the flag as it
     * answers.
     * <p>
     * Cleared on read rather than at the end of a frame, so exactly one draw can
     * consume each mark. A disk drawn anywhere else gets false, which is what
     * makes an inventory icon or an item frame show a bare plate instead of
     * somebody's live board.
     */
    private static boolean takeLocalPlayerMark()
    {
        boolean marked = drawingForLocalPlayer;
        drawingForLocalPlayer = false;
        return marked;
    }

    /**
     * The frame model for a disk item, by the rule the resource port follows.
     * <p>
     * {@code dueldimension:duel_disk} becomes {@code item/duel_disk_frame}, which
     * is the file 26.2's composite already names for the stock disk.
     */
    private static ResourceLocation frameModel(ItemStack stack)
    {
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        return ResourceLocation.fromNamespaceAndPath(id.getNamespace(),
            "item/" + id.getPath() + "_frame");
    }

    /**
     * Asks the bakery for the ten frame models, which nothing else references.
     * <p>
     * Called once from the client initialiser. Without it every frame model is
     * absent from the bake -- nothing points at them any more, since each disk's
     * own model is now a {@code builtin/entity} marker -- and every duel disk in
     * the game is an invisible plate with cards floating over it.
     */
    public static void registerModels()
    {
        ModelLoadingPlugin.register(context ->
        {
            List<ResourceLocation> frames = new ArrayList<>();
            for(String name : de.cas_ual_ty.dueldimension.duel.profile.DuelDisks.ALL)
            {
                frames.add(ResourceLocation.fromNamespaceAndPath("dueldimension",
                    "item/" + name + "_frame"));
            }
            context.addModels(frames);
        });
    }

    @Override
    public void render(ItemStack stack, ItemDisplayContext ctx, PoseStack pose,
        MultiBufferSource buffers, int light, int overlay)
    {
        // The plate first, and unconditionally: it is what the item LOOKS like,
        // so it is drawn in an inventory slot and an item frame and on a
        // stranger's arm, none of which get a board.
        renderFrame(stack, pose, buffers, light, overlay);

        if(!takeLocalPlayerMark())
        {
            return;
        }
        DiskCardsRenderer.Rack rack =
            DiskCardsRenderer.rackFor(DuelClientState.board.self(), 0);
        if(rack.isEmpty())
        {
            return;
        }

        // See CardItemModel: ItemRenderer translates to the model's corner
        // before calling a custom renderer, and the slot table is in the
        // model's own centred coordinates.
        pose.pushPose();
        pose.translate(0.5F, 0.5F, 0.5F);
        renderer.submit(rack, pose, new SubmitNodeCollector(buffers), light, overlay, false, 0);
        pose.popPose();
    }

    /**
     * The disk's own quads, drawn the way vanilla would have drawn them.
     * <p>
     * <b>{@code ItemDisplayContext.NONE} is the whole trick.</b> The obvious call
     * would be to hand the frame model back to {@code ItemRenderer.render} with
     * the context this draw is really in -- and that would apply the display
     * transform a SECOND time, because the caller has already applied it before
     * reaching a custom renderer. {@code NONE}'s transform is the identity, so
     * the model is drawn exactly where the pose already is.
     * <p>
     * The half-block undoes the other thing the caller did: it translates by
     * -0.5 on every axis so a custom renderer starts at the model's corner, and
     * {@code render} does that again for itself. See {@code CardItemModel}, which
     * meets the same offset from the other side.
     * <p>
     * The quad-level call underneath ({@code renderModelLists}) is private on
     * 1.21.1, so this goes in through the public door. No loss: {@code render}
     * with an identity transform does the same work, and gets the render type and
     * the enchantment glint right without this having to know how.
     */
    private void renderFrame(ItemStack stack, PoseStack pose, MultiBufferSource buffers,
        int light, int overlay)
    {
        BakedModel frame = ((FabricBakedModelManager)Minecraft.getInstance().getModelManager())
            .getModel(frameModel(stack));
        if(frame == null)
        {
            // A disk whose frame model failed to bake. Drawn as nothing rather
            // than as a crash, and the missing model is already in the log.
            return;
        }

        pose.pushPose();
        pose.translate(0.5F, 0.5F, 0.5F);
        Minecraft.getInstance().getItemRenderer().render(stack, ItemDisplayContext.NONE,
            false, pose, buffers, light, overlay, frame);
        pose.popPose();
    }
}
