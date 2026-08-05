package de.cas_ual_ty.dueldimension.ocg.deck;

import java.util.List;

/**
 * The decks a new player picks from, and the loaner decks NPC duelists can
 * offer. All three are the real 2002/2003 TCG starter decks, generated from
 * the card database's own set data — so every card in them is obtainable in
 * game, and a starting deck doubles as a legitimate collection.
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

    public static final List<Entry> ALL = List.of(YUGI, KAIBA, JOEY);

    private StarterDecks()
    {
    }

    public static Entry byId(String id)
    {
        return ALL.stream().filter(entry -> entry.id().equals(id)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No starter deck with id " + id));
    }
}
