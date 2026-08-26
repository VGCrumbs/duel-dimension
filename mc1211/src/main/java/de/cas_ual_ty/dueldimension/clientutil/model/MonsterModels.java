package de.cas_ual_ty.dueldimension.clientutil.model;

import de.cas_ual_ty.dueldimension.DuelDimension;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The 3D models a monster can stand as, loaded on demand and kept.
 * <p>
 * They live in {@code config/dueldimension/models}, beside the {@code sheets}
 * folder holding the sprite sheets they are an alternative to — so adding a
 * model is dropping a {@code .glb} next to the {@code .png}s rather than
 * learning a second place for it.
 * <p>
 * <b>Loaded lazily and remembered, including the failures.</b> Reading and
 * baking a model is not free, and the alternative to remembering a failure is
 * re-reading a broken file sixty times a second and printing a warning each
 * time. A name that failed is stored as a miss and never tried again this
 * session, which is also why the warning it prints says what to do about it.
 */
public final class MonsterModels
{
    /**
     * Both hits and misses. A null value means "tried, could not", which is a
     * different thing from absent.
     */
    private static final Map<String, ModelMesh> LOADED = new HashMap<>();

    private MonsterModels()
    {
    }

    /** Where a duellist puts a {@code .glb}. */
    public static Path folder()
    {
        return FabricLoader.getInstance().getConfigDir()
            .resolve("dueldimension").resolve("models");
    }

    /**
     * The model of that name, or null if there is not one that loads.
     * <p>
     * Must be called on the render thread: baking registers textures, and that
     * reaches for the graphics device.
     *
     * @param name the file's name without {@code .glb}, as written in
     *             {@code monster_sprites.json}
     */
    public static ModelMesh get(String name)
    {
        if(name == null || name.isBlank())
        {
            return null;
        }
        // containsKey rather than get() != null: a remembered failure is a null
        // value, and asking the wrong way would retry it every frame.
        if(LOADED.containsKey(name))
        {
            return LOADED.get(name);
        }
        ModelMesh mesh = read(name);
        LOADED.put(name, mesh);
        return mesh;
    }

    /**
     * Whether a name is one this will actually load.
     * <p>
     * <b>One rule, asked in three places</b> — here, by {@link #names} deciding
     * what to offer, and implicitly by {@link #take} deciding what to call an
     * imported file. They used to disagree: the picker offered whatever was on
     * disk, and a file called {@code blue..eyes.glb} was offered, chosen, saved
     * into the definition, and then refused by the loader, so a name the editor
     * itself had produced turned into a monster that stayed a sprite.
     * <p>
     * The rule is deliberately narrow — the same lower-case alphabet
     * {@code take} sanitises into. A name from a config file becomes a path, so
     * it must not be able to leave the folder; and two names that differ only in
     * case or spacing would otherwise register their textures under one shared
     * identifier and wear each other's skins.
     */
    static boolean plain(String name)
    {
        if(name == null || name.isBlank() || name.contains(".."))
        {
            return false;
        }
        if(!name.matches("[a-z0-9._-]+"))
        {
            return false;
        }
        try
        {
            // Belt and braces against the platform's own idea of a path.
            // "C:model" is not absolute on Windows and contains no separator,
            // yet it carries a root -- and Path.resolve DISCARDS its base when
            // the argument has one, which walks straight out of the folder past
            // every check above.
            Path candidate = Path.of(name);
            return candidate.getRoot() == null && !candidate.isAbsolute()
                && candidate.getNameCount() == 1;
        }
        catch(Exception notAPath)
        {
            return false;
        }
    }

    /**
     * The model of that name only if it is ALREADY baked, without loading one.
     * <p>
     * Safe off the render thread, which {@link #get} is not: baking registers
     * textures and reaches for the graphics device. The playback clock runs on
     * the client tick and needs to know how long a monster's attack lasts before
     * it can decide how long to hold the board still — a question it must be
     * able to ask without accidentally loading a model from the wrong thread.
     * <p>
     * A miss is an ordinary answer here, not a failure: it means nothing has
     * drawn that monster yet, and the caller falls back to its own timing.
     */
    public static ModelMesh peek(String name)
    {
        return name == null ? null : LOADED.get(name);
    }

    private static ModelMesh read(String name)
    {
        if(!plain(name))
        {
            DuelDimension.warn("the model name \"" + name + "\" is not a plain file name;"
                + " use lower-case letters, digits, . _ and - (Import does this for you)");
            return null;
        }
        Path path = folder().resolve(name + ".glb");
        if(!Files.isRegularFile(path))
        {
            DuelDimension.warn("no model at " + path
                + "; that monster will fall back to its sprite");
            return null;
        }
        try
        {
            GlbModel model = GlbModel.load(Files.readAllBytes(path));
            ModelMesh mesh = ModelMesh.bake(model, name);
            DuelDimension.log("model " + name + ": " + mesh.parts().size() + " part(s), "
                + mesh.parts().stream().mapToInt(ModelMesh.Part::vertexCount).sum()
                + " vertices, " + String.format("%.1f", mesh.modelHeight()) + " units tall");
            return mesh;
        }
        catch(Exception broken)
        {
            // Named rather than swallowed: the loader rejects the parts of glTF
            // it does not read, and its message says which one, so this is the
            // difference between "re-export it with TRS" and "it did not work".
            DuelDimension.warn("could not load the model " + path + ": " + broken);
            return null;
        }
    }

