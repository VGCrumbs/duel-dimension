package de.cas_ual_ty.dueldimension.card.properties;

import com.google.gson.JsonObject;
import de.cas_ual_ty.dueldimension.util.JsonKeys;
import de.cas_ual_ty.dueldimension.card.CardLine;

import java.util.List;

public class MonsterProperties extends Properties
{
    public String attribute;
    public int atk;
    public String species;
    public MonsterType monsterType;
    public boolean isPendulum;
    public String ability;
    public boolean hasEffect;
    
    // Only if isPendulum = true
    public String pendulumText;
    public byte pendulumScaleLeftBlue;
    public byte pendulumScaleRightRed;
    
    public MonsterProperties(Properties p0, JsonObject j)
    {
        super(p0);
        readMonsterProperties(j);
    }
    
    public MonsterProperties(Properties p0)
    {
        super(p0);
        
        if(p0 instanceof MonsterProperties)
        {
            MonsterProperties p1 = (MonsterProperties) p0;
            attribute = p1.attribute;
            atk = p1.atk;
            species = p1.species;
            monsterType = p1.monsterType;
            isPendulum = p1.isPendulum;
            ability = p1.ability;
            hasEffect = p1.hasEffect;
            
            if(p1.isPendulum)
            {
                pendulumText = p1.pendulumText;
                pendulumScaleLeftBlue = p1.pendulumScaleLeftBlue;
                pendulumScaleRightRed = p1.pendulumScaleRightRed;
            }
        }
    }
    
    public MonsterProperties()
    {
    }
    
    @Override
    public void readAllProperties(JsonObject j)
    {
        super.readAllProperties(j);
        readMonsterProperties(j);
    }
    
    @Override
    public void writeAllProperties(JsonObject j)
    {
        super.writeAllProperties(j);
        writeMonsterProperties(j);
    }
    
    public void readMonsterProperties(JsonObject j)
    {
        attribute = j.get(JsonKeys.ATTRIBUTE).getAsString();
        atk = j.get(JsonKeys.ATK).getAsInt();
        species = j.get(JsonKeys.SPECIES).getAsString();
        monsterType = MonsterType.fromString(j.get(JsonKeys.MONSTER_TYPE).getAsString());
        isPendulum = j.get(JsonKeys.IS_PENDULUM).getAsBoolean();
        ability = j.get(JsonKeys.ABILITY).getAsString();
        hasEffect = j.get(JsonKeys.HAS_EFFECT).getAsBoolean();
        
        if(getIsPendulum())
        {
            pendulumText = j.get(JsonKeys.PENDULUM_TEXT).getAsString();
            pendulumScaleLeftBlue = j.get(JsonKeys.PENDULUM_SCALE_LEFT_BLUE).getAsByte();
            pendulumScaleRightRed = j.get(JsonKeys.PENDULUM_SCALE_RIGHT_RED).getAsByte();
        }
    }
    
    public void writeMonsterProperties(JsonObject j)
    {
        j.addProperty(JsonKeys.ATTRIBUTE, attribute);
        j.addProperty(JsonKeys.ATK, atk);
        j.addProperty(JsonKeys.SPECIES, species);
        j.addProperty(JsonKeys.MONSTER_TYPE, monsterType.name);
        j.addProperty(JsonKeys.IS_PENDULUM, isPendulum);
        j.addProperty(JsonKeys.ABILITY, ability);
        j.addProperty(JsonKeys.HAS_EFFECT, hasEffect);
        
        if(getIsPendulum())
        {
            j.addProperty(JsonKeys.PENDULUM_TEXT, pendulumText);
            j.addProperty(JsonKeys.PENDULUM_SCALE_LEFT_BLUE, pendulumScaleLeftBlue);
            j.addProperty(JsonKeys.PENDULUM_SCALE_RIGHT_RED, pendulumScaleRightRed);
        }
    }
    
    public boolean getIsNormal()
    {
        return getMonsterType() == null && !getHasEffect();
    }
    
    public boolean getIsEffect()
    {
        return getMonsterType() == null && getHasEffect();
    }
    
    public boolean getIsFusion()
    {
        return getMonsterType() == MonsterType.FUSION;
    }
    
    public boolean getIsLink()
    {
        return getMonsterType() == MonsterType.LINK;
    }
    
    public boolean getIsRitual()
    {
        return getMonsterType() == MonsterType.RITUAL;
    }
    
    public boolean getIsSynchro()
    {
        return getMonsterType() == MonsterType.SYNCHRO;
    }
    
    public boolean getIsXyz()
    {
        return getMonsterType() == MonsterType.XYZ;
    }
    
