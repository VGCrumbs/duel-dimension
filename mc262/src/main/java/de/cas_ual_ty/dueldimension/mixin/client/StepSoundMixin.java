package de.cas_ual_ty.dueldimension.mixin.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Silences the distance-counted footstep for a duellist, so the retimed one can
 * take its place.
 * <p>
 * {@code Entity.move} fires this every time travelled distance passes
 * {@code nextStep}: the average rate is right and each individual step is
 * wherever the counter landed. {@link
 * de.cas_ual_ty.dueldimension.clientutil.character.StepSounds} plays one when
 * the foot actually lands instead, and both together would be two sets of
 * footsteps.
 *
 * <h2>Client side, and only where a character is drawn</h2>
 * Gated on {@code level.isClientSide} because this is the one path a client
 * runs for its own player; the server's copy still broadcasts to everyone else,
 * which is what keeps remote duellists audible at all. And gated on the player
 * actually wearing a character, so a duellist who has not chosen one -- or
 * anybody else on the server -- keeps vanilla's footsteps exactly.
 *
 * <h2>{@code Player}, not {@code Entity}</h2>
 * Both declare {@code playStepSound} and {@code Player} overrides it without
 * calling {@code super}, so an injection into {@code Entity} compiles, loads,
 * matches a real method and never fires for a player. {@code MixinTargetsTest}
 * caught exactly that and named the fix; it is the sort of thing that would
 * otherwise present as "the retimed footsteps play but the old ones did not
 * stop".
 *
 * <h2>Not {@code playMuffledStepSound}</h2>
 * That one is for a body inside a block, where the sound is a hint that you are
 * somewhere you should not be rather than a footfall. It is left alone: nothing
 * here retimes it, so cancelling it would remove a sound and put nothing back.
 */
@Mixin(Player.class)
public class StepSoundMixin
{
    @Inject(method = "playStepSound(Lnet/minecraft/core/BlockPos;"
        + "Lnet/minecraft/world/level/block/state/BlockState;)V",
        at = @At("HEAD"), cancellable = true)
    private void dueldimension$retimedStep(BlockPos at, BlockState state,
        CallbackInfo callback)
    {
        Player player = (Player)(Object)this;
        if(!player.level().isClientSide())
        {
            return;
        }
        if(de.cas_ual_ty.dueldimension.clientutil.character.ClientCharacters
            .isWearing(player.getUUID()))
        {
            callback.cancel();
        }
    }
}
