package de.cas_ual_ty.dueldimension.ocg.deck;

import de.cas_ual_ty.dueldimension.ocg.HeadlessDuelRunner;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The .ydk deck format shared by YGOPro/EDOPro: comment lines starting with
 * {@code #}, a {@code #main} section, {@code #extra}, and {@code !side}, each
 * followed by one passcode per line. Using the community format means decks
 * can be exported from or imported into other tools unchanged.
 */
public record YdkDeck(String name, List<Integer> main, List<Integer> extra, List<Integer> side)
{
    /** Main deck must be 40-60 cards, extra and side at most 15 (standard construction rules). */
    public boolean isLegalSize()
    {
        return main.size() >= 40 && main.size() <= 60 && extra.size() <= 15 && side.size() <= 15;
    }

    public HeadlessDuelRunner.Deck toRunnerDeck()
    {
        return new HeadlessDuelRunner.Deck(main, extra);
    }

    public static YdkDeck parse(String name, String text)
    {
        List<Integer> main = new ArrayList<>();
        List<Integer> extra = new ArrayList<>();
        List<Integer> side = new ArrayList<>();
        List<Integer> current = main;

        for(String raw : text.split("\\R"))
        {
            String line = raw.trim();
            if(line.isEmpty())
            {
                continue;
            }
            if(line.startsWith("#") || line.startsWith("!"))
            {
                String section = line.substring(1).toLowerCase();
                if(section.startsWith("main"))
                {
                    current = main;
                }
                else if(section.startsWith("extra"))
                {
                    current = extra;
                }
                else if(section.startsWith("side"))
                {
                    current = side;
                }
                // any other # line is a comment (e.g. "#created by ...")
                continue;
            }
            try
            {
                current.add(Integer.parseInt(line));
            }
            catch(NumberFormatException e)
            {
                throw new IllegalArgumentException("Bad passcode line in deck " + name + ": '" + line + "'", e);
            }
        }
        return new YdkDeck(name, List.copyOf(main), List.copyOf(extra), List.copyOf(side));
    }

    public static YdkDeck load(Path file)
    {
        try
        {
            String fileName = file.getFileName().toString();
            return parse(fileName.replaceFirst("\\.ydk$", ""), Files.readString(file));
        }
        catch(IOException e)
        {
            throw new UncheckedIOException("Cannot read deck " + file, e);
        }
    }

    /** Loads a deck packaged in the mod jar (or on the test classpath). */
    public static YdkDeck loadResource(String resourcePath)
    {
        try(InputStream in = YdkDeck.class.getClassLoader().getResourceAsStream(resourcePath))
        {
            if(in == null)
            {
                throw new IllegalArgumentException("Deck resource not found: " + resourcePath);
            }
            try(BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
            {
                String name = resourcePath.substring(resourcePath.lastIndexOf('/') + 1).replaceFirst("\\.ydk$", "");
                return parse(name, reader.lines().reduce("", (a, b) -> a + "\n" + b));
            }
        }
        catch(IOException e)
        {
            throw new UncheckedIOException("Cannot read deck resource " + resourcePath, e);
        }
    }
}
