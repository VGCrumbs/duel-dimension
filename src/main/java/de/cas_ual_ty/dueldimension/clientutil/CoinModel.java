package de.cas_ual_ty.dueldimension.clientutil;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.resources.Identifier;

/**
 * A real coin, built out of triangles, for the toss the engine announces.
 * <p>
 * A flat quad showing one of two pictures is what this replaces, and it never
 * looked like a coin because a coin is only recognisable while it turns: the
 * disc narrowing to an edge and opening out again is the whole read. So this is
 * a solid of revolution — two struck faces and the milled band between them —
 * turned about its horizontal axis.
 * <p>
 * <b>Where the 3D comes from.</b> The board is drawn inside a
 * picture-in-picture pass ({@link BoardPip}), and what that hands back is a raw
 * {@code VertexConsumer} and a real {@code PoseStack}. Everything else on the
 * board only ever needed flat quads, so {@link FieldQuad} pins z to zero; this
 * does not, and rotates the pose instead. The projection is orthographic, so a
 * turned disc becomes an ellipse rather than a perspective cone — which is
 * exactly how a coin reads at this size.
 * <p>
 * <b>Ordering, not depth.</b> The faces are emitted far-then-near rather than
 * relying on the depth buffer, because the pass blends and its depth state is
 * not ours to assume. Within one submission the triangles reach the buffer in
 * the order they are written, so writing them back-to-front is the whole of it.
 * The one subtlety is that the picture-in-picture pass scales z by
 * <em>minus</em> the GUI scale, so a larger local z is FURTHER away, not
 * nearer; the comparison below is written the way it is for that reason.
 */
