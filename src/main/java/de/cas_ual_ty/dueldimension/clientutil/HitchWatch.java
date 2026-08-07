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
        LOG.warn("[hitch] client tick took {} ms  |  queued tasks {}, images in flight {},"
                + " textures loaded info {} / main {}  |  report {}",
            elapsed, queuedTasks(), ImageHandler.inFlight(),
            CardRenderUtil.infoTextureBinder == null ? -1 : CardRenderUtil.infoTextureBinder.loaded(),
            CardRenderUtil.mainTextureBinder == null ? -1 : CardRenderUtil.mainTextureBinder.loaded(),
            reports);
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
