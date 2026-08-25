package de.cas_ual_ty.dueldimension.net;

import de.cas_ual_ty.dueldimension.DuelDimension;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * How this mod talks over the wire.
 * <p>
 * Forge gave every mod a {@code SimpleChannel} with a hand-assigned integer per
 * message and a {@code NetworkEvent.Context} that answered
 * {@code getSender()} on both sides. Minecraft has since grown its own version
 * of the same idea and it is stricter in the ways that matter: a message is a
 * {@link CustomPacketPayload} with an {@link CustomPacketPayload.Type} naming
 * it, a {@link StreamCodec} that reads and writes it, and a registration that
 * says which <em>direction</em> it may travel. There are no ids to keep in
 * step, and a client-to-server message registered as server-to-client is
 * refused rather than mis-parsed.
 * <p>
 * The mod's existing {@code encode}/{@code decode} pairs port straight across:
 * their signatures already match what {@link CustomPacketPayload#codec} wants,
 * so each message needs a type, a codec built from the methods it already has,
 * and one line saying which way it goes.
 */
public final class DdNetwork
{
    private DdNetwork()
    {
    }

    /** Names a message. The path is what appears on the wire. */
    public static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String name)
    {
        return new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(DuelDimension.MOD_ID, name));
    }

    /**
     * Everything the mod sends, registered before anything can send it.
     * <p>
     * Direction is declared per message rather than per channel, which is the
     * substantive difference from the Forge version: {@code serverboundPlay}
     * is what a client may send, {@code clientboundPlay} what a server may. Registering one in
     * the wrong list is a connection error rather than a silent misread.
     */
    public static void register()
    {
        // The profile and every edit to it, the deck's sleeve included: a sleeve
        // change is a deck edit, so it is declared and handled beside the other
        // eight rather than in a channel of its own.
        ProfilePayloads.register();

        // The duel channel. This carries every message of an actual duel in
        // both directions, and it lives in its own class because the Forge
        // version had its own SimpleChannel. Leaving it out does not fail
        // loudly -- the mod loads, a duel starts on the server, and then the
        // first packet of it cannot be sent because its type was never declared.
        de.cas_ual_ty.dueldimension.duel.network.DuelPayloads.register();

        // The card display pedestal: an editor opened by the server, and the
        // choice sent back. Declared and handled together, because a message
        // with nobody to receive it is the failure that looks like nothing.
        registerCardDisplay();

        // The outfit pair -- a client asking to wear one, the server telling
        // everybody what somebody is wearing -- was registered here. A
        // payload's identity is its own type rather than its place in this
        // list, so nothing else changed meaning by their going.

        // The engine's prompts and board updates. These were declared with the
        // engine phase but never registered -- nothing sent one until the
        // duelist self-test was wired up, and an unregistered payload does not
        // fail loudly: the id is unknown, so the game falls back to
        // DiscardedPayload and the encoder throws a ClassCastException that
        // reads as a netty problem rather than a missing registration.
        clientbound(de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.ShowPrompt.TYPE,
            de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.ShowPrompt.CODEC);
        clientbound(de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.DuelUpdate.TYPE,
            de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.DuelUpdate.CODEC);
        clientbound(de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.OpponentPlayMat.TYPE,
            de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.OpponentPlayMat.CODEC);
        clientbound(de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.OwnSleeve.TYPE,
            de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.OwnSleeve.CODEC);
        clientbound(de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.OpponentSleeve.TYPE,
            de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.OpponentSleeve.CODEC);
        serverbound(de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.AnswerPrompt.TYPE,
            de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.AnswerPrompt.CODEC);
        serverbound(de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.SetChainPreference.TYPE,
            de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.SetChainPreference.CODEC);
        serverbound(de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.SetPlayMat.TYPE,
            de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.SetPlayMat.CODEC);
        serverbound(de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.Surrender.TYPE,
            de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.Surrender.CODEC);
        // Looking through your own deck: an empty request, answered to exactly
        // one player. Registered clientbound so it can only ever travel from
        // the server, which is the side that decides whose deck it is.
        serverbound(de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.ViewOwnDeck.TYPE,
            de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.ViewOwnDeck.CODEC);
        clientbound(de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.OwnDeckList.TYPE,
            de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.OwnDeckList.CODEC);

        // The duel lobby.
        clientbound(de.cas_ual_ty.dueldimension.duel.match.LobbyMessages.OpenLobby.TYPE,
            de.cas_ual_ty.dueldimension.duel.match.LobbyMessages.OpenLobby.CODEC);
        clientbound(de.cas_ual_ty.dueldimension.duel.match.LobbyMessages.CloseLobby.TYPE,
            de.cas_ual_ty.dueldimension.duel.match.LobbyMessages.CloseLobby.CODEC);
        serverbound(de.cas_ual_ty.dueldimension.duel.match.LobbyMessages.Configure.TYPE,
            de.cas_ual_ty.dueldimension.duel.match.LobbyMessages.Configure.CODEC);
        serverbound(de.cas_ual_ty.dueldimension.duel.match.LobbyMessages.Ready.TYPE,
            de.cas_ual_ty.dueldimension.duel.match.LobbyMessages.Ready.CODEC);
        serverbound(de.cas_ual_ty.dueldimension.duel.match.LobbyMessages.Leave.TYPE,
            de.cas_ual_ty.dueldimension.duel.match.LobbyMessages.Leave.CODEC);
        // The duel disk shop, and the hub's way into all three shops.
        serverbound(de.cas_ual_ty.dueldimension.shop.DiskShopMessages.RequestShop.TYPE,
            de.cas_ual_ty.dueldimension.shop.DiskShopMessages.RequestShop.CODEC);
        clientbound(de.cas_ual_ty.dueldimension.shop.DiskShopMessages.OpenDiskShop.TYPE,
            de.cas_ual_ty.dueldimension.shop.DiskShopMessages.OpenDiskShop.CODEC);
        serverbound(de.cas_ual_ty.dueldimension.shop.DiskShopMessages.BuyDisk.TYPE,
            de.cas_ual_ty.dueldimension.shop.DiskShopMessages.BuyDisk.CODEC);
        serverbound(de.cas_ual_ty.dueldimension.shop.DiskShopMessages.SetActiveDisk.TYPE,
            de.cas_ual_ty.dueldimension.shop.DiskShopMessages.SetActiveDisk.CODEC);
        serverbound(de.cas_ual_ty.dueldimension.shop.DiskShopMessages.BuyStarter.TYPE,
            de.cas_ual_ty.dueldimension.shop.DiskShopMessages.BuyStarter.CODEC);

        clientbound(de.cas_ual_ty.dueldimension.duel.dueldisk.DiskMessages.WornDisk.TYPE,
            de.cas_ual_ty.dueldimension.duel.dueldisk.DiskMessages.WornDisk.CODEC);

        // Wearing the duel disk, asked for by the hotkey.
        serverbound(de.cas_ual_ty.dueldimension.duel.dueldisk.DiskMessages.ToggleDisk.TYPE,
            de.cas_ual_ty.dueldimension.duel.dueldisk.DiskMessages.ToggleDisk.CODEC);

        // The opening toss: the result goes out to both seats, the winner's
        // answer comes back from one.
        clientbound(de.cas_ual_ty.dueldimension.duel.match.LobbyMessages.CoinToss.TYPE,
            de.cas_ual_ty.dueldimension.duel.match.LobbyMessages.CoinToss.CODEC);
        clientbound(de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.DuelNames.TYPE,
            de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.DuelNames.CODEC);
        clientbound(de.cas_ual_ty.dueldimension.duel.npc.DuelistChallengeMessages.OfferDuel.TYPE,
            de.cas_ual_ty.dueldimension.duel.npc.DuelistChallengeMessages.OfferDuel.CODEC);
        serverbound(de.cas_ual_ty.dueldimension.duel.npc.DuelistChallengeMessages.ChooseDuel.TYPE,
            de.cas_ual_ty.dueldimension.duel.npc.DuelistChallengeMessages.ChooseDuel.CODEC);
        clientbound(de.cas_ual_ty.dueldimension.duel.overworld.OverworldPayloads.ShowField.TYPE,
            de.cas_ual_ty.dueldimension.duel.overworld.OverworldPayloads.ShowField.CODEC);
        clientbound(de.cas_ual_ty.dueldimension.duel.overworld.OverworldPayloads.HideField.TYPE,
            de.cas_ual_ty.dueldimension.duel.overworld.OverworldPayloads.HideField.CODEC);
        clientbound(
            de.cas_ual_ty.dueldimension.duel.overworld.OverworldPayloads.SpectatorBoard.TYPE,
            de.cas_ual_ty.dueldimension.duel.overworld.OverworldPayloads.SpectatorBoard.CODEC);
        serverbound(de.cas_ual_ty.dueldimension.duel.match.LobbyMessages.TurnChoice.TYPE,
            de.cas_ual_ty.dueldimension.duel.match.LobbyMessages.TurnChoice.CODEC);

        // The card shop, and the pack a purchase opens.
        clientbound(de.cas_ual_ty.dueldimension.shop.ShopMessages.SyncPoints.TYPE,
            de.cas_ual_ty.dueldimension.shop.ShopMessages.SyncPoints.CODEC);
        clientbound(de.cas_ual_ty.dueldimension.shop.DuelRewardMessages.Result.TYPE,
            de.cas_ual_ty.dueldimension.shop.DuelRewardMessages.Result.CODEC);
        clientbound(de.cas_ual_ty.dueldimension.shop.ShopMessages.OpenShop.TYPE,
            de.cas_ual_ty.dueldimension.shop.ShopMessages.OpenShop.CODEC);
        serverbound(de.cas_ual_ty.dueldimension.shop.ShopMessages.Buy.TYPE,
            de.cas_ual_ty.dueldimension.shop.ShopMessages.Buy.CODEC);
        clientbound(de.cas_ual_ty.dueldimension.set.PackMessages.OpenPack.TYPE,
            de.cas_ual_ty.dueldimension.set.PackMessages.OpenPack.CODEC);

        // The sleeve shop, the second counter of the same shop. Declared beside
        // the card shop's pair rather than in a channel of its own: it shares
        // the balance, SyncPoints and every rule about who decides a price.
        clientbound(de.cas_ual_ty.dueldimension.shop.ShopMessages.OpenSleeveShop.TYPE,
            de.cas_ual_ty.dueldimension.shop.ShopMessages.OpenSleeveShop.CODEC);
        serverbound(de.cas_ual_ty.dueldimension.shop.ShopMessages.BuySleeve.TYPE,
            de.cas_ual_ty.dueldimension.shop.ShopMessages.BuySleeve.CODEC);
        clientbound(de.cas_ual_ty.dueldimension.shop.ShopMessages.OpenDeckBoxShop.TYPE,
            de.cas_ual_ty.dueldimension.shop.ShopMessages.OpenDeckBoxShop.CODEC);
        serverbound(de.cas_ual_ty.dueldimension.shop.ShopMessages.BuyDeckBox.TYPE,
            de.cas_ual_ty.dueldimension.shop.ShopMessages.BuyDeckBox.CODEC);

        // What a menu's constructor needs, sent one packet ahead of the menu.
        clientbound(MenuData.TYPE, MenuData.CODEC);
        clientbound(PreloadMessages.Preload.TYPE, PreloadMessages.Preload.CODEC);
        serverbound(de.cas_ual_ty.dueldimension.duel.orichalcos
            .OrichalcosMessages.LeftDuel.TYPE,
            de.cas_ual_ty.dueldimension.duel.orichalcos.OrichalcosMessages.LeftDuel.CODEC);
        clientbound(de.cas_ual_ty.dueldimension.duel.orichalcos.OrichalcosMessages.SealBegin.TYPE,
            de.cas_ual_ty.dueldimension.duel.orichalcos.OrichalcosMessages.SealBegin.CODEC);

        // Paged card-item inventories (binders, deck boxes, card sets): the
        // server announces the page it moved to, the client asks to turn one.
        clientbound(de.cas_ual_ty.dueldimension.carditeminventory.CIIMessages.SetPage.TYPE,
            de.cas_ual_ty.dueldimension.carditeminventory.CIIMessages.SetPage.CODEC);
        serverbound(de.cas_ual_ty.dueldimension.carditeminventory.CIIMessages.ChangePage.TYPE,
            de.cas_ual_ty.dueldimension.carditeminventory.CIIMessages.ChangePage.CODEC);

        // The creative card-supply block: a request to mint a card by id.
        serverbound(de.cas_ual_ty.dueldimension.cardsupply.CardSupplyMessages.RequestCard.TYPE,
            de.cas_ual_ty.dueldimension.cardsupply.CardSupplyMessages.RequestCard.CODEC);

        // The card binder: the client turns pages, searches, picks and drops
        // cards; the server announces the page and the cards on it. The binder's
        // cards are a server-side collection, so these are the only traffic.
        serverbound(de.cas_ual_ty.dueldimension.cardbinder.CardBinderMessages.ChangePage.TYPE,
            de.cas_ual_ty.dueldimension.cardbinder.CardBinderMessages.ChangePage.CODEC);
        serverbound(de.cas_ual_ty.dueldimension.cardbinder.CardBinderMessages.ChangeSearch.TYPE,
            de.cas_ual_ty.dueldimension.cardbinder.CardBinderMessages.ChangeSearch.CODEC);
        serverbound(de.cas_ual_ty.dueldimension.cardbinder.CardBinderMessages.IndexClicked.TYPE,
            de.cas_ual_ty.dueldimension.cardbinder.CardBinderMessages.IndexClicked.CODEC);
        serverbound(de.cas_ual_ty.dueldimension.cardbinder.CardBinderMessages.IndexDropped.TYPE,
            de.cas_ual_ty.dueldimension.cardbinder.CardBinderMessages.IndexDropped.CODEC);
        clientbound(de.cas_ual_ty.dueldimension.cardbinder.CardBinderMessages.UpdatePage.TYPE,
            de.cas_ual_ty.dueldimension.cardbinder.CardBinderMessages.UpdatePage.CODEC);
        clientbound(de.cas_ual_ty.dueldimension.cardbinder.CardBinderMessages.UpdateList.TYPE,
            de.cas_ual_ty.dueldimension.cardbinder.CardBinderMessages.UpdateList.CODEC);
    }

    /** Registers the server's side of every message a client may send. */
    public static void registerServerHandlers()
    {
        ProfilePayloads.registerServerHandlers();
        de.cas_ual_ty.dueldimension.duel.network.DuelPayloads.registerServerHandlers();

        // Which way a duelist is to be played. Re-checked on arrival -- the id
        // is looked up, confirmed to be a duelist and confirmed to be within
        // reach -- because a reply is data and not an instruction.
        onServer(de.cas_ual_ty.dueldimension.duel.npc.DuelistChallengeMessages.ChooseDuel.TYPE,
            (message, player) -> de.cas_ual_ty.dueldimension.duel.npc.DuelistChallenge
                .begin(player, message.duelistId(), message.overworld()));

        // The engine's four client-to-server messages. Each body is the Forge
        // handler's, minus the enqueueWork and the null check: Fabric has
        // already moved to the server thread and already knows the sender.
        // The shop's one purchase message. The Forge handler unwrapped the
        // sender and enqueued; onServer already did both.
        onServer(de.cas_ual_ty.dueldimension.shop.ShopMessages.Buy.TYPE,
            (message, player) -> de.cas_ual_ty.dueldimension.shop.ShopMessages.Buy
                .sell(player, message.code(), message.count()));

        // The sleeve purchase. Same shape: the message names what to buy and
        // sell() decides everything else, price and entitlement included.
        onServer(de.cas_ual_ty.dueldimension.shop.ShopMessages.BuySleeve.TYPE,
            (message, player) -> de.cas_ual_ty.dueldimension.shop.ShopMessages.BuySleeve
                .sell(player, message.sleeve()));
        onServer(de.cas_ual_ty.dueldimension.shop.ShopMessages.BuyDeckBox.TYPE,
            (message, player) -> de.cas_ual_ty.dueldimension.shop.ShopMessages.BuyDeckBox
                .sell(player, message.deckBox()));

        onServer(de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.AnswerPrompt.TYPE,
            (message, player) ->
                de.cas_ual_ty.dueldimension.duel.npc.DuelistDuels.submitAnswer(player,
                    new de.cas_ual_ty.dueldimension.ocg.prompt.HumanResponseSource.Answer(
                        message.chosen(), message.declaredCode(), message.serial())));
        onServer(de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.SetChainPreference.TYPE,
            (message, player) ->
                de.cas_ual_ty.dueldimension.duel.npc.DuelistDuels.setChainPreference(player,
                    message.preference()));
        onServer(de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.SetPlayMat.TYPE,
            (message, player) ->
                de.cas_ual_ty.dueldimension.duel.npc.DuelistDuels.setPlayMat(player,
                    message.matId()));
        onServer(de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.Surrender.TYPE,
            (message, player) ->
                de.cas_ual_ty.dueldimension.duel.npc.DuelistDuels.surrender(player));
        // The message says nothing about whose deck, so the only thing that can
        // be passed on is who asked.
        onServer(de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.ViewOwnDeck.TYPE,
            (message, player) ->
                de.cas_ual_ty.dueldimension.duel.npc.DuelistDuels.viewOwnDeck(player));

        onServer(de.cas_ual_ty.dueldimension.duel.match.LobbyMessages.Configure.TYPE,
            (message, player) ->
                de.cas_ual_ty.dueldimension.duel.match.DuelLobby.configure(player,
                    message.config()));
        onServer(de.cas_ual_ty.dueldimension.duel.match.LobbyMessages.Ready.TYPE,
            (message, player) ->
                de.cas_ual_ty.dueldimension.duel.match.DuelLobby.ready(player, message.ready()));
        onServer(de.cas_ual_ty.dueldimension.duel.match.LobbyMessages.Leave.TYPE,
            (message, player) -> de.cas_ual_ty.dueldimension.duel.match.DuelLobby.leave(player));
        onServer(de.cas_ual_ty.dueldimension.shop.DiskShopMessages.RequestShop.TYPE,
            (message, player) -> de.cas_ual_ty.dueldimension.shop.DiskShopMessages.RequestShop
                .open(player, message.kind()));
        onServer(de.cas_ual_ty.dueldimension.shop.DiskShopMessages.BuyDisk.TYPE,
            (message, player) -> de.cas_ual_ty.dueldimension.shop.DiskShopMessages.BuyDisk
                .sell(player, message.disk()));
        onServer(de.cas_ual_ty.dueldimension.shop.DiskShopMessages.BuyStarter.TYPE,
            (message, player) -> de.cas_ual_ty.dueldimension.shop.DiskShopMessages.BuyStarter
                .sell(player, message.starter()));
        onServer(de.cas_ual_ty.dueldimension.shop.DiskShopMessages.SetActiveDisk.TYPE,
            (message, player) -> de.cas_ual_ty.dueldimension.shop.DiskShopMessages.SetActiveDisk
                .apply(player, message.disk()));
        onServer(de.cas_ual_ty.dueldimension.duel.dueldisk.DiskMessages.ToggleDisk.TYPE,
            (message, player) ->
                de.cas_ual_ty.dueldimension.duel.dueldisk.WornDisks.toggle(player));
        onServer(de.cas_ual_ty.dueldimension.duel.match.LobbyMessages.TurnChoice.TYPE,
            (message, player) -> de.cas_ual_ty.dueldimension.duel.match.DuelLobby
                .chooseTurn(player, message.goFirst()));

        onServer(de.cas_ual_ty.dueldimension.carditeminventory.CIIMessages.ChangePage.TYPE,
            (message, player) -> de.cas_ual_ty.dueldimension.carditeminventory.CIIMessages
                .doForContainer(player, container ->
                {
                    if(message.nextPage())
                    {
                        container.nextPage();
                    }
                    else
                    {
                        container.prevPage();
                    }
                }));

        onServer(de.cas_ual_ty.dueldimension.cardsupply.CardSupplyMessages.RequestCard.TYPE,
            (message, player) ->
        {
            de.cas_ual_ty.dueldimension.card.properties.Properties card =
                de.cas_ual_ty.dueldimension.DdDatabase.PROPERTIES_LIST.get(message.cardId());
            if(card != null && card != de.cas_ual_ty.dueldimension.card.properties.Properties.DUMMY)
            {
                de.cas_ual_ty.dueldimension.cardsupply.CardSupplyMessages.doForBinderContainer(player,
                    container -> container.giveCard(card, message.imageIndex()));
            }
        });

        onServer(de.cas_ual_ty.dueldimension.cardbinder.CardBinderMessages.ChangePage.TYPE,
            (message, player) -> de.cas_ual_ty.dueldimension.cardbinder.CardBinderMessages
                .doForBinderContainer(player, container ->
                {
                    if(message.nextPage())
                    {
                        container.nextPage();
                    }
                    else
                    {
                        container.prevPage();
                    }
                }));

        onServer(de.cas_ual_ty.dueldimension.cardbinder.CardBinderMessages.ChangeSearch.TYPE,
            (message, player) -> de.cas_ual_ty.dueldimension.cardbinder.CardBinderMessages
                .doForBinderContainer(player, container -> container.updateSearch(message.search())));

        onServer(de.cas_ual_ty.dueldimension.cardbinder.CardBinderMessages.IndexClicked.TYPE,
            (message, player) -> de.cas_ual_ty.dueldimension.cardbinder.CardBinderMessages
                .doForBinderContainer(player, container -> container.indexClicked(message.index())));

        onServer(de.cas_ual_ty.dueldimension.duel.orichalcos.OrichalcosMessages.LeftDuel.TYPE,
            (message, player) -> de.cas_ual_ty.dueldimension.duel.orichalcos.OrichalcosSouls
                .playerLeftDuel(player));
        onServer(de.cas_ual_ty.dueldimension.cardbinder.CardBinderMessages.IndexDropped.TYPE,
            (message, player) -> de.cas_ual_ty.dueldimension.cardbinder.CardBinderMessages
                .doForBinderContainer(player, container -> container.indexDropped(message.index())));
    }

    /**
     * The client's side of every message a server may send.
     * <p>
     * Called from the client initialiser, and only from there: a receiver
     * registered on a server would be a handler for a packet that never comes.
     */
    public static void registerClientHandlers()
    {
        de.cas_ual_ty.dueldimension.duel.network.DuelPayloads.registerClientHandlers();

        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
            ProfilePayloads.EngineUnknown.TYPE,
            (payload, context) -> de.cas_ual_ty.dueldimension.clientutil.hub.EditorState
                .setEngineUnknown(payload.codes()));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
            ProfilePayloads.SyncFreeMode.TYPE,
            (payload, context) ->
            {
                de.cas_ual_ty.dueldimension.duel.profile.FreeMode
                    .setClientBelief(payload.enabled());
                // The visible collection is cached. Changing the belief
                // without invalidating it leaves an already-open editor stuck
                // on the old owned-only pool until some unrelated filter is
                // changed.
                de.cas_ual_ty.dueldimension.clientutil.hub.EditorState.invalidate();
            });

        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
            MenuData.TYPE, (payload, context) -> MenuData.receive(payload.data()));

        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
            PreloadMessages.Preload.TYPE, (payload, context) ->
            {
                if(payload.start())
                {
                    de.cas_ual_ty.dueldimension.clientutil.CardPreloadJob.start();
                }
                else
                {
                    de.cas_ual_ty.dueldimension.clientutil.CardPreloadJob.stop();
                }
            });

        // The Seal of Orichalcos closing on someone. The whole six seconds run
        // from this one message; see OrichalcosRenderer.
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
            de.cas_ual_ty.dueldimension.duel.orichalcos.OrichalcosMessages.SealBegin.TYPE,
            (payload, context) -> de.cas_ual_ty.dueldimension.clientutil.OrichalcosRenderer
                .begin(payload.entityId(), payload.growTicks(), payload.holdTicks(),
                    payload.fadeTicks()));

        // The PvP lobby. Sent on every change to the room, so the handler
        // updates an open screen rather than replacing it; see the proxy.
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
            de.cas_ual_ty.dueldimension.duel.match.LobbyMessages.OpenLobby.TYPE,
            (payload, context) -> de.cas_ual_ty.dueldimension.DuelDimension.proxy
                .openDuelLobby(payload));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
            de.cas_ual_ty.dueldimension.duel.match.LobbyMessages.CloseLobby.TYPE,
            (payload, context) -> de.cas_ual_ty.dueldimension.DuelDimension.proxy
                .closeDuelLobby());
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
            de.cas_ual_ty.dueldimension.duel.dueldisk.DiskMessages.WornDisk.TYPE,
            (payload, context) -> de.cas_ual_ty.dueldimension.clientutil.ClientWornDisks
                .set(payload.player(), payload.disk(), payload.worn()));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
            de.cas_ual_ty.dueldimension.shop.DiskShopMessages.OpenDiskShop.TYPE,
            (payload, context) -> de.cas_ual_ty.dueldimension.DuelDimension.proxy
                .openDiskShop(payload));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
            de.cas_ual_ty.dueldimension.duel.match.LobbyMessages.CoinToss.TYPE,
            (payload, context) -> de.cas_ual_ty.dueldimension.DuelDimension.proxy
                .openCoinToss(payload));

        // Where a duel is standing in the world. Geometry only -- nothing about
        // the duel itself rides this, so it is safe to hand to any client that
        // is party to the field.
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
            de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.DuelNames.TYPE,
            (payload, context) ->
        {
            de.cas_ual_ty.dueldimension.clientutil.DuelClientState.selfName = payload.self();
            de.cas_ual_ty.dueldimension.clientutil.DuelClientState.opponentName =
                payload.opponent();
        });
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
            de.cas_ual_ty.dueldimension.duel.npc.DuelistChallengeMessages.OfferDuel.TYPE,
            (payload, context) -> de.cas_ual_ty.dueldimension.DuelDimension.proxy
                .offerDuelType(payload));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
            de.cas_ual_ty.dueldimension.duel.overworld.OverworldPayloads.ShowField.TYPE,
            (payload, context) -> de.cas_ual_ty.dueldimension.DuelDimension.proxy
                .showDuelField(payload));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
            de.cas_ual_ty.dueldimension.duel.overworld.OverworldPayloads.HideField.TYPE,
            (payload, context) -> de.cas_ual_ty.dueldimension.DuelDimension.proxy
                .hideDuelField());
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
            de.cas_ual_ty.dueldimension.duel.overworld.OverworldPayloads.SpectatorBoard.TYPE,
            (payload, context) -> de.cas_ual_ty.dueldimension.DuelDimension.proxy
                .showSpectatorBoard(payload));

        // The shop: opened by the server (clicking the counter is answered
        // with stock and balance), kept honest by it (every purchase comes
        // back as a new balance), and the pack reveal rides the same flow.
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
            de.cas_ual_ty.dueldimension.shop.ShopMessages.OpenShop.TYPE,
            (payload, context) -> de.cas_ual_ty.dueldimension.DuelDimension.proxy
                .openCardShop(payload.points(), payload.packs()));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
            de.cas_ual_ty.dueldimension.shop.ShopMessages.SyncPoints.TYPE,
            (payload, context) -> de.cas_ual_ty.dueldimension.DuelDimension.proxy
                .setDuelPoints(payload.points()));
        // The sleeve shop opens straight onto its screen rather than through the
        // proxy. The proxy exists so COMMON code can reach a client-only class;
        // this method is client-only already -- it is called from the client
        // initialiser and nowhere else, which is why EditorState, CardPreloadJob
        // and OrichalcosRenderer are all named directly a few lines from here.
        // Adding a proxy method would have meant editing the interface and both
        // of its implementations to say what one line says here.
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
            de.cas_ual_ty.dueldimension.shop.ShopMessages.OpenSleeveShop.TYPE,
            (payload, context) -> de.cas_ual_ty.dueldimension.clientutil.hub.SleeveShopScreen
                .open(payload.points(), payload.sleeves()));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
            de.cas_ual_ty.dueldimension.shop.ShopMessages.OpenDeckBoxShop.TYPE,
            (payload, context) -> de.cas_ual_ty.dueldimension.clientutil.hub.DeckBoxShopScreen
                .open(payload.points(), payload.deckBoxes()));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
            de.cas_ual_ty.dueldimension.shop.DuelRewardMessages.Result.TYPE,
            (payload, context) -> de.cas_ual_ty.dueldimension.clientutil.DuelClientState
                .acceptReward(payload));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
            de.cas_ual_ty.dueldimension.set.PackMessages.OpenPack.TYPE,
            (payload, context) -> de.cas_ual_ty.dueldimension.DuelDimension.proxy
                .openPackReveal(payload.setName(), payload.codes(), payload.rarities()));

        // The engine's three server-to-client messages. They arrive for real --
        // a duel against a duelist runs and sends them -- but what draws them,
        // EngineDuelScreen, is the last unported cluster. So these hand off to
        // the proxy, whose client half currently does nothing with them.
        //
        // Registering them anyway is not busywork: without a receiver the game
        // logs "Unknown custom packet payload" for every packet, which buries
        // real problems and makes a working server look broken.
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
            de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.ShowPrompt.TYPE,
            (payload, context) -> de.cas_ual_ty.dueldimension.DuelDimension.proxy
                .showEnginePrompt(payload.prompt(), payload.serial()));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
            de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.DuelUpdate.TYPE,
            (payload, context) -> de.cas_ual_ty.dueldimension.DuelDimension.proxy
                .updateEngineDuel(payload));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
            de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.OpponentSleeve.TYPE,
            (payload, context) -> de.cas_ual_ty.dueldimension.DuelDimension.proxy
                .setOpponentSleeve(payload.sleeve()));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
            de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.OwnSleeve.TYPE,
            (payload, context) -> de.cas_ual_ty.dueldimension.DuelDimension.proxy
                .setOwnSleeve(payload.sleeve()));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
            de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.OpponentPlayMat.TYPE,
            (payload, context) -> de.cas_ual_ty.dueldimension.DuelDimension.proxy
                .setOpponentPlayMat(payload.matId()));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
            de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.OwnDeckList.TYPE,
            (payload, context) -> de.cas_ual_ty.dueldimension.DuelDimension.proxy
                .showOwnDeck(payload.codes(), payload.arts()));

        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
            ProfilePayloads.Sync.TYPE,
            (payload, context) -> de.cas_ual_ty.dueldimension.clientutil.hub.EditorState
                .accept(payload.profile()));

        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
            de.cas_ual_ty.dueldimension.carditeminventory.CIIMessages.SetPage.TYPE,
            (payload, context) -> de.cas_ual_ty.dueldimension.carditeminventory.CIIMessages
                .doForContainer(context.player(), container -> container.setPage(payload.page())));

        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
            de.cas_ual_ty.dueldimension.cardbinder.CardBinderMessages.UpdatePage.TYPE,
            (payload, context) -> de.cas_ual_ty.dueldimension.cardbinder.CardBinderMessages
                .doForBinderContainer(context.player(), container ->
                {
                    container.setClientPage(payload.page());
                    container.setClientMaxPage(payload.maxPage());
                }));

        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
            de.cas_ual_ty.dueldimension.cardbinder.CardBinderMessages.UpdateList.TYPE,
            (payload, context) -> de.cas_ual_ty.dueldimension.cardbinder.CardBinderMessages
                .doForBinderContainer(context.player(),
                    container -> container.setClientList(payload.page(), payload.list())));

        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
            de.cas_ual_ty.dueldimension.duel.overworld.display.CardDisplayMessages.OpenEditor.TYPE,
            (payload, context) -> context.client().execute(() ->
                context.client().gui.setScreen(
                    new de.cas_ual_ty.dueldimension.clientutil.hub.CardDisplayScreen(
                        payload.pos(), payload.code(), payload.art(), payload.position()))));

        // The rest of the client receivers land with the screens they feed.
    }

    /**
     * What a card display editor may do, checked again on arrival.
     * <p>
     * A packet naming a block position is a packet that can name ANY block
     * position, and a client that has been asked nicely to be in creative mode
     * is a client that can decline. So the mode, the distance and the block
     * itself are all re-tested here: the screen is a convenience, and this is
     * the rule.
     */
    private static void registerCardDisplay()
    {
        clientbound(de.cas_ual_ty.dueldimension.duel.overworld.display.CardDisplayMessages
            .OpenEditor.TYPE, de.cas_ual_ty.dueldimension.duel.overworld.display
            .CardDisplayMessages.OpenEditor.CODEC);
        serverbound(de.cas_ual_ty.dueldimension.duel.overworld.display.CardDisplayMessages
            .SetCard.TYPE, de.cas_ual_ty.dueldimension.duel.overworld.display.CardDisplayMessages
            .SetCard.CODEC);

        onServer(de.cas_ual_ty.dueldimension.duel.overworld.display.CardDisplayMessages
            .SetCard.TYPE, (message, player) ->
        {
            // Refused OUT LOUD. Every one of these is a legitimate refusal and
            // every one of them looks identical from the other side -- a card
            // chosen and nothing happening -- so a rejected edit says which
            // rule stopped it rather than leaving a builder to guess between
            // four of them.
            if(!player.isCreative())
            {
                de.cas_ual_ty.dueldimension.DuelDimension.log("card display refused for "
                    + player.getGameProfile().name() + ": not in creative mode");
                return;
            }
            if(!player.level().isLoaded(message.pos()))
            {
                de.cas_ual_ty.dueldimension.DuelDimension.log("card display refused at "
                    + message.pos() + ": that chunk is not loaded");
                return;
            }
            if(player.distanceToSqr(message.pos().getX() + 0.5D, message.pos().getY() + 0.5D,
                message.pos().getZ() + 0.5D) > 64D)
            {
                de.cas_ual_ty.dueldimension.DuelDimension.log("card display refused at "
                    + message.pos() + ": " + player.getGameProfile().name() + " is too far away");
                return;
            }
            if(player.level().getBlockEntity(message.pos())
                instanceof de.cas_ual_ty.dueldimension.duel.overworld.display
                    .CardDisplayTileEntity display)
            {
                display.set(message.code(), message.art(), position(message.position()));
                de.cas_ual_ty.dueldimension.DuelDimension.log("card display at " + message.pos()
                    + " now shows " + message.code() + " in position "
                    + position(message.position()));
            }
            else
            {
                de.cas_ual_ty.dueldimension.DuelDimension.log("card display refused at "
                    + message.pos() + ": no display block there");
            }
        });
    }

    /**
     * One position bit, and one this mod actually draws.
     * <p>
     * The wire carries an int, and an int can be anything. Face-down ATTACK is
     * a real engine position but not one a display should offer, so anything
     * unrecognised becomes face-up attack rather than something the renderer
     * has no opinion about.
     */
    private static int position(int asked)
    {
        return switch(asked)
        {
            case de.cas_ual_ty.dueldimension.ocg.OcgConstants.POS_FACEUP_DEFENSE,
                de.cas_ual_ty.dueldimension.ocg.OcgConstants.POS_FACEDOWN_DEFENSE -> asked;
            default -> de.cas_ual_ty.dueldimension.ocg.OcgConstants.POS_FACEUP_ATTACK;
        };
    }

    // ---- helpers the payload classes share ----

    /**
     * Registers a client-to-server message and its handler in one place, so a
     * message cannot be declared without someone to receive it.
     */
    public static <T extends CustomPacketPayload> void serverbound(
        CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec)
    {
        PayloadTypeRegistry.serverboundPlay().register(type, codec);
    }

    public static <T extends CustomPacketPayload> void clientbound(
        CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec)
    {
        PayloadTypeRegistry.clientboundPlay().register(type, codec);
    }

    /**
     * A handler that runs on the server thread with the sending player.
     * <p>
     * Replaces the Forge helper that wrapped every server-side handler in
     * {@code enqueueWork} and {@code getSender()}. Fabric hands the player
     * straight to the handler and has already moved to the server thread, so
     * the wrapper is thinner -- but it stays, because the one thing worth
     * keeping from the old one is that no handler can forget either step.
     */
    public interface ServerHandler<T extends CustomPacketPayload>
    {
        void handle(T message, ServerPlayer player);
    }

    public static <T extends CustomPacketPayload> void onServer(
        CustomPacketPayload.Type<T> type, ServerHandler<T> handler)
    {
        ServerPlayNetworking.registerGlobalReceiver(type,
            (payload, context) -> handler.handle(payload, context.player()));
    }
}