public final class CoinModel
{
    /** Heads, tails and the milled edge, in one file. */
    public static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
        de.cas_ual_ty.dueldimension.DuelDimension.MOD_ID, "textures/duel/coin_model.png");

    /**
     * The atlas, in fractions. The faces share the top two thirds side by
     * side; the milled band runs the full width of the bottom third so a
     * vertex's angle around the coin can index it directly.
     */
    private static final float FACE_V1 = 128F / 192F;
    private static final float RIM_V0 = FACE_V1;

    /** How many segments the circle is cut into. */
    private static final int SEGMENTS = 32;

    /** The vertex format the board's unlit pipeline expects. */
    private static final int FULL_BRIGHT = 0xF000F0;

    private CoinModel()
    {
    }

    /**
     * Draws one coin.
     *
     * @param x y      where its centre sits, in board pixels
     * @param radius   half its width
     * @param thick    half its thickness, so the faces sit at plus and minus this
     * @param spin     how far it has turned about the horizontal axis, radians
     * @param heads    which face the toss came up; it is the one at the near
     *                 side, so landing on a whole turn shows the result
     * @param alpha    0..1, for the fade the announcement ends on
     */
    public static void draw(PoseStack poseStack, SubmitNodeCollector collector,
        float x, float y, float radius, float thick, float spin, boolean heads, float alpha)
    {
        int tint = ScreenUtil.colour(1F, 1F, 1F, Math.max(0F, Math.min(1F, alpha)));

        poseStack.pushPose();
        poseStack.translate(x, y, 0F);
        poseStack.mulPose(Axis.XP.rotation(spin));

        // Which way round to write them. A face's centre at local z = +thick
        // ends up at z = thick * cos(spin) once turned, and larger is further,
        // so a positive cosine means the +thick face is the one at the back.
        boolean plusIsFar = Math.cos(spin) > 0;

        FieldQuad.submit(poseStack, collector, TEXTURE, (pose, buffer) ->
        {
            if(plusIsFar)
            {
                face(buffer, pose, radius, thick, !heads, tint);
                rim(buffer, pose, radius, thick, tint);
                face(buffer, pose, radius, -thick, heads, tint);
            }
            else
            {
                face(buffer, pose, radius, -thick, heads, tint);
                rim(buffer, pose, radius, thick, tint);
                face(buffer, pose, radius, thick, !heads, tint);
            }
        });

        poseStack.popPose();
    }

    /**
     * One struck face, as a fan of quads about its centre.
     * <p>
     * The two faces sample the same picture upside down from each other,
     * because they are seen from opposite sides: without that the letter on the
     * far one reads mirrored the moment the coin turns past its edge.
     */
    private static void face(VertexConsumer buffer, PoseStack.Pose pose,
        float radius, float z, boolean isHeads, int tint)
    {
        float u0 = isHeads ? 0F : 0.5F;
        float u1 = isHeads ? 0.5F : 1F;
        // The face at +z is the one seen from behind, so its v runs the other
        // way. Which is which is decided by the sign, not by the caller.
        boolean flip = z > 0;

        for(int i = 0; i < SEGMENTS; i++)
        {
            double a0 = i * 2 * Math.PI / SEGMENTS;
            double a1 = (i + 1) * 2 * Math.PI / SEGMENTS;
            // A quad per segment with two vertices at the centre: a triangle
            // fan written as quads, which is what this buffer takes.
            faceVertex(buffer, pose, 0F, 0F, z, u0, u1, flip, 0F, 0F, tint);
            faceVertex(buffer, pose, 0F, 0F, z, u0, u1, flip, 0F, 0F, tint);
            faceVertex(buffer, pose, (float)(Math.cos(a1) * radius),
                (float)(Math.sin(a1) * radius), z, u0, u1, flip,
                (float)Math.cos(a1), (float)Math.sin(a1), tint);
            faceVertex(buffer, pose, (float)(Math.cos(a0) * radius),
                (float)(Math.sin(a0) * radius), z, u0, u1, flip,
                (float)Math.cos(a0), (float)Math.sin(a0), tint);
        }
    }

    /** A face vertex, with the disc mapped across its half of the atlas. */
    private static void faceVertex(VertexConsumer buffer, PoseStack.Pose pose,
        float x, float y, float z, float u0, float u1, boolean flip,
        float unitX, float unitY, int tint)
    {
        float u = u0 + (0.5F + unitX / 2F) * (u1 - u0);
        float v = (flip ? 0.5F - unitY / 2F : 0.5F + unitY / 2F) * FACE_V1;
        vertex(buffer, pose, x, y, z, u, v, tint);
    }

    /**
     * The milled band, as a ring of quads.
     * <p>
     * Its u runs once around the coin, so the reeding in the texture wraps the
     * edge exactly once however many segments the circle is cut into.
     */
    private static void rim(VertexConsumer buffer, PoseStack.Pose pose,
        float radius, float thick, int tint)
    {
        for(int i = 0; i < SEGMENTS; i++)
        {
            double a0 = i * 2 * Math.PI / SEGMENTS;
            double a1 = (i + 1) * 2 * Math.PI / SEGMENTS;
            float x0 = (float)(Math.cos(a0) * radius);
            float y0 = (float)(Math.sin(a0) * radius);
            float x1 = (float)(Math.cos(a1) * radius);
            float y1 = (float)(Math.sin(a1) * radius);
            float u0 = i / (float)SEGMENTS;
            float u1 = (i + 1) / (float)SEGMENTS;

            vertex(buffer, pose, x0, y0, -thick, u0, RIM_V0, tint);
            vertex(buffer, pose, x0, y0, thick, u0, 1F, tint);
            vertex(buffer, pose, x1, y1, thick, u1, 1F, tint);
            vertex(buffer, pose, x1, y1, -thick, u1, RIM_V0, tint);
        }
    }

    /**
     * As {@code FieldQuad.vertex}, but with a real z.
     * <p>
     * Every element the format declares is written. An unset one is not zero;
     * it is whatever the buffer held last, which is how geometry ends up
     * black, invisible or lit by a value from another draw.
     */
    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose,
        float x, float y, float z, float u, float v, int tint)
    {
        buffer.addVertex(pose, x, y, z)
            .setColor(tint)
            .setUv(u, v)
            .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
            .setLight(FULL_BRIGHT)
            .setNormal(0F, 0F, 1F);
    }
}
