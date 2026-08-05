package de.cas_ual_ty.dueldimension.ocg.bot;

import java.util.Map;

/**
 * What a spell or trap is <em>for</em>, so the bot can weigh activating it
 * against the board instead of treating every activation as equally good.
 * <p>
 * The engine cannot tell us this — effects are Lua scripts, opaque to the
 * host — so it is curated by passcode for the cards the starter decks
 * actually contain. {@code CardRolesTest} pins the table to the deck lists
 * both ways: every starter spell/trap must be classified, and every entry
 * here must exist in a deck, so a typo in a passcode fails the build rather
 * than silently degrading the bot. Cards outside the table default to
 * {@link Role#UTILITY}, which scores modestly and never beats a clearly good
 * play.
 * <p>
 * The flat 280 this replaces is what made the bot wipe its own board: with
 * every activation scored identically and above almost everything else, it
 * would fire any legal spell the moment it became legal, and a board wipe is
 * legal when only its own monsters would die.
 */
public final class CardRoles
{
    public enum Role
    {
        /** Destroys every monster on both sides: only good when behind. */
        WIPES_ALL_MONSTERS,
        /** Removes, takes or neutralises one of the opponent's monsters. */
        HITS_OPPONENT_MONSTER,
        /** Clears spells and traps; wants the opponent to actually have some. */
        HITS_BACKROW,
        /** Strengthens one of our own monsters; needs one to exist. */
        BUFFS_OWN_MONSTER,
        /** Brings a monster back; wants a strong one in a graveyard. */
        REVIVES_FROM_GRAVE,
        /** Everything else: fine to use, never urgent. */
        UTILITY
    }

    private static final Map<Integer, Role> ROLES = Map.ofEntries(
        Map.entry(53129443, Role.WIPES_ALL_MONSTERS),   // Dark Hole

        Map.entry(66788016, Role.HITS_OPPONENT_MONSTER), // Fissure
        Map.entry(4206964, Role.HITS_OPPONENT_MONSTER),  // Trap Hole
        Map.entry(25880422, Role.HITS_OPPONENT_MONSTER), // Block Attack
        Map.entry(4031928, Role.HITS_OPPONENT_MONSTER),  // Change of Heart
        Map.entry(68005187, Role.HITS_OPPONENT_MONSTER), // Soul Exchange
        Map.entry(24068492, Role.HITS_OPPONENT_MONSTER), // Just Desserts
        Map.entry(83887306, Role.HITS_OPPONENT_MONSTER), // Two-Pronged Attack
        Map.entry(50045299, Role.HITS_OPPONENT_MONSTER), // Dragon Capture Jar
        Map.entry(95051344, Role.HITS_OPPONENT_MONSTER), // Eternal Rest

        Map.entry(19159413, Role.HITS_BACKROW),          // De-Spell
        Map.entry(51482758, Role.HITS_BACKROW),          // Remove Trap
        Map.entry(42703248, Role.HITS_BACKROW),          // Giant Trunade

        Map.entry(91595718, Role.BUFFS_OWN_MONSTER),     // Book of Secret Arts
        Map.entry(4614116, Role.BUFFS_OWN_MONSTER),      // Dark Energy
        Map.entry(1435851, Role.BUFFS_OWN_MONSTER),      // Dragon Treasure
        Map.entry(98374133, Role.BUFFS_OWN_MONSTER),     // Invigoration
        Map.entry(37120512, Role.BUFFS_OWN_MONSTER),     // Sword of Dark Destruction
        Map.entry(99597615, Role.BUFFS_OWN_MONSTER),     // Malevolent Nuzzler
        Map.entry(52097679, Role.BUFFS_OWN_MONSTER),     // Shield & Sword
        Map.entry(77622396, Role.BUFFS_OWN_MONSTER),     // Reverse Trap

        Map.entry(83764719, Role.REVIVES_FROM_GRAVE),    // Monster Reborn

        Map.entry(17092736, Role.UTILITY),               // Ancient Telescope
        Map.entry(72892473, Role.UTILITY),               // Card Destruction
        Map.entry(44209392, Role.UTILITY),               // Castle Walls
        Map.entry(84257640, Role.UTILITY),               // Dian Keto the Cure Master
        Map.entry(3027001, Role.UTILITY),                // Fake Trap
        Map.entry(85602018, Role.UTILITY),               // Last Will
        Map.entry(50913601, Role.UTILITY),               // Mountain
        Map.entry(24094653, Role.UTILITY),               // Polymerization
        Map.entry(17814387, Role.UTILITY),               // Reinforcements
        Map.entry(73915051, Role.UTILITY),               // Scapegoat
        Map.entry(3819470, Role.UTILITY),                // Seven Tools of the Bandit
        Map.entry(86318356, Role.UTILITY),               // Sogen
        Map.entry(43973174, Role.UTILITY),               // The Flute of Summoning Dragon
        Map.entry(81820689, Role.UTILITY),               // The Inexperienced Spy
        Map.entry(16430187, Role.UTILITY),               // The Reliable Guardian
        Map.entry(80604092, Role.UTILITY),               // Ultimate Offering
        Map.entry(12607053, Role.UTILITY),               // Waboku
        Map.entry(59197169, Role.UTILITY));              // Yami

    private CardRoles()
    {
    }

    public static Role of(int code)
    {
        return ROLES.getOrDefault(code, Role.UTILITY);
    }

    /** The classified passcodes, for the completeness test. */
    public static Map<Integer, Role> all()
    {
        return ROLES;
    }
}
