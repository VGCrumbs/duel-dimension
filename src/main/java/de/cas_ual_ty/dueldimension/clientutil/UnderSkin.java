package de.cas_ual_ty.dueldimension.clientutil;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The skin drawn <em>under</em> an outfit, when the player has supplied one.
 * <p>
 * An outfit is a body's worth of clothing, and a skin underneath it often has
 * things that poke through the gaps — a hoodie, long sleeves, hair down the
 * back. Rather than guess which pixels to erase, the player edits their own
 * skin in whatever they already use and hands the result over: same person,
 * minus the parts that fought with the jacket.
 * <p>
 * Client side and local. It is not sent anywhere and nobody else sees it, which
 * is why it can be a file on disk rather than a thing the server validates.
 * With no outfit on there is nothing to bleed out from under, so this is
 * ignored and the player is drawn with their real skin.
 */
public final class UnderSkin
{
    private static final ResourceLocation TEXTURE =
        new ResourceLocation(de.cas_ual_ty.dueldimension.DuelDimension.MOD_ID, "underskin");

    private static DynamicTexture loaded;
    private static boolean tried;
    private static String problem = "";

    private UnderSkin()
    {
    }

    /** Where it lives between sessions, beside the mod's other client settings. */
    public static Path file()
    {
        return net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get()
            .resolve("dueldimension-underskin.png");
    }

    public static boolean present()
    {
        return texture() != null;
    }

    /** What went wrong with the last import, or empty. */
    public static String problem()
    {
        return problem;
    }

    /**
     * The imported skin, or null if there is none.
     * <p>
     * Read from disk once and kept: this is asked for every frame a duelist in
     * an outfit is on screen.
     */
    public static ResourceLocation texture()
    {
        if(!tried)
        {
            tried = true;
            load(file());
        }
        return loaded == null ? null : TEXTURE;
    }

    /**
     * Takes a PNG as the under-skin, copying it in so the original can move or
     * be deleted without the game losing it.
     *
     * @return null on success, else what was wrong with it
     */
    public static String importFrom(Path source)
    {
        try
        {
            byte[] bytes = Files.readAllBytes(source);
            try(NativeImage check = NativeImage.read(new java.io.ByteArrayInputStream(bytes)))
            {
                // The two shapes Minecraft accepts. A 64x32 is converted the
                // way the game converts one, rather than refused: a player
                // editing an old skin should not have to know why it failed.
                if(check.getWidth() != 64 || (check.getHeight() != 64 && check.getHeight() != 32))
                {
                    return "A skin must be 64x64 (or 64x32).";
                }
            }
            Files.createDirectories(file().getParent());
            Files.write(file(), bytes);
            release();
            tried = true;
            load(file());
            return loaded == null ? problem : null;
        }
        catch(Exception unreadable)
        {
            problem = "Could not read that file: " + unreadable.getMessage();
            return problem;
        }
    }

    /** Forgets the imported skin; the player is drawn with their own again. */
    public static void clear()
    {
        release();
        try
        {
            Files.deleteIfExists(file());
        }
        catch(Exception undeletable)
        {
            problem = "Could not delete " + file() + ": " + undeletable.getMessage();
        }
        tried = true;
        problem = "";
    }

    private static void release()
    {
        if(loaded != null)
        {
            Minecraft.getInstance().getTextureManager().release(TEXTURE);
            loaded.close();
            loaded = null;
        }
    }

    private static void load(Path path)
    {
        if(!Files.isRegularFile(path))
        {
            return;
        }
        try(java.io.InputStream stream = Files.newInputStream(path))
        {
            NativeImage image = NativeImage.read(stream);
            if(image.getHeight() == 32)
            {
                image = widen(image);
            }
            loaded = new DynamicTexture(image);
            Minecraft.getInstance().getTextureManager().register(TEXTURE, loaded);
            problem = "";
        }
        catch(Exception unreadable)
        {
            problem = "Could not load the under-skin: " + unreadable.getMessage();
        }
    }

    /**
     * A 64x32 skin on the 64x64 layout.
     * <p>
     * The old format drew both arms and both legs from one mirrored half, so
     * the left limbs have no region of their own and would sample empty
     * texture. Mirroring the right limb into the left slot is what Minecraft's
     * own converter does.
     */
    private static NativeImage widen(NativeImage old)
    {
        NativeImage wide = new NativeImage(64, 64, true);
        wide.fillRect(0, 0, 64, 64, 0);
        for(int y = 0; y < 32; y++)
        {
            for(int x = 0; x < 64; x++)
            {
                wide.setPixelRGBA(x, y, old.getPixelRGBA(x, y));
            }
        }
        // copyRect works within one image, so the mirroring happens after the
        // old half is in place. Right leg (0,16) to left leg (16,48), right arm
        // (40,16) to left arm (32,48); the offsets are deltas, not positions.
        wide.copyRect(0, 16, 16, 32, 16, 16, true, false);
        wide.copyRect(40, 16, -8, 32, 16, 16, true, false);
        old.close();
        return wide;
    }
}
