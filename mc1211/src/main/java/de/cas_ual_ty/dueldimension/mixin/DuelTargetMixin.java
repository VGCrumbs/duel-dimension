package de.cas_ual_ty.dueldimension.mixin;

import de.cas_ual_ty.dueldimension.duel.npc.DuelistDuels;
import de.cas_ual_ty.dueldimension.duel.overworld.OverworldDuels;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A duellist is not something to hunt.
 *
 * <h2>{@code setTarget}, not the goals</h2>
 * Every way a mob comes to want somebody dead ends here. {@code
 * NearestAttackableTargetGoal} calls it, {@code HurtByTargetGoal} calls it,
 * {@code Creeper} and {@code Zombie} and every bespoke boss call it, and so does
 * any other mod's goal. Refusing at the setter is therefore one rule that holds
 * for all of them, where blocking the vanilla goals individually would hold for
 * the ones that shipped with the game and miss the rest.
 * <p>
 * The refusal is narrow on purpose: only when the entity being TAKEN as a target
 * is a duelling player. A mob may still target anything else, including a
 * player standing next to the board watching, and a duellist's own attackers
 * are only dropped for as long as the duel lasts.
 *
 * <h2>What this cannot do, and who does it</h2>
 * Nothing sets a target it already holds, so a mob already hunting somebody when
 * they accept a challenge is not touched by anything here. {@code
 * OverworldDuels.keepThePeace} clears those once, as the board opens; between
 * the two, a duellist is left alone from the first tick of the duel to the last.
 *
 * <h2>Server only</h2>
 * Targeting is the server's decision and a client's copy of a mob is told the
 * result, so this is gated on the target being a {@link ServerPlayer}. That also
 * makes the predicates safe: {@code OverworldDuels} and {@code DuelistDuels} are
 * both server-side registries and neither has anything to say about a client's
 * view of a duel.
 */
@Mixin(Mob.class)
public class DuelTargetMixin
{
    @Inject(method = "setTarget(Lnet/minecraft/world/entity/LivingEntity;)V",
        at = @At("HEAD"), cancellable = true)
    private void dueldimension$duellistsAreNotPrey(LivingEntity target, CallbackInfo callback)
    {
        if(target instanceof ServerPlayer player
            && (OverworldDuels.isEngaged(player.getUUID())
                || DuelistDuels.isSeated(player.getUUID())))
        {
            callback.cancel();
        }
    }
}
