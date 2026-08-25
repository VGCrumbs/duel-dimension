package de.cas_ual_ty.dueldimension.card.properties;

import com.google.gson.JsonObject;
import de.cas_ual_ty.dueldimension.util.JsonKeys;
import de.cas_ual_ty.dueldimension.card.CardLine;

import java.util.List;

public class LevelMonsterProperties extends DefMonsterProperties
{
    public byte level;
    public boolean isTuner;
    
    public LevelMonsterProperties(Properties p0, JsonObject j)
    {
        super(p0);
        readLevelMonsterProperties(j);
    }
    
    public LevelMonsterProperties(Properties p0)
    {
        super(p0);
        
        if(p0 instanceof LevelMonsterProperties)
        {
            LevelMonsterProperties p1 = (LevelMonsterProperties) p0;
            level = p1.level;
            isTuner = p1.isTuner;
        }
    }
    
    public LevelMonsterProperties()
    {
    }
    
    @Override
    public void readAllProperties(JsonObject j)
    {
        super.readAllProperties(j);
        readLevelMonsterProperties(j);
    }
    
    @Override
    public void writeAllProperties(JsonObject j)
    {
        super.writeAllProperties(j);
        writeLevelProperties(j);
    }
    
    public void readLevelMonsterProperties(JsonObject j)
    {
        level = j.get(JsonKeys.LEVEL).getAsByte();
        isTuner = j.get(JsonKeys.IS_TUNER).getAsBoolean();
    }
    
    public void writeLevelProperties(JsonObject j)
    {
        j.addProperty(JsonKeys.LEVEL, level);
        j.addProperty(JsonKeys.IS_TUNER, isTuner);
    }
    
    @Override
    public void addMonsterHeader1(List<CardLine> list)
    {
        list.add(CardLine.of(getAttribute() + " / Level " + getLevel()));
    }
    
    @Override
    public void addMonsterTextHeader(List<CardLine> list)
    {
        // Plain string throughout -- no segment ever carried a style.
        StringBuilder s = new StringBuilder(getSpecies() + " / ");
        
        if(getMonsterType() != null)
        {
            s.append(getMonsterType().name + " / ");
        }
        
        if(getIsPendulum())
        {
            s.append("Pendulum" + " / ");
        }
        
        if(!getAbility().isEmpty())
        {
            s.append(getAbility() + " / ");
        }
        
        if(getIsTuner())
        {
            s.append("Tuner / ");
        }
        
        if(getHasEffect())
        {
            s.append("Effect");
        }
        else
        {
            s.append("Normal");
        }
        
        list.add(CardLine.of(s.toString()));
    }
    
    // --- Getters ---
    
    public byte getLevel()
    {
        return level;
    }
    
    public boolean getIsTuner()
    {
        return isTuner;
    }
}
