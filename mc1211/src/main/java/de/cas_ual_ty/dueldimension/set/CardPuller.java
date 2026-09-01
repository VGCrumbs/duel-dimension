package de.cas_ual_ty.dueldimension.set;

import com.google.gson.JsonObject;
import de.cas_ual_ty.dueldimension.card.CardHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.util.SortedArraySet;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Random;

public abstract class CardPuller
{
    public final CardSet set;
    
    public CardPuller(JsonObject setJson, CardSet set) throws IllegalArgumentException
    {
        this.set = set;
    }
    
    public void postDBInit()
    {
        logErrors();
    }
    
    public abstract List<ItemStack> open(Random random);

    /**
     * The same pull, recording which set each card actually came out of.
     * <p>
     * A tin is several booster packs in a box, and the reveal shows them under
     * the pack they came from -- so the pull has to say. A flat list of cards
     * cannot: by the time it is returned, a card from Ancient Prophecy and a
     * card from Crimson Crisis are indistinguishable.
     * <p>
     * The default answers with this set's own code for every card, which is the
     * truth for every puller except {@link CompositionCardPuller} -- a
     * distribution or a full set really is the one set it names. Only the
     * composition has more than one answer to give, and only it overrides.
     *
     * @param sourcesOut appended to, one entry per returned card, in the same
     *                   order. Null to not bother.
     */
    public List<ItemStack> open(Random random, List<String> sourcesOut)
    {
        List<ItemStack> cards = open(random);
        if(sourcesOut != null && cards != null)
        {
            for(int i = 0; i < cards.size(); i++)
            {
                sourcesOut.add(set.code);
            }
        }
        return cards;
    }

    
    public void addInformation(List<Component> tooltip)
    {
        
    }
    
    public abstract boolean addInformationInComposition();
    
    public void addAllCardEntries(SortedArraySet<CardHolder> sortedSet)
    {
        set.cards.forEach(sortedSet::add);
    }
    
    public void logErrors()
    {
        
    }
}
