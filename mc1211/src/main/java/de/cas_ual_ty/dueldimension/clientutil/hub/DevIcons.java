package de.cas_ual_ty.dueldimension.clientutil.hub;

import de.cas_ual_ty.dueldimension.clientutil.ClientProxy;
import de.cas_ual_ty.dueldimension.clientutil.ImageHandler;
import de.cas_ual_ty.dueldimension.set.CardSet;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Saving and replacing a product's icon, from inside the running game.
 *
 * <h2>Development only</h2>
 * Every entry point here is gated on {@link #available()}, which is
 * {@code FabricLoader.isDevelopmentEnvironment()}. This writes into the mod's
 * SOURCE tree -- the whole point is that a replacement ships -- and a released
 * jar has no source tree to write to. Worse, it would be a mod quietly editing
 * files on a player's disk, which is not a thing a card game should do. So the
 * menu entries are not merely hidden outside dev; the methods refuse.
 *
 * <h2>Where an icon actually lives</h2>
 * A set's art is one of two things. A HARDCODED set ships its picture in the
 * mod's resources at {@code textures/item/<size>/<name>.png}. Every other set
 * downloads its picture and keeps it under the run directory. Replacing one
 * therefore has to do two separate jobs:
 *
 *   * write the new picture into the source tree at every shipped size, which
 *     is what makes it ship; and
 *   * write it into the run directory's cache as well, which is what makes it
 *     appear NOW rather than after a rebuild.
 *
 * Doing only the first looks broken -- you replace an icon and nothing changes,
 * because the downloaded copy still wins. Doing only the second looks like it
 * worked and then loses the change on the next clean.
 *
 * <h2>Finding the source tree</h2>
 * Walked up from the run directory rather than hardcoded: dev runs in
 * {@code <repo>/mc262/run}, and looking for the first ancestor that contains
 * {@code shared/resources} both locates it and refuses gracefully if this is
 * somehow not a source checkout.
 */
public final class DevIcons
{
    private DevIcons()
    {
    }

    /** Every size the mod ships a set icon at. */
    private static final int[] SIZES = {16, 32, 64, 128, 256, 512, 1024};

    public static boolean available()
    {
        try
        {
            return net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment();
        }
        catch(Throwable ignored)
        {
            return false;
        }
    }

    /**
     * The repository's shared resource root, or null if this is not a checkout.
     */
    private static Path sharedResources()
    {
        Path at = new File(".").getAbsoluteFile().toPath().normalize();
        for(int i = 0; i < 6 && at != null; i++)
        {
            Path candidate = at.resolve("shared").resolve("resources");
            if(Files.isDirectory(candidate))
            {
                return candidate;
            }
            at = at.getParent();
        }
        return null;
    }

    private static Path textures(int size)
    {
        Path shared = sharedResources();
        return shared == null ? null
            : shared.resolve("assets/dueldimension/textures/item/" + size);
    }

    /**
     * The resource root the RUNNING game reads, which is not the source tree.
     * <p>
     * {@code shared/resources} is a {@code srcDir}: Gradle copies it into
     * {@code build/resources/main} at {@code processResources} time, and that
     * copy is what is on the dev runtime classpath. So a write into the source
     * tree is invisible until the next build -- which is precisely why replacing
     * an icon used to need a restart, and why writing the run-directory cache as
     * well did not help either: the mod's own pack outranks the image pack,
     * which sits at {@code Pack.Position.BOTTOM}.
     * <p>
     * Found by walking up rather than assumed one level above the run directory,
     * so this holds for whichever module is being launched.
     */
    private static Path liveResources()
    {
        Path at = new File(".").getAbsoluteFile().toPath().normalize();
        for(int i = 0; i < 6 && at != null; i++)
        {
            Path candidate = at.resolve("build").resolve("resources").resolve("main");
            if(Files.isDirectory(candidate))
            {
                return candidate;
            }
            at = at.getParent();
        }
        return null;
    }

    private static Path liveTextures(int size)
    {
        Path live = liveResources();
        return live == null ? null
            : live.resolve("assets/dueldimension/textures/item/" + size);
    }

    /**
     * The tiers a texture is ever drawn SMALLER than, which are the ones that
     * want bilinear sampling. Mirrors {@code tools/add_texture_blur.py}, which
     * writes the same file for everything already shipped; without it a replaced
     * icon would come back point-sampled and shimmer in a shop tile.
     */
    private static final int BLUR_FROM = 128;

    private static final byte[] MCMETA =
        "{\n  \"texture\": {\n    \"blur\": true,\n    \"clamp\": true\n  }\n}\n"
            .getBytes(java.nio.charset.StandardCharsets.UTF_8);

    /**
     * The picture this set is CURRENTLY drawn with, wherever it came from.
     * <p>
     * The downloaded copy is preferred over the shipped one, because that is
     * the one the game is showing: if a set has both, the download is what is
     * on screen, and "save this icon" means the one being looked at.
     */
    private static Path current(CardSet set)
    {
        String name = set.getImageName();
        for(int size : new int[] {ClientProxy.activeSetInfoImageSize, 512, 256, 1024, 128})
        {
            File adjusted = ImageHandler.getSetImageFile(ImageHandler.tagImage(name, size));
            if(adjusted != null && adjusted.isFile())
            {
                return adjusted.toPath();
            }
        }
        File raw = ImageHandler.getRawSetImageFile(name);
        if(raw != null && raw.isFile())
        {
            return raw.toPath();
        }
        for(int size : new int[] {1024, 512, 256, 128, 64})
        {
            Path shipped = textures(size);
            if(shipped != null && Files.isRegularFile(shipped.resolve(name + ".png")))
            {
                return shipped.resolve(name + ".png");
            }
        }
        return null;
    }

    /**
     * Copies this set's icon somewhere the developer chooses.
     *
     * @return what to put on screen
     */
    public static String save(CardSet set)
    {
        if(!available())
        {
            return "Not available outside the development environment.";
        }
        Path source = current(set);
        if(source == null)
        {
            return "Nothing to save: this set has no icon on disk yet.";
        }
        String suggested = new File(System.getProperty("user.home", "."),
            set.getImageName() + ".png").getAbsolutePath();
        String picked = org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_saveFileDialog(
            "Save " + set.name + " icon", suggested, null, "PNG image");
        if(picked == null)
        {
            return "";
        }
        try
        {
            Files.copy(source, Path.of(picked), StandardCopyOption.REPLACE_EXISTING);
            return "Saved to " + picked;
        }
        catch(IOException error)
        {
            return "Could not save: " + error.getMessage();
        }
    }

    /**
     * Replaces this set's icon, in the source tree and in the live cache.
     *
     * @return what to put on screen
     */
    public static String replace(CardSet set)
    {
        if(!available())
        {
            return "Not available outside the development environment.";
        }
        Path shared = sharedResources();
        if(shared == null)
        {
            return "Could not find the source tree from " + new File(".").getAbsolutePath();
        }
        String picked = org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog(
            "Replace " + set.name + " icon", "", null, "Images", false);
        if(picked == null)
        {
            return "";
        }
        Path chosen = Path.of(picked);
        if(!Files.isRegularFile(chosen))
        {
            return "That file does not exist.";
        }

        java.awt.image.BufferedImage image;
        try
        {
            image = javax.imageio.ImageIO.read(chosen.toFile());
        }
        catch(IOException error)
        {
            return "Could not read that image: " + error.getMessage();
        }
        if(image == null)
        {
            return "That is not an image this can read.";
        }

        String name = set.getImageName();
        List<String> written = new ArrayList<>();
        // Every shipped size, scaled from the one file, so the ladder stays
        // consistent -- a replacement that only landed at one size would leave
        // the others showing the old art at whatever the config asks for.
        for(int size : SIZES)
        {
            Path folder = textures(size);
            if(folder == null || !Files.isDirectory(folder))
            {
                continue;
            }
            try
            {
                writeScaled(image, size, folder.resolve(name + ".png"));
                written.add(Integer.toString(size));
            }
            catch(IOException error)
            {
                return "Could not write " + size + ": " + error.getMessage();
            }
        }
        if(written.isEmpty())
        {
            return "No texture folders found under " + shared;
        }

        // And into the resource root the running game actually reads, which is
        // what makes the change visible without a build or a restart. Not fatal
        // if it is missing -- outside a Gradle dev run there is no such folder,
        // and the source tree write above is the part that had to happen.
        int now = 0;
        for(int size : SIZES)
        {
            Path folder = liveTextures(size);
            if(folder == null || !Files.isDirectory(folder))
            {
                continue;
            }
            try
            {
                writeScaled(image, size, folder.resolve(name + ".png"));
                now++;
            }
            catch(IOException ignored)
            {
                // Counted by omission; the shipped copy is already written.
            }
        }

        // And the run directory's own copies, so the change is visible without
        // a rebuild. Failing here is not fatal: the source is already updated,
        // which is what was actually asked for.
        int live = 0;
        for(int size : SIZES)
        {
            File adjusted = ImageHandler.getSetImageFile(ImageHandler.tagImage(name, size));
            if(adjusted == null)
            {
                continue;
            }
            try
            {
                adjusted.getParentFile().mkdirs();
                writeScaled(image, size, adjusted.toPath());
                live++;
            }
            catch(IOException ignored)
            {
                // Reported by omission from the count rather than as an error.
            }
        }
        // The lookup caches whether a set has a bundled icon, and it has just
        // changed. Without this the tile keeps its placeholder until a restart,
        // which looks exactly like the replace having done nothing -- which is
        // what it USED to do, for a different reason.
        ImageHandler.forgetBundledImages();
        refresh(name);

        return "Replaced " + name + " at " + written.size() + " sizes in src"
            + (now > 0 ? ", " + now + " live" : "")
            + (live > 0 ? ", " + live + " cached" : "")
            + ".";
    }

    /**
     * Makes the new file the one on screen, this frame.
     * <p>
     * Writing the bytes is not enough on its own. The icon is already a GPU
     * texture registered under its Identifier, and {@code CardImageManager}'s
     * status map already says LOADED, so every later draw is answered from
     * both without anything going near the disk. Dropping the two together is
     * what sends the next draw back to the file.
     * <p>
     * All seven sizes, because the Identifier carries the size in its path
     * ({@code textures/item/<size>/<name>.png}) and the one on screen is
     * whichever the config selected -- and the preview and the grid tile do not
     * have to agree on that.
     * <p>
     * On the render thread: releasing a texture disposes a GPU object, and this
     * is called from a click handler.
     */
    private static void refresh(String name)
    {
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        if(minecraft == null)
        {
            return;
        }
        minecraft.execute(() ->
        {
            for(int size : SIZES)
            {
                de.cas_ual_ty.dueldimension.clientutil.CardTextureCache.drop(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                        de.cas_ual_ty.dueldimension.DuelDimension.MOD_ID,
                        "textures/item/" + size + "/" + name + ".png"));
            }
        });
    }

    /** One size, written as a square PNG. */
    private static void writeScaled(java.awt.image.BufferedImage source, int size, Path target)
        throws IOException
    {
        java.awt.image.BufferedImage scaled = new java.awt.image.BufferedImage(
            size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D graphics = scaled.createGraphics();
        graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
            java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING,
            java.awt.RenderingHints.VALUE_RENDER_QUALITY);
        graphics.drawImage(source, 0, 0, size, size, null);
        graphics.dispose();
        Files.createDirectories(target.getParent());
        javax.imageio.ImageIO.write(scaled, "png", target.toFile());

        // The sampling hint travels with the picture. Written here rather than
        // by the caller so both the shipped copy and the live one get it, and
        // only from the tier where the icon starts being drawn smaller than it
        // is authored -- below that, nearest is the sharper choice.
        if(size >= BLUR_FROM)
        {
            Path meta = target.resolveSibling(target.getFileName() + ".mcmeta");
            if(!Files.exists(meta))
            {
                Files.write(meta, MCMETA);
            }
        }
    }
}
