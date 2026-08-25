package de.cas_ual_ty.dueldimension.clientutil;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.resources.Identifier;

/**
 * A four-cornered textured quad.
 * <p>
 * The GUI's blit helpers only draw axis-aligned rectangles, which cannot show
 * a tilted table: on a perspective field every zone is a trapezoid that
 * narrows towards the far edge. On Forge this class drew an arbitrary quad by
 * building its own {@code POSITION_TEX} vertices through a {@code BufferBuilder}.
 * <p>
 * Minecraft 26.2 removed immediate-mode drawing, and for a while that left this
 * class as geometry only. The way back is
 * {@code SubmitNodeCollector.submitCustomGeometry}, which hands over a
 * {@code PoseStack.Pose} and a {@code VertexConsumer} — exactly the raw vertex
 * sink the old code had. Four vertices with a position and a UV each, and the
 * trapezoid is drawn.
 * <p>
 * <b>The signatures changed, and deliberately.</b> These take a {@code PoseStack}
 * and a {@code SubmitNodeCollector} rather than a {@code GuiGraphicsExtractor},
 * because that pair is what exists inside the pass where custom geometry is
 * allowed. A GUI reaches it by drawing the board as a picture-in-picture region
 * (see {@link BoardPip}); a caller that already has a {@code PoseStack} — the
 * duel field on a block, for instance — can call straight in.
 * <p>
 * Corners are given in the order top-left, top-right, bottom-right,
 * bottom-left, matching the texture's own corners.
 */
public final class FieldQuad
{
    /**
     * Lit as if in full daylight, and unaffected by damage flash.
     * <p>
     * The board is a picture, not a thing in the world: it should not dim
     * because the player is standing in a cave. The old code got this for free
     * by using a GUI shader with no lighting at all; an entity render type has
     * lighting, so it is told to leave the colours alone.
     */
    private static final int FULL_BRIGHT = 0xF000F0;

    private FieldQuad()
    {
    }

    /** The whole texture, into the quad. */
    /**
     * The layer the next draw goes into.
     * <p>
     * PORT-NOTE, settled by looking at a running client: submission order is
     * NOT draw order. Custom geometry is batched by render type, so draws with
     * different textures emerge in whatever order the batching yields. On the
     * board that put the playmat on top of the cards standing on it -- and the
     * mat is drawn tinted, so everything under it looked darkened -- and the
     * graveyard on top of the deck beside it.
     * <p>
     * Layers are drawn in key order, so giving every draw the next one makes
     * call order the draw order again, which is what immediate mode gave Forge
     * for free and what every caller here was written against.
     */
    private static int layer;

    /**
     * Starts a new frame's worth of layers.
     * <p>
     * Called once at the top of the board's painter. Without it the counter
     * would climb for the life of the client; nothing breaks immediately, but
     * the layer map is rebuilt per frame and there is no reason to grow it.
     */
    public static void resetLayers()
    {
        layer = 0;
    }

    public static void draw(PoseStack poseStack, SubmitNodeCollector collector,
        Identifier texture, Corners corners)
    {
        draw(poseStack, collector, texture, corners, 0F, 0F, 1F, 1F, -1);
    }

    /** The whole texture, tinted. */
    public static void draw(PoseStack poseStack, SubmitNodeCollector collector,
        Identifier texture, Corners corners, int tint)
    {
        draw(poseStack, collector, texture, corners, 0F, 0F, 1F, 1F, tint);
    }

    /**
     * Part of a texture, into the quad.
     * <p>
     * The corner order is the Forge code's and is not arbitrary: 0, 3, 2, 1 --
     * top-left, bottom-left, bottom-right, top-right. That is anticlockwise in
     * screen coordinates, which is what the quad winding wants; going round the
     * other way makes the face point away and it vanishes under back-face
     * culling.
     *
     * @param tint ARGB multiplied into the texture; -1 for none
     */
    public static void draw(PoseStack poseStack, SubmitNodeCollector collector,
        Identifier texture, Corners corners,
        float u0, float v0, float u1, float v1, int tint)
    {
        draw(poseStack, collector, texture, corners, u0, v0, u1, v1, tint, false);
    }

