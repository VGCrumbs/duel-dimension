package de.cas_ual_ty.dueldimension.duel.action;

import de.cas_ual_ty.dueldimension.duel.playfield.PlayField;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;


public class DiceRollAction extends RandomAction
{
    public int result;
    
    public DiceRollAction(ActionType actionType, int result)
    {
        super(actionType);
        this.result = result;
    }
    
    public DiceRollAction(ActionType actionType)
    {
        this(actionType, -1);
    }
    
    public DiceRollAction(ActionType actionType, RegistryFriendlyByteBuf buf)
    {
        this(actionType, buf.readInt());
    }
    
    @Override
    public void writeToBuf(RegistryFriendlyByteBuf buf)
    {
        buf.writeInt(result);
    }
    
    @Override
    public void initServer(PlayField playField)
    {
        result = playField.getDuelManager().getRandom().nextInt(6) + 1;
    }
    
    @Override
    public MutableComponent getAnnouncement(Component playerName)
    {
        return Component.translatable(getAnnouncementLocalKey()).append(": " + result);
    }
}
