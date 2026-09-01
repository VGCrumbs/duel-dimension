package de.cas_ual_ty.dueldimension.mixin;

import de.cas_ual_ty.dueldimension.duel.overworld.OverworldDuels;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A duel field is ground mobs will not walk onto.
 *
 * <h2>Why this is a path type and not a wall</h2>
 * Telling the pathfinder the ground is {@link PathType#BLOCKED} makes a mob
 * route AROUND the board the way it routes around lava -- it never sets off
 * across it, so there is nothing to push back out and no shoving match at the
 * edge. A collision box or a per-tick eviction would produce a mob pressed up
 * against an invisible wall trying to walk through it, which looks like a bug
 * even when it works.
 * <p>
 * {@code WalkNodeEvaluator} is where WALKING is decided, which is the word the
 * feature was asked for. Fliers and swimmers use their own evaluators and are
 * deliberately not covered: a bat over the board disturbs nothing, and a duel
 * cannot be sited in water.
 *
 * <h2>The cost, which is why the check is shaped as it is</h2>
 * This runs per node, per path, per mob -- easily thousands of calls a second on
 * a busy server, nearly all of them nowhere near a duel. So the first thing
 * asked is {@code anyBoards()}, a single volatile read that is false whenever
 * nobody is duelling, and the node's coordinates are passed as loose ints so
 * that the common case allocates nothing at all.
 *
 * <h2>Injected at RETURN</h2>
 * Rather than HEAD, so vanilla's answer is computed first and only then
 * overridden. At the head this would have to decide what a node it knows nothing
 * about should be, and would be wrong for the one inside a wall at the field's
 * edge; here the only claim made is the narrow one -- whatever this node was, if
 * it is inside a duel it is closed.
 */
@Mixin(WalkNodeEvaluator.class)
public class DuelPathMixin
{
    @Inject(method = "getPathType(Lnet/minecraft/world/level/pathfinder/PathfindingContext;III)"
        + "Lnet/minecraft/world/level/pathfinder/PathType;",
        at = @At("RETURN"), cancellable = true)
    private void dueldimension$duelFieldsAreClosed(PathfindingContext context, int x, int y, int z,
        CallbackInfoReturnable<PathType> callback)
    {
        if(!OverworldDuels.anyBoards())
        {
            return;
        }
        // PathfindingContext carries a CollisionGetter, which is all pathing
        // needs and is not enough to name a dimension. Every server-side one is
        // a Level; anything else is a scratch view a duel cannot be sited in.
        if(context.level() instanceof Level level
            && OverworldDuels.keepOut(level.dimension(), x, y, z))
        {
            callback.setReturnValue(PathType.BLOCKED);
        }
    }
}
