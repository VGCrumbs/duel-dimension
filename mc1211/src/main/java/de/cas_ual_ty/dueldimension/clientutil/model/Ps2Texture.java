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
        //
        // Read HERE and not per draw, because there is nowhere later to say
        // it -- which is why turning the setting off has to forget the baked
        // models. See HologramSettings.setPs2.
        setFilter(de.cas_ual_ty.dueldimension.clientutil.HologramSettings.ps2(), false);
    }

    /**
     * The render type asks for point sampling every frame. It does not get it.
     * <p>
     * <b>Setting the filter in the constructor is not enough on this version,
     * and that is not obvious.</b> {@code RenderStateShard.TextureStateShard}
     * calls {@code setFilter(blur, mipmap)} on the bound texture during
     * {@code setupRenderState} -- every draw, not once -- and every render type
     * a model goes through passes {@code false}: {@code entityCutout} and
     * {@code entityTranslucentCull} hard-code it, and {@code hologram()} built
     * its shard the same way. So the constructor set LINEAR and the very next
     * frame set it back, which is exactly what was seen: models point-sampled
     * into hard blocks no matter what the setting said.
     * <p>
     * Overriding here rather than building three custom render types, which was
     * the other way. Those would each be a copy of a vanilla recipe kept in step
     * by hand, and custom types are also the ones Iris has to be told about --
     * see {@code IrisCompat}. This is one method, and it puts the decision on
     * the texture, which is the thing the decision is actually about.
     * <p>
     * {@code mipmap} is passed through rather than forced: nothing asks these
     * for mipmaps, and if something ever does, refusing it here would be a
     * second surprise of exactly this kind.
     */
    @Override
    public void setFilter(boolean blur, boolean mipmap)
    {
        super.setFilter(de.cas_ual_ty.dueldimension.clientutil.HologramSettings.ps2(), mipmap);
    }
}
