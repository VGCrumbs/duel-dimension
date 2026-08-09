package de.cas_ual_ty.dueldimension.fabric;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Fabric fork's common entry point.
 * <p>
 * This fork ports the mod inside-out: the engine core (ocgcore binding, message
 * decoding, duel logic, bots) is loader-agnostic and came across first with its
 * tests; everything that touches Minecraft — registries, networking,
 * persistence, and two years of client API drift — lands in the phases
 * {@code PORTING.md} lays out.
 * <p>
 * What is wired here is what a server needs: the registries, the network, the
 * commands, the player lifecycle and the tick. The duel screen is the piece
 * still outstanding, and it is a client one.
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
        // Blocks bring their own BlockItems, and the creative tabs name them,
        // so they register before the tabs and after the item components.
        de.cas_ual_ty.dueldimension.DdBlocks.register();
        de.cas_ual_ty.dueldimension.DdItemGroup.register();
        de.cas_ual_ty.dueldimension.DdSounds.register();
        de.cas_ual_ty.dueldimension.DdEntityTypes.register();
        de.cas_ual_ty.dueldimension.DdTileEntityTypes.register();
        de.cas_ual_ty.dueldimension.DdContainerTypes.register();
        de.cas_ual_ty.dueldimension.DdDuelRegistries.register();

        // Data attachments, before anything can load a world. Each type lives on
        // a nested class and exists only once that class is touched; a world
        // saved with one and loaded before it is registered has that data
        // DISCARDED, with a single "Found unknown attachment type" warning. That
        // is how a whole card collection went missing once.
        de.cas_ual_ty.dueldimension.duel.profile.DuelProfiles.register();
        de.cas_ual_ty.dueldimension.shop.DuelPoints.register();
        de.cas_ual_ty.dueldimension.util.Cooldowns.register();

        // Every message is declared before anything can send one, and the
        // server's handlers with them: a payload registered without a receiver
        // is a packet that arrives and is dropped.
        de.cas_ual_ty.dueldimension.net.DdNetwork.register();
        de.cas_ual_ty.dueldimension.net.DdNetwork.registerServerHandlers();

        // A joining player is told what they own before they can open an editor.
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register(
            (handler, sender, server) ->
        {
            net.minecraft.server.level.ServerPlayer player = handler.getPlayer();
            de.cas_ual_ty.dueldimension.net.ProfilePayloads.sync(player);

            // The profile remembers the outfit, but the client render map is
            // intentionally cleared on every disconnect. Re-announce the
            // persisted choice on join so this client restores its own outfit
            // and every connected client sees it too.
            de.cas_ual_ty.dueldimension.duel.outfit.WornOutfits.announce(player);
        });

        // A leaving player is released by everything that was holding them.
        // Forge did this from PlayerLoggedOutEvent, and the order is its order.
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register(
            (handler, server) ->
        {
            net.minecraft.server.level.ServerPlayer player = handler.getPlayer();
            de.cas_ual_ty.dueldimension.duel.match.DuelLobby.forget(player);
            de.cas_ual_ty.dueldimension.duel.npc.DuelistDuels.abandon(player);
            // Saved, but not forgotten -- there is nothing to forget. Forge
            // kept profiles in a map that had to be cleared on logout or it
            // leaked; here a profile is a data attachment on the player, so it
            // leaves with them. The save still matters: it is what flushes the
            // throttled write to disk before the player is gone.
            de.cas_ual_ty.dueldimension.duel.profile.DuelProfiles.save(player);

            // Everyone else is told they are no longer wearing anything, so a
            // player who leaves does not linger in an outfit on other clients.
            for(net.minecraft.server.level.ServerPlayer everyone
                : server.getPlayerList().getPlayers())
            {
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(everyone,
                    new de.cas_ual_ty.dueldimension.duel.outfit.OutfitMessages.Worn(
                        player.getUUID(), ""));
            }
        });

        // ---- the bootstrap Forge ran before anything could use a card ----
        //
        // Forge spread this over four lifecycle callbacks (a config-baking
        // event, initFolders, initFiles, and common setup). Fabric has one
        // entry point, so the sequence is written out; the ORDER is Forge's and
        // matters at every step.

        // "Baking the common config": the statics that other code reads are
        // filled from the config file. dbSourceUrl has to be set before the
        // database is asked to update itself, or it reads as "no source" and
        // the download is silently skipped.
        de.cas_ual_ty.dueldimension.DuelDimension.dbSourceUrl =
            de.cas_ual_ty.dueldimension.DuelDimension.commonConfig().dbSourceUrl.get();

        // A user agent, before any HTTP happens. Requests without one are
        // refused by the host the database comes from.
        de.cas_ual_ty.dueldimension.util.DdIOUtil.setAgent();

        // The card database: check the local version against the remote one,
        // download it if it is missing or stale, then read every card into
        // PROPERTIES_LIST. Nothing that touches a card works before this --
        // a CardHolder resolves its id through that list, so with an empty
        // list every card in the game is an unknown card.
        de.cas_ual_ty.dueldimension.DdDatabase.initDatabase();

        // The background workers that fetch and rescale card images.
        de.cas_ual_ty.dueldimension.task.WorkerManager.init();

        // A profile has to be re-sent whenever the client's copy is thrown
        // away. Joining is handled above; these are the other two times it
        // happens. The profile itself survives -- it is an attachment marked
        // copyOnDeath -- but the CLIENT's copy does not, and an editor opened
        // after a respawn would show an empty collection without this.
        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register(
            (oldPlayer, newPlayer, alive) ->
                de.cas_ual_ty.dueldimension.net.ProfilePayloads.sync(newPlayer));
        // Renamed in 26.2: "world" became "level" throughout this API.
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register(
            (player, origin, destination) ->
                de.cas_ual_ty.dueldimension.net.ProfilePayloads.sync(player));

        // The mod's commands.
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register(
            (dispatcher, registry, environment) ->
            {
                de.cas_ual_ty.dueldimension.serverutil.DdCommand.registerCommand(dispatcher);
                de.cas_ual_ty.dueldimension.duel.match.DuelCommand.register(dispatcher);
                de.cas_ual_ty.dueldimension.shop.DuelPointsCommand.register(dispatcher);
                de.cas_ual_ty.dueldimension.duel.profile.FreeModeCommand.register(dispatcher);
            });

        // The duelist self-test, once the server is actually up.
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(
            de.cas_ual_ty.dueldimension.duel.npc.DuelistDuels::maybeStartSelfTest);

        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(
            server ->
        {
            // Duel-reward cooldowns count down. Forge ticked each player from
            // its own per-player event; there is no such event here, so one
            // pass over the player list does the same work.
            de.cas_ual_ty.dueldimension.util.Cooldowns.tick(server);

            // Duels against a duelist run on their own threads; this is where
            // what they produced is handed back to the game thread. Both of
            // these were Forge's END-phase server tick, in this order.
            de.cas_ual_ty.dueldimension.duel.npc.DuelistDuels.tick(server);
            de.cas_ual_ty.dueldimension.duel.match.DuelInvites.tick(server);

            // After the duels, never before: a duel that concludes this tick
            // marks its loser inside DuelistDuels.tick, and the queue has to
            // see that mark on the same tick it is made rather than a tick
            // late.
            de.cas_ual_ty.dueldimension.duel.orichalcos.OrichalcosSouls.tick(server);
        });

        LOG.info("Duel Dimension (Fabric fork): engine core, items, sounds, commands,"
            + " duelists and the profile network are up; remaining phases per PORTING.md");
    }
}
