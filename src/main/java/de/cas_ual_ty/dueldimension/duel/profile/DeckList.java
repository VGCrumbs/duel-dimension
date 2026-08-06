package de.cas_ual_ty.dueldimension.duel.profile;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One saved deck: a name and three lists of passcodes.
 * <p>
 * A deck holds <em>codes</em>, not cards. That is what lets one owned copy of a
 * card back several decks at once — the deck says "a Dark Hole goes here" and
 * the {@link Trunk} says how many you own. Storing card instances instead would
 * mean a card could only ever be in one deck, which is the model this
 * deliberately does not use.
 */
public final class DeckList
{
    /** Where a deck came from, which is what splits the recipe list in two. */
    public enum Origin
    {
        /** Built by the player in the editor. */
        SAVED,
        /** Granted by a starter deck; loadable as a recipe. */
        STARTER,
        /** Granted by a structure deck; loadable as a recipe. */
        STRUCTURE;

        /**
         * Whether this is a granted product rather than one of the player's own
         * builds. Granted decks are the record of what was opened, so they are
         * loadable but never edited or deleted in place.
         */
        public boolean isGranted()
        {
            return this != SAVED;
        }
    }

    private String name;
    private final List<Integer> main = new ArrayList<>();
    private final List<Integer> extra = new ArrayList<>();
    private final List<Integer> side = new ArrayList<>();
    private final Origin origin;

    public DeckList(String name, Origin origin)
    {
        this.name = name;
        this.origin = origin;
    }

    public DeckList(String name, Origin origin, List<Integer> main, List<Integer> extra, List<Integer> side)
    {
        this(name, origin);
        this.main.addAll(main);
        this.extra.addAll(extra);
        this.side.addAll(side);
    }

    public String name()
    {
        return name;
    }

    public void rename(String newName)
    {
        name = newName;
    }

    public Origin origin()
    {
        return origin;
    }

    public List<Integer> main()
    {
        return main;
    }

    public List<Integer> extra()
    {
        return extra;
    }

    public List<Integer> side()
    {
        return side;
    }

    /** The three parts in the order the editor stacks them. */
    public List<Integer> partFor(Part part)
    {
        return switch(part)
        {
            case MAIN -> main;
            case EXTRA -> extra;
            case SIDE -> side;
        };
    }

    /** Which grid of the editor a card sits in. */
    public enum Part
    {
        MAIN(60),
        EXTRA(15),
        SIDE(15);

        private final int capacity;

        Part(int capacity)
        {
            this.capacity = capacity;
        }

        public int capacity()
        {
            return capacity;
        }
    }

    /**
     * How many copies of this card the whole deck holds.
     * <p>
     * Counted across all three parts, because the copy limit is defined that
     * way: three in the side deck plus one in the main is four copies, and no
     * card is allowed four.
     */
    public int copiesOf(int passcode)
    {
        int count = 0;
        for(List<Integer> part : List.of(main, extra, side))
        {
            for(int code : part)
            {
                if(code == passcode)
                {
                    count++;
                }
            }
        }
        return count;
    }

    /** Every distinct card in the deck, with its count. */
    public Map<Integer, Integer> counts()
    {
        Map<Integer, Integer> counts = new LinkedHashMap<>();
        for(List<Integer> part : List.of(main, extra, side))
        {
            for(int code : part)
            {
                counts.merge(code, 1, Integer::sum);
            }
        }
        return Collections.unmodifiableMap(counts);
    }

    public int size()
    {
        return main.size() + extra.size() + side.size();
    }

    public DeckList copy(String newName, Origin newOrigin)
    {
        return new DeckList(newName, newOrigin, main, extra, side);
    }

    public CompoundTag save()
    {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", name);
        tag.putString("Origin", origin.name());
        tag.put("Main", codes(main));
        tag.put("Extra", codes(extra));
        tag.put("Side", codes(side));
        return tag;
    }

    public static DeckList load(CompoundTag tag)
    {
        Origin origin;
        try
        {
            origin = Origin.valueOf(tag.getString("Origin"));
        }
        catch(IllegalArgumentException unknown)
        {
            origin = Origin.SAVED;
        }
        DeckList deck = new DeckList(tag.getString("Name"), origin);
        read(tag.getList("Main", Tag.TAG_INT), deck.main);
        read(tag.getList("Extra", Tag.TAG_INT), deck.extra);
        read(tag.getList("Side", Tag.TAG_INT), deck.side);
        return deck;
    }

    private static ListTag codes(List<Integer> from)
    {
        ListTag list = new ListTag();
        for(int code : from)
        {
            list.add(net.minecraft.nbt.IntTag.valueOf(code));
        }
        return list;
    }

    private static void read(ListTag from, List<Integer> into)
    {
        for(int i = 0; i < from.size(); i++)
        {
            into.add(from.getInt(i));
        }
    }
}
