package de.cas_ual_ty.dueldimension.clientutil;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.BlendFactor;
import com.mojang.blaze3d.pipeline.BlendFunction;
import net.minecraft.client.renderer.RenderPipelines;

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
 * {@code RenderSystem.blendFuncSeparate} is gone, but the blend itself is not:
 * it moved onto the pipeline. These are {@code GUI_TEXTURED} rebuilt with the
 * two blend functions Forge asked for, factor for factor.
 * <p>
 * <b>Whether this works is an open question, and it is about batching, not
 * about blending.</b> The second draw has to see what the first wrote, and a
 * retained-mode GUI is free to reorder or merge draws. {@code FoilTestScreen}
 * exists to answer that before the duel screen is built on it.
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
    public static final RenderPipeline MASK = copyOfGuiTextured("foil_mask",
        new BlendFunction(BlendFactor.ZERO, BlendFactor.ONE,
            BlendFactor.SRC_ALPHA, BlendFactor.ZERO));

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
    public static final RenderPipeline FOIL = copyOfGuiTextured("foil",
        new BlendFunction(BlendFactor.ONE_MINUS_DST_ALPHA, BlendFactor.DST_COLOR,
            BlendFactor.DST_ALPHA, BlendFactor.ONE_MINUS_DST_ALPHA));

    /**
     * An {@code INVERTED} rarity layer: shown everywhere the cursor is NOT.
     * <p>
     * The exact complement of {@link #FOIL} — {@code DST_ALPHA} where that has
     * {@code ONE_MINUS_DST_ALPHA} — which is why the database pairs an
     * {@code _active} image with a {@code _passive} one. A foil is a crossfade
     * between two printings of the same frame, not a second coat over the
     * first.
     */
    public static final RenderPipeline FOIL_INVERTED = copyOfGuiTextured("foil_inverted",
        new BlendFunction(BlendFactor.DST_ALPHA, BlendFactor.DST_COLOR,
            BlendFactor.ONE_MINUS_DST_ALPHA, BlendFactor.DST_ALPHA));

    /**
     * The fallback: a plain additive glint that never reads the framebuffer.
     * <p>
     * If batching turns out to break the two-pass trick, this is what the foil
     * becomes -- brighter where the foil texture is bright, with no cursor
     * tracking. Visibly close, and it degrades a cosmetic rather than a rule.
     */
    public static final RenderPipeline ADDITIVE = copyOfGuiTextured("foil_additive",
        BlendFunction.ADDITIVE);

    private FoilPipelines()
    {
    }

    /**
     * {@code GUI_TEXTURED} with a different blend and nothing else changed.
     * <p>
     * The rebuild-from-getters this used to spell out itself now lives in
     * {@link PipelineCopy}, because {@link UnownedPipelines} needs the same
     * thing with the fragment shader overridden instead of the blend. Passing
     * null for the shader keeps {@code GUI_TEXTURED}'s own.
     */
    private static RenderPipeline copyOfGuiTextured(String name, BlendFunction blend)
    {
        return PipelineCopy.of(RenderPipelines.GUI_TEXTURED, name, null, blend);
    }
}
