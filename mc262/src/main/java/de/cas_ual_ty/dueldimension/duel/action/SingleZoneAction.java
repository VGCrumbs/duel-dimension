package de.cas_ual_ty.dueldimension.duel.action;

import de.cas_ual_ty.dueldimension.duel.playfield.PlayField;
import de.cas_ual_ty.dueldimension.duel.playfield.Zone;
import net.minecraft.network.RegistryFriendlyByteBuf;

public abstract class SingleZoneAction extends Action
{
    public byte sourceZoneId;
    
    public Zone sourceZone;
    
    public SingleZoneAction(ActionType actionType, byte sourceZoneId)
    {
        super(actionType);
        this.sourceZoneId = sourceZoneId;
    }
    
    public SingleZoneAction(ActionType actionType, RegistryFriendlyByteBuf buf)
    {
        this(actionType, buf.readByte());
    }
    
    @Override
    public void writeToBuf(RegistryFriendlyByteBuf buf)
    {
        buf.writeByte(sourceZoneId);
    }
    
    @Override
    public void initServer(PlayField playField)
    {
        if(sourceZoneId != -1)
        {
            sourceZone = playField.getZone(sourceZoneId);
        }
        else
        {
            sourceZone = null;
        }
    }
    
    @Override
    public void undoAction()
    {
        doAction();
    }
    
    @Override
    public void redoAction()
    {
        doAction();
    }
}
