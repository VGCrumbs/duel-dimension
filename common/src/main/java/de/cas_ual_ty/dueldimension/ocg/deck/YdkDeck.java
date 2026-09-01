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
public record YdkDeck(String name, List<Integer> main, List<Integer> extra, List<Integer> side,
    List<Integer> destiny)
{
    /**
     * The four-part deck every caller before Destiny Draw was building, with no
     * cards flagged.
     * <p>
     * Kept so that adding a fifth component did not become an edit to every
     * place a deck is constructed. A deck with no Destiny Cards is the normal
     * case and stays the shortest thing to write.
     */
    public YdkDeck(String name, List<Integer> main, List<Integer> extra, List<Integer> side)
    {
        this(name, main, extra, side, List.of());
    }

    /**
     * Deduplicated on the way in, and never null.
     * <p>
     * A flag is a property of a CARD, not of a copy: flagging one of three
     * copies flags the card, and the engine's random pick is over whichever
     * copies are still in the deck. Storing it as a set of passcodes rather
     * than as marks on deck slots is what makes that true without any further
     * code -- and is why a duplicate in the file is not an error, just a
     * repetition.
     */
    public YdkDeck
    {
        destiny = destiny == null ? List.of()
            : List.copyOf(new java.util.LinkedHashSet<>(destiny));
    }

    /** Whether this passcode is flagged as a Destiny Card. */
    public boolean isDestiny(int code)
    {
        return destiny.contains(code);
    }

    /** The same deck with one card's flag turned on or off. */
    public YdkDeck withDestiny(int code, boolean flagged)
    {
        if(isDestiny(code) == flagged)
        {
            return this;
        }
        List<Integer> next = new ArrayList<>(destiny);
        if(flagged)
        {
            next.add(code);
        }
        else
        {
            next.remove(Integer.valueOf(code));
        }
        return new YdkDeck(name, main, extra, side, next);
    }

    /** Main deck must be 40-60 cards, extra and side at most 15 (standard construction rules). */
    public boolean isLegalSize()
    {
        return main.size() >= 40 && main.size() <= 60 && extra.size() <= 15 && side.size() <= 15;
    }

    public HeadlessDuelRunner.Deck toRunnerDeck()
    {
        // The flags travel with the deck, so nothing between here and the
        // engine has to know they exist.
        return new HeadlessDuelRunner.Deck(main, extra).flagging(destiny);
    }

    public static YdkDeck parse(String name, String text)
    {
        List<Integer> main = new ArrayList<>();
        List<Integer> extra = new ArrayList<>();
        List<Integer> side = new ArrayList<>();
        List<Integer> destiny = new ArrayList<>();
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
                else if(section.startsWith("destiny"))
                {
                    // THE CODES ARE ON THIS LINE, NOT UNDER IT.
                    //
                    // Every other section is a header followed by bare
                    // passcodes, and this one deliberately is not. A reader
                    // that does not know the word "destiny" treats the line as
                    // a comment -- which every .ydk tool does, including this
                    // parser -- and then reads the lines beneath it into
                    // whichever section was open. Bare codes under an
                    // unrecognised header are not ignored, they are silently
                    // appended to the side deck.
                    //
                    // One self-contained line cannot be misread that way. The
                    // deck stays a legal .ydk that EDOPro will open, and the
                    // flags survive a round trip through anything that keeps
                    // comment lines.
                    for(String code : section.substring("destiny".length()).split("[ ,]+"))
                    {
                        if(!code.isBlank())
                        {
                            try
                            {
                                destiny.add(Integer.parseInt(code.trim()));
                            }
                            catch(NumberFormatException notACode)
                            {
                                // A hand-edited line is not worth refusing a
                                // whole deck over; the flag is a convenience.
                            }
                        }
                    }
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
        return new YdkDeck(name, List.copyOf(main), List.copyOf(extra), List.copyOf(side),
            List.copyOf(destiny));
    }

    /**
     * The deck as a .ydk file's contents.
     * <p>
     * Section markers exactly as the format has them, including {@code !side}
     * with a bang rather than a hash -- that inconsistency is the format's, and
     * a file written with {@code #side} is read by other clients as more of the
     * extra deck. A copy is one line, repeated, which is how every real file
     * writes them.
     */
    public String toYdkText()
    {
        StringBuilder text = new StringBuilder();
        text.append("#created by Duel Dimension\n");
        text.append("#main\n");
        main.forEach(code -> text.append(code).append('\n'));
        text.append("#extra\n");
        extra.forEach(code -> text.append(code).append('\n'));
        text.append("!side\n");
        side.forEach(code -> text.append(code).append('\n'));
        if(!destiny.isEmpty())
        {
            // Last, and on one line -- see parse(). Omitted entirely when
            // nothing is flagged, so a deck that never used the feature writes
            // exactly the bytes it always did.
            text.append("#destiny");
            destiny.forEach(code -> text.append(' ').append(code));
            text.append('\n');
        }
        return text.toString();
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
