package de.cas_ual_ty.dueldimension.duel.profile;

import com.mojang.serialization.Codec;

/**
 * Every deck case the editor and deck-box shop know.
 *
 * <h2>The id</h2>
 * Each constant carries the Master Duel texture id it came from --
 * {@code DeckCase0034} is {@code "0034"} -- and the shop orders itself by it, so
 * the shelf runs in the game's own order rather than in whatever order constants
 * happened to be added. The id is the only durable link back to the source art:
 * the display names live in Master Duel's item table and the textures are named
 * after the names, so without this there is nothing in the mod that says which
 * case a style actually is.
 * <p>
 * {@link #BLUE} has none. It is the default that predates the imported art and
 * draws the placeholder, so it sorts first and is not for sale.
 * <p>
 * See {@code tools/deck_box_names.json} for the id-to-name record and
 * {@code tools/import_deck_boxes.py} for what adds one.
 */
public enum DeckBoxStyle
{
    BLUE("Blue", true, "", 0x326CB2),
    RED("Red", true, "0001", 0xC04C57),
    PURPLE("Purple", true, "0002", 0x653B94),
    LINK_BLUE("Link Blue", false, "0004", 0x316BB0),
    RE_CONTRACT_UNIVERSE("Re-Contract Universe", false, "0005", 0x773868),
    MAGICIAN_OF_PENDULUM("Magician of Pendulum", false, "0006", 0x535FA7),
    RAGE_OF_CIPHER("Rage of Cipher", false, "0007", 0xB94533),
    CYBERNETIC_SUCCESSOR("Cybernetic Successor", false, "0008", 0xB78546),
    HEIR_TO_THE_SHIRANUI_STYLE("Heir to the Shiranui-Style", false, "0009", 0xA0384C),
    GROWING_DIGITAL_BUG("Growing Digital Bug", false, "0010", 0x3173D4),
    ROAR_OF_THE_GLADIATOR_BEASTS("Roar of the Gladiator Beasts", false, "0011", 0xAD543A),
    EMERGENCE_OF_THE_MONARCHS("Emergence of the Monarchs", false, "0012", 0x473374),
    BEING_WHO_SEES_THE_END_OF_THE_WORLD("Being Who Sees the End of the World", false, "0013", 0x364697),
    GEM_KNIGHTS_RESOLUTION("Gem-Knights' Resolution", false, "0014", 0x9C4F51),
    DRAGONMAID_TO_ORDER("Dragonmaid-To-Order", false, "0015", 0x80B395),
    BURNING_SPIRITS("Burning Spirits", false, "0016", 0x9E3225),
    SPELLBOOK_OF_PROPHECY("Spellbook of Prophecy", false, "0018", 0x879E74),
    IMMORTAL_GLORY("Immortal Glory", false, "0019", 0x8C6EA8),
    VORTEX_OF_MAGIC("Vortex of Magic", false, "0020", 0x455FA2),
    SPIRAL_SPEAR_STRIKE("Spiral Spear Strike", false, "0021", 0xCA8234),
    BLACKWING_S_PRIDE("Blackwing's Pride", false, "0022", 0xC55F66),
    HIDDEN_ARTS_OF_SHADOWS("Hidden Arts of Shadows", false, "0023", 0xC69F55),
    BLUE_EYES_MAX("Blue-Eyes Max", false, "0024", 0x5A697B),
    SWORD_OF_SOULS("Sword of Souls", false, "0025", 0x9EC5EE),
    ORIGIN_OF_THE_GALAXY("Origin of the Galaxy", false, "0026", 0x555F9A),
    HEROES_UNION("Heroes' Union", false, "0027", 0xE9D59C),
    BLAZE_OF_ALBAZ("Blaze of Albaz", false, "0028", 0x68A5D2),
    CRIMSON_POWERFORCE("Crimson Powerforce", false, "0029", 0xC9512B),
    REUNION_OF_RAIDERS("Reunion of Raiders", false, "0030", 0xAD84BD),
    DARK_MAGICAL_BLAST("Dark Magical Blast", false, "0031", 0x343867),
    GIGANTIC_SPARKLE("Gigantic Sparkle", false, "0032", 0x7D4CB7),
    RAGE_OF_DEEP_BLUE("Rage of Deep Blue", false, "0033", 0x4954AD),
    CYBER_KAISER("Cyber Kaiser", false, "0034", 0x704BA7),
    ZERO_TO_FUTURE("Zero to Future", false, "0035", 0x324871),
    XYZ_BLACK("Xyz Black", false, "2001", 0x2C505F),
    THE_MILLENNIUM_PUZZLE("The Millennium Puzzle", false, "2002", 0x808080),
    DUELIST_CARD_CASE_BLUE("Duelist Card Case Blue", false, "2003", 0x465D8A),
    CARD_CASE_LIGHT("Card Case - LIGHT", false, "2004", 0xDABB2E),
    CARD_CASE_DARK("Card Case - DARK", false, "2005", 0x8D0A7B),
    CARD_CASE_EARTH("Card Case - EARTH", false, "2006", 0x7B3721),
    CARD_CASE_WATER("Card Case - WATER", false, "2007", 0x226DAF),
    CARD_CASE_FIRE("Card Case - FIRE", false, "2008", 0xA92021),
    CARD_CASE_WIND("Card Case - WIND", false, "2009", 0x278744),
    GOLD_PRIDE("Gold Pride", false, "2010", 0xD62164),
    CASE_2024_CARD_CASE("2024 Card Case", false, "2011", 0x92292E),
    PRESIDENT_S_BRIEFCASE("President's Briefcase", false, "2012", 0x87898F),
    MAGICIANS_OF_BONDS_AND_UNITY("Magicians of Bonds and Unity", false, "2013", 0x588442),
    KING_S_SARCOPHAGUS("King's Sarcophagus", false, "2014", 0xBBAD78),
    SNAKE_PATTERN("Snake Pattern", false, "2015", 0x9EA4AC),
    HALLO_WEEN("Hallo & Ween", false, "2016", 0xB97B44),
    LIVE_TWIN("Live☆Twin", false, "2017", 0x956ADA),
    DRAGON_OF_PRIDE_AND_SOUL("Dragon of Pride and Soul", false, "2018", 0x7EBDD0),
    SKY_STRIKER_ACE_RAYE("Sky Striker Ace - Raye", false, "2019", 0x963533),
    SKY_STRIKER_ACE_ROZE("Sky Striker Ace - Roze", false, "2020", 0x91312F),
    ABYSSAL_LAND("Abyssal Land", false, "2021", 0x8B7D4A),
    ORCUST("Orcust", false, "2022", 0x6E4D99),
    YUM_YUM_YUMMYS("Yum☆Yum☆Yummys", false, "2023", 0x9CE1EB),
    DIABELLSTAR_THE_BLACK_WITCH("Diabellstar the Black Witch", false, "2024", 0x8E7753),
    LABRYNTH("Labrynth", false, "2025", 0x59789C),
    A_CASE_FOR_K9("\"A Case for K9\"", false, "2026", 0x2E36AB),
    CLOCK_TOWER_PRISON("Clock Tower Prison", false, "2027", 0x4C5E91),
    KEWL_TUNE("Kewl Tune", false, "2028", 0xA97A62),
    ELFNOTE("Elfnote", false, "2029", 0x4F898B),
    DRAGON_RAVINE("Dragon Ravine", false, "2030", 0xD38C38),
    EXOSISTER("Exosister", false, "2032", 0x809DA2),
    WHITE_BLUE("White & Blue", false, "3001", 0x5762BA);

