package de.cas_ual_ty.dueldimension.clientutil;

import de.cas_ual_ty.dueldimension.DuelDimension;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.IoSupplier;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Set;

/**
 * Serves card, set and rarity images out of the runtime cache as if they were
 * shipped assets.
 * <p>
 * The mod addresses card art as {@code dueldimension:textures/item/<name>.png}
 * — a resource path — but the files are downloaded and scaled long after
 * startup and live in the mod's images folder. This is the bridge: a resource
 * pack whose contents are a directory that fills up while the game runs.
 * <p>
 * <b>Smaller than the Forge version, because the API asks a better question.</b>
 * That one extended {@code FilePackResources} and was handed a raw, platform
 * dependent path string, so it had to normalise Windows backslashes, check and
 * strip an {@code assets/dueldimension/textures/item/} prefix by hand, and throw
 * a bespoke not-found exception. Here {@code getResource} is given a
 * {@link PackType} and an {@link ResourceLocation} that is already parsed, and returns
 * null for "not mine" — so the string surgery, the {@code CharMatcher} and the
 * exception type all go away with nothing lost.
 */
public class DdCardResourcePack implements PackResources
{
    /** The one folder inside the namespace this pack answers for. */
    public static final String PATH_PREFIX = "textures/item/";
    /** Same PNG files as PATH_PREFIX, but with metadata selecting bilinear sampling. */
    public static final String SMOOTH_PATH_PREFIX = "textures/item_smooth/";
    private static final byte[] SMOOTH_METADATA =
        "{\"texture\":{\"blur\":true,\"clamp\":true}}"
            .getBytes(java.nio.charset.StandardCharsets.UTF_8);

    private final PackLocationInfo location;

    public DdCardResourcePack(PackLocationInfo location)
    {
        this.location = location;
    }

    /**
     * No pack.png and no pack.mcmeta.
     * <p>
     * The Forge version built a pack metadata JSON by hand so the loader could
     * read a description and a format number off it. Nothing asks here: the
     * {@link DdResourcePackFinder} states the metadata when it builds the
     * {@code Pack}, rather than writing it down for something to read back.
     */
    @Override
    public IoSupplier<java.io.InputStream> getRootResource(String... path)
    {
        return null;
    }

    @Override
    public IoSupplier<java.io.InputStream> getResource(PackType type, ResourceLocation id)
    {
        if(type != PackType.CLIENT_RESOURCES || !id.getNamespace().equals(DuelDimension.MOD_ID))
        {
            return null;
        }

        String path = id.getPath();
        if(path.startsWith(SMOOTH_PATH_PREFIX) && path.endsWith(".png.mcmeta"))
        {
            return () -> new java.io.ByteArrayInputStream(SMOOTH_METADATA);
        }
        String prefix = path.startsWith(SMOOTH_PATH_PREFIX) ? SMOOTH_PATH_PREFIX
            : path.startsWith(PATH_PREFIX) ? PATH_PREFIX : null;
        if(prefix == null || !path.endsWith(".png"))
        {
            return null;
        }

        File image = getFile(path.substring(prefix.length()));
        if(image == null)
        {
            return null;
        }
        return IoSupplier.create(image.toPath());
    }

    // There was a third prefix here, textures/item_unowned/, whose bytes this
    // class desaturated pixel by pixel and re-encoded as a PNG -- inline on the
    // render thread, because that ran inside the IoSupplier MC calls from
    // Resource.open(). It cost 29ms for a 512px preview against 3.6ms for the
    // same card owned, and needed a 16MB LRU of the encoded results to stop
    // paying it twice. All of it is gone: an unowned card is the owned card's
    // file drawn through a desaturating fragment shader. See UnownedPipelines,
    // which is also where the arithmetic that used to live here now lives, as
    // the reference the shaders are a transliteration of.

    /**
     * Nothing is listed.
     * <p>
     * As on Forge: listing is what fonts and sounds need, and this pack holds
     * neither. Enumerating twenty thousand card images so that nothing reads
     * the list would be a real cost for no gain.
     */
    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput output)
    {
    }

    @Override
    public Set<String> getNamespaces(PackType type)
    {
        return type == PackType.CLIENT_RESOURCES ? Set.of(DuelDimension.MOD_ID) : Set.of();
    }

    @Override
    public <T> T getMetadataSection(MetadataSectionSerializer<T> type)
    {
        return null;
    }

    @Override
    public PackLocationInfo location()
    {
        return location;
    }

    @Override
    public void close()
    {
    }

    /**
     * The file behind an image name, from whichever cache holds it.
     * <p>
     * Cards, then sets, then rarities — the order the Forge version used, and
     * it matters only in that the three folders never hold the same name.
     */
    @Nullable
    private File getFile(String filename)
    {
        File image = ImageHandler.getCardFile(filename);
        if(image.exists())
        {
            return image;
        }

        image = ImageHandler.getSetFile(filename);
        if(image.exists())
        {
            return image;
        }

        image = ImageHandler.getRarityFile(filename);
        return image.exists() ? image : null;
    }
}
