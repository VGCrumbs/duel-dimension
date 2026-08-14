package de.cas_ual_ty.dueldimension.clientutil.overworld;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.phys.Vec3;

/**
 * A monster standing on its card, turned to face whoever is looking, with its
 * wings behind it.
 * <p>
 * Turned about the UPRIGHT axis only, and deliberately. A quad that faces the
 * camera completely is the right answer for a particle and the wrong one for a
 * figure: lean over the board and a fully-billboarded Dark Magician tips onto
 * his back to keep looking at you. Turning him about his own vertical keeps him
 * standing, which is what a standing monster does.
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
     * How far behind the body the wings sit, in blocks.
     * <p>
     * Small, and it exists for the depth buffer rather than for the eye. Two
     * quads on one plane is a coin toss the driver gets to make every frame,
     * which is a wing that flickers in and out of its own body.
     */
    private static final double BEHIND = 0.02D;

    /** Draws a monster with no wings. */
    public static void submit(PoseStack poseStack, SubmitNodeCollector collector, Vec3 camera,
        Vec3 eye, Vec3 feet, float height, SpriteLayer body, int frame, int tint)
    {
        submit(poseStack, collector, camera, eye, feet, height, body, frame, null, 0, tint);
    }

    /**
     * Draws a monster, and its wings behind it.
     *
     * @param camera the point every corner is measured against, which is the
     *               same argument {@link WorldQuad} takes -- the caller's own
     *               origin, not necessarily the eye
     * @param eye    where the viewer actually is, which is what the sprite
     *               turns towards
     * @param feet   the world point the sprite stands on, in {@code camera}'s
     *               space
     * @param height how tall to draw the body, in blocks
     */
    public static void submit(PoseStack poseStack, SubmitNodeCollector collector, Vec3 camera,
        Vec3 eye, Vec3 feet, float height, SpriteLayer body, int frame, Wings wings,
        int wingFrame, int tint)
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

        // WINGS FIRST, and pushed away from the eye. Both are needed, because
        // the render type is not fixed: an opaque sprite goes through the
        // alpha-TESTED cutout, which writes depth, and there the offset is what
        // keeps the wing behind the body instead of z-fighting it. A
        // half-transparent one goes through the blended type, which writes no
        // depth at all, and there the ORDER is the only thing deciding what is
        // behind what.
        if(wings != null && wings.layer() != null)
        {
            double back = BEHIND;
            Vec3 middle = feet.add(faceX * -back, height * wings.anchor(), faceZ * -back);
            float wingHeight = height * wings.scale();
            float out = height * wings.spacing();

            Vec3 leftAt = middle.add(-rightX * out, 0D, -rightZ * out);
            Vec3 rightAt = middle.add(rightX * out, 0D, rightZ * out);
            float[] uv = wings.layer().uv(wingFrame);
            float wingHalf = wingHeight * wings.layer().aspect() / 2F;

            // Mirrored in UV SPACE, never by negating the right vector. The
            // corners are wound so the quad's normal points at the viewer;
            // reversing them to mirror the picture would turn the normal round
            // and the wing would be culled as a back face. Swapping u costs
            // nothing and cannot do that.
            quad(poseStack, collector, wings.layer(), camera, leftAt, rightX, rightZ, wingHalf,
                wingHeight, uv, true, tint);
            quad(poseStack, collector, wings.layer(), camera, rightAt, rightX, rightZ, wingHalf,
                wingHeight, uv, false, tint);
        }

        float[] uv = body.uv(frame);
        float half = height * body.aspect() / 2F;
        quad(poseStack, collector, body, camera, feet, rightX, rightZ, half, height, uv, false,
            tint);
    }

    /**
     * One upright quad standing on a point.
     *
     * @param centre  the middle of its bottom edge
     * @param mirror  whether to flip the picture left to right
     */
    private static void quad(PoseStack poseStack, SubmitNodeCollector collector, SpriteLayer layer,
        Vec3 camera, Vec3 centre, double rightX, double rightZ, float half, float height,
        float[] uv, boolean mirror, int tint)
    {
        Vec3 top = centre.add(0D, height, 0D);
        Vec3[] corners = new Vec3[] {
            centre.add(-rightX * half, 0D, -rightZ * half),
            top.add(-rightX * half, 0D, -rightZ * half),
            top.add(rightX * half, 0D, rightZ * half),
            centre.add(rightX * half, 0D, rightZ * half)};

        float u0 = mirror ? uv[2] : uv[0];
        float u1 = mirror ? uv[0] : uv[2];
        // Corner order is bottom-left, top-left, top-right, bottom-right, so v
        // runs from the cell's bottom at the feet to its top at the head.
        float[] us = {u0, u0, u1, u1};
        float[] vs = {uv[3], uv[1], uv[1], uv[3]};

        // The KIND follows the alpha, and it has to. SOLID is an alpha-TESTED
        // cutout: it writes depth, which is what a solid sprite wants, but a
        // tint of half alpha through it is not half a sprite -- the test either
        // keeps a texel or throws it away, so the sprite would come out whole.
        WorldQuad.Kind kind = (tint >>> 24) >= 0xFF ? WorldQuad.Kind.SOLID : WorldQuad.Kind.GLOW;
        WorldQuad.submit(poseStack, collector, kind, layer.texture(), camera, corners, tint, us,
            vs);
    }
}
