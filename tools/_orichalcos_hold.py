"""A beat between the seal landing and the soul being taken.

The seal reaching full size and the beam firing on the same tick read as one
event rather than two -- there was nothing between the close-in and the kill for
the eye to land on. A second of the finished seal sitting there gives the moment
somewhere to breathe, and it is also where the buzz has just run out and the
fade sound has not yet started.
"""
import io

def sub(path, old, new, label):
    s = io.open(path, encoding="utf-8").read()
    assert old in s, "anchor missing: " + label
    assert new not in s, "already applied: " + label
    io.open(path, "w", encoding="utf-8", newline="\n").write(s.replace(old, new, 1))
    print("  ok:", label)

S = "src/main/java/de/cas_ual_ty/dueldimension/duel/orichalcos/OrichalcosSouls.java"
M = "src/main/java/de/cas_ual_ty/dueldimension/duel/orichalcos/OrichalcosMessages.java"
R = "src/main/java/de/cas_ual_ty/dueldimension/clientutil/OrichalcosRenderer.java"
N = "src/main/java/de/cas_ual_ty/dueldimension/net/DdNetwork.java"

# ---------- server: the hold, and the kill moving behind it ----------
sub(S, """    /**
     * How long the beam lingers after the kill.""",
    """    /**
     * The pause between the seal landing and the soul being taken.
     * <p>
     * One second. The seal reaching full size and the beam firing on the same
     * tick read as a single event; a beat between them gives the moment
     * somewhere to breathe. It also sits in the gap where the buzz has just
     * decayed to nothing and the fade has not yet started.
     */
    public static final int HOLD_TICKS = 20;

    /**
     * How long the beam lingers after the kill.""",
    "HOLD_TICKS")

sub(S, "            if(now - pending.startedAt() >= GROW_TICKS)",
    "            if(now - pending.startedAt() >= GROW_TICKS + HOLD_TICKS)",
    "kill waits out the hold")

sub(S, "            new OrichalcosMessages.SealBegin(target.getId(), GROW_TICKS, FADE_TICKS);",
    "            new OrichalcosMessages.SealBegin(target.getId(), GROW_TICKS, HOLD_TICKS,\n"
    "                FADE_TICKS);",
    "packet carries the hold")

# ---------- the message ----------
sub(M, """     * @param entityId   the network id of whatever the seal is closing on
     * @param growTicks  how long the seal takes to reach full size
     * @param fadeTicks  how long the beam lingers after the kill
     */
    public record SealBegin(int entityId, int growTicks, int fadeTicks)""",
    """     * @param entityId   the network id of whatever the seal is closing on
     * @param growTicks  how long the seal takes to reach full size
     * @param holdTicks  the pause between the seal landing and the beam firing
     * @param fadeTicks  how long the beam lingers after the kill
     */
    public record SealBegin(int entityId, int growTicks, int holdTicks, int fadeTicks)""",
    "message field")

sub(M, """            ByteBufCodecs.VAR_INT, SealBegin::growTicks,
            ByteBufCodecs.VAR_INT, SealBegin::fadeTicks,
            SealBegin::new);""",
    """            ByteBufCodecs.VAR_INT, SealBegin::growTicks,
            ByteBufCodecs.VAR_INT, SealBegin::holdTicks,
            ByteBufCodecs.VAR_INT, SealBegin::fadeTicks,
            SealBegin::new);""",
    "message codec")

sub(N, "                .begin(payload.entityId(), payload.growTicks(), payload.fadeTicks()));",
    "                .begin(payload.entityId(), payload.growTicks(), payload.holdTicks(),\n"
    "                    payload.fadeTicks()));",
    "receiver passes the hold")

# ---------- client ----------
sub(R, """    private static final class Seal
    {
        private final int growTicks;
        private final int fadeTicks;""",
    """    private static final class Seal
    {
        private final int growTicks;
        private final int holdTicks;
        private final int fadeTicks;""", "client field")

sub(R, """        private Seal(int growTicks, int fadeTicks)
        {
            this.growTicks = Math.max(1, growTicks);
            this.fadeTicks = Math.max(1, fadeTicks);
        }

        private int total()
        {
            return growTicks + fadeTicks;
        }""",
    """        private Seal(int growTicks, int holdTicks, int fadeTicks)
        {
            this.growTicks = Math.max(1, growTicks);
            this.holdTicks = Math.max(0, holdTicks);
            this.fadeTicks = Math.max(1, fadeTicks);
        }

        /** The tick the beam fires and the soul goes, counted from the start. */
        private int kill()
        {
            return growTicks + holdTicks;
        }

        private int total()
        {
            return kill() + fadeTicks;
        }""", "client constructor")

sub(R, """            if(age <= growTicks)
            {
                return 1F;
            }
            return 1F - Math.min(1F, (age - growTicks) / fadeTicks);""",
    """            if(age <= kill())
            {
                return 1F;
            }
            return 1F - Math.min(1F, (age - kill()) / fadeTicks);""", "client fade start")

sub(R, """    public static void begin(int entityId, int growTicks, int fadeTicks)
    {
        ACTIVE.put(entityId, new Seal(growTicks, fadeTicks));
    }""",
    """    public static void begin(int entityId, int growTicks, int holdTicks, int fadeTicks)
    {
        ACTIVE.put(entityId, new Seal(growTicks, holdTicks, fadeTicks));
    }""", "client begin")

sub(R, """            if(age >= seal.growTicks)
            {
                drawBeam(poseStack, collector, seal, age, gameTime, partial);
            }""",
    """            // The beam waits out the hold: the seal lands, sits there a
            // second, and only then does the soul go up.
            if(age >= seal.kill())
            {
                drawBeam(poseStack, collector, seal, age, gameTime, partial);
            }""", "client beam start")

print("done")
