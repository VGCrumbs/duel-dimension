package de.cas_ual_ty.dueldimension.clientutil;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Drawing a piece of a texture, with an optional tint.
 * <p>
 * The Forge version of this class built its own quads: it took a
 * {@code PoseStack}, computed four corners and four texture coordinates, and
 * pushed them through {@code RenderSystem.setShader}. None of that exists any
 * more — the GUI is retained-mode, and a caller describes a blit rather than
 * performing one.
 * <p>
 * So what is left is arithmetic and a choice of overload. Two things the
 * callers need are not on the same method:
 * <ul>
 * <li>a <b>UV window</b>, because card art is letterboxed inside a square image
 *     and only the card's own part of it should be drawn;</li>
 * <li>a <b>tint</b>, because a buried card in a pile is drawn dimmer and an
 *     unowned card pulses red — both of which were
 *     {@code RenderSystem.setShaderColor} before a draw, and that call is gone
 *     with everything else immediate-mode.</li>
 * </ul>
 * The extractor's UV-window blit takes no colour, and its coloured blit takes a
 * texel region rather than a UV window. This converts between them so the
 * coloured overload serves both and there is one path instead of two.
 */
public final class DdBlitUtil
{
    /** Opaque white — the colour that changes nothing. */
    public static final int NO_TINT = 0xFFFFFFFF;

    /**
     * The imaginary file size a UV window is expressed against.
     * <p>
     * Any number works as long as the same one is used for the offset and the
     * size; this is large enough that rounding a region to a whole "texel"
     * cannot shift a card's art by a visible fraction.
     */
    private static final int SCALE = 4096;

    private DdBlitUtil()
    {
    }

    /**
     * Draws part of a texture into a rectangle.
     *
     * @param u0 v0 u1 v1 the part to sample, in 0..1 of the file
     * @param tint        ARGB multiplied into it; {@link #NO_TINT} for none
     */
    public static void blit(GuiGraphicsExtractor graphics, Identifier texture,
        int x, int y, int width, int height,
        float u0, float v0, float u1, float v1, int tint)
    {
        // The coloured overload works in texels against a stated file size:
        // it divides the offset by that size and adds the region to reach the
        // far edge. Read off the bytecode, its arguments are
        //     (x, y, uTexels, vTexels, width, height, regionW, regionH,
        //      texW, texH, colour)
        // so a UV window becomes texels by multiplying through a resolution we
        // choose. The region has to be an int, so the resolution sets the
        // precision: SCALE is far finer than any card image, and the actual
        // file size is deliberately not used -- card art is fetched at runtime
        // and its size is a setting, so nothing here can depend on it.
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture,
            x, y, u0 * SCALE, v0 * SCALE, width, height,
            Math.round((u1 - u0) * SCALE), Math.round((v1 - v0) * SCALE),
            SCALE, SCALE, tint);
    }

    /** The whole texture, stretched into the rectangle. */
    public static void fullBlit(GuiGraphicsExtractor graphics, Identifier texture,
        int x, int y, int width, int height)
    {
        graphics.blit(texture, x, y, x + width, y + height, 0F, 1F, 0F, 1F);
    }

    /** The whole texture, tinted. */
    public static void fullBlit(GuiGraphicsExtractor graphics, Identifier texture,
        int x, int y, int width, int height, int tint)
    {
        blit(graphics, texture, x, y, width, height, 0F, 0F, 1F, 1F, tint);
    }

    /**
     * An ARGB colour from a white-multiplied alpha, which is how the Forge code
     * expressed "the same picture, fainter".
     */
    public static int alpha(float alpha)
    {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255F)));
        return (a << 24) | 0x00FFFFFF;
    }

    /** An ARGB colour from the red/green/blue/alpha the old shader colour took. */
    public static int tint(float red, float green, float blue, float alpha)
    {
        return (Math.round(alpha * 255F) << 24)
            | (Math.round(red * 255F) << 16)
            | (Math.round(green * 255F) << 8)
            | Math.round(blue * 255F);
    }
}
