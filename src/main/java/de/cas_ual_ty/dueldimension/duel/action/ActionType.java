package de.cas_ual_ty.dueldimension.duel.action;

import de.cas_ual_ty.dueldimension.DdDuelRegistries;
import de.cas_ual_ty.dueldimension.DuelDimension;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class ActionType
{
    public final ActionType.Factory factory;
    
    private String localKey;
    
    public ActionType(ActionType.Factory factory)
    {
        this.factory = factory;
        localKey = null;
    }
    
    public ActionType.Factory getFactory()
    {
        return factory;
    }
    
    public String getLocalKey()
    {
        if(localKey == null)
        {
            Identifier rl = DdDuelRegistries.ACTION_TYPES.getKey(this);
            localKey = "action." + rl.getNamespace() + "." + rl.getPath();
        }
        
        return localKey;
    }
    
    public Component getLocal()
    {
        return Component.translatable(getLocalKey());
    }
    
    public interface Factory
    {
        Action create(ActionType type, RegistryFriendlyByteBuf buf);
    }
}
