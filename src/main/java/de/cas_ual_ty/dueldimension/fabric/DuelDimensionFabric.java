package de.cas_ual_ty.dueldimension.fabric;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Fabric fork's common entry point.
 * <p>
 * Deliberately close to empty for now. This fork ports the mod inside-out:
 * the engine core (ocgcore binding, message decoding, duel logic, bots) is
 * loader-agnostic and came across first with its tests; everything that
 * touches Minecraft — registries, networking, persistence, and two years of
 * client API drift — lands in the phases {@code PORTING.md} lays out. Until a
 * phase lands, this class is honest about loading a core and not a game.
 */
public class DuelDimensionFabric implements ModInitializer
{
    /**
     * Both forwarded to {@link de.cas_ual_ty.dueldimension.DuelDimension}, which
     * is where the rest of the mod reads them from. Two names for one id is one
     * too many, and the shared one is the one already written in every file.
     */
    public static final String MOD_ID = de.cas_ual_ty.dueldimension.DuelDimension.MOD_ID;
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize()
    {
        LOG.info("Duel Dimension (Fabric fork): engine core loaded; game-facing phases per PORTING.md");
    }
}