    /**
     * As above, greyed, for a card the player does not own.
     * <p>
     * The board must keep passing false: this method is shared with
     * {@code BoardRenderer}, and a duel field draws cards that are in play
     * rather than cards that are missing from a collection. Hence an overload
     * with a default rather than a parameter added to the signature above —
     * a site that never learns about ownership fails safe as full colour.
     *
     * @param desaturate draw through {@link UnownedPipelines#MESH} instead
     */
    public static void draw(PoseStack poseStack, SubmitNodeCollector collector,
        Identifier texture, Corners corners,
        float u0, float v0, float u1, float v1, int tint, boolean desaturate)
    {
        collector.order(layer++).submitCustomGeometry(poseStack,
            // The one entity render type that is truly UNLIT. Read off the
            // 26.2 pipeline bytecode, not guessed:
            //   entityTranslucent           -> two directional lights dotted
            //                                  with the normal; everything on
            //                                  the board came out ~30% dark,
            //                                  which read as "a darkening
            //                                  filter over the whole field".
            //   entityTranslucentEmissive   -> NOT the fix its name promises:
            //                                  it defines PER_FACE_LIGHTING,
            //                                  which is the same two lights
            //                                  per face. Still dark.
            //   eyes                        -> unlit, but culls, and the
            //                                  picture-in-picture z-flip
            //                                  reverses our winding.
            //   breezeWind                  -> NO_CARDINAL_LIGHTING (vertex
            //                                  colour passes through), plain
            //                                  translucent blend, cull off.
            // Forge drew these quads with position/colour/tex and no lighting;
            // this is that pipeline in the format this buffer already writes.
            // The two zeros are the wind's texture scroll, declined.
            //
            // UnownedPipelines.mesh is that same recipe with one thing changed,
            // the fragment shader, so an unowned card is greyed by the draw
            // rather than by a second copy of its image.
            desaturate ? UnownedPipelines.mesh(texture)
                : net.minecraft.client.renderer.rendertype.RenderTypes.breezeWind(texture, 0F, 0F),
            (pose, buffer) ->
            {
                vertex(buffer, pose, corners.x0(), corners.y0(), u0, v0, tint);
                vertex(buffer, pose, corners.x3(), corners.y3(), u0, v1, tint);
                vertex(buffer, pose, corners.x2(), corners.y2(), u1, v1, tint);
                vertex(buffer, pose, corners.x1(), corners.y1(), u1, v0, tint);
            });
    }

