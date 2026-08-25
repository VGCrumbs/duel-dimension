package de.cas_ual_ty.dueldimension.clientutil;

import de.cas_ual_ty.dueldimension.DdDatabase;
import de.cas_ual_ty.dueldimension.DuelDimension;
import de.cas_ual_ty.dueldimension.card.properties.Properties;
import de.cas_ual_ty.dueldimension.task.TaskPriority;
import de.cas_ual_ty.dueldimension.task.TaskQueue;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Fetches the full art for every card, on request.
 * <p>
 * Nothing else fetches art ahead of time: a card's picture is downloaded and
 * scaled the first time something asks to draw it, and then kept on disk. This
 * is the opt-in alternative — the whole collection at once, up front, at the
 * cost of something on the order of a gigabyte. That is why it is a command
 * rather than something that happens to people.
 * <p>
 * Two phases, in order, because the second depends on the first: every raw is
 * downloaded, then every raw is scaled to the sizes the game draws. Scaling
 * second rather than interleaved keeps the network saturated while it is the
 * bottleneck, and the CPU saturated afterwards, instead of having each wait on
 * the other.
 */
public final class CardPreloadJob
{
    /** Where the job is up to. The order is the order they run in. */
    public enum Phase
    {
        IDLE("Idle"),
        DOWNLOADING("Downloading Raws"),
        SCALING("Scaling"),
        FINISHED("Finished");

        private final String label;

        Phase(String label)
        {
            this.label = label;
        }

        public String label()
        {
            return label;
        }
    }

    /**
     * How many downloads may be outstanding.
     * <p>
     * The player asked for this and is watching a progress bar, so finishing
     * sooner is the point — but it still shares the four workers with the
     * on-demand loads of whatever they are looking at while it runs, so one is
     * deliberately left free.
     */
    private static final int IN_FLIGHT_LIMIT = 4;

    /** Cards examined per pass. Skipping is cheap; fetching is not. */
    private static final int SCANNED_PER_TICK = 60;

    /** How often to re-measure what is on disk, in ticks. Walking the folder is not free. */
    private static final int DISK_POLL_TICKS = 40;

    private static final AtomicInteger IN_FLIGHT = new AtomicInteger();
    private static final AtomicLong FETCHED_BYTES = new AtomicLong();

    private static volatile Phase phase = Phase.IDLE;
    private static int cursor;
    private static int done;
    private static int total;
    private static int failed;
    private static long diskBytes;
    private static int diskPoll;
    /** Ticks left to keep the finished bar on screen before it fades out. */
    private static int finishedHold;

    private CardPreloadJob()
    {
    }

    public static Phase phase()
    {
        return phase;
    }

    public static boolean running()
    {
        return phase != Phase.IDLE;
    }

    /** How far through the current phase, 0 to 1. */
    public static float progress()
    {
        return total <= 0 ? 0F : Math.min(1F, done / (float)total);
    }

    public static int done()
    {
        return done;
    }

    public static int total()
    {
        return total;
    }

    public static int failed()
    {
        return failed;
    }

    /** Bytes of card art on disk at the last measurement. */
    public static long diskBytes()
    {
        return diskBytes;
    }

    /** Bytes this job has pulled down, which is what the player is spending. */
    public static long fetchedBytes()
    {
        return FETCHED_BYTES.get();
    }

    /** Whether the bar should be drawn at all. */
    public static boolean visible()
    {
        return phase != Phase.IDLE && (phase != Phase.FINISHED || finishedHold > 0);
    }

    /**
     * Starts, or restarts, the whole job.
     *
     * @return false if it was already running, so the caller can say so
     */
    public static boolean start()
    {
        if(running() && phase != Phase.FINISHED)
        {
            return false;
        }
        cursor = 0;
        done = 0;
        failed = 0;
        finishedHold = 0;
        FETCHED_BYTES.set(0L);
        total = DdDatabase.PROPERTIES_LIST.size();
        phase = Phase.DOWNLOADING;
        measureDisk();
        DuelDimension.log("Preload: started over " + total + " cards");
        return true;
    }

    /** Stops early, leaving whatever has already been fetched in place. */
    public static void stop()
    {
        if(running())
        {
            DuelDimension.log("Preload: stopped at " + done + " / " + total);
        }
        phase = Phase.IDLE;
        finishedHold = 0;
    }

