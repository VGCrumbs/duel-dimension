package de.cas_ual_ty.dueldimension.mixin.client;

import de.cas_ual_ty.dueldimension.clientutil.DuelSuppression;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * No floating names over a duel.
 * <p>
 * A duel board is a thing you read: five zones a side, cards laid flat, and a
 * monster standing on each of them. A player's name hangs in the air at head
 * height and renders through everything, so during a duel it sits squarely over
 * the half of the board its owner is standing behind — which is the half you
 * are trying to look at.
 * <p>
 * Only players, and only while a duel is running. Everything else keeps its
 * name, and so do players the moment the duel ends.
 * <p>
 * <b>Cancelled at HEAD, on the player's own renderer.</b> 1.21.1 draws name
 * tags immediately instead of extracting them onto a pooled render state, so
 * there is no stale value left over from last frame and nothing has to be
 * written before it is cleared: not drawing is the whole of it.
 * <p>
 * {@code PlayerRenderer} overrides {@code renderNameTag} to draw the
 * below-name scoreboard line and then calls super for the name itself, so
 * cancelling that one override removes both -- exactly what clearing
 * {@code nameTag} and {@code scoreText} used to do. Checked in the bytecode
 * rather than assumed: the body is the scoreboard block followed by an
 * {@code invokespecial} to {@code LivingEntityRenderer.renderNameTag}. The
 * descriptor is spelled out in full so the synthetic bridge that takes an
 * Entity is not matched as well.
 */
@Mixin(PlayerRenderer.class)
public class NameTagMixin
{
    @Inject(method = "renderNameTag(Lnet/minecraft/client/player/AbstractClientPlayer;"
        + "Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;"
        + "Lnet/minecraft/client/renderer/MultiBufferSource;IF)V",
        at = @At("HEAD"), cancellable = true)
    private void dueldimension$hideDuringDuel(AbstractClientPlayer player, Component name,
        PoseStack poseStack, MultiBufferSource buffer, int packedLight, float partialTick,
        CallbackInfo callback)
    {
        if(DuelSuppression.inDuel())
        {
            callback.cancel();
        }
    }
}
