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

    /** Set when we declare an attack, so the following card selection is read as target choice. */
    private boolean expectingAttackTarget;

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
        else if(decoded instanceof DuelMessage.SelectEffectYesNo || decoded instanceof DuelMessage.SelectYesNo)
        {
            // These ask "do you want to use this?" about the asked player's
            // own option; taking it is the better default than a coin flip.
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
        for(int i = 0; i < idle.activatable().size(); i++)
        {
            double score = activationScore(state, idle.activatable().get(i).code());
            if(score > bestScore)
            {
                bestScore = score;
                best = Responses.idleActivate(i);
            }
        }

        // Backrow discipline: set spells/traps rather than sitting on them.
        for(int i = 0; i < idle.spellSettable().size(); i++)
        {
            double score = 110;
            if(score > bestScore)
            {
                bestScore = score;
                best = Responses.idleSpellSet(i);
            }
        }

        if(idle.toBattle() && hasWinningAttack(state))
        {
            double score = 100;
            if(score > bestScore)
            {
                bestScore = score;
                best = Responses.idleToBattle();
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
                best = Responses.idleReposition(i);
            }
        }

        if(best != null)
        {
            return best;
        }
        return idle.toBattle() ? Responses.idleToBattle()
            : idle.toEnd() ? Responses.idleToEnd() : null;
    }

    /** True if any of our face-up attackers beats something (or the opponent is open). */
    /**
     * How much this board wants this card activated; negative means "keep it".
     * Legality is the engine's business — this is the judgement the engine
     * cannot supply, because effects are opaque scripts to the host.
     */
    private double activationScore(BoardState state, int code)
    {
        int ownCount = state.self().monsterCount();
        int oppCount = state.opponent().monsterCount();
        int ownBest = state.self().strongestFaceUpAttack();
        int oppBest = state.opponent().strongestFaceUpAttack();

        return switch(CardRoles.of(code))
        {
            case WIPES_ALL_MONSTERS ->
            {
                // Both boards die, so its value is the exchange. Firing it
                // while ahead -- or with only our own monsters out, the play
                // that prompted this method -- reads as negative and never
                // beats doing anything else.
                double exchange = (oppCount * 120 + oppBest / 8.0) - (ownCount * 120 + ownBest / 8.0);
                yield exchange > 100 ? 200 + exchange : -1;
            }
            case HITS_OPPONENT_MONSTER ->
                oppCount == 0 ? -1 : 260 + oppBest / 10.0;
            case HITS_BACKROW ->
                backrow(state.opponent()) == 0 ? -1 : 220 + backrow(state.opponent()) * 15;
            case BUFFS_OWN_MONSTER ->
                hasFaceUpMonster(state) ? 210 : -1;
            case REVIVES_FROM_GRAVE ->
            {
                int best = Math.max(bestGraveAttack(state.self()), bestGraveAttack(state.opponent()));
                yield best >= 1200 ? 240 + best / 10.0 : -1;
            }
            case UTILITY -> 170;
        };
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

    private boolean hasFaceUpMonster(BoardState state)
    {
        for(CardView card : state.self().monsters())
        {
            if(card != null && card.isFaceUp())
            {
                return true;
            }
        }
        return false;
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

    private boolean hasWinningAttack(BoardState state)
    {
        int ours = state.self().strongestAttacker();
        if(ours <= 0)
        {
            return false;
        }
        if(state.opponent().monsterCount() == 0)
        {
            return true; // direct attack
        }
        for(CardView card : state.opponent().monsters())
        {
            if(card == null)
            {
                continue;
            }
            if(!card.isFaceUp() || ours > card.attack())
            {
                return true;
            }
        }
        return false;
    }

    // ---- battle phase ----

    private byte[] battle(DuelMessage.SelectBattleCmd battle)
    {
        BoardState state = board.observe();
        double bestScore = 0;
        byte[] best = null;

        for(int i = 0; i < battle.attackable().size(); i++)
        {
            DuelMessage.AttackOption option = battle.attackable().get(i);
            int attack = attackOf(cards.get(option.code()));
            double score = attackValue(state, option, attack);
            if(score > bestScore)
            {
                bestScore = score;
                best = Responses.battleAttack(i);
            }
        }

        if(best != null)
        {
            expectingAttackTarget = true;
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
        expectingAttackTarget = false;

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
            selectionPriority(select, right, asAttackTarget),
            selectionPriority(select, left, asAttackTarget)));

        int[] chosen = new int[count];
        for(int i = 0; i < count; i++)
        {
            chosen[i] = order.get(i);
        }
        return Responses.selectCards(chosen);
    }

    /** Higher means "pick this one first". */
    private double selectionPriority(DuelMessage.SelectCard select, int index, boolean asAttackTarget)
    {
        DuelMessage.SelectableCard card = select.cards().get(index);
        int attack = attackOf(cards.get(card.code()));
        boolean mine = card.loc() != null && card.loc().controller() == player;

        if(asAttackTarget)
        {
            // Attack the cheapest defender that still dies.
            return -attack;
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
        return chain.forced() ? Responses.chain(0) : Responses.chainDecline();
    }

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
