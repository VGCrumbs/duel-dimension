package de.cas_ual_ty.dueldimension.clientutil.overworld;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Which of a Doom sheet's eight views faces the camera.
 * <p>
 * <b>This is agreement with another program, so it cannot be checked against
 * itself.</b> An earlier version of this file did exactly that -- it asserted
 * the front view faces the viewer and the back view is four rows on -- and
 * passed while being precisely backwards, because 0 and 4 are both their own
 * negation modulo 8. In game the monster turned the wrong way: step to its
 * left and it showed you what was on its right.
 * <p>
 * So the fixture is the BAKER's convention, written out here, and the
 * assertions follow from it:
 * <ul>
 * <li>the baker advances its camera yaw by one step per row, and places the
 * eye at {@code (sin yaw, ., cos yaw)} -- so rows advance from +Z towards +X;
 * <li>the model faces +Z, being glTF, and its own left is +X (right is
 * {@code forward cross up}, which is -X);
 * <li>therefore <b>row 2 of 8 is the view from the monster's LEFT</b>, row 6
 * is from its right, and row 4 is its back.
 * </ul>
 * If the baker's rotation order is ever changed, this file is what should fail.
 */
class SpriteDirectionTest
{
    private static final Vec3 FEET = new Vec3(0D, 0D, 0D);

    private static SpriteLayer sheet(int directions)
    {
        return new SpriteLayer("x", 0, 0, 0, 0, 4, directions, 0, 4, 5,
            MonsterSprites.Loop.LOOP, 0, 0, 0, java.util.List.of(), directions);
    }

    /** Minecraft's compass, as offsets from the monster. */
    private static final Vec3 SOUTH = new Vec3(0D, 0D, 8D);
    private static final Vec3 NORTH = new Vec3(0D, 0D, -8D);
    private static final Vec3 WEST = new Vec3(-8D, 0D, 0D);
    private static final Vec3 EAST = new Vec3(8D, 0D, 0D);

    /** Heading 0 is SOUTH, so a monster facing south has its left to the EAST. */
    private static final float FACING_SOUTH = 0F;

    @Test
    void a_viewer_standing_where_the_monster_looks_sees_its_front()
    {
        assertEquals(0, MonsterSprites.directionAt(sheet(8), SOUTH, FEET, FACING_SOUTH));
    }

    @Test
    void a_viewer_behind_it_sees_the_back()
    {
        assertEquals(4, MonsterSprites.directionAt(sheet(8), NORTH, FEET, FACING_SOUTH));
    }

    /**
     * The test the old one got wrong, and the only one that could have caught
     * it: rows advance towards the monster's LEFT, which for a south-facing
     * monster is east.
     */
    @Test
    void rows_advance_towards_the_monsters_left()
    {
        assertEquals(2, MonsterSprites.directionAt(sheet(8), EAST, FEET, FACING_SOUTH),
            "two steps round to the monster's left");
        assertEquals(6, MonsterSprites.directionAt(sheet(8), WEST, FEET, FACING_SOUTH),
            "six, which is two the other way");
    }

    @Test
    void the_two_sides_are_opposite_rows()
    {
        int east = MonsterSprites.directionAt(sheet(8), EAST, FEET, FACING_SOUTH);
        int west = MonsterSprites.directionAt(sheet(8), WEST, FEET, FACING_SOUTH);
        assertEquals(4, Math.floorMod(east - west, 8), "a side and its opposite");
    }

    @Test
    void turning_the_monster_turns_which_view_is_front()
    {
        // Facing NORTH, so the front is now the viewer to the north -- and the
        // monster's left is now WEST, so west is where row 2 moves to.
        assertEquals(0, MonsterSprites.directionAt(sheet(8), NORTH, FEET, 180F));
        assertEquals(4, MonsterSprites.directionAt(sheet(8), SOUTH, FEET, 180F));
        assertEquals(2, MonsterSprites.directionAt(sheet(8), WEST, FEET, 180F));
    }

    /**
     * A quarter turn of the monster is two rows the OTHER way: turning the
     * creature left is the same, to the sprite, as the viewer stepping right.
     */
    @Test
    void turning_the_monster_and_moving_the_viewer_cancel()
    {
        for(int turn = 0; turn < 360; turn += 45)
        {
            int still = MonsterSprites.directionAt(sheet(8), EAST, FEET, turn);
            int expected = Math.floorMod(2 + turn / 45, 8);
            assertEquals(expected, still, "heading " + turn);
        }
    }

    @Test
    void each_row_covers_the_angles_centred_on_it_rather_than_starting_at_it()
    {
        // Just short of half a step towards the monster's left is still the
        // front. Floor would have moved on a row ago, showing the front view
        // for only the far half of its arc.
        Vec3 nearlyHalfAStep = new Vec3(8D * Math.tan(Math.toRadians(22D)), 0D, 8D);
        assertEquals(0, MonsterSprites.directionAt(sheet(8), nearlyHalfAStep, FEET,
            FACING_SOUTH));
        Vec3 justPast = new Vec3(8D * Math.tan(Math.toRadians(23D)), 0D, 8D);
        assertEquals(1, MonsterSprites.directionAt(sheet(8), justPast, FEET, FACING_SOUTH));
    }

    @Test
    void an_ordinary_sheet_has_one_view_and_is_never_rotated()
    {
        SpriteLayer plain = sheet(1);
        assertEquals(0, MonsterSprites.directionAt(plain, NORTH, FEET, FACING_SOUTH));
        // facing() hands back the same object, so a monster with one view pays
        // nothing per frame for a feature it does not use.
        assertSame(plain, plain.facing(3));
    }

    @Test
    void a_view_is_a_row_of_cells_further_into_the_sheet()
    {
        SpriteLayer eight = sheet(8);
        // Four columns per row, so row 3 starts at cell 12 and its second frame
        // is cell 13 -- which is the whole trick that leaves uv() untouched.
        assertEquals(12, eight.facing(3).cell(0));
        assertEquals(13, eight.facing(3).cell(1));
        assertEquals(0, eight.facing(0).cell(0));
    }

    @Test
    void a_direction_past_the_end_wraps_rather_than_reading_off_the_sheet()
    {
        assertEquals(sheet(8).facing(1).first(), sheet(8).facing(9).first());
    }
}
