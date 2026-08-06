package de.cas_ual_ty.dueldimension.clientutil.hub;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil;
import net.minecraft.resources.ResourceLocation;

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
    public static void draw(PoseStack poseStack, ResourceLocation texture,
        int x, int y, int width, int height)
    {
        draw(poseStack, texture, x, y, width, height, 0, 1);
    }

    /**
     * Draws one row of a state atlas.
     *
     * @param row  which state, e.g. {@link #HOVER}
     * @param rows how many states the atlas holds
     */
    public static void draw(PoseStack poseStack, ResourceLocation texture,
        int x, int y, int width, int height, int row, int rows)
    {
        RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        RenderSystem.enableBlend();
        RenderSystem.setShaderTexture(0, texture);

        int fileHeight = TILE * rows;
        int v0 = row * TILE;

        // A panel smaller than two borders would have its corners overlap and
        // draw the frame twice; clamp so it degrades to just the corners.
        int edge = Math.min(BORDER, Math.min(width, height) / 2);
        int midW = Math.max(0, width - edge * 2);
        int midH = Math.max(0, height - edge * 2);
        int texMid = TILE - BORDER * 2;

        // corners
        blit(poseStack, x, y, edge, edge, 0, v0, BORDER, BORDER, fileHeight);
        blit(poseStack, x + width - edge, y, edge, edge, TILE - BORDER, v0, BORDER, BORDER, fileHeight);
        blit(poseStack, x, y + height - edge, edge, edge, 0, v0 + TILE - BORDER, BORDER, BORDER, fileHeight);
        blit(poseStack, x + width - edge, y + height - edge, edge, edge,
            TILE - BORDER, v0 + TILE - BORDER, BORDER, BORDER, fileHeight);

        // edges
        if(midW > 0)
        {
            blit(poseStack, x + edge, y, midW, edge, BORDER, v0, texMid, BORDER, fileHeight);
            blit(poseStack, x + edge, y + height - edge, midW, edge,
                BORDER, v0 + TILE - BORDER, texMid, BORDER, fileHeight);
        }
        if(midH > 0)
        {
            blit(poseStack, x, y + edge, edge, midH, 0, v0 + BORDER, BORDER, texMid, fileHeight);
            blit(poseStack, x + width - edge, y + edge, edge, midH,
                TILE - BORDER, v0 + BORDER, BORDER, texMid, fileHeight);
        }

        // middle
        if(midW > 0 && midH > 0)
        {
            blit(poseStack, x + edge, y + edge, midW, midH, BORDER, v0 + BORDER, texMid, texMid, fileHeight);
        }
    }

    private static void blit(PoseStack poseStack, int x, int y, int w, int h,
        int u, int v, int uw, int vh, int fileHeight)
    {
        DdBlitUtil.blit(poseStack, x, y, w, h, u, v, uw, vh, TILE, fileHeight);
    }

    /** A plain stretched texture, for art that is not a frame. */
    public static void image(PoseStack poseStack, ResourceLocation texture,
        int x, int y, int width, int height)
    {
        RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        RenderSystem.enableBlend();
        RenderSystem.setShaderTexture(0, texture);
        DdBlitUtil.fullBlit(poseStack, x, y, width, height);
    }

    /**
     * A texture multiplied by a colour. This is how one greyscale source serves
     * every colour the mat picker offers, rather than shipping a file per hue.
     */
    public static void tinted(PoseStack poseStack, ResourceLocation texture,
        int x, int y, int width, int height, int rgb)
    {
        RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexShader);
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(((rgb >> 16) & 0xFF) / 255F, ((rgb >> 8) & 0xFF) / 255F,
            (rgb & 0xFF) / 255F, 1F);
        RenderSystem.setShaderTexture(0, texture);
        DdBlitUtil.fullBlit(poseStack, x, y, width, height);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
    }
}
