package de.cas_ual_ty.dueldimension.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import de.cas_ual_ty.dueldimension.character.CharacterLook;
import de.cas_ual_ty.dueldimension.clientutil.character.CharacterCarrier;
import de.cas_ual_ty.dueldimension.clientutil.character.CharacterRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws a duellist as their created character instead of as a player.
 * <p>
 * <b>The whole submit is replaced, not the model inside it.</b> Cancelling here
 * takes the vanilla body, the arms, the head, the hat layer, the cape AND every
 * armour layer out in one act — because all of them are drawn by the layers this
 * method runs, and there is no partial version of "this is not a Minecraft
 * player". Swapping only the model would leave a helmet floating where a head
 * used to be.
 * <p>
 * That is also what takes out other model replacers. Customisable Player Models
 * and its like work by editing the vanilla model or its parts during this same
 * call; with the call cancelled there is nothing for them to edit. It is the one
 * lever that turns all of them off at once, and it does not require knowing
 * which of them is installed.
 * <p>
 * <b>The name tag is deliberately left alone.</b> It is submitted separately, and
 * a player you cannot identify is worse than one drawn unusually — this changes
 * what somebody looks like, not whether you can tell who they are.
 *
 * <b>The target is {@code LivingEntityRenderer}, not {@code AvatarRenderer}.</b>
 * The player renderer does not declare {@code submit} — it inherits it — and a
 * mixin into a method a class does not declare fails at LOAD, with nothing said
 * by the compiler. So it goes on the class that declares it and is narrowed by
 * the state check below, which costs one {@code instanceof} on entities that are
 * not players.
 *
 * @see CharacterRenderer for the drawing itself
 */
@Mixin(LivingEntityRenderer.class)
public class CharacterRendererMixin
{
    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;"
        + "Lcom/mojang/blaze3d/vertex/PoseStack;"
        + "Lnet/minecraft/client/renderer/SubmitNodeCollector;"
        + "Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at = @At("HEAD"), cancellable = true)
    private void dueldimension$drawCharacter(LivingEntityRenderState state, PoseStack poseStack,
        SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo callback)
    {
        if(!(state instanceof AvatarRenderState avatar)
            || !(state instanceof CharacterCarrier carrier))
        {
            return;
        }
        CharacterLook look = carrier.dueldimension$character();
        if(look == null)
        {
            return;
        }
        // No CpmSettings.renderCancelled here, unlike 1.21.1, and it is not an
        // omission. Customizable Player Models balances a bind against an
        // unbind across PlayerRenderer.render -- a method 26.2 does not have,
        // hooked by a mod that has no 26.2 build. There is nothing on this
        // version to leave stranded. The SETTING still exists in both trees, so
        // the switch is already there if that changes.
        //
        // Cancelled whether or not the model could be drawn. A character that
        // fails to load must not fall back to the vanilla body: the duellist
        // would flicker between two people as the model came and went, and a
        // missing character is a thing to fix rather than to paper over.
        callback.cancel();
        CharacterRenderer.submit(avatar, poseStack, collector, look);
    }
}
