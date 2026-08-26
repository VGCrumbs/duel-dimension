package de.cas_ual_ty.dueldimension.mixin.client;

import de.cas_ual_ty.dueldimension.clientutil.DiskSlotOverlay;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Draws the worn duel disk in the off hand.
 *
 * <h2>Why this is not the 26.2 mixin</h2>
 *
 * On 26.2 this is {@code AvatarRendererMixin}: it writes the disk onto a
 * player's RENDER STATE at extraction time, and a layer draws it from there.
 * Neither half of that exists here. 1.21.1 has no {@code AvatarRenderer} at all
 * — {@code net/minecraft/client/renderer/entity/player/} holds only a
 * package-info — and {@code PlayerRenderer} has no {@code extractRenderState} to
 * inject into, because 1.21.1 renders entities straight from the entity.
 * <p>
 * So the mechanism changes rather than the spelling. Instead of writing the disk
 * onto a state for the layer to find, this changes what the layer READS while it
 * is drawing.
 *
 * <h2>Why the off hand, and why one redirect covers both arms</h2>
 *
 * 1.21.1's {@code ItemInHandLayer.render} decides handedness with
 * {@code entity.getMainArm() == HumanoidArm.RIGHT} and then picks the two
 * stacks from it — so {@code getOffhandItem()} is called at two sites, exactly
 * one of which runs per frame. Redirecting it therefore replaces whichever arm
 * is the off one, which is precisely what the 26.2 version says it does:
 * <blockquote>The off hand is the arm that is not the main one, so this follows
 * a left-handed player without a second case.</blockquote>
 * The layer picks {@code THIRD_PERSON_LEFT_HAND} or {@code RIGHT} itself, so
 * nothing here has to know which it chose.
 *
 * <h2>What is not happening</h2>
 *
 * Nothing is swapped, consumed or lost. The player's shield is still in the real
 * off-hand slot and comes straight back when the disk is turned off; only what
 * the layer reads changes, for the duration of one draw.
 * <p>
 * The {@code instanceof} guard is load-bearing rather than defensive: this layer
 * runs for every {@code LivingEntity}, so without it every skeleton and zombie
 * would be asked whether it is wearing a duel disk.
 */
@Mixin(ItemInHandLayer.class)
public class ItemInHandLayerMixin
{
    /**
     * {@code expect = 2} because both call sites are redirected and only one
     * runs. Left at the default, Mixin would warn about the second as though it
     * had failed to apply.
     */
    @Redirect(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;"
        + "Lnet/minecraft/client/renderer/MultiBufferSource;I"
        + "Lnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;"
                + "getOffhandItem()Lnet/minecraft/world/item/ItemStack;"),
        expect = 2)
    private ItemStack dueldimension$wearDisk(LivingEntity entity)
    {
        ItemStack offhand = entity.getOffhandItem();
        if(!(entity instanceof AbstractClientPlayer player))
        {
            return offhand;
        }
        ItemStack disk = DiskSlotOverlay.wornDiskFor(player);
        return disk.isEmpty() ? offhand : disk;
    }
}
