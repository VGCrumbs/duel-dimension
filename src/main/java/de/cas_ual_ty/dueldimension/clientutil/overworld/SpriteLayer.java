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
 */
public record SpriteLayer(String sheet, int x, int y, int w, int h, int columns, int rows,
    int first, int frames, int ticks, MonsterSprites.Loop loop)
{
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
        return Identifier.fromNamespaceAndPath(DuelDimension.MOD_ID,
            "textures/duel/monsters/" + sheet + ".png");
    }

    /** Which cell of the region a given frame of this run is. */
    public int cell(int frame)
    {
        return first + Math.clamp(frame, 0, frames - 1);
    }

    /**
     * The texture coordinates of one frame, as {u0, v0, u1, v1}.
     * <p>
     * Half a texel in on every side. Sampling exactly on the seam between two
     * cells borrows a strip of the neighbour while the quad is scaled, which
     * reads as a sliver of the wrong pose down one edge.
     */
    public float[] uv(int frame)
    {
        int[] size = MonsterSprites.sizeOf(texture());
        float fileW = Math.max(1, size[0]);
        float fileH = Math.max(1, size[1]);
        float regionW = w > 0 ? w : fileW - x;
        float regionH = h > 0 ? h : fileH - y;

        int at = cell(frame);
        int column = at % columns;
        int row = at / columns;
        float cellW = regionW / columns;
        float cellH = regionH / rows;

        float left = (x + column * cellW) / fileW;
        float right = (x + (column + 1) * cellW) / fileW;
        float top = (y + row * cellH) / fileH;
        float bottom = (y + (row + 1) * cellH) / fileH;
        float insetU = (right - left) * 0.001F;
        float insetV = (bottom - top) * 0.001F;
        return new float[] {left + insetU, top + insetV, right - insetU, bottom - insetV};
    }

    /**
     * How wide one cell is against its height.
     * <p>
     * Measured from this LAYER's own region and grid rather than from the file,
     * which is the whole reason a layer carries a region at all: wings and a
     * body sharing one sheet have different cell shapes, and a proportion
     * remembered against the file would give one of them the other's.
     */
    public float aspect()
    {
        int[] size = MonsterSprites.sizeOf(texture());
        float regionW = w > 0 ? w : Math.max(1, size[0]) - x;
        float regionH = h > 0 ? h : Math.max(1, size[1]) - y;
        float cellW = regionW / Math.max(1, columns);
        float cellH = regionH / Math.max(1, rows);
        return cellH <= 0F ? 0.5F : cellW / cellH;
    }

    /** The same layer at a different pace, for the editor's speed control. */
    public SpriteLayer withTicks(int value)
    {
        return new SpriteLayer(sheet, x, y, w, h, columns, rows, first, frames,
            Math.max(1, value), loop);
    }

    public SpriteLayer withLoop(MonsterSprites.Loop value)
    {
        return new SpriteLayer(sheet, x, y, w, h, columns, rows, first, frames, ticks, value);
    }
}
