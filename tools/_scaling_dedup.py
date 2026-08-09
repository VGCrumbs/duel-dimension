"""One scaling task per image, not one per frame.

makeScalingTask checked only whether the result was already CACHED, never
whether the work was already QUEUED. The readiness gate now asks for a task
every time it is called with a cold image -- and it is called once per visible
card per frame. A deck editor showing a hundred cards at sixty frames a second
asks six thousand times a second, and every one of those became another entry in
a queue whose poll scans the whole queue to find the highest priority. The work
never got done because the queue never stopped growing.

A key is now recorded when its task is made and released when the task finishes,
so a cold image is scheduled once and everything after that is a set lookup.
"""
import io

p = "src/main/java/de/cas_ual_ty/dueldimension/clientutil/ImageHandler.java"
s = io.open(p, encoding="utf-8").read()

old = """    public static Task makeScalingTask(String imageName, int imageSize, File raw)
    {
        final String key = ImageHandler.scaledKey(imageName, imageSize, raw);
        synchronized(SCALED_CACHE)
        {
            if(SCALED_CACHE.containsKey(key))
            {
                return null;
            }
        }
        return new ClientTask(TaskPriority.IMG_ADJUSTMENT, () ->
        {
            try
            {
                if(raw.exists())
                {
                    scaledCached(raw, key, imageSize);
                }
            }
            catch(IOException unreadable)
            {
                // A card that will not scale is one card, not a reason to
                // stop the queue; the resource pack falls back and fails
                // that texture on its own.
            }
        });
    }"""

new = """    /**
     * Images whose scaling has been asked for and not yet finished.
     * <p>
     * Without this, the readiness gate asks for the same image again on every
     * frame it is visible on -- a hundred cards at sixty frames a second is six
     * thousand requests a second -- and each one queued another task behind the
     * first. The queue's poll scans it end to end to find the highest priority,
     * so the work got slower the more of it was asked for, which is the shape
     * of a job that never finishes.
     */
    private static final java.util.Set<String> SCALING_SCHEDULED =
        java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static Task makeScalingTask(String imageName, int imageSize, File raw)
    {
        final String key = ImageHandler.scaledKey(imageName, imageSize, raw);
        synchronized(SCALED_CACHE)
        {
            if(SCALED_CACHE.containsKey(key))
            {
                return null;
            }
        }
        if(!SCALING_SCHEDULED.add(key))
        {
            return null;   // already queued, or already running
        }
        return new ClientTask(TaskPriority.IMG_ADJUSTMENT, () ->
        {
            try
            {
                if(raw.exists())
                {
                    scaledCached(raw, key, imageSize);
                }
            }
            catch(IOException unreadable)
            {
                // A card that will not scale is one card, not a reason to
                // stop the queue; the resource pack falls back and fails
                // that texture on its own.
            }
            finally
            {
                // Released even on failure: a card that would not scale this
                // time is allowed to be tried again rather than being barred
                // for the rest of the session.
                SCALING_SCHEDULED.remove(key);
            }
        });
    }"""

assert old in s, "makeScalingTask anchor"
io.open(p, "w", encoding="utf-8", newline="\n").write(s.replace(old, new, 1))
print("makeScalingTask: in-flight dedup added")
