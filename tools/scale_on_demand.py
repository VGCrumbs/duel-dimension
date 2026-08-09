"""Keep only the raws on disk and scale to the wanted size when asked.

Measured on the sets cache before changing anything: 308 raws are 6.3 MB and
their 256px derivatives are 28 MB. The derived copies are 4.4x LARGER than the
originals they came from, because a raw is a JPEG and adjusting it writes a
lossless PNG on a square power-of-two canvas that is mostly padding.

The scale moves to DdCardResourcePack, which is already the one bridge between
these folders and the game's textures, and already generates a variant in
memory rather than caching it -- the "unowned" desaturated copy works exactly
this way. So the change is: when the adjusted file is absent, read the raw,
scale it with the same bilinear filter as before, and hand back the PNG bytes.

Readiness moves with it. getReplacementImage treated "the adjusted file exists"
as "this image is ready"; now the RAW existing is what makes it ready, because
the adjusted form is produced on demand and never written.

Adjusted files already on disk keep working -- they are preferred when present,
so an existing cache is not invalidated, it simply stops growing.
"""
import io


def sub(path, old, new, label):
    s = io.open(path, encoding="utf-8").read()
    assert old in s, "anchor missing: " + label
    io.open(path, "w", encoding="utf-8", newline="\n").write(s.replace(old, new, 1))
    print("   ok:", label)


H = "src/main/java/de/cas_ual_ty/dueldimension/clientutil/ImageHandler.java"

# ---------- 1. the scaler, usable without a destination file ----------
sub(H, """    public static void adjustRawImage(File adjusted, File raw, int size) throws IOException
    {
        // size: target size, maybe make different versions for card info and card item

        try(InputStream in = new FileInputStream(raw))
        {
            BufferedImage rawImg = ImageIO.read(in);""",
    """    /**
     * The scaled form of a raw image, as PNG bytes, without touching the disk.
     * <p>
     * Same picture {@link #adjustRawImage} would have written -- it is the same
     * code -- but produced when something asks for it rather than kept in a
     * second cache. A raw is a JPEG of the card; the adjusted form is a
     * lossless PNG on a square canvas that is mostly padding, so storing it
     * costs several times what the original did.
     *
     * @return the bytes, or null if the raw cannot be read
     */
    public static byte[] scaledPngBytes(File raw, int size) throws IOException
    {
        BufferedImage scaled = scaleRawImage(raw, size);
        if(scaled == null)
        {
            return null;
        }
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        ImageIO.write(scaled, "PNG", bytes);
        return bytes.toByteArray();
    }

    public static void adjustRawImage(File adjusted, File raw, int size) throws IOException
    {
        BufferedImage newImg = scaleRawImage(raw, size);
        if(newImg == null)
        {
            throw new NullPointerException();
        }
        // Only the three configured sizes get their folders created at
        // startup; writing a size the duel screen asked for would
        // otherwise throw and mark the image permanently failed.
        adjusted.getParentFile().mkdirs();
        ImageIO.write(newImg, "PNG", adjusted);
    }

    /** Everything {@link #adjustRawImage} did except write the result. */
    private static BufferedImage scaleRawImage(File raw, int size) throws IOException
    {
        // size: target size, maybe make different versions for card info and card item

        try(InputStream in = new FileInputStream(raw))
        {
            BufferedImage rawImg = ImageIO.read(in);""",
    "scaleRawImage + scaledPngBytes")

sub(H, """            newImg.flush();
            // Only the three configured sizes get their folders created at
            // startup; writing a size the duel screen asked for would
            // otherwise throw and mark the image permanently failed.
            adjusted.getParentFile().mkdirs();
            ImageIO.write(newImg, "PNG", adjusted);
        }
    }""",
    """            newImg.flush();
            return newImg;
        }
    }""", "scaleRawImage returns")

sub(H, """            if(rawImg == null)
            {
                DuelDimension.log("Can not read image: " + raw.getAbsolutePath());
                throw new NullPointerException();
            }""",
    """            if(rawImg == null)
            {
                DuelDimension.log("Can not read image: " + raw.getAbsolutePath());
                return null;
            }""", "unreadable raw returns null")

# ---------- 2. the raw is what makes an image ready ----------
sub(H, """                if(adjusted.exists())
                {
                    // image exists, so set ready and return
                    list.setImmediateFinished(imagePathName);
                    return imagePathName;
                }""",
    """                // The RAW is what readiness means now. The adjusted form is
                // produced by DdCardResourcePack when the texture is asked
                // for and is never written, so waiting for it on disk would
                // wait forever. An adjusted file from an older cache still
                // counts -- it is preferred when serving, so a cache built
                // before this change keeps working and simply stops growing.
                if(adjusted.exists() || raw.exists())
                {
                    // image exists, so set ready and return
                    list.setImmediateFinished(imagePathName);
                    return imagePathName;
                }""", "readiness gate")

# ---------- 3. stop writing the derived file ----------
sub(H, """        t = ImageHandler.makeMissingAdjustedTask(imageName, imageSize, raw, adjusted);
        if(t != null)
        {
            TaskQueue.addTask(t);
        }""",
    """        // No adjust task. Scaling happens in DdCardResourcePack when the
        // texture is loaded, so there is nothing to write and nothing to wait
        // for beyond the download itself. makeMissingAdjustedTask is kept for
        // the rarity images, which are generated rather than downloaded and
        // still live on disk.""", "no adjust task")

print("done")
