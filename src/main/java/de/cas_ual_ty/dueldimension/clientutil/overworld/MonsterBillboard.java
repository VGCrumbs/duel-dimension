package de.cas_ual_ty.dueldimension.clientutil.overworld;

import com.mojang.blaze3d.vertex.PoseStack;
import de.cas_ual_ty.dueldimension.clientutil.DuelTextures;
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
 * <p>
 * <b>A trim never moves anything.</b> Every sprite occupies a box decided by
 * the WHOLE cell -- {@code height} tall and {@code height * aspect()} wide --
 * and the trim only shrinks the part of that box which gets drawn, symmetrically
 * about its own middle. So the pixels that survive a crop land exactly where
 * they landed before it, at exactly the size they were. That is the whole point
 * of a crop: it decides what is read, not where the monster stands.
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

    /**
     * And the outline sits in front of the art, by more than the wings sit behind it.
     * <p>
     * More, and that is the whole reason for the number. The wings are a body's
     * width behind the body, so an outline pushed forward by the same {@link
     * #BEHIND} would land exactly on the body's plane -- and the body writes
     * depth while the outline does not, so a wing's box would be swallowed
     * wherever the dragon stands in front of it. Clearing that plane is what
     * keeps the box a box rather than two arcs either side of a monster.
     */
    private static final double FRONT = 0.05D;

    /** The editor's own gold and blue, so the box and the sheet grid agree. */
    private static final int BODY_LINE = 0xFFF4D089;
    private static final int BODY_FAINT = 0x50F4D089;
    private static final int WING_LINE = 0xFF63C8FF;
    private static final int WING_FAINT = 0x5063C8FF;

    /** Draws a monster with no wings. */
    public static void submit(PoseStack poseStack, SubmitNodeCollector collector, Vec3 camera,
        Vec3 eye, Vec3 feet, float height, SpriteLayer body, int frame, int tint, long code)
    {
        submit(poseStack, collector, camera, eye, feet, height, body, frame, null, 0, tint, code);
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
     * @param height how tall to draw the body, in blocks -- the WHOLE cell's
     *               height, margins included, so that cropping the margins away
     *               leaves the art at the size it already was
     * @param code   whose hologram this is, so the editor can outline the one
     *               card it is cropping and leave every other monster alone
     */
    public static void submit(PoseStack poseStack, SubmitNodeCollector collector, Vec3 camera,
        Vec3 eye, Vec3 feet, float height, SpriteLayer body, int frame, Wings wings,
        int wingFrame, int tint, long code)
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

        boolean outlined = BillboardOutline.wants(code);

        // WINGS FIRST, and pushed away from the eye. Both are needed, because
        // the render type is not fixed: an opaque sprite goes through the
        // alpha-TESTED cutout, which writes depth, and there the offset is what
        // keeps the wing behind the body instead of z-fighting it. A
        // half-transparent one goes through the blended type, which writes no
        // depth at all, and there the ORDER is the only thing deciding what is
        // behind what.
        if(wings != null && wings.layer() != null)
        {
            SpriteLayer layer = wings.layer();
            double back = BEHIND;
            float wingHeight = height * wings.scale();
            float wingHalf = wingHeight * layer.aspect() / 2F;

            // The anchor is the wing's MIDDLE, not the bottom of it. A wing is
            // a thing you line up with a shoulder, and a shoulder is in the
            // middle of the wing rather than under it -- anchoring by the
            // bottom edge meant every change of wing height also moved the
            // wing, and no two settings could be tuned independently.
            Vec3 middle = feet.add(faceX * -back, height * wings.anchor(), faceZ * -back);

            // Spacing is the gap between the body's centre line and the wing's
            // INNER edge, so zero means the pair meets in the middle and every
            // step outwards is a step you can see. Measured from the centre of
            // the quad, the sprite's own transparent margin counted as spacing
            // too, which is why they sat so far out with nothing to trim.
            //
            // Measured against the WHOLE cell, so that cropping a wing's margin
            // away does not tow the wing inwards behind it. Spacing moves the
            // wings; trim decides what is drawn in them. Two controls, two jobs.
            float out = height * wings.spacing() + wingHalf;

            float drawnHeight = wingHeight * layer.spanY();
            float drawnHalf = wingHalf * layer.spanX();
            double lift = -drawnHeight / 2D;

            Vec3 leftAt = middle.add(-rightX * out, lift, -rightZ * out);
            Vec3 rightAt = middle.add(rightX * out, lift, rightZ * out);
            float[] uv = layer.uv(wingFrame);

            // Mirrored in UV SPACE, never by negating the right vector. The
            // corners are wound so the quad's normal points at the viewer;
            // reversing them to mirror the picture would turn the normal round
            // and the wing would be culled as a back face. Swapping u costs
            // nothing and cannot do that.
            quad(poseStack, collector, layer, camera, leftAt, rightX, rightZ, drawnHalf,
                drawnHeight, uv, true, tint);
            quad(poseStack, collector, layer, camera, rightAt, rightX, rightZ, drawnHalf,
                drawnHeight, uv, false, tint);

            if(outlined)
            {
                double full = -wingHeight / 2D;
                Vec3 leftFull = middle.add(-rightX * out, full, -rightZ * out);
                Vec3 rightFull = middle.add(rightX * out, full, rightZ * out);
                box(poseStack, collector, camera, leftFull, rightX, rightZ, wingHalf, wingHeight,
                    faceX, faceZ, WING_FAINT);
                box(poseStack, collector, camera, rightFull, rightX, rightZ, wingHalf, wingHeight,
                    faceX, faceZ, WING_FAINT);
                box(poseStack, collector, camera, leftAt, rightX, rightZ, drawnHalf, drawnHeight,
                    faceX, faceZ, WING_LINE);
                box(poseStack, collector, camera, rightAt, rightX, rightZ, drawnHalf, drawnHeight,
                    faceX, faceZ, WING_LINE);
            }
        }

        float[] uv = body.uv(frame);
        float half = height * body.aspect() / 2F;
        float drawnHeight = height * body.spanY();
        float drawnHalf = half * body.spanX();
        // A symmetric trim keeps the sampled box centred on the cell, so the
        // drawn box shares the whole box's middle and its bottom rises by
        // exactly what was cut from underneath. Standing it back on the feet
        // instead is what used to drag a monster downwards and stretch it as
        // the vertical crop tightened.
        Vec3 stand = feet.add(0D, (height - drawnHeight) / 2D, 0D);
        quad(poseStack, collector, body, camera, stand, rightX, rightZ, drawnHalf, drawnHeight,
            uv, false, tint);

        if(outlined)
        {
            // The whole cell faintly and the crop brightly, because the useful
            // thing to see while cropping is not where the box is but how much
            // of the cell it has given up.
            box(poseStack, collector, camera, feet, rightX, rightZ, half, height, faceX, faceZ,
                BODY_FAINT);
            box(poseStack, collector, camera, stand, rightX, rightZ, drawnHalf, drawnHeight,
                faceX, faceZ, BODY_LINE);
        }
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

    /**
     * Four thin bars around where a quad stands, in front of it.
     * <p>
     * In FRONT, because the box exists to be compared against the art it
     * encloses and a line the sprite can hide is a line you cannot line
     * anything up with. Blended rather than cutout for the same reason: it
     * writes no depth, so nothing drawn afterwards has to fight it.
     * <p>
     * The bars thicken with the monster. A fixed width is a hairline on a
     * five-block dragon and a stripe across a half-block chick, and either way
     * the thing being judged is the edge of the ART, not the edge of the line
     * drawn over it.
     */
    private static void box(PoseStack poseStack, SubmitNodeCollector collector, Vec3 camera,
        Vec3 centre, double rightX, double rightZ, float half, float height, double faceX,
        double faceZ, int colour)
    {
        Vec3 at = centre.add(faceX * FRONT, 0D, faceZ * FRONT);
        float thick = Math.max(0.004F, height * 0.012F);
        double lx = -rightX * half;
        double lz = -rightZ * half;
        double rx = rightX * half;
        double rz = rightZ * half;

        bar(poseStack, collector, camera, at.add(lx, 0D, lz), at.add(rx, 0D, rz), thick, colour);
        bar(poseStack, collector, camera, at.add(lx, height - thick, lz),
            at.add(rx, height - thick, rz), thick, colour);
        bar(poseStack, collector, camera, at.add(lx, 0D, lz),
            at.add(lx + rightX * thick, 0D, lz + rightZ * thick), height, colour);
        bar(poseStack, collector, camera, at.add(rx - rightX * thick, 0D, rz - rightZ * thick),
            at.add(rx, 0D, rz), height, colour);
    }

    /** One upright bar between two points, wound like every other quad here. */
    private static void bar(PoseStack poseStack, SubmitNodeCollector collector, Vec3 camera,
        Vec3 from, Vec3 to, float tall, int colour)
    {
        Vec3[] corners = new Vec3[] {from, from.add(0D, tall, 0D), to.add(0D, tall, 0D), to};
        WorldQuad.submit(poseStack, collector, WorldQuad.Kind.GLOW, DuelTextures.WHITE, camera,
            corners, colour);
    }
}
