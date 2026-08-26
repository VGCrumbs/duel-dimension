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
 * There is no {@code render} in 26.2 -- screens draw through
 * {@code extractRenderState}, and a container screen's body through
 * {@code extractContents}, which is what the 26.2 copy injects into. 1.21.1 draws
 * immediately and has {@code render}, so that is the target here; see the two
 * injections below, which each note what moved.
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

    /**
     * 26.2 injects into {@code extractContents}; 1.21.1 has no such method and
     * draws from {@code render}.
     * <p>
     * <b>After {@code renderLabels}, and not at TAIL.</b> 26.2's
     * {@code extractContents} is the container's BODY -- the stack a player is
     * dragging is described after it, so the disk slot goes underneath, which is
     * right: a slot painted over a stack in transit looks like the stack went
     * behind the furniture. 1.21.1's {@code render} is the whole thing, and TAIL
     * is past the carried item.
     * <p>
     * {@code renderLabels} is the last call before that carried-item block, and
     * it is unconditional -- read off the bytecode rather than assumed, because
     * the block after it is full of {@code isEmpty} branches and an injection
     * point inside one of those would fire only while something was being
     * dragged. So this is the same position as 26.2's, expressed as the last
     * thing the body does rather than as the end of the method.
     *
     * <h2>And the pose here is NOT the screen's</h2>
     *
     * The thing that made moving off TAIL a bug rather than a nudge.
     * {@code render} does {@code pushPose(); translate(leftPos, topPos, 0)}
     * before it draws a single slot, and does not pop until after the carried
     * item -- offsets 28, 42 and 461 of the compiled method. TAIL was therefore
     * outside that translate and this is inside it, so the same
     * {@code leftPos + slot.x} that was right at the end of the method arrives
     * here already counted once. The disk slot was flung a whole panel's width
     * right and a panel's height down, which at 1634x920 put it off in the empty
     * space beside the inventory.
     * <p>
     * Undone rather than compensated for. Subtracting the offset from the
     * coordinates would leave {@link #dueldimension$slot} answering in one frame
     * for the draw and another for the click, and a hitbox that does not sit
     * under the icon it belongs to is the kind of wrong nobody finds by reading.
     * So the translate is reversed for the duration of the draw and there stays
     * exactly one coordinate system: the screen's.
     */
    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
        at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;"
                + "renderLabels(Lnet/minecraft/client/gui/GuiGraphics;II)V"))
    private void dueldimension$drawDiskSlot(net.minecraft.client.gui.GuiGraphics graphics,
        int mouseX, int mouseY, float partialTick, CallbackInfo callback)
    {
        int[] at = dueldimension$slot();
        if(at == null)
        {
            return;
        }
        // Back into screen space; see the note above. Same numbers the caller
        // translated by, so this lands exactly on the screen's own origin.
        graphics.pose().pushPose();
        graphics.pose().translate(-leftPos, -topPos, 0F);
        DiskSlotOverlay.draw(new GuiGraphicsExtractor(graphics), at[0], at[1],
            mouseX, mouseY);
        graphics.pose().popPose();
    }

    /**
     * 1.21.1 passes the click as three primitives where 26.2 passes an event
     * record. The double-click flag 26.2's second argument carries has no
     * counterpart here and is not needed: this slot answers a single click.
     */
    @Inject(method = "mouseClicked(DDI)Z", at = @At("HEAD"), cancellable = true)
    private void dueldimension$clickDiskSlot(double mouseX, double mouseY, int button,
        CallbackInfoReturnable<Boolean> callback)
    {
        int[] at = dueldimension$slot();
        if(at != null && DiskSlotOverlay.click(at[0], at[1], mouseX, mouseY))
        {
            // Consumed at HEAD so the menu never sees it: the slot sits outside
            // the menu's own grid, and a click there would otherwise register as
            // a click on nothing and drop a carried stack.
            callback.setReturnValue(true);
        }
    }
}
