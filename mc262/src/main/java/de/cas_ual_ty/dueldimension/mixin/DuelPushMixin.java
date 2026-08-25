package de.cas_ual_ty.dueldimension.mixin;

import de.cas_ual_ty.dueldimension.duel.npc.DuelistDuels;
import de.cas_ual_ty.dueldimension.duel.overworld.OverworldDuels;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Server authority for keeping physical entity collisions off duellists.
 * <p>
 * <b>Targets {@link LivingEntity}, not {@code Entity}, and that is the whole
 * point of this class working.</b> {@code Entity.isPushable()} is overridden by
 * {@code LivingEntity}, which computes its own answer -- {@code isAlive() &&
 * !isSpectator() && !onClimbable()} -- and never calls {@code super}. An
 * injection at the head of {@code Entity.isPushable} therefore loads without
 * complaint and never fires for anything alive, players included. This mixin was
 * written that way and was dead from the day it shipped.
 * <p>
 * {@code Player} does not declare {@code isPushable} at all, so {@code
 * LivingEntity} is the lowest class that actually answers for one.
 */
@Mixin(LivingEntity.class)
public abstract class DuelPushMixin
{
    @Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
    private void dueldimension$duellistsAreNotPushable(CallbackInfoReturnable<Boolean> callback)
    {
        if((Object)this instanceof ServerPlayer player
            && (OverworldDuels.isLocked(player.getUUID())
                || DuelistDuels.isSeated(player.getUUID())))
        {
            callback.setReturnValue(false);
        }
    }
}
