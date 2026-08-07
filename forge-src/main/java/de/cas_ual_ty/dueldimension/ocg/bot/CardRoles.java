package de.cas_ual_ty.dueldimension.ocg.bot;

import java.util.Map;

/**
 * What a spell or trap is <em>for</em>, so the bot can weigh activating it
 * against the board instead of treating every activation as equally good.
 * <p>
 * The engine cannot tell us this — effects are Lua scripts, opaque to the
 * host — so it is curated by passcode, exactly as the reference AI does it.
 * WindBot (the AI EDOPro ships, {@code WindBot/ExecutorBase.dll}) keeps a
 * {@code _CardId} table of several hundred passcodes and hangs a predicate off
 * each one; {@code GameAI.ShouldExecute} then activates a card only when an
 * entry matches it <em>and</em> that predicate holds:
 * <pre>
 * bool result = card != null &amp;&amp; exec.Type == type &amp;&amp;
 *     (exec.CardId == -1 || exec.CardId == card.Id) &amp;&amp;
 *     (exec.Func == null || exec.Func());
 * </pre>
 * The consequence is the rule this table exists to reproduce: <b>a card with
 * no rule is never activated.</b> There is no "fire anything legal" fallback,
 * because legality is a poor proxy for wisdom — the engine will happily let
 * you wipe your own board, spend two of your monsters to kill one of theirs,
 * or hand the opponent's monster a +500 ATK boost.
 * <p>
 * Every classification below is taken from the card's own text in
 * {@code cards.cdb}, quoted beside it, so the table can be audited against the
 * database rather than against anyone's memory. {@code CardRolesTest} pins it
 * to the deck lists, so a typo fails the build.
 */
public final class CardRoles
{
    /**
     * Equips that may only be put on a monster of one type, by that type's
     * {@code RACE_*} bit.
     * <p>
     * WindBot hangs a predicate off each passcode as well as a role
     * ({@code exec.Func}), and this is what those three need: the engine offers
     * the activation whenever ANY legal target exists, and for a restricted
     * equip that can be the opponent's monster and none of ours. The bot then
     * has no way to decline at the target prompt, so a +300 boost was handed
     * across the table. Read off the same card text the role is:
     * <pre>
     * Book of Secret Arts : "A Spellcaster-Type monster equipped with this card..."
     * Dark Energy         : "A Fiend-Type monster equipped with this card..."
     * Dragon Treasure     : "A Dragon-Type monster equipped with this card..."
     * </pre>
     */
    private static final Map<Integer, Long> EQUIP_RACE = Map.of(
        91595718, de.cas_ual_ty.dueldimension.ocg.OcgConstants.RACE_SPELLCASTER,
        4614116, de.cas_ual_ty.dueldimension.ocg.OcgConstants.RACE_FIEND,
        1435851, de.cas_ual_ty.dueldimension.ocg.OcgConstants.RACE_DRAGON);

    /** The type a card may only be equipped to, or 0 if it has no such rule. */
    public static long equipRace(int code)
    {
        return EQUIP_RACE.getOrDefault(code, 0L);
    }

    public enum Role
    {
        /** Destroys every monster on both sides: only good when behind. */
        WIPES_ALL_MONSTERS,
        /** Removes, takes or neutralises one of the opponent's monsters. */
        HITS_OPPONENT_MONSTER,
        /** Clears spells and traps; wants the opponent to actually have some. */
        HITS_BACKROW,
        /** Strengthens a monster; must be pointed at one of ours. */
        BUFFS_OWN_MONSTER,
        /** Brings a monster back; wants a strong one in a graveyard. */
        REVIVES_FROM_GRAVE,
        /** Damage straight to the opponent, scaling with their board. */
        BURNS_OPPONENT,
        /** Life points for us and nothing else: never harmful, never urgent. */
        GAINS_LIFE,
        /** Occupies the field zone; only worth it when that zone is empty. */
        FIELD_SPELL,
        /** Puts a monster on the board. The engine only offers it when it resolves. */
        SUMMONS_MONSTER,
        /**
         * A defensive answer that is worth a card only in response to what the
         * opponent is doing. Never activated on our own initiative.
         */
        REACTIVE,
        /**
         * Costs us more than it takes, or forfeits something we want, on the
         * boards this bot can read. Left in hand rather than misfired.
         */
        SELF_HARMING,
        /**
         * No board effect this bot can judge — and, as the default for any
         * unclassified card, the reason unknown cards are never fired blind.
         */
        UTILITY
    }

