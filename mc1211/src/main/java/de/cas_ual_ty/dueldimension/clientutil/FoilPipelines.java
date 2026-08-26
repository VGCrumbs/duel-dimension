package de.cas_ual_ty.dueldimension.clientutil;

import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;

/**
 * The two blends behind a foil card's glint.
 * <p>
 * A rare card catches the light as the cursor passes over it. Forge produced
 * that with two draws and the framebuffer's alpha channel as scratch space:
 * <ol>
 * <li>draw a soft radial mask at the cursor with a blend that writes
 *     <em>only</em> alpha and leaves colour alone, so nothing appears but the
 *     alpha channel now holds "how lit is this pixel";</li>
 * <li>draw the foil with a blend that shows it in proportion to that alpha.</li>
 * </ol>
 * On 1.21.1 {@code RenderSystem.blendFuncSeparate} is back, so these are the
 * two blend functions Forge asked for again, factor for factor -- the same
 * quadruples 1.19.2's {@code DdBlitUtil.advancedMaskedBlit} set.
 * <p>
 * <b>Batching is not a worry here.</b> 1.21.1's {@code GuiGraphics.innerBlit}
 * ends in {@code BufferUploader.drawWithShader}, so a blit is issued where it
 * is written and the second draw always sees what the first wrote. It brackets
 * every blit with {@code enableBlend}/{@code disableBlend} but never touches
 * the func, so setting the func before the blit is enough.
 * <p>
 * <b>These are no longer pipelines and cannot be handed to a blit.</b> Call
 * {@link Blend#apply()} before the draw and {@link #reset()} after the last
 * one. The class keeps its name only because every call site names it.
 */
public final class FoilPipelines
{
    /**
     * Writes alpha, not colour.
     * <p>
     * {@code (ZERO, ONE)} for colour: the source contributes nothing and the
     * destination is kept exactly, so the screen does not change. {@code
     * (SRC_ALPHA, ZERO)} for alpha: the destination's alpha is replaced by the
     * mask's. Straight from the Forge call.
     */
    public static final Blend MASK = new Blend(SourceFactor.ZERO, DestFactor.ONE,
        SourceFactor.SRC_ALPHA, DestFactor.ZERO);

    /**
     * A {@code NORMAL} rarity layer: shown AT the cursor.
     * <p>
     * <b>Read the factor and the mask together, or it comes out backwards.</b>
     * The source factor is {@code ONE_MINUS_DST_ALPHA}, so on its own this
     * shows the layer where the alpha written above is <em>low</em> — and
     * {@code rarity_mask.png} is deliberately the inverse of what its name
     * suggests: measured, it is alpha 128 across the middle and 255 at the
     * border, a hole rather than a spot. So the low alpha is exactly the
     * cursor, and the net effect is a layer that glints under the mouse.
     * <p>
     * Anything reproducing this without the two-pass trick — see
     * {@code CardPreviewScreen}, which cannot use it inside a
     * picture-in-picture — must therefore drive a NORMAL layer with the
     * highlight itself, not with its complement.
     */
    public static final Blend FOIL = new Blend(SourceFactor.ONE_MINUS_DST_ALPHA,
        DestFactor.DST_COLOR, SourceFactor.DST_ALPHA, DestFactor.ONE_MINUS_DST_ALPHA);

    /**
     * An {@code INVERTED} rarity layer: shown everywhere the cursor is NOT.
     * <p>
     * The exact complement of {@link #FOIL} — {@code DST_ALPHA} where that has
     * {@code ONE_MINUS_DST_ALPHA} — which is why the database pairs an
     * {@code _active} image with a {@code _passive} one. A foil is a crossfade
     * between two printings of the same frame, not a second coat over the
     * first.
     */
    public static final Blend FOIL_INVERTED = new Blend(SourceFactor.DST_ALPHA,
        DestFactor.DST_COLOR, SourceFactor.ONE_MINUS_DST_ALPHA, DestFactor.DST_ALPHA);

    /**
     * The fallback: a plain additive glint that never reads the framebuffer.
     * <p>
     * If batching turns out to break the two-pass trick, this is what the foil
     * becomes -- brighter where the foil texture is bright, with no cursor
     * tracking. Visibly close, and it degrades a cosmetic rather than a rule.
     */
    public static final Blend ADDITIVE = new Blend(SourceFactor.ONE, DestFactor.ONE,
        SourceFactor.ONE, DestFactor.ONE);

    private FoilPipelines()
    {
    }

    /**
     * Puts the blend back where the rest of the GUI expects to find it.
     * <p>
     * The 1.19.2 original only called {@code disableBlend} and left the func
     * dirty for whatever enabled blending next. {@code defaultBlendFunc} is
     * added here because that leak is real and free to avoid.
     */
    public static void reset()
    {
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /** One separate blend func: the colour pair, then the alpha pair. */
    public record Blend(SourceFactor srcColour, DestFactor dstColour,
        SourceFactor srcAlpha, DestFactor dstAlpha)
    {
        /** Set this blend for every draw until the next apply or {@link #reset()}. */
        public void apply()
        {
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(srcColour, dstColour, srcAlpha, dstAlpha);
        }
    }
}
