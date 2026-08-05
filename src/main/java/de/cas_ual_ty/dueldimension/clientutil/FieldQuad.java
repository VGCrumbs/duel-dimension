package de.cas_ual_ty.dueldimension.clientutil;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Matrix4f;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * A four-cornered textured quad.
 * <p>
 * The GUI's blit helpers only draw axis-aligned rectangles, which cannot show
 * a tilted table: on a perspective field every zone is a trapezoid that
 * narrows towards the far edge. This draws an arbitrary quad instead, so a
 * card can be laid onto the projected playfield the way the reference client
 * lays it into its 3D scene.
 * <p>
 * Corners are given in the order top-left, top-right, bottom-right,
 * bottom-left, matching the texture's own corners.
 */
public final class FieldQuad
{
    private FieldQuad()
    {
    }

    /** Screen positions of a quad's four corners. */
    public record Corners(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3)
    {
        /** Axis-aligned bounds, for cheap hit tests and badge placement. */
        public int minX()
        {
            return Math.round(Math.min(Math.min(x0, x1), Math.min(x2, x3)));
        }

        public int minY()
        {
            return Math.round(Math.min(Math.min(y0, y1), Math.min(y2, y3)));
        }

        public int maxX()
        {
            return Math.round(Math.max(Math.max(x0, x1), Math.max(x2, x3)));
        }

        public int maxY()
        {
            return Math.round(Math.max(Math.max(y0, y1), Math.max(y2, y3)));
        }

        /** True if the point is inside the quad (works for any convex shape). */
        public boolean contains(double px, double py)
        {
            return sameSide(px, py, x0, y0, x1, y1) && sameSide(px, py, x1, y1, x2, y2)
                && sameSide(px, py, x2, y2, x3, y3) && sameSide(px, py, x3, y3, x0, y0);
        }

        private boolean sameSide(double px, double py, float ax, float ay, float bx, float by)
        {
            // Cross product sign against each edge, walking the quad one way.
            return (bx - ax) * (py - ay) - (by - ay) * (px - ax) >= 0;
        }
    }

    /** Draws the whole texture into the quad. */
    public static void draw(PoseStack poseStack, ResourceLocation texture, Corners corners)
    {
        draw(poseStack, texture, corners, 1F, 1F, 1F, 1F);
    }

    public static void draw(PoseStack poseStack, ResourceLocation texture, Corners corners,
        float red, float green, float blue, float alpha)
    {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(red, green, blue, alpha);
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        Matrix4f matrix = poseStack.last().pose();
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.vertex(matrix, corners.x0(), corners.y0(), 0).uv(0, 0).endVertex();
        buffer.vertex(matrix, corners.x3(), corners.y3(), 0).uv(0, 1).endVertex();
        buffer.vertex(matrix, corners.x2(), corners.y2(), 0).uv(1, 1).endVertex();
        buffer.vertex(matrix, corners.x1(), corners.y1(), 0).uv(1, 0).endVertex();
        tesselator.end();

        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
    }

    /** Outlines a quad, for zone borders and highlights. */
    public static void outline(PoseStack poseStack, Corners corners, int colour)
    {
        line(poseStack, corners.x0(), corners.y0(), corners.x1(), corners.y1(), colour);
        line(poseStack, corners.x1(), corners.y1(), corners.x2(), corners.y2(), colour);
        line(poseStack, corners.x2(), corners.y2(), corners.x3(), corners.y3(), colour);
        line(poseStack, corners.x3(), corners.y3(), corners.x0(), corners.y0(), colour);
    }

    /** Fills a quad with a flat colour. */
    public static void fill(PoseStack poseStack, Corners corners, int colour)
    {
        float alpha = (colour >> 24 & 0xFF) / 255F;
        float red = (colour >> 16 & 0xFF) / 255F;
        float green = (colour >> 8 & 0xFF) / 255F;
        float blue = (colour & 0xFF) / 255F;

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        Matrix4f matrix = poseStack.last().pose();
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        buffer.vertex(matrix, corners.x0(), corners.y0(), 0).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, corners.x3(), corners.y3(), 0).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, corners.x2(), corners.y2(), 0).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, corners.x1(), corners.y1(), 0).color(red, green, blue, alpha).endVertex();
        tesselator.end();
        RenderSystem.disableBlend();
    }

    private static void line(PoseStack poseStack, float ax, float ay, float bx, float by, int colour)
    {
        // A thin quad is simpler than a line primitive and scales with the GUI.
        float dx = bx - ax;
        float dy = by - ay;
        float length = (float)Math.sqrt(dx * dx + dy * dy);
        if(length < 0.001F)
        {
            return;
        }
        float nx = -dy / length;
        float ny = dx / length;
        fill(poseStack, new Corners(ax + nx, ay + ny, bx + nx, by + ny, bx - nx, by - ny, ax - nx, ay - ny),
            colour);
    }
}
