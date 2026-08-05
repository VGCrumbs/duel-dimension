package de.cas_ual_ty.dueldimension.ocg.bot.executor;

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
}
