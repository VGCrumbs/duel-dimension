package de.cas_ual_ty.dueldimension.clientutil;

import de.cas_ual_ty.dueldimension.task.TaskQueue;

/**
 * Notices a client tick that took far too long and says what this mod was
 * holding at the time.
 * <p>
 * Written because a stutter that happens "randomly during normal gameplay" is
 * not something reasoning about the code can settle: measuring the game at rest
 * ruled out the obvious suspects — the workers were asleep, the tick handlers
 * return immediately with no duel running, garbage collection averaged seven
 * milliseconds with no full collections, and this mod's own objects came to
 * 1.6 MB — and none of that says what happens at the moment it hitches.
 * <p>
 * So this reports from inside the hitch instead. It costs one subtraction per
 * tick and prints nothing until a tick overruns, which makes it cheap enough to
 * leave switched on and useless to leave switched off.
 */
public final class HitchWatch
{
    private static final org.apache.logging.log4j.Logger LOG =
        org.apache.logging.log4j.LogManager.getLogger();

    /**
     * A client tick is 50 ms. Anything past this is a visible stumble rather
     * than a busy tick, and low enough to catch the small ones that a player
     * feels before they are bad enough to look at a frame graph over.
     */
    private static final long THRESHOLD_MS =
        Long.getLong("dueldimension.hitchMs", 120);

    /** Two of these a second would be noise; this is a report, not a stream. */
    private static final long QUIET_MS = 2000;

    private static long lastTick;
    private static long lastReport;
    private static int reports;

    /** Pipeline requests made during the tick that just ended. */
    private static int imageRequests;

    /**
     * The thread that ticks the client, remembered so it can be sampled.
     * <p>
     * Set from {@link #tick()}, which runs on it.
     */
    private static volatile Thread client;

    /** When that thread last got through a tick, for the watchdog to compare against. */
    private static volatile long lastTickNanos;

    private static volatile boolean sampled;
    private static Thread watchdog;

    private HitchWatch()
    {
    }

    /** Called once per client tick. */
    public static void tick()
    {
        client = Thread.currentThread();
        lastTickNanos = System.nanoTime();
        sampled = false;
        startWatchdog();

        // Read and cleared EVERY tick, not only on a report, or it would count
        // from the last hitch instead of from the last tick -- so the figure
        // describes the tick that just ended, which is the one that stalled.
        imageRequests = ImageHandler.takeRequests();
        reportFilled();

        long now = System.currentTimeMillis();
        long previous = lastTick;
        lastTick = now;
        if(previous == 0)
        {
            return;
        }

        long elapsed = now - previous;
        if(elapsed < THRESHOLD_MS || now - lastReport < QUIET_MS)
        {
            return;
        }
        lastReport = now;
        reports++;

        // What this mod could plausibly have been doing. If a hitch lands with
        // all of these at rest, the cause is not here and the next place to
        // look is somewhere else entirely -- which is worth knowing too.
        //
        // "images in flight" proved to be the wrong counter, and it cost several
        // rounds of reasoning to notice: it sums the download and rescale jobs
        // on worker threads, so it drops to zero forever the moment a PNG is on
        // disk. It watches the PRODUCER side of the image pipeline. The cost was
        // on the consumer side -- TextureManager.getTexture decoding and
        // uploading inline on the render thread -- so a scroll hitch read zero
        // by construction. It is kept because a zero that is understood is
        // still evidence.
        //
        // The rest is what covers that side: which screen was open, how much
        // card art is waiting to be decoded and waiting to be uploaded, how much
        // the cache believes it is holding, and -- the point of the two figures
        // being separate -- what each HALF of a first sighting costs.
        //
        // Decode is on a worker now, so a stall with a large decode queue and a
        // healthy upload figure means the workers are behind and the render
        // thread is not the problem. The signature to look for instead is a
        // stalled frame with NO uploads recorded against it: that is a card
        // being realised somewhere other than CardImageManager, which means a
        // producer bypassed getTextureCard or a release forgot to reset the
        // status, and either restores the original hitch invisibly.
        LOG.warn("[hitch] client tick took {} ms  |  screen {}  |  queued tasks {},"
                + " images in flight {}, pipeline requests {}"
                + "  |  card art: {} queued, {} decoded, resident {} MB"
                + "  |  decode {}  |  upload {}  |  report {}",
            elapsed, screen(), queuedTasks(), ImageHandler.inFlight(),
            imageRequests,
            CardImageManager.queued(), CardImageManager.decoded(),
            CardTextureCache.residentBytes() / (1024L * 1024L),
            CardTextureCache.decodeCost(), CardTextureCache.uploadCost(), reports);
    }

