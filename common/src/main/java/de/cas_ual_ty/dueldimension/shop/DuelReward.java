package de.cas_ual_ty.dueldimension.shop;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure Tag-Force-style duel assessment.
 * <p>
 * The engine-facing tracker produces one {@link Metrics} value per player;
 * this class knows nothing about Minecraft, networking, or mutable balances.
 * Keeping the arithmetic here makes every displayed line testable and ensures
 * the menu is an explanation of the amount the server actually awarded.
 */
public final class DuelReward
{
    public enum Outcome
    {
        WIN("Victory"), DRAW("Draw"), LOSS("Defeat");

        private final String label;

        Outcome(String label)
        {
            this.label = label;
        }

        public String label()
        {
            return label;
        }
    }

    /** One visible row of the post-duel calculation. */
    public record Line(String id, String label, int amount)
    {
        public Line
        {
            id = id == null ? "" : id;
            label = label == null ? "" : label;
            amount = Math.max(0, amount);
        }
    }

    /**
     * Facts collected from the ordered core stream. Counts cover the whole
     * contest; finish-state values are from its final game.
     */
    public record Metrics(int games, int turns, int finalLifePoints, int maximumLifePoints,
        int finalDeckCount, boolean lifePointsDecreased, int spells, int traps,
        int normalSummons, int flipSummons, int fusionSummons, int ritualSummons,
        int synchroSummons, int xyzSummons, int pendulumSummons, int linkSummons,
        int otherSpecialSummons, int maximumChain, int maximumAttack, int maximumDamage,
        int favorableCoins, boolean filledMonsterZones, boolean filledSpellTrapZones,
        boolean reversalFinish, boolean opponentTurnFinish, boolean exactZeroFinish,
        boolean forfeit)
    {
        public Metrics
        {
            games = Math.max(1, games);
            turns = Math.max(0, turns);
            finalLifePoints = Math.max(0, finalLifePoints);
            maximumLifePoints = Math.max(finalLifePoints, maximumLifePoints);
            finalDeckCount = Math.max(0, finalDeckCount);
        }

        public int specialSummons()
        {
            return fusionSummons + ritualSummons + synchroSummons + xyzSummons
                + pendulumSummons + linkSummons + otherSpecialSummons;
        }
    }

    /** The immutable result sent to the client after the balance is committed. */
    public record Breakdown(Outcome outcome, List<Line> lines, int total)
    {
        public Breakdown
        {
            outcome = outcome == null ? Outcome.DRAW : outcome;
            lines = lines == null ? List.of() : List.copyOf(lines);
            total = Math.max(0, total);
        }
    }

    private DuelReward()
    {
    }

