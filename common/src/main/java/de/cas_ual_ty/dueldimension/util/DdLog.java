package de.cas_ual_ty.dueldimension.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The mod's log, for code that does not know which Minecraft it is running on.
 * <p>
 * {@code DuelDimension.log()} was the only way to write a line, and
 * {@code DuelDimension} is the Fabric entrypoint holder — it names Minecraft, so
 * it belongs to a platform. Anything in the shared core that wanted to log was
 * therefore pinned to a platform too, over nothing but a string.
 * <p>
 * That was not hypothetical. {@link
 * de.cas_ual_ty.dueldimension.ocg.HeadlessDuelRunner} — which drives a complete
 * duel outside Minecraft, and is what every engine test is built on — imported
 * no Minecraft class anywhere and still could not be shared, because of two
 * calls to {@code DuelDimension.debug}. With it stuck on one platform, so were
 * the tests, and the rules engine was only ever exercised on one Java version
 * despite shipping in both builds.
 * <p>
 * {@code DuelDimension.log/debug/warn} delegate here, so there is one
 * implementation and one prefix rather than two that can drift.
 */
public final class DdLog
{
    /**
     * The mod id, and the logger's name.
     * <p>
     * Defined here rather than in {@code DuelDimension} because the shared half
     * cannot see that class. {@code DuelDimension.MOD_ID} reads from this one,
     * so the string exists once.
     */
    public static final String MOD_ID = "dueldimension";

    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private DdLog()
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
