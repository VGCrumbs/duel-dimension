package de.cas_ual_ty.dueldimension.clientutil.overworld;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.phys.Vec3;

/**
 * A monster standing on its card, turned to face whoever is looking.
 * <p>
 * Turned about the UPRIGHT axis only, and deliberately. A quad that faces the
 * camera completely is the right answer for a particle and the wrong one for a
 * figure: lean over the board and a fully-billboarded Dark Magician tips onto
 * his back to keep looking at you. Turning him about his own vertical keeps him
 * standing, which is what a standing monster does -- it is the same choice
 * Doom's own sprites make, and the reason they read as characters rather than
 * as stickers.
 * <p>
 * Built from world-space corners rather than from an inverted view matrix.
 * There is a well-known way to do this by taking the model-view basis and
 * transposing it, and it is a trap in deferred rendering: the matrix at the
 * moment the quad is FILLED is not the matrix that was current when it was
 * submitted. Two vectors and a cross product need no such assumption.
 */
public final class MonsterBillboard
{
    private MonsterBillboard()
    {
    }

    /**
     * Draws one sprite standing at a point.
     *
     * @param camera the point every corner is measured against, which is the
     *               same argument {@link WorldQuad} takes -- the caller's own
     *               origin, not necessarily the eye
     * @param eye    where the viewer actually is, which is what the sprite
     *               turns towards
     * @param feet   the world point the sprite stands on, in the same space as
     *               {@code camera}
     * @param height how tall to draw it, in blocks
     */
    public static void submit(PoseStack poseStack, SubmitNodeCollector collector, Vec3 camera,
        Vec3 eye, Vec3 feet, float height, MonsterSprites.Sheet sheet, int frame, int tint)
    {
        // Towards the viewer, flattened. Taking only x and z is what keeps the
        // sprite upright: the height of the eye is exactly the part of the
        // direction that must NOT reach the geometry.
        double dx = eye.x - feet.x;
        double dz = eye.z - feet.z;
        double flat = Math.sqrt(dx * dx + dz * dz);
        if(flat < 1e-4D)
        {
            // Directly overhead, where "which way is the camera" has no answer.
            // Any facing will do and none of them will be seen edge-on, so this
            // picks one rather than dividing by nothing.
            dx = 0D;
            dz = 1D;
            flat = 1D;
        }
        double faceX = dx / flat;
        double faceZ = dz / flat;
        // Perpendicular in the ground plane, which with the upright axis makes
        // a basis whose normal points back along the facing -- at the viewer.
        double rightX = -faceZ;
        double rightZ = faceX;

        float half = height * sheet.aspect() / 2F;
        Vec3 top = feet.add(0D, height, 0D);
        Vec3[] corners = new Vec3[] {
            feet.add(-rightX * half, 0D, -rightZ * half),
            top.add(-rightX * half, 0D, -rightZ * half),
            top.add(rightX * half, 0D, rightZ * half),
            feet.add(rightX * half, 0D, rightZ * half)};

        // Half a texel in from each side. Sampling exactly on the seam between
        // two cells borrows a column of the next frame while the quad is
        // scaled, which reads as a sliver of the wrong pose down one edge --
        // the same correction the phase bar's atlas needs, for the same reason.
        float cell = 1F / sheet.frames();
        float inset = cell * 0.001F;
        float u0 = frame * cell + inset;
        float u1 = (frame + 1) * cell - inset;

        // Corner order is bottom-left, top-left, top-right, bottom-right, so v
        // runs from 1 at the feet to 0 at the head.
        //
        // The KIND follows the alpha, and it has to. SOLID is an alpha-TESTED
        // cutout: it writes depth, which is what a solid sprite wants, but a
        // tint of half alpha through it is not half a sprite -- the test either
        // keeps a texel or throws it away, so the sprite would come out whole.
        // Anything short of opaque therefore goes through the blended type,
        // which is the only one that can draw half of something.
        WorldQuad.Kind kind = (tint >>> 24) >= 0xFF ? WorldQuad.Kind.SOLID : WorldQuad.Kind.GLOW;
        WorldQuad.submit(poseStack, collector, kind, sheet.texture(), camera,
            corners, tint, new float[] {u0, u0, u1, u1}, new float[] {1F, 0F, 0F, 1F});
    }
}
