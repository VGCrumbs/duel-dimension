package de.cas_ual_ty.dueldimension;


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
    /**
     * The mod's own source of randomness.
     * <p>
     * Every duel seeds its shuffles from this rather than from a fresh Random
     * per duel: two duels starting in the same millisecond would otherwise draw
     * the same opening hand, which is exactly the kind of thing nobody notices
     * until it happens in a tournament.
     */
    public static final java.util.Random random = new java.util.Random();

    /**
     * Read from {@link de.cas_ual_ty.dueldimension.util.DdLog}, which the
     * shared half can see and this class cannot be seen BY. One definition of
     * the string, in the module that both platforms share.
     */
    public static final String MOD_ID = de.cas_ual_ty.dueldimension.util.DdLog.MOD_ID;

    /** The id in capitals, for the places that name a thread after the mod. */
    public static final String MOD_ID_UP = MOD_ID.toUpperCase(java.util.Locale.ROOT);


    /**
     * Where the card database is unpacked, inside the game directory.
     * <p>
     * Plain files rather than resources: the database is tens of thousands of
     * cards that arrive after the game has started, and it is shared by the
     * client and the server of an integrated game.
     * <p>
     * Resolved against {@link de.cas_ual_ty.dueldimension.util.GameDir} rather
     * than named relatively. {@code new File("ydm_db")} means "ydm_db under the
     * process working directory", which is the game directory only by
     * coincidence -- the coincidence holds for a vanilla launcher and breaks for
     * anything that passes {@code --gameDir} or starts a server from elsewhere,
     * and the config was already going to the loader's directory either way.
     * <p>
     * All five are assigned together, here, because the four below are derived
     * from the first: moving {@code mainFolder} on its own would leave them
     * pointing at the old place and the database would read as "(cards folder)
     * does not exist".
     */
    public static File mainFolder = de.cas_ual_ty.dueldimension.util.GameDir.file("ydm_db");
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
        de.cas_ual_ty.dueldimension.util.DdLog.log(message);
    }

    public static void debug(String message)
    {
        de.cas_ual_ty.dueldimension.util.DdLog.debug(message);
    }

    public static void warn(String message)
    {
        de.cas_ual_ty.dueldimension.util.DdLog.warn(message);
    }
}
