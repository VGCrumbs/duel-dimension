package de.cas_ual_ty.dueldimension.duel.match;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Walks the match state machine exhaustively, because it is the thing every
 * multiplayer packet is checked against: if a transition nobody intended is
 * legal here, a client can reach it.
 */
class MatchStateMachineTest
{
    @Test
    void everyStateIsReachableFromIdle()
    {
        Set<MatchState> reached = EnumSet.of(MatchState.IDLE);
        List<MatchState> frontier = new ArrayList<>(List.of(MatchState.IDLE));
        while(!frontier.isEmpty())
        {
            MatchState current = frontier.remove(0);
            for(MatchState next : current.successors())
            {
                if(reached.add(next))
                {
                    frontier.add(next);
                }
            }
        }
        assertEquals(EnumSet.allOf(MatchState.class), reached,
            "a state no sequence of legal moves can reach is dead code");
    }

    @Test
    void terminalStatesGoNowhere()
    {
        for(MatchState state : MatchState.values())
        {
            if(state.isTerminal())
            {
                assertTrue(state.successors().isEmpty(), state + " claims to be terminal but has successors");
            }
        }
        assertTrue(MatchState.FINISHED.isTerminal());
        assertTrue(MatchState.CANCELLED.isTerminal());
    }

    @Test
    void everyLiveStateCanBeCancelled()
    {
        // A disconnect, a decline or a server shutdown must never need a
        // special path out. Note IDLE is excluded: there is no match to cancel.
        for(MatchState state : MatchState.values())
        {
            if(state.isTerminal() || state == MatchState.IDLE)
            {
                continue;
            }
            assertTrue(state.canMoveTo(MatchState.CANCELLED),
                state + " cannot be cancelled, so a disconnect there would wedge the match");
        }
    }

    @Test
    void theMachineRefusesIllegalMovesInsteadOfPerformingThem()
    {
        MatchStateMachine machine = new MatchStateMachine();
        // "Start the duel" arriving while still on the invitation.
        assertFalse(machine.tryMoveTo(MatchState.DUELING));
        assertEquals(MatchState.IDLE, machine.state());

        assertTrue(machine.tryMoveTo(MatchState.INVITED));
        // The same accept arriving twice: the second must not re-enter.
        assertTrue(machine.tryMoveTo(MatchState.CONFIGURING));
        assertFalse(machine.tryMoveTo(MatchState.CONFIGURING));
        assertEquals(MatchState.CONFIGURING, machine.state());
    }

    @Test
    void aFullSingleDuelRunsEndToEnd()
    {
        MatchStateMachine machine = new MatchStateMachine();
        for(MatchState step : List.of(MatchState.INVITED, MatchState.CONFIGURING, MatchState.COIN_FLIP,
            MatchState.TURN_CHOICE, MatchState.DUELING, MatchState.GAME_OVER))
        {
            assertTrue(machine.tryMoveTo(step), "could not reach " + step);
        }
        assertTrue(machine.finish("player won"));
        assertTrue(machine.isTerminal());
        assertEquals("player won", machine.endReason());
    }

    @Test
    void aBestOfThreeCyclesBackToTheCoinFlip()
    {
        MatchStateMachine machine = new MatchStateMachine();
        for(MatchState step : List.of(MatchState.INVITED, MatchState.CONFIGURING, MatchState.COIN_FLIP,
            MatchState.TURN_CHOICE, MatchState.DUELING, MatchState.GAME_OVER))
        {
            machine.moveTo(step);
        }
        // Duel one is over but the match is not: back round for duel two.
        machine.moveTo(MatchState.INTERMISSION);
        machine.moveTo(MatchState.COIN_FLIP);
        machine.moveTo(MatchState.TURN_CHOICE);
        machine.moveTo(MatchState.DUELING);
        machine.moveTo(MatchState.GAME_OVER);
        assertTrue(machine.finish("2-0"));
    }

    @Test
    void moveToThrowsWhereTryMoveToWouldRefuse()
    {
        MatchStateMachine machine = new MatchStateMachine();
        assertThrows(IllegalStateException.class, () -> machine.moveTo(MatchState.FINISHED));
    }

    @Test
    void listenersSeeTheStateAlreadyChanged()
    {
        MatchStateMachine machine = new MatchStateMachine();
        List<String> seen = new ArrayList<>();
        machine.onTransition((from, to) -> seen.add(from + ">" + to + "@" + machine.state()));
        machine.moveTo(MatchState.INVITED);
        assertEquals(List.of("IDLE>INVITED@INVITED"), seen,
            "a listener must observe the new state, not the one being left");
    }

