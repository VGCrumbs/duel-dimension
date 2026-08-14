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
            //
            // Nothing opens a per-tick allowance here any more. The ration that
            // used to be reset from this line was a nanosecond budget spent per
            // FRAME and refilled per TICK, so one allowance was shared by three
            // frames at 60 fps and twelve at 240. The decode is on a worker now
            // and the uploads are counted per frame in CardImageManager, where
            // they are actually spent.
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

        // The hotbar and its item name are hidden while a duel is on: the
        // bottom of the screen belongs to the hand, and a duellist locked in
        // place with their hands on a duel has no use for a hotbar. Replaced
        // rather than removed, so it is the mod's own state that hides it and
        // everything comes back the moment the duel ends.
        for(net.minecraft.resources.Identifier hidden : new net.minecraft.resources.Identifier[] {
            net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.HOTBAR,
            net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.HELD_ITEM_TOOLTIP})
        {
            net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
                .replaceElement(hidden, original -> (extractor, delta) ->
            {
                if(!de.cas_ual_ty.dueldimension.clientutil.overworld.ClientDuelField.locked())
                {
                    original.extractRenderState(extractor, delta);
                }
            });
        }

        // Your hand during an overworld duel. addLast for the same reason: it
        // is the one part of a world duel that must not be painted over.
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(
            net.minecraft.resources.Identifier.fromNamespaceAndPath(
                de.cas_ual_ty.dueldimension.DuelDimension.MOD_ID, "duel_hand"),
            new de.cas_ual_ty.dueldimension.clientutil.overworld.HandHud());

        // The seal is drawn in the world, not in a GUI. WorldRenderEvents is
        // gone in 26.2; COLLECT_SUBMITS is where geometry is handed to the
        // renderer for the frame, and its context carries both the pose stack
        // and the submit collector.
        net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.COLLECT_SUBMITS
            .register(de.cas_ual_ty.dueldimension.clientutil.OrichalcosRenderer::render);

        // Where to stand for an overworld duel. Same event, same reasons; it
        // returns immediately unless this client has been sent a field.
        net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.COLLECT_SUBMITS
            .register(de.cas_ual_ty.dueldimension.clientutil.overworld
                .PlacementGuideRenderer::render);

        // The arena markers a builder has put down, and the board they
        // describe while TAB is held. Registered before the duel's own board so
        // a preview never draws over a duel actually being played.
        net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.COLLECT_SUBMITS
            .register(de.cas_ual_ty.dueldimension.clientutil.overworld.ArenaRenderer::render);

        // The board itself, once both duellists are standing at it. Registered
        // after the guide so it draws over the markers in the frame they both
        // exist, which is the frame the duel begins.
        net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.COLLECT_SUBMITS
            .register(de.cas_ual_ty.dueldimension.clientutil.overworld
                .OverworldBoardRenderer::render);

        // THIS client's own card database, which is a separate copy from the
        // server's and is what every card the player looks at is drawn from.
        // A client whose download failed shows a world of unknown cards and
        // says nothing about why; the server cannot tell them, because from
        // where it is standing everything is fine. Skipped in singleplayer,
        // where the integrated server has already said it about the same
        // database in the same JVM.
        // Overworld duels: follow what the duellist is looking at, and act on
        // it when they press the key. Both are cheap no-ops unless this client
        // is actually standing at a board.
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK
            .register(client ->
        {
            de.cas_ual_ty.dueldimension.clientutil.overworld.ClientDuelTargeting.tick(client);
            // The hotbar is hidden during a duel, so the slot it is on must not
            // wander either: scrolling an invisible hotbar would change what is
            // in hand without anyone seeing it happen, and the duel disk is in
            // one of those slots.
            de.cas_ual_ty.dueldimension.clientutil.overworld.ClientDuelField.holdHotbar(client);
            // The board's own ending: it waits for the last animation, says who
            // won, fades, and only then goes.
            de.cas_ual_ty.dueldimension.clientutil.overworld.ClientDuelField.advanceEnding();
            // Swapping between the two views of the duel. Remembered, so the
            // screen is not handed back the moment the board could take the
            // question -- which is right when it opened by itself and wrong
            // when the player asked for it.
            while(de.cas_ual_ty.dueldimension.clientutil.hub.HubKeybinds.DUEL_VIEW.consumeClick())
            {
                de.cas_ual_ty.dueldimension.clientutil.overworld.ClientDuelField
                    .toggleScreen(client);
            }

            // A click while the camera is still the player's acts on whatever
            // the crosshair is on. Consumed so it does not also reach the world
            // as a swing; the server refuses a locked duellist's block
            // interactions anyway, and this stops the arm swinging at nothing.
            if(de.cas_ual_ty.dueldimension.clientutil.overworld.ClientDuelField.locked()
                && client.gui.screen() == null)
            {
                // consumeClick drains the queued presses, which is all that is
                // needed: with the queue empty the game's own handler finds
                // nothing to act on, so the click does not also swing at the
                // air. release() is not ours to call -- it is protected.
                while(client.options.keyAttack.consumeClick())
                {
                    de.cas_ual_ty.dueldimension.clientutil.overworld.CrosshairAction
                        .click(client, false);
                }
                while(client.options.keyUse.consumeClick())
                {
                    de.cas_ual_ty.dueldimension.clientutil.overworld.CrosshairAction
                        .click(client, true);
                }
            }

            // Drained and dropped. The cursor is what a duel looks like by
            // default now -- holding the camera key is what takes it away --
            // so there is nothing left for a press to hand over. Still drained,
            // because a count nobody consumes just accumulates.
            while(de.cas_ual_ty.dueldimension.clientutil.hub.HubKeybinds.DUEL_ACT.consumeClick())
            {
            }
        });

        // A board belongs to the world it was built in. Remembering one across
        // a disconnect would draw it into the next world the player joins.
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT
            .register((handler, client) ->
                de.cas_ual_ty.dueldimension.clientutil.overworld.ClientDuelField.clear());

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
                de.cas_ual_ty.dueldimension.clientutil.ClientWornDisks.clear();
                de.cas_ual_ty.dueldimension.clientutil.OrichalcosRenderer.clear();
                // Both halves of EDOPro's ClearTexture (image_manager.cpp:339-367):
                // bump the epoch so everything in flight is abandoned and freed
                // off the render thread, then release the textures and forget
                // their status so a later visit asks for them again.
                de.cas_ual_ty.dueldimension.clientutil.CardImageManager.clearTexture();
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
