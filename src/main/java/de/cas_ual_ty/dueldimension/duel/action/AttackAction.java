package de.cas_ual_ty.dueldimension.duel.action;

import de.cas_ual_ty.dueldimension.duel.playfield.PlayField;
import de.cas_ual_ty.dueldimension.duel.playfield.Zone;
import net.minecraft.network.RegistryFriendlyByteBuf;

public class AttackAction extends SingleZoneAction
{
    public byte attackedZoneId;
    
    public Zone attackedZone;
    
    public AttackAction(ActionType actionType, byte sourceZoneId, byte attackedZoneId)
    {
        super(actionType, sourceZoneId);
        this.attackedZoneId = attackedZoneId;
    }
    
    public AttackAction(ActionType actionType, Zone sourceZone, Zone attackedZone)
    {
        this(actionType, sourceZone.index, attackedZone.index);
    }
    
    public AttackAction(ActionType actionType, RegistryFriendlyByteBuf buf)
    {
        this(actionType, buf.readByte(), buf.readByte());
    }
    
    @Override
    public void writeToBuf(RegistryFriendlyByteBuf buf)
    {
        super.writeToBuf(buf);
        buf.writeByte(attackedZoneId);
    }
    
    @Override
    public void initServer(PlayField playField)
    {
        super.initServer(playField);
        attackedZone = playField.getZone(attackedZoneId);
    }
    
    @Override
    public void doAction()
    {
        // TODO attack action
        // from zone to zone, no need for a specific card
    }
}
