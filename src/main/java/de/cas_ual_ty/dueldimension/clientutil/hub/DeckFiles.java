package de.cas_ual_ty.dueldimension.clientutil.hub;

import de.cas_ual_ty.dueldimension.DdDatabase;
import de.cas_ual_ty.dueldimension.duel.profile.DeckList;
import de.cas_ual_ty.dueldimension.ocg.deck.YdkDeck;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Decks on disk, in the .ydk format EDOPro and YGOPro read and write.
 * <p>
 * Client side, and deliberately: a .ydk is a file on the machine a player is
 * sitting at, and the deck it becomes still goes through the editor and the
 * server exactly as a hand-built one does. Importing is not a way to put a
 * deck on the server that the editor would have refused — every card added
 * here is added the way clicking it would add it.
 */
public final class DeckFiles
{
    /**
     * Where exported decks land, beside the mod's other game-directory
     * folders ({@code ydm_binders}, {@code ydm_db_images}).
     */
    public static final File FOLDER = new File("ydm_decks");

    private DeckFiles()
    {
    }

    /** What an import did, so the editor can say so rather than fail quietly. */
    public record Result(boolean ok, String message)
    {
    }

    private static Path folder() throws IOException
    {
        Path path = FOLDER.toPath();
        Files.createDirectories(path);
        return path;
    }

    /**
     * Writes a deck out as {@code ydm_decks/<name>.ydk}.
     * <p>
     * The name is sanitised rather than rejected: a deck may legitimately be
     * called "Blue-Eyes / Chaos", and a player who named it that should get a
     * file, not an error about path separators.
     */
    public static Result export(DeckList deck)
    {
        try
        {
            String safe = deck.name().replaceAll("[\\\\/:*?\"<>|]", "_").trim();
            if(safe.isEmpty())
            {
                safe = "deck";
            }
            Path file = chooseSaveTarget(safe + ".ydk");
            if(file == null)
            {
                return new Result(false, "");   // cancelled, which is an answer
            }
            YdkDeck ydk = new YdkDeck(deck.name(),
                List.copyOf(deck.main()), List.copyOf(deck.extra()), List.copyOf(deck.side()));
            Files.writeString(file, ydk.toYdkText());
            return new Result(true, "Exported to " + file.getFileName());
        }
        catch(IOException unwritable)
        {
            return new Result(false, "Could not write the deck: " + unwritable.getMessage());
        }
    }

    /**
     * Asks where to write a deck, suggesting our own folder and name.
     * <p>
     * A real Save As dialog, so the file can go wherever the player keeps
     * their decks -- an EDOPro deck folder, most usefully -- rather than only
     * into ours. Falls back to {@link #FOLDER} when the native dialog is
     * unavailable, so exporting still works on a build without the library.
     *
     * @return where to write, or null if the player cancelled
     */
    private static Path chooseSaveTarget(String suggested) throws IOException
    {
        try
        {
            org.lwjgl.PointerBuffer filters = org.lwjgl.BufferUtils.createPointerBuffer(1);
            filters.put(org.lwjgl.system.MemoryUtil.memUTF8("*.ydk"));
            filters.flip();
            String picked = org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_saveFileDialog(
                "Export deck", folder() + File.separator + suggested, filters, "YGOPro deck");
            if(picked == null)
            {
                return null;
            }
            // Someone who typed a bare name still means a .ydk.
            return picked.toLowerCase(Locale.ROOT).endsWith(".ydk")
                ? Path.of(picked) : Path.of(picked + ".ydk");
        }
        catch(Throwable unavailable)
        {
            return folder().resolve(suggested);
        }
    }

    /**
     * Asks the system for a .ydk.
     * <p>
     * Through LWJGL's file dialog, which Minecraft already ships, so the player
     * browses the way they would in any other program. If that native library
     * is missing the export folder is offered instead — the same fallback the
     * under-skin import uses, and for the same reason.
     *
     * @return the chosen file, or null if the player cancelled
     */
    public static Path choose()
    {
        try
        {
            org.lwjgl.PointerBuffer filters = org.lwjgl.BufferUtils.createPointerBuffer(1);
            filters.put(org.lwjgl.system.MemoryUtil.memUTF8("*.ydk"));
            filters.flip();
            String start = FOLDER.getAbsolutePath() + File.separator;
            String picked = org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog(
                "Choose a deck (.ydk)", start, filters, "YGOPro deck", false);
            return picked == null ? null : Path.of(picked);
        }
        catch(Throwable unavailable)
        {
            return null;
        }
    }

    /**
     * Reads a .ydk into a new deck.
     * <p>
     * Cards the database does not know are dropped and counted rather than
     * failing the import: a file written against a newer database is still
     * mostly a deck, and saying "12 of 40 cards are unknown" is more use than
     * refusing it. Ownership and the banlist are NOT checked here — an
     * imported deck is allowed to be one you cannot yet play, and the editor
     * already marks such a deck unusable and says why.
     */
    public static Result importInto(Path file)
    {
        YdkDeck ydk;
        try
        {
            ydk = YdkDeck.load(file);
        }
        catch(RuntimeException unreadable)
        {
            return new Result(false, "Not a readable .ydk: " + unreadable.getMessage());
        }

        DeckList deck = EditorState.newDeck();
        int unknown = 0;
        unknown += fill(deck.main(), ydk.main(), DeckList.Part.MAIN.capacity());
        unknown += fill(deck.extra(), ydk.extra(), DeckList.Part.EXTRA.capacity());
        unknown += fill(deck.side(), ydk.side(), DeckList.Part.SIDE.capacity());

        // Named after the file, since a .ydk carries no name of its own beyond
        // one; EditorState.rename refuses a duplicate, and the deck simply
        // keeps its generated name in that case.
        String name = file.getFileName().toString().replaceFirst("(?i)\\.ydk$", "");
        EditorState.rename(name);

        int total = deck.main().size() + deck.extra().size() + deck.side().size();
        String message = "Imported " + total + " cards from " + file.getFileName();
        if(unknown > 0)
        {
            message += " (" + unknown + " not in the database)";
        }
        return new Result(true, message);
    }

    /**
     * Copies as many passcodes as the part can hold, skipping any the database
     * cannot resolve.
     *
     * @return how many were skipped
     */
    private static int fill(List<Integer> into, List<Integer> from, int capacity)
    {
        int unknown = 0;
        for(int code : from)
        {
            if(DdDatabase.PROPERTIES_LIST.get((long)code) == null)
            {
                unknown++;
                continue;
            }
            if(into.size() >= capacity)
            {
                continue;
            }
            into.add(code);
        }
        return unknown;
    }

    /** The folder, for a message that tells the player where to look. */
    public static String folderPath()
    {
        return FOLDER.getAbsolutePath().toLowerCase(Locale.ROOT);
    }
}
