package de.cas_ual_ty.dueldimension.mixin.client;

import de.cas_ual_ty.dueldimension.clientutil.DuelCamera;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps the player out of their own overhead shot.
 * <p>
 * The camera is over the middle of the mat looking down, and the duellist is
 * standing at the edge of it -- so from up there they are a figure lying across
 * their own board, between the view and the cards it is there to show.
 * <p>
 * At {@code shouldRender} rather than by cancelling the draw: this is the
 * question the renderer already asks about every entity, so answering it costs
 * nothing and cannot leave a half-built render state behind. It covers third
 * person as well as first, because it is asked either way -- the FIRST person
 * hand is a separate renderer and goes in
 * {@link OverheadHidesHandMixin}.
 */
@Mixin(EntityRenderer.class)
public class OverheadHidesPlayerMixin
{
    @Inject(method = "shouldRender(Lnet/minecraft/world/entity/Entity;"
        + "Lnet/minecraft/client/renderer/culling/Frustum;DDD)Z",
        at = @At("HEAD"), cancellable = true)
    private void dueldimension$hideSelfOverhead(Entity entity, Frustum frustum, double x,
        double y, double z, CallbackInfoReturnable<Boolean> callback)
    {
        // Only the player looking through the camera. Everybody else on the
        // board stays: a duel has an opponent, and hiding them would be hiding
        // half of what the view is for.
        if(DuelCamera.active()
            && entity == net.minecraft.client.Minecraft.getInstance().player)
        {
            callback.setReturnValue(false);
        }
    }
}
