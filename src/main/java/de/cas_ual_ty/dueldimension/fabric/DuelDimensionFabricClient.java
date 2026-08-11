package de.cas_ual_ty.dueldimension.fabric;

import net.fabricmc.api.ClientModInitializer;

/** Client entry point; fills in as the client phases of PORTING.md land. */
public class DuelDimensionFabricClient implements ClientModInitializer
{
    @Override
    public void onInitializeClient()
    {
        // The client's proxy, FIRST. DuelDimension holds a ServerProxy until
        // something replaces it, and every sided hook -- sending a duel
        // message, wrapping a duel manager, opening the duel screen -- answers
        // with the server's no-op until it is. Nothing below this line works
        // without it.
        de.cas_ual_ty.dueldimension.DuelDimension.proxy =
            new de.cas_ual_ty.dueldimension.clientutil.ClientProxy();

        // Settings first: the image pipeline and the animations read them.
        de.cas_ual_ty.dueldimension.clientutil.ClientProxy.loadConfig();
        de.cas_ual_ty.dueldimension.clientutil.ClientProxy.initClient();

        // World chat, mirrored so a duel screen can show it beside duel chat.
        // Both kinds: CHAT is a player talking, GAME is everything else
        // (deaths, joins, /say), and the Forge event this replaces saw both.
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.CHAT.register(
            (message, signed, sender, params, receptionTimestamp) ->
                de.cas_ual_ty.dueldimension.clientutil.ClientProxy.rememberChatMessage(message));
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register(
            (message, overlay) ->
            {
                if(!overlay)
                {
                    de.cas_ual_ty.dueldimension.clientutil.ClientProxy
                        .rememberChatMessage(message);
                }
            });

        // Duel playback, every client tick. This is the heartbeat of a duel on
        // the client: the queue that updates and prompts are put into is
        // drained HERE, and the board snapshot the screen draws is only ever
        // assigned from that drain. Without it a duel runs to completion on
        // the server while the client shows an empty field and 0 life points
        // -- the packets arrive, they just never get played.
        //
        // Deliberately not in render: playback has to survive the screen being
        // closed, or reopening it loses every board commit still queued.
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK
            .register(client ->
        {
            de.cas_ual_ty.dueldimension.clientutil.DuelClientState.tickPlayback();
            de.cas_ual_ty.dueldimension.clientutil.HitchWatch.tick();
            de.cas_ual_ty.dueldimension.clientutil.OrichalcosRenderer.tick();
            de.cas_ual_ty.dueldimension.clientutil.CardPreloadJob.tick();
            // Card textures were never released by anything; this is what
            // keeps a long browse from growing GPU memory without bound.
            de.cas_ual_ty.dueldimension.clientutil.CardTextureCache.sweep();
        });

        // The preload's progress bar. addLast, NOT attachElementBefore: an
        // ATTACHED element is bound to its host's visibility, and the host I
        // picked was the experience bar -- which is not drawn at all in
        // creative mode, so the bar silently never appeared. This is an
        // element in its own right, drawn last so nothing paints over it.
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(
            net.minecraft.resources.Identifier.fromNamespaceAndPath(
                de.cas_ual_ty.dueldimension.DuelDimension.MOD_ID, "preload_progress"),
            new de.cas_ual_ty.dueldimension.clientutil.PreloadHud());

        // The seal is drawn in the world, not in a GUI. WorldRenderEvents is
        // gone in 26.2; COLLECT_SUBMITS is where geometry is handed to the
        // renderer for the frame, and its context carries both the pose stack
        // and the submit collector.
        net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.COLLECT_SUBMITS
            .register(de.cas_ual_ty.dueldimension.clientutil.OrichalcosRenderer::render);

        // THIS client's own card database, which is a separate copy from the
        // server's and is what every card the player looks at is drawn from.
        // A client whose download failed shows a world of unknown cards and
        // says nothing about why; the server cannot tell them, because from
        // where it is standing everything is fine. Skipped in singleplayer,
        // where the integrated server has already said it about the same
        // database in the same JVM.
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN
            .register((handler, sender, client) ->
            {
                String problem = de.cas_ual_ty.dueldimension.DdDatabase.problem();
                if(problem == null || client.hasSingleplayerServer())
                {
                    return;
                }
                client.gui.chatListener().handleSystemMessage(
                    net.minecraft.network.chat.Component.literal(
                        "Duel Dimension could not set up your card database: " + problem
                            + ". Every card will show as an unknown card until that is fixed;"
                            + " the game log has the details.")
                        .withStyle(net.minecraft.ChatFormatting.RED), false);
            });

        // Leaving a server forgets what everyone was wearing. The map is keyed
        // by UUID and nothing else clears it, so without this the next server
        // starts with the last one's outfits on strangers who share a UUID.
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT
            .register((handler, client) ->
            {
                de.cas_ual_ty.dueldimension.duel.outfit.WornOutfits.clear();
                de.cas_ual_ty.dueldimension.clientutil.OrichalcosRenderer.clear();
                de.cas_ual_ty.dueldimension.clientutil.CardTextureCache.clear();
                // A duel interrupted by a disconnect never reports itself over,
                // so nothing else would ever stop its music -- it would play on
                // over the title screen.
                de.cas_ual_ty.dueldimension.clientutil.DuelMusic.stopNow();
            });

        de.cas_ual_ty.dueldimension.clientutil.hub.HubKeybinds.register();

        // The two entity renderers. Forge registered these from an event that
        // fired once per entity type; Fabric takes them directly.
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
            de.cas_ual_ty.dueldimension.DdEntityTypes.DUELIST,
            de.cas_ual_ty.dueldimension.duel.npc.DuelistRenderer::new);
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
            de.cas_ual_ty.dueldimension.DdEntityTypes.DUEL,
            de.cas_ual_ty.dueldimension.clientutil.DuelEntityRenderer::new);

        // The picture-in-picture region the board is drawn through. Without it
        // nothing can draw a quad that is not an axis-aligned rectangle.
        de.cas_ual_ty.dueldimension.clientutil.BoardPip.register();

        // Bind each container menu to the screen that draws it.
        de.cas_ual_ty.dueldimension.clientutil.DdScreens.register();

        // Register the card and card-set item-model types so the card/set items
        // can select them from their ClientItem JSON and show their card faces.
        de.cas_ual_ty.dueldimension.clientutil.DdCardModels.register();

        // Told what everyone is wearing, and what the server holds for us.
        de.cas_ual_ty.dueldimension.net.DdNetwork.registerClientHandlers();

        // The outfit pass, on every player renderer. Forge subscribed to an
        // event that fired once per renderer type; this fires the same way, and
        // the check is the same one -- players only, because an outfit is drawn
        // on a player model and nothing else has one.
        net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback
            .EVENT.register((entityType, renderer, helper, context) ->
        {
            if(entityType == net.minecraft.world.entity.EntityTypes.PLAYER)
            {
                helper.register(new de.cas_ual_ty.dueldimension.clientutil.OutfitLayer(
                    (net.minecraft.client.renderer.entity.RenderLayerParent<
                        net.minecraft.client.renderer.entity.state.AvatarRenderState,
                        net.minecraft.client.model.player.PlayerModel>)renderer));
            }
        });
    }
}
