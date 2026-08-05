package de.cas_ual_ty.dueldimension.duel.profile;

import net.minecraft.nbt.CompoundTag;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every card a player owns, and how many of each.
 * <p>
 * The trunk is a <em>collection</em>, not a container: taking a card out to
 * build a deck does not remove it, so the same three copies of a card can sit
 * in every deck the player owns simultaneously. That is the whole point of the
 * shared pool, and it is why decks store passcodes while this stores counts.
 * <p>
 * Cards arrive here by being registered — opening a pack, unlocking a structure
 * deck, or picking up a physical card. Nothing ever needs to be spent to build
 * with it afterwards.
 */
public final class Trunk
{
    private final Map<Integer, Integer> owned = new LinkedHashMap<>();

    /** How many copies of this card the player owns. */
    public int countOf(int passcode)
    {
        return owned.getOrDefault(passcode, 0);
    }

    public boolean has(int passcode)
    {
        return countOf(passcode) > 0;
    }

    /**
     * Adds copies.
     *
     * @return how many were actually added, which is all of them: a collection
     *         has no capacity
     */
    public int add(int passcode, int copies)
    {
        if(copies <= 0)
        {
            return 0;
        }
        owned.merge(passcode, copies, Integer::sum);
        return copies;
    }

    public void addAll(Iterable<Integer> passcodes)
    {
        for(int code : passcodes)
        {
            add(code, 1);
        }
    }

    /**
     * Removes copies, never below zero.
     *
     * @return how many were actually removed
     */
    public int remove(int passcode, int copies)
    {
        int have = countOf(passcode);
        int taken = Math.min(have, Math.max(0, copies));
        if(taken <= 0)
        {
            return 0;
        }
        if(have - taken <= 0)
        {
            owned.remove(passcode);
        }
        else
        {
            owned.put(passcode, have - taken);
        }
        return taken;
    }

    /** The whole collection, for the editor's right-hand panel. */
    public Map<Integer, Integer> all()
    {
        return Collections.unmodifiableMap(owned);
    }

    public int distinctCards()
    {
        return owned.size();
    }

    public int totalCards()
    {
        int total = 0;
        for(int count : owned.values())
        {
            total += count;
        }
        return total;
    }

    public CompoundTag save()
    {
        CompoundTag tag = new CompoundTag();
        CompoundTag cards = new CompoundTag();
        owned.forEach((code, count) -> cards.putInt(Integer.toString(code), count));
        tag.put("Cards", cards);
        return tag;
    }

    public static Trunk load(CompoundTag tag)
    {
        Trunk trunk = new Trunk();
        CompoundTag cards = tag.getCompound("Cards");
        for(String key : cards.getAllKeys())
        {
            try
            {
                trunk.owned.put(Integer.parseInt(key), cards.getInt(key));
            }
            catch(NumberFormatException malformed)
            {
                // A key that is not a passcode is not ours; skip it rather
                // than refusing to load the whole collection.
            }
        }
        return trunk;
    }
}
