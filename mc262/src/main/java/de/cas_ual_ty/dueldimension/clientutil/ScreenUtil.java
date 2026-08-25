package de.cas_ual_ty.dueldimension.clientutil;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * Rectangles and wrapped text.
 * <p>
 * The Forge version of this class built quads by hand: a {@code Tesselator}, a
 * vertex format, a shader picked by hand and blending enabled and disabled
 * around it, all to fill a rectangle with a colour. None of that survives the
 * move to a retained-mode GUI, and none of it is missed — the extractor fills a
 * rectangle, and the renderer decides how.
 * <p>
 * Two habits from the old API are gone rather than translated:
 * <ul>
 * <li>{@code white()}, which reset the global shader colour. There is no global
 *     shader colour to reset; a colour is an argument to the thing being drawn.
 *     Every call to it was undoing a previous call's side effect.</li>
 * <li>The colour-mask trick in the hover and disabled overlays. It wrote RGB
 *     but not alpha so a translucent white would lighten a slot without
 *     touching what was behind it. There is no per-draw colour mask any more;
 *     an ARGB fill does the same job in one call, and says what it means.</li>
 * </ul>
 */
public class ScreenUtil
{
    /** An ARGB colour from the four floats the old calls took. */
    public static int colour(float r, float g, float b, float a)
    {
        return (channel(a) << 24) | (channel(r) << 16) | (channel(g) << 8) | channel(b);
    }

    private static int channel(float value)
    {
        return Math.max(0, Math.min(255, Math.round(value * 255F)));
    }

    /** An outline, drawn as four fills — the same four the Forge version drew. */
    public static void drawLineRect(GuiGraphicsExtractor graphics, float x, float y,
        float w, float h, float lineWidth, float r, float g, float b, float a)
    {
        drawRect(graphics, x, y, w, lineWidth, r, g, b, a); // top
        drawRect(graphics, x, y + h - lineWidth, w, lineWidth, r, g, b, a); // bottom
        drawRect(graphics, x, y, lineWidth, h, r, g, b, a); // left
        drawRect(graphics, x + w - lineWidth, y, lineWidth, h, r, g, b, a); // right
    }

    public static void drawRect(GuiGraphicsExtractor graphics, float x, float y, float w, float h,
        float r, float g, float b, float a)
    {
        drawRect(graphics, x, y, w, h, colour(r, g, b, a));
    }

    /**
     * A filled rectangle.
     * <p>
     * The extractor works in whole pixels and takes two corners rather than a
     * position and a size, so the floats the callers deal in are rounded here
     * rather than at every call site. Rounding the far edge from the far edge —
     * not the width — is what stops a row of rectangles growing a gap between
     * them.
     */
    public static void drawRect(GuiGraphicsExtractor graphics, float x, float y, float w, float h,
        int argb)
    {
        graphics.fill(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h), argb);
    }

    public static void drawSplitString(GuiGraphicsExtractor graphics, Font fontRenderer,
        List<Component> list, float x, float y, int maxWidth, int color)
    {
        // The colour used to be an RGB with the alpha implied; the extractor
        // takes ARGB and would draw nothing for a colour with none.
        int argb = (color & 0xFF000000) == 0 ? color | 0xFF000000 : color;
        for(Component t : list)
        {
            if(t.getString().isEmpty() && t.getSiblings().isEmpty())
            {
                y += fontRenderer.lineHeight;
            }
            else
            {
                for(FormattedCharSequence p : fontRenderer.split(t, maxWidth))
                {
                    graphics.text(fontRenderer, p, Math.round(x), Math.round(y), argb, true);
                    y += fontRenderer.lineHeight;
                }
            }
        }
    }

    /** The lightening a hovered slot gets. */
    public static void renderHoverRect(GuiGraphicsExtractor graphics, float x, float y,
        float w, float h)
    {
        drawRect(graphics, x, y, w, h, 0x80FFFFFF);
    }

    /** The dimming an unavailable one gets. */
    public static void renderDisabledRect(GuiGraphicsExtractor graphics, float x, float y,
        float w, float h)
    {
        drawRect(graphics, x, y, w, h, 0x80000000);
    }
}
