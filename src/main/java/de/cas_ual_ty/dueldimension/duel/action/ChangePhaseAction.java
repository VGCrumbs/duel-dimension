package de.cas_ual_ty.dueldimension.duel.action;

import de.cas_ual_ty.dueldimension.duel.DuelPhase;
import de.cas_ual_ty.dueldimension.duel.network.DuelMessageUtility;
import de.cas_ual_ty.dueldimension.duel.playfield.PlayField;
import net.minecraft.network.RegistryFriendlyByteBuf;

import java.util.function.Consumer;

public class ChangePhaseAction extends Action
{
    public DuelPhase phase;
    
    public Consumer<DuelPhase> phaseSetter;
    public DuelPhase prevPhase;
    
    public ChangePhaseAction(ActionType actionType, DuelPhase phase)
    {
        super(actionType);
        this.phase = phase;
    }
    
    public ChangePhaseAction(ActionType actionType, RegistryFriendlyByteBuf buf)
    {
        this(actionType, DuelMessageUtility.decodePhase(buf));
    }
    
    @Override
    public void writeToBuf(RegistryFriendlyByteBuf buf)
    {
        super.writeToBuf(buf);
        DuelMessageUtility.encodePhase(phase, buf);
    }
    
    @Override
    public void initServer(PlayField playField)
    {
        super.initServer(playField);
        prevPhase = playField.getPhase();
        phaseSetter = playField::setPhase;
    }
    
    @Override
    public void doAction()
    {
        phaseSetter.accept(phase);
    }
    
    @Override
    public void undoAction()
    {
        phaseSetter.accept(prevPhase);
    }
    
    @Override
    public void redoAction()
    {
        doAction();
    }
}
