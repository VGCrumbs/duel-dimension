package de.cas_ual_ty.dueldimension.util;

import de.cas_ual_ty.dueldimension.DuelDimension;
import de.cas_ual_ty.dueldimension.card.CardHolder;
import de.cas_ual_ty.dueldimension.card.properties.Properties;
import de.cas_ual_ty.dueldimension.rarity.RarityLayer;
import de.cas_ual_ty.dueldimension.set.CardSet;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.IEventBus;

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
    default void updateEngineDuel(de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.DuelUpdate update)
    {
    }

    default void registerModEventListeners(IEventBus bus)
    {
    }
    
    default void registerForgeEventListeners(IEventBus bus)
    {
    }
    
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
}
