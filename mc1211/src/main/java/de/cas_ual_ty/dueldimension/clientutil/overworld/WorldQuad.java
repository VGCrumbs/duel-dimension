package de.cas_ual_ty.dueldimension.clientutil.overworld;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import de.cas_ual_ty.dueldimension.compat.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * One textured quad, anywhere in the world, given its four corners.
 * <p>
 * A deliberate sibling of {@code FieldQuad} rather than a reuse of it. That one
 * cannot serve here for three reasons, all of them in its code rather than its
 * intent: it pins every vertex to {@code z = 0}, so it can only draw on a flat
 * screen; it hardcodes full-bright; and its draw-order counter is a
 * <em>global static</em> shared with the 2D board, so a world renderer calling
 * into it would quietly perturb the ordering of the GUI board being drawn in the
 * same frame.
 * <p>
 * What it does keep from FieldQuad is that class's hard-won lesson: every
 * element the vertex format declares is written, every time. An element left
 * unset is not defaulted -- it reads back whatever happened to be in the buffer,
 * which is how a quad ends up black, invisible, or lit by the last thing drawn.
 */
public final class WorldQuad
{
    private WorldQuad()
    {
    }

    /** Full bright. The board is a projection, and a projection is its own light. */
    public static final int PROJECTED_LIGHT = 0xF000F0;

    /**
     * Whether a quad is a solid thing or a glow over one.
     * <p>
     * <b>This is the answer to the flickering.</b> A translucent render type
     * does not write to the depth buffer -- it blends, and relies on being
     * drawn in the right order -- so two translucent quads facing the camera at
     * similar distances have no defined winner and swap places as the camera
     * moves. Cards, mats and zone squares are not translucent things: they are
     * opaque pictures with transparent surroundings, which is exactly what an
     * alpha-TESTED cutout type is for. Cutout discards the clear pixels and
     * writes depth for the rest, so the card in front is in front because the
     * depth buffer says so and not because it happened to be drawn later.
     * <p>
     * Glows stay translucent, because a glow really is see-through and its
     * soft edge would be chopped to a hard one by an alpha test.
     * <p>
     * Both stay unlit: these vertices carry {@link #PROJECTED_LIGHT}, so a lit
     * render type lights them to full and the board reads the same at midnight
     * as at noon.
     */
    public enum Kind
    {
        /** A card, a mat, a zone square: opaque, and writes depth. */
        SOLID,
        /** A highlight or a beam: blended, and drawn over what it marks. */
        GLOW
    }

    private static net.minecraft.client.renderer.RenderType typeFor(Kind kind,
        ResourceLocation texture)
    {
        return kind == Kind.SOLID
            ? net.minecraft.client.renderer.RenderType.entityCutout(texture)
            : net.minecraft.client.renderer.RenderType.breezeWind(texture, 0F, 0F);
    }

    /**
     * Submits one quad, its corners given in world space and drawn relative to
     * the camera.
     * <p>
     * The corners are baked into the call and the pose captured first, because
     * {@code submitCustomGeometry} defers the draw: a {@link PoseStack.Pose}
     * taken from the stack is only good until something pushes over it, and the
     * next quad in a loop would.
     *
     * @param corners four world positions, wound anticlockwise seen from above,
     *                so the quad's face points up
     */
    /**
     * The only render type a tint can honestly be drawn through.
     * <p>
     * SOLID is an alpha-TESTED cutout: the test keeps a texel or throws it
     * away, and never mixes. Hand it a half-faded tint and it draws the thing
     * at full strength anyway, then drops the lot in one frame when the alpha
     * crosses the threshold. That is why a five second fade of the duel board
     * looked like the board blinking out of existence -- the arithmetic was
     * right and the render type was discarding the answer.
     * <p>
     * Here rather than in each renderer, because three of them had to learn it
     * separately and the third one only after the first two had already been
     * fixed. Anything short of opaque blends; blending writes no depth, which
     * for flat pieces submitted in the order they are stacked is no loss.
     */
    public static Kind kindFor(int tint)
    {
        return (tint >>> 24) >= 0xFF ? Kind.SOLID : Kind.GLOW;
    }

    public static void submit(PoseStack poseStack, SubmitNodeCollector collector,
        ResourceLocation texture, Vec3 camera, Vec3[] corners, int tint)
    {
        submit(poseStack, collector, texture, camera, corners, tint, 0F, 0F, 1F, 1F);
    }

