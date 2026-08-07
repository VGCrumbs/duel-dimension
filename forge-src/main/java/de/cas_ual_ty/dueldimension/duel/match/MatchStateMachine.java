package de.cas_ual_ty.dueldimension.duel.match;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Holds a match's current {@link MatchState} and refuses illegal moves.
 * <p>
 * Every packet the server accepts for a match goes through {@link #tryMoveTo}
 * first. A client that sends "start the duel" while the lobby is still
 * validating, or accepts an invitation twice because the button was
 * double-clicked, is answered with a refusal rather than with a half-started
 * match — and because the refusal is one check in one place, no handler has to
 * re-derive what is currently legal.
 * <p>
 * Transitions are announced to listeners AFTER the state has changed, so a
 * listener always observes a consistent machine and may itself request the
 * next transition (lobby validated, so move to the coin flip).
 */
public final class MatchStateMachine
{
    private MatchState state = MatchState.IDLE;
    private final List<BiConsumer<MatchState, MatchState>> listeners = new ArrayList<>();

    /** Why the match ended, for the players and the log. Null while running. */
    private String endReason;

    public MatchState state()
    {
        return state;
    }

    public String endReason()
    {
        return endReason;
    }

    public void onTransition(BiConsumer<MatchState, MatchState> listener)
    {
        listeners.add(listener);
    }

    /**
     * Moves if the transition is legal.
     *
     * @return true if the state changed
     */
    public boolean tryMoveTo(MatchState next)
    {
        if(next == null || !state.canMoveTo(next))
        {
            return false;
        }
        MatchState previous = state;
        state = next;
        for(BiConsumer<MatchState, MatchState> listener : List.copyOf(listeners))
        {
            listener.accept(previous, next);
        }
        return true;
    }

    /**
     * Moves, or throws. For transitions the server itself drives, where an
     * illegal move is a bug rather than a misbehaving client.
     */
    public void moveTo(MatchState next)
    {
        if(!tryMoveTo(next))
        {
            throw new IllegalStateException("Illegal match transition " + state + " -> " + next);
        }
    }

    /**
     * Ends the match from wherever it is. Cancelling is reachable from every
     * non-terminal state precisely so that a disconnect, a decline or a
     * shutdown never needs a special path.
     */
    public boolean cancel(String reason)
    {
        if(state.isTerminal())
        {
            return false;
        }
        endReason = reason;
        return tryMoveTo(MatchState.CANCELLED);
    }

    public boolean finish(String reason)
    {
        endReason = reason;
        return tryMoveTo(MatchState.FINISHED);
    }

    public boolean isTerminal()
    {
        return state.isTerminal();
    }
}
