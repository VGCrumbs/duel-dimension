package de.cas_ual_ty.dueldimension.clientutil.model;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.renderer.texture.DynamicTexture;

import java.util.function.Supplier;

/**
 * A model's texture, filtered the way the console that drew it filtered.
 * <p>
 * <b>Bilinear, and that is the whole of it.</b> Minecraft's
 * {@link DynamicTexture} hard-codes {@code FilterMode.NEAREST} when it builds
 * its sampler, which is exactly right for the game it belongs to — block and
 * item art is pixel art, and smoothing it would be vandalism. It is exactly
 * wrong for these models. The PlayStation 2's Graphics Synthesizer filtered
 * textures bilinearly, and the art was drawn knowing that: 64- and 128-pixel
 * skins stretched over a whole dragon, which read as smooth on the console and
 * as a mosaic of hard squares when point-sampled at a Minecraft monster's size.
 * <p>
 * Bilinear and nothing else — no mipmaps and no anisotropy — because the
 * hardware had neither. A mip chain would fade these textures towards grey with
 * distance in a way the originals never did.
 * <p>
 * Only the models use this. The sprite sheets stay point-sampled, since those
 * really are pixel art.
 */
final class Ps2Texture extends DynamicTexture
{
    Ps2Texture(Supplier<String> label, NativeImage image)
    {
        super(label, image);
        // Replaced after construction rather than passed in: the superclass
        // builds its own sampler in a private method and offers no say in it.
        // The cache hands back a shared object per combination, so every model
        // texture in the game shares this one.
        //
        // Read HERE and not per draw, for the same reason: there is nowhere
        // later to say it. That is why turning the setting off has to forget
        // the baked models -- see HologramSettings.setPs2.
        sampler = RenderSystem.getSamplerCache().getRepeat(
            de.cas_ual_ty.dueldimension.clientutil.HologramSettings.ps2()
                ? FilterMode.LINEAR
                : FilterMode.NEAREST);
    }
}
