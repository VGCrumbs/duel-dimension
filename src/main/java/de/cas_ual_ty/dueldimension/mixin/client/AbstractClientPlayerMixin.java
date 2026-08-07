package de.cas_ual_ty.dueldimension.mixin.client;

import de.cas_ual_ty.dueldimension.clientutil.OutfitSkins;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Supplies the skin and the body a duelist is drawn on.
 * <p>
 * Two jobs, one hook, because they are the same question. A development client
 * runs offline: no session, so no profile properties, so nowhere for the game to
 * fetch a skin from, and every dev player is Steve whoever they are. And a
 * player wearing an outfit needs the body their clothes were cut for, plus
 * whatever they edited out from under them. {@link OutfitSkins} works out the
 * answer; this is what asks it.
 * <p>
 * On Forge the same job took two hooks and a compromise —
 * {@code RenderPlayerEvent.Pre} to hide the real body and a render layer to
 * draw a replacement over it, because the skin itself could not be changed
 * without a mixin. Fabric has neither event, so this does it properly instead,
 * and properly turns out to be smaller: the skin is patched at the source and
 * every part of the game that draws a player — the world, the inventory, the
 * skin-layers mod in the run folder, and the choice of classic or slim renderer
 * — picks it up without knowing anything happened.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin
{
    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void dueldimension$supplySkin(CallbackInfoReturnable<PlayerSkin> callback)
    {
        PlayerSkin resolved = callback.getReturnValue();
        // The game's own answer is passed in rather than asked for: this runs
        // inside getSkin, and asking would call itself.
        PlayerSkin.Patch patch = OutfitSkins.patch(
            (AbstractClientPlayer)(Object)this, resolved);
        if(patch != null)
        {
            callback.setReturnValue(resolved.with(patch));
        }
    }
}
