package de.cas_ual_ty.dueldimension.clientutil.model;

import com.mojang.blaze3d.platform.NativeImage;
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
    /**
     * The {@code label} is accepted and dropped. 26.2's {@link DynamicTexture}
     * takes a debug name for its GPU object; 1.21.1's has nowhere to put one, and
     * inventing a field would only mean carrying a string nothing reads. Kept in
     * the signature so {@code ModelMesh}'s call site is the same text on both
     * versions.
     */
    Ps2Texture(Supplier<String> label, NativeImage image)
    {
        super(image);
        // Set after construction rather than passed in, on both versions and for
        // the same reason: the superclass decides filtering while it uploads and
        // offers no say in it. 26.2 swaps the sampler afterwards; here the same
        // override is a texture parameter.
        //
        // blur = bilinear, mipmap = false. The pair is exactly the class note:
        // smoothed, and no mip chain. Safe to call now because the superclass
        // constructor has already uploaded -- an upload sets these parameters
        // itself, so doing this first would be overwritten a line later.
        setFilter(true, false);
    }
}
