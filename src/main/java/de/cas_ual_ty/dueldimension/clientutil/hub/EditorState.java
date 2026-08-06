package de.cas_ual_ty.dueldimension.clientutil.hub;

import de.cas_ual_ty.dueldimension.DdDatabase;
import de.cas_ual_ty.dueldimension.card.properties.LevelMonsterProperties;
import de.cas_ual_ty.dueldimension.card.properties.MonsterProperties;
import de.cas_ual_ty.dueldimension.card.properties.Properties;
import de.cas_ual_ty.dueldimension.card.properties.Type;
import de.cas_ual_ty.dueldimension.duel.match.Banlist;
import de.cas_ual_ty.dueldimension.duel.profile.CardQuery;
import de.cas_ual_ty.dueldimension.duel.profile.DeckList;
import de.cas_ual_ty.dueldimension.duel.profile.Trunk;

import java.util.ArrayList;
import java.util.List;

/**
 * What the deck editor is working on: a collection, a deck, and a query.
 * <p>
 * Client-side and in memory. Server-backed profiles are not wired yet, so the
 * trunk is seeded from the card database — the editor is fully usable and every
 * rule it enforces is the real one, but the contents are a stand-in until packs
 * register cards for real. That is stated in the UI rather than hidden.
 */
public final class EditorState
{
    /** Copies of each card the seeded trunk grants. Three is the deck ceiling. */
    private static final int SEEDED_COPIES = 3;

    private static Trunk trunk;
    /** Every deck the player has, and which one the editor is on. */
    private static final de.cas_ual_ty.dueldimension.duel.profile.DuelProfile PROFILE =
        new de.cas_ual_ty.dueldimension.duel.profile.DuelProfile();
    private static int current;
    private static Banlist banlist = Banlist.none();
    private static CardQuery<Properties> query;
    private static List<Properties> visible = new ArrayList<>();
    private static boolean dirty = true;

    private EditorState()
    {
    }

    /**
     * Reads the mod's own card model for the query. Kept here rather than in
     * {@link CardQuery} so that class stays testable without a card database.
     */
    private static final CardQuery.Facets<Properties> FACETS = new CardQuery.Facets<>()
    {
        @Override
        public String name(Properties card)
        {
            return card.getName();
        }

        @Override
        public String text(Properties card)
        {
            return card.getText();
        }

        @Override
        public CardQuery.Kind kind(Properties card)
        {
            Type type = card.getType();
            if(type == Type.SPELL)
            {
                return CardQuery.Kind.SPELL;
            }
            return type == Type.TRAP ? CardQuery.Kind.TRAP : CardQuery.Kind.MONSTER;
        }

        @Override
        public String attribute(Properties card)
        {
            return card instanceof MonsterProperties monster ? monster.getAttribute() : null;
        }

        @Override
        public String species(Properties card)
        {
            return card instanceof MonsterProperties monster ? monster.getSpecies() : null;
        }

        @Override
        public int level(Properties card)
        {
            return card instanceof LevelMonsterProperties levelled ? levelled.level : 0;
        }

        @Override
        public int attack(Properties card)
        {
            return card instanceof MonsterProperties monster ? monster.getAtk() : 0;
        }

        @Override
        public int defence(Properties card)
        {
            // Not every monster has a DEF (Link monsters do not), so this is
            // read off the subclass that actually carries one.
            return card instanceof de.cas_ual_ty.dueldimension.card.properties.DefMonsterProperties def
                ? def.def : 0;
        }

        @Override
        public long id(Properties card)
        {
            return card.getId();
        }
    };

    public static Trunk trunk()
    {
        ensureSeeded();
        return trunk;
    }

    public static de.cas_ual_ty.dueldimension.duel.profile.DuelProfile profile()
    {
        return PROFILE;
    }

    /** The player's own builds -- what the Decks view lists. */
    public static List<DeckList> ownDecks()
    {
        decks();
        List<DeckList> own = new ArrayList<>(PROFILE.savedRecipes());
        if(own.isEmpty())
        {
            PROFILE.addDeck(new DeckList("New Deck", DeckList.Origin.SAVED));
            own = new ArrayList<>(PROFILE.savedRecipes());
        }
        return own;
    }

    /** Every deck, in the order they were made. */
    public static List<DeckList> decks()
    {
        ensureStarterDecks();
        if(PROFILE.decks().isEmpty())
        {
            // A player always has somewhere to put cards, so the editor is
            // never in the state of having no deck to edit.
            PROFILE.addDeck(new DeckList("New Deck", DeckList.Origin.SAVED));
        }
        return PROFILE.decks();
    }

    /**
     * Starter decks ARE structure decks: opening one grants its cards, the deck
     * itself ready to play, and the recipe. They are granted here for now
     * because pack opening is not wired to a profile yet — the same stand-in as
     * the seeded trunk, and the same single call
     * ({@link DuelProfile#unlockStructureDeck}) the real opening will make.
     */
    private static boolean startersGranted;

    private static void ensureStarterDecks()
    {
        if(startersGranted)
        {
            return;
        }
        startersGranted = true;
        for(de.cas_ual_ty.dueldimension.ocg.deck.StarterDecks.Entry entry
            : de.cas_ual_ty.dueldimension.ocg.deck.StarterDecks.ALL)
        {
            try
            {
                de.cas_ual_ty.dueldimension.ocg.deck.YdkDeck deck = entry.load();
                ensureSeeded();
                PROFILE.unlockStarterDeck(entry.id(), entry.displayName(),
                    deck.main(), deck.extra(), deck.side());
            }
            catch(Exception unavailable)
            {
                // A missing deck list is not worth failing the editor over.
            }
        }
    }

