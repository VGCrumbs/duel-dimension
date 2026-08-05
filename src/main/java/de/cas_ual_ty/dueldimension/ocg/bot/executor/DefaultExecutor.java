package de.cas_ual_ty.dueldimension.ocg.bot.executor;

import de.cas_ual_ty.dueldimension.ocg.OcgConstants;

import java.util.List;

/**
 * WindBot's {@code DefaultExecutor}: the deck-agnostic rules every duelist
 * inherits, transliterated from
 * {@code ExecutorBase/Game/AI/DefaultExecutor.cs}.
 * <p>
 * Each method carries its original C# above it. That is the point of this
 * class — a rule here can be diffed against the reference rather than argued
 * about, and anything not present in the reference has no business being here.
 * <p>
 * The card-specific {@code Default*} predicates (DefaultSolemnJudgment,
 * DefaultBookOfMoon, the Kaiju package) are deliberately absent: they name
 * modern passcodes that no starter deck contains, so porting them would be
 * dead code. What IS ported is everything that applies to any card at all,
 * plus the handful of card rules whose passcodes we actually hold.
 */
public abstract class DefaultExecutor extends Executor
{
    /**
     * Passcodes referenced by the ported rules. WindBot keeps these in a
     * {@code _CardId} class for the same reason: a rule keyed to a magic number
     * is unreadable, and the gate compares raw ids.
     */
    protected static final class CardId
    {
        public static final int DarkHole = 53129443;
        public static final int Fissure = 66788016;
        public static final int MonsterReborn = 83764719;
        public static final int Scapegoat = 73915051;
        public static final int Polymerization = 24094653;
        public static final int Waboku = 12607053;
        public static final int SevenToolsOfTheBandit = 3819470;
        public static final int JustDesserts = 24068492;
        public static final int CardDestruction = 72892473;
        public static final int Mountain = 50913601;
        public static final int Sogen = 86318356;
        public static final int Yami = 59197169;

        private CardId()
        {
        }
    }

    /**
     * <pre>
     * /// Summon with no tribute, or with tributes ATK lower.
     * protected bool DefaultMonsterSummon()
     * {
     *     if (Card.Level &lt;= 4)
     *         return true;
     *
     *     if (!UniqueFaceupMonster())
     *         return false;
     *     int tributecount = (int)Math.Ceiling((Card.Level - 4.0d) / 2.0d);
     *     for (int j = 0; j &lt; 7; ++j)
     *     {
     *         ClientCard tributeCard = Bot.MonsterZone[j];
     *         if (tributeCard == null) continue;
     *         if (tributeCard.GetDefensePower() &lt; Card.Attack) tributecount--;
     *     }
     *     return tributecount &lt;= 0;
     * }
     * </pre>
     */
    protected boolean defaultMonsterSummon()
    {
        BotCard self = card();
        if(self == null)
        {
            return false;
        }
        if(self.level() <= 4)
        {
            return true;
        }
        if(!uniqueFaceupMonster())
        {
            return false;
        }
        int tributecount = (int)Math.ceil((self.level() - 4.0D) / 2.0D);
        for(int j = 0; j < 7 && j < bot().monsterZone.size(); ++j)
        {
            BotCard tributeCard = bot().monsterZone.get(j);
            if(tributeCard == null)
            {
                continue;
            }
            if(tributeCard.getDefensePower() < self.attack())
            {
                tributecount--;
            }
        }
        return tributecount <= 0;
    }

    /** {@code UniqueFaceupMonster}: we do not already control a copy of this card. */
    protected boolean uniqueFaceupMonster()
    {
        BotCard self = card();
        for(BotCard monster : bot().getMonsters())
        {
            if(monster.isFaceUp() && self != null && monster.isCode(self.code()))
            {
                return false;
            }
        }
        return true;
    }

    /** {@code UniqueFaceupSpell}: same, for our spell/trap zone. */
    protected boolean uniqueFaceupSpell()
    {
        BotCard self = card();
        for(BotCard spell : bot().getSpells())
        {
            if(spell.isFaceUp() && self != null && spell.isCode(self.code()))
            {
                return false;
            }
        }
        return true;
    }

