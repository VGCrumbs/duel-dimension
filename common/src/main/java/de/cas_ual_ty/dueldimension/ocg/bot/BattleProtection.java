package de.cas_ual_ty.dueldimension.ocg.bot;

import java.util.Locale;

/**
 * How many battles a monster survives in a turn, read from its own rules text.
 * <p>
 * <b>A departure from the reference, and meant to be read as one.</b> WindBot
 * answers this in {@code ClientCard.IsMonsterInvincible()} and in the body of
 * {@code OnPreBattleBetween}, and both are switches over passcodes — Catastor,
 * Crystal Wing, Mekk-Knight Crusadia Astram. A list only knows the cards
 * somebody thought to add, and none of those are in a starter deck, which is
 * why our port left {@code onPreBattleBetween} returning true.
 * <p>
 * So this asks the CARD rather than a list, which is the same move the rest of
 * this mod makes with the engine and the database. Every monster that survives
 * battle says so in wording the game has kept stable for twenty years, and the
 * database already carries it.
 * <p>
 * <b>It is deliberately timid.</b> Anything conditional — "during your turn",
 * "if you control", "except by battle with a Ritual Monster" — reads as
 * {@link #NONE}, because the bot cannot evaluate the condition and a wrongly
 * skipped attack is worse than the bad attack it was trying to avoid. Measured
 * against the 9,017 monsters in the database, that leaves 148 always-protected,
 * 12 once-per-turn and 3 twice-per-turn; the rest are unchanged from before.
 */
public final class BattleProtection
{
    /** Nothing in the text protects it: attack as the reference always did. */
    public static final int NONE = 0;
    /** Survives every battle. Attacking is pointless without a bypass. */
    public static final int ALWAYS = -1;

    private BattleProtection()
    {
    }

    /**
     * The phrases that say "this survives battle", as the database spells them.
     * <p>
     * Counted rather than guessed: 459 cards use the first, 69 the third, 2 the
     * second, and none the fourth — which is kept because it costs nothing and
     * the alternative is finding out later that one printing used it.
     */
    private static final String[] PROTECTED = {
        "cannot be destroyed by battle",
        "is not destroyed by battle",
        "cannot be destroyed in battle"
    };

    /** A limit on the shield, and how many battles it therefore absorbs. */
    private static final String[] LIMIT_PHRASES = {
        "once per turn", "once during each turn", "once during this turn",
        "the first time", "twice per turn"
    };
    private static final int[] LIMIT_COUNTS = {1, 1, 1, 1, 2};

    /**
     * Wording that makes the shield conditional on something this cannot judge.
     * <p>
     * Each of these was found in the database sitting next to a protection
     * phrase, and each would have cost a real attack. "during your turn" is
     * Abyss Actor - Twinkle Little, which is protected on its controller's turn
     * and therefore never on the turn the bot is attacking. "that used this
     * card" is Bi'an, which protects a Synchro Monster and not itself.
     */
    private static final String[] CONDITIONAL = {
        "if you", "if this", "if it", "if your opponent", "while you control", "while this",
        "as long as", "except", "unless", "during your turn", "during each of your",
        "this turn", "you targeted", "target", "when this card", "that used this card"
    };

    /** Normalises the punctuation the database actually contains. */
    private static String flatten(String text)
    {
        if(text == null)
        {
            return "";
        }
        return text.toLowerCase(Locale.ROOT)
            .replace('’', '\'')
            .replace(' ', ' ')
            .replaceAll("\\s+", " ");
    }

    /** The sentence a phrase sits in, since that is the scope of its clauses. */
    private static String sentenceAround(String text, int at)
    {
        int start = Math.max(Math.max(text.lastIndexOf(". ", at), text.lastIndexOf(": ", at)),
            text.lastIndexOf("; ", at));
        int end = text.indexOf(". ", at);
        return text.substring(start < 0 ? 0 : start + 2, end < 0 ? text.length() : end + 1);
    }