    /**
     * Grants a structure deck: its cards into the trunk, the deck itself, and
     * its recipe. This is the call an opened product makes.
     */
    public static void unlockStructureDeck(String id, String displayName,
        List<Integer> main, List<Integer> extra, List<Integer> side)
    {
        ensureSeeded();
        PROFILE.unlockStructureDeck(id, displayName, main, extra, side);
        dirty = true;
    }

    /** The deck being edited. */
    public static DeckList deck()
    {
        List<DeckList> all = decks();
        current = Math.max(0, Math.min(current, all.size() - 1));
        return all.get(current);
    }

    public static int currentIndex()
    {
        decks();
        return current;
    }

    public static void select(int index)
    {
        List<DeckList> all = decks();
        current = Math.max(0, Math.min(index, all.size() - 1));
    }

    /** Adds a deck and switches to it, with a name that is not already taken. */
    public static DeckList newDeck()
    {
        String name = "New Deck";
        for(int suffix = 2; PROFILE.deckNamed(name) != null; suffix++)
        {
            name = "New Deck " + suffix;
        }
        DeckList made = new DeckList(name, DeckList.Origin.SAVED);
        PROFILE.addDeck(made);
        current = PROFILE.decks().size() - 1;
        return made;
    }

    /**
     * Copies a deck as a new build of the player's own and switches to it.
     * <p>
     * A copy of a granted structure deck becomes SAVED rather than STRUCTURE:
     * it is the player's build from that point, editable and deletable, while
     * the original stays as the record of what was opened.
     */
    public static DeckList duplicate(int index)
    {
        List<DeckList> all = decks();
        DeckList source = all.get(Math.max(0, Math.min(index, all.size() - 1)));
        String name = source.name() + " copy";
        for(int suffix = 2; PROFILE.deckNamed(name) != null; suffix++)
        {
            name = source.name() + " copy " + suffix;
        }
        DeckList copy = source.copy(name, DeckList.Origin.SAVED);
        PROFILE.addDeck(copy);
        current = PROFILE.decks().size() - 1;
        return copy;
    }

    /**
     * Builds a new deck from a recipe and switches to it.
     * <p>
     * Named after the recipe, which is what a player expects, but suffixed if
     * that name is already taken -- the recipe itself still holds it, and two
     * decks alike could not be told apart. The assigned name is what the rename
     * prompt is pre-filled with, so the default shown is the one that will
     * actually be kept.
     */
    public static DeckList useRecipe(int index)
    {
        List<DeckList> all = decks();
        DeckList source = all.get(Math.max(0, Math.min(index, all.size() - 1)));
        String name = source.name();
        for(int suffix = 2; PROFILE.deckNamed(name) != null; suffix++)
        {
            name = source.name() + " " + suffix;
        }
        DeckList made = source.copy(name, DeckList.Origin.SAVED);
        PROFILE.addDeck(made);
        current = PROFILE.decks().size() - 1;
        return made;
    }

    /**
     * Renames the current deck. Refused if the name is blank or taken, since
     * decks are found by name and two alike could not be told apart.
     */
    public static boolean rename(String name)
    {
        String trimmed = name == null ? "" : name.strip();
        if(trimmed.isEmpty())
        {
            return false;
        }
        DeckList existing = PROFILE.deckNamed(trimmed);
        if(existing != null && existing != deck())
        {
            return false;
        }
        deck().rename(trimmed);
        return true;
    }

    /**
     * Deletes the current deck. A granted structure deck is left alone: it is
     * the recipe the cards came with, and deleting it would lose the record of
     * what was opened.
     *
     * @return null on success, else why not
     */
    public static String deleteCurrent()
    {
        DeckList target = deck();
        if(target.origin().isGranted())
        {
            return "Granted decks cannot be deleted";
        }
        if(PROFILE.savedRecipes().size() <= 1)
        {
            // Clearing it is the same outcome and leaves somewhere to build.
            target.main().clear();
            target.extra().clear();
            target.side().clear();
            target.rename("New Deck");
            return null;
        }
        PROFILE.removeDeck(target.name());
        current = Math.max(0, current - 1);
        return null;
    }

    public static Banlist banlist()
    {
        return banlist;
    }

    public static void setBanlist(Banlist value)
    {
        banlist = value;
    }

    public static CardQuery<Properties> query()
    {
        if(query == null)
        {
            query = new CardQuery<>(FACETS);
        }
        return query;
    }

    /** Marks the trunk view stale, so the next render re-runs the query. */
    public static void invalidate()
    {
        dirty = true;
    }

    /**
     * The trunk as the right-hand panel shows it: filtered and sorted.
     * <p>
     * Cached, because the query runs over the whole collection and the panel
     * asks for it every frame. Anything that changes the query calls
     * {@link #invalidate()}.
     */
    public static List<Properties> visible()
    {
        ensureSeeded();
        if(dirty)
        {
            List<Properties> owned = new ArrayList<>();
            for(int code : trunk.all().keySet())
            {
                Properties card = DdDatabase.PROPERTIES_LIST.get((long)code);
                if(card != null)
                {
                    owned.add(card);
                }
            }
            visible = query().apply(owned);
            dirty = false;
        }
        return visible;
    }

    /** True while the collection is the database stand-in rather than earned cards. */
    public static boolean isSeeded()
    {
        return true;
    }

    private static void ensureSeeded()
    {
        if(trunk != null)
        {
            return;
        }
        trunk = new Trunk();
        for(Properties card : DdDatabase.PROPERTIES_LIST)
        {
            if(card == null || card.getId() <= 0 || card.getIllegal())
            {
                continue;
            }
            trunk.add((int)card.getId(), SEEDED_COPIES);
        }
        dirty = true;
    }
}