    /** Whether card art was still being fetched when this tick started. */
    private static boolean wasFilling;

    /** When the current fill began, so its length can be reported. */
    private static long fillingSince;

    /**
     * Says how long a screen took to fill, once it has.
     * <p>
     * <b>Unconditional, and that is the point.</b> Everything else here only
     * prints on a stall, so a screen that fills slowly WITHOUT hitching -- which
     * is the second of the two complaints this subsystem exists to answer, and
     * the one the old ration caused -- printed nothing at all. There was no
     * record on disk of how long a collection took to come in, only of the
     * frames that stumbled.
     * <p>
     * A fill is over when the request stack and every result queue are empty. At
     * one line per fill this is a report rather than a stream: a stationary
     * screen with warm art never enters the state at all.
     */
    private static void reportFilled()
    {
        boolean filling = CardImageManager.pending() > 0;
        if(filling && !wasFilling)
        {
            fillingSince = System.currentTimeMillis();
        }
        else if(!filling && wasFilling)
        {
            LOG.info("[card-image] {} filled in {} ms  |  resident {} MB"
                    + "  |  decode {}  |  upload {}",
                screen(), System.currentTimeMillis() - fillingSince,
                CardTextureCache.residentBytes() / (1024L * 1024L),
                CardTextureCache.decodeCost(), CardTextureCache.uploadCost());
        }
        wasFilling = filling;
    }

    /**
     * The open screen's class name, or "none".
     * <p>
     * {@code Minecraft.screen} is gone in 26.2; {@code gui.screen()} is the
     * accessor, which is what ClientProxy and DuelClientState already use.
     * Worth the line: the counters say what kind of work stalled, and this says
     * where the player was standing when it did.
     */
    private static String screen()
    {
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        if(client == null || client.gui == null || client.gui.screen() == null)
        {
            return "none";
        }
        return client.gui.screen().getClass().getSimpleName();
    }

    /**
     * Watches from outside, and takes the stack while the thread is still in it.
     * <p>
     * The report above can only run once the tick has finished, by which time
     * whatever caused the stall has returned and left nothing behind. Three
     * rounds of reasoning about which of this mod's systems could be
     * responsible produced three wrong answers, so this stops reasoning and
     * takes the evidence: a daemon thread that notices the client has not
     * ticked for too long and captures its stack at that moment.
     * <p>
     * The sample is a snapshot of a running thread, so the top frame may be a
     * method that is merely quick and often called. The frames beneath it are
     * the ones worth reading — they say which system the time is being spent
     * in, which is the question.
     */
    private static synchronized void startWatchdog()
    {
        if(watchdog != null)
        {
            return;
        }
        watchdog = new Thread(() ->
        {
            while(true)
            {
                try
                {
                    Thread.sleep(20);
                }
                catch(InterruptedException stop)
                {
                    return;
                }
                Thread stuck = client;
                if(stuck == null || sampled)
                {
                    continue;
                }
                long stalled = (System.nanoTime() - lastTickNanos) / 1_000_000L;
                if(stalled < THRESHOLD_MS
                    || System.currentTimeMillis() - lastReport < QUIET_MS)
                {
                    continue;
                }
                // Once per stall: a long one would otherwise print the same
                // stack every twenty milliseconds until it ended.
                sampled = true;
                StackTraceElement[] frames = stuck.getStackTrace();
                StringBuilder where = new StringBuilder();
                for(int i = 0; i < Math.min(frames.length, 24); i++)
                {
                    where.append("\n    at ").append(frames[i]);
                }
                LOG.warn("[hitch] client thread stalled {} ms, caught in:{}", stalled, where);
            }
        }, "dueldimension-hitch-watch");
        watchdog.setDaemon(true);
        // Below the game's own threads: this must never be the reason a frame
        // is late, and it has nothing to do that cannot wait.
        watchdog.setPriority(Thread.MIN_PRIORITY);
        watchdog.start();
    }

    private static int queuedTasks()
    {
        synchronized(TaskQueue.TASK_QUEUE)
        {
            return TaskQueue.TASK_QUEUE.size();
        }
    }
}
