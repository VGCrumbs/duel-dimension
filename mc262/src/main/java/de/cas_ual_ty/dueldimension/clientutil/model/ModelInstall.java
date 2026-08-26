package de.cas_ual_ty.dueldimension.clientutil.model;

import de.cas_ual_ty.dueldimension.DdDatabase;
import de.cas_ual_ty.dueldimension.DuelDimension;
import de.cas_ual_ty.dueldimension.card.properties.Properties;
import de.cas_ual_ty.dueldimension.clientutil.overworld.MonsterSprites;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Fetches the monster models and puts them where they belong.
 * <p>
 * One agreement, then everything: download, check, unpack, and hand the models
 * to the cards that should be wearing them. A duellist who has said yes should
 * not then be asked to find a folder.
 * <p>
 * <b>Everything happens off the render thread except the last step.</b> A 273MB
 * download on the thread that draws frames is a frozen client for several
 * minutes; the only part that has to come back is forgetting the model cache, so
 * the models already on screen pick the new ones up.
 */
public final class ModelInstall
{
    /**
     * The file, direct.
     * <p>
     * Not the {@code /view} share link, which is an HTML page, and not the
     * {@code uc?export=download} form either -- that one answers a large file
     * with the virus-scan interstitial instead of the bytes. This host with
     * {@code confirm=t} is the form that returns the archive, verified by the
     * headers it sends back: {@code application/octet-stream},
     * {@code filename="models.zip"}, and a byte count that matches.
     */
    private static final String URL = "https://drive.usercontent.google.com/download"
        + "?id=1vXZ7Ng9eaZbhH2o915jiFnv2n7PI8KtH&export=download&confirm=t";

    /** What the archive actually is, so a wrong answer cannot be unpacked. */
    private static final String SHA256 =
        "b321e85c36720b6e846054ca04c802d2e8772b6a576a047e6002327976cb0419";

    /**
     * The human-facing download page, for a duellist who would rather fetch the
     * archive themselves than have the game do it.
     * <p>
     * Lived on {@code ModelPrompt} until the launch-time question was removed;
     * it moved here because this is the class that still exists and the one that
     * already owns the direct download link beside it.
     */
    public static final String PAGE_URL = "https://drive.google.com/file/d/1vXZ7Ng9eaZbhH2o915jiFnv2n7PI8KtH/view?usp=sharing";

    private static final long EXPECTED_BYTES = 286_355_168L;

    /** What the installer is doing, for something to draw. */
    public enum Stage
    {
        IDLE,
        DOWNLOADING,
        CHECKING,
        UNPACKING,
        ASSIGNING,
        DONE,
        FAILED
    }

    private static volatile Stage stage = Stage.IDLE;
    private static volatile long done;
    private static volatile long total = EXPECTED_BYTES;
    private static volatile String message = "";
    private static volatile boolean cancelled;
    /** Set by the worker, cleared by the client tick that acts on it. */
    private static volatile boolean finished;
    private static volatile int installed;

    private ModelInstall()
    {
    }

    public static Stage stage()
    {
        return stage;
    }

    /** 0 to 1, or -1 while the size is unknown. */
    public static float progress()
    {
        return total > 0L ? Math.min(1F, done / (float)total) : -1F;
    }

    public static long bytesDone()
    {
        return done;
    }

    public static long bytesTotal()
    {
        return total;
    }

    public static String message()
    {
        return message;
    }

    public static int installed()
    {
        return installed;
    }

    public static boolean running()
    {
        return stage == Stage.DOWNLOADING || stage == Stage.CHECKING
            || stage == Stage.UNPACKING || stage == Stage.ASSIGNING;
    }

    public static void cancel()
    {
        cancelled = true;
    }

