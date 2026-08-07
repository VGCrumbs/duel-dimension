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
        // Order matters exactly once here: the creative tabs name items, so the
        // items have to exist first. Everything else is independent.
        de.cas_ual_ty.dueldimension.DdComponents.register();
        de.cas_ual_ty.dueldimension.DdItems.register();
        de.cas_ual_ty.dueldimension.DdItemGroup.register();
        de.cas_ual_ty.dueldimension.DdSounds.register();
        de.cas_ual_ty.dueldimension.DdEntityTypes.register();

        // Every message is declared before anything can send one, and the
        // server's handlers with them: a payload registered without a receiver
        // is a packet that arrives and is dropped.
        de.cas_ual_ty.dueldimension.net.DdNetwork.register();
        de.cas_ual_ty.dueldimension.net.DdNetwork.registerServerHandlers();

        // A joining player is told what they own before they can open an editor.
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register(
            (handler, sender, server) ->
                de.cas_ual_ty.dueldimension.net.ProfilePayloads.sync(handler.getPlayer()));

        LOG.info("Duel Dimension (Fabric fork): engine core, items, sounds and the"
            + " profile network are up; remaining phases per PORTING.md");
    }
}
