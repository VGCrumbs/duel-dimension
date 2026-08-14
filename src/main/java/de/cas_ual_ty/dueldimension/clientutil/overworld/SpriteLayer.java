package de.cas_ual_ty.dueldimension.clientutil.overworld;

import de.cas_ual_ty.dueldimension.DuelDimension;
import net.minecraft.resources.Identifier;

/**
 * One run of animation frames, taken from a rectangle of a sprite sheet.
 * <p>
 * A rectangle rather than the whole file, because a sheet can hold more than
 * one thing. Blue-Eyes' sheet carries six wing frames across the top and five
 * body frames underneath -- at different cell widths, because the wings are
 * narrower than the dragon. A single grid over the whole file cannot describe
 * that, and every sheet drawn by an artist rather than by a tool eventually
 * looks like this.
 * <p>
 * So a layer owns its own region and its own grid inside it. Cells run left to
 * right and then down within the region, exactly as they did when the grid
 * covered the file -- which it still does, for the thirty sheets that only ever
 * held one animation.
 *
 * @param sheet   the file, under {@code textures/duel/monsters/}, without its
 *                extension -- {@code "dragon/blue_eyes_white_dragon"}
 * @param x       the region's left edge in pixels
 * @param y       its top edge
 * @param w       its width, or 0 for "to the right edge of the file"
 * @param h       its height, or 0 for "to the bottom"
 * @param columns cells across the region
 * @param rows    cells down it
 * @param first   which cell this run starts at
 * @param frames  how many it runs for
 * @param ticks   how long each frame is held
 * @param trimX   pixels taken off the region's OUTER left and right edges
 * @param trimY   pixels taken off the region's OUTER top and bottom edges
 */
