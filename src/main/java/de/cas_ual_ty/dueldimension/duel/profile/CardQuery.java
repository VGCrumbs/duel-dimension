package de.cas_ual_ty.dueldimension.duel.profile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The trunk's search, narrowing and sorting, as data rather than as UI.
 * <p>
 * Everything the editor's filter bar can express lives here, so the same query
 * can be unit tested, reset in one call, and later reused by anything else that
 * needs to search a collection. The screen only binds widgets to these fields.
 * <p>
 * A filter set that is <em>empty</em> means "do not narrow on this axis" rather
 * than "match nothing". That is what makes the bar additive: ticking DARK
 * narrows to DARK, and unticking it widens again, without the UI needing to
 * special-case the all-off state.
 *
 * @param <C> the card type, so this stays independent of the mod's own model
 */
public final class CardQuery<C>
{
    /** What a card is, at the coarsest level. */
    public enum Kind
    {
        MONSTER, SPELL, TRAP
    }

    /** How the trunk is ordered. */
    public enum Sort
    {
        NAME("Name"),
        LEVEL("Level"),
        ATTACK("ATK"),
        DEFENCE("DEF"),
        KIND("Type"),
        ID("ID");

        private final String label;

        Sort(String label)
        {
            this.label = label;
        }

        public String label()
        {
            return label;
        }

        public Sort next()
        {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    /**
     * What the query needs to know about a card. Supplied by the caller so this
     * class does not depend on the mod's card model, which is also what lets
     * the tests use a trivial stand-in.
     */
    public interface Facets<C>
    {
        String name(C card);

        String text(C card);

        Kind kind(C card);

        /** Attribute name (DARK, LIGHT…), or null for a spell or trap. */
        String attribute(C card);

        /** Species/type name (Dragon, Spellcaster…), or null. */
        String species(C card);

        /** Level, rank or link rating; 0 when the card has none. */
        int level(C card);

        int attack(C card);

        int defence(C card);

        long id(C card);
    }

    private final Facets<C> facets;

    private String text = "";
    private final Set<Kind> kinds = EnumSet.noneOf(Kind.class);
    private final Set<String> attributes = new java.util.LinkedHashSet<>();
    private final Set<String> species = new java.util.LinkedHashSet<>();
    /** Inclusive level band; 0 on either end means "unbounded that way". */
    private int minLevel;
    private int maxLevel;
    private Sort sort = Sort.NAME;
    private boolean descending;

    public CardQuery(Facets<C> facets)
    {
        this.facets = facets;
    }

    // ---- the filter bar binds to these ----

    public String text()
    {
        return text;
    }

    public void setText(String value)
    {
        text = value == null ? "" : value;
    }

    public Set<Kind> kinds()
    {
        return kinds;
    }

    public void toggleKind(Kind kind)
    {
        if(!kinds.remove(kind))
        {
            kinds.add(kind);
        }
    }

    public Set<String> attributes()
    {
        return attributes;
    }

    public void toggleAttribute(String attribute)
    {
        if(!attributes.remove(attribute))
        {
            attributes.add(attribute);
        }
    }

    public Set<String> species()
    {
        return species;
    }

    public void toggleSpecies(String value)
    {
        if(!species.remove(value))
        {
            species.add(value);
        }
    }

    public void setLevelRange(int min, int max)
    {
        minLevel = Math.max(0, min);
        maxLevel = Math.max(0, max);
    }

    public int minLevel()
    {
        return minLevel;
    }

    public int maxLevel()
    {
        return maxLevel;
    }

    public Sort sort()
    {
        return sort;
    }

    public void setSort(Sort value)
    {
        sort = value;
    }

    public boolean descending()
    {
        return descending;
    }

    public void setDescending(boolean value)
    {
        descending = value;
    }

    /**
     * The Clear Filters button. Resets narrowing but keeps the sort, because a
     * player clearing a search wants the whole trunk back in the order they
     * were already reading it, not reordered underneath them.
     */
    public void clear()
    {
        text = "";
        kinds.clear();
        attributes.clear();
        species.clear();
        minLevel = 0;
        maxLevel = 0;
    }

    /** True when nothing is narrowing, so the UI can grey out Clear. */
    public boolean isClear()
    {
        return text.isEmpty() && kinds.isEmpty() && attributes.isEmpty()
            && species.isEmpty() && minLevel == 0 && maxLevel == 0;
    }

    // ---- applying ----

    public boolean matches(C card)
    {
        if(!text.isEmpty())
        {
            String needle = text.toLowerCase(Locale.ROOT);
            String name = facets.name(card);
            String body = facets.text(card);
            boolean hit = name != null && name.toLowerCase(Locale.ROOT).contains(needle);
            // Effect text is searched too, so "destroy all monsters" finds Dark
            // Hole without the player knowing its name.
            hit = hit || (body != null && body.toLowerCase(Locale.ROOT).contains(needle));
            if(!hit)
            {
                return false;
            }
        }
        if(!kinds.isEmpty() && !kinds.contains(facets.kind(card)))
        {
            return false;
        }
        if(!attributes.isEmpty())
        {
            String attribute = facets.attribute(card);
            if(attribute == null || !attributes.contains(attribute))
            {
                return false;
            }
        }
        if(!species.isEmpty())
        {
            String value = facets.species(card);
            if(value == null || !species.contains(value))
            {
                return false;
            }
        }
        if(minLevel > 0 || maxLevel > 0)
        {
            int level = facets.level(card);
            // A card with no level is excluded once a level band is set: a
            // spell is not "level 0", it simply has nothing to compare.
            if(level <= 0)
            {
                return false;
            }
            if(minLevel > 0 && level < minLevel)
            {
                return false;
            }
            if(maxLevel > 0 && level > maxLevel)
            {
                return false;
            }
        }
        return true;
    }

    /** Everything matching, in the chosen order. */
    public List<C> apply(Iterable<C> cards)
    {
        List<C> result = new ArrayList<>();
        for(C card : cards)
        {
            if(matches(card))
            {
                result.add(card);
            }
        }
        result.sort(comparator());
        return result;
    }

    private Comparator<C> comparator()
    {
        Comparator<C> byChosen = switch(sort)
        {
            case NAME -> Comparator.comparing(card -> safe(facets.name(card)));
            case LEVEL -> Comparator.comparingInt(facets::level);
            case ATTACK -> Comparator.comparingInt(facets::attack);
            case DEFENCE -> Comparator.comparingInt(facets::defence);
            case KIND -> Comparator.comparing(card -> facets.kind(card) == null
                ? "" : facets.kind(card).name());
            case ID -> Comparator.comparingLong(facets::id);
        };
        if(descending)
        {
            byChosen = byChosen.reversed();
        }
        // Name then id as the tie-break, so equal ATK never shuffles between
        // openings -- an unstable trunk is disorienting to build in.
        return byChosen
            .thenComparing(card -> safe(facets.name(card)))
            .thenComparingLong(facets::id);
    }

    private static String safe(String value)
    {
        return value == null ? "" : value;
    }
}
