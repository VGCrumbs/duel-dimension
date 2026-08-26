package de.cas_ual_ty.dueldimension.mixin.client;

import de.cas_ual_ty.dueldimension.clientutil.DuelSuppression;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the block-selection outline off a duel.
 * <p>
 * A duellist standing at a board is looking at cards, and the game is drawing a
 * black wireframe around whichever block happens to be behind them -- usually a
 * square of the floor the board is painted on, right through the middle of the
 * field. It is the same problem Jade has and it wants the same answer.
 * <p>
 * The outline is also a lie during a duel: it says "you can interact with
 * this", and a locked duellist cannot. The server refuses their block
 * interactions and the click is spent on the board instead, so the one thing
 * the outline promises is the one thing it cannot deliver.
 * <p>
 * Cancelled at the point the outline is DRAWN rather than by hiding the hit
 * result, because the hit result is what the board's own picking runs on -- and
 * a duellist who could no longer target anything would be a duellist who could
 * no longer play.
 * <p>
 * 26.2 calls that point {@code submitBlockOutline}, because 26.2 submits
 * geometry rather than drawing it. Here it is {@code renderHitOutline}, which is
 * the same decision at the same place under the name it had before the
 * submission model arrived.
 */
@Mixin(LevelRenderer.class)
public class BlockOutlineMixin
{
    @Inject(method = "renderHitOutline(Lcom/mojang/blaze3d/vertex/PoseStack;"
        + "Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/entity/Entity;"
        + "DDDLnet/minecraft/core/BlockPos;"
        + "Lnet/minecraft/world/level/block/state/BlockState;)V",
        at = @At("HEAD"), cancellable = true)
    private void dueldimension$hideDuringDuel(CallbackInfo callback)
    {
        // Either presentation, and the same one answer every other overlay
        // asks: a duel on the screen has a screen over the world anyway, and a
        // duel on a board is being looked THROUGH.
        if(DuelSuppression.inDuel())
        {
            callback.cancel();
        }
    }
}
