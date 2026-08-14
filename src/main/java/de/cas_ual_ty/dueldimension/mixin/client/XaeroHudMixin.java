package de.cas_ual_ty.dueldimension.mixin.client;

import de.cas_ual_ty.dueldimension.clientutil.DuelSuppression;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps Xaero's minimap off the screen during a duel.
 * <p>
 * The minimap sits in the corner a duellist is reading -- life points on one
 * side, the opponent's on the other -- and a duel is the one time a player is
 * not navigating. It comes back the moment the duel ends.
 * <p>
 * Targeted BY NAME and marked {@code @Pseudo}, because Xaero is not a
 * dependency of this mod and most players will not have it -- which is exactly
 * what Pseudo is for: a target that may not be on the classpath at all, applied
 * where it is and quietly skipped where it is not. The injection is
 * {@code require = 0} for the same reason. Xaero publishes no API for this -- there is no callback and
 * no config hook -- so its own renderer is the only place to ask, and
 * {@code xaero.hud.render.HudRenderer.render} is the one entry point every
 * module goes through.
 */
@Pseudo
@Mixin(targets = "xaero.hud.render.HudRenderer", remap = false)
public class XaeroHudMixin
{
    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private void dueldimension$hideDuringDuel(CallbackInfo callback)
    {
        if(DuelSuppression.hudHidden())
        {
            callback.cancel();
        }
    }
}
