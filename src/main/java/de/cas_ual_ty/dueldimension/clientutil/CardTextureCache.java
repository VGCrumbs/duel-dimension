package de.cas_ual_ty.dueldimension.clientutil;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Releases card textures that have not been looked at lately.
 * <p>
 * <b>Nothing was releasing them at all.</b> {@code TextureManager} keeps a plain
 * map with no budget and no eviction: {@code tick()} only visits tickable
 * textures and a reload only rebuilds {@code ReloadableTexture}s. The mod has a
 * {@link LimitedTextureBinder} that does release, but only
 * {@code CardRenderUtil.bind*ResourceLocation} feeds it, and
 * {@link DuelTextures#card} does not — it builds an Identifier and hands it
 * straight to the renderer. So every distinct card ever drawn stayed on the GPU
 * for the whole session.
 * <p>
 * That was survivable while a card was only loaded when it was actually on
 * screen. It stopped being survivable when the deck editor began warming a page
 * either side of the view plus a full page of 512px previews: at 64KB for a
 * 128px icon and 1MB for a 512px preview, browsing the collection climbs
 * steadily and never comes back down.
 * <p>
 * <b>Releasing is cheap here, which is what makes this safe.</b> A released
 * Identifier is rebuilt on next use straight from the resource pack, and the
 * scaled PNG behind it is still in {@code ImageHandler}'s byte cache and, for
 * icons, on disk. Evicting the wrong thing costs a texture upload, not a
 * download and not a decode.
 */
public final class CardTextureCache
{
    /**
     * How much card art to leave resident on the GPU.
     * <p>
     * 192MB holds about three thousand 128px icons, or a couple of hundred
     * 512px previews, or the mixture actually in play. Chosen to be comfortably
     * more than any one screen needs so that scrolling back and forth over the
     * same page never evicts anything, while still bounding a session that
     * browses the whole collection.
     */
    private static final long BUDGET_BYTES = 192L * 1024L * 1024L;

    /** Ticks between sweeps. Eviction is not urgent; growth is gradual. */
    private static final int SWEEP_TICKS = 20;

    /** Resident card textures in least-recently-used order. */
    private static final Map<Identifier, Integer> RESIDENT =
        new LinkedHashMap<>(256, 0.75F, true);

    private static long residentBytes;
    private static int ticks;

    private CardTextureCache()
    {
    }

    /**
     * Notes that a card texture is in use, and how big it is.
     * <p>
     * Called from {@link DuelTextures} as the Identifier is handed out, which is
     * the one place every card draw passes through.
     *
     * @param size the square edge in pixels, which is what it costs on the GPU
     */
    public static void touch(Identifier id, int size)
    {
        if(id == null)
        {
            return;
        }
        // RGBA, one byte a channel, no mipmaps on these.
        int bytes = size * size * 4;
        synchronized(RESIDENT)
        {
            Integer had = RESIDENT.put(id, bytes);
            if(had == null)
            {
                residentBytes += bytes;
            }
        }
    }

    /** Bytes of card art believed resident, for a diagnostic line. */
    public static long residentBytes()
    {
        synchronized(RESIDENT)
        {
            return residentBytes;
        }
    }

    /**
     * Drops the least recently used textures until the budget is met.
     * <p>
     * On the client tick, which is the render thread — {@code release} disposes
     * GPU objects and may not be called from a worker.
     */
    public static void sweep()
    {
        if(++ticks < SWEEP_TICKS)
        {
            return;
        }
        ticks = 0;

        Minecraft client = Minecraft.getInstance();
        if(client.getTextureManager() == null)
        {
            return;
        }
        synchronized(RESIDENT)
        {
            if(residentBytes <= BUDGET_BYTES)
            {
                return;
            }
            // Access order, so the iterator starts at the least recently used.
            for(Iterator<Map.Entry<Identifier, Integer>> it = RESIDENT.entrySet().iterator();
                it.hasNext() && residentBytes > BUDGET_BYTES;)
            {
                Map.Entry<Identifier, Integer> eldest = it.next();
                client.getTextureManager().release(eldest.getKey());
                residentBytes -= eldest.getValue();
                it.remove();
            }
        }
    }

    /** Leaving a world does not free these, so a disconnect should. */
    public static void clear()
    {
        Minecraft client = Minecraft.getInstance();
        synchronized(RESIDENT)
        {
            if(client != null && client.getTextureManager() != null)
            {
                for(Identifier id : RESIDENT.keySet())
                {
                    client.getTextureManager().release(id);
                }
            }
            RESIDENT.clear();
            residentBytes = 0L;
        }
    }
}
