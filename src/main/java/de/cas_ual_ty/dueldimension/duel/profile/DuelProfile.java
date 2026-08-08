package de.cas_ual_ty.dueldimension.duel.profile;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * One player's duelling life: what they own, what they have built, and what
 * they have unlocked.
 * <p>
 * Cards and decks arrive together. Opening a structure deck is not just a
 * handful of cards — it grants the cards <em>and</em> the deck itself, ready to
 * play, <em>and</em> the recipe, so it can be reloaded after the player has
 * pulled it apart. Those are the same object here: a {@link DeckList} with
 * {@link DeckList.Origin#STRUCTURE}, which is what splits the editor's list
 * into "Saved Recipes" and "Structure Decks".
 */
public final class DuelProfile
{
    private final Trunk trunk = new Trunk();
    private final List<DeckList> decks = new ArrayList<>();
    /** Structure deck ids already granted, so a second copy is not a second deck. */
    private final Set<String> unlockedStructures = new LinkedHashSet<>();
    /**
     * Cards the player has starred. Kept in insertion order so the list reads
     * as the order they were picked rather than by passcode, which means
     * nothing to anyone.
     */
    private final Set<Integer> favourites = new LinkedHashSet<>();
    private String activeDeck = "";
    /** The outfit this duelist is seen in; empty means their own skin. */
    private String outfit = "";

    public Trunk trunk()
    {
        return trunk;
    }

    public List<DeckList> decks()
    {
        return Collections.unmodifiableList(decks);
    }

    /** The player's own builds: everything the deck list shows. */
    public List<DeckList> ownDecks()
    {
        return decks.stream().filter(deck -> deck.origin() == DeckList.Origin.SAVED).toList();
    }

    /**
     * The player's own builds that they chose to offer as recipes.
     * <p>
     * Not simply "their decks". Every saved deck used to appear here, which
     * made the recipe list a second copy of the deck list.
     */
    public List<DeckList> savedRecipes()
    {
        return decks.stream()
            .filter(deck -> deck.origin() == DeckList.Origin.SAVED && deck.published())
            .toList();
    }

    /** Granted starter decks, for the "Starter Decks" recipe group. */
    public List<DeckList> starterDecks()
    {
        return decks.stream().filter(deck -> deck.origin() == DeckList.Origin.STARTER).toList();
    }

    /** Granted structure decks, for the "Structure Decks" recipe group. */
    public List<DeckList> structureDecks()
    {
        return decks.stream().filter(deck -> deck.origin() == DeckList.Origin.STRUCTURE).toList();
    }

    public Set<String> unlockedStructures()
    {
        return Collections.unmodifiableSet(unlockedStructures);
    }

    public Set<Integer> favourites()
    {
        return Collections.unmodifiableSet(favourites);
    }

    public boolean isFavourite(int passcode)
    {
        return favourites.contains(passcode);
    }

    /**
     * Stars a card, or unstars one already starred.
     *
     * @return true if it is now a favourite
     */
    public boolean toggleFavourite(int passcode)
    {
        if(favourites.remove(passcode))
        {
            return false;
        }
        favourites.add(passcode);
        return true;
    }

    public String outfit()
    {
        return outfit;
    }

    public void setOutfit(String id)
    {
        outfit = id == null ? "" : id;
    }

    public String activeDeck()
    {
        return activeDeck;
    }

    public void setActiveDeck(String name)
    {
        activeDeck = name == null ? "" : name;
    }

    /**
     * A deck of the player's own by that name, or null.
     * <p>
     * The lookup that every edit uses, and deliberately blind to granted decks.
     * A player's decks and the recipes they were given are separate namespaces:
     * using the "Yugi Muto" starter recipe should produce a deck called "Yugi
     * Muto", and asking one list whether a name was taken said it was, because
     * the recipe itself held it.
     */
    public DeckList savedNamed(String name)
    {
        for(DeckList deck : decks)
        {
            if(deck.origin() == DeckList.Origin.SAVED && deck.name().equals(name))
            {
                return deck;
            }
        }
        return null;
    }

    /**
     * Any deck by that name, the player's own first.
     * <p>
     * For the callers that mean "whatever holds this name" — copying a recipe,
     * reading the active deck. Own decks win, so a name held in both namespaces
     * resolves to the one the player can change.
     */
    public DeckList deckNamed(String name)
    {
        DeckList own = savedNamed(name);
        if(own != null)
        {
            return own;
        }
        for(DeckList deck : decks)
        {
            if(deck.name().equals(name))
            {
                return deck;
            }
        }
        return null;
    }

    public void addDeck(DeckList deck)
    {
        decks.add(deck);
    }

    /**
     * A detached value for the player attachment.
     * <p>
     * Profiles are edited through mutable deck and collection lists. Storing
     * the same instance again makes Fabric's attachment change check compare
     * an object with itself and report no change. A deep snapshot gives the
     * attachment a genuinely new value and prevents later client/server work
     * from mutating the value that was handed to persistence.
     */
    public DuelProfile snapshot()
    {
        DuelProfile copy = new DuelProfile();
        trunk.all().forEach(copy.trunk::add);
        for(DeckList deck : decks)
        {
            DeckList deckCopy = deck.copy(deck.name(), deck.origin());
            deckCopy.publish(deck.published());
            copy.decks.add(deckCopy);
        }
        copy.unlockedStructures.addAll(unlockedStructures);
        copy.favourites.addAll(favourites);
        copy.activeDeck = activeDeck;
        copy.outfit = outfit;
        return copy;
    }

    /** Removes one of the player's own decks; granted decks are not theirs to remove. */
    public boolean removeDeck(String name)
    {
        return decks.removeIf(deck -> deck.origin() == DeckList.Origin.SAVED
            && deck.name().equals(name));
    }

    /**
     * Opens a structure deck.
     * <p>
     * The cards are always added, because a second copy of a product really is
     * a second set of cards and that is what makes three-of a card reachable.
     * The deck and its recipe are added only once: a player who opens two
     * copies wants six cards, not two identically named decks they cannot tell
     * apart.
     *
     * @return true if this was the first copy, so the deck itself was granted
     */
    public boolean unlockStructureDeck(String id, String displayName,
        List<Integer> main, List<Integer> extra, List<Integer> side)
    {
        return unlockDeck(id, displayName, DeckList.Origin.STRUCTURE, main, extra, side);
    }

    /** A starter deck grants exactly as a structure deck does; only the group differs. */
    public boolean unlockStarterDeck(String id, String displayName,
        List<Integer> main, List<Integer> extra, List<Integer> side)
    {
        return unlockDeck(id, displayName, DeckList.Origin.STARTER, main, extra, side);
    }

    private boolean unlockDeck(String id, String displayName, DeckList.Origin origin,
        List<Integer> main, List<Integer> extra, List<Integer> side)
    {
        for(List<Integer> part : List.of(main, extra, side))
        {
            trunk.addAll(part);
        }
        if(!unlockedStructures.add(id))
        {
            return false;
        }
        decks.add(new DeckList(displayName, origin, main, extra, side));
        // A player with no deck yet should be able to duel straight away with
        // what they just opened.
        if(activeDeck.isEmpty())
        {
            activeDeck = displayName;
        }
        return true;
    }

    /** Copies a structure deck into an editable build of the player's own. */
    public DeckList copyAsRecipe(String structureName, String newName)
    {
        DeckList source = deckNamed(structureName);
        if(source == null)
        {
            return null;
        }
        DeckList copy = source.copy(newName, DeckList.Origin.SAVED);
        decks.add(copy);
        return copy;
    }

    /**
     * A whole profile: what the player owns, built and starred.
     * <p>
     * Every field is optional with an empty default, so a profile written
     * before any one of them existed still loads -- which is what the Forge
     * build's conditional writes achieved by hand. The names match that build's
     * exactly, so a world moved across reads without conversion.
     */
    public static final Codec<DuelProfile> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Trunk.CODEC.optionalFieldOf("Trunk", new Trunk()).forGetter(DuelProfile::trunk),
            DeckList.CODEC.listOf().optionalFieldOf("Decks", List.of())
                .forGetter(profile -> profile.decks),
            Codec.STRING.listOf().optionalFieldOf("Structures", List.of())
                .forGetter(profile -> List.copyOf(profile.unlockedStructures)),
            Codec.INT.listOf().optionalFieldOf("Favourites", List.of())
                .forGetter(profile -> List.copyOf(profile.favourites)),
            Codec.STRING.optionalFieldOf("Active", "").forGetter(DuelProfile::activeDeck),
            Codec.STRING.optionalFieldOf("Outfit", "").forGetter(DuelProfile::outfit)
        ).apply(instance, DuelProfile::of));

    private static DuelProfile of(Trunk trunk, List<DeckList> decks, List<String> structures,
        List<Integer> favourites, String activeDeck, String outfit)
    {
        DuelProfile profile = new DuelProfile();
        trunk.all().forEach(profile.trunk::add);
        profile.decks.addAll(decks);
        profile.unlockedStructures.addAll(structures);
        profile.favourites.addAll(favourites);
        profile.activeDeck = activeDeck;
        profile.outfit = outfit;
        return profile;
    }

}
