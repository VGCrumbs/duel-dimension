package de.cas_ual_ty.dueldimension.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import de.cas_ual_ty.dueldimension.clientutil.OutfitCarrier;
import de.cas_ual_ty.dueldimension.clientutil.OutfitHand;
import de.cas_ual_ty.dueldimension.clientutil.OutfitSkins;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Carries a player's outfit onto their render state, and puts a sleeve on the
 * hand you see in first person.
 */
@Mixin(AvatarRenderer.class)
public class AvatarRendererMixin
{
    /**
     * Looks up the outfit while the entity is still in hand.
     * <p>
     * This is the one moment where both halves are available: the renderer has
     * the player, and the state is about to be handed to layers that will not.
     * Doing it here rather than in the layer is what the render-state refactor
     * asks for, and it also means the lookup happens once per frame per player
     * rather than once per layer.
     */
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Entity;"
        + "Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V",
        at = @At("TAIL"))
    private void dueldimension$carryOutfit(Entity entity, EntityRenderState state,
        float partialTick, CallbackInfo callback)
    {
        if(state instanceof OutfitCarrier carrier && entity instanceof AbstractClientPlayer player)
        {
            carrier.dueldimension$setOutfit(OutfitSkins.worn(player));
        }
    }

    /**
     * First person does not go through the layer at all: it calls
     * {@code renderRightHand}, which draws one arm of the base model directly
     * and runs nothing else. So a duelist wearing an outfit saw everyone else's
     * sleeves and their own bare wrist.
     * <p>
     * The two arms are separate methods rather than one with a flag, so which
     * arm it is comes free.
     */
    @Inject(method = "renderRightHand", at = @At("TAIL"))
    private void dueldimension$rightSleeve(PoseStack poseStack, SubmitNodeCollector collector,
        int light, Identifier texture, boolean sleeve, CallbackInfo callback)
    {
        OutfitHand.draw(poseStack, collector, light, true, sleeve);
    }

    @Inject(method = "renderLeftHand", at = @At("TAIL"))
    private void dueldimension$leftSleeve(PoseStack poseStack, SubmitNodeCollector collector,
        int light, Identifier texture, boolean sleeve, CallbackInfo callback)
    {
        OutfitHand.draw(poseStack, collector, light, false, sleeve);
    }
}
