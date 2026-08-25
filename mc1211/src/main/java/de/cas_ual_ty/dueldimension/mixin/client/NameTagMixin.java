package de.cas_ual_ty.dueldimension.mixin.client;

import de.cas_ual_ty.dueldimension.clientutil.DuelSuppression;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * No floating names over a duel.
 * <p>
 * A duel board is a thing you read: five zones a side, cards laid flat, and a
 * monster standing on each of them. A player's name hangs in the air at head
 * height and renders through everything, so during a duel it sits squarely over
 * the half of the board its owner is standing behind — which is the half you
 * are trying to look at.
 * <p>
 * Only players, and only while a duel is running. Everything else keeps its
 * name, and so do players the moment the duel ends.
 * <p>
 * <b>Cleared at TAIL rather than cancelled at HEAD.</b> Render states are pooled
 * and reused between frames, so a state that carried a name last frame still
 * carries it now — cancelling the extraction would leave that stale name in
 * place and draw exactly what this is meant to remove. Letting it be written and
 * then clearing it is the version that does not depend on what the pool happens
 * to hand back.
 */
@Mixin(EntityRenderer.class)
public class NameTagMixin
{
    /**
     * The FINAL five-argument form, not the three-argument one.
     * <p>
     * That distinction is the whole of why this did not work at first.
     * {@code LivingEntityRenderer} OVERRIDES the three-argument method — it
     * reads the entity's scale attributes and then calls this one directly —
     * and never calls its super. So an injection into the three-argument version
     * ran for nothing that is alive, which is every player. Verified in the
     * bytecode rather than assumed: the override's last call is an
     * {@code invokespecial} straight to this signature.
     * <p>
     * This one is {@code final}, so no renderer can route around it. Both paths
     * arrive here.
     */
    @Inject(method = "extractNameTags(Lnet/minecraft/world/entity/Entity;"
        + "Lnet/minecraft/client/renderer/entity/state/EntityRenderState;FDD)V",
        at = @At("TAIL"))
    private void dueldimension$hideDuringDuel(Entity entity, EntityRenderState state,
        float partialTick, double near, double far, CallbackInfo callback)
    {
        if(entity instanceof Player && DuelSuppression.inDuel())
        {
            state.nameTag = null;
            // The score line rides along with the name and is drawn from the
            // same anchor, so leaving it would put a stray number where the
            // name was.
            state.scoreText = null;
        }
    }
}