    /**
     * One corner.
     * <p>
     * An entity render type wants a full vertex -- colour, UV, overlay, light
     * and normal -- where the old GUI shader wanted only a position and a UV.
     * Leaving any of them unset does not fail; it reads whatever was in the
     * buffer, which is how a quad ends up black or invisible for no obvious
     * reason.
     */
    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose,
        float x, float y, float u, float v, int tint)
    {
        buffer.addVertex(pose, x, y, 0F)
            .setColor(tint)
            .setUv(u, v)
            .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
            .setLight(FULL_BRIGHT)
            // Unused by the unlit pipeline, but the format requires a value and
            // an unset element reads garbage from the buffer.
            .setNormal(0F, 0F, 1F);
    }

    /** Draws a textured quad at four genuine model-space points. */
    public static void draw3D(PoseStack poseStack, SubmitNodeCollector collector,
        Identifier texture, Corners3D corners,
        float u0, float v0, float u1, float v1, int tint)
    {
        collector.order(layer++).submitCustomGeometry(poseStack,
            net.minecraft.client.renderer.rendertype.RenderTypes.breezeWind(texture, 0F, 0F),
            (pose, buffer) ->
            {
                vertex3D(buffer, pose, corners.x0(), corners.y0(), corners.z0(), u0, v0, tint);
                vertex3D(buffer, pose, corners.x3(), corners.y3(), corners.z3(), u0, v1, tint);
                vertex3D(buffer, pose, corners.x2(), corners.y2(), corners.z2(), u1, v1, tint);
                vertex3D(buffer, pose, corners.x1(), corners.y1(), corners.z1(), u1, v0, tint);
            });
    }

    private static void vertex3D(VertexConsumer buffer, PoseStack.Pose pose,
        float x, float y, float z, float u, float v, int tint)
    {
        buffer.addVertex(pose, x, y, z)
            .setColor(tint)
            .setUv(u, v)
            .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
            .setLight(FULL_BRIGHT)
            .setNormal(0F, 1F, 0F);
    }

    /**
     * A quad at explicit corners, with a UV window and a shade.
     * <p>
     * Sampling v past 1 tiles the texture -- repeat is the default wrap -- which
     * is how a stack's side repeats its two-row card-edge stripe once per card.
     */
    public static void drawCorners(PoseStack poseStack, SubmitNodeCollector collector,
        Identifier texture, Corners corners,
        float u0, float v0, float u1, float v1, float shade, float alpha)
    {
        drawCorners(poseStack, collector, texture, corners, u0, v0, u1, v1,
            shade, shade, shade, alpha);
    }

    /**
     * As above, greyed, for a card the player does not own. Only the card
     * preview passes true; the duel field's shapes are not collection entries.
     */
    public static void drawCorners(PoseStack poseStack, SubmitNodeCollector collector,
        Identifier texture, Corners corners,
        float u0, float v0, float u1, float v1, float shade, float alpha, boolean desaturate)
    {
        drawCorners(poseStack, collector, texture, corners, u0, v0, u1, v1,
            shade, shade, shade, alpha, desaturate);
    }

    /** As above with a full tint, for coloured shapes cut from white.png. */
    public static void drawCorners(PoseStack poseStack, SubmitNodeCollector collector,
        Identifier texture, Corners corners,
        float u0, float v0, float u1, float v1,
        float red, float green, float blue, float alpha)
    {
        drawCorners(poseStack, collector, texture, corners, u0, v0, u1, v1,
            red, green, blue, alpha, false);
    }

    /** As above, greyed. */
    public static void drawCorners(PoseStack poseStack, SubmitNodeCollector collector,
        Identifier texture, Corners corners,
        float u0, float v0, float u1, float v1,
        float red, float green, float blue, float alpha, boolean desaturate)
    {
        if(desaturate && !UnownedPipelines.available())
        {
            // The shader did not compile, so this is a DIM and not a
            // desaturation. Multiplied into the shade the caller asked for
            // rather than replacing it, so a foil's own alpha still means what
            // it meant.
            red *= UnownedPipelines.DIM_RED;
            green *= UnownedPipelines.DIM_GREEN;
            blue *= UnownedPipelines.DIM_BLUE;
            desaturate = false;
        }
        draw(poseStack, collector, texture, corners, u0, v0, u1, v1,
            ScreenUtil.colour(red, green, blue, alpha), desaturate);
    }

    /**
     * A rectangle of the playfield, pushed through the perspective projection.
     *
     * @param steps cells per axis; cost is steps squared, 1 disables it
     */
    public static void drawProjected(PoseStack poseStack, SubmitNodeCollector collector,
        Identifier texture, FieldLayout.Projection projection, FieldLayout.Rect rect, int steps)
    {
        drawProjected(poseStack, collector, texture, projection, rect, steps, false);
    }

    /**
     * @param quarterTurn rotate the texture 90 degrees within the rectangle,
     *                    for a monster lying in defence position
     */
    public static void drawProjected(PoseStack poseStack, SubmitNodeCollector collector,
        Identifier texture, FieldLayout.Projection projection, FieldLayout.Rect rect,
        int steps, boolean quarterTurn)
    {
        drawProjected(poseStack, collector, texture, projection, rect, steps, quarterTurn,
            0F, 0F, 1F, 1F);
    }

    /**
     * @param su0 sv0 su1 sv1 the part of the texture to sample. The mod stores
     *                        card images letterboxed inside a square, so a card
     *                        drawn with the full 0..1 range would be squashed
     *                        into the padding; sampling just the card's own
     *                        window restores its proportions.
     */
    public static void drawProjected(PoseStack poseStack, SubmitNodeCollector collector,
        Identifier texture, FieldLayout.Projection projection, FieldLayout.Rect rect,
        int steps, boolean quarterTurn, float su0, float sv0, float su1, float sv1)
    {
        drawProjected(poseStack, collector, texture, projection, rect, steps,
            quarterTurn ? 1 : 0, su0, sv0, su1, sv1);
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
    public static void drawProjected(PoseStack poseStack, SubmitNodeCollector collector,
        Identifier texture, FieldLayout.Projection projection, FieldLayout.Rect rect,
        int steps, int turns, float su0, float sv0, float su1, float sv1)
    {
        drawProjected(poseStack, collector, texture, projection, rect, steps, turns,
            su0, sv0, su1, sv1, 1F, 1F, 1F, 1F);
    }

    /**
     * As above, tinted. Used to shade the buried cards of a pile so a stack
     * reads as separate cards rather than one slab.
     * <p>
     * <b>Every cell goes into one geometry submission.</b> The subdivision emits
     * {@code steps * steps} quads and they all belong to the same draw -- that is
     * what the single {@code BufferBuilder} batch did, and splitting them would
     * turn one draw into a few hundred.
     */
    public static void drawProjected(PoseStack poseStack, SubmitNodeCollector collector,
        Identifier texture, FieldLayout.Projection projection, FieldLayout.Rect rect,
        int steps, int turns, float su0, float sv0, float su1, float sv1,
        float red, float green, float blue, float alpha)
    {
        int tint = ScreenUtil.colour(red, green, blue, alpha);

        collector.order(layer++).submitCustomGeometry(poseStack,
            net.minecraft.client.renderer.rendertype.RenderTypes.breezeWind(texture, 0F, 0F),
            (pose, buffer) ->
        {
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
                    // rectangle: once is how a defending monster lies, twice is
                    // the opponent's side of the table.
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

                    vertex(buffer, pose, projection.x(x0, y0), projection.y(y0), au, av, tint);
                    vertex(buffer, pose, projection.x(x0, y1), projection.y(y1), bu, bv, tint);
                    vertex(buffer, pose, projection.x(x1, y1), projection.y(y1), cu, cv, tint);
                    vertex(buffer, pose, projection.x(x1, y0), projection.y(y0), du, dv, tint);
                }
            }
        });
    }

    /** Outlines a quad, for zone borders and highlights. */
    public static void outline(PoseStack poseStack, SubmitNodeCollector collector,
        Corners corners, int colour)
    {
        line(poseStack, collector, corners.x0(), corners.y0(), corners.x1(), corners.y1(), colour);
        line(poseStack, collector, corners.x1(), corners.y1(), corners.x2(), corners.y2(), colour);
        line(poseStack, collector, corners.x2(), corners.y2(), corners.x3(), corners.y3(), colour);
        line(poseStack, collector, corners.x3(), corners.y3(), corners.x0(), corners.y0(), colour);
    }

    /**
     * One edge, as a thin quad.
     * <p>
     * A line primitive would be thinner and cheaper, but its width is a driver
     * setting rather than something a caller controls, so a border would come out
     * a different thickness on different machines. A quad is predictable.
     */
    private static void line(PoseStack poseStack, SubmitNodeCollector collector,
        float ax, float ay, float bx, float by, int colour)
    {
        float dx = bx - ax;
        float dy = by - ay;
        float length = (float)Math.sqrt(dx * dx + dy * dy);
        if(length < 1.0E-4F)
        {
            return;
        }
        // Half a pixel either side of the centre line, perpendicular to it.
        float nx = -dy / length * 0.5F;
        float ny = dx / length * 0.5F;
        draw(poseStack, collector, DuelTextures.WHITE,
            new Corners(ax + nx, ay + ny, bx + nx, by + ny, bx - nx, by - ny, ax - nx, ay - ny),
            0F, 0F, 1F, 1F, colour);
    }

    /**
     * A submission on the board's own pipeline and its next layer, for
     * geometry this class cannot describe.
     * <p>
     * Everything here is a flat quad with z pinned to zero, which is all the
     * board ever needed. {@link CoinModel} needs a real solid, so it borrows
     * the two things that are awkward to get right — the unlit render type
     * chosen above, and a layer from the same counter, so a coin sorts against
     * the cards rather than against nothing.
     */
    public static void submit(PoseStack poseStack, SubmitNodeCollector collector,
        Identifier texture,
        net.minecraft.client.renderer.SubmitNodeCollector.CustomGeometryRenderer painter)
    {
        collector.order(layer++).submitCustomGeometry(poseStack,
            net.minecraft.client.renderer.rendertype.RenderTypes.breezeWind(texture, 0F, 0F),
            painter);
    }

    /** Fills a quad with a flat colour. */
    public static void fill(PoseStack poseStack, SubmitNodeCollector collector,
        Corners corners, int colour)
    {
        draw(poseStack, collector, DuelTextures.WHITE, corners, colour);
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

    /** Top-left, top-right, bottom-right, bottom-left in three dimensions. */
    public record Corners3D(float x0, float y0, float z0,
        float x1, float y1, float z1, float x2, float y2, float z2,
        float x3, float y3, float z3)
    {
    }
}
