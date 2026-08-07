package de.cas_ual_ty.dueldimension.ocg.bot.executor;

/**
 * The kinds of action an executor can be registered for.
 * <p>
 * Transliterated from {@code ExecutorBase/Game/AI/ExecutorType.cs}, in the same
 * order, because the order is meaningful: {@code GameAI.OnSelectIdleCmd} tries
 * the action kinds in a fixed sequence per executor, and matching WindBot's
 * list keeps our dispatch comparable to theirs line for line.
 * <pre>
 * public enum ExecutorType
 * {
 *     Summon,
 *     SpSummon,
 *     Repos,
 *     MonsterSet,
 *     SpellSet,
 *     Activate,
 *     SummonOrSet,
 *     GoToBattlePhase,
 *     GoToMainPhase2,
 *     GoToEndPhase,
 *     Surrender
 * }
 * </pre>
 */
public enum ExecutorType
{
    SUMMON,
    SP_SUMMON,
    REPOS,
    MONSTER_SET,
    SPELL_SET,
    ACTIVATE,
    SUMMON_OR_SET,
    GO_TO_BATTLE_PHASE,
    GO_TO_MAIN_PHASE_2,
    GO_TO_END_PHASE,
    SURRENDER
}
