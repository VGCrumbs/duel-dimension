package de.cas_ual_ty.dueldimension;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * The mod's shared identity: its id, its log, and where its database lives.
 * <p>
 * Kept under the name the Forge tree uses because a hundred and seventy files
 * say {@code DuelDimension.MOD_ID} and a hundred say {@code DuelDimension.log}.
 * Renaming it would have meant touching every one of them to say the same thing
 * a different way, and a port has enough real changes in it without that.
 * <p>
 * What it is NOT is the mod entry point. On Forge this class was also the
 * {@code @Mod}: it owned the registries, the network channel, the config and
 * the sided proxy, all of which are loader-specific and none of which belong to
 * something this widely referenced. Those live in
 * {@link de.cas_ual_ty.dueldimension.fabric.DuelDimensionFabric} and the phases
 * around it. What is left here is the part every file genuinely shares.
 */
public final class DuelDimension
{
    public static final String MOD_ID = "dueldimension";

    /** The id in capitals, for the places that name a thread after the mod. */
    public static final String MOD_ID_UP = MOD_ID.toUpperCase(java.util.Locale.ROOT);

    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /**
     * Where the card database is unpacked, relative to the game directory.
     * <p>
     * Plain files rather than resources: the database is tens of thousands of
     * cards that arrive after the game has started, and it is shared by the
     * client and the server of an integrated game.
     */
    public static File mainFolder = new File("ydm_db");
    public static File cardsFolder = new File(mainFolder, "cards");
    public static File setsFolder = new File(mainFolder, "sets");
    public static File distributionsFolder = new File(mainFolder, "distributions");
    public static File raritiesFolder = new File(mainFolder, "rarities");

    /** Where a missing database is fetched from; set from config once it lands. */
    public static String dbSourceUrl = "";

    /**
     * Whether the background workers should keep going, and whether they should
     * stop right now.
     * <p>
     * Volatile because the workers read them on their own threads and the game
     * writes them on its own. They live here rather than with the entry point
     * because the workers are loader-agnostic and so is the question.
     */
    public static volatile boolean continueTasks = true;
    public static volatile boolean forceTaskStop = false;

    /**
     * The side this copy of the mod is running on.
     * <p>
     * Forge chose between two of these with {@code DistExecutor}; Fabric has no
     * equivalent because it does not need one -- a client entrypoint only runs
     * on a client. So the server implementation is simply the default, and
     * {@code DuelDimensionFabricClient} replaces it on the way up. Code that
     * asks the proxy for something a server cannot do gets a no-op, which is
     * the same answer it got before.
     */
    public static de.cas_ual_ty.dueldimension.util.ISidedProxy proxy =
        new de.cas_ual_ty.dueldimension.serverutil.ServerProxy();

    /**
     * The settings.
     * <p>
     * Read on first touch rather than held in a field, so that merely loading
     * this class does not go looking for a config directory -- a unit test
     * asking a question about decks has no game directory to find one in.
     */
    public static CommonConfig commonConfig()
    {
        return CommonConfig.get();
    }

    private DuelDimension()
    {
    }

    public static void log(String message)
    {
        LOGGER.info("[{}] {}", MOD_ID, message);
    }

    public static void debug(String message)
    {
        LOGGER.debug("[{}] {}", MOD_ID, message);
    }

    public static void warn(String message)
    {
        LOGGER.warn("[{}] {}", MOD_ID, message);
    }
}
