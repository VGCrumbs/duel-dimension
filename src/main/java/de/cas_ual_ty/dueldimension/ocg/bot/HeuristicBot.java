package de.cas_ual_ty.dueldimension.ocg.bot;

import de.cas_ual_ty.dueldimension.ocg.OcgCard;
import de.cas_ual_ty.dueldimension.ocg.OcgConstants;
import de.cas_ual_ty.dueldimension.ocg.OcgDuel;
import de.cas_ual_ty.dueldimension.ocg.RawMessage;
import de.cas_ual_ty.dueldimension.ocg.ResponseSource;
import de.cas_ual_ty.dueldimension.ocg.msg.DuelMessage;
import de.cas_ual_ty.dueldimension.ocg.msg.Responses;
import de.cas_ual_ty.dueldimension.ocg.query.BoardObserver;
import de.cas_ual_ty.dueldimension.ocg.query.BoardState;
import de.cas_ual_ty.dueldimension.ocg.query.CardView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

/**
 * Layer 2: deck-agnostic competence. Plays by evaluating the board rather
 * than by knowing any particular card — summon the biggest body, attack only
 * when the trade is winning, set what can't attack, keep backrow up.
 * <p>
 * Everything is judged from {@link BoardState#observe}, the honest view, so
 * this bot cannot see face-down cards or the opponent's hand. Personality
 * weights and deliberate misplays (Phase 4) modulate the same scores.
 * <p>
 * Falls back to {@link RandomBot} behaviour for prompts where a generic rule
 * would be worse than a coin flip — a legal answer always comes back.
 */
public class HeuristicBot implements ResponseSource
{
    private final RandomBot fallback;
    private final OcgDuel.CardProvider cards;
    private final Random random;

    private BoardObserver board;
    private int player;

    /**
     * The role of the effect just activated, so the selection that follows it
     * knows which way to point. Activation was already role-driven; TARGETING
     * was not, and "always prefer the opponent's best card" is exactly how a
     * buff like Reinforcements landed on the player's monster.
     */
    private CardRoles.Role pendingTargetRole;

    /** Set when we declare an attack, so the following card selection is read as target choice. */
    private boolean expectingAttackTarget;

    /**
     * The attacking monster's ATK, kept alongside the flag above so the target
     * selection can pick the biggest defender that still dies rather than the
     * smallest. {@code GameAI.OnSelectBattleCmd} sorts defenders by defence
     * power descending and {@code OnSelectAttackTarget} takes the first one
     * the attacker beats, which is the strongest beatable body.
     */
    private int attackerPower;

    /**
     * Ported from {@code Duel.LastChainPlayer} / {@code Duel.LastSummonPlayer}
     * / {@code Duel.Player}, the three facts WindBot's {@code DefaultTrap}
     * needs to tell "the opponent is doing something" from "I am".
     */
    private int turnPlayer = -1;
    private int lastChainPlayer = -1;
    private int lastSummonPlayer = -1;

    /**
     * How often each card has been activated, and the ceiling from
     * {@code GameAI.ShouldExecute}: {@code if (_activatedCards[card.Id] >= 9)
     * return false;}. A continuous card whose activation stays legal after it
     * resolves would otherwise be fired forever.
     */
    private final java.util.Map<Integer, Integer> activatedCards = new java.util.HashMap<>();
    private static final int ACTIVATION_LIMIT = 9;

