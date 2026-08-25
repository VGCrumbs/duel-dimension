package de.cas_ual_ty.dueldimension.mixin.client;

import de.cas_ual_ty.dueldimension.clientutil.DuelSuppression;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Matches the server's collision protection locally, avoiding push jitter.
 * <p>
 * {@link LivingEntity} rather than {@code Entity}, for the reason spelled out in
 * {@link de.cas_ual_ty.dueldimension.mixin.DuelPushMixin}: {@code LivingEntity}
 * overrides {@code isPushable} without calling {@code super}, so an injection on
 * {@code Entity} never runs for a player.
 */
@Mixin(LivingEntity.class)
public abstract class LocalDuelPushMixin
{
    @Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
    private void dueldimension$localDuellistIsNotPushable(
        CallbackInfoReturnable<Boolean> callback)
    {
        if((Object)this instanceof LocalPlayer && DuelSuppression.inDuel())
        {
            callback.setReturnValue(false);
        }
    }
}
