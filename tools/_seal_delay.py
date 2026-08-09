"""Start the seal when the player is back in the world, not on a blind timer.

A duelist has no duel screen to close, so nothing ever called playerLeftDuel for
one and its mark sat out the whole 20-second fallback -- measured at exactly 20s
between "marked" and "the seal closes on" in the server log. The wait was never
about the loser anyway: it is about the PLAYER being back in control of their
character to watch it happen. So a mark now records who it is waiting for, and a
duelist's mark waits for the human at the other seat.

The fallback drops with it, from 20 seconds to 12. It is only a backstop for a
client that never reports leaving, and 20 seconds of nothing was long enough to
read as broken.
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

S = "src/main/java/de/cas_ual_ty/dueldimension/duel/orichalcos/OrichalcosSouls.java"
D = "src/main/java/de/cas_ual_ty/dueldimension/duel/npc/DuelistDuels.java"

sub(S, """    private static final int FALLBACK_TICKS = 400;""",
    """    private static final int FALLBACK_TICKS = 240;""", "fallback 20s -> 12s")

sub(S, """     * The longest a marked player may stay alive if their client never says it
     * is ready. Twenty seconds: long enough to leave a duel screen unhurried,
     * short enough that nobody thinks they got away with it.
     */""",
    """     * The longest a marked target may stay alive if nobody reports being back
     * in the world. Twelve seconds — purely a backstop for a client that never
     * sends the signal, and short enough that waiting it out does not read as
     * the seal having failed.
     */""", "fallback javadoc")

# ---------- a mark knows who it is waiting for ----------
sub(S, """     * @param deadline  the tick the sequence starts at come what may
     * @param startedAt the tick it actually started, or -1 while waiting
     * @param beamed    whether the beam has already fired, so it fires once
     */
    private record Pending(long deadline, long startedAt, boolean beamed)""",
    """     * @param deadline  the tick the sequence starts at come what may
     * @param startedAt the tick it actually started, or -1 while waiting
     * @param beamed    whether the beam has already fired, so it fires once
     * @param waitFor   the player whose return to the world starts this, or
     *                  null to wait out the deadline. For a duelist that is the
     *                  human at the other seat: the duelist has no screen to
     *                  close, but somebody has to be there to see it.
     */
    private record Pending(long deadline, long startedAt, boolean beamed, UUID waitFor)""",
    "Pending.waitFor")

sub(S, """        arm(loser, loser.level().getGameTime() + FALLBACK_TICKS);""",
    """        // A player waits for their own screen to close.
        arm(loser, loser.level().getGameTime() + FALLBACK_TICKS, loser.getUUID());""",
    "mark waits for itself")

sub(S, """    public static void markById(MinecraftServer server, UUID id)
    {
        if(id != null)
        {
            mark(resolve(server, id));
        }
    }""",
    """    public static void markById(MinecraftServer server, UUID id, UUID waitFor)
    {
        if(id == null)
        {
            return;
        }
        LivingEntity target = resolve(server, id);
        if(target == null
            || !de.cas_ual_ty.dueldimension.duel.orichalcos.SealSettings.enabled())
        {
            return;
        }
        arm(target, target.level().getGameTime() + FALLBACK_TICKS,
            waitFor == null ? target.getUUID() : waitFor);
    }""", "markById waitFor")

sub(S, """        return arm(target, target.level().getGameTime());""",
    """        return arm(target, target.level().getGameTime(), null);""", "force arms now")

sub(S, """    private static boolean arm(LivingEntity target, long deadline)
    {
        if(PENDING.putIfAbsent(target.getUUID(), new Pending(deadline, -1, false)) != null)""",
    """    private static boolean arm(LivingEntity target, long deadline, UUID waitFor)
    {
        if(PENDING.putIfAbsent(target.getUUID(),
            new Pending(deadline, -1, false, waitFor)) != null)""", "arm signature")

# ---------- the signal releases whoever was waiting on it ----------
sub(S, """        PENDING.computeIfPresent(player.getUUID(), (id, pending) ->
            pending.started() ? pending
                : new Pending(Math.min(pending.deadline(), player.level().getGameTime()),
                    pending.startedAt(), pending.beamed()));""",
    """        long now = player.level().getGameTime();
        // Everything waiting on THIS player, which is their own mark and any
        // duelist they just beat. Not every pending mark: another player's
        // duel, somewhere else entirely, is none of this one's business.
        PENDING.replaceAll((id, pending) ->
            pending.started() || !player.getUUID().equals(pending.waitFor()) ? pending
                : new Pending(Math.min(pending.deadline(), now),
                    pending.startedAt(), pending.beamed(), pending.waitFor()));""",
    "playerLeftDuel releases waiters")

sub(S, """                    entry.setValue(new Pending(pending.deadline(), now, false));""",
    """                    entry.setValue(new Pending(pending.deadline(), now, false,
                        pending.waitFor()));""", "tick start")

sub(S, """                entry.setValue(new Pending(pending.deadline(), pending.startedAt(), true));""",
    """                entry.setValue(new Pending(pending.deadline(), pending.startedAt(), true,
                    pending.waitFor()));""", "tick beam")

# ---------- a duelist waits for the human who beat it ----------
sub(D, """            de.cas_ual_ty.dueldimension.duel.orichalcos.OrichalcosSouls
                .markById(server, duel.duelistId);
            return;""",
    """            // Waits for the human at the OTHER seat: a duelist has no screen
            // to close, and the point of the wait is that somebody is back in
            // the world to watch.
            Watcher winner = duel.seats[1 - loser];
            de.cas_ual_ty.dueldimension.duel.orichalcos.OrichalcosSouls.markById(
                server, duel.duelistId,
                winner == null || winner.console() ? null : winner.playerId());
            return;""", "duelist waits for the winner")

sub(D, """        de.cas_ual_ty.dueldimension.duel.orichalcos.OrichalcosSouls
            .markById(server, watcher.playerId());""",
    """        de.cas_ual_ty.dueldimension.duel.orichalcos.OrichalcosSouls
            .markById(server, watcher.playerId(), watcher.playerId());""",
    "player waits for itself")

print("done")
