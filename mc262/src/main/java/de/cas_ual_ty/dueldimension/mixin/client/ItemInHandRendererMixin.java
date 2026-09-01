package de.cas_ual_ty.dueldimension.mixin.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Puts the duel disk in the first-person off hand while it is the active one.
 *
 * <h2>Why this redirects the source instead of overwriting the cache</h2>
 * {@code ItemInHandRenderer.tick()} reads {@code getOffhandItem()}, compares it
 * against its cached {@code offHandItem} through
 * {@code shouldInstantlyReplaceVisibleItem}, and stores the result -- that
 * comparison is what drives the lower-and-raise swap animation.
 * <p>
 * Writing the disk into the cache after tick would therefore loop forever: every
 * tick would compare the player's real shield against a cached disk, see a
 * change, and play the swap again. Redirecting what tick READS makes both sides
 * of the comparison the disk, so it is stable from the first frame and the
 * animation plays exactly once -- when the player actually presses F.
 * <p>
 * The shield itself is untouched. This changes only which item the renderer is
 * shown, which is the same thing the third-person path does to the render state.
 */
@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin
{
    @Redirect(method = "tick",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;getOffhandItem()"
                + "Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack dueldimension$offHandItem(LocalPlayer player)
    {
        // Not while wearing a character. Its arm has a duel disk modelled onto
        // it, and the item one would be a second disk -- in first person that
        // reads as a vanilla arm holding a disk beside the one you are already
        // wearing. The third-person path skips it for the same reason; see
        // CharacterRendererMixin.
        if(de.cas_ual_ty.dueldimension.clientutil.character.ClientCharacters
            .isWearing(player.getUUID()))
        {
            return player.getOffhandItem();
        }
        ItemStack disk = de.cas_ual_ty.dueldimension.clientutil.ClientWornDisks
            .worn(player.getUUID());
        return disk.isEmpty() ? player.getOffhandItem() : disk;
    }
}
