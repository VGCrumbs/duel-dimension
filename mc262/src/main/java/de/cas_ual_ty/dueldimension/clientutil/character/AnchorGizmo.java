package de.cas_ual_ty.dueldimension.clientutil.character;

import com.mojang.blaze3d.vertex.PoseStack;
import de.cas_ual_ty.dueldimension.clientutil.model.ModelMesh;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * The handles you tune an item anchor with, drawn where the item is.
 *
 * <h2>Blender's colours, because they are the ones people already know</h2>
 * X red, Y green, Z blue, the same three every 3D tool has used for thirty
 * years. A gizmo that invented its own would have to be learnt, and this exists
 * to save learning.
 * <p>
 * Six arrows rather than three, because a slider goes both ways and an axis that
 * only points one way leaves you guessing which end of it you are on. Each is
 * labelled at the TIP, where the eye already is once it has followed the arrow.
 *
 * <h2>And a ring per axis, for the turns</h2>
 * Translation and rotation are different questions and want different shapes:
 * an arrow says "along" and a ring says "about". Drawn at a smaller radius than
 * the arrows are long, so the two never sit on top of each other.
 *
 * <h2>Drawn in the ITEM's frame</h2>
 * Not the model's. Every number the editor moves is applied in the hand's own
 * space, so a handle that pointed along the world's axes would move the item
 * somewhere other than where it pointed — which is the one thing a gizmo may
 * never do. The labels are the directions in that frame, so "Up" is the way the
 * Up slider goes.
 */
public final class AnchorGizmo
{
    /** How long an arrow is, in the character's own units. */
    private static final float ARM = 0.34F;
    /** How fat, and how big the head is. */
    private static final float THICK = 0.016F;
    private static final float HEAD = 0.075F;
    /** The rings sit inside the arrows so the two never overlap. */
    private static final float RING = 0.21F;
    private static final int RING_STEPS = 40;

    /** Blender's three, and a dimmer copy for the half that points backwards. */
    private static final int X_POS = 0xFFFF4A56;
    private static final int X_NEG = 0xFF8E2A31;
    private static final int Y_POS = 0xFF8CE04A;
    private static final int Y_NEG = 0xFF4E7A29;
    private static final int Z_POS = 0xFF3E8CFF;
    private static final int Z_NEG = 0xFF23508F;

    private AnchorGizmo()
    {
    }

    /**
     * Draws the handles at the current pose.
     *
     * @param pose already carrying the item's own frame, so this needs no
     *             knowledge of which hand it is in or where that hand has got to
     */
    public static void draw(PoseStack pose, SubmitNodeCollector collector, ModelMesh mesh)
    {
        // Named for the SIDE OF THE HELD MODEL each one points at, which is
        // the question being asked while the anchor is being placed: not "which
        // slider is this" but "which way is the sword facing". The slider names
        // are on the panel a few inches away and say the same thing.
        arrow(pose, collector, mesh, 1F, 0F, 0F, X_POS, "RIGHT");
        arrow(pose, collector, mesh, -1F, 0F, 0F, X_NEG, "LEFT");
        arrow(pose, collector, mesh, 0F, 1F, 0F, Y_POS, "TOP");
        arrow(pose, collector, mesh, 0F, -1F, 0F, Y_NEG, "BOTTOM");
        arrow(pose, collector, mesh, 0F, 0F, 1F, Z_POS, "FRONT");
        arrow(pose, collector, mesh, 0F, 0F, -1F, Z_NEG, "BACK");

        ring(pose, collector, mesh, 0, X_POS);
        ring(pose, collector, mesh, 1, Y_POS);
        ring(pose, collector, mesh, 2, Z_POS);
    }

    /**
     * One arrow: a square shaft and a flat head, then its name at the tip.
     * <p>
     * Built from quads rather than lines because a line is one pixel wide
     * whatever the distance, so a gizmo made of them vanishes as you step back
     * and is unreadable up close. Two crossed quads per shaft, which reads as a
     * solid rod from any angle for a quarter of the geometry of one.
     */
    private static void arrow(PoseStack pose, SubmitNodeCollector collector, ModelMesh mesh,
        float dx, float dy, float dz, int colour, String label)
    {
        // The two directions across the axis, for the crossed quads and the head.
        float[] a = perpendicular(dx, dy, dz);
        float[] b = {dy * a[2] - dz * a[1], dz * a[0] - dx * a[2], dx * a[1] - dy * a[0]};

        float tipX = dx * ARM;
        float tipY = dy * ARM;
        float tipZ = dz * ARM;
        float neckX = dx * (ARM - HEAD);
        float neckY = dy * (ARM - HEAD);
        float neckZ = dz * (ARM - HEAD);

        quad(pose, collector, mesh, colour,
            0F, 0F, 0F, neckX, neckY, neckZ, a, THICK);
        quad(pose, collector, mesh, colour,
            0F, 0F, 0F, neckX, neckY, neckZ, b, THICK);
        // The head, as two crossed triangles drawn as degenerate quads: the tip
        // is one point, so the far edge collapses to it.
        quad(pose, collector, mesh, colour,
            neckX, neckY, neckZ, tipX, tipY, tipZ, a, HEAD * 0.35F, true);
        quad(pose, collector, mesh, colour,
            neckX, neckY, neckZ, tipX, tipY, tipZ, b, HEAD * 0.35F, true);

        label(pose, collector, tipX * 1.18F, tipY * 1.18F, tipZ * 1.18F, label, colour);
    }

