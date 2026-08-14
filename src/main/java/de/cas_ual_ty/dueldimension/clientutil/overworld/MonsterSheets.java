package de.cas_ual_ty.dueldimension.clientutil.overworld;

import com.mojang.blaze3d.platform.NativeImage;
import de.cas_ual_ty.dueldimension.DuelDimension;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Sprite sheets brought in from outside the mod.
 * <p>
 * A sheet inside the jar is a sheet that needs a rebuild to change, which is
 * the wrong shape for art somebody is still drawing. Anything dropped in
 * {@code config/dueldimension/sheets/} is read at startup and handed to the
 * game as a texture of its own, so a new monster is a PNG in a folder and a
 * line in the editor -- no build, no resource pack, no restart beyond the one
 * that picks the file up.
 * <p>
 * Imported sheets are looked up FIRST. A file with the same name as a shipped
 * one replaces it, which is what makes this a way to fix a sheet as well as a
 * way to add one -- and deleting the file puts the shipped version back, since
 * nothing was overwritten to begin with.
 * <p>
 * Names have no extension and use forward slashes, exactly as the shipped ones
 * do: {@code dragon/blue_eyes_white_dragon} finds either
 * {@code config/dueldimension/sheets/dragon/blue_eyes_white_dragon.png} or the
 * one in the jar, in that order, and nothing else in the editor has to know
 * which it got.
 */
public final class MonsterSheets
{
    private MonsterSheets()
    {
    }

    private static final Map<String, Identifier> IMPORTED = new HashMap<>();
    private static final Map<Identifier, int[]> SIZES = new HashMap<>();

    /** Where a sheet goes to be picked up without a rebuild. */
    public static Path folder()
    {
        return FabricLoader.getInstance().getConfigDir()
            .resolve("dueldimension").resolve("sheets");
    }

    /**
     * The texture for a sheet name: the imported one if there is one, the
     * shipped one otherwise.
     */
    public static Identifier resolve(String sheet)
    {
        Identifier imported = IMPORTED.get(sheet.toLowerCase(Locale.ROOT));
        return imported != null ? imported
            : Identifier.fromNamespaceAndPath(DuelDimension.MOD_ID,
                "textures/duel/monsters/" + sheet + ".png");
    }

    /**
     * An imported sheet's size, or null if this texture is not one.
     * <p>
     * Kept from the moment it was read, because an imported texture is not in
     * the resource manager and cannot be measured back out of it -- the usual
     * route finds nothing and would report a sheet that exists as missing.
     */
    public static int[] size(Identifier texture)
    {
        return SIZES.get(texture);
    }

    /** Every imported sheet's name, for the editor to offer. */
    public static List<String> names()
    {
        return IMPORTED.keySet().stream().sorted().toList();
    }

    /**
     * Reads the folder and registers everything in it.
     * <p>
     * Every file is read on its own. One unreadable PNG costs that one sheet
     * and says so; it does not stop the rest, because a folder somebody is
     * dropping files into will contain a half-written file sooner or later.
     */
    public static void load()
    {
        IMPORTED.clear();
        SIZES.clear();
        Path root = folder();
        try
        {
            Files.createDirectories(root);
        }
        catch(Exception uncreatable)
        {
            DuelDimension.warn("could not make " + root + ": " + uncreatable);
            return;
        }
        try(Stream<Path> files = Files.walk(root))
        {
            files.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)
                    .endsWith(".png"))
                .forEach(path -> read(root, path));
        }
        catch(Exception unreadable)
        {
            DuelDimension.warn("could not read " + root + ": " + unreadable);
            return;
        }
        if(!IMPORTED.isEmpty())
        {
            DuelDimension.log("imported " + IMPORTED.size() + " monster sheet(s) from " + root);
        }
    }

    private static void read(Path root, Path path)
    {
        String name = root.relativize(path).toString().replace('\\', '/');
        name = name.substring(0, name.length() - 4).toLowerCase(Locale.ROOT);
        try(InputStream stream = Files.newInputStream(path))
        {
            NativeImage image = NativeImage.read(stream);
            Identifier id = Identifier.fromNamespaceAndPath(DuelDimension.MOD_ID,
                "imported/" + name.replaceAll("[^a-z0-9/._-]", "_"));
            // The texture takes ownership of the image and keeps it: a
            // DynamicTexture uploads from the pixels it holds, so closing them
            // here would hand the game an empty sheet.
            Minecraft.getInstance().getTextureManager()
                .register(id, new DynamicTexture(() -> name, image));
            IMPORTED.put(name, id);
            SIZES.put(id, new int[] {image.getWidth(), image.getHeight()});
        }
        catch(Exception broken)
        {
            DuelDimension.warn("could not import the sheet " + path + ": " + broken);
        }
    }

    /** Re-reads the folder, for the editor's reload button. */
    public static void reload()
    {
        load();
        MonsterSprites.clearMeasurements();
    }

    /**
     * Opens the folder in whatever the desktop uses for folders.
     * <p>
     * The point of an import folder is putting files in it, and telling
     * somebody a path they then have to go and find by hand is most of the
     * friction this feature exists to remove.
     */
    public static void open()
    {
        try
        {
            Files.createDirectories(folder());
            net.minecraft.util.Util.getPlatform().openPath(folder());
        }
        catch(Exception unopenable)
        {
            DuelDimension.warn("could not open " + folder() + ": " + unopenable);
        }
    }
}
