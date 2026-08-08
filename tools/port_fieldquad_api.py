"""Ports the rest of FieldQuad's drawing API onto submitCustomGeometry.

`draw` and `Corners` were already across. What was missing is everything the
board actually calls: the `drawCorners` tint overloads, the `drawProjected`
family, and `outline`.

The important structural point is in `drawProjected`: the subdivision emits
`steps * steps` quads and they all go into ONE `submitCustomGeometry` call.
That is what the single BufferBuilder batch did. Splitting them would turn one
draw into a few hundred, which on a board full of cards is the difference
between a frame and a stutter.
"""
import io

PATH = "src/main/java/de/cas_ual_ty/dueldimension/clientutil/FieldQuad.java"

ADDITION = '''    /**
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

    /** As above with a full tint, for coloured shapes cut from white.png. */
    public static void drawCorners(PoseStack poseStack, SubmitNodeCollector collector,
        Identifier texture, Corners corners,
        float u0, float v0, float u1, float v1,
        float red, float green, float blue, float alpha)
    {
        draw(poseStack, collector, texture, corners, u0, v0, u1, v1,
            ScreenUtil.colour(red, green, blue, alpha));
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

        collector.submitCustomGeometry(poseStack,
            net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucent(texture),
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

'''

src = io.open(PATH, encoding="utf-8").read()
anchor = "    /** Fills a quad with a flat colour. */"
assert anchor in src, "anchor moved"
src = src.replace(anchor, ADDITION + anchor, 1)
io.open(PATH, "w", encoding="utf-8", newline="\n").write(src)
print("FieldQuad: full drawing API ported")
