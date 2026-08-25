package de.cas_ual_ty.dueldimension.duel.network;

import java.util.function.Supplier;

public class DuelMessageHeaderType
{
    public Supplier<DuelMessageHeader> factory;
    
    public DuelMessageHeaderType(Supplier<DuelMessageHeader> factory)
    {
        this.factory = factory;
    }
    
    public DuelMessageHeader createHeader()
    {
        return factory.get();
    }
}
