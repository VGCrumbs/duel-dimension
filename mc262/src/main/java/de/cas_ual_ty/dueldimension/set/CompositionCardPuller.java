package de.cas_ual_ty.dueldimension.set;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.cas_ual_ty.dueldimension.DuelDimension;
import de.cas_ual_ty.dueldimension.DdDatabase;
import de.cas_ual_ty.dueldimension.DdItems;
import de.cas_ual_ty.dueldimension.card.CardHolder;
import de.cas_ual_ty.dueldimension.util.JsonKeys;
import net.minecraft.network.chat.Component;
import net.minecraft.util.SortedArraySet;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class CompositionCardPuller extends CardPuller
{
    /**
     * Separates a sub-set's code from its position in the composition.
     * <p>
     * A character no set code contains, so splitting at the FIRST one always
     * recovers the code.
     */
    public static final String PACK_TAG = "#";

    public final List<String> subSetCodes;
    protected List<CardSet> subSets;

    public CompositionCardPuller(JsonObject setJson, CardSet set) throws IllegalArgumentException
    {
        super(setJson, set);

        JsonArray subSetsJson = setJson.get(JsonKeys.SUB_SETS).getAsJsonArray();

        openSubSets = setJson.has(JsonKeys.OPEN_SUB_SETS)
            && setJson.get(JsonKeys.OPEN_SUB_SETS).getAsBoolean();

        subSetCodes = new ArrayList<>(subSetsJson.size());
        subSets = null;

        for(int i = 0; i < subSetsJson.size(); ++i)
        {
            subSetCodes.add(subSetsJson.get(i).getAsString());
        }
    }

    @Override
    public void postDBInit()
    {
        super.postDBInit();
        linkSubSets();
    }

    public void linkSubSets()
    {
        if(subSets == null)
        {
            subSets = new ArrayList<>(subSetCodes.size());

            CardSet subSet;
            for(String code : subSetCodes)
            {
                subSet = DdDatabase.SETS_LIST.get(code);

                if(subSet == null)
                {
                    DuelDimension.log("Can not find sub-set: " + code + " in set: " + set.code + " (" + set.name + ")");
                }
                else
                {
                    subSets.add(subSet);
                }
            }
        }
    }

    /**
     * Whether a sub-set that could be handed over sealed is opened instead.
     * <p>
     * <b>This is what a tin is.</b> A tin holds five real booster packs, and
     * those are independent sets — so the rule below hands the player five
     * SEALED PACK ITEMS and nothing else, which is why a tin "only gives one
     * card or one pack". That is right for a box that really does contain a
     * boxed structure deck ({@code SD09_SS} is a deck plus a booster, and the
     * deck should arrive as a deck), and wrong for a tin, where the packs are
     * the product rather than a container for it.
     * <p>
     * Opt-in per set rather than a change to the rule, so the four
     * deck-in-a-box compositions that predate this keep handing over sealed
     * items exactly as they did.
     */
    public final boolean openSubSets;

    @Override
    public List<ItemStack> open(Random random)
    {
        return open(random, null);
    }

    @Override
    public List<ItemStack> open(Random random, List<String> sourcesOut)
    {
        List<ItemStack> list = new ArrayList<>(0);

        // The pack's position in the tin, appended to its code below. A tin
        // holding two Ancient Prophecy packs opened TWO packs, and without this
        // their cards are eighteen entries all reading "ANPR" -- one run, one
        // row, and the product silently reads as having held a single
        // double-length pack. The reader splits the tag back off; see
        // PackOpeningScreen.groupSet.
        int ordinal = 0;

        for(CardSet subSet : subSets)
        {
            // A SUB-SET is labelled with the composition's own code, not its
            // own. A sub-set has no name, no date and no artwork -- that is
            // exactly what makes it a sub-set rather than a product -- so a
            // reveal that filed cards under it had nothing to draw and showed a
            // blank icon. The honest answer for "which pack did this come from"
            // is the tin, because a promo sub-set is not something you could
            // have bought on its own.
            //
            // The ordinal still comes from the position, so three Mega Packs
            // out of one tin remain three packs wearing the tin's picture.
            String tag = (subSet.isIndependentAndItem() ? subSet.code : set.code)
                + PACK_TAG + (ordinal++);
            if(subSet.isIndependentAndItem() && !openSubSets)
            {
                list.add(DdItems.SET.createItemForSet(subSet));
                if(sourcesOut != null)
                {
                    sourcesOut.add(tag);
                }
                continue;
            }
            // Recorded against the SUB-SET, not against this one: the whole
            // point is that the reveal can say which pack a card came out of,
            // and the same booster appearing twice in one tin is two separate
            // packs that each opened for themselves.
            List<ItemStack> cards = subSet.open(random);
            if(cards == null)
            {
                continue;
            }
            list.addAll(cards);
            if(sourcesOut != null)
            {
                for(int i = 0; i < cards.size(); i++)
                {
                    sourcesOut.add(tag);
                }
            }
        }

        return list;
    }

    @Override
    public void addInformation(List<Component> tooltip)
    {
        if(addInformationInComposition())
        {
            for(CardSet subSet : subSets)
            {
                if(subSet.isIndependentAndItem())
                {
                    tooltip.add(Component.literal(subSet.name));
                }
                else
                {
                    subSet.pull.addInformation(tooltip);
                }
            }
        }
    }

    @Override
    public boolean addInformationInComposition()
    {
        for(CardSet subSet : subSets)
        {
            if(!subSet.pull.addInformationInComposition())
            {
                return false;
            }
        }

        return true;
    }

    @Override
    public void addAllCardEntries(SortedArraySet<CardHolder> sortedSet)
    {
        for(CardSet subSet : subSets)
        {
            subSet.addAllCardEntries(sortedSet);
        }
    }
}
