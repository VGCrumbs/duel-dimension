"""Shorter growth, and a beat between the beam firing and the kill.

Growth drops from six seconds to five. The beam then fires after the one-second
hold, and the kill follows it a quarter second later -- so the soul is visibly
pulled out before the body drops, rather than both happening on one tick.

That split also moves the fade SOUND. It was played from kill(); it belongs with
the beam, which is what the user asked it to accompany, and the two are no
longer the same moment.
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
R = "src/main/java/de/cas_ual_ty/dueldimension/clientutil/OrichalcosRenderer.java"

# ---------- growth: six seconds -> five ----------
sub(S, """    /** How long the seal takes to close in, in ticks. Six seconds. */
    public static final int GROW_TICKS = 120;""",
    """    /** How long the seal takes to close in, in ticks. Five seconds. */
    public static final int GROW_TICKS = 100;""", "GROW_TICKS 120 -> 100")

# ---------- the quarter second between beam and kill ----------
sub(S, """    /**
     * How long the beam lingers after the kill.""",
    """    /**
     * The gap between the beam firing and the target dying.
     * <p>
     * A quarter second. The soul is pulled out and the body drops after it;
     * on the same tick the two read as one event and the beam looks like a
     * death effect rather than the cause of it.
     */
    public static final int KILL_DELAY_TICKS = 5;

    /**
     * How long the beam lingers after the kill.""", "KILL_DELAY_TICKS")

# ---------- exactly-once beam moment ----------
sub(S, """     * @param deadline  the tick the sequence starts at come what may
     * @param startedAt the tick it actually started, or -1 while waiting
     */
    private record Pending(long deadline, long startedAt)
    {""",
    """     * @param deadline  the tick the sequence starts at come what may
     * @param startedAt the tick it actually started, or -1 while waiting
     * @param beamed    whether the beam has already fired, so it fires once
     */
    private record Pending(long deadline, long startedAt, boolean beamed)
    {""", "Pending gains beamed")

sub(S, "if(PENDING.putIfAbsent(target.getUUID(), new Pending(deadline, -1)) != null)",
    "if(PENDING.putIfAbsent(target.getUUID(), new Pending(deadline, -1, false)) != null)",
    "arm")

sub(S, """                : new Pending(Math.min(pending.deadline(), player.level().getGameTime()),
                    pending.startedAt()));""",
    """                : new Pending(Math.min(pending.deadline(), player.level().getGameTime()),
                    pending.startedAt(), pending.beamed()));""", "playerLeftDuel")

sub(S, """                if(now >= pending.deadline())
                {
                    entry.setValue(new Pending(pending.deadline(), now));
                    begin(target);
                }
                continue;
            }

            if(now - pending.startedAt() >= GROW_TICKS + HOLD_TICKS)
            {
                kill(target);
                it.remove();
            }""",
    """                if(now >= pending.deadline())
                {
                    entry.setValue(new Pending(pending.deadline(), now, false));
                    begin(target);
                }
                continue;
            }

            long elapsed = now - pending.startedAt();
            if(!pending.beamed() && elapsed >= GROW_TICKS + HOLD_TICKS)
            {
                // The beam, and the sound that goes with it. Guarded by the
                // flag rather than by an exact tick comparison so a tick the
                // server ran long can never skip it.
                playAt(target, de.cas_ual_ty.dueldimension.DdSounds.ORICHALCOS_FADE);
                entry.setValue(new Pending(pending.deadline(), pending.startedAt(), true));
            }
            if(elapsed >= GROW_TICKS + HOLD_TICKS + KILL_DELAY_TICKS)
            {
                kill(target);
                it.remove();
            }""", "tick: beam then kill")

# ---------- the sound leaves kill() ----------
sub(S, """        // With the beam, not after it: this is the sound of the soul leaving,
        // and it lasts exactly as long as the beam takes to fade.
        playAt(target, de.cas_ual_ty.dueldimension.DdSounds.ORICHALCOS_FADE);

        Holder<DamageType> type""",
    """        Holder<DamageType> type""", "fade sound out of kill()")

# ---------- client: the beam moment is not the kill moment any more ----------
sub(R, """        /** The tick the beam fires and the soul goes, counted from the start. */
        private int kill()
        {
            return growTicks + holdTicks;
        }

        private int total()
        {
            return kill() + fadeTicks;
        }""",
    """        /**
         * The tick the beam fires, counted from the start.
         * <p>
         * Not the tick the target dies -- the server holds that back a further
         * quarter second. The visuals key off the beam, which is what is
         * actually on screen.
         */
        private int beamAt()
        {
            return growTicks + holdTicks;
        }

        private int total()
        {
            return beamAt() + fadeTicks;
        }""", "beamAt rename")

sub(R, """            if(age <= kill())
            {
                return 1F;
            }
            return 1F - Math.min(1F, (age - kill()) / fadeTicks);""",
    """            if(age <= beamAt())
            {
                return 1F;
            }
            return 1F - Math.min(1F, (age - beamAt()) / fadeTicks);""", "remaining")

sub(R, "            if(age >= seal.kill())", "            if(age >= seal.beamAt())",
    "beam start")

print("done")
