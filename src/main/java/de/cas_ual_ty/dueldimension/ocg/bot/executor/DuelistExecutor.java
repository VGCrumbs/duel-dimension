package de.cas_ual_ty.dueldimension.ocg.bot.executor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The base a custom duelist extends, and the generic play it inherits.
 * <p>
 * <b>How to write a duelist.</b> Subclass this, and in the constructor register
 * what the duelist is willing to do, most important first:
 * <pre>
 * public final class MyDuelist extends DuelistExecutor
 * {
 *     private static final int MY_FAVOURITE_TRAP = 12345678;
 *
 *     public MyDuelist()
 *     {
 *         addExecutor(ExecutorType.ACTIVATE, CardId.DarkHole, this::defaultDarkHole);
 *         addExecutor(ExecutorType.ACTIVATE, MY_FAVOURITE_TRAP, this::defaultTrap);
 *         addGenericPlay();
 *     }
 * }
 * </pre>
 * Registration order is priority: the dispatcher walks this list from the top
 * and takes the first rule that fires, so put the cards that should be played
 * first, first. {@link #addGenericPlay()} goes last because it is the "and
 * otherwise, play normally" clause.
 * <p>
 * This mirrors WindBot's own {@code OldSchoolExecutor} — a deck of exactly our
 * card era — which registers its removal spells, then its bodies, then
 * {@code AddExecutor(ExecutorType.Repos, DefaultMonsterRepos)} and its traps.
 * <p>
 * <b>What a rule may be.</b> Any {@code boolean} method: one of the inherited
 * {@code default*} predicates ported from the reference, or one you write for
 * this duelist. Passing no predicate at all means "whenever it is legal",
 * which WindBot uses for cards that are never a bad play
 * ({@code AddExecutor(ExecutorType.Activate, CardId.Fissure)}).
 * <p>
 * <b>What happens to cards you do not register.</b> They are never activated.
 * That is not a safety net bolted on; it is the gate in
 * {@code ExecutorBot.shouldExecute}, which requires a matching entry to exist.
 * Registering nothing yields a duelist that summons, attacks and ends its turn.
 */
public abstract class DuelistExecutor extends DefaultExecutor
{
    /**
     * Use a trigger on a card that is already in the graveyard.
     * <p>
     * The card is spent whether or not the effect is used, so there is no copy
     * being held back and no later moment being preferred: declining is
     * strictly worse. That is what makes this safe to answer generally when
     * Windbot would not, and why it is limited to the graveyard rather than
     * applied to everything offered -- a trigger on a card still on the field
     * or in the hand may well be worth saving, and answering yes to those
     * would have the bot chain every trap the moment it could.
     * <p>
     * Mystic Tomato is the case that prompted it: destroyed by battle, sitting
     * in the graveyard, offering a free body from the deck, and declined
     * because no rule named it.
     */
    @Override
    public boolean activateUnlistedOptional(BotCard card, int location)
    {
        return location == de.cas_ual_ty.dueldimension.ocg.OcgConstants.LOCATION_GRAVE;
    }

    /**
     * The deck-agnostic clause every duelist wants last: summon what can be
     * summoned, set what cannot fight, keep the backrow stocked, and turn
     * monsters that are outclassed to defence.
     * <p>
     * Taken from the tail of {@code OldSchoolExecutor}'s constructor, which
     * ends with the same wildcard registrations:
     * <pre>
     * AddExecutor(ExecutorType.Repos, DefaultMonsterRepos);
     * AddExecutor(ExecutorType.SpellSet, DefaultSpellSet);
     * </pre>
     * Summoning is a wildcard here rather than one line per monster, because
     * our decks change with the player's collection and an unlisted monster
     * should still be playable. {@code DefaultMonsterSummon} is the reference's
     * own predicate for whether a body is worth the tributes.
     */
    protected final void addGenericPlay()
    {
        addExecutor(ExecutorType.SUMMON_OR_SET, this::defaultMonsterSummon);
        addExecutor(ExecutorType.SP_SUMMON, this::defaultMonsterSummon);
        addExecutor(ExecutorType.REPOS, this::defaultMonsterRepos);
        addExecutor(ExecutorType.SPELL_SET, this::defaultSpellSet);
    }

    /**
     * The ten starter-deck cards WindBot has a strictly-defined rule for, each
     * registered with that rule and nothing else.
     * <p>
     * Every other spell and trap in these decks — Reinforcements, Reverse Trap,
     * Waboku's cousins, the equips — has NO rule in the reference, so it gets
     * none here either and will only ever be set, never fired. Adding one is a
     * deliberate act: write the predicate, register it in a duelist, and it
     * becomes that duelist's own judgement rather than a claim about EDOPro.
     */
    protected final void addReferenceCardRules()
    {
        // DefaultDarkHole: Util.IsOneEnemyBetter()
        addExecutor(ExecutorType.ACTIVATE, CardId.DarkHole, this::defaultDarkHole);
        // OldSchoolExecutor registers Fissure with no predicate at all: it only
        // ever destroys an opponent's monster, so it is never a bad activation.
        addExecutor(ExecutorType.ACTIVATE, CardId.Fissure);
        // DefaultCallOfTheHaunted, the reference's only generic revive rule.
        addExecutor(ExecutorType.ACTIVATE, CardId.MonsterReborn, this::defaultCallOfTheHaunted);
        // DefaultScapegoat: opponent's turn only, and only when lethal is coming.
        addExecutor(ExecutorType.ACTIVATE, CardId.Scapegoat, this::defaultScapegoat);
        // DefaultPotOfDesires' deck-out guard, the reference's rule for refills.
        addExecutor(ExecutorType.ACTIVATE, CardId.CardDestruction, this::defaultDeckIsDeep);
        // DefaultField: only when our own field zone is empty.
        addExecutor(ExecutorType.ACTIVATE, CardId.Mountain, this::defaultField);
        addExecutor(ExecutorType.ACTIVATE, CardId.Sogen, this::defaultField);
        addExecutor(ExecutorType.ACTIVATE, CardId.Yami, this::defaultField);
        // DefaultTrap: chain the opponent's action, never our own.
        addExecutor(ExecutorType.ACTIVATE, CardId.Waboku, this::defaultTrap);
        addExecutor(ExecutorType.ACTIVATE, CardId.SevenToolsOfTheBandit, this::defaultTrap);
        addExecutor(ExecutorType.ACTIVATE, CardId.JustDesserts, this::defaultTrap);
    }

    // ------------------------------------------------------------------
    // HOUSE RULES
    //
    // Everything below this line is OURS, not EDOPro's. The reference AI has
    // no rule for any of these cards, so nothing here can be checked against
    // it; each is a stated judgement about how the card should be played, and
    // each says whose judgement it is and what it was asked to do.
    //
    // They are registered separately from addReferenceCardRules() so the two
    // kinds of claim never blur together.
    // ------------------------------------------------------------------

    /** How much ATK Reinforcements adds. Card text: "it gains 500 ATK until the end of this turn". */
    private static final int REINFORCEMENTS_BOOST = 500;
    /** The ATK at which an enemy monster counts as a "dire threat" worth Two-Pronged Attack. */
    private static final int DIRE_THREAT_ATTACK = 2000;

    protected static final int TWO_PRONGED_ATTACK = 83887306;
    protected static final int REINFORCEMENTS = 17814387;
    protected static final int SHIELD_AND_SWORD = 52097679;

    /** The three cards above, with the rules requested for them. */
    protected final void addHouseCardRules()
    {
        addExecutor(ExecutorType.ACTIVATE, TWO_PRONGED_ATTACK, this::twoProngedAttack);
        addExecutor(ExecutorType.ACTIVATE, REINFORCEMENTS, this::reinforcements);
        addExecutor(ExecutorType.ACTIVATE, SHIELD_AND_SWORD, this::shieldAndSword);
    }

    /**
     * Two-Pronged Attack — "Select and destroy 2 of your monsters and 1 of
     * your opponent's monsters."
     * <p>
     * Held for a dire threat: it only fires against an enemy monster of more
     * than {@value #DIRE_THREAT_ATTACK} ATK, which is the point at which
     * trading two bodies for one stops being a loss. It takes the strongest
     * such monster, and gives up our two least valuable — the two lowest by
     * power, so a wall we are relying on is spent last.
     * <p>
     * Selection order matters: the strongest enemy first, then our two
     * cheapest, so the prompt that follows is answered with the same intent
     * the decision was made on.
     */
    private boolean twoProngedAttack()
    {
        BotCard target = null;
        for(BotCard enemy : enemy().getMonsters())
        {
            if(enemy.attack() > DIRE_THREAT_ATTACK
                && (target == null || enemy.attack() > target.attack()))
            {
                target = enemy;
            }
        }
        if(target == null)
        {
            return false;
        }
        // Two of ours have to go, so there must be two to give.
        List<BotCard> ours = new ArrayList<>(bot().getMonsters());
        if(ours.size() < 2)
        {
            return false;
        }
        ours.sort(Comparator.comparingInt(BotCard::getDefensePower));
        selectCard(target, ours.get(0), ours.get(1));
        return true;
    }

    /**
     * Reinforcements — "Target 1 face-up monster; it gains 500 ATK until the
     * end of this turn."
     * <p>
     * Saved for a battle it actually turns. Both of the cases asked for reduce
     * to the same arithmetic: one of our attack-position monsters currently
     * loses or ties against one of theirs, and would win with the boost. On
     * our turn that is "attack a strong monster with it"; on theirs it is
     * "protect a monster it could save". The card gives ATK only, so a monster
     * sitting in defence cannot be rescued by it and is not considered.
     * <p>
     * The boost is a known constant here rather than a guess: it is printed on
     * the card, and {@link #REINFORCEMENTS_BOOST} quotes it.
     */
    private boolean reinforcements()
    {
        BotCard best = null;
        for(BotCard ourMonster : bot().getMonsters())
        {
            if(!ourMonster.isFaceUp() || !ourMonster.isAttack())
            {
                continue;
            }
            for(BotCard theirMonster : enemy().getMonsters())
            {
                int theirPower = theirMonster.getDefensePower();
                boolean losesNow = ourMonster.attack() <= theirPower;
                boolean winsWithBoost = ourMonster.attack() + REINFORCEMENTS_BOOST > theirPower;
                if(losesNow && winsWithBoost
                    && (best == null || ourMonster.attack() > best.attack()))
                {
                    best = ourMonster;
                }
            }
        }
        if(best == null)
        {
            return false;
        }
        selectCard(best);
        return true;
    }

    /**
     * Shield &amp; Sword — "Switch the original ATK and DEF of all face-up
     * monsters currently on the field, until the end of this turn."
     * <p>
     * Fired when the swap turns one of their monsters into something we can
     * kill this turn. The swap is symmetric and hits our own monsters too, so
     * the comparison is made on both sides after inverting: our attacker
     * swings with its printed DEF, and their monster defends with the printed
     * stat its position will then use.
     * <p>
     * It must be a change, not just a win — the rule requires that we cannot
     * already beat the monster, so the card is never spent on a fight we were
     * winning anyway. Our turn only, since the swap lasts one turn and the
     * point is to attack into it.
     * <p>
     * Note this reads {@code baseAttack}/{@code baseDefense} rather than the
     * live values, because the card switches ORIGINAL ATK and DEF; judging it
     * on boosted numbers would mis-predict the result.
     */
    private boolean shieldAndSword()
    {
        if(duelPlayer() != 0)
        {
            return false;
        }
        for(BotCard ourMonster : bot().getMonsters())
        {
            if(!ourMonster.isFaceUp() || !ourMonster.isAttack())
            {
                continue;
            }
            for(BotCard theirMonster : enemy().getMonsters())
            {
                if(!theirMonster.isFaceUp())
                {
                    continue; // a set monster's stats are not ours to reason about
                }
                int theirPowerNow = theirMonster.getDefensePower();
                // After the swap each monster's ATK is its printed DEF and its
                // DEF is its printed ATK; which one defends still depends on
                // the position it is sitting in.
                int theirPowerAfter = theirMonster.isAttack()
                    ? theirMonster.baseDefense() : theirMonster.baseAttack();
                int ourPowerAfter = ourMonster.baseDefense();
                if(ourMonster.attack() <= theirPowerNow && ourPowerAfter > theirPowerAfter)
                {
                    return true;
                }
            }
        }
        return false;
    }
}
