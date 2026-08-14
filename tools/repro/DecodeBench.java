import com.mojang.blaze3d.platform.NativeImage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

/**
 * What half of a first sighting is CPU, measured against real card files.
 * <p>
 * The ration in CardTextureCache prices a first sighting as ONE number -- the
 * blit that realises the texture -- and the only split in evidence came from a
 * benchmark whose decoder was never written down. That matters because
 * {@code TextureManager.getTexture} does two separable things on a miss, and
 * javap says exactly where the seam is:
 * <pre>
 *   getTexture -> new SimpleTexture -> registerAndLoad
 *       loadContentsSafe -> loadContents -> SimpleTexture.loadContents
 *           -> TextureContents.load(rm, id)          CPU: Resource.open +
 *                                                    NativeImage.read + mcmeta
 *       ReloadableTexture.apply(contents)
 *           -> SamplerCache.getSampler               GPU
 *           -> doLoad -> GpuDevice.createTexture     GPU
 *                        createTextureView           GPU
 *                        CommandEncoder.writeToTexture
 * </pre>
 * Everything above the seam is {@code TextureContents.load}, a static that takes
 * a ResourceManager and an Identifier and touches no GL object -- which is why
 * it can be timed here, off the game, and why it can be moved to a worker.
 * <p>
 * Four timings per size, because the difference between them is the answer:
 * <ul>
 * <li><b>file read</b> alone, so decode is not credited with the disk;</li>
 * <li><b>STB decode</b> from bytes already in memory -- {@code NativeImage.read},
 *     which is what Minecraft actually calls;</li>
 * <li><b>read + STB decode</b> from a stream, which is the whole CPU half as
 *     {@code TextureContents.load} performs it;</li>
 * <li><b>ImageIO decode</b> of the same bytes, because that is the other
 *     plausible decoder a benchmark could have measured, and if the old figures
 *     match this one rather than STB then they were pricing a decoder the game
 *     does not use.</li>
 * </ul>
 */
public final class DecodeBench
{
    /** Files timed per size. Enough for a p99 to mean something. */
    private static final int SAMPLES = 300;

    /** Files decoded before the clock starts, so JIT is not in the measurement. */
    private static final int WARMUP = 80;

    public static void main(String[] args) throws Exception
    {
        Path root = Path.of(args[0]);
        int samples = args.length > 1 ? Integer.parseInt(args[1]) : SAMPLES;

        System.out.println("java " + System.getProperty("java.version")
            + "  os " + System.getProperty("os.name"));
        System.out.println("cards " + root);
        System.out.println();

        for(String size : new String[] {"128", "512"})
        {
            Path dir = root.resolve(size);
            if(!Files.isDirectory(dir))
            {
                System.out.println("skip " + dir + " (absent)");
                continue;
            }
            bench(dir, size, samples);
        }
        staging();
        collection(root);
    }

    /**
     * How long a whole collection takes to decode, on one worker and on four.
     * <p>
     * The number the design turns on. Today every one of these decodes happens
     * on the render thread, metered, so a collection arrives at whatever rate
     * the ration allows. EDOPro does not meter decode at all -- it runs
     * {@code imageLoadThreads} of them (default 4, image_manager.cpp:51) and
     * meters only the GPU upload, {@code maxImagesPerFrame} (default 50,
     * image_manager.cpp:372). This says what that would buy here.
     */
    private static void collection(Path root) throws Exception
    {
        System.out.println("== a collection's worth of decode (608 owned cards)");
        for(String size : new String[] {"128", "512"})
        {
            Path dir = root.resolve(size);
            if(!Files.isDirectory(dir))
            {
                continue;
            }
            List<Path> files = new ArrayList<>();
            try(Stream<Path> walk = Files.list(dir))
            {
                walk.filter(p -> p.toString().endsWith(".png")).limit(608).forEach(files::add);
            }
            for(int threads : new int[] {1, 4})
            {
                java.util.concurrent.ExecutorService pool =
                    java.util.concurrent.Executors.newFixedThreadPool(threads);
                long t0 = System.nanoTime();
                List<java.util.concurrent.Future<?>> jobs = new ArrayList<>();
                for(Path p : files)
                {
                    jobs.add(pool.submit(() ->
                    {
                        try(InputStream in = new BufferedInputStream(Files.newInputStream(p)))
                        {
                            NativeImage img = NativeImage.read(in);
                            img.close();
                        }
                        catch(Exception e)
                        {
                            throw new RuntimeException(e);
                        }
                    }));
                }
                for(java.util.concurrent.Future<?> f : jobs)
                {
                    f.get();
                }
                long took = System.nanoTime() - t0;
                pool.shutdown();
                System.out.printf("  %s px  %d worker%s  %d cards in %6.0f ms%n",
                    size, threads, threads == 1 ? " " : "s", files.size(), took / 1_000_000D);
            }
        }
        System.out.println();
    }

