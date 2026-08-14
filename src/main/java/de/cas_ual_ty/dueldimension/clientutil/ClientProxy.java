package de.cas_ual_ty.dueldimension.clientutil;

import de.cas_ual_ty.dueldimension.DuelDimension;
import de.cas_ual_ty.dueldimension.util.DdIOUtil;
import de.cas_ual_ty.dueldimension.util.ISidedProxy;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.io.File;

/**
 * The client half of the mod: where card images live, and how big they are.
 * <p>
 * The Forge version of this class was nine hundred lines, because on that
 * loader a proxy is also where every client event subscription lives — key
 * bindings, renderers, screen opening, the tick hook. On Fabric those are
 * registered by the client entrypoint instead, so what belongs here is only
 * what the name says: the client-side state the rest of the mod reads.
 * <p>
 * Right now that is the image pipeline's settings. The sizes and folders are
 * static because {@link ImageHandler} reads them from its worker threads, and
 * a worker that had to reach through a proxy instance to find out where to
 * write a PNG would be a worker holding a reference to the game.
 */
public class ClientProxy implements ISidedProxy
{
    // ---- image sizes ----
    //
    // One image per (card, size), so these decide both how sharp a card looks
    // and how much disk the cache takes. The defaults are the Forge build's.

    /** Card art on an inspect screen or a preview panel. */
    public static int activeCardInfoImageSize = 256;
    /** Card art on an item, which is drawn small and drawn a lot. */
    public static volatile int activeCardItemImageSize = 64;
    /** Card art on the duel field and in the deck editor's grids. */
    public static int activeCardMainImageSize = 256;

    public static int activeSetInfoImageSize = 256;
    public static volatile int activeSetItemImageSize = 64;

    /**
     * Whether a downloaded image survives a restart.
     * <p>
     * On by default: the database is twenty thousand cards, and fetching one
     * again because the game closed would be rude to both the player and the
     * server holding the images.
     */
    public static boolean keepCachedImages = true;


    // ---- where they live ----

    /**
     * Card art, and deliberately NOT inside {@code ydm_db}.
     * <p>
     * A database update deletes {@code ydm_db} recursively before unpacking the
     * new one. Images nested under it would go with it, and the client would
     * re-download thousands of them every time the card list changed.
     * <p>
     * In the game directory rather than the working directory, for the reason
     * given on {@link DuelDimension#mainFolder}: a launcher that passes
     * {@code --gameDir} would otherwise scatter one instance's image cache into
     * whichever folder it happened to start the JVM in, and two instances would
     * share it.
     */
    public static File imagesParentFolder = de.cas_ual_ty.dueldimension.util.GameDir.file("ydm_db_images");
    public static File cardImagesFolder = new File(imagesParentFolder, "cards");
    public static File setImagesFolder = new File(imagesParentFolder, "sets");
    public static File rarityImagesFolder = new File(imagesParentFolder, "rarities");

    /**
     * The originals, before they are scaled to the sizes above. Kept separately
     * so changing a size re-scales from the source rather than re-downloading.
     */
    public static File rawCardImagesFolder = new File(cardImagesFolder, "raw");
    public static File rawSetImagesFolder = new File(setImagesFolder, "raw");

    /**
     * The rarity foils, which are the one thing here that is not downloaded
     * per-card: they arrive inside the database zip, so they live where the
     * database put them rather than beside the other raw images.
     */
    public static File rawRarityImagesFolder = new File(DuelDimension.mainFolder, "rarity_images");

    // ---- animation lengths ----
    //
    // In ticks, from the client config. Static for the same reason the image
    // sizes are: an animation reads its own length when it is created, and
    // reaching through a proxy instance for a number would be a strange thing
    // to do sixty times a second.

    /**
     * How far the duel chat's text is scaled down, 0.5 to 1. Read per frame by
     * the chat widget, which is why it lives here rather than being asked of
     * the config.
     */
    public static double duelChatSize = 1D;

    public static int moveAnimationLength = 10;
    public static int specialAnimationLength = 10;
    public static int attackAnimationLength = 12;
    public static int announcementAnimationLength = 16;

