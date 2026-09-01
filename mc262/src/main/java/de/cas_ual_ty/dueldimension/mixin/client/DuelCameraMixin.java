package de.cas_ual_ty.dueldimension.mixin.client;

import de.cas_ual_ty.dueldimension.clientutil.DuelCamera;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Puts the camera over the board while the overhead view is on.
 * <p>
 * At the TAIL of {@code update}, so everything vanilla does to the camera --
 * following the entity, bobbing, clipping it out of a wall -- has already
 * happened and is then replaced. Injecting at the head would have all of that
 * run afterwards and undo it.
 * <p>
 * {@link DuelCamera#placement()} answers null whenever there is no board, so
 * this is inert outside a duel without needing to know anything about duels.
 */
@Mixin(Camera.class)
public abstract class DuelCameraMixin
{
    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Inject(method = "update(Lnet/minecraft/client/DeltaTracker;)V", at = @At("TAIL"))
    private void dueldimension$overhead(DeltaTracker delta, CallbackInfo callback)
    {
        DuelCamera.Placement where = DuelCamera.placement();
        if(where != null)
        {
            // Rotation first: setPosition only moves the point, but the basis
            // the renderer culls and draws against comes out of the rotation,
            // and setting it second would leave the two describing different
            // cameras for one frame.
            setRotation(where.yaw(), where.pitch());
            setPosition(where.x(), where.y(), where.z());
        }
    }
}
