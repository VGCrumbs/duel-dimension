package de.cas_ual_ty.dueldimension.ocg.bot.executor;

import java.util.ArrayList;
import java.util.List;

/**
 * The board-evaluation predicates every ported rule is built out of —
 * WindBot's {@code AIUtil}, the object its rules call {@code Util}.
 * <p>
 * Two edge cases here drive a surprising amount of real behaviour and are
 * reproduced deliberately rather than smoothed over:
 * <ul>
 * <li>{@link #getBestPower} returns <b>-1</b> on an empty board, not 0, because
 * C#'s {@code .Max(card => (int?)card.GetDefensePower()) ?? -1} yields the null
 * coalesce. So with no monsters of our own, ANY enemy monster — even a 0 ATK
 * token — satisfies {@code isOneEnemyBetter}, and a board wipe fires.</li>
 * <li>{@link #isAllEnemyBetterThanValue} requires {@code monsters.Count > 0},
 * so "all enemies are better" is false against an EMPTY enemy board. Rules
 * gated on it therefore hold their fire when the opponent has nothing.</li>
 * </ul>
 */
public final class AIUtil
{
    private final Executor executor;

    AIUtil(Executor executor)
    {
        this.executor = executor;
    }

    private BotField bot()
    {
        return executor.bot();
    }

    private BotField enemy()
    {
        return executor.enemy();
    }

    /**
     * <pre>
     * public int GetTotalAttackingMonsterAttack(int player)
     * {
     *     return Duel.Fields[player].GetMonsters().Where(m =&gt; m.IsAttack()).Sum(m =&gt; (int?)m.Attack) ?? 0;
     * }
     * </pre>
     */
    public int getTotalAttackingMonsterAttack(BotField field)
    {
        int total = 0;
        for(BotCard card : field.getMonsters())
        {
            if(card.isAttack())
            {
                total += card.attack();
            }
        }
        return total;
    }

    /**
     * <pre>
     * public int GetBestPower(ClientField field, bool onlyATK = false)
     * {
     *     return field.MonsterZone.GetMonsters()
     *         .Where(card =&gt; !onlyATK || card.IsAttack())
     *         .Max(card =&gt; (int?)card.GetDefensePower()) ?? -1;
     * }
     * </pre>
     */
    public int getBestPower(BotField field, boolean onlyAttack)
    {
        int best = -1;
        boolean any = false;
        for(BotCard card : field.getMonsters())
        {
            if(onlyAttack && !card.isAttack())
            {
                continue;
            }
            any = true;
            best = Math.max(best, card.getDefensePower());
        }
        return any ? best : -1;
    }

    public int getBestPower(BotField field)
    {
        return getBestPower(field, false);
    }

    public int getBestAttack(BotField field)
    {
        return getBestPower(field, true);
    }

    /**
     * <pre>
     * public bool IsOneEnemyBetterThanValue(int value, bool onlyATK)
     * {
     *     return Enemy.MonsterZone.GetMonsters()
     *         .Any(card =&gt; card.GetDefensePower() &gt; value &amp;&amp; (!onlyATK || card.IsAttack()));
     * }
     * </pre>
     */
    public boolean isOneEnemyBetterThanValue(int value, boolean onlyAttack)
    {
        for(BotCard card : enemy().getMonsters())
        {
            if(card.getDefensePower() > value && (!onlyAttack || card.isAttack()))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * <pre>
     * public bool IsAllEnemyBetterThanValue(int value, bool onlyATK)
     * {
     *     List&lt;ClientCard&gt; monsters = Enemy.MonsterZone.GetMonsters();
     *     return monsters.Count &gt; 0 &amp;&amp; monsters
     *         .All(card =&gt; card.GetDefensePower() &gt; value &amp;&amp; (!onlyATK || card.IsAttack()));
     * }
     * </pre>
     */
    public boolean isAllEnemyBetterThanValue(int value, boolean onlyAttack)
    {
        List<BotCard> monsters = enemy().getMonsters();
        if(monsters.isEmpty())
        {
            return false;
        }
        for(BotCard card : monsters)
        {
            if(!(card.getDefensePower() > value && (!onlyAttack || card.isAttack())))
            {
                return false;
            }
        }
        return true;
    }

    /** {@code IsOneEnemyBetter}: does anything of theirs out-power our best? */
    public boolean isOneEnemyBetter(boolean onlyAttack)
    {
        return isOneEnemyBetterThanValue(getBestPower(bot(), onlyAttack), onlyAttack);
    }

    public boolean isOneEnemyBetter()
    {
        return isOneEnemyBetter(false);
    }

    /** {@code IsAllEnemyBetter}: does EVERYTHING of theirs out-power our best? */
    public boolean isAllEnemyBetter(boolean onlyAttack)
    {
        return isAllEnemyBetterThanValue(getBestPower(bot(), onlyAttack), onlyAttack);
    }

    public boolean isAllEnemyBetter()
    {
        return isAllEnemyBetter(false);
    }

    /** {@code GetBestBotMonster}: ours with the highest power. */
    public BotCard getBestBotMonster(boolean onlyAttack)
    {
        BotCard best = null;
        for(BotCard card : bot().getMonsters())
        {
            if(onlyAttack && !card.isAttack())
            {
                continue;
            }
            if(best == null || card.getDefensePower() > best.getDefensePower())
            {
                best = card;
            }
        }
        return best;
    }

    /** {@code GetWorstBotMonster}: ours with the lowest power. */
    public BotCard getWorstBotMonster(boolean onlyAttack)
    {
        BotCard worst = null;
        for(BotCard card : bot().getMonsters())
        {
            if(onlyAttack && !card.isAttack())
            {
                continue;
            }
            if(worst == null || card.getDefensePower() < worst.getDefensePower())
            {
                worst = card;
            }
        }
        return worst;
    }

    /** Their monsters ordered strongest first, as OnSelectBattleCmd sorts defenders. */
    public List<BotCard> enemyMonstersByPowerDescending()
    {
        List<BotCard> monsters = new ArrayList<>(enemy().getMonsters());
        monsters.sort((left, right) -> Integer.compare(right.getDefensePower(), left.getDefensePower()));
        return monsters;
    }
}