    // The maxInfoImages/maxMainImages copies that used to be here are gone with
    // the LimitedTextureBinder they configured. What decides when a card image
    // is let go is now CardTextureCache, and it decides in BYTES per size class
    // rather than in a count: a 512px preview is sixteen times the memory of a
    // 128px icon, so one number could never mean the same thing for both. The
    // config keys are still read and written by ClientConfig, so nobody's
    // settings file changes.

    /** The settings this client was started with. */
    public static ClientConfig clientConfig;

    /**
     * Reads the config and copies out what the rest of the client reads.
     * <p>
     * Copied into fields rather than read through {@code clientConfig} at each
     * use, because that is what the Forge code did and what its call sites
     * expect -- and because these are read per frame.
     */
    public static void loadConfig()
    {
        clientConfig = ClientConfig.load();
        activeCardInfoImageSize = clientConfig.activeCardInfoImageSize.get();
        activeCardItemImageSize = clientConfig.activeCardItemImageSize.get();
        activeCardMainImageSize = clientConfig.activeCardMainImageSize.get();
        activeSetInfoImageSize = clientConfig.activeSetInfoImageSize.get();
        activeSetItemImageSize = clientConfig.activeSetItemImageSize.get();
        keepCachedImages = clientConfig.keepCachedImages.get();
        duelChatSize = clientConfig.duelChatSize.get();
        moveAnimationLength = clientConfig.moveAnimationLength.get();
        specialAnimationLength = clientConfig.specialAnimationLength.get();
        attackAnimationLength = clientConfig.attackAnimationLength.get();
        announcementAnimationLength = clientConfig.announcementAnimationLength.get();
    }

    /**
     * The client-side setup Forge did from its proxy's init event.
     * <p>
     * Separate from {@link #loadConfig()} because it reads the settings that
     * one loads. Missing it is not subtle: the card image folders are never
     * created, so every download throws, and the loader threads never start, so
     * no card art is ever decoded and every card in the game stays a
     * placeholder.
     */
    public static void initClient()
    {
        // The image folders, before a worker thread tries to write into one.
        // Nothing creates these lazily on the download path -- downloadRawImage
        // copies straight into rawCardImagesFolder -- so a missing directory is
        // a NoSuchFileException per card and no art anywhere in the game.
        DdIOUtil.createDirIfNonExistant(imagesParentFolder);
        DdIOUtil.createDirIfNonExistant(cardImagesFolder);
        DdIOUtil.createDirIfNonExistant(setImagesFolder);
        DdIOUtil.createDirIfNonExistant(rarityImagesFolder);
        DdIOUtil.createDirIfNonExistant(rawCardImagesFolder);
        DdIOUtil.createDirIfNonExistant(rawSetImagesFolder);
        DdIOUtil.createDirIfNonExistant(rawRarityImagesFolder);

        // Custom cards shipped in the jar. AFTER the folders exist, because
        // that is where their art is written; nothing here overwrites a file
        // the player put there themselves.
        de.cas_ual_ty.dueldimension.ocg.session.CustomCardBundle.install();

        ImageHandler.prepareRarityImages(activeCardMainImageSize);
        ImageHandler.prepareRarityImages(activeCardInfoImageSize);
        // The four card-image decode threads. EDOPro spawns its own in the
        // ImageManager constructor (image_manager.cpp:47-53); this is the same
        // moment in our lifecycle -- after the settings they read, before the
        // first screen that could ask for a card.
        CardImageManager.init();
    }

    // ---- world chat, mirrored for the duel screen ----
    //
    // A duel screen shows world chat beside duel chat, so it needs the recent
    // messages -- and the vanilla chat component keeps its own history in a form
    // that is not readable from outside. Forge subscribed to
    // ClientChatReceivedEvent for this; Fabric has the same hook by another name.

    public static int maxMessages = 50; //TODO make configurable
    public static final java.util.List<net.minecraft.network.chat.Component> chatMessages =
        new java.util.ArrayList<>(50);

    /**
     * Remembers a message, dropping the oldest once the buffer is full.
     * <p>
     * The cap is what stops a long session turning this into a leak: nothing
     * ever removes from it otherwise, and a duel screen only ever shows the
     * last screenful.
     */
    public static void rememberChatMessage(net.minecraft.network.chat.Component message)
    {
        if(message == null || message.getString().isEmpty())
        {
            return;
        }
        if(chatMessages.size() >= maxMessages)
        {
            chatMessages.remove(0);
        }
        chatMessages.add(message);
    }

