package de.cas_ual_ty.dueldimension.mixin.client;

import de.cas_ual_ty.dueldimension.clientutil.overworld.CrosshairAction;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A click during an overworld duel plays the duel, not the world.
 * <p>
 * Injected at the game's own two click entry points rather than read from a
 * client tick handler, because the tick handler was racing vanilla for the same
 * queue: {@code handleKeybinds} drains the attack and use clicks inside
 * {@code Minecraft.tick}, and a Fabric END_CLIENT_TICK listener runs after it,
 * so {@code consumeClick} found nothing left and a duellist in first person
 * could not place or activate anything. Taking the click where it starts is
 * both earlier and unambiguous.
 * <p>
 * Only takes it while the player is locked to a board with no screen open, and
 * only when the duel actually does something with it -- otherwise the click
 * falls through to the game, so mining and using are untouched everywhere else.
 */
@Mixin(Minecraft.class)
public class MinecraftDuelClickMixin
{
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void dueldimension$duelAttack(CallbackInfoReturnable<Boolean> callback)
    {
        if(CrosshairAction.click(Minecraft.getInstance(), false))
        {
            // false: nothing was swung at, so no swing animation and no
            // continued attack on the block behind the board.
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void dueldimension$duelUse(CallbackInfo callback)
    {
        // The secondary click: declines a chain window, and otherwise acts on
        // whatever the crosshair is on, exactly as the left one does.
        if(CrosshairAction.click(Minecraft.getInstance(), true))
        {
            callback.cancel();
        }
    }
}
