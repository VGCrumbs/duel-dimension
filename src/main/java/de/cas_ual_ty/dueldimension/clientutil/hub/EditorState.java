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
    private static DeckList deck = new DeckList("New Deck", DeckList.Origin.SAVED);
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

    public static DeckList deck()
    {
        return deck;
    }

    public static void setDeck(DeckList value)
    {
        deck = value;
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
