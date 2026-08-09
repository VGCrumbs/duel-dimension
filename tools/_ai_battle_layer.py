"""Port the battle-phase decision layer, and fix one undeclared parity break.

Every item verified against the vendored sources first, because a survey that
cites files is not the same as files that say what it claims:
  ExecutorBase/Game/AI/DefaultExecutor.cs  (OnSelectBattleReplay, :370, :1207)
  ExecutorBase/Game/GameAI.cs              (:289 IsLastAttacker)
  ygopro-core/processor.cpp                (:2200 SelectYesNo(turn_player, 30))

1. BATTLE REPLAY. The core offers a replay whenever the defender leaves the
   field mid battle step, and the mod answered it with Executor.onSelectYesNo,
   whose base is `return true`. So the bot always re-swung, then picked its new
   target through the terminal fallback -- ramming whichever defender the core
   happened to list first. The reference has a dedicated hook that re-runs the
   target choice and declines when nothing is worth hitting.

2. IsLastAttacker. The reference attacks on `>` OR on `>=` when this is the last
   attacker and the defender is in attack position -- an even trade, taken
   because passing leaves the opponent to take it on their own terms. The mod
   kept only the strict `>`, and its javadoc justified that by saying the flag
   "needs per-attack state the core does not expose to a host". That is wrong:
   GameAI.cs:289 sets it from the position in the host's OWN sorted attacker
   list, which ExecutorBot already builds.

3. MONSTER REPOS. An undeclared break, not a documented deviation. The
   reference (DefaultExecutor.cs:1207) is
     Card.Attack >= Card.Defense || Card.Attack >= Util.GetBestPower(Enemy)
   with no phase gate at all. The mod had `>` and an invented PHASE_MAIN1 gate.
   The `>` alone means any ATK==DEF monster -- pervasive on this card era --
   never stands up again once it is set.
"""
import io

def sub(path, old, new, label):
    s = io.open(path, encoding="utf-8").read()
    if new in s:
        print("  skip (already applied):", label)
        return
    assert old in s, "anchor missing: " + label
    io.open(path, "w", encoding="utf-8", newline="\n").write(s.replace(old, new, 1))
    print("  ok:", label)

E = "src/main/java/de/cas_ual_ty/dueldimension/ocg/bot/executor/Executor.java"
D = "src/main/java/de/cas_ual_ty/dueldimension/ocg/bot/executor/DefaultExecutor.java"
B = "src/main/java/de/cas_ual_ty/dueldimension/ocg/bot/executor/ExecutorBot.java"
C = "src/main/java/de/cas_ual_ty/dueldimension/ocg/bot/executor/BotCard.java"

# ---------- 3. repos parity ----------
sub(D, """        return self.isDefense() && !enemyBetter
            && (self.attack() > self.defense()
                || (phase() == OcgConstants.PHASE_MAIN1 && self.attack() >= util.getBestPower(enemy())));""",
    """        // DefaultExecutor.cs:1207, verbatim:
        //   Card.Attack >= Card.Defense || Card.Attack >= Util.GetBestPower(Enemy)
        // Both comparisons are >=, and there is no phase gate. This tree had >
        // and a PHASE_MAIN1 clause that appears nowhere in the reference, which
        // meant an ATK==DEF monster -- most of a starter deck -- could be set
        // once and never stand up again, and a defender that came to out-power
        // the enemy board could not re-arm in Main Phase 2, right when the
        // board had just changed in its favour.
        return self.isDefense() && !enemyBetter
            && (self.attack() >= self.defense()
                || self.attack() >= util.getBestPower(enemy()));""", "repos >= and no phase gate")

# ---------- 2. IsLastAttacker ----------
sub(C, """    public int code()""",
    """    /**
     * {@code ClientCard.IsLastAttacker}: whether this is the last monster the
     * host will offer as an attacker this battle phase.
     * <p>
     * Set by the host from the position in its own sorted attacker list
     * (GameAI.cs:289), not by the engine — which is why it is a plain field.
     */
    public boolean isLastAttacker;

    public int code()""", "BotCard.isLastAttacker")

sub(D, """            if(attacker.realPower > defender.realPower)
            {
                return defender;
            }""",
    """            // DefaultExecutor.cs:370, both halves:
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
            }""", "even-trade clause")

# ---------- 1. battle replay ----------
sub(E, """    /** {@code OnSelectYesNo}: base returns true. */""",
    """    /**
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

    /** {@code OnSelectYesNo}: base returns true. */""", "Executor.onSelectBattleReplay")

sub(D, """    public BotCard onSelectAttackTarget(BotCard attacker, List<BotCard> defenders)""",
    """    /**
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

    public BotCard onSelectAttackTarget(BotCard attacker, List<BotCard> defenders)""",
    "DefaultExecutor.onSelectBattleReplay")

print("done")
