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
    /** Client only: remember which mat the other duelist brought. */
    default void setOpponentPlayMat(String matId)
    {
    }

    /** Shows a duel prompt; server side does nothing. */
    default void showEnginePrompt(de.cas_ual_ty.dueldimension.ocg.prompt.EnginePrompt prompt, int serial)
    {
    }

    /** Applies a duel board/log update; server side does nothing. */
    // Parked until the client phase; the type in its signature is not
    // ported yet. Kept as a comment because this list IS the record of
    // what the client still owes the rest of the mod.
    // default void updateEngineDuel(de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.DuelUpdate update)
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
    // Parked until the client phase; see above.
    // default void openCardShop(int points,
    // java.util.List<de.cas_ual_ty.dueldimension.shop.ShopStock.Pack> packs)
    // {
    // }

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
    // Parked until the client phase; see above.
    // default void openDuelLobby(
    // de.cas_ual_ty.dueldimension.duel.match.LobbyMessages.OpenLobby room)
    // {
    // }

    /** Shuts the lobby: the duel started, or someone left. Client only. */
    default void closeDuelLobby()
    {
    }
}
