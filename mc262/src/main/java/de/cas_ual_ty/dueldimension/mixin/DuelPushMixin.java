package de.cas_ual_ty.dueldimension.mixin;

import de.cas_ual_ty.dueldimension.duel.npc.DuelistDuels;
import de.cas_ual_ty.dueldimension.duel.overworld.OverworldDuels;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Server authority for keeping physical entity collisions off duellists. */
@Mixin(Entity.class)
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
