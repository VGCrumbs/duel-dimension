"""Wire the battle-replay hook and the last-attacker flag into ExecutorBot."""
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
B = "src/main/java/de/cas_ual_ty/dueldimension/ocg/bot/executor/ExecutorBot.java"

# ---------- Bot.BattlingMonster ----------
sub(E, """    final void setFields(BotField bot, BotField enemy)""",
    """    /** {@code Bot.BattlingMonster}: the monster whose attack is being resolved. */
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

    final void setFields(BotField bot, BotField enemy)""", "Executor.battlingMonster")

# ---------- the flag, from the host's own ordering ----------
sub(B, """        List<BotCard> defenders = util().enemyMonstersByPowerDescending();
        for(int index : order)
        {
            DuelMessage.AttackOption option = battle.attackable().get(index);
            BotCard attacker = cardOf(option.code(), option.controller(), option.location(),
                option.sequence());""",
    """        List<BotCard> defenders = util().enemyMonstersByPowerDescending();
        for(int position = 0; position < order.size(); position++)
        {
            int index = order.get(position);
            DuelMessage.AttackOption option = battle.attackable().get(index);
            BotCard attacker = cardOf(option.code(), option.controller(), option.location(),
                option.sequence());
            // GameAI.cs:289 -- `attacker.IsLastAttacker = (k == attackers.Count - 1)`,
            // read off the host's OWN sorted list, not from the engine. This is
            // that list, sorted the same way.
            attacker.isLastAttacker = position == order.size() - 1;""",
    "isLastAttacker set from the order")

sub(B, """                expectingAttackTarget = true;
                pendingAttacker = attacker;
                return Responses.battleAttack(index);
            }
            if(executor.onSelectAttackTarget(attacker, defenders) != null)
            {
                expectingAttackTarget = true;
                pendingAttacker = attacker;
                return Responses.battleAttack(index);
            }""",
    """                expectingAttackTarget = true;
                pendingAttacker = attacker;
                executor.setBattlingMonster(attacker);
                return Responses.battleAttack(index);
            }
            if(executor.onSelectAttackTarget(attacker, defenders) != null)
            {
                expectingAttackTarget = true;
                pendingAttacker = attacker;
                executor.setBattlingMonster(attacker);
                return Responses.battleAttack(index);
            }""", "battlingMonster set on declare")

# ---------- route the replay prompt ----------
sub(B, """            else if(decoded instanceof DuelMessage.SelectYesNo yesNo)
            {
                answer = executor.onSelectYesNo(yesNo.description()) ? Responses.yes() : Responses.no();
            }""",
    """            else if(decoded instanceof DuelMessage.SelectYesNo yesNo)
            {
                // Description 30 is the attack replay, not a card's question.
                // ygopro-core/processor.cpp emits SelectYesNo(turn_player, 30)
                // when the monster being attacked leaves the field mid battle
                // step; GameBehavior.cs routes that one description to
                // OnSelectBattleReplay rather than OnSelectYesNo, whose base
                // answer is an unconditional yes.
                answer = (yesNo.description() == BATTLE_REPLAY_HINT
                    ? executor.onSelectBattleReplay()
                    : executor.onSelectYesNo(yesNo.description()))
                    ? Responses.yes() : Responses.no();
            }""", "route description 30")

sub(B, """    private boolean expectingAttackTarget;""",
    """    /**
     * The system-string id the core uses to ask "attack again?".
     * <p>
     * ygopro-core/processor.cpp: {@code SelectYesNo(infos.turn_player, 30)} at
     * the replay branch. Suppressed only by DUEL_STORE_ATTACK_REPLAYS, which
     * this mod's flags do not set, so the prompt does reach the bot.
     */
    private static final long BATTLE_REPLAY_HINT = 30L;

    private boolean expectingAttackTarget;""", "BATTLE_REPLAY_HINT")

print("done")
