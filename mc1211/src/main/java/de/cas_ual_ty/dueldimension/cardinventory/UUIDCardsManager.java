package de.cas_ual_ty.dueldimension.cardinventory;

import de.cas_ual_ty.dueldimension.util.JsonKeys;
import de.cas_ual_ty.dueldimension.util.DdUtil;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

public abstract class UUIDCardsManager extends JsonCardsManager
{
    private UUID uuid;
    
    public UUIDCardsManager()
    {
        uuid = null;
    }
    
    public UUID getUUID()
    {
        return uuid;
    }
    
    public void setUUID(UUID uuid)
    {
        this.uuid = uuid;
    }
    
    public void generateUUIDIfNull()
    {
        if(uuid == null)
        {
            uuid = DdUtil.createRandomUUID();
        }
    }
    
    @Override
    public void readFromNBT(CompoundTag nbt)
    {
        if(nbt.hasUUID(JsonKeys.UUID))
        {
            uuid = nbt.getUUID(JsonKeys.UUID);
        }
    }
    
    @Override
    public void writeToNBT(CompoundTag nbt)
    {
        if(getUUID() != null)
        {
            nbt.putUUID(JsonKeys.UUID, getUUID());
        }
    }
}
