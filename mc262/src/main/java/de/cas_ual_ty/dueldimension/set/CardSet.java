package de.cas_ual_ty.dueldimension.set;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.cas_ual_ty.dueldimension.DuelDimension;
import de.cas_ual_ty.dueldimension.DdDatabase;
import de.cas_ual_ty.dueldimension.card.CardHolder;
import de.cas_ual_ty.dueldimension.card.properties.Properties;
import de.cas_ual_ty.dueldimension.util.JsonKeys;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.SortedArraySet;
import net.minecraft.world.item.ItemStack;

import java.text.ParseException;
import java.util.*;

public class CardSet
{
    public static final CardSet DUMMY = new CardSet("Dummy", "DUM-MY", "DUMMY", new Date(0), new CardPuller(null, null)
    {
        @Override
        public List<ItemStack> open(Random random)
        {
            return ImmutableList.of();
        }
        
        @Override
        public boolean addInformationInComposition()
        {
            return false;
        }
        
        @Override
        public void addAllCardEntries(SortedArraySet<CardHolder> sortedSet)
        {
            
        }
    }, ImmutableList.of())
    {
        @Override
        public String getImageName()
        {
            return "blanc_set";
        }
        
        @Override
        public boolean getIsHardcoded()
        {
            return true;
        }
    };
    
    public String name;
    public String code;
    public String type;
    public Date date;
    public String image;
    public CardPuller pull;
    
    // must be same size and order as cards JsonArray
    public List<CardHolder> cards;
    
    // list of all contained rarities
    public Set<String> rarityPool;
    
    public boolean isSubSet;
    public String shownCode;
    
    public CardSet(String name, String code, String type, Date date, CardPuller pull, List<CardHolder> cards)
    {
        this.name = name;
        this.code = code;
        this.type = type;
        this.date = date;
        this.pull = pull;
        this.cards = cards;
        
        init();
    }
    
    public CardSet(JsonObject j) throws IllegalArgumentException
    {
        if(j.has(JsonKeys.NAME))
        {
            name = j.get(JsonKeys.NAME).getAsString();
        }
        else
        {
            name = null;
        }
        
        code = j.get(JsonKeys.CODE).getAsString();
        type = j.get(JsonKeys.TYPE).getAsString();
        
        if(!j.has(JsonKeys.DATE))
        {
            date = null;
        }
        else
        {
            String date = j.get(JsonKeys.DATE).getAsString();
            try
            {
                this.date = DdDatabase.SET_DATE_PARSER.parse(date);
            }
            catch(ParseException e)
            {
                DuelDimension.log("Can not parse date: " + date);
                throw new IllegalArgumentException(e);
            }
        }
        
        if(!j.has(JsonKeys.IMAGE))
        {
            image = null;
        }
        else
        {
            image = j.get(JsonKeys.IMAGE).getAsString();
        }
        
        pull = PullType.createPull(j.get(JsonKeys.PULL_TYPE).getAsString(), j, this);
        
        if(!j.has(JsonKeys.CARDS))
        {
            cards = ImmutableList.of();
            rarityPool = ImmutableSet.of();
        }
        else
        {
            JsonArray cards = j.get(JsonKeys.CARDS).getAsJsonArray();
            this.cards = new ArrayList<>(cards.size());
            rarityPool = new HashSet<>();
            
            JsonObject c;
            long id;
            Properties card;
            byte imageIndex;
            String rarity;
            for(JsonElement e : cards)
            {
                c = e.getAsJsonObject();
                
                id = c.get(JsonKeys.ID).getAsLong();
                card = DdDatabase.PROPERTIES_LIST.get(id);
                
                if(card == null)
                {
                    DuelDimension.log("Can not parse card in: " + code + " card: " + id);
                    continue;
                }
                
                imageIndex = c.get(JsonKeys.IMAGE_INDEX).getAsByte();
                
                if(imageIndex >= card.getImageIndicesAmt())
                {
                    DuelDimension.log("Bad image index for card in: " + code + " card: " + card);
                }
                
                rarity = c.get(JsonKeys.RARITY).getAsString();
                rarityPool.add(rarity);
                
                this.cards.add(new CardHolder(card, imageIndex, rarity, c.get(JsonKeys.CODE).getAsString()));
            }
        }
        
        init();
    }
    
    protected void init()
    {
        isSubSet = type.equals("Sub-Set");
        shownCode = code.split("_")[0];
    }
    
    public void postDBInit()
    {
        pull.postDBInit();
    }
    
    public List<ItemStack> open(Random random)
    {
        return pull.open(random);
    }
    
    public SortedArraySet<CardHolder> getAllCardEntries()
    {
        SortedArraySet<CardHolder> sortedSet = SortedArraySet.create(0);
        addAllCardEntries(sortedSet);
        return sortedSet;
    }
    
    public void addAllCardEntries(SortedArraySet<CardHolder> sortedSet)
    {
        pull.addAllCardEntries(sortedSet);
    }
    
    public void addItemInformation(List<Component> tooltip)
    {
        tooltip.add(Component.literal(name));
        tooltip.add(Component.literal(type));
        tooltip.add(Component.literal(shownCode));
    }
    
    public void addInformation(List<Component> tooltip)
    {
        tooltip.add(Component.literal(name));
        tooltip.add(Component.literal(type));
        tooltip.add(Component.literal(DdDatabase.SET_DATE_PARSER.format(date)));
        tooltip.add(Component.literal(shownCode));
        tooltip.add(Component.empty());
        pull.addInformation(tooltip);
    }
    
    public boolean isIndependentAndItem()
    {
        return this != CardSet.DUMMY && !isSubSet && name != null && date != null;
    }
    
    public String getImageName()
    {
        return code.toLowerCase();
    }
    
    public String getImageURL()
    {
        return image;
    }
    
    public String getInfoImageName()
    {
        return DuelDimension.proxy.addSetInfoTag(getImageName());
    }
    
    public String getItemImageName()
    {
        return DuelDimension.proxy.addSetItemTag(getImageName());
    }
    
    public Identifier getInfoImageResourceLocation()
    {
        return Identifier.fromNamespaceAndPath(DuelDimension.MOD_ID, "textures/item/" + DuelDimension.proxy.getSetInfoReplacementImage(this) + ".png");
    }
    
    public Identifier getItemImageResourceLocation()
    {
        return Identifier.fromNamespaceAndPath(DuelDimension.MOD_ID, "item/" + getItemImageName());
    }
    
    public boolean getIsHardcoded()
    {
        return false;
    }
}