    /** Forgets everything, so a changed file is picked up without a restart. */
    public static void clear()
    {
        LOADED.clear();
    }

    // ------------------------------------------------------------ importing --

    /**
     * Every {@code .glb} sitting in the folder, by the name a definition would
     * call it.
     * <p>
     * Read off the disk each time rather than cached. This is answered when a
     * duellist opens a menu, not per frame, and the whole point of it is to
     * notice a file that was dropped in a moment ago — a cache here would be a
     * cache of exactly the thing that is expected to change.
     */
    public static List<String> names()
    {
        if(!Files.isDirectory(folder()))
        {
            return List.of();
        }
        try(Stream<Path> files = Files.list(folder()))
        {
            return files.filter(Files::isRegularFile)
                .map(path -> path.getFileName().toString())
                .filter(name -> name.toLowerCase(Locale.ROOT).endsWith(".glb"))
                .map(name -> name.substring(0, name.length() - 4))
                // Only what will actually load. Offering a name the loader then
                // refuses is worse than not offering it: it is chosen, saved
                // into the definition, and fails somewhere else entirely.
                .filter(MonsterModels::plain)
                .sorted()
                .toList();
        }
        catch(Exception unreadable)
        {
            DuelDimension.warn("could not list " + folder() + ": " + unreadable);
            return List.of();
        }
    }

    /**
     * Asks the operating system for a {@code .glb}.
     * <p>
     * The same native dialog the sprite sheets and the decks use, so importing a
     * model is the gesture importing anything else already is.
     *
     * @return null both when the duellist cancels and when there is no dialog to
     *         be had — the caller cannot tell those apart, which is why the one
     *         here says where to put files by hand
     */
    public static Path choose()
    {
        try
        {
            org.lwjgl.PointerBuffer filters = org.lwjgl.BufferUtils.createPointerBuffer(1);
            filters.put(org.lwjgl.system.MemoryUtil.memUTF8("*.glb"));
            filters.flip();
            String start = folder().toAbsolutePath() + java.io.File.separator;
            String picked = org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog(
                "Choose a model (.glb)", start, filters, "glTF binary", false);
            return picked == null ? null : Path.of(picked);
        }
        catch(Throwable unavailable)
        {
            return null;
        }
    }

    /**
     * Copies a chosen file into the models folder and returns the name to write
     * into a definition.
     * <p>
     * <b>Flattened and sanitised to what {@link #read} will accept.</b> A
     * definition names a model by a bare file name, and that name is resolved
     * against the folder — so it may not contain a separator, and it may not
     * contain {@code ..}. Producing a name the loader then refuses would be an
     * import that appears to work and a monster that silently stays a sprite.
     *
     * @return the bare name, or null if the file could not be taken
     */
    public static String take(Path file)
    {
        if(file == null)
        {
            return null;
        }
        try
        {
            Files.createDirectories(folder());
            String given = file.getFileName().toString();
            int dot = given.lastIndexOf('.');
            String name = (dot > 0 ? given.substring(0, dot) : given)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]", "_")
                // After the extension is off, a run of dots is left over from a
                // name like "dragon..final.glb" -- and ".." is the one sequence
                // the loader's path guard rejects outright.
                .replaceAll("\\.{2,}", "_");
            // Asked rather than assumed: the sanitising above is meant to land
            // inside what plain() accepts, and checking closes the loop instead
            // of trusting two regexes to agree forever.
            if(!plain(name))
            {
                DuelDimension.warn("could not make a usable file name out of \"" + given + "\"");
                return null;
            }
            Path target = folder().resolve(name + ".glb");
            // Existence first: isSameFile throws when asked about a file that is
            // not there, so re-importing something already in the folder must
            // not go looking for a copy that has never been made.
            if(!Files.exists(target) || !Files.isSameFile(file, target))
            {
                Files.copy(file, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            // The failures are remembered, so a name that failed a moment ago
            // stays failed until this is called -- including the name that has
            // just this second become a real file.
            clear();
            return name;
        }
        catch(Exception uncopyable)
        {
            DuelDimension.warn("could not import the model " + file + ": " + uncopyable);
            return null;
        }
    }

    /** Opens the models folder in whatever the system uses to show folders. */
    public static void open()
    {
        try
        {
            Files.createDirectories(folder());
            net.minecraft.Util.getPlatform().openPath(folder());
        }
        catch(Exception unopenable)
        {
            DuelDimension.warn("could not open " + folder() + ": " + unopenable);
        }
    }
}
