package de.cas_ual_ty.dueldimension.clientutil.overworld;

import de.cas_ual_ty.dueldimension.clientutil.BoardTarget;
import de.cas_ual_ty.dueldimension.clientutil.FieldLayout;
import de.cas_ual_ty.dueldimension.duel.overworld.FieldSiting;
import de.cas_ual_ty.dueldimension.duel.overworld.FieldSpec;
import de.cas_ual_ty.dueldimension.duel.overworld.FieldTransform;
import de.cas_ual_ty.dueldimension.ocg.OcgConstants;
import de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Looking at a card on the world board.
 * <p>
 * The picker takes a ray rather than a camera, so what a duellist would be
 * pointing at can be worked out here rather than by standing in a world and
 * squinting. What is being pinned is that the thing picked is the thing DRAWN --
 * both go through the same {@link FieldLayout} rectangles and the same
 * transform, and a board where you cannot click what you can see is a board
 * where those two drifted apart.
 */
public class BoardPickerTest
{
    private static FieldTransform transform(Direction facing)
    {
        return new FieldTransform(
            new FieldSiting(new BlockPos(0, 63, 0), facing, FieldSpec.DEFAULT));
    }

    private static BoardSnapshot.Slot card(int code)
    {
        return new BoardSnapshot.Slot(true, code, false, false, 0, 0, 0, 0, 0, 0, 0, null,
            false, 0);
    }

    private static BoardSnapshot.Slot empty()
    {
        return BoardSnapshot.Slot.EMPTY;
    }

    /** A side with one monster in the given zone and nothing else. */
    private static BoardSnapshot.Side sideWithMonster(int sequence, int code)
    {
        List<BoardSnapshot.Slot> monsters = new ArrayList<>();
        for(int zone = 0; zone < 7; zone++)
        {
            monsters.add(zone == sequence ? card(code) : empty());
        }
        List<BoardSnapshot.Slot> spells = new ArrayList<>();
        for(int zone = 0; zone < 6; zone++)
        {
            spells.add(empty());
        }
        return new BoardSnapshot.Side(8000, monsters, spells, List.of(), List.of(), List.of(),
            List.of(), 40);
    }

    /** Where a zone's middle is in the world, which is where a duellist would aim. */
    private static Vec3 middleOf(FieldTransform transform, int controller, int location,
        int sequence)
    {
        FieldLayout.Rect rect = FieldLayout.zone(controller, location, sequence);
        return transform.at(rect.x() + rect.w() / 2F, rect.y() + rect.h() / 2F, 0D);
    }

    @Test
    public void lookingDownAtTheBoardFindsTheBoard()
    {
        FieldTransform transform = transform(Direction.EAST);
        Vec3 above = new Vec3(0.5D, 68D, 0.5D);

        float[] field = BoardPicker.aim(transform, above, new Vec3(0D, -1D, 0D), 24D);

        assertNotNull(field);
        assertEquals(FieldTransform.CENTRE_X, field[0], 1e-4F);
        assertEquals(FieldTransform.CENTRE_Y, field[1], 1e-4F);
    }

    @Test
    public void lookingAwayFromTheBoardFindsNothing()
    {
        FieldTransform transform = transform(Direction.EAST);
        Vec3 above = new Vec3(0.5D, 68D, 0.5D);

        assertNull(BoardPicker.aim(transform, above, new Vec3(0D, 1D, 0D), 24D),
            "looking up");
        assertNull(BoardPicker.aim(transform, above, new Vec3(1D, 0D, 0D), 24D),
            "looking along the board's plane");
        assertNull(BoardPicker.aim(transform, above, new Vec3(0D, -1D, 0D), 1D),
            "the board is further away than the player can reach");
    }

    /**
     * The real property: aim at where a card is drawn and the picker names that
     * card. Checked at every facing, because the field-to-world mapping flips
     * an axis and a picker that agreed with the renderer at one facing and not
     * another would be maddening to diagnose in game.
     */
    @Test
    public void aimingAtACardFindsThatCard()
    {
        for(Direction facing : Direction.Plane.HORIZONTAL)
        {
            FieldTransform transform = transform(facing);
            BoardSnapshot board = new BoardSnapshot(sideWithMonster(2, 46986414),
                BoardSnapshot.Side.empty(), 1, 0, 0);

            Vec3 middle = middleOf(transform, 0, OcgConstants.LOCATION_MZONE, 2);
            Vec3 eye = middle.add(0D, 3D, 0D);
            float[] field = BoardPicker.aim(transform, eye, new Vec3(0D, -1D, 0D), 24D);
            BoardTarget hit = BoardPicker.at(board, 0, field);

            assertNotNull(hit, "facing " + facing + ": nothing under the crosshair");
            assertEquals(OcgConstants.LOCATION_MZONE, hit.location());
            assertEquals(2, hit.sequence());
            assertEquals(0, hit.controller());
            assertEquals(46986414, hit.code());
        }
    }

