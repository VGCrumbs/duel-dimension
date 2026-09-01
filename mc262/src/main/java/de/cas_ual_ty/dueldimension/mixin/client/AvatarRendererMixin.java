package de.cas_ual_ty.dueldimension.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
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
 * Puts the worn duel disk onto a player's render state.
 * <p>
 * It carried their outfit here too, and put a matching sleeve on the hand you
 * see in first person. Outfits are shelved; the disk is not, and it is drawn
 * from the same hook for the same reason.
 */
@Mixin(AvatarRenderer.class)
public class AvatarRendererMixin
{
    /**
     * Looks the disk up while the entity is still in hand.
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
    private void dueldimension$carryWorn(Entity entity, EntityRenderState state,
        float partialTick, CallbackInfo callback)
    {
        if(entity instanceof AbstractClientPlayer player
            && state instanceof net.minecraft.client.renderer.entity.state.AvatarRenderState avatar)
        {
            // WRITTEN EVERY TIME, including when there is none. The renderer
            // reuses one state per entity, so leaving the field alone would
            // leave the last character on the next player who has none.
            de.cas_ual_ty.dueldimension.character.CharacterLook look =
                de.cas_ual_ty.dueldimension.clientutil.character.ClientCharacters
                    .look(player.getUUID());
            if(avatar instanceof de.cas_ual_ty.dueldimension.clientutil.character
                .CharacterCarrier carrier)
            {
                carrier.dueldimension$setCharacter(look);
                // Written every time, for the same reason: one state per
                // entity, reused.
                carrier.dueldimension$setSprinting(player.isSprinting());
                // Where they MOVED against where they are POINTED; see
                // CharacterRenderer for why the walk cycle needs it.
                double dx = player.getX() - player.xOld;
                double dz = player.getZ() - player.zOld;
                double radians = Math.toRadians(avatar.bodyRot);
                boolean moved = dx * dx + dz * dz >= 1.0E-6D;
                carrier.dueldimension$setMoving(moved);
                carrier.dueldimension$setRiding(player.isPassenger());
                // Slippery ground AND coasting across it rather than walking
                // on it; the renderer owns both halves. Asked here because both
                // are questions about the player, who is about to go away.
                carrier.dueldimension$setCoasting(de.cas_ual_ty.dueldimension
                    .clientutil.character.CharacterRenderer.carried(player));
                // Only a walk cycle has a direction to get wrong. Reversing
                // the seated clip because the horse backed up would run the
                // rider's idle sway backwards.
                carrier.dueldimension$setBackwards(moved && !player.isPassenger()
                    && dx * -Math.sin(radians) + dz * Math.cos(radians) < 0D);
                carrier.dueldimension$setSwing(player.swinging
                    ? (player.swingTime + partialTick) / 20F : -1F);
                carrier.dueldimension$setEye(net.minecraft.client.Minecraft.getInstance()
                    .gameRenderer.mainCamera().position());

            }
            // A character carries its own duel disk, modelled on its arm. The
            // item one would be a second disk floating beside it.
            if(look == null)
            {
                dueldimension$wearDisk(player, avatar);
            }
        }
    }

    /**
     * Draws the duel disk in the off hand while it is the active one.
     * <p>
     * The disk is not an item in that slot -- the player's shield is still
     * sitting there untouched, and comes straight back when the disk is turned
     * off. What changes is only which of the two the render state carries, so
     * "switch which item is active" is exactly what this does and nothing is
     * ever swapped, consumed or lost.
     * <p>
     * The off hand is the arm that is not the main one, so this follows a
     * left-handed player without a second case.
     */
    @org.spongepowered.asm.mixin.Unique
    private void dueldimension$wearDisk(AbstractClientPlayer player,
        net.minecraft.client.renderer.entity.state.AvatarRenderState state)
    {
        net.minecraft.world.item.ItemStack disk =
            de.cas_ual_ty.dueldimension.clientutil.DiskSlotOverlay.wornDiskFor(player);
        if(disk.isEmpty())
        {
            return;
        }
        // The resolver owns "turn this stack into something drawable"; the
        // render state only carries the result.
        net.minecraft.client.renderer.item.ItemModelResolver resolver =
            net.minecraft.client.Minecraft.getInstance().getItemModelResolver();
        boolean offHandIsLeft = state.mainArm == net.minecraft.world.entity.HumanoidArm.RIGHT;
        if(offHandIsLeft)
        {
            state.leftHandItemStack = disk;
            resolver.updateForLiving(state.leftHandItemState, disk,
                net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_LEFT_HAND, player);
        }
        else
        {
            state.rightHandItemStack = disk;
            resolver.updateForLiving(state.rightHandItemState, disk,
                net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, player);
        }
    }

}