    /**
     * The one part of the GPU half that scales with the image.
     * <p>
     * This client runs the Vulkan backend (its own log says so), and javap of
     * {@code VulkanCommandEncoder.writeToTexture} says what that costs on the
     * calling thread: {@code VulkanTransientMemory.uploadStaging} -- a memcpy of
     * the image into a mapped staging block -- then a {@code VkBufferImageCopy}
     * filled in and recorded with {@code vkCmdCopyBufferToImage} and a memory
     * barrier. There is <b>no vkQueueSubmit and no fence wait</b> in that method,
     * so the render thread never blocks on the GPU; everything else it does is a
     * fixed handful of struct writes.
     * <p>
     * So the memcpy is the term that grows with the card, and this measures it
     * between two direct buffers. RAM to RAM is the floor: real staging may be
     * write-combined device memory across PCIe, which is slower. It is still the
     * right order of magnitude to compare against a decode.
     */
    private static void staging()
    {
        System.out.println("== staging copy (the data-proportional part of the Vulkan upload)");
        for(int edge : new int[] {128, 512})
        {
            int bytes = edge * edge * 4;
            java.nio.ByteBuffer src = java.nio.ByteBuffer.allocateDirect(bytes);
            java.nio.ByteBuffer dst = java.nio.ByteBuffer.allocateDirect(bytes);
            for(int i = 0; i < 2000; i++)
            {
                org.lwjgl.system.MemoryUtil.memCopy(src, dst);
            }
            long[] took = new long[2000];
            for(int i = 0; i < took.length; i++)
            {
                long t0 = System.nanoTime();
                org.lwjgl.system.MemoryUtil.memCopy(src, dst);
                took[i] = System.nanoTime() - t0;
            }
            report("  " + edge + "px (" + (bytes / 1024) + " KB) memcpy ", took);
        }
        System.out.println();
    }

    private static void bench(Path dir, String label, int samples) throws Exception
    {
        List<Path> files = new ArrayList<>();
        try(Stream<Path> walk = Files.list(dir))
        {
            walk.filter(p -> p.toString().endsWith(".png")).forEach(files::add);
        }
        // A fixed seed so two runs compare, and a shuffle so the sample is not
        // one contiguous run of card ids that happen to share an artist.
        Collections.shuffle(files, new Random(20260812L));

        int take = Math.min(samples + WARMUP, files.size());
        List<Path> chosen = files.subList(0, take);

        // Warm the page cache for every file first. Otherwise the first pass
        // measures the disk and the last three measure RAM, and the comparison
        // between them would be meaningless.
        byte[][] bytes = new byte[take][];
        for(int i = 0; i < take; i++)
        {
            bytes[i] = Files.readAllBytes(chosen.get(i));
        }

        for(int i = 0; i < Math.min(WARMUP, take); i++)
        {
            NativeImage warm = NativeImage.read(bytes[i]);
            warm.close();
            ImageIO.read(new ByteArrayInputStream(bytes[i]));
            Files.readAllBytes(chosen.get(i));
        }

        int n = take - WARMUP;
        if(n <= 0)
        {
            System.out.println(label + "px: only " + take + " files, not enough to time");
            return;
        }
        long[] readOnly = new long[n];
        long[] stbOnly = new long[n];
        long[] readAndStb = new long[n];
        long[] imageIo = new long[n];
        long pixels = 0L;
        long fileBytes = 0L;
        int width = 0;
        int height = 0;

        for(int i = 0; i < n; i++)
        {
            Path p = chosen.get(WARMUP + i);
            byte[] b = bytes[WARMUP + i];
            fileBytes += b.length;

            long t0 = System.nanoTime();
            byte[] readBack = Files.readAllBytes(p);
            readOnly[i] = System.nanoTime() - t0;

            t0 = System.nanoTime();
            NativeImage img = NativeImage.read(b);
            stbOnly[i] = System.nanoTime() - t0;
            width = img.getWidth();
            height = img.getHeight();
            pixels += (long)width * height;
            img.close();

            // The whole CPU half the way TextureContents.load does it: open the
            // file, hand the stream to NativeImage.read.
            t0 = System.nanoTime();
            try(InputStream in = new BufferedInputStream(Files.newInputStream(p)))
            {
                NativeImage whole = NativeImage.read(in);
                readAndStb[i] = System.nanoTime() - t0;
                whole.close();
            }

            t0 = System.nanoTime();
            BufferedImage awt = ImageIO.read(new ByteArrayInputStream(b));
            imageIo[i] = System.nanoTime() - t0;
            if(awt != null)
            {
                awt.flush();
            }
            if(readBack.length != b.length)
            {
                throw new IllegalStateException("file changed under the benchmark");
            }
        }

        System.out.println("== " + label + "px  (" + width + "x" + height + ", n=" + n
            + ", avg file " + (fileBytes / n / 1024) + " KB, "
            + (pixels / n / 1000) + "k pixels)");
        report("  file read           ", readOnly);
        report("  STB decode          ", stbOnly);
        report("  read + STB decode   ", readAndStb);
        report("  ImageIO decode      ", imageIo);
        System.out.println();
    }

    private static void report(String what, long[] nanos)
    {
        long[] sorted = Arrays.copyOf(nanos, nanos.length);
        Arrays.sort(sorted);
        long sum = 0L;
        for(long v : sorted)
        {
            sum += v;
        }
        System.out.printf("%s p50 %6.3f ms   mean %6.3f ms   p99 %7.3f ms   max %7.3f ms%n",
            what,
            sorted[sorted.length / 2] / 1_000_000D,
            (sum / (double)sorted.length) / 1_000_000D,
            sorted[(sorted.length * 99 - 1) / 100] / 1_000_000D,
            sorted[sorted.length - 1] / 1_000_000D);
    }
}
