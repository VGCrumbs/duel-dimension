"""Wire the seal to an actual duel loss.

Until now mark() was only reachable from the debug item and the command -- the
feature the config toggles did not exist.

The hook is payOut(), not concludeGame(). concludeGame runs once per GAME, so in
a best-of-three it would take a soul after game one; payOut runs once per
CONTEST (it is guarded by duel.paid), after duel.wins[] is final, and it already
works out each seat's WIN/LOSS. And because a match's next game gets a fresh
RunningDuel, "the Seal was up in the deciding game" needs no carrying between
games -- it is just a field on the duel that ends.
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

D = "src/main/java/de/cas_ual_ty/dueldimension/duel/npc/DuelistDuels.java"
S = "src/main/java/de/cas_ual_ty/dueldimension/duel/orichalcos/OrichalcosSouls.java"

# ---------- the flag on the duel ----------
sub(D, """        boolean paid;
        boolean forfeit;""",
    """        boolean paid;
        /**
         * Whether The Seal of Orichalcos was on the field at the last look.
         * <p>
         * Current state rather than ever-seen, so a Seal that gets destroyed
         * stops counting -- the rule is that the field spell is ACTIVE when the
         * duel is lost, not that it was played at some point.
         */
        boolean sealOnField;
        boolean forfeit;""", "RunningDuel.sealOnField")

# ---------- kept up to date from the board stream ----------
sub(D, "                    duel.rewards.observe(board.forSeat(0));",
    """                    duel.rewards.observe(board.forSeat(0));
                    // Same absolute seat-0 view the reward tracker reads, so
                    // the Seal is seen whichever side played it.
                    duel.sealOnField = de.cas_ual_ty.dueldimension.duel.orichalcos
                        .OrichalcosSouls.sealOnField(board.forSeat(0));""",
    "observe the seal")

# ---------- the mark, at the one place a contest is decided ----------
sub(D, """            int before = de.cas_ual_ty.dueldimension.shop.DuelPoints.get(player);""",
    """            if(outcome == de.cas_ual_ty.dueldimension.shop.DuelReward.Outcome.LOSS
                && duel.sealOnField)
            {
                // The anime's forfeit. A draw has no loser, which the LOSS test
                // already excludes. mark() decides whether the rule is on.
                de.cas_ual_ty.dueldimension.duel.orichalcos.OrichalcosSouls.mark(player);
            }

            int before = de.cas_ual_ty.dueldimension.shop.DuelPoints.get(player);""",
    "mark the loser")

# ---------- the board test, and the gate moving to the live setting ----------
sub(S, """    /** Marked targets, by UUID so a logout or a chunk unload does not lose them. */""",
    """    /**
     * The Seal of Orichalcos.
     * <p>
     * Matched by passcode rather than by name: a name is localised and a
     * passcode is what the engine actually deals in.
     */
    public static final int SEAL_PASSCODE = 48179391;

    /** Marked targets, by UUID so a logout or a chunk unload does not lose them. */""",
    "SEAL_PASSCODE")

sub(S, """    /** Whether this target is already marked, so nothing marks them twice. */""",
    """    /**
     * Whether The Seal of Orichalcos is face-up on either side of this board.
     * <p>
     * Either side on purpose: in the anime the Seal takes the loser whoever
     * played it, and the player who activates it loses to it as readily as
     * their opponent does.
     */
    public static boolean sealOnField(
        de.cas_ual_ty.dueldimension.ocg.msg.BoardSnapshot board)
    {
        return sealIn(board.self()) || sealIn(board.opponent());
    }

    private static boolean sealIn(de.cas_ual_ty.dueldimension.ocg.msg.BoardSnapshot.Side side)
    {
        for(de.cas_ual_ty.dueldimension.ocg.msg.BoardSnapshot.Slot slot : side.spells())
        {
            // Face-down is not active, and a set card's code is the server's
            // business anyway.
            if(slot.present() && !slot.faceDown() && slot.code() == SEAL_PASSCODE)
            {
                return true;
            }
        }
        return false;
    }

    /** Whether this target is already marked, so nothing marks them twice. */""",
    "sealOnField test")

sub(S, """        if(!de.cas_ual_ty.dueldimension.CommonConfig.get().sealOfOrichalcosDeath.get())
        {
            return;
        }""",
    """        // The live setting, not the read-once config: the config seeds its
        // default, but a toggle has to be able to take effect without a restart.
        if(!SealSettings.enabled())
        {
            return;
        }""", "gate on SealSettings")

print("done")
