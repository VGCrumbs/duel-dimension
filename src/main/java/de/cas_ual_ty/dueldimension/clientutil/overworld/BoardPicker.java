package de.cas_ual_ty.dueldimension.clientutil.overworld;

import de.cas_ual_ty.dueldimension.clientutil.BoardTarget;
import de.cas_ual_ty.dueldimension.clientutil.FieldLayout;
import de.cas_ual_ty.dueldimension.duel.overworld.FieldTransform;
import de.cas_ual_ty.dueldimension.ocg.OcgConstants;
import de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * What a duellist is looking at on the board.
 * <p>
 * Pure: it takes a ray, not a camera, so it can be tested without a client. The
 * renderer supplies the ray from the player's eye and look vector; everything
 * after that is arithmetic against the same {@link FieldLayout} rectangles the
 * board is drawn from -- so the thing picked is, by construction, the thing
 * seen. A separate set of hit rectangles is how a board ends up where you
 * cannot click what you can see.
 * <p>
 * The ray is intersected with the board's plane once and the answer looked up in
 * field units, rather than testing every card as a solid. Cards lie flat on a
 * flat board, so the plane is the board and the depth ordering is not in
 * question; this also means a card and the zone under it cannot disagree about
 * which of them was hit.
 */
public final class BoardPicker
{
    private BoardPicker()
    {
    }

    /**
     * The extra monster zones are one physical square shared by two logical
     * zones, one per controller. Taking the first match can hide an occupied
     * centre card behind its empty twin, which is a real rule and not a
     * rendering detail -- the same priority the 2D board applies.
     */
    private static final int[][] ZONES = {
        {OcgConstants.LOCATION_MZONE, 0}, {OcgConstants.LOCATION_MZONE, 1},
        {OcgConstants.LOCATION_MZONE, 2}, {OcgConstants.LOCATION_MZONE, 3},
        {OcgConstants.LOCATION_MZONE, 4}, {OcgConstants.LOCATION_MZONE, 5},
        {OcgConstants.LOCATION_MZONE, 6},
        {OcgConstants.LOCATION_SZONE, 0}, {OcgConstants.LOCATION_SZONE, 1},
        {OcgConstants.LOCATION_SZONE, 2}, {OcgConstants.LOCATION_SZONE, 3},
        {OcgConstants.LOCATION_SZONE, 4}, {OcgConstants.LOCATION_SZONE, 5},
    };

    /** The piles, which are picked as a whole and have no sequence of their own. */
    private static final int[] PILES = {OcgConstants.LOCATION_DECK, OcgConstants.LOCATION_EXTRA,
        OcgConstants.LOCATION_GRAVE, OcgConstants.LOCATION_REMOVED};

    /**
     * Where a look ray meets the board, in field units, or null if it never
     * does -- looking up, or along the board, or at the ground behind it.
     *
     * @param from the eye
     * @param look a unit vector in the direction of view
     */
    public static float[] aim(FieldTransform transform, Vec3 from, Vec3 look, double reach)
    {
        double surface = transform.surfaceY();
        // Parallel to the board, or pointing away from it: no meeting point.
        if(Math.abs(look.y) < 1e-6D)
        {
            return null;
        }
        double distance = (surface - from.y) / look.y;
        if(distance < 0D || distance > reach)
        {
            return null;
        }
        return transform.toField(from.add(look.scale(distance)));
    }

    /**
     * What is at this point of the board, or null for bare mat.
     *
     * @param viewerSeat which seat is looking, so the board's absolute halves
     *                   can be read back into the snapshot's seat-relative ones
     */
    public static BoardTarget at(BoardSnapshot board, int viewerSeat, float[] field)
    {
        if(board == null || field == null)
        {
            return null;
        }
        // Occupied zones first, across both controllers, so a card is never
        // hidden behind an empty zone that happens to overlap it.
        for(boolean occupiedOnly : new boolean[] {true, false})
        {
            for(int controller = 0; controller <= 1; controller++)
            {
                BoardSnapshot.Side side = sideFor(board, viewerSeat, controller);
                if(side == null)
                {
                    continue;
                }
                BoardTarget hit = inZones(side, controller, relative(viewerSeat, controller),
                    field, occupiedOnly);
                if(hit != null)
                {
                    return hit;
                }
            }
        }
        for(int controller = 0; controller <= 1; controller++)
        {
            BoardSnapshot.Side side = sideFor(board, viewerSeat, controller);
            if(side == null)
            {
                continue;
            }
            BoardTarget pile = inPiles(side, controller, relative(viewerSeat, controller),
                field);
            if(pile != null)
            {
                return pile;
            }
        }
        return null;
    }