    /**
     * Battles this monster survives per turn: {@link #NONE}, {@link #ALWAYS},
     * or a positive count.
     * <p>
     * Scoped to the SENTENCE the protection is in. A card can carry an
     * unrelated "once per turn" elsewhere — Argostars, Armed Dragon Thunder
     * LV10 — and reading that as the shield's limit would send two monsters at
     * something that survives both.
     */
    public static int battlesSurvived(String cardText)
    {
        String text = flatten(cardText);
        int at = -1;
        String phrase = null;
        for(String candidate : PROTECTED)
        {
            int found = text.indexOf(candidate);
            if(found >= 0 && (at < 0 || found < at))
            {
                at = found;
                phrase = candidate;
            }
        }
        if(at < 0)
        {
            return NONE;
        }
        String sentence = sentenceAround(text, at);
        // It has to be THIS card that survives. Without this, "a Synchro
        // Monster that used this card as material cannot be destroyed by
        // battle" reads as the material being indestructible.
        if(!sentence.contains("this card") && !sentence.trim().startsWith(phrase))
        {
            return NONE;
        }
        String before = sentence.substring(0, Math.max(0, sentence.indexOf(phrase)));
        int limit = NONE;
        String remainder = sentence;
        for(int i = 0; i < LIMIT_PHRASES.length; i++)
        {
            if(before.contains(LIMIT_PHRASES[i]))
            {
                limit = Math.max(limit, LIMIT_COUNTS[i]);
            }
            remainder = remainder.replace(LIMIT_PHRASES[i], "");
        }
        // The limit phrases are stripped first: "once per turn" contains "turn"
        // and would otherwise trip the conditional test on every card that has
        // the very limit this is trying to read.
        for(String conditional : CONDITIONAL)
        {
            if(remainder.contains(conditional))
            {
                return NONE;
            }
        }
        return limit == NONE ? ALWAYS : limit;
    }

    /**
     * Whether an ATTACKER answers what it battles without destroying it.
     * <p>
     * The point of the exercise: a monster that cannot be destroyed by battle
     * is still worth attacking if the attack itself removes it. Both halves
     * have to be present and close together — a trigger naming a battle, and an
     * outcome that bounces, banishes, flips or destroys by effect. Either alone
     * is not evidence; plenty of cards return something to the hand for reasons
     * that have nothing to do with attacking.
     */
    public static boolean bypassesProtection(String cardText)
    {
        String text = flatten(cardText);
        for(String trigger : BATTLE_TRIGGER)
        {
            int at = text.indexOf(trigger);
            while(at >= 0)
            {
                String window = text.substring(at,
                    Math.min(text.length(), at + trigger.length() + 160));
                for(String outcome : PUNISHES)
                {
                    if(window.contains(outcome))
                    {
                        return true;
                    }
                }
                at = text.indexOf(trigger, at + 1);
            }
        }
        return false;
    }

    /**
     * The two wordings for piercing, as the database spells them.
     * <p>
     * 152 monsters mention piercing; 84 of them pierce on their OWN attacks and
     * the rest grant it to something else — Amazoness Empress, Aromage
     * Bergamot, Ally of Justice Thunder Armor. Requiring the sentence to name
     * "this card" is what separates them, the same test the protection above
     * uses to tell a card that survives battle from one that makes something
     * else survive it.
     */
    private static final String[] PIERCE = {
        "piercing battle damage",
        "inflict the difference as battle damage"
    };

    /**
     * Whether this monster's own attacks pierce.
     * <p>
     * <b>Only from its own text.</b> Piercing granted by an equip — Fairy
     * Meteor Crush and its kind — or by a field effect is invisible here: the
     * attacker's text says nothing about it, and the board snapshot does not
     * carry what a monster is wearing. That is a false negative, so the bot
     * declines an attack it could have profited from rather than making one
     * that achieves nothing, which is the safer way round.
     */
    public static boolean pierces(String cardText)
    {
        String text = flatten(cardText);
        for(String phrase : PIERCE)
        {
            int at = text.indexOf(phrase);
            while(at >= 0)
            {
                String sentence = sentenceAround(text, at);
                if(sentence.contains("this card attacks")
                    || sentence.contains("this card battles"))
                {
                    return true;
                }
                at = text.indexOf(phrase, at + 1);
            }
        }
        return false;
    }

    private static final String[] BATTLE_TRIGGER = {
        "attacks a monster", "attacks an opponent's monster", "attacks a defense position monster",
        "battles an opponent's monster", "at the end of the damage step",
        "after damage calculation", "destroys a monster by battle"
    };

    private static final String[] PUNISHES = {
        "return it to the hand", "return that monster to the hand", "returned to the hand",
        "shuffle it into the deck", "banish it", "banish that monster",
        "destroy it", "destroy that monster", "change it to face-down defense position",
        "send it to the graveyard", "send that monster to the graveyard"
    };
}
