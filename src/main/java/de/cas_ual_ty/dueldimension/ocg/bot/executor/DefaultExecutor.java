package de.cas_ual_ty.dueldimension.ocg.bot.executor;

import de.cas_ual_ty.dueldimension.ocg.OcgConstants;
import de.cas_ual_ty.dueldimension.ocg.bot.BattleProtection;

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
        public static final int MysticalSpaceTyphoon = 5318639;
        public static final int BookOfMoon = 14087893;
        public static final int TorrentialTribute = 53582587;
        public static final int SmashingGround = 97169186;
        public static final int HeavyStorm = 19613556;
        public static final int CompulsoryEvacuationDevice = 94192409;

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
        // DefaultExecutor.cs:1207, verbatim:
        //   Card.Attack >= Card.Defense || Card.Attack >= Util.GetBestPower(Enemy)
        // Both comparisons are >=, and there is no phase gate. This tree had >
        // and a PHASE_MAIN1 clause that appears nowhere in the reference, which
        // meant an ATK==DEF monster -- most of a starter deck -- could be set
        // once and never stand up again, and a defender that came to out-power
        // the enemy board could not re-arm in Main Phase 2, right when the
        // board had just changed in its favour.
        return self.isDefense() && !enemyBetter
            && (self.attack() >= self.defense()
                || self.attack() >= util.getBestPower(enemy()));
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
     * protected bool DefaultHeavyStorm()
     * {
     *     return Bot.GetSpellCount() &lt; Enemy.GetSpellCount();
     * }
     * </pre>
     * A trade rule, not a value one: it fires only when the opponent has more
     * spells and traps out than we do, so a storm never costs us more than it
     * takes. It does not look at WHAT is out — the reference does not either.
     */
    protected boolean defaultHeavyStorm()
    {
        return bot().getSpells().size() < enemy().getSpells().size();
    }

    /**
     * <pre>
     * protected bool DefaultTorrentialTribute()
     * {
     *     return !Util.HasChainedTrap(0) &amp;&amp; Util.IsAllEnemyBetter(true);
     * }
     * </pre>
     * Wipes the board only when EVERY enemy monster out-powers our best
     * attacker, so it is a losing-board button rather than a trade.
     * <p>
     * <b>Deliberately NOT registered yet.</b> {@code HasChainedTrap(0)} reads
     * {@code Duel.CurrentChain} — the cards on the chain right now — and we
     * track only {@code lastChainPlayer}, an int. That guard is what stops the
     * bot chaining this to its OWN trap and wiping its own board, so dropping
     * it is not a nuance, it is a misplay the reference explicitly prevents.
     * The body is ported and correct; it goes live the moment chain contents
     * are exposed, and until then registering it would be inventing behaviour
     * by omission.
     */
    protected boolean defaultTorrentialTribute()
    {
        return util.isAllEnemyBetter(true);
    }

    /**
     * <pre>
     * protected bool DefaultBookOfMoon()
     * {
     *     if (Util.IsAllEnemyBetter(true))
     *     {
     *         ClientCard monster = Enemy.GetMonsters().GetHighestAttackMonster(true);
     *         if (monster != null &amp;&amp; monster.HasType(CardType.Effect) &amp;&amp; !monster.HasType(CardType.Link)
     *             &amp;&amp; (monster.HasType(CardType.Xyz) || monster.Level &gt; 4))
     *         {
     *             AI.SelectCard(monster);
     *             return true;
     *         }
     *     }
     *     return false;
     * }
     * </pre>
     * Turns their best monster face-down, which strips an Effect monster of its
     * effect as well as its attack. The level/Xyz test is what stops it being
     * spent on a small body: flipping a Level 4 beater is rarely worth the card.
     */
    protected boolean defaultBookOfMoon()
    {
        if(!util.isAllEnemyBetter(true))
        {
            return false;
        }
        List<BotCard> byPower = util.enemyMonstersByPowerDescending();
        for(BotCard monster : byPower)
        {
            // Face-up only: a set monster's type and level are hidden, and the
            // reference reads both before committing.
            if(monster.isFaceDown() || !monster.isAttack())
            {
                continue;
            }
            if(monster.hasType(OcgConstants.TYPE_EFFECT)
                && !monster.hasType(OcgConstants.TYPE_LINK)
                && (monster.hasType(OcgConstants.TYPE_XYZ) || monster.level() > 4))
            {
                selectCard(monster);
                return true;
            }
            // GetHighestAttackMonster returns ONE card; the reference gives up
            // if that one does not qualify rather than looking further down.
            return false;
        }
        return false;
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
        if(card == null)
        {
            return false;
        }
        // ---- beyond the reference, and universal ------------------------
        // A FLIP monster face-up has thrown its effect away. Its whole text is
        // "when this card is flipped face-up", and one summoned in attack can
        // never be flipped face-up because it already is. The engine classifies
        // these itself, so this asks the TYPE rather than reading anything.
        if(card.hasType(OcgConstants.TYPE_FLIP))
        {
            return true;
        }
        // And outclassed by everything they control, set it. The reference says
        // this too, in the rule below -- but only for a level 4 or lower
        // monster, and only while we control nothing face-up. Those gates keep
        // it from firing in the case that costs the most: a board where they
        // have the bigger body everywhere and a face-up summon is a free attack
        // and free life points. Neither gate is a rule about whether the summon
        // is a good idea.
        //
        // A monster that answers a battle regardless is exempt, which is the
        // same question BattleProtection settles for attacking, asked from the
        // other side of the table.
        if(util.isAllEnemyBetter(true) && !winsAFightAnyway(card))
        {
            return true;
        }

        // ---- and the reference's own rule, unchanged --------------------
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
     * attacker beats is the biggest body that still dies — and, as the LAST
     * attacker, one it merely ties with, because an even trade costs no
     * further attacks and declining it hands the opponent the same trade on
     * their own terms.
     * <p>
     * This javadoc used to say the {@code IsLastAttacker} clause "needs
     * per-attack state the core does not expose to a host". That was wrong:
     * GameAI.cs:289 sets the flag from the position in the host's own sorted
     * attacker list, which {@code ExecutorBot} already builds. The clause is
     * now ported.
     */
    /**
     * <pre>
     * public override bool OnSelectBattleReplay()
     * {
     *     if (Bot.BattlingMonster == null)
     *         return false;
     *     List&lt;ClientCard&gt; defenders = new List&lt;ClientCard&gt;(Duel.Fields[1].GetMonsters());
     *     defenders.Sort(CardContainer.CompareDefensePower);
     *     defenders.Reverse();
     *     BattlePhaseAction result = OnSelectAttackTarget(Bot.BattlingMonster, defenders);
     *     if (result != null &amp;&amp; result.Action == BattlePhaseAction.BattleAction.Attack)
     *         return true;
     *     return false;
     * }
     * </pre>
     * Re-asks the same question the attack was declared on, against the board
     * as it stands NOW. The defenders are re-sorted strongest first, so a
     * "yes" means there is still something this monster beats — not merely
     * that it is allowed to swing again.
     */
    @Override
    public boolean onSelectBattleReplay()
    {
        BotCard attacker = battlingMonster();
        if(attacker == null)
        {
            return false;
        }
        List<BotCard> defenders = new java.util.ArrayList<>(enemy().getMonsters());
        // CompareDefensePower ascending, then reversed: strongest first.
        defenders.sort((left, right) ->
            Integer.compare(right.getDefensePower(), left.getDefensePower()));
        return onSelectAttackTarget(attacker, defenders) != null;
    }

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
            // DefaultExecutor.cs:370, both halves:
            //   attacker.RealPower > defender.RealPower
            //   || (attacker.RealPower >= defender.RealPower
            //       && attacker.IsLastAttacker && defender.IsAttack())
            // The second clause is the even trade. Declining it leaves both
            // monsters alive and hands the opponent the same trade on their
            // own terms next turn; taking it as the LAST attacker costs no
            // further attacks, which is why the reference gates it that way.
            if(attacker.realPower > defender.realPower
                || (attacker.realPower >= defender.realPower
                    && attacker.isLastAttacker && defender.isAttack()))
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
     * <p>
     * <b>What follows is NOT from the reference.</b> It is the same question
     * those 117 lines answer — is this attack worth making — asked of the
     * defender's rules text instead of a list of passcodes, so it covers the
     * cards a starter deck actually holds. See {@link BattleProtection} for why
     * it reads the card rather than a table, and for how conservative it is.
     * <p>
     * Three outcomes, in order:
     * <ul>
     * <li>the defender has no shield, or this attacker answers it anyway — the
     *     reference's "no objection";</li>
     * <li>the shield never lapses — decline, whatever the numbers say;</li>
     * <li>the shield absorbs N battles a turn — attack only if N+1 bodies can
     *     reach it this turn, counting the ones that already have.</li>
     * </ul>
     */
    @Override
    public boolean onPreBattleBetween(BotCard attacker, BotCard defender)
    {
        int survives = BattleProtection.battlesSurvived(textOf(defender));
        if(survives == BattleProtection.NONE)
        {
            return true;
        }
        // The attacker's own effect may not care whether the thing dies in
        // combat -- bouncing it, banishing it, flipping it down. That is the
        // exception the request turns on, so it is checked before the shield.
        if(BattleProtection.bypassesProtection(textOf(attacker)))
        {
            return true;
        }
        // Nor does the shield stop DAMAGE.
        //
        // A monster that cannot be destroyed by battle still loses the battle,
        // and its controller still takes the difference. So an indestructible
        // wall in ATTACK position is a perfectly good thing to attack -- the
        // point of the attack is the life points, not the kill. The size
        // comparison in onSelectAttackTarget already refuses this unless the
        // attacker is bigger, so nothing further is needed here.
        if(defender.isAttack())
        {
            return true;
        }
        // In DEFENCE it takes piercing, which most monsters do not have. Same
        // comparison again: onSelectAttackTarget weighs the attacker against
        // getDefensePower, so reaching this with a bigger attacker means the
        // difference is real damage.
        if(BattleProtection.pierces(textOf(attacker)))
        {
            return true;
        }
        if(survives == BattleProtection.ALWAYS)
        {
            return false;
        }
        // Spent shields plus swings still to come. The current attacker is
        // included in attackersLeft, so N+1 is the honest threshold: one to
        // spend the shield and one to land.
        return battlesThisTurn(defender) + attackersLeft() > survives;
    }

    /**
     * Whether this monster is worth having face-up even against bigger bodies.
     * <p>
     * Either it does something to what it battles rather than needing to beat
     * it, or it cannot be destroyed by battle at all. Both are read from the
     * card's own text by {@link BattleProtection}, so a monster the bot would
     * happily ATTACK a wall with is also one it will stand up.
     */
    private boolean winsAFightAnyway(BotCard card)
    {
        String text = textOf(card);
        return BattleProtection.bypassesProtection(text)
            || BattleProtection.battlesSurvived(text) != BattleProtection.NONE;
    }

    /**
     * A card's printed rules text, or empty when it cannot be identified.
     * <p>
     * Code 0 is a card the viewer may not see -- a set monster -- and a set
     * monster's text is not something the bot is entitled to read. Empty means
     * {@link BattleProtection#NONE}, so an unknown defender is attacked exactly
     * as it was before.
     */
    protected String textOf(BotCard card)
    {
        if(card == null || card.code() == 0)
        {
            return "";
        }
        de.cas_ual_ty.dueldimension.card.properties.Properties properties =
            de.cas_ual_ty.dueldimension.DdDatabase.PROPERTIES_LIST.get((long)card.code());
        if(properties == null || properties.text == null || properties.text.isBlank())
        {
            // Said once per card, not once per attack: every battle rule here
            // reads this, and a card the database cannot describe is judged as
            // if it had no effects at all. If a monster the bot plainly should
            // respect is being walked into, this is the line that says why.
            warnMissingText(card.code());
            return "";
        }
        return properties.text;
    }

    /** Codes already complained about, so a duel does not fill the log. */
    private static final java.util.Set<Integer> MISSING_TEXT =
        java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static void warnMissingText(int code)
    {
        if(MISSING_TEXT.add(code))
        {
            de.cas_ual_ty.dueldimension.DuelDimension.warn(
                "the bot has no rules text for card " + code
                    + "; its battle effects cannot be weighed");
        }
    }
}
