package de.cas_ual_ty.dueldimension.clientutil;

import com.mojang.blaze3d.vertex.PoseStack;
import de.cas_ual_ty.dueldimension.compat.SubmitNodeCollector;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

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

    @Override
    public void render(ItemStack stack, ItemDisplayContext ctx, PoseStack pose,
        MultiBufferSource buffers, int light, int overlay)
    {
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
}
