package de.cas_ual_ty.dueldimension.fabric;

import net.fabricmc.api.ClientModInitializer;

/**
 * Client entry point.
 *
 * <h2>How this differs from 26.2's, and where the differences went</h2>
 *
 * Line for line it is the same file, with four exceptions — each one a place
 * where 26.2 has a registry that 1.21.1 does not, so the same decision has to be
 * made somewhere else:
 *
 * <ul>
 * <li><b>The HUD.</b> 26.2 has a HUD element registry: elements are added by id
 * and existing ones can be replaced. 1.21.1 has {@code HudRenderCallback}, which
 * can only ADD — so the two additions are registrations here and the two
 * REPLACEMENTS (hiding the hotbar and the held-item name during a duel) are
 * injections in {@code DuelHudMixin} instead. Order is registration order, so
 * the hand still draws after the preload bar.
 * <li><b>World geometry.</b> {@code LevelRenderEvents.COLLECT_SUBMITS} became
 * {@code WorldRenderEvents.LAST} — see the note at the registrations for why the
 * obvious choice, AFTER_ENTITIES, is a trap.
 * <li><b>The board's picture-in-picture region.</b> Registered on 26.2, a no-op
 * here; see {@code BoardPip}, which needs no registry because a 1.21.1 screen can
 * draw a quad itself.
 * <li><b>Iris.</b> 26.2 claims the mod's own model pipelines as entity geometry,
 * because Iris matches pipelines by object identity and a mod's copy of one is
 * absent from its map. There are no mod-owned pipelines here: {@code ModelMesh}
 * draws on vanilla's {@code RenderType.entityCutout} and {@code entityTranslucent},
 * which Iris already knows. So the two claims are not made, and {@code IrisCompat}
 * is carried unused rather than deleted — the day this build grows a pipeline of
 * its own, it is the thing that will be needed.
 * </ul>
 *
 * Everything else is a rename: {@code Identifier} to {@code ResourceLocation},
 * {@code setScreenAndShow} to {@code setScreen}, {@code gui.screen()} to
 * {@code screen}.
 */
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
            de.cas_ual_ty.dueldimension.clientutil.DuelClientState.tickSkip();
            // Finishes an install on the thread that draws: the worker cannot
            // touch the model cache, because baking what replaces it reaches for
            // the graphics device.
            de.cas_ual_ty.dueldimension.clientutil.model.ModelInstall.tick();
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

        // The preload's progress bar, then the duel hand. Both draw after the
        // vanilla HUD, in this order, which is what 26.2's two addLast calls buy
        // there: the hand is the one part of a world duel that must not be
        // painted over, so it goes last.
        //
        // No ids here. 26.2 names each element so that another mod can order
        // against it; 1.21.1's callback list has no names and no ordering hooks,
        // so registration order is the whole of it.
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register(
            new de.cas_ual_ty.dueldimension.clientutil.PreloadHud());
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register(
            new de.cas_ual_ty.dueldimension.clientutil.overworld.HandHud());

        // The seal is drawn in the world, not in a GUI.
        //
        // LAST, and NOT AFTER_ENTITIES, which is where these four were and is a
        // trap twice over.
        //
        // FIRST: AFTER_ENTITIES fires when entity geometry has been SUBMITTED,
        // not when it has been drawn. Vanilla flushes only the block-atlas
        // entity buffers by name at that point -- endBatch(entitySolid(
        // LOCATION_BLOCKS)), entityCutout, and so on -- and a mob wearing a SKIN
        // is on entityCutoutNoCull(<that skin>), a different RenderType
        // instance, which waits for the flush-all much later. So a duelist
        // standing behind a monster had not been rasterised yet when the monster
        // wrote its depth, and was rejected when it finally was: the monster is
        // see-through, and the NPC behind it vanished entirely.
        //
        // SECOND, and the reason this is LAST rather than AFTER_TRANSLUCENT: a
        // monster must cull ITSELF and nothing else. The two-pass draw in
        // ModelHologram writes depth on purpose -- that is what makes it show
        // only its nearest surface -- and depth written into the shared buffer
        // rejects whatever is drawn after it. The only way for that to reject
        // nothing is for nothing to come after, which is what LAST means:
        // particles, clouds and weather have all been drawn by then.
        //
        // Being occluded still works, and is worth stating because it sounds
        // like it should not. The depth TEST is untouched -- a wall in front of
        // a monster wrote its depth long before, so the monster's fragments
        // fail LEQUAL and are discarded exactly as they always were. What
        // changes is only who can be rejected BY us, and the answer is nobody.
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.LAST
            .register(de.cas_ual_ty.dueldimension.clientutil.OrichalcosRenderer::render);

        // Where to stand for an overworld duel. Same event, same reasons; it
        // returns immediately unless this client has been sent a field.
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.LAST
            .register(de.cas_ual_ty.dueldimension.clientutil.overworld
                .PlacementGuideRenderer::render);

        // Sheets dropped into the config folder, read before the list that
        // names them -- a definition pointing at an imported sheet has to find
        // it already registered.
        de.cas_ual_ty.dueldimension.clientutil.overworld.MonsterSheets.load();

        // NO LAUNCH-TIME OFFER. There was one: a title-screen notice asking
        // whether to download the monster models, with "not now" and "don't ask
        // again" and a config file remembering the answer.
        //
        // It is a question nobody asked to be asked. The models are optional,
        // 273 MB, and wanted by the duellist who wants them -- so the offer now
        // waits where someone would go looking for it, as the Misc tab of the
        // Duel Hub, which can also take them away again. See DuelHubScreen.

        // The monster sprites: the shipped list, then whatever the player has
        // edited on top of it. Read here rather than from a static block --
        // that one first ran in the middle of drawing a frame, which is fine
        // for a constant and no place to be opening files.
        de.cas_ual_ty.dueldimension.clientutil.overworld.MonsterSprites.load();

        // The card on a display pedestal, and the monster standing on it.
        net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry.register(
            de.cas_ual_ty.dueldimension.DdTileEntityTypes.CARD_DISPLAY,
            de.cas_ual_ty.dueldimension.clientutil.overworld.CardDisplayRenderer::new);

        // The arena markers a builder has put down, and the board they
        // describe while TAB is held. Registered before the duel's own board so
        // a preview never draws over a duel actually being played.
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.LAST
            .register(de.cas_ual_ty.dueldimension.clientutil.overworld.ArenaRenderer::render);

        // The board itself, once both duellists are standing at it. Registered
        // after the guide so it draws over the markers in the frame they both
        // exist, which is the frame the duel begins.
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.LAST
            .register(de.cas_ual_ty.dueldimension.clientutil.overworld
                .OverworldBoardRenderer::render);

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
                && client.screen == null)
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
                client.getChatListener().handleSystemMessage(
                    net.minecraft.network.chat.Component.literal(
                        "Duel Dimension could not set up your card database: " + problem
                            + ". Every card will show as an unknown card until that is fixed;"
                            + " the game log has the details.")
                        .withStyle(net.minecraft.ChatFormatting.RED), false);
            });

        // Leaving a server forgets what everyone was wearing. The maps are
        // keyed by UUID and nothing else clears them, so without this the next
        // server starts with the last one's disks on strangers who share a UUID.
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT
            .register((handler, client) ->
            {
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

        // Kept for the shape of the file rather than the effect: a 1.21.1 screen
        // can draw a quad without asking anyone. See BoardPip.
        de.cas_ual_ty.dueldimension.clientutil.BoardPip.register();

        // The ten duel-disk frame models, which nothing in a resource pack points
        // at any more -- each disk's own model is a builtin/entity marker now, so
        // without this the bakery never sees them and every disk is invisible.
        de.cas_ual_ty.dueldimension.clientutil.DiskCardsItemModel.registerModels();

        // Bind each container menu to the screen that draws it.
        de.cas_ual_ty.dueldimension.clientutil.DdScreens.register();

        // Bind the card, set and disk items to the renderers that draw them.
        de.cas_ual_ty.dueldimension.clientutil.DdCardModels.register();

        // Told what everyone is wearing, and what the server holds for us.
        de.cas_ual_ty.dueldimension.net.DdNetwork.registerClientHandlers();
    }
}
