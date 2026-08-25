package de.cas_ual_ty.dueldimension.ocg.prompt;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a bystander may see of a duel.
 * <p>
 * A board built in the world can be walked up to, so this is the projection that
 * decides what a stranger's client is ever told. These tests are the reason it
 * can be trusted: every one of them is about something NOT being there.
 */
public class StrangerViewTest
{
    private static final int BLUE_EYES = 89631139;
    private static final int DARK_MAGICIAN = 46986414;

    private static BoardSnapshot.Slot faceUp(int code)
    {
        return new BoardSnapshot.Slot(true, code, false, false, 3000, 2500, 3000, 2500, 0, 0, 0,
            null, false, 1);
    }

    private static BoardSnapshot.Slot faceDown(int code)
    {
        return new BoardSnapshot.Slot(true, code, true, true, 1200, 2000, 1200, 2000, 0, 0, 0,
            null, false, 1);
    }

    private static BoardSnapshot.Side richSide()
    {
        // Deliberately not 8000: a starting total that differs from both the
        // engine default and the current life is what would catch the two being
        // confused for one another.
        return new BoardSnapshot.Side(7200, 12000,
            List.of(faceUp(BLUE_EYES), faceDown(DARK_MAGICIAN)),
            List.of(faceDown(BLUE_EYES)),
            List.of(faceUp(DARK_MAGICIAN), faceUp(BLUE_EYES)),
            List.of(faceUp(DARK_MAGICIAN)),
            List.of(faceUp(BLUE_EYES), faceDown(BLUE_EYES)),
            List.of(faceUp(BLUE_EYES)),
            37);
    }

    private static BoardSnapshot full()
    {
        return new BoardSnapshot(richSide(), richSide(), 4, 2, 1);
    }

    @Test
    public void aFaceUpMonsterIsPublic()
    {
        BoardSnapshot seen = StrangerView.of(full());

        assertEquals(BLUE_EYES, seen.self().monsters().get(0).code());
        assertEquals(3000, seen.self().monsters().get(0).attack());
    }

    @Test
    public void aFaceDownCardKeepsItsSecret()
    {
        BoardSnapshot seen = StrangerView.of(full());

        assertEquals(0, seen.self().monsters().get(1).code(), "a set monster was named");
        assertEquals(0, seen.self().spells().get(0).code(), "a set spell was named");
        assertTrue(seen.self().monsters().get(1).present(),
            "the card must still be THERE -- it is a visible object on the board");
        assertTrue(seen.self().monsters().get(1).faceDown());
    }

    /**
     * A set monster's attack and defence are as good as its name to anyone who
     * knows the game.
     */
    @Test
    public void aFaceDownMonstersStatisticsGoWithIt()
    {
        BoardSnapshot.Slot set = StrangerView.of(full()).self().monsters().get(1);

        assertEquals(0, set.attack());
        assertEquals(0, set.defense());
        assertEquals(0, set.baseAttack());
        assertEquals(0, set.baseDefense());
    }

    /**
     * The artwork index all but names the card on its own -- only about 122 of
     * nearly 14,000 cards have a second one.
     */
    @Test
    public void theArtworkGoesToo()
    {
        assertEquals(0, StrangerView.of(full()).self().monsters().get(1).art());
    }

    @Test
    public void aHandIsPrivateEvenFaceUp()
    {
        BoardSnapshot.Side seen = StrangerView.of(full()).self();

        assertEquals(2, seen.hand().size(), "how many cards are in a hand is public");
        for(BoardSnapshot.Slot slot : seen.hand())
        {
            assertEquals(0, slot.code(), "a spectator was handed somebody's hand");
        }
    }

    @Test
    public void anExtraDeckIsPrivateAndItsSizeIsNot()
    {
        BoardSnapshot.Side seen = StrangerView.of(full()).self();

        assertEquals(1, seen.extra().size());
        assertEquals(0, seen.extra().get(0).code());
        assertEquals(37, seen.deckCount(), "how many cards are left to draw is public");
    }

    @Test
    public void graveyardsAndBanishedPilesArePublic()
    {
        BoardSnapshot.Side seen = StrangerView.of(full()).self();

        assertEquals(DARK_MAGICIAN, seen.grave().get(0).code());
        assertEquals(BLUE_EYES, seen.banished().get(0).code());
        assertEquals(0, seen.banished().get(1).code(),
            "a card banished FACE DOWN is not public just because the pile is");
    }

    @Test
    public void lifePointsAndTheTurnArePublic()
    {
        BoardSnapshot seen = StrangerView.of(full());

        assertEquals(7200, seen.self().lifePoints());
        assertEquals(4, seen.turn());
        assertEquals(2, seen.phase());
        assertEquals(1, seen.turnPlayer());
    }

    /**
     * The property that makes this safe to build from whichever snapshot is to
     * hand: everything one seat can see and the other cannot is exactly what is
     * removed, so both seats reduce to the same view.
     */
    @Test
    public void bothSeatsReduceToTheSameView()
    {
        BoardSnapshot fromSeatZero = StrangerView.of(full());
        // The same duel as the other seat sees it: the two sides swapped.
        BoardSnapshot fromSeatOne = StrangerView.of(
            new BoardSnapshot(full().opponent(), full().self(), 4, 2, 1));

        assertEquals(fromSeatZero.self(), fromSeatOne.opponent());
        assertEquals(fromSeatZero.opponent(), fromSeatOne.self());
    }

    /** Removing only: a stranger never learns something a seat did not know. */
    @Test
    public void theViewOnlyEverRemoves()
    {
        BoardSnapshot before = full();
        BoardSnapshot after = StrangerView.of(before);

        assertEquals(before.self().monsters().size(), after.self().monsters().size());
        assertEquals(before.self().hand().size(), after.self().hand().size());
        for(int zone = 0; zone < before.self().monsters().size(); zone++)
        {
            BoardSnapshot.Slot was = before.self().monsters().get(zone);
            BoardSnapshot.Slot now = after.self().monsters().get(zone);
            assertEquals(was.present(), now.present());
            assertEquals(was.faceDown(), now.faceDown());
            assertTrue(now.code() == 0 || now.code() == was.code(),
                "a code appeared that was not in the source");
        }
    }

    @Test
    public void nothingSurvivesAnEmptyDuel()
    {
        assertEquals(null, StrangerView.of(null));
        BoardSnapshot seen = StrangerView.of(BoardSnapshot.EMPTY);
        assertNotEquals(null, seen);
        assertEquals(0, seen.self().monsters().size());
    }
}
