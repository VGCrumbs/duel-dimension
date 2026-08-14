package de.cas_ual_ty.dueldimension.clientutil.overworld;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
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
    public static void submit(PoseStack poseStack, SubmitNodeCollector collector,
        Identifier texture, Vec3 camera, Vec3[] corners, int tint)
    {
        submit(poseStack, collector, texture, camera, corners, tint, 0F, 0F, 1F, 1F);
    }

    /** The same, taking only part of the texture. */
    public static void submit(PoseStack poseStack, SubmitNodeCollector collector,
        Identifier texture, Vec3 camera, Vec3[] corners, int tint, float u0, float v0, float u1,
        float v1)
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

        PoseStack.Pose pose = poseStack.last();
        collector.submitCustomGeometry(poseStack,
            // The one entity render type established as truly unlit, by
            // FieldQuad and then by the Orichalcos seal. See PROJECTED_LIGHT:
            // a duel board that dimmed with the block light under it would be
            // unreadable in a cave and invisible at night, and it is a
            // projection thrown by a duel disk rather than a painted rug.
            net.minecraft.client.renderer.rendertype.RenderTypes.breezeWind(texture, 0F, 0F),
            (unused, buffer) ->
            {
                vertex(buffer, pose, xs[0], ys[0], zs[0], u0, v0, tint);
                vertex(buffer, pose, xs[1], ys[1], zs[1], u0, v1, tint);
                vertex(buffer, pose, xs[2], ys[2], zs[2], u1, v1, tint);
                vertex(buffer, pose, xs[3], ys[3], zs[3], u1, v0, tint);
            });
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y,
        float z, float u, float v, int tint)
    {
        buffer.addVertex(pose, x, y, z)
            .setColor(tint)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(PROJECTED_LIGHT)
            .setNormal(0F, 1F, 0F);
    }
}