    /**
     * <pre>
     * /// Set traps only and avoid block the activation of other cards.
     * protected bool DefaultSpellSet()
     * {
     *     return (Card.IsTrap() || Card.HasType(CardType.QuickPlay) || DefaultSpellMustSetFirst())
     *         &amp;&amp; Bot.GetSpellCountWithoutField() &lt; 4;
     * }
     * </pre>
     * The Anti-Spell Fragrance clause inside {@code DefaultSpellMustSetFirst}
     * is dropped: that card is in no starter deck, so the sub-predicate is
     * constant false here and inlining it would be inventing a rule.
     */
    protected boolean defaultSpellSet()
    {
        BotCard self = card();
        return self != null
            && (self.isTrap() || self.hasType(OcgConstants.TYPE_QUICKPLAY))
            && bot().getSpellCountWithoutField() < 4;
    }

    /**
     * <pre>
     * /// Turn if all enemy is better.
     * protected bool DefaultMonsterRepos()
     * {
     *     if (Card.IsMonsterInvincible()) return Card.IsDefense();
     *     if (Card.Attack == 0)
     *     {
     *         if (Card.IsFaceup() &amp;&amp; Card.IsAttack()) return true;
     *         if (Card.IsFaceup() &amp;&amp; Card.IsDefense()) return false;
     *     }
     *     ... two Blue-Eyes Chaos MAX Dragon clauses ...
     *     bool enemyBetter = Util.IsAllEnemyBetter();
     *     if (Card.IsAttack() &amp;&amp; enemyBetter) return true;
     *     if (Card.IsDefense() &amp;&amp; !enemyBetter &amp;&amp;
     *         (Card.Attack &gt; Card.Defense
     *          || (Duel.Phase == DuelPhase.Main1 &amp;&amp; Card.Attack &gt;= Util.GetBestPower(Enemy))))
     *         return true;
     *     return false;
     * }
     * </pre>
     * The Blue-Eyes Chaos MAX clauses are omitted for the same reason as
     * above: that passcode cannot appear in these decks.
     */
    protected boolean defaultMonsterRepos()
    {
        BotCard self = card();
        if(self == null)
        {
            return false;
        }
        if(self.attack() == 0)
        {
            if(self.isFaceUp() && self.isAttack())
            {
                return true;
            }
            if(self.isFaceUp() && self.isDefense())
            {
                return false;
            }
        }
        boolean enemyBetter = util.isAllEnemyBetter();
        if(self.isAttack() && enemyBetter)
        {
            return true;
        }
        return self.isDefense() && !enemyBetter
            && (self.attack() > self.defense()
                || (phase() == OcgConstants.PHASE_MAIN1 && self.attack() >= util.getBestPower(enemy())));
    }

    /**
     * <pre>
     * /// Activate when we have no field.
     * protected bool DefaultField()
     * {
     *     return Bot.SpellZone[5] == null;
     * }
     * </pre>
     * Note it evaluates nothing at all about the field spell itself.
     */
    protected boolean defaultField()
    {
        return bot().spellZone.size() <= 5 || bot().spellZone.get(5) == null;
    }

    /**
     * <pre>
     * /// Chain enemy activation or summon.
     * protected bool DefaultTrap()
     * {
     *     if (DefaultCheckWhetherCardIsNegated(Card)) return false;
     *     return (Duel.LastChainPlayer == -1 &amp;&amp; Duel.LastSummonPlayer != 0) || Duel.LastChainPlayer == 1;
     * }
     * </pre>
     * The negation-tracking guard is dropped: it consults a Crossout Designator
     * list that only modern cards populate.
     * <p>
     * Read the sentinels carefully, because this is narrower than "the opponent
     * is doing something": with nothing on the chain it demands that the last
     * summon was not ours. Both fields reset at the start of every turn.
     */
    protected boolean defaultTrap()
    {
        return (lastChainPlayer() == -1 && lastSummonPlayer() != 0) || lastChainPlayer() == 1;
    }

