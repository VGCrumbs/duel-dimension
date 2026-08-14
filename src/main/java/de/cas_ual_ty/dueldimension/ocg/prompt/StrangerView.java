package de.cas_ual_ty.dueldimension.ocg.prompt;

import java.util.ArrayList;
import java.util.List;

/**
 * What somebody who is not playing may see of a duel.
 * <p>
 * A board built in the world is a physical object, so anybody who walks up to it
 * can look at it. That is a feature -- duels should be watchable -- and it is
 * also the moment the mod acquires a third kind of viewer. {@code BoardState}
 * has exactly two: honest-per-seat and omniscient. A spectator is neither, and
 * sending them either seat's copy would hand a bystander that duellist's hand.
 * <p>
 * <b>Subtractive only, and that is the whole safety argument.</b> This takes a
 * snapshot the server already built for one seat and removes from it; it never
 * consults the engine, never reads the other seat's observer, and never adds a
 * field. A projection that can only remove cannot reveal, whatever else is wrong
 * with it -- which is a much shorter thing to be sure of than a second query
 * against the core, and is why the spectator view is built this way rather than
 * as {@code observeAsStranger}.
 * <p>
 * What survives is what is public at the table: face-up cards on the field, both
 * graveyards, banished piles, life points, and how many cards are in each hand
 * and deck. What does not: every hand's identities, every face-down card, and
 * the extra decks.
 */
public final class StrangerView
{
    private StrangerView()
    {
    }

    /**
     * A spectator's view of a duel, from either seat's snapshot.
     * <p>
     * The seat it came from is irrelevant to the result: everything that seat
     * could see and the other could not is exactly what gets removed, so both
     * seats' snapshots reduce to the same stranger view. {@code StrangerViewTest}
     * asserts that, because it is the property that makes this safe to build
     * from whichever snapshot happens to be to hand.
     */
    public static BoardSnapshot of(BoardSnapshot seat)
    {
        if(seat == null)
        {
            return null;
        }
        return new BoardSnapshot(strip(seat.self()), strip(seat.opponent()), seat.turn(),
            seat.phase(), seat.turnPlayer());
    }

    private static BoardSnapshot.Side strip(BoardSnapshot.Side side)
    {
        if(side == null)
        {
            return null;
        }
        return new BoardSnapshot.Side(side.lifePoints(),
            // Face-up cards on the field are public; face-down ones are not,
            // whoever set them.
            conceal(side.monsters(), true),
            conceal(side.spells(), true),
            // A hand is private even to the person sitting next to it. Kept as
            // slots rather than as a count so the number of cards still shows.
            conceal(side.hand(), false),
            // Both graveyards are public knowledge and may be read by either
            // player at any time; the banished pile likewise, except for the
            // cards banished face down, which conceal() leaves alone.
            conceal(side.grave(), true),
            conceal(side.banished(), true),
            // An extra deck's contents are private, though its size is not.
            conceal(side.extra(), false),
            side.deckCount());
    }

    /**
     * @param keepFaceUp true where a face-up card is public, false where the
     *                   whole zone is private however its cards are turned
     */
    private static List<BoardSnapshot.Slot> conceal(List<BoardSnapshot.Slot> slots,
        boolean keepFaceUp)
    {
        if(slots == null)
        {
            return List.of();
        }
        List<BoardSnapshot.Slot> stripped = new ArrayList<>(slots.size());
        for(BoardSnapshot.Slot slot : slots)
        {
            stripped.add(keepFaceUp && !slot.faceDown() ? slot : blank(slot));
        }
        return stripped;
    }

    /**
     * The same slot with nothing identifying left on it.
     * <p>
     * Both the code AND the artwork, because only about 122 of nearly 14,000
     * cards have a second artwork -- so the artwork index all but names the
     * card on its own. {@code BoardState.conceal} zeroes both for the same
     * reason, and this is not the place to be cleverer than it.
     * <p>
     * The statistics go too. A monster's attack and defence are as good as a
     * name to anyone who knows the game, and a face-down monster's are not
     * something a spectator has any business reading.
     */
    private static BoardSnapshot.Slot blank(BoardSnapshot.Slot slot)
    {
        return new BoardSnapshot.Slot(slot.present(), 0, slot.faceDown(), slot.defence(),
            0, 0, 0, 0, 0, 0, slot.overlays(), null, false, 0);
    }
}
