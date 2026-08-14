package de.cas_ual_ty.dueldimension.mixin.client;

import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reaches the real slot behind one of creative's wrappers.
 * <p>
 * The creative inventory does not show the player's slots directly; it wraps
 * each one in a package-private {@code SlotWrapper}. That wrapper passes the
 * target's CONTAINER up to {@link Slot} but its own INDEX, so asking a wrapper
 * for its container slot answers with creative's layout position rather than
 * the inventory position it stands for -- which is why looking for the off-hand
 * by index finds it in the survival inventory and never in creative.
 * <p>
 * Targeted by name because the class is not public. Same idiom as
 * {@code GuiGraphicsExtractorAccessor}.
 */
@Mixin(targets = "net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen$SlotWrapper")
public interface SlotWrapperAccessor
{
    @Accessor("target")
    Slot dueldimension$target();
}
