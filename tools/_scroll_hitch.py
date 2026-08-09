"""Stop scaling card art on the render thread.

HitchWatch caught the whole pipeline in its stall samples: JPEGImageReader
(decode the raw), Deflater (re-encode a PNG), then STBImage (Minecraft decoding
that PNG again). Decode -> scale -> encode -> decode, per card, synchronously,
at the moment a card first comes into view. Scrolling a grid brings dozens into
view at once.

TWO faults, and the second one hid the first:

1. THE CACHE KEYS DID NOT MATCH. makeScalingTask cached under
   tagImage(imageName, size) -> "256/blue_eyes". DdCardResourcePack looked up
   name + "@" + raw.getName() -> "256/blue_eyes.png@blue_eyes.jpg". Those can
   never be equal, so every byte of background scaling was thrown away and the
   render thread re-did all of it. Both now go through one scaledKey().

   The raw's filename stays in the key on purpose: the small stand-in and the
   full art scale to the same size and name, and sharing a key would leave a
   card stuck on its blurry stand-in after the full image arrived.

2. NOTHING QUEUED THE WORK FOR CARDS ALREADY ON DISK. makeScalingTask was
   called from exactly one place -- the download path. A card whose raw was
   already downloaded (which, after the preloader has run, is every card) was
   marked finished on the spot with nothing scaled, so the first bind paid the
   whole cost. Readiness now means the SCALED BYTES ARE IN HAND, not merely
   that the raw is on disk; a cold card queues the work and shows the
   in-progress art for the frame or two it takes.
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
P = "src/main/java/de/cas_ual_ty/dueldimension/clientutil/DdCardResourcePack.java"

# ---------- 1. one key, used by both sides ----------
sub(H, """    public static Task makeScalingTask(String imageName, int imageSize, File raw)
    {
        final String key = ImageHandler.tagImage(imageName, imageSize);""",
    """    /**
     * The cache key for one scaled image.
     * <p>
     * The producer and the consumer MUST derive this the same way. They did not
     * -- the worker cached under {@code tagImage(name, size)} and the resource
     * pack looked up {@code name + "@" + raw} -- so every scaled image was
     * cached where nothing would ever look for it, and the render thread
     * redid the work every single time.
     * <p>
     * The raw's filename is part of the key deliberately: the small stand-in
     * and the full art scale to the same size under the same name, and one key
     * for both would leave a card showing its blurry stand-in for ever.
     */
    public static String scaledKey(String imageName, int size, File raw)
    {
        return ImageHandler.tagImage(imageName, size) + "@" + raw.getName();
    }

    /** Whether this image is already scaled and waiting, without doing any work. */
    public static boolean isScaled(String imageName, int size, File raw)
    {
        String key = ImageHandler.scaledKey(imageName, size, raw);
        synchronized(SCALED_CACHE)
        {
            return SCALED_CACHE.containsKey(key);
        }
    }

    public static Task makeScalingTask(String imageName, int imageSize, File raw)
    {
        final String key = ImageHandler.scaledKey(imageName, imageSize, raw);""",
    "scaledKey + isScaled")

sub(P, """                    return ImageHandler.scaledCached(raw, name + "@" + raw.getName(), size);""",
    """                    // The same key the worker caches under. These were
                    // derived independently and did not agree, which is why
                    // nothing was ever a cache hit.
                    return ImageHandler.scaledCached(raw,
                        ImageHandler.scaledKey(image, size, raw), size);""",
    "pack uses scaledKey")

# ---------- 2. readiness means scaled, not merely downloaded ----------
sub(H, """                if(adjusted.exists() || raw.exists()
                    || ImageHandler.getSmallCardImageFile(imageName).exists())
                {
                    // image exists, so set ready and return
                    list.setImmediateFinished(imagePathName);
                    return imagePathName;
                }""",
    """                if(adjusted.exists())
                {
                    // An adjusted file from an older cache is served straight
                    // off disk with no scaling, so it is ready as it stands.
                    list.setImmediateFinished(imagePathName);
                    return imagePathName;
                }
                File source = raw.exists() ? raw
                    : ImageHandler.getSmallCardImageFile(imageName);
                if(source.exists())
                {
                    // Downloaded is not the same as ready. Serving this now
                    // means the render thread decodes the raw, resamples it and
                    // re-encodes a PNG the moment the texture is first bound --
                    // which, in a scrolling grid, is dozens of cards in one
                    // frame. Hand back the in-progress art and let a worker do
                    // it; the card appears a frame or two later instead of
                    // stopping the client.
                    if(ImageHandler.isScaled(imageName, imageSize, source))
                    {
                        list.setImmediateFinished(imagePathName);
                        return imagePathName;
                    }
                    Task scaling = ImageHandler.makeScalingTask(imageName, imageSize, source);
                    if(scaling != null)
                    {
                        TaskQueue.addTask(scaling);
                    }
                    return ImageHandler.tagImage(inProgress, imageSize);
                }""",
    "readiness means scaled")

# ---------- 3. a cache big enough for a grid ----------
sub(H, """    private static final int SCALED_CACHE_SIZE = 320;""",
    """    /**
     * Enough for a scrolling grid plus what is just off screen.
     * <p>
     * 320 was sized when nothing ever hit this cache, so its only job was to
     * avoid rescaling a texture the binder had evicted. Now that it is the
     * thing standing between the render thread and a JPEG decode, it has to
     * hold everything on screen at once with room to scroll.
     */
    private static final int SCALED_CACHE_SIZE = 768;""", "cache size")

print("done")