    /** The same, saying whether this is a solid thing or a glow over one. */
    public static void submit(PoseStack poseStack, SubmitNodeCollector collector, Kind kind,
        ResourceLocation texture, Vec3 camera, Vec3[] corners, int tint)
    {
        submit(poseStack, collector, kind, texture, camera, corners, tint,
            new float[] {0F, 0F, 1F, 1F}, new float[] {0F, 1F, 1F, 0F});
    }

    /** The same, taking only part of the texture. */
    public static void submit(PoseStack poseStack, SubmitNodeCollector collector,
        ResourceLocation texture, Vec3 camera, Vec3[] corners, int tint, float u0, float v0, float u1,
        float v1)
    {
        submit(poseStack, collector, texture, camera, corners, tint,
            new float[] {u0, u0, u1, u1}, new float[] {v0, v1, v1, v0});
    }

    public static void submit(PoseStack poseStack, SubmitNodeCollector collector, Kind kind,
        ResourceLocation texture, Vec3 camera, Vec3[] corners, int tint, float u0, float v0, float u1,
        float v1)
    {
        submit(poseStack, collector, kind, texture, camera, corners, tint,
            new float[] {u0, u0, u1, u1}, new float[] {v0, v1, v1, v0});
    }

    /**
     * The same again, with a texture coordinate per corner -- which is how a
     * card's art is turned to face its owner without turning the card itself
     * off its zone.
     */
    public static void submit(PoseStack poseStack, SubmitNodeCollector collector,
        ResourceLocation texture, Vec3 camera, Vec3[] corners, int tint, float[] us, float[] vs)
    {
        submit(poseStack, collector, Kind.GLOW, texture, camera, corners, tint, us, vs);
    }

    public static void submit(PoseStack poseStack, SubmitNodeCollector collector, Kind kind,
        ResourceLocation texture, Vec3 camera, Vec3[] corners, int tint, float[] us, float[] vs)
    {
        // Relative to the camera in doubles before anything becomes a float:
        // world coordinates run to millions and a float loses whole blocks out
        // there, which shows up as a board that jitters far from spawn.
        float[] xs = new float[4];
        float[] ys = new float[4];
        float[] zs = new float[4];
        for(int corner = 0; corner < 4; corner++)
        {
            xs[corner] = (float)(corners[corner].x - camera.x);
            ys[corner] = (float)(corners[corner].y - camera.y);
            zs[corner] = (float)(corners[corner].z - camera.z);
        }

        // The face's normal, from its own winding, rather than a hardcoded
        // "up". The board's pieces are all ground-plane quads; a card is a
        // solid, and its four edges point sideways.
        float[] normal = normalOf(xs, ys, zs);

        // COPIED, not referenced. PoseStack.last() hands back the live Pose
        // object and the stack RECYCLES it: the next pushPose overwrites the
        // very matrix this lambda is holding, and submitCustomGeometry does not
        // draw until later. On the duel board that never showed, because that
        // renderer sits at the base of the stack and nothing pops over it. A
        // block entity is the opposite case -- the level renderer pushes,
        // translates, submits and pops immediately -- so by the time the quad
        // was filled its pose had been reused for something else entirely, and
        // the card came out nowhere near the block it belonged to.
        PoseStack.Pose pose = poseStack.last().copy();
        collector.submitCustomGeometry(poseStack, typeFor(kind, texture),
            (unused, buffer) ->
            {
                for(int corner = 0; corner < 4; corner++)
                {
                    vertex(buffer, pose, xs[corner], ys[corner], zs[corner], us[corner],
                        vs[corner], tint, normal);
                }
            });
    }

    /** The unit normal of the quad, from the cross product of two of its edges. */
    private static float[] normalOf(float[] xs, float[] ys, float[] zs)
    {
        float ax = xs[1] - xs[0];
        float ay = ys[1] - ys[0];
        float az = zs[1] - zs[0];
        float bx = xs[3] - xs[0];
        float by = ys[3] - ys[0];
        float bz = zs[3] - zs[0];
        float nx = ay * bz - az * by;
        float ny = az * bx - ax * bz;
        float nz = ax * by - ay * bx;
        float length = (float)Math.sqrt(nx * nx + ny * ny + nz * nz);
        // A degenerate quad has no normal to speak of; straight up is the
        // answer that cannot make anything worse.
        return length < 1e-6F ? new float[] {0F, 1F, 0F}
            : new float[] {nx / length, ny / length, nz / length};
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y,
        float z, float u, float v, int tint, float[] normal)
    {
        buffer.addVertex(pose, x, y, z)
            .setColor(tint)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(PROJECTED_LIGHT)
            .setNormal(normal[0], normal[1], normal[2]);
    }
}
