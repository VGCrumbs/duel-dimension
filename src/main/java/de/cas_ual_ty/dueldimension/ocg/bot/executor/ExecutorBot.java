package de.cas_ual_ty.dueldimension.ocg.bot.executor;

import de.cas_ual_ty.dueldimension.ocg.OcgConstants;
import de.cas_ual_ty.dueldimension.ocg.OcgDuel;
import de.cas_ual_ty.dueldimension.ocg.RawMessage;
import de.cas_ual_ty.dueldimension.ocg.ResponseSource;
import de.cas_ual_ty.dueldimension.ocg.bot.RandomBot;
import de.cas_ual_ty.dueldimension.ocg.msg.DuelMessage;
import de.cas_ual_ty.dueldimension.ocg.msg.Responses;
import de.cas_ual_ty.dueldimension.ocg.query.BoardObserver;
import de.cas_ual_ty.dueldimension.ocg.query.BoardState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The dispatcher: WindBot's {@code GameAI}, driving an {@link Executor}.
 * <p>
 * This class contains no opinions about cards. It owns exactly one decision
 * procedure — {@link #shouldExecute} — and four call sites that consult the
 * executor list through it. Everything a duelist believes lives in its
 * {@link Executor} subclass.
 * <p>
 * The single most important property, and the one that separates this from the
 * scoring bot it replaces: <b>the executor list is the outer loop and the
 * candidate cards are the inner loop, and the first match returns
 * immediately.</b> Priority is registration order. Nothing is scored, nothing
 * is compared, and a card with no registered rule is never played.
 * <p>
 * Prompts with no rule fall through to {@link RandomBot} so a legal answer
 * always comes back, exactly as before — but note that this is a fallback for
 * prompt SHAPES we have no procedure for (announce a card, pick a zone), never
 * a fallback that activates a card the executor list declined.
 */
public class ExecutorBot implements ResponseSource
{
    private final Executor executor;
    private final OcgDuel.CardProvider cards;
    private final RandomBot fallback;

    private BoardObserver board;
    private int player;

    /** Duel-wide state the ported predicates read, tracked off the message stream. */
    private int turn;
    private int phase = OcgConstants.PHASE_DRAW;
    private int turnPlayer = -1;
    private int lastChainPlayer = -1;
    private int lastSummonPlayer = -1;

    /**
     * {@code GameAI._activatedCards}, the per-card activation ceiling:
     * <pre>
     * if (_activatedCards.ContainsKey(card.Id) &amp;&amp; _activatedCards[card.Id] &gt;= 9)
     *     return false;
     * ...
     * int count = card.IsDisabled() ? 3 : 1;
     * </pre>
     * Without it a continuous card whose activation stays legal after it
     * resolves is fired forever.
     */
    private final Map<Integer, Integer> activatedCards = new HashMap<>();
    private static final int ACTIVATION_LIMIT = 9;

    /** Set when we declare an attack, so the following card prompt is read as target choice. */
    private boolean expectingAttackTarget;
    private BotCard pendingAttacker;

    public ExecutorBot(long seed, Executor executor, OcgDuel.CardProvider cards,
        java.util.Collection<de.cas_ual_ty.dueldimension.ocg.OcgCard> declarableIndex)
    {
        this.executor = executor;
        this.cards = cards;
        fallback = new RandomBot(seed ^ 0x5DEECE66DL, declarableIndex);
    }

    public Executor executor()
    {
        return executor;
    }

    @Override
    public void onDuelStart(int playerIndex, BoardObserver observer)
    {
        player = playerIndex;
        board = observer;
        fallback.onDuelStart(playerIndex, observer);
    }

    @Override
    public void observe(RawMessage message)
    {
        fallback.observe(message);
        switch(message.type())
        {
            case OcgConstants.MSG_NEW_TURN ->
            {
                if(DuelMessage.decode(message) instanceof DuelMessage.NewTurn newTurn)
                {
                    turnPlayer = newTurn.player();
                }
                turn++;
                // Both reset each turn, as WindBot's Duel does; a trap must
                // answer what is happening now, not what happened last turn.
                lastChainPlayer = -1;
                lastSummonPlayer = -1;
                activatedCards.clear();
            }
            case OcgConstants.MSG_NEW_PHASE ->
            {
                if(DuelMessage.decode(message) instanceof DuelMessage.NewPhase newPhase)
                {
                    phase = newPhase.phase();
                }
            }
            case OcgConstants.MSG_CHAINING ->
            {
                if(DuelMessage.decode(message) instanceof DuelMessage.Chaining chaining)
                {
                    lastChainPlayer = chaining.card().controller();
                }
            }
            case OcgConstants.MSG_CHAIN_END -> lastChainPlayer = -1;
            case OcgConstants.MSG_SUMMONING, OcgConstants.MSG_SPSUMMONING,
                OcgConstants.MSG_FLIPSUMMONING, OcgConstants.MSG_SET ->
            {
                DuelMessage decoded = DuelMessage.decode(message);
                if(decoded instanceof DuelMessage.Summoning summoning)
                {
                    lastSummonPlayer = summoning.card().controller();
                }
                else if(decoded instanceof DuelMessage.SpSummoning summoning)
                {
                    lastSummonPlayer = summoning.card().controller();
                }
                else if(decoded instanceof DuelMessage.FlipSummoning summoning)
                {
                    lastSummonPlayer = summoning.card().controller();
                }
                else if(decoded instanceof DuelMessage.SetCard set)
                {
                    lastSummonPlayer = set.card().controller();
                }
            }
            default ->
            {
            }
        }
    }

    /** Rebuilds the executor's view of the table before any rule is consulted. */
    private void refresh()
    {
        BoardState state = board.observe();
        executor.setFields(BotField.of(state.self(), 0, cards), BotField.of(state.opponent(), 1, cards));
        executor.setDuelState(turn, phase, turnPlayer, player, lastChainPlayer, lastSummonPlayer);
    }

    @Override
    public byte[] respond(RawMessage prompt)
    {
        DuelMessage decoded = DuelMessage.decode(prompt);
        byte[] answer = null;
        if(board != null)
        {
            refresh();
            if(decoded instanceof DuelMessage.SelectIdleCmd idle)
            {
                answer = onSelectIdleCmd(idle);
            }
            else if(decoded instanceof DuelMessage.SelectBattleCmd battle)
            {
                answer = onSelectBattleCmd(battle);
            }
            else if(decoded instanceof DuelMessage.SelectChain chain)
            {
                answer = onSelectChain(chain);
            }
            else if(decoded instanceof DuelMessage.SelectEffectYesNo effect)
            {
                answer = onSelectEffectYn(effect);
            }
            else if(decoded instanceof DuelMessage.SelectPosition position)
            {
                answer = onSelectPosition(position);
            }
            else if(decoded instanceof DuelMessage.SelectCard select)
            {
                answer = onSelectCard(select);
            }
            else if(decoded instanceof DuelMessage.SelectYesNo yesNo)
            {
                answer = executor.onSelectYesNo(yesNo.description()) ? Responses.yes() : Responses.no();
            }
        }
        return answer != null ? answer : fallback.respond(prompt);
    }

    // ---- the gate ----

    /**
     * <pre>
     * private bool ShouldExecute(CardExecutor exec, ClientCard card, ExecutorType type, long desc = -1)
     * {
     *     if (card.Id != 0 &amp;&amp; type == ExecutorType.Activate)
     *     {
     *         if (_activatedCards.ContainsKey(card.Id) &amp;&amp; _activatedCards[card.Id] &gt;= 9) return false;
     *         if (!Executor.OnPreActivate(card)) return false;
     *     }
     *     Executor.SetCard(type, card, desc);
     *     bool result = card != null &amp;&amp; exec.Type == type &amp;&amp;
     *         (exec.CardId == -1 || exec.CardId == card.Id) &amp;&amp;
     *         (exec.Func == null || exec.Func());
     *     if (card.Id != 0 &amp;&amp; type == ExecutorType.Activate &amp;&amp; result)
     *     {
     *         int count = card.IsDisabled() ? 3 : 1;
     *         ...
     *     }
     *     return result;
     * }
     * </pre>
     * The {@code card.Id != 0} guard is load-bearing and easy to drop: a card
     * whose identity we cannot see skips the ceiling and the veto entirely.
     */
    private boolean shouldExecute(CardExecutor exec, BotCard card, ExecutorType type)
    {
        if(card == null)
        {
            return false;
        }
        if(card.code() != 0 && type == ExecutorType.ACTIVATE)
        {
            if(activatedCards.getOrDefault(card.code(), 0) >= ACTIVATION_LIMIT)
            {
                return false;
            }
            if(!executor.onPreActivate(card))
            {
                return false;
            }
        }
        executor.setCard(type, card);
        boolean result = exec.type() == type
            && (exec.cardId() == CardExecutor.ANY || exec.cardId() == card.code())
            && (exec.func() == null || exec.func().getAsBoolean());
        if(card.code() != 0 && type == ExecutorType.ACTIVATE && result)
        {
            activatedCards.merge(card.code(), 1, Integer::sum);
        }
        return result;
    }

    // ---- the four call sites ----

    /**
     * <pre>
     * foreach (CardExecutor exec in Executor.Executors)
     * {
     *     ... Activate, MonsterSet, Repos, SpSummon, Summon, SummonOrSet, SpellSet ...
     * }
     * if (main.CanBattlePhase &amp;&amp; Duel.Fields[0].HasAttackingMonster())
     *     return new MainPhaseAction(MainPhaseAction.MainAction.ToBattlePhase);
     * return new MainPhaseAction(MainPhaseAction.MainAction.ToEndPhase);
     * </pre>
     * The action kinds are tried in that fixed order within each executor, and
     * the terminal branch asks only whether we HAVE an attacker, never whether
     * attacking would be good. That judgement belongs to the battle phase.
     */
    private byte[] onSelectIdleCmd(DuelMessage.SelectIdleCmd idle)
    {
        for(CardExecutor exec : executor.executors())
        {
            for(int i = 0; i < idle.activatable().size(); i++)
            {
                DuelMessage.ActivatableOption option = idle.activatable().get(i);
                if(shouldExecute(exec, cardOf(option.code(), option.controller(), option.location(),
                    option.sequence()), ExecutorType.ACTIVATE))
                {
                    return Responses.idleActivate(i);
                }
            }
            for(int i = 0; i < idle.monsterSettable().size(); i++)
            {
                if(shouldExecute(exec, cardOf(idle.monsterSettable().get(i)), ExecutorType.MONSTER_SET))
                {
                    return Responses.idleMonsterSet(i);
                }
            }
            for(int i = 0; i < idle.repositionable().size(); i++)
            {
                if(shouldExecute(exec, cardOf(idle.repositionable().get(i)), ExecutorType.REPOS))
                {
                    return Responses.idleReposition(i);
                }
            }
            for(int i = 0; i < idle.spSummonable().size(); i++)
            {
                if(shouldExecute(exec, cardOf(idle.spSummonable().get(i)), ExecutorType.SP_SUMMON))
                {
                    return Responses.idleSpSummon(i);
                }
            }
            for(int i = 0; i < idle.summonable().size(); i++)
            {
                BotCard card = cardOf(idle.summonable().get(i));
                if(shouldExecute(exec, card, ExecutorType.SUMMON))
                {
                    return Responses.idleSummon(i);
                }
                if(shouldExecute(exec, card, ExecutorType.SUMMON_OR_SET))
                {
                    int set = indexOf(idle.monsterSettable(), idle.summonable().get(i));
                    if(set >= 0 && executor.onSelectMonsterSummonOrSet(card))
                    {
                        return Responses.idleMonsterSet(set);
                    }
                    return Responses.idleSummon(i);
                }
            }
            for(int i = 0; i < idle.spellSettable().size(); i++)
            {
                if(shouldExecute(exec, cardOf(idle.spellSettable().get(i)), ExecutorType.SPELL_SET))
                {
                    return Responses.idleSpellSet(i);
                }
            }
        }

        if(idle.toBattle() && executor.bot().hasAttackingMonster())
        {
            return Responses.idleToBattle();
        }
        return idle.toEnd() ? Responses.idleToEnd()
            : idle.toBattle() ? Responses.idleToBattle() : null;
    }

    /**
     * <pre>
     * List&lt;ClientCard&gt; attackers = new List&lt;ClientCard&gt;(battle.AttackableCards);
     * attackers.Sort(CardContainer.CompareCardAttack);
     * attackers.Reverse();
     * List&lt;ClientCard&gt; defenders = new List&lt;ClientCard&gt;(Duel.Fields[1].GetMonsters());
     * defenders.Sort(CardContainer.CompareDefensePower);
     * defenders.Reverse();
     * </pre>
     * Highest-ATK attacker first, strongest defender first, so the biggest
     * body that can be beaten is the one that dies.
     */
    private byte[] onSelectBattleCmd(DuelMessage.SelectBattleCmd battle)
    {
        for(CardExecutor exec : executor.executors())
        {
            for(int i = 0; i < battle.activatable().size(); i++)
            {
                DuelMessage.ActivatableOption option = battle.activatable().get(i);
                if(shouldExecute(exec, cardOf(option.code(), option.controller(), option.location(),
                    option.sequence()), ExecutorType.ACTIVATE))
                {
                    return Responses.battleActivate(i);
                }
            }
        }

        List<Integer> order = new ArrayList<>();
        for(int i = 0; i < battle.attackable().size(); i++)
        {
            order.add(i);
        }
        order.sort((left, right) -> Integer.compare(
            attackOf(battle.attackable().get(right)), attackOf(battle.attackable().get(left))));

        List<BotCard> defenders = util().enemyMonstersByPowerDescending();
        for(int index : order)
        {
            DuelMessage.AttackOption option = battle.attackable().get(index);
            BotCard attacker = cardOf(option.code(), option.controller(), option.location(),
                option.sequence());
            if(option.canDirect() || defenders.isEmpty())
            {
                expectingAttackTarget = true;
                pendingAttacker = attacker;
                return Responses.battleAttack(index);
            }
            if(executor.onSelectAttackTarget(attacker, defenders) != null)
            {
                expectingAttackTarget = true;
                pendingAttacker = attacker;
                return Responses.battleAttack(index);
            }
        }

        if(battle.toMain2())
        {
            return Responses.battleToMain2();
        }
        return battle.toEnd() ? Responses.battleToEnd() : null;
    }

    /**
     * <pre>
     * foreach (CardExecutor exec in Executor.Executors)
     *     for (int i = 0; i &lt; cards.Count; ++i)
     *         if (ShouldExecute(exec, cards[i], ExecutorType.Activate, descs[i])) return i;
     * ...
     * return forced ? 0 : -1;
     * </pre>
     * There is no default yes. An unregistered card is declined, and only a
     * forced chain bypasses the list.
     */
    private byte[] onSelectChain(DuelMessage.SelectChain chain)
    {
        for(CardExecutor exec : executor.executors())
        {
            for(int i = 0; i < chain.chains().size(); i++)
            {
                DuelMessage.ChainOption option = chain.chains().get(i);
                if(shouldExecute(exec, cardOf(option.code(), option.loc().controller(),
                    option.loc().location(), option.loc().sequence()), ExecutorType.ACTIVATE))
                {
                    return Responses.chain(i);
                }
            }
        }
        return chain.forced() && !chain.chains().isEmpty()
            ? Responses.chain(0) : Responses.chainDecline();
    }

    /**
     * <pre>
     * public bool OnSelectEffectYn(ClientCard card, int desc)
     * {
     *     foreach (CardExecutor exec in Executor.Executors)
     *         if (ShouldExecute(exec, card, ExecutorType.Activate, desc)) return true;
     *     return false;
     * }
     * </pre>
     * The default is NO. Answering yes here unconditionally is a hole straight
     * through the executor list, because it activates a card the list declined.
     */
    private byte[] onSelectEffectYn(DuelMessage.SelectEffectYesNo effect)
    {
        BotCard card = cardOf(effect.code(), effect.loc().controller(), effect.loc().location(),
            effect.loc().sequence());
        for(CardExecutor exec : executor.executors())
        {
            if(shouldExecute(exec, card, ExecutorType.ACTIVATE))
            {
                return Responses.yes();
            }
        }
        return Responses.no();
    }

    private byte[] onSelectPosition(DuelMessage.SelectPosition position)
    {
        int wanted = executor.onSelectPosition(position.code(), position.positions());
        if(wanted != 0 && (position.positions() & wanted) != 0)
        {
            return Responses.position(wanted);
        }
        // "0 if no position is set for this card": take the engine's default,
        // which is the first legal position.
        for(int pos : new int[] {OcgConstants.POS_FACEUP_ATTACK, OcgConstants.POS_FACEUP_DEFENSE,
            OcgConstants.POS_FACEDOWN_DEFENSE, OcgConstants.POS_FACEDOWN_ATTACK})
        {
            if((position.positions() & pos) != 0)
            {
                return Responses.position(pos);
            }
        }
        return null;
    }

    /**
     * Target selection. When we have just declared an attack this is the
     * defender choice and {@code OnSelectAttackTarget} decides it; otherwise
     * WindBot's terminal fallback applies — {@code GameAI.OnSelectCard} ends
     * with "select the first available cards and choose the minimum".
     */
    private byte[] onSelectCard(DuelMessage.SelectCard select)
    {
        boolean asAttackTarget = expectingAttackTarget;
        BotCard attacker = pendingAttacker;
        expectingAttackTarget = false;
        pendingAttacker = null;

        if(select.cards().isEmpty())
        {
            return null;
        }
        int count = Math.max(select.min(), 1);
        if(count > select.cards().size())
        {
            return null;
        }

        if(asAttackTarget && attacker != null)
        {
            List<BotCard> offered = new ArrayList<>();
            for(DuelMessage.SelectableCard card : select.cards())
            {
                offered.add(cardOf(card.code(), card.loc().controller(), card.loc().location(),
                    card.loc().sequence()));
            }
            List<BotCard> sorted = new ArrayList<>(offered);
            sorted.sort((left, right) -> Integer.compare(right.getDefensePower(), left.getDefensePower()));
            BotCard chosen = executor.onSelectAttackTarget(attacker, sorted);
            if(chosen != null)
            {
                int index = offered.indexOf(chosen);
                if(index >= 0)
                {
                    return Responses.selectCards(new int[] {index});
                }
            }
        }

        int[] chosen = new int[count];
        for(int i = 0; i < count; i++)
        {
            chosen[i] = i;
        }
        return Responses.selectCards(chosen);
    }

    // ---- helpers ----

    private AIUtil util()
    {
        return new AIUtil(executor);
    }

    private int attackOf(DuelMessage.AttackOption option)
    {
        BotCard card = cardOf(option.code(), option.controller(), option.location(), option.sequence());
        return card == null ? 0 : card.attack();
    }

    private BotCard cardOf(DuelMessage.IdleOption option)
    {
        return cardOf(option.code(), option.controller(), option.location(), option.sequence());
    }

    private static int indexOf(List<DuelMessage.IdleOption> options, DuelMessage.IdleOption wanted)
    {
        for(int i = 0; i < options.size(); i++)
        {
            DuelMessage.IdleOption option = options.get(i);
            if(option.code() == wanted.code() && option.location() == wanted.location()
                && option.sequence() == wanted.sequence())
            {
                return i;
            }
        }
        return -1;
    }

    /**
     * Resolves an option to the live card in its zone, so a rule reads the
     * core's current ATK rather than the printed one. A card in hand has no
     * board entry, so it is built from the database instead.
     */
    private BotCard cardOf(int code, int controller, int location, int sequence)
    {
        int side = controller == player ? 0 : 1;
        BotField field = side == 0 ? executor.bot() : executor.enemy();
        if(field != null)
        {
            List<BotCard> zone = switch(location)
            {
                case OcgConstants.LOCATION_MZONE -> field.monsterZone;
                case OcgConstants.LOCATION_SZONE -> field.spellZone;
                case OcgConstants.LOCATION_HAND -> field.hand;
                case OcgConstants.LOCATION_GRAVE -> field.graveyard;
                case OcgConstants.LOCATION_REMOVED -> field.banished;
                case OcgConstants.LOCATION_EXTRA -> field.extra;
                default -> null;
            };
            if(zone != null && sequence >= 0 && sequence < zone.size())
            {
                BotCard card = zone.get(sequence);
                if(card != null && (card.code() == code || card.code() == 0))
                {
                    return card;
                }
            }
        }
        de.cas_ual_ty.dueldimension.ocg.OcgCard data = cards.get(code);
        return new BotCard(code, OcgConstants.POS_FACEUP_ATTACK,
            data == null ? 0 : data.type(), data == null ? 0 : data.level(),
            data == null ? 0 : data.attack(), data == null ? 0 : data.defense(),
            side, location, sequence);
    }
}
