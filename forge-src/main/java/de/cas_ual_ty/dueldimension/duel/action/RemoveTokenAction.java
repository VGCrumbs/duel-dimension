package de.cas_ual_ty.dueldimension.duel.action;

import de.cas_ual_ty.dueldimension.duel.network.DuelMessageUtility;
import de.cas_ual_ty.dueldimension.duel.playfield.DuelCard;
import de.cas_ual_ty.dueldimension.duel.playfield.Zone;
import de.cas_ual_ty.dueldimension.duel.playfield.ZoneOwner;
import net.minecraft.network.FriendlyByteBuf;

public class RemoveTokenAction extends DualZoneAction
{
    public ZoneOwner player;
    
    public RemoveTokenAction(ActionType actionType, byte zoneId, short cardIndex, byte destinationZoneId, ZoneOwner player)
    {
        super(actionType, zoneId, cardIndex, destinationZoneId);
        this.player = player;
    }
    
    public RemoveTokenAction(ActionType actionType, Zone sourceZone, DuelCard card, Zone destinationZone, ZoneOwner player)
    {
        this(actionType, sourceZone.index, sourceZone.getCardIndexShort(card), destinationZone.index, player);
    }
    
    public RemoveTokenAction(ActionType actionType, FriendlyByteBuf buf)
    {
        this(actionType, buf.readByte(), buf.readShort(), buf.readByte(), DuelMessageUtility.decodeZoneOwner(buf));
    }
    
    @Override
    public void writeToBuf(FriendlyByteBuf buf)
    {
        super.writeToBuf(buf);
        DuelMessageUtility.encodeZoneOwner(player, buf);
    }
    
    @Override
    public void doAction()
    {
        sourceZone.removeCard(card);
    }
    
    @Override
    public void undoAction()
    {
        sourceZone.addCard(player, card, sourceCardIndex);
    }
}
