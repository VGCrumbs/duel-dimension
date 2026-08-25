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
 *
 * <h2>How to read what it prints</h2>
 *
 * Two kinds of line, and they mean different things:
 *
 * <ul>
 * <li><b>{@code client tick took N ms}</b> — a duration. The tick that just
 * ended ran for N milliseconds. This one is a measurement.</li>
 * <li><b>{@code client thread still in this tick at N ms (sample k)}</b> — a
 * position, not a duration. It says the tick had been running N milliseconds
 * when this stack was taken. It does <b>not</b> say the frame at the top cost N
 * milliseconds, or cost anything at all.</li>
 * </ul>
 *
 * <b>That distinction is not pedantry — misreading it cost a whole
 * investigation.</b> A stall was diagnosed as "1178 ms inside another mod's
 * thread-pool resize" on the strength of one sample; the resize was in fact a
 * sub-second sleep the thread merely happened to be in when the stack was
 * taken, and the real cost was elsewhere in the same tick. A single sample can
 * only tell you the thread was <em>somewhere</em> at <em>some instant</em>.
 * Several samples across one stall, which is what this now takes, tell you
 * where the time went; the {@code stacks} figure on the tick line says how many
 * there were to look at, and zero means nobody looked inside.
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

    /**
     * A stall this long is never noise, so it is never rate-limited.
     * <p>
     * <b>This exists because the rationing hid the thing worth seeing.</b> The
     * quiet window applies between one report and the next, and a world load
     * produces a burst — so the small stall at the front of the burst claimed
     * the window and the multi-second one behind it printed nothing. Measured
     * across the archived logs, the single largest stall of a world load was
     * suppressed in 62 of 76 recorded loads. The rate limiter is for a stream of
     * 130 ms stumbles; a four-second freeze is the report.
     */
    private static final long LOUD_MS = 1000;

    /**
     * How many stacks to take from one stall before letting it be.
     * <p>
     * Reached only by a stall of about thirty seconds, given the doubling
     * below. A stall that long has bigger problems than log volume.
     */
    private static final int MAX_SAMPLES = 8;

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

    /**
     * How many stacks have been taken from the stall in progress.
     * <p>
     * Was a boolean, and that was the defect: one stack from a four-second
     * freeze, taken at whatever instant the rate limiter happened to allow,
     * names whichever method the thread was passing through at that moment. It
     * reads like an accusation and is barely evidence. Several stacks across the
     * stall show where the time actually went.
     */
    private static volatile int samples;

    /**
     * How far into the stall the next stack is due, in milliseconds.
     * <p>
     * Doubles after each one — 120, 240, 480, 960, 1920 — so a stumble costs a
     * single line and a long freeze is sampled through its whole length without
     * the log filling up. The cost is the same one subtraction per poll either
     * way.
     */
    private static volatile long nextSampleAt = THRESHOLD_MS;

    /** When a stack was last taken, for rationing across stalls (not within one). */
    private static volatile long lastSample;

    private static Thread watchdog;

    private HitchWatch()
    {
    }

    /** Called once per client tick. */
    public static void tick()
    {
        client = Thread.currentThread();
        lastTickNanos = System.nanoTime();
        // Taken BEFORE the reset: the report below describes the tick that just
        // ended, and so must the count of stacks taken during it. Reading the
        // field after clearing it reports zero every time.
        int stacks = samples;
        samples = 0;
        nextSampleAt = THRESHOLD_MS;
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
        if(elapsed < THRESHOLD_MS)
        {
            return;
        }
        // A big one always prints. Rationing exists so a stream of small
        // stumbles does not bury the log, and a four-second freeze is not that
        // -- it is the thing being looked for, and it was being dropped
        // whenever a small stall had claimed the window just before it.
        if(elapsed < LOUD_MS && now - lastReport < QUIET_MS)
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
        //
        // "stacks" joins this line to the watchdog's. A tick reported with zero
        // stacks is a stall nobody looked inside — which is itself worth
        // knowing, and used to be indistinguishable from a stall whose stack
        // simply was not printed.
        LOG.warn("[hitch] client tick took {} ms  |  {} stacks  |  screen {}"
                + "  |  queued tasks {}, images in flight {}, pipeline requests {}"
                + "  |  card art: {} queued, {} decoded, resident {} MB"
                + "  |  decode {}  |  upload {}  |  report {}",
            elapsed, stacks, screen(), queuedTasks(), ImageHandler.inFlight(),
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
                if(stuck == null || samples >= MAX_SAMPLES)
                {
                    continue;
                }
                long stalled = (System.nanoTime() - lastTickNanos) / 1_000_000L;
                if(stalled < nextSampleAt)
                {
                    continue;
                }
                // The ration applies to the FIRST stack of a stall and to
                // nothing else. It used to be checked against lastReport, which
                // is set when a TICK report prints -- a different event
                // entirely -- so the sample landed at "whenever the last report
                // happened plus two seconds", an instant with no relationship to
                // the stall being sampled. Later stacks are never rationed:
                // this stall has already been judged worth looking at, and the
                // whole point is to see it change.
                if(samples == 0 && System.currentTimeMillis() - lastSample < QUIET_MS)
                {
                    continue;
                }
                samples++;
                lastSample = System.currentTimeMillis();
                nextSampleAt = stalled * 2;

                StackTraceElement[] frames = stuck.getStackTrace();
                StringBuilder where = new StringBuilder();
                for(int i = 0; i < Math.min(frames.length, 24); i++)
                {
                    where.append("\n    at ").append(frames[i]);
                }
                // "at {} ms" and not "for {} ms". The number is how long the
                // tick has been running when the stack was taken, NOT time
                // spent in the frame at the top of it -- reading it the second
                // way turns a thread that was merely passing through into the
                // culprit, and that misreading has already cost one whole
                // investigation.
                LOG.warn("[hitch] client thread still in this tick at {} ms"
                    + " (sample {}), passing through:{}", stalled, samples, where);
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