    private static final Map<Integer, Role> ROLES = Map.ofEntries(
        // "Destroy all monsters on the field." Symmetric, so it is judged by
        // the exchange, the way WindBot's DefaultDarkHole judges it.
        Map.entry(53129443, Role.WIPES_ALL_MONSTERS),    // Dark Hole

        // Each of these takes an opponent's monster off the table or off its feet.
        Map.entry(66788016, Role.HITS_OPPONENT_MONSTER), // Fissure: destroy the lowest-ATK monster YOUR OPPONENT controls
        Map.entry(4206964, Role.HITS_OPPONENT_MONSTER),  // Trap Hole: destroy the monster your opponent just summoned
        Map.entry(25880422, Role.HITS_OPPONENT_MONSTER), // Block Attack: opponent's attacker to defence
        Map.entry(4031928, Role.HITS_OPPONENT_MONSTER),  // Change of Heart: take control of one of theirs

        Map.entry(19159413, Role.HITS_BACKROW),          // De-Spell
        Map.entry(51482758, Role.HITS_BACKROW),          // Remove Trap
        Map.entry(42703248, Role.HITS_BACKROW),          // Giant Trunade

        // Equips, and the three "target 1 face-up monster" boosts. The latter
        // three can legally point at the OPPONENT'S monster -- "Target 1
        // face-up monster ON THE FIELD" -- which is how Reinforcements ended
        // up buffing the player's attacker. Classifying them as buffs is what
        // sends the following selection to our own side.
        Map.entry(91595718, Role.BUFFS_OWN_MONSTER),     // Book of Secret Arts: +300/+300 to a Spellcaster
        Map.entry(4614116, Role.BUFFS_OWN_MONSTER),      // Dark Energy: +300/+300 to a Fiend
        Map.entry(1435851, Role.BUFFS_OWN_MONSTER),      // Dragon Treasure: +300/+300 to a Dragon
        Map.entry(98374133, Role.BUFFS_OWN_MONSTER),     // Invigoration: +400 ATK / -200 DEF
        Map.entry(37120512, Role.BUFFS_OWN_MONSTER),     // Sword of Dark Destruction: +400 ATK / -200 DEF
        Map.entry(99597615, Role.BUFFS_OWN_MONSTER),     // Malevolent Nuzzler: +700 ATK
        Map.entry(17814387, Role.BUFFS_OWN_MONSTER),     // Reinforcements: target 1 face-up monster; +500 ATK
        Map.entry(44209392, Role.BUFFS_OWN_MONSTER),     // Castle Walls: a selected monster's DEF +500
        Map.entry(16430187, Role.BUFFS_OWN_MONSTER),     // The Reliable Guardian: 1 face-up monster's DEF +700

        Map.entry(83764719, Role.REVIVES_FROM_GRAVE),    // Monster Reborn

        // "Inflict 500 damage to your opponent for each monster they control."
        Map.entry(24068492, Role.BURNS_OPPONENT),        // Just Desserts

        // "Increase your Life Points by 1000 points." Nothing else happens.
        Map.entry(84257640, Role.GAINS_LIFE),            // Dian Keto the Cure Master

        // Ported from WindBot's DefaultField: activate only when our own field
        // zone is empty, so a field spell never overwrites one already working.
        Map.entry(50913601, Role.FIELD_SPELL),           // Mountain
        Map.entry(86318356, Role.FIELD_SPELL),           // Sogen
        Map.entry(59197169, Role.FIELD_SPELL),           // Yami

        // The core only offers these when they can actually resolve -- Flute
        // needs "Lord of D." out, Last Will needs a monster to have died, and
        // Polymerization needs materials -- so legality IS the condition here.
        Map.entry(24094653, Role.SUMMONS_MONSTER),       // Polymerization
        Map.entry(85602018, Role.SUMMONS_MONSTER),       // Last Will
        Map.entry(43973174, Role.SUMMONS_MONSTER),       // The Flute of Summoning Dragon

        // Answers. Each is worth a card only against something the opponent is
        // doing, so they wait for a chain window (see HeuristicBot.chain).
        Map.entry(12607053, Role.REACTIVE),              // Waboku: no battle damage, no destruction by battle this turn
        Map.entry(3819470, Role.REACTIVE),               // Seven Tools of the Bandit: negate a Trap
        Map.entry(3027001, Role.REACTIVE),               // Fake Trap: substitute for a Trap being destroyed
        Map.entry(77622396, Role.REACTIVE),              // Reverse Trap: invert ATK/DEF changes -- answers THEIR buff
        Map.entry(50045299, Role.REACTIVE),              // Dragon Capture Jar: all Dragons to defence
        Map.entry(80604092, Role.REACTIVE),              // Ultimate Offering: pay 500 to summon again
        Map.entry(73915051, Role.REACTIVE),              // Scapegoat: "You cannot Summon other monsters the turn you activate this"

        // Cards that cost us more than they take on a board we can read.
        Map.entry(83887306, Role.SELF_HARMING),          // Two-Pronged Attack: destroy 2 of YOUR monsters and 1 of theirs
        Map.entry(68005187, Role.SELF_HARMING),          // Soul Exchange: "You cannot conduct your Battle Phase the turn you activate this"
        Map.entry(52097679, Role.SELF_HARMING),          // Shield & Sword: swaps ATK/DEF of ALL monsters, ours included
        Map.entry(95051344, Role.SELF_HARMING),          // Eternal Rest: destroys every equipped monster, ours included
        Map.entry(72892473, Role.SELF_HARMING),          // Card Destruction: discards our whole hand

        // Information only: they change nothing this bot can evaluate.
        Map.entry(17092736, Role.UTILITY),               // Ancient Telescope: see the top 5 of their deck
        Map.entry(81820689, Role.UTILITY));              // The Inexperienced Spy: see 1 card in their hand

    private CardRoles()
    {
    }

    /**
     * An unclassified card is {@link Role#UTILITY}, which never activates on
     * the bot's own initiative. That default is the point: it is WindBot's
     * {@code DefaultNoExecutor} rule, and it is what stops a card the bot does
     * not understand from being fired the moment the engine says it may be.
     */
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
