package de.cas_ual_ty.dueldimension;

import com.google.common.io.Files;
import com.google.gson.*;
import de.cas_ual_ty.dueldimension.card.CustomCards;
import de.cas_ual_ty.dueldimension.card.properties.Properties;
import de.cas_ual_ty.dueldimension.rarity.RarityEntry;
import de.cas_ual_ty.dueldimension.set.CardSet;
import de.cas_ual_ty.dueldimension.set.Distribution;
import de.cas_ual_ty.dueldimension.util.DNCList;
import de.cas_ual_ty.dueldimension.util.JsonKeys;
import de.cas_ual_ty.dueldimension.util.DdIOUtil;
import de.cas_ual_ty.dueldimension.util.DdUtil;

import java.io.*;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class DdDatabase
{
    public static final DNCList<Long, Properties> PROPERTIES_LIST = new DNCList<>((p) -> p.getId(), Long::compare);
    private static int cardsVariantsCount = -1;
    
    public static final HashSet<String> FOUND_RARITIES = new HashSet<>();
    
    public static final DNCList<String, RarityEntry> RARITIES_LIST = new DNCList<>((r) -> r.rarity, (s1, s2) -> s1.compareTo(s2));
    public static final DNCList<String, Distribution> DISTRIBUTIONS_LIST = new DNCList<>((d) -> d.name, (s1, s2) -> s1.compareTo(s2));
    public static final DNCList<String, CardSet> SETS_LIST = new DNCList<>((s) -> s.code, (s1, s2) -> s1.compareTo(s2));
    
    public static final JsonParser JSON_PARSER = new JsonParser();
    public static final SimpleDateFormat SET_DATE_PARSER = new SimpleDateFormat("dd-MM-yyyy");
    
    public static boolean databaseReady = false;

    /**
     * Why there is no usable card database, or null when there is one.
     * <p>
     * The download used to fail by returning: a first run with no network
     * reached neither {@link #readFiles()} nor a log line anyone would read,
     * and the game then ran with an empty card list where every card in the
     * world is an unknown card. This is what a player is told instead, in chat
     * on join and by {@code /dueldimension database}, and it is written in
     * terms of what they can do about it.
     */
    private static volatile String problem = null;

    /** @return why the database is unusable, or null when it is fine */
    public static String problem()
    {
        return DdDatabase.problem;
    }

    /**
     * How many cards were actually read. Zero means the list holds nothing but
     * the dummy, which is the state that looks like the mod working and is not.
     */
    public static int cardCount()
    {
        return DdDatabase.PROPERTIES_LIST.getList().size();
    }

    public static JsonObject localDbInfo = null;
    public static int localVersionIteration = Integer.MIN_VALUE;
    public static String localVersionId = null;
    
    public static JsonObject remoteDbInfo = null;
    public static int remoteVersionIteration = Integer.MIN_VALUE;
    public static String remoteDownloadLink = null;
    public static String remoteVersionId = null;
    
    /**
     * Gets the card database ready, downloading it if this installation has
     * none yet.
     * <p>
     * <b>Every path through here ends at {@link #readFiles()}.</b> It used to
     * return early when the source could not be reached, which skipped the
     * dummy card, the custom cards and the bundled extras as well -- so a first
     * run with no network was left worse off than one with an empty database,
     * and said so only in a stack trace. Now a failure is recorded in
     * {@link #problem()}, explained in the log in terms of what to do about it,
     * and shown to the player; the mod still loads, because refusing to load is
     * not a better answer than loading with no cards.
     */
    public static void initDatabase()
    {
        DdDatabase.problem = null;

        // The database this jar carries, before anything asks whether there is
        // one. Order is the whole point: `firstRun` below is answered by whether
        // ydm_db exists, and the download it triggers is what a bundled database
        // exists to make unnecessary -- so the bundle has to be on disk before
        // the question is put, not while it is being answered.
        //
        // It writes only files that are not already there. A player who has been
        // playing for months keeps every byte of theirs; see
        // EngineBundle.installCardDatabase.
        DuelDimension.log("Card database bundle: "
            + de.cas_ual_ty.dueldimension.ocg.session.EngineBundle.installCardDatabase(
                DuelDimension.mainFolder.toPath()));

        boolean firstRun = !DuelDimension.mainFolder.exists();

        if(!DuelDimension.dbSourceUrl.isEmpty())
        {
            boolean downloadDB = false;
            boolean remoteRead = false;

            if(firstRun)
            {
                DuelDimension.log("No card database at " + DuelDimension.mainFolder.getAbsolutePath()
                    + "; fetching one. This is a one-off download of the whole database"
                    + " (tens of megabytes) and the game waits for it.");
                downloadDB = true;
            }
            else
            {
                try
                {
                    if(DdDatabase.readLocalVersion())
                    {
                        remoteRead = true;
                        if(DdDatabase.readRemoteVersion() && (DdDatabase.localVersionIteration < DdDatabase.remoteVersionIteration || !DdDatabase.localVersionId.equals(DdDatabase.remoteVersionId)))
                        {
                            DuelDimension.log("New database version: " + DdDatabase.remoteVersionIteration + " (Old version: " + DdDatabase.localVersionIteration + ")");
                            downloadDB = true;
                        }
                    }
                    else
                    {
                        downloadDB = true;
                    }
                }
                catch(Exception e)
                {
                    downloadDB = true;
                    // Said, not thrown at the log. Every one of these used to be
                    // a bare printStackTrace, so the first thing a player saw on
                    // a machine with no connection was a stack trace with no
                    // sentence attached -- which reads as a crash and is not one.
                    DuelDimension.log("Failed assessing if a new database needs to be downloaded"
                        + " (" + e + "). Doing it anyways...");
                }
            }
            
            if(downloadDB)
            {
                if(!remoteRead)
                {
                    DdDatabase.readRemoteVersion();
                }
                
                if(DdDatabase.remoteDownloadLink == null)
                {
                    // No return: the dummy card, the custom cards and the
                    // bundled extras still have to be registered, and an
                    // existing database from a previous run is still perfectly
                    // readable.
                    DdDatabase.problem = "the card database index at " + DuelDimension.dbSourceUrl
                        + " could not be read (no connection, or the source has moved)";
                }
                else
                {
                    try
                    {
                        DdDatabase.downloadDatabase();

                        // A download REPLACES ydm_db -- the old folder is
                        // deleted and the archive's is moved into its place, as
                        // upstream's own README warns ("The ydm_db folder
                        // automatically gets deleted entirely whenever a new
                        // database is downloaded"). Everything the bundle put
                        // there went with it, including the 3,106 cards this
                        // fork adds that the downloaded database has never heard
                        // of. So it is put back, now, in the same run rather
                        // than at the next launch -- and still without
                        // overwriting one file the download just wrote.
                        DuelDimension.log("Card database bundle, after download: "
                            + de.cas_ual_ty.dueldimension.ocg.session.EngineBundle
                                .installCardDatabase(DuelDimension.mainFolder.toPath()));
                    }
                    catch(IOException e)
                    {
                        DdDatabase.problem = "the card database download from "
                            + DdDatabase.remoteDownloadLink + " failed (" + e + ")";
                        DuelDimension.warn("Failed downloading database: " + e);
                    }
                }
            }
        }
        else if(firstRun)
        {
            DdDatabase.problem = "no download source is configured (dbSourceUrl is empty in"
                + " config/" + DuelDimension.MOD_ID + ".json) and there is no database at "
                + DuelDimension.mainFolder.getAbsolutePath();
        }

        DdDatabase.readFiles();
        DdDatabase.reportProblem();
    }

    /**
     * Says once, loudly, what a player would otherwise only discover by finding
     * every card in the game replaced with an unknown one.
     */
    private static void reportProblem()
    {
        if(DdDatabase.problem == null)
        {
            return;
        }

        DuelDimension.warn("+---------------------------------------------------------------");
        DuelDimension.warn("| Duel Dimension has no card database: " + DdDatabase.problem + ".");
        DuelDimension.warn("| Until that is fixed every card in the game is an unknown card,");
        DuelDimension.warn("| and no duel can be started.");
        DuelDimension.warn("| Either restart with a working connection -- the mod downloads");
        DuelDimension.warn("| the database by itself from " + DuelDimension.dbSourceUrl);
        DuelDimension.warn("| -- or unpack a copy of ydm_db into");
        DuelDimension.warn("|   " + DuelDimension.mainFolder.getAbsolutePath());
        DuelDimension.warn("+---------------------------------------------------------------");
    }
    
    private static boolean readLocalVersion()
    {
        try
        {
            File version = new File(DuelDimension.mainFolder, "db.json");
            if(!version.exists())
            {
                DuelDimension.log("Local db.json file does not exist: " + version.getAbsolutePath());
                return false;
            }
            
            DuelDimension.log("Reading local db.json file: " + version.getAbsolutePath());
            DdDatabase.localDbInfo = DdIOUtil.parseJsonFile(version).getAsJsonObject();
            DdDatabase.localVersionIteration = DdDatabase.localDbInfo.get(JsonKeys.VERSION_ITERATION).getAsInt();
            DdDatabase.localVersionId = DdDatabase.localDbInfo.get(JsonKeys.DB_ID).getAsString();
            
            return true;
        }
        catch(IOException e)
        {
            DuelDimension.log("Cannot read local db.json file (" + e + "). Redownloading database...");
        }
        catch(JsonParseException e)
        {
            DuelDimension.log("Cannot parse local db.json file (" + e + "). Redownloading database...");
        }
        
        return false;
    }
    
    private static boolean readRemoteVersion()
    {
        try
        {
            DuelDimension.log("Reading remote db.json file: " + DuelDimension.dbSourceUrl);
            URL url = new URL(DuelDimension.dbSourceUrl);
            try(InputStream in = DdIOUtil.urlInputStream(url))
            {
                DdDatabase.remoteDbInfo = DdIOUtil.parseJsonFile(new InputStreamReader(in)).getAsJsonObject();
                DdDatabase.remoteVersionIteration = DdDatabase.remoteDbInfo.get(JsonKeys.VERSION_ITERATION).getAsInt();
                DdDatabase.remoteDownloadLink = DdDatabase.remoteDbInfo.get(JsonKeys.DOWNLOAD_LINK).getAsString();
                DdDatabase.remoteVersionId = DdDatabase.remoteDbInfo.get(JsonKeys.DB_ID).getAsString();
            }
            
            return true;
        }
        catch(IOException e)
        {
            // The ordinary case on a machine with no connection, which is not an
            // error worth a stack trace: initDatabase() turns it into the boxed
            // warning that tells a player what to do about it.
            DuelDimension.log("Cannot read remote db.json file (" + e
                + "). Staying on current database...");
        }
        catch(JsonParseException | NullPointerException e)
        {
            DuelDimension.log("Cannot parse remote db.json file (" + e
                + "). Staying on current database...");
        }
        
        return false;
    }
    
    private static void readFiles()
    {
        DuelDimension.log("Reading database!");
        DdDatabase.databaseReady = true;
        
        DdDatabase.PROPERTIES_LIST.add(Properties.DUMMY);
        DdDatabase.SETS_LIST.add(CardSet.DUMMY);

        CustomCards.createAndRegisterEverything();

        // "Is there a database?" has to be answered BEFORE the bundled extras
        // are installed, and it used to be answered after.
        //
        // installBundledExtras() writes into ydm_db and mkdirs its way there,
        // so by the time the two checks below ran on a clean machine whose
        // download had failed, ydm_db AND ydm_db/cards both existed -- created
        // moments earlier by the extras themselves. Neither branch could fire.
        // The log line about the main folder not existing was unreachable, and
        // the game came up reporting a database while holding 113 cards: the
        // dummy, eleven custom ones, and the 101 extras that had just made the
        // folder they were being tested for.
        boolean haveMainFolder = DuelDimension.mainFolder.isDirectory();
        boolean haveCardsFolder = DuelDimension.cardsFolder.isDirectory();

        if(!haveMainFolder)
        {
            DuelDimension.log(DuelDimension.mainFolder.getAbsolutePath() + " (main folder) does not exist! Aborting...");
            if(DdDatabase.problem == null)
            {
                DdDatabase.problem = "there is no database at "
                    + DuelDimension.mainFolder.getAbsolutePath();
            }
            return;
        }

        if(!haveCardsFolder)
        {
            DuelDimension.log(DuelDimension.cardsFolder.getAbsolutePath() + " (cards folder) does not exist! Aborting...");
            if(DdDatabase.problem == null)
            {
                DdDatabase.problem = "the database at " + DuelDimension.mainFolder.getAbsolutePath()
                    + " has no cards folder, so it is incomplete";
            }
            return;
        }

        // Only now, with a real database to add them to. Extras on their own are
        // not a database; they are 101 cards that make one look present.
        DdDatabase.installBundledExtras();

        DdDatabase.readCards(DuelDimension.cardsFolder);
        
        if(DuelDimension.distributionsFolder.exists())
        {
            DdDatabase.readDistributions(DuelDimension.distributionsFolder);
        }
        else
        {
            DuelDimension.log(DuelDimension.distributionsFolder.getAbsolutePath() + " (distributions folder) does not exist! Skipping...");
        }
        
        if(DuelDimension.setsFolder.exists())
        {
            DdDatabase.readSets(DuelDimension.setsFolder);
        }
        else
        {
            DuelDimension.log(DuelDimension.setsFolder.getAbsolutePath() + " (sets folder) does not exist! Skipping...");
        }
        
        if(DuelDimension.raritiesFolder.exists())
        {
            DdDatabase.readRarities(DuelDimension.raritiesFolder);
        }
        else
        {
            DuelDimension.log(DuelDimension.raritiesFolder.getAbsolutePath() + " (rarities folder) does not exist! Skipping...");
        }
        
        DdDatabase.postDBInit();
    }
    
    /**
     * Where the bundled extras live inside the mod, and the list of them.
     * <p>
     * A jar cannot have its directories listed through the classloader, so the
     * contents are named in an index written alongside them by
     * {@code tools/import_set.py}.
     */
    private static final String EXTRAS_ROOT = "/ydm_extras/";
    private static final String EXTRAS_INDEX = EXTRAS_ROOT + "index.json";

    /**
     * Copies the cards and sets shipped inside the mod into the live database.
     * <p>
     * The database proper is downloaded per installation and stops in August
     * 2021, so anything printed since is added here. Doing it at load time
     * rather than by running a script matters for multiplayer: a card is
     * carried between client and server as an id, and an id the other side
     * cannot find silently becomes a dummy card. A server whose database was
     * assembled differently from its players' therefore does not fail loudly —
     * it quietly turns their cards into blanks. Shipping the extras in the jar
     * is what makes every installation agree.
     * <p>
     * Existing files are left alone unless their contents differ, so this is
     * cheap on every boot after the first and still corrects a stale copy.
     */
    private static void installBundledExtras()
    {
        String index;
        try(InputStream stream = DdDatabase.class.getResourceAsStream(EXTRAS_INDEX))
        {
            if(stream == null)
            {
                // No extras bundled is a perfectly valid build.
                return;
            }
            index = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        catch(IOException e)
        {
            DuelDimension.log("Could not read the bundled extras index: " + e.getMessage());
            return;
        }

        JsonArray entries;
        try
        {
            entries = JSON_PARSER.parse(index).getAsJsonArray();
        }
        catch(RuntimeException notJson)
        {
            DuelDimension.log("Bundled extras index is not a JSON array; skipping extras.");
            return;
        }

        int written = 0;
        for(JsonElement entry : entries)
        {
            String path = entry.getAsString();
            // The index is written by us, but it names a file path, so a path
            // that climbs out of the database folder is refused rather than
            // followed.
            if(path.contains("..") || path.startsWith("/") || path.contains("\\"))
            {
                DuelDimension.log("Refusing suspicious bundled extras path: " + path);
                continue;
            }

            try(InputStream stream = DdDatabase.class.getResourceAsStream(EXTRAS_ROOT + path))
            {
                if(stream == null)
                {
                    DuelDimension.log("Bundled extras index names a missing file: " + path);
                    continue;
                }

                byte[] bundled = stream.readAllBytes();
                File target = new File(DuelDimension.mainFolder, path);
                if(target.isFile() && java.util.Arrays.equals(bundled,
                    java.nio.file.Files.readAllBytes(target.toPath())))
                {
                    continue;
                }

                File parent = target.getParentFile();
                if(parent != null && !parent.exists() && !parent.mkdirs())
                {
                    DuelDimension.log("Could not create " + parent.getAbsolutePath() + " for bundled extras.");
                    continue;
                }

                java.nio.file.Files.write(target.toPath(), bundled);
                written++;
            }
            catch(IOException e)
            {
                DuelDimension.log("Could not install bundled extra " + path + ": " + e.getMessage());
            }
        }

        if(written > 0)
        {
            DuelDimension.log("Installed " + written + " bundled database files into "
                + DuelDimension.mainFolder.getAbsolutePath());
        }
    }

    /**
     * Fetches the database archive and swaps it in.
     * <p>
     * The order is the point. The old database used to be deleted FIRST, so a
     * download that failed halfway through an update left an installation with
     * no cards at all where a moment earlier it had a working -- merely stale --
     * copy. Nothing existing is touched here until the new tree is unpacked and
     * found to contain a database, and the scratch files are cleaned up however
     * this ends rather than left behind as tens of megabytes nobody knows the
     * name of.
     * <p>
     * The scratch lives in the game directory beside {@code ydm_db}, not in the
     * process working directory, so it lands on the same disk as its
     * destination -- which is what makes the final move a rename rather than a
     * copy of fourteen thousand files.
     */
    public static void downloadDatabase() throws IOException
    {
        DuelDimension.log("Downloading database from " + DdDatabase.remoteDownloadLink);

        URL url = new URL(DdDatabase.remoteDownloadLink);

        File zip = de.cas_ual_ty.dueldimension.util.GameDir.file("ydm_db_temp.zip");
        File temp = de.cas_ual_ty.dueldimension.util.GameDir.file("ydm_db_temp");

        // Leftovers from an attempt that was interrupted. deleteRecursively,
        // because File.delete() on a non-empty directory fails and returns a
        // false nobody checked -- so the previous attempt's contents used to
        // survive into this one and get moved into place alongside the new.
        DdIOUtil.deleteRecursively(zip);
        DdIOUtil.deleteRecursively(temp);

        if(!temp.mkdirs())
        {
            throw new IOException("Could not create " + temp.getAbsolutePath());
        }

        try
        {
            DdIOUtil.downloadFile(url, zip);
            DdDatabase.unpack(zip, temp);

            // The archive is a repository export: one top-level folder holding
            // the ydm_db the mod actually wants.
            File[] unpacked = new File[1];
            DdIOUtil.doForDeepSearched(temp,
                (file) -> file.isDirectory() && file.getName().equals(DuelDimension.mainFolder.getName()),
                (file) -> unpacked[0] = file);

            if(unpacked[0] == null)
            {
                throw new IOException("the archive contained no "
                    + DuelDimension.mainFolder.getName() + " folder");
            }

            // Only now is anything that already worked touched.
            if(DuelDimension.mainFolder.exists())
            {
                DdIOUtil.deleteRecursively(DuelDimension.mainFolder);
            }
            Files.move(unpacked[0], DuelDimension.mainFolder);
        }
        finally
        {
            DdIOUtil.deleteRecursively(zip);
            DdIOUtil.deleteRecursively(temp);
        }

        //finally recreate the db.json file
        File dbJson = new File(DuelDimension.mainFolder, "db.json");

        if(dbJson.exists())
        {
            dbJson.delete();
        }
        dbJson.createNewFile();

        try(FileWriter fw = new FileWriter(dbJson))
        {
            DdIOUtil.GSON.toJson(DdDatabase.remoteDbInfo, fw);
            fw.flush();
        }

        DuelDimension.log("Finished downloading database!");
    }

    /**
     * Unpacks an archive into a folder, and refuses to write outside it.
     * <p>
     * The entry names in a zip are attacker-chosen -- the archive comes from a
     * URL the player can edit in the config -- and an entry called
     * {@code ../../mods/evil.jar} used to be written exactly there, because the
     * destination was built with {@code new File(temp, entry.getName())} and
     * never checked. The bundled-extras path a few lines up has always refused
     * such names; this is the same refusal for the downloaded one.
     * <p>
     * Parent directories are created per entry rather than trusting the archive
     * to list every folder before the files inside it, which is not something a
     * zip is required to do.
     */
    private static void unpack(File zip, File into) throws IOException
    {
        java.nio.file.Path root = into.toPath().toAbsolutePath().normalize();

        try(ZipInputStream zipIn = new ZipInputStream(
            new BufferedInputStream(new FileInputStream(zip))))
        {
            byte[] buffer = new byte[8192];

            for(ZipEntry entry = zipIn.getNextEntry(); entry != null; entry = zipIn.getNextEntry())
            {
                java.nio.file.Path target = root.resolve(entry.getName()).normalize();
                if(!target.startsWith(root))
                {
                    throw new IOException("archive entry escapes the unpack folder: "
                        + entry.getName());
                }

                if(entry.isDirectory())
                {
                    java.nio.file.Files.createDirectories(target);
                    continue;
                }

                java.nio.file.Files.createDirectories(target.getParent());

                try(OutputStream out = new BufferedOutputStream(
                    java.nio.file.Files.newOutputStream(target)))
                {
                    int length;
                    while((length = zipIn.read(buffer)) > 0)
                    {
                        out.write(buffer, 0, length);
                    }
                }
            }
        }
    }
    
    /**
     * Adds any artwork supplied locally for this card.
     * <p>
     * <b>Why this has to exist.</b> The card database's source keys an artwork by
     * PASSCODE — Dark Magician's nine arts are nine consecutive passcodes — so an
     * alternate printing only has an image there if Konami gave it its own
     * passcode. Modern full arts do not: "The Dark Magicians" is printed seven
     * times in RA04-EN054, once per rarity, and every one of them points at the
     * single artwork 50237654. The source knows the printing exists and knows its
     * rarity; it has nowhere to put a second picture. No query can retrieve what
     * the model cannot express, so the images are supplied here instead.
     * <p>
     * Drop files in {@code ydm_db/alt_art/&lt;passcode&gt;/}, named so they sort into
     * the order you want them offered. They are appended after the printed art,
     * so index 0 stays what it always was and no saved deck changes meaning.
     * <p>
     * They join the pipeline as {@code file:} URLs rather than by a separate
     * path: {@code ImageHandler.downloadRawImage} builds a {@code java.net.URL}
     * and streams it, and a file URL streams like any other — so a local artwork
     * is cached, scaled and served exactly as a downloaded one, with nothing else
     * needing to know the difference.
     */
    private static void addLocalArtwork(Properties card)
    {
        File folder = new File(new File(DuelDimension.mainFolder, "alt_art"),
            Long.toString(card.getId()));
        if(!folder.isDirectory())
        {
            return;
        }
        File[] files = folder.listFiles((dir, name) ->
        {
            String lower = name.toLowerCase(java.util.Locale.ROOT);
            return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg");
        });
        if(files == null || files.length == 0)
        {
            return;
        }
        java.util.Arrays.sort(files, java.util.Comparator.comparing(File::getName));

        String[] urls = new String[files.length];
        for(int i = 0; i < files.length; i++)
        {
            urls[i] = files[i].toURI().toString();
        }
        card.addArtwork(urls);
    }

    private static void readCards(File cardsFolder)
    {
        DuelDimension.log("Reading card files from: " + cardsFolder.getAbsolutePath());
        
        File[] cardsFiles = cardsFolder.listFiles(DdIOUtil.JSON_FILTER);
        DdDatabase.PROPERTIES_LIST.ensureExtraCapacity(cardsFiles.length);
        
        JsonObject j;
        Properties p;
        
        for(File cardFile : cardsFiles)
        {
            try
            {
                j = DdIOUtil.parseJsonFile(cardFile).getAsJsonObject();
                p = DdUtil.buildProperties(j);
                DdDatabase.addLocalArtwork(p);
                p.addInformation(new LinkedList<>()); // this throws in case of wrong information
                DdDatabase.PROPERTIES_LIST.add(p);
            }
            catch(NullPointerException | IllegalArgumentException | IllegalStateException e)
            {
                DuelDimension.log("Failed reading card: " + cardFile.getAbsolutePath());
                e.printStackTrace();
            }
            catch(JsonSyntaxException e)
            {
                DuelDimension.log("Failed reading card: " + cardFile.getAbsolutePath());
                e.printStackTrace();
            }
            catch(JsonIOException | FileNotFoundException e)
            {
                DuelDimension.log("Failed reading card: " + cardFile.getAbsolutePath());
                e.printStackTrace();
            }
            catch(IOException e)
            {
                DuelDimension.log("Failed reading card: " + cardFile.getAbsolutePath());
                e.printStackTrace();
            }
            catch(Exception e)
            {
                DuelDimension.log("Failed reading card: " + cardFile.getAbsolutePath());
                throw e;
            }
        }
        
        DdDatabase.PROPERTIES_LIST.sort();
        
        DuelDimension.log("Done reading card files!");
    }
    
    private static void readDistributions(File distributionsFolder)
    {
        DuelDimension.log("Reading distribution files from: " + distributionsFolder.getAbsolutePath());
        
        File[] distributionsFiles = distributionsFolder.listFiles(DdIOUtil.JSON_FILTER);
        DdDatabase.DISTRIBUTIONS_LIST.ensureExtraCapacity(distributionsFiles.length);
        
        JsonObject j;
        Distribution d;
        
        for(File distributionFile : distributionsFiles)
        {
            try
            {
                j = DdIOUtil.parseJsonFile(distributionFile).getAsJsonObject();
                d = new Distribution(j);
                DdDatabase.DISTRIBUTIONS_LIST.add(d);
            }
            catch(NullPointerException | IllegalArgumentException | IllegalStateException e)
            {
                DuelDimension.log("Failed reading distribution: " + distributionFile.getAbsolutePath());
                e.printStackTrace();
            }
            catch(JsonSyntaxException e)
            {
                DuelDimension.log("Failed reading distribution: " + distributionFile.getAbsolutePath());
                e.printStackTrace();
            }
            catch(JsonIOException | FileNotFoundException e)
            {
                DuelDimension.log("Failed reading distribution: " + distributionFile.getAbsolutePath());
                e.printStackTrace();
            }
            catch(IOException e)
            {
                DuelDimension.log("Failed reading distribution: " + distributionFile.getAbsolutePath());
                e.printStackTrace();
            }
            catch(Exception e)
            {
                DuelDimension.log("Failed reading distribution: " + distributionFile.getAbsolutePath());
                throw e;
            }
        }
        
        DdDatabase.DISTRIBUTIONS_LIST.sort();
        
        DuelDimension.log("Done reading distribution files!");
    }
    
    private static void readRarities(File raritiesFolder)
    {
        DuelDimension.log("Reading rarity files from: " + raritiesFolder.getAbsolutePath());
        
        File[] raritiesFiles = raritiesFolder.listFiles(DdIOUtil.JSON_FILTER);
        DdDatabase.RARITIES_LIST.ensureExtraCapacity(raritiesFiles.length);
        
        JsonObject j;
        RarityEntry r;
        
        for(File rarityFile : raritiesFiles)
        {
            try
            {
                j = DdIOUtil.parseJsonFile(rarityFile).getAsJsonObject();
                r = new RarityEntry(j);
                DdDatabase.RARITIES_LIST.add(r);
            }
            catch(NullPointerException | IllegalArgumentException | IllegalStateException e)
            {
                DuelDimension.log("Failed reading rarity: " + rarityFile.getAbsolutePath());
                e.printStackTrace();
            }
            catch(JsonSyntaxException e)
            {
                DuelDimension.log("Failed reading rarity: " + rarityFile.getAbsolutePath());
                e.printStackTrace();
            }
            catch(JsonIOException | FileNotFoundException e)
            {
                DuelDimension.log("Failed reading rarity: " + rarityFile.getAbsolutePath());
                e.printStackTrace();
            }
            catch(IOException e)
            {
                DuelDimension.log("Failed reading rarity: " + rarityFile.getAbsolutePath());
                e.printStackTrace();
            }
            catch(Exception e)
            {
                DuelDimension.log("Failed reading rarity: " + rarityFile.getAbsolutePath());
                throw e;
            }
        }
        
        DdDatabase.RARITIES_LIST.sort();
        
        DuelDimension.log("Done reading rarity files!");
    }
    
    private static void readSets(File setsFolder)
    {
        DuelDimension.log("Reading set files from: " + setsFolder.getAbsolutePath());
        
        File[] setsFiles = setsFolder.listFiles(DdIOUtil.JSON_FILTER);
        DdDatabase.SETS_LIST.ensureExtraCapacity(setsFiles.length);
        
        JsonObject j;
        CardSet s;
        
        for(File setFile : setsFiles)
        {
            try
            {
                j = DdIOUtil.parseJsonFile(setFile).getAsJsonObject();
                s = new CardSet(j);
                DdDatabase.SETS_LIST.add(s);
            }
            catch(NullPointerException | IllegalArgumentException | IllegalStateException e)
            {
                DuelDimension.log("Failed reading set: " + setFile.getAbsolutePath());
                e.printStackTrace();
            }
            catch(JsonSyntaxException e)
            {
                DuelDimension.log("Failed reading set: " + setFile.getAbsolutePath());
                e.printStackTrace();
            }
            catch(JsonIOException | FileNotFoundException e)
            {
                DuelDimension.log("Failed reading set: " + setFile.getAbsolutePath());
                e.printStackTrace();
            }
            catch(IOException e)
            {
                DuelDimension.log("Failed reading set: " + setFile.getAbsolutePath());
                e.printStackTrace();
            }
            catch(Exception e)
            {
                DuelDimension.log("Failed reading set: " + setFile.getAbsolutePath());
                throw e;
            }
        }
        
        DdDatabase.SETS_LIST.sort();
        
        DuelDimension.log("Done reading set files!");
    }
    
    private static void postDBInit()
    {
        DuelDimension.log("Finalizing database!");
        
        for(Properties x : DdDatabase.PROPERTIES_LIST)
        {
            x.postDBInit();
        }
        
        for(Distribution x : DdDatabase.DISTRIBUTIONS_LIST)
        {
            x.postDBInit();
        }
        
        for(CardSet x : DdDatabase.SETS_LIST)
        {
            x.postDBInit();
        }
        
        SETS_LIST.getList().stream().filter(Objects::nonNull).map(s -> s.rarityPool).filter(Objects::nonNull).forEach(FOUND_RARITIES::addAll);

        // The shop's catalogue is derived from these sets and cached, so it is
        // dropped whenever the sets themselves are rebuilt.
        de.cas_ual_ty.dueldimension.shop.ShopStock.invalidate();
        
        DuelDimension.log("All rarities found:");
        DuelDimension.log(FOUND_RARITIES.stream().map(s -> "\"" + s + "\"").collect(Collectors.joining(", ")));
    }
    
    public static int getTotalCardsAndVariants()
    {
        if(DdDatabase.cardsVariantsCount == -1)
        {
            DdDatabase.cardsVariantsCount = DdDatabase.PROPERTIES_LIST.getList().stream().mapToInt((p) -> p.getImageIndicesAmt()).sum();
        }
        
        return DdDatabase.cardsVariantsCount;
    }
    
    public static void forAllCardVariants(BiConsumer<Properties, Byte> cardImageConsumer)
    {
        byte i;
        for(Properties c : DdDatabase.PROPERTIES_LIST)
        {
            if(c == Properties.DUMMY)
            {
                continue;
            }
            
            for(i = 0; i < c.getImageIndicesAmt(); ++i)
            {
                cardImageConsumer.accept(c, i);
            }
        }
    }
    
    public static RarityEntry getRarity(String rarity)
    {
        return rarity != null ? RARITIES_LIST.get(rarity) : null;
    }
}
