package de.cas_ual_ty.dueldimension.duel.action;

import de.cas_ual_ty.dueldimension.duel.playfield.PlayField;
import de.cas_ual_ty.dueldimension.duel.playfield.Zone;
import net.minecraft.network.RegistryFriendlyByteBuf;

public abstract class DualZoneAction extends SingleCardAction
{
    public byte destinationZoneId;
    
    public Zone destinationZone;
    
    public DualZoneAction(ActionType actionType, byte zoneId, short cardIndex, byte destinationZoneId)
    {
        super(actionType, zoneId, cardIndex);
        this.destinationZoneId = destinationZoneId;
    }
    
    public DualZoneAction(ActionType actionType, RegistryFriendlyByteBuf buf)
    {
        this(actionType, buf.readByte(), buf.readShort(), buf.readByte());
    }
    
    @Override
    public void writeToBuf(RegistryFriendlyByteBuf buf)
    {
        super.writeToBuf(buf);
        buf.writeByte(destinationZoneId);
    }
    
    @Override
    public void initServer(PlayField playField)
    {
        super.initServer(playField);
        destinationZone = playField.getZone(destinationZoneId);
    }
}