    /**
     * The player at this client.
     * <p>
     * Static because the duel screens ask for it from static context. The
     * instance method {@link #getClientPlayer()} is the same answer through
     * {@code ISidedProxy}, which is how common code asks.
     */
    public static net.minecraft.client.player.LocalPlayer getPlayer()
    {
        return Minecraft.getInstance().player;
    }


    // ---- what only a client can do ----
    //
    // These were the interface's no-op defaults until the duel screen existed.
    // Every one of them is the Forge ClientProxy's body, with the two calls that
    // changed name in 26.2 brought up to date.

    /**
     * Sends a duel message to the server.
     * <p>
     * Common code reaches this through the proxy so that Fabric's client
     * networking class never has to be on a dedicated server's classpath.
     */
    @Override
    public void sendDuelMessage(de.cas_ual_ty.dueldimension.duel.network.DuelMessage message)
    {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
            new de.cas_ual_ty.dueldimension.duel.network.DuelPayloads.ToServer(message));
    }

    /**
     * Wraps a duel manager so updates also reach the screen.
     * <p>
     * The server talks to the manager directly; a client needs the wrapper, and
     * this is the side that knows which it is.
     */
    @Override
    public de.cas_ual_ty.dueldimension.duel.network.IDuelManagerProvider duelProvider(
        de.cas_ual_ty.dueldimension.duel.DuelManager duelManager)
    {
        return new de.cas_ual_ty.dueldimension.duel.network.ClientDuelManagerProvider(duelManager);
    }

    @Override
    public void setOpponentSleeve(String sleeve)
    {
        de.cas_ual_ty.dueldimension.card.CardSleevesType named =
            de.cas_ual_ty.dueldimension.duel.profile.Sleeves.byName(sleeve);
        DuelClientState.opponentSleeve = named == null
            ? de.cas_ual_ty.dueldimension.duel.profile.Sleeves.DEFAULT : named;
    }

    @Override
    public void setOwnSleeve(String sleeve)
    {
        // byName returns null for a name this build does not know, rather than
        // guessing. An unknown sleeve is simply the plain back.
        de.cas_ual_ty.dueldimension.card.CardSleevesType named =
            de.cas_ual_ty.dueldimension.duel.profile.Sleeves.byName(sleeve);
        DuelClientState.ownSleeve = named == null
            ? de.cas_ual_ty.dueldimension.duel.profile.Sleeves.DEFAULT : named;
    }

    /**
     * The player's own deck list, as the server shuffled it.
     * <p>
     * Built into slots here rather than through {@code Slot.of(CardView)}: the
     * deck query asks for no position, so {@code CardView.isFaceUp()} would be
     * false and {@code Slot.of} would mark every one of these face down. That
     * renders correctly today only because the pile panel never reads the flag,
     * which makes it a trap for whoever adds "if faceDown, draw the cover".
     * These cards are the viewer's own and are meant to be read, so the flag is
     * written out explicitly. Everything else takes its neutral value; none of
     * it is drawn, and an equip is null because a card in a deck equips nothing.
     */
    @Override
    public void showOwnDeck(int[] codes, int[] arts)
    {
        java.util.List<de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot.Slot> deck =
            new java.util.ArrayList<>(codes.length);
        for(int i = 0; i < codes.length; i++)
        {
            int art = i < arts.length ? arts[i] : 0;
            deck.add(new de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot.Slot(
                true, codes[i], false, false, -1, -1, -1, -1, -1, -1, 0, null, false, art));
        }
        DuelClientState.deckView = java.util.List.copyOf(deck);
    }

    @Override
    public void setOpponentPlayMat(String matId)
    {
        DuelClientState.opponentMat = PlayMats.byId(matId);
        // The id may be a colour now rather than a mat name.
        DuelClientState.opponentMatColour =
            DuelClientState.parseMatColour(matId, DuelClientState.DEFAULT_MAT_COLOUR);
    }

    @Override
    public void showEnginePrompt(de.cas_ual_ty.dueldimension.ocg.prompt.EnginePrompt prompt,
        int serial)
    {
        // Into the playback queue, never straight onto the screen: the prompt
        // was sent after the events it concludes, and it must not be seen
        // before they have PLAYED. Applying it here let it jump the queue.
        synchronized(DuelClientState.class)
        {
            DuelClientState.pending.add(DuelClientState.PendingUpdate.ofPrompt(prompt, serial));
        }
    }

    // ---- where a card or set image lives ----
    //
    // Every one of these is the Forge ClientProxy's body. They are how a
    // Properties, a CardSet or a RarityLayer turns into a texture path, and
    // the interface's defaults return NULL -- so with them missing the path
    // came out "textures/item/null.png", every binder, supply, shop and card
    // item drew the missing-texture checkerboard, and ImageHandler was never
    // asked to fetch the real image either. The duel screen was unaffected
    // only because it goes through DuelTextures instead, which is why this
    // survived so long.

    @Override
    public String addCardInfoTag(String imageName)
    {
        return ClientProxy.activeCardInfoImageSize + "/" + imageName;
    }

    @Override
    public String addCardItemTag(String imageName)
    {
        return ClientProxy.activeCardItemImageSize + "/" + imageName;
    }

    @Override
    public String addCardMainTag(String imageName)
    {
        return ClientProxy.activeCardMainImageSize + "/" + imageName;
    }

    @Override
    public String addSetInfoTag(String imageName)
    {
        return ClientProxy.activeSetInfoImageSize + "/" + imageName;
    }

    @Override
    public String addSetItemTag(String imageName)
    {
        return ClientProxy.activeSetItemImageSize + "/" + imageName;
    }

    @Override
    public String getCardInfoReplacementImage(
        de.cas_ual_ty.dueldimension.card.properties.Properties properties, byte imageIndex)
    {
        return ImageHandler.getInfoReplacementImage(properties, imageIndex);
    }

    @Override
    public String getCardMainReplacementImage(
        de.cas_ual_ty.dueldimension.card.properties.Properties properties, byte imageIndex)
    {
        return ImageHandler.getMainReplacementImage(properties, imageIndex);
    }

    @Override
    public String getSetInfoReplacementImage(de.cas_ual_ty.dueldimension.set.CardSet set)
    {
        return ImageHandler.getInfoReplacementImage(set);
    }

    @Override
    public String getRarityMainImage(de.cas_ual_ty.dueldimension.rarity.RarityLayer layer)
    {
        return ImageHandler.getRarityMainImage(layer);
    }

    @Override
    public String getRarityInfoImage(de.cas_ual_ty.dueldimension.rarity.RarityLayer layer)
    {
        return ImageHandler.getRarityInfoImage(layer);
    }

    /** Reading a card item. Ported, reachable, and never called until now. */
    @Override
    public void openCardInspectScreen(de.cas_ual_ty.dueldimension.card.CardHolder card)
    {
        getMinecraft().gui.setScreen(
            new de.cas_ual_ty.dueldimension.card.InspectCardScreen(card));
    }

    /**
     * Opens the collection binder over whatever is on screen.
     * <p>
     * It reads the already-synced profile, so this needs nothing from the
     * server and no menu; {@code gui.screen()} is the current screen, which
     * becomes the one to go back to.
     */
    @Override
    public void openCollectionBinder()
    {
        getMinecraft().setScreenAndShow(
            new de.cas_ual_ty.dueldimension.cardbinder.BinderScreen(getMinecraft().gui.screen()));
    }

    /**
     * The PvP lobby, opened or refreshed.
     * <p>
     * Every change re-sends the whole room, so an open lobby is updated in
     * place rather than replaced: rebuilding the screen would drop focus and
     * flicker on every click either player made.
     */
    @Override
    public void openDiskShop(
        de.cas_ual_ty.dueldimension.shop.DiskShopMessages.OpenDiskShop shop)
    {
        // Updated in place when it is already open, so buying a disk does not
        // rebuild the screen under the player's cursor and lose their place.
        if(getMinecraft().gui.screen()
            instanceof de.cas_ual_ty.dueldimension.clientutil.hub.DiskShopScreen open)
        {
            open.update(shop);
            return;
        }
        getMinecraft().gui.setScreen(
            new de.cas_ual_ty.dueldimension.clientutil.hub.DiskShopScreen(shop));
    }

    @Override
    public void openCoinToss(de.cas_ual_ty.dueldimension.duel.match.LobbyMessages.CoinToss toss)
    {
        getMinecraft().gui.setScreen(
            new de.cas_ual_ty.dueldimension.clientutil.hub.CoinTossScreen(toss));
    }

    @Override
    public void openDuelLobby(de.cas_ual_ty.dueldimension.duel.match.LobbyMessages.OpenLobby room)
    {
        if(getMinecraft().gui.screen()
            instanceof de.cas_ual_ty.dueldimension.clientutil.hub.DuelLobbyScreen open)
        {
            open.update(room);
            return;
        }
        getMinecraft().gui.setScreen(
            new de.cas_ual_ty.dueldimension.clientutil.hub.DuelLobbyScreen(room));
    }

    @Override
    public void closeDuelLobby()
    {
        if(getMinecraft().gui.screen()
            instanceof de.cas_ual_ty.dueldimension.clientutil.hub.DuelLobbyScreen)
        {
            getMinecraft().gui.setScreen(null);
        }
    }

    /**
     * The shop, with the stock and the balance the server just sent.
     * <p>
     * Both facts arrive in the packet rather than being read from anywhere on
     * the client, because both are the server's: the balance is spendable and
     * the stock is what is actually for sale.
     */
    @Override
    public void openCardShop(int points,
        java.util.List<de.cas_ual_ty.dueldimension.shop.ShopStock.Pack> packs)
    {
        de.cas_ual_ty.dueldimension.clientutil.hub.CardShopScreen.setPoints(points);
        getMinecraft().gui.setScreen(
            new de.cas_ual_ty.dueldimension.clientutil.hub.CardShopScreen(packs));
    }

    @Override
    public void setDuelPoints(int points)
    {
        de.cas_ual_ty.dueldimension.clientutil.hub.CardShopScreen.setPoints(points);
    }

    /** The pack-opening reveal, over whatever screen asked for the packs. */
    @Override
    public void openPackReveal(String setName, java.util.List<Integer> codes,
        java.util.List<String> rarities)
    {
        // The screen that asked for the packs, so closing the reveal returns
        // there rather than dumping the player back into the world.
        getMinecraft().gui.setScreen(
            new de.cas_ual_ty.dueldimension.clientutil.hub.PackOpeningScreen(
                getMinecraft().gui.screen(), setName, codes, rarities));
    }

    @Override
    public void updateEngineDuel(
        de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.DuelUpdate update)
    {
        update.log().forEach(DuelClientState::addLog);
        DuelClientState.warmUpArt(update.warmUp());
        synchronized(DuelClientState.class)
        {
            // Board and events travel together and are played in order, so the
            // field advances at the pace of the animation rather than jumping
            // to the settled state the moment the packet lands.
            DuelClientState.pending.add(
                DuelClientState.PendingUpdate.ofUpdate(update.events(), update.board()));
            if(update.over())
            {
                // The result rides the stream too, after the win animation.
                boolean won = update.result() != null
                    && update.result().toLowerCase(java.util.Locale.ROOT).contains("winner: you");
                DuelClientState.pending.add(
                    DuelClientState.PendingUpdate.ofOver(won, update.result()));
            }
        }
        // The opponent's whole turn arrives as updates with no prompt attached.
        // Only opening the screen for prompts meant those events queued up
        // unseen and then replayed in a rush at the next prompt, so a duel
        // update reopens the screen too -- that is what makes an opponent's
        // sequence watchable rather than something that happens off-screen.
        if(!update.over() && !update.events().isEmpty()
            && !(getMinecraft().gui.screen() instanceof EngineDuelScreen))
        {
            // gui.setScreen, not setScreenAndShow: the latter forces a frame,
            // and this runs while the update batch is still being applied.
            getMinecraft().gui.setScreen(new EngineDuelScreen());
        }
        if(update.board() != null)
        {
            DuelClientState.warmUpBoard(update.board());
        }
    }

    public static Minecraft getMinecraft()
    {
        return Minecraft.getInstance();
    }

    @Override
    public Player getClientPlayer()
    {
        return Minecraft.getInstance().player;
    }
}
