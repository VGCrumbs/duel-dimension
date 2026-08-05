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

    /**
     * Draws a texture across a field rectangle with perspective correction.
     * <p>
     * A GPU interpolates texture coordinates linearly across a triangle, so a
     * single quad whose corners have been perspective-projected smears its
     * texture: the far half is stretched and a diagonal seam appears along the
     * split. That is fatal for the playmat, whose printed zones then land
     * nowhere near the zones we draw on top of it. Subdividing the rectangle
     * and projecting each cell separately keeps the error per cell far below
     * a pixel, which is the standard remedy when the pipeline cannot be given
     * homogeneous texture coordinates.
     *
     * @param steps cells per axis; cost is steps squared, 1 disables it
     */
    public static void drawProjected(PoseStack poseStack, ResourceLocation texture,
        FieldLayout.Projection projection, FieldLayout.Rect rect, int steps)
    {
        drawProjected(poseStack, texture, projection, rect, steps, false);
    }

    /**
     * @param quarterTurn rotate the texture 90 degrees within the rectangle,
     *                    for a monster lying in defence position
     */
    public static void drawProjected(PoseStack poseStack, ResourceLocation texture,
        FieldLayout.Projection projection, FieldLayout.Rect rect, int steps, boolean quarterTurn)
    {
        drawProjected(poseStack, texture, projection, rect, steps, quarterTurn, 0F, 0F, 1F, 1F);
    }

    /**
     * @param su0 su1 sv0 sv1 the part of the texture to sample. The mod stores
     *                        card images letterboxed inside a square, so a card
     *                        drawn with the full 0..1 range would be squashed
     *                        into the padding; sampling just the card's own
     *                        window restores its proportions.
     */
    public static void drawProjected(PoseStack poseStack, ResourceLocation texture,
        FieldLayout.Projection projection, FieldLayout.Rect rect, int steps, boolean quarterTurn,
        float su0, float sv0, float su1, float sv1)
    {
        drawProjected(poseStack, texture, projection, rect, steps, quarterTurn ? 1 : 0,
            su0, sv0, su1, sv1);
    }

    /**
     * As above, but with the turn given in quarter turns anticlockwise.
     * <p>
     * Two turns (180 degrees) is what the opponent's half of the table needs:
     * client_field.cpp gives every card of theirs {@code oppoATK = {0,0,PI}},
     * and a real playmat faces its owner, so theirs reads upside down to you.
     *
     * @param turns 0 upright, 1 a quarter turn (a defending monster), 2 upside
     *              down (the opponent's side), 3 the other quarter turn
     */
    public static void drawProjected(PoseStack poseStack, ResourceLocation texture,
        FieldLayout.Projection projection, FieldLayout.Rect rect, int steps, int turns,
        float su0, float sv0, float su1, float sv1)
    {
        drawProjected(poseStack, texture, projection, rect, steps, turns, su0, sv0, su1, sv1,
            1F, 1F, 1F, 1F);
    }

    /**
     * As above, tinted. Used to shade the buried cards of a pile so a stack
     * reads as separate cards rather than one slab.
     */
    public static void drawProjected(PoseStack poseStack, ResourceLocation texture,
        FieldLayout.Projection projection, FieldLayout.Rect rect, int steps, int turns,
        float su0, float sv0, float su1, float sv1,
        float red, float green, float blue, float alpha)
    {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(red, green, blue, alpha);
        DuelTextures.bindSmooth(texture);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        Matrix4f matrix = poseStack.last().pose();
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);

        for(int row = 0; row < steps; row++)
        {
            float v0 = row / (float)steps;
            float v1 = (row + 1) / (float)steps;
            float y0 = rect.y() + rect.h() * v0;
            float y1 = rect.y() + rect.h() * v1;

            for(int column = 0; column < steps; column++)
            {
                float u0 = column / (float)steps;
                float u1 = (column + 1) / (float)steps;
                float x0 = rect.x() + rect.w() * u0;
                float x1 = rect.x() + rect.w() * u1;

                // Each quarter turn is (u,v) -> (v, 1-u) inside the same
                // rectangle: once is how a defending monster lies, twice is the
                // opponent's side of the table.
                float au = u0;
                float av = v0;
                float bu = u0;
                float bv = v1;
                float cu = u1;
                float cv = v1;
                float du = u1;
                float dv = v0;
                for(int turn = 0; turn < (turns & 3); turn++)
                {
                    float ta = au;
                    au = av;
                    av = 1F - ta;
                    float tb = bu;
                    bu = bv;
                    bv = 1F - tb;
                    float tc = cu;
                    cu = cv;
                    cv = 1F - tc;
                    float td = du;
                    du = dv;
                    dv = 1F - td;
                }

                // Map the unit square onto the requested texture window.
                au = su0 + au * (su1 - su0);
                bu = su0 + bu * (su1 - su0);
                cu = su0 + cu * (su1 - su0);
                du = su0 + du * (su1 - su0);
                av = sv0 + av * (sv1 - sv0);
                bv = sv0 + bv * (sv1 - sv0);
                cv = sv0 + cv * (sv1 - sv0);
                dv = sv0 + dv * (sv1 - sv0);

                buffer.vertex(matrix, projection.x(x0, y0), projection.y(y0), 0).uv(au, av).endVertex();
                buffer.vertex(matrix, projection.x(x0, y1), projection.y(y1), 0).uv(bu, bv).endVertex();
                buffer.vertex(matrix, projection.x(x1, y1), projection.y(y1), 0).uv(cu, cv).endVertex();
                buffer.vertex(matrix, projection.x(x1, y0), projection.y(y0), 0).uv(du, dv).endVertex();
            }
        }
        tesselator.end();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
    }

    /**
     * A textured quad at explicit screen corners, with a UV window and a tint.
     * Sampling v past 1 tiles the texture (GL_REPEAT is the default wrap),
     * which is how a stack's side repeats its two-row card-edge stripe once
     * per card.
     */
    public static void drawCorners(PoseStack poseStack, ResourceLocation texture, Corners corners,
        float u0, float v0, float u1, float v1, float shade, float alpha)
    {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(shade, shade, shade, alpha);
        DuelTextures.bindSmooth(texture);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        Matrix4f matrix = poseStack.last().pose();
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.vertex(matrix, corners.x0(), corners.y0(), 0).uv(u0, v0).endVertex();
        buffer.vertex(matrix, corners.x3(), corners.y3(), 0).uv(u0, v1).endVertex();
        buffer.vertex(matrix, corners.x2(), corners.y2(), 0).uv(u1, v1).endVertex();
        buffer.vertex(matrix, corners.x1(), corners.y1(), 0).uv(u1, v0).endVertex();
        tesselator.end();

        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
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
        DuelTextures.bindSmooth(texture);
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
        // position_color multiplies the vertex colour by RenderSystem's shader
        // colour, and this was the one draw path here that never set it -- every
        // other one does. It therefore inherited whatever the last widget left
        // behind (the mod's widgets all set (1,1,1,alpha) for their fade and do
        // not restore it), and since Screen.render draws widgets after the
        // board, a stale alpha carried from one frame into the next one's zone
        // grid. Set it explicitly.
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
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
