package de.cas_ual_ty.dueldimension.clientutil;

/**
 * How full a life bar is drawn, for both presentations.
 * <p>
 * <b>Shared because it was duplicated, and the duplication is what broke it.</b>
 * The rule lived as one line copied into {@code EngineDuelScreen.lifeBarFill}
 * and {@code DuelHud.fill}:
 * <pre>
 * Math.max(0, Math.min(barW - 4, Math.round((barW - 4) * lifePoints / 8000F)))
 * </pre>
 * Two copies of a constant nobody had plumbed. Fixing it in one place would have
 * left the other view wrong and looking right, which is the worse of the two
 * failures.
 * <p>
 * <b>A fraction of the duel's OWN starting life, not of 8000.</b> The lobby
 * offers 8000, 4000, 2000 and 16000; against a fixed 8000 a duel started at 4000
 * would open with the bar already half gone, and one started at 16000 would sit
 * pinned at full until the loser was halfway dead. The bar has to answer "how
 * much of what you started with is left", which is the only question a duellist
 * is asking of it.
 * <p>
 * <b>Above the starting amount, a second bar fills over the first.</b> Gaining
 * life is not a bar that overflows its frame — there is nowhere for it to go —
 * so it becomes a second fill drawn on top, full at twice the starting amount.
 * That keeps the frame the same size at 8000 and at 16000, and still says at a
 * glance which duellist is ahead of where they began.
 */
public final class LifeBar
{
    /**
     * What the engine starts a duel at when nothing says otherwise
     * ({@code OcgDuel.PlayerConfig.DEFAULT}).
     * <p>
     * A fallback, not the rule: it is used only when a snapshot arrives with no
     * starting value, so an old or empty one draws the way it always did rather
     * than dividing by zero or reading as an empty bar.
     */
    public static final int DEFAULT_LIFE_POINTS = 8000;

    /** Both frames inset their fill by two pixels on each side. */
    private static final int INSET = 2;

    /**
     * The colours of the bar ABOVE the starting amount.
     * <p>
     * Deliberately not lighter greens and reds. The overflow is a different
     * fact from the life beneath it — "ahead of where you began" rather than
     * "how much is left" — and a shade of the same colour would read as more of
     * the same bar. Blue and orange are the two nobody uses for a life total,
     * so neither can be misread as one.
     */
    public static final int OVERFLOW_SELF = 0xFF4C8FE0;

    /** @see #OVERFLOW_SELF */
    public static final int OVERFLOW_OPPONENT = 0xFFE08A2E;

    private LifeBar()
    {
    }

    /**
     * Width in pixels of the main fill.
     * <p>
     * Clamped at full rather than allowed to run past the frame: once life is
     * above the starting amount this bar stays complete and
     * {@link #overflow} carries the rest.
     *
     * @param barW                the full width of the frame, insets included
     * @param startingLifePoints  what this duel began at; zero or less falls
     *                            back to {@link #DEFAULT_LIFE_POINTS}
     */
    public static int fill(int barW, int lifePoints, int startingLifePoints)
    {
        return scale(barW, lifePoints, startingLifePoints);
    }

    /**
     * Width in pixels of the fill drawn OVER {@link #fill}, for life above the
     * starting amount.
     * <p>
     * Measured against the same starting figure as the bar underneath, so it is
     * empty at the starting amount and full at twice it. Using the same divisor
     * for both is what makes the two bars comparable: a full overflow always
     * means "twice what I began with", whatever that was.
     */
    public static int overflow(int barW, int lifePoints, int startingLifePoints)
    {
        return scale(barW, lifePoints - starting(startingLifePoints), startingLifePoints);
    }

    private static int starting(int startingLifePoints)
    {
        return startingLifePoints > 0 ? startingLifePoints : DEFAULT_LIFE_POINTS;
    }

    private static int scale(int barW, int amount, int startingLifePoints)
    {
        int usable = Math.max(0, barW - INSET * 2);
        return Math.max(0, Math.min(usable,
            Math.round(usable * amount / (float)starting(startingLifePoints))));
    }
}
