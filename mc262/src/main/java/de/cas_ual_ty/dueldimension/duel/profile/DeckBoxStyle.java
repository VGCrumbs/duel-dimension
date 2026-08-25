package de.cas_ual_ty.dueldimension.duel.profile;

import com.mojang.serialization.Codec;

/** Every deck case the editor and deck-box shop know. */
public enum DeckBoxStyle
{
    BLUE("Blue", true),
    RED("Red", true),
    PURPLE("Purple", true),
    VORTEX_OF_MAGIC("Vortex of Magic", false),
    BLUE_EYES_MAX("Blue-Eyes Max", false),
    DARK_MAGICAL_BLAST("Dark Magical Blast", false),
    RAGE_OF_DEEP_BLUE("Rage of Deep Blue", false),
    CYBER_KAISER("Cyber Kaiser", false),
    THE_MILLENNIUM_PUZZLE("The Millennium Puzzle", false);

    private final String label;
    private final boolean free;

    DeckBoxStyle(String label, boolean free)
    {
        this.label = label;
        this.free = free;
    }

    public String label()
    {
        return label;
    }

    public boolean isFree()
    {
        return free;
    }

    public boolean isPurchasable()
    {
        return !free;
    }

    /** Stable names on disk; an old or removed value safely becomes blue. */
    public static final Codec<DeckBoxStyle> CODEC = Codec.STRING.xmap(name ->
        java.util.Objects.requireNonNullElse(known(name), BLUE),
        DeckBoxStyle::name);

    /** Strict lookup for a value arriving from a client request. */
    public static DeckBoxStyle known(String name)
    {
        if(name != null)
        {
            // Migration for the two guessed names used by the first shop build.
            // The assets were 0034 and 2002; Master Duel's item table names
            // those Cyber Kaiser and The Millennium Puzzle respectively.
            if(name.equalsIgnoreCase("BLUE_EYES_WHITE_DRAGON"))
            {
                return CYBER_KAISER;
            }
            if(name.equalsIgnoreCase("DARK_MAGICIAN"))
            {
                return THE_MILLENNIUM_PUZZLE;
            }
            try
            {
                return valueOf(name.strip().toUpperCase(java.util.Locale.ROOT));
            }
            catch(IllegalArgumentException ignored)
            {
            }
        }
        return null;
    }
}
