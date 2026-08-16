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
            dueldimension$wearDisk(player, avatar);
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