    /**
     * One pass, from the client tick.
     * <p>
     * Cheap when idle: the first test returns before anything else is touched.
     */
    public static void tick()
    {
        if(phase == Phase.IDLE)
        {
            return;
        }
        if(phase == Phase.FINISHED)
        {
            if(--finishedHold <= 0)
            {
                phase = Phase.IDLE;
            }
            return;
        }

        if(++diskPoll >= DISK_POLL_TICKS)
        {
            diskPoll = 0;
            measureDisk();
        }

        if(IN_FLIGHT.get() >= IN_FLIGHT_LIMIT)
        {
            return;
        }

        int size = DdDatabase.PROPERTIES_LIST.size();
        for(int scanned = 0; scanned < SCANNED_PER_TICK; scanned++)
        {
            if(cursor >= size)
            {
                advancePhase();
                return;
            }
            Properties card = DdDatabase.PROPERTIES_LIST.getByIndex(cursor++);
            done++;
            if(card == null || card.getIsHardcoded())
            {
                continue;
            }
            boolean started = phase == Phase.DOWNLOADING ? fetchRaw(card) : scale(card);
            if(started && IN_FLIGHT.get() >= IN_FLIGHT_LIMIT)
            {
                return;
            }
        }
    }

    /** Moves to the next phase once this one's queue has drained. */
    private static void advancePhase()
    {
        if(IN_FLIGHT.get() > 0)
        {
            return;   // still finishing; the bar sits at full until it drains
        }
        if(phase == Phase.DOWNLOADING)
        {
            phase = Phase.SCALING;
            cursor = 0;
            done = 0;
            total = DdDatabase.PROPERTIES_LIST.size();
            DuelDimension.log("Preload: raws done, scaling");
            return;
        }
        measureDisk();
        phase = Phase.FINISHED;
        finishedHold = 20 * 8;
        DuelDimension.log("Preload: finished; " + human(diskBytes) + " of card art on disk");
    }

    /** Queues one card's full-resolution art if it is not already here. */
    private static boolean fetchRaw(Properties card)
    {
        String imageName = card.getImageName((byte)0);
        File raw = ImageHandler.getRawCardImageFile(imageName);
        if(raw.exists())
        {
            return false;
        }
        String url = card.getImageURL((byte)0);
        if(url == null)
        {
            return false;
        }

        IN_FLIGHT.incrementAndGet();
        TaskQueue.addTask(new ClientTask(TaskPriority.IMG_PRELOAD, () ->
        {
            try
            {
                ImageHandler.downloadRawImage(url, raw);
                FETCHED_BYTES.addAndGet(raw.length());
            }
            catch(Exception unreachable)
            {
                // One card that will not download is one card. Counted so the
                // bar can admit it rather than claiming completeness.
                failed++;
            }
            finally
            {
                IN_FLIGHT.decrementAndGet();
            }
        }));
        return true;
    }

    /**
     * Queues one card's scale to the size the game draws it at.
     * <p>
     * {@code makeMissingAdjustedTask} is the mod's own one-shot: it returns null
     * when the derived file is already on disk, writes it when it is not, and
     * that file is then what the resource pack serves for ever after. This is
     * the same work an ordinary first sighting of the card would do — the
     * command only does it for the whole collection at once.
     */
    private static boolean scale(Properties card)
    {
        String imageName = card.getImageName((byte)0);
        File raw = ImageHandler.getRawCardImageFile(imageName);
        File adjusted = ImageHandler.getAdjustedCardImageFile(imageName,
            DuelTextures.ICON_CARD_SIZE);
        if(!raw.exists() || adjusted.exists())
        {
            return false;
        }
        de.cas_ual_ty.dueldimension.task.Task adjusting = ImageHandler.makeMissingAdjustedTask(
            imageName, DuelTextures.ICON_CARD_SIZE, raw, adjusted);
        if(adjusting == null)
        {
            return false;
        }
        IN_FLIGHT.incrementAndGet();
        TaskQueue.addTask(new ClientTask(TaskPriority.IMG_PRELOAD, () ->
        {
            try
            {
                adjusting.run();
            }
            finally
            {
                IN_FLIGHT.decrementAndGet();
            }
        }));
        return true;
    }

    /**
     * Adds up what the card art is costing.
     * <p>
     * Measured rather than estimated, and only every couple of seconds: it
     * walks the image folders, which is thousands of stat calls.
     */
    private static void measureDisk()
    {
        TaskQueue.addTask(new ClientTask(TaskPriority.IMG_PRELOAD, () ->
        {
            long sum = 0L;
            sum += folderBytes(ClientProxy.cardImagesFolder);
            diskBytes = sum;
        }));
    }

    private static long folderBytes(File dir)
    {
        if(dir == null || !dir.isDirectory())
        {
            return 0L;
        }
        long sum = 0L;
        File[] children = dir.listFiles();
        if(children == null)
        {
            return 0L;
        }
        for(File child : children)
        {
            sum += child.isDirectory() ? folderBytes(child) : child.length();
        }
        return sum;
    }

    /** Bytes as something a player can read at a glance. */
    public static String human(long bytes)
    {
        if(bytes >= 1024L * 1024L * 1024L)
        {
            return String.format("%.1f GB", bytes / (1024D * 1024D * 1024D));
        }
        if(bytes >= 1024L * 1024L)
        {
            return String.format("%.0f MB", bytes / (1024D * 1024D));
        }
        return String.format("%.0f KB", Math.max(1D, bytes / 1024D));
    }
}
