package de.cas_ual_ty.dueldimension.duel.action;

import de.cas_ual_ty.dueldimension.duel.playfield.PlayField;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;


public abstract class RandomAction extends Action implements IAnnouncedAction
{
    public RandomAction(ActionType actionType)
    {
        super(actionType);
    }
    
    public RandomAction(ActionType actionType, RegistryFriendlyByteBuf buf)
    {
        super(actionType, buf);
    }
    
    @Override
    public abstract void writeToBuf(RegistryFriendlyByteBuf buf);
    
    @Override
    public abstract void initServer(PlayField playField);
    
    @Override
    public void doAction()
    {
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
    
    @Override
    public String getAnnouncementLocalKey()
    {
        return actionType.getLocalKey();
    }
    
    @Override
    public abstract MutableComponent getAnnouncement(Component playerName);
}
