package de.cas_ual_ty.dueldimension.clientutil.model;

/**
 * Which monster is swinging, which is flinching, and how far through.
 * <p>
 * <b>The duel step lasts as long as the clip, because that is what the game
 * does.</b> Duelists of the Roses runs its battle from a state machine that sets
 * an animation and then polls {@code SzModel_GetAnim} until it reports finished
 * before advancing — the calls that set the attacker's slot 2 and the defender's
 * slot 5 have those polls between them. The battle is blocked on the animation,
 * so a card breaks after the swing rather than during it.
 * <p>
 * This once did the opposite: the attack held the board for EDOPro's 667ms and
 * the clip was left to run on underneath, because these swings are long — a
 * median of 8.7 seconds across the 683 monsters, up to 33.7. That decoupling
 * made the defender shatter while the attacker was still winding up. The step is
 * now sized from the clip instead (see {@code DuelAnimations.attackMs}), which
 * costs duel pace and buys a battle that reads as one thing happening.
 * <p>
 * What is kept here is WHICH clip a zone is playing and HOW FAR into it, which
 * the renderer needs per frame either way.
 * <p>
 * <b>This reads the animator and nothing else.</b> Attacks are learned from
 * {@code DuelAnimations.attacksInFlight}, which is fed by the single ordered
 * playback queue — so a swing becomes visible exactly when that queue releases
 * the attack, and never when a packet happens to land. Reading the board snapshot
 * or the packet directly would step outside that order, which is the mistake that
 * once made cards appear instantly.
 */
public final class BattleAnimations
{
    /**
     * The last attack seen, and nothing older.
     * <p>
     * One latch rather than a map, because the playback queue releases one event
     * at a time and holds the next until this one's window closes: there is
     * never a second attack in flight to remember. A new attack replaces this
     * one, which is also what should happen on screen.
     */
    private static int fromZone = -1;
    private static int toZone = -1;
    /**
     * Two starts, because they are two moments.
     * <p>
     * The swing begins when the attack is declared and the flinch when the blow
     * lands, and between them sit every negation and every trap that stops the
     * second from ever happening. They shared one instant while the flinch was
     * driven off the declaration, which made monsters recoil from attacks that
     * were later stopped.
     */
    private static long start;
    private static long hurtStart;
    /**
     * What stood in those zones when the attack began.
     * <p>
     * A zone is not an identity — it is a square, and squares are re-occupied.
     * A swing that outlives its own monster by several seconds could otherwise
     * be inherited by whatever is summoned into that square next, which would
     * look like the new monster attacking nothing.
     */
    private static long fromCode;
    private static long toCode;

    private BattleAnimations()
    {
    }

    /**
     * Records an attack the animator is reporting.
     * <p>
     * Idempotent for the same attack: the view is handed out every frame for as
     * long as its window is open, and the instant it carries is the attack's own
     * start rather than now, so latching it repeatedly cannot make the clip
     * restart or drift.
     */
    public static void latch(int attacker, long began, long attackerCode)
    {
        if(began == start && attacker == fromZone)
        {
            return;
        }
        fromZone = attacker;
        start = began;
        fromCode = attackerCode;
    }

    /** The same for the defender, at the later moment the blow lands. */
    public static void latchHurt(int defender, long began, long defenderCode)
    {
        if(began == hurtStart && defender == toZone)
        {
            return;
        }
        toZone = defender;
        hurtStart = began;
        toCode = defenderCode;
    }

    /**
     * How far into its battle clip the monster in this zone is, or -1 for a
     * monster that is not in one.
     *
     * @param zone   the packed zone reference, as {@code EnginePrompt.zoneRef}
     *               builds it — the same one the attack was recorded with
     * @param code   the card standing there now; a different card means the
     *               square has changed hands and the swing is not its
     * @param length the clip's own length in seconds, so a monster whose file
     *               has a short attack returns to idle sooner than one whose
     *               file has a long one
     * @param now    wall clock, millis
     */
    public static float phaseOf(int zone, long code, String animation, float length, long now)
    {
        if(zone < 0 || length <= 0F)
        {
            return -1F;
        }
        long expected;
        long began;
        if(zone == fromZone && ModelSkeleton.ATTACK.equals(animation))
        {
            expected = fromCode;
            began = start;
        }
        else if(zone == toZone && ModelSkeleton.HURT.equals(animation))
        {
            expected = toCode;
            began = hurtStart;
        }
        else
        {
            return -1F;
        }
        if(expected != code)
        {
            return -1F;
        }
        // Sped up, and the duel step is shortened by the same factor -- see
        // AnimationSettings.speed. Applied here rather than at the latch so a
        // change takes effect on a swing already in flight.
        float at = (now - began) / 1000F * AnimationSettings.speed();
        // Once, not looped -- but the window runs a blend PAST the end, because
        // standing up is a transition rather than an instant. The renderer
        // recognises a phase beyond the clip's own length as the cross-fade back
        // to the idle; a negative is the caller's signal that even that is over.
        return at >= 0F && at < length + ModelSkeleton.BLEND_SECONDS ? at : -1F;
    }

    /** What a monster in this zone should be playing, or null for its idle. */
    public static String clipFor(int zone)
    {
        // Asked here rather than at the latch, so switching the setting takes
        // effect on the next frame instead of on the next attack.
        if(zone < 0 || !AnimationSettings.extras())
        {
            return null;
        }
        if(zone == fromZone)
        {
            return ModelSkeleton.ATTACK;
        }
        return zone == toZone ? ModelSkeleton.HURT : null;
    }

    /** Forgets everything, so a new duel does not inherit the last one's swing. */
    public static void clear()
    {
        fromZone = -1;
        toZone = -1;
        start = 0L;
        hurtStart = 0L;
        fromCode = 0L;
        toCode = 0L;
    }
}
