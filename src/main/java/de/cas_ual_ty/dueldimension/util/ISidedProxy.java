package de.cas_ual_ty.dueldimension.util;

import de.cas_ual_ty.dueldimension.DuelDimension;
import de.cas_ual_ty.dueldimension.card.CardHolder;
import de.cas_ual_ty.dueldimension.card.properties.Properties;
import de.cas_ual_ty.dueldimension.rarity.RarityLayer;
import de.cas_ual_ty.dueldimension.set.CardSet;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

public interface ISidedProxy
{
    /**
     * How this side reaches a duel it has been told about.
     * <p>
     * Forge asked {@code DistExecutor.unsafeRunForDist} at every call site: the
     * server talks to the manager directly, the client wraps it so updates also
     * reach the screen. That is a question about which side is running, which is
     * what this interface is for -- and unlike DistExecutor it cannot load a
     * client class on a server by accident, because the two answers live in two
     * classes that only their own side ever loads.
     */
    /**
     * Sends a duel message to the server.
     * <p>
     * Only ever called from client code, but from classes that are common -- a
     * container runs on both sides. Routing it through the proxy keeps Fabric's
     * client networking class off a dedicated server's classpath, which is the
     * same reason the rest of this interface exists.
     */
    default void sendDuelMessage(de.cas_ual_ty.dueldimension.duel.network.DuelMessage message)
    {
    }

    /**
     * How this side reaches a duel it has been told about.
     * <p>
     * Forge asked {@code DistExecutor.unsafeRunForDist} at every call site: the
     * server talks to the manager directly, the client wraps it so updates also
     * reach the screen. That is a question about which side is running, which is
     * what this interface is for -- and unlike DistExecutor it cannot load a
     * client class on a server by accident, because the two answers live in two
     * classes that only their own side ever loads.
     */
    default de.cas_ual_ty.dueldimension.duel.network.IDuelManagerProvider duelProvider(
        de.cas_ual_ty.dueldimension.duel.DuelManager duelManager)
    {
        return () -> duelManager;
    }

    /** Client only: remember which mat the other duelist brought. */
    default void setOpponentPlayMat(String matId)
    {
    }

    /** The sleeve on the deck this player is duelling with; server side does nothing. */
    default void setOwnSleeve(String sleeve)
    {
    }

    /** The sleeve on the OPPONENT's deck; server side does nothing. */
    default void setOpponentSleeve(String sleeve)
    {
    }

    /**
     * The cards in this player's own deck, shuffled by the server, ready to be
     * shown in the pile panel. Server side does nothing.
     * <p>
     * Two parallel arrays rather than any card type: the answer is a multiset of
     * (passcode, artwork) pairs, and nothing about where a card sits in the deck
     * is permitted past this point.
     */
    default void showOwnDeck(int[] codes, int[] arts)
    {
    }

    /** Shows a duel prompt; server side does nothing. */
    default void showEnginePrompt(de.cas_ual_ty.dueldimension.ocg.prompt.EnginePrompt prompt, int serial)
    {
    }

    /** Applies a duel board/log update; server side does nothing. */
    /**
     * A new view of the board, from the engine.
     * <p>
     * No-op on the server, and no-op on the client too until
     * {@code EngineDuelScreen} is ported -- it is the thing that would draw
     * this. The hook exists now because the packets are real and arriving; a
     * receiver that hands them to nobody is better than no receiver, which the
     * game reports as "Unknown custom packet payload" every time one lands.
     */
    default void updateEngineDuel(
        de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.DuelUpdate update)
    {
    }
    // {
    // }

    // The two event-bus registrations that stood here are gone. They were
    // Forge's mod-bus/forge-bus split, and Fabric has no such thing: a client
    // entrypoint IS the client-side registration, so a hook to ask for one
    // would be a hook with nothing to do.

    default void preInit()
    {
        
    }
    
    default void init()
    {
        
    }
    
