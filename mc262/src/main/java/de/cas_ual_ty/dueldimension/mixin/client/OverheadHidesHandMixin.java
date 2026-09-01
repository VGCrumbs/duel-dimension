package de.cas_ual_ty.dueldimension.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import de.cas_ual_ty.dueldimension.clientutil.DuelCamera;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * And the hand, which is a separate renderer from the player.
 * <p>
 * The first-person arm is not drawn as part of the body -- it is posed in view
 * space and drawn over whatever the camera is looking at, so moving the camera
 * to the ceiling takes it along. The duel disk especially: it is a large model
 * held up near the middle of the screen, which over an overhead shot is a
 * plastic slab across the board.
 */
@Mixin(ItemInHandRenderer.class)
public class OverheadHidesHandMixin
{
    @Inject(method = "submitHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;"
        + "Lnet/minecraft/client/renderer/SubmitNodeCollector;"
        + "Lnet/minecraft/client/player/LocalPlayer;I)V",
        at = @At("HEAD"), cancellable = true)
    private void dueldimension$hideHandOverhead(float partialTick, PoseStack poseStack,
        SubmitNodeCollector collector, LocalPlayer player, int light, CallbackInfo callback)
    {
        if(DuelCamera.active())
        {
            callback.cancel();
        }
    }
}