    @Test
    void cancellingATerminalMatchDoesNothing()
    {
        MatchStateMachine machine = new MatchStateMachine();
        machine.moveTo(MatchState.INVITED);
        assertTrue(machine.cancel("declined"));
        assertFalse(machine.cancel("declined again"));
        assertEquals("declined", machine.endReason());
    }

    // ---- config ----

    @Test
    void aProposedConfigIsClampedToOfferedChoices()
    {
        // A client may propose anything; the server takes none of it on trust.
        MatchConfig hostile = new MatchConfig("some_list", 999999, MatchConfig.Format.SINGLE, 99999);
        MatchConfig safe = hostile.sanitised();
        assertEquals(8000, safe.lifePoints(), "life points were not clamped to an offered choice");
        assertEquals(0, safe.turnSeconds(), "timer was not clamped to an offered choice");

        MatchConfig legal = new MatchConfig("goat", 4000, MatchConfig.Format.MATCH_BEST_OF_THREE, 120);
        assertEquals(legal, legal.sanitised(), "a legal configuration must survive unchanged");
    }

    @Test
    void bestOfThreeNeedsTwoWinsAcrossAtMostThreeDuels()
    {
        assertEquals(1, MatchConfig.Format.SINGLE.winsNeeded());
        assertEquals(1, MatchConfig.Format.SINGLE.maxDuels());
        assertEquals(2, MatchConfig.Format.MATCH_BEST_OF_THREE.winsNeeded());
        assertEquals(3, MatchConfig.Format.MATCH_BEST_OF_THREE.maxDuels());
    }

    // ---- banlist ----

    private static final String SAMPLE = """
        #[2026.05 TCG]
        !2026.05 TCG
        #Forbidden
        21044178 0 --Abyss Dweller
        #Limited
        83764719 1 --Monster Reborn
        #Semi-Limited
        53129443 2 --Dark Hole
        !Older List
        53129443 0 --Dark Hole
        """;

    @Test
    void banlistsAreParsedPerHeaderFromTheReferenceFormat()
    {
        List<Banlist> lists = assertDoesNotThrow(() -> Banlist.parse(new StringReader(SAMPLE)));
        assertEquals(2, lists.size(), "each ! header starts its own list");

        Banlist tcg = lists.get(0);
        assertEquals("2026.05 TCG", tcg.displayName());
        assertEquals(0, tcg.limitFor(21044178));
        assertEquals(1, tcg.limitFor(83764719));
        assertEquals(2, tcg.limitFor(53129443));
        // The rule that matters most: an unlisted card is unlimited, not banned.
        assertEquals(Banlist.UNLIMITED, tcg.limitFor(66788016));

        assertEquals(0, lists.get(1).limitFor(53129443), "the second list must not inherit the first");
    }

    private static <T> T assertDoesNotThrow(ThrowingSupplier<T> supplier)
    {
        try
        {
            return supplier.get();
        }
        catch(Exception e)
        {
            throw new AssertionError(e);
        }
    }

    private interface ThrowingSupplier<T>
    {
        T get() throws Exception;
    }

    @Test
    void deckValidationReportsEveryReasonItFailed()
    {
        Banlist list = assertDoesNotThrow(() -> Banlist.parse(new StringReader(SAMPLE))).get(0);

        List<Integer> legalMain = new ArrayList<>();
        for(int i = 0; i < 40; i++)
        {
            legalMain.add(66788016); // unlisted, but 40 copies breaks the limit
        }
        assertFalse(list.validate(legalMain, List.of(), List.of()).isEmpty(),
            "40 copies of one card must fail even when the card is unlisted");

        List<Integer> tooSmall = new ArrayList<>();
        for(int i = 0; i < 39; i++)
        {
            tooSmall.add(1000 + i);
        }
        List<String> problems = list.validate(tooSmall, List.of(), List.of());
        assertEquals(1, problems.size(), problems.toString());
        assertTrue(problems.get(0).contains("Main deck"), problems.get(0));

        List<Integer> good = new ArrayList<>();
        for(int i = 0; i < 40; i++)
        {
            good.add(1000 + i);
        }
        assertTrue(list.validate(good, List.of(), List.of()).isEmpty(), "a legal deck must pass");
    }

    @Test
    void copiesAreCountedAcrossMainExtraAndSideTogether()
    {
        Banlist list = assertDoesNotThrow(() -> Banlist.parse(new StringReader(SAMPLE))).get(0);
        List<Integer> main = new ArrayList<>();
        for(int i = 0; i < 39; i++)
        {
            main.add(1000 + i);
        }
        main.add(83764719); // Monster Reborn, limited to 1
        // One more in the side deck makes two copies overall, which is illegal.
        List<String> problems = list.validate(main, List.of(), List.of(83764719));
        assertEquals(1, problems.size(), problems.toString());
        assertTrue(problems.get(0).contains("limited to 1"), problems.get(0));
    }
}
