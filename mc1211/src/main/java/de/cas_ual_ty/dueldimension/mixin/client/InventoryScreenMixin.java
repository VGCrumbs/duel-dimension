package de.cas_ual_ty.dueldimension.mixin.client;

import de.cas_ual_ty.dueldimension.clientutil.DiskSlotOverlay;
import de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Puts the duel disk slot in the inventory, immediately left of the shield.
 *
 * <h2>Why this targets AbstractContainerScreen and not InventoryScreen</h2>
 * Mixin resolves {@code @Shadow} and {@code @Inject} against the TARGET class
 * only -- it does not walk up the hierarchy. {@code InventoryScreen} declares
 * neither {@code leftPos}/{@code topPos} nor any render method; both live on
 * {@code AbstractContainerScreen}. Targeting the subclass therefore fails at
 * class-transform time, which crashes the game before the window opens. Two
 * separate crashes were spent learning that, so: <b>verify with javap that the
 * target class actually declares the member, because javac cannot.</b>
 * <p>
 * There is also no {@code render} in 26.2 -- screens draw through
 * {@code extractRenderState}, and a container screen's body through
 * {@code extractContents}, which is what this injects into.
 * <p>
 * The wider target means every container screen runs this, so it checks it is
 * really the inventory before drawing anything.
 * <p>
 * The slot is drawn, not added: the disk is worn equipment held on the player's
 * profile rather than an item in a container, so there is nothing for a real
 * {@code Slot} to hold. Clicking it sends the same message the F key sends.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class InventoryScreenMixin
{
    /** Declared on this target, so shadowing them is legal here. */
    @Shadow
    protected int leftPos;
    @Shadow
    protected int topPos;

    /**
     * Finds the shield slot by asking what it IS, not where it sits.
     * <p>
     * The survival inventory and the creative one are different menus and lay
     * their slots out differently, so an index into the slot list is only ever
     * right for one of them. A slot backed by the player's own Inventory at
     * {@link net.minecraft.world.entity.player.Inventory#SLOT_OFFHAND} is the
     * shield in both, and in anything else that shows one.
     */
    @Unique
    private Slot dueldimension$shield()
    {
        AbstractContainerScreen<?> self = (AbstractContainerScreen<?>)(Object)this;
        for(Slot slot : self.getMenu().slots)
        {
            // Creative shows WRAPPERS around the player's slots, and a wrapper
            // reports its own layout index rather than the inventory position
            // it stands for. So the question is asked of whatever is behind it,
            // while the wrapper itself is what gets returned -- the wrapper is
            // the thing with the on-screen coordinates.
            Slot real = slot instanceof SlotWrapperAccessor wrapper
                ? wrapper.dueldimension$target() : slot;
            if(real != null
                && real.container instanceof net.minecraft.world.entity.player.Inventory
                && real.getContainerSlot()
                    == net.minecraft.world.entity.player.Inventory.SLOT_OFFHAND)
            {
                return slot;
            }
        }
        return null;
    }

    /**
     * Screen-space position of the disk slot, or null when it does not apply.
     * <p>
     * ABOVE the shield in the survival inventory, where there is room and the
     * two read as one column of worn equipment. The creative inventory has the
     * recipe-book tabs sitting in that space, so there it goes to the LEFT.
     */
    @Unique
    private int[] dueldimension$slot()
    {
        boolean survival = (Object)this instanceof InventoryScreen;
        boolean creative = (Object)this
            instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
        if(!survival && !creative)
        {
            return null; // some other container; not our business
        }
        Slot shield = dueldimension$shield();
        if(shield == null)
        {
            return null; // creative's other tabs show no player inventory at all
        }
        return survival
            ? new int[] {leftPos + shield.x, topPos + shield.y - DiskSlotOverlay.STEP}
            : new int[] {leftPos + shield.x - DiskSlotOverlay.STEP, topPos + shield.y};
    }

    @Inject(method = "extractContents", at = @At("TAIL"))
    private void dueldimension$drawDiskSlot(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
        float partialTick, CallbackInfo callback)
    {
        int[] at = dueldimension$slot();
        if(at != null)
        {
            DiskSlotOverlay.draw(graphics, at[0], at[1], mouseX, mouseY);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void dueldimension$clickDiskSlot(de.cas_ual_ty.dueldimension.compat.InputEvents.MouseButtonEvent event,
        boolean doubled, CallbackInfoReturnable<Boolean> callback)
    {
        int[] at = dueldimension$slot();
        if(at != null && DiskSlotOverlay.click(at[0], at[1], event.x(), event.y()))
        {
            // Consumed at HEAD so the menu never sees it: the slot sits outside
            // the menu's own grid, and a click there would otherwise register as
            // a click on nothing and drop a carried stack.
            callback.setReturnValue(true);
        }
    }
}
