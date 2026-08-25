package de.cas_ual_ty.dueldimension.card.properties;

import com.google.gson.JsonObject;
import de.cas_ual_ty.dueldimension.util.JsonKeys;
import de.cas_ual_ty.dueldimension.card.CardLine;

import java.util.List;

public class XyzMonsterProperties extends DefMonsterProperties
{
    public byte rank;
    
    public XyzMonsterProperties(Properties p0, JsonObject j)
    {
        super(p0);
        readXyzProperties(j);
    }
    
    public XyzMonsterProperties(Properties p0)
    {
        super(p0);
        
        if(p0 instanceof XyzMonsterProperties)
        {
            XyzMonsterProperties p1 = (XyzMonsterProperties) p0;
            rank = p1.rank;
        }
    }
    
    public XyzMonsterProperties()
    {
    }
    
    @Override
    public void readAllProperties(JsonObject j)
    {
        super.readAllProperties(j);
        readXyzProperties(j);
    }
    
    @Override
    public void writeAllProperties(JsonObject j)
    {
        super.writeAllProperties(j);
    }
    
    public void readXyzProperties(JsonObject j)
    {
        rank = j.get(JsonKeys.RANK).getAsByte();
    }
    
    public void writeXyzProperties(JsonObject j)
    {
        j.addProperty(JsonKeys.RANK, rank);
    }
    
    @Override
    public void addMonsterHeader1(List<CardLine> list)
    {
        list.add(CardLine.of(getAttribute() + " / Rank " + getRank()));
    }
    
    // --- Getters ---
    
    public byte getRank()
    {
        return rank;
    }
}
