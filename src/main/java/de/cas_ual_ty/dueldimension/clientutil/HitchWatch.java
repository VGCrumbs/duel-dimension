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

    private HitchWatch()
    {
    }

    /** Called once per client tick. */
    public static void tick()
    {
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

    private static int queuedTasks()
    {
        synchronized(TaskQueue.TASK_QUEUE)
        {
            return TaskQueue.TASK_QUEUE.size();
        }
    }
}