    public HeuristicBot(long seed, OcgDuel.CardProvider cards, Collection<OcgCard> declarableIndex)
    {
        this.cards = cards;
        random = new Random(seed);
        fallback = new RandomBot(seed ^ 0x5DEECE66DL, declarableIndex);
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
        // Track who is acting. The core's controller bytes are absolute, and
        // so is our own player index, so these compare directly.
        switch(message.type())
        {
            case OcgConstants.MSG_NEW_TURN ->
            {
                if(DuelMessage.decode(message) instanceof DuelMessage.NewTurn turn)
                {
                    turnPlayer = turn.player();
                }
                // WindBot clears both at the start of a turn; a trap must
                // answer something happening now, not something last turn.
                lastChainPlayer = -1;
                lastSummonPlayer = -1;
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
                if(decoded instanceof DuelMessage.Summoning summon)
                {
                    lastSummonPlayer = summon.card().controller();
                }
                else if(decoded instanceof DuelMessage.SpSummoning summon)
                {
                    lastSummonPlayer = summon.card().controller();
                }
                else if(decoded instanceof DuelMessage.FlipSummoning summon)
                {
                    lastSummonPlayer = summon.card().controller();
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

    @Override
    public byte[] respond(RawMessage prompt)
    {
        DuelMessage decoded = DuelMessage.decode(prompt);
        byte[] answer = null;

        if(decoded instanceof DuelMessage.SelectIdleCmd idle)
        {
            answer = idle(idle);
        }
        else if(decoded instanceof DuelMessage.SelectBattleCmd battle)
        {
            answer = battle(battle);
        }
        else if(decoded instanceof DuelMessage.SelectPosition position)
        {
            answer = position(position);
        }
        else if(decoded instanceof DuelMessage.SelectCard select)
        {
            answer = selectCard(select);
        }
        else if(decoded instanceof DuelMessage.SelectTribute tribute)
        {
            answer = tribute(tribute);
        }
        else if(decoded instanceof DuelMessage.SelectSum sum)
        {
            answer = sum(sum);
        }
        else if(decoded instanceof DuelMessage.SelectChain chain)
        {
            answer = chain(chain);
        }
        else if(decoded instanceof DuelMessage.SelectEffectYesNo effect)
        {
            // "Do you want to use this card's effect?" -- which names the card,
            // so it is an activation decision and goes through the same table
            // as every other one. WindBot's OnSelectEffectYn is the same shape:
            // it walks its executors and returns false when none matches.
            //
            // Answering an unconditional yes here was a hole straight through
            // the roles table: a trap the bot had correctly declined to
            // activate from the main phase was activated anyway the moment the
            // core asked about it politely.
            answer = activationScore(board.observe(), effect.code(), true) >= 0
                ? Responses.yes() : Responses.no();
        }
        else if(decoded instanceof DuelMessage.SelectYesNo)
        {
            // No card is named, so there is nothing to judge; this is the
            // asked player's own option and taking it beats a coin flip.
            answer = Responses.yes();
        }

        // Chains, yes/no, options, announces — and anything the rules above
        // had no opinion on — fall through to a legal random answer.
        return answer != null ? answer : fallback.respond(prompt);
    }

    // ---- main phase ----

    private byte[] idle(DuelMessage.SelectIdleCmd idle)
    {
        BoardState state = board.observe();
        double bestScore = Double.NEGATIVE_INFINITY;
        byte[] best = null;

        // Scores are tiered so that once-per-turn plays (summon/set) happen
        // before entering battle: the idle prompt repeats, and the used-up
        // options simply disappear from the next one.
        int threat = state.opponent().strongestAttacker();

        // Special summons don't consume the normal summon, so they come first.
        for(int i = 0; i < idle.spSummonable().size(); i++)
        {
            OcgCard card = cards.get(idle.spSummonable().get(i).code());
            double score = 300 + attackOf(card) / 100.0 + (attackOf(card) > threat ? 150 : -100);
            if(score > bestScore)
            {
                bestScore = score;
                best = Responses.idleSpSummon(i);
            }
        }

        for(int i = 0; i < idle.summonable().size(); i++)
        {
            OcgCard card = cards.get(idle.summonable().get(i).code());
            // A flip monster's whole point is its flip effect: summoning it
            // face up throws that away. It appears in the settable list too,
            // where it gets the boost this branch is skipping it for.
            if(card != null && (card.type() & OcgConstants.TYPE_FLIP) != 0)
            {
                continue;
            }
            double score = 250 + attackOf(card) / 100.0 + (attackOf(card) > threat ? 150 : -100);
            if(score > bestScore)
            {
                bestScore = score;
                best = Responses.idleSummon(i);
            }
        }

        // Setting is for walls: a monster whose DEF holds off the current
        // threat. Setting a body that could instead be attacking (or become
        // tribute fodder) just hands over the initiative.
        for(int i = 0; i < idle.monsterSettable().size(); i++)
        {
            OcgCard card = cards.get(idle.monsterSettable().get(i).code());
            if(card == null)
            {
                continue;
            }
            boolean wall = threat > 0 && defenseOf(card) > threat && defenseOf(card) > attackOf(card);
            boolean flip = (card.type() & OcgConstants.TYPE_FLIP) != 0;
            // A flip monster set is a summon-grade play, not a reluctant wall.
            double score = 120 + defenseOf(card) / 100.0 + (wall ? 120 : 0) + (flip ? 180 : 0);
            if(score > bestScore)
            {
                bestScore = score;
                best = Responses.idleMonsterSet(i);
            }
        }

        // Activation is scored by what the card is FOR against this board,
        // via the curated CardRoles table. The flat score this replaces fired
        // any legal spell immediately -- including a board wipe whose only
        // victims were the bot's own monsters.
        CardRoles.Role bestRole = null;
        int bestCode = 0;
        for(int i = 0; i < idle.activatable().size(); i++)
        {
            int code = idle.activatable().get(i).code();
            double score = activationScore(state, code, false);
            // A negative score means "never", not "only if nothing else is
            // going on". bestScore starts at -Infinity, so without this test a
            // -1 still counted as the best available play whenever the board
            // offered nothing at all -- which is exactly the position a bot
            // holding one dead card is in, and exactly when it fired it.
            if(score >= 0 && score > bestScore)
            {
                bestScore = score;
                bestRole = CardRoles.of(code);
                bestCode = code;
                best = Responses.idleActivate(i);
            }
        }

        // Backrow discipline: set spells/traps rather than sitting on them.
        // Anything the roles table says to hold back is still worth SETTING --
        // that is how a reactive trap gets into position to answer the
        // opponent later, and it is why declining to activate costs nothing.
        for(int i = 0; i < idle.spellSettable().size(); i++)
        {
            double score = 110;
            if(score > bestScore)
            {
                bestScore = score;
                bestRole = null; // this play targets nothing; see pendingTargetRole
                bestCode = 0;
                best = Responses.idleSpellSet(i);
            }
        }

        // Repositioning is only ever worth it to duck under a threat we
        // cannot beat: turning our own attackers face-up-defense otherwise
        // just surrenders the initiative (and it is a per-turn free action,
        // so a naive score here dominates every other choice).
        for(int i = 0; i < idle.repositionable().size(); i++)
        {
            DuelMessage.IdleOption option = idle.repositionable().get(i);
            CardView current = state.self().monsterAt(option.sequence());
            OcgCard card = cards.get(option.code());
            if(current == null || card == null || !current.isAttackPosition())
            {
                continue; // already defending, or we cannot see what it is
            }
            boolean outclassed = threat > 0 && attackOf(card) <= threat;
            if(!outclassed || defenseOf(card) <= attackOf(card))
            {
                continue;
            }
            double score = 90;
            if(score > bestScore)
            {
                bestScore = score;
                bestRole = null;
                bestCode = 0;
                best = Responses.idleReposition(i);
            }
        }

        if(best != null)
        {
            pendingTargetRole = bestRole;
            if(bestCode != 0)
            {
                activatedCards.merge(bestCode, 1, Integer::sum);
            }
            return best;
        }

        // Nothing left worth doing in the main phase. WindBot's fallback at
        // the end of GameAI.OnSelectIdleCmd is:
        //
        //     if (main.CanBattlePhase && Duel.Fields[0].HasAttackingMonster())
        //         return new MainPhaseAction(MainAction.ToBattlePhase);
        //     return new MainPhaseAction(MainAction.ToEndPhase);
        //
        // Note what it does NOT ask: whether an attack would be good. That
        // judgement belongs to the battle phase, which can see the defenders
        // and can still activate battle-phase cards. Gating entry on "do I
        // already have a winning attack" -- the rule this replaces -- is what
        // made the bot buff a monster in Main 1 and then end its turn without
        // swinging, wasting the card it had just spent.
        if(idle.toBattle() && state.self().strongestAttacker() > 0)
        {
            return Responses.idleToBattle();
        }
        return idle.toEnd() ? Responses.idleToEnd()
            : idle.toBattle() ? Responses.idleToBattle() : null;
    }

    /**
     * How much this board wants this card activated; negative means "keep it".
     * Legality is the engine's business — this is the judgement the engine
     * cannot supply, because effects are opaque scripts to the host.
     */
    private double activationScore(BoardState state, int code, boolean respondingToOpponent)
    {
        // GameAI.ShouldExecute refuses a card it has already fired nine times,
        // which is the only thing standing between a continuous card and an
        // infinite main phase.
        if(activatedCards.getOrDefault(code, 0) >= ACTIVATION_LIMIT)
        {
            return -1;
        }

        int oppCount = state.opponent().monsterCount();
        int oppBest = state.opponent().strongestFaceUpAttack();

        return switch(CardRoles.of(code))
        {
            // WindBot: protected bool DefaultDarkHole() { return
            // Util.IsOneEnemyBetter(); } -- a symmetric wipe is worth it
            // exactly when they have something we cannot handle. With only our
            // own monsters out this is false, so the board-wipe-into-itself
            // that started this method never happens.
            case WIPES_ALL_MONSTERS -> isOneEnemyBetter(state) ? 300 : -1;
            case HITS_OPPONENT_MONSTER -> oppCount == 0 ? -1 : 260 + oppBest / 10.0;
            case HITS_BACKROW ->
                backrow(state.opponent()) == 0 ? -1 : 220 + backrow(state.opponent()) * 15;
            case BUFFS_OWN_MONSTER -> buffScore(state);
            case REVIVES_FROM_GRAVE ->
            {
                int best = Math.max(bestGraveAttack(state.self()), bestGraveAttack(state.opponent()));
                yield best >= 1200 ? 240 + best / 10.0 : -1;
            }
            // "500 damage for each monster they control": worth nothing
            // against an empty board, and better the wider theirs is.
            case BURNS_OPPONENT -> oppCount == 0 ? -1 : 230 + oppCount * 20;
            // Never wrong, never urgent: below every play that touches the
            // board, so it fills a turn rather than replacing a real one.
            case GAINS_LIFE -> 60;
            // WindBot: protected bool DefaultField() { return Bot.SpellZone[5] == null; }
            case FIELD_SPELL -> fieldZoneEmpty(state) ? 130 : -1;
            // The engine only offers these when they can resolve, so its
            // legality check IS the condition WindBot would hand-write.
            case SUMMONS_MONSTER -> 280;
            // WindBot: protected bool DefaultTrap() { return (Duel.LastChainPlayer
            // == -1 && Duel.LastSummonPlayer != 0) || Duel.LastChainPlayer == 1; }
            // An answer is worth a card only against something the opponent is
            // doing; on our own turn, with nothing on the stack, it is a
            // wasted card. Fired from a chain window only.
            case REACTIVE -> respondingToOpponent && opponentIsActing() ? 250 : -1;
            // Held. These cost more than they take on any board we can read,
            // and no legality check will ever say so.
            case SELF_HARMING -> -1;
            case UTILITY -> unclassifiedScore(code);
        };
    }

    /**
     * What to do with a card the table says nothing about.
     * <p>
     * For a spell or trap the answer is nothing: firing it spends a card on an
     * effect the bot cannot evaluate, which is the whole reason WindBot
     * requires a registered rule before it activates anything.
     * <p>
     * A monster's own effect is a different trade. The body stays on the
     * field, so using it costs no card — declining one is a real loss, and
     * flip and ignition effects are most of what the starter-deck monsters do.
     * These stay available.
     */
    private double unclassifiedScore(int code)
    {
        OcgCard card = cards.get(code);
        return card != null && (card.type() & OcgConstants.TYPE_MONSTER) != 0 ? 200 : -1;
    }

    /**
     * WindBot's {@code AIUtil.IsOneEnemyBetter}: is any enemy monster's power
     * above the best of ours? Power is ATK for an attacker and DEF for a
     * defender, matching {@code ClientCard.GetDefensePower}.
     */
    private boolean isOneEnemyBetter(BoardState state)
    {
        int ours = bestPower(state.self());
        for(CardView card : state.opponent().monsters())
        {
            if(card != null && power(card) > ours)
            {
                return true;
            }
        }
        return false;
    }

    private static int power(CardView card)
    {
        return card.isAttackPosition() ? card.attack() : card.defense();
    }

    private static int bestPower(BoardState.PlayerBoard side)
    {
        int best = -1;
        for(CardView card : side.monsters())
        {
            if(card != null)
            {
                best = Math.max(best, power(card));
            }
        }
        return best;
    }

    /**
     * A buff is only worth a card when it can change something.
     * <p>
     * The old rule was "we have a face-up monster", which spent an equip on a
     * board where every trade was already decided — the wasted-card complaint.
     * We cannot read the size of the boost (it lives in a Lua script), but we
     * do not need to: what matters is whether there is a battle whose outcome
     * is still open. If their board is empty the extra ATK is extra damage; if
     * something out there already beats or ties our best attacker, the boost
     * has a job. When we already beat everything, it has none.
     */
    private double buffScore(BoardState state)
    {
        int ours = state.self().strongestAttacker();
        if(ours <= 0)
        {
            return -1; // nothing face-up in attack position to carry it
        }
        if(state.opponent().monsterCount() == 0)
        {
            return 210; // straight to life points, so every point counts
        }
        for(CardView card : state.opponent().monsters())
        {
            if(card != null && (!card.isFaceUp() || power(card) >= ours))
            {
                return 210;
            }
        }
        return -1;
    }

    /** The field zone is spell sequence 5, per the core's zone numbering. */
    private boolean fieldZoneEmpty(BoardState state)
    {
        List<CardView> spells = state.self().spells();
        return spells.size() <= 5 || spells.get(5) == null;
    }

    /** {@code Duel.LastChainPlayer == 1}, or a summon that was not ours. */
    private boolean opponentIsActing()
    {
        if(lastChainPlayer >= 0)
        {
            return lastChainPlayer != player;
        }
        return lastSummonPlayer >= 0 ? lastSummonPlayer != player : turnPlayer != player;
    }

    private static int backrow(BoardState.PlayerBoard side)
    {
        int count = 0;
        for(CardView card : side.spells())
        {
            if(card != null)
            {
                count++;
            }
        }
        return count;
    }

    private int bestGraveAttack(BoardState.PlayerBoard side)
    {
        int best = 0;
        for(CardView card : side.grave())
        {
            OcgCard data = card == null ? null : cards.get(card.code());
            if(data != null && (data.type() & OcgConstants.TYPE_MONSTER) != 0)
            {
                best = Math.max(best, attackOf(data));
            }
        }
        return best;
    }

    // ---- battle phase ----

    private byte[] battle(DuelMessage.SelectBattleCmd battle)
    {
        BoardState state = board.observe();
        double bestScore = 0;
        byte[] best = null;
        int bestPower = 0;

        for(int i = 0; i < battle.attackable().size(); i++)
        {
            DuelMessage.AttackOption option = battle.attackable().get(i);
            int attack = attackOf(cards.get(option.code()));
            double score = attackValue(state, option, attack);
            if(score > bestScore)
            {
                bestScore = score;
                bestPower = attack;
                best = Responses.battleAttack(i);
            }
        }

        if(best != null)
        {
            expectingAttackTarget = true;
            attackerPower = bestPower;
            return best;
        }
        if(battle.toMain2())
        {
            return Responses.battleToMain2();
        }
        return battle.toEnd() ? Responses.battleToEnd() : null;
    }

    /** Positive only when the attack is worth making. */
    private double attackValue(BoardState state, DuelMessage.AttackOption option, int attack)
    {
        if(option.canDirect() || state.opponent().monsterCount() == 0)
        {
            return 1000 + attack / 100.0; // damage straight to life points
        }

        double best = 0;
        for(CardView defender : state.opponent().monsters())
        {
            if(defender == null)
            {
                continue;
            }
            if(!defender.isFaceUp())
            {
                // Unknown DEF, and guessing wrong costs life points: hitting a
                // set monster whose DEF exceeds our ATK deals the difference
                // straight to us. Only swing with something big enough that
                // most walls fall to it.
                best = Math.max(best, attack >= 1800 ? 200 + attack / 100.0 : 0);
                continue;
            }
            if(defender.isAttackPosition())
            {
                if(attack > defender.attack())
                {
                    best = Math.max(best, 500 + (attack - defender.attack()) / 100.0);
                }
                else if(attack == defender.attack())
                {
                    best = Math.max(best, 10); // mutual destruction: rarely worth it
                }
            }
            else if(attack > defender.defense())
            {
                best = Math.max(best, 300);
            }
        }
        return best;
    }

    // ---- selections ----

    private byte[] position(DuelMessage.SelectPosition position)
    {
        OcgCard card = cards.get(position.code());
        // A flip monster wants to be face down whenever the core allows it.
        if(card != null && (card.type() & OcgConstants.TYPE_FLIP) != 0
            && (position.positions() & OcgConstants.POS_FACEDOWN_DEFENSE) != 0)
        {
            return Responses.position(OcgConstants.POS_FACEDOWN_DEFENSE);
        }
        boolean preferDefense = card != null && defenseOf(card) > attackOf(card);
        int wanted = preferDefense ? OcgConstants.POS_FACEUP_DEFENSE : OcgConstants.POS_FACEUP_ATTACK;
        if((position.positions() & wanted) != 0)
        {
            return Responses.position(wanted);
        }
        return firstLegalPosition(position);
    }

    private byte[] firstLegalPosition(DuelMessage.SelectPosition position)
    {
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
     * Without reading the effect's intent we cannot know whether a selection
     * is a cost or a target, but ownership is a good proxy: our own cards are
     * almost always being spent (tribute, discard, material), so give up the
     * least; the opponent's are almost always being removed, so take the best.
     */
    private byte[] selectCard(DuelMessage.SelectCard select)
    {
        boolean asAttackTarget = expectingAttackTarget;
        int power = attackerPower;
        expectingAttackTarget = false;
        attackerPower = 0;
        CardRoles.Role targetRole = pendingTargetRole;
        pendingTargetRole = null;

        int count = Math.max(select.min(), 1);
        if(select.cards().isEmpty() || count > select.cards().size())
        {
            return null;
        }

        List<Integer> order = new ArrayList<>();
        for(int i = 0; i < select.cards().size(); i++)
        {
            order.add(i);
        }
        order.sort((left, right) -> Double.compare(
            selectionPriority(select, right, asAttackTarget, power, targetRole),
            selectionPriority(select, left, asAttackTarget, power, targetRole)));

        int[] chosen = new int[count];
        for(int i = 0; i < count; i++)
        {
            chosen[i] = order.get(i);
        }
        return Responses.selectCards(chosen);
    }

    /** Higher means "pick this one first". */
    private double selectionPriority(DuelMessage.SelectCard select, int index, boolean asAttackTarget,
        int power, CardRoles.Role targetRole)
    {
        DuelMessage.SelectableCard card = select.cards().get(index);
        int attack = attackOf(cards.get(card.code()));
        boolean mine = card.loc() != null && card.loc().controller() == player;

        if(asAttackTarget)
        {
            // GameAI sorts defenders by power descending and OnSelectAttackTarget
            // takes the first the attacker beats, i.e. the biggest body that
            // still dies. Picking the cheapest instead -- the rule this
            // replaces -- left their best monster standing and burned the
            // attack on something that was no threat.
            return power > attack ? 10000 + attack : -attack;
        }
        if(targetRole == CardRoles.Role.BUFFS_OWN_MONSTER)
        {
            // What we just activated HELPS its target, so it belongs on our
            // best monster and never on theirs.
            return mine ? 10000 + attack : -10000 - attack;
        }
        if(mine)
        {
            // A cost: spend the weakest, and rank all of our cards below any
            // of the opponent's.
            return -10000 - attack;
        }
        // A target: take out their biggest threat.
        return attack;
    }

    /**
     * Without knowing what an effect does, a coin flip on every chain window
     * is worse than a policy: decline unless the core forces a choice. Real
     * chain evaluation needs card roles, which arrive with duelist profiles.
     */
    private byte[] chain(DuelMessage.SelectChain chain)
    {
        if(chain.chains().isEmpty())
        {
            return chain.forced() ? null : Responses.chainDecline();
        }
        if(chain.forced())
        {
            return Responses.chain(0);
        }
        // Judge a chain window by what the card is FOR, the same way the main
        // phase judges an activation. Declining everything unreactively -- the
        // old policy -- meant a set trap never fired at the moment it was set
        // for, which is most of what a trap is.
        BoardState state = board.observe();
        double bestScore = CHAIN_THRESHOLD;
        int best = -1;
        int bestCode = 0;
        CardRoles.Role bestRole = null;
        for(int i = 0; i < chain.chains().size(); i++)
        {
            int code = chain.chains().get(i).code();
            // A chain window is by definition a response, so REACTIVE cards
            // become available here and only here.
            double score = activationScore(state, code, true);
            if(score > bestScore)
            {
                bestScore = score;
                bestRole = CardRoles.of(code);
                bestCode = code;
                best = i;
            }
        }
        if(best < 0)
        {
            return Responses.chainDecline();
        }
        pendingTargetRole = bestRole;
        activatedCards.merge(bestCode, 1, Integer::sum);
        return Responses.chain(best);
    }

    /**
     * How good a response has to look before the bot spends a card on it.
     * An unknown effect scores UTILITY (170) and stays below this, so the
     * cautious default survives: only cards whose role actually fits the board
     * are worth chaining.
     */
    private static final double CHAIN_THRESHOLD = 200;

    /** Give up the least: tribute the weakest monsters that still satisfy the requirement. */
    private byte[] tribute(DuelMessage.SelectTribute tribute)
    {
        List<Integer> order = weakestFirst(tribute.cards().stream()
            .map(card -> attackOf(cards.get(card.code()))).toList());

        List<Integer> chosen = new ArrayList<>();
        int total = 0;
        for(int index : order)
        {
            if(total >= tribute.min() || chosen.size() >= tribute.max())
            {
                break;
            }
            chosen.add(index);
            total += tribute.cards().get(index).releaseParam();
        }
        if(total < tribute.min())
        {
            return null;
        }
        return Responses.selectTribute(chosen.stream().mapToInt(Integer::intValue).toArray());
    }

    /**
     * Material selection has a hard legality constraint (the totals must work
     * out), so search for valid sets and prefer the one that spends the least
     * attack power.
     */
    private byte[] sum(DuelMessage.SelectSum prompt)
    {
        int[] cheapest = null;
        int cheapestCost = Integer.MAX_VALUE;
        for(int attempt = 0; attempt < 24; attempt++)
        {
            int[] candidate = SumSolver.solve(prompt, random);
            if(candidate == null)
            {
                break;
            }
            int cost = 0;
            for(int index : candidate)
            {
                cost += attackOf(cards.get(prompt.selectable().get(index).code()));
            }
            if(cost < cheapestCost)
            {
                cheapestCost = cost;
                cheapest = candidate;
            }
        }
        return cheapest == null ? null : Responses.selectSum(cheapest);
    }

    private List<Integer> weakestFirst(List<Integer> attacks)
    {
        List<Integer> order = new ArrayList<>();
        for(int i = 0; i < attacks.size(); i++)
        {
            order.add(i);
        }
        order.sort((left, right) -> Integer.compare(attacks.get(left), attacks.get(right)));
        return order;
    }

    private int attackOf(OcgCard card)
    {
        return card == null || card.attack() < 0 ? 0 : card.attack();
    }

    private int defenseOf(OcgCard card)
    {
        return card == null || card.defense() < 0 ? 0 : card.defense();
    }
}
