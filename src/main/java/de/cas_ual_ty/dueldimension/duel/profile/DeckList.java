package de.cas_ual_ty.dueldimension.duel.profile;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.cas_ual_ty.dueldimension.card.CardSleevesType;

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
    /**
     * The sleeve this deck is dressed in.
     * <p>
     * On the deck rather than on the player, because that is the choice being
     * made: a duellist runs a burn deck in red and a dragon deck in gold, and a
     * single per-player sleeve would make them pick again every time they swap
     * decks. Held as the enum, stored as its name — see {@link Sleeves}.
     */
    private CardSleevesType sleeve = Sleeves.DEFAULT;

    /**
     * Which artwork each copy wears, one entry per position in the list beside
     * it. 0 is the printed art, which is what an absent or short list means.
     * <p>
     * <b>Per POSITION, not per card.</b> Three Dark Magicians can carry three
     * different arts, which is the whole point — a map keyed by passcode could
     * not say that.
     * <p>
     * <b>And therefore it cannot be carried over on save the way the sleeve is.</b>
     * {@code DeckEdits.saveDeck} rebuilds a deck from the payload, and the sleeve
     * survives by being copied off the old object; that trick is wrong here,
     * because removing one card shifts every position after it and the old
     * indices would land on the wrong copies. Arts have to travel WITH the lists
     * that give them meaning.
     */
    private final List<Integer> mainArts = new ArrayList<>();
    private final List<Integer> extraArts = new ArrayList<>();
    private final List<Integer> sideArts = new ArrayList<>();

    /** The artwork the copy at this position wears; 0 when nothing was chosen. */
    public int artAt(List<Integer> part, int index)
    {
        List<Integer> arts = artsFor(part);
        return index >= 0 && index < arts.size() ? arts.get(index) : 0;
    }

    /** The art list belonging to one of this deck's three parts. */
    public List<Integer> artsFor(List<Integer> part)
    {
        if(part == extra)
        {
            return extraArts;
        }
        if(part == side)
        {
            return sideArts;
        }
        return mainArts;
    }

    public List<Integer> mainArts()
    {
        return mainArts;
    }

    public List<Integer> extraArts()
    {
        return extraArts;
    }

    public List<Integer> sideArts()
    {
        return sideArts;
    }

    /**
     * Records the art for one position, padding with the printed art so the
     * list always lines up with the cards it describes.
     */
    public void setArtAt(List<Integer> part, int index, int art)
    {
        List<Integer> arts = artsFor(part);
        while(arts.size() <= index)
        {
            arts.add(0);
        }
        arts.set(index, art);
    }

    public void setArts(List<Integer> mainArts, List<Integer> extraArts, List<Integer> sideArts)
    {
        this.mainArts.clear();
        this.mainArts.addAll(mainArts);
        this.extraArts.clear();
        this.extraArts.addAll(extraArts);
        this.sideArts.clear();
        this.sideArts.addAll(sideArts);
    }

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

    public CardSleevesType sleeve()
    {
        return sleeve;
    }

    /**
     * Dresses the deck.
     * <p>
     * Deliberately does <em>not</em> check ownership: a deck knows nothing about
     * who owns it, and a check here would be one a caller could skip by writing
     * the field some other way. Entitlement is the profile's to answer and
     * {@link DeckEdits#setDeckSleeve} is the only place a client's request
     * reaches this.
     */
    public void setSleeve(CardSleevesType newSleeve)
    {
        sleeve = newSleeve == null ? Sleeves.DEFAULT : newSleeve;
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

    /**
     * The same deck under a new name.
     * <p>
     * Carries the sleeve, because a copy of a deck is that deck: duplicating
     * "Burn" should not hand back a copy in the plain back. {@code published} is
     * the one thing left behind on purpose — offering the original as a recipe
     * is not a statement about every deck ever built from it, which is why
     * {@link DeckEdits#copyRecipe} says so explicitly.
     */
    public DeckList copy(String newName, Origin newOrigin)
    {
        DeckList copy = new DeckList(newName, newOrigin, main, extra, side);
        copy.sleeve = sleeve;
        // The artworks come too, and this line is load-bearing far beyond
        // duplicating a deck: DuelProfile.snapshot() copies every deck through
        // here, and the SNAPSHOT is what gets persisted. Leaving arts out did
        // not lose them when a deck was copied -- it lost them on every save,
        // because the object written to disk was a copy that never had them.
        copy.setArts(mainArts, extraArts, sideArts);
        return copy;
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
     * <p>
     * {@code Sleeve} is optional for exactly that reason as well. A deck saved
     * before sleeves existed has no such field, so the optional default applies
     * and it loads dressed in the plain card back — which is what it was being
     * drawn in anyway. Nothing else about that deck is touched, and saving it
     * again simply writes the field for the first time.
     */
    public static final Codec<DeckList> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.STRING.fieldOf("Name").forGetter(DeckList::name),
            Origin.CODEC.optionalFieldOf("Origin", Origin.SAVED).forGetter(DeckList::origin),
            Codec.BOOL.optionalFieldOf("Recipe", false).forGetter(deck -> deck.published),
            Codec.INT.listOf().optionalFieldOf("Main", List.of()).forGetter(DeckList::main),
            Codec.INT.listOf().optionalFieldOf("Extra", List.of()).forGetter(DeckList::extra),
            Codec.INT.listOf().optionalFieldOf("Side", List.of()).forGetter(DeckList::side),
            Sleeves.CODEC.optionalFieldOf("Sleeve", Sleeves.DEFAULT).forGetter(DeckList::sleeve),
            // Optional and empty-by-default, so every deck saved before this
            // existed loads with every copy on its printed art.
            Codec.INT.listOf().optionalFieldOf("MainArts", List.of()).forGetter(DeckList::mainArts),
            Codec.INT.listOf().optionalFieldOf("ExtraArts", List.of()).forGetter(DeckList::extraArts),
            Codec.INT.listOf().optionalFieldOf("SideArts", List.of()).forGetter(DeckList::sideArts)
        ).apply(instance, DeckList::of));

    private static DeckList of(String name, Origin origin, boolean published,
        List<Integer> main, List<Integer> extra, List<Integer> side, CardSleevesType sleeve,
        List<Integer> mainArts, List<Integer> extraArts, List<Integer> sideArts)
    {
        DeckList deck = new DeckList(name, origin, main, extra, side);
        deck.published = published;
        deck.sleeve = sleeve;
        deck.setArts(mainArts, extraArts, sideArts);
        return deck;
    }

}