    /** Starts, unless one is already going. */
    public static synchronized void start()
    {
        if(running())
        {
            return;
        }
        cancelled = false;
        finished = false;
        done = 0L;
        total = EXPECTED_BYTES;
        installed = 0;
        message = "";
        stage = Stage.DOWNLOADING;
        Thread worker = new Thread(ModelInstall::run, "dueldimension-model-install");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Called from the client tick: finishes on the render thread.
     * <p>
     * Forgetting the cache is the one step that must not happen on the worker.
     * The map it clears is read while frames are drawn, and baking what replaces
     * it reaches for the graphics device.
     *
     * @return whether this call was the one that completed an install
     */
    public static boolean tick()
    {
        if(!finished)
        {
            return false;
        }
        finished = false;
        MonsterModels.clear();
        return true;
    }

    private static void run()
    {
        Path folder = MonsterModels.folder();
        Path archive = folder.resolveSibling("models-download.zip");
        try
        {
            Files.createDirectories(folder);
            download(archive);
            if(cancelled)
            {
                fail("cancelled");
                Files.deleteIfExists(archive);
                return;
            }

            stage = Stage.CHECKING;
            String actual = digest(archive);
            if(!SHA256.equalsIgnoreCase(actual))
            {
                // Almost always an interstitial or a truncated read rather than
                // a corrupted file: Drive answers a quota-exceeded request with
                // an HTML page and status 200, which would unpack as nothing.
                Files.deleteIfExists(archive);
                fail("the download did not match its checksum");
                return;
            }

            stage = Stage.UNPACKING;
            int count = unpack(archive, folder);
            Files.deleteIfExists(archive);
            if(cancelled)
            {
                fail("cancelled");
                return;
            }

            stage = Stage.ASSIGNING;
            installed = count;
            int matched = assign(folder);
            DuelDimension.log("models: " + count + " installed, " + matched + " matched to cards");
            message = count + " models installed, " + matched + " matched to cards";
            stage = Stage.DONE;
            finished = true;
        }
        catch(Throwable broken)
        {
            DuelDimension.warn("model install failed: " + broken);
            fail(broken.getClass().getSimpleName() + ": " + broken.getMessage());
            try
            {
                Files.deleteIfExists(archive);
            }
            catch(Exception ignored)
            {
                // The temp file is the least of the problems at this point.
            }
        }
    }

    private static void fail(String why)
    {
        message = why;
        stage = Stage.FAILED;
    }

    /**
     * Fetches the archive, resuming a part-finished one.
     * <p>
     * A range request is what makes 273MB survivable on a connection that drops:
     * the server answers 206 and the bytes already on disk are kept. Drive
     * supports it, which was checked before this was written rather than hoped
     * for.
     */
    private static void download(Path archive) throws Exception
    {
        long already = Files.isRegularFile(archive) ? Files.size(archive) : 0L;
        if(already >= EXPECTED_BYTES)
        {
            // A complete file from a previous run; the checksum decides whether
            // it is the right one.
            done = already;
            return;
        }

        HttpURLConnection connection = (HttpURLConnection)URI.create(URL).toURL()
            .openConnection();
        connection.setConnectTimeout(20_000);
        connection.setReadTimeout(30_000);
        connection.setInstanceFollowRedirects(true);
        if(already > 0L)
        {
            connection.setRequestProperty("Range", "bytes=" + already + "-");
        }

        int status = connection.getResponseCode();
        boolean resuming = status == HttpURLConnection.HTTP_PARTIAL;
        if(status != HttpURLConnection.HTTP_OK && !resuming)
        {
            throw new IllegalStateException("the server answered " + status);
        }
        if(!resuming)
        {
            // The server ignored the range, so whatever is on disk is not a
            // prefix of what is coming.
            already = 0L;
        }

        long length = connection.getContentLengthLong();
        total = length > 0L ? already + length : EXPECTED_BYTES;
        done = already;

        try(InputStream in = connection.getInputStream();
            OutputStream out = Files.newOutputStream(archive, java.nio.file.StandardOpenOption.CREATE,
                already > 0L ? java.nio.file.StandardOpenOption.APPEND
                    : java.nio.file.StandardOpenOption.TRUNCATE_EXISTING))
        {
            byte[] buffer = new byte[1 << 16];
            int read;
            while((read = in.read(buffer)) > 0)
            {
                if(cancelled)
                {
                    return;
                }
                out.write(buffer, 0, read);
                done += read;
            }
        }
        finally
        {
            connection.disconnect();
        }
    }

    private static String digest(Path archive) throws Exception
    {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        try(InputStream in = Files.newInputStream(archive))
        {
            byte[] buffer = new byte[1 << 16];
            int read;
            while((read = in.read(buffer)) > 0)
            {
                sha.update(buffer, 0, read);
            }
        }
        StringBuilder hex = new StringBuilder();
        for(byte b : sha.digest())
        {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16));
            hex.append(Character.forDigit(b & 0xF, 16));
        }
        return hex.toString();
    }

    /**
     * Unpacks the models.
     * <p>
     * <b>Every name is checked, not trusted.</b> A zip entry can be called
     * {@code ../../anything}, and an unpacker that resolves entry names against
     * a folder writes wherever it is told to. {@link MonsterModels#plain} already
     * describes exactly the names this mod will load -- lower case, no
     * separators, no dots in a row -- so an entry that is not one is not a model
     * and is skipped rather than argued with.
     *
     * @return how many were written
     */
    private static int unpack(Path archive, Path folder) throws Exception
    {
        int count = 0;
        try(ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive)))
        {
            ZipEntry entry;
            while((entry = zip.getNextEntry()) != null)
            {
                if(cancelled)
                {
                    return count;
                }
                String name = entry.getName();
                if(entry.isDirectory() || !name.toLowerCase(Locale.ROOT).endsWith(".glb"))
                {
                    continue;
                }
                String stem = name.substring(0, name.length() - 4);
                if(!MonsterModels.plain(stem))
                {
                    DuelDimension.warn("skipping " + name + " from the archive:"
                        + " not a name this loads");
                    continue;
                }
                Files.copy(zip, folder.resolve(stem + ".glb"),
                    StandardCopyOption.REPLACE_EXISTING);
                count++;
            }
        }
        return count;
    }

    /**
     * Gives every installed model to the card of the same name.
     * <p>
     * The archive is models and nothing else -- no list saying which card each
     * belongs to -- so the match is made here, against the card database the mod
     * already has. The file names arrive in the same lower-case-and-underscores
     * form {@link MonsterModels#take} produces, so a card's name is put through
     * the same reduction and the two are compared.
     * <p>
     * <b>Only where a card has no model already.</b> A duellist who has assigned
     * one by hand has said something this should not overrule.
     *
     * @return how many cards gained a model
     */
    private static int assign(Path folder)
    {
        Map<String, Long> byName = new HashMap<>();
        for(Properties card : DdDatabase.PROPERTIES_LIST.getList())
        {
            String name = card.getName();
            if(name == null || name.isBlank())
            {
                continue;
            }
            // putIfAbsent: alternate artworks share a name, and the first is the
            // one the rest of the mod treats as the card.
            byName.putIfAbsent(reduce(name), card.getId());
        }

        int matched = 0;
        for(String model : MonsterModels.names())
        {
            Long code = byName.get(model);
            if(code == null)
            {
                continue;
            }
            MonsterSprites.Definition existing = MonsterSprites.of(code);
            if(existing != null && existing.hasModel())
            {
                continue;
            }
            MonsterSprites.Definition definition = existing != null
                ? new MonsterSprites.Definition(code, existing.body(), existing.defence(),
                    existing.wings(), existing.scale(), model, existing.animation(),
                    existing.elevation(), existing.turn(), existing.offsetX(), existing.offsetZ())
                // No sprite for this card at all: a model-only definition, which
                // the format has allowed since bodies became optional.
                : new MonsterSprites.Definition(code, null, null, null, 1F, model, null);
            MonsterSprites.put(definition);
            matched++;
        }
        if(matched > 0)
        {
            MonsterSprites.save();
        }
        return matched;
    }

    /** A name in the same shape the model files are named in. */
    private static String reduce(String name)
    {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
    }
}