public record SpriteLayer(String sheet, int x, int y, int w, int h, int columns, int rows,
    int first, int frames, int ticks, MonsterSprites.Loop loop, int trimX, int trimY)
{
    /** A layer that takes the whole of each cell, which is most of them. */
    public SpriteLayer(String sheet, int x, int y, int w, int h, int columns, int rows,
        int first, int frames, int ticks, MonsterSprites.Loop loop)
    {
        this(sheet, x, y, w, h, columns, rows, first, frames, ticks, loop, 0, 0);
    }

    /** A whole file as one row of frames, which is the commonest sheet there is. */
    public static SpriteLayer row(String sheet, int frames, MonsterSprites.Loop loop)
    {
        return grid(sheet, frames, 1, 0, frames, loop);
    }

    /** A run of cells from a grid covering the whole file. */
    public static SpriteLayer grid(String sheet, int columns, int rows, int first, int frames,
        MonsterSprites.Loop loop)
    {
        return new SpriteLayer(sheet, 0, 0, 0, 0, Math.max(1, columns), Math.max(1, rows),
            Math.max(0, first), Math.max(1, frames), MonsterSprites.DEFAULT_TICKS, loop);
    }

    public Identifier texture()
    {
        return MonsterSheets.resolve(sheet);
    }

    /** Which cell of the region a given frame of this run is. */
    public int cell(int frame)
    {
        return first + Math.clamp(frame, 0, frames - 1);
    }

    /**
     * The texture coordinates of one frame, as {u0, v0, u1, v1}.
     * <p>
     * Pulled in by a whole TEXEL on every side, and the unit is the point. This
     * was a fraction of the cell -- a thousandth of it -- which on a sheet of
     * six cells across 512 pixels comes to eight hundredths of a texel: near
     * enough to nothing, and nothing is not enough.
     * <p>
     * The sheet's own edges are where it showed. The first cell begins at u = 0
     * and the last ends at u = 1, so a sample reaching past either one does not
     * find empty space -- it WRAPS, and comes back with the far side of the
     * sheet. A wing at the left edge grew a copy of the tip belonging to the
     * wing at the right edge, floating out beside it with nothing attached.
     * <p>
     * A whole texel rather than the usual half, because these sheets are drawn
     * small on screen and a scaled-down sample reads a neighbourhood rather
     * than a point. The art has margins to spare -- no sprite in any of these
     * sheets touches its cell's edge -- so the cost is nothing and the bleed is
     * gone.
     */
    public float[] uv(int frame)
    {
        int[] size = MonsterSprites.sizeOf(texture());
        float fileW = Math.max(1, size[0]);
        float fileH = Math.max(1, size[1]);
        float regionW = w > 0 ? w : fileW - x;
        float regionH = h > 0 ? h : fileH - y;

        int at = cell(frame);
        int column = at % Math.max(1, columns);
        int row = at / Math.max(1, columns);
        float cellW = regionW / Math.max(1, columns);
        float cellH = regionH / Math.max(1, rows);

        float[] window = windowAt(at);
        float left = (x + (column + window[0]) * cellW) / fileW;
        float top = (y + (row + window[1]) * cellH) / fileH;
        float right = (x + (column + window[2]) * cellW) / fileW;
        float bottom = (y + (row + window[3]) * cellH) / fileH;
        float insetU = 1F / fileW;
        float insetV = 1F / fileH;
        return new float[] {left + insetU, top + insetV, right - insetU, bottom - insetV};
    }

    /**
     * How wide one WHOLE cell is against its height, trim ignored.
     * <p>
     * Measured from this LAYER's own region and grid rather than from the file,
     * which is the whole reason a layer carries a region at all: wings and a
     * body sharing one sheet have different cell shapes, and a proportion
     * remembered against the file would give one of them the other's.
     * <p>
     * Deliberately blind to the trim, and that is what makes a crop a crop.
     * This is the size the sprite occupies in the world; {@link #spanX} and
     * {@link #spanY} say how much of that box the trim leaves. Fold the trim in
     * here instead and the two stop being separable: cutting a margin away
     * would resize the monster, because the same number would be describing
     * both how big it is and how much of it is being read.
     */
    public float aspect()
    {
        float cellW = cellW();
        float cellH = cellH();
        return cellW <= 0F || cellH <= 0F ? 0.5F : cellW / cellH;
    }

    /**
     * One cell's width in pixels.
     * <p>
     * The sheet is measured only when the region does not say. A layer that
     * states its own width knows its cells without opening the file, and asking
     * anyway would drag the texture manager into arithmetic that does not need
     * it -- which is also what made this impossible to test off a running game.
     */
    private float cellW()
    {
        float regionW = w > 0 ? w : Math.max(1, MonsterSprites.sizeOf(texture())[0]) - x;
        return regionW / Math.max(1, columns);
    }

    private float cellH()
    {
        float regionH = h > 0 ? h : Math.max(1, MonsterSprites.sizeOf(texture())[1]) - y;
        return regionH / Math.max(1, rows);
    }

    /**
     * Which part of a cell is sampled, as fractions of it: {left, top, right,
     * bottom}, where 0 and 1 are the cell's own edges.
     * <p>
     * <b>The trim comes off the OUTER edges only -- the ones on the boundary of
     * the region -- and never off the divisions between one cell and the next.</b>
     * That is where the problem it was built for lives. A sample reaching past
     * the region's edge does not find empty space, it WRAPS, and comes back
     * with the far side of the sheet: a wing at the left edge grew a copy of
     * the tip belonging to the wing at the right edge. Nothing of the kind
     * happens at an internal division, where the worst a stray sample can do is
     * pick up a neighbouring frame's margin -- which the texel inset in
     * {@link #uv} already handles.
     * <p>
     * So cutting every cell on all four sides was paying for one problem four
     * times over. A four-frame sheet has two outer vertical edges and three
     * internal ones; trimming all of them threw away art from the middle of the
     * run to fix a fault only its ends could have.
     * <p>
     * A cell that is both first and last on its axis -- a single column, or a
     * single row, which is what most of these sheets are -- has both of its
     * edges on the boundary and so gets trimmed at both. That is not a special
     * case, it is the same rule.
     */
    public float[] windowAt(int cell)
    {
        int across = Math.max(1, columns);
        int down = Math.max(1, rows);
        int column = cell % across;
        int row = cell / across;
        float cellW = cellW();
        float cellH = cellH();
        // Never past halfway, because the editor's trim reaches 128 pixels and
        // some cells are narrower than that -- Blue-Eyes' wings are about 85
        // across. An inverted box would not fail, which is the problem: it
        // would draw the sprite mirrored, and mirrored is a thing this code
        // does on purpose elsewhere.
        float insetX = cellW <= 0F ? 0F : Math.clamp(trimX / cellW, 0F, LIMIT);
        float insetY = cellH <= 0F ? 0F : Math.clamp(trimY / cellH, 0F, LIMIT);
        return new float[] {
            column == 0 ? insetX : 0F,
            row == 0 ? insetY : 0F,
            column == across - 1 ? 1F - insetX : 1F,
            row == down - 1 ? 1F - insetY : 1F};
    }

    /** The same, for a frame of this run rather than a cell of the region. */
    public float[] window(int frame)
    {
        return windowAt(cell(frame));
    }

    /** A sliver, rather than nothing at all, when the trim is asked to eat a whole cell. */
    private static final float LIMIT = 0.49F;

    /**
     * The same layer at a different pace, for the editor's speed control.
     * <p>
     * Carrying the trim through, like every other component. A "with" that
     * quietly drops one field is worse than no "with" at all: changing the
     * speed would throw away a crop somebody had just spent a minute lining up,
     * and nothing on screen would say where it went.
     */
    public SpriteLayer withTicks(int value)
    {
        return new SpriteLayer(sheet, x, y, w, h, columns, rows, first, frames,
            Math.max(1, value), loop, trimX, trimY);
    }

    public SpriteLayer withLoop(MonsterSprites.Loop value)
    {
        return new SpriteLayer(sheet, x, y, w, h, columns, rows, first, frames, ticks, value,
            trimX, trimY);
    }
}