    /** The opponent's half is pickable too, and comes back as their controller. */
    @Test
    public void aimingAcrossTheBoardFindsTheOpponentsCard()
    {
        FieldTransform transform = transform(Direction.SOUTH);
        BoardSnapshot board = new BoardSnapshot(BoardSnapshot.Side.empty(),
            sideWithMonster(4, 89631139), 1, 0, 0);

        Vec3 middle = middleOf(transform, 1, OcgConstants.LOCATION_MZONE, 4);
        float[] field = BoardPicker.aim(transform, middle.add(0D, 3D, 0D),
            new Vec3(0D, -1D, 0D), 24D);
        BoardTarget hit = BoardPicker.at(board, 0, field);

        assertNotNull(hit);
        assertEquals(1, hit.controller());
        assertEquals(89631139, hit.code());
    }

    /**
     * The two extra monster zones are one physical square shared by two logical
     * zones. Taking the first match would hide an occupied card behind its
     * empty twin, which is a real rule rather than a rendering detail.
     */
    @Test
    public void anOccupiedZoneIsNeverHiddenBehindAnEmptyOne()
    {
        FieldTransform transform = transform(Direction.EAST);
        // Controller 1 holds the shared extra monster zone; controller 0's
        // logical twin of it is empty.
        BoardSnapshot board = new BoardSnapshot(sideWithMonster(-1, 0),
            sideWithMonster(5, 12345678), 1, 0, 0);

        Vec3 middle = middleOf(transform, 1, OcgConstants.LOCATION_MZONE, 5);
        float[] field = BoardPicker.aim(transform, middle.add(0D, 3D, 0D),
            new Vec3(0D, -1D, 0D), 24D);
        BoardTarget hit = BoardPicker.at(board, 0, field);

        assertNotNull(hit);
        assertEquals(12345678, hit.code(), "the empty twin swallowed the occupied zone");
    }

    @Test
    public void aimingAtAPileFindsThePile()
    {
        FieldTransform transform = transform(Direction.EAST);
        BoardSnapshot board = new BoardSnapshot(sideWithMonster(-1, 0),
            BoardSnapshot.Side.empty(), 1, 0, 0);

        Vec3 middle = middleOf(transform, 0, OcgConstants.LOCATION_DECK, 0);
        float[] field = BoardPicker.aim(transform, middle.add(0D, 3D, 0D),
            new Vec3(0D, -1D, 0D), 24D);
        BoardTarget hit = BoardPicker.at(board, 0, field);

        assertNotNull(hit);
        assertTrue(hit.isPile(), "a pile is picked as one thing, so it has no sequence");
        assertEquals(OcgConstants.LOCATION_DECK, hit.location());
        assertEquals(40, hit.count());
    }

    @Test
    public void aimingAtBareMatFindsNothing()
    {
        FieldTransform transform = transform(Direction.EAST);
        BoardSnapshot board = new BoardSnapshot(BoardSnapshot.Side.empty(),
            BoardSnapshot.Side.empty(), 1, 0, 0);

        // The far corner of the mat, outside every zone rectangle.
        float[] field = {FieldLayout.FIELD_MIN_X + 0.05F, FieldLayout.FIELD_MIN_Y + 0.05F};

        assertNull(BoardPicker.at(board, 0, field));
    }

    /** A viewer at seat 1 reads controller 1 as their own side, not seat 0's. */
    @Test
    public void theSeatDecidesWhichHalfIsYours()
    {
        FieldTransform transform = transform(Direction.EAST);
        BoardSnapshot board = new BoardSnapshot(sideWithMonster(0, 55555),
            BoardSnapshot.Side.empty(), 1, 0, 0);

        Vec3 middle = middleOf(transform, 1, OcgConstants.LOCATION_MZONE, 0);
        float[] field = BoardPicker.aim(transform, middle.add(0D, 3D, 0D),
            new Vec3(0D, -1D, 0D), 24D);

        BoardTarget forSeatOne = BoardPicker.at(board, 1, field);
        assertNotNull(forSeatOne);
        assertEquals(55555, forSeatOne.code(),
            "seat 1's own cards are drawn on controller 1's half and must pick from there");
    }
}