    private final String label;
    private final boolean free;
    private final String deckCaseId;

    /**
     * A representative colour, so a shop can sort by it.
     * <p>
     * The DOMINANT colour, not the average: the peak of a saturation-weighted
     * hue histogram, which is a colour the case actually has rather than the
     * blend of the two it has. Sampled offline by
     * {@code tools/sample_deck_box_colors.py}, which imports the sleeves'
     * sampler rather than reimplementing it -- the two catalogues sort by the
     * same definition of "the colour of this" because they run the same code.
     * <p>
     * 0xRRGGBB, no alpha.
     */
    public final int colour;

    /** That colour as hue and brightness, worked out once, at construction. */
    private final float hue;
    private final float brightness;
    private final boolean chromatic;

    /** Below this saturation a hue is noise; see CardSleevesType. */
    public static final float COLOUR_ACHROMATIC = 0.15F;

    /**
     * Orders cases the way the eye groups them: greys first by brightness, then
     * round the colour wheel. Ascending; the arrow reverses it.
     */
    public static int compareByColour(DeckBoxStyle a, DeckBoxStyle b)
    {
        if(a.chromatic != b.chromatic)
        {
            return a.chromatic ? 1 : -1;
        }
        if(!a.chromatic)
        {
            return Float.compare(a.brightness, b.brightness);
        }
        int byHue = Float.compare(a.hue, b.hue);
        return byHue != 0 ? byHue : Float.compare(a.brightness, b.brightness);
    }

    DeckBoxStyle(String label, boolean free, String deckCaseId, int colour)
    {
        this.colour = colour;
        float r = ((colour >> 16) & 0xFF) / 255F;
        float g = ((colour >> 8) & 0xFF) / 255F;
        float b = (colour & 0xFF) / 255F;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float span = max - min;
        brightness = max;
        chromatic = max > 0F && span / max >= COLOUR_ACHROMATIC;
        float h;
        if(span <= 0F)
        {
            h = 0F;
        }
        else if(max == r)
        {
            h = ((g - b) / span) / 6F;
        }
        else if(max == g)
        {
            h = (2F + (b - r) / span) / 6F;
        }
        else
        {
            h = (4F + (r - g) / span) / 6F;
        }
        hue = (h % 1F + 1F) % 1F;

        this.label = label;
        this.free = free;
        this.deckCaseId = deckCaseId;
    }

    /**
     * The Master Duel texture id this case came from, or empty for {@link #BLUE}.
     * <p>
     * Four digits, kept as a STRING rather than an int: they are zero-padded in
     * the asset names and sorting them as text gives the game's own order for
     * free, where parsing to a number would drop the padding that makes 0009
     * come before 0012.
     */
    public String deckCaseId()
    {
        return deckCaseId;
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
