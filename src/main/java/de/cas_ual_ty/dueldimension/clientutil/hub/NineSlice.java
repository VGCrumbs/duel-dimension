package de.cas_ual_ty.dueldimension.clientutil.hub;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil;
import net.minecraft.resources.Identifier;

/**
 * Draws a panel of any size from a small texture.
 * <p>
 * Every surface in the hub is a PNG rather than a filled rectangle, which would
 * normally mean one file per size. A nine-slice avoids that: the four corners
 * are drawn at their true size, the four edges are stretched along one axis
 * only, and the middle is stretched both ways. A 24&times;24 tile therefore
 * dresses a panel of any dimensions without its corners smearing or its border
 * changing thickness.
 * <p>
 * Every tile this class draws is 3&times;3 cells of {@link #BORDER}, so one
 * constant describes them all. Atlases stack their states vertically, so a
 * widget picks a row rather than a file.
 * <p>
 * The drawing is entirely rewritten for this version. The Forge original built
 * its own quads through {@code RenderSystem.setShader} and a
 * {@code PoseStack}, which is immediate-mode rendering and no longer exists:
 * the GUI is retained-mode now, and a screen <em>describes</em> itself to a
 * {@link GuiGraphicsExtractor} which draws everything later in one pass. That
 * turns out to suit a nine-slice exactly — the extractor's blit takes a UV
 * window, which is the one thing this class ever needed.
 */
public final class NineSlice
{
    /** Corner size in texture pixels; the tile is three of these square. */
    public static final int BORDER = 8;
    /** One state of an atlas: a full 3x3 tile. */
    public static final int TILE = BORDER * 3;

    /** Rows of a state atlas, in the order the generator writes them. */
    public static final int IDLE = 0;
    public static final int HOVER = 1;
    /** Disabled for a button; selected for a tab, which is never disabled. */
    public static final int DISABLED = 2;
    public static final int SELECTED = 2;

    private NineSlice()
    {
    }

    /** Draws a single-state tile (no atlas). */
    public static void draw(GuiGraphicsExtractor graphics, Identifier texture,
        int x, int y, int width, int height)
    {
        draw(graphics, texture, x, y, width, height, 0, 1);
    }

    /**
     * Draws one row of a state atlas.
     *
     * @param row  which state, e.g. {@link #HOVER}
     * @param rows how many states the atlas holds
     */
    public static void draw(GuiGraphicsExtractor graphics, Identifier texture,
        int x, int y, int width, int height, int row, int rows)
    {
        draw(graphics, texture, x, y, width, height, row, rows, 1F);
    }

    /**
     * The same, fainter.
     * <p>
     * The Forge code said this with {@code RenderSystem.setShaderColor} before
     * the draw. There is no draw-time colour any more, so the alpha travels
     * down to each cell as part of the tint it is drawn with.
     */
    public static void draw(GuiGraphicsExtractor graphics, Identifier texture,
        int x, int y, int width, int height, int row, int rows, float alpha)
    {
        int tint = DdBlitUtil.alpha(alpha);
        int fileHeight = TILE * rows;
        int v0 = row * TILE;

        // A panel smaller than two borders would have its corners overlap and
        // draw the frame twice; clamp so it degrades to just the corners.
        int edge = Math.min(BORDER, Math.min(width, height) / 2);
        int midW = Math.max(0, width - edge * 2);
        int midH = Math.max(0, height - edge * 2);
        int texMid = TILE - BORDER * 2;

        // corners
        cell(graphics, texture, x, y, edge, edge, 0, v0, BORDER, BORDER, fileHeight, tint);
        cell(graphics, texture, x + width - edge, y, edge, edge,
            TILE - BORDER, v0, BORDER, BORDER, fileHeight, tint);
        cell(graphics, texture, x, y + height - edge, edge, edge,
            0, v0 + TILE - BORDER, BORDER, BORDER, fileHeight, tint);
        cell(graphics, texture, x + width - edge, y + height - edge, edge, edge,
            TILE - BORDER, v0 + TILE - BORDER, BORDER, BORDER, fileHeight, tint);

        // edges
        if(midW > 0)
        {
            cell(graphics, texture, x + edge, y, midW, edge,
                BORDER, v0, texMid, BORDER, fileHeight, tint);
            cell(graphics, texture, x + edge, y + height - edge, midW, edge,
                BORDER, v0 + TILE - BORDER, texMid, BORDER, fileHeight, tint);
        }
        if(midH > 0)
        {
            cell(graphics, texture, x, y + edge, edge, midH,
                0, v0 + BORDER, BORDER, texMid, fileHeight, tint);
            cell(graphics, texture, x + width - edge, y + edge, edge, midH,
                TILE - BORDER, v0 + BORDER, BORDER, texMid, fileHeight, tint);
        }

        // middle
        if(midW > 0 && midH > 0)
        {
            cell(graphics, texture, x + edge, y + edge, midW, midH,
                BORDER, v0 + BORDER, texMid, texMid, fileHeight, tint);
        }
    }

    /**
     * One cell of the nine, given its place in the texture in pixels.
     * <p>
     * Two conversions happen here, and both are easy to get wrong.
     * <p>
     * The extractor wants a UV window in 0..1 rather than pixels, so the file's
     * size is divided out — this is the only place that knows both the cell's
     * pixel bounds and the file's dimensions.
     * <p>
     * And its blit takes <em>corners</em>, not a size: the arguments after the
     * texture are {@code x0, y0, x1, y1}, which is not obvious from the
     * signature — every one of them is an {@code int} and the older API in the
     * same position meant width and height. Passing a size draws each cell from
     * its corner to a point measured from the screen origin, which is why the
     * first attempt at this came out as garbage rather than as a panel.
     */
    private static void cell(GuiGraphicsExtractor graphics, Identifier texture,
        int x, int y, int width, int height,
        int u, int v, int uw, int vh, int fileHeight, int tint)
    {
        DdBlitUtil.blit(graphics, texture, x, y, width, height,
            u / (float)TILE, v / (float)fileHeight,
            (u + uw) / (float)TILE, (v + vh) / (float)fileHeight, tint);
    }

    /** A plain stretched texture, for art that is not a frame. */
    public static void image(GuiGraphicsExtractor graphics, Identifier texture,
        int x, int y, int width, int height)
    {
        graphics.blit(texture, x, y, x + width, y + height, 0F, 1F, 0F, 1F);
    }
}