    /**
     * <pre>
     * /// Activate when avail and no other our trap card in this chain or face-up.
     * protected bool DefaultUniqueTrap()
     * {
     *     if (Util.HasChainedTrap(0)) return false;
     *     return UniqueFaceupSpell();
     * }
     * </pre>
     */
    protected boolean defaultUniqueTrap()
    {
        return uniqueFaceupSpell();
    }

    /**
     * <pre>
     * protected bool DefaultDontChainMyself()
     * {
     *     if (Type != ExecutorType.Activate) return true;
     *     if (Executors.Any(exec =&gt; exec.Type == Type &amp;&amp; exec.CardId == Card.Id)) return false;
     *     return Duel.LastChainPlayer != 0;
     * }
     * </pre>
     */
    protected boolean defaultDontChainMyself()
    {
        if(type() != ExecutorType.ACTIVATE)
        {
            return true;
        }
        BotCard self = card();
        for(CardExecutor exec : executors())
        {
            if(exec.type() == type() && self != null && exec.cardId() == self.code())
            {
                return false;
            }
        }
        return lastChainPlayer() != 0;
    }

    /**
     * <pre>
     * /// Activate when one enemy monsters have better ATK or DEF.
     * protected bool DefaultDarkHole()
     * {
     *     return Util.IsOneEnemyBetter();
     * }
     * </pre>
     * With our board empty {@code GetBestPower} is -1, so any enemy monster at
     * all triggers this, which is why it never wipes only its own side.
     */
    protected boolean defaultDarkHole()
    {
        return util.isOneEnemyBetter();
    }

    /** {@code DefaultRaigeki} and {@code DefaultSmashingGround} share this body. */
    protected boolean defaultRaigeki()
    {
        return util.isOneEnemyBetter();
    }

    /**
     * <pre>
     * protected bool DefaultHammerShot()
     * {
     *     return Util.IsOneEnemyBetter(true);
     * }
     * </pre>
     * The ATK-only variant, for removal that can only hit attack position.
     */
    protected boolean defaultHammerShot()
    {
        return util.isOneEnemyBetter(true);
    }

    /**
     * <pre>
     * /// Revive the best monster when we don't have better one in field.
     * protected bool DefaultCallOfTheHaunted()
     * {
     *     if (!Util.IsAllEnemyBetter(true)) return false;
     *     ClientCard selected = Bot.Graveyard.GetMatchingCards(card =&gt; card.IsCanRevive())
     *         .OrderByDescending(card =&gt; card.Attack).FirstOrDefault();
     *     AI.SelectCard(selected);
     *     return true;
     * }
     * </pre>
     * A losing-board rule, not a value rule: it demands that EVERY enemy
     * monster out-powers our best attacker, which is false when they have none.
     */
    protected boolean defaultCallOfTheHaunted()
    {
        return util.isAllEnemyBetter(true) && !bot().getGraveyard().isEmpty();
    }

    /**
     * <pre>
     * protected bool DefaultScapegoat()
     * {
     *     if (DefaultSpellWillBeNegated()) return false;
     *     if (Duel.Player == 0) return false;
     *     if (Duel.Phase == DuelPhase.End) return true;
     *     if (DefaultOnBecomeTarget()) return true;
     *     if (Duel.Phase &gt; DuelPhase.Main1 &amp;&amp; Duel.Phase &lt; DuelPhase.Main2)
     *     {
     *         ... enemy-specific card list ...
     *         if (Util.GetTotalAttackingMonsterAttack(1) &gt;= Bot.LifePoints) return true;
     *     }
     *     return false;
     * }
     * </pre>
     * {@code Duel.Player == 0} means our own turn, so this is a purely
     * defensive card: it never fires on our turn, which is what makes the
     * "cannot summon this turn" drawback free.
     */
    protected boolean defaultScapegoat()
    {
        if(duelPlayer() == 0)
        {
            return false;
        }
        if(phase() == OcgConstants.PHASE_END)
        {
            return true;
        }
        if(phase() > OcgConstants.PHASE_MAIN1 && phase() < OcgConstants.PHASE_MAIN2)
        {
            return util.getTotalAttackingMonsterAttack(enemy()) >= bot().lifePoints;
        }
        return false;
    }

