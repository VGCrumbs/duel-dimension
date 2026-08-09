"""Keep derived card-list icons on disk, so a launch does not rebuild them.

Nothing survived a restart: the scaled forms lived only in memory, so the first
browse of every session re-derived the whole visible collection. That is the
"restreaming every launch".

SCOPE: icons only, meaning sizes at or below SMALL_SOURCE_MAX. Two reasons, and
the second is the one that matters.

 1. Cost. Measured on this install: a derived 128px icon is about 39KB, so the
    whole collection is roughly 403MB on top of the 305MB of small raws. Doing
    the same for 512px previews would multiply that. Icons are what the card
    list shows a hundred at a time and what actually felt slow.

 2. Staleness, which rules previews out on correctness rather than cost. An
    icon is derived from the SMALL raw, and every card has one already -- it
    never changes, so a file written from it is right for ever. A preview is
    derived from the FULL raw, and only 966 of 10,856 have downloaded; the rest
    are standing in with the small copy. Persist one of those and the readiness
    gate, which treats an existing file as ready with no further checks, would
    serve that blurry stand-in for ever, even after the real art arrived.

The write goes on the worker that was already scaling, and the compressed bytes
are put in the memory cache too, so the image is encoded once and serves both.
The render thread's fallback keeps using the fast low-compression encode, where
the bytes are thrown away immediately and only speed matters.
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

H = "src/main/java/de/cas_ual_ty/dueldimension/clientutil/ImageHandler.java"

sub(H, """        return new ClientTask(TaskPriority.IMG_ADJUSTMENT, () ->
        {
            try
            {
                if(raw.exists())
                {
                    scaledCached(raw, key, imageSize);
                }
            }""",
    """        return new ClientTask(TaskPriority.IMG_ADJUSTMENT, () ->
        {
            try
            {
                if(raw.exists())
                {
                    persistOrScale(imageName, imageSize, raw, key);
                }
            }""", "task persists")

sub(H, """    /**
     * The scaled form of a raw image as PNG bytes, without touching the disk.""",
    """    /**
     * Scales one image on a worker, keeping the result if it is worth keeping.
     * <p>
     * An icon is written to disk as well as cached, so the next launch finds it
     * already made — the readiness gate serves an existing file with no scaling
     * at all. A preview is only cached, because a preview can be derived from a
     * stand-in and writing that down would freeze the stand-in permanently.
     * <p>
     * Encoded ONCE, at normal compression, and the same bytes go to both places.
     * Disk is where compression is worth paying for, and the memory copy is
     * happy to be smaller too. The low-compression encode stays on the render
     * thread's fallback path, where the bytes are decoded immediately and
     * discarded, so only speed counts.
     */
    private static void persistOrScale(String imageName, int size, File raw, String key)
        throws IOException
    {
        synchronized(SCALED_CACHE)
        {
            if(SCALED_CACHE.containsKey(key))
            {
                return;
            }
        }
        File adjusted = ImageHandler.getCardImageFile(ImageHandler.tagImage(imageName, size));
        // Only what came from the small raw, which is present for every card and
        // never changes; see the class of problems in this method's javadoc.
        boolean keep = size <= SMALL_SOURCE_MAX && !adjusted.exists()
            && raw.equals(ImageHandler.getSmallCardImageFile(imageName));

        BufferedImage scaled = scaleRawImage(raw, size);
        if(scaled == null)
        {
            return;
        }
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        ImageIO.write(scaled, "PNG", bytes);
        byte[] made = bytes.toByteArray();

        if(keep)
        {
            try
            {
                adjusted.getParentFile().mkdirs();
                // Written beside itself and moved into place, so a launch that
                // dies mid-write cannot leave a half a card on disk to be
                // served for ever afterwards.
                File part = new File(adjusted.getParentFile(), adjusted.getName() + ".part");
                try(java.io.FileOutputStream out = new java.io.FileOutputStream(part))
                {
                    out.write(made);
                }
                if(!part.renameTo(adjusted))
                {
                    part.delete();
                }
            }
            catch(IOException unwritable)
            {
                // A cache that will not write is still a cache for this
                // session. Nothing here is worth failing the image over.
            }
        }

        synchronized(SCALED_CACHE)
        {
            byte[] replaced = SCALED_CACHE.put(key, made);
            scaledCacheBytes += made.length - (replaced == null ? 0 : replaced.length);
            java.util.Iterator<java.util.Map.Entry<String, byte[]>> eldest =
                SCALED_CACHE.entrySet().iterator();
            while(scaledCacheBytes > SCALED_CACHE_BYTES && SCALED_CACHE.size() > 1
                && eldest.hasNext())
            {
                scaledCacheBytes -= eldest.next().getValue().length;
                eldest.remove();
            }
        }
    }

    /**
     * The scaled form of a raw image as PNG bytes, without touching the disk.""",
    "persistOrScale")

print("done")
