package de.cas_ual_ty.dueldimension.duel.npc;

import de.cas_ual_ty.dueldimension.ocg.CdbCardProvider;
import de.cas_ual_ty.dueldimension.ocg.OcgCard;
import de.cas_ual_ty.dueldimension.ocg.OcgConstants;
import de.cas_ual_ty.dueldimension.ocg.msg.DuelMessage;
import de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot;
import de.cas_ual_ty.dueldimension.shop.DuelReward;

/** Accumulates only facts explicitly present in the ordered OCGCore stream. */
final class DuelRewardTracker
{
    private final CdbCardProvider cards;
    private final Seat[] seats = {new Seat(), new Seat()};
    private int games;
    private int turns;
    private int currentTurn = -1;
    private final int[] turnStartLp = {8000, 8000};
    private final boolean[] reversalFinish = new boolean[2];
    private final boolean[] opponentTurnFinish = new boolean[2];
    private final boolean[] exactZeroFinish = new boolean[2];

    DuelRewardTracker(CdbCardProvider cards)
    {
        this.cards = cards;
    }

    /** Carries contest-wide counters into the next game of a match. */
    void carryFrom(DuelRewardTracker old)
    {
        games = old.games;
        turns = old.turns;
        for(int i = 0; i < 2; i++)
        {
            seats[i].carryFrom(old.seats[i]);
            reversalFinish[i] = old.reversalFinish[i];
            opponentTurnFinish[i] = old.opponentTurnFinish[i];
            exactZeroFinish[i] = old.exactZeroFinish[i];
        }
    }

    void accept(DuelMessage message)
    {
        if(message instanceof DuelMessage.NewTurn turn)
        {
            currentTurn = turn.player();
            turns++;
            turnStartLp[0] = seats[0].lifePoints;
            turnStartLp[1] = seats[1].lifePoints;
        }
        else if(message instanceof DuelMessage.PayLpCost cost)
        {
            decrease(cost.player(), cost.amount(), false);
        }
        else if(message instanceof DuelMessage.Damage damage)
        {
            int target = damage.player();
            int dealer = 1 - target;
            seats[dealer].maximumDamage = Math.max(seats[dealer].maximumDamage, damage.amount());
            exactZeroFinish[dealer] |= seats[target].lifePoints == damage.amount();
            decrease(target, damage.amount(), true);
        }
        else if(message instanceof DuelMessage.Recover recover)
        {
            Seat seat = seats[recover.player()];
            seat.lifePoints += recover.amount();
            seat.maximumLifePoints = Math.max(seat.maximumLifePoints, seat.lifePoints);
        }
        else if(message instanceof DuelMessage.LpUpdate update)
        {
            Seat seat = seats[update.player()];
            seat.lifePointsDecreased |= update.lifePoints() < seat.lifePoints;
            seat.lifePoints = update.lifePoints();
            seat.maximumLifePoints = Math.max(seat.maximumLifePoints, seat.lifePoints);
        }
        else if(message instanceof DuelMessage.Chaining chain)
        {
            Seat seat = seats[chain.triggeringController()];
            seat.maximumChain = Math.max(seat.maximumChain, chain.chainSize());
            OcgCard card = cards.get(chain.code());
            if(card != null && (card.type() & OcgConstants.TYPE_SPELL) != 0)
            {
                seat.spells++;
            }
            else if(card != null && (card.type() & OcgConstants.TYPE_TRAP) != 0)
            {
                seat.traps++;
            }
        }
        else if(message instanceof DuelMessage.Summoning summon)
        {
            seats[summon.card().controller()].normalSummons++;
        }
        else if(message instanceof DuelMessage.FlipSummoning summon)
        {
            seats[summon.card().controller()].flipSummons++;
        }
        else if(message instanceof DuelMessage.SpSummoning summon)
        {
            special(seats[summon.card().controller()], cards.get(summon.code()));
        }
        else if(message instanceof DuelMessage.Battle battle)
        {
            int controller = battle.attacker().controller();
            seats[controller].maximumAttack = Math.max(seats[controller].maximumAttack,
                Math.max(0, battle.attackerAtk()));
        }
        else if(message instanceof DuelMessage.TossCoin coin)
        {
            seats[coin.player()].favorableCoins += (int)coin.results().stream()
                .filter(result -> result == 1).count();
        }
    }

    void observe(BoardSnapshot absoluteSeat0)
    {
        observe(0, absoluteSeat0.self());
        observe(1, absoluteSeat0.opponent());
    }

