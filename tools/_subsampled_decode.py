"""Decode card JPEGs at the resolution we actually need.

The raw art is several hundred pixels on a side. The deck editor's grid icons are
128. The scaler was decoding every raw at FULL resolution, copying it into a
second full-resolution ARGB image, and only then resampling down -- so the great
majority of the pixels it decoded and copied were thrown away, and the render
thread was waiting for them.

JPEG readers can decode at a fraction of full size for a fraction of the cost.
Reading the header alone gives the source dimensions, so the factor is chosen
before committing to a decode: the largest power of two that still leaves at
least twice the target size, which keeps enough detail for the bilinear pass to
land on and never subsamples so far that it becomes the resampler itself.

Nothing about the OUTPUT contract changes -- same square canvas, same margin,
same bilinear filter, and adjustRawImage and scaledPngBytes still share this one
method, so they still agree byte for byte.
"""
import io

p = "src/main/java/de/cas_ual_ty/dueldimension/clientutil/ImageHandler.java"
s = io.open(p, encoding="utf-8").read()

old = "            BufferedImage rawImg = ImageIO.read(in);"
new = "            BufferedImage rawImg = readAtLeast(in, size * 2);"

assert old in s, "scaleRawImage anchor"
s = s.replace(old, new, 1)

# the reader
anchor = "    private static BufferedImage scaleRawImage(File raw, int size) throws IOException"
helper = '''    /**
     * Decodes an image at the smallest size that is still at least {@code least}
     * pixels on its shorter side.
     * <p>
     * A JPEG decoder can skip pixels while it decodes, and the cost falls with
     * the square of the factor -- decoding at a quarter size is roughly a
     * sixteenth of the work. The raws here are several hundred pixels on a side
     * and the grid asks for 128, so almost everything the old full-resolution
     * decode produced was discarded immediately, having been decoded, allocated
     * and copied first.
     * <p>
     * The factor is a power of two and never takes the image below {@code least},
     * which is twice the target: the bilinear pass afterwards still gets more
     * detail than it needs, so subsampling never becomes the thing doing the
     * resampling. Where the source is already small, the factor is 1 and this is
     * exactly the old behaviour.
     *
     * @return the decoded image, or null if nothing can read it
     */
    private static BufferedImage readAtLeast(InputStream in, int least) throws IOException
    {
        try(javax.imageio.stream.ImageInputStream stream =
            ImageIO.createImageInputStream(in))
        {
            if(stream == null)
            {
                return null;
            }
            java.util.Iterator<javax.imageio.ImageReader> readers =
                ImageIO.getImageReaders(stream);
            if(!readers.hasNext())
            {
                return null;
            }
            javax.imageio.ImageReader reader = readers.next();
            try
            {
                reader.setInput(stream, true, true);
                // The header alone gives the dimensions, so the factor is
                // decided before any pixels are paid for.
                int shorter = Math.min(reader.getWidth(0), reader.getHeight(0));
                int factor = 1;
                while(shorter / (factor * 2) >= Math.max(1, least))
                {
                    factor *= 2;
                }
                javax.imageio.ImageReadParam param = reader.getDefaultReadParam();
                if(factor > 1)
                {
                    param.setSourceSubsampling(factor, factor, 0, 0);
                }
                return reader.read(0, param);
            }
            catch(IOException | RuntimeException unreadable)
            {
                // A raw that will not decode is one card. Reported by the
                // caller, which already logs the path.
                return null;
            }
            finally
            {
                reader.dispose();
            }
        }
    }

''' + anchor

assert anchor in s
s = s.replace(anchor, helper, 1)
io.open(p, "w", encoding="utf-8", newline="\n").write(s)
print("subsampled decode added")