    /** A circle of short segments about one axis. */
    private static void ring(PoseStack pose, SubmitNodeCollector collector, ModelMesh mesh,
        int axis, int colour)
    {
        float[] previous = null;
        for(int step = 0; step <= RING_STEPS; step++)
        {
            double angle = step * Math.PI * 2D / RING_STEPS;
            float u = (float) Math.cos(angle) * RING;
            float v = (float) Math.sin(angle) * RING;
            float[] here = switch(axis)
            {
                case 0 -> new float[] {0F, u, v};
                case 1 -> new float[] {u, 0F, v};
                default -> new float[] {u, v, 0F};
            };
            if(previous != null)
            {
                float[] across = switch(axis)
                {
                    case 0 -> new float[] {1F, 0F, 0F};
                    case 1 -> new float[] {0F, 1F, 0F};
                    default -> new float[] {0F, 0F, 1F};
                };
                quad(pose, collector, mesh, colour, previous[0], previous[1], previous[2],
                    here[0], here[1], here[2], across, THICK);
            }
            previous = here;
        }
    }

    private static void quad(PoseStack pose, SubmitNodeCollector collector, ModelMesh mesh,
        int colour, float x0, float y0, float z0, float x1, float y1, float z1,
        float[] across, float half)
    {
        quad(pose, collector, mesh, colour, x0, y0, z0, x1, y1, z1, across, half, false);
    }

    /**
     * @param taper collapse the far edge to a point, which is what makes a head
     *              a head rather than a longer shaft
     */
    private static void quad(PoseStack pose, SubmitNodeCollector collector, ModelMesh mesh,
        int colour, float x0, float y0, float z0, float x1, float y1, float z1,
        float[] across, float half, boolean taper)
    {
        float ax = across[0] * half;
        float ay = across[1] * half;
        float az = across[2] * half;
        float bx = taper ? 0F : ax;
        float by = taper ? 0F : ay;
        float bz = taper ? 0F : az;
        // A flat white texture and the alpha-tested type, which is the one that
        // writes depth -- a handle has to be occluded by the hand it is on or it
        // reads as floating in front of the whole character.
        collector.submitCustomGeometry(pose,
            ModelMesh.typeFor(de.cas_ual_ty.dueldimension.clientutil.DuelTextures.WHITE),
            (unused, buffer) ->
        {
            PoseStack.Pose at = pose.last();
            corner(buffer, at, x0 - ax, y0 - ay, z0 - az, colour);
            corner(buffer, at, x0 + ax, y0 + ay, z0 + az, colour);
            corner(buffer, at, x1 + bx, y1 + by, z1 + bz, colour);
            corner(buffer, at, x1 - bx, y1 - by, z1 - bz, colour);
            // And the same again backwards, so it is solid from either side --
            // the same reason ModelHologram draws its sheets twice.
            corner(buffer, at, x1 - bx, y1 - by, z1 - bz, colour);
            corner(buffer, at, x1 + bx, y1 + by, z1 + bz, colour);
            corner(buffer, at, x0 + ax, y0 + ay, z0 + az, colour);
            corner(buffer, at, x0 - ax, y0 - ay, z0 - az, colour);
        });
    }

    private static void corner(com.mojang.blaze3d.vertex.VertexConsumer buffer,
        PoseStack.Pose at, float x, float y, float z, int colour)
    {
        buffer.addVertex(at, x, y, z)
            .setColor(colour)
            .setUv(0F, 0F)
            .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
            .setLight(0xF000F0)
            .setNormal(at, 0F, 1F, 0F);
    }

    /**
     * A name at the end of an arrow, turned to face whoever is reading it.
     * <p>
     * Billboarded rather than laid flat along the axis: an axis label lying in
     * the plane it names is edge-on exactly when you are looking down that axis,
     * which is exactly when you need it.
     */
    private static void label(PoseStack pose, SubmitNodeCollector collector,
        float x, float y, float z, String text, int colour)
    {
        Minecraft client = Minecraft.getInstance();
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(client.gameRenderer.mainCamera().rotation());
        // Small, and flipped: the font draws down the page and the world's y
        // goes up.
        pose.scale(-0.006F, -0.006F, 0.006F);
        int width = client.font.width(text);
        collector.submitText(pose, -width / 2F, 0F,
            Component.literal(text).getVisualOrderText(), false,
            net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0xF000F0, colour, 0, 0);
        pose.popPose();
    }

    private static float[] perpendicular(float x, float y, float z)
    {
        // Any direction not along the axis will do; the cross product below
        // makes the third one, so only this one has to be chosen.
        return Math.abs(y) > 0.9F ? new float[] {1F, 0F, 0F} : new float[] {0F, 1F, 0F};
    }
}