    void finishGame(int winner)
    {
        games++;
        if(winner == 0 || winner == 1)
        {
            reversalFinish[winner] |= turnStartLp[winner] < turnStartLp[1 - winner];
            opponentTurnFinish[winner] |= currentTurn != -1 && currentTurn != winner;
        }
    }

    DuelReward.Metrics metrics(int seat, boolean forfeit)
    {
        Seat mine = seats[seat];
        return new DuelReward.Metrics(Math.max(1, games), turns, mine.lifePoints,
            mine.maximumLifePoints, mine.deckCount, mine.lifePointsDecreased,
            mine.spells, mine.traps, mine.normalSummons, mine.flipSummons,
            mine.fusionSummons, mine.ritualSummons, mine.synchroSummons,
            mine.xyzSummons, mine.pendulumSummons, mine.linkSummons,
            mine.otherSpecialSummons, mine.maximumChain, mine.maximumAttack,
            mine.maximumDamage, mine.favorableCoins, mine.filledMonsterZones,
            mine.filledSpellTrapZones, reversalFinish[seat], opponentTurnFinish[seat],
            exactZeroFinish[seat], forfeit);
    }

    private void decrease(int player, int amount, boolean damage)
    {
        if(player < 0 || player > 1 || amount <= 0)
        {
            return;
        }
        Seat seat = seats[player];
        seat.lifePointsDecreased = true;
        seat.lifePoints = Math.max(0, seat.lifePoints - amount);
    }

    private static void special(Seat seat, OcgCard card)
    {
        int type = card == null ? 0 : card.type();
        if((type & OcgConstants.TYPE_FUSION) != 0)
        {
            seat.fusionSummons++;
        }
        else if((type & OcgConstants.TYPE_RITUAL) != 0)
        {
            seat.ritualSummons++;
        }
        else if((type & OcgConstants.TYPE_SYNCHRO) != 0)
        {
            seat.synchroSummons++;
        }
        else if((type & OcgConstants.TYPE_XYZ) != 0)
        {
            seat.xyzSummons++;
        }
        else if((type & OcgConstants.TYPE_PENDULUM) != 0)
        {
            seat.pendulumSummons++;
        }
        else if((type & OcgConstants.TYPE_LINK) != 0)
        {
            seat.linkSummons++;
        }
        else
        {
            seat.otherSpecialSummons++;
        }
    }

    private void observe(int index, BoardSnapshot.Side board)
    {
        Seat seat = seats[index];
        seat.lifePointsDecreased |= board.lifePoints() < seat.lifePoints;
        seat.lifePoints = board.lifePoints();
        seat.maximumLifePoints = Math.max(seat.maximumLifePoints, board.lifePoints());
        seat.deckCount = board.deckCount();
        seat.filledMonsterZones |= board.monsters().stream().limit(5)
            .filter(BoardSnapshot.Slot::present).count() >= 5;
        seat.filledSpellTrapZones |= board.spells().stream().limit(5)
            .filter(BoardSnapshot.Slot::present).count() >= 5;
        board.monsters().stream().filter(BoardSnapshot.Slot::present)
            .forEach(card -> seat.maximumAttack = Math.max(seat.maximumAttack,
                Math.max(0, card.attack())));
    }

    private static final class Seat
    {
        int lifePoints = 8000;
        int maximumLifePoints = 8000;
        int deckCount = 40;
        boolean lifePointsDecreased;
        int spells;
        int traps;
        int normalSummons;
        int flipSummons;
        int fusionSummons;
        int ritualSummons;
        int synchroSummons;
        int xyzSummons;
        int pendulumSummons;
        int linkSummons;
        int otherSpecialSummons;
        int maximumChain;
        int maximumAttack;
        int maximumDamage;
        int favorableCoins;
        boolean filledMonsterZones;
        boolean filledSpellTrapZones;

        void carryFrom(Seat old)
        {
            maximumLifePoints = old.maximumLifePoints;
            lifePointsDecreased = old.lifePointsDecreased;
            spells = old.spells;
            traps = old.traps;
            normalSummons = old.normalSummons;
            flipSummons = old.flipSummons;
            fusionSummons = old.fusionSummons;
            ritualSummons = old.ritualSummons;
            synchroSummons = old.synchroSummons;
            xyzSummons = old.xyzSummons;
            pendulumSummons = old.pendulumSummons;
            linkSummons = old.linkSummons;
            otherSpecialSummons = old.otherSpecialSummons;
            maximumChain = old.maximumChain;
            maximumAttack = old.maximumAttack;
            maximumDamage = old.maximumDamage;
            favorableCoins = old.favorableCoins;
            filledMonsterZones = old.filledMonsterZones;
            filledSpellTrapZones = old.filledSpellTrapZones;
        }
    }
}
