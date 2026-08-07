package de.cas_ual_ty.dueldimension.clientutil;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

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
    private final LinkedHashSet<Identifier> list;

    public LimitedTextureBinder(Minecraft mc, int size)
    {
        this.mc = mc;
        this.size = Math.max(1, size);
        this.list = new LinkedHashSet<>();
    }

    public void bind(Identifier rl)
    {
        // Already loaded: move it to the most-recent end and use it. This is
        // the overwhelmingly common case -- the same card, frame after frame --
        // and it used to be the case that grew the cache.
        if(!list.remove(rl))
        {
            // Genuinely new, so it may push the cache over its cap.
            while(list.size() >= size)
            {
                Iterator<Identifier> oldest = list.iterator();
                Identifier evicted = oldest.next();
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

    /**
     * Registering a texture is all that is left of "binding" one.
     * <p>
     * This class exists to cap how many card images are resident at once, and
     * that job is unchanged. What has gone is the binding: a draw hands an
     * {@link Identifier} to the renderer now and the renderer binds it, so
     * there is nothing for a caller to bind and this became a no-op rather
     * than a translation.
     */
    private void bindTexture(Identifier rl)
    {
    }

    private void unbindTexture(Identifier rl)
    {
        mc.getTextureManager().release(rl);
    }
}
