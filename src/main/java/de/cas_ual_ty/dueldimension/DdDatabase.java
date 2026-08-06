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
    
    public static JsonObject localDbInfo = null;
    public static int localVersionIteration = Integer.MIN_VALUE;
    public static String localVersionId = null;
    
    public static JsonObject remoteDbInfo = null;
    public static int remoteVersionIteration = Integer.MIN_VALUE;
    public static String remoteDownloadLink = null;
    public static String remoteVersionId = null;
    
    public static void initDatabase()
    {
        if(!DuelDimension.dbSourceUrl.isEmpty())
        {
            boolean downloadDB = false;
            boolean remoteRead = false;
            
            if(!DuelDimension.mainFolder.exists())
            {
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
                    DuelDimension.log("Failed assessing if a new database needs to be downloaded. Doing it anyways...");
                    e.printStackTrace();
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
                    DuelDimension.log("Cannot download database.");
                    return;
                }
                
                try
                {
                    DdDatabase.downloadDatabase();
                }
                catch(IOException e)
                {
                    DuelDimension.log("Failed downloading database.");
                    e.printStackTrace();
                    return;
                }
            }
        }
        
        DdDatabase.readFiles();
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
            DuelDimension.log("Cannot read local db.json file. Redownloading database...");
            e.printStackTrace();
        }
        catch(JsonParseException e)
        {
            DuelDimension.log("Cannot parse local db.json file. Redownloading database...");
            e.printStackTrace();
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
            DuelDimension.log("Cannot read remote db.json file. Staying on current database...");
            e.printStackTrace();
        }
        catch(JsonParseException | NullPointerException e)
        {
            DuelDimension.log("Cannot parse remote db.json file. Staying on current database...");
            e.printStackTrace();
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

        DdDatabase.installBundledExtras();

        if(!DuelDimension.mainFolder.exists())
        {
            DuelDimension.log(DuelDimension.mainFolder.getAbsolutePath() + " (main folder) does not exist! Aborting...");
            return;
        }
        
        if(!DuelDimension.cardsFolder.exists())
        {
            DuelDimension.log(DuelDimension.cardsFolder.getAbsolutePath() + " (cards folder) does not exist! Aborting...");
            return;
        }
        
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

    public static void downloadDatabase() throws IOException
    {
        DuelDimension.log("Downloading database from " + DdDatabase.remoteDownloadLink);
        
        // remove main folder and contents
        if(DuelDimension.mainFolder.exists())
        {
            DdIOUtil.deleteRecursively(DuelDimension.mainFolder);
        }
        
        URL url = new URL(DdDatabase.remoteDownloadLink);
        
        // archive containing the files
        File zip = new File("ydm_db_temp.zip");
        if(zip.exists())
        {
            zip.delete();
        }
        
        // archive to inpack to
        File temp = new File("ydm_db_temp");
        if(temp.exists())
        {
            temp.delete();
        }
        temp.mkdir();
        
        // download the zipped db
        DdIOUtil.downloadFile(url, zip);
        
        // --- zip unpack ---
        
        ZipInputStream zipIn = new ZipInputStream(new FileInputStream(zip));
        ZipEntry entry = zipIn.getNextEntry();
        
        byte[] buffer = new byte[1024];
        
        File currentFile;
        FileOutputStream zipOut;
        int length;
        
        while(entry != null)
        {
            currentFile = new File(temp, entry.getName());
            
            if(entry.isDirectory())
            {
                currentFile.mkdir();
            }
            else
            {
                zipOut = new FileOutputStream(currentFile);
                
                while((length = zipIn.read(buffer)) > 0)
                {
                    zipOut.write(buffer, 0, length);
                }
                
                zipOut.close();
            }
            
            entry = zipIn.getNextEntry();
        }
        
        zipIn.closeEntry();
        zipIn.close();
        
        zip.delete();
        
        // --- zip unpack end ---
        
        // now move the file out
        DdIOUtil.doForDeepSearched(temp, (file) -> file.getName().equals(DuelDimension.mainFolder.getName()), (file) ->
        {
            try
            {
                Files.move(file, DuelDimension.mainFolder);
            }
            catch(IOException e)
            {
                e.printStackTrace();
            }
        });
        
        // now delete temp folder
        DdIOUtil.deleteRecursively(temp);
        
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
