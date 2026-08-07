package de.cas_ual_ty.dueldimension.duel.profile;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

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

        /**
         * Lenient on the way in, exactly as the hand-written loader was: an
         * origin this build does not recognise reads as SAVED rather than
         * failing the whole profile. A deck of unknown provenance is still the
         * player's deck.
         */
        public static final Codec<Origin> CODEC = Codec.STRING.xmap(name ->
        {
            try
            {
                return valueOf(name);
            }
            catch(IllegalArgumentException unknown)
            {
                return SAVED;
            }
        }, Origin::name);
    }

    private String name;
    /**
     * Whether the player has offered this deck as a recipe.
     * <p>
     * Off by default, which is the point: making a deck used to put it in the
     * recipe list as well, so a player with six decks had six recipes they
     * never asked for. A recipe is something you choose to keep as a starting
     * point, so it is now something you say.
     */
    private boolean published;
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

    /** Granted decks are recipes by their nature; the player's are by choice. */
    public boolean published()
    {
        return origin.isGranted() || published;
    }

    public void publish(boolean asRecipe)
    {
        published = asRecipe;
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

    /**
     * How a deck is written down.
     * <p>
     * A Codec rather than the save/load pair this replaces. Both of the places
     * a profile now persists -- Fabric's attachments and {@code SavedDataType}
     * -- ask for one, and a class with a Codec that also hand-writes its own
     * NBT has two descriptions of its own shape to keep in step.
     * <p>
     * The field names are the ones the Forge build wrote, so a world saved by
     * that build reads here unchanged. {@code Recipe} and the three card lists
     * are optional for the same reason they were conditional there: a deck
     * saved before recipes existed simply is not one.
     */
    public static final Codec<DeckList> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.STRING.fieldOf("Name").forGetter(DeckList::name),
            Origin.CODEC.optionalFieldOf("Origin", Origin.SAVED).forGetter(DeckList::origin),
            Codec.BOOL.optionalFieldOf("Recipe", false).forGetter(deck -> deck.published),
            Codec.INT.listOf().optionalFieldOf("Main", List.of()).forGetter(DeckList::main),
            Codec.INT.listOf().optionalFieldOf("Extra", List.of()).forGetter(DeckList::extra),
            Codec.INT.listOf().optionalFieldOf("Side", List.of()).forGetter(DeckList::side)
        ).apply(instance, DeckList::of));

    private static DeckList of(String name, Origin origin, boolean published,
        List<Integer> main, List<Integer> extra, List<Integer> side)
    {
        DeckList deck = new DeckList(name, origin, main, extra, side);
        deck.published = published;
        return deck;
    }

}