    @Override
    public boolean getIsInExtraDeck()
    {
        return getIsFusion() || getIsLink() || getIsSynchro() || getIsXyz();
    }
    
    public boolean getHasLevel()
    {
        return getMonsterType() == null || getIsFusion() || getIsRitual() || getIsSynchro();
    }
    
    public boolean getHasDef()
    {
        return getMonsterType() == null || getIsFusion() || getIsRitual() || getIsSynchro() || getIsXyz();
    }
    
    @Override
    public void addHeader(List<CardLine> list)
    {
        super.addHeader(list);
        addMonsterHeader(list);
    }

    /**
     * A monster leads with its species and subtypes -- "Spellcaster / Effect",
     * "Dragon / Fusion / Effect" -- which is the line a printed card puts in
     * brackets, and then its attribute, level and stats.
     * <p>
     * This replaces the plain card type rather than adding to it: "Spellcaster
     * / Effect" already says everything "Effect Monster" did.
     */
    @Override
    protected void addFactLines(List<CardLine> list)
    {
        addMonsterTextHeader(list);
        addMonsterHeader(list);
    }
    
    @Override
    public void addText(List<CardLine> list)
    {
        if(getIsPendulum())
        {
            addPendulumTextHeader(list);
            list.add(CardLine.of(getPendulumText()));
            list.add(CardLine.blank());
        }
        addMonsterTextHeader(list);
        super.addText(list);
    }
    
    public void addPendulumTextHeader(List<CardLine> list)
    {
        // One line of six pieces, two of them coloured. This is the case that
        // decided CardLine is a list of segments rather than a string with a
        // colour: split into a line each, the scales would stack vertically
        // instead of reading as "1 <blue> / <red> 8".
        list.add(CardLine.of(
            new CardLine.Segment("" + getPendulumScaleLeftBlue(), CardLine.Colour.DEFAULT),
            new CardLine.Segment(" ", CardLine.Colour.DEFAULT),
            new CardLine.Segment("◀", CardLine.Colour.BLUE),
            new CardLine.Segment(" / ", CardLine.Colour.DEFAULT),
            new CardLine.Segment("▶", CardLine.Colour.RED),
            new CardLine.Segment(" ", CardLine.Colour.DEFAULT),
            new CardLine.Segment("" + getPendulumScaleRightRed(), CardLine.Colour.DEFAULT)));
    }
    
    @Override
    public void addCardType(List<CardLine> list)
    {
        if(getMonsterType() != null)
        {
            list.add(CardLine.of(getMonsterType().name + " " + getType().name));
        }
        else if(getHasEffect())
        {
            list.add(CardLine.of("Effect " + getType().name));
        }
        else
        {
            list.add(CardLine.of("Normal " + getType().name));
        }
    }
    
    public void addMonsterHeader(List<CardLine> list)
    {
        addMonsterHeader1(list);
        addMonsterHeader2(list);
    }
    
    public void addMonsterHeader1(List<CardLine> list)
    {
        list.add(CardLine.of(getAttribute()));
    }
    
    public void addMonsterHeader2(List<CardLine> list)
    {
        list.add(CardLine.of(getAtk() + " ATK"));
    }
    
    public void addMonsterTextHeader(List<CardLine> list)
    {
        // A StringBuilder, because every append here was a plain string onto a
        // literal -- the component carried no style at any point, so this is
        // the same line by a cheaper route.
        StringBuilder s = new StringBuilder(getSpecies() + " / ");

        if(getMonsterType() != null)
        {
            s.append(getMonsterType().name + " / ");
        }

        if(getIsPendulum())
        {
            s.append("Pendulum" + " / ");
        }

        if(getAbility() != null && !getAbility().isEmpty())
        {
            s.append(getAbility() + " / ");
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
    
    public String getAttribute()
    {
        return attribute;
    }
    
    public int getAtk()
    {
        return atk;
    }
    
    public String getSpecies()
    {
        return species;
    }
    
    public MonsterType getMonsterType()
    {
        return monsterType;
    }
    
    public boolean getIsPendulum()
    {
        return isPendulum;
    }
    
    public String getAbility()
    {
        return ability;
    }
    
    public boolean getHasEffect()
    {
        return hasEffect;
    }
    
    public String getPendulumText()
    {
        return pendulumText;
    }
    
    public byte getPendulumScaleLeftBlue()
    {
        return pendulumScaleLeftBlue;
    }
    
    public byte getPendulumScaleRightRed()
    {
        return pendulumScaleRightRed;
    }
}