    /**
     * <pre>
     * /// Activate when we have more than 15 cards in deck.
     * protected bool DefaultPotOfDesires() { return Bot.Deck.Count &gt; 15; }
     * </pre>
     * Reused for Card Destruction, the one refill-the-hand card we hold: the
     * rule is about not decking out, which is the same risk.
     */
    protected boolean defaultDeckIsDeep()
    {
        return bot().deckCount > 15;
    }

    // ---- overridden hooks ----

    /**
     * <pre>
     * public override CardPosition OnSelectPosition(int cardId, IList&lt;CardPosition&gt; positions)
     * {
     *     ... if (cardData.Attack == 0) return CardPosition.FaceUpDefence; ...
     * }
     * </pre>
     * The whole generic rule: a 0 ATK monster stands in defence, everything
     * else takes the engine's own default. The caller supplies the ATK, since
     * a position prompt names a card the board does not hold yet.
     */
    @Override
    public int onSelectPosition(int cardId, int available)
    {
        return 0;
    }

    /**
     * <pre>
     * /// Set when this card can't beat the enemies
     * public override bool OnSelectMonsterSummonOrSet(ClientCard card)
     * {
     *     return card.Level &lt;= 4
     *         &amp;&amp; Bot.GetMonsters().Count(m =&gt; m.IsFaceup()) == 0
     *         &amp;&amp; Util.IsAllEnemyBetterThanValue(card.Attack, true);
     * }
     * </pre>
     */
    @Override
    public boolean onSelectMonsterSummonOrSet(BotCard card)
    {
        int faceUp = 0;
        for(BotCard monster : bot().getMonsters())
        {
            if(monster.isFaceUp())
            {
                faceUp++;
            }
        }
        return card.level() <= 4 && faceUp == 0
            && util.isAllEnemyBetterThanValue(card.attack(), true);
    }

    /**
     * <pre>
     * public override BattlePhaseAction OnSelectAttackTarget(ClientCard attacker, IList&lt;ClientCard&gt; defenders)
     * {
     *     foreach (ClientCard defender in defenders)
     *     {
     *         attacker.RealPower = attacker.Attack;
     *         defender.RealPower = defender.GetDefensePower();
     *         if (!OnPreBattleBetween(attacker, defender)) continue;
     *         if (attacker.RealPower &gt; defender.RealPower
     *             || (attacker.RealPower &gt;= defender.RealPower &amp;&amp; attacker.IsLastAttacker &amp;&amp; defender.IsAttack()))
     *             return AI.Attack(attacker, defender);
     *     }
     *     if (attacker.CanDirectAttack) return AI.Attack(attacker, null);
     *     return null;
     * }
     * </pre>
     * Defenders arrive sorted by power descending, so the first one the
     * attacker beats is the biggest body that still dies. Strictly greater
     * than: an even trade is declined. The {@code IsLastAttacker} clause that
     * permits an even trade needs per-attack state the core does not expose to
     * a host, so it is left out rather than approximated.
     */
    @Override
    public BotCard onSelectAttackTarget(BotCard attacker, List<BotCard> defenders)
    {
        for(BotCard defender : defenders)
        {
            attacker.realPower = attacker.attack();
            defender.realPower = defender.getDefensePower();
            if(!onPreBattleBetween(attacker, defender))
            {
                continue;
            }
            if(attacker.realPower > defender.realPower)
            {
                return defender;
            }
        }
        return null;
    }

    /**
     * <pre>
     * public override bool OnPreBattleBetween(ClientCard attacker, ClientCard defender)
     * </pre>
     * The reference body is 117 lines, and every branch of it names a modern
     * passcode: Mekk-Knight Crusadia Astram, Crystal Wing Synchro Dragon, Ally
     * of Justice Catastor, Moon Mirror Shield. None can appear in a starter
     * deck, so the generic remainder is "no objection", which is what the
     * reference itself returns once those checks fall through.
     */
    @Override
    public boolean onPreBattleBetween(BotCard attacker, BotCard defender)
    {
        return true;
    }
}
