package de.cas_ual_ty.dueldimension.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import de.cas_ual_ty.dueldimension.DdDatabase;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class DdIOUtil
{
    public static final FileFilterSuffix JSON_FILTER = DdIOUtil.createFileFilter(".json");
    public static final FileFilterSuffix PNG_FILTER = DdIOUtil.createFileFilter(".png");
    
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    public static FileFilterSuffix createFileFilter(String requiredSuffix)
    {
        return () -> requiredSuffix;
    }
    
    /** How long a card image may take to answer before the worker gives up. */
    private static final int CONNECT_TIMEOUT_MS = 8_000;
    private static final int READ_TIMEOUT_MS = 15_000;

    public static InputStream urlInputStream(URL url) throws IOException
    {
        //        /*
        URLConnection c = url.openConnection();
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Macintosh; U; Intel Mac OS X 10.4; en-US; rv:1.9.2.2) Gecko/20100316 Firefox/3.6.2");
        // Bounded, because URLConnection's default is to wait for ever. There
        // are four worker threads: four downloads to a host that accepts the
        // connection and then says nothing will starve every image in the game
        // permanently, and it looks exactly like the client having hung.
        c.setConnectTimeout(CONNECT_TIMEOUT_MS);
        c.setReadTimeout(READ_TIMEOUT_MS);
        return c.getInputStream();
        //        */
        //        return url.openStream();
    }
    
    public static void downloadFile(URL url, File target) throws IOException
    {
        // try-with-resources. The stream used to be opened and never closed, so
        // a download that failed part way -- which is exactly what happens on a
        // flaky connection, the case this is most often exercised on -- left the
        // connection open until the collector got round to it.
        try(InputStream in = DdIOUtil.urlInputStream(url))
        {
            Files.copy(in, Paths.get(target.toURI()));
        }
    }
    
    public static void setAgent()
    {
        System.setProperty("http.agent", "Netscape 1.0");
        //        System.setProperty("http.agent", "Mozilla/5.0 (Macintosh; U; Intel Mac OS X 10.4; en-US; rv:1.9.2.2) Gecko/20100316 Firefox/3.6.2");
    }
    
    /**
     * Makes a directory, and every parent it needs.
     * <p>
     * {@code mkdirs}, not {@code mkdir}: the callers ask for nested folders --
     * {@code ydm_db_images/cards/raw} and {@code ydm_db/rarity_images} -- and
     * one level at a time silently did nothing when the parent was not there
     * yet, which is exactly the state a clean install whose database download
     * failed is in. Every image the workers then wrote failed one by one.
     */
    public static void createDirIfNonExistant(File file)
    {
        if(!file.exists() && !file.mkdirs() && !file.isDirectory())
        {
            // Said once, here, rather than as one exception per file written
            // into a folder that is not there.
            de.cas_ual_ty.dueldimension.DuelDimension.warn(
                "Could not create " + file.getAbsolutePath());
        }
    }
    
    public static void writeJson(File target, JsonElement json) throws IOException
    {
        if(target.exists())
        {
            target.delete();
        }
        
        target.createNewFile();
        FileWriter fw = new FileWriter(target);
        DdIOUtil.GSON.toJson(json, fw);
        fw.flush();
        fw.close();
    }
    
    public static boolean doForDeepSearched(File parent, Predicate<File> predicate, Consumer<File> consumer)
    {
        for(File file : parent.listFiles())
        {
            if(predicate.test(file))
            {
                consumer.accept(file);
                return true;
            }
            else if(file.isDirectory() && DdIOUtil.doForDeepSearched(file, predicate, consumer))
            {
                return true;
            }
        }
        
        return false;
    }
    
    public static void deleteRecursively(File parent)
    {
        if(parent.isDirectory())
        {
            for(File file : parent.listFiles())
            {
                DdIOUtil.deleteRecursively(file);
            }
        }
        
        parent.delete();
    }
    
    public static JsonElement parseJsonFile(File file) throws JsonParseException, IOException
    {
        try(FileReader fr = new FileReader(file))
        {
            return DdIOUtil.parseJsonFile(fr);
        }
    }
    
    public static JsonElement parseJsonFile(Reader reader) throws JsonParseException
    {
        return DdDatabase.JSON_PARSER.parse(reader);
    }
}
