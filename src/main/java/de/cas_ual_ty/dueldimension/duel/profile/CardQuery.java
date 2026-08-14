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

        /**
         * The card's own sub-type, as the official editors group them: a
         * monster's Fusion/Synchro/Xyz/Link/Ritual, a spell's Quick-Play or
         * Field, a trap's Counter. Null when it has none, which is what a
         * plain Normal monster has.
         * <p>
         * Qualified by kind, through {@link CardQuery#subTypeKey}, because the
         * names are not unique across kinds -- see that method.
         */
        String subType(C card);

        /**
         * Abilities a monster carries -- Flip, Gemini, Spirit, Toon, Union.
         * Empty for anything else. A set because a card can hold more than one.
         */
        java.util.Set<String> abilities(C card);

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
    private final Set<String> subTypes = new java.util.LinkedHashSet<>();
    private final Set<String> abilities = new java.util.LinkedHashSet<>();
    /**
     * Inclusive ATK and DEF bands. -1 means unbounded that way rather than 0,
     * because 0 is a real attack value and "at least 0" is a filter a player
     * can reasonably set.
     */
    private int minAttack = -1;
    private int maxAttack = -1;
    private int minDefence = -1;
    private int maxDefence = -1;
    /**
     * Narrow to starred cards only, and the stars to check against.
     * <p>
     * The set is handed in rather than reached for: this class deliberately
     * knows nothing about profiles, which is what lets it be tested without
     * one.
     */
    private boolean favouritesOnly;
    private Set<Long> favourites = java.util.Set.of();
    /**
     * Narrow to tuners.
     * <p>
     * Its own axis rather than a value in {@link #abilities}, and that is the
     * whole point of it: that set is OR-within, so Tuner and Pendulum chosen
     * there together would WIDEN to their union -- while a player asking for
     * both means the twelve cards that are both. A boolean beside the set ANDs
     * with it instead.
     */
    private boolean tunersOnly;
    private Sort sort = Sort.NAME;
    private boolean descending;

    /**
     * Tuner, as a filter value.
     * <p>
     * The constant lives here because {@link #matches} names it directly.
     * {@code EditorState.PENDULUM} stays where it is: that value is only ever
     * compared inside the ability set and is never named by the query.
     */
    public static final String TUNER = "Tuner";

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
        dropInapplicable();
    }

    /**
     * The kinds a card in the pool can be.
     * <p>
     * What is lit, or all three when nothing is: an empty set means "do not
     * narrow", so it is every kind rather than none. That is also the state the
     * bar opens in and the state Clear Filters returns it to, which is why the
     * sub-filters have to be offered for it.
     */
    public Set<Kind> kindsInPool()
    {
        return kinds.isEmpty() ? EnumSet.allOf(Kind.class) : EnumSet.copyOf(kinds);
    }

    /**
     * Drops every value on an axis the bar can no longer show a control for.
     * <p>
     * Here rather than in the screen because this query outlives the screen: a
     * filter cleared only while the widgets were being rebuilt would still be
     * narrowing the trunk the next time the editor opened, with nothing on
     * screen to say so.
     */
    private void dropInapplicable()
    {
        Set<Kind> pool = kindsInPool();
        retainSubTypes(pool);
        if(!pool.contains(Kind.MONSTER))
        {
            // Attribute, species, ability and tuner are monster properties, and
            // a band excludes every non-monster outright (see withinBand), so an
            // ATK band left set with only SPELL lit is a permanently empty pool
            // with no visible cause.
            clearAttributes();
            clearSpecies();
            clearAbilities();
            tunersOnly = false;
            clearBands();
        }
    }

    /** Drops every sub-type whose kind is not among {@code keep}. */
    public void retainSubTypes(java.util.Collection<Kind> keep)
    {
        subTypes.removeIf(key ->
        {
            for(Kind kind : keep)
            {
                if(key.startsWith(kind.name() + "/"))
                {
                    return false;
                }
            }
            return true;
        });
    }

    public Set<String> attributes()
    {
        return attributes;
    }

    public void clearAttributes()
    {
        attributes.clear();
    }

    public void clearSpecies()
    {
        species.clear();
    }

    public void clearAbilities()
    {
        abilities.clear();
    }

    /** Both ends of all three bands back to unbounded. */
    public void clearBands()
    {
        minLevel = 0;
        maxLevel = 0;
        minAttack = -1;
        maxAttack = -1;
        minDefence = -1;
        maxDefence = -1;
    }

    /**
     * A sub-type value, qualified by the kind that owns it.
     * <p>
     * The names are NOT unique across kinds: "Normal" is a monster type, a
     * spell type and a trap type; "Continuous" is a spell type and a trap type;
     * "Ritual" is a monster type and a spell type. Matched by bare name, one
     * entry therefore meant several things at once -- one "Continuous" that
     * found 503 spells and 558 traps together, and one "Ritual" that found 148
     * Ritual Monsters alongside 82 Ritual Spells. The key carries its kind, so
     * a Continuous Trap can be asked for as a Continuous Trap.
     */
    public static String subTypeKey(Kind kind, String label)
    {
        return kind.name() + "/" + label;
    }

    /**
     * The label out of a key, for showing what is chosen.
     * <p>
     * A value with no kind on it comes back unchanged, so the axes that are not
     * qualified -- attribute, species, ability -- can be shown through this too
     * rather than needing a second path.
     */
    public static String subTypeLabel(String key)
    {
        int i = key.indexOf('/');
        return i < 0 ? key : key.substring(i + 1);
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

    public Set<String> subTypes()
    {
        return java.util.Collections.unmodifiableSet(subTypes);
    }

    public void toggleSubType(String value)
    {
        if(!subTypes.remove(value))
        {
            subTypes.add(value);
        }
    }

    public Set<String> abilities()
    {
        return java.util.Collections.unmodifiableSet(abilities);
    }

    public void toggleAbility(String value)
    {
        if(!abilities.remove(value))
        {
            abilities.add(value);
        }
    }

    public void setAttackRange(int min, int max)
    {
        minAttack = min;
        maxAttack = max;
    }

    public int minAttack()
    {
        return minAttack;
    }

    public int maxAttack()
    {
        return maxAttack;
    }

    public void setDefenceRange(int min, int max)
    {
        minDefence = min;
        maxDefence = max;
    }

    public int minDefence()
    {
        return minDefence;
    }

    public int maxDefence()
    {
        return maxDefence;
    }

    public boolean favouritesOnly()
    {
        return favouritesOnly;
    }

    public void setFavouritesOnly(boolean value)
    {
        favouritesOnly = value;
    }

    public boolean tunersOnly()
    {
        return tunersOnly;
    }

    public void setTunersOnly(boolean value)
    {
        tunersOnly = value;
    }

    /** The starred cards, as ids. Replaced whenever the profile changes. */
    public void setFavourites(Set<Long> value)
    {
        favourites = value == null ? java.util.Set.of() : value;
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
        subTypes.clear();
        abilities.clear();
        favouritesOnly = false;
        tunersOnly = false;
        minLevel = 0;
        maxLevel = 0;
        minAttack = -1;
        maxAttack = -1;
        minDefence = -1;
        maxDefence = -1;
    }

    /** True when nothing is narrowing, so the UI can grey out Clear. */
    public boolean isClear()
    {
        return text.isEmpty() && !favouritesOnly && !tunersOnly && kinds.isEmpty()
            && attributes.isEmpty()
            && species.isEmpty() && subTypes.isEmpty() && abilities.isEmpty()
            && minLevel == 0 && maxLevel == 0
            && minAttack < 0 && maxAttack < 0 && minDefence < 0 && maxDefence < 0;
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
        if(favouritesOnly && !favourites.contains(facets.id(card)))
        {
            return false;
        }
        if(!subTypes.isEmpty())
        {
            String value = facets.subType(card);
            if(value == null || !subTypes.contains(value))
            {
                return false;
            }
        }
        if(!abilities.isEmpty())
        {
            java.util.Set<String> carried = facets.abilities(card);
            // Any one of the chosen abilities is enough, matching how the
            // other chip rows read: chips widen, rows narrow.
            if(carried == null || carried.stream().noneMatch(abilities::contains))
            {
                return false;
            }
        }
        if(tunersOnly)
        {
            // ANDed with the abilities above rather than folded into them, so
            // "pendulum tuners" is a question that can be asked. Tuner rides in
            // on the same facet because it is the same shape of thing -- a flag
            // the card carries -- and adding a facet would have broken every
            // implementation of the interface for one boolean.
            java.util.Set<String> carried = facets.abilities(card);
            if(carried == null || !carried.contains(TUNER))
            {
                return false;
            }
        }
        if(!withinBand(facets.attack(card), minAttack, maxAttack, facets.kind(card)))
        {
            return false;
        }
        if(!withinBand(facets.defence(card), minDefence, maxDefence, facets.kind(card)))
        {
            return false;
        }
        return true;
    }

    /**
     * An ATK or DEF band. A spell has neither, so once a band is set a spell is
     * excluded rather than treated as having zero -- the same rule the level
     * band follows, and for the same reason.
     */
    private boolean withinBand(int value, int min, int max, Kind kind)
    {
        if(min < 0 && max < 0)
        {
            return true;
        }
        if(kind != Kind.MONSTER)
        {
            return false;
        }
        return (min < 0 || value >= min) && (max < 0 || value <= max);
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

    /** Orders without filtering: for sorting a deck, where nothing is hidden. */
    public List<C> sortOnly(List<C> cards)
    {
        List<C> result = new ArrayList<>(cards);
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
