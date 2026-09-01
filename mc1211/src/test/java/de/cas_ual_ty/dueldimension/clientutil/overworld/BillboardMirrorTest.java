package de.cas_ual_ty.dueldimension.clientutil.overworld;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That a sprite is drawn the way round it was painted.
 * <p>
 * Every sprite in the world was mirrored, and it survived a long time because
 * almost every monster is roughly symmetrical -- a reflected dragon is a
 * dragon. It shows up on the ones holding something in one hand, and once seen
 * it cannot be unseen on any of them.
 * <p>
 * The trap is the word "right". The billboard's horizontal axis is built as
 * {@code (-faceZ, faceX)} from the direction the sprite turns to face, and that
 * is the viewer's LEFT: a viewer looking at the sprite has forward {@code -face}
 * and up {@code +Y}, so their right is {@code cross(forward, up)}, which is
 * {@code (faceZ, -faceX)} -- the negation. Anything reasoning about it as
 * "right" gets the picture back to front, which is what happened.
 */
class BillboardMirrorTest
{
    /** Where a viewer's right hand points, given the sprite-to-eye direction. */
    private static double[] viewersRight(double faceX, double faceZ)
    {
        // forward = -face, up = +Y, right = cross(forward, up).
        double fx = -faceX;
        double fz = -faceZ;
        // cross((fx,0,fz), (0,1,0)) = (0*0 - fz*1, fz*0 - fx*0, fx*1 - 0*0)
        return new double[] {-fz, fx};
    }

    @Test
    void the_quad_axis_is_the_viewers_left()
    {
        // Viewer due SOUTH of the sprite, so face is +Z.
        double[] axis = MonsterBillboard.screenAxis(0D, 1D);
        double[] right = viewersRight(0D, 1D);
        assertEquals(-right[0], axis[0], 1e-9, "opposite on x");
        assertEquals(-right[1], axis[1], 1e-9, "opposite on z");
    }

    @Test
    void that_holds_from_every_angle()
    {
        for(int degrees = 0; degrees < 360; degrees += 15)
        {
            double faceX = Math.cos(Math.toRadians(degrees));
            double faceZ = Math.sin(Math.toRadians(degrees));
            double[] axis = MonsterBillboard.screenAxis(faceX, faceZ);
            double[] right = viewersRight(faceX, faceZ);
            assertEquals(-right[0], axis[0], 1e-9, "at " + degrees);
            assertEquals(-right[1], axis[1], 1e-9, "at " + degrees);
        }
    }

    /**
     * The fix itself. {@code quad} lays the returned pair onto the corners at
     * MINUS the axis first, then PLUS -- so unmirrored art must put the cell's
     * left edge second, on the plus side, which is the viewer's left.
     */
    @Test
    void unmirrored_art_puts_the_cells_left_edge_on_the_viewers_left()
    {
        float[] uv = {0.25F, 0F, 0.75F, 1F};
        float[] ends = MonsterBillboard.uEnds(uv, false);
        assertEquals(uv[2], ends[0], "the cell's RIGHT edge goes to the viewer's right");
        assertEquals(uv[0], ends[1], "the cell's LEFT edge goes to the viewer's left");
    }

    @Test
    void asking_for_a_mirror_swaps_exactly_that()
    {
        float[] uv = {0.25F, 0F, 0.75F, 1F};
        float[] plain = MonsterBillboard.uEnds(uv, false);
        float[] flipped = MonsterBillboard.uEnds(uv, true);
        assertEquals(plain[0], flipped[1]);
        assertEquals(plain[1], flipped[0]);
    }

    /**
     * A wing pair is one cell and its reflection, placed symmetrically, so the
     * two must disagree about which end of the cell they read -- otherwise both
     * wings point the same way and the monster has one wing on backwards.
     */
    @Test
    void a_wing_and_its_reflection_read_opposite_ends()
    {
        float[] uv = {0F, 0F, 1F, 1F};
        assertTrue(MonsterBillboard.uEnds(uv, true)[0] != MonsterBillboard.uEnds(uv, false)[0]);
    }
}
