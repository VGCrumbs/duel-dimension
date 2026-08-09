"""Warm the hover preview too, and budget the cache in bytes rather than entries.

The grid draws at ICON_CARD_SIZE (128) and the hover preview at
PREVIEW_CARD_SIZE (512). Warming only the icons left every preview cold, so the
first hover over each card still waited for a 512px scale.

Previews are warmed for the VISIBLE page only, not the page either side like the
icons: a 512 costs roughly sixteen times the pixels of a 128, and the cards you
might scroll to are a much weaker bet than the ones you are looking at.

That makes the cache's entry count the wrong budget. 768 entries was sized when
everything in it was small; 768 previews would be hundreds of megabytes. It now
counts bytes, which is the thing that was actually scarce all along.

cardUnowned() resolves through the same getReplacementImage(), so warming a size
serves the owned and desaturated variants alike.
"""
import io

def sub(path, old, new, label):
    s = io.open(path, encoding="utf-8").read()
    if new in s:
        print("  skip (already applied):", label)
        return
    assert old in s, "anchor missing: " + label
    io.open(path, "w", encoding="utf-8", newline="\n").write(s.replace(old, new, 1))
    print("  ok:", label)

D = "src/main/java/de/cas_ual_ty/dueldimension/clientutil/hub/DeckEditorScreen.java"
H = "src/main/java/de/cas_ual_ty/dueldimension/clientutil/ImageHandler.java"

# ---------- warm previews for what is on screen ----------
sub(D, """        for(int i = first; i < last; i++)
        {
            Properties card = shown.get(i);
            if(card != null)
            {
                DuelTextures.card(card, (byte)0, DuelTextures.ICON_CARD_SIZE);
            }
        }
    }""",
    """        int onScreenFirst = Math.max(0, trunkScroll * trunkColumns);
        int onScreenLast = Math.min(shown.size(),
            (trunkScroll + visibleRows) * trunkColumns);

        for(int i = first; i < last; i++)
        {
            Properties card = shown.get(i);
            if(card == null)
            {
                continue;
            }
            DuelTextures.card(card, (byte)0, DuelTextures.ICON_CARD_SIZE);
            // The hover preview is a different, much larger texture, so warming
            // the grid icon did nothing for it and the first hover over every
            // card still waited. Only for the rows actually on screen though: a
            // 512 is about sixteen times the pixels of a 128, and a card you
            // MIGHT scroll to is a far weaker bet than one you are looking at.
            if(i >= onScreenFirst && i < onScreenLast)
            {
                DuelTextures.card(card, (byte)0, DuelTextures.PREVIEW_CARD_SIZE);
            }
        }
    }""", "warm previews")

# ---------- budget the cache in bytes ----------
sub(H, """    /**
     * Enough for a scrolling grid plus what is just off screen.
     * <p>
     * 320 was sized when nothing ever hit this cache, so its only job was to
     * avoid rescaling a texture the binder had evicted. Now that it is the
     * thing standing between the render thread and a JPEG decode, it has to
     * hold everything on screen at once with room to scroll.
     */
    private static final int SCALED_CACHE_SIZE = 768;""",
    """    /**
     * How much scaled art to keep, in bytes.
     * <p>
     * A count of entries was the wrong budget. It was chosen when everything in
     * here was a small grid icon, but the same cache now holds 512px hover
     * previews, which are roughly sixteen times the pixels — so a limit of 768
     * entries could mean anything from a few megabytes to a few hundred,
     * depending entirely on what the player happened to look at. Bytes are what
     * was actually scarce; count them instead.
     */
    private static final long SCALED_CACHE_BYTES = 96L * 1024L * 1024L;

    /** Bytes currently held, maintained alongside the map it describes. */
    private static long scaledCacheBytes;""", "byte budget constant")

sub(H, """            protected boolean removeEldestEntry(java.util.Map.Entry<String, byte[]> eldest)
            {
                return size() > SCALED_CACHE_SIZE;""",
    """            protected boolean removeEldestEntry(java.util.Map.Entry<String, byte[]> eldest)
            {
                // One per call: LinkedHashMap only ever drops the single eldest
                // entry per insertion, so the accounting is kept here rather
                // than looped, and put() trims the rest.
                if(scaledCacheBytes > SCALED_CACHE_BYTES && size() > 1)
                {
                    scaledCacheBytes -= eldest.getValue().length;
                    return true;
                }
                return false;""", "byte-aware eviction")

sub(H, """            synchronized(SCALED_CACHE)
            {
                SCALED_CACHE.put(key, made);
            }""",
    """            synchronized(SCALED_CACHE)
            {
                byte[] replaced = SCALED_CACHE.put(key, made);
                scaledCacheBytes += made.length - (replaced == null ? 0 : replaced.length);
                // removeEldestEntry drops at most one per insertion, so a big
                // entry arriving over budget is trimmed for here.
                java.util.Iterator<java.util.Map.Entry<String, byte[]>> eldest =
                    SCALED_CACHE.entrySet().iterator();
                while(scaledCacheBytes > SCALED_CACHE_BYTES && SCALED_CACHE.size() > 1
                    && eldest.hasNext())
                {
                    scaledCacheBytes -= eldest.next().getValue().length;
                    eldest.remove();
                }
            }""", "byte accounting on put")

print("done")
