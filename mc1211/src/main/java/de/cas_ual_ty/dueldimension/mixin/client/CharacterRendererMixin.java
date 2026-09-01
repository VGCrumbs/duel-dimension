package de.cas_ual_ty.dueldimension.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import de.cas_ual_ty.dueldimension.character.CharacterLook;
import de.cas_ual_ty.dueldimension.clientutil.character.CharacterRenderer;
import de.cas_ual_ty.dueldimension.clientutil.character.ClientCharacters;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws a duellist as their created character instead of as a player.
 * <p>
 * <b>The whole render is replaced, not the model inside it.</b> Cancelling here
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
 * <b>The name tag is deliberately left alone.</b> It is drawn by
 * {@code renderNameTag}, which this does not touch — a player you cannot
 * identify is worse than one drawn unusually. This changes what somebody looks
 * like, not whether you can tell who they are.
 * <p>
 * <b>Simpler than the 26.2 version, and the difference is instructive.</b> There
 * the target has to be {@code LivingEntityRenderer}, because {@code AvatarRenderer}
 * inherits {@code submit} rather than declaring it and a mixin into an inherited
 * method fails at LOAD. Here {@code PlayerRenderer} declares {@code render}
 * itself, and it is handed the player — so there is no narrowing
 * {@code instanceof}, no render state and no carrier interface.
 *
 * @see CharacterRenderer for the drawing itself
 */
@Mixin(PlayerRenderer.class)
public class CharacterRendererMixin
{
    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FF"
        + "Lcom/mojang/blaze3d/vertex/PoseStack;"
        + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At("HEAD"), cancellable = true)
    private void dueldimension$drawCharacter(AbstractClientPlayer player, float viewYaw,
        float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight,
        CallbackInfo callback)
    {
        CharacterLook look = ClientCharacters.look(player.getUUID());
        if(look == null)
        {
            return;
        }
        // Any other mod that set something up at the head of this method is
        // about to lose the return that would have torn it down. Told before
        // the cancel, so the teardown lands where the method's own would have.
        // See CpmSettings: the claim in this class's javadoc that cancelling is
        // "the one lever that turns all of them off at once" is true of the
        // DRAWING and was never true of the bookkeeping.
        de.cas_ual_ty.dueldimension.clientutil.CpmSettings.renderCancelled(buffers,
            ((net.minecraft.client.renderer.entity.player.PlayerRenderer)(Object)this)
                .getModel());
        // Cancelled whether or not the model could be drawn. A character that
        // fails to load must not fall back to the vanilla body: the duellist
        // would flicker between two people as the model came and went, and a
        // missing character is a thing to fix rather than to paper over.
        callback.cancel();
        // NOT the yaw this method was handed. Despite the parameter's usual
        // name, EntityRenderDispatcher passes `getViewYRot` -- where the player
        // is LOOKING -- and vanilla's own LivingEntityRenderer.render ignores it
        // for the body, computing yBodyRot itself a few lines in. Using it turns
        // the whole model with every twitch of the mouse, where a Minecraft body
        // stands still until it has a reason to come round.
        CharacterRenderer.submit(player, poseStack,
            new de.cas_ual_ty.dueldimension.compat.SubmitNodeCollector(buffers), look,
            net.minecraft.util.Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot),
            partialTick, packedLight);
    }
}
