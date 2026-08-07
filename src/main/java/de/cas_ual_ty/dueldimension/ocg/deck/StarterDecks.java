package de.cas_ual_ty.dueldimension.ocg.deck;

import java.util.List;

/**
 * The decks a new player picks from, and the loaner decks NPC duelists can
 * offer. Every one is a real TCG starter deck, generated from the card
 * database's own set data by {@code tools/gen_starter_deck.py} — so every card
 * in them is obtainable in game, and a starting deck doubles as a legitimate
 * collection. One deck per era, from the original three through to Zexal.
 */
public final class StarterDecks
{
    /**
     * @param id          resource/deck slug
     * @param displayName shown to the player
     * @param setCode     the TCG set this deck reproduces
     * @param flavour     one-line pitch for the selection screen
     */
    public record Entry(String id, String displayName, String setCode, String flavour)
    {
        public String resourcePath()
        {
            return "data/dueldimension/decks/" + id + ".ydk";
        }

        public YdkDeck load()
        {
            return YdkDeck.loadResource(resourcePath());
        }
    }

    public static final Entry YUGI = new Entry("yugi", "Starter Deck: Yugi", "SDY",
        "Spellcasters and classic beatdown, built around Dark Magician.");
    public static final Entry KAIBA = new Entry("kaiba", "Starter Deck: Kaiba", "SDK",
        "Big dragons and aggression, built around Blue-Eyes White Dragon.");
    public static final Entry JOEY = new Entry("joey", "Starter Deck: Joey", "SDJ",
        "Warriors and swarm tactics, built around Red-Eyes Black Dragon.");

    public static final Entry JADEN = new Entry("jaden", "Starter Deck: Jaden Yuki", "YSDJ",
        "GX era: Elemental HEROes and the fusions they make.");
    public static final Entry YUSEI = new Entry("yusei", "Starter Deck: Yu-Gi-Oh! 5D's", "5DS1",
        "5D's era: tuners, and the synchro monsters they summon.");
    public static final Entry YUMA = new Entry("yuma", "Starter Deck: Dawn of the Xyz", "YS11",
        "Zexal era: stacking levels to build xyz monsters.");

    public static final List<Entry> ALL = List.of(YUGI, KAIBA, JOEY, JADEN, YUSEI, YUMA);

    private StarterDecks()
    {
    }

    public static Entry byId(String id)
    {
        return ALL.stream().filter(entry -> entry.id().equals(id)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No starter deck with id " + id));
    }
}
