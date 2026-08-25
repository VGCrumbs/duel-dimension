package de.cas_ual_ty.dueldimension.ocg.bot.executor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * The base every duelist AI extends — WindBot's {@code Executor}.
 * <p>
 * This is the seam the whole design turns on. A duelist does not describe what
 * its cards <em>mean</em>; it registers, in order, the actions it is willing to
 * take and the condition for each:
 * <pre>
 * AddExecutor(ExecutorType.Activate, CardId.DarkHole, DefaultDarkHole);
 * AddExecutor(ExecutorType.SpellSet, DefaultSpellSet);
 * AddExecutor(ExecutorType.Activate, CardId.Fissure);
 * AddExecutor(ExecutorType.Repos, DefaultMonsterRepos);
 * </pre>
 * That is {@code OldSchoolExecutor}, WindBot's own deck for cards of exactly
 * our era, and it is the template our duelists follow.
 * <p>
 * <b>Registration order is priority.</b> {@code GameAI} loops over executors on
 * the outside and candidate cards on the inside, returning the first match as
 * the single action for that decision point. There is no scoring anywhere in
 * WindBot; a rule earlier in the constructor simply wins.
 * <p>
 * A card with no registered executor is never activated. That is not a policy
 * choice bolted on top — it falls out of the gate in
 * {@link ExecutorBot#shouldExecute}, which requires a matching entry to exist.
 */
public abstract class Executor
{
    private final List<CardExecutor> executors = new ArrayList<>();

    /** {@code Duel.Fields[0]} and {@code [1]}: our side and theirs. */
    private BotField bot;
    private BotField enemy;

    /** {@code Executor.SetCard} state: what the gate is currently asking about. */
    private ExecutorType type;
    private BotCard card;

    /** Duel-wide facts the ported predicates read. */
    private int turn;
    private int phase;
    private int turnPlayer = -1;
    private int player;
    private int lastChainPlayer = -1;
    private int lastSummonPlayer = -1;

    protected final AIUtil util = new AIUtil(this);

    /**
     * {@code GameAI.m_selector}: cards a rule has already decided on, waiting
     * for the prompt that asks for them.
     * <p>
     * This is how a card-specific rule controls its own targeting. WindBot's
     * own {@code DefaultCallOfTheHaunted} is the pattern -- it picks the
     * monster, hands it over, and only then returns true:
     * <pre>
     * ClientCard selected = Bot.Graveyard.GetMatchingCards(card =&gt; card.IsCanRevive())
     *     .OrderByDescending(card =&gt; card.Attack).FirstOrDefault();
     * AI.SelectCard(selected);
     * return true;
     * </pre>
     * Without it every effect falls back to "take the first cards offered",
     * which is fine for a cost and wrong for a target.
     */
    private final List<BotCard> selection = new ArrayList<>();

    // ---- the registration API ----

    /**
     * {@code public void AddExecutor(ExecutorType type, int cardId, Func<bool> func)}
     * — do this action for this card when the condition holds.
     */
    protected final void addExecutor(ExecutorType type, int cardId, BooleanSupplier func)
    {
        executors.add(new CardExecutor(type, cardId, func));
    }

    /**
     * {@code public void AddExecutor(ExecutorType type, int cardId)} — do this
     * action for this card whenever it is available. A null condition is
     * WindBot's own idiom for "this card is always worth playing".
     */
    protected final void addExecutor(ExecutorType type, int cardId)
    {
        executors.add(new CardExecutor(type, cardId, null));
    }

    /**
     * {@code public void AddExecutor(ExecutorType type, Func<bool> func)} — do
     * this action for EVERY card when the condition holds. This is the
     * id-wildcard enabler, and it is how the generic rules are hung on:
     * {@code AddExecutor(ExecutorType.Repos, DefaultMonsterRepos)}.
     */
    protected final void addExecutor(ExecutorType type, BooleanSupplier func)
    {
        executors.add(new CardExecutor(type, CardExecutor.ANY, func));
    }

    /**
     * {@code public void AddExecutor(ExecutorType type)} — do this action for
     * every card that has no rule of its own:
     * <pre>
     * private bool DefaultNoExecutor()
     * {
     *     return Executors.All(exec =&gt; exec.Type != Type || exec.CardId != Card.Id);
     * }
     * </pre>
     */
    protected final void addExecutor(ExecutorType type)
    {
        executors.add(new CardExecutor(type, CardExecutor.ANY, this::defaultNoExecutor));
    }

    private boolean defaultNoExecutor()
    {
        for(CardExecutor exec : executors)
        {
            if(exec.type() == type && card != null && exec.cardId() == card.code())
            {
                return false;
            }
        }
        return true;
    }

    public final List<CardExecutor> executors()
    {
        return executors;
    }

    /** {@code AI.SelectCard(card)}: name the cards this effect should be pointed at, in order. */
    protected final void selectCard(BotCard... cards)
    {
        selection.clear();
        for(BotCard card : cards)
        {
            if(card != null)
            {
                selection.add(card);
            }
        }
    }

    protected final void selectCard(List<BotCard> cards)
    {
        selection.clear();
        for(BotCard card : cards)
        {
            if(card != null)
            {
                selection.add(card);
            }
        }
    }

    /** Consumed by the dispatcher when the selection prompt arrives. */
    final List<BotCard> takeSelection()
    {
        List<BotCard> taken = new ArrayList<>(selection);
        selection.clear();
        return taken;
    }

    final void clearSelection()
    {
        selection.clear();
    }

    // ---- state the gate maintains ----

    final void setCard(ExecutorType type, BotCard card)
    {
        this.type = type;
        this.card = card;
    }

    /** {@code Bot.BattlingMonster}: the monster whose attack is being resolved. */
    private BotCard battlingMonster;

    /**
     * The monster currently attacking, or null outside a battle step.
     * <p>
     * WindBot keeps this on the field object; the host sets it when it declares
     * the attack, and it is what a replay decision is asked about.
     */
    protected final BotCard battlingMonster()
    {
        return battlingMonster;
    }

    final void setBattlingMonster(BotCard attacker)
    {
        this.battlingMonster = attacker;
    }

    final void setFields(BotField bot, BotField enemy)
    {
        this.bot = bot;
        this.enemy = enemy;
    }

    final void setDuelState(int turn, int phase, int turnPlayer, int player,
        int lastChainPlayer, int lastSummonPlayer)
    {
        this.turn = turn;
        this.phase = phase;
        this.turnPlayer = turnPlayer;
        this.player = player;
        this.lastChainPlayer = lastChainPlayer;
        this.lastSummonPlayer = lastSummonPlayer;
    }

    /** {@code Card}: the card the current rule is being asked about. */
    protected final BotCard card()
    {
        return card;
    }

    /** {@code Type}: the action the current rule is being asked about. */
    protected final ExecutorType type()
    {
        return type;
    }

    /** {@code Bot}: our side of the table. */
    public final BotField bot()
    {
        return bot;
    }

    /** {@code Enemy}: theirs. */
    public final BotField enemy()
    {
        return enemy;
    }

    protected final int turn()
    {
        return turn;
    }

    protected final int phase()
    {
        return phase;
    }

    /**
     * {@code Duel.Player}: 0 when it is OUR turn, 1 when it is theirs. WindBot
     * writes rules as {@code if (Duel.Player == 0) return false;}, so this is
     * normalised to that numbering rather than to the core's seat numbers.
     */
    protected final int duelPlayer()
    {
        return turnPlayer < 0 ? 0 : turnPlayer == player ? 0 : 1;
    }

    /** {@code Duel.LastChainPlayer}, in the same 0=us/1=them numbering, -1 when none. */
    protected final int lastChainPlayer()
    {
        return lastChainPlayer < 0 ? -1 : lastChainPlayer == player ? 0 : 1;
    }

    /** {@code Duel.LastSummonPlayer}, same numbering, -1 when none. */
    protected final int lastSummonPlayer()
    {
        return lastSummonPlayer < 0 ? -1 : lastSummonPlayer == player ? 0 : 1;
    }

    /**
     * Attackers still to declare this battle phase, including the current one.
     * <p>
     * Not part of the reference. It exists for one rule: a monster that
     * survives its first battle each turn is worth attacking only if a second
     * body can follow up, and "is there a second body" is a question about the
     * attacker LIST, which lives in the host. {@code ExecutorBot} sets it from
     * the same sorted list it reads {@code isLastAttacker} off.
     */
    private int attackersLeft;

    /** Battles each defender has been in this turn, and the turn that counts. */
    private final java.util.Map<String, Integer> battlesThisTurn = new java.util.HashMap<>();
    private int battlesCountedOn = -1;

    public final void setAttackersLeft(int count)
    {
        attackersLeft = count;
    }

    protected final int attackersLeft()
    {
        return attackersLeft;
    }

    /** Where a card is, which is what makes two copies of one passcode distinct. */
    private static String identityOf(BotCard card)
    {
        return card.controller() + ":" + card.location() + ":" + card.sequence()
            + ":" + card.code();
    }

    /**
     * Records that a defender has been attacked, so a shield that absorbs one
     * battle a turn is known to be spent.
     * <p>
     * Without this the follow-up never happens: the second attacker asks the
     * same question, reads the same "once per turn" off the same text, finds
     * only itself left to swing, and declines -- wasting the first attack,
     * which is the exact opposite of the rule's intent.
     */
    public final void noteBattle(BotCard defender)
    {
        forgetBattlesIfTurnChanged();
        battlesThisTurn.merge(identityOf(defender), 1, Integer::sum);
    }

    protected final int battlesThisTurn(BotCard defender)
    {
        forgetBattlesIfTurnChanged();
        return battlesThisTurn.getOrDefault(identityOf(defender), 0);
    }

    private void forgetBattlesIfTurnChanged()
    {
        if(battlesCountedOn != turn())
        {
            battlesCountedOn = turn();
            battlesThisTurn.clear();
        }
    }

    // ---- overridable hooks, matching Executor.cs's virtuals ----

    /** Which of their monsters this attacker should hit, or null to decline. */
    public BotCard onSelectAttackTarget(BotCard attacker, List<BotCard> defenders)
    {
        return null;
    }

    /** {@code OnPreBattleBetween}: false if this attack should not be made. */
    public boolean onPreBattleBetween(BotCard attacker, BotCard defender)
    {
        return true;
    }

    /** {@code OnPreActivate}: a veto pass that runs before the per-card rule. */
    public boolean onPreActivate(BotCard card)
    {
        return true;
    }

    /**
     * Whether to activate an optional effect no rule in the executor list
     * covers.
     * <p>
     * <b>Not Windbot.</b> Upstream there is no such question: GameAI's
     * OnSelectEffectYn returns false the moment the list runs out, so a card
     * without a rule is always declined. This is the one place the duelists
     * are allowed to differ, and the default here is the reference's answer --
     * only {@code DuelistExecutor} says otherwise, and only for the case where
     * declining cannot be the better play.
     *
     * @param location where the card is now, as a LOCATION_* constant
     */
    public boolean activateUnlistedOptional(BotCard card, int location)
    {
        return false;
    }

    /** {@code OnSelectPosition}: 0 to let the engine's own default stand. */
    public int onSelectPosition(int cardId, int available)
    {
        return 0;
    }

    /** {@code OnSelectMonsterSummonOrSet}: true to set the monster face down. */
    public boolean onSelectMonsterSummonOrSet(BotCard card)
    {
        return false;
    }

    /**
     * {@code OnSelectBattleReplay}: base returns false.
     * <p>
     * A replay is offered when the monster being attacked leaves the field
     * during the battle step. Answering it through {@code onSelectYesNo} —
     * whose base is "yes" — meant always swinging again into whatever the core
     * listed first. Declining is the safe base; {@link DefaultExecutor}
     * overrides it with the reference's actual judgement.
     */
    public boolean onSelectBattleReplay()
    {
        return false;
    }

    /** {@code OnSelectYesNo}: base returns true. */
    public boolean onSelectYesNo(long description)
    {
        return true;
    }
}
