package de.cas_ual_ty.dueldimension.cardbinder;

import de.cas_ual_ty.dueldimension.DuelDimension;
import de.cas_ual_ty.dueldimension.cardinventory.UUIDCardsManager;

import java.io.File;
import java.util.UUID;

public class CardBinderCardsManager extends UUIDCardsManager
{
    public CardBinderCardsManager()
    {
        super();
    }
    
    @Override
    protected File getFile()
    {
        generateUUIDIfNull();
        return CardBinderCardsManager.getBinderFile(getUUID());
    }
    
    public static File getBinderFile(UUID uuid)
    {
        return new File(DuelDimension.bindersFolder, uuid.toString() + ".json");
    }
}