    /**
     * Evaluates the Duel Dimension preset. PvP uses scale 1; NPC duels use a
     * smaller scale but the same transparent rules.
     */
    public static Breakdown calculate(Outcome outcome, Metrics metrics, double scale)
    {
        List<Line> lines = new ArrayList<>();
        Award award = new Award(lines, Math.max(0D, scale));

        switch(outcome)
        {
            case WIN -> award.add("outcome.win", "Victory bonus", 300);
            case DRAW -> award.add("outcome.draw", "Draw bonus", 120);
            case LOSS -> award.add("outcome.loss", "Duel completed", 75);
        }

        // The durable participation award. It is capped so deliberately
        // stalling a decided duel never becomes the best source of currency.
        award.add("turns", "Turn bonus (" + metrics.turns() + ")",
            Math.min(100, metrics.turns() * 5));

        // A concession is a valid outcome, but not a shortcut to the large
        // "used no cards" assessment bonuses. Both players receive the result
        // and whatever turn participation actually elapsed, then stop here.
        if(metrics.forfeit())
        {
            int total = lines.stream().mapToInt(Line::amount).sum();
            return new Breakdown(outcome, lines, total);
        }

        if(outcome == Outcome.WIN)
        {
            if(metrics.games() > 1)
            {
                award.add("match", "Match victory", 100);
            }
            if(metrics.turns() <= 5)
            {
                award.add("quick", "Quick finish", 75);
            }
            if(metrics.reversalFinish())
            {
                award.add("reversal", "Reversal finish", 100);
            }
            if(metrics.opponentTurnFinish())
            {
                award.add("opponent_turn", "Opponent-turn finish", 75);
            }
            if(metrics.finalLifePoints() <= 1000)
            {
                award.add("low_lp", "Low LP finish", 75);
            }
            if(metrics.finalLifePoints() <= 100)
            {
                award.add("critical_lp", "Critical LP finish", 150);
            }
            if(!metrics.lifePointsDecreased())
            {
                award.add("no_lp_loss", "No LP lost", 100);
            }
            if(metrics.maximumLifePoints() >= 20000)
            {
                award.add("lp_20000", "Reached 20,000 LP", 100);
            }
            if(metrics.finalLifePoints() == 5730)
            {
                award.add("konami", "Konami bonus", 573);
            }
            if(metrics.finalDeckCount() <= 5)
            {
                award.add("low_deck", "Low deck finish", 75);
            }
            if(metrics.finalDeckCount() == 0)
            {
                award.add("zero_deck", "Zero deck finish", 200);
            }
            if(metrics.exactZeroFinish())
            {
                award.add("exact_zero", "Exact 0 LP finish", 100);
            }
        }

        counted(award, "spells", "Spell activations", metrics.spells(), 3, 90);
        counted(award, "traps", "Trap activations", metrics.traps(), 5, 100);
        if(metrics.spells() == 0)
        {
            award.add("no_spells", "No spells used", 100);
        }
        if(metrics.traps() == 0)
        {
            award.add("no_traps", "No traps used", 100);
        }

        counted(award, "normal", "Normal Summons", metrics.normalSummons(), 5, 60);
        counted(award, "flip", "Flip Summons", metrics.flipSummons(), 10, 80);
        counted(award, "fusion", "Fusion Summons", metrics.fusionSummons(), 25, 150);
        counted(award, "ritual", "Ritual Summons", metrics.ritualSummons(), 50, 200);
        counted(award, "synchro", "Synchro Summons", metrics.synchroSummons(), 20, 150);
        counted(award, "xyz", "Xyz Summons", metrics.xyzSummons(), 20, 150);
        counted(award, "pendulum", "Pendulum Summons", metrics.pendulumSummons(), 25, 150);
        counted(award, "link", "Link Summons", metrics.linkSummons(), 20, 150);
        counted(award, "special", "Other Special Summons", metrics.otherSpecialSummons(), 5, 75);
        if(metrics.specialSummons() == 0)
        {
            award.add("no_special", "No Special Summons", 150);
        }

        if(metrics.maximumChain() >= 3)
        {
            award.add("chain", "Maximum chain: " + metrics.maximumChain(),
                Math.min(100, metrics.maximumChain() * 10));
        }
        if(metrics.maximumAttack() >= 3000)
        {
            award.add("max_atk", "Maximum ATK: " + metrics.maximumAttack(),
                Math.min(150, metrics.maximumAttack() / 500 * 10));
        }
        if(metrics.maximumDamage() >= 3000)
        {
            award.add("max_damage", "Maximum damage: " + metrics.maximumDamage(),
                Math.min(150, metrics.maximumDamage() / 500 * 10));
        }
        counted(award, "lucky", "Favorable coin tosses", metrics.favorableCoins(), 25, 150);
        if(metrics.filledMonsterZones())
        {
            award.add("full_monsters", "Filled all monster zones", 100);
        }
        if(metrics.filledSpellTrapZones())
        {
            award.add("full_spells", "Filled all Spell/Trap zones", 100);
        }

        int total = lines.stream().mapToInt(Line::amount).sum();
        return new Breakdown(outcome, lines, total);
    }

    private static void counted(Award award, String id, String label, int count,
        int each, int cap)
    {
        if(count > 0)
        {
            award.add(id, label + " (" + count + ")", Math.min(cap, count * each));
        }
    }

    /** Applies the duel-type scale consistently to every visible line. */
    private record Award(List<Line> lines, double scale)
    {
        void add(String id, String label, int base)
        {
            if(base <= 0 || scale <= 0)
            {
                return;
            }
            int amount = Math.max(1, (int)Math.round(base * scale));
            lines.add(new Line(id, label, amount));
        }
    }
}
