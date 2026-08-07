package de.cas_ual_ty.dueldimension.clientutil;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.Iterator;
import java.util.LinkedHashSet;

/**
 * Keeps the most recently used card textures loaded and releases the rest.
 * <p>
 * The cap exists because a card image is a real texture and the database holds
 * ten thousand of them; what it must not do is release one that is still being
 * looked at.
 * <p>
 * It used to add an entry on every bind without checking whether it was already
 * there, and only looked for duplicates once the list was full. Binding the same
 * card every frame — which is what drawing one card does — therefore filled the
 * whole cache with copies of that one texture within a couple of seconds. The
 * cap was still honoured, so the cache was "full" while holding one distinct
 * image, and the next different card evicted a texture that was still in use.
 */
public class LimitedTextureBinder
{
    private final Minecraft mc;
    private final int size;
    /**
     * Insertion-ordered, so the oldest is first and re-binding moves an entry
     * to the back. A set rather than a list: the question asked on every bind
     * is "is this already loaded", and that should not be a scan.
     */
    private final LinkedHashSet<ResourceLocation> list;

    public LimitedTextureBinder(Minecraft mc, int size)
    {
        this.mc = mc;
        this.size = Math.max(1, size);
        this.list = new LinkedHashSet<>();
    }

    public void bind(ResourceLocation rl)
    {
        // Already loaded: move it to the most-recent end and use it. This is
        // the overwhelmingly common case -- the same card, frame after frame --
        // and it used to be the case that grew the cache.
        if(!list.remove(rl))
        {
            // Genuinely new, so it may push the cache over its cap.
            while(list.size() >= size)
            {
                Iterator<ResourceLocation> oldest = list.iterator();
                ResourceLocation evicted = oldest.next();
                oldest.remove();
                unbindTexture(evicted);
            }
        }
        list.add(rl);
        bindTexture(rl);
    }

    /** How many distinct textures are held, for diagnostics. */
    public int loaded()
    {
        return list.size();
    }

    private void bindTexture(ResourceLocation rl)
    {
        RenderSystem.setShaderTexture(0, rl);
    }

    private void unbindTexture(ResourceLocation rl)
    {
        mc.getTextureManager().release(rl);
    }
}
