package de.cas_ual_ty.dueldimension.mixin.client;

import de.cas_ual_ty.dueldimension.clientutil.PlayerSkins;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.ClientAsset;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Supplies a skin for players this mod knows about.
 * <p>
 * A development client runs offline: no session, so no profile properties, so
 * nowhere for the game to fetch a skin from, and every dev player is Steve
 * whoever they are. {@link PlayerSkins} holds the answer; this is what asks it.
 * <p>
 * On Forge the same job took two hooks and a compromise —
 * {@code RenderPlayerEvent.Pre} to hide the real body and a render layer to
 * draw a replacement over it, because the skin itself could not be changed
 * without a mixin. Fabric has neither event, so this does it properly instead,
 * and properly turns out to be smaller: the skin is patched at the source and
 * every part of the game that draws a player — the world, the inventory, the
 * skin-layers mod in the run folder — picks it up without knowing anything
 * happened.
 * <p>
 * {@code PlayerSkin.Patch} is built for exactly this. Only the fields named are
 * replaced, so a cape, an elytra texture and anything added later survive
 * untouched rather than being flattened by a wholesale rebuild.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin
{
    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void dueldimension$supplySkin(CallbackInfoReturnable<PlayerSkin> callback)
    {
        PlayerSkins.Skin override = PlayerSkins.of((AbstractClientPlayer)(Object)this);
        if(override == null)
        {
            return;
        }
        callback.setReturnValue(callback.getReturnValue().with(new PlayerSkin.Patch(
            Optional.of(new ClientAsset.ResourceTexture(override.texture())),
            Optional.empty(),
            Optional.empty(),
            Optional.of(override.slim() ? PlayerModelType.SLIM : PlayerModelType.WIDE))));
    }
}
