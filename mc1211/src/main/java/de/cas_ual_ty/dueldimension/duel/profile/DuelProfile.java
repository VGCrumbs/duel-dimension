package de.cas_ual_ty.dueldimension.duel.profile;

import com.mojang.serialization.Codec;
import de.cas_ual_ty.dueldimension.character.CharacterText;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.cas_ual_ty.dueldimension.card.CardSleevesType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
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
    /**
     * Sealed products the player has starred, by set CODE.
     * <p>
     * A separate set from {@link #favourites} rather than a shared one: a
     * card is keyed by passcode and a product by set code, and the two
     * namespaces have no reason to agree. Keyed by CODE and not by name
     * because the code is what the shop's stock carries and what survives a
     * set being retitled.
     */
    private final Set<String> favouritePacks = new LinkedHashSet<>();
    /**
     * Sleeves this player has bought or been given.
     * <p>
     * The first cosmetic in this mod that anyone has to <em>own</em>: a play
     * mat's colour is a file on the player's own disk, so it never needed an
     * entitlement to check. A sleeve is
     * sold, which means the answer to "may I wear this" has to live on the side
     * that also holds the money — here, beside the {@link Trunk}, persisted and
     * synced by the same Codec.
     * <p>
     * Holds only what was <em>granted</em>. The free sleeves are a rule rather
     * than a grant ({@link Sleeves#FREE}), so they never enter this set and can
     * never be lost from it.
     */
    private final Set<CardSleevesType> sleeves = new LinkedHashSet<>();
    /** Paid deck cases; the three basic colours are owned by rule. */
    private final Set<DeckBoxStyle> deckBoxes = EnumSet.noneOf(DeckBoxStyle.class);
    /**
     * Disks this player has bought. Same contract as {@link #sleeves} above:
     * only GRANTS live here, never the free plain disk, which is owned by the
     * rule in {@link DuelDisks#FREE} and so can never be lost.
     */
    private final Set<String> disks = new LinkedHashSet<>();
    /** Which disk is worn, by name. Empty means the free one. */
    private String activeDisk = "";
    /**
     * Whether the disk is actually ON.
     * <p>
     * Its own slot rather than an item in the off-hand: a duel disk is worn
     * equipment, and borrowing the off-hand made it compete with shields and
     * totems, put it at risk of being dropped, and meant "am I wearing my
     * disk" had four different answers scattered across the mod. One boolean
     * beside the disk it refers to is the whole slot.
     */
    private boolean diskWorn;

    /**
     * The created character, and whether it is being worn.
     * <p>
     * Saved here so it survives a restart with everything else this duellist
     * owns. An "Outfit" string used to live in this profile and was dropped; a
     * character is what it became, and it is a record rather than a name
     * because there is nothing to look up -- the models are in the jar and the
     * eight numbers ARE the character.
     */
    private de.cas_ual_ty.dueldimension.character.CharacterLook characterLook =
        de.cas_ual_ty.dueldimension.character.CharacterLook.DEFAULT;

    /**
     * Whether the custom model replaces the vanilla one.
     * <p>
     * Separate from having made one on purpose: a duellist may build a
     * character and still walk around as themselves, and turning it off must
     * not throw the character away.
     */
    private boolean characterShown;
    private String activeDeck = "";

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
    /**
     * The player's own decks, published or not.
     * <p>
     * Distinct from {@link #savedRecipes()}, which additionally requires
     * published() and therefore answers "how many recipes have I shared", not
     * "how many decks do I have". Deleting a deck asked the wrong one of these
     * and so refused to remove anything until at least two decks were shared.
     */
    public List<DeckList> savedDecks()
    {
        return decks.stream()
            .filter(deck -> deck.origin() == DeckList.Origin.SAVED)
            .toList();
    }

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

    public Set<String> favouritePacks()
    {
        return Collections.unmodifiableSet(favouritePacks);
    }

    public boolean isFavouritePack(String code)
    {
        return code != null && favouritePacks.contains(code);
    }

    /**
     * Stars a sealed product, or unstars one already starred.
     *
     * @return true if it is now a favourite
     */
    public boolean toggleFavouritePack(String code)
    {
        if(code == null || code.isEmpty())
        {
            return false;
        }
        if(favouritePacks.remove(code))
        {
            return false;
        }
        favouritePacks.add(code);
        return true;
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

    /**
     * Every sleeve this player may dress a deck in, free ones included.
     * <p>
     * What a picker iterates to decide what is greyed out. In enum order rather
     * than in the order they were bought, so the list does not reshuffle itself
     * under the player after a purchase.
     */
    public Set<CardSleevesType> ownedSleeves()
    {
        EnumSet<CardSleevesType> all = EnumSet.copyOf(Sleeves.FREE);
        all.addAll(sleeves);
        return Collections.unmodifiableSet(all);
    }

    /** The question every sleeve change has to pass, and the only one. */
    public boolean ownsSleeve(CardSleevesType sleeve)
    {
        return sleeve != null && (Sleeves.isFree(sleeve) || sleeves.contains(sleeve));
    }

    /**
     * Records that this player owns a sleeve.
     *
     * @return true if this granted something they did not already have, so a
     *         shop can tell a purchase from a second click on the same button
     */
    public boolean grantSleeve(CardSleevesType sleeve)
    {
        if(sleeve == null || Sleeves.isFree(sleeve))
        {
            // Nothing to grant, and nothing worth writing to disk: a free sleeve
            // is owned by the rule that says so.
            return false;
        }
        return sleeves.add(sleeve);
    }

    public Set<DeckBoxStyle> ownedDeckBoxes()
    {
        EnumSet<DeckBoxStyle> all = EnumSet.noneOf(DeckBoxStyle.class);
        for(DeckBoxStyle style : DeckBoxStyle.values())
        {
            if(style.isFree() || deckBoxes.contains(style))
            {
                all.add(style);
            }
        }
        return Collections.unmodifiableSet(all);
    }

    public boolean ownsDeckBox(DeckBoxStyle style)
    {
        return style != null && (style.isFree() || deckBoxes.contains(style));
    }

    public boolean grantDeckBox(DeckBoxStyle style)
    {
        return style != null && style.isPurchasable() && deckBoxes.add(style);
    }

    /** Same contract as {@link #grantSleeve}, for disks. */
    public boolean grantDisk(String disk)
    {
        if(!DuelDisks.isPurchasable(disk))
        {
            // Unknown, or free and therefore owned by the rule that says so.
            return false;
        }
        return disks.add(disk);
    }

    /** Every disk this player may wear, the free one included. */
    public Set<String> ownedDisks()
    {
        Set<String> all = new LinkedHashSet<>(DuelDisks.FREE);
        all.addAll(disks);
        return all;
    }

    /** The question every disk change has to pass, and the only one. */
    public boolean ownsDisk(String disk)
    {
        return DuelDisks.isKnown(disk) && (DuelDisks.isFree(disk) || disks.contains(disk));
    }

    /**
     * The disk this player wears.
     * <p>
     * Answered rather than stored blindly: a disk that was active and is
     * somehow no longer owned falls back to the free one, so losing a grant
     * can never leave a player unable to duel.
     */
    public String activeDisk()
    {
        return ownsDisk(activeDisk) ? activeDisk : DuelDisks.DEFAULT;
    }

    /** Whether the active disk is currently being worn. */
    public boolean diskWorn()
    {
        return diskWorn;
    }

    public de.cas_ual_ty.dueldimension.character.CharacterLook characterLook()
    {
        return characterLook;
    }

    /** Whether this player is drawn as their character rather than themselves. */
    public boolean characterShown()
    {
        return characterShown;
    }

    public void setCharacter(de.cas_ual_ty.dueldimension.character.CharacterLook look,
        boolean shown)
    {
        // Never null: a profile with no character still answers, with the one
        // the creator opens on.
        characterLook = look == null
            ? de.cas_ual_ty.dueldimension.character.CharacterLook.DEFAULT : look;
        characterShown = shown;
    }

    public void setDiskWorn(boolean worn)
    {
        diskWorn = worn;
    }

    /** @return true if the disk was owned and is now active */
    public boolean setActiveDisk(String disk)
    {
        if(!ownsDisk(disk))
        {
            return false;
        }
        activeDisk = disk;
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
     * Moves one player-built deck to another player's deck position.
     * Granted decks share the persisted list but are not manually arranged.
     */
    public boolean moveSavedDeck(String name, String targetName)
    {
        List<DeckList> saved = new ArrayList<>(savedDecks());
        int from = -1;
        int to = -1;
        for(int i = 0; i < saved.size(); i++)
        {
            if(saved.get(i).name().equals(name))
            {
                from = i;
            }
            if(saved.get(i).name().equals(targetName))
            {
                to = i;
            }
        }
        if(from < 0 || to < 0 || from == to)
        {
            return false;
        }

        DeckList moved = saved.remove(from);
        saved.add(to, moved);
        int savedIndex = 0;
        for(int i = 0; i < decks.size(); i++)
        {
            if(decks.get(i).origin() == DeckList.Origin.SAVED)
            {
                decks.set(i, saved.get(savedIndex++));
            }
        }
        return true;
    }

    /**
     * A detached value for the player attachment.
     * <p>
     * Profiles are edited through mutable deck and collection lists. Storing
     * the same instance again makes Fabric's attachment change check compare
     * an object with itself and report no change. A deep snapshot gives the
     * attachment a genuinely new value and prevents later client/server work
     * from mutating the value that was handed to persistence.
     * <p>
     * <b>This copies field by field, and the copy is what persists.</b> A field
     * added to the class and to {@link #CODEC} but forgotten here does not fail
     * anywhere — it simply saves as empty, every time, for everyone. Anything
     * added above belongs in here too. A deck's own sleeve rides along inside
     * {@link DeckList#copy}.
     */
    /**
     * The last match this player ARRANGED, so the next one they arrange starts
     * from it rather than from the built-in default.
     * <p>
     * Remembered for the host, because the host is the one who chooses: a
     * guest never sets anything, so there would be nothing of theirs to
     * remember. Kept on the profile rather than on the lobby because a lobby
     * lasts one challenge and the point is to outlive it.
     */
    private de.cas_ual_ty.dueldimension.duel.match.MatchConfig matchConfig =
        de.cas_ual_ty.dueldimension.duel.match.MatchConfig.DEFAULT;

    public de.cas_ual_ty.dueldimension.duel.match.MatchConfig matchConfig()
    {
        return matchConfig;
    }

    public void rememberMatchConfig(de.cas_ual_ty.dueldimension.duel.match.MatchConfig config)
    {
        matchConfig = config == null
            ? de.cas_ual_ty.dueldimension.duel.match.MatchConfig.DEFAULT : config.sanitised();
    }

    public DuelProfile snapshot()
    {
        DuelProfile copy = new DuelProfile();
        // Printing for printing, NOT through trunk.all(). That flattened map is
        // passcode to total, and feeding it back in resolved to the two-argument
        // add, which files everything under Trunk.UNKNOWN_RARITY on art 0. Since
        // this copy is what persists, every rarity and every artwork the
        // collection knew was destroyed on each save while the counts stayed
        // right -- so nothing ever looked wrong.
        trunk.copyInto(copy.trunk);
        for(DeckList deck : decks)
        {
            DeckList deckCopy = deck.copy(deck.name(), deck.origin());
            deckCopy.publish(deck.published());
            copy.decks.add(deckCopy);
        }
        copy.unlockedStructures.addAll(unlockedStructures);
        copy.favourites.addAll(favourites);
        copy.favouritePacks.addAll(favouritePacks);
        copy.sleeves.addAll(sleeves);
        copy.deckBoxes.addAll(deckBoxes);
        // Copied for the same reason as the sleeves beside them: a field left
        // out of here is lost on EVERY save, not only on a duplication.
        copy.disks.addAll(disks);
        copy.activeDisk = activeDisk;
        copy.diskWorn = diskWorn;
        copy.characterLook = characterLook;
        copy.characterShown = characterShown;
        copy.activeDeck = activeDeck;
        // Carried for the reason spelled out above the disks: snapshot() is
        // what persists, so a field left out here is lost on every save.
        copy.matchConfig = matchConfig;
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

    /**
     * Records a deck the player already holds the cards for.
     * <p>
     * The card shop's own purchase path pulls the product and puts every card
     * into the trunk WITH the rarity and artwork that printing specifies, which
     * {@link #unlockDeck} cannot do -- it takes bare passcodes. Letting it add
     * them again would hand out two of everything in the product.
     *
     * @return true if this was the first copy, so the deck itself was granted
     */
    public boolean unlockBoughtDeck(String id, String displayName, DeckList.Origin origin,
        List<Integer> main, List<Integer> extra, List<Integer> side)
    {
        return unlockDeck(id, displayName, origin, main, extra, side, false);
    }

    private boolean unlockDeck(String id, String displayName, DeckList.Origin origin,
        List<Integer> main, List<Integer> extra, List<Integer> side)
    {
        return unlockDeck(id, displayName, origin, main, extra, side, true);
    }

    private boolean unlockDeck(String id, String displayName, DeckList.Origin origin,
        List<Integer> main, List<Integer> extra, List<Integer> side, boolean addCards)
    {
        if(addCards)
        {
            for(List<Integer> part : List.of(main, extra, side))
            {
                trunk.addAll(part);
            }
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
     * <p>
     * This is also the wire format: {@code ProfilePayloads.Sync} carries a
     * {@code DuelProfile} through this Codec, so {@code Sleeves} reaching the
     * client is not a second message to remember to send — it is the same one
     * fact written once. Which is the point of a profile having a Codec at all.
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
            // Optional and empty by default, so every profile saved before
            // products could be starred loads with none.
            Codec.STRING.listOf().optionalFieldOf("FavouritePacks", List.of())
                .forGetter(profile -> List.copyOf(profile.favouritePacks)),
            Sleeves.CODEC.listOf().optionalFieldOf("Sleeves", List.of())
                .forGetter(profile -> List.copyOf(profile.sleeves)),
            DeckBoxStyle.CODEC.listOf().optionalFieldOf("DeckBoxes", List.of())
                .forGetter(profile -> List.copyOf(profile.deckBoxes)),
            // Optional and empty by default, so every profile saved before disks
            // were sold loads with just the free one.
            DuelDisks.CODEC.listOf().optionalFieldOf("Disks", List.of())
                .forGetter(profile -> List.copyOf(profile.disks)),
            Codec.STRING.optionalFieldOf("ActiveDisk", "")
                .forGetter(profile -> profile.activeDisk),
            Codec.BOOL.optionalFieldOf("DiskWorn", false)
                .forGetter(profile -> profile.diskWorn),
            // An "Outfit" string was written here too. It is no longer read,
            // and a key a record codec does not name is ignored rather than
            // refused -- so every profile saved with one still loads, and
            // whatever each player last wore is still in their save file if
            // outfits come back.
            Codec.STRING.optionalFieldOf("Active", "").forGetter(DuelProfile::activeDeck),
            // The character, as the one string that describes it. Optional and
            // empty by default, so every profile saved before characters
            // existed loads with none and the creator opens on its default.
            Codec.STRING.optionalFieldOf("Character", "")
                .forGetter(profile -> CharacterText.write(profile.characterLook)),
            Codec.BOOL.optionalFieldOf("CharacterShown", false)
                .forGetter(profile -> profile.characterShown),
            // Optional and defaulting to the built-in match, so a profile saved
            // before this loads as a player who has never arranged one.
            de.cas_ual_ty.dueldimension.duel.match.MatchConfig.CODEC
                .optionalFieldOf("LastMatch",
                    de.cas_ual_ty.dueldimension.duel.match.MatchConfig.DEFAULT)
                .forGetter(DuelProfile::matchConfig)
        ).apply(instance, DuelProfile::of));

    private static DuelProfile of(Trunk trunk, List<DeckList> decks, List<String> structures,
        List<Integer> favourites, List<String> favouritePacks,
        List<CardSleevesType> sleeves, List<DeckBoxStyle> deckBoxes,
        List<String> disks, String activeDisk, boolean diskWorn, String activeDeck,
        String character, boolean characterShown,
        de.cas_ual_ty.dueldimension.duel.match.MatchConfig matchConfig)
    {
        DuelProfile profile = new DuelProfile();
        // Copied rather than kept: the optionalFieldOf default above is a single
        // shared Trunk instance, so holding onto it would give every profile
        // without a saved collection the same one. Printing for printing, for
        // the reason snapshot() spells out -- and this one runs on every load
        // AND on every ProfilePayloads.Sync decode, so flattening here meant the
        // client's collection was always rarity-blind and art-blind as well.
        trunk.copyInto(profile.trunk);
        profile.decks.addAll(decks);
        profile.unlockedStructures.addAll(structures);
        profile.favourites.addAll(favourites);
        profile.favouritePacks.addAll(favouritePacks);
        // Through grantSleeve rather than into the set, so the two things it
        // refuses are refused on the way in as well: a free sleeve saved by an
        // older rule is dropped rather than kept as a stale grant, and an id
        // this build no longer has reads as the plain back and lands in the
        // same bin. A profile therefore cleans itself up the next time it saves.
        sleeves.forEach(profile::grantSleeve);
        deckBoxes.forEach(profile::grantDeckBox);
        // Through grantDisk for the same reason: a free disk saved by an older
        // rule is dropped rather than kept as a stale grant.
        disks.forEach(profile::grantDisk);
        profile.activeDisk = activeDisk;
        profile.diskWorn = diskWorn;
        profile.characterLook = CharacterText.read(character);
        profile.characterShown = characterShown;
        profile.rememberMatchConfig(matchConfig);
        profile.activeDeck = activeDeck;
        return profile;
    }

}