    /**
     * The controller as the ENGINE numbers it: 0 is whoever is being asked.
     * <p>
     * The board's halves are absolute -- both duellists walk around the same
     * object -- but a prompt's options are built for one seat, so a target that
     * is going to be compared against them has to speak the engine's numbering
     * or seat 1 would never match its own cards.
     */
    private static int relative(int viewerSeat, int controller)
    {
        return controller == viewerSeat ? 0 : 1;
    }

    private static BoardTarget inZones(BoardSnapshot.Side side, int controller, int relative,
        float[] field, boolean occupiedOnly)
    {
        for(int[] zone : ZONES)
        {
            FieldLayout.Rect rect = FieldLayout.zone(controller, zone[0], zone[1]);
            if(rect == null || !within(rect, field))
            {
                continue;
            }
            BoardSnapshot.Slot slot = slotAt(side, zone[0], zone[1]);
            boolean occupied = slot != null && slot.present();
            if(occupiedOnly != occupied)
            {
                continue;
            }
            // The engine's own reference for this square, packed the same
            // way BoardRenderer packs it. Without it a PLACES prompt -- "where
            // do you want to put this" -- has nothing to match against, so a
            // summon could be started on the board and never finished.
            int zoneRef = de.cas_ual_ty.dueldimension.ocg.prompt.EnginePrompt.zoneRef(
                relative == 1, zone[0] == OcgConstants.LOCATION_MZONE, zone[1]);
            return new BoardTarget(occupied ? slot.code() : 0, relative, zone[0], zone[1],
                zoneRef, label(zone[0], zone[1]), occupied ? 1 : 0,
                occupied ? slot.art() : 0);
        }
        return null;
    }

    private static BoardTarget inPiles(BoardSnapshot.Side side, int controller, int relative,
        float[] field)
    {
        for(int location : PILES)
        {
            FieldLayout.Rect rect = FieldLayout.zone(controller, location, 0);
            if(rect == null || !within(rect, field))
            {
                continue;
            }
            // A pile is picked as one thing, so it has no sequence -- which is
            // exactly what BoardTarget.isPile reads.
            return new BoardTarget(0, relative, location, -1, -1, pileLabel(location),
                countOf(side, location), 0);
        }
        return null;
    }

    private static boolean within(FieldLayout.Rect rect, float[] field)
    {
        return field[0] >= rect.x() && field[0] <= rect.x() + rect.w()
            && field[1] >= rect.y() && field[1] <= rect.y() + rect.h();
    }

    /**
     * The snapshot is seat-relative and the board is not. Seat 0's own cards
     * are on controller 0's half, so a viewer at seat 1 reads controller 1 as
     * their own side. The inverse of {@link FieldTransform#controllerFor}, and
     * the only place it is undone.
     */
    private static BoardSnapshot.Side sideFor(BoardSnapshot board, int viewerSeat, int controller)
    {
        return controller == viewerSeat ? board.self() : board.opponent();
    }

    private static BoardSnapshot.Slot slotAt(BoardSnapshot.Side side, int location, int sequence)
    {
        List<BoardSnapshot.Slot> slots = location == OcgConstants.LOCATION_MZONE
            ? side.monsters() : side.spells();
        return slots == null || sequence >= slots.size() ? null : slots.get(sequence);
    }

    private static int countOf(BoardSnapshot.Side side, int location)
    {
        if(location == OcgConstants.LOCATION_DECK)
        {
            return side.deckCount();
        }
        List<BoardSnapshot.Slot> slots = location == OcgConstants.LOCATION_EXTRA ? side.extra()
            : location == OcgConstants.LOCATION_GRAVE ? side.grave() : side.banished();
        return slots == null ? 0 : slots.size();
    }

    private static String label(int location, int sequence)
    {
        if(location == OcgConstants.LOCATION_MZONE)
        {
            return sequence < 5 ? "Monster Zone " + (sequence + 1) : "Extra Monster Zone";
        }
        return sequence < 5 ? "Spell & Trap Zone " + (sequence + 1) : "Field Zone";
    }

    private static String pileLabel(int location)
    {
        return switch(location)
        {
            case OcgConstants.LOCATION_DECK -> "Deck";
            case OcgConstants.LOCATION_EXTRA -> "Extra Deck";
            case OcgConstants.LOCATION_GRAVE -> "Graveyard";
            default -> "Banished";
        };
    }
}