    default void initFolders()
    {
        
    }
    
    default void initFiles()
    {
        
    }
    
    @Nullable
    default Player getClientPlayer()
    {
        return null;
    }
    
    default String addCardInfoTag(String imageName)
    {
        return null;
    }
    
    default String addCardItemTag(String imageName)
    {
        return null;
    }
    
    default String addCardMainTag(String imageName)
    {
        return null;
    }
    
    default String addSetInfoTag(String imageName)
    {
        return null;
    }
    
    default String addSetItemTag(String imageName)
    {
        return null;
    }
    
    default String getCardInfoReplacementImage(Properties properties, byte imageIndex)
    {
        return null;
    }
    
    default String getCardMainReplacementImage(Properties properties, byte imageIndex)
    {
        return null;
    }
    
    default String getSetInfoReplacementImage(CardSet set)
    {
        return null;
    }
    
    default String getRarityMainImage(RarityLayer layer)
    {
        return null;
    }
    
    default String getRarityInfoImage(RarityLayer layer)
    {
        return null;
    }
    
    default boolean continueTasks()
    {
        return DuelDimension.continueTasks;
    }
    
    default boolean forceTaskStop()
    {
        return DuelDimension.forceTaskStop;
    }
    
    default void openCardInspectScreen(CardHolder card)
    {
    
    }

    /**
     * Shows the pack-opening reveal. Client only; the server has nothing to
     * show, so it is a no-op there rather than a side check at the call site.
     */
    default void openPackReveal(String setName, java.util.List<Integer> codes,
        java.util.List<String> rarities)
    {
    }

    /** Opens the card shop. Client only. */
    default void openCardShop(int points,
        java.util.List<de.cas_ual_ty.dueldimension.shop.ShopStock.Pack> packs)
    {
    }

    /** Records the player's DP balance for display. Client only. */
    default void setDuelPoints(int points)
    {
    }

    /**
     * Takes the player's collection and decks as the server holds them. Client
     * only: the server does not need to be told what it already knows.
     */
    default void setDuelProfile(net.minecraft.nbt.CompoundTag profile)
    {
    }

    /** Opens or refreshes the duel lobby. Client only. */
    /**
     * Opens the collection binder.
     * <p>
     * A default that does nothing, like the rest of the client hooks here: the
     * server calls this on the same code path and has no screens.
     */
    default void openCollectionBinder()
    {
    }

    default void openDuelLobby(
        de.cas_ual_ty.dueldimension.duel.match.LobbyMessages.OpenLobby room)
    {
    }

    /** Opens the duel disk shop, or refreshes it if it is already open. Client only. */
    default void openDiskShop(
        de.cas_ual_ty.dueldimension.shop.DiskShopMessages.OpenDiskShop shop)
    {
    }

    /** Shows the opening toss and, to its winner, the choice. Client only. */
    default void openCoinToss(
        de.cas_ual_ty.dueldimension.duel.match.LobbyMessages.CoinToss toss)
    {
    }

    /** Shuts the lobby: the duel started, or someone left. Client only. */
    default void closeDuelLobby()
    {
    }

    /**
     * A duel field is standing in the world: where it is, which end this player
     * belongs at, and whether they are already locked to it. Client only.
     */
    default void showDuelField(
        de.cas_ual_ty.dueldimension.duel.overworld.OverworldPayloads.ShowField field)
    {
    }

    /** The duel field is gone. Client only. */
    default void hideDuelField()
    {
    }

    /** Asks which way to play a duelist that has just been clicked. Client only. */
    default void offerDuelType(
        de.cas_ual_ty.dueldimension.duel.npc.DuelistChallengeMessages.OfferDuel offer)
    {
    }

    /** A bystander's redacted view of a duel being played nearby. Client only. */
    default void showSpectatorBoard(
        de.cas_ual_ty.dueldimension.duel.overworld.OverworldPayloads.SpectatorBoard board)
    {
    }
}
