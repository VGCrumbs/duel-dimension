package de.cas_ual_ty.dueldimension.clientutil;

import de.cas_ual_ty.dueldimension.DuelDimension;
import de.cas_ual_ty.dueldimension.util.ISidedProxy;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.io.File;

/**
 * The client half of the mod: where card images live, and how big they are.
 * <p>
 * The Forge version of this class was nine hundred lines, because on that
 * loader a proxy is also where every client event subscription lives — key
 * bindings, renderers, screen opening, the tick hook. On Fabric those are
 * registered by the client entrypoint instead, so what belongs here is only
 * what the name says: the client-side state the rest of the mod reads.
 * <p>
 * Right now that is the image pipeline's settings. The sizes and folders are
 * static because {@link ImageHandler} reads them from its worker threads, and
 * a worker that had to reach through a proxy instance to find out where to
 * write a PNG would be a worker holding a reference to the game.
 */
public class ClientProxy implements ISidedProxy
{
    // ---- image sizes ----
    //
    // One image per (card, size), so these decide both how sharp a card looks
    // and how much disk the cache takes. The defaults are the Forge build's.

    /** Card art on an inspect screen or a preview panel. */
    public static int activeCardInfoImageSize = 256;
    /** Card art on an item, which is drawn small and drawn a lot. */
    public static volatile int activeCardItemImageSize = 64;
    /** Card art on the duel field and in the deck editor's grids. */
    public static int activeCardMainImageSize = 256;

    public static int activeSetInfoImageSize = 256;
    public static volatile int activeSetItemImageSize = 64;

    /**
     * Whether a downloaded image survives a restart.
     * <p>
     * On by default: the database is twenty thousand cards, and fetching one
     * again because the game closed would be rude to both the player and the
     * server holding the images.
     */
    public static boolean keepCachedImages = true;

    // ---- where they live ----

    public static File imagesParentFolder = new File(DuelDimension.mainFolder, "images");
    public static File cardImagesFolder = new File(imagesParentFolder, "cards");
    public static File setImagesFolder = new File(imagesParentFolder, "sets");
    public static File rarityImagesFolder = new File(imagesParentFolder, "rarities");

    /**
     * The originals, before they are scaled to the sizes above. Kept separately
     * so changing a size re-scales from the source rather than re-downloading.
     */
    public static File rawCardImagesFolder = new File(cardImagesFolder, "raw");
    public static File rawSetImagesFolder = new File(setImagesFolder, "raw");
    public static File rawRarityImagesFolder = new File(rarityImagesFolder, "raw");

    public static Minecraft getMinecraft()
    {
        return Minecraft.getInstance();
    }

    @Override
    public Player getClientPlayer()
    {
        return Minecraft.getInstance().player;
    }
}
