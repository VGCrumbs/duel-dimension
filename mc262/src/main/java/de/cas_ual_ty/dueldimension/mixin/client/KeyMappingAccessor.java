package de.cas_ual_ty.dueldimension.mixin.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The key a binding is actually bound to.
 * <p>
 * {@code KeyMapping} keeps it protected and offers no getter, and the mod needs
 * it for one thing: reading whether a binding's key is being HELD. A binding
 * only reports presses, and only while no screen is open -- so a hold that
 * opens a screen would never be seen to end. Asking the window about the key
 * itself is the only way round that, and this is how the key is learned.
 * <p>
 * Reading the player's own binding rather than the default, so rebinding the
 * key rebinds the hold with it.
 */
@Mixin(KeyMapping.class)
public interface KeyMappingAccessor
{
    @Accessor("key")
    InputConstants.Key dueldimension$key();
}
