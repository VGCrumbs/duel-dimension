package de.cas_ual_ty.dueldimension.mixin.client;

import de.cas_ual_ty.dueldimension.clientutil.DuelSuppression;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stands a duellist still for the duration of a duel.
 * <p>
 * A duel is played at a board the player is standing at. Walking off it is not
 * a move the duel has: on the world board it carries the camera away from the
 * field the duel is being drawn on, and W held for a second is a duellist
 * looking at their own cards from across the garden. Crouching and jumping are
 * the same thing more briefly.
 * <p>
 * Taken at the INPUT rather than by cancelling movement itself, which is the
 * difference between a player who cannot walk and a player who is being shoved
 * back. Nothing is pushed, nothing is teleported, no position is corrected --
 * the keys simply are not read, so the server never hears a step that has to be
 * undone. Looking around still works; so does everything else a duellist does.
 * <p>
 * Applied at TAIL, after vanilla has read the keyboard and worked out its
 * impulses, so this replaces a finished answer rather than racing the code that
 * computes it. Both halves have to be cleared: {@code keyPresses} is what the
 * server is told and what crouching reads, and {@code moveVector} is what the
 * player is actually moved by.
 */
@Mixin(KeyboardInput.class)
public abstract class DuelMovementMixin extends ClientInput
{
    @Inject(method = "tick", at = @At("TAIL"))
    private void dueldimension$standStillDuringDuel(CallbackInfo callback)
    {
        // Either presentation. A duel on the screen already has a screen open,
        // which stops the keys on its own -- but only for as long as it stays
        // open, and this is the same question either way.
        if(DuelSuppression.inDuel())
        {
            keyPresses = Input.EMPTY;
            moveVector = Vec2.ZERO;
        }
    }
}
