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
    public static final Entry PEGASUS = new Entry("pegasus", "Starter Deck: Pegasus", "SDP",
        "Toons, rituals and control from Maximillion Pegasus.");
    public static final Entry YUGI_EVOLUTION = new Entry("yugi_evolution",
        "Starter Deck: Yugi Evolution", "SYE", "Yugi's evolved spellcaster strategy.");
    public static final Entry KAIBA_EVOLUTION = new Entry("kaiba_evolution",
        "Starter Deck: Kaiba Evolution", "SKE", "Kaiba's evolved dragon strategy.");
    public static final Entry STARTER_2006 = new Entry("starter_2006", "Starter Deck 2006", "YSD",
        "The first generation-neutral TCG starter deck.");

    public static final Entry JADEN = new Entry("jaden", "Starter Deck: Jaden Yuki", "YSDJ",
        "GX era: Elemental HEROes and the fusions they make.");
    public static final Entry SYRUS = new Entry("syrus", "Starter Deck: Syrus Truesdale", "YSDS",
        "GX machines built around Syrus's Vehicroids.");
    public static final Entry YUSEI = new Entry("yusei", "Starter Deck: Yu-Gi-Oh! 5D's", "5DS1",
        "5D's era: tuners, and the synchro monsters they summon.");
    public static final Entry STARTER_2009 = new Entry("starter_2009",
        "Starter Deck: Yu-Gi-Oh! 5D's 2009", "5DS2", "A second 5D's-era Synchro starter.");
    public static final Entry YUMA = new Entry("yuma", "Starter Deck: Dawn of the Xyz", "YS11",
        "Zexal era: stacking levels to build xyz monsters.");
    public static final Entry KAIBA_RELOADED = new Entry("kaiba_reloaded",
        "Starter Deck: Kaiba Reloaded", "YSKR", "A modernized Kaiba dragon deck.");
    public static final Entry YUGI_RELOADED = new Entry("yugi_reloaded",
        "Starter Deck: Yugi Reloaded", "YSYR", "A modernized Yugi spellcaster deck.");
    public static final Entry SABER_FORCE = new Entry("saber_force", "Saber Force Starter Deck", "YS15",
        "Light monsters and Pendulum Summoning.");
    public static final Entry YUYA = new Entry("yuya", "Starter Deck: Yuya", "YS16",
        "Yuya's Performapals and Pendulum monsters.");
    public static final Entry LINK_STRIKE = new Entry("link_strike", "Starter Deck: Link Strike", "YS17",
        "The first Link Summoning starter deck.");
    public static final Entry CODEBREAKER = new Entry("codebreaker", "Starter Deck: Codebreaker", "YS18",
        "Cyberse monsters and Link Summoning.");

    public static final List<Entry> ALL = List.of(
        YUGI, KAIBA, JOEY, PEGASUS, YUGI_EVOLUTION, KAIBA_EVOLUTION, STARTER_2006,
        JADEN, SYRUS, YUSEI, STARTER_2009, YUMA,
        KAIBA_RELOADED, YUGI_RELOADED, SABER_FORCE, YUYA, LINK_STRIKE,
        CODEBREAKER);

    /**
     * The one deck per generation that everybody starts with.
     * <p>
     * The protagonist's, in each era: Yugi, Jaden, Yusei, Yuma, Yuya and Link
     * Strike. The rest are bought.
     * <p>
     * Free is a <em>rule</em> and never a stored grant, the same decision
     * {@code Sleeves.FREE} and {@code DuelDisks.FREE} record: nothing is
     * written to disk for these, so they cannot be lost, cannot be
     * double-granted, and a deck added to this set later becomes free for
     * every existing profile without a migration.
     */
    public static final java.util.Set<String> FREE = java.util.Set.of(
        "yugi", "jaden", "yusei", "yuma", "yuya", "link_strike");

    public static boolean isFree(String id)
    {
        return FREE.contains(id);
    }

    /** Everything else is stock. */
    public static boolean isPurchasable(String id)
    {
        return id != null && !isFree(id) && ALL.stream().anyMatch(e -> e.id().equals(id));
    }

    private StarterDecks()
    {
    }

    /**
     * The deck of that id, or a thrown exception.
     * <p>
     * Throwing is right where the id came from this class or from a screen that
     * only offers these decks: there the absence is a bug and failing loudly
     * beats duelling with something else. Use {@link #find} where the id came
     * from anywhere else.
     */
    public static Entry byId(String id)
    {
        return ALL.stream().filter(entry -> entry.id().equals(id)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No starter deck with id " + id));
    }

    /**
     * The deck of that id, or null.
     * <p>
     * Exists because a profile id is not a deck id. They were the same thing
     * while every NPC was a starter-deck duelist, and {@link #byId} was called
     * on a profile in several places on that assumption -- then the Duel Bot
     * arrived with the profile {@code duel_bot}, which names a skin and no
     * deck, and every one of those calls became a crash in the server tick
     * loop. Anything holding an id that MIGHT not be a starter deck asks this.
     */
    public static Entry find(String id)
    {
        if(id == null || id.isBlank())
        {
            return null;
        }
        return ALL.stream().filter(entry -> entry.id().equals(id)).findFirst().orElse(null);
    }
}
