package de.cas_ual_ty.dueldimension.clientutil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.SimpleTexture;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Card art, decoded off the render thread and uploaded a few at a time.
 * <p>
 * A port of EDOPro's {@code ImageManager} ({@code gframe/image_manager.cpp}),
 * whose answer to "a first sighting is expensive" is not to do fewer of them.
 * It is to <b>cut the work in half at the decode/upload seam, put the expensive
 * half on worker threads, and ration only the cheap half</b> — generously.
 * <p>
 * The seam is real and it is Mojang's own. {@code TextureManager.getTexture}
 * on a miss is {@code new SimpleTexture(id)} then {@code registerAndLoad},
 * which is {@code loadContents} (read + decode, no GPU call anywhere in it)
 * followed by {@code apply} (sampler, {@code createTexture},
 * {@code createTextureView}, {@code writeToTexture}) — both inline on whoever
 * asked. Vanilla itself splits them: {@code TextureManager.scheduleLoad} is
 * {@code CompletableFuture.supplyAsync(() -> loadContents(...), executor)}.
 * This class does the same split by hand and keeps the halves on the threads
 * EDOPro keeps them on.
 * <p>
 * Measured on this machine, read + STB decode is 0.271 ms for a 128px icon and
 * 2.972 ms for a 512px preview, while the part of the Vulkan upload that scales
 * with the image — the staging memcpy — is 0.001 ms and 0.018 ms. So 82–99% of
 * a first sighting is CPU, which is why moving the decode is the whole fix and
 * why a <em>count</em> of uploads is enough to meter what is left.
 * <p>
 * <b>What replaces the old ration.</b> Nothing on the render thread is metered
 * by time any more. Four brakes keep work off the queue instead, all EDOPro's:
 * the velocity gate refuses to <em>request</em> while a list is flicking
 * ({@code drawing.cpp}:1378, {@link #drawThumb}); prefetch is one row either
 * side and is a draw through the clip rect, so it cannot warm what the screen
 * would not draw ({@code drawing.cpp}:1385-1387); the queue is a stack, so a
 * stale request sinks and is served last; and the epoch discards the lot on a
 * screen change.
 * <p>
 * <b>The one rule that is not enforced by the type system.</b> A texture is
 * reached by {@link ResourceLocation}, not by a handle, so this class works by
 * pre-registering under the ResourceLocation and handing the ResourceLocation back. An
 * ResourceLocation that reaches a blit while {@code TextureManager} does not hold it
 * is decoded and uploaded synchronously — the exact hitch this removes. So
 * every producer of card art must come through {@link #getTextureCard}, and
 * anything that releases a texture must put its entry back to
 * {@link preloadStatus#NONE} in the same breath (see
 * {@link CardTextureCache#sweep()}).
 */
public final class CardImageManager
{
    private static final org.apache.logging.log4j.Logger LOG =
        org.apache.logging.log4j.LogManager.getLogger();

    /**
     * {@code image_manager.h}:43-48.
     * <p>
     * WAIT_DOWNLOAD has no counterpart: the download is upstream of us, and
     * {@code ImageHandler} answers a card it is still fetching with a different
     * name entirely, which never reaches here.
     * <p>
     * FAILED has no counterpart either, and it has to exist. EDOPro records a
     * permanent failure as the PAIR {@code preload_status == LOADED} with
     * {@code texture == nullptr} ({@code image_manager.cpp}:386-388), and
     * {@code GetTextureCard} then returns the placeholder because the pointer is
     * null (:733-737). We have no pointer to be null — the texture lives in
     * {@code TextureManager.byPath} — so the pair collapses into one state. It
     * matters: without it a card whose file is missing would come back LOADED,
     * be handed out, miss in {@code byPath}, and be decoded on the render thread
     * once a frame for ever.
     */
    enum preloadStatus
    {
        NONE,
        LOADING,
        LOADED,
        FAILED
    }

    /** {@code image_manager.h}:68-72. */
    enum loadStatus
    {
        LOAD_OK,
        LOAD_FAIL
    }

    /**
     * One request. {@code image_manager.h}:54-68.
     * <p>
     * EDOPro holds {@code const std::atomic<irr::s32>&} references to the live
     * size rather than copies, so a worker mid-resample can re-target itself.
     * We hold a plain int because there is nothing to re-target: our size is
     * part of the ResourceLocation's path ({@code textures/item/{size}/…}), so a
     * request and its file cannot disagree about it.
     *
     * @param requeued whether this is the second attempt after a size mismatch;
     *                 see {@link #refreshCachedTextures()}
     */
    record LoadParameter(ResourceLocation id, int sizeIndex, int size, long timestamp,
        boolean requeued)
    {
    }

    /** One finished decode on its way to the GPU. {@code image_manager.h}:74-79. */
    record LoadReturn(loadStatus status, ResourceLocation id, int sizeIndex, int size,
        long timestamp, boolean requeued, NativeImage contents)
    {
    }

    /**
     * How many size classes there are, and therefore how many maps and how many
     * result queues. Four, which is EDOPro's count ({@code tMap[0]},
     * {@code tMap[1]}, {@code tThumb}, {@code tCovers}) and by coincidence also
     * ours: 64 item icons, 128 grid icons, 256 info/main art, 512 field and
     * preview art.
     */
    static final int CLASSES = 4;

    /**
     * The largest image each class holds, so an arbitrary configured size lands
     * in the right one. {@code ClientConfig} allows 16..1024, so the classes are
     * upper bounds rather than an exact match on the four numbers in play.
     */
    private static final int[] CLASS_MAX = { 64, 128, 256, Integer.MAX_VALUE };

    /**
     * Uploads allowed per class per frame. {@code image_manager.cpp}:372, and
     * EDOPro's default is 50 for every one of its four ({@code
     * game_config.inl}:107).
     * <p>
     * Verbatim for 64, 128 and 256. <b>16 for the 512 class</b>, which is the
     * only number in this file derived rather than copied: EDOPro's 50 is
     * calibrated against an 11 KB thumb and a 180 KB art, giving it a ceiling
     * near 27 MB of upload a frame. Fifty of our 1 MB previews would be 50 MB,
     * nearly double that, and EDOPro has no texture of that size to have
     * calibrated against. Sixteen is about 60% of its ceiling and covers every
     * screen that holds 512s except the 24-card pack summary, which then fills
     * in two frames — the same ratio EDOPro's own fullest screen has.
     * <p>
     * The revision rule, should the measurement land: if the {@code apply} p99
     * at 512 exceeds 0.5 ms, lower 16 until {@code 16 × p99 < 8 ms}; if it is
     * under 0.2 ms, raise it towards EDOPro's 50.
     */
    private static final int[] MAX_IMAGES_PER_FRAME = { 50, 50, 50, 16 };

    /** {@code game_config.inl}:108. */
    private static final int IMAGE_LOAD_THREADS = 4;

    /**
     * Whether each card is absent, in flight or on the GPU, one map per size
     * class. {@code image_manager.h}:104-107.
     * <p>
     * Holds no image: {@code TextureManager.byPath} <em>is</em> EDOPro's
     * {@code texture_map_entry.texture}, and {@code register} is its
     * {@code addTexture}. One map per class rather than one map keyed on
     * (id, size) so that putting an evicted entry back to NONE is a single
     * {@code put} and not a search — {@link CardTextureCache#sweep()} depends
     * on that.
     */
    @SuppressWarnings("unchecked")
    static final Map<ResourceLocation, preloadStatus>[] tMap = new Map[CLASSES];

    /**
     * Requests, newest first. {@code image_manager.cpp}:725 pushes the front and
     * :458-459 pops the front.
     * <p>
     * <b>A stack, and that is the entire supersede mechanism.</b> Nothing is
     * ever cancelled individually; the newest request decodes first and a stale
     * one sinks to the bottom and is served last. A FIFO here is what makes a
     * flick feel laggy, because it serves the rows the player has already flown
     * past before the ones they landed on.
     */
    static final ArrayDeque<LoadParameter> toLoad = new ArrayDeque<>();

    /** Finished decodes, newest first, one queue per size class.
     * {@code image_manager.cpp}:463 pushes the front, :377-378 pops it. */
    @SuppressWarnings("unchecked")
    static final ArrayDeque<LoadReturn>[] loadedPics = new ArrayDeque[CLASSES];

    /** Decoded images nobody wants, freed off the render thread.
     * {@code image_manager.cpp}:411-426. */
    static final ArrayDeque<LoadReturn> toClear = new ArrayDeque<>();

    private static final ReentrantLock picLoad = new ReentrantLock();
    private static final Condition cvLoad = picLoad.newCondition();
    private static final ReentrantLock objClearLock = new ReentrantLock();
    private static final Condition cvClear = objClearLock.newCondition();

    /**
     * The generation every request carries. {@code image_manager.h}:152.
     * <p>
     * Bumping it abandons everything in flight without touching any of it —
     * which is what makes cancellation free. Workers compare before opening the
     * file and again before handing the result over.
     */
    private static volatile long timestampId;

    private static volatile boolean stopThreads;
    private static boolean started;

    static
    {
        for(int i = 0; i < CLASSES; i++)
        {
            tMap[i] = new HashMap<>(256);
            loadedPics[i] = new ArrayDeque<>();
        }
    }

    private CardImageManager()
    {
    }

    /**
     * Which of the four maps a size belongs in.
     * <p>
     * EDOPro asks the same question by which accessor you called; ours is a
     * number from a config value, so it is asked here. Upper bounds rather than
     * equality because {@code cardInfoImageSize} and friends are user-set
     * anywhere in 16..1024 and every one of them still has to land somewhere.
     */
    public static int classOf(int size)
    {
        for(int i = 0; i < CLASSES - 1; i++)
        {
            if(size <= CLASS_MAX[i])
            {
                return i;
            }
        }
        return CLASSES - 1;
    }

    /**
     * Whether a scrolling list is moving slowly enough to be worth asking for
     * art. {@code drawing.cpp}:1378, transliterated.
     * <p>
     * <b>This is EDOPro's actual answer to the flick hitch, and note its
     * shape: it is admission control on the REQUEST, not a rate limit on the
     * work.</b> Above the threshold the caller draws the placeholder without
     * calling {@link #getTextureCard} at all — no map entry, no queue entry, no
     * decode. Below it the pipeline runs flat out, which is why it costs
     * nothing when the player is standing still.
     * <p>
     * The threshold is EDOPro's own: {@code 10.0f * 60.0f / 1000.0f} rows per
     * millisecond, i.e. ten rows per 16.6 ms frame, and it is expressed against
     * elapsed time so it means the same thing at 60 fps and at 240. EDOPro
     * shows nine rows, so the gate only fires when more than the whole visible
     * list turns over inside one frame.
     * <p>
     * This replaces a timestamp of the last wheel event, which could not tell a
     * flick from a single click: what matters is how fast the LIST is moving,
     * not how recently it was touched.
     *
     * @param deltaMillis milliseconds since the previous frame, as
     *                    {@code delta_time} is in {@code game.cpp}:2044
     */
    public static boolean drawThumb(int prevRow, int row, float deltaMillis)
    {
        return Math.abs(prevRow - row) < (10F * 60F / 1000F) * deltaMillis;
    }

    /**
     * Starts the loader threads. Called once, from {@code ClientProxy}.
     * <p>
     * EDOPro spawns these in the constructor ({@code image_manager.cpp}:47-53).
     * A static initialiser is the Java equivalent and is the wrong place for it:
     * it would start four threads the moment any class in this package is
     * touched, including from a unit test that only wants to check the queue.
     */
    public static synchronized void init()
    {
        if(started)
        {
            return;
        }
        started = true;

        Thread objClearThread = new Thread(CardImageManager::clearFutureObjects,
            "dueldimension-img-clear");
        objClearThread.setDaemon(true);
        objClearThread.start();

        for(int i = 0; i < IMAGE_LOAD_THREADS; i++)
        {
            Thread worker = new Thread(CardImageManager::loadPic,
                "dueldimension-img-load-" + (i + 1));
            worker.setDaemon(true);
            // Below the render thread. A decode must never be the reason a
            // frame is late; it is work the frame is deliberately not waiting
            // for.
            worker.setPriority(Thread.NORM_PRIORITY - 1);
            worker.start();
        }

        // Every discarded TextureContents is 64 KB to 1 MB of stb memory
        // outside the Java heap, so the collector will not save us on the way
        // down. WorkerManager:20-46 already models this.
        Runtime.getRuntime().addShutdownHook(new Thread(() ->
        {
            stopThreads = true;
            picLoad.lock();
            try
            {
                cvLoad.signalAll();
            }
            finally
            {
                picLoad.unlock();
            }
            objClearLock.lock();
            try
            {
                for(LoadReturn pending : toClear)
                {
                    closeQuietly(pending);
                }
                toClear.clear();
                cvClear.signalAll();
            }
            finally
            {
                objClearLock.unlock();
            }
        }, "dueldimension-img-shutdown"));
    }

    /**
     * The ResourceLocation to draw this card with, which is the placeholder until the
     * art is on the GPU. {@code image_manager.cpp}:658-737.
     * <p>
     * On the render thread, and it is a hash lookup and nothing else in the
     * overwhelmingly common case. A card drawn two hundred frames running is
     * enqueued once: the entry goes to {@link preloadStatus#LOADING} in the same
     * breath as the push ({@code image_manager.cpp}:707-708), so the other 199
     * calls find it there and return.
     * <p>
     * <b>There is no {@code wait} parameter.</b> EDOPro has one and no caller in
     * its tree passes it — all thirteen {@code GetTextureCard} sites take the
     * default or an explicit false. Keeping a synchronous path "just for the
     * first frame" would not be parity; it would be the hitch, on a timer.
     */
    /**
     * The same as {@link #getTextureCard}, but able to look without asking.
     * <p>
     * A velocity-gated screen still has to DRAW what it already has. Returning
     * the placeholder for a resident texture just because the list is moving
     * makes the tile alternate between art and card back as the gate flips
     * from frame to frame, which is seen as flickering.
     *
     * @param mayRequest false to look only: a miss stays a miss and nothing is
     *                   queued
     */
    public static ResourceLocation peekTextureCard(ResourceLocation id, int size, boolean mayRequest)
    {
        if(id == null)
        {
            return DuelTextures.UNKNOWN;
        }
        if(!mayRequest)
        {
            Map<ResourceLocation, preloadStatus> map = tMap[classOf(size)];
            synchronized(map)
            {
                if(map.get(id) == preloadStatus.LOADED)
                {
                    CardTextureCache.touch(id, size);
                    return id;
                }
            }
            return DuelTextures.UNKNOWN;
        }
        return getTextureCard(id, size);
    }

    public static ResourceLocation getTextureCard(ResourceLocation id, int size)
    {
        if(id == null)
        {
            return DuelTextures.UNKNOWN;
        }
        int index = classOf(size);
        Map<ResourceLocation, preloadStatus> map = tMap[index];
        synchronized(map)
        {
            preloadStatus status = map.get(id);
            if(status == preloadStatus.LOADED)
            {
                // Touched on every HIT, not only when it was uploaded. The
                // resident map is access-ordered, so this is the only thing
                // that makes it a least-recently-used order at all: touching
                // solely at register time left it in upload order, and sweep()
                // then evicted whatever was loaded first rather than whatever
                // the player had stopped looking at.
                CardTextureCache.touch(id, size);
                return id;
            }
            // FAILED as well as LOADING: a card whose file is missing or
            // truncated keeps the placeholder and is NOT asked for again, or
            // every frame would queue a decode that is going to fail again.
            // clearCachedTextures resets the map, so the next visit retries --
            // which is EDOPro's own recovery, not something invented here.
            if(status == preloadStatus.LOADING || status == preloadStatus.FAILED)
            {
                return DuelTextures.UNKNOWN;
            }
            map.put(id, preloadStatus.LOADING);
        }

        picLoad.lock();
        try
        {
            toLoad.addFirst(new LoadParameter(id, index, size, timestampId, false));
            cvLoad.signal();
        }
        finally
        {
            picLoad.unlock();
        }
        return DuelTextures.UNKNOWN;
    }

    /**
     * Puts finished decodes on the GPU, up to a few per class.
     * {@code image_manager.cpp}:368-410.
     * <p>
     * Once a frame, on the render thread, from {@code GameRendererMixin} at the
     * head of {@code GameRenderer.extract} — the structural twin of
     * {@code drawing.cpp}:774, the first act of {@code Game::DrawGUI}. Two
     * independent proofs that creating GPU textures there is safe: vanilla's own
     * reload applies textures from {@code Minecraft.execute}, drained earlier in
     * the same frame, and today's synchronous {@code getTexture} creates them
     * during the GUI extract pass, later in it.
     * <p>
     * <b>The lock is released before {@code apply}</b> ({@code
     * image_manager.cpp}:378 before :396) so a slow VMA allocation can never
     * block a worker.
     */
    public static void refreshCachedTextures()
    {
        // Once per frame, before anything is drawn: this is what tells the
        // cache which textures are on screen NOW, so the sweep cannot evict
        // one that is about to be drawn again.
        CardTextureCache.beginFrame();
        Minecraft client = Minecraft.getInstance();
        if(client == null || client.getTextureManager() == null)
        {
            return;
        }
        for(int index = 0; index < CLASSES; index++)
        {
            loadTexture(client, index);
        }
    }

    private static void loadTexture(Minecraft client, int index)
    {
        ArrayDeque<LoadParameter> readd = null;
        for(int i = 0; i < MAX_IMAGES_PER_FRAME[index]; i++)
        {
            LoadReturn loaded;
            picLoad.lock();
            try
            {
                loaded = loadedPics[index].pollFirst();
            }
            finally
            {
                picLoad.unlock();
            }
            if(loaded == null)
            {
                break;
            }

            // A result from before the last clear. It must NOT write to tMap:
            // the clear emptied that map, and marking LOADED here would hand
            // callers an ResourceLocation with no texture behind it, which is the
            // silent way back to a synchronous decode.
            if(loaded.timestamp() != timestampId)
            {
                abandon(loaded);
                continue;
            }

            if(loaded.status() == loadStatus.LOAD_FAIL)
            {
                // EDOPro's LOADED-with-a-null-texture, :386-388: asked, answered
                // and not to be asked again until the map is reset.
                mark(index, loaded.id(), preloadStatus.FAILED);
                continue;
            }

            // 26.2 carries a TextureContents -- a NativeImage plus its texture
            // metadata. 1.21.1 has no such record and reads metadata separately;
            // nothing here ever asked for the metadata half, so the image IS the
            // contents.
            NativeImage contents = loaded.contents();
            int width = contents.getWidth();
            int height = contents.getHeight();
            if(width != loaded.size() || height != loaded.size())
            {
                // :391-404, with one adaptation. EDOPro re-queues because its
                // wanted size is a live atomic that may have moved under the
                // worker, so a second pass fixes it. Ours is baked into the
                // ResourceLocation's path, so a re-decode yields the same mismatch --
                // re-queueing without a bound would spin four workers for ever
                // on one malformed file. So it is retried exactly once and then
                // treated as a failure.
                //
                // And the image is freed on the way. EDOPro's own :390-395
                // never drops it, unlike both siblings at :397, so every resize
                // with loads in flight leaks one decoded image per card. That
                // bug is not ported.
                abandon(loaded);
                if(!loaded.requeued())
                {
                    if(readd == null)
                    {
                        readd = new ArrayDeque<>();
                    }
                    readd.add(new LoadParameter(loaded.id(), index, loaded.size(),
                        timestampId, true));
                }
                else
                {
                    LOG.warn("[card-image] {} decoded {}x{} for a {}px slot; giving up",
                        loaded.id(), width, height, loaded.size());
                    mark(index, loaded.id(), preloadStatus.FAILED);
                }
                continue;
            }

            // The GPU half, and the whole of it: the sampler lookup, the VMA
            // image, the view, the staging memcpy and the register that makes
            // the ResourceLocation resolvable. This is the figure that decides
            // MAX_IMAGES_PER_FRAME for the 512 class, and the only part of a
            // first sighting that no off-device benchmark could reach.
            long start = System.nanoTime();
            try
            {
                // DynamicTexture, not SimpleTexture: 1.21.1's SimpleTexture
                // loads from the resource manager by id and has no public
                // "here is an image I already decoded" entry point, which is
                // exactly what this pipeline has. DynamicTexture(NativeImage)
                // takes ownership and uploads, which is the same two steps
                // 26.2's apply() did.
                DynamicTexture texture = new DynamicTexture(contents);
                // apply() closes the NativeImage itself once doLoad has copied
                // it, which is EDOPro's texture->drop() at :397 -- also on the
                // render thread. Only ABANDONED images go to the clear thread.
                client.getTextureManager().register(loaded.id(), texture);
            }
            catch(RuntimeException gpu)
            {
                // This runs inside the frame, for up to fifty textures at a
                // time, so one image the driver refuses must cost that image and
                // not the client. close() is idempotent, so the defensive one
                // here cannot double-free whatever apply() already released.
                LOG.warn("[card-image] could not upload {}", loaded.id(), gpu);
                closeQuietly(loaded);
                mark(index, loaded.id(), preloadStatus.FAILED);
                continue;
            }
            CardTextureCache.uploadTook(loaded.size(), System.nanoTime() - start);

            mark(index, loaded.id(), preloadStatus.LOADED);
            // Resident is recorded HERE and nowhere else, because this is the
            // moment a GPU object actually exists. The old cache recorded it at
            // admit time, so a warmed-but-never-drawn ResourceLocation counted against
            // the budget for ever while occupying no VRAM at all.
            CardTextureCache.touch(loaded.id(), loaded.size());
        }

        if(readd != null)
        {
            picLoad.lock();
            try
            {
                for(LoadParameter again : readd)
                {
                    toLoad.addFirst(again);
                }
                cvLoad.signalAll();
            }
            finally
            {
                picLoad.unlock();
            }
        }
    }

    /** One loader thread. {@code image_manager.cpp}:448-465. */
    private static void loadPic()
    {
        while(!stopThreads)
        {
            LoadParameter request;
            picLoad.lock();
            try
            {
                while(toLoad.isEmpty())
                {
                    // A condition variable, not a poll. WorkerManager's threads
                    // sleep 100 ms when the queue is empty, so a card the player
                    // is looking at would wait a tenth of a second before a
                    // worker so much as looked at it; EDOPro signals for exactly
                    // that reason (cv_load.notify_one, :726).
                    cvLoad.await();
                    if(stopThreads)
                    {
                        return;
                    }
                }
                request = toLoad.pollFirst();
            }
            catch(InterruptedException stop)
            {
                Thread.currentThread().interrupt();
                return;
            }
            finally
            {
                picLoad.unlock();
            }

            LoadReturn result = loadCardTexture(request);
            picLoad.lock();
            try
            {
                loadedPics[request.sizeIndex()].addFirst(result);
            }
            finally
            {
                picLoad.unlock();
            }
        }
    }

    /**
     * Read and decode, on a worker. {@code image_manager.cpp}:578-657.
     * <p>
     * {@code TextureContents.load} is EDOPro's {@code createImageFromFile} and
     * its {@code GetScaledImage} at once, because our scaling already happened
     * upstream: {@code ImageHandler} writes a pre-scaled PNG and
     * {@code DdCardResourcePack} serves it. It touches no GL or Vulkan object —
     * {@code getResourceOrThrow}, {@code Resource.open},
     * {@code NativeImage.read}, {@code metadata()} — which is why it is safe
     * here, and vanilla runs the same call on an executor during a reload.
     * <p>
     * <b>Two threads never share a {@code Resource}.</b>
     * {@code FallbackResourceManager.getResource} builds a new one per call, and
     * {@code Resource.metadata()} is a lazy memoisation with no happens-before
     * edge — so caching {@code Resource} objects across calls, which looks like
     * an obvious optimisation, would make that race real. Recorded here so
     * nobody does it. (The state machine already keeps a card in flight at most
     * once, which is a second, independent reason it cannot happen today.)
     */
    private static LoadReturn loadCardTexture(LoadParameter p)
    {
        // :592/:614 -- before the file is even opened, so a screen the player
        // has already left costs nothing.
        if(p.timestamp() != timestampId)
        {
            return fail(p);
        }

        NativeImage contents;
        long start = System.nanoTime();
        try
        {
            // TextureContents.load is EDOPro's createImageFromFile; on 1.21.1
            // the same two steps are open-the-resource and NativeImage.read.
            // Still off the render thread, still throwing on a truncated PNG
            // in exactly the place the catch below expects.
            try(java.io.InputStream stream = Minecraft.getInstance().getResourceManager()
                .open(p.id()))
            {
                contents = NativeImage.read(stream);
            }
            CardTextureCache.decodeTook(p.size(), System.nanoTime() - start);
        }
        catch(Exception missing)
        {
            // A truncated PNG throws in NativeImage.read. That hazard is not
            // new -- the render thread read the same bytes the same way -- and
            // off-thread it is strictly better: a failure on a worker instead
            // of a stack trace in the middle of a frame.
            return fail(p);
        }

        // :626 -- and again before handing it over, so a clear during the decode
        // still costs at most this one image.
        if(p.timestamp() != timestampId)
        {
            contents.close();
            return fail(p);
        }
        return new LoadReturn(loadStatus.LOAD_OK, p.id(), p.sizeIndex(), p.size(),
            p.timestamp(), p.requeued(), contents);
    }

    private static LoadReturn fail(LoadParameter p)
    {
        return new LoadReturn(loadStatus.LOAD_FAIL, p.id(), p.sizeIndex(), p.size(),
            p.timestamp(), p.requeued(), null);
    }

    /**
     * Frees decoded images nobody wants. {@code image_manager.cpp}:411-426.
     * <p>
     * A thread whose whole job is one {@code close()}, which is EDOPro's
     * {@code img.texture->drop()}. Note the asymmetry it inherits: the
     * successful path frees on the render thread, inside {@code apply}; only
     * ABANDONED images come here. Freeing several hundred of them at a screen
     * transition would otherwise stall a frame.
     */
    private static void clearFutureObjects()
    {
        while(!stopThreads)
        {
            LoadReturn img;
            objClearLock.lock();
            try
            {
                while(toClear.isEmpty())
                {
                    cvClear.await();
                    if(stopThreads)
                    {
                        return;
                    }
                }
                img = toClear.pollFirst();
            }
            catch(InterruptedException stop)
            {
                Thread.currentThread().interrupt();
                return;
            }
            finally
            {
                objClearLock.unlock();
            }
            closeQuietly(img);
        }
    }

    /**
     * Abandons everything in flight. {@code image_manager.cpp}:466-478.
     * <p>
     * Bumps the epoch, empties the request stack and hands every pending result
     * to the clear thread. A worker mid-decode finishes its one image — we have
     * no hook inside {@code NativeImage.read}, so EDOPro's per-scanline abort at
     * :492 does not port and per-image is as fine as it gets — then sees the
     * stale epoch and frees it. Worst-case waste is one decode per worker, under
     * 3 ms, and none of it on the render thread.
     * <p>
     * <b>Not public, on purpose.</b> EDOPro only ever calls this from
     * {@code ClearTexture}, which also wipes the maps, and calling it alone
     * would be a bug rather than a half-measure: every entry still reading
     * LOADING has just had its result condemned, so those cards would keep the
     * placeholder for ever. {@link #clearTexture()} is the pair, and it is the
     * only way in.
     */
    static void clearCachedTextures()
    {
        timestampId++;
        objClearLock.lock();
        try
        {
            picLoad.lock();
            try
            {
                for(ArrayDeque<LoadReturn> queue : loadedPics)
                {
                    toClear.addAll(queue);
                    queue.clear();
                }
                toLoad.clear();
            }
            finally
            {
                picLoad.unlock();
            }
            cvClear.signal();
        }
        finally
        {
            objClearLock.unlock();
        }
    }

    /**
     * Everything: the queues, the GPU textures and the maps.
     * {@code image_manager.cpp}:339-367.
     * <p>
     * Render thread — {@code release} disposes GPU objects. The map wipe is
     * {@link CardTextureCache#clear()}'s job because releasing a texture and
     * forgetting its status have to happen together, and it is the side that
     * knows which textures exist.
     */
    public static void clearTexture()
    {
        clearCachedTextures();
        CardTextureCache.clear();
    }

    /**
     * Forgets one card's status, so the next draw asks for it again.
     * <p>
     * <b>The single most important correctness coupling here, and EDOPro has no
     * counterpart to check it against.</b> Releasing a texture removes it from
     * {@code TextureManager.byPath}; if this map still said LOADED,
     * {@link #getTextureCard} would keep handing the ResourceLocation out, the blit
     * would miss, and MC would decode and upload it on the render thread —
     * silently restoring the exact hitch this class removes. Called from inside
     * {@link CardTextureCache}'s own critical section for that reason.
     */
    static void forget(ResourceLocation id, int sizeIndex)
    {
        Map<ResourceLocation, preloadStatus> map = tMap[sizeIndex];
        synchronized(map)
        {
            map.remove(id);
        }
    }

    /** Forgets every card's status, for the wholesale clear. */
    static void forgetAll()
    {
        for(Map<ResourceLocation, preloadStatus> map : tMap)
        {
            synchronized(map)
            {
                map.clear();
            }
        }
    }

    /** How many requests are waiting, for a diagnostic line. */
    public static int queued()
    {
        picLoad.lock();
        try
        {
            return toLoad.size();
        }
        finally
        {
            picLoad.unlock();
        }
    }

    /** How many finished decodes are waiting for the GPU, for a diagnostic line. */
    public static int decoded()
    {
        picLoad.lock();
        try
        {
            return decodedLocked();
        }
        finally
        {
            picLoad.unlock();
        }
    }

    /**
     * Everything not yet on screen, under ONE lock.
     * <p>
     * Not {@code queued() + decoded()}: those release the lock in between, so a
     * queue that is draining can read zero for an instant while work is moving
     * from one deque to the other, and the fill report would call that a
     * finished screen.
     */
    public static int pending()
    {
        picLoad.lock();
        try
        {
            return toLoad.size() + decodedLocked();
        }
        finally
        {
            picLoad.unlock();
        }
    }

    private static int decodedLocked()
    {
        int total = 0;
        for(ArrayDeque<LoadReturn> queue : loadedPics)
        {
            total += queue.size();
        }
        return total;
    }

    private static void mark(int index, ResourceLocation id, preloadStatus status)
    {
        Map<ResourceLocation, preloadStatus> map = tMap[index];
        synchronized(map)
        {
            map.put(id, status);
        }
    }

    /** Hands a decoded image the render thread will not use to the clear thread. */
    private static void abandon(LoadReturn loaded)
    {
        if(loaded.contents() == null)
        {
            return;
        }
        objClearLock.lock();
        try
        {
            toClear.addLast(loaded);
            cvClear.signal();
        }
        finally
        {
            objClearLock.unlock();
        }
    }

    /**
     * {@code NativeImage.close} null-checks and zeroes its pointer, so it is
     * idempotent and a defensive close can never double-free.
     */
    private static void closeQuietly(LoadReturn img)
    {
        if(img == null || img.contents() == null)
        {
            return;
        }
        try
        {
            img.contents().close();
        }
        catch(RuntimeException ignored)
        {
        }
    }
}
