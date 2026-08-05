package de.cas_ual_ty.dueldimension.duel.match;

import java.util.EnumSet;
import java.util.Set;

/**
 * Where a challenge between two players has got to.
 * <p>
 * The whole multiplayer flow is one state machine, and this is its alphabet.
 * Every packet the server accepts is checked against the current state before
 * it is acted on, so a client cannot start a duel from a lobby that never
 * validated, accept an invitation twice, or configure a match that is already
 * running — the illegal transition is simply refused rather than each handler
 * re-deriving what is currently allowed.
 * <p>
 * A match may contain several duels ({@code Best of 3}), so the cycle from
 * {@link #COIN_FLIP} to {@link #GAME_OVER} can run more than once; that loop is
 * closed by {@link #INTERMISSION}.
 */
public enum MatchState
{
    /** No challenge exists. The terminal state in both directions. */
    IDLE,

    /**
     * The challenger has sent an invitation and the target has not answered.
     * Times out on its own, so a declined-by-silence invitation cannot wedge
     * either player out of duelling.
     */
    INVITED,

    /**
     * Both players are in the lobby choosing banlist, life points, format and
     * timer. Deck validation runs on every change and reports into the GUI.
     */
    CONFIGURING,

    /** The opening toss, to decide who chooses turn order. */
    COIN_FLIP,

    /** The toss winner is choosing to go first or second. */
    TURN_CHOICE,

    /** A duel is running. */
    DUELING,

    /**
     * One duel of a match ended. For a single duel this leads straight to
     * {@link #FINISHED}; for a match it leads to {@link #INTERMISSION} unless
     * someone has already taken it.
     */
    GAME_OVER,

    /** Between duels of a match: side decking would live here. */
    INTERMISSION,

    /** The match is decided. Terminal. */
    FINISHED,

    /** Declined, timed out, disconnected or cancelled. Terminal. */
    CANCELLED;

    /**
     * The states this one may legally move to.
     * <p>
     * Kept as data rather than as branches in a handler so that the machine can
     * be read in one place and tested exhaustively — {@code MatchStateTest}
     * walks every pair.
     */
    public Set<MatchState> successors()
    {
        return switch(this)
        {
            case IDLE -> EnumSet.of(INVITED);
            case INVITED -> EnumSet.of(CONFIGURING, CANCELLED);
            case CONFIGURING -> EnumSet.of(COIN_FLIP, CANCELLED);
            case COIN_FLIP -> EnumSet.of(TURN_CHOICE, CANCELLED);
            case TURN_CHOICE -> EnumSet.of(DUELING, CANCELLED);
            case DUELING -> EnumSet.of(GAME_OVER, CANCELLED);
            case GAME_OVER -> EnumSet.of(INTERMISSION, FINISHED, CANCELLED);
            // Back to the toss: each duel of a match opens with its own.
            case INTERMISSION -> EnumSet.of(COIN_FLIP, FINISHED, CANCELLED);
            case FINISHED, CANCELLED -> EnumSet.noneOf(MatchState.class);
        };
    }

    public boolean canMoveTo(MatchState next)
    {
        return successors().contains(next);
    }

    /** True once the match can no longer change. */
    public boolean isTerminal()
    {
        return successors().isEmpty();
    }

    /**
     * Whether a duel is on the table right now. Used to decide whether a
     * disconnect forfeits a game or merely cancels a lobby.
     */
    public boolean isInDuel()
    {
        return this == DUELING;
    }

    /** Whether both players should be looking at the lobby screen. */
    public boolean isConfiguring()
    {
        return this == CONFIGURING;
    }
}
