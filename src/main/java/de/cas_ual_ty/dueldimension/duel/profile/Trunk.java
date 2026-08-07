package de.cas_ual_ty.dueldimension.duel.profile;

import com.mojang.serialization.Codec;

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

    /**
     * The collection, as a map of passcode to count.
     * <p>
     * Keyed by the passcode written as a string, which is the shape the Forge
     * build's {@code Cards} compound had, so an existing collection loads
     * unchanged. A key that is not a number is dropped rather than failing the
     * profile -- the same forgiveness the hand-written loader had, for the same
     * reason: one stray key is not worth a player's whole collection.
     */
    public static final Codec<Trunk> CODEC = Codec.unboundedMap(Codec.STRING, Codec.INT)
        .fieldOf("Cards").codec().xmap(Trunk::of, Trunk::written);

    private static Trunk of(Map<String, Integer> written)
    {
        Trunk trunk = new Trunk();
        written.forEach((key, count) ->
        {
            try
            {
                trunk.owned.put(Integer.parseInt(key), count);
            }
            catch(NumberFormatException notAPasscode)
            {
                // Not ours; skip it rather than refusing the whole collection.
            }
        });
        return trunk;
    }

    private Map<String, Integer> written()
    {
        Map<String, Integer> written = new LinkedHashMap<>();
        owned.forEach((code, count) -> written.put(Integer.toString(code), count));
        return written;
    }
}
