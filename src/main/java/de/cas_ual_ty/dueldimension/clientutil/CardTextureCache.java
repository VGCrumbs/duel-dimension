package de.cas_ual_ty.dueldimension.clientutil;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Releases card textures that have not been looked at lately, and records what
 * the two halves of a first sighting cost.
 * <p>
 * <b>Nothing was releasing them at all.</b> {@code TextureManager} keeps a plain
 * map with no budget and no eviction: {@code tick()} only visits tickable
 * textures and a reload only rebuilds {@code ReloadableTexture}s. So every
 * distinct card ever drawn stayed on the GPU for the whole session.
 * <p>
 * <b>This has no EDOPro counterpart, and that is deliberate.</b> EDOPro evicts
 * nothing — every {@code removeTexture} in {@code image_manager.cpp} is the
 * destructor, a skin swap, a cover reload, or the wholesale
 * {@code ClearTexture} — and it can afford that because its thumb is 44x64 =
 * 11 KB, so all 13,864 cards resident is about 149 MB. Ours is 128x128 square =
 * 64 KB, 5.8x, and square is the wrong shape: the card occupies u 0.199..0.801
 * and v 0.0625..0.9375, so of 16,384 texels only about 8,600 show a card and
 * half the icon's VRAM is padding. The same population at our sizes is 866 MB,
 * and our preview is 1 MB against EDOPro's 180 KB largest. This is not a parity
 * gap; it is the price of our texture sizes.
 * <p>
 * <b>Releasing is cheap here, which is what makes it safe.</b> A released
 * Identifier goes back to {@link CardImageManager.preloadStatus#NONE} in the
 * same breath, so the next draw re-requests it, a worker decodes it in a few
 * milliseconds and the next frame or two puts it back. That coupling is not
 * optional — see {@link #sweep()}.
 * <p>
 * There is no ration here any more. {@code NEW_PER_TICK}, {@code BUDGET_NANOS},
 * {@code IDLE_BUDGET_NANOS}, {@code SCROLL_QUIET_MS}, {@code noteScroll} and
 * {@code admit} are gone, and the reason is structural rather than a judgement
 * call: {@code admit} charged a median of measured BLIT samples — which
 * {@code GuiGraphicsExtractor.innerBlit} proves is decode plus upload fused —
 * against a budget reset at 20 Hz and spent per frame, so one 4 ms allowance was
 * shared across three frames at 60 fps and twelve at 240. What it rationed was
 * permission to stall the render thread, not the stall. With the decode on a
 * worker there is no multi-millisecond thing left on the render thread to
 * ration, and EDOPro's own answer — a count of uploads, {@code
 * image_manager.cpp}:372 — is enough.
 */
public final class CardTextureCache
{
    /**
     * How much card art to leave resident on the GPU, per size class.
     * <p>
     * 192 MB in total, unchanged — but <b>split</b>, because one shared pool
     * could not do the job its own comment claimed. Twenty-four previews
     * arriving is 24 MB, and in a deck editor the least recently touched thing
     * is a grid icon on a row the player scrolled past two seconds ago and is
     * about to scroll back to. Evicting it is not free now: it resets the entry
     * and the card shows a placeholder again. Four pools, one per class, is
     * EDOPro's own structure ({@code image_manager.h}:104-107) and it happens to
     * fix this.
     * <p>
     * By class: 8 MB of 64px item icons (500 of them, and the inventory bounds
     * how many can be on screen), 48 MB of 128px grid icons (750, against the
     * ~240 a deck editor holds at minimum scale, so three pages deep), 24 MB of
     * 256px info/main art (90, against about 20), and 112 MB of 512px previews
     * and field cards (112, against the 24 a pack summary holds).
     */
    private static final long[] BUDGET_BYTES =
    {
        8L * 1024L * 1024L,
        48L * 1024L * 1024L,
        24L * 1024L * 1024L,
        112L * 1024L * 1024L
    };

    /** Ticks between sweeps. Eviction is not urgent; growth is gradual. */
    private static final int SWEEP_TICKS = 20;

    /**
     * Resident card textures in least-recently-used order, one map per size
     * class. Access-ordered, so the iterator starts at the least recently used.
     */
    @SuppressWarnings("unchecked")
    static final Map<Identifier, Integer>[] RESIDENT = new Map[CardImageManager.CLASSES];

    private static final long[] residentBytes = new long[CardImageManager.CLASSES];

    /**
     * When each resident texture was last drawn.
     * <p>
     * A budget alone is not enough once the VISIBLE set is bigger than it. The
     * 256 class holds 96 textures and a maximised shop shows about 102 packs,
     * so the sweep evicted art that was still on screen, the next frame asked
     * for it again, and that eviction pushed out another -- every pack blinking
     * between its art and the placeholder for as long as the window stayed
     * large. Anything drawn recently is therefore off limits to the sweep.
     */
    private static final Map<Identifier, Long>[] TOUCHED_AT = newTouchMaps();

    @SuppressWarnings("unchecked")
    private static Map<Identifier, Long>[] newTouchMaps()
    {
        Map<Identifier, Long>[] maps = new Map[CardImageManager.CLASSES];
        for(int i = 0; i < maps.length; i++)
        {
            maps[i] = new java.util.HashMap<>();
        }
        return maps;
    }

    /**
     * Frames actually drawn, counted by {@link #beginFrame()}.
     * <p>
     * Protection is measured in FRAMES rather than milliseconds so it means
     * "currently on screen" and nothing else. It also keeps the eviction tests
     * honest: no frame is ever drawn in a unit test, so this stays 0, no entry
     * is protected, and the LRU is exercised exactly as before.
     */
    private static volatile long frame;

    /** Called once per rendered frame, before anything is drawn. */
    public static void beginFrame()
    {
        frame++;
    }

    private static int ticks;

    static
    {
        for(int i = 0; i < CardImageManager.CLASSES; i++)
        {
            RESIDENT[i] = new LinkedHashMap<>(256, 0.75F, true);
        }
    }

    private CardTextureCache()
    {
    }

    /**
     * Notes that a card texture exists and how big it is.
     * <p>
     * Called from {@link CardImageManager#refreshCachedTextures()} at the moment
     * {@code register} creates the GPU object, and from nowhere else. The old
     * cache recorded residency at admit time, before any texture existed, and
     * then returned early on "already resident" — so a warmed-but-never-drawn
     * Identifier counted against the budget for ever while occupying no VRAM,
     * and held the first-sighting gate open for exactly the cards a flick was
     * about to reach. LOADING is a distinct state from resident, which is
     * precisely why EDOPro has it ({@code image_manager.h}:43-48).
     *
     * @param size the square edge in pixels, which is what it costs on the GPU
     */
    public static void touch(Identifier id, int size)
    {
        if(id == null)
        {
            return;
        }
        int index = CardImageManager.classOf(size);
        // RGBA, one byte a channel, no mipmaps on these.
        int bytes = size * size * 4;
        Map<Identifier, Integer> resident = RESIDENT[index];
        synchronized(TOUCHED_AT[index])
        {
            TOUCHED_AT[index].put(id, frame);
        }
        synchronized(resident)
        {
            Integer had = resident.put(id, bytes);
            if(had == null)
            {
                residentBytes[index] += bytes;
            }
        }
    }

    /** Bytes of card art believed resident, for a diagnostic line. */
    public static long residentBytes()
    {
        long total = 0L;
        for(int i = 0; i < CardImageManager.CLASSES; i++)
        {
            synchronized(RESIDENT[i])
            {
                total += residentBytes[i];
            }
        }
        return total;
    }

    /** What {@link #sweep()} does to a texture it has decided to drop. */
    interface Releaser
    {
        void release(Identifier id, int sizeIndex);
    }

    /**
     * Drops the least recently used textures until every class is inside its
     * budget.
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
        if(client == null || client.getTextureManager() == null)
        {
            return;
        }
        sweep((id, sizeIndex) ->
        {
            client.getTextureManager().release(id);
            CardImageManager.forget(id, sizeIndex);
        });
    }

    /**
     * The eviction rule itself, with the disposal handed in.
     * <p>
     * Separated so it can be exercised without a GPU, and because the release
     * is the only part that needs one. <b>It runs inside the same critical
     * section as the bookkeeping on purpose:</b> releasing a texture without
     * putting its status back to NONE leaves {@code getTextureCard} handing out
     * an Identifier that {@code TextureManager} no longer holds, and the next
     * blit then decodes and uploads it inline on the render thread. Nothing in
     * the type system enforces that pairing, which is why it is stated here.
     */
    static void sweep(Releaser releaser)
    {
        for(int index = 0; index < CardImageManager.CLASSES; index++)
        {
            Map<Identifier, Integer> resident = RESIDENT[index];
            synchronized(resident)
            {
                if(residentBytes[index] <= BUDGET_BYTES[index])
                {
                    continue;
                }
                long drawnFrame = frame;
                for(Iterator<Map.Entry<Identifier, Integer>> it = resident.entrySet().iterator();
                    it.hasNext() && residentBytes[index] > BUDGET_BYTES[index];)
                {
                    Map.Entry<Identifier, Integer> eldest = it.next();
                    Long drawn;
                    synchronized(TOUCHED_AT[index])
                    {
                        drawn = TOUCHED_AT[index].get(eldest.getKey());
                    }
                    // frame == 0 means nothing has been rendered -- a unit test --
                    // so nothing is protected and the LRU behaves as written.
                    if(drawnFrame > 0 && drawn != null && drawn >= drawnFrame)
                    {
                        // On screen right now. The budget is a target, not a
                        // promise: going over it costs memory, while evicting
                        // what is being drawn costs a reload every frame and
                        // shows as flickering.
                        continue;
                    }
                    releaser.release(eldest.getKey(), index);
                    residentBytes[index] -= eldest.getValue();
                    synchronized(TOUCHED_AT[index])
                    {
                        TOUCHED_AT[index].remove(eldest.getKey());
                    }
                    it.remove();
                }
            }
        }
    }

    /** Leaving a world does not free these, so a disconnect should. */
    public static void clear()
    {
        Minecraft client = Minecraft.getInstance();
        for(int index = 0; index < CardImageManager.CLASSES; index++)
        {
            Map<Identifier, Integer> resident = RESIDENT[index];
            synchronized(resident)
            {
                for(Identifier id : resident.keySet())
                {
                    if(client != null && client.getTextureManager() != null)
                    {
                        client.getTextureManager().release(id);
                    }
                    // Same pairing as sweep, and for the same reason: a released
                    // texture whose entry still said LOADED would be handed out
                    // and decoded inline on the next draw.
                    CardImageManager.forget(id, index);
                }
                resident.clear();
                residentBytes[index] = 0L;
            }
        }
        // And then the entries that were never resident: a card still LOADING
        // when the epoch was bumped has a result that is about to be thrown
        // away, so its entry has to go back to NONE or nothing would ever ask
        // for that card again.
        CardImageManager.forgetAll();
    }

    /** The last {@link Samples#RING} timings for one texture size. */
    private static final class Samples
    {
        /** Enough to give a p99 a hundred samples to stand on, and no more. */
        private static final int RING = 128;

        private final long[] nanos = new long[RING];
        private int taken;

        void add(long ns)
        {
            nanos[taken % RING] = ns;
            taken++;
        }

        /** Sorted copy of the valid part of the ring; empty before any sample. */
        long[] sorted()
        {
            // Before the ring wraps only the first `taken` slots hold a real
            // measurement; after it wraps, all of them do.
            long[] copy = java.util.Arrays.copyOf(nanos, Math.min(taken, RING));
            java.util.Arrays.sort(copy);
            return copy;
        }
    }

    /**
     * The two halves of a first sighting, measured separately, keyed by size.
     * <p>
     * <b>This is the instrument that closes the last gap in the evidence.</b>
     * The old ring measured one fused number — the blit — and an earlier
     * estimate went wrong precisely because it assumed decode dominated within
     * it. Off the game, read plus STB decode is 0.271 ms at 128px and 2.972 ms
     * at 512px while the staging memcpy is 0.001 ms and 0.018 ms; what cannot be
     * timed without a live device is {@code vmaCreateImage} +
     * {@code vkCmdPipelineBarrier} + {@code vkCreateImageView}, which is fixed
     * per texture. {@link #uploadTook} is what measures it, and it is the one
     * number that would revise {@code MAX_IMAGES_PER_FRAME} for the 512 class.
     * <p>
     * Two rings rather than one, so neither half can hide inside the other.
     */
    private static final Map<Integer, Samples> DECODE_NANOS = new java.util.HashMap<>(4);
    private static final Map<Integer, Samples> UPLOAD_NANOS = new java.util.HashMap<>(4);

    /** Read plus decode, timed on the worker around {@code TextureContents.load}. */
    public static void decodeTook(int size, long nanos)
    {
        synchronized(DECODE_NANOS)
        {
            DECODE_NANOS.computeIfAbsent(size, s -> new Samples()).add(nanos);
        }
    }

    /** GPU texture creation and upload, timed on the render thread around {@code apply}. */
    public static void uploadTook(int size, long nanos)
    {
        synchronized(UPLOAD_NANOS)
        {
            UPLOAD_NANOS.computeIfAbsent(size, s -> new Samples()).add(nanos);
        }
    }

    /** Decode cost per size, for a diagnostic line. */
    public static String decodeCost()
    {
        return describe(DECODE_NANOS);
    }

    /**
     * Upload cost per size, for a diagnostic line.
     * <p>
     * Also the tripwire for the one silent regression this design can suffer. A
     * card realised anywhere other than {@code refreshCachedTextures} — a
     * producer that bypassed {@link CardImageManager#getTextureCard}, or a
     * release that forgot to reset the status — shows up as a stalled frame
     * with no uploads recorded against it. That is the property the old
     * {@code refusedThisTick} counter was reaching for and could not deliver,
     * because it read zero through a whole hitch by construction.
     */
    public static String uploadCost()
    {
        return describe(UPLOAD_NANOS);
    }

    private static String describe(Map<Integer, Samples> rings)
    {
        StringBuilder out = new StringBuilder();
        synchronized(rings)
        {
            for(Map.Entry<Integer, Samples> e : rings.entrySet())
            {
                long[] sorted = e.getValue().sorted();
                if(sorted.length == 0)
                {
                    continue;
                }
                out.append(out.isEmpty() ? "" : ", ")
                    .append(e.getKey()).append("px n=").append(e.getValue().taken)
                    .append(" p50=").append(ms(sorted[sorted.length / 2]))
                    .append(" p99=").append(ms(sorted[(sorted.length * 99 - 1) / 100]));
            }
        }
        return out.isEmpty() ? "none yet" : out.toString();
    }

    private static String ms(long nanos)
    {
        return String.format("%.2fms", nanos / 1_000_000D);
    }
}
