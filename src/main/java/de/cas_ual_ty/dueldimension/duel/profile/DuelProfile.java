package de.cas_ual_ty.dueldimension.duel.profile;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

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

    public Trunk trunk()
    {
        return trunk;
    }

    public List<DeckList> decks()
    {
        return Collections.unmodifiableList(decks);
    }

    /** The player's own builds, for the "Saved Recipes" half of the list. */
    public List<DeckList> savedRecipes()
    {
        return decks.stream().filter(deck -> deck.origin() == DeckList.Origin.SAVED).toList();
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

    public String activeDeck()
    {
        return activeDeck;
    }

    public void setActiveDeck(String name)
    {
        activeDeck = name == null ? "" : name;
    }

    public DeckList deckNamed(String name)
    {
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

    public boolean removeDeck(String name)
    {
        return decks.removeIf(deck -> deck.name().equals(name));
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

    public CompoundTag save()
    {
        CompoundTag tag = new CompoundTag();
        tag.put("Trunk", trunk.save());
        ListTag list = new ListTag();
        decks.forEach(deck -> list.add(deck.save()));
        tag.put("Decks", list);
        ListTag unlocked = new ListTag();
        unlockedStructures.forEach(id -> unlocked.add(net.minecraft.nbt.StringTag.valueOf(id)));
        tag.put("Structures", unlocked);
        // An int array rather than a list of tags: this is a few hundred
        // numbers travelling on every profile sync.
        tag.putIntArray("Favourites", new ArrayList<>(favourites));
        tag.putString("Active", activeDeck);
        return tag;
    }

    public static DuelProfile load(CompoundTag tag)
    {
        DuelProfile profile = new DuelProfile();
        Trunk loaded = Trunk.load(tag.getCompound("Trunk"));
        loaded.all().forEach(profile.trunk::add);
        ListTag decks = tag.getList("Decks", Tag.TAG_COMPOUND);
        for(int i = 0; i < decks.size(); i++)
        {
            profile.decks.add(DeckList.load(decks.getCompound(i)));
        }
        ListTag structures = tag.getList("Structures", Tag.TAG_STRING);
        for(int i = 0; i < structures.size(); i++)
        {
            profile.unlockedStructures.add(structures.getString(i));
        }
        for(int passcode : tag.getIntArray("Favourites"))
        {
            profile.favourites.add(passcode);
        }
        profile.activeDeck = tag.getString("Active");
        return profile;
    }
}
